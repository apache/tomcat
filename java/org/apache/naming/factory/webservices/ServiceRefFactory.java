/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.naming.factory.webservices;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import javax.naming.spi.ObjectFactory;
import javax.wsdl.Definition;
import javax.wsdl.Port;
import javax.wsdl.extensions.ExtensibilityElement;
import javax.wsdl.extensions.soap.SOAPAddress;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import javax.xml.namespace.QName;
import javax.xml.rpc.Service;
import javax.xml.rpc.ServiceFactory;
import javax.xml.rpc.handler.Handler;
import javax.xml.rpc.handler.HandlerChain;
import javax.xml.rpc.handler.HandlerInfo;
import javax.xml.rpc.handler.HandlerRegistry;

import org.apache.naming.HandlerRef;
import org.apache.naming.ServiceRef;

/**
 * Object factory for Web Services.
 */
public class ServiceRefFactory implements ObjectFactory {

    /**
     * Create a new serviceref instance.
     *
     * @param obj The reference object describing the webservice
     */
    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?,?> environment)
            throws Exception {

        if (obj instanceof ServiceRef) {
            ServiceRef ref = (ServiceRef) obj;

            // ClassLoader
            ClassLoader tcl = Thread.currentThread().getContextClassLoader();
            if (tcl == null) {
                tcl = this.getClass().getClassLoader();
            }
            ServiceFactory factory = ServiceFactory.newInstance();
            javax.xml.rpc.Service service = null;

            // Service Interface
            RefAddr tmp = ref.get(ServiceRef.SERVICE_INTERFACE);
            String serviceInterface = null;
            if (tmp != null) {
                serviceInterface = (String) tmp.getContent();
            }

            // WSDL
            tmp = ref.get(ServiceRef.WSDL);
            String wsdlRefAddr = null;
            if (tmp != null) {
                wsdlRefAddr = (String) tmp.getContent();
            }

            // PortComponent
            Hashtable<String,QName> portComponentRef = new Hashtable<>();

            // Create QName object
            QName serviceQname = null;
            tmp = ref.get(ServiceRef.SERVICE_LOCAL_PART);
            if (tmp != null) {
                String serviceLocalPart = (String) tmp.getContent();
                tmp = ref.get(ServiceRef.SERVICE_NAMESPACE);
                if (tmp == null) {
                    serviceQname = new QName(serviceLocalPart);
                } else {
                    String serviceNamespace = (String) tmp.getContent();
                    serviceQname = new QName(serviceNamespace, serviceLocalPart);
                }
            }
            Class<?> serviceInterfaceClass = null;

            // Create service object
            if (serviceInterface == null) {
                if (serviceQname == null) {
                    throw new NamingException("Could not create service-ref instance");
                }
                try {
                    if (wsdlRefAddr == null) {
                        service = factory.createService(serviceQname);
                    } else {
                        service = factory.createService(new URI(wsdlRefAddr).toURL(), serviceQname);
                    }
                } catch (Exception e) {
                    NamingException ex = new NamingException("Could not create service");
                    ex.initCause(e);
                    throw ex;
                }
            } else {
                // Loading service Interface
                try {
                    serviceInterfaceClass = tcl.loadClass(serviceInterface);
                } catch (ClassNotFoundException e) {
                    NamingException ex = new NamingException("Could not load service Interface");
                    ex.initCause(e);
                    throw ex;
                }
                if (serviceInterfaceClass == null) {
                    throw new NamingException("Could not load service Interface");
                }
                try {
                    if (wsdlRefAddr == null) {
                        if (!Service.class.isAssignableFrom(serviceInterfaceClass)) {
                            throw new NamingException("service Interface should extend javax.xml.rpc.Service");
                        }
                        service = factory.loadService(serviceInterfaceClass);
                    } else {
                        service = factory.loadService(new URI(wsdlRefAddr).toURL(), serviceInterfaceClass,
                                new Properties());
                    }
                } catch (Exception e) {
                    NamingException ex = new NamingException("Could not create service");
                    ex.initCause(e);
                    throw ex;
                }
            }
            if (service == null) {
                throw new NamingException("Cannot create service object");
            }
            serviceQname = service.getServiceName();
            serviceInterfaceClass = service.getClass();
            if (wsdlRefAddr != null) {
                try {
                    WSDLFactory wsdlfactory = WSDLFactory.newInstance();
                    WSDLReader reader = wsdlfactory.newWSDLReader();
                    reader.setFeature("javax.wsdl.importDocuments", true);
                    Definition def = reader.readWSDL(new URI(wsdlRefAddr).toURL().toExternalForm());

                    javax.wsdl.Service wsdlservice = def.getService(serviceQname);
                    @SuppressWarnings("unchecked") // Can't change the API
                    Map<String,?> ports = wsdlservice.getPorts();
                    Method m = serviceInterfaceClass.getMethod("setEndpointAddress",
                            new Class[] { java.lang.String.class, java.lang.String.class });
                    for (String portName : ports.keySet()) {
                        Port port = wsdlservice.getPort(portName);
                        String endpoint = getSOAPLocation(port);
                        m.invoke(service, new Object[] { port.getName(), endpoint });
                        portComponentRef.put(endpoint, new QName(port.getName()));
                    }
                } catch (Exception e) {
                    if (e instanceof InvocationTargetException) {
                        Throwable cause = e.getCause();
                        if (cause instanceof ThreadDeath) {
                            throw (ThreadDeath) cause;
                        }
                        if (cause instanceof VirtualMachineError) {
                            throw (VirtualMachineError) cause;
                        }
                    }
                    NamingException ex = new NamingException("Error while reading Wsdl File");
                    ex.initCause(e);
                    throw ex;
                }
            }

            ServiceProxy proxy = new ServiceProxy(service);

            // Use port-component-ref
            for (int i = 0; i < ref.size(); i++) {
                if (ServiceRef.SERVICEENDPOINTINTERFACE.equals(ref.get(i).getType())) {
                    String serviceendpoint = "";
                    String portlink = "";
                    serviceendpoint = (String) ref.get(i).getContent();
                    if (ServiceRef.PORTCOMPONENTLINK.equals(ref.get(i + 1).getType())) {
                        i++;
                        portlink = (String) ref.get(i).getContent();
                    }
                    portComponentRef.put(serviceendpoint, new QName(portlink));

                }
            }
            proxy.setPortComponentRef(portComponentRef);

            // Instantiate service with proxy class
            Class<?>[] serviceInterfaces = serviceInterfaceClass.getInterfaces();

            Class<?>[] interfaces = Arrays.copyOf(serviceInterfaces, serviceInterfaces.length + 1);
            interfaces[interfaces.length - 1] = javax.xml.rpc.Service.class;

            Object proxyInstance = null;
            try {
                proxyInstance = Proxy.newProxyInstance(tcl, interfaces, proxy);
            } catch (IllegalArgumentException e) {
                proxyInstance = Proxy.newProxyInstance(tcl, serviceInterfaces, proxy);
            }

            // Use handler
            if (ref.getHandlersSize() > 0) {

                HandlerRegistry handlerRegistry = service.getHandlerRegistry();
                List<String> soaproles = new ArrayList<>();

                while (ref.getHandlersSize() > 0) {
                    HandlerRef handlerRef = ref.getHandler();
                    HandlerInfo handlerInfo = new HandlerInfo();

                    // Loading handler Class
                    tmp = handlerRef.get(HandlerRef.HANDLER_CLASS);
                    if ((tmp == null) || (tmp.getContent() == null)) {
                        break;
                    }
                    Class<?> handlerClass = null;
                    try {
                        handlerClass = tcl.loadClass((String) tmp.getContent());
                    } catch (ClassNotFoundException e) {
                        break;
                    }

                    // Load all data relative to the handler : SOAPHeaders, config init element,
                    // portNames to be set on
                    List<QName> headers = new ArrayList<>();
                    Hashtable<String,String> config = new Hashtable<>();
                    List<String> portNames = new ArrayList<>();
                    for (int i = 0; i < handlerRef.size(); i++) {
                        if (HandlerRef.HANDLER_LOCALPART.equals(handlerRef.get(i).getType())) {
                            String localpart = "";
                            String namespace = "";
                            localpart = (String) handlerRef.get(i).getContent();
                            if (HandlerRef.HANDLER_NAMESPACE.equals(handlerRef.get(i + 1).getType())) {
                                i++;
                                namespace = (String) handlerRef.get(i).getContent();
                            }
                            QName header = new QName(namespace, localpart);
                            headers.add(header);
                        } else if (HandlerRef.HANDLER_PARAMNAME.equals(handlerRef.get(i).getType())) {
                            String paramName = "";
                            String paramValue = "";
                            paramName = (String) handlerRef.get(i).getContent();
                            if (HandlerRef.HANDLER_PARAMVALUE.equals(handlerRef.get(i + 1).getType())) {
                                i++;
                                paramValue = (String) handlerRef.get(i).getContent();
                            }
                            config.put(paramName, paramValue);
                        } else if (HandlerRef.HANDLER_SOAPROLE.equals(handlerRef.get(i).getType())) {
                            String soaprole = "";
                            soaprole = (String) handlerRef.get(i).getContent();
                            soaproles.add(soaprole);
                        } else if (HandlerRef.HANDLER_PORTNAME.equals(handlerRef.get(i).getType())) {
                            String portName = "";
                            portName = (String) handlerRef.get(i).getContent();
                            portNames.add(portName);
                        }
                    }

                    // Set the handlers information
                    handlerInfo.setHandlerClass(handlerClass);
                    handlerInfo.setHeaders(headers.toArray(new QName[0]));
                    handlerInfo.setHandlerConfig(config);

                    if (!portNames.isEmpty()) {
                        for (String portName : portNames) {
                            initHandlerChain(new QName(portName), handlerRegistry, handlerInfo, soaproles);
                        }
                    } else {
                        Enumeration<QName> e = portComponentRef.elements();
                        while (e.hasMoreElements()) {
                            initHandlerChain(e.nextElement(), handlerRegistry, handlerInfo, soaproles);
                        }
                    }
                }
            }

            return proxyInstance;

        }

        return null;

    }

    /**
     * @param port analyzed port
     *
     * @return Returns the endpoint URL of the given Port
     */
    private String getSOAPLocation(Port port) {
        String endpoint = null;
        @SuppressWarnings("unchecked") // Can't change the API
        List<ExtensibilityElement> extensions = port.getExtensibilityElements();
        for (ExtensibilityElement ext : extensions) {
            if (ext instanceof SOAPAddress) {
                SOAPAddress addr = (SOAPAddress) ext;
                endpoint = addr.getLocationURI();
            }
        }
        return endpoint;
    }


    private void initHandlerChain(QName portName, HandlerRegistry handlerRegistry, HandlerInfo handlerInfo,
            List<String> soaprolesToAdd) {
        HandlerChain handlerChain = (HandlerChain) handlerRegistry.getHandlerChain(portName);
        @SuppressWarnings("unchecked") // Can't change the API
        Iterator<Handler> iter = handlerChain.iterator();
        while (iter.hasNext()) {
            Handler handler = iter.next();
            handler.init(handlerInfo);
        }
        String[] soaprolesRegistered = handlerChain.getRoles();
        String[] soaproles = new String[soaprolesRegistered.length + soaprolesToAdd.size()];
        int i;
        for (i = 0; i < soaprolesRegistered.length; i++) {
            soaproles[i] = soaprolesRegistered[i];
        }
        for (int j = 0; j < soaprolesToAdd.size(); j++) {
            soaproles[i + j] = soaprolesToAdd.get(j);
        }
        handlerChain.setRoles(soaproles);
        handlerRegistry.setHandlerChain(portName, handlerChain);
    }


}

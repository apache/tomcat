/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
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

import java.net.URL;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.spi.ObjectFactory;
import javax.naming.Reference;
import javax.naming.RefAddr;

import javax.wsdl.Definition;
import javax.wsdl.Port;
import javax.wsdl.extensions.ExtensibilityElement;
import javax.wsdl.extensions.soap.SOAPAddress;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;

import javax.xml.namespace.QName;
import javax.xml.rpc.handler.HandlerChain;
import javax.xml.rpc.handler.HandlerInfo;
import javax.xml.rpc.handler.HandlerRegistry;
import javax.xml.rpc.Service;
import javax.xml.rpc.ServiceFactory;

import org.apache.naming.HandlerRef;
import org.apache.naming.ServiceRef;

/**
 * Object factory for Web Services.
 * 
 * @author Fabien Carrion
 */

public class ServiceRefFactory
    implements ObjectFactory {


    // ----------------------------------------------------------- Constructors


    // -------------------------------------------------------------- Constants


    // ----------------------------------------------------- Instance Variables


    // --------------------------------------------------------- Public Methods


    // -------------------------------------------------- ObjectFactory Methods


    /**
     * Crete a new serviceref instance.
     * 
     * @param obj The reference object describing the webservice
     */
    public Object getObjectInstance(Object obj, Name name, Context nameCtx,
            Hashtable environment)
    throws Exception {

        if (obj instanceof ServiceRef) {
            Reference ref = (Reference) obj;

            // ClassLoader
            ClassLoader tcl = 
                Thread.currentThread().getContextClassLoader();
            if (tcl == null)
                tcl = this.getClass().getClassLoader();
            ServiceFactory factory = ServiceFactory.newInstance();
            javax.xml.rpc.Service service = null;

            // Service Interface
            RefAddr tmp = ref.get(ServiceRef.SERVICE_INTERFACE);
            String serviceInterface = null;
            if (tmp != null)
                serviceInterface = (String) tmp.getContent();

            // WSDL
            tmp = ref.get(ServiceRef.WSDL);
            String wsdlRefAddr = null;
            if (tmp != null)
                wsdlRefAddr = (String) tmp.getContent();

            // PortComponent
            Hashtable portComponentRef = new Hashtable();

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
                    serviceQname = new QName(serviceNamespace,
                            serviceLocalPart);
                }
            }
            Class serviceInterfaceClass = null;

            // Create service object
            if (serviceInterface == null) {
                if (serviceQname == null) {
                    throw new NamingException
                    ("Could not create service-ref instance");
                }
                try {
                    if (wsdlRefAddr == null) {
                        service = factory.createService( serviceQname );
                    } else {
                        service = factory.createService( new URL(wsdlRefAddr),
                                serviceQname );
                    }
                } catch (Throwable t) {
                    NamingException ex = new NamingException
                    ("Could not create service");
                    ex.initCause(t);
                    throw ex;
                }
            } else {
                // Loading service Interface
                try {
                    serviceInterfaceClass = tcl.loadClass(serviceInterface);
                } catch(ClassNotFoundException e) {
                    NamingException ex = new NamingException
                    ("Could not load service Interface");
                    ex.initCause(e);
                    throw ex;
                }
                if (serviceInterfaceClass == null) {
                    throw new NamingException
                    ("Could not load service Interface");
                }
                try {
                    if (wsdlRefAddr == null) {
                        if (!Service.class.isAssignableFrom(serviceInterfaceClass)) {
                            throw new NamingException
                            ("service Interface should extend javax.xml.rpc.Service");
                        }
                        service = factory.loadService( serviceInterfaceClass );
                    } else {
                        service = factory.loadService( new URL(wsdlRefAddr),
                                serviceInterfaceClass,
                                new Properties() );
                    }
                } catch (Throwable t) {
                    NamingException ex = new NamingException
                    ("Could not create service");
                    ex.initCause(t);
                    throw ex;
                }
            }
            if (service == null) {
                throw new NamingException
                ("Cannot create service object");
            }
            serviceQname = service.getServiceName();
            serviceInterfaceClass = service.getClass();
            if (wsdlRefAddr != null) {
                try {
                    WSDLFactory wsdlfactory = WSDLFactory.newInstance();
                    WSDLReader reader = wsdlfactory.newWSDLReader();
                    reader.setFeature("javax.wsdl.importDocuments", true);
                    Definition def = reader.readWSDL((new URL(wsdlRefAddr)).toExternalForm());

                    javax.wsdl.Service wsdlservice = def.getService(serviceQname);
                    Map ports = wsdlservice.getPorts();
                    Method m = serviceInterfaceClass.getMethod("setEndpointAddress",
                            new Class[] { java.lang.String.class,
                            java.lang.String.class });
                    for (Iterator i = ports.keySet().iterator(); i.hasNext();) {
                        String portName = (String) i.next();
                        Port port = wsdlservice.getPort(portName);
                        String endpoint = getSOAPLocation(port);
                        m.invoke(service, new Object[] {port.getName(), endpoint });
                        portComponentRef.put(endpoint, new QName(port.getName()));
                    }
                } catch (Throwable t) {
                    NamingException ex = new NamingException
                    ("Error while reading Wsdl File");
                    ex.initCause(t);
                    throw ex;
                }
            }

            ServiceProxy proxy = new ServiceProxy(service);

            // Use port-component-ref
            for (int i = 0; i < ref.size(); i++)
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
            proxy.setPortComponentRef(portComponentRef);

            // Instantiate service with proxy class
            Class[] interfaces = null;
            Class[] serviceInterfaces = serviceInterfaceClass.getInterfaces();
            if (serviceInterfaceClass != null) {
                interfaces = new Class[serviceInterfaces.length + 1];
                for (int i = 0; i < serviceInterfaces.length; i++) {
                    interfaces[i] = serviceInterfaces[i];
                }
            } else {
                interfaces = new Class[1];
            }
            interfaces[interfaces.length - 1] = javax.xml.rpc.Service.class;
            Object proxyInstance = null;
            try {
                proxyInstance = Proxy.newProxyInstance(tcl, interfaces, proxy);
            } catch (IllegalArgumentException e) {
                proxyInstance = Proxy.newProxyInstance(tcl, serviceInterfaces, proxy);
            }

            // Use handler
            if (((ServiceRef) ref).getHandlersSize() > 0) {

                HandlerRegistry handlerRegistry = service.getHandlerRegistry();
                ArrayList soaproles = new ArrayList();

                while (((ServiceRef) ref).getHandlersSize() > 0) {
                    HandlerRef handler = ((ServiceRef) ref).getHandler();
                    HandlerInfo handlerref = new HandlerInfo();

                    // Loading handler Class
                    tmp = handler.get(HandlerRef.HANDLER_CLASS);
                    if ((tmp == null) || (tmp.getContent() == null))
                        break;
                    Class handlerClass = null;
                    try {
                        handlerClass = tcl.loadClass((String) tmp.getContent());
                    } catch(ClassNotFoundException e) {
                        break;
                    }

                    // Load all datas relative to the handler : SOAPHeaders, config init element,
                    // portNames to be set on
                    ArrayList headers = new ArrayList();
                    Hashtable config = new Hashtable();
                    ArrayList portNames = new ArrayList();
                    for (int i = 0; i < handler.size(); i++)
                        if (HandlerRef.HANDLER_LOCALPART.equals(handler.get(i).getType())) {
                            String localpart = "";
                            String namespace = "";
                            localpart = (String) handler.get(i).getContent();
                            if (HandlerRef.HANDLER_NAMESPACE.equals(handler.get(i + 1).getType())) {
                                i++;
                                namespace = (String) handler.get(i).getContent();
                            }
                            QName header = new QName(namespace, localpart);
                            headers.add(header);
                        } else if (HandlerRef.HANDLER_PARAMNAME.equals(handler.get(i).getType())) {
                            String paramName = "";
                            String paramValue = "";
                            paramName = (String) handler.get(i).getContent();
                            if (HandlerRef.HANDLER_PARAMVALUE.equals(handler.get(i + 1).getType())) {
                                i++;
                                paramValue = (String) handler.get(i).getContent();
                            }
                            config.put(paramName, paramValue);
                        } else if (HandlerRef.HANDLER_SOAPROLE.equals(handler.get(i).getType())) {
                            String soaprole = "";
                            soaprole = (String) handler.get(i).getContent();
                            soaproles.add(soaprole);
                        } else if (HandlerRef.HANDLER_PORTNAME.equals(handler.get(i).getType())) {
                            String portName = "";
                            portName = (String) handler.get(i).getContent();
                            portNames.add(portName);
                        }

                    // Set the handlers informations
                    handlerref.setHandlerClass(handlerClass);
                    handlerref.setHeaders((QName []) headers.toArray(new QName[headers.size()]));
                    handlerref.setHandlerConfig(config);

                    if (!portNames.isEmpty()) {
                        Iterator iter = portNames.iterator();
                        while (iter.hasNext())
                            initHandlerChain(new QName((String) iter.next()), handlerRegistry,
                                    handlerref, soaproles);
                    } else {
                        Enumeration e = portComponentRef.elements();
                        while(e.hasMoreElements())
                            initHandlerChain((QName) e.nextElement(), handlerRegistry,
                                    handlerref, soaproles);
                    }
                }
            }

            return proxyInstance;

        }

        return null;

    }

    /**
     * @param port analyzed port
     * @return Returns the endpoint URL of the given Port
     */
    private String getSOAPLocation(Port port) {
        String endpoint = null;
        List extensions = port.getExtensibilityElements();
        for (Iterator i = extensions.iterator(); i.hasNext();) {
            ExtensibilityElement ext = (ExtensibilityElement) i.next();
            if (ext instanceof SOAPAddress) {
                SOAPAddress addr = (SOAPAddress) ext;
                endpoint = addr.getLocationURI();
            }
        }
        return endpoint;
    }


    private void initHandlerChain(QName portName, HandlerRegistry handlerRegistry,
            HandlerInfo handlerref, ArrayList soaprolesToAdd) {
        HandlerChain handlerList = (HandlerChain) handlerRegistry.getHandlerChain(portName);
        handlerList.add(handlerref);
        String[] soaprolesRegistered = handlerList.getRoles();
        String [] soaproles = new String[soaprolesRegistered.length + soaprolesToAdd.size()];
        int i;
        for (i = 0;i < soaprolesRegistered.length; i++)
            soaproles[i] = soaprolesRegistered[i];
        for (int j = 0; j < soaprolesToAdd.size(); j++)
            soaproles[i+j] = (String) soaprolesToAdd.get(j);
        handlerList.setRoles(soaproles);
        handlerRegistry.setHandlerChain(portName, handlerList);
    }


}

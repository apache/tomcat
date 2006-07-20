/*
 *  Copyright 1999-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.jk.common;


import org.apache.jk.core.JkHandler;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.Attribute;
import javax.management.MBeanServerFactory;
import java.io.IOException;

/**
 * Load the HTTP or RMI adapters for MX4J and JMXRI.
 *
 * Add "mx.enabled=true" in jk2.properties to enable it.
 * You could also select http and/or jrmp protocol, 
 * with mx.httpPort, mx.httpHost, mxjrmpPort and mx.jrmpPort.
 * <p />
 * If you run into an error message like
 * "SystemId Unknown; Line #12; Column #81; Cannot add attribute name after
 * child nodes or before an element is produced.  Attribute will be ignored."
 * after setting mx.enabled to true, you probably need a newer version
 * of Xalan.  See the RELEASE-NOTES document section on XML Parsers for
 * more information.
 *
 */
public class JkMX extends JkHandler
{
    MBeanServer mserver;
    private boolean enabled=false;
    private boolean log4jEnabled=true;
    private int httpport=-1;
    private String httphost="localhost";
    private String authmode="none";
    private String authuser=null;
    private String authpassword=null;
    private int jrmpport=-1;
    private String jrmphost="localhost";
    private boolean useXSLTProcessor = true;

    public JkMX() {
    }

    /* -------------------- Public methods -------------------- */

    /** Enable the MX4J adapters (new way)
     */
    public void setEnabled(boolean b) {
        enabled=b;
    }
        
    public boolean getEnabled() {
        return enabled;
    }
        
    /** Enable the Log4j MBean)
     */
    public void setLog4jEnabled(boolean b) {
        log4jEnabled=b;
    }
        
    public boolean getLog4jEnabled() {
        return log4jEnabled;
    }

    /** Enable the MX4J adapters (old way, compatible)
     */
    public void setPort(int i) {
        enabled=(i != -1);
    }
        
    public int getPort() {
        return ((httpport != -1) ? httpport : jrmpport);
    }

    /** Enable the MX4J HTTP internal adapter
     */ 
    public void setHttpPort( int i ) {
        httpport=i;
    }

    public int getHttpPort() {
        return httpport;
    }

    public void setHttpHost(String host ) {
        this.httphost=host;
    }

    public String getHttpHost() {
        return httphost;
    }

    public void setAuthMode(String mode) {
        authmode=mode;
    }

    public String getAuthMode() {
        return authmode;
    }

    public void setAuthUser(String user) {
        authuser=user;
    }

    public String getAuthUser() {
        return authuser;
    }

    public void setAuthPassword(String password) {
        authpassword=password;
    }

    public String getAuthPassword() {
        return authpassword;
    }

    /** Enable the MX4J JRMP internal adapter
     */
    public void setJrmpPort( int i ) {
        jrmpport=i;
    }

    public int getJrmpPort() {
        return jrmpport;
    }

    public void setJrmpHost(String host ) {
        this.jrmphost=host;
    }

    public String getJrmpHost() {
        return jrmphost;
    }

    public boolean getUseXSLTProcessor() {
        return useXSLTProcessor;
    }

    public void setUseXSLTProcessor(boolean uxsltp) {
        useXSLTProcessor = uxsltp;
    }        

    /* ==================== Start/stop ==================== */
    ObjectName httpServerName=null;
    ObjectName jrmpServerName=null;

    /** Initialize the worker. After this call the worker will be
     *  ready to accept new requests.
     */
    public void loadAdapter() throws IOException {
        boolean httpAdapterLoaded = false;
        boolean jrmpAdapterLoaded = false;
        
        if ((httpport != -1) && classExists("mx4j.adaptor.http.HttpAdaptor")) {
            try {
                httpServerName = registerObject("mx4j.adaptor.http.HttpAdaptor",
                                                "Http:name=HttpAdaptor");

                        
                if( httphost!=null )
                    mserver.setAttribute(httpServerName, new Attribute("Host", httphost));
                mserver.setAttribute(httpServerName, new Attribute("Port", new Integer(httpport)));

                if( "none".equals(authmode) || "basic".equals(authmode) || "digest".equals(authmode) )
                    mserver.setAttribute(httpServerName, new Attribute("AuthenticationMethod", authmode));

                if( authuser!=null && authpassword!=null )
                    mserver.invoke(httpServerName, "addAuthorization",
                        new Object[] {
                            authuser,
                            authpassword},
                        new String[] { "java.lang.String", "java.lang.String" });

                if(useXSLTProcessor) {
                    ObjectName processorName = registerObject("mx4j.adaptor.http.XSLTProcessor",
                                                          "Http:name=XSLTProcessor");
                    mserver.setAttribute(httpServerName, new Attribute("ProcessorName", processorName));
                }

                // starts the server
                mserver.invoke(httpServerName, "start", null, null);

                log.info( "Started MX4J console on host " + httphost + " at port " + httpport);
                
                httpAdapterLoaded = true;

            } catch( Throwable t ) {
                httpServerName=null;
                log.error( "Can't load the MX4J http adapter ", t );
            }
        }

        if ((httpport != -1) && (!httpAdapterLoaded) && classExists("mx4j.tools.adaptor.http.HttpAdaptor")) {
            try {
                httpServerName = registerObject("mx4j.tools.adaptor.http.HttpAdaptor",
                                                "Http:name=HttpAdaptor");

                        
                if( httphost!=null )
                    mserver.setAttribute(httpServerName, new Attribute("Host", httphost));
                mserver.setAttribute(httpServerName, new Attribute("Port", new Integer(httpport)));

                if( "none".equals(authmode) || "basic".equals(authmode) || "digest".equals(authmode) )
                    mserver.setAttribute(httpServerName, new Attribute("AuthenticationMethod", authmode));

                if( authuser!=null && authpassword!=null )
                    mserver.invoke(httpServerName, "addAuthorization",
                        new Object[] {
                            authuser,
                            authpassword},
                        new String[] { "java.lang.String", "java.lang.String" });

               if(useXSLTProcessor) {
                    ObjectName processorName = registerObject("mx4j.tools.adaptor.http.XSLTProcessor",
                                                          "Http:name=XSLTProcessor");
                    mserver.setAttribute(httpServerName, new Attribute("ProcessorName", processorName));
		}
                // starts the server
                mserver.invoke(httpServerName, "start", null, null);
                if(log.isInfoEnabled())
                    log.info( "Started MX4J console on host " + httphost + " at port " + httpport);
                
                httpAdapterLoaded = true;

            } catch( Throwable t ) {
                httpServerName=null;
                log.error( "Can't load the MX4J http adapter ", t );
            }
        }

        if ((jrmpport != -1) && classExists("mx4j.tools.naming.NamingService")) {
            try {
                jrmpServerName = registerObject("mx4j.tools.naming.NamingService",
                                                "Naming:name=rmiregistry");
				mserver.setAttribute(jrmpServerName, new Attribute("Port", 
				                                     new Integer(jrmpport)));
                mserver.invoke(jrmpServerName, "start", null, null);
                if(log.isInfoEnabled())
                    log.info( "Creating " + jrmpServerName );

                // Create the JRMP adaptor
                ObjectName adaptor = registerObject("mx4j.adaptor.rmi.jrmp.JRMPAdaptor",
                                                    "Adaptor:protocol=jrmp");


                mserver.setAttribute(adaptor, new Attribute("JNDIName", "jrmp"));

                mserver.invoke( adaptor, "putNamingProperty",
                        new Object[] {
                            javax.naming.Context.INITIAL_CONTEXT_FACTORY,
                            "com.sun.jndi.rmi.registry.RegistryContextFactory"},
                        new String[] { "java.lang.Object", "java.lang.Object" });

                String jrpmurl = "rmi://" + jrmphost + ":" + Integer.toString(jrmpport) ;
                                        
                mserver.invoke( adaptor, "putNamingProperty",
                        new Object[] {
                            javax.naming.Context.PROVIDER_URL,
                            jrpmurl},
                        new String[] { "java.lang.Object", "java.lang.Object" });

                // Registers the JRMP adaptor in JNDI and starts it
                mserver.invoke(adaptor, "start", null, null);
                if(log.isInfoEnabled())
                    log.info( "Creating " + adaptor + " on host " + jrmphost + " at port " + jrmpport);

                jrmpAdapterLoaded = true;

            } catch( Exception ex ) {
                jrmpServerName = null;
                log.error( "MX4j RMI adapter not loaded: " + ex.toString());
            }
        }

        if ((httpport != -1) && (! httpAdapterLoaded) && classExists("com.sun.jdmk.comm.HtmlAdaptorServer")) {
            try {
                httpServerName=registerObject("com.sun.jdmk.comm.HtmlAdaptorServer",
                                              "Adaptor:name=html,port=" + httpport);
                if(log.isInfoEnabled())
                    log.info("Registering the JMX_RI html adapter " + httpServerName + " at port " + httpport);

                mserver.setAttribute(httpServerName,
                                     new Attribute("Port", new Integer(httpport)));

                mserver.invoke(httpServerName, "start", null, null);

                httpAdapterLoaded = true;
            } catch( Throwable t ) {
                httpServerName = null;
                log.error( "Can't load the JMX_RI http adapter " + t.toString()  );
            }
        }

        if ((!httpAdapterLoaded) && (!jrmpAdapterLoaded))
            log.warn( "No adaptors were loaded but mx.enabled was defined.");

    }

    public void destroy() {
        try {
            if(log.isInfoEnabled())
                log.info("Stoping JMX ");

            if( httpServerName!=null ) {
                mserver.invoke(httpServerName, "stop", null, null);
            }
            if( jrmpServerName!=null ) {
                mserver.invoke(jrmpServerName, "stop", null, null);
            }
        } catch( Throwable t ) {
            log.error( "Destroy error" + t );
        }
    }

    public void init() throws IOException {
        try {
            mserver = getMBeanServer();

            if( enabled ) {
                loadAdapter();
            }
            if( log4jEnabled) {
                try {
                    registerObject("org.apache.log4j.jmx.HierarchyDynamicMBean" ,
                                   "log4j:hierarchy=default");
                    if(log.isInfoEnabled())
                         log.info("Registering the JMX hierarchy for Log4J ");
                } catch( Throwable t ) {
                    if(log.isInfoEnabled())
                        log.info("Can't enable log4j mx: ",t);
                }
            }
        } catch( Throwable t ) {
            log.error( "Init error", t );
        }
    }

    public void addHandlerCallback( JkHandler w ) {
    }

    MBeanServer getMBeanServer() {
        MBeanServer server;
        if( MBeanServerFactory.findMBeanServer(null).size() > 0 ) {
            server=(MBeanServer)MBeanServerFactory.findMBeanServer(null).get(0);
        } else {
            server=MBeanServerFactory.createMBeanServer();
        }
        return (server);
    }


    private static boolean classExists(String className) {
        try {
            Thread.currentThread().getContextClassLoader().loadClass(className);
            return true;
        } catch(Throwable e) {
            if (log.isInfoEnabled())
                log.info( "className [" + className + "] does not exist");
            return false;
        }
    }

    private ObjectName registerObject(String className, String oName) 
        throws Exception {
        Class c = Class.forName(className);
        Object o = c.newInstance();
        ObjectName objN = new ObjectName(oName);
        mserver.registerMBean(o, objN);
        return objN;
    }

    private static org.apache.commons.logging.Log log=
        org.apache.commons.logging.LogFactory.getLog( JkMX.class );


}


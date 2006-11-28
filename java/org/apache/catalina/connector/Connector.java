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


package org.apache.catalina.connector;

import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.util.HashMap;

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Service;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.catalina.util.StringManager;
import org.apache.coyote.Adapter;
import org.apache.coyote.ProtocolHandler;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.http.mapper.Mapper;
import org.apache.tomcat.util.modeler.Registry;


/**
 * Implementation of a Coyote connector for Tomcat 5.x.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 * @version $Revision$ $Date$
 */


public class Connector
    implements Lifecycle, MBeanRegistration
{
    private static Log log = LogFactory.getLog(Connector.class);


    /**
     * Alternate flag to enable recycling of facades.
     */
    public static final boolean RECYCLE_FACADES =
        Boolean.valueOf(System.getProperty("org.apache.catalina.connector.RECYCLE_FACADES", "false")).booleanValue();


    // ------------------------------------------------------------ Constructor


    public Connector()
        throws Exception {
        this(null);
    }

    public Connector(String protocol)
        throws Exception {
        setProtocol(protocol);
        // Instantiate protocol handler
        try {
            Class clazz = Class.forName(protocolHandlerClassName);
            this.protocolHandler = (ProtocolHandler) clazz.newInstance();
        } catch (Exception e) {
            log.error
                (sm.getString
                 ("coyoteConnector.protocolHandlerInstantiationFailed", e));
        }
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The <code>Service</code> we are associated with (if any).
     */
    protected Service service = null;


    /**
     * Do we allow TRACE ?
     */
    protected boolean allowTrace = false;


    /**
     * The Container used for processing requests received by this Connector.
     */
    protected Container container = null;


    /**
     * Use "/" as path for session cookies ?
     */
    protected boolean emptySessionPath = false;


    /**
     * The "enable DNS lookups" flag for this Connector.
     */
    protected boolean enableLookups = false;


    /*
     * Is generation of X-Powered-By response header enabled/disabled?
     */
    protected boolean xpoweredBy = false;


    /**
     * Descriptive information about this Connector implementation.
     */
    protected static final String info =
        "org.apache.catalina.connector.Connector/2.1";


    /**
     * The lifecycle event support for this component.
     */
    protected LifecycleSupport lifecycle = new LifecycleSupport(this);


    /**
     * The port number on which we listen for requests.
     */
    protected int port = 0;


    /**
     * The server name to which we should pretend requests to this Connector
     * were directed.  This is useful when operating Tomcat behind a proxy
     * server, so that redirects get constructed accurately.  If not specified,
     * the server name included in the <code>Host</code> header is used.
     */
    protected String proxyName = null;


    /**
     * The server port to which we should pretent requests to this Connector
     * were directed.  This is useful when operating Tomcat behind a proxy
     * server, so that redirects get constructed accurately.  If not specified,
     * the port number specified by the <code>port</code> property is used.
     */
    protected int proxyPort = 0;


    /**
     * The redirect port for non-SSL to SSL redirects.
     */
    protected int redirectPort = 443;


    /**
     * The request scheme that will be set on all requests received
     * through this connector.
     */
    protected String scheme = "http";


    /**
     * The secure connection flag that will be set on all requests received
     * through this connector.
     */
    protected boolean secure = false;


    /**
     * The string manager for this package.
     */
    protected StringManager sm =
        StringManager.getManager(Constants.Package);


    /**
     * Maximum size of a POST which will be automatically parsed by the
     * container. 2MB by default.
     */
    protected int maxPostSize = 2 * 1024 * 1024;


    /**
     * Maximum size of a POST which will be saved by the container
     * during authentication. 4kB by default
     */
    protected int maxSavePostSize = 4 * 1024;


    /**
     * Has this component been initialized yet?
     */
    protected boolean initialized = false;


    /**
     * Has this component been started yet?
     */
    protected boolean started = false;


    /**
     * The shutdown signal to our background thread
     */
    protected boolean stopped = false;

    /**
     * Flag to use IP-based virtual hosting.
     */
    protected boolean useIPVHosts = false;

    /**
     * The background thread.
     */
    protected Thread thread = null;


    /**
     * Coyote Protocol handler class name.
     * Defaults to the Coyote HTTP/1.1 protocolHandler.
     */
    protected String protocolHandlerClassName =
        "org.apache.coyote.http11.Http11Protocol";


    /**
     * Coyote protocol handler.
     */
    protected ProtocolHandler protocolHandler = null;


    /**
     * Coyote adapter.
     */
    protected Adapter adapter = null;


     /**
      * Mapper.
      */
     protected Mapper mapper = new Mapper();


     /**
      * Mapper listener.
      */
     protected MapperListener mapperListener = new MapperListener(mapper);


     /**
      * URI encoding.
      */
     protected String URIEncoding = null;


     /**
      * URI encoding as body.
      */
     protected boolean useBodyEncodingForURI = false;


     protected static HashMap replacements = new HashMap();
     static {
         replacements.put("acceptCount", "backlog");
         replacements.put("connectionLinger", "soLinger");
         replacements.put("connectionTimeout", "soTimeout");
         replacements.put("connectionUploadTimeout", "timeout");
         replacements.put("clientAuth", "clientauth");
         replacements.put("keystoreFile", "keystore");
         replacements.put("randomFile", "randomfile");
         replacements.put("rootFile", "rootfile");
         replacements.put("keystorePass", "keypass");
         replacements.put("keystoreType", "keytype");
         replacements.put("sslProtocol", "protocol");
         replacements.put("sslProtocols", "protocols");
     }


    // ------------------------------------------------------------- Properties


    /**
     * Return a configured property.
     */
    public Object getProperty(String name) {
        String repl = name;
        if (replacements.get(name) != null) {
            repl = (String) replacements.get(name);
        }
        return IntrospectionUtils.getProperty(protocolHandler, repl);
    }


    /**
     * Set a configured property.
     */
    public void setProperty(String name, String value) {
        String repl = name;
        if (replacements.get(name) != null) {
            repl = (String) replacements.get(name);
        }
        IntrospectionUtils.setProperty(protocolHandler, repl, value);
    }


    /**
     * Return a configured property.
     */
    public Object getAttribute(String name) {
        return getProperty(name);
    }


    /**
     * Set a configured property.
     */
    public void setAttribute(String name, Object value) {
        setProperty(name, String.valueOf(value));
    }


    /**
     * remove a configured property.
     */
    public void removeProperty(String name) {
        // FIXME !
        //protocolHandler.removeAttribute(name);
    }


    /**
     * Return the <code>Service</code> with which we are associated (if any).
     */
    public Service getService() {

        return (this.service);

    }


    /**
     * Set the <code>Service</code> with which we are associated (if any).
     *
     * @param service The service that owns this Engine
     */
    public void setService(Service service) {

        this.service = service;
        // FIXME: setProperty("service", service);

    }


    /**
     * True if the TRACE method is allowed.  Default value is "false".
     */
    public boolean getAllowTrace() {

        return (this.allowTrace);

    }


    /**
     * Set the allowTrace flag, to disable or enable the TRACE HTTP method.
     *
     * @param allowTrace The new allowTrace flag
     */
    public void setAllowTrace(boolean allowTrace) {

        this.allowTrace = allowTrace;
        setProperty("allowTrace", String.valueOf(allowTrace));

    }

    /**
     * Is this connector available for processing requests?
     */
    public boolean isAvailable() {

        return (started);

    }


    /**
     * Return the input buffer size for this Connector.
     *
     * @deprecated
     */
    public int getBufferSize() {
        return 2048;
    }

    /**
     * Set the input buffer size for this Connector.
     *
     * @param bufferSize The new input buffer size.
     * @deprecated
     */
    public void setBufferSize(int bufferSize) {
    }


    /**
     * Return the Container used for processing requests received by this
     * Connector.
     */
    public Container getContainer() {
        if( container==null ) {
            // Lazy - maybe it was added later
            findContainer();
        }
        return (container);

    }


    /**
     * Set the Container used for processing requests received by this
     * Connector.
     *
     * @param container The new Container to use
     */
    public void setContainer(Container container) {

        this.container = container;

    }


    /**
     * Return the "empty session path" flag.
     */
    public boolean getEmptySessionPath() {

        return (this.emptySessionPath);

    }


    /**
     * Set the "empty session path" flag.
     *
     * @param emptySessionPath The new "empty session path" flag value
     */
    public void setEmptySessionPath(boolean emptySessionPath) {

        this.emptySessionPath = emptySessionPath;
        setProperty("emptySessionPath", String.valueOf(emptySessionPath));

    }


    /**
     * Return the "enable DNS lookups" flag.
     */
    public boolean getEnableLookups() {

        return (this.enableLookups);

    }


    /**
     * Set the "enable DNS lookups" flag.
     *
     * @param enableLookups The new "enable DNS lookups" flag value
     */
    public void setEnableLookups(boolean enableLookups) {

        this.enableLookups = enableLookups;
        setProperty("enableLookups", String.valueOf(enableLookups));

    }


    /**
     * Return descriptive information about this Connector implementation.
     */
    public String getInfo() {

        return (info);

    }


     /**
      * Return the mapper.
      */
     public Mapper getMapper() {

         return (mapper);

     }


    /**
     * Return the maximum size of a POST which will be automatically
     * parsed by the container.
     */
    public int getMaxPostSize() {

        return (maxPostSize);

    }


    /**
     * Set the maximum size of a POST which will be automatically
     * parsed by the container.
     *
     * @param maxPostSize The new maximum size in bytes of a POST which will
     * be automatically parsed by the container
     */
    public void setMaxPostSize(int maxPostSize) {

        this.maxPostSize = maxPostSize;
    }


    /**
     * Return the maximum size of a POST which will be saved by the container
     * during authentication.
     */
    public int getMaxSavePostSize() {

        return (maxSavePostSize);

    }


    /**
     * Set the maximum size of a POST which will be saved by the container
     * during authentication.
     *
     * @param maxSavePostSize The new maximum size in bytes of a POST which will
     * be saved by the container during authentication.
     */
    public void setMaxSavePostSize(int maxSavePostSize) {

        this.maxSavePostSize = maxSavePostSize;
        setProperty("maxSavePostSize", String.valueOf(maxSavePostSize));
    }


    /**
     * Return the port number on which we listen for requests.
     */
    public int getPort() {

        return (this.port);

    }


    /**
     * Set the port number on which we listen for requests.
     *
     * @param port The new port number
     */
    public void setPort(int port) {

        this.port = port;
        setProperty("port", String.valueOf(port));

    }


    /**
     * Return the Coyote protocol handler in use.
     */
    public String getProtocol() {

        if ("org.apache.coyote.http11.Http11Protocol".equals
            (getProtocolHandlerClassName())
            || "org.apache.coyote.http11.Http11AprProtocol".equals
            (getProtocolHandlerClassName())) {
            return "HTTP/1.1";
        } else if ("org.apache.jk.server.JkCoyoteHandler".equals
                   (getProtocolHandlerClassName())
                   || "org.apache.coyote.ajp.AjpAprProtocol".equals
                   (getProtocolHandlerClassName())) {
            return "AJP/1.3";
        }
        return getProtocolHandlerClassName();

    }
    
    // ---------------------------------------------- APR Version Constants

    private static final int TCN_REQUIRED_MAJOR = 1;
    private static final int TCN_REQUIRED_MINOR = 1;
    private static final int TCN_REQUIRED_PATCH = 3;
    private static boolean aprInitialized = false;

    // APR init support
    private static synchronized void initializeAPR()
    {
        if (aprInitialized) {
            return;
        }
        int major = 0;
        int minor = 0;
        int patch = 0;
        try {
            String methodName = "initialize";
            Class paramTypes[] = new Class[1];
            paramTypes[0] = String.class;
            Object paramValues[] = new Object[1];
            paramValues[0] = null;
            Class clazz = Class.forName("org.apache.tomcat.jni.Library");
            Method method = clazz.getMethod(methodName, paramTypes);
            method.invoke(null, paramValues);
            major = clazz.getField("TCN_MAJOR_VERSION").getInt(null);
            minor = clazz.getField("TCN_MINOR_VERSION").getInt(null);
            patch = clazz.getField("TCN_PATCH_VERSION").getInt(null);
        } catch (Throwable t) {
            return;
        }
        if ((major != TCN_REQUIRED_MAJOR) ||
            (minor != TCN_REQUIRED_MINOR) ||
            (patch <  TCN_REQUIRED_PATCH)) {
            try {
                // Terminate the APR in case the version
                // is below required.
                String methodName = "terminate";
                Method method = Class.forName("org.apache.tomcat.jni.Library")
                                    .getMethod(methodName, (Class [])null);
                method.invoke(null, (Object []) null);
            } catch (Throwable t) {
                // Ignore
            }
            return;
        }
        aprInitialized = true;
    }

    /**
     * Set the Coyote protocol which will be used by the connector.
     *
     * @param protocol The Coyote protocol name
     */
    public void setProtocol(String protocol) {

        // Test APR support
        initializeAPR();

        if (aprInitialized) {
            if ("HTTP/1.1".equals(protocol)) {
                setProtocolHandlerClassName
                    ("org.apache.coyote.http11.Http11AprProtocol");
            } else if ("AJP/1.3".equals(protocol)) {
                setProtocolHandlerClassName
                    ("org.apache.coyote.ajp.AjpAprProtocol");
            } else if (protocol != null) {
                setProtocolHandlerClassName(protocol);
            } else {
                setProtocolHandlerClassName
                    ("org.apache.coyote.http11.Http11AprProtocol");
            }
        } else {
            if ("HTTP/1.1".equals(protocol)) {
                setProtocolHandlerClassName
                    ("org.apache.coyote.http11.Http11Protocol");
            } else if ("AJP/1.3".equals(protocol)) {
                setProtocolHandlerClassName
                    ("org.apache.jk.server.JkCoyoteHandler");
            } else if (protocol != null) {
                setProtocolHandlerClassName(protocol);
            }
        }

    }


    /**
     * Return the class name of the Coyote protocol handler in use.
     */
    public String getProtocolHandlerClassName() {

        return (this.protocolHandlerClassName);

    }


    /**
     * Set the class name of the Coyote protocol handler which will be used
     * by the connector.
     *
     * @param protocolHandlerClassName The new class name
     */
    public void setProtocolHandlerClassName(String protocolHandlerClassName) {

        this.protocolHandlerClassName = protocolHandlerClassName;

    }


    /**
     * Return the protocol handler associated with the connector.
     */
    public ProtocolHandler getProtocolHandler() {

        return (this.protocolHandler);

    }


    /**
     * Return the proxy server name for this Connector.
     */
    public String getProxyName() {

        return (this.proxyName);

    }


    /**
     * Set the proxy server name for this Connector.
     *
     * @param proxyName The new proxy server name
     */
    public void setProxyName(String proxyName) {

        if(proxyName != null && proxyName.length() > 0) {
            this.proxyName = proxyName;
            setProperty("proxyName", proxyName);
        } else {
            this.proxyName = null;
            removeProperty("proxyName");
        }

    }


    /**
     * Return the proxy server port for this Connector.
     */
    public int getProxyPort() {

        return (this.proxyPort);

    }


    /**
     * Set the proxy server port for this Connector.
     *
     * @param proxyPort The new proxy server port
     */
    public void setProxyPort(int proxyPort) {

        this.proxyPort = proxyPort;
        setProperty("proxyPort", String.valueOf(proxyPort));

    }


    /**
     * Return the port number to which a request should be redirected if
     * it comes in on a non-SSL port and is subject to a security constraint
     * with a transport guarantee that requires SSL.
     */
    public int getRedirectPort() {

        return (this.redirectPort);

    }


    /**
     * Set the redirect port number.
     *
     * @param redirectPort The redirect port number (non-SSL to SSL)
     */
    public void setRedirectPort(int redirectPort) {

        this.redirectPort = redirectPort;
        setProperty("redirectPort", String.valueOf(redirectPort));

    }


    /**
     * Return the scheme that will be assigned to requests received
     * through this connector.  Default value is "http".
     */
    public String getScheme() {

        return (this.scheme);

    }


    /**
     * Set the scheme that will be assigned to requests received through
     * this connector.
     *
     * @param scheme The new scheme
     */
    public void setScheme(String scheme) {

        this.scheme = scheme;

    }


    /**
     * Return the secure connection flag that will be assigned to requests
     * received through this connector.  Default value is "false".
     */
    public boolean getSecure() {

        return (this.secure);

    }


    /**
     * Set the secure connection flag that will be assigned to requests
     * received through this connector.
     *
     * @param secure The new secure connection flag
     */
    public void setSecure(boolean secure) {

        this.secure = secure;
        setProperty("secure", Boolean.toString(secure));
    }

     /**
      * Return the character encoding to be used for the URI.
      */
     public String getURIEncoding() {

         return (this.URIEncoding);

     }


     /**
      * Set the URI encoding to be used for the URI.
      *
      * @param URIEncoding The new URI character encoding.
      */
     public void setURIEncoding(String URIEncoding) {

         this.URIEncoding = URIEncoding;
         setProperty("uRIEncoding", URIEncoding);

     }


     /**
      * Return the true if the entity body encoding should be used for the URI.
      */
     public boolean getUseBodyEncodingForURI() {

         return (this.useBodyEncodingForURI);

     }


     /**
      * Set if the entity body encoding should be used for the URI.
      *
      * @param useBodyEncodingForURI The new value for the flag.
      */
     public void setUseBodyEncodingForURI(boolean useBodyEncodingForURI) {

         this.useBodyEncodingForURI = useBodyEncodingForURI;
         setProperty
             ("useBodyEncodingForURI", String.valueOf(useBodyEncodingForURI));

     }


    /**
     * Indicates whether the generation of an X-Powered-By response header for
     * servlet-generated responses is enabled or disabled for this Connector.
     *
     * @return true if generation of X-Powered-By response header is enabled,
     * false otherwise
     */
    public boolean getXpoweredBy() {
        return xpoweredBy;
    }


    /**
     * Enables or disables the generation of an X-Powered-By header (with value
     * Servlet/2.4) for all servlet-generated responses returned by this
     * Connector.
     *
     * @param xpoweredBy true if generation of X-Powered-By response header is
     * to be enabled, false otherwise
     */
    public void setXpoweredBy(boolean xpoweredBy) {
        this.xpoweredBy = xpoweredBy;
        setProperty("xpoweredBy", String.valueOf(xpoweredBy));
    }

    /**
     * Enable the use of IP-based virtual hosting.
     *
     * @param useIPVHosts <code>true</code> if Hosts are identified by IP,
     *                    <code>false/code> if Hosts are identified by name.
     */
    public void setUseIPVHosts(boolean useIPVHosts) {
        this.useIPVHosts = useIPVHosts;
        setProperty("useIPVHosts", String.valueOf(useIPVHosts));
    }

    /**
     * Test if IP-based virtual hosting is enabled.
     */
    public boolean getUseIPVHosts() {
        return useIPVHosts;
    }

    // --------------------------------------------------------- Public Methods


    /**
     * Create (or allocate) and return a Request object suitable for
     * specifying the contents of a Request to the responsible Container.
     */
    public Request createRequest() {

        Request request = new Request();
        request.setConnector(this);
        return (request);

    }


    /**
     * Create (or allocate) and return a Response object suitable for
     * receiving the contents of a Response from the responsible Container.
     */
    public Response createResponse() {

        Response response = new Response();
        response.setConnector(this);
        return (response);

    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * Add a lifecycle event listener to this component.
     *
     * @param listener The listener to add
     */
    public void addLifecycleListener(LifecycleListener listener) {

        lifecycle.addLifecycleListener(listener);

    }


    /**
     * Get the lifecycle listeners associated with this lifecycle. If this
     * Lifecycle has no listeners registered, a zero-length array is returned.
     */
    public LifecycleListener[] findLifecycleListeners() {

        return lifecycle.findLifecycleListeners();

    }


    /**
     * Remove a lifecycle event listener from this component.
     *
     * @param listener The listener to add
     */
    public void removeLifecycleListener(LifecycleListener listener) {

        lifecycle.removeLifecycleListener(listener);

    }


    protected ObjectName createObjectName(String domain, String type)
            throws MalformedObjectNameException {
        String encodedAddr = null;
        if (getProperty("address") != null) {
            encodedAddr = URLEncoder.encode(getProperty("address").toString());
        }
        String addSuffix = (getProperty("address") == null) ? "" : ",address="
                + encodedAddr;
        ObjectName _oname = new ObjectName(domain + ":type=" + type + ",port="
                + getPort() + addSuffix);
        return _oname;
    }

    /**
     * Initialize this connector (create ServerSocket here!)
     */
    public void initialize()
        throws LifecycleException
    {
        if (initialized) {
            if(log.isInfoEnabled())
                log.info(sm.getString("coyoteConnector.alreadyInitialized"));
           return;
        }

        this.initialized = true;

        if( oname == null && (container instanceof StandardEngine)) {
            try {
                // we are loaded directly, via API - and no name was given to us
                StandardEngine cb=(StandardEngine)container;
                oname = createObjectName(cb.getName(), "Connector");
                Registry.getRegistry(null, null)
                    .registerComponent(this, oname, null);
                controller=oname;
            } catch (Exception e) {
                log.error( "Error registering connector ", e);
            }
            if(log.isDebugEnabled())
                log.debug("Creating name for connector " + oname);
        }

        // Initializa adapter
        adapter = new CoyoteAdapter(this);
        protocolHandler.setAdapter(adapter);

        IntrospectionUtils.setProperty(protocolHandler, "jkHome",
                                       System.getProperty("catalina.base"));

        try {
            protocolHandler.init();
        } catch (Exception e) {
            throw new LifecycleException
                (sm.getString
                 ("coyoteConnector.protocolHandlerInitializationFailed", e));
        }
    }


    /**
     * Pause the connector.
     */
    public void pause()
        throws LifecycleException {
        try {
            protocolHandler.pause();
        } catch (Exception e) {
            log.error(sm.getString
                      ("coyoteConnector.protocolHandlerPauseFailed"), e);
        }
    }


    /**
     * Pause the connector.
     */
    public void resume()
        throws LifecycleException {
        try {
            protocolHandler.resume();
        } catch (Exception e) {
            log.error(sm.getString
                      ("coyoteConnector.protocolHandlerResumeFailed"), e);
        }
    }


    /**
     * Begin processing requests via this Connector.
     *
     * @exception LifecycleException if a fatal startup error occurs
     */
    public void start() throws LifecycleException {
        if( !initialized )
            initialize();

        // Validate and update our current state
        if (started ) {
            if(log.isInfoEnabled())
                log.info(sm.getString("coyoteConnector.alreadyStarted"));
            return;
        }
        lifecycle.fireLifecycleEvent(START_EVENT, null);
        started = true;

        // We can't register earlier - the JMX registration of this happens
        // in Server.start callback
        if ( this.oname != null ) {
            // We are registred - register the adapter as well.
            try {
                Registry.getRegistry(null, null).registerComponent
                    (protocolHandler, createObjectName(this.domain,"ProtocolHandler"), null);
            } catch (Exception ex) {
                log.error(sm.getString
                          ("coyoteConnector.protocolRegistrationFailed"), ex);
            }
        } else {
            if(log.isInfoEnabled())
                log.info(sm.getString
                     ("coyoteConnector.cannotRegisterProtocol"));
        }

        try {
            protocolHandler.start();
        } catch (Exception e) {
            String errPrefix = "";
            if(this.service != null) {
                errPrefix += "service.getName(): \"" + this.service.getName() + "\"; ";
            }

            throw new LifecycleException
                (errPrefix + " " + sm.getString
                 ("coyoteConnector.protocolHandlerStartFailed", e));
        }

        if( this.domain != null ) {
            mapperListener.setDomain( domain );
            //mapperListener.setEngine( service.getContainer().getName() );
            mapperListener.init();
            try {
                ObjectName mapperOname = createObjectName(this.domain,"Mapper");
                if (log.isDebugEnabled())
                    log.debug(sm.getString(
                            "coyoteConnector.MapperRegistration", mapperOname));
                Registry.getRegistry(null, null).registerComponent
                    (mapper, mapperOname, "Mapper");
            } catch (Exception ex) {
                log.error(sm.getString
                        ("coyoteConnector.protocolRegistrationFailed"), ex);
            }
        }
    }


    /**
     * Terminate processing requests via this Connector.
     *
     * @exception LifecycleException if a fatal shutdown error occurs
     */
    public void stop() throws LifecycleException {

        // Validate and update our current state
        if (!started) {
            log.error(sm.getString("coyoteConnector.notStarted"));
            return;

        }
        lifecycle.fireLifecycleEvent(STOP_EVENT, null);
        started = false;

        try {
            mapperListener.destroy();
            Registry.getRegistry(null, null).unregisterComponent
                (createObjectName(this.domain,"Mapper"));
            Registry.getRegistry(null, null).unregisterComponent
                (createObjectName(this.domain,"ProtocolHandler"));
        } catch (MalformedObjectNameException e) {
            log.error( sm.getString
                    ("coyoteConnector.protocolUnregistrationFailed"), e);
        }
        try {
            protocolHandler.destroy();
        } catch (Exception e) {
            throw new LifecycleException
                (sm.getString
                 ("coyoteConnector.protocolHandlerDestroyFailed", e));
        }

    }


    // -------------------- JMX registration  --------------------
    protected String domain;
    protected ObjectName oname;
    protected MBeanServer mserver;
    ObjectName controller;

    public ObjectName getController() {
        return controller;
    }

    public void setController(ObjectName controller) {
        this.controller = controller;
    }

    public ObjectName getObjectName() {
        return oname;
    }

    public String getDomain() {
        return domain;
    }

    public ObjectName preRegister(MBeanServer server,
                                  ObjectName name) throws Exception {
        oname=name;
        mserver=server;
        domain=name.getDomain();
        return name;
    }

    public void postRegister(Boolean registrationDone) {
    }

    public void preDeregister() throws Exception {
    }

    public void postDeregister() {
        try {
            if( started ) {
                stop();
            }
        } catch( Throwable t ) {
            log.error( "Unregistering - can't stop", t);
        }
    }

    protected void findContainer() {
        try {
            // Register to the service
            ObjectName parentName=new ObjectName( domain + ":" +
                    "type=Service");

            if(log.isDebugEnabled())
                log.debug("Adding to " + parentName );
            if( mserver.isRegistered(parentName )) {
                mserver.invoke(parentName, "addConnector", new Object[] { this },
                        new String[] {"org.apache.catalina.connector.Connector"});
                // As a side effect we'll get the container field set
                // Also initialize will be called
                //return;
            }
            // XXX Go directly to the Engine
            // initialize(); - is called by addConnector
            ObjectName engName=new ObjectName( domain + ":" + "type=Engine");
            if( mserver.isRegistered(engName )) {
                Object obj=mserver.getAttribute(engName, "managedResource");
                if(log.isDebugEnabled())
                      log.debug("Found engine " + obj + " " + obj.getClass());
                container=(Container)obj;

                // Internal initialize - we now have the Engine
                initialize();

                if(log.isDebugEnabled())
                    log.debug("Initialized");
                // As a side effect we'll get the container field set
                // Also initialize will be called
                return;
            }
        } catch( Exception ex ) {
            log.error( "Error finding container " + ex);
        }
    }

    public void init() throws Exception {

        if( this.getService() != null ) {
            if(log.isDebugEnabled())
                 log.debug( "Already configured" );
            return;
        }
        if( container==null ) {
            findContainer();
        }
    }

    public void destroy() throws Exception {
        if( oname!=null && controller==oname ) {
            if(log.isDebugEnabled())
                 log.debug("Unregister itself " + oname );
            Registry.getRegistry(null, null).unregisterComponent(oname);
        }
        if( getService() == null)
            return;
        getService().removeConnector(this);
    }

}

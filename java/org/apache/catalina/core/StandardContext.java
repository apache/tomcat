/*
 * Copyright 1999,2004-2005 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.catalina.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Stack;
import java.util.TreeMap;

import javax.management.AttributeNotFoundException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletRequestListener;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionListener;

import org.apache.catalina.Container;
import org.apache.catalina.ContainerListener;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.InstanceListener;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Loader;
import org.apache.catalina.Manager;
import org.apache.catalina.Wrapper;
import org.apache.catalina.deploy.ApplicationParameter;
import org.apache.catalina.deploy.ErrorPage;
import org.apache.catalina.deploy.FilterDef;
import org.apache.catalina.deploy.FilterMap;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.deploy.MessageDestination;
import org.apache.catalina.deploy.MessageDestinationRef;
import org.apache.catalina.deploy.NamingResources;
import org.apache.catalina.deploy.SecurityCollection;
import org.apache.catalina.deploy.SecurityConstraint;
import org.apache.catalina.loader.WebappLoader;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.TldConfig;
import org.apache.catalina.util.AnnotationProcessor;
import org.apache.catalina.util.CharsetMapper;
import org.apache.catalina.util.ExtensionValidator;
import org.apache.catalina.util.RequestUtil;
import org.apache.catalina.util.URLEncoder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.naming.ContextBindings;
import org.apache.naming.resources.BaseDirContext;
import org.apache.naming.resources.DirContextURLStreamHandler;
import org.apache.naming.resources.FileDirContext;
import org.apache.naming.resources.ProxyDirContext;
import org.apache.naming.resources.WARDirContext;
import org.apache.tomcat.util.modeler.Registry;

/**
 * Standard implementation of the <b>Context</b> interface.  Each
 * child container must be a Wrapper implementation to process the
 * requests directed to a particular servlet.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 * @version $Revision: 386331 $ $Date: 2006-03-16 15:03:12 +0100 (jeu., 16 mars 2006) $
 */

public class StandardContext
    extends ContainerBase
    implements Context, Serializable, NotificationEmitter
{
    private static transient Log log = LogFactory.getLog(StandardContext.class);


    // ----------------------------------------------------------- Constructors


    /**
     * Create a new StandardContext component with the default basic Valve.
     */
    public StandardContext() {

        super();
        pipeline.setBasic(new StandardContextValve());
        broadcaster = new NotificationBroadcasterSupport();

    }


    // ----------------------------------------------------- Class Variables


    /**
     * The descriptive information string for this implementation.
     */
    private static final String info =
        "org.apache.catalina.core.StandardContext/1.0";


    /**
     * Array containing the safe characters set.
     */
    protected static URLEncoder urlEncoder;


    /**
     * GMT timezone - all HTTP dates are on GMT
     */
    static {
        urlEncoder = new URLEncoder();
        urlEncoder.addSafeCharacter('~');
        urlEncoder.addSafeCharacter('-');
        urlEncoder.addSafeCharacter('_');
        urlEncoder.addSafeCharacter('.');
        urlEncoder.addSafeCharacter('*');
        urlEncoder.addSafeCharacter('/');
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The alternate deployment descriptor name.
     */
    private String altDDName = null;


    /**
     * Associated host name.
     */
    private String hostName;


    /**
     * The antiJARLocking flag for this Context.
     */
    private boolean antiJARLocking = false;

    
    /**
     * The antiResourceLocking flag for this Context.
     */
    private boolean antiResourceLocking = false;

    
    /**
     * The set of application listener class names configured for this
     * application, in the order they were encountered in the web.xml file.
     */
    private String applicationListeners[] = new String[0];


    /**
     * The set of instantiated application event listener objects</code>.
     */
    private transient Object applicationEventListenersObjects[] = 
        new Object[0];


    /**
     * The set of instantiated application lifecycle listener objects</code>.
     */
    private transient Object applicationLifecycleListenersObjects[] = 
        new Object[0];


    /**
     * The set of application parameters defined for this application.
     */
    private ApplicationParameter applicationParameters[] =
        new ApplicationParameter[0];


    /**
     * The application available flag for this Context.
     */
    private boolean available = false;
    
    /**
     * The broadcaster that sends j2ee notifications. 
     */
    private NotificationBroadcasterSupport broadcaster = null;
    
    /**
     * The Locale to character set mapper for this application.
     */
    private transient CharsetMapper charsetMapper = null;


    /**
     * The Java class name of the CharsetMapper class to be created.
     */
    private String charsetMapperClass =
      "org.apache.catalina.util.CharsetMapper";


    /**
     * The path to a file to save this Context information.
     */
    private String configFile = null;


    /**
     * The "correctly configured" flag for this Context.
     */
    private boolean configured = false;


    /**
     * The security constraints for this web application.
     */
    private SecurityConstraint constraints[] = new SecurityConstraint[0];


    /**
     * The ServletContext implementation associated with this Context.
     */
    protected transient ApplicationContext context = null;


    /**
     * Compiler classpath to use.
     */
    private String compilerClasspath = null;


    /**
     * Should we attempt to use cookies for session id communication?
     */
    private boolean cookies = true;


    /**
     * Should we allow the <code>ServletContext.getContext()</code> method
     * to access the context of other web applications in this server?
     */
    private boolean crossContext = false;

    
    /**
     * Encoded path.
     */
    private String encodedPath = null;
    

    /**
     * The "follow standard delegation model" flag that will be used to
     * configure our ClassLoader.
     */
    private boolean delegate = false;


    /**
     * The display name of this web application.
     */
    private String displayName = null;


    /** 
     * Override the default context xml location.
     */
    private String defaultContextXml;


    /** 
     * Override the default web xml location.
     */
    private String defaultWebXml;


    /**
     * The distributable flag for this web application.
     */
    private boolean distributable = false;


    /**
     * The document root for this web application.
     */
    private String docBase = null;


    /**
     * The exception pages for this web application, keyed by fully qualified
     * class name of the Java exception.
     */
    private HashMap exceptionPages = new HashMap();


    /**
     * The set of filter configurations (and associated filter instances) we
     * have initialized, keyed by filter name.
     */
    private HashMap filterConfigs = new HashMap();


    /**
     * The set of filter definitions for this application, keyed by
     * filter name.
     */
    private HashMap filterDefs = new HashMap();


    /**
     * The set of filter mappings for this application, in the order
     * they were defined in the deployment descriptor.
     */
    private FilterMap filterMaps[] = new FilterMap[0];


    /**
     * Ignore annotations.
     */
    private boolean ignoreAnnotations = false;


    /**
     * The set of classnames of InstanceListeners that will be added
     * to each newly created Wrapper by <code>createWrapper()</code>.
     */
    private String instanceListeners[] = new String[0];


    /**
     * The login configuration descriptor for this web application.
     */
    private LoginConfig loginConfig = null;


    /**
     * The mapper associated with this context.
     */
    private org.apache.tomcat.util.http.mapper.Mapper mapper = 
        new org.apache.tomcat.util.http.mapper.Mapper();


    /**
     * The naming context listener for this web application.
     */
    private transient NamingContextListener namingContextListener = null;


    /**
     * The naming resources for this web application.
     */
    private NamingResources namingResources = null;


    /**
     * The message destinations for this web application.
     */
    private HashMap messageDestinations = new HashMap();


    /**
     * The MIME mappings for this web application, keyed by extension.
     */
    private HashMap mimeMappings = new HashMap();


     /**
      * Special case: error page for status 200.
      */
     private ErrorPage okErrorPage = null;


    /**
     * The context initialization parameters for this web application,
     * keyed by name.
     */
    private HashMap parameters = new HashMap();


    /**
     * The request processing pause flag (while reloading occurs)
     */
    private boolean paused = false;


    /**
     * The public identifier of the DTD for the web application deployment
     * descriptor version we are currently parsing.  This is used to support
     * relaxed validation rules when processing version 2.2 web.xml files.
     */
    private String publicId = null;


    /**
     * The reloadable flag for this web application.
     */
    private boolean reloadable = false;


    /**
     * Unpack WAR property.
     */
    private boolean unpackWAR = true;


    /**
     * The DefaultContext override flag for this web application.
     */
    private boolean override = false;


    /**
     * The original document root for this web application.
     */
    private String originalDocBase = null;
    
    
    /**
     * The privileged flag for this web application.
     */
    private boolean privileged = false;


    /**
     * Should the next call to <code>addWelcomeFile()</code> cause replacement
     * of any existing welcome files?  This will be set before processing the
     * web application's deployment descriptor, so that application specified
     * choices <strong>replace</strong>, rather than append to, those defined
     * in the global descriptor.
     */
    private boolean replaceWelcomeFiles = false;


    /**
     * The security role mappings for this application, keyed by role
     * name (as used within the application).
     */
    private HashMap roleMappings = new HashMap();


    /**
     * The security roles for this application, keyed by role name.
     */
    private String securityRoles[] = new String[0];


    /**
     * The servlet mappings for this web application, keyed by
     * matching pattern.
     */
    private HashMap servletMappings = new HashMap();


    /**
     * The session timeout (in minutes) for this web application.
     */
    private int sessionTimeout = 30;

    /**
     * The notification sequence number.
     */
    private long sequenceNumber = 0;
    
    /**
     * The status code error pages for this web application, keyed by
     * HTTP status code (as an Integer).
     */
    private HashMap statusPages = new HashMap();


    /**
     * Set flag to true to cause the system.out and system.err to be redirected
     * to the logger when executing a servlet.
     */
    private boolean swallowOutput = false;


    /**
     * The JSP tag libraries for this web application, keyed by URI
     */
    private HashMap taglibs = new HashMap();


    /**
     * Amount of ms that the container will wait for servlets to unload.
     */
    private long unloadDelay = 2000;


    /**
     * The watched resources for this application.
     */
    private String watchedResources[] = new String[0];


    /**
     * The welcome files for this application.
     */
    private String welcomeFiles[] = new String[0];


    /**
     * The set of classnames of LifecycleListeners that will be added
     * to each newly created Wrapper by <code>createWrapper()</code>.
     */
    private String wrapperLifecycles[] = new String[0];


    /**
     * The set of classnames of ContainerListeners that will be added
     * to each newly created Wrapper by <code>createWrapper()</code>.
     */
    private String wrapperListeners[] = new String[0];


    /**
     * The pathname to the work directory for this context (relative to
     * the server's home if not absolute).
     */
    private String workDir = null;


    /**
     * Java class name of the Wrapper class implementation we use.
     */
    private String wrapperClassName = StandardWrapper.class.getName();
    private Class wrapperClass = null;


    /**
     * JNDI use flag.
     */
    private boolean useNaming = true;


    /**
     * Filesystem based flag.
     */
    private boolean filesystemBased = false;


    /**
     * Name of the associated naming context.
     */
    private String namingContextName = null;


    /**
     * Caching allowed flag.
     */
    private boolean cachingAllowed = true;


    /**
     * Case sensitivity.
     */
    protected boolean caseSensitive = true;


    /**
     * Allow linking.
     */
    protected boolean allowLinking = false;


    /**
     * Cache max size in KB.
     */
    protected int cacheMaxSize = 10240; // 10 MB


    /**
     * Cache TTL in ms.
     */
    protected int cacheTTL = 5000;


    private boolean lazy=true;

    /**
     * Non proxied resources.
     */
    private transient DirContext webappResources = null;

    private long startupTime;
    private long startTime;
    private long tldScanTime;

    /** 
     * Name of the engine. If null, the domain is used.
     */ 
    private String engineName = null;
    private String j2EEApplication="none";
    private String j2EEServer="none";


    /**
     * Attribute value used to turn on/off XML validation
     */
     private boolean webXmlValidation = false;


    /**
     * Attribute value used to turn on/off XML namespace validation
     */
     private boolean webXmlNamespaceAware = false;

    /**
     * Attribute value used to turn on/off TLD processing
     */
    private boolean processTlds = true;

    /**
     * Attribute value used to turn on/off XML validation
     */
     private boolean tldValidation = false;


    /**
     * Attribute value used to turn on/off TLD XML namespace validation
     */
     private boolean tldNamespaceAware = false;


    /**
     * Should we save the configuration.
     */
    private boolean saveConfig = true;


    // ----------------------------------------------------- Context Properties


    public String getEncodedPath() {
        return encodedPath;
    }


    public void setName( String name ) {
        super.setName( name );
        encodedPath = urlEncoder.encode(name);
    }


    /**
     * Is caching allowed ?
     */
    public boolean isCachingAllowed() {
        return cachingAllowed;
    }


    /**
     * Set caching allowed flag.
     */
    public void setCachingAllowed(boolean cachingAllowed) {
        this.cachingAllowed = cachingAllowed;
    }


    /**
     * Set case sensitivity.
     */
    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }


    /**
     * Is case sensitive ?
     */
    public boolean isCaseSensitive() {
        return caseSensitive;
    }


    /**
     * Set allow linking.
     */
    public void setAllowLinking(boolean allowLinking) {
        this.allowLinking = allowLinking;
    }


    /**
     * Is linking allowed.
     */
    public boolean isAllowLinking() {
        return allowLinking;
    }


    /**
     * Set cache TTL.
     */
    public void setCacheTTL(int cacheTTL) {
        this.cacheTTL = cacheTTL;
    }


    /**
     * Get cache TTL.
     */
    public int getCacheTTL() {
        return cacheTTL;
    }


    /**
     * Return the maximum size of the cache in KB.
     */
    public int getCacheMaxSize() {
        return cacheMaxSize;
    }


    /**
     * Set the maximum size of the cache in KB.
     */
    public void setCacheMaxSize(int cacheMaxSize) {
        this.cacheMaxSize = cacheMaxSize;
    }


    /**
     * Return the "follow standard delegation model" flag used to configure
     * our ClassLoader.
     */
    public boolean getDelegate() {

        return (this.delegate);

    }


    /**
     * Set the "follow standard delegation model" flag used to configure
     * our ClassLoader.
     *
     * @param delegate The new flag
     */
    public void setDelegate(boolean delegate) {

        boolean oldDelegate = this.delegate;
        this.delegate = delegate;
        support.firePropertyChange("delegate", new Boolean(oldDelegate),
                                   new Boolean(this.delegate));

    }


    /**
     * Returns true if the internal naming support is used.
     */
    public boolean isUseNaming() {

        return (useNaming);

    }


    /**
     * Enables or disables naming.
     */
    public void setUseNaming(boolean useNaming) {
        this.useNaming = useNaming;
    }


    /**
     * Returns true if the resources associated with this context are
     * filesystem based.
     */
    public boolean isFilesystemBased() {

        return (filesystemBased);

    }


    /**
     * Return the set of initialized application event listener objects,
     * in the order they were specified in the web application deployment
     * descriptor, for this application.
     *
     * @exception IllegalStateException if this method is called before
     *  this application has started, or after it has been stopped
     */
    public Object[] getApplicationEventListeners() {
        return (applicationEventListenersObjects);
    }


    /**
     * Store the set of initialized application event listener objects,
     * in the order they were specified in the web application deployment
     * descriptor, for this application.
     *
     * @param listeners The set of instantiated listener objects.
     */
    public void setApplicationEventListeners(Object listeners[]) {
        applicationEventListenersObjects = listeners;
    }


    /**
     * Return the set of initialized application lifecycle listener objects,
     * in the order they were specified in the web application deployment
     * descriptor, for this application.
     *
     * @exception IllegalStateException if this method is called before
     *  this application has started, or after it has been stopped
     */
    public Object[] getApplicationLifecycleListeners() {
        return (applicationLifecycleListenersObjects);
    }


    /**
     * Store the set of initialized application lifecycle listener objects,
     * in the order they were specified in the web application deployment
     * descriptor, for this application.
     *
     * @param listeners The set of instantiated listener objects.
     */
    public void setApplicationLifecycleListeners(Object listeners[]) {
        applicationLifecycleListenersObjects = listeners;
    }


    /**
     * Return the antiJARLocking flag for this Context.
     */
    public boolean getAntiJARLocking() {

        return (this.antiJARLocking);

    }


    /**
     * Return the antiResourceLocking flag for this Context.
     */
    public boolean getAntiResourceLocking() {

        return (this.antiResourceLocking);

    }


    /**
     * Set the antiJARLocking feature for this Context.
     *
     * @param antiJARLocking The new flag value
     */
    public void setAntiJARLocking(boolean antiJARLocking) {

        boolean oldAntiJARLocking = this.antiJARLocking;
        this.antiJARLocking = antiJARLocking;
        support.firePropertyChange("antiJARLocking",
                                   new Boolean(oldAntiJARLocking),
                                   new Boolean(this.antiJARLocking));

    }


    /**
     * Set the antiResourceLocking feature for this Context.
     *
     * @param antiResourceLocking The new flag value
     */
    public void setAntiResourceLocking(boolean antiResourceLocking) {

        boolean oldAntiResourceLocking = this.antiResourceLocking;
        this.antiResourceLocking = antiResourceLocking;
        support.firePropertyChange("antiResourceLocking",
                                   new Boolean(oldAntiResourceLocking),
                                   new Boolean(this.antiResourceLocking));

    }


    /**
     * Return the application available flag for this Context.
     */
    public boolean getAvailable() {

        return (this.available);

    }


    /**
     * Set the application available flag for this Context.
     *
     * @param available The new application available flag
     */
    public void setAvailable(boolean available) {

        boolean oldAvailable = this.available;
        this.available = available;
        support.firePropertyChange("available",
                                   new Boolean(oldAvailable),
                                   new Boolean(this.available));

    }


    /**
     * Return the Locale to character set mapper for this Context.
     */
    public CharsetMapper getCharsetMapper() {

        // Create a mapper the first time it is requested
        if (this.charsetMapper == null) {
            try {
                Class clazz = Class.forName(charsetMapperClass);
                this.charsetMapper =
                  (CharsetMapper) clazz.newInstance();
            } catch (Throwable t) {
                this.charsetMapper = new CharsetMapper();
            }
        }

        return (this.charsetMapper);

    }


    /**
     * Set the Locale to character set mapper for this Context.
     *
     * @param mapper The new mapper
     */
    public void setCharsetMapper(CharsetMapper mapper) {

        CharsetMapper oldCharsetMapper = this.charsetMapper;
        this.charsetMapper = mapper;
        if( mapper != null )
            this.charsetMapperClass= mapper.getClass().getName();
        support.firePropertyChange("charsetMapper", oldCharsetMapper,
                                   this.charsetMapper);

    }

    /**
     * Return the path to a file to save this Context information.
     */
    public String getConfigFile() {

        return (this.configFile);

    }


    /**
     * Set the path to a file to save this Context information.
     *
     * @param configFile The path to a file to save this Context information.
     */
    public void setConfigFile(String configFile) {

        this.configFile = configFile;
    }


    /**
     * Return the "correctly configured" flag for this Context.
     */
    public boolean getConfigured() {

        return (this.configured);

    }


    /**
     * Set the "correctly configured" flag for this Context.  This can be
     * set to false by startup listeners that detect a fatal configuration
     * error to avoid the application from being made available.
     *
     * @param configured The new correctly configured flag
     */
    public void setConfigured(boolean configured) {

        boolean oldConfigured = this.configured;
        this.configured = configured;
        support.firePropertyChange("configured",
                                   new Boolean(oldConfigured),
                                   new Boolean(this.configured));

    }


    /**
     * Return the "use cookies for session ids" flag.
     */
    public boolean getCookies() {

        return (this.cookies);

    }


    /**
     * Set the "use cookies for session ids" flag.
     *
     * @param cookies The new flag
     */
    public void setCookies(boolean cookies) {

        boolean oldCookies = this.cookies;
        this.cookies = cookies;
        support.firePropertyChange("cookies",
                                   new Boolean(oldCookies),
                                   new Boolean(this.cookies));

    }


    /**
     * Return the "allow crossing servlet contexts" flag.
     */
    public boolean getCrossContext() {

        return (this.crossContext);

    }


    /**
     * Set the "allow crossing servlet contexts" flag.
     *
     * @param crossContext The new cross contexts flag
     */
    public void setCrossContext(boolean crossContext) {

        boolean oldCrossContext = this.crossContext;
        this.crossContext = crossContext;
        support.firePropertyChange("crossContext",
                                   new Boolean(oldCrossContext),
                                   new Boolean(this.crossContext));

    }

    public String getDefaultContextXml() {
        return defaultContextXml;
    }

    /** 
     * Set the location of the default context xml that will be used.
     * If not absolute, it'll be made relative to the engine's base dir
     * ( which defaults to catalina.base system property ).
     *
     * @param defaultContextXml The default web xml 
     */
    public void setDefaultContextXml(String defaultContextXml) {
        this.defaultContextXml = defaultContextXml;
    }

    public String getDefaultWebXml() {
        return defaultWebXml;
    }

    /** 
     * Set the location of the default web xml that will be used.
     * If not absolute, it'll be made relative to the engine's base dir
     * ( which defaults to catalina.base system property ).
     *
     * @param defaultWebXml The default web xml 
     */
    public void setDefaultWebXml(String defaultWebXml) {
        this.defaultWebXml = defaultWebXml;
    }

    /**
     * Gets the time (in milliseconds) it took to start this context.
     *
     * @return Time (in milliseconds) it took to start this context.
     */
    public long getStartupTime() {
        return startupTime;
    }

    public void setStartupTime(long startupTime) {
        this.startupTime = startupTime;
    }

    public long getTldScanTime() {
        return tldScanTime;
    }

    public void setTldScanTime(long tldScanTime) {
        this.tldScanTime = tldScanTime;
    }

    /**
     * Return the display name of this web application.
     */
    public String getDisplayName() {

        return (this.displayName);

    }


    /**
     * Return the alternate Deployment Descriptor name.
     */
    public String getAltDDName(){
        return altDDName;
    }


    /**
     * Set an alternate Deployment Descriptor name.
     */
    public void setAltDDName(String altDDName) {
        this.altDDName = altDDName;
        if (context != null) {
            context.setAttribute(Globals.ALT_DD_ATTR,altDDName);
        }
    }


    /**
     * Return the compiler classpath.
     */
    public String getCompilerClasspath(){
        return compilerClasspath;
    }


    /**
     * Set the compiler classpath.
     */
    public void setCompilerClasspath(String compilerClasspath) {
        this.compilerClasspath = compilerClasspath;
    }


    /**
     * Set the display name of this web application.
     *
     * @param displayName The new display name
     */
    public void setDisplayName(String displayName) {

        String oldDisplayName = this.displayName;
        this.displayName = displayName;
        support.firePropertyChange("displayName", oldDisplayName,
                                   this.displayName);
    }


    /**
     * Return the distributable flag for this web application.
     */
    public boolean getDistributable() {

        return (this.distributable);

    }

    /**
     * Set the distributable flag for this web application.
     *
     * @param distributable The new distributable flag
     */
    public void setDistributable(boolean distributable) {
        boolean oldDistributable = this.distributable;
        this.distributable = distributable;
        support.firePropertyChange("distributable",
                                   new Boolean(oldDistributable),
                                   new Boolean(this.distributable));

        // Bugzilla 32866
        if(getManager() != null) {
            if(log.isDebugEnabled()) {
                log.debug("Propagating distributable=" + distributable
                          + " to manager");
            }
            getManager().setDistributable(distributable);
        }
    }


    /**
     * Return the document root for this Context.  This can be an absolute
     * pathname, a relative pathname, or a URL.
     */
    public String getDocBase() {

        return (this.docBase);

    }


    /**
     * Set the document root for this Context.  This can be an absolute
     * pathname, a relative pathname, or a URL.
     *
     * @param docBase The new document root
     */
    public void setDocBase(String docBase) {

        this.docBase = docBase;

    }

    // experimental
    public boolean isLazy() {
        return lazy;
    }

    public void setLazy(boolean lazy) {
        this.lazy = lazy;
    }


    /**
     * Return descriptive information about this Container implementation and
     * the corresponding version number, in the format
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {

        return (info);

    }

    public String getEngineName() {
        if( engineName != null ) return engineName;
        return domain;
    }

    public void setEngineName(String engineName) {
        this.engineName = engineName;
    }

    public String getJ2EEApplication() {
        return j2EEApplication;
    }

    public void setJ2EEApplication(String j2EEApplication) {
        this.j2EEApplication = j2EEApplication;
    }

    public String getJ2EEServer() {
        return j2EEServer;
    }

    public void setJ2EEServer(String j2EEServer) {
        this.j2EEServer = j2EEServer;
    }


    /**
     * Set the Loader with which this Context is associated.
     *
     * @param loader The newly associated loader
     */
    public synchronized void setLoader(Loader loader) {

        super.setLoader(loader);

    }


    /**
     * Return the boolean on the annotations parsing.
     */
    public boolean getIgnoreAnnotations() {
        return this.ignoreAnnotations;
    }
    
    
    /**
     * Set the boolean on the annotations parsing for this web 
     * application.
     * 
     * @param ignoreAnnotations The boolean on the annotations parsing
     */
    public void setIgnoreAnnotations(boolean ignoreAnnotations) {
        boolean oldIgnoreAnnotations = this.ignoreAnnotations;
        this.ignoreAnnotations = ignoreAnnotations;
        support.firePropertyChange("ignoreAnnotations", Boolean.valueOf(oldIgnoreAnnotations),
                Boolean.valueOf(this.ignoreAnnotations));
    }
    
    
    /**
     * Return the login configuration descriptor for this web application.
     */
    public LoginConfig getLoginConfig() {

        return (this.loginConfig);

    }


    /**
     * Set the login configuration descriptor for this web application.
     *
     * @param config The new login configuration
     */
    public void setLoginConfig(LoginConfig config) {

        // Validate the incoming property value
        if (config == null)
            throw new IllegalArgumentException
                (sm.getString("standardContext.loginConfig.required"));
        String loginPage = config.getLoginPage();
        if ((loginPage != null) && !loginPage.startsWith("/")) {
            if (isServlet22()) {
                if(log.isDebugEnabled())
                    log.debug(sm.getString("standardContext.loginConfig.loginWarning",
                                 loginPage));
                config.setLoginPage("/" + loginPage);
            } else {
                throw new IllegalArgumentException
                    (sm.getString("standardContext.loginConfig.loginPage",
                                  loginPage));
            }
        }
        String errorPage = config.getErrorPage();
        if ((errorPage != null) && !errorPage.startsWith("/")) {
            if (isServlet22()) {
                if(log.isDebugEnabled())
                    log.debug(sm.getString("standardContext.loginConfig.errorWarning",
                                 errorPage));
                config.setErrorPage("/" + errorPage);
            } else {
                throw new IllegalArgumentException
                    (sm.getString("standardContext.loginConfig.errorPage",
                                  errorPage));
            }
        }

        // Process the property setting change
        LoginConfig oldLoginConfig = this.loginConfig;
        this.loginConfig = config;
        support.firePropertyChange("loginConfig",
                                   oldLoginConfig, this.loginConfig);

    }


    /**
     * Get the mapper associated with the context.
     */
    public org.apache.tomcat.util.http.mapper.Mapper getMapper() {
        return (mapper);
    }


    /**
     * Return the naming resources associated with this web application.
     */
    public NamingResources getNamingResources() {

        if (namingResources == null) {
            setNamingResources(new NamingResources());
        }
        return (namingResources);

    }


    /**
     * Set the naming resources for this web application.
     *
     * @param namingResources The new naming resources
     */
    public void setNamingResources(NamingResources namingResources) {

        // Process the property setting change
        NamingResources oldNamingResources = this.namingResources;
        this.namingResources = namingResources;
        namingResources.setContainer(this);
        support.firePropertyChange("namingResources",
                                   oldNamingResources, this.namingResources);

    }


    /**
     * Return the context path for this Context.
     */
    public String getPath() {

        return (getName());

    }

    
    /**
     * Set the context path for this Context.
     * <p>
     * <b>IMPLEMENTATION NOTE</b>:  The context path is used as the "name" of
     * a Context, because it must be unique.
     *
     * @param path The new context path
     */
    public void setPath(String path) {
        // XXX Use host in name
        setName(RequestUtil.URLDecode(path));

    }


    /**
     * Return the public identifier of the deployment descriptor DTD that is
     * currently being parsed.
     */
    public String getPublicId() {

        return (this.publicId);

    }


    /**
     * Set the public identifier of the deployment descriptor DTD that is
     * currently being parsed.
     *
     * @param publicId The public identifier
     */
    public void setPublicId(String publicId) {

        if (log.isDebugEnabled())
            log.debug("Setting deployment descriptor public ID to '" +
                publicId + "'");

        String oldPublicId = this.publicId;
        this.publicId = publicId;
        support.firePropertyChange("publicId", oldPublicId, publicId);

    }


    /**
     * Return the reloadable flag for this web application.
     */
    public boolean getReloadable() {

        return (this.reloadable);

    }


    /**
     * Return the DefaultContext override flag for this web application.
     */
    public boolean getOverride() {

        return (this.override);

    }


    /**
     * Return the original document root for this Context.  This can be an absolute
     * pathname, a relative pathname, or a URL.
     * Is only set as deployment has change docRoot!
     */
    public String getOriginalDocBase() {

        return (this.originalDocBase);

    }

    /**
     * Set the original document root for this Context.  This can be an absolute
     * pathname, a relative pathname, or a URL.
     *
     * @param docBase The orginal document root
     */
    public void setOriginalDocBase(String docBase) {

        this.originalDocBase = docBase;
    }
    
    /**
     * Return the privileged flag for this web application.
     */
    public boolean getPrivileged() {

        return (this.privileged);

    }


    /**
     * Set the privileged flag for this web application.
     *
     * @param privileged The new privileged flag
     */
    public void setPrivileged(boolean privileged) {

        boolean oldPrivileged = this.privileged;
        this.privileged = privileged;
        support.firePropertyChange("privileged",
                                   new Boolean(oldPrivileged),
                                   new Boolean(this.privileged));

    }


    /**
     * Set the reloadable flag for this web application.
     *
     * @param reloadable The new reloadable flag
     */
    public void setReloadable(boolean reloadable) {

        boolean oldReloadable = this.reloadable;
        this.reloadable = reloadable;
        support.firePropertyChange("reloadable",
                                   new Boolean(oldReloadable),
                                   new Boolean(this.reloadable));

    }


    /**
     * Set the DefaultContext override flag for this web application.
     *
     * @param override The new override flag
     */
    public void setOverride(boolean override) {

        boolean oldOverride = this.override;
        this.override = override;
        support.firePropertyChange("override",
                                   new Boolean(oldOverride),
                                   new Boolean(this.override));

    }


    /**
     * Return the "replace welcome files" property.
     */
    public boolean isReplaceWelcomeFiles() {

        return (this.replaceWelcomeFiles);

    }


    /**
     * Set the "replace welcome files" property.
     *
     * @param replaceWelcomeFiles The new property value
     */
    public void setReplaceWelcomeFiles(boolean replaceWelcomeFiles) {

        boolean oldReplaceWelcomeFiles = this.replaceWelcomeFiles;
        this.replaceWelcomeFiles = replaceWelcomeFiles;
        support.firePropertyChange("replaceWelcomeFiles",
                                   new Boolean(oldReplaceWelcomeFiles),
                                   new Boolean(this.replaceWelcomeFiles));

    }


    /**
     * Return the servlet context for which this Context is a facade.
     */
    public ServletContext getServletContext() {

        if (context == null) {
            context = new ApplicationContext(getBasePath(), this);
            if (altDDName != null)
                context.setAttribute(Globals.ALT_DD_ATTR,altDDName);
        }
        return (context.getFacade());

    }


    /**
     * Return the default session timeout (in minutes) for this
     * web application.
     */
    public int getSessionTimeout() {

        return (this.sessionTimeout);

    }


    /**
     * Set the default session timeout (in minutes) for this
     * web application.
     *
     * @param timeout The new default session timeout
     */
    public void setSessionTimeout(int timeout) {

        int oldSessionTimeout = this.sessionTimeout;
        /*
         * SRV.13.4 ("Deployment Descriptor"):
         * If the timeout is 0 or less, the container ensures the default
         * behaviour of sessions is never to time out.
         */
        this.sessionTimeout = (timeout == 0) ? -1 : timeout;
        support.firePropertyChange("sessionTimeout",
                                   new Integer(oldSessionTimeout),
                                   new Integer(this.sessionTimeout));

    }


    /**
     * Return the value of the swallowOutput flag.
     */
    public boolean getSwallowOutput() {

        return (this.swallowOutput);

    }


    /**
     * Set the value of the swallowOutput flag. If set to true, the system.out
     * and system.err will be redirected to the logger during a servlet
     * execution.
     *
     * @param swallowOutput The new value
     */
    public void setSwallowOutput(boolean swallowOutput) {

        boolean oldSwallowOutput = this.swallowOutput;
        this.swallowOutput = swallowOutput;
        support.firePropertyChange("swallowOutput",
                                   new Boolean(oldSwallowOutput),
                                   new Boolean(this.swallowOutput));

    }


    /**
     * Return the value of the unloadDelay flag.
     */
    public long getUnloadDelay() {

        return (this.unloadDelay);

    }


    /**
     * Set the value of the unloadDelay flag, which represents the amount
     * of ms that the container will wait when unloading servlets.
     * Setting this to a small value may cause more requests to fail 
     * to complete when stopping a web application.
     *
     * @param unloadDelay The new value
     */
    public void setUnloadDelay(long unloadDelay) {

        long oldUnloadDelay = this.unloadDelay;
        this.unloadDelay = unloadDelay;
        support.firePropertyChange("unloadDelay",
                                   new Long(oldUnloadDelay),
                                   new Long(this.unloadDelay));

    }


    /**
     * Unpack WAR flag accessor.
     */
    public boolean getUnpackWAR() {

        return (unpackWAR);

    }


    /**
     * Unpack WAR flag mutator.
     */
    public void setUnpackWAR(boolean unpackWAR) {

        this.unpackWAR = unpackWAR;

    }

    /**
     * Return the Java class name of the Wrapper implementation used
     * for servlets registered in this Context.
     */
    public String getWrapperClass() {

        return (this.wrapperClassName);

    }


    /**
     * Set the Java class name of the Wrapper implementation used
     * for servlets registered in this Context.
     *
     * @param wrapperClassName The new wrapper class name
     *
     * @throws IllegalArgumentException if the specified wrapper class
     * cannot be found or is not a subclass of StandardWrapper
     */
    public void setWrapperClass(String wrapperClassName) {

        this.wrapperClassName = wrapperClassName;

        try {
            wrapperClass = Class.forName(wrapperClassName);         
            if (!StandardWrapper.class.isAssignableFrom(wrapperClass)) {
                throw new IllegalArgumentException(
                    sm.getString("standardContext.invalidWrapperClass",
                                 wrapperClassName));
            }
        } catch (ClassNotFoundException cnfe) {
            throw new IllegalArgumentException(cnfe.getMessage());
        }
    }


    /**
     * Set the resources DirContext object with which this Container is
     * associated.
     *
     * @param resources The newly associated DirContext
     */
    public synchronized void setResources(DirContext resources) {

        if (started) {
            throw new IllegalStateException
                (sm.getString("standardContext.resources.started"));
        }

        DirContext oldResources = this.webappResources;
        if (oldResources == resources)
            return;

        if (resources instanceof BaseDirContext) {
            ((BaseDirContext) resources).setCached(isCachingAllowed());
            ((BaseDirContext) resources).setCacheTTL(getCacheTTL());
            ((BaseDirContext) resources).setCacheMaxSize(getCacheMaxSize());
        }
        if (resources instanceof FileDirContext) {
            filesystemBased = true;
            ((FileDirContext) resources).setCaseSensitive(isCaseSensitive());
            ((FileDirContext) resources).setAllowLinking(isAllowLinking());
        }
        this.webappResources = resources;

        // The proxied resources will be refreshed on start
        this.resources = null;

        support.firePropertyChange("resources", oldResources,
                                   this.webappResources);

    }


    // ------------------------------------------------------ Public Properties


    /**
     * Return the Locale to character set mapper class for this Context.
     */
    public String getCharsetMapperClass() {

        return (this.charsetMapperClass);

    }


    /**
     * Set the Locale to character set mapper class for this Context.
     *
     * @param mapper The new mapper class
     */
    public void setCharsetMapperClass(String mapper) {

        String oldCharsetMapperClass = this.charsetMapperClass;
        this.charsetMapperClass = mapper;
        support.firePropertyChange("charsetMapperClass",
                                   oldCharsetMapperClass,
                                   this.charsetMapperClass);

    }


    /** Get the absolute path to the work dir.
     *  To avoid duplication.
     * 
     * @return The work path
     */ 
    public String getWorkPath() {
        File workDir = new File(getWorkDir());
        if (!workDir.isAbsolute()) {
            File catalinaHome = engineBase();
            String catalinaHomePath = null;
            try {
                catalinaHomePath = catalinaHome.getCanonicalPath();
                workDir = new File(catalinaHomePath,
                        getWorkDir());
            } catch (IOException e) {
                log.warn("Exception obtaining work path for " + getPath());
            }
        }
        return workDir.getAbsolutePath();
    }
    
    /**
     * Return the work directory for this Context.
     */
    public String getWorkDir() {

        return (this.workDir);

    }


    /**
     * Set the work directory for this Context.
     *
     * @param workDir The new work directory
     */
    public void setWorkDir(String workDir) {

        this.workDir = workDir;

        if (started) {
            postWorkDirectory();
        }
    }


    /**
     * Save config ?
     */
    public boolean isSaveConfig() {
        return saveConfig;
    }


    /**
     * Set save config flag.
     */
    public void setSaveConfig(boolean saveConfig) {
        this.saveConfig = saveConfig;
    }


    // -------------------------------------------------------- Context Methods


    /**
     * Add a new Listener class name to the set of Listeners
     * configured for this application.
     *
     * @param listener Java class name of a listener class
     */
    public void addApplicationListener(String listener) {

        synchronized (applicationListeners) {
            String results[] =new String[applicationListeners.length + 1];
            for (int i = 0; i < applicationListeners.length; i++) {
                if (listener.equals(applicationListeners[i]))
                    return;
                results[i] = applicationListeners[i];
            }
            results[applicationListeners.length] = listener;
            applicationListeners = results;
        }
        fireContainerEvent("addApplicationListener", listener);

        // FIXME - add instance if already started?

    }


    /**
     * Add a new application parameter for this application.
     *
     * @param parameter The new application parameter
     */
    public void addApplicationParameter(ApplicationParameter parameter) {

        synchronized (applicationParameters) {
            String newName = parameter.getName();
            for (int i = 0; i < applicationParameters.length; i++) {
                if (newName.equals(applicationParameters[i].getName()) &&
                    !applicationParameters[i].getOverride())
                    return;
            }
            ApplicationParameter results[] =
                new ApplicationParameter[applicationParameters.length + 1];
            System.arraycopy(applicationParameters, 0, results, 0,
                             applicationParameters.length);
            results[applicationParameters.length] = parameter;
            applicationParameters = results;
        }
        fireContainerEvent("addApplicationParameter", parameter);

    }


    /**
     * Add a child Container, only if the proposed child is an implementation
     * of Wrapper.
     *
     * @param child Child container to be added
     *
     * @exception IllegalArgumentException if the proposed container is
     *  not an implementation of Wrapper
     */
    public void addChild(Container child) {

        // Global JspServlet
        Wrapper oldJspServlet = null;

        if (!(child instanceof Wrapper)) {
            throw new IllegalArgumentException
                (sm.getString("standardContext.notWrapper"));
        }

        Wrapper wrapper = (Wrapper) child;
        boolean isJspServlet = "jsp".equals(child.getName());

        // Allow webapp to override JspServlet inherited from global web.xml.
        if (isJspServlet) {
            oldJspServlet = (Wrapper) findChild("jsp");
            if (oldJspServlet != null) {
                removeChild(oldJspServlet);
            }
        }

        String jspFile = wrapper.getJspFile();
        if ((jspFile != null) && !jspFile.startsWith("/")) {
            if (isServlet22()) {
                if(log.isDebugEnabled())
                    log.debug(sm.getString("standardContext.wrapper.warning", 
                                       jspFile));
                wrapper.setJspFile("/" + jspFile);
            } else {
                throw new IllegalArgumentException
                    (sm.getString("standardContext.wrapper.error", jspFile));
            }
        }

        super.addChild(child);

        if (isJspServlet && oldJspServlet != null) {
            /*
             * The webapp-specific JspServlet inherits all the mappings
             * specified in the global web.xml, and may add additional ones.
             */
            String[] jspMappings = oldJspServlet.findMappings();
            for (int i=0; jspMappings!=null && i<jspMappings.length; i++) {
                addServletMapping(jspMappings[i], child.getName());
            }
        }
    }


    /**
     * Add a security constraint to the set for this web application.
     */
    public void addConstraint(SecurityConstraint constraint) {

        // Validate the proposed constraint
        SecurityCollection collections[] = constraint.findCollections();
        for (int i = 0; i < collections.length; i++) {
            String patterns[] = collections[i].findPatterns();
            for (int j = 0; j < patterns.length; j++) {
                patterns[j] = adjustURLPattern(patterns[j]);
                if (!validateURLPattern(patterns[j]))
                    throw new IllegalArgumentException
                        (sm.getString
                         ("standardContext.securityConstraint.pattern",
                          patterns[j]));
            }
        }

        // Add this constraint to the set for our web application
        synchronized (constraints) {
            SecurityConstraint results[] =
                new SecurityConstraint[constraints.length + 1];
            for (int i = 0; i < constraints.length; i++)
                results[i] = constraints[i];
            results[constraints.length] = constraint;
            constraints = results;
        }

    }



    /**
     * Add an error page for the specified error or Java exception.
     *
     * @param errorPage The error page definition to be added
     */
    public void addErrorPage(ErrorPage errorPage) {
        // Validate the input parameters
        if (errorPage == null)
            throw new IllegalArgumentException
                (sm.getString("standardContext.errorPage.required"));
        String location = errorPage.getLocation();
        if ((location != null) && !location.startsWith("/")) {
            if (isServlet22()) {
                if(log.isDebugEnabled())
                    log.debug(sm.getString("standardContext.errorPage.warning",
                                 location));
                errorPage.setLocation("/" + location);
            } else {
                throw new IllegalArgumentException
                    (sm.getString("standardContext.errorPage.error",
                                  location));
            }
        }

        // Add the specified error page to our internal collections
        String exceptionType = errorPage.getExceptionType();
        if (exceptionType != null) {
            synchronized (exceptionPages) {
                exceptionPages.put(exceptionType, errorPage);
            }
        } else {
            synchronized (statusPages) {
                if (errorPage.getErrorCode() == 200) {
                    this.okErrorPage = errorPage;
                }
                statusPages.put(new Integer(errorPage.getErrorCode()),
                                errorPage);
            }
        }
        fireContainerEvent("addErrorPage", errorPage);

    }


    /**
     * Add a filter definition to this Context.
     *
     * @param filterDef The filter definition to be added
     */
    public void addFilterDef(FilterDef filterDef) {

        synchronized (filterDefs) {
            filterDefs.put(filterDef.getFilterName(), filterDef);
        }
        fireContainerEvent("addFilterDef", filterDef);

    }


    /**
     * Add a filter mapping to this Context.
     *
     * @param filterMap The filter mapping to be added
     *
     * @exception IllegalArgumentException if the specified filter name
     *  does not match an existing filter definition, or the filter mapping
     *  is malformed
     */
    public void addFilterMap(FilterMap filterMap) {

        // Validate the proposed filter mapping
        String filterName = filterMap.getFilterName();
        String[] servletNames = filterMap.getServletNames();
        String[] urlPatterns = filterMap.getURLPatterns();
        if (findFilterDef(filterName) == null)
            throw new IllegalArgumentException
                (sm.getString("standardContext.filterMap.name", filterName));
        if ((servletNames.length == 0) && (urlPatterns.length == 0))
            throw new IllegalArgumentException
                (sm.getString("standardContext.filterMap.either"));
        // FIXME: Older spec revisions may still check this
        /*
        if ((servletNames.length != 0) && (urlPatterns.length != 0))
            throw new IllegalArgumentException
                (sm.getString("standardContext.filterMap.either"));
        */
        // Because filter-pattern is new in 2.3, no need to adjust
        // for 2.2 backwards compatibility
        for (int i = 0; i < urlPatterns.length; i++) {
            if (!validateURLPattern(urlPatterns[i])) {
                throw new IllegalArgumentException
                    (sm.getString("standardContext.filterMap.pattern",
                            urlPatterns[i]));
            }
        }

        // Add this filter mapping to our registered set
        synchronized (filterMaps) {
            FilterMap results[] =new FilterMap[filterMaps.length + 1];
            System.arraycopy(filterMaps, 0, results, 0, filterMaps.length);
            results[filterMaps.length] = filterMap;
            filterMaps = results;
        }
        fireContainerEvent("addFilterMap", filterMap);

    }


    /**
     * Add the classname of an InstanceListener to be added to each
     * Wrapper appended to this Context.
     *
     * @param listener Java class name of an InstanceListener class
     */
    public void addInstanceListener(String listener) {

        synchronized (instanceListeners) {
            String results[] =new String[instanceListeners.length + 1];
            for (int i = 0; i < instanceListeners.length; i++)
                results[i] = instanceListeners[i];
            results[instanceListeners.length] = listener;
            instanceListeners = results;
        }
        fireContainerEvent("addInstanceListener", listener);

    }

    /**
     * Add the given URL pattern as a jsp-property-group.  This maps
     * resources that match the given pattern so they will be passed
     * to the JSP container.  Though there are other elements in the
     * property group, we only care about the URL pattern here.  The
     * JSP container will parse the rest.
     *
     * @param pattern URL pattern to be mapped
     */
    public void addJspMapping(String pattern) {
        String servletName = findServletMapping("*.jsp");
        if (servletName == null) {
            servletName = "jsp";
        }

        if( findChild(servletName) != null) {
            addServletMapping(pattern, servletName, true);
        } else {
            if(log.isDebugEnabled())
                log.debug("Skiping " + pattern + " , no servlet " + servletName);
        }
    }


    /**
     * Add a Locale Encoding Mapping (see Sec 5.4 of Servlet spec 2.4)
     *
     * @param locale locale to map an encoding for
     * @param encoding encoding to be used for a give locale
     */
    public void addLocaleEncodingMappingParameter(String locale, String encoding){
        getCharsetMapper().addCharsetMappingFromDeploymentDescriptor(locale, encoding);
    }


    /**
     * Add a message destination for this web application.
     *
     * @param md New message destination
     */
    public void addMessageDestination(MessageDestination md) {

        synchronized (messageDestinations) {
            messageDestinations.put(md.getName(), md);
        }
        fireContainerEvent("addMessageDestination", md.getName());

    }


    /**
     * Add a message destination reference for this web application.
     *
     * @param mdr New message destination reference
     */
    public void addMessageDestinationRef
        (MessageDestinationRef mdr) {

        namingResources.addMessageDestinationRef(mdr);
        fireContainerEvent("addMessageDestinationRef", mdr.getName());

    }


    /**
     * Add a new MIME mapping, replacing any existing mapping for
     * the specified extension.
     *
     * @param extension Filename extension being mapped
     * @param mimeType Corresponding MIME type
     */
    public void addMimeMapping(String extension, String mimeType) {

        synchronized (mimeMappings) {
            mimeMappings.put(extension, mimeType);
        }
        fireContainerEvent("addMimeMapping", extension);

    }


    /**
     * Add a new context initialization parameter.
     *
     * @param name Name of the new parameter
     * @param value Value of the new  parameter
     *
     * @exception IllegalArgumentException if the name or value is missing,
     *  or if this context initialization parameter has already been
     *  registered
     */
    public void addParameter(String name, String value) {
        // Validate the proposed context initialization parameter
        if ((name == null) || (value == null))
            throw new IllegalArgumentException
                (sm.getString("standardContext.parameter.required"));
        if (parameters.get(name) != null)
            throw new IllegalArgumentException
                (sm.getString("standardContext.parameter.duplicate", name));

        // Add this parameter to our defined set
        synchronized (parameters) {
            parameters.put(name, value);
        }
        fireContainerEvent("addParameter", name);

    }


    /**
     * Add a security role reference for this web application.
     *
     * @param role Security role used in the application
     * @param link Actual security role to check for
     */
    public void addRoleMapping(String role, String link) {

        synchronized (roleMappings) {
            roleMappings.put(role, link);
        }
        fireContainerEvent("addRoleMapping", role);

    }


    /**
     * Add a new security role for this web application.
     *
     * @param role New security role
     */
    public void addSecurityRole(String role) {

        synchronized (securityRoles) {
            String results[] =new String[securityRoles.length + 1];
            for (int i = 0; i < securityRoles.length; i++)
                results[i] = securityRoles[i];
            results[securityRoles.length] = role;
            securityRoles = results;
        }
        fireContainerEvent("addSecurityRole", role);

    }


    /**
     * Add a new servlet mapping, replacing any existing mapping for
     * the specified pattern.
     *
     * @param pattern URL pattern to be mapped
     * @param name Name of the corresponding servlet to execute
     *
     * @exception IllegalArgumentException if the specified servlet name
     *  is not known to this Context
     */
    public void addServletMapping(String pattern, String name) {
        addServletMapping(pattern, name, false);
    }


    /**
     * Add a new servlet mapping, replacing any existing mapping for
     * the specified pattern.
     *
     * @param pattern URL pattern to be mapped
     * @param name Name of the corresponding servlet to execute
     * @param jspWildCard true if name identifies the JspServlet
     * and pattern contains a wildcard; false otherwise
     *
     * @exception IllegalArgumentException if the specified servlet name
     *  is not known to this Context
     */
    public void addServletMapping(String pattern, String name,
                                  boolean jspWildCard) {
        // Validate the proposed mapping
        if (findChild(name) == null)
            throw new IllegalArgumentException
                (sm.getString("standardContext.servletMap.name", name));
        pattern = adjustURLPattern(RequestUtil.URLDecode(pattern));
        if (!validateURLPattern(pattern))
            throw new IllegalArgumentException
                (sm.getString("standardContext.servletMap.pattern", pattern));

        // Add this mapping to our registered set
        synchronized (servletMappings) {
            String name2 = (String) servletMappings.get(pattern);
            if (name2 != null) {
                // Don't allow more than one servlet on the same pattern
                Wrapper wrapper = (Wrapper) findChild(name2);
                wrapper.removeMapping(pattern);
                mapper.removeWrapper(pattern);
            }
            servletMappings.put(pattern, name);
        }
        Wrapper wrapper = (Wrapper) findChild(name);
        wrapper.addMapping(pattern);

        // Update context mapper
        mapper.addWrapper(pattern, wrapper, jspWildCard);

        fireContainerEvent("addServletMapping", pattern);

    }


    /**
     * Add a JSP tag library for the specified URI.
     *
     * @param uri URI, relative to the web.xml file, of this tag library
     * @param location Location of the tag library descriptor
     */
    public void addTaglib(String uri, String location) {

        synchronized (taglibs) {
            taglibs.put(uri, location);
        }
        fireContainerEvent("addTaglib", uri);

    }


    /**
     * Add a new watched resource to the set recognized by this Context.
     *
     * @param name New watched resource file name
     */
    public void addWatchedResource(String name) {

        synchronized (watchedResources) {
            String results[] = new String[watchedResources.length + 1];
            for (int i = 0; i < watchedResources.length; i++)
                results[i] = watchedResources[i];
            results[watchedResources.length] = name;
            watchedResources = results;
        }
        fireContainerEvent("addWatchedResource", name);

    }


    /**
     * Add a new welcome file to the set recognized by this Context.
     *
     * @param name New welcome file name
     */
    public void addWelcomeFile(String name) {

        synchronized (welcomeFiles) {
            // Welcome files from the application deployment descriptor
            // completely replace those from the default conf/web.xml file
            if (replaceWelcomeFiles) {
                welcomeFiles = new String[0];
                setReplaceWelcomeFiles(false);
            }
            String results[] =new String[welcomeFiles.length + 1];
            for (int i = 0; i < welcomeFiles.length; i++)
                results[i] = welcomeFiles[i];
            results[welcomeFiles.length] = name;
            welcomeFiles = results;
        }
        postWelcomeFiles();
        fireContainerEvent("addWelcomeFile", name);

    }


    /**
     * Add the classname of a LifecycleListener to be added to each
     * Wrapper appended to this Context.
     *
     * @param listener Java class name of a LifecycleListener class
     */
    public void addWrapperLifecycle(String listener) {

        synchronized (wrapperLifecycles) {
            String results[] =new String[wrapperLifecycles.length + 1];
            for (int i = 0; i < wrapperLifecycles.length; i++)
                results[i] = wrapperLifecycles[i];
            results[wrapperLifecycles.length] = listener;
            wrapperLifecycles = results;
        }
        fireContainerEvent("addWrapperLifecycle", listener);

    }


    /**
     * Add the classname of a ContainerListener to be added to each
     * Wrapper appended to this Context.
     *
     * @param listener Java class name of a ContainerListener class
     */
    public void addWrapperListener(String listener) {

        synchronized (wrapperListeners) {
            String results[] =new String[wrapperListeners.length + 1];
            for (int i = 0; i < wrapperListeners.length; i++)
                results[i] = wrapperListeners[i];
            results[wrapperListeners.length] = listener;
            wrapperListeners = results;
        }
        fireContainerEvent("addWrapperListener", listener);

    }


    /**
     * Factory method to create and return a new Wrapper instance, of
     * the Java implementation class appropriate for this Context
     * implementation.  The constructor of the instantiated Wrapper
     * will have been called, but no properties will have been set.
     */
    public Wrapper createWrapper() {

        Wrapper wrapper = null;
        if (wrapperClass != null) {
            try {
                wrapper = (Wrapper) wrapperClass.newInstance();
            } catch (Throwable t) {
                log.error("createWrapper", t);
                return (null);
            }
        } else {
            wrapper = new StandardWrapper();
        }

        synchronized (instanceListeners) {
            for (int i = 0; i < instanceListeners.length; i++) {
                try {
                    Class clazz = Class.forName(instanceListeners[i]);
                    InstanceListener listener =
                      (InstanceListener) clazz.newInstance();
                    wrapper.addInstanceListener(listener);
                } catch (Throwable t) {
                    log.error("createWrapper", t);
                    return (null);
                }
            }
        }

        synchronized (wrapperLifecycles) {
            for (int i = 0; i < wrapperLifecycles.length; i++) {
                try {
                    Class clazz = Class.forName(wrapperLifecycles[i]);
                    LifecycleListener listener =
                      (LifecycleListener) clazz.newInstance();
                    if (wrapper instanceof Lifecycle)
                        ((Lifecycle) wrapper).addLifecycleListener(listener);
                } catch (Throwable t) {
                    log.error("createWrapper", t);
                    return (null);
                }
            }
        }

        synchronized (wrapperListeners) {
            for (int i = 0; i < wrapperListeners.length; i++) {
                try {
                    Class clazz = Class.forName(wrapperListeners[i]);
                    ContainerListener listener =
                      (ContainerListener) clazz.newInstance();
                    wrapper.addContainerListener(listener);
                } catch (Throwable t) {
                    log.error("createWrapper", t);
                    return (null);
                }
            }
        }

        return (wrapper);

    }


    /**
     * Return the set of application listener class names configured
     * for this application.
     */
    public String[] findApplicationListeners() {

        return (applicationListeners);

    }


    /**
     * Return the set of application parameters for this application.
     */
    public ApplicationParameter[] findApplicationParameters() {

        return (applicationParameters);

    }


    /**
     * Return the security constraints for this web application.
     * If there are none, a zero-length array is returned.
     */
    public SecurityConstraint[] findConstraints() {

        return (constraints);

    }


    /**
     * Return the error page entry for the specified HTTP error code,
     * if any; otherwise return <code>null</code>.
     *
     * @param errorCode Error code to look up
     */
    public ErrorPage findErrorPage(int errorCode) {
        if (errorCode == 200) {
            return (okErrorPage);
        } else {
            return ((ErrorPage) statusPages.get(new Integer(errorCode)));
        }

    }


    /**
     * Return the error page entry for the specified Java exception type,
     * if any; otherwise return <code>null</code>.
     *
     * @param exceptionType Exception type to look up
     */
    public ErrorPage findErrorPage(String exceptionType) {

        synchronized (exceptionPages) {
            return ((ErrorPage) exceptionPages.get(exceptionType));
        }

    }


    /**
     * Return the set of defined error pages for all specified error codes
     * and exception types.
     */
    public ErrorPage[] findErrorPages() {

        synchronized(exceptionPages) {
            synchronized(statusPages) {
                ErrorPage results1[] = new ErrorPage[exceptionPages.size()];
                results1 =
                    (ErrorPage[]) exceptionPages.values().toArray(results1);
                ErrorPage results2[] = new ErrorPage[statusPages.size()];
                results2 =
                    (ErrorPage[]) statusPages.values().toArray(results2);
                ErrorPage results[] =
                    new ErrorPage[results1.length + results2.length];
                for (int i = 0; i < results1.length; i++)
                    results[i] = results1[i];
                for (int i = results1.length; i < results.length; i++)
                    results[i] = results2[i - results1.length];
                return (results);
            }
        }

    }


    /**
     * Return the filter definition for the specified filter name, if any;
     * otherwise return <code>null</code>.
     *
     * @param filterName Filter name to look up
     */
    public FilterDef findFilterDef(String filterName) {

        synchronized (filterDefs) {
            return ((FilterDef) filterDefs.get(filterName));
        }

    }


    /**
     * Return the set of defined filters for this Context.
     */
    public FilterDef[] findFilterDefs() {

        synchronized (filterDefs) {
            FilterDef results[] = new FilterDef[filterDefs.size()];
            return ((FilterDef[]) filterDefs.values().toArray(results));
        }

    }


    /**
     * Return the set of filter mappings for this Context.
     */
    public FilterMap[] findFilterMaps() {

        return (filterMaps);

    }


    /**
     * Return the set of InstanceListener classes that will be added to
     * newly created Wrappers automatically.
     */
    public String[] findInstanceListeners() {

        return (instanceListeners);

    }


    /**
     * FIXME: Fooling introspection ...
     */
    public Context findMappingObject() {
        return (Context) getMappingObject();
    }
    
    
    /**
     * Return the message destination with the specified name, if any;
     * otherwise, return <code>null</code>.
     *
     * @param name Name of the desired message destination
     */
    public MessageDestination findMessageDestination(String name) {

        synchronized (messageDestinations) {
            return ((MessageDestination) messageDestinations.get(name));
        }

    }


    /**
     * Return the set of defined message destinations for this web
     * application.  If none have been defined, a zero-length array
     * is returned.
     */
    public MessageDestination[] findMessageDestinations() {

        synchronized (messageDestinations) {
            MessageDestination results[] =
                new MessageDestination[messageDestinations.size()];
            return ((MessageDestination[])
                    messageDestinations.values().toArray(results));
        }

    }


    /**
     * Return the message destination ref with the specified name, if any;
     * otherwise, return <code>null</code>.
     *
     * @param name Name of the desired message destination ref
     */
    public MessageDestinationRef
        findMessageDestinationRef(String name) {

        return namingResources.findMessageDestinationRef(name);

    }


    /**
     * Return the set of defined message destination refs for this web
     * application.  If none have been defined, a zero-length array
     * is returned.
     */
    public MessageDestinationRef[]
        findMessageDestinationRefs() {

        return namingResources.findMessageDestinationRefs();

    }


    /**
     * Return the MIME type to which the specified extension is mapped,
     * if any; otherwise return <code>null</code>.
     *
     * @param extension Extension to map to a MIME type
     */
    public String findMimeMapping(String extension) {

        return ((String) mimeMappings.get(extension));

    }


    /**
     * Return the extensions for which MIME mappings are defined.  If there
     * are none, a zero-length array is returned.
     */
    public String[] findMimeMappings() {

        synchronized (mimeMappings) {
            String results[] = new String[mimeMappings.size()];
            return
                ((String[]) mimeMappings.keySet().toArray(results));
        }

    }


    /**
     * Return the value for the specified context initialization
     * parameter name, if any; otherwise return <code>null</code>.
     *
     * @param name Name of the parameter to return
     */
    public String findParameter(String name) {

        synchronized (parameters) {
            return ((String) parameters.get(name));
        }

    }


    /**
     * Return the names of all defined context initialization parameters
     * for this Context.  If no parameters are defined, a zero-length
     * array is returned.
     */
    public String[] findParameters() {

        synchronized (parameters) {
            String results[] = new String[parameters.size()];
            return ((String[]) parameters.keySet().toArray(results));
        }

    }


    /**
     * For the given security role (as used by an application), return the
     * corresponding role name (as defined by the underlying Realm) if there
     * is one.  Otherwise, return the specified role unchanged.
     *
     * @param role Security role to map
     */
    public String findRoleMapping(String role) {

        String realRole = null;
        synchronized (roleMappings) {
            realRole = (String) roleMappings.get(role);
        }
        if (realRole != null)
            return (realRole);
        else
            return (role);

    }


    /**
     * Return <code>true</code> if the specified security role is defined
     * for this application; otherwise return <code>false</code>.
     *
     * @param role Security role to verify
     */
    public boolean findSecurityRole(String role) {

        synchronized (securityRoles) {
            for (int i = 0; i < securityRoles.length; i++) {
                if (role.equals(securityRoles[i]))
                    return (true);
            }
        }
        return (false);

    }


    /**
     * Return the security roles defined for this application.  If none
     * have been defined, a zero-length array is returned.
     */
    public String[] findSecurityRoles() {

        return (securityRoles);

    }


    /**
     * Return the servlet name mapped by the specified pattern (if any);
     * otherwise return <code>null</code>.
     *
     * @param pattern Pattern for which a mapping is requested
     */
    public String findServletMapping(String pattern) {

        synchronized (servletMappings) {
            return ((String) servletMappings.get(pattern));
        }

    }


    /**
     * Return the patterns of all defined servlet mappings for this
     * Context.  If no mappings are defined, a zero-length array is returned.
     */
    public String[] findServletMappings() {

        synchronized (servletMappings) {
            String results[] = new String[servletMappings.size()];
            return
               ((String[]) servletMappings.keySet().toArray(results));
        }

    }


    /**
     * Return the context-relative URI of the error page for the specified
     * HTTP status code, if any; otherwise return <code>null</code>.
     *
     * @param status HTTP status code to look up
     */
    public String findStatusPage(int status) {

        return ((String) statusPages.get(new Integer(status)));

    }


    /**
     * Return the set of HTTP status codes for which error pages have
     * been specified.  If none are specified, a zero-length array
     * is returned.
     */
    public int[] findStatusPages() {

        synchronized (statusPages) {
            int results[] = new int[statusPages.size()];
            Iterator elements = statusPages.keySet().iterator();
            int i = 0;
            while (elements.hasNext())
                results[i++] = ((Integer) elements.next()).intValue();
            return (results);
        }

    }


    /**
     * Return the tag library descriptor location for the specified taglib
     * URI, if any; otherwise, return <code>null</code>.
     *
     * @param uri URI, relative to the web.xml file
     */
    public String findTaglib(String uri) {

        synchronized (taglibs) {
            return ((String) taglibs.get(uri));
        }

    }


    /**
     * Return the URIs of all tag libraries for which a tag library
     * descriptor location has been specified.  If none are specified,
     * a zero-length array is returned.
     */
    public String[] findTaglibs() {

        synchronized (taglibs) {
            String results[] = new String[taglibs.size()];
            return ((String[]) taglibs.keySet().toArray(results));
        }

    }


    /**
     * Return <code>true</code> if the specified welcome file is defined
     * for this Context; otherwise return <code>false</code>.
     *
     * @param name Welcome file to verify
     */
    public boolean findWelcomeFile(String name) {

        synchronized (welcomeFiles) {
            for (int i = 0; i < welcomeFiles.length; i++) {
                if (name.equals(welcomeFiles[i]))
                    return (true);
            }
        }
        return (false);

    }


    /**
     * Return the set of watched resources for this Context. If none are 
     * defined, a zero length array will be returned.
     */
    public String[] findWatchedResources() {
        return watchedResources;
    }
    
    
    /**
     * Return the set of welcome files defined for this Context.  If none are
     * defined, a zero-length array is returned.
     */
    public String[] findWelcomeFiles() {

        return (welcomeFiles);

    }


    /**
     * Return the set of LifecycleListener classes that will be added to
     * newly created Wrappers automatically.
     */
    public String[] findWrapperLifecycles() {

        return (wrapperLifecycles);

    }


    /**
     * Return the set of ContainerListener classes that will be added to
     * newly created Wrappers automatically.
     */
    public String[] findWrapperListeners() {

        return (wrapperListeners);

    }


    /**
     * Reload this web application, if reloading is supported.
     * <p>
     * <b>IMPLEMENTATION NOTE</b>:  This method is designed to deal with
     * reloads required by changes to classes in the underlying repositories
     * of our class loader.  It does not handle changes to the web application
     * deployment descriptor.  If that has occurred, you should stop this
     * Context and create (and start) a new Context instance instead.
     *
     * @exception IllegalStateException if the <code>reloadable</code>
     *  property is set to <code>false</code>.
     */
    public synchronized void reload() {

        // Validate our current component state
        if (!started)
            throw new IllegalStateException
                (sm.getString("containerBase.notStarted", logName()));

        // Make sure reloading is enabled
        //      if (!reloadable)
        //          throw new IllegalStateException
        //              (sm.getString("standardContext.notReloadable"));
        if(log.isInfoEnabled())
            log.info(sm.getString("standardContext.reloadingStarted"));

        // Stop accepting requests temporarily
        setPaused(true);

        try {
            stop();
        } catch (LifecycleException e) {
            log.error(sm.getString("standardContext.stoppingContext"), e);
        }

        try {
            start();
        } catch (LifecycleException e) {
            log.error(sm.getString("standardContext.startingContext"), e);
        }

        setPaused(false);

    }


    /**
     * Remove the specified application listener class from the set of
     * listeners for this application.
     *
     * @param listener Java class name of the listener to be removed
     */
    public void removeApplicationListener(String listener) {

        synchronized (applicationListeners) {

            // Make sure this welcome file is currently present
            int n = -1;
            for (int i = 0; i < applicationListeners.length; i++) {
                if (applicationListeners[i].equals(listener)) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified constraint
            int j = 0;
            String results[] = new String[applicationListeners.length - 1];
            for (int i = 0; i < applicationListeners.length; i++) {
                if (i != n)
                    results[j++] = applicationListeners[i];
            }
            applicationListeners = results;

        }

        // Inform interested listeners
        fireContainerEvent("removeApplicationListener", listener);

        // FIXME - behavior if already started?

    }


    /**
     * Remove the application parameter with the specified name from
     * the set for this application.
     *
     * @param name Name of the application parameter to remove
     */
    public void removeApplicationParameter(String name) {

        synchronized (applicationParameters) {

            // Make sure this parameter is currently present
            int n = -1;
            for (int i = 0; i < applicationParameters.length; i++) {
                if (name.equals(applicationParameters[i].getName())) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified parameter
            int j = 0;
            ApplicationParameter results[] =
                new ApplicationParameter[applicationParameters.length - 1];
            for (int i = 0; i < applicationParameters.length; i++) {
                if (i != n)
                    results[j++] = applicationParameters[i];
            }
            applicationParameters = results;

        }

        // Inform interested listeners
        fireContainerEvent("removeApplicationParameter", name);

    }


    /**
     * Add a child Container, only if the proposed child is an implementation
     * of Wrapper.
     *
     * @param child Child container to be added
     *
     * @exception IllegalArgumentException if the proposed container is
     *  not an implementation of Wrapper
     */
    public void removeChild(Container child) {

        if (!(child instanceof Wrapper)) {
            throw new IllegalArgumentException
                (sm.getString("standardContext.notWrapper"));
        }

        super.removeChild(child);

    }


    /**
     * Remove the specified security constraint from this web application.
     *
     * @param constraint Constraint to be removed
     */
    public void removeConstraint(SecurityConstraint constraint) {

        synchronized (constraints) {

            // Make sure this constraint is currently present
            int n = -1;
            for (int i = 0; i < constraints.length; i++) {
                if (constraints[i].equals(constraint)) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified constraint
            int j = 0;
            SecurityConstraint results[] =
                new SecurityConstraint[constraints.length - 1];
            for (int i = 0; i < constraints.length; i++) {
                if (i != n)
                    results[j++] = constraints[i];
            }
            constraints = results;

        }

        // Inform interested listeners
        fireContainerEvent("removeConstraint", constraint);

    }


    /**
     * Remove the error page for the specified error code or
     * Java language exception, if it exists; otherwise, no action is taken.
     *
     * @param errorPage The error page definition to be removed
     */
    public void removeErrorPage(ErrorPage errorPage) {

        String exceptionType = errorPage.getExceptionType();
        if (exceptionType != null) {
            synchronized (exceptionPages) {
                exceptionPages.remove(exceptionType);
            }
        } else {
            synchronized (statusPages) {
                if (errorPage.getErrorCode() == 200) {
                    this.okErrorPage = null;
                }
                statusPages.remove(new Integer(errorPage.getErrorCode()));
            }
        }
        fireContainerEvent("removeErrorPage", errorPage);

    }


    /**
     * Remove the specified filter definition from this Context, if it exists;
     * otherwise, no action is taken.
     *
     * @param filterDef Filter definition to be removed
     */
    public void removeFilterDef(FilterDef filterDef) {

        synchronized (filterDefs) {
            filterDefs.remove(filterDef.getFilterName());
        }
        fireContainerEvent("removeFilterDef", filterDef);

    }


    /**
     * Remove a filter mapping from this Context.
     *
     * @param filterMap The filter mapping to be removed
     */
    public void removeFilterMap(FilterMap filterMap) {

        synchronized (filterMaps) {

            // Make sure this filter mapping is currently present
            int n = -1;
            for (int i = 0; i < filterMaps.length; i++) {
                if (filterMaps[i] == filterMap) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified filter mapping
            FilterMap results[] = new FilterMap[filterMaps.length - 1];
            System.arraycopy(filterMaps, 0, results, 0, n);
            System.arraycopy(filterMaps, n + 1, results, n,
                             (filterMaps.length - 1) - n);
            filterMaps = results;

        }

        // Inform interested listeners
        fireContainerEvent("removeFilterMap", filterMap);

    }


    /**
     * Remove a class name from the set of InstanceListener classes that
     * will be added to newly created Wrappers.
     *
     * @param listener Class name of an InstanceListener class to be removed
     */
    public void removeInstanceListener(String listener) {

        synchronized (instanceListeners) {

            // Make sure this welcome file is currently present
            int n = -1;
            for (int i = 0; i < instanceListeners.length; i++) {
                if (instanceListeners[i].equals(listener)) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified constraint
            int j = 0;
            String results[] = new String[instanceListeners.length - 1];
            for (int i = 0; i < instanceListeners.length; i++) {
                if (i != n)
                    results[j++] = instanceListeners[i];
            }
            instanceListeners = results;

        }

        // Inform interested listeners
        fireContainerEvent("removeInstanceListener", listener);

    }


    /**
     * Remove any message destination with the specified name.
     *
     * @param name Name of the message destination to remove
     */
    public void removeMessageDestination(String name) {

        synchronized (messageDestinations) {
            messageDestinations.remove(name);
        }
        fireContainerEvent("removeMessageDestination", name);

    }


    /**
     * Remove any message destination ref with the specified name.
     *
     * @param name Name of the message destination ref to remove
     */
    public void removeMessageDestinationRef(String name) {

        namingResources.removeMessageDestinationRef(name);
        fireContainerEvent("removeMessageDestinationRef", name);

    }


    /**
     * Remove the MIME mapping for the specified extension, if it exists;
     * otherwise, no action is taken.
     *
     * @param extension Extension to remove the mapping for
     */
    public void removeMimeMapping(String extension) {

        synchronized (mimeMappings) {
            mimeMappings.remove(extension);
        }
        fireContainerEvent("removeMimeMapping", extension);

    }


    /**
     * Remove the context initialization parameter with the specified
     * name, if it exists; otherwise, no action is taken.
     *
     * @param name Name of the parameter to remove
     */
    public void removeParameter(String name) {

        synchronized (parameters) {
            parameters.remove(name);
        }
        fireContainerEvent("removeParameter", name);

    }


    /**
     * Remove any security role reference for the specified name
     *
     * @param role Security role (as used in the application) to remove
     */
    public void removeRoleMapping(String role) {

        synchronized (roleMappings) {
            roleMappings.remove(role);
        }
        fireContainerEvent("removeRoleMapping", role);

    }


    /**
     * Remove any security role with the specified name.
     *
     * @param role Security role to remove
     */
    public void removeSecurityRole(String role) {

        synchronized (securityRoles) {

            // Make sure this security role is currently present
            int n = -1;
            for (int i = 0; i < securityRoles.length; i++) {
                if (role.equals(securityRoles[i])) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified security role
            int j = 0;
            String results[] = new String[securityRoles.length - 1];
            for (int i = 0; i < securityRoles.length; i++) {
                if (i != n)
                    results[j++] = securityRoles[i];
            }
            securityRoles = results;

        }

        // Inform interested listeners
        fireContainerEvent("removeSecurityRole", role);

    }


    /**
     * Remove any servlet mapping for the specified pattern, if it exists;
     * otherwise, no action is taken.
     *
     * @param pattern URL pattern of the mapping to remove
     */
    public void removeServletMapping(String pattern) {

        String name = null;
        synchronized (servletMappings) {
            name = (String) servletMappings.remove(pattern);
        }
        Wrapper wrapper = (Wrapper) findChild(name);
        if( wrapper != null ) {
            wrapper.removeMapping(pattern);
        }
        mapper.removeWrapper(pattern);
        fireContainerEvent("removeServletMapping", pattern);

    }


    /**
     * Remove the tag library location forthe specified tag library URI.
     *
     * @param uri URI, relative to the web.xml file
     */
    public void removeTaglib(String uri) {

        synchronized (taglibs) {
            taglibs.remove(uri);
        }
        fireContainerEvent("removeTaglib", uri);
    }


    /**
     * Remove the specified watched resource name from the list associated
     * with this Context.
     * 
     * @param name Name of the watched resource to be removed
     */
    public void removeWatchedResource(String name) {
        
        synchronized (watchedResources) {

            // Make sure this watched resource is currently present
            int n = -1;
            for (int i = 0; i < watchedResources.length; i++) {
                if (watchedResources[i].equals(name)) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified watched resource
            int j = 0;
            String results[] = new String[watchedResources.length - 1];
            for (int i = 0; i < watchedResources.length; i++) {
                if (i != n)
                    results[j++] = watchedResources[i];
            }
            watchedResources = results;

        }

        fireContainerEvent("removeWatchedResource", name);

    }
    
    
    /**
     * Remove the specified welcome file name from the list recognized
     * by this Context.
     *
     * @param name Name of the welcome file to be removed
     */
    public void removeWelcomeFile(String name) {

        synchronized (welcomeFiles) {

            // Make sure this welcome file is currently present
            int n = -1;
            for (int i = 0; i < welcomeFiles.length; i++) {
                if (welcomeFiles[i].equals(name)) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified constraint
            int j = 0;
            String results[] = new String[welcomeFiles.length - 1];
            for (int i = 0; i < welcomeFiles.length; i++) {
                if (i != n)
                    results[j++] = welcomeFiles[i];
            }
            welcomeFiles = results;

        }

        // Inform interested listeners
        postWelcomeFiles();
        fireContainerEvent("removeWelcomeFile", name);

    }


    /**
     * Remove a class name from the set of LifecycleListener classes that
     * will be added to newly created Wrappers.
     *
     * @param listener Class name of a LifecycleListener class to be removed
     */
    public void removeWrapperLifecycle(String listener) {


        synchronized (wrapperLifecycles) {

            // Make sure this welcome file is currently present
            int n = -1;
            for (int i = 0; i < wrapperLifecycles.length; i++) {
                if (wrapperLifecycles[i].equals(listener)) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified constraint
            int j = 0;
            String results[] = new String[wrapperLifecycles.length - 1];
            for (int i = 0; i < wrapperLifecycles.length; i++) {
                if (i != n)
                    results[j++] = wrapperLifecycles[i];
            }
            wrapperLifecycles = results;

        }

        // Inform interested listeners
        fireContainerEvent("removeWrapperLifecycle", listener);

    }


    /**
     * Remove a class name from the set of ContainerListener classes that
     * will be added to newly created Wrappers.
     *
     * @param listener Class name of a ContainerListener class to be removed
     */
    public void removeWrapperListener(String listener) {


        synchronized (wrapperListeners) {

            // Make sure this welcome file is currently present
            int n = -1;
            for (int i = 0; i < wrapperListeners.length; i++) {
                if (wrapperListeners[i].equals(listener)) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified constraint
            int j = 0;
            String results[] = new String[wrapperListeners.length - 1];
            for (int i = 0; i < wrapperListeners.length; i++) {
                if (i != n)
                    results[j++] = wrapperListeners[i];
            }
            wrapperListeners = results;

        }

        // Inform interested listeners
        fireContainerEvent("removeWrapperListener", listener);

    }


    /**
     * Gets the cumulative processing times of all servlets in this
     * StandardContext.
     *
     * @return Cumulative processing times of all servlets in this
     * StandardContext
     */
    public long getProcessingTime() {
        
        long result = 0;

        Container[] children = findChildren();
        if (children != null) {
            for( int i=0; i< children.length; i++ ) {
                result += ((StandardWrapper)children[i]).getProcessingTime();
            }
        }

        return result;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Configure and initialize the set of filters for this Context.
     * Return <code>true</code> if all filter initialization completed
     * successfully, or <code>false</code> otherwise.
     */
    public boolean filterStart() {

        if (getLogger().isDebugEnabled())
            getLogger().debug("Starting filters");
        // Instantiate and record a FilterConfig for each defined filter
        boolean ok = true;
        synchronized (filterConfigs) {
            filterConfigs.clear();
            Iterator names = filterDefs.keySet().iterator();
            while (names.hasNext()) {
                String name = (String) names.next();
                if (getLogger().isDebugEnabled())
                    getLogger().debug(" Starting filter '" + name + "'");
                ApplicationFilterConfig filterConfig = null;
                try {
                    filterConfig = new ApplicationFilterConfig
                      (this, (FilterDef) filterDefs.get(name));
                    filterConfigs.put(name, filterConfig);
                } catch (Throwable t) {
                    getLogger().error
                        (sm.getString("standardContext.filterStart", name), t);
                    ok = false;
                }
            }
        }

        return (ok);

    }


    /**
     * Finalize and release the set of filters for this Context.
     * Return <code>true</code> if all filter finalization completed
     * successfully, or <code>false</code> otherwise.
     */
    public boolean filterStop() {

        if (getLogger().isDebugEnabled())
            getLogger().debug("Stopping filters");

        // Release all Filter and FilterConfig instances
        synchronized (filterConfigs) {
            Iterator names = filterConfigs.keySet().iterator();
            while (names.hasNext()) {
                String name = (String) names.next();
                if (getLogger().isDebugEnabled())
                    getLogger().debug(" Stopping filter '" + name + "'");
                ApplicationFilterConfig filterConfig =
                  (ApplicationFilterConfig) filterConfigs.get(name);
                filterConfig.release();
            }
            filterConfigs.clear();
        }
        return (true);

    }


    /**
     * Find and return the initialized <code>FilterConfig</code> for the
     * specified filter name, if any; otherwise return <code>null</code>.
     *
     * @param name Name of the desired filter
     */
    public FilterConfig findFilterConfig(String name) {

        return ((FilterConfig) filterConfigs.get(name));

    }


    /**
     * Configure the set of instantiated application event listeners
     * for this Context.  Return <code>true</code> if all listeners wre
     * initialized successfully, or <code>false</code> otherwise.
     */
    public boolean listenerStart() {

        if (log.isDebugEnabled())
            log.debug("Configuring application event listeners");

        // Instantiate the required listeners
        ClassLoader loader = getLoader().getClassLoader();
        String listeners[] = findApplicationListeners();
        Object results[] = new Object[listeners.length];
        boolean ok = true;
        for (int i = 0; i < results.length; i++) {
            if (getLogger().isDebugEnabled())
                getLogger().debug(" Configuring event listener class '" +
                    listeners[i] + "'");
            try {
                Class clazz = loader.loadClass(listeners[i]);
                results[i] = clazz.newInstance();
                // Annotation processing
                if (!getIgnoreAnnotations()) {
                    if (getNamingContextListener() != null) {
                        AnnotationProcessor.injectNamingResources
                            (getNamingContextListener().getEnvContext(), results[i]);
                    }
                    AnnotationProcessor.postConstruct(results[i]);
                }
            } catch (Throwable t) {
                getLogger().error
                    (sm.getString("standardContext.applicationListener",
                                  listeners[i]), t);
                ok = false;
            }
        }
        if (!ok) {
            getLogger().error(sm.getString("standardContext.applicationSkipped"));
            return (false);
        }

        // Sort listeners in two arrays
        ArrayList eventListeners = new ArrayList();
        ArrayList lifecycleListeners = new ArrayList();
        for (int i = 0; i < results.length; i++) {
            if ((results[i] instanceof ServletContextAttributeListener)
                || (results[i] instanceof ServletRequestAttributeListener)
                || (results[i] instanceof ServletRequestListener)
                || (results[i] instanceof HttpSessionAttributeListener)) {
                eventListeners.add(results[i]);
            }
            if ((results[i] instanceof ServletContextListener)
                || (results[i] instanceof HttpSessionListener)) {
                lifecycleListeners.add(results[i]);
            }
        }

        setApplicationEventListeners(eventListeners.toArray());
        setApplicationLifecycleListeners(lifecycleListeners.toArray());

        // Send application start events

        if (getLogger().isDebugEnabled())
            getLogger().debug("Sending application start events");

        Object instances[] = getApplicationLifecycleListeners();
        if (instances == null)
            return (ok);
        ServletContextEvent event =
          new ServletContextEvent(getServletContext());
        for (int i = 0; i < instances.length; i++) {
            if (instances[i] == null)
                continue;
            if (!(instances[i] instanceof ServletContextListener))
                continue;
            ServletContextListener listener =
                (ServletContextListener) instances[i];
            try {
                fireContainerEvent("beforeContextInitialized", listener);
                listener.contextInitialized(event);
                fireContainerEvent("afterContextInitialized", listener);
            } catch (Throwable t) {
                fireContainerEvent("afterContextInitialized", listener);
                getLogger().error
                    (sm.getString("standardContext.listenerStart",
                                  instances[i].getClass().getName()), t);
                ok = false;
            }
        }
        return (ok);

    }


    /**
     * Send an application stop event to all interested listeners.
     * Return <code>true</code> if all events were sent successfully,
     * or <code>false</code> otherwise.
     */
    public boolean listenerStop() {

        if (log.isDebugEnabled())
            log.debug("Sending application stop events");

        boolean ok = true;
        Object listeners[] = getApplicationLifecycleListeners();
        if (listeners != null) {
            ServletContextEvent event =
                new ServletContextEvent(getServletContext());
            for (int i = 0; i < listeners.length; i++) {
                int j = (listeners.length - 1) - i;
                if (listeners[j] == null)
                    continue;
                if (listeners[j] instanceof ServletContextListener) {
                    ServletContextListener listener =
                        (ServletContextListener) listeners[j];
                    try {
                        fireContainerEvent("beforeContextDestroyed", listener);
                        listener.contextDestroyed(event);
                        fireContainerEvent("afterContextDestroyed", listener);
                    } catch (Throwable t) {
                        fireContainerEvent("afterContextDestroyed", listener);
                        getLogger().error
                            (sm.getString("standardContext.listenerStop",
                                listeners[j].getClass().getName()), t);
                        ok = false;
                    }
                }
                // Annotation processing
                if (!getIgnoreAnnotations()) {
                    try {
                        AnnotationProcessor.preDestroy(listeners[j]);
                    } catch (Throwable t) {
                        getLogger().error
                            (sm.getString("standardContext.listenerStop",
                                listeners[j].getClass().getName()), t);
                        ok = false;
                    }
                }
            }
        }

        // Annotation processing
        listeners = getApplicationEventListeners();
        if (!getIgnoreAnnotations() && listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                int j = (listeners.length - 1) - i;
                if (listeners[j] == null)
                    continue;
                try {
                    AnnotationProcessor.preDestroy(listeners[j]);
                } catch (Throwable t) {
                    getLogger().error
                        (sm.getString("standardContext.listenerStop",
                            listeners[j].getClass().getName()), t);
                    ok = false;
                }
            }
        }
        
        setApplicationEventListeners(null);
        setApplicationLifecycleListeners(null);

        return (ok);

    }


    /**
     * Allocate resources, including proxy.
     * Return <code>true</code> if initialization was successfull,
     * or <code>false</code> otherwise.
     */
    public boolean resourcesStart() {

        boolean ok = true;

        Hashtable env = new Hashtable();
        if (getParent() != null)
            env.put(ProxyDirContext.HOST, getParent().getName());
        env.put(ProxyDirContext.CONTEXT, getName());

        try {
            ProxyDirContext proxyDirContext =
                new ProxyDirContext(env, webappResources);
            if (webappResources instanceof FileDirContext) {
                filesystemBased = true;
                ((FileDirContext) webappResources).setCaseSensitive
                    (isCaseSensitive());
                ((FileDirContext) webappResources).setAllowLinking
                    (isAllowLinking());
            }
            if (webappResources instanceof BaseDirContext) {
                ((BaseDirContext) webappResources).setDocBase(getBasePath());
                ((BaseDirContext) webappResources).setCached
                    (isCachingAllowed());
                ((BaseDirContext) webappResources).setCacheTTL(getCacheTTL());
                ((BaseDirContext) webappResources).setCacheMaxSize
                    (getCacheMaxSize());
                ((BaseDirContext) webappResources).allocate();
            }
            // Register the cache in JMX
            if (isCachingAllowed()) {
                ObjectName resourcesName = 
                    new ObjectName(this.getDomain() + ":type=Cache,host=" 
                                   + getHostname() + ",path=" 
                                   + (("".equals(getPath()))?"/":getPath()));
                Registry.getRegistry(null, null).registerComponent
                    (proxyDirContext.getCache(), resourcesName, null);
            }
            this.resources = proxyDirContext;
        } catch (Throwable t) {
            log.error(sm.getString("standardContext.resourcesStart"), t);
            ok = false;
        }

        return (ok);

    }


    /**
     * Deallocate resources and destroy proxy.
     */
    public boolean resourcesStop() {

        boolean ok = true;

        try {
            if (resources != null) {
                if (resources instanceof Lifecycle) {
                    ((Lifecycle) resources).stop();
                }
                if (webappResources instanceof BaseDirContext) {
                    ((BaseDirContext) webappResources).release();
                }
                // Unregister the cache in JMX
                if (isCachingAllowed()) {
                    ObjectName resourcesName = 
                        new ObjectName(this.getDomain()
                                       + ":type=Cache,host=" 
                                       + getHostname() + ",path=" 
                                       + (("".equals(getPath()))?"/"
                                          :getPath()));
                    Registry.getRegistry(null, null)
                        .unregisterComponent(resourcesName);
                }
            }
        } catch (Throwable t) {
            log.error(sm.getString("standardContext.resourcesStop"), t);
            ok = false;
        }

        this.resources = null;

        return (ok);

    }


    /**
     * Load and initialize all servlets marked "load on startup" in the
     * web application deployment descriptor.
     *
     * @param children Array of wrappers for all currently defined
     *  servlets (including those not declared load on startup)
     */
    public void loadOnStartup(Container children[]) {

        // Collect "load on startup" servlets that need to be initialized
        TreeMap map = new TreeMap();
        for (int i = 0; i < children.length; i++) {
            Wrapper wrapper = (Wrapper) children[i];
            int loadOnStartup = wrapper.getLoadOnStartup();
            if (loadOnStartup < 0)
                continue;
            if (loadOnStartup == 0)     // Arbitrarily put them last
                loadOnStartup = Integer.MAX_VALUE;
            Integer key = new Integer(loadOnStartup);
            ArrayList list = (ArrayList) map.get(key);
            if (list == null) {
                list = new ArrayList();
                map.put(key, list);
            }
            list.add(wrapper);
        }

        // Load the collected "load on startup" servlets
        Iterator keys = map.keySet().iterator();
        while (keys.hasNext()) {
            Integer key = (Integer) keys.next();
            ArrayList list = (ArrayList) map.get(key);
            Iterator wrappers = list.iterator();
            while (wrappers.hasNext()) {
                Wrapper wrapper = (Wrapper) wrappers.next();
                try {
                    wrapper.load();
                } catch (ServletException e) {
                    getLogger().error(sm.getString("standardWrapper.loadException",
                                      getName()), StandardWrapper.getRootCause(e));
                    // NOTE: load errors (including a servlet that throws
                    // UnavailableException from tht init() method) are NOT
                    // fatal to application startup
                }
            }
        }

    }


    /**
     * Start this Context component.
     *
     * @exception LifecycleException if a startup error occurs
     */
    public synchronized void start() throws LifecycleException {
        //if (lazy ) return;
        if (started) {
            if(log.isInfoEnabled())
                log.info(sm.getString("containerBase.alreadyStarted", logName()));
            return;
        }
        if( !initialized ) { 
            try {
                init();
            } catch( Exception ex ) {
                throw new LifecycleException("Error initializaing ", ex);
            }
        }
        if(log.isDebugEnabled())
            log.debug("Starting " + ("".equals(getName()) ? "ROOT" : getName()));

        // Set JMX object name for proper pipeline registration
        preRegisterJMX();

        if ((oname != null) && 
            (Registry.getRegistry(null, null).getMBeanServer().isRegistered(oname))) {
            // As things depend on the JMX registration, the context
            // must be reregistered again once properly initialized
            Registry.getRegistry(null, null).unregisterComponent(oname);
        }

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(BEFORE_START_EVENT, null);

        setAvailable(false);
        setConfigured(false);
        boolean ok = true;

        // Add missing components as necessary
        if (webappResources == null) {   // (1) Required by Loader
            if (log.isDebugEnabled())
                log.debug("Configuring default Resources");
            try {
                if ((docBase != null) && (docBase.endsWith(".war")) && (!(new File(getBasePath())).isDirectory()))
                    setResources(new WARDirContext());
                else
                    setResources(new FileDirContext());
            } catch (IllegalArgumentException e) {
                log.error("Error initializing resources: " + e.getMessage());
                ok = false;
            }
        }
        if (ok) {
            if (!resourcesStart()) {
                log.error( "Error in resourceStart()");
                ok = false;
            }
        }

        // Look for a realm - that may have been configured earlier. 
        // If the realm is added after context - it'll set itself.
        // TODO: what is the use case for this ? 
        if( realm == null && mserver != null ) {
            ObjectName realmName=null;
            try {
                realmName=new ObjectName( getEngineName() + ":type=Realm,host=" + 
                        getHostname() + ",path=" + getPath());
                if( mserver.isRegistered(realmName ) ) {
                    mserver.invoke(realmName, "init", 
                            new Object[] {},
                            new String[] {}
                    );            
                }
            } catch( Throwable t ) {
                if(log.isDebugEnabled())
                    log.debug("No realm for this host " + realmName);
            }
        }
        
        if (getLoader() == null) {
            ClassLoader parent = null;
            if (getPrivileged()) {
                if (log.isDebugEnabled())
                    log.debug("Configuring privileged default Loader");
                parent = this.getClass().getClassLoader();
            } else {
                if (log.isDebugEnabled())
                    log.debug("Configuring non-privileged default Loader");
                parent = getParentClassLoader();
            }
            WebappLoader webappLoader = new WebappLoader(parent);
            webappLoader.setDelegate(getDelegate());
            setLoader(webappLoader);
        }

        // Initialize character set mapper
        getCharsetMapper();

        // Post work directory
        postWorkDirectory();

        // Validate required extensions
        boolean dependencyCheck = true;
        try {
            dependencyCheck = ExtensionValidator.validateApplication
                (getResources(), this);
        } catch (IOException ioe) {
            log.error("Error in dependencyCheck", ioe);
            dependencyCheck = false;
        }

        if (!dependencyCheck) {
            // do not make application available if depency check fails
            ok = false;
        }

        // Reading the "catalina.useNaming" environment variable
        String useNamingProperty = System.getProperty("catalina.useNaming");
        if ((useNamingProperty != null)
            && (useNamingProperty.equals("false"))) {
            useNaming = false;
        }

        if (ok && isUseNaming()) {
            if (namingContextListener == null) {
                namingContextListener = new NamingContextListener();
                namingContextListener.setName(getNamingContextName());
                addLifecycleListener(namingContextListener);
            }
        }

        // Standard container startup
        if (log.isDebugEnabled())
            log.debug("Processing standard container startup");

        // Acquire clustered manager
        Manager contextManager = null;
        if (manager == null) {
        	if ((getCluster() != null) && distributable) {
        		try {
        			contextManager = getCluster().createManager(getName());
        		} catch (Exception ex) {
        			log.error("standardContext.clusterFail", ex);
        			ok = false;
        		}
        	} else {
        		contextManager = new StandardManager();
        	}
        }
        
        
        // Binding thread
        ClassLoader oldCCL = bindThread();

        boolean mainOk = false;

        try {

            if (ok) {
                
                started = true;

                // Start our subordinate components, if any
                if ((loader != null) && (loader instanceof Lifecycle))
                    ((Lifecycle) loader).start();

                // Unbinding thread
                unbindThread(oldCCL);

                // Binding thread
                oldCCL = bindThread();

                // Initialize logger again. Other components might have used it too early, 
                // so it should be reset.
                logger = null;
                getLogger();
                if ((logger != null) && (logger instanceof Lifecycle))
                    ((Lifecycle) logger).start();
                
                if ((cluster != null) && (cluster instanceof Lifecycle))
                    ((Lifecycle) cluster).start();
                if ((realm != null) && (realm instanceof Lifecycle))
                    ((Lifecycle) realm).start();
                if ((resources != null) && (resources instanceof Lifecycle))
                    ((Lifecycle) resources).start();

                // Start our child containers, if any
                Container children[] = findChildren();
                for (int i = 0; i < children.length; i++) {
                    if (children[i] instanceof Lifecycle)
                        ((Lifecycle) children[i]).start();
                }

                // Start the Valves in our pipeline (including the basic),
                // if any
                if (pipeline instanceof Lifecycle) {
                    ((Lifecycle) pipeline).start();
                }
                
                if(getProcessTlds()) {
                    processTlds();
                }
                
                // Notify our interested LifecycleListeners
                lifecycle.fireLifecycleEvent(START_EVENT, null);

                // Configure default manager if none was specified
                if (contextManager != null) {
                	setManager(contextManager);
                }
                
                // Start manager
                if ((manager != null) && (manager instanceof Lifecycle)) {
                    ((Lifecycle) getManager()).start();
                }

                // Start ContainerBackgroundProcessor thread
                super.threadStart();

                mainOk = true;

            }

        } finally {
            // Unbinding thread
            unbindThread(oldCCL);
            if (!mainOk) {
                // An exception occurred
                // Register with JMX anyway, to allow management
                registerJMX();
            }
        }

        if (!getConfigured()) {
            log.error( "Error getConfigured");
            ok = false;
        }

        // We put the resources into the servlet context
        if (ok)
            getServletContext().setAttribute
                (Globals.RESOURCES_ATTR, getResources());

        // Initialize associated mapper
        mapper.setContext(getPath(), welcomeFiles, resources);

        // Binding thread
        oldCCL = bindThread();

        // Set annotation processing parameter for Jasper (unfortunately, since
        // this can be configured in many places and not just in /WEB-INF/web.xml,
        // there are not many solutions)
        if (ignoreAnnotations) {
            Wrapper jspServlet = (Wrapper) findChild(Constants.JSP_SERVLET_NAME);
            jspServlet.addInitParameter("org.apache.jasper.IGNORE_ANNOTATIONS", "true");
        }
        
        try {
            
            // Create context attributes that will be required
            if (ok) {
                postWelcomeFiles();
            }
            
            if (ok) {
                // Notify our interested LifecycleListeners
                lifecycle.fireLifecycleEvent(AFTER_START_EVENT, null);
            }
            
            // Configure and call application event listeners and filters
            if (ok) {
                if (!listenerStart()) {
                    log.error( "Error listenerStart");
                    ok = false;
                }
            }
            if (ok) {
                if (!filterStart()) {
                    log.error( "Error filterStart");
                    ok = false;
                }
            }
            
            // Load and initialize all "load on startup" servlets
            if (ok) {
                loadOnStartup(findChildren());
            }
            
        } finally {
            // Unbinding thread
            unbindThread(oldCCL);
        }

        // Set available status depending upon startup success
        if (ok) {
            if (log.isDebugEnabled())
                log.debug("Starting completed");
            setAvailable(true);
        } else {
            log.error(sm.getString("standardContext.startFailed", getName()));
            try {
                stop();
            } catch (Throwable t) {
                log.error(sm.getString("standardContext.startCleanup"), t);
            }
            setAvailable(false);
        }

        // JMX registration
        registerJMX();

        startTime=System.currentTimeMillis();
        
        // Send j2ee.state.running notification 
        if (ok && (this.getObjectName() != null)) {
            Notification notification = 
                new Notification("j2ee.state.running", this.getObjectName(), 
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        }

        // Close all JARs right away to avoid always opening a peak number 
        // of files on startup
        if (getLoader() instanceof WebappLoader) {
            ((WebappLoader) getLoader()).closeJARs(true);
        }

        // Reinitializing if something went wrong
        if (!ok && started) {
            stop();
        }

        //cacheContext();
    }

    /**
     * Processes TLDs.
     *
     * @throws LifecycleException If an error occurs
     */
     protected void processTlds() throws LifecycleException {
       TldConfig tldConfig = new TldConfig();
       tldConfig.setContext(this);

       // (1)  check if the attribute has been defined
       //      on the context element.
       tldConfig.setTldValidation(tldValidation);
       tldConfig.setTldNamespaceAware(tldNamespaceAware);

       // (2) if the attribute wasn't defined on the context
       //     try the host.
       if (!tldValidation) {
         tldConfig.setTldValidation
           (((StandardHost) getParent()).getXmlValidation());
       }

       if (!tldNamespaceAware) {
         tldConfig.setTldNamespaceAware
           (((StandardHost) getParent()).getXmlNamespaceAware());
       }
                    
       try {
         tldConfig.execute();
       } catch (Exception ex) {
         log.error("Error reading tld listeners " 
                    + ex.toString(), ex); 
       }
     }
    
    private void cacheContext() {
        try {
            File workDir=new File( getWorkPath() );
            
            File ctxSer=new File( workDir, "_tomcat_context.ser");
            FileOutputStream fos=new FileOutputStream( ctxSer );
            ObjectOutputStream oos=new ObjectOutputStream( fos );
            oos.writeObject(this);
            oos.close();
            fos.close();
        } catch( Throwable t ) {
            if(log.isInfoEnabled())
                log.info("Error saving context.ser ", t);
        }
    }

    
    /**
     * Stop this Context component.
     *
     * @exception LifecycleException if a shutdown error occurs
     */
    public synchronized void stop() throws LifecycleException {

        // Validate and update our current component state
        if (!started) {
            if(log.isInfoEnabled())
                log.info(sm.getString("containerBase.notStarted", logName()));
            return;
        }

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(BEFORE_STOP_EVENT, null);
        
        // Send j2ee.state.stopping notification 
        if (this.getObjectName() != null) {
            Notification notification = 
                new Notification("j2ee.state.stopping", this.getObjectName(), 
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        }
        
        // Mark this application as unavailable while we shut down
        setAvailable(false);

        // Binding thread
        ClassLoader oldCCL = bindThread();

        try {

            // Stop our filters
            filterStop();

            // Stop our application listeners
            listenerStop();

            // Stop ContainerBackgroundProcessor thread
            super.threadStop();

            if ((manager != null) && (manager instanceof Lifecycle)) {
                ((Lifecycle) manager).stop();
            }

            // Finalize our character set mapper
            setCharsetMapper(null);

            // Normal container shutdown processing
            if (log.isDebugEnabled())
                log.debug("Processing standard container shutdown");
            // Notify our interested LifecycleListeners
            lifecycle.fireLifecycleEvent(STOP_EVENT, null);
            started = false;

            // Stop the Valves in our pipeline (including the basic), if any
            if (pipeline instanceof Lifecycle) {
                ((Lifecycle) pipeline).stop();
            }

            // Stop our child containers, if any
            Container[] children = findChildren();
            for (int i = 0; i < children.length; i++) {
                if (children[i] instanceof Lifecycle)
                    ((Lifecycle) children[i]).stop();
            }

            // Clear all application-originated servlet context attributes
            if (context != null)
                context.clearAttributes();

            // Stop resources
            resourcesStop();

            if ((realm != null) && (realm instanceof Lifecycle)) {
                ((Lifecycle) realm).stop();
            }
            if ((cluster != null) && (cluster instanceof Lifecycle)) {
                ((Lifecycle) cluster).stop();
            }
            if ((logger != null) && (logger instanceof Lifecycle)) {
                ((Lifecycle) logger).stop();
            }
            if ((loader != null) && (loader instanceof Lifecycle)) {
                ((Lifecycle) loader).stop();
            }

        } finally {

            // Unbinding thread
            unbindThread(oldCCL);

        }

        // Send j2ee.state.stopped notification 
        if (this.getObjectName() != null) {
            Notification notification = 
                new Notification("j2ee.state.stopped", this.getObjectName(), 
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        }
        
        // Reset application context
        context = null;

        // This object will no longer be visible or used. 
        try {
            resetContext();
        } catch( Exception ex ) {
            log.error( "Error reseting context " + this + " " + ex, ex );
        }
        
        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(AFTER_STOP_EVENT, null);

        if (log.isDebugEnabled())
            log.debug("Stopping complete");

    }

    /** Destroy needs to clean up the context completely.
     * 
     * The problem is that undoing all the config in start() and restoring 
     * a 'fresh' state is impossible. After stop()/destroy()/init()/start()
     * we should have the same state as if a fresh start was done - i.e
     * read modified web.xml, etc. This can only be done by completely 
     * removing the context object and remapping a new one, or by cleaning
     * up everything.
     * 
     * XXX Should this be done in stop() ?
     * 
     */ 
    public void destroy() throws Exception {
        if( oname != null ) { 
            // Send j2ee.object.deleted notification 
            Notification notification = 
                new Notification("j2ee.object.deleted", this.getObjectName(), 
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        } 
        super.destroy();

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(DESTROY_EVENT, null);

        instanceListeners = new String[0];

    }
    
    private void resetContext() throws Exception, MBeanRegistrationException {
        // Restore the original state ( pre reading web.xml in start )
        // If you extend this - override this method and make sure to clean up
        children=new HashMap();
        startupTime = 0;
        startTime = 0;
        tldScanTime = 0;

        // Bugzilla 32867
        distributable = false;

        applicationListeners = new String[0];
        applicationEventListenersObjects = new Object[0];
        applicationLifecycleListenersObjects = new Object[0];
        
        if(log.isDebugEnabled())
            log.debug("resetContext " + oname);
    }

    /**
     * Return a String representation of this component.
     */
    public String toString() {

        StringBuffer sb = new StringBuffer();
        if (getParent() != null) {
            sb.append(getParent().toString());
            sb.append(".");
        }
        sb.append("StandardContext[");
        sb.append(getName());
        sb.append("]");
        return (sb.toString());

    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Adjust the URL pattern to begin with a leading slash, if appropriate
     * (i.e. we are running a servlet 2.2 application).  Otherwise, return
     * the specified URL pattern unchanged.
     *
     * @param urlPattern The URL pattern to be adjusted (if needed)
     *  and returned
     */
    protected String adjustURLPattern(String urlPattern) {

        if (urlPattern == null)
            return (urlPattern);
        if (urlPattern.startsWith("/") || urlPattern.startsWith("*."))
            return (urlPattern);
        if (!isServlet22())
            return (urlPattern);
        if(log.isDebugEnabled())
            log.debug(sm.getString("standardContext.urlPattern.patternWarning",
                         urlPattern));
        return ("/" + urlPattern);

    }


    /**
     * Are we processing a version 2.2 deployment descriptor?
     */
    protected boolean isServlet22() {

        if (this.publicId == null)
            return (false);
        if (this.publicId.equals
            (org.apache.catalina.startup.Constants.WebDtdPublicId_22))
            return (true);
        else
            return (false);

    }


    /**
     * Return a File object representing the base directory for the
     * entire servlet container (i.e. the Engine container if present).
     */
    protected File engineBase() {
        String base=System.getProperty("catalina.base");
        if( base == null ) {
            StandardEngine eng=(StandardEngine)this.getParent().getParent();
            base=eng.getBaseDir();
        }
        return (new File(base));
    }


    // -------------------------------------------------------- Private Methods


    /**
     * Bind current thread, both for CL purposes and for JNDI ENC support
     * during : startup, shutdown and realoading of the context.
     *
     * @return the previous context class loader
     */
    private ClassLoader bindThread() {

        ClassLoader oldContextClassLoader =
            Thread.currentThread().getContextClassLoader();

        if (getResources() == null)
            return oldContextClassLoader;

        if (getLoader().getClassLoader() != null) {
            Thread.currentThread().setContextClassLoader
                (getLoader().getClassLoader());
        }

        DirContextURLStreamHandler.bind(getResources());

        if (isUseNaming()) {
            try {
                ContextBindings.bindThread(this, this);
            } catch (NamingException e) {
                // Silent catch, as this is a normal case during the early
                // startup stages
            }
        }

        return oldContextClassLoader;

    }


    /**
     * Unbind thread.
     */
    private void unbindThread(ClassLoader oldContextClassLoader) {

        Thread.currentThread().setContextClassLoader(oldContextClassLoader);

        oldContextClassLoader = null;

        if (isUseNaming()) {
            ContextBindings.unbindThread(this, this);
        }

        DirContextURLStreamHandler.unbind();

    }



    /**
     * Get base path.
     */
    protected String getBasePath() {
        String docBase = null;
        Container container = this;
        while (container != null) {
            if (container instanceof Host)
                break;
            container = container.getParent();
        }
        File file = new File(getDocBase());
        if (!file.isAbsolute()) {
            if (container == null) {
                docBase = (new File(engineBase(), getDocBase())).getPath();
            } else {
                // Use the "appBase" property of this container
                String appBase = ((Host) container).getAppBase();
                file = new File(appBase);
                if (!file.isAbsolute())
                    file = new File(engineBase(), appBase);
                docBase = (new File(file, getDocBase())).getPath();
            }
        } else {
            docBase = file.getPath();
        }
        return docBase;
    }


    /**
     * Get app base.
     */
    protected String getAppBase() {
        String appBase = null;
        Container container = this;
        while (container != null) {
            if (container instanceof Host)
                break;
            container = container.getParent();
        }
        if (container != null) {
            appBase = ((Host) container).getAppBase();
        }
        return appBase;
    }


    /**
     * Get config base.
     */
    public File getConfigBase() {
        File configBase = 
            new File(System.getProperty("catalina.base"), "conf");
        if (!configBase.exists()) {
            return null;
        }
        Container container = this;
        Container host = null;
        Container engine = null;
        while (container != null) {
            if (container instanceof Host)
                host = container;
            if (container instanceof Engine)
                engine = container;
            container = container.getParent();
        }
        if (engine != null) {
            configBase = new File(configBase, engine.getName());
        }
        if (host != null) {
            configBase = new File(configBase, host.getName());
        }
        if (saveConfig) {
            configBase.mkdirs();
        }
        return configBase;
    }


    /**
     * Given a context path, get the config file name.
     */
    protected String getDefaultConfigFile() {
        String basename = null;
        String path = getPath();
        if (path.equals("")) {
            basename = "ROOT";
        } else {
            basename = path.substring(1).replace('/', '#');
        }
        return (basename + ".xml");
    }


    /**
     * Copy a file.
     */
    private boolean copy(File src, File dest) {
        FileInputStream is = null;
        FileOutputStream os = null;
        try {
            is = new FileInputStream(src);
            os = new FileOutputStream(dest);
            byte[] buf = new byte[4096];
            while (true) {
                int len = is.read(buf);
                if (len < 0)
                    break;
                os.write(buf, 0, len);
            }
            is.close();
            os.close();
        } catch (IOException e) {
            return false;
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (Exception e) {
                // Ignore
            }
            try {
                if (os != null) {
                    os.close();
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        return true;
    }


    /**
     * Get naming context full name.
     */
    private String getNamingContextName() {
    if (namingContextName == null) {
        Container parent = getParent();
        if (parent == null) {
        namingContextName = getName();
        } else {
        Stack stk = new Stack();
        StringBuffer buff = new StringBuffer();
        while (parent != null) {
            stk.push(parent.getName());
            parent = parent.getParent();
        }
        while (!stk.empty()) {
            buff.append("/" + stk.pop());
        }
        buff.append(getName());
        namingContextName = buff.toString();
        }
    }
    return namingContextName;
    }

    
    /**
     * Naming context listener accessor.
     */
    public NamingContextListener getNamingContextListener() {
        return namingContextListener;
    }
    

    /**
     * Naming context listener setter.
     */
    public void setNamingContextListener(NamingContextListener namingContextListener) {
        this.namingContextListener = namingContextListener;
    }
    

    /**
     * Return the request processing paused flag for this Context.
     */
    public boolean getPaused() {

        return (this.paused);

    }


    /**
     * Post a copy of our web application resources as a servlet context
     * attribute.
     */
    private void postResources() {

        getServletContext().setAttribute
            (Globals.RESOURCES_ATTR, getResources());

    }


    /**
     * Post a copy of our current list of welcome files as a servlet context
     * attribute, so that the default servlet can find them.
     */
    private void postWelcomeFiles() {

        getServletContext().setAttribute("org.apache.catalina.WELCOME_FILES",
                                         welcomeFiles);

    }

    public String getHostname() {
        Container parentHost = getParent();
        if (parentHost != null) {
            hostName = parentHost.getName();
        }
        if ((hostName == null) || (hostName.length() < 1))
            hostName = "_";
        return hostName;
    }

    /**
     * Set the appropriate context attribute for our work directory.
     */
    private void postWorkDirectory() {

        // Acquire (or calculate) the work directory path
        String workDir = getWorkDir();
        if (workDir == null) {

            // Retrieve our parent (normally a host) name
            String hostName = null;
            String engineName = null;
            String hostWorkDir = null;
            Container parentHost = getParent();
            if (parentHost != null) {
                hostName = parentHost.getName();
                if (parentHost instanceof StandardHost) {
                    hostWorkDir = ((StandardHost)parentHost).getWorkDir();
                }
                Container parentEngine = parentHost.getParent();
                if (parentEngine != null) {
                   engineName = parentEngine.getName();
                }
            }
            if ((hostName == null) || (hostName.length() < 1))
                hostName = "_";
            if ((engineName == null) || (engineName.length() < 1))
                engineName = "_";

            String temp = getPath();
            if (temp.startsWith("/"))
                temp = temp.substring(1);
            temp = temp.replace('/', '_');
            temp = temp.replace('\\', '_');
            if (temp.length() < 1)
                temp = "_";
            if (hostWorkDir != null ) {
                workDir = hostWorkDir + File.separator + temp;
            } else {
                workDir = "work" + File.separator + engineName +
                    File.separator + hostName + File.separator + temp;
            }
            setWorkDir(workDir);
        }

        // Create this directory if necessary
        File dir = new File(workDir);
        if (!dir.isAbsolute()) {
            File catalinaHome = engineBase();
            String catalinaHomePath = null;
            try {
                catalinaHomePath = catalinaHome.getCanonicalPath();
                dir = new File(catalinaHomePath, workDir);
            } catch (IOException e) {
            }
        }
        dir.mkdirs();

        // Set the appropriate servlet context attribute
        getServletContext().setAttribute(Globals.WORK_DIR_ATTR, dir);
        if (getServletContext() instanceof ApplicationContext)
            ((ApplicationContext) getServletContext()).setAttributeReadOnly
                (Globals.WORK_DIR_ATTR);

    }


    /**
     * Set the request processing paused flag for this Context.
     *
     * @param paused The new request processing paused flag
     */
    private void setPaused(boolean paused) {

        this.paused = paused;

    }


    /**
     * Validate the syntax of a proposed <code>&lt;url-pattern&gt;</code>
     * for conformance with specification requirements.
     *
     * @param urlPattern URL pattern to be validated
     */
    private boolean validateURLPattern(String urlPattern) {

        if (urlPattern == null)
            return (false);
        if (urlPattern.indexOf('\n') >= 0 || urlPattern.indexOf('\r') >= 0) {
            getLogger().warn(sm.getString("standardContext.crlfinurl",urlPattern));
        }
        if (urlPattern.startsWith("*.")) {
            if (urlPattern.indexOf('/') < 0)
                return (true);
            else
                return (false);
        }
        if ( (urlPattern.startsWith("/")) &&
                (urlPattern.indexOf("*.") < 0))
            return (true);
        else
            return (false);

    }


    // ------------------------------------------------------------- Operations


    /**
     * JSR77 deploymentDescriptor attribute
     *
     * @return string deployment descriptor 
     */
    public String getDeploymentDescriptor() {
    
        InputStream stream = null;
        ServletContext servletContext = getServletContext();
        if (servletContext != null) {
            stream = servletContext.getResourceAsStream(
                org.apache.catalina.startup.Constants.ApplicationWebXml);
        }
        if (stream == null) {
            return "";
        }
        BufferedReader br = new BufferedReader(
                                new InputStreamReader(stream));
        StringBuffer sb = new StringBuffer();
        String strRead = "";
        try {
            while (strRead != null) {
                sb.append(strRead);
                strRead = br.readLine();
            }
        } catch (IOException e) {
            return "";
        }

        return sb.toString(); 
    
    }
    
    
    /**
     * JSR77 servlets attribute
     *
     * @return list of all servlets ( we know about )
     */
    public String[] getServlets() {
        
        String[] result = null;

        Container[] children = findChildren();
        if (children != null) {
            result = new String[children.length];
            for( int i=0; i< children.length; i++ ) {
                result[i] = ((StandardWrapper)children[i]).getObjectName();
            }
        }

        return result;
    }
    

    public ObjectName createObjectName(String hostDomain, ObjectName parentName)
            throws MalformedObjectNameException
    {
        String onameStr;
        StandardHost hst=(StandardHost)getParent();
        
        String pathName=getName();
        String hostName=getParent().getName();
        String name= "//" + ((hostName==null)? "DEFAULT" : hostName) +
                (("".equals(pathName))?"/":pathName );

        String suffix=",J2EEApplication=" +
                getJ2EEApplication() + ",J2EEServer=" +
                getJ2EEServer();

        onameStr="j2eeType=WebModule,name=" + name + suffix;
        if( log.isDebugEnabled())
            log.debug("Registering " + onameStr + " for " + oname);
        
        // default case - no domain explictely set.
        if( getDomain() == null ) domain=hst.getDomain();

        ObjectName oname=new ObjectName(getDomain() + ":" + onameStr);
        return oname;        
    }    
    
    private void preRegisterJMX() {
        try {
            StandardHost host = (StandardHost) getParent();
            if ((oname == null) 
                || (oname.getKeyProperty("j2eeType") == null)) {
                oname = createObjectName(host.getDomain(), host.getJmxName());
                controller = oname;
            }
        } catch(Exception ex) {
            if(log.isInfoEnabled())
                log.info("Error registering ctx with jmx " + this + " " +
                     oname + " " + ex.toString(), ex );
        }
    }

    private void registerJMX() {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Checking for " + oname );
            }
            if(! Registry.getRegistry(null, null)
                .getMBeanServer().isRegistered(oname)) {
                controller = oname;
                Registry.getRegistry(null, null)
                    .registerComponent(this, oname, null);
                
                // Send j2ee.object.created notification 
                if (this.getObjectName() != null) {
                    Notification notification = new Notification(
                                                        "j2ee.object.created", 
                                                        this.getObjectName(), 
                                                        sequenceNumber++);
                    broadcaster.sendNotification(notification);
                }
            }
            Container children[] = findChildren();
            for (int i=0; children!=null && i<children.length; i++) {
                ((StandardWrapper)children[i]).registerJMX( this );
            }
        } catch (Exception ex) {
            if(log.isInfoEnabled())
                log.info("Error registering wrapper with jmx " + this + " " +
                    oname + " " + ex.toString(), ex );
        }
    }

    /** There are 2 cases:
     *   1.The context is created and registered by internal APIS
     *   2. The context is created by JMX, and it'll self-register.
     *
     * @param server The server
     * @param name The object name
     * @return ObjectName The name of the object
     * @throws Exception If an error occurs
     */
    public ObjectName preRegister(MBeanServer server,
                                  ObjectName name)
            throws Exception
    {
        if( oname != null ) {
            //log.info( "Already registered " + oname + " " + name);
            // Temporary - /admin uses the old names
            return name;
        }
        ObjectName result=super.preRegister(server,name);
        return name;
    }

    public void preDeregister() throws Exception {
        if( started ) {
            try {
                stop();
            } catch( Exception ex ) {
                log.error( "error stopping ", ex);
            }
        }
    }

    public void init() throws Exception {

        if( this.getParent() == null ) {
            ObjectName parentName=getParentName();
            
            if( ! mserver.isRegistered(parentName)) {
                if(log.isDebugEnabled())
                    log.debug("No host, creating one " + parentName);
                StandardHost host=new StandardHost();
                host.setName(hostName);
                host.setAutoDeploy(false);
                Registry.getRegistry(null, null)
                    .registerComponent(host, parentName, null);
                // We could do it the hard way...
                //mserver.invoke(parentName, "init", new Object[] {}, new String[] {} );
                // or same thing easier:
                host.init();
            }
            
            // Add the main configuration listener
            LifecycleListener config = null;
            try {
                String configClassName = ((Host)getParent()).getConfigClass();
                if (configClassName != null) {
                    Class clazz = Class.forName(configClassName);
                    config = (LifecycleListener) clazz.newInstance();
                } else {
                    config = new ContextConfig();
                }
            } catch (Exception e) {
                log.warn("Error creating ContextConfig for " + parentName, e);
                throw e;
            }
            this.addLifecycleListener(config);

            if (log.isDebugEnabled()) {
                log.debug("AddChild " + parentName + " " + this);
            }
            try {
                ((Host)getParent()).addChild(this);
            } catch (Exception e) {
                destroy();
                throw e;
            }
            // It's possible that addChild may have started us
            if( initialized ) {
                return;
            }
        }
        super.init();
        
        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(INIT_EVENT, null);

        // Send j2ee.state.starting notification 
        if (this.getObjectName() != null) {
            Notification notification = new Notification("j2ee.state.starting", 
                                                        this.getObjectName(), 
                                                        sequenceNumber++);
            broadcaster.sendNotification(notification);
        }
        
    }

    public ObjectName getParentName() throws MalformedObjectNameException {
        // "Life" update
        String path=oname.getKeyProperty("name");
        if( path == null ) {
            log.error( "No name attribute " +name );
            return null;
        }
        if( ! path.startsWith( "//")) {
            log.error("Invalid name " + name);
        }
        path=path.substring(2);
        int delim=path.indexOf( "/" );
        hostName="localhost"; // Should be default...
        if( delim > 0 ) {
            hostName=path.substring(0, delim);
            path = path.substring(delim);
            if (path.equals("/")) {
                this.setName("");
            } else {
                this.setName(path);
            }
        } else {
            if(log.isDebugEnabled())
                log.debug("Setting path " +  path );
            this.setName( path );
        }
        // XXX The service and domain should be the same.
        String parentDomain=getEngineName();
        if( parentDomain == null ) parentDomain=domain;
        ObjectName parentName=new ObjectName( parentDomain + ":" +
                "type=Host,host=" + hostName);
        return parentName;
    }
    
    public void create() throws Exception{
        init();
    }

    /* Remove a JMX notficationListener 
     * @see javax.management.NotificationEmitter#removeNotificationListener(javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
     */
    public void removeNotificationListener(NotificationListener listener, 
            NotificationFilter filter, Object object) throws ListenerNotFoundException {
    	broadcaster.removeNotificationListener(listener,filter,object);
    	
    }
    
    private MBeanNotificationInfo[] notificationInfo;
    
    /* Get JMX Broadcaster Info
     * @TODO use StringManager for international support!
     * @TODO This two events we not send j2ee.state.failed and j2ee.attribute.changed!
     * @see javax.management.NotificationBroadcaster#getNotificationInfo()
     */
    public MBeanNotificationInfo[] getNotificationInfo() {
    	// FIXME: i18n
    	if(notificationInfo == null) {
    		notificationInfo = new MBeanNotificationInfo[]{
    				new MBeanNotificationInfo(new String[] {
    				"j2ee.object.created"},
					Notification.class.getName(),
					"web application is created"
    				), 
					new MBeanNotificationInfo(new String[] {
					"j2ee.state.starting"},
					Notification.class.getName(),
					"change web application is starting"
					),
					new MBeanNotificationInfo(new String[] {
					"j2ee.state.running"},
					Notification.class.getName(),
					"web application is running"
					),
					new MBeanNotificationInfo(new String[] {
					"j2ee.state.stopped"},
					Notification.class.getName(),
					"web application start to stopped"
					),
					new MBeanNotificationInfo(new String[] {
					"j2ee.object.stopped"},
					Notification.class.getName(),
					"web application is stopped"
					),
					new MBeanNotificationInfo(new String[] {
					"j2ee.object.deleted"},
					Notification.class.getName(),
					"web application is deleted"
					)
    		};
    		
    	}
    	
    	return notificationInfo;
    }
    
    
    /* Add a JMX-NotificationListener
     * @see javax.management.NotificationBroadcaster#addNotificationListener(javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
     */
    public void addNotificationListener(NotificationListener listener, 
            NotificationFilter filter, Object object) throws IllegalArgumentException {
    	broadcaster.addNotificationListener(listener,filter,object);
    	
    }
    
    
    /**
     * Remove a JMX-NotificationListener 
     * @see javax.management.NotificationBroadcaster#removeNotificationListener(javax.management.NotificationListener)
     */
    public void removeNotificationListener(NotificationListener listener) 
    throws ListenerNotFoundException {
    	broadcaster.removeNotificationListener(listener);
    	
    }
    
    
    // ------------------------------------------------------------- Attributes


    /**
     * Return the naming resources associated with this web application.
     */
    public javax.naming.directory.DirContext getStaticResources() {

        return getResources();

    }


    /**
     * Return the naming resources associated with this web application.
     * FIXME: Fooling introspection ... 
     */
    public javax.naming.directory.DirContext findStaticResources() {

        return getResources();

    }


    /**
     * Return the naming resources associated with this web application.
     */
    public String[] getWelcomeFiles() {

        return findWelcomeFiles();

    }

     /**
     * Set the validation feature of the XML parser used when
     * parsing xml instances.
     * @param webXmlValidation true to enable xml instance validation
     */
    public void setXmlValidation(boolean webXmlValidation){
        
        this.webXmlValidation = webXmlValidation;

    }

    /**
     * Get the server.xml <context> attribute's xmlValidation.
     * @return true if validation is enabled.
     *
     */
    public boolean getXmlValidation(){
        return webXmlValidation;
    }


    /**
     * Get the server.xml <context> attribute's xmlNamespaceAware.
     * @return true if namespace awarenes is enabled.
     */
    public boolean getXmlNamespaceAware(){
        return webXmlNamespaceAware;
    }


    /**
     * Set the namespace aware feature of the XML parser used when
     * parsing xml instances.
     * @param webXmlNamespaceAware true to enable namespace awareness
     */
    public void setXmlNamespaceAware(boolean webXmlNamespaceAware){
        this.webXmlNamespaceAware= webXmlNamespaceAware;
    }    


    /**
     * Set the validation feature of the XML parser used when
     * parsing tlds files. 
     * @param tldValidation true to enable xml instance validation
     */
    public void setTldValidation(boolean tldValidation){
        
        this.tldValidation = tldValidation;

    }

    /**
     * Get the server.xml <context> attribute's webXmlValidation.
     * @return true if validation is enabled.
     *
     */
    public boolean getTldValidation(){
        return tldValidation;
    }

    /**
     * Sets the process TLDs attribute.
     *
     * @param newProcessTlds The new value
     */
    public void setProcessTlds(boolean newProcessTlds) {
	processTlds = newProcessTlds;
    }

    /**
     * Returns the processTlds attribute value.
     */
    public boolean getProcessTlds() {
	return processTlds;
    }

    /**
     * Get the server.xml <host> attribute's xmlNamespaceAware.
     * @return true if namespace awarenes is enabled.
     */
    public boolean getTldNamespaceAware(){
        return tldNamespaceAware;
    }


    /**
     * Set the namespace aware feature of the XML parser used when
     * parsing xml instances.
     * @param tldNamespaceAware true to enable namespace awareness
     */
    public void setTldNamespaceAware(boolean tldNamespaceAware){
        this.tldNamespaceAware= tldNamespaceAware;
    }    


    /** 
     * Support for "stateManageable" JSR77 
     */
    public boolean isStateManageable() {
        return true;
    }
    
    public void startRecursive() throws LifecycleException {
        // nothing to start recursive, the servlets will be started by load-on-startup
        start();
    }
    
    public int getState() {
        if( started ) {
            return 1; // RUNNING
        }
        if( initialized ) {
            return 0; // starting ? 
        }
        if( ! available ) { 
            return 4; //FAILED
        }
        // 2 - STOPPING
        return 3; // STOPPED
    }
    
    /**
     * The J2EE Server ObjectName this module is deployed on.
     */     
    private String server = null;
    
    /**
     * The Java virtual machines on which this module is running.
     */       
    private String[] javaVMs = null;
    
    public String getServer() {
        return server;
    }
        
    public String setServer(String server) {
        return this.server=server;
    }
        
    public String[] getJavaVMs() {
        return javaVMs;
    }
        
    public String[] setJavaVMs(String[] javaVMs) {
        return this.javaVMs = javaVMs;
    }
    
    /**
     * Gets the time this context was started.
     *
     * @return Time (in milliseconds since January 1, 1970, 00:00:00) when this
     * context was started 
     */
    public long getStartTime() {
        return startTime;
    }
    
    public boolean isEventProvider() {
        return false;
    }
    
    public boolean isStatisticsProvider() {
        return false;
    }
    
}

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


package org.apache.catalina.startup;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.servlet.ServletContext;

import org.apache.tomcat.util.bcel.classfile.AnnotationElementValue;
import org.apache.tomcat.util.bcel.classfile.AnnotationEntry;
import org.apache.tomcat.util.bcel.classfile.ArrayElementValue;
import org.apache.tomcat.util.bcel.classfile.ClassFormatException;
import org.apache.tomcat.util.bcel.classfile.ClassParser;
import org.apache.tomcat.util.bcel.classfile.ElementValue;
import org.apache.tomcat.util.bcel.classfile.ElementValuePair;
import org.apache.tomcat.util.bcel.classfile.JavaClass;
import org.apache.catalina.Authenticator;
import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Valve;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.ContainerBase;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.deploy.ErrorPage;
import org.apache.catalina.deploy.FilterDef;
import org.apache.catalina.deploy.FilterMap;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.deploy.SecurityConstraint;
import org.apache.catalina.deploy.ServletDef;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.naming.resources.DirContextURLConnection;
import org.apache.naming.resources.ResourceAttributes;
import org.apache.tomcat.JarScanner;
import org.apache.tomcat.JarScannerCallback;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.RuleSet;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

/**
 * Startup event listener for a <b>Context</b> that configures the properties
 * of that Context, and the associated defined servlets.
 *
 * @author Craig R. McClanahan
 * @author Jean-Francois Arcand
 * @version $Revision$ $Date$
 */

public class ContextConfig
    implements LifecycleListener {

    private static final Log log = LogFactory.getLog( ContextConfig.class );

    // ----------------------------------------------------- Instance Variables


    /**
     * Custom mappings of login methods to authenticators
     */
    protected Map<String,Authenticator> customAuthenticators;


    /**
     * The set of Authenticators that we know how to configure.  The key is
     * the name of the implemented authentication method, and the value is
     * the fully qualified Java class name of the corresponding Valve.
     */
    protected static Properties authenticators = null;


    /**
     * The Context we are associated with.
     */
    protected Context context = null;


    /**
     * The default web application's context file location.
     */
    protected String defaultContextXml = null;
    
    
    /**
     * The default web application's deployment descriptor location.
     */
    protected String defaultWebXml = null;
    
    
    /**
     * Track any fatal errors during startup configuration processing.
     */
    protected boolean ok = false;


    /**
     * Original docBase.
     */
    protected String originalDocBase = null;
    

    /**
     * The string resources for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


    /**
     * The <code>Digester</code> we will use to process web application
     * context files.
     */
    protected static Digester contextDigester = null;
    
    
    /**
     * The <code>Digester</code> we will use to process web application
     * deployment descriptor files.
     */
    protected Digester webDigester = null;

    /**
     * The <code>Digester</code> we will use to process web fragment
     * deployment descriptor files.
     */
    protected Digester webFragmentDigester = null;

    
    protected static Digester[] webDigesters = new Digester[4];

    /**
     * The <code>Digester</code>s available to process web fragment
     * deployment descriptor files.
     */
    protected static Digester[] webFragmentDigesters = new Digester[4];
    
    /**
     * The <code>Rule</code>s used to parse the web.xml
     */
    protected static WebRuleSet webRuleSet = new WebRuleSet(false);

    /**
     * The <code>Rule</code>s used to parse the web-fragment.xml
     */
    protected static WebRuleSet webFragmentRuleSet = new WebRuleSet(true);

    /**
     * Deployment count.
     */
    protected static long deploymentCount = 0L;
    
    
    protected static final LoginConfig DUMMY_LOGIN_CONFIG =
                                new LoginConfig("NONE", null, null, null);


    // Names of JARs that are known not to contain web-fragment.xml
    private static HashSet<String> noFragmentJars;
    
    /*
     * Initializes the set of JARs that are known not to contain any web-fragments
     */
    static {
        // TODO - set this list via configuration (also TLDs in Jasper) 
        noFragmentJars = new HashSet<String>();
        // Bootstrap JARs
        noFragmentJars.add("bootstrap.jar");
        noFragmentJars.add("commons-daemon.jar");
        noFragmentJars.add("tomcat-juli.jar");
        // Main JARs
        noFragmentJars.add("annotations-api.jar");
        noFragmentJars.add("catalina.jar");
        noFragmentJars.add("catalina-ant.jar");
        noFragmentJars.add("catalina-ha.jar");
        noFragmentJars.add("catalina-tribes.jar");
        noFragmentJars.add("el-api.jar");
        noFragmentJars.add("jasper.jar");
        noFragmentJars.add("jasper-el.jar");
        noFragmentJars.add("jasper-jdt.jar");
        noFragmentJars.add("jsp-api.jar");
        noFragmentJars.add("servlet-api.jar");
        noFragmentJars.add("tomcat-api.jar");
        noFragmentJars.add("tomcat-coyote.jar");
        noFragmentJars.add("tomcat-dbcp.jar");
        // i18n JARs
        noFragmentJars.add("tomcat-i18n-en.jar");
        noFragmentJars.add("tomcat-i18n-es.jar");
        noFragmentJars.add("tomcat-i18n-fr.jar");
        noFragmentJars.add("tomcat-i18n-ja.jar");
        // Misc JARs not included with Tomcat
        noFragmentJars.add("ant.jar");
        noFragmentJars.add("commons-dbcp.jar");
        noFragmentJars.add("commons-beanutils.jar");
        noFragmentJars.add("commons-fileupload-1.0.jar");
        noFragmentJars.add("commons-pool.jar");
        noFragmentJars.add("commons-digester.jar");
        noFragmentJars.add("commons-logging.jar");
        noFragmentJars.add("commons-collections.jar");
        noFragmentJars.add("jmx.jar");
        noFragmentJars.add("jmx-tools.jar");
        noFragmentJars.add("xercesImpl.jar");
        noFragmentJars.add("xmlParserAPIs.jar");
        noFragmentJars.add("xml-apis.jar");
        // JARs from J2SE runtime
        noFragmentJars.add("sunjce_provider.jar");
        noFragmentJars.add("ldapsec.jar");
        noFragmentJars.add("localedata.jar");
        noFragmentJars.add("dnsns.jar");
        noFragmentJars.add("tools.jar");
        noFragmentJars.add("sunpkcs11.jar");
        noFragmentJars.add("sunec.jar");
        // Apple J2SE runtime
        noFragmentJars.add("apple_provider.jar");
        noFragmentJars.add("AppleScriptEngine.jar");
        noFragmentJars.add("CoreAudio.jar");
        noFragmentJars.add("dns_sd.jar");
        noFragmentJars.add("j3daudio.jar");
        noFragmentJars.add("j3dcore.jar");
        noFragmentJars.add("j3dutils.jar");
        noFragmentJars.add("jai_core.jar");
        noFragmentJars.add("jai_codec.jar");
        noFragmentJars.add("mlibwrapper_jai.jar");
        noFragmentJars.add("MRJToolkit.jar");
        noFragmentJars.add("vecmath.jar");
    }

    // ------------------------------------------------------------- Properties


    /**
     * Return the location of the default deployment descriptor
     */
    public String getDefaultWebXml() {
        if( defaultWebXml == null ) {
            defaultWebXml=Constants.DefaultWebXml;
        }

        return (this.defaultWebXml);

    }


    /**
     * Set the location of the default deployment descriptor
     *
     * @param path Absolute/relative path to the default web.xml
     */
    public void setDefaultWebXml(String path) {

        this.defaultWebXml = path;

    }


    /**
     * Return the location of the default context file
     */
    public String getDefaultContextXml() {
        if( defaultContextXml == null ) {
            defaultContextXml=Constants.DefaultContextXml;
        }

        return (this.defaultContextXml);

    }


    /**
     * Set the location of the default context file
     *
     * @param path Absolute/relative path to the default context.xml
     */
    public void setDefaultContextXml(String path) {

        this.defaultContextXml = path;

    }


    /**
     * Sets custom mappings of login methods to authenticators.
     *
     * @param customAuthenticators Custom mappings of login methods to
     * authenticators
     */
    public void setCustomAuthenticators(
            Map<String,Authenticator> customAuthenticators) {
        this.customAuthenticators = customAuthenticators;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Process events for an associated Context.
     *
     * @param event The lifecycle event that has occurred
     */
    public void lifecycleEvent(LifecycleEvent event) {

        // Identify the context we are associated with
        try {
            context = (Context) event.getLifecycle();
        } catch (ClassCastException e) {
            log.error(sm.getString("contextConfig.cce", event.getLifecycle()), e);
            return;
        }

        // Process the event that has occurred
        if (event.getType().equals(Lifecycle.START_EVENT)) {
            start();
        } else if (event.getType().equals(Lifecycle.BEFORE_START_EVENT)) {
            beforeStart();
        } else if (event.getType().equals(Lifecycle.AFTER_START_EVENT)) {
            // Restore docBase for management tools
            if (originalDocBase != null) {
                String docBase = context.getDocBase();
                context.setDocBase(originalDocBase);
                originalDocBase = docBase;
            }
        } else if (event.getType().equals(Lifecycle.STOP_EVENT)) {
            if (originalDocBase != null) {
                String docBase = context.getDocBase();
                context.setDocBase(originalDocBase);
                originalDocBase = docBase;
            }
            stop();
        } else if (event.getType().equals(Lifecycle.INIT_EVENT)) {
            init();
        } else if (event.getType().equals(Lifecycle.DESTROY_EVENT)) {
            destroy();
        }

    }


    // -------------------------------------------------------- protected Methods


    /**
     * Process the application classes annotations, if it exists.
     */
    protected void applicationAnnotationsConfig() {
        
        long t1=System.currentTimeMillis();
        
        WebAnnotationSet.loadApplicationAnnotations(context);
        
        long t2=System.currentTimeMillis();
        if (context instanceof StandardContext) {
            ((StandardContext) context).setStartupTime(t2-t1+
                    ((StandardContext) context).getStartupTime());
        }
    }


    /**
     * Set up an Authenticator automatically if required, and one has not
     * already been configured.
     */
    protected synchronized void authenticatorConfig() {

        // Does this Context require an Authenticator?
        SecurityConstraint constraints[] = context.findConstraints();
        if ((constraints == null) || (constraints.length == 0))
            return;
        LoginConfig loginConfig = context.getLoginConfig();
        if (loginConfig == null) {
            loginConfig = DUMMY_LOGIN_CONFIG;
            context.setLoginConfig(loginConfig);
        }

        // Has an authenticator been configured already?
        if (context.getAuthenticator() != null)
            return;
        
        if (!(context instanceof ContainerBase)) {
            return;     // Cannot install a Valve even if it would be needed
        }

        // Has a Realm been configured for us to authenticate against?
        if (context.getRealm() == null) {
            log.error(sm.getString("contextConfig.missingRealm"));
            ok = false;
            return;
        }

        /*
         * First check to see if there is a custom mapping for the login
         * method. If so, use it. Otherwise, check if there is a mapping in
         * org/apache/catalina/startup/Authenticators.properties.
         */
        Valve authenticator = null;
        if (customAuthenticators != null) {
            authenticator = (Valve)
                customAuthenticators.get(loginConfig.getAuthMethod());
        }
        if (authenticator == null) {
            // Load our mapping properties if necessary
            if (authenticators == null) {
                try {
                    InputStream is=this.getClass().getClassLoader().getResourceAsStream("org/apache/catalina/startup/Authenticators.properties");
                    if( is!=null ) {
                        authenticators = new Properties();
                        authenticators.load(is);
                    } else {
                        log.error(sm.getString(
                                "contextConfig.authenticatorResources"));
                        ok=false;
                        return;
                    }
                } catch (IOException e) {
                    log.error(sm.getString(
                                "contextConfig.authenticatorResources"), e);
                    ok = false;
                    return;
                }
            }

            // Identify the class name of the Valve we should configure
            String authenticatorName = null;
            authenticatorName =
                    authenticators.getProperty(loginConfig.getAuthMethod());
            if (authenticatorName == null) {
                log.error(sm.getString("contextConfig.authenticatorMissing",
                                 loginConfig.getAuthMethod()));
                ok = false;
                return;
            }

            // Instantiate and install an Authenticator of the requested class
            try {
                Class<?> authenticatorClass = Class.forName(authenticatorName);
                authenticator = (Valve) authenticatorClass.newInstance();
            } catch (Throwable t) {
                log.error(sm.getString(
                                    "contextConfig.authenticatorInstantiate",
                                    authenticatorName),
                          t);
                ok = false;
            }
        }

        if (authenticator != null && context instanceof ContainerBase) {
            Pipeline pipeline = ((ContainerBase) context).getPipeline();
            if (pipeline != null) {
                ((ContainerBase) context).addValve(authenticator);
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString(
                                    "contextConfig.authenticatorConfigured",
                                    loginConfig.getAuthMethod()));
                }
            }
        }

    }


    /**
     * Create (if necessary) and return a Digester configured to process the
     * web application deployment descriptor (web.xml).
     */
    public void createWebXmlDigester(boolean namespaceAware,
            boolean validation) {
        
        if (!namespaceAware && !validation) {
            if (webDigesters[0] == null) {
                webDigesters[0] = DigesterFactory.newDigester(validation,
                        namespaceAware, webRuleSet);
                webFragmentDigesters[0] = DigesterFactory.newDigester(validation,
                        namespaceAware, webFragmentRuleSet);
            }
            webDigester = webDigesters[0];
            webFragmentDigester = webFragmentDigesters[0];
            
        } else if (!namespaceAware && validation) {
            if (webDigesters[1] == null) {
                webDigesters[1] = DigesterFactory.newDigester(validation,
                        namespaceAware, webRuleSet);
                webFragmentDigesters[1] = DigesterFactory.newDigester(validation,
                        namespaceAware, webFragmentRuleSet);
            }
            webDigester = webDigesters[1];
            webFragmentDigester = webFragmentDigesters[1];
        } else if (namespaceAware && !validation) {
            if (webDigesters[2] == null) {
                webDigesters[2] = DigesterFactory.newDigester(validation,
                        namespaceAware, webRuleSet);
                webFragmentDigesters[2] = DigesterFactory.newDigester(validation,
                        namespaceAware, webFragmentRuleSet);
            }
            webDigester = webDigesters[2];
            webFragmentDigester = webFragmentDigesters[2];
        } else {
            if (webDigesters[3] == null) {
                webDigesters[3] = DigesterFactory.newDigester(validation,
                        namespaceAware, webFragmentRuleSet);
                webFragmentDigesters[3] = DigesterFactory.newDigester(validation,
                        namespaceAware, webRuleSet);
            }
            webDigester = webDigesters[3];
            webFragmentDigester = webFragmentDigesters[3];
        }
    }

    
    /**
     * Create (if necessary) and return a Digester configured to process the
     * context configuration descriptor for an application.
     */
    protected Digester createContextDigester() {
        Digester digester = new Digester();
        digester.setValidating(false);
        RuleSet contextRuleSet = new ContextRuleSet("", false);
        digester.addRuleSet(contextRuleSet);
        RuleSet namingRuleSet = new NamingRuleSet("Context/");
        digester.addRuleSet(namingRuleSet);
        return digester;
    }


    protected String getBaseDir() {
        Container engineC=context.getParent().getParent();
        if( engineC instanceof StandardEngine ) {
            return ((StandardEngine)engineC).getBaseDir();
        }
        return System.getProperty("catalina.base");
    }

    
    /**
     * Process the default configuration file, if it exists.
     */
    protected void contextConfig() {
        
        // Open the default web.xml file, if it exists
        if( defaultContextXml==null && context instanceof StandardContext ) {
            defaultContextXml = ((StandardContext)context).getDefaultContextXml();
        }
        // set the default if we don't have any overrides
        if( defaultContextXml==null ) getDefaultContextXml();

        if (!context.getOverride()) {
            processContextConfig(new File(getBaseDir()), defaultContextXml);
            processContextConfig(getConfigBase(), getHostConfigPath(Constants.HostContextXml));
        }
        if (context.getConfigFile() != null)
            processContextConfig(new File(context.getConfigFile()), null);
        
    }

    
    /**
     * Process a context.xml.
     */
    protected void processContextConfig(File baseDir, String resourceName) {
        
        if (log.isDebugEnabled())
            log.debug("Processing context [" + context.getName() 
                    + "] configuration file " + baseDir + " " + resourceName);

        InputSource source = null;
        InputStream stream = null;

        File file = baseDir;
        if (resourceName != null) {
            file = new File(baseDir, resourceName);
        }
        
        try {
            if ( !file.exists() ) {
                if (resourceName != null) {
                    // Use getResource and getResourceAsStream
                    stream = getClass().getClassLoader()
                        .getResourceAsStream(resourceName);
                    if( stream != null ) {
                        source = new InputSource
                            (getClass().getClassLoader()
                            .getResource(resourceName).toString());
                    }
                }
            } else {
                source =
                    new InputSource("file://" + file.getAbsolutePath());
                stream = new FileInputStream(file);
                // Add as watched resource so that cascade reload occurs if a default
                // config file is modified/added/removed
                context.addWatchedResource(file.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error(sm.getString("contextConfig.contextMissing",  
                      resourceName + " " + file) , e);
        }
        
        if (source == null)
            return;
        synchronized (contextDigester) {
            try {
                source.setByteStream(stream);
                contextDigester.setClassLoader(this.getClass().getClassLoader());
                contextDigester.setUseContextClassLoader(false);
                contextDigester.push(context.getParent());
                contextDigester.push(context);
                ContextErrorHandler errorHandler = new ContextErrorHandler();
                contextDigester.setErrorHandler(errorHandler);
                contextDigester.parse(source);
                if (errorHandler.parseException != null) {
                    ok = false;
                }
                if (log.isDebugEnabled())
                    log.debug("Successfully processed context [" + context.getName() 
                            + "] configuration file " + baseDir + " " + resourceName);
            } catch (SAXParseException e) {
                log.error(sm.getString("contextConfig.contextParse",
                        context.getName()), e);
                log.error(sm.getString("contextConfig.defaultPosition",
                                 "" + e.getLineNumber(),
                                 "" + e.getColumnNumber()));
                ok = false;
            } catch (Exception e) {
                log.error(sm.getString("contextConfig.contextParse",
                        context.getName()), e);
                ok = false;
            } finally {
                contextDigester.reset();
                try {
                    if (stream != null) {
                        stream.close();
                    }
                } catch (IOException e) {
                    log.error(sm.getString("contextConfig.contextClose"), e);
                }
            }
        }
    }

    
    /**
     * Adjust docBase.
     */
    protected void fixDocBase()
        throws IOException {
        
        Host host = (Host) context.getParent();
        String appBase = host.getAppBase();

        boolean unpackWARs = true;
        if (host instanceof StandardHost) {
            unpackWARs = ((StandardHost) host).isUnpackWARs() 
                && ((StandardContext) context).getUnpackWAR();
        }

        File canonicalAppBase = new File(appBase);
        if (canonicalAppBase.isAbsolute()) {
            canonicalAppBase = canonicalAppBase.getCanonicalFile();
        } else {
            canonicalAppBase = 
                new File(System.getProperty("catalina.base"), appBase)
                .getCanonicalFile();
        }

        String docBase = context.getDocBase();
        if (docBase == null) {
            // Trying to guess the docBase according to the path
            String path = context.getPath();
            if (path == null) {
                return;
            }
            if (path.equals("")) {
                docBase = "ROOT";
            } else {
                if (path.startsWith("/")) {
                    docBase = path.substring(1).replace('/', '#');
                } else {
                    docBase = path.replace('/', '#');
                }
            }
        }

        File file = new File(docBase);
        if (!file.isAbsolute()) {
            docBase = (new File(canonicalAppBase, docBase)).getPath();
        } else {
            docBase = file.getCanonicalPath();
        }
        file = new File(docBase);
        String origDocBase = docBase;
        
        String pathName = context.getPath();
        if (pathName.equals("")) {
            pathName = "ROOT";
        } else {
            // Context path must start with '/'
            pathName = pathName.substring(1).replace('/', '#');
        }
        if (docBase.toLowerCase().endsWith(".war") && !file.isDirectory() && unpackWARs) {
            URL war = new URL("jar:" + (new File(docBase)).toURI().toURL() + "!/");
            docBase = ExpandWar.expand(host, war, pathName);
            file = new File(docBase);
            docBase = file.getCanonicalPath();
            if (context instanceof StandardContext) {
                ((StandardContext) context).setOriginalDocBase(origDocBase);
            }
        } else if (docBase.toLowerCase().endsWith(".war") &&
                !file.isDirectory() && !unpackWARs) {
            URL war =
                new URL("jar:" + (new File (docBase)).toURI().toURL() + "!/");
            ExpandWar.validate(host, war, pathName);
        } else {
            File docDir = new File(docBase);
            if (!docDir.exists()) {
                File warFile = new File(docBase + ".war");
                if (warFile.exists()) {
                    URL war =
                        new URL("jar:" + warFile.toURI().toURL() + "!/");
                    if (unpackWARs) {
                        docBase = ExpandWar.expand(host, war, pathName);
                        file = new File(docBase);
                        docBase = file.getCanonicalPath();
                    } else {
                        docBase = warFile.getCanonicalPath();
                        ExpandWar.validate(host, war, pathName);
                    }
                }
                if (context instanceof StandardContext) {
                    ((StandardContext) context).setOriginalDocBase(origDocBase);
                }
            }
        }

        if (docBase.startsWith(canonicalAppBase.getPath() + File.separatorChar)) {
            docBase = docBase.substring(canonicalAppBase.getPath().length());
            docBase = docBase.replace(File.separatorChar, '/');
            if (docBase.startsWith("/")) {
                docBase = docBase.substring(1);
            }
        } else {
            docBase = docBase.replace(File.separatorChar, '/');
        }

        context.setDocBase(docBase);

    }
    
    
    protected void antiLocking() {

        if ((context instanceof StandardContext) 
            && ((StandardContext) context).getAntiResourceLocking()) {
            
            Host host = (Host) context.getParent();
            String appBase = host.getAppBase();
            String docBase = context.getDocBase();
            if (docBase == null)
                return;
            if (originalDocBase == null) {
                originalDocBase = docBase;
            } else {
                docBase = originalDocBase;
            }
            File docBaseFile = new File(docBase);
            if (!docBaseFile.isAbsolute()) {
                File file = new File(appBase);
                if (!file.isAbsolute()) {
                    file = new File(System.getProperty("catalina.base"), appBase);
                }
                docBaseFile = new File(file, docBase);
            }
            
            String path = context.getPath();
            if (path == null) {
                return;
            }
            if (path.equals("")) {
                docBase = "ROOT";
            } else {
                if (path.startsWith("/")) {
                    docBase = path.substring(1);
                } else {
                    docBase = path;
                }
            }

            File file = null;
            if (docBase.toLowerCase().endsWith(".war")) {
                file = new File(System.getProperty("java.io.tmpdir"),
                        deploymentCount++ + "-" + docBase + ".war");
            } else {
                file = new File(System.getProperty("java.io.tmpdir"), 
                        deploymentCount++ + "-" + docBase);
            }
            
            if (log.isDebugEnabled())
                log.debug("Anti locking context[" + context.getPath() 
                        + "] setting docBase to " + file);
            
            // Cleanup just in case an old deployment is lying around
            ExpandWar.delete(file);
            if (ExpandWar.copy(docBaseFile, file)) {
                context.setDocBase(file.getAbsolutePath());
            }
            
        }
        
    }
    

    /**
     * Process a "init" event for this Context.
     */
    protected void init() {
        // Called from StandardContext.init()

        if (contextDigester == null){
            contextDigester = createContextDigester();
            contextDigester.getParser();
        }

        if (log.isDebugEnabled())
            log.debug(sm.getString("contextConfig.init"));
        context.setConfigured(false);
        ok = true;
        
        contextConfig();
        
        try {
            fixDocBase();
        } catch (IOException e) {
            log.error(sm.getString(
                    "contextConfig.fixDocBase", context.getPath()), e);
        }
        
    }
    
    
    /**
     * Process a "before start" event for this Context.
     */
    protected synchronized void beforeStart() {
        
        antiLocking();

    }
    
    
    /**
     * Process a "start" event for this Context.
     */
    protected synchronized void start() {
        // Called from StandardContext.start()

        if (log.isDebugEnabled())
            log.debug(sm.getString("contextConfig.start"));

        // Process the default and application web.xml files
        // Set properties based on default context
        boolean useXmlValidation = context.getXmlValidation();
        boolean useXmlNamespaceAware = context.getXmlNamespaceAware();

        Container container = context.getParent();
        // Use the value from the host if:
        // - override is false on the context
        // - value has been set to false / not set on the context
        if( !context.getOverride() ) {
            if( container instanceof Host ) {
                if (!useXmlValidation) {
                    useXmlValidation = ((Host)container).getXmlValidation();
                }
                
                if (!useXmlNamespaceAware){
                    useXmlNamespaceAware 
                                = ((Host)container).getXmlNamespaceAware();
                }

            }
        }
        
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("contextConfig.xmlSettings",
                    context.getName(), Boolean.valueOf(useXmlValidation),
                    Boolean.valueOf(useXmlNamespaceAware)));
        }
        
        createWebXmlDigester(useXmlNamespaceAware, useXmlValidation);
        
        webConfig();

        if (!context.getIgnoreAnnotations()) {
            applicationAnnotationsConfig();
        }
        if (ok) {
            validateSecurityRoles();
        }

        // Configure an authenticator if we need one
        if (ok)
            authenticatorConfig();

        // Dump the contents of this pipeline if requested
        if ((log.isDebugEnabled()) && (context instanceof ContainerBase)) {
            log.debug("Pipeline Configuration:");
            Pipeline pipeline = ((ContainerBase) context).getPipeline();
            Valve valves[] = null;
            if (pipeline != null)
                valves = pipeline.getValves();
            if (valves != null) {
                for (int i = 0; i < valves.length; i++) {
                    log.debug("  " + valves[i].getInfo());
                }
            }
            log.debug("======================");
        }

        // Make our application available if no problems were encountered
        if (ok)
            context.setConfigured(true);
        else {
            log.error(sm.getString("contextConfig.unavailable"));
            context.setConfigured(false);
        }

    }


    /**
     * Process a "stop" event for this Context.
     */
    protected synchronized void stop() {

        if (log.isDebugEnabled())
            log.debug(sm.getString("contextConfig.stop"));

        int i;

        // Removing children
        Container[] children = context.findChildren();
        for (i = 0; i < children.length; i++) {
            context.removeChild(children[i]);
        }

        // Removing application parameters
        /*
        ApplicationParameter[] applicationParameters =
            context.findApplicationParameters();
        for (i = 0; i < applicationParameters.length; i++) {
            context.removeApplicationParameter
                (applicationParameters[i].getName());
        }
        */

        // Removing security constraints
        SecurityConstraint[] securityConstraints = context.findConstraints();
        for (i = 0; i < securityConstraints.length; i++) {
            context.removeConstraint(securityConstraints[i]);
        }

        // Removing Ejbs
        /*
        ContextEjb[] contextEjbs = context.findEjbs();
        for (i = 0; i < contextEjbs.length; i++) {
            context.removeEjb(contextEjbs[i].getName());
        }
        */

        // Removing environments
        /*
        ContextEnvironment[] contextEnvironments = context.findEnvironments();
        for (i = 0; i < contextEnvironments.length; i++) {
            context.removeEnvironment(contextEnvironments[i].getName());
        }
        */

        // Removing errors pages
        ErrorPage[] errorPages = context.findErrorPages();
        for (i = 0; i < errorPages.length; i++) {
            context.removeErrorPage(errorPages[i]);
        }

        // Removing filter defs
        FilterDef[] filterDefs = context.findFilterDefs();
        for (i = 0; i < filterDefs.length; i++) {
            context.removeFilterDef(filterDefs[i]);
        }

        // Removing filter maps
        FilterMap[] filterMaps = context.findFilterMaps();
        for (i = 0; i < filterMaps.length; i++) {
            context.removeFilterMap(filterMaps[i]);
        }

        // Removing local ejbs
        /*
        ContextLocalEjb[] contextLocalEjbs = context.findLocalEjbs();
        for (i = 0; i < contextLocalEjbs.length; i++) {
            context.removeLocalEjb(contextLocalEjbs[i].getName());
        }
        */

        // Removing Mime mappings
        String[] mimeMappings = context.findMimeMappings();
        for (i = 0; i < mimeMappings.length; i++) {
            context.removeMimeMapping(mimeMappings[i]);
        }

        // Removing parameters
        String[] parameters = context.findParameters();
        for (i = 0; i < parameters.length; i++) {
            context.removeParameter(parameters[i]);
        }

        // Removing resource env refs
        /*
        String[] resourceEnvRefs = context.findResourceEnvRefs();
        for (i = 0; i < resourceEnvRefs.length; i++) {
            context.removeResourceEnvRef(resourceEnvRefs[i]);
        }
        */

        // Removing resource links
        /*
        ContextResourceLink[] contextResourceLinks =
            context.findResourceLinks();
        for (i = 0; i < contextResourceLinks.length; i++) {
            context.removeResourceLink(contextResourceLinks[i].getName());
        }
        */

        // Removing resources
        /*
        ContextResource[] contextResources = context.findResources();
        for (i = 0; i < contextResources.length; i++) {
            context.removeResource(contextResources[i].getName());
        }
        */

        // Removing security role
        String[] securityRoles = context.findSecurityRoles();
        for (i = 0; i < securityRoles.length; i++) {
            context.removeSecurityRole(securityRoles[i]);
        }

        // Removing servlet mappings
        String[] servletMappings = context.findServletMappings();
        for (i = 0; i < servletMappings.length; i++) {
            context.removeServletMapping(servletMappings[i]);
        }

        // FIXME : Removing status pages

        // Removing taglibs
        String[] taglibs = context.findTaglibs();
        for (i = 0; i < taglibs.length; i++) {
            context.removeTaglib(taglibs[i]);
        }

        // Removing welcome files
        String[] welcomeFiles = context.findWelcomeFiles();
        for (i = 0; i < welcomeFiles.length; i++) {
            context.removeWelcomeFile(welcomeFiles[i]);
        }

        // Removing wrapper lifecycles
        String[] wrapperLifecycles = context.findWrapperLifecycles();
        for (i = 0; i < wrapperLifecycles.length; i++) {
            context.removeWrapperLifecycle(wrapperLifecycles[i]);
        }

        // Removing wrapper listeners
        String[] wrapperListeners = context.findWrapperListeners();
        for (i = 0; i < wrapperListeners.length; i++) {
            context.removeWrapperListener(wrapperListeners[i]);
        }

        // Remove (partially) folders and files created by antiLocking
        Host host = (Host) context.getParent();
        String appBase = host.getAppBase();
        String docBase = context.getDocBase();
        if ((docBase != null) && (originalDocBase != null)) {
            File docBaseFile = new File(docBase);
            if (!docBaseFile.isAbsolute()) {
                docBaseFile = new File(appBase, docBase);
            }
            // No need to log failure - it is expected in this case
            ExpandWar.delete(docBaseFile, false);
        }
        
        ok = true;

    }
    
    
    /**
     * Process a "destroy" event for this Context.
     */
    protected synchronized void destroy() {
        // Called from StandardContext.destroy()
        if (log.isDebugEnabled())
            log.debug(sm.getString("contextConfig.destroy"));

        // Changed to getWorkPath per Bugzilla 35819.
        String workDir = ((StandardContext) context).getWorkPath();
        if (workDir != null)
            ExpandWar.delete(new File(workDir));
    }
    
    
    /**
     * Validate the usage of security role names in the web application
     * deployment descriptor.  If any problems are found, issue warning
     * messages (for backwards compatibility) and add the missing roles.
     * (To make these problems fatal instead, simply set the <code>ok</code>
     * instance variable to <code>false</code> as well).
     */
    protected void validateSecurityRoles() {

        // Check role names used in <security-constraint> elements
        SecurityConstraint constraints[] = context.findConstraints();
        for (int i = 0; i < constraints.length; i++) {
            String roles[] = constraints[i].findAuthRoles();
            for (int j = 0; j < roles.length; j++) {
                if (!"*".equals(roles[j]) &&
                    !context.findSecurityRole(roles[j])) {
                    log.info(sm.getString("contextConfig.role.auth", roles[j]));
                    context.addSecurityRole(roles[j]);
                }
            }
        }

        // Check role names used in <servlet> elements
        Container wrappers[] = context.findChildren();
        for (int i = 0; i < wrappers.length; i++) {
            Wrapper wrapper = (Wrapper) wrappers[i];
            String runAs = wrapper.getRunAs();
            if ((runAs != null) && !context.findSecurityRole(runAs)) {
                log.info(sm.getString("contextConfig.role.runas", runAs));
                context.addSecurityRole(runAs);
            }
            String names[] = wrapper.findSecurityReferences();
            for (int j = 0; j < names.length; j++) {
                String link = wrapper.findSecurityReference(names[j]);
                if ((link != null) && !context.findSecurityRole(link)) {
                    log.info(sm.getString("contextConfig.role.link", link));
                    context.addSecurityRole(link);
                }
            }
        }

    }


    /**
     * Get config base.
     */
    protected File getConfigBase() {
        File configBase = 
            new File(System.getProperty("catalina.base"), "conf");
        if (!configBase.exists()) {
            return null;
        }
        return configBase;
    }  

    
    protected String getHostConfigPath(String resourceName) {
        StringBuilder result = new StringBuilder();
        Container container = context;
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
            result.append(engine.getName()).append('/');
        }
        if (host != null) {
            result.append(host.getName()).append('/');
        }
        result.append(resourceName);
        return result.toString();
    }


    /**
     * Scan the web.xml files that apply to the web application and merge them
     * using the rules defined in the spec. For the global web.xml files,
     * where there is duplicate configuration, the most specific level wins. ie
     * an application's web.xml takes precedence over the host level or global
     * web.xml file.
     */
    protected void webConfig() {
        WebXml webXml = new WebXml();

        // Parse global web.xml if present
        InputSource globalWebXml = getGlobalWebXmlSource();
        if (globalWebXml == null) {
            // This is unusual enough to log
            log.info(sm.getString("contextConfig.defaultMissing"));
        } else {
            parseWebXml(globalWebXml, webXml, false);
        }

        // Parse host level web.xml if present
        // Additive apart from welcome pages
        webXml.setReplaceWelcomeFiles(true);
        InputSource hostWebXml = getHostWebXmlSource();
        parseWebXml(hostWebXml, webXml, false);
        
        // Parse context level web.xml
        webXml.setReplaceWelcomeFiles(true);
        InputSource contextWebXml = getContextWebXmlSource();
        parseWebXml(contextWebXml, webXml, false);
        
        if (!webXml.isMetadataComplete()) {
            // Process /WEB-INF/classes for annotations
            URL webinfClasses;
            try {
                webinfClasses =
                    context.getServletContext().getResource("/WEB-INF/classes");
                processAnnotationsUrl(webinfClasses, webXml);
            } catch (MalformedURLException e) {
                log.error(sm.getString("contextConfig.webinfClassesUrl"), e);
            }
            
            // Have to process JARs for fragments
            Map<String,WebXml> fragments = processJarsForWebFragments();
            
            // Merge the fragments into the main web.xml
            Set<WebXml> orderedFragments =
                WebXml.orderWebFragments(webXml, fragments);

            // Process JARs for annotations - only need to process those
            // fragments we are going to use
            processAnnotations(orderedFragments);

            // Merge fragment into application
            if (ok) {
                ok = webXml.merge(orderedFragments);
            }

            // Apply merged web.xml to Context
            webXml.configureContext(context);
        } else {
            // Apply merged web.xml to Context
            webXml.configureContext(context);
        }
    }

    
    /**
     * Identify the default web.xml to be used and obtain an input source for
     * it.
     */
    protected InputSource getGlobalWebXmlSource() {
        // Is a default web.xml specified for the Context?
        if (defaultWebXml == null && context instanceof StandardContext) {
            defaultWebXml = ((StandardContext) context).getDefaultWebXml();
        }
        // Set the default if we don't have any overrides
        if (defaultWebXml == null) getDefaultWebXml();

        return getWebXmlSource(defaultWebXml, getBaseDir());
    }
    
    /**
     * Identify the host web.xml to be used and obtain an input source for
     * it.
     */
    protected InputSource getHostWebXmlSource() {
        String resourceName = getHostConfigPath(Constants.HostWebXml);
        
        // In an embedded environment, configBase might not be set
        File configBase = getConfigBase();
        if (configBase == null)
            return null;
        
        String basePath = null;
        try {
            basePath = configBase.getCanonicalPath();
        } catch (IOException e) {
            log.error(sm.getString("contectConfig.baseError"), e);
            return null;
        }

        return getWebXmlSource(resourceName, basePath);
    }
    
    /**
     * Identify the application web.xml to be used and obtain an input source
     * for it.
     */
    protected InputSource getContextWebXmlSource() {
        InputStream stream = null;
        InputSource source = null;
        URL url = null;
        
        String altDDName = null;

        // Open the application web.xml file, if it exists
        ServletContext servletContext = context.getServletContext();
        if (servletContext != null) {
            altDDName = (String)servletContext.getAttribute(
                                                        Globals.ALT_DD_ATTR);
            if (altDDName != null) {
                try {
                    stream = new FileInputStream(altDDName);
                    url = new File(altDDName).toURI().toURL();
                } catch (FileNotFoundException e) {
                    log.error(sm.getString("contextConfig.altDDNotFound",
                                           altDDName));
                } catch (MalformedURLException e) {
                    log.error(sm.getString("contextConfig.applicationUrl"));
                }
            }
            else {
                stream = servletContext.getResourceAsStream
                    (Constants.ApplicationWebXml);
                try {
                    url = servletContext.getResource(
                            Constants.ApplicationWebXml);
                } catch (MalformedURLException e) {
                    log.error(sm.getString("contextConfig.applicationUrl"));
                }
            }
        }
        if (stream == null || url == null) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("contextConfig.applicationMissing") + " " + context);
            }
        } else {
            source = new InputSource(url.toExternalForm());
            source.setByteStream(stream);
        }
        
        return source;
    }
    
    /**
     * 
     * @param filename  Name of the file (possibly with one or more leading path
     *                  segments) to read
     * @param path      Location that filename is relative to 
     * @return
     */
    protected InputSource getWebXmlSource(String filename, String path) {
        File file = new File(filename);
        if (!file.isAbsolute()) {
            file = new File(path, filename);
        }

        InputStream stream = null;
        InputSource source = null;

        try {
            if (!file.exists()) {
                // Use getResource and getResourceAsStream
                stream =
                    getClass().getClassLoader().getResourceAsStream(filename);
                if(stream != null) {
                    source =
                        new InputSource(getClass().getClassLoader().getResource(
                                filename).toString());
                } 
            } else {
                source = new InputSource("file://" + file.getAbsolutePath());
                stream = new FileInputStream(file);
                context.addWatchedResource(file.getAbsolutePath());
            }

            if (stream != null && source != null) {
                source.setByteStream(stream);
            }
        } catch (Exception e) {
            log.error(sm.getString(
                    "contextConfig.defaultError", filename, file), e);
        }

        return source;
    }


    protected void parseWebXml(InputSource source, WebXml dest,
            boolean fragment) {
        
        if (source == null) return;

        ContextErrorHandler handler = new ContextErrorHandler();

        // Web digesters and rulesets are shared between contexts but are not
        // thread safe. Whilst there should only be one thread at a time
        // processing a config, play safe and sync.
        Digester digester;
        if (fragment) {
            digester = webFragmentDigester;
        } else {
            digester = webDigester;
        }
        
        synchronized(digester) {
            
            digester.push(dest);
            digester.setErrorHandler(handler);
            
            if(log.isDebugEnabled()) {
                log.debug(sm.getString("contextConfig.applicationStart",
                        source.getSystemId()));
            }

            try {
                digester.parse(source);

                if (handler.getParseException() != null) {
                    ok = false;
                }
            } catch (SAXParseException e) {
                log.error(sm.getString("contextConfig.applicationParse",
                        source.getSystemId()), e);
                log.error(sm.getString("contextConfig.applicationPosition",
                                 "" + e.getLineNumber(),
                                 "" + e.getColumnNumber()));
                ok = false;
            } catch (Exception e) {
                log.error(sm.getString("contextConfig.applicationParse",
                        source.getSystemId()), e);
                ok = false;
            } finally {
                digester.reset();
                if (fragment) {
                    webFragmentRuleSet.recycle();
                } else {
                    webRuleSet.recycle();
                }
            }
        }
    }


    /**
     * Scan /META-INF/lib for JARs and for each one found add it and any
     * /META-INF/web-fragment.xml to the resulting Map. web-fragment.xml files
     * will be parsed before being added to the map. Every JAR will be added and
     * <code>null</code> will be used if no web-fragment.xml was found. Any JARs
     * known not contain fragments will be skipped.
     * 
     * @return A map of JAR name to processed web fragment (if any)
     */
    protected Map<String,WebXml> processJarsForWebFragments() {
        
        JarScanner jarScanner = context.getJarScanner();
        FragmentJarScannerCallback callback = new FragmentJarScannerCallback();
        
        jarScanner.scan(context.getServletContext(),
                context.getLoader().getClassLoader(), callback, noFragmentJars);
        
        return callback.getFragments();
    }

    protected void processAnnotations(Set<WebXml> fragments) {
        for(WebXml fragment : fragments) {
            if (!fragment.isMetadataComplete()) {
                URL url = fragment.getURL();
                processAnnotationsUrl(url, fragment);
            }
        }
    }

    protected void processAnnotationsUrl(URL url, WebXml fragment) {
        if (url == null) {
            // Nothing to do.
            return;
        } else if ("jar".equals(url.getProtocol())) {
            processAnnotationsJar(url, fragment);
        } else if ("jndi".equals(url.getProtocol())) {
            processAnnotationsJndi(url, fragment);
        } else if ("file".equals(url.getProtocol())) {
            try {
                processAnnotationsFile(new File(url.toURI()), fragment);
            } catch (URISyntaxException e) {
                log.error(sm.getString("contextConfig.fileUrl", url), e);
            }
        } else {
            log.error(sm.getString("contextConfig.unknownUrlProtocol",
                    url.getProtocol(), url));
        }
        
    }


    protected void processAnnotationsJar(URL url, WebXml fragment) {
        JarFile jarFile = null;
        
        try {
            URLConnection urlConn = url.openConnection();
            JarURLConnection jarUrlConn;
            if (!(urlConn instanceof JarURLConnection)) {
                // This should never happen
                sm.getString("contextConfig.jarUrl", url);
                return;
            }
            
            jarUrlConn = (JarURLConnection) urlConn;
            jarUrlConn.setUseCaches(false);
            jarFile = jarUrlConn.getJarFile();
            
            Enumeration<JarEntry> jarEntries = jarFile.entries();
            while (jarEntries.hasMoreElements()) {
                JarEntry jarEntry = jarEntries.nextElement();
                String entryName = jarEntry.getName();
                if (entryName.endsWith(".class")) {
                    InputStream is = null;
                    try {
                        is = jarFile.getInputStream(jarEntry);
                        processAnnotationsStream(is, fragment);
                    } catch (IOException e) {
                        log.error(sm.getString("contextConfig.inputStreamJar",
                                entryName, url),e);
                    } finally {
                        if (is != null) {
                            try {
                                is.close();
                            } catch (Throwable t) {
                                // ignore
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error(sm.getString("contextConfig.jarFile", url), e);
        } finally {
            if (jarFile != null) {
                try {
                    jarFile.close();
                } catch (Throwable t) {
                    // ignore
                }
            }
        }
    }

    
    protected void processAnnotationsJndi(URL url, WebXml fragment) {
        try {
            URLConnection urlConn = url.openConnection();
            DirContextURLConnection dcUrlConn;
            if (!(urlConn instanceof DirContextURLConnection)) {
                // This should never happen
                sm.getString("contextConfig.jndiUrl", url);
                return;
            }
            
            dcUrlConn = (DirContextURLConnection) urlConn;
            dcUrlConn.setUseCaches(false);
            
            String type = dcUrlConn.getHeaderField(ResourceAttributes.TYPE);
            if (ResourceAttributes.COLLECTION_TYPE.equals(type)) {
                // Collection
                Enumeration<String> dirs = dcUrlConn.list();
                while (dirs.hasMoreElements()) {
                    String dir = dirs.nextElement();
                    URL dirUrl = new URL(url.toString() + '/' + dir);
                    processAnnotationsJndi(dirUrl, fragment);
                }
                
            } else {
                // Single file
                if (url.getPath().endsWith(".class")) {
                    InputStream is = null;
                    try {
                        is = dcUrlConn.getInputStream();
                        processAnnotationsStream(is, fragment);
                    } catch (IOException e) {
                        log.error(sm.getString("contextConfig.inputStreamJndi",
                                url),e);
                    } finally {
                        if (is != null) {
                            try {
                                is.close();
                            } catch (Throwable t) {
                                // ignore
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error(sm.getString("contextConfig.jndiUrl", url), e);
        }
    }
    
    
    protected void processAnnotationsFile(File file, WebXml fragment) {
        
        if (file.isDirectory()) {
            String[] dirs = file.list();
            for (String dir : dirs) {
                processAnnotationsFile(new File(file,dir), fragment);
            }
        } else if (file.canRead() && file.getName().endsWith(".class")) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(file);
                processAnnotationsStream(fis, fragment);
            } catch (IOException e) {
                log.error(sm.getString("contextConfig.inputStreamFile",
                        file.getAbsolutePath()),e);
            } finally {
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (Throwable t) {
                        // ignore
                    }
                }
            }
        }
    }


    protected void processAnnotationsStream(InputStream is, WebXml fragment)
            throws ClassFormatException, IOException {
        
        ClassParser parser = new ClassParser(is, null);
        JavaClass clazz = parser.parse();
        String className = clazz.getClassName();
        AnnotationEntry[] annotationsEntries = clazz.getAnnotationEntries();
        
        for (AnnotationEntry ae : annotationsEntries) {
            String type = ae.getAnnotationType();
            if ("Ljavax/servlet/annotation/WebServlet;".equals(type)) {
                processAnnotationWebServlet(className, ae, fragment);
            }else if ("Ljavax/servlet/annotation/WebFilter;".equals(type)) {
                processAnnotationWebFilter(className, ae, fragment);
            }else if ("Ljavax/servlet/annotation/WebListener;".equals(type)) {
                fragment.addListener(className);
            } else {
                // Unknown annotation - ignore
            }
        }
    }

    protected void processAnnotationWebServlet(String className,
            AnnotationEntry ae, WebXml fragment) {
        if (fragment.getServlets().containsKey(className)) {
            // Skip this annotation. Entry in web.xml takes priority
            return;
        }
        boolean urlPatternsSet = false;
        ServletDef servletDef = new ServletDef();
        servletDef.setServletName(className);
        servletDef.setServletClass(className);
        String[] urlPatterns = null;

        ElementValuePair[] evps = ae.getElementValuePairs();
        for (ElementValuePair evp : evps) {
            String name = evp.getNameString();
            if ("value".equals(name) || "urlPatterns".equals(name)) {
                if (urlPatternsSet) {
                    throw new IllegalArgumentException(sm.getString(
                            "contextConfig.urlPatternValue", className));
                }
                urlPatternsSet = true;
                urlPatterns = processAnnotationsStringArray(evp.getValue());
            } else if ("name".equals(name)) {
                servletDef.setServletName(evp.getValue().stringifyValue());
            } else if ("description".equals(name)) {
                servletDef.setDescription(evp.getValue().stringifyValue());
            } else if ("displayName".equals(name)) {
                servletDef.setDisplayName(evp.getValue().stringifyValue());
            } else if ("largeIcon".equals(name)) {
                servletDef.setLargeIcon(evp.getValue().stringifyValue());
            } else if ("smallIcon".equals(name)) {
                servletDef.setSmallIcon(evp.getValue().stringifyValue());
            } else if ("asyncSupported".equals(name)) {
                servletDef.setAsyncSupported(evp.getValue().stringifyValue());
            } else if ("loadOnStartup".equals(name)) {
                servletDef.setLoadOnStartup(evp.getValue().stringifyValue());
            } else if ("initParams".equals(name)) {
                Map<String,String> initParams =
                    processAnnotationWebInitParams(evp.getValue());
                for (String paramName : initParams.keySet()) {
                    servletDef.addInitParameter(paramName,
                            initParams.get(paramName));
                }
            } else {
                // Ignore
            }
        }
        if (urlPatterns != null) {
            fragment.addServlet(servletDef);
            for (String urlPattern : urlPatterns) {
                fragment.addServletMapping(urlPattern,
                        servletDef.getServletName());
            }
        }
    }

    protected void processAnnotationWebFilter(String className,
            AnnotationEntry ae, WebXml fragment) {
        if (fragment.getFilters().containsKey(className)) {
            // Skip this annotation. Entry in web.xml takes priority
            return;
        }
        boolean urlPatternsSet = false;
        FilterDef filterDef = new FilterDef();
        FilterMap filterMap = new FilterMap();
        filterDef.setFilterName(className);
        filterDef.setFilterClass(className);
        String[] urlPatterns = null;

        ElementValuePair[] evps = ae.getElementValuePairs();
        for (ElementValuePair evp : evps) {
            String name = evp.getNameString();
            if ("value".equals(name) || "urlPatterns".equals(name)) {
                if (urlPatternsSet) {
                    throw new IllegalArgumentException(sm.getString(
                            "contextConfig.urlPatternValue", className));
                }
                urlPatternsSet = true;
                urlPatterns = processAnnotationsStringArray(evp.getValue());
                for (String urlPattern : urlPatterns) {
                    filterMap.addURLPattern(urlPattern);
                }
            } else if ("filterName".equals(name)) {
                filterDef.setFilterName(evp.getValue().stringifyValue());
            } else if ("servletNames".equals(name)) {
                String[] servletNames =
                    processAnnotationsStringArray(evp.getValue());
                for (String servletName : servletNames) {
                    filterMap.addServletName(servletName);
                }
            } else if ("dispatcherTypes".equals(name)) {
                String[] dispatcherTypes =
                    processAnnotationsStringArray(evp.getValue());
                for (String dispatcherType : dispatcherTypes) {
                    filterMap.setDispatcher(dispatcherType);
                }
            } else if ("description".equals(name)) {
                filterDef.setDescription(evp.getValue().stringifyValue());
            } else if ("displayName".equals(name)) {
                filterDef.setDisplayName(evp.getValue().stringifyValue());
            } else if ("largeIcon".equals(name)) {
                filterDef.setLargeIcon(evp.getValue().stringifyValue());
            } else if ("smallIcon".equals(name)) {
                filterDef.setSmallIcon(evp.getValue().stringifyValue());
            } else if ("asyncSupported".equals(name)) {
                filterDef.setAsyncSupported(evp.getValue().stringifyValue());
            } else if ("initParams".equals(name)) {
                Map<String,String> initParams =
                    processAnnotationWebInitParams(evp.getValue());
                for (String paramName : initParams.keySet()) {
                    filterDef.addInitParameter(paramName,
                            initParams.get(paramName));
                }
            } else {
                // Ignore
            }
        }
        fragment.addFilter(filterDef);
        filterMap.setFilterName(filterDef.getFilterName());
        fragment.addFilterMapping(filterMap);
    }

    protected String[] processAnnotationsStringArray(ElementValue ev) {
        ArrayList<String> values = new ArrayList<String>();
        if (ev instanceof ArrayElementValue) {
            ElementValue[] arrayValues =
                ((ArrayElementValue) ev).getElementValuesArray();
            for (ElementValue value : arrayValues) {
                values.add(value.stringifyValue());
            }
        } else {
            values.add(ev.stringifyValue());
        }
        String[] result = new String[values.size()];
        return values.toArray(result);
    }
    
    protected Map<String,String> processAnnotationWebInitParams(
            ElementValue ev) {
        Map<String, String> result = new HashMap<String,String>();
        if (ev instanceof ArrayElementValue) {
            ElementValue[] arrayValues =
                ((ArrayElementValue) ev).getElementValuesArray();
            for (ElementValue value : arrayValues) {
                if (value instanceof AnnotationElementValue) {
                    ElementValuePair[] evps = ((AnnotationElementValue)
                            value).getAnnotationEntry().getElementValuePairs();
                    String initParamName = null;
                    String initParamValue = null;
                    for (ElementValuePair evp : evps) {
                        if ("name".equals(evp.getNameString())) {
                            initParamName = evp.getValue().stringifyValue();
                        } else if ("value".equals(evp.getNameString())) {
                            initParamValue = evp.getValue().stringifyValue();
                        } else {
                            // Ignore
                        }
                    }
                    result.put(initParamName, initParamValue);
                }
            }
        }
        return result;
    }
    
    private class FragmentJarScannerCallback implements JarScannerCallback {

        private static final String FRAGMENT_LOCATION =
            "META-INF/web-fragment.xml";
        private Map<String,WebXml> fragments = new HashMap<String,WebXml>();
        
        @Override
        public void scan(JarURLConnection urlConn) throws IOException {
            
            JarFile jarFile = null;
            InputStream stream = null;
            WebXml fragment = new WebXml();

            try {
                urlConn.setUseCaches(false);
                jarFile = urlConn.getJarFile();
                JarEntry fragmentEntry =
                    jarFile.getJarEntry(FRAGMENT_LOCATION);
                if (fragmentEntry == null) {
                    // If there is no web.xml, normal JAR no impact on
                    // distributable
                    fragment.setDistributable(true);
                } else {
                    stream = jarFile.getInputStream(fragmentEntry);
                    InputSource source = new InputSource(
                            urlConn.getJarFileURL().toString() +
                            File.separatorChar + FRAGMENT_LOCATION);
                    source.setByteStream(stream);
                    parseWebXml(source, fragment, true);
                }
            } finally {
                if (jarFile != null) {
                    try {
                        jarFile.close();
                    } catch (Throwable t) {
                        // ignore
                    }
                }
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (Throwable t) {
                        // ignore
                    }
                }
                fragment.setURL(urlConn.getURL());
                if (fragment.getName() == null) {
                    fragment.setName(fragment.getURL().toString());
                }
                fragments.put(fragment.getName(), fragment);
            }
        }

        @Override
        public void scan(File file) throws IOException {

            InputStream stream = null;
            WebXml fragment = null;
            
            try {
                File fragmentFile = new File(file, FRAGMENT_LOCATION);
                if (fragmentFile.isFile()) {
                    stream = new FileInputStream(fragmentFile);
                    InputSource source =
                        new InputSource(fragmentFile.toURI().toURL().toString());
                    source.setByteStream(stream);
                    fragment = new WebXml();
                    parseWebXml(source, fragment, true);
                }
            } finally {
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (Throwable t) {
                        // ignore
                    }
                }
                if (fragment == null) {
                    fragments.put(file.toURI().toURL().toString(), fragment);
                } else {
                    fragment.setURL(file.toURI().toURL());
                    if (fragment.getName() == null) {
                        fragment.setName(fragment.getURL().toString());
                    }
                    fragments.put(fragment.getName(), fragment);
                }
            }
        }
        
        public Map<String,WebXml> getFragments() {
            return fragments;
        }
    }


    protected static class ContextErrorHandler
        implements ErrorHandler {

        private SAXParseException parseException = null;

        public void error(SAXParseException exception) {
            parseException = exception;
        }

        public void fatalError(SAXParseException exception) {
            parseException = exception;
        }

        public void warning(SAXParseException exception) {
            parseException = exception;
        }

        public SAXParseException getParseException() {
            return parseException;
        }
    }


}

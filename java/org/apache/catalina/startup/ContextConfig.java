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
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.annotation.HandlesTypes;

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
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.Valve;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.util.ContextName;
import org.apache.catalina.util.Introspection;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.Jar;
import org.apache.tomcat.JarScanType;
import org.apache.tomcat.JarScanner;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.bcel.classfile.AnnotationElementValue;
import org.apache.tomcat.util.bcel.classfile.AnnotationEntry;
import org.apache.tomcat.util.bcel.classfile.ArrayElementValue;
import org.apache.tomcat.util.bcel.classfile.ClassFormatException;
import org.apache.tomcat.util.bcel.classfile.ClassParser;
import org.apache.tomcat.util.bcel.classfile.ElementValue;
import org.apache.tomcat.util.bcel.classfile.ElementValuePair;
import org.apache.tomcat.util.bcel.classfile.JavaClass;
import org.apache.tomcat.util.buf.UriUtil;
import org.apache.tomcat.util.descriptor.InputSourceUtil;
import org.apache.tomcat.util.descriptor.XmlErrorHandler;
import org.apache.tomcat.util.descriptor.web.ContextEjb;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.apache.tomcat.util.descriptor.web.ContextLocalEjb;
import org.apache.tomcat.util.descriptor.web.ContextResource;
import org.apache.tomcat.util.descriptor.web.ContextResourceEnvRef;
import org.apache.tomcat.util.descriptor.web.ContextService;
import org.apache.tomcat.util.descriptor.web.ErrorPage;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.apache.tomcat.util.descriptor.web.FragmentJarScannerCallback;
import org.apache.tomcat.util.descriptor.web.JspPropertyGroup;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.MessageDestinationRef;
import org.apache.tomcat.util.descriptor.web.MultipartDef;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.apache.tomcat.util.descriptor.web.SecurityRoleRef;
import org.apache.tomcat.util.descriptor.web.ServletDef;
import org.apache.tomcat.util.descriptor.web.SessionConfig;
import org.apache.tomcat.util.descriptor.web.WebXml;
import org.apache.tomcat.util.descriptor.web.WebXmlParser;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.RuleSet;
import org.apache.tomcat.util.file.ConfigFileLoader;
import org.apache.tomcat.util.file.ConfigurationSource;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.scan.JarFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

/**
 * Startup event listener for a <b>Context</b> that configures the properties of that Context, and the associated
 * defined servlets.
 *
 * @author Craig R. McClanahan
 */
public class ContextConfig implements LifecycleListener {

    private static final Log log = LogFactory.getLog(ContextConfig.class);

    /**
     * The string resources for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);


    protected static final LoginConfig DUMMY_LOGIN_CONFIG = new LoginConfig("NONE", null, null, null);


    /**
     * The set of Authenticators that we know how to configure. The key is the name of the implemented authentication
     * method, and the value is the fully qualified Java class name of the corresponding Valve.
     */
    protected static final Properties authenticators;

    static {
        // Load our mapping properties for the standard authenticators
        Properties props = new Properties();
        try (InputStream is = ContextConfig.class.getClassLoader()
                .getResourceAsStream("org/apache/catalina/startup/Authenticators.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException ioe) {
            props = null;
        }
        authenticators = props;
    }

    /**
     * Deployment count.
     */
    protected static long deploymentCount = 0L;


    /**
     * Cache of default web.xml fragments per Host
     */
    protected static final Map<Host,DefaultWebXmlCacheEntry> hostWebXmlCache = new ConcurrentHashMap<>();


    /**
     * Set used as the value for {@code JavaClassCacheEntry.sciSet} when there are no SCIs associated with a class.
     */
    private static final Set<ServletContainerInitializer> EMPTY_SCI_SET = Collections.emptySet();


    // ----------------------------------------------------- Instance Variables
    /**
     * Custom mappings of login methods to authenticators
     */
    protected Map<String,Authenticator> customAuthenticators;


    /**
     * The Context we are associated with.
     */
    protected volatile Context context = null;


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
     * Anti-locking docBase. It is a path to a copy of the web application in the java.io.tmpdir directory. This path is
     * always an absolute one.
     */
    private File antiLockingDocBase = null;


    /**
     * Map of ServletContainerInitializer to classes they expressed interest in.
     */
    protected final Map<ServletContainerInitializer,Set<Class<?>>> initializerClassMap = new LinkedHashMap<>();

    /**
     * Map of Types to ServletContainerInitializer that are interested in those types.
     */
    protected final Map<Class<?>,Set<ServletContainerInitializer>> typeInitializerMap = new HashMap<>();

    /**
     * Flag that indicates if at least one {@link HandlesTypes} entry is present that represents an annotation.
     */
    protected boolean handlesTypesAnnotations = false;

    /**
     * Flag that indicates if at least one {@link HandlesTypes} entry is present that represents a non-annotation.
     */
    protected boolean handlesTypesNonAnnotations = false;


    // ------------------------------------------------------------- Properties

    /**
     * Obtain the location of the default deployment descriptor.
     *
     * @return The path to the default web.xml. If not absolute, it is relative to CATALINA_BASE.
     */
    public String getDefaultWebXml() {
        if (defaultWebXml == null) {
            defaultWebXml = Constants.DefaultWebXml;
        }
        return defaultWebXml;
    }


    /**
     * Set the location of the default deployment descriptor.
     *
     * @param path The path to the default web.xml. If not absolute, it is relative to CATALINA_BASE.
     */
    public void setDefaultWebXml(String path) {
        this.defaultWebXml = path;
    }


    /**
     * Sets custom mappings of login methods to authenticators.
     *
     * @param customAuthenticators Custom mappings of login methods to authenticators
     */
    public void setCustomAuthenticators(Map<String,Authenticator> customAuthenticators) {
        this.customAuthenticators = customAuthenticators;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Process events for an associated Context.
     *
     * @param event The lifecycle event that has occurred
     */
    @Override
    public void lifecycleEvent(LifecycleEvent event) {

        // Identify the context we are associated with
        try {
            context = (Context) event.getLifecycle();
        } catch (ClassCastException e) {
            log.error(sm.getString("contextConfig.cce", event.getLifecycle()), e);
            return;
        }

        // Process the event that has occurred
        if (event.getType().equals(Lifecycle.CONFIGURE_START_EVENT)) {
            configureStart();
        } else if (event.getType().equals(Lifecycle.BEFORE_START_EVENT)) {
            beforeStart();
        } else if (event.getType().equals(Lifecycle.AFTER_START_EVENT)) {
            // Restore docBase for management tools
            if (originalDocBase != null) {
                context.setDocBase(originalDocBase);
            }
        } else if (event.getType().equals(Lifecycle.CONFIGURE_STOP_EVENT)) {
            configureStop();
        } else if (event.getType().equals(Lifecycle.AFTER_INIT_EVENT)) {
            init();
        } else if (event.getType().equals(Lifecycle.AFTER_DESTROY_EVENT)) {
            destroy();
        }

    }


    // -------------------------------------------------------- protected Methods


    /**
     * Process the application classes annotations, if it exists.
     */
    protected void applicationAnnotationsConfig() {

        long t1 = System.currentTimeMillis();

        WebAnnotationSet.loadApplicationAnnotations(context);

        long t2 = System.currentTimeMillis();
        if (context instanceof StandardContext) {
            ((StandardContext) context).setStartupTime(t2 - t1 + ((StandardContext) context).getStartupTime());
        }
    }


    /**
     * Set up an Authenticator automatically if required, and one has not already been configured.
     */
    protected void authenticatorConfig() {

        LoginConfig loginConfig = context.getLoginConfig();
        if (loginConfig == null) {
            // Need an authenticator to support HttpServletRequest.login()
            loginConfig = DUMMY_LOGIN_CONFIG;
            context.setLoginConfig(loginConfig);
        }

        // Has an authenticator been configured already?
        if (context.getAuthenticator() != null) {
            return;
        }

        // Has a Realm been configured for us to authenticate against?
        if (context.getRealm() == null) {
            log.error(sm.getString("contextConfig.missingRealm"));
            ok = false;
            return;
        }

        /*
         * First check to see if there is a custom mapping for the login method. If so, use it. Otherwise, check if
         * there is a mapping in org/apache/catalina/startup/Authenticators.properties.
         */
        Valve authenticator = null;
        if (customAuthenticators != null) {
            authenticator = (Valve) customAuthenticators.get(loginConfig.getAuthMethod());
        }

        if (authenticator == null) {
            if (authenticators == null) {
                log.error(sm.getString("contextConfig.authenticatorResources"));
                ok = false;
                return;
            }

            // Identify the class name of the Valve we should configure
            String authenticatorName = authenticators.getProperty(loginConfig.getAuthMethod());
            if (authenticatorName == null) {
                log.error(sm.getString("contextConfig.authenticatorMissing", loginConfig.getAuthMethod()));
                ok = false;
                return;
            }

            // Instantiate and install an Authenticator of the requested class
            try {
                Class<?> authenticatorClass = Class.forName(authenticatorName);
                authenticator = (Valve) authenticatorClass.getConstructor().newInstance();
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.error(sm.getString("contextConfig.authenticatorInstantiate", authenticatorName), t);
                ok = false;
            }
        }

        if (authenticator != null) {
            Pipeline pipeline = context.getPipeline();
            if (pipeline != null) {
                pipeline.addValve(authenticator);
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("contextConfig.authenticatorConfigured", loginConfig.getAuthMethod()));
                }
            }
        }
    }


    /**
     * Create (if necessary) and return a Digester configured to process the context configuration descriptor for an
     * application.
     *
     * @return the digester for context.xml files
     */
    protected Digester createContextDigester() {
        Digester digester = new Digester();
        digester.setValidating(false);
        digester.setRulesValidation(true);
        Map<Class<?>,List<String>> fakeAttributes = new HashMap<>();
        List<String> objectAttrs = new ArrayList<>();
        objectAttrs.add("className");
        fakeAttributes.put(Object.class, objectAttrs);
        // Ignore attribute added by Eclipse for its internal tracking
        List<String> contextAttrs = new ArrayList<>();
        contextAttrs.add("source");
        fakeAttributes.put(StandardContext.class, contextAttrs);
        digester.setFakeAttributes(fakeAttributes);
        RuleSet contextRuleSet = new ContextRuleSet("", false);
        digester.addRuleSet(contextRuleSet);
        RuleSet namingRuleSet = new NamingRuleSet("Context/");
        digester.addRuleSet(namingRuleSet);
        return digester;
    }


    protected boolean getGenerateCode() {
        Catalina catalina = Container.getService(context).getServer().getCatalina();
        if (catalina != null) {
            return catalina.getGenerateCode();
        } else {
            return false;
        }
    }


    protected boolean getUseGeneratedCode() {
        Catalina catalina = Container.getService(context).getServer().getCatalina();
        if (catalina != null) {
            return catalina.getUseGeneratedCode();
        } else {
            return false;
        }
    }


    protected File getGeneratedCodeLocation() {
        Catalina catalina = Container.getService(context).getServer().getCatalina();
        if (catalina != null) {
            return catalina.getGeneratedCodeLocation();
        } else {
            // Cannot happen
            return null;
        }
    }


    protected String getGeneratedCodePackage() {
        Catalina catalina = Container.getService(context).getServer().getCatalina();
        if (catalina != null) {
            return catalina.getGeneratedCodePackage();
        } else {
            return "generatedCodePackage";
        }
    }


    protected static String getContextXmlPackageName(String generatedCodePackage, Container container) {
        StringBuilder result = new StringBuilder();
        Container host = null;
        Container engine = null;
        while (container != null) {
            if (container instanceof Host) {
                host = container;
            } else if (container instanceof Engine) {
                engine = container;
            }
            container = container.getParent();
        }
        result.append(generatedCodePackage);
        if (engine != null) {
            result.append('.');
        }
        if (engine != null) {
            result.append(engine.getName());
            if (host != null) {
                result.append('.');
            }
        }
        if (host != null) {
            result.append(host.getName());
        }
        return result.toString();
    }


    protected File getContextXmlJavaSource(String contextXmlPackageName, String contextXmlSimpleClassName) {
        File generatedSourceFolder = getGeneratedCodeLocation();
        String path = contextXmlPackageName.replace('.', File.separatorChar);
        File packageFolder = new File(generatedSourceFolder, path);
        if (packageFolder.isDirectory() || packageFolder.mkdirs()) {
            return new File(packageFolder, contextXmlSimpleClassName + ".java");
        }
        return null;
    }


    protected void generateClassHeader(Digester digester, String packageName, String resourceName) {
        StringBuilder code = digester.getGeneratedCode();
        code.append("package ").append(packageName).append(';').append(System.lineSeparator());
        code.append("public class ").append(resourceName).append(" implements ");
        code.append(ContextXml.class.getName().replace('$', '.'));
        code.append(" {").append(System.lineSeparator());
        code.append("public void load(");
        code.append(Context.class.getName());
        String contextArgument = digester.toVariableName(context);
        code.append(' ').append(contextArgument).append(") throws Exception {").append(System.lineSeparator());
        // Create a new variable with the concrete type
        digester.setKnown(context);
        code.append(context.getClass().getName()).append(' ').append(digester.toVariableName(context));
        code.append(" = (").append(context.getClass().getName()).append(") ").append(contextArgument);
        code.append(';').append(System.lineSeparator());
    }


    protected void generateClassFooter(Digester digester) {
        StringBuilder code = digester.getGeneratedCode();
        code.append('}').append(System.lineSeparator());
        code.append('}').append(System.lineSeparator());
    }


    public interface ContextXml {
        void load(Context context) throws Exception;
    }


    /**
     * Process the default configuration file, if it exists.
     *
     * @param digester The digester that will be used for XML parsing
     */
    protected void contextConfig(Digester digester) {

        String defaultContextXml = null;

        boolean generateCode = getGenerateCode();
        boolean useGeneratedCode = getUseGeneratedCode();

        String contextXmlPackageName = null;
        String contextXmlSimpleClassName = null;
        String contextXmlClassName = null;
        File contextXmlJavaSource = null;

        // Open the default context.xml file, if it exists
        if (context instanceof StandardContext) {
            defaultContextXml = ((StandardContext) context).getDefaultContextXml();
        }
        // set the default if we don't have any overrides
        if (defaultContextXml == null) {
            defaultContextXml = Constants.DefaultContextXml;
        }

        ContextXml contextXml = null;

        if (!context.getOverride()) {

            if (useGeneratedCode || generateCode) {
                contextXmlPackageName = getGeneratedCodePackage();
                contextXmlSimpleClassName = "ContextXmlDefault";
                contextXmlClassName = contextXmlPackageName + "." + contextXmlSimpleClassName;
            }
            if (useGeneratedCode) {
                contextXml = (ContextXml) Digester.loadGeneratedClass(contextXmlClassName);
            }
            if (contextXml != null) {
                try {
                    contextXml.load(context);
                } catch (Exception e) {
                    log.warn(sm.getString("contextConfig.loadError"), e);
                }
                contextXml = null;
            } else if (!useGeneratedCode) {
                try (ConfigurationSource.Resource contextXmlResource =
                        ConfigFileLoader.getSource().getResource(defaultContextXml)) {
                    if (generateCode) {
                        contextXmlJavaSource =
                                getContextXmlJavaSource(contextXmlPackageName, contextXmlSimpleClassName);
                        if (contextXmlJavaSource != null) {
                            digester.startGeneratingCode();
                            generateClassHeader(digester, contextXmlPackageName, contextXmlSimpleClassName);
                        } else {
                            generateCode = false;
                        }
                    }
                    URL defaultContextUrl = contextXmlResource.getURI().toURL();
                    processContextConfig(digester, defaultContextUrl, contextXmlResource.getInputStream());
                    if (generateCode) {
                        generateClassFooter(digester);
                        try (FileWriter writer = new FileWriter(contextXmlJavaSource)) {
                            writer.write(digester.getGeneratedCode().toString());
                        }
                        digester.endGeneratingCode();
                        Digester.addGeneratedClass(contextXmlClassName);
                    }
                } catch (MalformedURLException e) {
                    log.error(sm.getString("contextConfig.badUrl", defaultContextXml), e);
                } catch (IOException e) {
                    // Not found
                }
            }

            if (useGeneratedCode || generateCode) {
                contextXmlPackageName = getContextXmlPackageName(getGeneratedCodePackage(), context);
                contextXmlSimpleClassName = "ContextXmlDefault";
                contextXmlClassName = contextXmlPackageName + "." + contextXmlSimpleClassName;
            }
            if (useGeneratedCode) {
                contextXml = (ContextXml) Digester.loadGeneratedClass(contextXmlClassName);
            }
            if (contextXml != null) {
                try {
                    contextXml.load(context);
                } catch (Exception e) {
                    log.warn(sm.getString("contextConfig.loadError"), e);
                }
                contextXml = null;
            } else if (!useGeneratedCode) {
                String hostContextFile = Container.getConfigPath(context, Constants.HostContextXml);
                try (ConfigurationSource.Resource contextXmlResource =
                        ConfigFileLoader.getSource().getResource(hostContextFile)) {
                    if (generateCode) {
                        contextXmlJavaSource =
                                getContextXmlJavaSource(contextXmlPackageName, contextXmlSimpleClassName);
                        digester.startGeneratingCode();
                        generateClassHeader(digester, contextXmlPackageName, contextXmlSimpleClassName);
                    }
                    URL defaultContextUrl = contextXmlResource.getURI().toURL();
                    processContextConfig(digester, defaultContextUrl, contextXmlResource.getInputStream());
                    if (generateCode) {
                        generateClassFooter(digester);
                        try (FileWriter writer = new FileWriter(contextXmlJavaSource)) {
                            writer.write(digester.getGeneratedCode().toString());
                        }
                        digester.endGeneratingCode();
                        Digester.addGeneratedClass(contextXmlClassName);
                    }
                } catch (MalformedURLException e) {
                    log.error(sm.getString("contextConfig.badUrl", hostContextFile), e);
                } catch (IOException e) {
                    // Not found
                }
            }
        }

        if (context.getConfigFile() != null) {
            if (useGeneratedCode || generateCode) {
                contextXmlPackageName = getContextXmlPackageName(getGeneratedCodePackage(), context);
                contextXmlSimpleClassName = "ContextXml_" + context.getName().replace('/', '_').replace("-", "__");
                contextXmlClassName = contextXmlPackageName + "." + contextXmlSimpleClassName;
            }
            if (useGeneratedCode) {
                contextXml = (ContextXml) Digester.loadGeneratedClass(contextXmlClassName);
            }
            if (contextXml != null) {
                try {
                    contextXml.load(context);
                } catch (Exception e) {
                    log.warn(sm.getString("contextConfig.loadError"), e);
                }
                contextXml = null;
            } else if (!useGeneratedCode) {
                if (generateCode) {
                    contextXmlJavaSource = getContextXmlJavaSource(contextXmlPackageName, contextXmlSimpleClassName);
                    digester.startGeneratingCode();
                    generateClassHeader(digester, contextXmlPackageName, contextXmlSimpleClassName);
                }
                processContextConfig(digester, context.getConfigFile(), null);
                if (generateCode) {
                    generateClassFooter(digester);
                    try (FileWriter writer = new FileWriter(contextXmlJavaSource)) {
                        writer.write(digester.getGeneratedCode().toString());
                    } catch (IOException e) {
                        // Ignore
                    }
                    digester.endGeneratingCode();
                    Digester.addGeneratedClass(contextXmlClassName);
                }
            }
        }

    }


    /**
     * Process a context.xml.
     *
     * @param digester   The digester that will be used for XML parsing
     * @param contextXml The URL to the context.xml configuration
     * @param stream     The XML resource stream
     */
    protected void processContextConfig(Digester digester, URL contextXml, InputStream stream) {

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("contextConfig.processContext", context.getName(), contextXml));
        }

        InputSource source = null;

        try {
            source = new InputSource(contextXml.toString());
            if (stream == null) {
                URLConnection xmlConn = contextXml.openConnection();
                xmlConn.setUseCaches(false);
                stream = xmlConn.getInputStream();
            }
        } catch (Exception e) {
            log.error(sm.getString("contextConfig.contextMissing", contextXml), e);
        }

        if (source == null) {
            return;
        }

        try {
            source.setByteStream(stream);
            digester.setClassLoader(this.getClass().getClassLoader());
            digester.setUseContextClassLoader(false);
            digester.push(context.getParent());
            digester.push(context);
            XmlErrorHandler errorHandler = new XmlErrorHandler();
            digester.setErrorHandler(errorHandler);
            digester.parse(source);
            if (errorHandler.getWarnings().size() > 0 || errorHandler.getErrors().size() > 0) {
                errorHandler.logFindings(log, contextXml.toString());
                ok = false;
            }
            if (log.isTraceEnabled()) {
                log.trace("Successfully processed context [" + context.getName() + "] configuration file [" +
                        contextXml + "]");
            }
        } catch (SAXParseException e) {
            log.error(sm.getString("contextConfig.contextParse", context.getName()), e);
            log.error(sm.getString("contextConfig.defaultPosition", "" + e.getLineNumber(), "" + e.getColumnNumber()));
            ok = false;
        } catch (Exception e) {
            log.error(sm.getString("contextConfig.contextParse", context.getName()), e);
            ok = false;
        } finally {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (IOException e) {
                log.error(sm.getString("contextConfig.contextClose"), e);
            }
        }
    }


    /**
     * Adjust docBase.
     *
     * @throws IOException cannot access the context base path
     */
    protected void fixDocBase() throws IOException {

        Host host = (Host) context.getParent();
        File appBase = host.getAppBaseFile();

        // This could be blank, relative, absolute or canonical
        String docBaseConfigured = context.getDocBase();
        // If there is no explicit docBase, derive it from the path and version
        if (docBaseConfigured == null) {
            // Trying to guess the docBase according to the path
            String path = context.getPath();
            if (path == null) {
                return;
            }
            ContextName cn = new ContextName(path, context.getWebappVersion());
            docBaseConfigured = cn.getBaseName();
        }

        // Obtain the absolute docBase in String and File form
        String docBaseAbsolute;
        File docBaseConfiguredFile = new File(docBaseConfigured);
        if (!docBaseConfiguredFile.isAbsolute()) {
            docBaseAbsolute = (new File(appBase, docBaseConfigured)).getAbsolutePath();
        } else {
            docBaseAbsolute = docBaseConfiguredFile.getAbsolutePath();
        }
        File docBaseAbsoluteFile = new File(docBaseAbsolute);
        String originalDocBase = docBaseAbsolute;

        ContextName cn = new ContextName(context.getPath(), context.getWebappVersion());
        String pathName = cn.getBaseName();

        boolean unpackWARs = true;
        if (host instanceof StandardHost) {
            unpackWARs = ((StandardHost) host).isUnpackWARs();
            if (unpackWARs && context instanceof StandardContext) {
                unpackWARs = ((StandardContext) context).getUnpackWAR();
            }
        }

        // At this point we need to determine if we have a WAR file in the
        // appBase that needs to be expanded. Therefore we consider the absolute
        // docBase NOT the canonical docBase. This is because some users symlink
        // WAR files into the appBase and we want this to work correctly.
        boolean docBaseAbsoluteInAppBase = docBaseAbsolute.startsWith(appBase.getPath() + File.separatorChar);
        if (docBaseAbsolute.toLowerCase(Locale.ENGLISH).endsWith(".war") && !docBaseAbsoluteFile.isDirectory()) {
            URL war = UriUtil.buildJarUrl(docBaseAbsoluteFile);
            if (unpackWARs) {
                docBaseAbsolute = ExpandWar.expand(host, war, pathName);
                docBaseAbsoluteFile = new File(docBaseAbsolute);
                if (context instanceof StandardContext) {
                    ((StandardContext) context).setOriginalDocBase(originalDocBase);
                }
            } else {
                ExpandWar.validate(host, war, pathName);
            }
        } else {
            File docBaseAbsoluteFileWar = new File(docBaseAbsolute + ".war");
            URL war = null;
            if (docBaseAbsoluteFileWar.exists() && docBaseAbsoluteInAppBase) {
                war = UriUtil.buildJarUrl(docBaseAbsoluteFileWar);
            }
            if (docBaseAbsoluteFile.exists()) {
                if (war != null && unpackWARs) {
                    // Check if WAR needs to be re-expanded (e.g. if it has
                    // changed). Note: HostConfig.deployWar() takes care of
                    // ensuring that the correct XML file is used.
                    // This will be a NO-OP if the WAR is unchanged.
                    ExpandWar.expand(host, war, pathName);
                }
            } else {
                if (war != null) {
                    if (unpackWARs) {
                        docBaseAbsolute = ExpandWar.expand(host, war, pathName);
                        docBaseAbsoluteFile = new File(docBaseAbsolute);
                    } else {
                        docBaseAbsoluteFile = docBaseAbsoluteFileWar;
                        ExpandWar.validate(host, war, pathName);
                    }
                }
                if (context instanceof StandardContext) {
                    ((StandardContext) context).setOriginalDocBase(originalDocBase);
                }
            }
        }

        String docBaseCanonical = docBaseAbsoluteFile.getCanonicalPath();

        // Re-calculate now docBase is a canonical path
        boolean docBaseCanonicalInAppBase =
                docBaseAbsoluteFile.getCanonicalFile().toPath().startsWith(appBase.toPath());
        String docBase;
        if (docBaseCanonicalInAppBase) {
            docBase = docBaseCanonical.substring(appBase.getPath().length());
            docBase = docBase.replace(File.separatorChar, '/');
            if (docBase.startsWith("/")) {
                docBase = docBase.substring(1);
            }
        } else {
            docBase = docBaseCanonical.replace(File.separatorChar, '/');
        }

        context.setDocBase(docBase);
    }


    protected void antiLocking() {

        if ((context instanceof StandardContext) && ((StandardContext) context).getAntiResourceLocking()) {

            Host host = (Host) context.getParent();
            String docBase = context.getDocBase();
            if (docBase == null) {
                return;
            }
            originalDocBase = docBase;

            File docBaseFile = new File(docBase);
            if (!docBaseFile.isAbsolute()) {
                docBaseFile = new File(host.getAppBaseFile(), docBase);
            }

            String path = context.getPath();
            if (path == null) {
                return;
            }
            ContextName cn = new ContextName(path, context.getWebappVersion());
            docBase = cn.getBaseName();

            String tmp = System.getProperty("java.io.tmpdir");
            File tmpFile = new File(tmp);
            if (!tmpFile.isDirectory()) {
                log.error(sm.getString("contextConfig.noAntiLocking", tmp, context.getName()));
                return;
            }

            if (originalDocBase.toLowerCase(Locale.ENGLISH).endsWith(".war")) {
                antiLockingDocBase = new File(tmpFile, deploymentCount++ + "-" + docBase + ".war");
            } else {
                antiLockingDocBase = new File(tmpFile, deploymentCount++ + "-" + docBase);
            }
            antiLockingDocBase = antiLockingDocBase.getAbsoluteFile();

            if (log.isDebugEnabled()) {
                log.debug(sm.getString("contextConfig.antiLocking", context.getName(), antiLockingDocBase.getPath()));
            }

            // Cleanup just in case an old deployment is lying around
            ExpandWar.delete(antiLockingDocBase);
            if (ExpandWar.copy(docBaseFile, antiLockingDocBase)) {
                context.setDocBase(antiLockingDocBase.getPath());
            }
        }
    }


    /**
     * Process a "init" event for this Context.
     */
    protected synchronized void init() {
        // Called from StandardContext.init()

        Digester contextDigester = null;
        if (!getUseGeneratedCode()) {
            contextDigester = createContextDigester();
            contextDigester.getParser();
        }

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("contextConfig.init"));
        }
        context.setConfigured(false);
        ok = true;

        contextConfig(contextDigester);
    }


    /**
     * Process a "before start" event for this Context.
     */
    protected synchronized void beforeStart() {

        try {
            fixDocBase();
        } catch (IOException e) {
            log.error(sm.getString("contextConfig.fixDocBase", context.getName()), e);
        }

        antiLocking();
    }


    /**
     * Process a "contextConfig" event for this Context.
     */
    protected synchronized void configureStart() {
        // Called from StandardContext.start()

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("contextConfig.start"));
        }

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("contextConfig.xmlSettings", context.getName(),
                    Boolean.valueOf(context.getXmlValidation()), Boolean.valueOf(context.getXmlNamespaceAware())));
        }

        webConfig();

        if (!context.getIgnoreAnnotations()) {
            applicationAnnotationsConfig();
        }
        if (ok) {
            validateSecurityRoles();
        }

        // Configure an authenticator if we need one
        if (ok) {
            authenticatorConfig();
        }

        // Dump the contents of this pipeline if requested
        if (log.isTraceEnabled()) {
            log.trace("Pipeline Configuration:");
            Pipeline pipeline = context.getPipeline();
            Valve valves[] = null;
            if (pipeline != null) {
                valves = pipeline.getValves();
            }
            if (valves != null) {
                for (Valve valve : valves) {
                    log.trace("  " + valve.getClass().getName());
                }
            }
            log.trace("======================");
        }

        // Make our application available if no problems were encountered
        if (ok) {
            context.setConfigured(true);
        } else {
            log.error(sm.getString("contextConfig.unavailable"));
            context.setConfigured(false);
        }

    }


    /**
     * Process a "stop" event for this Context.
     */
    protected synchronized void configureStop() {

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("contextConfig.stop"));
        }

        int i;

        // Removing children
        Container[] children = context.findChildren();
        for (i = 0; i < children.length; i++) {
            context.removeChild(children[i]);
        }

        // Removing security constraints
        SecurityConstraint[] securityConstraints = context.findConstraints();
        for (i = 0; i < securityConstraints.length; i++) {
            context.removeConstraint(securityConstraints[i]);
        }

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
        if (antiLockingDocBase != null) {
            // No need to log failure - it is expected in this case
            ExpandWar.delete(antiLockingDocBase, false);
        }

        // Reset ServletContextInitializer scanning
        initializerClassMap.clear();
        typeInitializerMap.clear();

        ok = true;

    }


    /**
     * Process a "destroy" event for this Context.
     */
    protected synchronized void destroy() {
        // Called from StandardContext.destroy()
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("contextConfig.destroy"));
        }

        // Skip clearing the work directory if Tomcat is being shutdown
        Server s = getServer();
        if (s != null && !s.getState().isAvailable()) {
            return;
        }

        // Changed to getWorkPath per Bugzilla 35819.
        if (context instanceof StandardContext) {
            String workDir = ((StandardContext) context).getWorkPath();
            if (workDir != null) {
                ExpandWar.delete(new File(workDir));
            }
        }
    }


    private Server getServer() {
        Container c = context;
        while (c != null && !(c instanceof Engine)) {
            c = c.getParent();
        }

        if (c == null) {
            return null;
        }

        Service s = ((Engine) c).getService();

        if (s == null) {
            return null;
        }

        return s.getServer();
    }

    /**
     * Validate the usage of security role names in the web application deployment descriptor. If any problems are
     * found, issue warning messages (for backwards compatibility) and add the missing roles. (To make these problems
     * fatal instead, simply set the <code>ok</code> instance variable to <code>false</code> as well).
     */
    protected void validateSecurityRoles() {

        // Check role names used in <security-constraint> elements
        SecurityConstraint constraints[] = context.findConstraints();
        for (SecurityConstraint constraint : constraints) {
            String roles[] = constraint.findAuthRoles();
            for (String role : roles) {
                if (!"*".equals(role) && !context.findSecurityRole(role)) {
                    log.warn(sm.getString("contextConfig.role.auth", role));
                    context.addSecurityRole(role);
                }
            }
        }

        // Check role names used in <servlet> elements
        Container wrappers[] = context.findChildren();
        for (Container container : wrappers) {
            Wrapper wrapper = (Wrapper) container;
            String runAs = wrapper.getRunAs();
            if ((runAs != null) && !context.findSecurityRole(runAs)) {
                log.warn(sm.getString("contextConfig.role.runas", runAs));
                context.addSecurityRole(runAs);
            }
            String names[] = wrapper.findSecurityReferences();
            for (String name : names) {
                String link = wrapper.findSecurityReference(name);
                if ((link != null) && !context.findSecurityRole(link)) {
                    log.warn(sm.getString("contextConfig.role.link", link));
                    context.addSecurityRole(link);
                }
            }
        }

    }


    protected File getHostConfigBase() {
        File file = null;
        if (context.getParent() instanceof Host) {
            file = ((Host) context.getParent()).getConfigBaseFile();
        }
        return file;
    }

    /**
     * Scan the web.xml files that apply to the web application and merge them using the rules defined in the spec. For
     * the global web.xml files, where there is duplicate configuration, the most specific level wins. ie an
     * application's web.xml takes precedence over the host level or global web.xml file.
     */
    protected void webConfig() {
        /*
         * Anything and everything can override the global and host defaults. This is implemented in two parts:
         *
         * - Handle as a web fragment that gets added after everything else so everything else takes priority
         *
         * - Mark Servlets as overridable so SCI configuration can replace configuration from the defaults
         */

        /*
         * The rules for annotation scanning are not as clear-cut as one might think. Tomcat implements the following
         * process:
         *
         * - As per SRV.1.6.2, Tomcat will scan for annotations regardless of which Servlet spec version is declared in
         * web.xml. The EG has confirmed this is the expected behaviour.
         *
         * - As per http://java.net/jira/browse/SERVLET_SPEC-36, if the main web.xml is marked as metadata-complete,
         * JARs are still processed for SCIs.
         *
         * - If metadata-complete=true and an absolute ordering is specified, JARs excluded from the ordering are also
         * excluded from the SCI processing.
         *
         * - If an SCI has a @HandlesType annotation then all classes (except those in JARs excluded from an absolute
         * ordering) need to be scanned to check if they match.
         */
        WebXmlParser webXmlParser = new WebXmlParser(context.getXmlNamespaceAware(), context.getXmlValidation(),
                context.getXmlBlockExternal());

        Set<WebXml> defaults = new HashSet<>();
        defaults.add(getDefaultWebXmlFragment(webXmlParser));

        Set<WebXml> tomcatWebXml = new HashSet<>();
        tomcatWebXml.add(getTomcatWebXmlFragment(webXmlParser));

        WebXml webXml = createWebXml();

        // Parse context level web.xml
        InputSource contextWebXml = getContextWebXmlSource();
        if (!webXmlParser.parseWebXml(contextWebXml, webXml, false)) {
            ok = false;
        }

        ServletContext sContext = context.getServletContext();

        // Ordering is important here

        // Step 1. Identify all the JARs packaged with the application and those
        // provided by the container. If any of the application JARs have a
        // web-fragment.xml it will be parsed at this point. web-fragment.xml
        // files are ignored for container provided JARs.
        Map<String,WebXml> fragments = processJarsForWebFragments(webXml, webXmlParser);

        // Step 2. Order the fragments.
        Set<WebXml> orderedFragments = null;
        orderedFragments = WebXml.orderWebFragments(webXml, fragments, sContext);

        // Step 3. Look for ServletContainerInitializer implementations
        if (ok) {
            processServletContainerInitializers();
        }

        if (!webXml.isMetadataComplete() || typeInitializerMap.size() > 0) {
            // Steps 4 & 5.
            processClasses(webXml, orderedFragments);
        }

        if (!webXml.isMetadataComplete()) {
            // Step 6. Merge web-fragment.xml files into the main web.xml
            // file.
            if (ok) {
                ok = webXml.merge(orderedFragments);
            }

            // Step 7a
            // merge tomcat-web.xml
            webXml.merge(tomcatWebXml);

            // Step 7b. Apply global defaults
            // Have to merge defaults before JSP conversion since defaults
            // provide JSP servlet definition.
            webXml.merge(defaults);

            // Step 8. Convert explicitly mentioned jsps to servlets
            if (ok) {
                convertJsps(webXml);
            }

            // Step 9. Apply merged web.xml to Context
            if (ok) {
                configureContext(webXml);
            }
        } else {
            webXml.merge(tomcatWebXml);
            webXml.merge(defaults);
            convertJsps(webXml);
            configureContext(webXml);
        }

        if (context.getLogEffectiveWebXml()) {
            log.info(sm.getString("contextConfig.effectiveWebXml", webXml.toXml()));
        }

        // Always need to look for static resources
        // Step 10. Look for static resources packaged in JARs
        if (ok) {
            // Spec does not define an order.
            // Use ordered JARs followed by remaining JARs
            Set<WebXml> resourceJars = new LinkedHashSet<>(orderedFragments);
            for (WebXml fragment : fragments.values()) {
                if (!resourceJars.contains(fragment)) {
                    resourceJars.add(fragment);
                }
            }
            processResourceJARs(resourceJars);
            // See also StandardContext.resourcesStart() for
            // WEB-INF/classes/META-INF/resources configuration
        }

        // Step 11. Apply the ServletContainerInitializer config to the
        // context
        if (ok) {
            for (Map.Entry<ServletContainerInitializer,Set<Class<?>>> entry : initializerClassMap.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    context.addServletContainerInitializer(entry.getKey(), null);
                } else {
                    context.addServletContainerInitializer(entry.getKey(), entry.getValue());
                }
            }
        }
    }


    protected void processClasses(WebXml webXml, Set<WebXml> orderedFragments) {
        // Step 4. Process /WEB-INF/classes for annotations and
        // @HandlesTypes matches

        Map<String,JavaClassCacheEntry> javaClassCache;

        if (context.getParallelAnnotationScanning()) {
            javaClassCache = new ConcurrentHashMap<>();
        } else {
            javaClassCache = new HashMap<>();
        }

        if (ok) {
            WebResource[] webResources = context.getResources().listResources("/WEB-INF/classes");

            for (WebResource webResource : webResources) {
                // Skip the META-INF directory from any JARs that have been
                // expanded in to WEB-INF/classes (sometimes IDEs do this).
                if ("META-INF".equals(webResource.getName())) {
                    continue;
                }
                processAnnotationsWebResource(webResource, webXml, webXml.isMetadataComplete(), javaClassCache);
            }
        }

        // Step 5. Process JARs for annotations and
        // @HandlesTypes matches - only need to process those fragments we
        // are going to use (remember orderedFragments includes any
        // container fragments)
        if (ok) {
            processAnnotations(orderedFragments, webXml.isMetadataComplete(), javaClassCache);
        }

        // Cache, if used, is no longer required so clear it
        javaClassCache.clear();
    }


    private void configureContext(WebXml webxml) {
        // As far as possible, process in alphabetical order so it is easy to
        // check everything is present
        // Some validation depends on correct public ID
        context.setPublicId(webxml.getPublicId());

        // Everything else in order
        context.setEffectiveMajorVersion(webxml.getMajorVersion());
        context.setEffectiveMinorVersion(webxml.getMinorVersion());

        for (Entry<String,String> entry : webxml.getContextParams().entrySet()) {
            context.addParameter(entry.getKey(), entry.getValue());
        }
        context.setDenyUncoveredHttpMethods(webxml.getDenyUncoveredHttpMethods());
        context.setDisplayName(webxml.getDisplayName());
        context.setDistributable(webxml.isDistributable());
        for (ContextLocalEjb ejbLocalRef : webxml.getEjbLocalRefs().values()) {
            context.getNamingResources().addLocalEjb(ejbLocalRef);
        }
        for (ContextEjb ejbRef : webxml.getEjbRefs().values()) {
            context.getNamingResources().addEjb(ejbRef);
        }
        for (ContextEnvironment environment : webxml.getEnvEntries().values()) {
            context.getNamingResources().addEnvironment(environment);
        }
        for (ErrorPage errorPage : webxml.getErrorPages().values()) {
            context.addErrorPage(errorPage);
        }
        for (FilterDef filter : webxml.getFilters().values()) {
            if (filter.getAsyncSupported() == null) {
                filter.setAsyncSupported("false");
            }
            context.addFilterDef(filter);
        }
        for (FilterMap filterMap : webxml.getFilterMappings()) {
            context.addFilterMap(filterMap);
        }
        context.setJspConfigDescriptor(webxml.getJspConfigDescriptor());
        for (String listener : webxml.getListeners()) {
            context.addApplicationListener(listener);
        }
        for (Entry<String,String> entry : webxml.getLocaleEncodingMappings().entrySet()) {
            context.addLocaleEncodingMappingParameter(entry.getKey(), entry.getValue());
        }
        // Prevents IAE
        if (webxml.getLoginConfig() != null) {
            context.setLoginConfig(webxml.getLoginConfig());
        }
        for (MessageDestinationRef mdr : webxml.getMessageDestinationRefs().values()) {
            context.getNamingResources().addMessageDestinationRef(mdr);
        }

        // messageDestinations were ignored in Tomcat 6, so ignore here

        context.setMetadataComplete(webxml.isMetadataComplete());
        for (Entry<String,String> entry : webxml.getMimeMappings().entrySet()) {
            context.addMimeMapping(entry.getKey(), entry.getValue());
        }
        context.setRequestCharacterEncoding(webxml.getRequestCharacterEncoding());
        // Name is just used for ordering
        for (ContextResourceEnvRef resource : webxml.getResourceEnvRefs().values()) {
            context.getNamingResources().addResourceEnvRef(resource);
        }
        for (ContextResource resource : webxml.getResourceRefs().values()) {
            context.getNamingResources().addResource(resource);
        }
        context.setResponseCharacterEncoding(webxml.getResponseCharacterEncoding());
        boolean allAuthenticatedUsersIsAppRole =
                webxml.getSecurityRoles().contains(SecurityConstraint.ROLE_ALL_AUTHENTICATED_USERS);
        for (SecurityConstraint constraint : webxml.getSecurityConstraints()) {
            if (allAuthenticatedUsersIsAppRole) {
                constraint.treatAllAuthenticatedUsersAsApplicationRole();
            }
            context.addConstraint(constraint);
        }
        for (String role : webxml.getSecurityRoles()) {
            context.addSecurityRole(role);
        }
        for (ContextService service : webxml.getServiceRefs().values()) {
            context.getNamingResources().addService(service);
        }
        for (ServletDef servlet : webxml.getServlets().values()) {
            Wrapper wrapper = context.createWrapper();
            // Description is ignored
            // Display name is ignored
            // Icons are ignored

            // jsp-file gets passed to the JSP Servlet as an init-param

            if (servlet.getLoadOnStartup() != null) {
                wrapper.setLoadOnStartup(servlet.getLoadOnStartup().intValue());
            }
            if (servlet.getEnabled() != null) {
                wrapper.setEnabled(servlet.getEnabled().booleanValue());
            }
            wrapper.setName(servlet.getServletName());
            Map<String,String> params = servlet.getParameterMap();
            for (Entry<String,String> entry : params.entrySet()) {
                wrapper.addInitParameter(entry.getKey(), entry.getValue());
            }
            wrapper.setRunAs(servlet.getRunAs());
            Set<SecurityRoleRef> roleRefs = servlet.getSecurityRoleRefs();
            for (SecurityRoleRef roleRef : roleRefs) {
                wrapper.addSecurityReference(roleRef.getName(), roleRef.getLink());
            }
            wrapper.setServletClass(servlet.getServletClass());
            MultipartDef multipartdef = servlet.getMultipartDef();
            if (multipartdef != null) {
                long maxFileSize = -1;
                long maxRequestSize = -1;
                int fileSizeThreshold = 0;

                if (null != multipartdef.getMaxFileSize()) {
                    maxFileSize = Long.parseLong(multipartdef.getMaxFileSize());
                }
                if (null != multipartdef.getMaxRequestSize()) {
                    maxRequestSize = Long.parseLong(multipartdef.getMaxRequestSize());
                }
                if (null != multipartdef.getFileSizeThreshold()) {
                    fileSizeThreshold = Integer.parseInt(multipartdef.getFileSizeThreshold());
                }

                wrapper.setMultipartConfigElement(new MultipartConfigElement(multipartdef.getLocation(), maxFileSize,
                        maxRequestSize, fileSizeThreshold));
            }
            if (servlet.getAsyncSupported() != null) {
                wrapper.setAsyncSupported(servlet.getAsyncSupported().booleanValue());
            }
            wrapper.setOverridable(servlet.isOverridable());
            context.addChild(wrapper);
        }
        for (Entry<String,String> entry : webxml.getServletMappings().entrySet()) {
            context.addServletMappingDecoded(entry.getKey(), entry.getValue());
        }
        SessionConfig sessionConfig = webxml.getSessionConfig();
        if (sessionConfig != null) {
            if (sessionConfig.getSessionTimeout() != null) {
                context.setSessionTimeout(sessionConfig.getSessionTimeout().intValue());
            }
            SessionCookieConfig scc = context.getServletContext().getSessionCookieConfig();
            scc.setName(sessionConfig.getCookieName());
            Map<String,String> attributes = sessionConfig.getCookieAttributes();
            for (Map.Entry<String,String> attribute : attributes.entrySet()) {
                scc.setAttribute(attribute.getKey(), attribute.getValue());
            }
            if (sessionConfig.getSessionTrackingModes().size() > 0) {
                context.getServletContext().setSessionTrackingModes(sessionConfig.getSessionTrackingModes());
            }
        }

        // Context doesn't use version directly

        for (String welcomeFile : webxml.getWelcomeFiles()) {
            /*
             * The following will result in a welcome file of "" so don't add that to the context <welcome-file-list>
             * <welcome-file/> </welcome-file-list>
             */
            if (welcomeFile != null && welcomeFile.length() > 0) {
                context.addWelcomeFile(welcomeFile);
            }
        }

        // Do this last as it depends on servlets
        for (JspPropertyGroup jspPropertyGroup : webxml.getJspPropertyGroups()) {
            String jspServletName = context.findServletMapping("*.jsp");
            if (jspServletName == null) {
                jspServletName = "jsp";
            }
            if (context.findChild(jspServletName) != null) {
                for (String urlPattern : jspPropertyGroup.getUrlPatterns()) {
                    context.addServletMappingDecoded(urlPattern, jspServletName, true);
                }
            } else {
                if (log.isDebugEnabled()) {
                    for (String urlPattern : jspPropertyGroup.getUrlPatterns()) {
                        log.debug(sm.getString("contextConfig.noJsp", urlPattern, jspServletName));
                    }
                }
            }
        }

        for (Entry<String,String> entry : webxml.getPostConstructMethods().entrySet()) {
            context.addPostConstructMethod(entry.getKey(), entry.getValue());
        }

        for (Entry<String,String> entry : webxml.getPreDestroyMethods().entrySet()) {
            context.addPreDestroyMethod(entry.getKey(), entry.getValue());
        }
    }


    private WebXml getTomcatWebXmlFragment(WebXmlParser webXmlParser) {

        WebXml webXmlTomcatFragment = createWebXml();
        webXmlTomcatFragment.setOverridable(true);

        // Set to distributable else every app will be prevented from being
        // distributable when the Tomcat fragment is merged with the main
        // web.xml
        webXmlTomcatFragment.setDistributable(true);
        // When merging, the default welcome files are only used if the app has
        // not defined any welcomes files.
        webXmlTomcatFragment.setAlwaysAddWelcomeFiles(false);

        WebResource resource = context.getResources().getResource(Constants.TomcatWebXml);
        if (resource.isFile()) {
            try {
                InputSource source = new InputSource(resource.getURL().toURI().toString());
                source.setByteStream(resource.getInputStream());
                if (!webXmlParser.parseWebXml(source, webXmlTomcatFragment, false)) {
                    ok = false;
                }
            } catch (URISyntaxException e) {
                log.error(sm.getString("contextConfig.tomcatWebXmlError"), e);
            }
        }
        return webXmlTomcatFragment;
    }


    private WebXml getDefaultWebXmlFragment(WebXmlParser webXmlParser) {

        // Host should never be null
        Host host = (Host) context.getParent();

        DefaultWebXmlCacheEntry entry = hostWebXmlCache.get(host);

        InputSource globalWebXml = getGlobalWebXmlSource();
        InputSource hostWebXml = getHostWebXmlSource();

        long globalTimeStamp = 0;
        long hostTimeStamp = 0;

        if (globalWebXml != null) {
            URLConnection uc = null;
            try {
                URI uri = new URI(globalWebXml.getSystemId());
                URL url = uri.toURL();
                uc = url.openConnection();
                globalTimeStamp = uc.getLastModified();
            } catch (IOException | URISyntaxException | IllegalArgumentException e) {
                globalTimeStamp = -1;
            } finally {
                if (uc != null) {
                    try {
                        uc.getInputStream().close();
                    } catch (IOException e) {
                        ExceptionUtils.handleThrowable(e);
                        globalTimeStamp = -1;
                    }
                }
            }
        }

        if (hostWebXml != null) {
            URLConnection uc = null;
            try {
                URI uri = new URI(hostWebXml.getSystemId());
                URL url = uri.toURL();
                uc = url.openConnection();
                hostTimeStamp = uc.getLastModified();
            } catch (IOException | URISyntaxException | IllegalArgumentException e) {
                hostTimeStamp = -1;
            } finally {
                if (uc != null) {
                    try {
                        uc.getInputStream().close();
                    } catch (IOException e) {
                        ExceptionUtils.handleThrowable(e);
                        hostTimeStamp = -1;
                    }
                }
            }
        }

        if (entry != null && entry.getGlobalTimeStamp() == globalTimeStamp &&
                entry.getHostTimeStamp() == hostTimeStamp) {
            InputSourceUtil.close(globalWebXml);
            InputSourceUtil.close(hostWebXml);
            return entry.getWebXml();
        }

        // Parsing global web.xml is relatively expensive. Use a sync block to
        // make sure it only happens once. Use the pipeline since a lock will
        // already be held on the host by another thread
        synchronized (host.getPipeline()) {
            entry = hostWebXmlCache.get(host);
            if (entry != null && entry.getGlobalTimeStamp() == globalTimeStamp &&
                    entry.getHostTimeStamp() == hostTimeStamp) {
                return entry.getWebXml();
            }

            WebXml webXmlDefaultFragment = createWebXml();
            webXmlDefaultFragment.setOverridable(true);
            // Set to distributable else every app will be prevented from being
            // distributable when the default fragment is merged with the main
            // web.xml
            webXmlDefaultFragment.setDistributable(true);
            // When merging, the default welcome files are only used if the app has
            // not defined any welcomes files.
            webXmlDefaultFragment.setAlwaysAddWelcomeFiles(false);

            // Parse global web.xml if present
            if (globalWebXml == null) {
                // This is unusual enough to log
                log.info(sm.getString("contextConfig.defaultMissing"));
            } else {
                if (!webXmlParser.parseWebXml(globalWebXml, webXmlDefaultFragment, false)) {
                    ok = false;
                }
            }

            // Parse host level web.xml if present
            // Additive apart from welcome pages
            webXmlDefaultFragment.setReplaceWelcomeFiles(true);

            if (!webXmlParser.parseWebXml(hostWebXml, webXmlDefaultFragment, false)) {
                ok = false;
            }

            // Don't update the cache if an error occurs
            if (globalTimeStamp != -1 && hostTimeStamp != -1) {
                entry = new DefaultWebXmlCacheEntry(webXmlDefaultFragment, globalTimeStamp, hostTimeStamp);
                hostWebXmlCache.put(host, entry);
                // Add a Lifecycle listener to the Host that will remove it from
                // the hostWebXmlCache once the Host is destroyed
                host.addLifecycleListener(new HostWebXmlCacheCleaner());
            }

            return webXmlDefaultFragment;
        }
    }


    private void convertJsps(WebXml webXml) {
        Map<String,String> jspInitParams;
        ServletDef jspServlet = webXml.getServlets().get("jsp");
        if (jspServlet == null) {
            jspInitParams = new HashMap<>();
            Wrapper w = (Wrapper) context.findChild("jsp");
            if (w != null) {
                String[] params = w.findInitParameters();
                for (String param : params) {
                    jspInitParams.put(param, w.findInitParameter(param));
                }
            }
        } else {
            jspInitParams = jspServlet.getParameterMap();
        }
        for (ServletDef servletDef : webXml.getServlets().values()) {
            if (servletDef.getJspFile() != null) {
                convertJsp(servletDef, jspInitParams);
            }
        }
    }

    private void convertJsp(ServletDef servletDef, Map<String,String> jspInitParams) {
        servletDef.setServletClass(org.apache.catalina.core.Constants.JSP_SERVLET_CLASS);
        String jspFile = servletDef.getJspFile();
        if ((jspFile != null) && !jspFile.startsWith("/")) {
            if (context.isServlet22()) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("contextConfig.jspFile.warning", jspFile));
                }
                jspFile = "/" + jspFile;
            } else {
                throw new IllegalArgumentException(sm.getString("contextConfig.jspFile.error", jspFile));
            }
        }
        servletDef.getParameterMap().put("jspFile", jspFile);
        servletDef.setJspFile(null);
        for (Map.Entry<String,String> initParam : jspInitParams.entrySet()) {
            servletDef.addInitParameter(initParam.getKey(), initParam.getValue());
        }
    }

    protected WebXml createWebXml() {
        return new WebXml();
    }

    /**
     * Scan JARs for ServletContainerInitializer implementations.
     */
    protected void processServletContainerInitializers() {

        List<ServletContainerInitializer> detectedScis;
        try {
            WebappServiceLoader<ServletContainerInitializer> loader = new WebappServiceLoader<>(context);
            detectedScis = loader.load(ServletContainerInitializer.class);
        } catch (IOException e) {
            log.error(sm.getString("contextConfig.servletContainerInitializerFail", context.getName()), e);
            ok = false;
            return;
        }

        for (ServletContainerInitializer sci : detectedScis) {
            initializerClassMap.put(sci, new HashSet<>());

            HandlesTypes ht;
            try {
                ht = sci.getClass().getAnnotation(HandlesTypes.class);
            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.info(sm.getString("contextConfig.sci.debug", sci.getClass().getName()), e);
                } else {
                    log.info(sm.getString("contextConfig.sci.info", sci.getClass().getName()));
                }
                continue;
            }
            if (ht == null) {
                continue;
            }
            Class<?>[] types = ht.value();
            if (types == null) {
                continue;
            }

            for (Class<?> type : types) {
                if (type.isAnnotation()) {
                    handlesTypesAnnotations = true;
                } else {
                    handlesTypesNonAnnotations = true;
                }
                typeInitializerMap.computeIfAbsent(type, k -> new HashSet<>()).add(sci);
            }
        }
    }


    /**
     * Scan JARs that contain web-fragment.xml files that will be used to configure this application to see if they also
     * contain static resources. If static resources are found, add them to the context. Resources are added in
     * web-fragment.xml priority order.
     *
     * @param fragments The set of fragments that will be scanned for static resources
     */
    protected void processResourceJARs(Set<WebXml> fragments) {
        for (WebXml fragment : fragments) {
            URL url = fragment.getURL();
            try {
                if ("jar".equals(url.getProtocol()) || url.toString().endsWith(".jar")) {
                    try (Jar jar = JarFactory.newInstance(url)) {
                        jar.nextEntry();
                        String entryName = jar.getEntryName();
                        while (entryName != null) {
                            if (entryName.startsWith("META-INF/resources/")) {
                                context.getResources().createWebResourceSet(
                                        WebResourceRoot.ResourceSetType.RESOURCE_JAR, "/", url, "/META-INF/resources");
                                break;
                            }
                            jar.nextEntry();
                            entryName = jar.getEntryName();
                        }
                    }
                } else if ("file".equals(url.getProtocol())) {
                    File file = new File(url.toURI());
                    File resources = new File(file, "META-INF/resources/");
                    if (resources.isDirectory()) {
                        context.getResources().createWebResourceSet(WebResourceRoot.ResourceSetType.RESOURCE_JAR, "/",
                                resources.getAbsolutePath(), null, "/");
                    }
                }
            } catch (IOException | URISyntaxException e) {
                log.error(sm.getString("contextConfig.resourceJarFail", url, context.getName()));
            }
        }
    }


    /**
     * Identify the default web.xml to be used and obtain an input source for it.
     *
     * @return an input source to the default web.xml
     */
    protected InputSource getGlobalWebXmlSource() {
        // Is a default web.xml specified for the Context?
        if (defaultWebXml == null && context instanceof StandardContext) {
            defaultWebXml = ((StandardContext) context).getDefaultWebXml();
        }
        // Set the default if we don't have any overrides
        if (defaultWebXml == null) {
            getDefaultWebXml();
        }

        // Is it explicitly suppressed, e.g. in embedded environment?
        if (Constants.NoDefaultWebXml.equals(defaultWebXml)) {
            return null;
        }
        return getWebXmlSource(defaultWebXml, true);
    }


    /**
     * Identify the host web.xml to be used and obtain an input source for it.
     *
     * @return an input source to the default per host web.xml
     */
    protected InputSource getHostWebXmlSource() {
        File hostConfigBase = getHostConfigBase();
        if (hostConfigBase == null) {
            return null;
        }

        return getWebXmlSource(hostConfigBase.getPath(), false);
    }

    /**
     * Identify the application web.xml to be used and obtain an input source for it.
     *
     * @return an input source to the context web.xml
     */
    protected InputSource getContextWebXmlSource() {
        InputStream stream = null;
        InputSource source = null;
        URL url = null;

        String altDDName = null;

        // Open the application web.xml file, if it exists
        ServletContext servletContext = context.getServletContext();
        try {
            if (servletContext != null) {
                altDDName = (String) servletContext.getAttribute(Globals.ALT_DD_ATTR);
                if (altDDName != null) {
                    try {
                        stream = new FileInputStream(altDDName);
                        url = new File(altDDName).toURI().toURL();
                    } catch (FileNotFoundException e) {
                        log.error(sm.getString("contextConfig.altDDNotFound", altDDName));
                    } catch (MalformedURLException e) {
                        log.error(sm.getString("contextConfig.applicationUrl"));
                    }
                } else {
                    stream = servletContext.getResourceAsStream(Constants.ApplicationWebXml);
                    try {
                        url = servletContext.getResource(Constants.ApplicationWebXml);
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
        } finally {
            if (source == null && stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }

        return source;
    }

    public String getConfigBasePath() {
        String path = null;
        if (context.getParent() instanceof Host) {
            Host host = (Host) context.getParent();
            if (host.getXmlBase() != null) {
                path = host.getXmlBase();
            } else {
                StringBuilder xmlDir = new StringBuilder("conf");
                Container parent = host.getParent();
                if (parent instanceof Engine) {
                    xmlDir.append('/');
                    xmlDir.append(parent.getName());
                }
                xmlDir.append('/');
                xmlDir.append(host.getName());
                path = xmlDir.toString();
            }
        }
        return path;
    }

    /**
     * Utility method to create an input source from the specified XML file.
     *
     * @param filename Name of the file (possibly with one or more leading path segments) to read
     * @param global   true if processing a shared resource, false if processing a host based resource
     *
     * @return the input source
     */
    protected InputSource getWebXmlSource(String filename, boolean global) {
        ConfigurationSource.Resource webXmlResource = null;
        try {
            if (global) {
                if (Constants.DefaultWebXml.equals(filename)) {
                    webXmlResource = ConfigFileLoader.getSource().getSharedWebXml();
                } else {
                    webXmlResource = ConfigFileLoader.getSource().getResource(filename);
                }
            } else {
                String hostWebXml = Container.getConfigPath(context, Constants.HostWebXml);
                webXmlResource = ConfigFileLoader.getSource().getResource(hostWebXml);
            }
        } catch (IOException e) {
            // Ignore if not found
            return null;
        }

        InputStream stream = null;
        InputSource source = null;

        try {
            stream = webXmlResource.getInputStream();
            source = new InputSource(webXmlResource.getURI().toString());
            if (stream != null) {
                source.setByteStream(stream);
            }
        } catch (Exception e) {
            log.error(sm.getString("contextConfig.defaultError", filename, webXmlResource.getURI()), e);
        } finally {
            if (source == null && stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }

        return source;
    }


    /**
     * Scan /WEB-INF/lib for JARs and for each one found add it and any /META-INF/web-fragment.xml to the resulting Map.
     * web-fragment.xml files will be parsed before being added to the map. Every JAR will be added and
     * <code>null</code> will be used if no web-fragment.xml was found. Any JARs known not contain fragments will be
     * skipped.
     *
     * @param application  The main web.xml metadata
     * @param webXmlParser The parser to use to process the web.xml file
     *
     * @return A map of JAR name to processed web fragment (if any)
     */
    protected Map<String,WebXml> processJarsForWebFragments(WebXml application, WebXmlParser webXmlParser) {

        JarScanner jarScanner = context.getJarScanner();
        boolean delegate = false;
        if (context instanceof StandardContext) {
            delegate = ((StandardContext) context).getDelegate();
        }
        boolean parseRequired = true;
        Set<String> absoluteOrder = application.getAbsoluteOrdering();
        if (absoluteOrder != null && absoluteOrder.isEmpty() && !context.getXmlValidation()) {
            // Skip parsing when there is an empty absolute ordering and
            // validation is not enabled
            parseRequired = false;
        }

        FragmentJarScannerCallback callback = new FragmentJarScannerCallback(webXmlParser, delegate, parseRequired);

        jarScanner.scan(JarScanType.PLUGGABILITY, context.getServletContext(), callback);

        if (!callback.isOk()) {
            ok = false;
        }
        return callback.getFragments();
    }

    protected void processAnnotations(Set<WebXml> fragments, boolean handlesTypesOnly,
            Map<String,JavaClassCacheEntry> javaClassCache) {

        if (context.getParallelAnnotationScanning()) {
            processAnnotationsInParallel(fragments, handlesTypesOnly, javaClassCache);
        } else {
            for (WebXml fragment : fragments) {
                scanWebXmlFragment(handlesTypesOnly, fragment, javaClassCache);
            }
        }
    }

    private void scanWebXmlFragment(boolean handlesTypesOnly, WebXml fragment,
            Map<String,JavaClassCacheEntry> javaClassCache) {

        // Only need to scan for @HandlesTypes matches if any of the
        // following are true:
        // - it has already been determined only @HandlesTypes is required
        // (e.g. main web.xml has metadata-complete="true"
        // - this fragment is for a container JAR (Servlet 3.1 section 8.1)
        // - this fragment has metadata-complete="true"
        boolean htOnly = handlesTypesOnly || !fragment.getWebappJar() || fragment.isMetadataComplete();

        WebXml annotations = new WebXml();
        // no impact on distributable
        annotations.setDistributable(true);
        URL url = fragment.getURL();
        processAnnotationsUrl(url, annotations, htOnly, javaClassCache);
        Set<WebXml> set = new HashSet<>();
        set.add(annotations);
        // Merge annotations into fragment - fragment takes priority
        fragment.merge(set);
    }

    /**
     * Executable task to scan a segment for annotations. Each task does the same work as the for loop inside
     * processAnnotations();
     */
    private class AnnotationScanTask implements Runnable {
        private final WebXml fragment;
        private final boolean handlesTypesOnly;
        private Map<String,JavaClassCacheEntry> javaClassCache;

        private AnnotationScanTask(WebXml fragment, boolean handlesTypesOnly,
                Map<String,JavaClassCacheEntry> javaClassCache) {
            this.fragment = fragment;
            this.handlesTypesOnly = handlesTypesOnly;
            this.javaClassCache = javaClassCache;
        }

        @Override
        public void run() {
            scanWebXmlFragment(handlesTypesOnly, fragment, javaClassCache);
        }

    }

    /**
     * Parallelized version of processAnnotationsInParallel(). Constructs tasks, submits them as they're created, then
     * waits for completion.
     *
     * @param fragments        Set of parallelizable scans
     * @param handlesTypesOnly Important parameter for the underlying scan
     * @param javaClassCache   The class cache
     */
    protected void processAnnotationsInParallel(Set<WebXml> fragments, boolean handlesTypesOnly,
            Map<String,JavaClassCacheEntry> javaClassCache) {
        Server s = getServer();
        ExecutorService pool = (s == null) ? null : s.getUtilityExecutor();
        if (pool != null) {
            List<Future<?>> futures = new ArrayList<>(fragments.size());
            for (WebXml fragment : fragments) {
                Runnable task = new AnnotationScanTask(fragment, handlesTypesOnly, javaClassCache);
                futures.add(pool.submit(task));
            }
            try {
                for (Future<?> future : futures) {
                    future.get();
                }
            } catch (Exception e) {
                throw new RuntimeException(sm.getString("contextConfig.processAnnotationsInParallelFailure"), e);
            }
        } else {
            // Fallback to regular processing
            for (WebXml fragment : fragments) {
                scanWebXmlFragment(handlesTypesOnly, fragment, javaClassCache);
            }
        }
    }

    protected void processAnnotationsWebResource(WebResource webResource, WebXml fragment, boolean handlesTypesOnly,
            Map<String,JavaClassCacheEntry> javaClassCache) {

        if (webResource.isDirectory()) {
            WebResource[] webResources = webResource.getWebResourceRoot().listResources(webResource.getWebappPath());
            if (webResources.length > 0) {
                if (log.isTraceEnabled()) {
                    log.trace(sm.getString("contextConfig.processAnnotationsWebDir.debug", webResource.getURL()));
                }
                for (WebResource r : webResources) {
                    processAnnotationsWebResource(r, fragment, handlesTypesOnly, javaClassCache);
                }
            }
        } else if (webResource.isFile() && webResource.getName().endsWith(".class")) {
            try (InputStream is = webResource.getInputStream()) {
                processAnnotationsStream(is, fragment, handlesTypesOnly, javaClassCache);
            } catch (IOException | ClassFormatException e) {
                log.error(sm.getString("contextConfig.inputStreamWebResource", webResource.getWebappPath()), e);
            }
        }
    }


    protected void processAnnotationsUrl(URL url, WebXml fragment, boolean handlesTypesOnly,
            Map<String,JavaClassCacheEntry> javaClassCache) {
        if (url == null) {
            // Nothing to do.
            return;
        } else if ("jar".equals(url.getProtocol()) || url.toString().endsWith(".jar")) {
            processAnnotationsJar(url, fragment, handlesTypesOnly, javaClassCache);
        } else if ("file".equals(url.getProtocol())) {
            try {
                processAnnotationsFile(new File(url.toURI()), fragment, handlesTypesOnly, javaClassCache);
            } catch (URISyntaxException e) {
                log.error(sm.getString("contextConfig.fileUrl", url), e);
            }
        } else {
            log.error(sm.getString("contextConfig.unknownUrlProtocol", url.getProtocol(), url));
        }

    }


    protected void processAnnotationsJar(URL url, WebXml fragment, boolean handlesTypesOnly,
            Map<String,JavaClassCacheEntry> javaClassCache) {

        try (Jar jar = JarFactory.newInstance(url)) {
            if (log.isTraceEnabled()) {
                log.trace(sm.getString("contextConfig.processAnnotationsJar.debug", url));
            }

            jar.nextEntry();
            String entryName = jar.getEntryName();
            while (entryName != null) {
                if (entryName.endsWith(".class")) {
                    try (InputStream is = jar.getEntryInputStream()) {
                        processAnnotationsStream(is, fragment, handlesTypesOnly, javaClassCache);
                    } catch (IOException | ClassFormatException e) {
                        log.error(sm.getString("contextConfig.inputStreamJar", entryName, url), e);
                    }
                }
                jar.nextEntry();
                entryName = jar.getEntryName();
            }
        } catch (IOException e) {
            log.error(sm.getString("contextConfig.jarFile", url), e);
        }
    }


    protected void processAnnotationsFile(File file, WebXml fragment, boolean handlesTypesOnly,
            Map<String,JavaClassCacheEntry> javaClassCache) {

        if (file.isDirectory()) {
            // Returns null if directory is not readable
            String[] dirs = file.list();
            if (dirs != null) {
                if (log.isTraceEnabled()) {
                    log.trace(sm.getString("contextConfig.processAnnotationsDir.debug", file));
                }
                for (String dir : dirs) {
                    processAnnotationsFile(new File(file, dir), fragment, handlesTypesOnly, javaClassCache);
                }
            }
        } else if (file.getName().endsWith(".class") && file.canRead()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                processAnnotationsStream(fis, fragment, handlesTypesOnly, javaClassCache);
            } catch (IOException | ClassFormatException e) {
                log.error(sm.getString("contextConfig.inputStreamFile", file.getAbsolutePath()), e);
            }
        }
    }


    protected void processAnnotationsStream(InputStream is, WebXml fragment, boolean handlesTypesOnly,
            Map<String,JavaClassCacheEntry> javaClassCache) throws ClassFormatException, IOException {

        ClassParser parser = new ClassParser(is);
        JavaClass clazz = parser.parse();
        checkHandlesTypes(clazz, javaClassCache);

        if (handlesTypesOnly) {
            return;
        }

        processClass(fragment, clazz);
    }


    protected void processClass(WebXml fragment, JavaClass clazz) {
        AnnotationEntry[] annotationsEntries = clazz.getAnnotationEntries();
        if (annotationsEntries != null) {
            String className = clazz.getClassName();
            for (AnnotationEntry ae : annotationsEntries) {
                String type = ae.getAnnotationType();
                if ("Ljakarta/servlet/annotation/WebServlet;".equals(type)) {
                    processAnnotationWebServlet(className, ae, fragment);
                } else if ("Ljakarta/servlet/annotation/WebFilter;".equals(type)) {
                    processAnnotationWebFilter(className, ae, fragment);
                } else if ("Ljakarta/servlet/annotation/WebListener;".equals(type)) {
                    fragment.addListener(className);
                } else {
                    // Unknown annotation - ignore
                }
            }
        }
    }


    /**
     * For classes packaged with the web application, the class and each super class needs to be checked for a match
     * with {@link HandlesTypes} or for an annotation that matches {@link HandlesTypes}.
     *
     * @param javaClass      the class to check
     * @param javaClassCache a class cache
     */
    protected void checkHandlesTypes(JavaClass javaClass, Map<String,JavaClassCacheEntry> javaClassCache) {

        // Skip this if we can
        if (typeInitializerMap.size() == 0) {
            return;
        }

        if ((javaClass.getAccessFlags() & org.apache.tomcat.util.bcel.Const.ACC_ANNOTATION) != 0) {
            // Skip annotations.
            return;
        }

        String className = javaClass.getClassName();

        Class<?> clazz = null;
        if (handlesTypesNonAnnotations) {
            // This *might* be match for a HandlesType.
            populateJavaClassCache(className, javaClass, javaClassCache);
            JavaClassCacheEntry entry = javaClassCache.get(className);
            if (entry.getSciSet() == null) {
                try {
                    populateSCIsForCacheEntry(entry, javaClassCache);
                } catch (StackOverflowError soe) {
                    throw new IllegalStateException(sm.getString("contextConfig.annotationsStackOverflow",
                            context.getName(), classHierarchyToString(className, entry, javaClassCache)));
                }
            }
            if (!entry.getSciSet().isEmpty()) {
                // Need to try and load the class
                clazz = Introspection.loadClass(context, className);
                if (clazz == null) {
                    // Can't load the class so no point continuing
                    return;
                }

                for (ServletContainerInitializer sci : entry.getSciSet()) {
                    Set<Class<?>> classes = initializerClassMap.computeIfAbsent(sci, k -> new HashSet<>());
                    classes.add(clazz);
                }
            }
        }

        if (handlesTypesAnnotations) {
            AnnotationEntry[] annotationEntries = javaClass.getAllAnnotationEntries();
            if (annotationEntries != null) {
                for (Map.Entry<Class<?>,Set<ServletContainerInitializer>> entry : typeInitializerMap.entrySet()) {
                    if (entry.getKey().isAnnotation()) {
                        String entryClassName = entry.getKey().getName();
                        for (AnnotationEntry annotationEntry : annotationEntries) {
                            if (entryClassName.equals(getClassName(annotationEntry.getAnnotationType()))) {
                                if (clazz == null) {
                                    clazz = Introspection.loadClass(context, className);
                                    if (clazz == null) {
                                        // Can't load the class so no point
                                        // continuing
                                        return;
                                    }
                                }
                                for (ServletContainerInitializer sci : entry.getValue()) {
                                    initializerClassMap.get(sci).add(clazz);
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }
    }


    private String classHierarchyToString(String className, JavaClassCacheEntry entry,
            Map<String,JavaClassCacheEntry> javaClassCache) {
        JavaClassCacheEntry start = entry;
        StringBuilder msg = new StringBuilder(className);
        msg.append("->");

        String parentName = entry.getSuperclassName();
        JavaClassCacheEntry parent = javaClassCache.get(parentName);
        int count = 0;

        while (count < 100 && parent != null && parent != start) {
            msg.append(parentName);
            msg.append("->");

            count++;
            parentName = parent.getSuperclassName();
            parent = javaClassCache.get(parentName);
        }

        msg.append(parentName);

        return msg.toString();
    }

    private void populateJavaClassCache(String className, JavaClass javaClass,
            Map<String,JavaClassCacheEntry> javaClassCache) {
        if (javaClassCache.containsKey(className)) {
            return;
        }

        // Add this class to the cache
        javaClassCache.put(className, new JavaClassCacheEntry(javaClass));

        populateJavaClassCache(javaClass.getSuperclassName(), javaClassCache);

        for (String interfaceName : javaClass.getInterfaceNames()) {
            populateJavaClassCache(interfaceName, javaClassCache);
        }
    }

    private void populateJavaClassCache(String className, Map<String,JavaClassCacheEntry> javaClassCache) {
        if (!javaClassCache.containsKey(className)) {
            String name = className.replace('.', '/') + ".class";
            try (InputStream is = context.getLoader().getClassLoader().getResourceAsStream(name)) {
                if (is == null) {
                    return;
                }
                ClassParser parser = new ClassParser(is);
                JavaClass clazz = parser.parse();
                populateJavaClassCache(clazz.getClassName(), clazz, javaClassCache);
            } catch (ClassFormatException | IOException e) {
                log.debug(sm.getString("contextConfig.invalidSciHandlesTypes", className), e);
            }
        }
    }

    private void populateSCIsForCacheEntry(JavaClassCacheEntry cacheEntry,
            Map<String,JavaClassCacheEntry> javaClassCache) {
        Set<ServletContainerInitializer> result = new HashSet<>();

        // Super class
        String superClassName = cacheEntry.getSuperclassName();
        JavaClassCacheEntry superClassCacheEntry = javaClassCache.get(superClassName);

        // Avoid an infinite loop with java.lang.Object
        if (cacheEntry.equals(superClassCacheEntry)) {
            cacheEntry.setSciSet(EMPTY_SCI_SET);
            return;
        }

        // May be null of the class is not present or could not be loaded.
        if (superClassCacheEntry != null) {
            if (superClassCacheEntry.getSciSet() == null) {
                populateSCIsForCacheEntry(superClassCacheEntry, javaClassCache);
            }
            result.addAll(superClassCacheEntry.getSciSet());
        }
        result.addAll(getSCIsForClass(superClassName));

        // Interfaces
        for (String interfaceName : cacheEntry.getInterfaceNames()) {
            JavaClassCacheEntry interfaceEntry = javaClassCache.get(interfaceName);
            // A null could mean that the class not present in application or
            // that there is nothing of interest. Either way, nothing to do here
            // so move along
            if (interfaceEntry != null) {
                if (interfaceEntry.getSciSet() == null) {
                    populateSCIsForCacheEntry(interfaceEntry, javaClassCache);
                }
                result.addAll(interfaceEntry.getSciSet());
            }
            result.addAll(getSCIsForClass(interfaceName));
        }

        cacheEntry.setSciSet(result.isEmpty() ? EMPTY_SCI_SET : result);
    }

    private Set<ServletContainerInitializer> getSCIsForClass(String className) {
        for (Map.Entry<Class<?>,Set<ServletContainerInitializer>> entry : typeInitializerMap.entrySet()) {
            Class<?> clazz = entry.getKey();
            if (!clazz.isAnnotation()) {
                if (clazz.getName().equals(className)) {
                    return entry.getValue();
                }
            }
        }
        return EMPTY_SCI_SET;
    }

    private static String getClassName(String internalForm) {
        if (!internalForm.startsWith("L")) {
            return internalForm;
        }

        // Assume starts with L, ends with ; and uses / rather than .
        return internalForm.substring(1, internalForm.length() - 1).replace('/', '.');
    }

    protected void processAnnotationWebServlet(String className, AnnotationEntry ae, WebXml fragment) {
        String servletName = null;
        // must search for name s. Spec Servlet API 3.0 - 8.2.3.3.n.ii page 81
        List<ElementValuePair> evps = ae.getElementValuePairs();
        for (ElementValuePair evp : evps) {
            String name = evp.getNameString();
            if ("name".equals(name)) {
                servletName = evp.getValue().stringifyValue();
                break;
            }
        }
        if (servletName == null) {
            // classname is default servletName as annotation has no name!
            servletName = className;
        }
        ServletDef servletDef = fragment.getServlets().get(servletName);

        boolean isWebXMLservletDef;
        if (servletDef == null) {
            servletDef = new ServletDef();
            servletDef.setServletName(servletName);
            servletDef.setServletClass(className);
            isWebXMLservletDef = false;
        } else {
            isWebXMLservletDef = true;
        }

        boolean urlPatternsSet = false;
        String[] urlPatterns = null;

        // List<ElementValuePair> evps = ae.getElementValuePairs();
        for (ElementValuePair evp : evps) {
            String name = evp.getNameString();
            if ("value".equals(name) || "urlPatterns".equals(name)) {
                if (urlPatternsSet) {
                    throw new IllegalArgumentException(
                            sm.getString("contextConfig.urlPatternValue", "WebServlet", className));
                }
                urlPatternsSet = true;
                urlPatterns = processAnnotationsStringArray(evp.getValue());
            } else if ("description".equals(name)) {
                if (servletDef.getDescription() == null) {
                    servletDef.setDescription(evp.getValue().stringifyValue());
                }
            } else if ("displayName".equals(name)) {
                if (servletDef.getDisplayName() == null) {
                    servletDef.setDisplayName(evp.getValue().stringifyValue());
                }
            } else if ("largeIcon".equals(name)) {
                if (servletDef.getLargeIcon() == null) {
                    servletDef.setLargeIcon(evp.getValue().stringifyValue());
                }
            } else if ("smallIcon".equals(name)) {
                if (servletDef.getSmallIcon() == null) {
                    servletDef.setSmallIcon(evp.getValue().stringifyValue());
                }
            } else if ("asyncSupported".equals(name)) {
                if (servletDef.getAsyncSupported() == null) {
                    servletDef.setAsyncSupported(evp.getValue().stringifyValue());
                }
            } else if ("loadOnStartup".equals(name)) {
                if (servletDef.getLoadOnStartup() == null) {
                    servletDef.setLoadOnStartup(evp.getValue().stringifyValue());
                }
            } else if ("initParams".equals(name)) {
                Map<String,String> initParams = processAnnotationWebInitParams(evp.getValue());
                if (isWebXMLservletDef) {
                    Map<String,String> webXMLInitParams = servletDef.getParameterMap();
                    for (Map.Entry<String,String> entry : initParams.entrySet()) {
                        if (webXMLInitParams.get(entry.getKey()) == null) {
                            servletDef.addInitParameter(entry.getKey(), entry.getValue());
                        }
                    }
                } else {
                    for (Map.Entry<String,String> entry : initParams.entrySet()) {
                        servletDef.addInitParameter(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
        if (!isWebXMLservletDef && urlPatterns != null) {
            fragment.addServlet(servletDef);
        }
        if (urlPatterns != null) {
            if (!fragment.getServletMappings().containsValue(servletName)) {
                for (String urlPattern : urlPatterns) {
                    fragment.addServletMapping(urlPattern, servletName);
                }
            }
        }

    }

    /**
     * Process filter annotation and merge with existing one
     *
     * @param className The filter class name
     * @param ae        The filter annotation
     * @param fragment  The corresponding fragment
     */
    protected void processAnnotationWebFilter(String className, AnnotationEntry ae, WebXml fragment) {
        String filterName = null;
        // must search for name s. Spec Servlet API 3.0 - 8.2.3.3.n.ii page 81
        List<ElementValuePair> evps = ae.getElementValuePairs();
        for (ElementValuePair evp : evps) {
            String name = evp.getNameString();
            if ("filterName".equals(name)) {
                filterName = evp.getValue().stringifyValue();
                break;
            }
        }
        if (filterName == null) {
            // classname is default filterName as annotation has no name!
            filterName = className;
        }
        FilterDef filterDef = fragment.getFilters().get(filterName);
        FilterMap filterMap = new FilterMap();

        boolean isWebXMLfilterDef;
        if (filterDef == null) {
            filterDef = new FilterDef();
            filterDef.setFilterName(filterName);
            filterDef.setFilterClass(className);
            isWebXMLfilterDef = false;
        } else {
            isWebXMLfilterDef = true;
        }

        boolean urlPatternsSet = false;
        boolean servletNamesSet = false;
        boolean dispatchTypesSet = false;
        String[] urlPatterns = null;

        for (ElementValuePair evp : evps) {
            String name = evp.getNameString();
            if ("value".equals(name) || "urlPatterns".equals(name)) {
                if (urlPatternsSet) {
                    throw new IllegalArgumentException(
                            sm.getString("contextConfig.urlPatternValue", "WebFilter", className));
                }
                urlPatterns = processAnnotationsStringArray(evp.getValue());
                urlPatternsSet = urlPatterns.length > 0;
                for (String urlPattern : urlPatterns) {
                    // % decoded (if required) using UTF-8
                    filterMap.addURLPattern(urlPattern);
                }
            } else if ("servletNames".equals(name)) {
                String[] servletNames = processAnnotationsStringArray(evp.getValue());
                servletNamesSet = servletNames.length > 0;
                for (String servletName : servletNames) {
                    filterMap.addServletName(servletName);
                }
            } else if ("dispatcherTypes".equals(name)) {
                String[] dispatcherTypes = processAnnotationsStringArray(evp.getValue());
                dispatchTypesSet = dispatcherTypes.length > 0;
                for (String dispatcherType : dispatcherTypes) {
                    filterMap.setDispatcher(dispatcherType);
                }
            } else if ("description".equals(name)) {
                if (filterDef.getDescription() == null) {
                    filterDef.setDescription(evp.getValue().stringifyValue());
                }
            } else if ("displayName".equals(name)) {
                if (filterDef.getDisplayName() == null) {
                    filterDef.setDisplayName(evp.getValue().stringifyValue());
                }
            } else if ("largeIcon".equals(name)) {
                if (filterDef.getLargeIcon() == null) {
                    filterDef.setLargeIcon(evp.getValue().stringifyValue());
                }
            } else if ("smallIcon".equals(name)) {
                if (filterDef.getSmallIcon() == null) {
                    filterDef.setSmallIcon(evp.getValue().stringifyValue());
                }
            } else if ("asyncSupported".equals(name)) {
                if (filterDef.getAsyncSupported() == null) {
                    filterDef.setAsyncSupported(evp.getValue().stringifyValue());
                }
            } else if ("initParams".equals(name)) {
                Map<String,String> initParams = processAnnotationWebInitParams(evp.getValue());
                if (isWebXMLfilterDef) {
                    Map<String,String> webXMLInitParams = filterDef.getParameterMap();
                    for (Map.Entry<String,String> entry : initParams.entrySet()) {
                        if (webXMLInitParams.get(entry.getKey()) == null) {
                            filterDef.addInitParameter(entry.getKey(), entry.getValue());
                        }
                    }
                } else {
                    for (Map.Entry<String,String> entry : initParams.entrySet()) {
                        filterDef.addInitParameter(entry.getKey(), entry.getValue());
                    }
                }

            }
        }
        if (!isWebXMLfilterDef) {
            fragment.addFilter(filterDef);
            if (urlPatternsSet || servletNamesSet) {
                filterMap.setFilterName(filterName);
                fragment.addFilterMapping(filterMap);
            }
        }
        if (urlPatternsSet || dispatchTypesSet) {
            Set<FilterMap> fmap = fragment.getFilterMappings();
            FilterMap descMap = null;
            for (FilterMap map : fmap) {
                if (filterName.equals(map.getFilterName())) {
                    descMap = map;
                    break;
                }
            }
            if (descMap != null) {
                String[] urlsPatterns = descMap.getURLPatterns();
                if (urlPatternsSet && (urlsPatterns == null || urlsPatterns.length == 0)) {
                    for (String urlPattern : filterMap.getURLPatterns()) {
                        // % decoded (if required) using UTF-8
                        descMap.addURLPattern(urlPattern);
                    }
                }
                String[] dispatcherNames = descMap.getDispatcherNames();
                if (dispatchTypesSet && (dispatcherNames == null || dispatcherNames.length == 0)) {
                    for (String dis : filterMap.getDispatcherNames()) {
                        descMap.setDispatcher(dis);
                    }
                }
            }
        }

    }

    protected String[] processAnnotationsStringArray(ElementValue ev) {
        List<String> values = new ArrayList<>();
        if (ev instanceof ArrayElementValue) {
            ElementValue[] arrayValues = ((ArrayElementValue) ev).getElementValuesArray();
            for (ElementValue value : arrayValues) {
                values.add(value.stringifyValue());
            }
        } else {
            values.add(ev.stringifyValue());
        }
        return values.toArray(new String[0]);
    }

    protected Map<String,String> processAnnotationWebInitParams(ElementValue ev) {
        Map<String,String> result = new HashMap<>();
        if (ev instanceof ArrayElementValue) {
            ElementValue[] arrayValues = ((ArrayElementValue) ev).getElementValuesArray();
            for (ElementValue value : arrayValues) {
                if (value instanceof AnnotationElementValue) {
                    List<ElementValuePair> evps =
                            ((AnnotationElementValue) value).getAnnotationEntry().getElementValuePairs();
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

    private static class DefaultWebXmlCacheEntry {
        private final WebXml webXml;
        private final long globalTimeStamp;
        private final long hostTimeStamp;

        DefaultWebXmlCacheEntry(WebXml webXml, long globalTimeStamp, long hostTimeStamp) {
            this.webXml = webXml;
            this.globalTimeStamp = globalTimeStamp;
            this.hostTimeStamp = hostTimeStamp;
        }

        public WebXml getWebXml() {
            return webXml;
        }

        public long getGlobalTimeStamp() {
            return globalTimeStamp;
        }

        public long getHostTimeStamp() {
            return hostTimeStamp;
        }
    }

    private static class HostWebXmlCacheCleaner implements LifecycleListener {

        @Override
        public void lifecycleEvent(LifecycleEvent event) {

            if (Lifecycle.AFTER_DESTROY_EVENT.equals(event.getType())) {
                Host host = (Host) event.getSource();
                hostWebXmlCache.remove(host);
            }
        }
    }

    static class JavaClassCacheEntry {
        public final String superclassName;

        public final String[] interfaceNames;

        private Set<ServletContainerInitializer> sciSet = null;

        JavaClassCacheEntry(JavaClass javaClass) {
            superclassName = javaClass.getSuperclassName();
            interfaceNames = javaClass.getInterfaceNames();
        }

        public String getSuperclassName() {
            return superclassName;
        }

        public String[] getInterfaceNames() {
            return interfaceNames;
        }

        public Set<ServletContainerInitializer> getSciSet() {
            return sciSet;
        }

        public void setSciSet(Set<ServletContainerInitializer> sciSet) {
            this.sciSet = sciSet;
        }
    }
}

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
package org.apache.catalina.loader;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.catalina.Container;
import org.apache.catalina.Globals;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.webresources.TomcatURLStreamHandlerFactory;
import org.apache.juli.WebappProperties;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.InstrumentableClassLoader;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * Specialized web application class loader.
 * <p>
 * This class loader is a full reimplementation of the
 * <code>URLClassLoader</code> from the JDK. It is designed to be fully
 * compatible with a normal <code>URLClassLoader</code>, although its internal
 * behavior may be completely different.
 * <p>
 * <strong>IMPLEMENTATION NOTE</strong> - By default, this class loader follows
 * the delegation model required by the specification. The system class
 * loader will be queried first, then the local repositories, and only then
 * delegation to the parent class loader will occur. This allows the web
 * application to override any shared class except the classes from J2SE.
 * Special handling is provided from the JAXP XML parser interfaces, the JNDI
 * interfaces, and the classes from the servlet API, which are never loaded
 * from the webapp repositories. The <code>delegate</code> property
 * allows an application to modify this behavior to move the parent class loader
 * ahead of the local repositories.
 * <p>
 * <strong>IMPLEMENTATION NOTE</strong> - Due to limitations in Jasper
 * compilation technology, any repository which contains classes from
 * the servlet API will be ignored by the class loader.
 * <p>
 * <strong>IMPLEMENTATION NOTE</strong> - The class loader generates source
 * URLs which include the full JAR URL when a class is loaded from a JAR file,
 * which allows setting security permission at the class level, even when a
 * class is contained inside a JAR.
 * <p>
 * <strong>IMPLEMENTATION NOTE</strong> - Local repositories are searched in
 * the order they are added via the initial constructor and/or any subsequent
 * calls to <code>addRepository()</code> or <code>addJar()</code>.
 * <p>
 * <strong>IMPLEMENTATION NOTE</strong> - No check for sealing violations or
 * security is made unless a security manager is present.
 * <p>
 * <strong>IMPLEMENTATION NOTE</strong> - As of 8.0, this class
 * loader implements {@link InstrumentableClassLoader}, permitting web
 * application classes to instrument other classes in the same web
 * application. It does not permit instrumentation of system or container
 * classes or classes in other web apps.
 *
 * @author Remy Maucherat
 * @author Craig R. McClanahan
 */
public abstract class WebappClassLoaderBase extends URLClassLoader
        implements Lifecycle, InstrumentableClassLoader, WebappProperties {

    private static final Log log = LogFactory.getLog(WebappClassLoaderBase.class);

    /**
     * List of ThreadGroup names to ignore when scanning for web application
     * started threads that need to be shut down.
     */
    private static final List<String> JVM_THREAD_GROUP_NAMES = new ArrayList<>();

    private static final String JVM_THREAD_GROUP_SYSTEM = "system";

    private static final String CLASS_FILE_SUFFIX = ".class";
    private static final String SERVICES_PREFIX = "/META-INF/services/";

    static {
        ClassLoader.registerAsParallelCapable();
        JVM_THREAD_GROUP_NAMES.add(JVM_THREAD_GROUP_SYSTEM);
        JVM_THREAD_GROUP_NAMES.add("RMI Runtime");
    }

    protected class PrivilegedFindResourceByName
        implements PrivilegedAction<ResourceEntry> {

        protected final String name;
        protected final String path;

        PrivilegedFindResourceByName(String name, String path) {
            this.name = name;
            this.path = path;
        }

        @Override
        public ResourceEntry run() {
            return findResourceInternal(name, path);
        }

    }


    protected static final class PrivilegedGetClassLoader
        implements PrivilegedAction<ClassLoader> {

        public final Class<?> clazz;

        public PrivilegedGetClassLoader(Class<?> clazz){
            this.clazz = clazz;
        }

        @Override
        public ClassLoader run() {
            return clazz.getClassLoader();
        }
    }


    // ------------------------------------------------------- Static Variables

    /**
     * Regular expression of package names which are not allowed to be loaded
     * from a webapp class loader without delegating first.
     */
    protected final Matcher packageTriggersDeny = Pattern.compile(
            "^javax(\\.|/)el(\\.|/)|" +
            "^javax(\\.|/)security(\\.|/)auth(\\.|/)message(\\.|/)|" +
            "^javax(\\.|/)servlet(\\.|/)|" +
            "^javax(\\.|/)websocket(\\.|/)|" +
            "^org(\\.|/)apache(\\.|/)(catalina|coyote|el|jasper|juli|naming|tomcat)(\\.|/)"
            ).matcher("");


    /**
     * Regular expression of package names which are allowed to be loaded from a
     * webapp class loader without delegating first and override any set by
     * {@link #packageTriggersDeny}.
     */
    protected final Matcher packageTriggersPermit =
            Pattern.compile("^javax(\\.|/)servlet(\\.|/)jsp(\\.|/)jstl(\\.|/)|" +
                    "^org(\\.|/)apache(\\.|/)tomcat(\\.|/)jdbc(\\.|/)").matcher("");


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


    // ----------------------------------------------------------- Constructors

    /**
     * Construct a new ClassLoader with no defined repositories and no
     * parent ClassLoader.
     */
    protected WebappClassLoaderBase() {

        super(new URL[0]);

        ClassLoader p = getParent();
        if (p == null) {
            p = getSystemClassLoader();
        }
        this.parent = p;

        ClassLoader j = String.class.getClassLoader();
        if (j == null) {
            j = getSystemClassLoader();
            while (j.getParent() != null) {
                j = j.getParent();
            }
        }
        this.javaseClassLoader = j;

        securityManager = System.getSecurityManager();
        if (securityManager != null) {
            refreshPolicy();
        }
    }


    /**
     * Construct a new ClassLoader with no defined repositories and the given
     * parent ClassLoader.
     * <p>
     * Method is used via reflection -
     * see {@link WebappLoader#createClassLoader()}
     *
     * @param parent Our parent class loader
     */
    protected WebappClassLoaderBase(ClassLoader parent) {

        super(new URL[0], parent);

        ClassLoader p = getParent();
        if (p == null) {
            p = getSystemClassLoader();
        }
        this.parent = p;

        ClassLoader j = String.class.getClassLoader();
        if (j == null) {
            j = getSystemClassLoader();
            while (j.getParent() != null) {
                j = j.getParent();
            }
        }
        this.javaseClassLoader = j;

        securityManager = System.getSecurityManager();
        if (securityManager != null) {
            refreshPolicy();
        }
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * Associated web resources for this webapp.
     */
    protected WebResourceRoot resources = null;


    /**
     * The cache of ResourceEntry for classes and resources we have loaded,
     * keyed by resource path, not binary name. Path is used as the key since
     * resources may be requested by binary name (classes) or path (other
     * resources such as property files) and the mapping from binary name to
     * path is unambiguous but the reverse mapping is ambiguous.
     */
    protected final Map<String, ResourceEntry> resourceEntries =
            new ConcurrentHashMap<>();


    /**
     * Should this class loader delegate to the parent class loader
     * <strong>before</strong> searching its own repositories (i.e. the
     * usual Java2 delegation model)?  If set to <code>false</code>,
     * this class loader will search its own repositories first, and
     * delegate to the parent only if the class or resource is not
     * found locally. Note that the default, <code>false</code>, is
     * the behavior called for by the servlet specification.
     */
    protected boolean delegate = false;


    private final HashMap<String,Long> jarModificationTimes = new HashMap<>();


    /**
     * A list of read File and Jndi Permission's required if this loader
     * is for a web application context.
     */
    protected final ArrayList<Permission> permissionList = new ArrayList<>();


    /**
     * The PermissionCollection for each CodeSource for a web
     * application context.
     */
    protected final HashMap<String, PermissionCollection> loaderPC = new HashMap<>();


    /**
     * Instance of the SecurityManager installed.
     */
    protected final SecurityManager securityManager;


    /**
     * The parent class loader.
     */
    protected final ClassLoader parent;


    /**
     * The bootstrap class loader used to load the JavaSE classes. In some
     * implementations this class loader is always <code>null</null> and in
     * those cases {@link ClassLoader#getParent()} will be called recursively on
     * the system class loader and the last non-null result used.
     */
    private ClassLoader javaseClassLoader;


    /**
     * need conversion for properties files
     */
    protected boolean needConvert = false;


    /**
     * All permission.
     */
    protected final Permission allPermission = new java.security.AllPermission();


    /**
     * Should Tomcat attempt to null out any static or final fields from loaded
     * classes when a web application is stopped as a work around for apparent
     * garbage collection bugs and application coding errors? There have been
     * some issues reported with log4j when this option is true. Applications
     * without memory leaks using recent JVMs should operate correctly with this
     * option set to <code>false</code>. If not specified, the default value of
     * <code>false</code> will be used.
     */
    private boolean clearReferencesStatic = false;

    /**
     * Should Tomcat attempt to terminate threads that have been started by the
     * web application? Stopping threads is performed via the deprecated (for
     * good reason) <code>Thread.stop()</code> method and is likely to result in
     * instability. As such, enabling this should be viewed as an option of last
     * resort in a development environment and is not recommended in a
     * production environment. If not specified, the default value of
     * <code>false</code> will be used.
     */
    private boolean clearReferencesStopThreads = false;

    /**
     * Should Tomcat attempt to terminate any {@link java.util.TimerThread}s
     * that have been started by the web application? If not specified, the
     * default value of <code>false</code> will be used.
     */
    private boolean clearReferencesStopTimerThreads = false;

    /**
     * Should Tomcat call {@link org.apache.juli.logging.LogFactory#release()}
     * when the class loader is stopped? If not specified, the default value
     * of <code>true</code> is used. Changing the default setting is likely to
     * lead to memory leaks and other issues.
     */
    private boolean clearReferencesLogFactoryRelease = true;

    /**
     * If an HttpClient keep-alive timer thread has been started by this web
     * application and is still running, should Tomcat change the context class
     * loader from the current {@link ClassLoader} to
     * {@link ClassLoader#getParent()} to prevent a memory leak? Note that the
     * keep-alive timer thread will stop on its own once the keep-alives all
     * expire however, on a busy system that might not happen for some time.
     */
    private boolean clearReferencesHttpClientKeepAliveThread = true;

    /**
     * Holds the class file transformers decorating this class loader. The
     * CopyOnWriteArrayList is thread safe. It is expensive on writes, but
     * those should be rare. It is very fast on reads, since synchronization
     * is not actually used. Importantly, the ClassLoader will never block
     * iterating over the transformers while loading a class.
     */
    private final List<ClassFileTransformer> transformers = new CopyOnWriteArrayList<>();


    /**
     * Flag that indicates that {@link #addURL(URL)} has been called which
     * creates a requirement to check the super class when searching for
     * resources.
     */
    private boolean hasExternalRepositories = false;


    /**
     * Repositories managed by this class rather than the super class.
     */
    private List<URL> localRepositories = new ArrayList<>();


    private volatile LifecycleState state = LifecycleState.NEW;


    // ------------------------------------------------------------- Properties

    /**
     * Get associated resources.
     */
    public WebResourceRoot getResources() {
        return this.resources;
    }


    /**
     * Set associated resources.
     */
    public void setResources(WebResourceRoot resources) {
        this.resources = resources;
    }


    /**
     * Return the context name for this class loader.
     */
    public String getContextName() {
        if (resources == null) {
            return "Unknown";
        } else {
            return resources.getContext().getBaseName();
        }
    }


    /**
     * Return the "delegate first" flag for this class loader.
     */
    public boolean getDelegate() {

        return (this.delegate);

    }


    /**
     * Set the "delegate first" flag for this class loader.
     * If this flag is true, this class loader delegates
     * to the parent class loader
     * <strong>before</strong> searching its own repositories, as
     * in an ordinary (non-servlet) chain of Java class loaders.
     * If set to <code>false</code> (the default),
     * this class loader will search its own repositories first, and
     * delegate to the parent only if the class or resource is not
     * found locally, as per the servlet specification.
     *
     * @param delegate The new "delegate first" flag
     */
    public void setDelegate(boolean delegate) {
        this.delegate = delegate;
    }


    /**
     * If there is a Java SecurityManager create a read permission for the
     * target of the given URL as appropriate.
     *
     * @param url URL for a file or directory on local system
     */
    void addPermission(URL url) {
        if (url == null) {
            return;
        }
        if (securityManager != null) {
            String protocol = url.getProtocol();
            if ("file".equalsIgnoreCase(protocol)) {
                URI uri;
                File f;
                String path;
                try {
                    uri = url.toURI();
                    f = new File(uri);
                    path = f.getCanonicalPath();
                } catch (IOException | URISyntaxException e) {
                    log.warn(sm.getString(
                            "webappClassLoader.addPermisionNoCanonicalFile",
                            url.toExternalForm()));
                    return;
                }
                if (f.isFile()) {
                    // Allow the file to be read
                    addPermission(new FilePermission(path, "read"));
                } else if (f.isDirectory()) {
                    addPermission(new FilePermission(path, "read"));
                    addPermission(new FilePermission(
                            path + File.separator + "-", "read"));
                } else {
                    // File does not exist - ignore (shouldn't happen)
                }
            } else {
                // Unsupported URL protocol
                log.warn(sm.getString(
                        "webappClassLoader.addPermisionNoProtocol",
                        protocol, url.toExternalForm()));
            }
        }
    }


    /**
     * If there is a Java SecurityManager create a Permission.
     *
     * @param permission The permission
     */
    void addPermission(Permission permission) {
        if ((securityManager != null) && (permission != null)) {
            permissionList.add(permission);
        }
    }


    /**
     * Return the clearReferencesStatic flag for this Context.
     */
    public boolean getClearReferencesStatic() {
        return (this.clearReferencesStatic);
    }


    /**
     * Set the clearReferencesStatic feature for this Context.
     *
     * @param clearReferencesStatic The new flag value
     */
    public void setClearReferencesStatic(boolean clearReferencesStatic) {
        this.clearReferencesStatic = clearReferencesStatic;
    }


    /**
     * Return the clearReferencesStopThreads flag for this Context.
     */
    public boolean getClearReferencesStopThreads() {
        return (this.clearReferencesStopThreads);
    }


    /**
     * Set the clearReferencesStopThreads feature for this Context.
     *
     * @param clearReferencesStopThreads The new flag value
     */
    public void setClearReferencesStopThreads(
            boolean clearReferencesStopThreads) {
        this.clearReferencesStopThreads = clearReferencesStopThreads;
    }


    /**
     * Return the clearReferencesStopTimerThreads flag for this Context.
     */
    public boolean getClearReferencesStopTimerThreads() {
        return (this.clearReferencesStopTimerThreads);
    }


    /**
     * Set the clearReferencesStopTimerThreads feature for this Context.
     *
     * @param clearReferencesStopTimerThreads The new flag value
     */
    public void setClearReferencesStopTimerThreads(
            boolean clearReferencesStopTimerThreads) {
        this.clearReferencesStopTimerThreads = clearReferencesStopTimerThreads;
    }


    /**
     * Return the clearReferencesLogFactoryRelease flag for this Context.
     */
    public boolean getClearReferencesLogFactoryRelease() {
        return (this.clearReferencesLogFactoryRelease);
    }


    /**
     * Set the clearReferencesLogFactoryRelease feature for this Context.
     *
     * @param clearReferencesLogFactoryRelease The new flag value
     */
    public void setClearReferencesLogFactoryRelease(
            boolean clearReferencesLogFactoryRelease) {
        this.clearReferencesLogFactoryRelease =
            clearReferencesLogFactoryRelease;
    }


    /**
     * Return the clearReferencesHttpClientKeepAliveThread flag for this
     * Context.
     */
    public boolean getClearReferencesHttpClientKeepAliveThread() {
        return (this.clearReferencesHttpClientKeepAliveThread);
    }


    /**
     * Set the clearReferencesHttpClientKeepAliveThread feature for this
     * Context.
     *
     * @param clearReferencesHttpClientKeepAliveThread The new flag value
     */
    public void setClearReferencesHttpClientKeepAliveThread(
            boolean clearReferencesHttpClientKeepAliveThread) {
        this.clearReferencesHttpClientKeepAliveThread =
            clearReferencesHttpClientKeepAliveThread;
    }


    // ------------------------------------------------------- Reloader Methods

    /**
     * Adds the specified class file transformer to this class loader. The
     * transformer will then be able to modify the bytecode of any classes
     * loaded by this class loader after the invocation of this method.
     *
     * @param transformer The transformer to add to the class loader
     */
    @Override
    public void addTransformer(ClassFileTransformer transformer) {

        if (transformer == null) {
            throw new IllegalArgumentException(sm.getString(
                    "webappClassLoader.addTransformer.illegalArgument", getContextName()));
        }

        if (this.transformers.contains(transformer)) {
            // if the same instance of this transformer was already added, bail out
            log.warn(sm.getString("webappClassLoader.addTransformer.duplicate",
                    transformer, getContextName()));
            return;
        }
        this.transformers.add(transformer);

        log.info(sm.getString("webappClassLoader.addTransformer", transformer, getContextName()));
    }

    /**
     * Removes the specified class file transformer from this class loader.
     * It will no longer be able to modify the byte code of any classes
     * loaded by the class loader after the invocation of this method.
     * However, any classes already modified by this transformer will
     * remain transformed.
     *
     * @param transformer The transformer to remove
     */
    @Override
    public void removeTransformer(ClassFileTransformer transformer) {

        if (transformer == null) {
            return;
        }

        if (this.transformers.remove(transformer)) {
            log.info(sm.getString("webappClassLoader.removeTransformer",
                    transformer, getContextName()));
            return;
        }

    }

    protected void copyStateWithoutTransformers(WebappClassLoaderBase base) {
        base.resources = this.resources;
        base.delegate = this.delegate;
        base.state = LifecycleState.NEW;
        base.needConvert = this.needConvert;
        base.clearReferencesStatic = this.clearReferencesStatic;
        base.clearReferencesStopThreads = this.clearReferencesStopThreads;
        base.clearReferencesStopTimerThreads = this.clearReferencesStopTimerThreads;
        base.clearReferencesLogFactoryRelease = this.clearReferencesLogFactoryRelease;
        base.clearReferencesHttpClientKeepAliveThread = this.clearReferencesHttpClientKeepAliveThread;
        base.jarModificationTimes.putAll(this.jarModificationTimes);
        base.permissionList.addAll(this.permissionList);
        base.loaderPC.putAll(this.loaderPC);
    }

    /**
     * Have one or more classes or resources been modified so that a reload
     * is appropriate?
     */
    public boolean modified() {

        if (log.isDebugEnabled())
            log.debug("modified()");

        for (Entry<String,ResourceEntry> entry : resourceEntries.entrySet()) {
            long cachedLastModified = entry.getValue().lastModified;
            long lastModified = resources.getClassLoaderResource(
                    entry.getKey()).getLastModified();
            if (lastModified != cachedLastModified) {
                if( log.isDebugEnabled() )
                    log.debug(sm.getString("webappClassLoader.resourceModified",
                            entry.getKey(),
                            new Date(cachedLastModified),
                            new Date(lastModified)));
                return true;
            }
        }

        // Check if JARs have been added or removed
        WebResource[] jars = resources.listResources("/WEB-INF/lib");
        // Filter out non-JAR resources

        int jarCount = 0;
        for (WebResource jar : jars) {
            if (jar.getName().endsWith(".jar") && jar.isFile() && jar.canRead()) {
                jarCount++;
                Long recordedLastModified = jarModificationTimes.get(jar.getName());
                if (recordedLastModified == null) {
                    // Jar has been added
                    log.info(sm.getString("webappClassLoader.jarsAdded",
                            resources.getContext().getName()));
                    return true;
                }
                if (recordedLastModified.longValue() != jar.getLastModified()) {
                    // Jar has been changed
                    log.info(sm.getString("webappClassLoader.jarsModified",
                            resources.getContext().getName()));
                    return true;
                }
            }
        }

        if (jarCount < jarModificationTimes.size()){
            log.info(sm.getString("webappClassLoader.jarsRemoved",
                    resources.getContext().getName()));
            return true;
        }


        // No classes have been modified
        return false;
    }


    /**
     * Render a String representation of this object.
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder(this.getClass().getSimpleName());
        sb.append("\r\n  context: ");
        sb.append(getContextName());
        sb.append("\r\n  delegate: ");
        sb.append(delegate);
        sb.append("\r\n");
        if (this.parent != null) {
            sb.append("----------> Parent Classloader:\r\n");
            sb.append(this.parent.toString());
            sb.append("\r\n");
        }
        if (this.transformers.size() > 0) {
            sb.append("----------> Class file transformers:\r\n");
            for (ClassFileTransformer transformer : this.transformers) {
                sb.append(transformer).append("\r\n");
            }
        }
        return (sb.toString());
    }


    // ---------------------------------------------------- ClassLoader Methods


    /**
     * Expose this method for use by the unit tests.
     */
    protected final Class<?> doDefineClass(String name, byte[] b, int off, int len,
            ProtectionDomain protectionDomain) {
        return super.defineClass(name, b, off, len, protectionDomain);
    }

    /**
     * Find the specified class in our local repositories, if possible.  If
     * not found, throw <code>ClassNotFoundException</code>.
     *
     * @param name The binary name of the class to be loaded
     *
     * @exception ClassNotFoundException if the class was not found
     */
    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {

        if (log.isDebugEnabled())
            log.debug("    findClass(" + name + ")");

        checkStateForClassLoading(name);

        // (1) Permission to define this class when using a SecurityManager
        if (securityManager != null) {
            int i = name.lastIndexOf('.');
            if (i >= 0) {
                try {
                    if (log.isTraceEnabled())
                        log.trace("      securityManager.checkPackageDefinition");
                    securityManager.checkPackageDefinition(name.substring(0,i));
                } catch (Exception se) {
                    if (log.isTraceEnabled())
                        log.trace("      -->Exception-->ClassNotFoundException", se);
                    throw new ClassNotFoundException(name, se);
                }
            }
        }

        // Ask our superclass to locate this class, if possible
        // (throws ClassNotFoundException if it is not found)
        Class<?> clazz = null;
        try {
            if (log.isTraceEnabled())
                log.trace("      findClassInternal(" + name + ")");
            try {
                clazz = findClassInternal(name);
            } catch(AccessControlException ace) {
                log.warn("WebappClassLoader.findClassInternal(" + name
                        + ") security exception: " + ace.getMessage(), ace);
                throw new ClassNotFoundException(name, ace);
            } catch (RuntimeException e) {
                if (log.isTraceEnabled())
                    log.trace("      -->RuntimeException Rethrown", e);
                throw e;
            }
            if ((clazz == null) && hasExternalRepositories) {
                try {
                    clazz = super.findClass(name);
                } catch(AccessControlException ace) {
                    log.warn("WebappClassLoader.findClassInternal(" + name
                            + ") security exception: " + ace.getMessage(), ace);
                    throw new ClassNotFoundException(name, ace);
                } catch (RuntimeException e) {
                    if (log.isTraceEnabled())
                        log.trace("      -->RuntimeException Rethrown", e);
                    throw e;
                }
            }
            if (clazz == null) {
                if (log.isDebugEnabled())
                    log.debug("    --> Returning ClassNotFoundException");
                throw new ClassNotFoundException(name);
            }
        } catch (ClassNotFoundException e) {
            if (log.isTraceEnabled())
                log.trace("    --> Passing on ClassNotFoundException");
            throw e;
        }

        // Return the class we have located
        if (log.isTraceEnabled())
            log.debug("      Returning class " + clazz);

        if (log.isTraceEnabled()) {
            ClassLoader cl;
            if (Globals.IS_SECURITY_ENABLED){
                cl = AccessController.doPrivileged(
                    new PrivilegedGetClassLoader(clazz));
            } else {
                cl = clazz.getClassLoader();
            }
            log.debug("      Loaded by " + cl.toString());
        }
        return (clazz);

    }


    /**
     * Find the specified resource in our local repository, and return a
     * <code>URL</code> referring to it, or <code>null</code> if this resource
     * cannot be found.
     *
     * @param name Name of the resource to be found
     */
    @Override
    public URL findResource(final String name) {

        if (log.isDebugEnabled())
            log.debug("    findResource(" + name + ")");

        checkStateForResourceLoading(name);

        URL url = null;

        String path = nameToPath(name);

        ResourceEntry entry = resourceEntries.get(path);
        if (entry == null) {
            if (securityManager != null) {
                PrivilegedAction<ResourceEntry> dp =
                    new PrivilegedFindResourceByName(name, path);
                entry = AccessController.doPrivileged(dp);
            } else {
                entry = findResourceInternal(name, path);
            }
        }
        if (entry != null) {
            url = entry.source;
        }

        if ((url == null) && hasExternalRepositories) {
            url = super.findResource(name);
        }

        if (log.isDebugEnabled()) {
            if (url != null)
                log.debug("    --> Returning '" + url.toString() + "'");
            else
                log.debug("    --> Resource not found, returning null");
        }
        return (url);

    }


    /**
     * Return an enumeration of <code>URLs</code> representing all of the
     * resources with the given name.  If no resources with this name are
     * found, return an empty enumeration.
     *
     * @param name Name of the resources to be found
     *
     * @exception IOException if an input/output error occurs
     */
    @Override
    public Enumeration<URL> findResources(String name) throws IOException {

        if (log.isDebugEnabled())
            log.debug("    findResources(" + name + ")");

        checkStateForResourceLoading(name);

        LinkedHashSet<URL> result = new LinkedHashSet<>();

        String path = nameToPath(name);

        WebResource[] webResources = resources.getClassLoaderResources(path);
        for (WebResource webResource : webResources) {
            if (webResource.exists()) {
                result.add(webResource.getURL());
            }
        }

        // Adding the results of a call to the superclass
        if (hasExternalRepositories) {
            Enumeration<URL> otherResourcePaths = super.findResources(name);
            while (otherResourcePaths.hasMoreElements()) {
                result.add(otherResourcePaths.nextElement());
            }
        }

        return Collections.enumeration(result);
    }


    /**
     * Find the resource with the given name.  A resource is some data
     * (images, audio, text, etc.) that can be accessed by class code in a
     * way that is independent of the location of the code.  The name of a
     * resource is a "/"-separated path name that identifies the resource.
     * If the resource cannot be found, return <code>null</code>.
     * <p>
     * This method searches according to the following algorithm, returning
     * as soon as it finds the appropriate URL.  If the resource cannot be
     * found, returns <code>null</code>.
     * <ul>
     * <li>If the <code>delegate</code> property is set to <code>true</code>,
     *     call the <code>getResource()</code> method of the parent class
     *     loader, if any.</li>
     * <li>Call <code>findResource()</code> to find this resource in our
     *     locally defined repositories.</li>
     * <li>Call the <code>getResource()</code> method of the parent class
     *     loader, if any.</li>
     * </ul>
     *
     * @param name Name of the resource to return a URL for
     */
    @Override
    public URL getResource(String name) {

        if (log.isDebugEnabled())
            log.debug("getResource(" + name + ")");

        checkStateForResourceLoading(name);

        URL url = null;

        boolean delegateFirst = delegate || filter(name);

        // (1) Delegate to parent if requested
        if (delegateFirst) {
            if (log.isDebugEnabled())
                log.debug("  Delegating to parent classloader " + parent);
            url = parent.getResource(name);
            if (url != null) {
                if (log.isDebugEnabled())
                    log.debug("  --> Returning '" + url.toString() + "'");
                return (url);
            }
        }

        // (2) Search local repositories
        url = findResource(name);
        if (url != null) {
            if (log.isDebugEnabled())
                log.debug("  --> Returning '" + url.toString() + "'");
            return (url);
        }

        // (3) Delegate to parent unconditionally if not already attempted
        if (!delegateFirst) {
            url = parent.getResource(name);
            if (url != null) {
                if (log.isDebugEnabled())
                    log.debug("  --> Returning '" + url.toString() + "'");
                return (url);
            }
        }

        // (4) Resource was not found
        if (log.isDebugEnabled())
            log.debug("  --> Resource not found, returning null");
        return (null);

    }


    /**
     * Find the resource with the given name, and return an input stream
     * that can be used for reading it.  The search order is as described
     * for <code>getResource()</code>, after checking to see if the resource
     * data has been previously cached.  If the resource cannot be found,
     * return <code>null</code>.
     *
     * @param name Name of the resource to return an input stream for
     */
    @Override
    public InputStream getResourceAsStream(String name) {

        if (log.isDebugEnabled())
            log.debug("getResourceAsStream(" + name + ")");

        checkStateForResourceLoading(name);

        InputStream stream = null;

        // (0) Check for a cached copy of this resource
        stream = findLoadedResource(name);
        if (stream != null) {
            if (log.isDebugEnabled())
                log.debug("  --> Returning stream from cache");
            return (stream);
        }

        boolean delegateFirst = delegate || filter(name);

        // (1) Delegate to parent if requested
        if (delegateFirst) {
            if (log.isDebugEnabled())
                log.debug("  Delegating to parent classloader " + parent);
            stream = parent.getResourceAsStream(name);
            if (stream != null) {
                // FIXME - cache???
                if (log.isDebugEnabled())
                    log.debug("  --> Returning stream from parent");
                return (stream);
            }
        }

        // (2) Search local repositories
        if (log.isDebugEnabled())
            log.debug("  Searching local repositories");
        URL url = findResource(name);
        if (url != null) {
            // FIXME - cache???
            if (log.isDebugEnabled())
                log.debug("  --> Returning stream from local");
            stream = findLoadedResource(name);
            try {
                if (hasExternalRepositories && (stream == null))
                    stream = url.openStream();
            } catch (IOException e) {
                // Ignore
            }
            if (stream != null)
                return (stream);
        }

        // (3) Delegate to parent unconditionally
        if (!delegateFirst) {
            if (log.isDebugEnabled())
                log.debug("  Delegating to parent classloader unconditionally " + parent);
            stream = parent.getResourceAsStream(name);
            if (stream != null) {
                // FIXME - cache???
                if (log.isDebugEnabled())
                    log.debug("  --> Returning stream from parent");
                return (stream);
            }
        }

        // (4) Resource was not found
        if (log.isDebugEnabled())
            log.debug("  --> Resource not found, returning null");
        return (null);

    }


    /**
     * Load the class with the specified name.  This method searches for
     * classes in the same manner as <code>loadClass(String, boolean)</code>
     * with <code>false</code> as the second argument.
     *
     * @param name The binary name of the class to be loaded
     *
     * @exception ClassNotFoundException if the class was not found
     */
    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {

        return (loadClass(name, false));

    }


    /**
     * Load the class with the specified name, searching using the following
     * algorithm until it finds and returns the class.  If the class cannot
     * be found, returns <code>ClassNotFoundException</code>.
     * <ul>
     * <li>Call <code>findLoadedClass(String)</code> to check if the
     *     class has already been loaded.  If it has, the same
     *     <code>Class</code> object is returned.</li>
     * <li>If the <code>delegate</code> property is set to <code>true</code>,
     *     call the <code>loadClass()</code> method of the parent class
     *     loader, if any.</li>
     * <li>Call <code>findClass()</code> to find this class in our locally
     *     defined repositories.</li>
     * <li>Call the <code>loadClass()</code> method of our parent
     *     class loader, if any.</li>
     * </ul>
     * If the class was found using the above steps, and the
     * <code>resolve</code> flag is <code>true</code>, this method will then
     * call <code>resolveClass(Class)</code> on the resulting Class object.
     *
     * @param name The binary name of the class to be loaded
     * @param resolve If <code>true</code> then resolve the class
     *
     * @exception ClassNotFoundException if the class was not found
     */
    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {

        synchronized (getClassLoadingLock(name)) {
            if (log.isDebugEnabled())
                log.debug("loadClass(" + name + ", " + resolve + ")");
            Class<?> clazz = null;

            // Log access to stopped class loader
            checkStateForClassLoading(name);

            // (0) Check our previously loaded local class cache
            clazz = findLoadedClass0(name);
            if (clazz != null) {
                if (log.isDebugEnabled())
                    log.debug("  Returning class from cache");
                if (resolve)
                    resolveClass(clazz);
                return (clazz);
            }

            // (0.1) Check our previously loaded class cache
            clazz = findLoadedClass(name);
            if (clazz != null) {
                if (log.isDebugEnabled())
                    log.debug("  Returning class from cache");
                if (resolve)
                    resolveClass(clazz);
                return (clazz);
            }

            // (0.2) Try loading the class with the system class loader, to prevent
            //       the webapp from overriding Java SE classes. This implements
            //       SRV.10.7.2
            String resourceName = binaryNameToPath(name, false);
            ClassLoader javaseLoader = getJavaseClassLoader();
            if (javaseLoader.getResource(resourceName) != null) {
                try {
                    clazz = javaseLoader.loadClass(name);
                    if (clazz != null) {
                        if (resolve)
                            resolveClass(clazz);
                        return (clazz);
                    }
                } catch (ClassNotFoundException e) {
                    // Ignore
                }
            }

            // (0.5) Permission to access this class when using a SecurityManager
            if (securityManager != null) {
                int i = name.lastIndexOf('.');
                if (i >= 0) {
                    try {
                        securityManager.checkPackageAccess(name.substring(0,i));
                    } catch (SecurityException se) {
                        String error = "Security Violation, attempt to use " +
                            "Restricted Class: " + name;
                        log.info(error, se);
                        throw new ClassNotFoundException(error, se);
                    }
                }
            }

            boolean delegateLoad = delegate || filter(name);

            // (1) Delegate to our parent if requested
            if (delegateLoad) {
                if (log.isDebugEnabled())
                    log.debug("  Delegating to parent classloader1 " + parent);
                try {
                    clazz = Class.forName(name, false, parent);
                    if (clazz != null) {
                        if (log.isDebugEnabled())
                            log.debug("  Loading class from parent");
                        if (resolve)
                            resolveClass(clazz);
                        return (clazz);
                    }
                } catch (ClassNotFoundException e) {
                    // Ignore
                }
            }

            // (2) Search local repositories
            if (log.isDebugEnabled())
                log.debug("  Searching local repositories");
            try {
                clazz = findClass(name);
                if (clazz != null) {
                    if (log.isDebugEnabled())
                        log.debug("  Loading class from local repository");
                    if (resolve)
                        resolveClass(clazz);
                    return (clazz);
                }
            } catch (ClassNotFoundException e) {
                // Ignore
            }

            // (3) Delegate to parent unconditionally
            if (!delegateLoad) {
                if (log.isDebugEnabled())
                    log.debug("  Delegating to parent classloader at end: " + parent);
                try {
                    clazz = Class.forName(name, false, parent);
                    if (clazz != null) {
                        if (log.isDebugEnabled())
                            log.debug("  Loading class from parent");
                        if (resolve)
                            resolveClass(clazz);
                        return (clazz);
                    }
                } catch (ClassNotFoundException e) {
                    // Ignore
                }
            }
        }

        throw new ClassNotFoundException(name);
    }


    protected void checkStateForClassLoading(String className) throws ClassNotFoundException {
        // It is not permitted to load new classes once the web application has
        // been stopped.
        try {
            checkStateForResourceLoading(className);
        } catch (IllegalStateException ise) {
            ClassNotFoundException cnfe = new ClassNotFoundException();
            cnfe.initCause(ise);
            throw cnfe;
        }
    }


    protected void checkStateForResourceLoading(String resource) throws IllegalStateException {
        // It is not permitted to load resources once the web application has
        // been stopped.
        if (!state.isAvailable()) {
            String msg = sm.getString("webappClassLoader.stopped", resource);
            IllegalStateException ise = new IllegalStateException(msg);
            log.info(msg, ise);
            throw ise;
        }
    }

    /**
     * Get the Permissions for a CodeSource.  If this instance
     * of WebappClassLoaderBase is for a web application context,
     * add read FilePermission or JndiPermissions for the base
     * directory (if unpacked),
     * the context URL, and jar file resources.
     *
     * @param codeSource where the code was loaded from
     * @return PermissionCollection for CodeSource
     */
    @Override
    protected PermissionCollection getPermissions(CodeSource codeSource) {

        String codeUrl = codeSource.getLocation().toString();
        PermissionCollection pc;
        if ((pc = loaderPC.get(codeUrl)) == null) {
            pc = super.getPermissions(codeSource);
            if (pc != null) {
                Iterator<Permission> perms = permissionList.iterator();
                while (perms.hasNext()) {
                    Permission p = perms.next();
                    pc.add(p);
                }
                loaderPC.put(codeUrl,pc);
            }
        }
        return (pc);

    }


    /**
     * {@inheritDoc}
     * <p>
     * Note that list of URLs returned by this method may not be complete. The
     * web application class loader accesses class loader resources via the
     * {@link WebResourceRoot} which supports the arbitrary mapping of
     * additional files, directories and contents of JAR files under
     * WEB-INF/classes. Any such resources will not be included in the URLs
     * returned here.
     */
    @Override
    public URL[] getURLs() {
        ArrayList<URL> result = new ArrayList<>();
        result.addAll(localRepositories);
        result.addAll(Arrays.asList(super.getURLs()));
        return result.toArray(new URL[result.size()]);
    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * Add a lifecycle event listener to this component.
     *
     * @param listener The listener to add
     */
    @Override
    public void addLifecycleListener(LifecycleListener listener) {
        // NOOP
    }


    /**
     * Get the lifecycle listeners associated with this lifecycle. If this
     * Lifecycle has no listeners registered, a zero-length array is returned.
     */
    @Override
    public LifecycleListener[] findLifecycleListeners() {
        return new LifecycleListener[0];
    }


    /**
     * Remove a lifecycle event listener from this component.
     *
     * @param listener The listener to remove
     */
    @Override
    public void removeLifecycleListener(LifecycleListener listener) {
        // NOOP
    }


    /**
     * Obtain the current state of the source component.
     *
     * @return The current state of the source component.
     */
    @Override
    public LifecycleState getState() {
        return state;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getStateName() {
        return getState().toString();
    }


    @Override
    public void init() {
        state = LifecycleState.INITIALIZED;
    }


    /**
     * Start the class loader.
     *
     * @exception LifecycleException if a lifecycle error occurs
     */
    @Override
    public void start() throws LifecycleException {

        state = LifecycleState.STARTING_PREP;

        WebResource classes = resources.getResource("/WEB-INF/classes");
        if (classes.isDirectory() && classes.canRead()) {
            localRepositories.add(classes.getURL());
        }
        WebResource[] jars = resources.listResources("/WEB-INF/lib");
        for (WebResource jar : jars) {
            if (jar.getName().endsWith(".jar") && jar.isFile() && jar.canRead()) {
                localRepositories.add(jar.getURL());
                jarModificationTimes.put(
                        jar.getName(), Long.valueOf(jar.getLastModified()));
            }
        }

        state = LifecycleState.STARTING;

        String encoding = null;
        try {
            encoding = System.getProperty("file.encoding");
        } catch (SecurityException e) {
            return;
        }
        if (encoding.indexOf("EBCDIC")!=-1) {
            needConvert = true;
        }

        state = LifecycleState.STARTED;
    }


    /**
     * Stop the class loader.
     *
     * @exception LifecycleException if a lifecycle error occurs
     */
    @Override
    public void stop() throws LifecycleException {

        state = LifecycleState.STOPPING_PREP;

        // Clearing references should be done before setting started to
        // false, due to possible side effects
        clearReferences();

        state = LifecycleState.STOPPING;

        resourceEntries.clear();
        jarModificationTimes.clear();
        resources = null;

        permissionList.clear();
        loaderPC.clear();

        state = LifecycleState.STOPPED;
    }


    @Override
    public void destroy() {
        state = LifecycleState.DESTROYING;

        try {
            super.close();
        } catch (IOException ioe) {
            log.warn(sm.getString("webappClassLoader.superCloseFail"), ioe);
        }
        state = LifecycleState.DESTROYED;
    }


    // ------------------------------------------------------ Protected Methods

    protected ClassLoader getJavaseClassLoader() {
        return javaseClassLoader;
    }

    protected void setJavaseClassLoader(ClassLoader classLoader) {
        if (classLoader == null) {
            throw new IllegalArgumentException(
                    sm.getString("webappClassLoader.javaseClassLoaderNull"));
        }
        javaseClassLoader = classLoader;
    }

    /**
     * Clear references.
     */
    protected void clearReferences() {

        // De-register any remaining JDBC drivers
        clearReferencesJdbc();

        // Stop any threads the web application started
        clearReferencesThreads();

        // Check for leaks triggered by ThreadLocals loaded by this class loader
        checkThreadLocalsForLeaks();

        // Clear RMI Targets loaded by this class loader
        clearReferencesRmiTargets();

        // Null out any static or final fields from loaded classes,
        // as a workaround for apparent garbage collection bugs
        if (clearReferencesStatic) {
            clearReferencesStaticFinal();
        }

         // Clear the IntrospectionUtils cache.
        IntrospectionUtils.clear();

        // Clear the classloader reference in common-logging
        if (clearReferencesLogFactoryRelease) {
            org.apache.juli.logging.LogFactory.release(this);
        }

        // Clear the resource bundle cache
        // This shouldn't be necessary, the cache uses weak references but
        // it has caused leaks. Oddly, using the leak detection code in
        // standard host allows the class loader to be GC'd. This has been seen
        // on Sun but not IBM JREs. Maybe a bug in Sun's GC impl?
        clearReferencesResourceBundles();

        // Clear the classloader reference in the VM's bean introspector
        java.beans.Introspector.flushCaches();

        // Clear any custom URLStreamHandlers
        TomcatURLStreamHandlerFactory.release(this);
    }


    /**
     * Deregister any JDBC drivers registered by the webapp that the webapp
     * forgot. This is made unnecessary complex because a) DriverManager
     * checks the class loader of the calling class (it would be much easier
     * if it checked the context class loader) b) using reflection would
     * create a dependency on the DriverManager implementation which can,
     * and has, changed.
     *
     * We can't just create an instance of JdbcLeakPrevention as it will be
     * loaded by the common class loader (since it's .class file is in the
     * $CATALINA_HOME/lib directory). This would fail DriverManager's check
     * on the class loader of the calling class. So, we load the bytes via
     * our parent class loader but define the class with this class loader
     * so the JdbcLeakPrevention looks like a webapp class to the
     * DriverManager.
     *
     * If only apps cleaned up after themselves...
     */
    private final void clearReferencesJdbc() {
        // We know roughly how big the class will be (~ 1K) so allow 2k as a
        // starting point
        byte[] classBytes = new byte[2048];
        int offset = 0;
        try (InputStream is = getResourceAsStream(
                "org/apache/catalina/loader/JdbcLeakPrevention.class")) {
            int read = is.read(classBytes, offset, classBytes.length-offset);
            while (read > -1) {
                offset += read;
                if (offset == classBytes.length) {
                    // Buffer full - double size
                    byte[] tmp = new byte[classBytes.length * 2];
                    System.arraycopy(classBytes, 0, tmp, 0, classBytes.length);
                    classBytes = tmp;
                }
                read = is.read(classBytes, offset, classBytes.length-offset);
            }
            Class<?> lpClass =
                defineClass("org.apache.catalina.loader.JdbcLeakPrevention",
                    classBytes, 0, offset, this.getClass().getProtectionDomain());
            Object obj = lpClass.newInstance();
            @SuppressWarnings("unchecked")
            List<String> driverNames = (List<String>) obj.getClass().getMethod(
                    "clearJdbcDriverRegistrations").invoke(obj);
            for (String name : driverNames) {
                log.warn(sm.getString("webappClassLoader.clearJdbc",
                        getContextName(), name));
            }
        } catch (Exception e) {
            // So many things to go wrong above...
            Throwable t = ExceptionUtils.unwrapInvocationTargetException(e);
            ExceptionUtils.handleThrowable(t);
            log.warn(sm.getString(
                    "webappClassLoader.jdbcRemoveFailed", getContextName()), t);
        }
    }


    private final void clearReferencesStaticFinal() {

        Collection<ResourceEntry> values = resourceEntries.values();
        Iterator<ResourceEntry> loadedClasses = values.iterator();
        //
        // walk through all loaded class to trigger initialization for
        //    any uninitialized classes, otherwise initialization of
        //    one class may call a previously cleared class.
        while(loadedClasses.hasNext()) {
            ResourceEntry entry = loadedClasses.next();
            if (entry.loadedClass != null) {
                Class<?> clazz = entry.loadedClass;
                try {
                    Field[] fields = clazz.getDeclaredFields();
                    for (int i = 0; i < fields.length; i++) {
                        if(Modifier.isStatic(fields[i].getModifiers())) {
                            fields[i].get(null);
                            break;
                        }
                    }
                } catch(Throwable t) {
                    // Ignore
                }
            }
        }
        loadedClasses = values.iterator();
        while (loadedClasses.hasNext()) {
            ResourceEntry entry = loadedClasses.next();
            if (entry.loadedClass != null) {
                Class<?> clazz = entry.loadedClass;
                try {
                    Field[] fields = clazz.getDeclaredFields();
                    for (int i = 0; i < fields.length; i++) {
                        Field field = fields[i];
                        int mods = field.getModifiers();
                        if (field.getType().isPrimitive()
                                || (field.getName().indexOf("$") != -1)) {
                            continue;
                        }
                        if (Modifier.isStatic(mods)) {
                            try {
                                field.setAccessible(true);
                                if (Modifier.isFinal(mods)) {
                                    if (!((field.getType().getName().startsWith("java."))
                                            || (field.getType().getName().startsWith("javax.")))) {
                                        nullInstance(field.get(null));
                                    }
                                } else {
                                    field.set(null, null);
                                    if (log.isDebugEnabled()) {
                                        log.debug("Set field " + field.getName()
                                                + " to null in class " + clazz.getName());
                                    }
                                }
                            } catch (Throwable t) {
                                ExceptionUtils.handleThrowable(t);
                                if (log.isDebugEnabled()) {
                                    log.debug("Could not set field " + field.getName()
                                            + " to null in class " + clazz.getName(), t);
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    if (log.isDebugEnabled()) {
                        log.debug("Could not clean fields for class " + clazz.getName(), t);
                    }
                }
            }
        }

    }


    private void nullInstance(Object instance) {
        if (instance == null) {
            return;
        }
        Field[] fields = instance.getClass().getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            int mods = field.getModifiers();
            if (field.getType().isPrimitive()
                    || (field.getName().indexOf("$") != -1)) {
                continue;
            }
            try {
                field.setAccessible(true);
                if (Modifier.isStatic(mods) && Modifier.isFinal(mods)) {
                    // Doing something recursively is too risky
                    continue;
                }
                Object value = field.get(instance);
                if (null != value) {
                    Class<? extends Object> valueClass = value.getClass();
                    if (!loadedByThisOrChild(valueClass)) {
                        if (log.isDebugEnabled()) {
                            log.debug("Not setting field " + field.getName() +
                                    " to null in object of class " +
                                    instance.getClass().getName() +
                                    " because the referenced object was of type " +
                                    valueClass.getName() +
                                    " which was not loaded by this web application class loader.");
                        }
                    } else {
                        field.set(instance, null);
                        if (log.isDebugEnabled()) {
                            log.debug("Set field " + field.getName()
                                    + " to null in class " + instance.getClass().getName());
                        }
                    }
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                if (log.isDebugEnabled()) {
                    log.debug("Could not set field " + field.getName()
                            + " to null in object instance of class "
                            + instance.getClass().getName(), t);
                }
            }
        }
    }


    @SuppressWarnings("deprecation") // thread.stop()
    private void clearReferencesThreads() {
        Thread[] threads = getThreads();
        List<Thread> executorThreadsToStop = new ArrayList<>();

        // Iterate over the set of threads
        for (Thread thread : threads) {
            if (thread != null) {
                ClassLoader ccl = thread.getContextClassLoader();
                if (ccl == this) {
                    // Don't warn about this thread
                    if (thread == Thread.currentThread()) {
                        continue;
                    }

                    final String threadName = thread.getName();

                    // JVM controlled threads
                    ThreadGroup tg = thread.getThreadGroup();
                    if (tg != null &&
                            JVM_THREAD_GROUP_NAMES.contains(tg.getName())) {

                        // HttpClient keep-alive threads
                        if (clearReferencesHttpClientKeepAliveThread &&
                                threadName.equals("Keep-Alive-Timer")) {
                            thread.setContextClassLoader(parent);
                            log.debug(sm.getString(
                                    "webappClassLoader.checkThreadsHttpClient"));
                        }

                        // Don't warn about remaining JVM controlled threads
                        continue;
                    }

                    // Skip threads that have already died
                    if (!thread.isAlive()) {
                        continue;
                    }

                    // TimerThread can be stopped safely so treat separately
                    // "java.util.TimerThread" in Sun/Oracle JDK
                    // "java.util.Timer$TimerImpl" in Apache Harmony and in IBM JDK
                    if (thread.getClass().getName().startsWith("java.util.Timer") &&
                            clearReferencesStopTimerThreads) {
                        clearReferencesStopTimerThread(thread);
                        continue;
                    }

                    if (isRequestThread(thread)) {
                        log.warn(sm.getString("webappClassLoader.stackTraceRequestThread",
                                getContextName(), threadName, getStackTrace(thread)));
                    } else {
                        log.warn(sm.getString("webappClassLoader.stackTrace",
                                getContextName(), threadName, getStackTrace(thread)));
                    }

                    // Don't try an stop the threads unless explicitly
                    // configured to do so
                    if (!clearReferencesStopThreads) {
                        continue;
                    }

                    // If the thread has been started via an executor, try
                    // shutting down the executor
                    boolean usingExecutor = false;
                    try {

                        // Runnable wrapped by Thread
                        // "target" in Sun/Oracle JDK
                        // "runnable" in IBM JDK
                        // "action" in Apache Harmony
                        Object target = null;
                        for (String fieldName : new String[] { "target",
                                "runnable", "action" }) {
                            try {
                                Field targetField = thread.getClass()
                                        .getDeclaredField(fieldName);
                                targetField.setAccessible(true);
                                target = targetField.get(thread);
                                break;
                            } catch (NoSuchFieldException nfe) {
                                continue;
                            }
                        }

                        // "java.util.concurrent" code is in public domain,
                        // so all implementations are similar
                        if (target != null &&
                                target.getClass().getCanonicalName() != null
                                && target.getClass().getCanonicalName().equals(
                                "java.util.concurrent.ThreadPoolExecutor.Worker")) {
                            Field executorField =
                                target.getClass().getDeclaredField("this$0");
                            executorField.setAccessible(true);
                            Object executor = executorField.get(target);
                            if (executor instanceof ThreadPoolExecutor) {
                                ((ThreadPoolExecutor) executor).shutdownNow();
                                usingExecutor = true;
                            }
                        }
                    } catch (SecurityException e) {
                        log.warn(sm.getString(
                                "webappClassLoader.stopThreadFail",
                                thread.getName(), getContextName()), e);
                    } catch (NoSuchFieldException e) {
                        log.warn(sm.getString(
                                "webappClassLoader.stopThreadFail",
                                thread.getName(), getContextName()), e);
                    } catch (IllegalArgumentException e) {
                        log.warn(sm.getString(
                                "webappClassLoader.stopThreadFail",
                                thread.getName(), getContextName()), e);
                    } catch (IllegalAccessException e) {
                        log.warn(sm.getString(
                                "webappClassLoader.stopThreadFail",
                                thread.getName(), getContextName()), e);
                    }

                    if (usingExecutor) {
                        // Executor may take a short time to stop all the
                        // threads. Make a note of threads that should be
                        // stopped and check them at the end of the method.
                        executorThreadsToStop.add(thread);
                    } else {
                        // This method is deprecated and for good reason. This
                        // is very risky code but is the only option at this
                        // point. A *very* good reason for apps to do this
                        // clean-up themselves.
                        thread.stop();
                    }
                }
            }
        }

        // If thread stopping is enabled, executor threads should have been
        // stopped above when the executor was shut down but that depends on the
        // thread correctly handling the interrupt. Give all the executor
        // threads a few seconds shutdown and if they are still running
        // Give threads up to 2 seconds to shutdown
        int count = 0;
        for (Thread t : executorThreadsToStop) {
            while (t.isAlive() && count < 100) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    // Quit the while loop
                    break;
                }
                count++;
            }
            if (t.isAlive()) {
                // This method is deprecated and for good reason. This is
                // very risky code but is the only option at this point.
                // A *very* good reason for apps to do this clean-up
                // themselves.
                t.stop();
            }
        }
    }


    /*
     * Look at a threads stack trace to see if it is a request thread or not. It
     * isn't perfect, but it should be good-enough for most cases.
     */
    private boolean isRequestThread(Thread thread) {

        StackTraceElement[] elements = thread.getStackTrace();

        if (elements == null || elements.length == 0) {
            // Must have stopped already. Too late to ignore it. Assume not a
            // request processing thread.
            return false;
        }

        // Step through the methods in reverse order looking for calls to any
        // CoyoteAdapter method. All request threads will have this unless
        // Tomcat has been heavily modified - in which case there isn't much we
        // can do.
        for (int i = 0; i < elements.length; i++) {
            StackTraceElement element = elements[elements.length - (i+1)];
            if ("org.apache.catalina.connector.CoyoteAdapter".equals(
                    element.getClassName())) {
                return true;
            }
        }
        return false;
    }


    private void clearReferencesStopTimerThread(Thread thread) {

        // Need to get references to:
        // in Sun/Oracle JDK:
        // - newTasksMayBeScheduled field (in java.util.TimerThread)
        // - queue field
        // - queue.clear()
        // in IBM JDK, Apache Harmony:
        // - cancel() method (in java.util.Timer$TimerImpl)

        try {

            try {
                Field newTasksMayBeScheduledField =
                    thread.getClass().getDeclaredField("newTasksMayBeScheduled");
                newTasksMayBeScheduledField.setAccessible(true);
                Field queueField = thread.getClass().getDeclaredField("queue");
                queueField.setAccessible(true);

                Object queue = queueField.get(thread);

                Method clearMethod = queue.getClass().getDeclaredMethod("clear");
                clearMethod.setAccessible(true);

                synchronized(queue) {
                    newTasksMayBeScheduledField.setBoolean(thread, false);
                    clearMethod.invoke(queue);
                    queue.notify();  // In case queue was already empty.
                }

            }catch (NoSuchFieldException nfe){
                Method cancelMethod = thread.getClass().getDeclaredMethod("cancel");
                synchronized(thread) {
                    cancelMethod.setAccessible(true);
                    cancelMethod.invoke(thread);
                }
            }

            log.warn(sm.getString("webappClassLoader.warnTimerThread",
                    getContextName(), thread.getName()));

        } catch (Exception e) {
            // So many things to go wrong above...
            Throwable t = ExceptionUtils.unwrapInvocationTargetException(e);
            ExceptionUtils.handleThrowable(t);
            log.warn(sm.getString(
                    "webappClassLoader.stopTimerThreadFail",
                    thread.getName(), getContextName()), t);
        }
    }

    private void checkThreadLocalsForLeaks() {
        Thread[] threads = getThreads();

        try {
            // Make the fields in the Thread class that store ThreadLocals
            // accessible
            Field threadLocalsField =
                Thread.class.getDeclaredField("threadLocals");
            threadLocalsField.setAccessible(true);
            Field inheritableThreadLocalsField =
                Thread.class.getDeclaredField("inheritableThreadLocals");
            inheritableThreadLocalsField.setAccessible(true);
            // Make the underlying array of ThreadLoad.ThreadLocalMap.Entry objects
            // accessible
            Class<?> tlmClass = Class.forName("java.lang.ThreadLocal$ThreadLocalMap");
            Field tableField = tlmClass.getDeclaredField("table");
            tableField.setAccessible(true);
            Method expungeStaleEntriesMethod = tlmClass.getDeclaredMethod("expungeStaleEntries");
            expungeStaleEntriesMethod.setAccessible(true);

            for (int i = 0; i < threads.length; i++) {
                Object threadLocalMap;
                if (threads[i] != null) {

                    // Clear the first map
                    threadLocalMap = threadLocalsField.get(threads[i]);
                    if (null != threadLocalMap){
                        expungeStaleEntriesMethod.invoke(threadLocalMap);
                        checkThreadLocalMapForLeaks(threadLocalMap, tableField);
                    }

                    // Clear the second map
                    threadLocalMap =inheritableThreadLocalsField.get(threads[i]);
                    if (null != threadLocalMap){
                        expungeStaleEntriesMethod.invoke(threadLocalMap);
                        checkThreadLocalMapForLeaks(threadLocalMap, tableField);
                    }
                }
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.warn(sm.getString(
                    "webappClassLoader.checkThreadLocalsForLeaksFail",
                    getContextName()), t);
        }
    }


    /**
     * Analyzes the given thread local map object. Also pass in the field that
     * points to the internal table to save re-calculating it on every
     * call to this method.
     */
    private void checkThreadLocalMapForLeaks(Object map,
            Field internalTableField) throws IllegalAccessException,
            NoSuchFieldException {
        if (map != null) {
            Object[] table = (Object[]) internalTableField.get(map);
            if (table != null) {
                for (int j =0; j < table.length; j++) {
                    Object obj = table[j];
                    if (obj != null) {
                        boolean potentialLeak = false;
                        // Check the key
                        Object key = ((Reference<?>) obj).get();
                        if (this.equals(key) || loadedByThisOrChild(key)) {
                            potentialLeak = true;
                        }
                        // Check the value
                        Field valueField =
                                obj.getClass().getDeclaredField("value");
                        valueField.setAccessible(true);
                        Object value = valueField.get(obj);
                        if (this.equals(value) || loadedByThisOrChild(value)) {
                            potentialLeak = true;
                        }
                        if (potentialLeak) {
                            Object[] args = new Object[5];
                            args[0] = getContextName();
                            if (key != null) {
                                args[1] = getPrettyClassName(key.getClass());
                                try {
                                    args[2] = key.toString();
                                } catch (Exception e) {
                                    log.warn(sm.getString(
                                            "webappClassLoader.checkThreadLocalsForLeaks.badKey",
                                            args[1]), e);
                                    args[2] = sm.getString(
                                            "webappClassLoader.checkThreadLocalsForLeaks.unknown");
                                }
                            }
                            if (value != null) {
                                args[3] = getPrettyClassName(value.getClass());
                                try {
                                    args[4] = value.toString();
                                } catch (Exception e) {
                                    log.warn(sm.getString(
                                            "webappClassLoader.checkThreadLocalsForLeaks.badValue",
                                            args[3]), e);
                                    args[4] = sm.getString(
                                    "webappClassLoader.checkThreadLocalsForLeaks.unknown");
                                }
                            }
                            if (value == null) {
                                if (log.isDebugEnabled()) {
                                    log.debug(sm.getString(
                                            "webappClassLoader.checkThreadLocalsForLeaksDebug",
                                            args));
                                }
                            } else {
                                log.error(sm.getString(
                                        "webappClassLoader.checkThreadLocalsForLeaks",
                                        args));
                            }
                        }
                    }
                }
            }
        }
    }

    private String getPrettyClassName(Class<?> clazz) {
        String name = clazz.getCanonicalName();
        if (name==null){
            name = clazz.getName();
        }
        return name;
    }

    private String getStackTrace(Thread thread) {
        StringBuilder builder = new StringBuilder();
        for (StackTraceElement ste : thread.getStackTrace()) {
            builder.append("\n ").append(ste);
        }
        return builder.toString();
    }

    /**
     * @param o object to test, may be null
     * @return <code>true</code> if o has been loaded by the current classloader
     * or one of its descendants.
     */
    private boolean loadedByThisOrChild(Object o) {
        if (o == null) {
            return false;
        }

        Class<?> clazz;
        if (o instanceof Class) {
            clazz = (Class<?>) o;
        } else {
            clazz = o.getClass();
        }

        ClassLoader cl = clazz.getClassLoader();
        while (cl != null) {
            if (cl == this) {
                return true;
            }
            cl = cl.getParent();
        }

        if (o instanceof Collection<?>) {
            Iterator<?> iter = ((Collection<?>) o).iterator();
            try {
                while (iter.hasNext()) {
                    Object entry = iter.next();
                    if (loadedByThisOrChild(entry)) {
                        return true;
                    }
                }
            } catch (ConcurrentModificationException e) {
                log.warn(sm.getString(
                        "webappClassLoader.loadedByThisOrChildFail", clazz.getName(), getContextName()),
                        e);
            }
        }
        return false;
    }

    /*
     * Get the set of current threads as an array.
     */
    private Thread[] getThreads() {
        // Get the current thread group
        ThreadGroup tg = Thread.currentThread().getThreadGroup();
        // Find the root thread group
        try {
            while (tg.getParent() != null) {
                tg = tg.getParent();
            }
        } catch (SecurityException se) {
            String msg = sm.getString(
                    "webappClassLoader.getThreadGroupError", tg.getName());
            if (log.isDebugEnabled()) {
                log.debug(msg, se);
            } else {
                log.warn(msg);
            }
        }

        int threadCountGuess = tg.activeCount() + 50;
        Thread[] threads = new Thread[threadCountGuess];
        int threadCountActual = tg.enumerate(threads);
        // Make sure we don't miss any threads
        while (threadCountActual == threadCountGuess) {
            threadCountGuess *=2;
            threads = new Thread[threadCountGuess];
            // Note tg.enumerate(Thread[]) silently ignores any threads that
            // can't fit into the array
            threadCountActual = tg.enumerate(threads);
        }

        return threads;
    }


    /**
     * This depends on the internals of the Sun JVM so it does everything by
     * reflection.
     */
    private void clearReferencesRmiTargets() {
        try {
            // Need access to the ccl field of sun.rmi.transport.Target
            Class<?> objectTargetClass =
                Class.forName("sun.rmi.transport.Target");
            Field cclField = objectTargetClass.getDeclaredField("ccl");
            cclField.setAccessible(true);

            // Clear the objTable map
            Class<?> objectTableClass =
                Class.forName("sun.rmi.transport.ObjectTable");
            Field objTableField = objectTableClass.getDeclaredField("objTable");
            objTableField.setAccessible(true);
            Object objTable = objTableField.get(null);
            if (objTable == null) {
                return;
            }

            // Iterate over the values in the table
            if (objTable instanceof Map<?,?>) {
                Iterator<?> iter = ((Map<?,?>) objTable).values().iterator();
                while (iter.hasNext()) {
                    Object obj = iter.next();
                    Object cclObject = cclField.get(obj);
                    if (this == cclObject) {
                        iter.remove();
                    }
                }
            }

            // Clear the implTable map
            Field implTableField = objectTableClass.getDeclaredField("implTable");
            implTableField.setAccessible(true);
            Object implTable = implTableField.get(null);
            if (implTable == null) {
                return;
            }

            // Iterate over the values in the table
            if (implTable instanceof Map<?,?>) {
                Iterator<?> iter = ((Map<?,?>) implTable).values().iterator();
                while (iter.hasNext()) {
                    Object obj = iter.next();
                    Object cclObject = cclField.get(obj);
                    if (this == cclObject) {
                        iter.remove();
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            log.info(sm.getString("webappClassLoader.clearRmiInfo",
                    getContextName()), e);
        } catch (SecurityException e) {
            log.warn(sm.getString("webappClassLoader.clearRmiFail",
                    getContextName()), e);
        } catch (NoSuchFieldException e) {
            log.warn(sm.getString("webappClassLoader.clearRmiFail",
                    getContextName()), e);
        } catch (IllegalArgumentException e) {
            log.warn(sm.getString("webappClassLoader.clearRmiFail",
                    getContextName()), e);
        } catch (IllegalAccessException e) {
            log.warn(sm.getString("webappClassLoader.clearRmiFail",
                    getContextName()), e);
        }
    }


    /**
     * Clear the {@link ResourceBundle} cache of any bundles loaded by this
     * class loader or any class loader where this loader is a parent class
     * loader. Whilst {@link ResourceBundle#clearCache()} could be used there
     * are complications around the
     * {@link org.apache.jasper.servlet.JasperLoader} that mean a reflection
     * based approach is more likely to be complete.
     *
     * The ResourceBundle is using WeakReferences so it shouldn't be pinning the
     * class loader in memory. However, it is. Therefore clear ou the
     * references.
     */
    private void clearReferencesResourceBundles() {
        // Get a reference to the cache
        try {
            Field cacheListField =
                ResourceBundle.class.getDeclaredField("cacheList");
            cacheListField.setAccessible(true);

            // Java 6 uses ConcurrentMap
            // Java 5 uses SoftCache extends Abstract Map
            // So use Map and it *should* work with both
            Map<?,?> cacheList = (Map<?,?>) cacheListField.get(null);

            // Get the keys (loader references are in the key)
            Set<?> keys = cacheList.keySet();

            Field loaderRefField = null;

            // Iterate over the keys looking at the loader instances
            Iterator<?> keysIter = keys.iterator();

            int countRemoved = 0;

            while (keysIter.hasNext()) {
                Object key = keysIter.next();

                if (loaderRefField == null) {
                    loaderRefField =
                        key.getClass().getDeclaredField("loaderRef");
                    loaderRefField.setAccessible(true);
                }
                WeakReference<?> loaderRef =
                    (WeakReference<?>) loaderRefField.get(key);

                ClassLoader loader = (ClassLoader) loaderRef.get();

                while (loader != null && loader != this) {
                    loader = loader.getParent();
                }

                if (loader != null) {
                    keysIter.remove();
                    countRemoved++;
                }
            }

            if (countRemoved > 0 && log.isDebugEnabled()) {
                log.debug(sm.getString(
                        "webappClassLoader.clearReferencesResourceBundlesCount",
                        Integer.valueOf(countRemoved), getContextName()));
            }
        } catch (SecurityException e) {
            log.warn(sm.getString(
                    "webappClassLoader.clearReferencesResourceBundlesFail",
                    getContextName()), e);
        } catch (NoSuchFieldException e) {
            if (Globals.IS_ORACLE_JVM) {
                log.warn(sm.getString(
                        "webappClassLoader.clearReferencesResourceBundlesFail",
                        getContextName()), e);
            } else {
                log.debug(sm.getString(
                        "webappClassLoader.clearReferencesResourceBundlesFail",
                        getContextName()), e);
            }
        } catch (IllegalArgumentException e) {
            log.warn(sm.getString(
                    "webappClassLoader.clearReferencesResourceBundlesFail",
                    getContextName()), e);
        } catch (IllegalAccessException e) {
            log.warn(sm.getString(
                    "webappClassLoader.clearReferencesResourceBundlesFail",
                    getContextName()), e);
        }
    }


    /**
     * Find specified class in local repositories.
     *
     * @param name The binary name of the class to be loaded
     *
     * @return the loaded class, or null if the class isn't found
     */
    protected Class<?> findClassInternal(String name) {

        String path = binaryNameToPath(name, true);

        ResourceEntry entry = null;

        if (securityManager != null) {
            PrivilegedAction<ResourceEntry> dp =
                new PrivilegedFindResourceByName(name, path);
            entry = AccessController.doPrivileged(dp);
        } else {
            entry = findResourceInternal(name, path);
        }

        if (entry == null) {
            return null;
        }

        Class<?> clazz = entry.loadedClass;
        if (clazz != null)
            return clazz;

        synchronized (getClassLoadingLock(name)) {
            clazz = entry.loadedClass;
            if (clazz != null)
                return clazz;

            if (entry.binaryContent == null) {
                return null;
            }

            // Looking up the package
            String packageName = null;
            int pos = name.lastIndexOf('.');
            if (pos != -1)
                packageName = name.substring(0, pos);

            Package pkg = null;

            if (packageName != null) {
                pkg = getPackage(packageName);
                // Define the package (if null)
                if (pkg == null) {
                    try {
                        if (entry.manifest == null) {
                            definePackage(packageName, null, null, null, null,
                                    null, null, null);
                        } else {
                            definePackage(packageName, entry.manifest,
                                    entry.codeBase);
                        }
                    } catch (IllegalArgumentException e) {
                        // Ignore: normal error due to dual definition of package
                    }
                    pkg = getPackage(packageName);
                }
            }

            if (securityManager != null) {

                // Checking sealing
                if (pkg != null) {
                    boolean sealCheck = true;
                    if (pkg.isSealed()) {
                        sealCheck = pkg.isSealed(entry.codeBase);
                    } else {
                        sealCheck = (entry.manifest == null)
                            || !isPackageSealed(packageName, entry.manifest);
                    }
                    if (!sealCheck)
                        throw new SecurityException
                            ("Sealing violation loading " + name + " : Package "
                             + packageName + " is sealed.");
                }

            }

            try {
                clazz = defineClass(name, entry.binaryContent, 0,
                        entry.binaryContent.length,
                        new CodeSource(entry.codeBase, entry.certificates));
            } catch (UnsupportedClassVersionError ucve) {
                throw new UnsupportedClassVersionError(
                        ucve.getLocalizedMessage() + " " +
                        sm.getString("webappClassLoader.wrongVersion",
                                name));
            }
            // Now the class has been defined, clear the elements of the local
            // resource cache that are no longer required.
            entry.loadedClass = clazz;
            entry.binaryContent = null;
            entry.codeBase = null;
            entry.manifest = null;
            entry.certificates = null;
            // Retain entry.source in case of a getResourceAsStream() call on
            // the class file after the class has been defined.
        }

        return clazz;
    }


    private String binaryNameToPath(String binaryName, boolean withLeadingSlash) {
        // 1 for leading '/', 6 for ".class"
        StringBuilder path = new StringBuilder(7 + binaryName.length());
        if (withLeadingSlash) {
            path.append('/');
        }
        path.append(binaryName.replace('.', '/'));
        path.append(CLASS_FILE_SUFFIX);
        return path.toString();
    }


    private String nameToPath(String name) {
        if (name.startsWith("/")) {
            return name;
        }
        StringBuilder path = new StringBuilder(
                1 + name.length());
        path.append('/');
        path.append(name);
        return path.toString();
    }


    /**
     * Find specified resource in local repositories.
     *
     * @return the loaded resource, or null if the resource isn't found
     */
    protected ResourceEntry findResourceInternal(final String name, final String path) {

        checkStateForResourceLoading(name);

        if (name == null || path == null) {
            return null;
        }

        ResourceEntry entry = resourceEntries.get(path);
        if (entry != null) {
            return entry;
        }

        boolean isClassResource = path.endsWith(CLASS_FILE_SUFFIX);
        boolean isCacheable = isClassResource;
        if (!isCacheable) {
             isCacheable = path.startsWith(SERVICES_PREFIX);
        }

        WebResource resource = null;

        boolean fileNeedConvert = false;

        resource = resources.getClassLoaderResource(path);

        if (!resource.exists()) {
            return null;
        }

        entry = new ResourceEntry();
        entry.source = resource.getURL();
        entry.codeBase = resource.getCodeBase();
        entry.lastModified = resource.getLastModified();

        if (needConvert && path.endsWith(".properties")) {
            fileNeedConvert = true;
        }

        /* Only cache the binary content if there is some content
         * available one of the following is true:
         * a) It is a class file since the binary content is only cached
         *    until the class has been loaded
         *    or
         * b) The file needs conversion to address encoding issues (see
         *    below)
         *    or
         * c) The resource is a service provider configuration file located
         *    under META=INF/services
         *
         * In all other cases do not cache the content to prevent
         * excessive memory usage if large resources are present (see
         * https://bz.apache.org/bugzilla/show_bug.cgi?id=53081).
         */
        if (isCacheable || fileNeedConvert) {
            byte[] binaryContent = resource.getContent();
            if (binaryContent != null) {
                 if (fileNeedConvert) {
                    // Workaround for certain files on platforms that use
                    // EBCDIC encoding, when they are read through FileInputStream.
                    // See commit message of rev.303915 for details
                    // http://svn.apache.org/viewvc?view=revision&revision=303915
                    String str = new String(binaryContent);
                    try {
                        binaryContent = str.getBytes(StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        return null;
                    }
                }
                entry.binaryContent = binaryContent;
                // The certificates and manifest are made available as a side
                // effect of reading the binary content
                entry.certificates = resource.getCertificates();
            }
        }
        entry.manifest = resource.getManifest();

        if (isClassResource && entry.binaryContent != null &&
                this.transformers.size() > 0) {
            // If the resource is a class just being loaded, decorate it
            // with any attached transformers
            String className = name.endsWith(CLASS_FILE_SUFFIX) ?
                    name.substring(0, name.length() - CLASS_FILE_SUFFIX.length()) : name;
            String internalName = className.replace(".", "/");

            for (ClassFileTransformer transformer : this.transformers) {
                try {
                    byte[] transformed = transformer.transform(
                            this, internalName, null, null, entry.binaryContent
                    );
                    if (transformed != null) {
                        entry.binaryContent = transformed;
                    }
                } catch (IllegalClassFormatException e) {
                    log.error(sm.getString("webappClassLoader.transformError", name), e);
                    return null;
                }
            }
        }

        // Add the entry in the local resource repository
        synchronized (resourceEntries) {
            // Ensures that all the threads which may be in a race to load
            // a particular class all end up with the same ResourceEntry
            // instance
            ResourceEntry entry2 = resourceEntries.get(path);
            if (entry2 == null) {
                resourceEntries.put(path, entry);
            } else {
                entry = entry2;
            }
        }

        return entry;
    }


    /**
     * Returns true if the specified package name is sealed according to the
     * given manifest.
     */
    protected boolean isPackageSealed(String name, Manifest man) {

        String path = name.replace('.', '/') + '/';
        Attributes attr = man.getAttributes(path);
        String sealed = null;
        if (attr != null) {
            sealed = attr.getValue(Name.SEALED);
        }
        if (sealed == null) {
            if ((attr = man.getMainAttributes()) != null) {
                sealed = attr.getValue(Name.SEALED);
            }
        }
        return "true".equalsIgnoreCase(sealed);

    }


    /**
     * Finds the resource with the given name if it has previously been
     * loaded and cached by this class loader, and return an input stream
     * to the resource data.  If this resource has not been cached, return
     * <code>null</code>.
     *
     * @param name Name of the resource to return
     */
    protected InputStream findLoadedResource(String name) {

        String path = nameToPath(name);

        ResourceEntry entry = resourceEntries.get(path);
        if (entry != null) {
            if (entry.binaryContent != null)
                return new ByteArrayInputStream(entry.binaryContent);
            else {
                try {
                    return entry.source.openStream();
                } catch (IOException ioe) {
                    // Ignore
                }
            }
        }
        return null;
    }


    /**
     * Finds the class with the given name if it has previously been
     * loaded and cached by this class loader, and return the Class object.
     * If this class has not been cached, return <code>null</code>.
     *
     * @param name The binary name of the resource to return
     */
    protected Class<?> findLoadedClass0(String name) {

        String path = binaryNameToPath(name, true);

        ResourceEntry entry = resourceEntries.get(path);
        if (entry != null) {
            return entry.loadedClass;
        }
        return null;
    }


    /**
     * Refresh the system policy file, to pick up eventual changes.
     */
    protected void refreshPolicy() {

        try {
            // The policy file may have been modified to adjust
            // permissions, so we're reloading it when loading or
            // reloading a Context
            Policy policy = Policy.getPolicy();
            policy.refresh();
        } catch (AccessControlException e) {
            // Some policy files may restrict this, even for the core,
            // so this exception is ignored
        }

    }


    /**
     * Filter classes.
     *
     * @param name class name
     * @return true if the class should be filtered
     */
    protected synchronized boolean filter(String name) {

        if (name == null)
            return false;

        // Looking up the package
        String packageName = null;
        int pos = name.lastIndexOf('.');
        if (pos != -1)
            // Package names in the filters include the last '.'
            packageName = name.substring(0, pos + 1);
        else
            return false;

        packageTriggersPermit.reset(packageName);
        if (packageTriggersPermit.lookingAt()) {
            return false;
        }

        packageTriggersDeny.reset(packageName);
        if (packageTriggersDeny.lookingAt()) {
            return true;
        }

        return false;
    }


    @Override
    protected void addURL(URL url) {
        super.addURL(url);
        hasExternalRepositories = true;
    }


    @Override
    public String getWebappName() {
        return getContextName();
    }


    @Override
    public String getHostName() {
        if (resources != null) {
            Container host = resources.getContext().getParent();
            if (host != null) {
                return host.getName();
            }
        }
        return null;
    }


    @Override
    public String getServiceName() {
        if (resources != null) {
            Container host = resources.getContext().getParent();
            if (host != null) {
                Container engine = host.getParent();
                if (engine != null) {
                    return engine.getName();
                }
            }
        }
        return null;
    }
}

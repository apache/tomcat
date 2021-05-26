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

import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
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
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;

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
import org.apache.tomcat.util.compat.JreCompat;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.PermissionCheck;

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
 * the order they are added via the initial constructor.
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
        implements Lifecycle, InstrumentableClassLoader, WebappProperties, PermissionCheck {

    private static final Log log = LogFactory.getLog(WebappClassLoaderBase.class);

    /**
     * List of ThreadGroup names to ignore when scanning for web application
     * started threads that need to be shut down.
     */
    private static final List<String> JVM_THREAD_GROUP_NAMES = new ArrayList<>();

    private static final String JVM_THREAD_GROUP_SYSTEM = "system";

    private static final String CLASS_FILE_SUFFIX = ".class";

    static {
        if (!JreCompat.isGraalAvailable()) {
            ClassLoader.registerAsParallelCapable();
        }
        JVM_THREAD_GROUP_NAMES.add(JVM_THREAD_GROUP_SYSTEM);
        JVM_THREAD_GROUP_NAMES.add("RMI Runtime");
    }

    protected class PrivilegedFindClassByName implements PrivilegedAction<Class<?>> {

        private final String name;

        PrivilegedFindClassByName(String name) {
            this.name = name;
        }

        @Override
        public Class<?> run() {
            return findClassInternal(name);
        }
    }


    protected static final class PrivilegedGetClassLoader implements PrivilegedAction<ClassLoader> {

        private final Class<?> clazz;

        public PrivilegedGetClassLoader(Class<?> clazz){
            this.clazz = clazz;
        }

        @Override
        public ClassLoader run() {
            return clazz.getClassLoader();
        }
    }


    protected final class PrivilegedJavaseGetResource implements PrivilegedAction<URL> {

        private final String name;

        public PrivilegedJavaseGetResource(String name) {
            this.name = name;
        }

        @Override
        public URL run() {
            return javaseClassLoader.getResource(name);
        }
    }


    // ------------------------------------------------------- Static Variables

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(WebappClassLoaderBase.class);


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


    private final Map<String,Long> jarModificationTimes = new HashMap<>();


    /**
     * A list of read File Permission's required if this loader is for a web
     * application context.
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
     * implementations this class loader is always <code>null</code> and in
     * those cases {@link ClassLoader#getParent()} will be called recursively on
     * the system class loader and the last non-null result used.
     */
    private ClassLoader javaseClassLoader;


    /**
     * Enables the RMI Target memory leak detection to be controlled. This is
     * necessary since the detection can only work on Java 9 if some of the
     * modularity checks are disabled.
     */
    private boolean clearReferencesRmiTargets = true;

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
     * Should Tomcat call
     * {@link org.apache.juli.logging.LogFactory#release(ClassLoader)} when the
     * class loader is stopped? If not specified, the default value of
     * <code>true</code> is used. Changing the default setting is likely to lead
     * to memory leaks and other issues.
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
     * Should Tomcat attempt to clear references to classes loaded by this class
     * loader from the ObjectStreamClass caches?
     */
    private boolean clearReferencesObjectStreamClassCaches = true;

    /**
     * Should Tomcat attempt to clear references to classes loaded by this class
     * loader from ThreadLocals?
     */
    private boolean clearReferencesThreadLocals = true;

    /**
     * Should Tomcat skip the memory leak checks when the web application is
     * stopped as part of the process of shutting down the JVM?
     */
    private boolean skipMemoryLeakChecksOnJvmShutdown = false;

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
     * @return associated resources.
     */
    public WebResourceRoot getResources() {
        return this.resources;
    }


    /**
     * Set associated resources.
     * @param resources the resources from which the classloader will
     *     load the classes
     */
    public void setResources(WebResourceRoot resources) {
        this.resources = resources;
    }


    /**
     * @return the context name for this class loader.
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
     * @return <code>true</code> if the class lookup will delegate to
     *   the parent first. The default in Tomcat is <code>false</code>.
     */
    public boolean getDelegate() {
        return this.delegate;
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
                            "webappClassLoader.addPermissionNoCanonicalFile",
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
                        "webappClassLoader.addPermissionNoProtocol",
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


    public boolean getClearReferencesRmiTargets() {
        return this.clearReferencesRmiTargets;
    }


    public void setClearReferencesRmiTargets(boolean clearReferencesRmiTargets) {
        this.clearReferencesRmiTargets = clearReferencesRmiTargets;
    }


    /**
     * @return the clearReferencesStopThreads flag for this Context.
     */
    public boolean getClearReferencesStopThreads() {
        return this.clearReferencesStopThreads;
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
     * @return the clearReferencesStopTimerThreads flag for this Context.
     */
    public boolean getClearReferencesStopTimerThreads() {
        return this.clearReferencesStopTimerThreads;
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
     * @return the clearReferencesLogFactoryRelease flag for this Context.
     */
    public boolean getClearReferencesLogFactoryRelease() {
        return this.clearReferencesLogFactoryRelease;
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
     * @return the clearReferencesHttpClientKeepAliveThread flag for this
     * Context.
     */
    public boolean getClearReferencesHttpClientKeepAliveThread() {
        return this.clearReferencesHttpClientKeepAliveThread;
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


    public boolean getClearReferencesObjectStreamClassCaches() {
        return clearReferencesObjectStreamClassCaches;
    }


    public void setClearReferencesObjectStreamClassCaches(
            boolean clearReferencesObjectStreamClassCaches) {
        this.clearReferencesObjectStreamClassCaches = clearReferencesObjectStreamClassCaches;
    }


    public boolean getClearReferencesThreadLocals() {
        return clearReferencesThreadLocals;
    }


    public void setClearReferencesThreadLocals(boolean clearReferencesThreadLocals) {
        this.clearReferencesThreadLocals = clearReferencesThreadLocals;
    }


    public boolean getSkipMemoryLeakChecksOnJvmShutdown() {
        return skipMemoryLeakChecksOnJvmShutdown;
    }


    public void setSkipMemoryLeakChecksOnJvmShutdown(boolean skipMemoryLeakChecksOnJvmShutdown) {
        this.skipMemoryLeakChecksOnJvmShutdown = skipMemoryLeakChecksOnJvmShutdown;
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
        }
    }

    protected void copyStateWithoutTransformers(WebappClassLoaderBase base) {
        base.resources = this.resources;
        base.delegate = this.delegate;
        base.state = LifecycleState.NEW;
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
     * @return <code>true</code> if there's been a modification
     */
    public boolean modified() {

        if (log.isDebugEnabled()) {
            log.debug("modified()");
        }

        for (Entry<String,ResourceEntry> entry : resourceEntries.entrySet()) {
            long cachedLastModified = entry.getValue().lastModified;
            long lastModified = resources.getClassLoaderResource(
                    entry.getKey()).getLastModified();
            if (lastModified != cachedLastModified) {
                if( log.isDebugEnabled() ) {
                    log.debug(sm.getString("webappClassLoader.resourceModified",
                            entry.getKey(),
                            new Date(cachedLastModified),
                            new Date(lastModified)));
                }
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
        return sb.toString();
    }


    // ---------------------------------------------------- ClassLoader Methods


    // Note: exposed for use by tests
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

        if (log.isDebugEnabled()) {
            log.debug("    findClass(" + name + ")");
        }

        checkStateForClassLoading(name);

        // (1) Permission to define this class when using a SecurityManager
        if (securityManager != null) {
            int i = name.lastIndexOf('.');
            if (i >= 0) {
                try {
                    if (log.isTraceEnabled()) {
                        log.trace("      securityManager.checkPackageDefinition");
                    }
                    securityManager.checkPackageDefinition(name.substring(0,i));
                } catch (Exception se) {
                    if (log.isTraceEnabled()) {
                        log.trace("      -->Exception-->ClassNotFoundException", se);
                    }
                    throw new ClassNotFoundException(name, se);
                }
            }
        }

        // Ask our superclass to locate this class, if possible
        // (throws ClassNotFoundException if it is not found)
        Class<?> clazz = null;
        try {
            if (log.isTraceEnabled()) {
                log.trace("      findClassInternal(" + name + ")");
            }
            try {
                if (securityManager != null) {
                    PrivilegedAction<Class<?>> dp =
                        new PrivilegedFindClassByName(name);
                    clazz = AccessController.doPrivileged(dp);
                } else {
                    clazz = findClassInternal(name);
                }
            } catch(AccessControlException ace) {
                log.warn(sm.getString("webappClassLoader.securityException", name,
                        ace.getMessage()), ace);
                throw new ClassNotFoundException(name, ace);
            } catch (RuntimeException e) {
                if (log.isTraceEnabled()) {
                    log.trace("      -->RuntimeException Rethrown", e);
                }
                throw e;
            }
            if ((clazz == null) && hasExternalRepositories) {
                try {
                    clazz = super.findClass(name);
                } catch(AccessControlException ace) {
                    log.warn(sm.getString("webappClassLoader.securityException", name,
                            ace.getMessage()), ace);
                    throw new ClassNotFoundException(name, ace);
                } catch (RuntimeException e) {
                    if (log.isTraceEnabled()) {
                        log.trace("      -->RuntimeException Rethrown", e);
                    }
                    throw e;
                }
            }
            if (clazz == null) {
                if (log.isDebugEnabled()) {
                    log.debug("    --> Returning ClassNotFoundException");
                }
                throw new ClassNotFoundException(name);
            }
        } catch (ClassNotFoundException e) {
            if (log.isTraceEnabled()) {
                log.trace("    --> Passing on ClassNotFoundException");
            }
            throw e;
        }

        // Return the class we have located
        if (log.isTraceEnabled()) {
            log.debug("      Returning class " + clazz);
        }

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
        return clazz;

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

        if (log.isDebugEnabled()) {
            log.debug("    findResource(" + name + ")");
        }

        checkStateForResourceLoading(name);

        URL url = null;

        String path = nameToPath(name);

        WebResource resource = resources.getClassLoaderResource(path);
        if (resource.exists()) {
            url = resource.getURL();
            trackLastModified(path, resource);
        }

        if ((url == null) && hasExternalRepositories) {
            url = super.findResource(name);
        }

        if (log.isDebugEnabled()) {
            if (url != null) {
                log.debug("    --> Returning '" + url.toString() + "'");
            } else {
                log.debug("    --> Resource not found, returning null");
            }
        }
        return url;
    }


    private void trackLastModified(String path, WebResource resource) {
        if (resourceEntries.containsKey(path)) {
            return;
        }
        ResourceEntry entry = new ResourceEntry();
        entry.lastModified = resource.getLastModified();
        synchronized(resourceEntries) {
            resourceEntries.putIfAbsent(path, entry);
        }
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

        if (log.isDebugEnabled()) {
            log.debug("    findResources(" + name + ")");
        }

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

        if (log.isDebugEnabled()) {
            log.debug("getResource(" + name + ")");
        }

        checkStateForResourceLoading(name);

        URL url = null;

        boolean delegateFirst = delegate || filter(name, false);

        // (1) Delegate to parent if requested
        if (delegateFirst) {
            if (log.isDebugEnabled()) {
                log.debug("  Delegating to parent classloader " + parent);
            }
            url = parent.getResource(name);
            if (url != null) {
                if (log.isDebugEnabled()) {
                    log.debug("  --> Returning '" + url.toString() + "'");
                }
                return url;
            }
        }

        // (2) Search local repositories
        url = findResource(name);
        if (url != null) {
            if (log.isDebugEnabled()) {
                log.debug("  --> Returning '" + url.toString() + "'");
            }
            return url;
        }

        // (3) Delegate to parent unconditionally if not already attempted
        if (!delegateFirst) {
            url = parent.getResource(name);
            if (url != null) {
                if (log.isDebugEnabled()) {
                    log.debug("  --> Returning '" + url.toString() + "'");
                }
                return url;
            }
        }

        // (4) Resource was not found
        if (log.isDebugEnabled()) {
            log.debug("  --> Resource not found, returning null");
        }
        return null;

    }


    @Override
    public Enumeration<URL> getResources(String name) throws IOException {

        Enumeration<URL> parentResources = getParent().getResources(name);
        Enumeration<URL> localResources = findResources(name);

        // Need to combine these enumerations. The order in which the
        // Enumerations are combined depends on how delegation is configured
        boolean delegateFirst = delegate || filter(name, false);

        if (delegateFirst) {
            return new CombinedEnumeration(parentResources, localResources);
        } else {
            return new CombinedEnumeration(localResources, parentResources);
        }
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

        if (log.isDebugEnabled()) {
            log.debug("getResourceAsStream(" + name + ")");
        }

        checkStateForResourceLoading(name);

        InputStream stream = null;

        boolean delegateFirst = delegate || filter(name, false);

        // (1) Delegate to parent if requested
        if (delegateFirst) {
            if (log.isDebugEnabled()) {
                log.debug("  Delegating to parent classloader " + parent);
            }
            stream = parent.getResourceAsStream(name);
            if (stream != null) {
                if (log.isDebugEnabled()) {
                    log.debug("  --> Returning stream from parent");
                }
                return stream;
            }
        }

        // (2) Search local repositories
        if (log.isDebugEnabled()) {
            log.debug("  Searching local repositories");
        }
        String path = nameToPath(name);
        WebResource resource = resources.getClassLoaderResource(path);
        if (resource.exists()) {
            stream = resource.getInputStream();
            trackLastModified(path, resource);
        }
        try {
            if (hasExternalRepositories && stream == null) {
                URL url = super.findResource(name);
                if (url != null) {
                    stream = url.openStream();
                }
            }
        } catch (IOException e) {
            // Ignore
        }
        if (stream != null) {
            if (log.isDebugEnabled()) {
                log.debug("  --> Returning stream from local");
            }
            return stream;
        }

        // (3) Delegate to parent unconditionally
        if (!delegateFirst) {
            if (log.isDebugEnabled()) {
                log.debug("  Delegating to parent classloader unconditionally " + parent);
            }
            stream = parent.getResourceAsStream(name);
            if (stream != null) {
                if (log.isDebugEnabled()) {
                    log.debug("  --> Returning stream from parent");
                }
                return stream;
            }
        }

        // (4) Resource was not found
        if (log.isDebugEnabled()) {
            log.debug("  --> Resource not found, returning null");
        }
        return null;
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
        return loadClass(name, false);
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

        synchronized (JreCompat.isGraalAvailable() ? this : getClassLoadingLock(name)) {
            if (log.isDebugEnabled()) {
                log.debug("loadClass(" + name + ", " + resolve + ")");
            }
            Class<?> clazz = null;

            // Log access to stopped class loader
            checkStateForClassLoading(name);

            // (0) Check our previously loaded local class cache
            clazz = findLoadedClass0(name);
            if (clazz != null) {
                if (log.isDebugEnabled()) {
                    log.debug("  Returning class from cache");
                }
                if (resolve) {
                    resolveClass(clazz);
                }
                return clazz;
            }

            // (0.1) Check our previously loaded class cache
            clazz = JreCompat.isGraalAvailable() ? null : findLoadedClass(name);
            if (clazz != null) {
                if (log.isDebugEnabled()) {
                    log.debug("  Returning class from cache");
                }
                if (resolve) {
                    resolveClass(clazz);
                }
                return clazz;
            }

            // (0.2) Try loading the class with the system class loader, to prevent
            //       the webapp from overriding Java SE classes. This implements
            //       SRV.10.7.2
            String resourceName = binaryNameToPath(name, false);

            ClassLoader javaseLoader = getJavaseClassLoader();
            boolean tryLoadingFromJavaseLoader;
            try {
                // Use getResource as it won't trigger an expensive
                // ClassNotFoundException if the resource is not available from
                // the Java SE class loader. However (see
                // https://bz.apache.org/bugzilla/show_bug.cgi?id=58125 for
                // details) when running under a security manager in rare cases
                // this call may trigger a ClassCircularityError.
                // See https://bz.apache.org/bugzilla/show_bug.cgi?id=61424 for
                // details of how this may trigger a StackOverflowError
                // Given these reported errors, catch Throwable to ensure any
                // other edge cases are also caught
                URL url;
                if (securityManager != null) {
                    PrivilegedAction<URL> dp = new PrivilegedJavaseGetResource(resourceName);
                    url = AccessController.doPrivileged(dp);
                } else {
                    url = javaseLoader.getResource(resourceName);
                }
                tryLoadingFromJavaseLoader = (url != null);
            } catch (Throwable t) {
                // Swallow all exceptions apart from those that must be re-thrown
                ExceptionUtils.handleThrowable(t);
                // The getResource() trick won't work for this class. We have to
                // try loading it directly and accept that we might get a
                // ClassNotFoundException.
                tryLoadingFromJavaseLoader = true;
            }

            if (tryLoadingFromJavaseLoader) {
                try {
                    clazz = javaseLoader.loadClass(name);
                    if (clazz != null) {
                        if (resolve) {
                            resolveClass(clazz);
                        }
                        return clazz;
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
                        String error = sm.getString("webappClassLoader.restrictedPackage", name);
                        log.info(error, se);
                        throw new ClassNotFoundException(error, se);
                    }
                }
            }

            boolean delegateLoad = delegate || filter(name, true);

            // (1) Delegate to our parent if requested
            if (delegateLoad) {
                if (log.isDebugEnabled()) {
                    log.debug("  Delegating to parent classloader1 " + parent);
                }
                try {
                    clazz = Class.forName(name, false, parent);
                    if (clazz != null) {
                        if (log.isDebugEnabled()) {
                            log.debug("  Loading class from parent");
                        }
                        if (resolve) {
                            resolveClass(clazz);
                        }
                        return clazz;
                    }
                } catch (ClassNotFoundException e) {
                    // Ignore
                }
            }

            // (2) Search local repositories
            if (log.isDebugEnabled()) {
                log.debug("  Searching local repositories");
            }
            try {
                clazz = findClass(name);
                if (clazz != null) {
                    if (log.isDebugEnabled()) {
                        log.debug("  Loading class from local repository");
                    }
                    if (resolve) {
                        resolveClass(clazz);
                    }
                    return clazz;
                }
            } catch (ClassNotFoundException e) {
                // Ignore
            }

            // (3) Delegate to parent unconditionally
            if (!delegateLoad) {
                if (log.isDebugEnabled()) {
                    log.debug("  Delegating to parent classloader at end: " + parent);
                }
                try {
                    clazz = Class.forName(name, false, parent);
                    if (clazz != null) {
                        if (log.isDebugEnabled()) {
                            log.debug("  Loading class from parent");
                        }
                        if (resolve) {
                            resolveClass(clazz);
                        }
                        return clazz;
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
            throw new ClassNotFoundException(ise.getMessage(), ise);
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
     * add read FilePermission for the appropriate resources.
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
                for (Permission p : permissionList) {
                    pc.add(p);
                }
                loaderPC.put(codeUrl,pc);
            }
        }
        return pc;
    }


    @Override
    public boolean check(Permission permission) {
        if (!Globals.IS_SECURITY_ENABLED) {
            return true;
        }
        Policy currentPolicy = Policy.getPolicy();
        if (currentPolicy != null) {
            URL contextRootUrl = resources.getResource("/").getCodeBase();
            CodeSource cs = new CodeSource(contextRootUrl, (Certificate[]) null);
            PermissionCollection pc = currentPolicy.getPermissions(cs);
            if (pc.implies(permission)) {
                return true;
            }
        }
        return false;
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
        return result.toArray(new URL[0]);
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

        WebResource[] classesResources = resources.getResources("/WEB-INF/classes");
        for (WebResource classes : classesResources) {
            if (classes.isDirectory() && classes.canRead()) {
                localRepositories.add(classes.getURL());
            }
        }
        WebResource[] jars = resources.listResources("/WEB-INF/lib");
        for (WebResource jar : jars) {
            if (jar.getName().endsWith(".jar") && jar.isFile() && jar.canRead()) {
                localRepositories.add(jar.getURL());
                jarModificationTimes.put(
                        jar.getName(), Long.valueOf(jar.getLastModified()));
            }
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

        // If the JVM is shutting down, skip the memory leak checks
        if (skipMemoryLeakChecksOnJvmShutdown
            && !resources.getContext().getParent().getState().isAvailable()) {
            // During reloading / redeployment the parent is expected to be
            // available. Parent is not available so this might be a JVM
            // shutdown.
            try {
                Thread dummyHook = new Thread();
                Runtime.getRuntime().addShutdownHook(dummyHook);
                Runtime.getRuntime().removeShutdownHook(dummyHook);
            } catch (IllegalStateException ise) {
                return;
            }
        }

        if (!JreCompat.isGraalAvailable()) {
            // De-register any remaining JDBC drivers
            clearReferencesJdbc();
        }

        // Stop any threads the web application started
        clearReferencesThreads();

        // Clear any references retained in the serialization caches
        if (clearReferencesObjectStreamClassCaches && !JreCompat.isGraalAvailable()) {
            clearReferencesObjectStreamClassCaches();
        }

        // Check for leaks triggered by ThreadLocals loaded by this class loader
        if (clearReferencesThreadLocals && !JreCompat.isGraalAvailable()) {
            checkThreadLocalsForLeaks();
        }

        // Clear RMI Targets loaded by this class loader
        if (clearReferencesRmiTargets) {
            clearReferencesRmiTargets();
        }

         // Clear the IntrospectionUtils cache.
        IntrospectionUtils.clear();

        // Clear the classloader reference in common-logging
        if (clearReferencesLogFactoryRelease) {
            org.apache.juli.logging.LogFactory.release(this);
        }

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
            Object obj = lpClass.getConstructor().newInstance();
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


    @SuppressWarnings("deprecation") // thread.stop()
    private void clearReferencesThreads() {
        Thread[] threads = getThreads();
        List<Thread> threadsToStop = new ArrayList<>();

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
                    if (tg != null && JVM_THREAD_GROUP_NAMES.contains(tg.getName())) {
                        // HttpClient keep-alive threads
                        if (clearReferencesHttpClientKeepAliveThread &&
                                threadName.equals("Keep-Alive-Timer")) {
                            thread.setContextClassLoader(parent);
                            log.debug(sm.getString("webappClassLoader.checkThreadsHttpClient"));
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

                    // Don't try and stop the threads unless explicitly
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
                        for (String fieldName : new String[] { "target", "runnable", "action" }) {
                            try {
                                Field targetField = thread.getClass().getDeclaredField(fieldName);
                                targetField.setAccessible(true);
                                target = targetField.get(thread);
                                break;
                            } catch (NoSuchFieldException nfe) {
                                continue;
                            }
                        }

                        // "java.util.concurrent" code is in public domain,
                        // so all implementations are similar
                        if (target != null && target.getClass().getCanonicalName() != null &&
                                target.getClass().getCanonicalName().equals(
                                        "java.util.concurrent.ThreadPoolExecutor.Worker")) {
                            Field executorField = target.getClass().getDeclaredField("this$0");
                            executorField.setAccessible(true);
                            Object executor = executorField.get(target);
                            if (executor instanceof ThreadPoolExecutor) {
                                ((ThreadPoolExecutor) executor).shutdownNow();
                                usingExecutor = true;
                            }
                        }
                    } catch (NoSuchFieldException | IllegalAccessException | RuntimeException e) {
                        // InaccessibleObjectException is only available in Java 9+,
                        // swapped for RuntimeException
                        log.warn(sm.getString("webappClassLoader.stopThreadFail",
                                thread.getName(), getContextName()), e);
                    }

                    // Stopping an executor automatically interrupts the
                    // associated threads. For non-executor threads, interrupt
                    // them here.
                    if (!usingExecutor && !thread.isInterrupted()) {
                        thread.interrupt();
                    }

                    // Threads are expected to take a short time to stop after
                    // being interrupted. Make a note of all threads that are
                    // expected to stop to enable them to be checked at the end
                    // of this method.
                    threadsToStop.add(thread);
                }
            }
        }

        // If thread stopping is enabled, threads should have been stopped above
        // when the executor was shut down or the thread was interrupted but
        // that depends on the thread correctly handling the interrupt. Check
        // each thread and if any are still running give all threads up to a
        // total of 2 seconds to shutdown.
        int count = 0;
        for (Thread t : threadsToStop) {
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
                    // In case queue was already empty. Should only be one
                    // thread waiting but use notifyAll() to be safe.
                    queue.notifyAll();
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

            for (Thread thread : threads) {
                Object threadLocalMap;
                if (thread != null) {

                    // Clear the first map
                    threadLocalMap = threadLocalsField.get(thread);
                    if (null != threadLocalMap) {
                        expungeStaleEntriesMethod.invoke(threadLocalMap);
                        checkThreadLocalMapForLeaks(threadLocalMap, tableField);
                    }

                    // Clear the second map
                    threadLocalMap = inheritableThreadLocalsField.get(thread);
                    if (null != threadLocalMap) {
                        expungeStaleEntriesMethod.invoke(threadLocalMap);
                        checkThreadLocalMapForLeaks(threadLocalMap, tableField);
                    }
                }
            }
        } catch (Throwable t) {
            JreCompat jreCompat = JreCompat.getInstance();
            if (jreCompat.isInstanceOfInaccessibleObjectException(t)) {
                // Must be running on Java 9 without the necessary command line
                // options.
                String currentModule = JreCompat.getInstance().getModuleName(this.getClass());
                log.warn(sm.getString("webappClassLoader.addExportsThreadLocal", currentModule));
            } else {
                ExceptionUtils.handleThrowable(t);
                log.warn(sm.getString(
                        "webappClassLoader.checkThreadLocalsForLeaksFail",
                        getContextName()), t);
            }
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
                for (Object obj : table) {
                    if (obj != null) {
                        boolean keyLoadedByWebapp = false;
                        boolean valueLoadedByWebapp = false;
                        // Check the key
                        Object key = ((Reference<?>) obj).get();
                        if (this.equals(key) || loadedByThisOrChild(key)) {
                            keyLoadedByWebapp = true;
                        }
                        // Check the value
                        Field valueField =
                                obj.getClass().getDeclaredField("value");
                        valueField.setAccessible(true);
                        Object value = valueField.get(obj);
                        if (this.equals(value) || loadedByThisOrChild(value)) {
                            valueLoadedByWebapp = true;
                        }
                        if (keyLoadedByWebapp || valueLoadedByWebapp) {
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
                            if (valueLoadedByWebapp) {
                                log.error(sm.getString(
                                        "webappClassLoader.checkThreadLocalsForLeaks",
                                        args));
                            } else if (value == null) {
                                if (log.isDebugEnabled()) {
                                    log.debug(sm.getString(
                                            "webappClassLoader.checkThreadLocalsForLeaksNull",
                                            args));
                                }
                            } else {
                                if (log.isDebugEnabled()) {
                                    log.debug(sm.getString(
                                            "webappClassLoader.checkThreadLocalsForLeaksNone",
                                            args));
                                }
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
            // Need access to the ccl field of sun.rmi.transport.Target to find
            // the leaks
            Class<?> objectTargetClass =
                Class.forName("sun.rmi.transport.Target");
            Field cclField = objectTargetClass.getDeclaredField("ccl");
            cclField.setAccessible(true);
            // Need access to the stub field to report the leaks
            Field stubField = objectTargetClass.getDeclaredField("stub");
            stubField.setAccessible(true);

            // Clear the objTable map
            Class<?> objectTableClass = Class.forName("sun.rmi.transport.ObjectTable");
            Field objTableField = objectTableClass.getDeclaredField("objTable");
            objTableField.setAccessible(true);
            Object objTable = objTableField.get(null);
            if (objTable == null) {
                return;
            }
            Field tableLockField = objectTableClass.getDeclaredField("tableLock");
            tableLockField.setAccessible(true);
            Object tableLock = tableLockField.get(null);

            synchronized (tableLock) {
                // Iterate over the values in the table
                if (objTable instanceof Map<?,?>) {
                    Iterator<?> iter = ((Map<?,?>) objTable).values().iterator();
                    while (iter.hasNext()) {
                        Object obj = iter.next();
                        Object cclObject = cclField.get(obj);
                        if (this == cclObject) {
                            iter.remove();
                            Object stubObject = stubField.get(obj);
                            log.error(sm.getString("webappClassLoader.clearRmi",
                                    stubObject.getClass().getName(), stubObject));
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
            }
        } catch (ClassNotFoundException e) {
            log.info(sm.getString("webappClassLoader.clearRmiInfo",
                    getContextName()), e);
        } catch (SecurityException | NoSuchFieldException | IllegalArgumentException |
                IllegalAccessException e) {
            log.warn(sm.getString("webappClassLoader.clearRmiFail",
                    getContextName()), e);
        } catch (Exception e) {
            JreCompat jreCompat = JreCompat.getInstance();
            if (jreCompat.isInstanceOfInaccessibleObjectException(e)) {
                // Must be running on Java 9 without the necessary command line
                // options.
                String currentModule = JreCompat.getInstance().getModuleName(this.getClass());
                log.warn(sm.getString("webappClassLoader.addExportsRmi", currentModule));
            } else {
                // Re-throw all other exceptions
                throw e;
            }
        }
    }


    private void clearReferencesObjectStreamClassCaches() {
        try {
            Class<?> clazz = Class.forName("java.io.ObjectStreamClass$Caches");
            clearCache(clazz, "localDescs");
            clearCache(clazz, "reflectors");
        } catch (ReflectiveOperationException | SecurityException | ClassCastException e) {
            log.warn(sm.getString(
                    "webappClassLoader.clearObjectStreamClassCachesFail", getContextName()), e);
        } catch (Exception e) {
            JreCompat jreCompat = JreCompat.getInstance();
            if (jreCompat.isInstanceOfInaccessibleObjectException(e)) {
                // Must be running on Java 9 without the necessary command line
                // options.
                String currentModule = JreCompat.getInstance().getModuleName(this.getClass());
                log.warn(sm.getString("webappClassLoader.addExportsJavaIo", currentModule));
                return;
            } else {
                // Re-throw all other exceptions
                throw e;
            }
        }
    }


    private void clearCache(Class<?> target, String mapName)
            throws ReflectiveOperationException, SecurityException, ClassCastException {
        Field f = target.getDeclaredField(mapName);
        f.setAccessible(true);
        Map<?,?> map = (Map<?,?>) f.get(null);
        Iterator<?> keys = map.keySet().iterator();
        while (keys.hasNext()) {
            Object key = keys.next();
            if (key instanceof Reference) {
                Object clazz = ((Reference<?>) key).get();
                if (loadedByThisOrChild(clazz)) {
                    keys.remove();
                }
            }
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

        checkStateForResourceLoading(name);

        if (name == null) {
            return null;
        }
        String path = binaryNameToPath(name, true);

        ResourceEntry entry = resourceEntries.get(path);
        WebResource resource = null;

        if (entry == null) {
            resource = resources.getClassLoaderResource(path);

            if (!resource.exists()) {
                return null;
            }

            entry = new ResourceEntry();
            entry.lastModified = resource.getLastModified();

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
        }

        Class<?> clazz = entry.loadedClass;
        if (clazz != null) {
            return clazz;
        }

        synchronized (JreCompat.isGraalAvailable() ? this : getClassLoadingLock(name)) {
            clazz = entry.loadedClass;
            if (clazz != null) {
                return clazz;
            }

            if (resource == null) {
                resource = resources.getClassLoaderResource(path);
            }

            if (!resource.exists()) {
                return null;
            }

            byte[] binaryContent = resource.getContent();
            if (binaryContent == null) {
                // Something went wrong reading the class bytes (and will have
                // been logged at debug level).
                return null;
            }
            Manifest manifest = resource.getManifest();
            URL codeBase = resource.getCodeBase();
            Certificate[] certificates = resource.getCertificates();

            if (transformers.size() > 0) {
                // If the resource is a class just being loaded, decorate it
                // with any attached transformers

                // Ignore leading '/' and trailing CLASS_FILE_SUFFIX
                // Should be cheaper than replacing '.' by '/' in class name.
                String internalName = path.substring(1, path.length() - CLASS_FILE_SUFFIX.length());

                for (ClassFileTransformer transformer : this.transformers) {
                    try {
                        byte[] transformed = transformer.transform(
                                this, internalName, null, null, binaryContent);
                        if (transformed != null) {
                            binaryContent = transformed;
                        }
                    } catch (IllegalClassFormatException e) {
                        log.error(sm.getString("webappClassLoader.transformError", name), e);
                        return null;
                    }
                }
            }

            // Looking up the package
            String packageName = null;
            int pos = name.lastIndexOf('.');
            if (pos != -1) {
                packageName = name.substring(0, pos);
            }

            Package pkg = null;

            if (packageName != null) {
                pkg = getPackage(packageName);
                // Define the package (if null)
                if (pkg == null) {
                    try {
                        if (manifest == null) {
                            definePackage(packageName, null, null, null, null, null, null, null);
                        } else {
                            definePackage(packageName, manifest, codeBase);
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
                        sealCheck = pkg.isSealed(codeBase);
                    } else {
                        sealCheck = (manifest == null) || !isPackageSealed(packageName, manifest);
                    }
                    if (!sealCheck) {
                        throw new SecurityException
                            ("Sealing violation loading " + name + " : Package "
                             + packageName + " is sealed.");
                    }
                }

            }

            try {
                clazz = defineClass(name, binaryContent, 0,
                        binaryContent.length, new CodeSource(codeBase, certificates));
            } catch (UnsupportedClassVersionError ucve) {
                throw new UnsupportedClassVersionError(
                        ucve.getLocalizedMessage() + " " +
                        sm.getString("webappClassLoader.wrongVersion",
                                name));
            }
            entry.loadedClass = clazz;
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
     * Returns true if the specified package name is sealed according to the
     * given manifest.
     *
     * @param name Path name to check
     * @param man Associated manifest
     * @return <code>true</code> if the manifest associated says it is sealed
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
     * Finds the class with the given name if it has previously been
     * loaded and cached by this class loader, and return the Class object.
     * If this class has not been cached, return <code>null</code>.
     *
     * @param name The binary name of the resource to return
     * @return a loaded class
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
     * @param isClassName <code>true</code> if name is a class name,
     *                <code>false</code> if name is a resource name
     * @return <code>true</code> if the class should be filtered
     */
    protected boolean filter(String name, boolean isClassName) {

        if (name == null) {
            return false;
        }

        char ch;
        if (name.startsWith("javax")) {
            /* 5 == length("javax") */
            if (name.length() == 5) {
                return false;
            }
            ch = name.charAt(5);
            if (isClassName && ch == '.') {
                /* 6 == length("javax.") */
                if (name.startsWith("servlet.jsp.jstl.", 6)) {
                    return false;
                }
                if (name.startsWith("el.", 6) ||
                    name.startsWith("servlet.", 6) ||
                    name.startsWith("websocket.", 6) ||
                    name.startsWith("security.auth.message.", 6)) {
                    return true;
                }
            } else if (!isClassName && ch == '/') {
                /* 6 == length("javax/") */
                if (name.startsWith("servlet/jsp/jstl/", 6)) {
                    return false;
                }
                if (name.startsWith("el/", 6) ||
                    name.startsWith("servlet/", 6) ||
                    name.startsWith("websocket/", 6) ||
                    name.startsWith("security/auth/message/", 6)) {
                    return true;
                }
            }
        } else if (name.startsWith("org")) {
            /* 3 == length("org") */
            if (name.length() == 3) {
                return false;
            }
            ch = name.charAt(3);
            if (isClassName && ch == '.') {
                /* 4 == length("org.") */
                if (name.startsWith("apache.", 4)) {
                    /* 11 == length("org.apache.") */
                    if (name.startsWith("tomcat.jdbc.", 11)) {
                        return false;
                    }
                    if (name.startsWith("el.", 11) ||
                        name.startsWith("catalina.", 11) ||
                        name.startsWith("jasper.", 11) ||
                        name.startsWith("juli.", 11) ||
                        name.startsWith("tomcat.", 11) ||
                        name.startsWith("naming.", 11) ||
                        name.startsWith("coyote.", 11)) {
                        return true;
                    }
                }
            } else if (!isClassName && ch == '/') {
                /* 4 == length("org/") */
                if (name.startsWith("apache/", 4)) {
                    /* 11 == length("org/apache/") */
                    if (name.startsWith("tomcat/jdbc/", 11)) {
                        return false;
                    }
                    if (name.startsWith("el/", 11) ||
                        name.startsWith("catalina/", 11) ||
                        name.startsWith("jasper/", 11) ||
                        name.startsWith("juli/", 11) ||
                        name.startsWith("tomcat/", 11) ||
                        name.startsWith("naming/", 11) ||
                        name.startsWith("coyote/", 11)) {
                        return true;
                    }
                }
            }
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


    @Override
    public boolean hasLoggingConfig() {
        if (Globals.IS_SECURITY_ENABLED) {
            Boolean result = AccessController.doPrivileged(new PrivilegedHasLoggingConfig());
            return result.booleanValue();
        } else {
            return findResource("logging.properties") != null;
        }
    }


    private class PrivilegedHasLoggingConfig implements PrivilegedAction<Boolean> {

        @Override
        public Boolean run() {
            return Boolean.valueOf(findResource("logging.properties") != null);
        }
    }


    private static class CombinedEnumeration implements Enumeration<URL> {

        private final Enumeration<URL>[] sources;
        private int index = 0;

        public CombinedEnumeration(Enumeration<URL> enum1, Enumeration<URL> enum2) {
            @SuppressWarnings("unchecked")
            Enumeration<URL>[] sources = new Enumeration[] { enum1, enum2 };
            this.sources = sources;
        }


        @Override
        public boolean hasMoreElements() {
            return inc();
        }


        @Override
        public URL nextElement() {
            if (inc()) {
                return sources[index].nextElement();
            }
            throw new NoSuchElementException();
        }


        private boolean inc() {
            while (index < sources.length) {
                if (sources[index].hasMoreElements()) {
                    return true;
                }
                index++;
            }
            return false;
        }
    }
}

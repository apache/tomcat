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

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;

import javax.management.ObjectName;

import jakarta.servlet.ServletContext;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Loader;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.catalina.util.ToStringUtil;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jakartaee.ClassConverter;
import org.apache.tomcat.jakartaee.EESpecProfile;
import org.apache.tomcat.jakartaee.EESpecProfiles;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.UDecoder;
import org.apache.tomcat.util.compat.JreCompat;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.res.StringManager;

/**
 * Classloader implementation which is specialized for handling web
 * applications in the most efficient way, while being Catalina aware (all
 * accesses to resources are made through
 * {@link org.apache.catalina.WebResourceRoot}).
 * This class loader supports detection of modified
 * Java classes, which can be used to implement auto-reload support.
 * <p>
 * This class loader is configured via the Resources children of its Context
 * prior to calling <code>start()</code>.  When a new class is required,
 * these Resources will be consulted first to locate the class.  If it
 * is not present, the system class loader will be used instead.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
public class WebappLoader extends LifecycleMBeanBase implements Loader{

    private static final Log log = LogFactory.getLog(WebappLoader.class);

    // ----------------------------------------------------- Instance Variables

    /**
     * The class loader being managed by this Loader component.
     */
    private WebappClassLoaderBase classLoader = null;


    /**
     * The Context with which this Loader has been associated.
     */
    private Context context = null;


    /**
     * The "follow standard delegation model" flag that will be used to
     * configure our ClassLoader.
     */
    private boolean delegate = false;


    /**
     * The profile name which will be used by the converter, or null if not used.
     * Any invalid profile value will default to the TOMCAT profile, which
     * converts all packages used by Tomcat.
     */
    private String jakartaConverter = null;


    /**
     * The Java class name of the ClassLoader implementation to be used.
     * This class should extend WebappClassLoaderBase, otherwise, a different
     * loader implementation must be used.
     */
    private String loaderClass = ParallelWebappClassLoader.class.getName();


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(WebappLoader.class);


    /**
     * The property change support for this component.
     */
    protected final PropertyChangeSupport support = new PropertyChangeSupport(this);


    /**
     * Classpath set in the loader.
     */
    private String classpath = null;


    // ------------------------------------------------------------- Properties

    /**
     * Return the Java class loader to be used by this Container.
     */
    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }


    @Override
    public Context getContext() {
        return context;
    }


    @Override
    public void setContext(Context context) {

        if (this.context == context) {
            return;
        }

        if (getState().isAvailable()) {
            throw new IllegalStateException(sm.getString("webappLoader.setContext.ise"));
        }

        // Process this property change
        Context oldContext = this.context;
        this.context = context;
        support.firePropertyChange("context", oldContext, this.context);
    }


    /**
     * Return the "follow standard delegation model" flag used to configure
     * our ClassLoader.
     */
    @Override
    public boolean getDelegate() {
        return this.delegate;
    }


    /**
     * Set the "follow standard delegation model" flag used to configure
     * our ClassLoader.
     *
     * @param delegate The new flag
     */
    @Override
    public void setDelegate(boolean delegate) {
        boolean oldDelegate = this.delegate;
        this.delegate = delegate;
        support.firePropertyChange("delegate", Boolean.valueOf(oldDelegate),
                                   Boolean.valueOf(this.delegate));
    }


    /**
     * @return a non null String if the loader will attempt to use the
     *  Jakarta converter. The String is the name of the profile
     *  used for conversion.
     */
    public String getJakartaConverter() {
        return jakartaConverter;
    }


    /**
     * Set the Jakarta converter.
     *
     * @param jakartaConverter The profile name which will be used by the converter
     *   Any invalid profile value will default to the TOMCAT profile, which
     *   converts all packages used by Tomcat.
     */
    public void setJakartaConverter(String jakartaConverter) {
        String oldJakartaConverter = this.jakartaConverter;
        this.jakartaConverter = jakartaConverter;
        support.firePropertyChange("jakartaConverter",
                oldJakartaConverter,
                this.jakartaConverter);
    }


    /**
     * @return the ClassLoader class name.
     */
    public String getLoaderClass() {
        return this.loaderClass;
    }


    /**
     * Set the ClassLoader class name.
     *
     * @param loaderClass The new ClassLoader class name
     */
    public void setLoaderClass(String loaderClass) {
        this.loaderClass = loaderClass;
    }

    /**
     * Set the ClassLoader instance, without relying on reflection
     * This method will also invoke {@link #setLoaderClass(String)} with
     * {@code loaderInstance.getClass().getName()} as an argument
     *
     * @param loaderInstance The new ClassLoader instance to use
     */
    public void setLoaderInstance(WebappClassLoaderBase loaderInstance) {
        this.classLoader = loaderInstance;
        setLoaderClass(loaderInstance.getClass().getName());
    }

    // --------------------------------------------------------- Public Methods

    /**
     * Add a property change listener to this component.
     *
     * @param listener The listener to add
     */
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {

        support.addPropertyChangeListener(listener);

    }


    /**
     * Execute a periodic task, such as reloading, etc. This method will be
     * invoked inside the classloading context of this container. Unexpected
     * throwables will be caught and logged.
     */
    @Override
    public void backgroundProcess() {
        Context context = getContext();
        if (context != null) {
            if (context.getReloadable() && modified()) {
                ClassLoader originalTccl = Thread.currentThread().getContextClassLoader();
                try {
                    Thread.currentThread().setContextClassLoader(WebappLoader.class.getClassLoader());
                    context.reload();
                } finally {
                    Thread.currentThread().setContextClassLoader(originalTccl);
                }
            }
        }
    }


    public String[] getLoaderRepositories() {
        if (classLoader == null) {
            return new String[0];
        }
        URL[] urls = classLoader.getURLs();
        String[] result = new String[urls.length];
        for (int i = 0; i < urls.length; i++) {
            result[i] = urls[i].toExternalForm();
        }
        return result;
    }

    public String getLoaderRepositoriesString() {
        String repositories[]=getLoaderRepositories();
        StringBuilder sb=new StringBuilder();
        for (String repository : repositories) {
            sb.append(repository).append(':');
        }
        return sb.toString();
    }


    /**
     * Classpath, as set in org.apache.catalina.jsp_classpath context
     * property
     *
     * @return The classpath
     */
    public String getClasspath() {
        return classpath;
    }


    /**
     * Has the internal repository associated with this Loader been modified,
     * such that the loaded classes should be reloaded?
     */
    @Override
    public boolean modified() {
        return classLoader != null ? classLoader.modified() : false ;
    }


    /**
     * Remove a property change listener from this component.
     *
     * @param listener The listener to remove
     */
    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }


    /**
     * Return a String representation of this component.
     */
    @Override
    public String toString() {
        return ToStringUtil.toString(this, context);
    }


    /**
     * Start associated {@link ClassLoader} and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected void startInternal() throws LifecycleException {

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("webappLoader.starting"));
        }

        if (context.getResources() == null) {
            log.info(sm.getString("webappLoader.noResources", context));
            setState(LifecycleState.STARTING);
            return;
        }

        // Construct a class loader based on our current repositories list
        try {

            classLoader = createClassLoader();
            classLoader.setResources(context.getResources());
            classLoader.setDelegate(this.delegate);

            // Set Jakarta class converter
            if (getJakartaConverter() != null) {
                MigrationUtil.addJakartaEETransformer(classLoader, getJakartaConverter());
            }

            // Configure our repositories
            setClassPath();

            setPermissions();

            classLoader.start();

            String contextName = context.getName();
            if (!contextName.startsWith("/")) {
                contextName = "/" + contextName;
            }
            ObjectName cloname = new ObjectName(context.getDomain() + ":type=" +
                    classLoader.getClass().getSimpleName() + ",host=" +
                    context.getParent().getName() + ",context=" + contextName);
            Registry.getRegistry(null, null)
                .registerComponent(classLoader, cloname, null);

        } catch (Throwable t) {
            t = ExceptionUtils.unwrapInvocationTargetException(t);
            ExceptionUtils.handleThrowable(t);
            throw new LifecycleException(sm.getString("webappLoader.startError"), t);
        }

        setState(LifecycleState.STARTING);
    }


    /**
     * Stop associated {@link ClassLoader} and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected void stopInternal() throws LifecycleException {

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("webappLoader.stopping"));
        }

        setState(LifecycleState.STOPPING);

        // Remove context attributes as appropriate
        ServletContext servletContext = context.getServletContext();
        servletContext.removeAttribute(Globals.CLASS_PATH_ATTR);

        // Throw away our current class loader if any
        if (classLoader != null) {
            try {
                classLoader.stop();
            } finally {
                classLoader.destroy();
            }

            // classLoader must be non-null to have been registered
            try {
                String contextName = context.getName();
                if (!contextName.startsWith("/")) {
                    contextName = "/" + contextName;
                }
                ObjectName cloname = new ObjectName(context.getDomain() + ":type=" +
                        classLoader.getClass().getSimpleName() + ",host=" +
                        context.getParent().getName() + ",context=" + contextName);
                Registry.getRegistry(null, null).unregisterComponent(cloname);
            } catch (Exception e) {
                log.warn(sm.getString("webappLoader.stopError"), e);
            }
        }


        classLoader = null;
    }


    // ------------------------------------------------------- Private Methods

    /**
     * Create associated classLoader.
     */
    private WebappClassLoaderBase createClassLoader()
        throws Exception {

        if (classLoader != null) {
            return classLoader;
        }

        if (ParallelWebappClassLoader.class.getName().equals(loaderClass)) {
            return new ParallelWebappClassLoader(context.getParentClassLoader());
        }

        Class<?> clazz = Class.forName(loaderClass);
        WebappClassLoaderBase classLoader = null;

        ClassLoader parentClassLoader = context.getParentClassLoader();

        Class<?>[] argTypes = { ClassLoader.class };
        Object[] args = { parentClassLoader };
        Constructor<?> constr = clazz.getConstructor(argTypes);
        classLoader = (WebappClassLoaderBase) constr.newInstance(args);

        return classLoader;
    }


    /**
     * Configure associated class loader permissions.
     */
    private void setPermissions() {

        if (!Globals.IS_SECURITY_ENABLED) {
            return;
        }
        if (context == null) {
            return;
        }

        // Tell the class loader the root of the context
        ServletContext servletContext = context.getServletContext();

        // Assigning permissions for the work directory
        File workDir =
            (File) servletContext.getAttribute(ServletContext.TEMPDIR);
        if (workDir != null) {
            try {
                String workDirPath = workDir.getCanonicalPath();
                classLoader.addPermission
                    (new FilePermission(workDirPath, "read,write"));
                classLoader.addPermission
                    (new FilePermission(workDirPath + File.separator + "-",
                                        "read,write,delete"));
            } catch (IOException e) {
                // Ignore
            }
        }

        for (URL url : context.getResources().getBaseUrls()) {
           classLoader.addPermission(url);
        }
    }


    /**
     * Set the appropriate context attribute for our class path.  This
     * is required only because Jasper depends on it.
     */
    private void setClassPath() {

        // Validate our current state information
        if (context == null) {
            return;
        }
        ServletContext servletContext = context.getServletContext();
        if (servletContext == null) {
            return;
        }

        StringBuilder classpath = new StringBuilder();

        // Assemble the class path information from our class loader chain
        ClassLoader loader = getClassLoader();

        if (delegate && loader != null) {
            // Skip the webapp loader for now as delegation is enabled
            loader = loader.getParent();
        }

        while (loader != null) {
            if (!buildClassPath(classpath, loader)) {
                break;
            }
            loader = loader.getParent();
        }

        if (delegate) {
            // Delegation was enabled, go back and add the webapp paths
            loader = getClassLoader();
            if (loader != null) {
                buildClassPath(classpath, loader);
            }
        }

        this.classpath = classpath.toString();

        // Store the assembled class path as a servlet context attribute
        servletContext.setAttribute(Globals.CLASS_PATH_ATTR, this.classpath);
    }


    private boolean buildClassPath(StringBuilder classpath, ClassLoader loader) {
        if (loader instanceof URLClassLoader) {
            URL repositories[] = ((URLClassLoader) loader).getURLs();
            for (URL url : repositories) {
                String repository = url.toString();
                if (repository.startsWith("file://")) {
                    repository = UDecoder.URLDecode(repository.substring(7), StandardCharsets.UTF_8);
                } else if (repository.startsWith("file:")) {
                    repository = UDecoder.URLDecode(repository.substring(5), StandardCharsets.UTF_8);
                } else {
                    continue;
                }
                if (repository == null) {
                    continue;
                }
                if (classpath.length() > 0) {
                    classpath.append(File.pathSeparator);
                }
                classpath.append(repository);
            }
        } else if (loader == ClassLoader.getSystemClassLoader()){
            // From Java 9 the internal class loaders no longer extend
            // URLCLassLoader
            String cp = System.getProperty("java.class.path");
            if (cp != null && cp.length() > 0) {
                if (classpath.length() > 0) {
                    classpath.append(File.pathSeparator);
                }
                classpath.append(cp);
            }
            return false;
        } else {
            // Ignore Graal "unknown" classloader
            if (!JreCompat.isGraalAvailable()) {
                log.info(sm.getString("webappLoader.unknownClassLoader", loader, loader.getClass()));
            }
            return false;
        }
        return true;
    }

    @Override
    protected String getDomainInternal() {
        return context.getDomain();
    }


    @Override
    protected String getObjectNameKeyProperties() {

        StringBuilder name = new StringBuilder("type=Loader");

        name.append(",host=");
        name.append(context.getParent().getName());

        name.append(",context=");

        String contextName = context.getName();
        if (!contextName.startsWith("/")) {
            name.append('/');
        }
        name.append(contextName);

        return name.toString();
    }


    /*
     * Implemented in a sub-class so EESpecProfile and EESpecProfiles are not
     * loaded unless a profile is configured. Otherwise, tomcat-embed-core.jar
     * has a runtime dependency on the migration tool whether it is used or not.
     */
    private static class MigrationUtil {

        public static void addJakartaEETransformer(WebappClassLoaderBase webappClassLoader, String profileName) {
            EESpecProfile profile = null;
            try {
                profile = EESpecProfiles.valueOf(profileName);
            } catch (IllegalArgumentException ignored) {
                // Use default value
                log.warn(sm.getString("webappLoader.unknownProfile", profileName));
            }
            webappClassLoader.addTransformer((profile != null) ? new ClassConverter(profile) : new ClassConverter());
        }
    }
}

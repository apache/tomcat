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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;

import javax.management.ObjectName;
import javax.servlet.ServletContext;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Loader;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.catalina.util.ToStringUtil;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.UDecoder;
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
public class WebappLoader extends LifecycleMBeanBase
    implements Loader, PropertyChangeListener {


    // ----------------------------------------------------------- Constructors

    /**
     * Construct a new WebappLoader with no defined parent class loader
     * (so that the actual parent will be the system class loader).
     */
    public WebappLoader() {
        this(null);
    }


    /**
     * Construct a new WebappLoader with the specified class loader
     * to be defined as the parent of the ClassLoader we ultimately create.
     *
     * @param parent The parent class loader
     */
    public WebappLoader(ClassLoader parent) {
        super();
        this.parentClassLoader = parent;
    }


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
     * The Java class name of the ClassLoader implementation to be used.
     * This class should extend WebappClassLoaderBase, otherwise, a different
     * loader implementation must be used.
     */
    private String loaderClass = ParallelWebappClassLoader.class.getName();


    /**
     * The parent class loader of the class loader we will create.
     */
    private ClassLoader parentClassLoader = null;


    /**
     * The reloadable flag for this Loader.
     */
    private boolean reloadable = false;


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


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
            throw new IllegalStateException(
                    sm.getString("webappLoader.setContext.ise"));
        }

        // Deregister from the old Context (if any)
        if (this.context != null) {
            this.context.removePropertyChangeListener(this);
        }

        // Process this property change
        Context oldContext = this.context;
        this.context = context;
        support.firePropertyChange("context", oldContext, this.context);

        // Register with the new Container (if any)
        if (this.context != null) {
            setReloadable(this.context.getReloadable());
            this.context.addPropertyChangeListener(this);
        }
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
     * Return the reloadable flag for this Loader.
     */
    @Override
    public boolean getReloadable() {
        return this.reloadable;
    }


    /**
     * Set the reloadable flag for this Loader.
     *
     * @param reloadable The new reloadable flag
     */
    @Override
    public void setReloadable(boolean reloadable) {
        // Process this property change
        boolean oldReloadable = this.reloadable;
        this.reloadable = reloadable;
        support.firePropertyChange("reloadable",
                                   Boolean.valueOf(oldReloadable),
                                   Boolean.valueOf(this.reloadable));
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
        if (reloadable && modified()) {
            try {
                Thread.currentThread().setContextClassLoader
                    (WebappLoader.class.getClassLoader());
                if (context != null) {
                    context.reload();
                }
            } finally {
                if (context != null && context.getLoader() != null) {
                    Thread.currentThread().setContextClassLoader
                        (context.getLoader().getClassLoader());
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
        for( int i=0; i<repositories.length ; i++ ) {
            sb.append( repositories[i]).append(":");
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

        if (log.isDebugEnabled())
            log.debug(sm.getString("webappLoader.starting"));

        if (context.getResources() == null) {
            log.info("No resources for " + context);
            setState(LifecycleState.STARTING);
            return;
        }

        // Construct a class loader based on our current repositories list
        try {

            classLoader = createClassLoader();
            classLoader.setResources(context.getResources());
            classLoader.setDelegate(this.delegate);

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
            log.error( "LifecycleException ", t );
            throw new LifecycleException("start: ", t);
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

        if (log.isDebugEnabled())
            log.debug(sm.getString("webappLoader.stopping"));

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
                log.error("LifecycleException ", e);
            }
        }


        classLoader = null;
    }


    // ----------------------------------------- PropertyChangeListener Methods


    /**
     * Process property change events from our associated Context.
     *
     * @param event The property change event that has occurred
     */
    @Override
    public void propertyChange(PropertyChangeEvent event) {

        // Validate the source of this event
        if (!(event.getSource() instanceof Context))
            return;

        // Process a relevant property change
        if (event.getPropertyName().equals("reloadable")) {
            try {
                setReloadable
                    ( ((Boolean) event.getNewValue()).booleanValue() );
            } catch (NumberFormatException e) {
                log.error(sm.getString("webappLoader.reloadable",
                                 event.getNewValue().toString()));
            }
        }
    }


    // ------------------------------------------------------- Private Methods

    /**
     * Create associated classLoader.
     */
    private WebappClassLoaderBase createClassLoader()
        throws Exception {

        Class<?> clazz = Class.forName(loaderClass);
        WebappClassLoaderBase classLoader = null;

        if (parentClassLoader == null) {
            parentClassLoader = context.getParentClassLoader();
        }
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

        if (!Globals.IS_SECURITY_ENABLED)
            return;
        if (context == null)
            return;

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
        if (context == null)
            return;
        ServletContext servletContext = context.getServletContext();
        if (servletContext == null)
            return;

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
                for (int i = 0; i < repositories.length; i++) {
                    String repository = repositories[i].toString();
                    if (repository.startsWith("file://"))
                        repository = UDecoder.URLDecode(repository.substring(7));
                    else if (repository.startsWith("file:"))
                        repository = UDecoder.URLDecode(repository.substring(5));
                    else
                        continue;
                    if (repository == null)
                        continue;
                    if (classpath.length() > 0)
                        classpath.append(File.pathSeparator);
                    classpath.append(repository);
                }
        } else if (loader == ClassLoader.getSystemClassLoader()){
            // Java 9 onwards. The internal class loaders no longer extend
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
            log.info( "Unknown loader " + loader + " " + loader.getClass());
            return false;
        }
        return true;
    }


    private static final Log log = LogFactory.getLog(WebappLoader.class);


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
            name.append("/");
        }
        name.append(contextName);

        return name.toString();
    }
}

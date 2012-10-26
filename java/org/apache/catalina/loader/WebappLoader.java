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
import java.io.FileOutputStream;
import java.io.FilePermission;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.jar.JarFile;

import javax.management.ObjectName;
import javax.servlet.ServletContext;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Loader;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.res.StringManager;


/**
 * Classloader implementation which is specialized for handling web
 * applications in the most efficient way, while being Catalina aware (all
 * accesses to resources are made through the DirContext interface).
 * This class loader supports detection of modified
 * Java classes, which can be used to implement auto-reload support.
 * <p>
 * This class loader is configured by adding the pathnames of directories,
 * JAR files, and ZIP files with the <code>addRepository()</code> method,
 * prior to calling <code>start()</code>.  When a new class is required,
 * these repositories will be consulted first to locate the class.  If it
 * is not present, the system class loader will be used instead.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 * @version $Id$
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
    private WebappClassLoader classLoader = null;


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
     * This class should extend WebappClassLoader, otherwise, a different
     * loader implementation must be used.
     */
    private String loaderClass =
        "org.apache.catalina.loader.WebappClassLoader";


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


    /**
     * Repositories that are set in the loader, for JMX.
     */
    private ArrayList<String> loaderRepositories = null;


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

        return (this.delegate);

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
     * Return the ClassLoader class name.
     */
    public String getLoaderClass() {

        return (this.loaderClass);

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

        return (this.reloadable);

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
        } else {
            closeJARs(false);
        }
    }


    public String[] getLoaderRepositories() {
        if( loaderRepositories==null ) return  null;
        String res[]=new String[ loaderRepositories.size()];
        loaderRepositories.toArray(res);
        return res;
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
     * Used to periodically signal to the classloader to release JAR resources.
     */
    public void closeJARs(boolean force) {
        if (classLoader !=null) {
            classLoader.closeJARs(force);
        }
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

        StringBuilder sb = new StringBuilder("WebappLoader[");
        if (context != null)
            sb.append(context.getName());
        sb.append("]");
        return (sb.toString());

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
            setRepositories();
            setClassPath();

            setPermissions();

            ((Lifecycle) classLoader).start();

            String contextName = context.getName();
            if (!contextName.startsWith("/")) {
                contextName = "/" + contextName;
            }
            ObjectName cloname = new ObjectName(context.getDomain() +
                    ":type=WebappClassLoader,context=" + contextName +
                    ",host=" + context.getParent().getName());
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

        // Throw away our current class loader
        ((Lifecycle) classLoader).stop();

        try {
            String contextName = context.getName();
            if (!contextName.startsWith("/")) {
                contextName = "/" + contextName;
            }
            ObjectName cloname = new ObjectName(context.getDomain() +
                    ":type=WebappClassLoader,context=" + contextName +
                    ",host=" + context.getParent().getName());
            Registry.getRegistry(null, null).unregisterComponent(cloname);
        } catch (Exception e) {
            log.error("LifecycleException ", e);
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
    private WebappClassLoader createClassLoader()
        throws Exception {

        Class<?> clazz = Class.forName(loaderClass);
        WebappClassLoader classLoader = null;

        if (parentClassLoader == null) {
            parentClassLoader = context.getParentClassLoader();
        }
        Class<?>[] argTypes = { ClassLoader.class };
        Object[] args = { parentClassLoader };
        Constructor<?> constr = clazz.getConstructor(argTypes);
        classLoader = (WebappClassLoader) constr.newInstance(args);

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

        try {

            URL rootURL = servletContext.getResource("/");
            classLoader.addPermission(rootURL);

            String contextRoot = servletContext.getRealPath("/");
            if (contextRoot != null) {
                try {
                    contextRoot = (new File(contextRoot)).getCanonicalPath();
                    classLoader.addPermission(contextRoot);
                } catch (IOException e) {
                    // Ignore
                }
            }

            URL classesURL = servletContext.getResource("/WEB-INF/classes/");
            classLoader.addPermission(classesURL);
            URL libURL = servletContext.getResource("/WEB-INF/lib/");
            classLoader.addPermission(libURL);

            if (contextRoot != null) {

                if (libURL != null) {
                    File rootDir = new File(contextRoot);
                    File libDir = new File(rootDir, "WEB-INF/lib/");
                    try {
                        String path = libDir.getCanonicalPath();
                        classLoader.addPermission(path);
                    } catch (IOException e) {
                        // Ignore
                    }
                }

            } else {

                if (workDir != null) {
                    if (libURL != null) {
                        File libDir = new File(workDir, "WEB-INF/lib/");
                        try {
                            String path = libDir.getCanonicalPath();
                            classLoader.addPermission(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    }
                    if (classesURL != null) {
                        File classesDir = new File(workDir, "WEB-INF/classes/");
                        try {
                            String path = classesDir.getCanonicalPath();
                            classLoader.addPermission(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    }
                }

            }

        } catch (MalformedURLException e) {
            // Ignore
        }

    }


    /**
     * Configure the repositories for our class loader, based on the
     * associated Context.
     * @throws IOException
     */
    private void setRepositories() throws IOException {

        if (context == null)
            return;
        ServletContext servletContext = context.getServletContext();
        if (servletContext == null)
            return;

        loaderRepositories=new ArrayList<>();
        // Loading the work directory
        File workDir =
            (File) servletContext.getAttribute(ServletContext.TEMPDIR);
        if (workDir == null) {
            log.info("No work dir for " + servletContext);
        }

        if( log.isDebugEnabled() && workDir != null)
            log.debug(sm.getString("webappLoader.deploy", workDir.getAbsolutePath()));

        classLoader.setWorkDir(workDir);

        WebResourceRoot resources = context.getResources();

        // Setting up the class repository (/WEB-INF/classes), if it exists

        String classesPath = "/WEB-INF/classes";
        WebResource classes = resources.getResource(classesPath);

        if (classes.isDirectory()) {

            File classRepository = null;

            String absoluteClassesPath = classes.getCanonicalPath();

            if (absoluteClassesPath != null) {
                classRepository = new File(absoluteClassesPath);
            } else {
                classRepository = new File(workDir, classesPath);
                if (!classRepository.mkdirs() &&
                        !classRepository.isDirectory()) {
                    throw new IOException(
                            sm.getString("webappLoader.mkdirFailure"));
                }
                if (!copyDir(classes, classRepository)) {
                    throw new IOException(
                            sm.getString("webappLoader.copyFailure"));
                }
            }

            if(log.isDebugEnabled())
                log.debug(sm.getString("webappLoader.classDeploy", classesPath,
                             classRepository.getAbsolutePath()));

            // Adding the repository to the class loader
            classLoader.setRepository(classesPath + "/", classRepository);
            loaderRepositories.add(classesPath + "/" );
        }

        // Setting up the JAR repository (/WEB-INF/lib), if it exists

        String libPath = "/WEB-INF/lib";

        classLoader.setJarPath(libPath);

        WebResource libDir = resources.getResource(libPath);

        if (libDir.isDirectory()) {

            boolean copyJars = false;
            String absoluteLibPath = libDir.getCanonicalPath();

            File destDir = null;

            if (absoluteLibPath != null) {
                destDir = new File(absoluteLibPath);
            } else {
                copyJars = true;
                destDir = new File(workDir, libPath);
                if (!destDir.mkdirs() && !destDir.isDirectory()) {
                    throw new IOException(
                            sm.getString("webappLoader.mkdirFailure"));
                }
            }

            WebResource[] jars = resources.listResources(libPath);
            for (WebResource jar : jars) {

                String jarName = jar.getName();

                if (!jarName.endsWith(".jar"))
                    continue;

                String filename = libPath + "/" + jarName;

                // Copy JAR in the work directory, always (the JAR file
                // would get locked otherwise, which would make it
                // impossible to update it or remove it at runtime)
                File destFile = new File(destDir, jarName);

                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("webappLoader.jarDeploy", filename,
                            destFile.getAbsolutePath()));
                }

                // Bug 45403 - Check that the resource is readable
                if (!jar.canRead()) {
                    IOException ioe = new IOException(sm.getString(
                            "webappLoader.readFailure", filename));
                    throw ioe;
                }

                if (copyJars) {
                    if (!copy(jar.getInputStream(),destFile)) {
                        throw new IOException(
                                sm.getString("webappLoader.copyFailure"));
                    }
                }

                try {
                    JarFile jarFile = new JarFile(destFile);
                    classLoader.addJar(filename, jarFile, destFile);
                } catch (Exception ex) {
                    // Catch the exception if there is an empty jar file
                    // Should ignore and continue loading other jar files
                    // in the dir
                }

                loaderRepositories.add( filename );
            }
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
        int n = 0;
        while (loader != null) {
            if (!(loader instanceof URLClassLoader)) {
                String cp=getClasspath( loader );
                if( cp==null ) {
                    log.info( "Unknown loader " + loader + " " + loader.getClass());
                } else {
                    if (n > 0)
                        classpath.append(File.pathSeparator);
                    classpath.append(cp);
                    n++;
                }
                break;
                //continue;
            }
            URL repositories[] =
                ((URLClassLoader) loader).getURLs();
            for (int i = 0; i < repositories.length; i++) {
                String repository = repositories[i].toString();
                if (repository.startsWith("file://"))
                    repository = utf8Decode(repository.substring(7));
                else if (repository.startsWith("file:"))
                    repository = utf8Decode(repository.substring(5));
                else if (repository.startsWith("jndi:"))
                    repository =
                        servletContext.getRealPath(repository.substring(5));
                else
                    continue;
                if (repository == null)
                    continue;
                if (n > 0)
                    classpath.append(File.pathSeparator);
                classpath.append(repository);
                n++;
            }
            loader = loader.getParent();
        }

        this.classpath=classpath.toString();

        // Store the assembled class path as a servlet context attribute
        servletContext.setAttribute(Globals.CLASS_PATH_ATTR,
                                    classpath.toString());

    }

    private String utf8Decode(String input) {
        String result = null;
        try {
            result = URLDecoder.decode(input, "UTF-8");
        } catch (UnsupportedEncodingException uee) {
            // Impossible. All JVMs are required to support UTF-8.
        }
        return result;
    }

    // try to extract the classpath from a loader that is not URLClassLoader
    private String getClasspath( ClassLoader loader ) {
        try {
            Method m=loader.getClass().getMethod("getClasspath", new Class[] {});
            if( log.isTraceEnabled())
                log.trace("getClasspath " + m );
            if( m==null ) return null;
            Object o=m.invoke( loader, new Object[] {} );
            if( log.isDebugEnabled() )
                log.debug("gotClasspath " + o);
            if( o instanceof String )
                return (String)o;
            return null;
        } catch( Exception ex ) {
            Throwable t = ExceptionUtils.unwrapInvocationTargetException(ex);
            ExceptionUtils.handleThrowable(t);
            if (log.isDebugEnabled())
                log.debug("getClasspath ", ex);
        }
        return null;
    }

    /**
     * Copy directory.
     */
    private boolean copyDir(WebResource src, File destDir) {

        WebResource[] resources =
                src.getWebResourceRoot().listResources(src.getWebappPath());
        for (WebResource resource : resources) {
            File currentFile = new File(destDir, resource.getName());
            if (resource.isFile()) {
                InputStream is = resource.getInputStream();
                if (!copy(is, currentFile))
                    return false;
            } else if (resource.isDirectory()) {
                if (!currentFile.isDirectory() && !currentFile.mkdir())
                    return false;
                if (!copyDir(resource, currentFile))
                    return false;
            }
        }

        return true;
    }


    /**
     * Copy a file to the specified temp directory. This is required only
     * because Jasper depends on it.
     */
    private boolean copy(InputStream is, File file) {

        try (FileOutputStream os = new FileOutputStream(file)){
            byte[] buf = new byte[4096];
            while (true) {
                int len = is.read(buf);
                if (len < 0)
                    break;
                os.write(buf, 0, len);
            }
        } catch (IOException e) {
            return false;
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                // Ignore
            }
        }

        return true;

    }


    private static final org.apache.juli.logging.Log log=
        org.apache.juli.logging.LogFactory.getLog( WebappLoader.class );


    @Override
    protected String getDomainInternal() {
        return context.getDomain();
    }


    @Override
    protected String getObjectNameKeyProperties() {

        StringBuilder name = new StringBuilder("type=Loader");

        name.append(",context=");

        String contextName = context.getName();
        if (!contextName.startsWith("/")) {
            name.append("/");
        }
        name.append(contextName);

        name.append(",host=");
        name.append(context.getParent().getName());

        return name.toString();
    }
}

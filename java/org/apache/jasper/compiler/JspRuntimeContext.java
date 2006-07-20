/*
 * Copyright 1999,2004 The Apache Software Foundation.
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

package org.apache.jasper.compiler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilePermission;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.jsp.JspFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jasper.Constants;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.Options;
import org.apache.jasper.runtime.JspFactoryImpl;
import org.apache.jasper.security.SecurityClassLoad;
import org.apache.jasper.servlet.JspServletWrapper;

/**
 * Class for tracking JSP compile time file dependencies when the
 * &060;%@include file="..."%&062; directive is used.
 *
 * A background thread periodically checks the files a JSP page
 * is dependent upon.  If a dpendent file changes the JSP page
 * which included it is recompiled.
 *
 * Only used if a web application context is a directory.
 *
 * @author Glenn L. Nielsen
 * @version $Revision: 306189 $
 */
public final class JspRuntimeContext implements Runnable {

    // Logger
    private Log log = LogFactory.getLog(JspRuntimeContext.class);

    /*
     * Counts how many times the webapp's JSPs have been reloaded.
     */
    private int jspReloadCount;

    /**
     * Preload classes required at runtime by a JSP servlet so that
     * we don't get a defineClassInPackage security exception.
     */
    static {
        JspFactoryImpl factory = new JspFactoryImpl();
        SecurityClassLoad.securityClassLoad(factory.getClass().getClassLoader());
        JspFactory.setDefaultFactory(factory);
    }

    // ----------------------------------------------------------- Constructors

    /**
     * Create a JspRuntimeContext for a web application context.
     *
     * Loads in any previously generated dependencies from file.
     *
     * @param context ServletContext for web application
     */
    public JspRuntimeContext(ServletContext context, Options options) {

        this.context = context;
        this.options = options;

        // Get the parent class loader
        parentClassLoader =
            (URLClassLoader) Thread.currentThread().getContextClassLoader();
        if (parentClassLoader == null) {
            parentClassLoader =
                (URLClassLoader)this.getClass().getClassLoader();
        }

	if (log.isDebugEnabled()) {
	    if (parentClassLoader != null) {
		log.debug(Localizer.getMessage("jsp.message.parent_class_loader_is",
					       parentClassLoader.toString()));
	    } else {
		log.debug(Localizer.getMessage("jsp.message.parent_class_loader_is",
					       "<none>"));
	    }
        }

        initClassPath();

	if (context instanceof org.apache.jasper.servlet.JspCServletContext) {
	    return;
	}

        if (System.getSecurityManager() != null) {
            initSecurity();
        }

        // If this web application context is running from a
        // directory, start the background compilation thread
        String appBase = context.getRealPath("/");         
        if (!options.getDevelopment()
                && appBase != null
                && options.getCheckInterval() > 0) {
            if (appBase.endsWith(File.separator) ) {
                appBase = appBase.substring(0,appBase.length()-1);
            }
            String directory =
                appBase.substring(appBase.lastIndexOf(File.separator));
            threadName = threadName + "[" + directory + "]";
            threadStart();
        }                                            
    }

    // ----------------------------------------------------- Instance Variables

    /**
     * This web applications ServletContext
     */
    private ServletContext context;
    private Options options;
    private URLClassLoader parentClassLoader;
    private PermissionCollection permissionCollection;
    private CodeSource codeSource;                    
    private String classpath;

    /**
     * Maps JSP pages to their JspServletWrapper's
     */
    private Map jsps = Collections.synchronizedMap( new HashMap());
 

    /**
     * The background thread.
     */
    private Thread thread = null;


    /**
     * The background thread completion semaphore.
     */
    private boolean threadDone = false;


    /**
     * Name to register for the background thread.
     */
    private String threadName = "JspRuntimeContext";

    // ------------------------------------------------------ Public Methods

    /**
     * Add a new JspServletWrapper.
     *
     * @param jspUri JSP URI
     * @param jsw Servlet wrapper for JSP
     */
    public void addWrapper(String jspUri, JspServletWrapper jsw) {
        jsps.remove(jspUri);
        jsps.put(jspUri,jsw);
    }

    /**
     * Get an already existing JspServletWrapper.
     *
     * @param jspUri JSP URI
     * @return JspServletWrapper for JSP
     */
    public JspServletWrapper getWrapper(String jspUri) {
        return (JspServletWrapper) jsps.get(jspUri);
    }

    /**
     * Remove a  JspServletWrapper.
     *
     * @param jspUri JSP URI of JspServletWrapper to remove
     */
    public void removeWrapper(String jspUri) {
        jsps.remove(jspUri);
    }

    /**
     * Returns the number of JSPs for which JspServletWrappers exist, i.e.,
     * the number of JSPs that have been loaded into the webapp.
     *
     * @return The number of JSPs that have been loaded into the webapp
     */
    public int getJspCount() {
        return jsps.size();
    }

    /**
     * Get the SecurityManager Policy CodeSource for this web
     * applicaiton context.
     *
     * @return CodeSource for JSP
     */
    public CodeSource getCodeSource() {
        return codeSource;
    }

    /**
     * Get the parent URLClassLoader.
     *
     * @return URLClassLoader parent
     */
    public URLClassLoader getParentClassLoader() {
        return parentClassLoader;
    }

    /**
     * Get the SecurityManager PermissionCollection for this
     * web application context.
     *
     * @return PermissionCollection permissions
     */
    public PermissionCollection getPermissionCollection() {
        return permissionCollection;
    }

    /**
     * Process a "destory" event for this web application context.
     */                                                        
    public void destroy() {
        threadStop();

        Iterator servlets = jsps.values().iterator();
        while (servlets.hasNext()) {
            ((JspServletWrapper) servlets.next()).destroy();
        }
    }

    /**
     * Increments the JSP reload counter.
     */
    public synchronized void incrementJspReloadCount() {
        jspReloadCount++;
    }

    /**
     * Resets the JSP reload counter.
     *
     * @param count Value to which to reset the JSP reload counter
     */
    public synchronized void setJspReloadCount(int count) {
        this.jspReloadCount = count;
    }

    /**
     * Gets the current value of the JSP reload counter.
     *
     * @return The current value of the JSP reload counter
     */
    public int getJspReloadCount() {
        return jspReloadCount;
    }


    // -------------------------------------------------------- Private Methods

    /**
     * Method used by background thread to check the JSP dependencies
     * registered with this class for JSP's.
     */
    private void checkCompile() {
        Object [] wrappers = jsps.values().toArray();
        for (int i = 0; i < wrappers.length; i++ ) {
            JspServletWrapper jsw = (JspServletWrapper)wrappers[i];
            JspCompilationContext ctxt = jsw.getJspEngineContext();
            // JspServletWrapper also synchronizes on this when
            // it detects it has to do a reload
            synchronized(jsw) {
                try {
                    ctxt.compile();
                } catch (FileNotFoundException ex) {
                    ctxt.incrementRemoved();
                } catch (Throwable t) {
                    jsw.getServletContext().log("Background compile failed",
						t);
                }
            }
        }
    }

    /**
     * The classpath that is passed off to the Java compiler.
     */
    public String getClassPath() {
        return classpath;
    }

    /**
     * Method used to initialize classpath for compiles.
     */
    private void initClassPath() {

        URL [] urls = parentClassLoader.getURLs();
        StringBuffer cpath = new StringBuffer();
        String sep = System.getProperty("path.separator");

        for(int i = 0; i < urls.length; i++) {
            // Tomcat 4 can use URL's other than file URL's,
            // a protocol other than file: will generate a
            // bad file system path, so only add file:
            // protocol URL's to the classpath.
            
            if( urls[i].getProtocol().equals("file") ) {
                cpath.append((String)urls[i].getFile()+sep);
            }
        }    

	cpath.append(options.getScratchDir() + sep);

        String cp = (String) context.getAttribute(Constants.SERVLET_CLASSPATH);
        if (cp == null || cp.equals("")) {
            cp = options.getClassPath();
        }

        classpath = cpath.toString() + cp;

        if(log.isDebugEnabled()) {
            log.debug("Compilation classpath initialized: " + getClassPath());
        }
    }

    /**
     * Method used to initialize SecurityManager data.
     */
    private void initSecurity() {

        // Setup the PermissionCollection for this web app context
        // based on the permissions configured for the root of the
        // web app context directory, then add a file read permission
        // for that directory.
        Policy policy = Policy.getPolicy();
        if( policy != null ) {
            try {          
                // Get the permissions for the web app context
                String docBase = context.getRealPath("/");
                if( docBase == null ) {
                    docBase = options.getScratchDir().toString();
                }
                String codeBase = docBase;
                if (!codeBase.endsWith(File.separator)){
                    codeBase = codeBase + File.separator;
                }
                File contextDir = new File(codeBase);
                URL url = contextDir.getCanonicalFile().toURL();
                codeSource = new CodeSource(url,(Certificate[])null);
                permissionCollection = policy.getPermissions(codeSource);

                // Create a file read permission for web app context directory
                if (!docBase.endsWith(File.separator)){
                    permissionCollection.add
                        (new FilePermission(docBase,"read"));
                    docBase = docBase + File.separator;
                } else {
                    permissionCollection.add
                        (new FilePermission
                            (docBase.substring(0,docBase.length() - 1),"read"));
                }
                docBase = docBase + "-";
                permissionCollection.add(new FilePermission(docBase,"read"));

                // Create a file read permission for web app tempdir (work)
                // directory
                String workDir = options.getScratchDir().toString();
                if (!workDir.endsWith(File.separator)){
                    permissionCollection.add
                        (new FilePermission(workDir,"read"));
                    workDir = workDir + File.separator;
                }
                workDir = workDir + "-";
                permissionCollection.add(new FilePermission(workDir,"read"));

                // Allow the JSP to access org.apache.jasper.runtime.HttpJspBase
                permissionCollection.add( new RuntimePermission(
                    "accessClassInPackage.org.apache.jasper.runtime") );

                if (parentClassLoader instanceof URLClassLoader) {
                    URL [] urls = parentClassLoader.getURLs();
                    String jarUrl = null;
                    String jndiUrl = null;
                    for (int i=0; i<urls.length; i++) {
                        if (jndiUrl == null
                                && urls[i].toString().startsWith("jndi:") ) {
                            jndiUrl = urls[i].toString() + "-";
                        }
                        if (jarUrl == null
                                && urls[i].toString().startsWith("jar:jndi:")
                                ) {
                            jarUrl = urls[i].toString();
                            jarUrl = jarUrl.substring(0,jarUrl.length() - 2);
                            jarUrl = jarUrl.substring(0,
                                     jarUrl.lastIndexOf('/')) + "/-";
                        }
                    }
                    if (jarUrl != null) {
                        permissionCollection.add(
                                new FilePermission(jarUrl,"read"));
                        permissionCollection.add(
                                new FilePermission(jarUrl.substring(4),"read"));
                    }
                    if (jndiUrl != null)
                        permissionCollection.add(
                                new FilePermission(jndiUrl,"read") );
                }
            } catch(Exception e) {
                context.log("Security Init for context failed",e);
            }
        }
    }


    // -------------------------------------------------------- Thread Support

    /**
     * Start the background thread that will periodically check for
     * changes to compile time included files in a JSP.
     *
     * @exception IllegalStateException if we should not be starting
     *  a background thread now
     */
    protected void threadStart() {

        // Has the background thread already been started?
        if (thread != null) {
            return;
        }

        // Start the background thread
        threadDone = false;
        thread = new Thread(this, threadName);
        thread.setDaemon(true);
        thread.start();

    }


    /**
     * Stop the background thread that is periodically checking for
     * changes to compile time included files in a JSP.
     */ 
    protected void threadStop() {

        if (thread == null) {
            return;
        }

        threadDone = true;
        thread.interrupt();
        try {
            thread.join();
        } catch (InterruptedException e) {
            ;
        }
        
        thread = null;
        
    }

    /**
     * Sleep for the duration specified by the <code>checkInterval</code>
     * property.
     */ 
    protected void threadSleep() {
        
        try {
            Thread.sleep(options.getCheckInterval() * 1000L);
        } catch (InterruptedException e) {
            ;
        }
        
    }   
    
    
    // ------------------------------------------------------ Background Thread
        
        
    /**
     * The background thread that checks for changes to files
     * included by a JSP and flags that a recompile is required.
     */ 
    public void run() {
        
        // Loop until the termination semaphore is set
        while (!threadDone) {

            // Wait for our check interval
            threadSleep();

            // Check for included files which are newer than the
            // JSP which uses them.
            try {
                checkCompile();
            } catch (Throwable t) {
                log.error("Exception checking if recompile needed: ", t);
            }
        }
        
    }

}

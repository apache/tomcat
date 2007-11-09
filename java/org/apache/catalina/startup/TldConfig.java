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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.servlet.ServletException;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.util.StringManager;
import org.apache.tomcat.util.digester.Digester;
import org.xml.sax.InputSource;

/**
 * Startup event listener for a <b>Context</b> that configures the properties
 * of that Context, and the associated defined servlets.
 *
 * @author Craig R. McClanahan
 * @author Jean-Francois Arcand
 * @author Costin Manolache
 */
public final class TldConfig  {

    // Names of JARs that are known not to contain any TLDs
    private static HashSet<String> noTldJars;

    private static org.apache.juli.logging.Log log=
        org.apache.juli.logging.LogFactory.getLog( TldConfig.class );

    /*
     * Initializes the set of JARs that are known not to contain any TLDs
     */
    static {
        noTldJars = new HashSet<String>();
        // Bootstrap JARs
        noTldJars.add("bootstrap.jar");
        noTldJars.add("commons-daemon.jar");
        noTldJars.add("tomcat-juli.jar");
        // Main JARs
        noTldJars.add("annotations-api.jar");
        noTldJars.add("catalina.jar");
        noTldJars.add("catalina-ant.jar");
        noTldJars.add("catalina-ha.jar");
        noTldJars.add("catalina-tribes.jar");
        noTldJars.add("el-api.jar");
        noTldJars.add("jasper.jar");
        noTldJars.add("jasper-el.jar");
        noTldJars.add("jasper-jdt.jar");
        noTldJars.add("jsp-api.jar");
        noTldJars.add("servlet-api.jar");
        noTldJars.add("tomcat-coyote.jar");
        noTldJars.add("tomcat-dbcp.jar");
        // i18n JARs
        noTldJars.add("tomcat-i18n-en.jar");
        noTldJars.add("tomcat-i18n-es.jar");
        noTldJars.add("tomcat-i18n-fr.jar");
        noTldJars.add("tomcat-i18n-ja.jar");
        // Misc JARs not included with Tomcat
        noTldJars.add("ant.jar");
        noTldJars.add("commons-dbcp.jar");
        noTldJars.add("commons-beanutils.jar");
        noTldJars.add("commons-fileupload-1.0.jar");
        noTldJars.add("commons-pool.jar");
        noTldJars.add("commons-digester.jar");
        noTldJars.add("commons-logging.jar");
        noTldJars.add("commons-collections.jar");
        noTldJars.add("jmx.jar");
        noTldJars.add("jmx-tools.jar");
        noTldJars.add("xercesImpl.jar");
        noTldJars.add("xmlParserAPIs.jar");
        noTldJars.add("xml-apis.jar");
        // JARs from J2SE runtime
        noTldJars.add("sunjce_provider.jar");
        noTldJars.add("ldapsec.jar");
        noTldJars.add("localedata.jar");
        noTldJars.add("dnsns.jar");
        noTldJars.add("tools.jar");
        noTldJars.add("sunpkcs11.jar");
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * The Context we are associated with.
     */
    private Context context = null;


    /**
     * The string resources for this package.
     */
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);

    /**
     * The <code>Digester</code> we will use to process tag library
     * descriptor files.
     */
    private static Digester tldDigester = null;


    /**
     * Attribute value used to turn on/off TLD validation
     */
     private static boolean tldValidation = false;


    /**
     * Attribute value used to turn on/off TLD  namespace awarenes.
     */
    private static boolean tldNamespaceAware = false;

    private boolean rescan=true;

    private ArrayList<String> listeners = new ArrayList<String>();

    // --------------------------------------------------------- Public Methods

    /**
     * Sets the list of JARs that are known not to contain any TLDs.
     *
     * @param jarNames List of comma-separated names of JAR files that are 
     * known not to contain any TLDs 
     */
    public static void setNoTldJars(String jarNames) {
        if (jarNames != null) {
            noTldJars.clear();
            StringTokenizer tokenizer = new StringTokenizer(jarNames, ",");
            while (tokenizer.hasMoreElements()) {
                noTldJars.add(tokenizer.nextToken());
            }
        }
    }

    /**
     * Set the validation feature of the XML parser used when
     * parsing xml instances.
     * @param tldValidation true to enable xml instance validation
     */
    public void setTldValidation(boolean tldValidation){
        TldConfig.tldValidation = tldValidation;
    }

    /**
     * Get the server.xml <host> attribute's xmlValidation.
     * @return true if validation is enabled.
     *
     */
    public boolean getTldValidation(){
        return tldValidation;
    }

    /**
     * Get the server.xml <host> attribute's xmlNamespaceAware.
     * @return true if namespace awarenes is enabled.
     *
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
        TldConfig.tldNamespaceAware = tldNamespaceAware;
    }    


    public boolean isRescan() {
        return rescan;
    }

    public void setRescan(boolean rescan) {
        this.rescan = rescan;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public void addApplicationListener( String s ) {
        //if(log.isDebugEnabled())
            log.debug( "Add tld listener " + s);
        listeners.add(s);
    }

    public String[] getTldListeners() {
        String result[]=new String[listeners.size()];
        listeners.toArray(result);
        return result;
    }


    /**
     * Scan for and configure all tag library descriptors found in this
     * web application.
     *
     * @exception Exception if a fatal input/output or parsing error occurs
     */
    public void execute() throws Exception {
        long t1=System.currentTimeMillis();

        File tldCache=null;

        if (context instanceof StandardContext) {
            File workDir= (File)
                ((StandardContext)context).getServletContext().getAttribute(Globals.WORK_DIR_ATTR);
            //tldCache=new File( workDir, "tldCache.ser");
        }

        // Option to not rescan
        if( ! rescan ) {
            // find the cache
            if( tldCache!= null && tldCache.exists()) {
                // just read it...
                processCache(tldCache);
                return;
            }
        }

        /*
         * Acquire the list of TLD resource paths, possibly embedded in JAR
         * files, to be processed
         */
        Set resourcePaths = tldScanResourcePaths();
        Map jarPaths = getJarPaths();

        // Check to see if we can use cached listeners
        if (tldCache != null && tldCache.exists()) {
            long lastModified = getLastModified(resourcePaths, jarPaths);
            if (lastModified < tldCache.lastModified()) {
                processCache(tldCache);
                return;
            }
        }

        // Scan each accumulated resource path for TLDs to be processed
        Iterator paths = resourcePaths.iterator();
        while (paths.hasNext()) {
            String path = (String) paths.next();
            if (path.endsWith(".jar")) {
                tldScanJar(path);
            } else {
                tldScanTld(path);
            }
        }
        if (jarPaths != null) {
            paths = jarPaths.values().iterator();
            while (paths.hasNext()) {
                tldScanJar((File) paths.next());
            }
        }

        String list[] = getTldListeners();

        if( tldCache!= null ) {
            log.debug( "Saving tld cache: " + tldCache + " " + list.length);
            try {
                FileOutputStream out=new FileOutputStream(tldCache);
                ObjectOutputStream oos=new ObjectOutputStream( out );
                oos.writeObject( list );
                oos.close();
            } catch( IOException ex ) {
                ex.printStackTrace();
            }
        }

        if( log.isDebugEnabled() )
            log.debug( "Adding tld listeners:" + list.length);
        for( int i=0; list!=null && i<list.length; i++ ) {
            context.addApplicationListener(list[i]);
        }

        long t2=System.currentTimeMillis();
        if( context instanceof StandardContext ) {
            ((StandardContext)context).setTldScanTime(t2-t1);
        }

    }

    // -------------------------------------------------------- Private Methods

    /*
     * Returns the last modification date of the given sets of resources.
     *
     * @param resourcePaths
     * @param jarPaths
     *
     * @return Last modification date
     */
    private long getLastModified(Set resourcePaths, Map jarPaths)
            throws Exception {

        long lastModified = 0;

        Iterator paths = resourcePaths.iterator();
        while (paths.hasNext()) {
            String path = (String) paths.next();
            URL url = context.getServletContext().getResource(path);
            if (url == null) {
                log.debug( "Null url "+ path );
                break;
            }
            long lastM = url.openConnection().getLastModified();
            if (lastM > lastModified) lastModified = lastM;
            if (log.isDebugEnabled()) {
                log.debug( "Last modified " + path + " " + lastM);
            }
        }

        if (jarPaths != null) {
            paths = jarPaths.values().iterator();
            while (paths.hasNext()) {
                File jarFile = (File) paths.next();
                long lastM = jarFile.lastModified();
                if (lastM > lastModified) lastModified = lastM;
                if (log.isDebugEnabled()) {
                    log.debug("Last modified " + jarFile.getAbsolutePath()
                              + " " + lastM);
                }
            }
        }

        return lastModified;
    }

    private void processCache(File tldCache ) throws IOException {
        // read the cache and return;
        try {
            FileInputStream in=new FileInputStream(tldCache);
            ObjectInputStream ois=new ObjectInputStream( in );
            String list[]=(String [])ois.readObject();
            if( log.isDebugEnabled() )
                log.debug("Reusing tldCache " + tldCache + " " + list.length);
            for( int i=0; list!=null && i<list.length; i++ ) {
                context.addApplicationListener(list[i]);
            }
            ois.close();
        } catch( ClassNotFoundException ex ) {
            ex.printStackTrace();
        }
    }

    /**
     * Create (if necessary) and return a Digester configured to process a tag
     * library descriptor, looking for additional listener classes to be
     * registered.
     */
    private static Digester createTldDigester() {

        return DigesterFactory.newDigester(tldValidation, 
                                           tldNamespaceAware, 
                                           new TldRuleSet());

    }


    /**
     * Scan the JAR file at the specified resource path for TLDs in the
     * <code>META-INF</code> subdirectory, and scan each TLD for application
     * event listeners that need to be registered.
     *
     * @param resourcePath Resource path of the JAR file to scan
     *
     * @exception Exception if an exception occurs while scanning this JAR
     */
    private void tldScanJar(String resourcePath) throws Exception {

        if (log.isDebugEnabled()) {
            log.debug(" Scanning JAR at resource path '" + resourcePath + "'");
        }

        URL url = context.getServletContext().getResource(resourcePath);
        if (url == null) {
            throw new IllegalArgumentException
                                (sm.getString("contextConfig.tldResourcePath",
                                              resourcePath));
        }

        File file = null;
        try {
            file = new File(url.toURI());
        } catch (URISyntaxException e) {
            // Ignore, probably an unencoded char
            file = new File(url.getFile());
        }
        try {
            file = file.getCanonicalFile();
        } catch (IOException e) {
            // Ignore
        }
        tldScanJar(file);

    }

    /**
     * Scans all TLD entries in the given JAR for application listeners.
     *
     * @param file JAR file whose TLD entries are scanned for application
     * listeners
     */
    private void tldScanJar(File file) throws Exception {

        JarFile jarFile = null;
        String name = null;

        String jarPath = file.getAbsolutePath();

        try {
            jarFile = new JarFile(file);
            Enumeration entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = (JarEntry) entries.nextElement();
                name = entry.getName();
                if (!name.startsWith("META-INF/")) {
                    continue;
                }
                if (!name.endsWith(".tld")) {
                    continue;
                }
                if (log.isTraceEnabled()) {
                    log.trace("  Processing TLD at '" + name + "'");
                }
                try {
                    tldScanStream(new InputSource(jarFile.getInputStream(entry)));
                } catch (Exception e) {
                    log.error(sm.getString("contextConfig.tldEntryException",
                                           name, jarPath, context.getPath()),
                              e);
                }
            }
        } catch (Exception e) {
            log.error(sm.getString("contextConfig.tldJarException",
                                   jarPath, context.getPath()),
                      e);
        } finally {
            if (jarFile != null) {
                try {
                    jarFile.close();
                } catch (Throwable t) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Scan the TLD contents in the specified input stream, and register
     * any application event listeners found there.  <b>NOTE</b> - It is
     * the responsibility of the caller to close the InputStream after this
     * method returns.
     *
     * @param resourceStream InputStream containing a tag library descriptor
     *
     * @exception Exception if an exception occurs while scanning this TLD
     */
    private void tldScanStream(InputSource resourceStream)
        throws Exception {

        if (tldDigester == null){
            tldDigester = createTldDigester();
        }
        
        synchronized (tldDigester) {
            try {
                tldDigester.push(this);
                tldDigester.parse(resourceStream);
            } finally {
                tldDigester.reset();
            }
        }

    }

    /**
     * Scan the TLD contents at the specified resource path, and register
     * any application event listeners found there.
     *
     * @param resourcePath Resource path being scanned
     *
     * @exception Exception if an exception occurs while scanning this TLD
     */
    private void tldScanTld(String resourcePath) throws Exception {

        if (log.isDebugEnabled()) {
            log.debug(" Scanning TLD at resource path '" + resourcePath + "'");
        }

        InputSource inputSource = null;
        try {
            InputStream stream =
                context.getServletContext().getResourceAsStream(resourcePath);
            if (stream == null) {
                throw new IllegalArgumentException
                (sm.getString("contextConfig.tldResourcePath",
                        resourcePath));
            }
            inputSource = new InputSource(stream);
            if (inputSource == null) {
                throw new IllegalArgumentException
                    (sm.getString("contextConfig.tldResourcePath",
                                  resourcePath));
            }
            tldScanStream(inputSource);
        } catch (Exception e) {
             throw new ServletException
                 (sm.getString("contextConfig.tldFileException", resourcePath,
                               context.getPath()),
                  e);
        } 

    }

    /**
     * Accumulate and return a Set of resource paths to be analyzed for
     * tag library descriptors.  Each element of the returned set will be
     * the context-relative path to either a tag library descriptor file,
     * or to a JAR file that may contain tag library descriptors in its
     * <code>META-INF</code> subdirectory.
     *
     * @exception IOException if an input/output error occurs while
     *  accumulating the list of resource paths
     */
    private Set tldScanResourcePaths() throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(" Accumulating TLD resource paths");
        }
        Set resourcePaths = new HashSet();

        // Accumulate resource paths explicitly listed in the web application
        // deployment descriptor
        if (log.isTraceEnabled()) {
            log.trace("  Scanning <taglib> elements in web.xml");
        }
        String taglibs[] = context.findTaglibs();
        for (int i = 0; i < taglibs.length; i++) {
            String resourcePath = context.findTaglib(taglibs[i]);
            // FIXME - Servlet 2.4 DTD implies that the location MUST be
            // a context-relative path starting with '/'?
            if (!resourcePath.startsWith("/")) {
                resourcePath = "/WEB-INF/" + resourcePath;
            }
            if (log.isTraceEnabled()) {
                log.trace("   Adding path '" + resourcePath +
                    "' for URI '" + taglibs[i] + "'");
            }
            resourcePaths.add(resourcePath);
        }

        DirContext resources = context.getResources();
        if (resources != null) {
            tldScanResourcePathsWebInf(resources, "/WEB-INF", resourcePaths);
        }

        // Return the completed set
        return (resourcePaths);

    }

    /*
     * Scans the web application's subdirectory identified by rootPath,
     * along with its subdirectories, for TLDs.
     *
     * Initially, rootPath equals /WEB-INF. The /WEB-INF/classes and
     * /WEB-INF/lib subdirectories are excluded from the search, as per the
     * JSP 2.0 spec.
     *
     * @param resources The web application's resources
     * @param rootPath The path whose subdirectories are to be searched for
     * TLDs
     * @param tldPaths The set of TLD resource paths to add to
     */
    private void tldScanResourcePathsWebInf(DirContext resources,
                                            String rootPath,
                                            Set tldPaths) 
            throws IOException {

        if (log.isTraceEnabled()) {
            log.trace("  Scanning TLDs in " + rootPath + " subdirectory");
        }

        try {
            NamingEnumeration items = resources.list(rootPath);
            while (items.hasMoreElements()) {
                NameClassPair item = (NameClassPair) items.nextElement();
                String resourcePath = rootPath + "/" + item.getName();
                if (!resourcePath.endsWith(".tld")
                        && (resourcePath.startsWith("/WEB-INF/classes")
                            || resourcePath.startsWith("/WEB-INF/lib"))) {
                    continue;
                }
                if (resourcePath.endsWith(".tld")) {
                    if (log.isTraceEnabled()) {
                        log.trace("   Adding path '" + resourcePath + "'");
                    }
                    tldPaths.add(resourcePath);
                } else {
                    tldScanResourcePathsWebInf(resources, resourcePath,
                                               tldPaths);
                }
            }
        } catch (NamingException e) {
            ; // Silent catch: it's valid that no /WEB-INF directory exists
        }
    }

    /**
     * Returns a map of the paths to all JAR files that are accessible to the
     * webapp and will be scanned for TLDs.
     *
     * The map always includes all the JARs under WEB-INF/lib, as well as
     * shared JARs in the classloader delegation chain of the webapp's
     * classloader.
     *
     * The latter constitutes a Tomcat-specific extension to the TLD search
     * order defined in the JSP spec. It allows tag libraries packaged as JAR
     * files to be shared by web applications by simply dropping them in a 
     * location that all web applications have access to (e.g.,
     * <CATALINA_HOME>/common/lib).
     *
     * The set of shared JARs to be scanned for TLDs is narrowed down by
     * the <tt>noTldJars</tt> class variable, which contains the names of JARs
     * that are known not to contain any TLDs.
     *
     * @return Map of JAR file paths
     */
    private Map getJarPaths() {

        HashMap jarPathMap = null;

        ClassLoader webappLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader loader = webappLoader;
        while (loader != null) {
            if (loader instanceof URLClassLoader) {
                URL[] urls = ((URLClassLoader) loader).getURLs();
                for (int i=0; i<urls.length; i++) {
                    // Expect file URLs, these are %xx encoded or not depending on
                    // the class loader
                    // This is definitely not as clean as using JAR URLs either
                    // over file or the custom jndi handler, but a lot less
                    // buggy overall
                    File file = null;
                    try {
                        file = new File(urls[i].toURI());
                    } catch (URISyntaxException e) {
                        // Ignore, probably an unencoded char
                        file = new File(urls[i].getFile());
                    }
                    try {
                        file = file.getCanonicalFile();
                    } catch (IOException e) {
                        // Ignore
                    }
                    if (!file.exists()) {
                        continue;
                    }
                    String path = file.getAbsolutePath();
                    if (!path.endsWith(".jar")) {
                        continue;
                    }
                    /*
                     * Scan all JARs from WEB-INF/lib, plus any shared JARs
                     * that are not known not to contain any TLDs
                     */
                    if (loader == webappLoader
                            || noTldJars == null
                            || !noTldJars.contains(file.getName())) {
                        if (jarPathMap == null) {
                            jarPathMap = new HashMap();
                            jarPathMap.put(path, file);
                        } else if (!jarPathMap.containsKey(path)) {
                            jarPathMap.put(path, file);
                        }
                    }
                }
            }
            loader = loader.getParent();
        }

        return jarPathMap;
    }
}

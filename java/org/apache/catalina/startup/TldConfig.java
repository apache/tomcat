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


import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.servlet.ServletContext;

import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.util.StringManager;
import org.apache.jasper.JasperException;
import org.apache.tomcat.util.digester.Digester;
import org.xml.sax.InputSource;


/**
 * Startup event listener for a <b>Context</b> that configures application
 * listeners configured in any TLD files.
 *
 * @author Craig R. McClanahan
 * @author Jean-Francois Arcand
 * @author Costin Manolache
 */
public final class TldConfig  implements LifecycleListener {

    // Names of JARs that are known not to contain any TLDs
    private static HashSet<String> noTldJars;

    private static org.apache.juli.logging.Log log=
        org.apache.juli.logging.LogFactory.getLog( TldConfig.class );

    /**
     * The string resources for this package.
     */
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);

    /**
     * The <code>Digester</code>s available to process tld files.
     */
    private static Digester[] tldDigesters = new Digester[4];

    private static final TldRuleSet tldRuleSet = new TldRuleSet();

    private static final String FILE_PROTOCOL = "file:";
    private static final String JAR_FILE_SUFFIX = ".jar";

    /*
     * Initializes the set of JARs that are known not to contain any TLDs
     * 
     * Keep in sync with o.a.jasper.compiler.TldLocationsCache
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

    /**
     * Create (if necessary) and return a Digester configured to process the
     * tld.
     */
    private static Digester createTldDigester(boolean namespaceAware,
            boolean validation) {
        
        Digester digester = null;
        if (!namespaceAware && !validation) {
            if (tldDigesters[0] == null) {
                tldDigesters[0] = DigesterFactory.newDigester(validation,
                        namespaceAware, tldRuleSet);
            }
            digester = tldDigesters[0];
        } else if (!namespaceAware && validation) {
            if (tldDigesters[1] == null) {
                tldDigesters[1] = DigesterFactory.newDigester(validation,
                        namespaceAware, tldRuleSet);
            }
            digester = tldDigesters[1];
        } else if (namespaceAware && !validation) {
            if (tldDigesters[2] == null) {
                tldDigesters[2] = DigesterFactory.newDigester(validation,
                        namespaceAware, tldRuleSet);
            }
            digester = tldDigesters[2];
        } else {
            if (tldDigesters[3] == null) {
                tldDigesters[3] = DigesterFactory.newDigester(validation,
                        namespaceAware, tldRuleSet);
            }
            digester = tldDigesters[3];
        }
        return digester;
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * The Context we are associated with.
     */
    private Context context = null;


    /**
     * The <code>Digester</code> we will use to process tag library
     * descriptor files.
     */
    private Digester tldDigester = null;


    /**
     * Attribute value used to turn on/off TLD validation
     */
    private boolean tldValidation = false;


    /**
     * Attribute value used to turn on/off TLD  namespace awarenes.
     */
    private boolean tldNamespaceAware = false;

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
        this.tldValidation = tldValidation;
    }

    /**
     * Get the server.xml &lt;host&gt; attribute's xmlValidation.
     * @return true if validation is enabled.
     *
     */
    public boolean getTldValidation(){
        return this.tldValidation;
    }

    /**
     * Get the server.xml &lt;host&gt; attribute's xmlNamespaceAware.
     * @return true if namespace awarenes is enabled.
     *
     */
    public boolean getTldNamespaceAware(){
        return this.tldNamespaceAware;
    }


    /**
     * Set the namespace aware feature of the XML parser used when
     * parsing xml instances.
     * @param tldNamespaceAware true to enable namespace awareness
     */
    public void setTldNamespaceAware(boolean tldNamespaceAware){
        this.tldNamespaceAware = tldNamespaceAware;
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
        if(log.isDebugEnabled()) {
            log.debug( "Add tld listener " + s);
        }
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

        scanJars();
        processTldsInFileSystem("/WEB-INF/");

        String list[] = getTldListeners();

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

    /**
     * Scans all JARs accessible to the webapp's classloader and its
     * parent classloaders for TLDs.
     * 
     * The list of JARs always includes the JARs under WEB-INF/lib, as well as
     * all shared JARs in the classloader delegation chain of the webapp's
     * classloader.
     *
     * Considering JARs in the classloader delegation chain constitutes a
     * Tomcat-specific extension to the TLD search
     * order defined in the JSP spec. It allows tag libraries packaged as JAR
     * files to be shared by web applications by simply dropping them in a 
     * location that all web applications have access to (e.g.,
     * <CATALINA_HOME>/common/lib).
     *
     * The set of shared JARs to be scanned for TLDs is narrowed down by
     * the <tt>noTldJars</tt> class variable, which contains the names of JARs
     * that are known not to contain any TLDs.
     * 
     * Keep in sync with o.a.jasper.compiler.TldLocationsCache
     */
    private void scanJars() throws Exception {

        ClassLoader webappLoader
            = Thread.currentThread().getContextClassLoader();
        ClassLoader loader = webappLoader;

        while (loader != null) {
            if (loader instanceof URLClassLoader) {
                URL[] urls = ((URLClassLoader) loader).getURLs();
                for (int i=0; i<urls.length; i++) {
                    URLConnection conn = urls[i].openConnection();
                    if (conn instanceof JarURLConnection) {
                        if (needScanJar(loader, webappLoader,
                                        ((JarURLConnection) conn).getJarFile().getName())) {
                            scanJar((JarURLConnection) conn);
                        }
                    } else {
                        String urlStr = urls[i].toString();
                        if (urlStr.startsWith(FILE_PROTOCOL)
                                && urlStr.endsWith(JAR_FILE_SUFFIX)
                                && needScanJar(loader, webappLoader, urlStr)) {
                            URL jarURL = new URL("jar:" + urlStr + "!/");
                            scanJar((JarURLConnection) jarURL.openConnection());
                        }
                    }
                }
            }

            loader = loader.getParent();
        }
    }

    /**
     * Scans the given JarURLConnection for TLD files located in META-INF
     * (or a subdirectory of it), adding an implicit map entry to the taglib
     * map for any TLD that has a <uri> element.
     *
     * @param conn The JarURLConnection to the JAR file to scan
     * 
     * Keep in sync with o.a.jasper.compiler.TldLocationsCache
     */
    private void scanJar(JarURLConnection conn) throws JasperException {

        JarFile jarFile = null;
        String resourcePath = conn.getJarFileURL().toString();

        if (log.isDebugEnabled()) {
            log.debug("Scanning JAR at resource path '" + resourcePath + "'");
        }

        try {
            conn.setUseCaches(false);
            jarFile = conn.getJarFile();
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith("META-INF/")) continue;
                if (!name.endsWith(".tld")) continue;
                InputStream stream = jarFile.getInputStream(entry);
                if (log.isTraceEnabled()) {
                    log.trace("  Processing TLD at '" + name + "'");
                }
                try {
                    tldScanStream(
                            new InputSource(jarFile.getInputStream(entry)));
                } catch (Exception e) {
                    log.error(sm.getString("contextConfig.tldEntryException",
                            name, resourcePath, context.getPath()), e);
                } finally {
                    if (stream != null) {
                        try {
                            stream.close();
                        } catch (Throwable t) {
                            // do nothing
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.error(sm.getString("contextConfig.tldJarException",
                    resourcePath, context.getPath()), ex);

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


    /**
     * Searches the filesystem under /WEB-INF for any TLD files, and adds
     * an implicit map entry to the taglib map for any TLD that has a <uri>
     * element.
     * 
     * Keep in sync with o.a.jasper.compiler.TldLocationsCache
     */
    private void processTldsInFileSystem(String startPath)
            throws Exception {

        ServletContext ctxt = context.getServletContext();

        Set<String> dirList = ctxt.getResourcePaths(startPath);
        if (dirList != null) {
            Iterator<String> it = dirList.iterator();
            while (it.hasNext()) {
                String path = it.next();
                if (path.endsWith("/")) {
                    processTldsInFileSystem(path);
                }
                if (!path.endsWith(".tld")) {
                    continue;
                }
                InputStream stream = ctxt.getResourceAsStream(path);
                try {
                    tldScanStream(new InputSource(stream));
                } finally {
                    if (stream != null) {
                        try {
                            stream.close();
                        } catch (Throwable t) {
                            // do nothing
                        }
                    }
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

        synchronized (tldDigester) {
            try {
                tldDigester.push(this);
                tldDigester.parse(resourceStream);
            } finally {
                tldDigester.reset();
            }
        }

    }

    /*
     * Determines if the JAR file with the given <tt>jarPath</tt> needs to be
     * scanned for TLDs.
     *
     * @param loader The current classloader in the parent chain
     * @param webappLoader The webapp classloader
     * @param jarPath The JAR file path
     *
     * @return TRUE if the JAR file identified by <tt>jarPath</tt> needs to be
     * scanned for TLDs, FALSE otherwise
     * 
     * Keep in sync with o.a.jasper.compiler.TldLocationsCache
     */
    private boolean needScanJar(ClassLoader loader, ClassLoader webappLoader,
                                String jarPath) {
        if (loader == webappLoader) {
            // JARs under WEB-INF/lib must be scanned unconditionally according
            // to the spec.
            return true;
        } else {
            String jarName = jarPath;
            int slash = jarPath.lastIndexOf('/');
            if (slash >= 0) {
                jarName = jarPath.substring(slash + 1);
            }
            return (!noTldJars.contains(jarName));
        }
    }

    public void lifecycleEvent(LifecycleEvent event) {
        // Identify the context we are associated with
        try {
            context = (Context) event.getLifecycle();
        } catch (ClassCastException e) {
            log.error(sm.getString("tldConfig.cce", event.getLifecycle()), e);
            return;
        }
        
        if (event.getType().equals(Lifecycle.INIT_EVENT)) {
            init();
        } else if (event.getType().equals(Lifecycle.START_EVENT)) {
            try {
                execute();
            } catch (Exception e) {
                log.error(sm.getString(
                        "tldConfig.execute", context.getPath()), e);
            }
        } // Ignore the other event types - nothing to do 
    }
    
    private void init() {
        if (tldDigester == null){
            // (1)  check if the attribute has been defined
            //      on the context element.
            setTldValidation(context.getTldValidation());
            setTldNamespaceAware(context.getTldNamespaceAware());
    
            // (2) if the attribute wasn't defined on the context
            //     and override is not set on the context try the host.
            if (!context.getOverride()) {
                if (!tldValidation) {
                    setTldValidation(
                            ((StandardHost) context.getParent()).getXmlValidation());
                }
    
                if (!tldNamespaceAware) {
                    setTldNamespaceAware(
                      ((StandardHost) context.getParent()).getXmlNamespaceAware());
                }
            }
            tldDigester = createTldDigester(tldNamespaceAware, tldValidation);
        }
    }
}

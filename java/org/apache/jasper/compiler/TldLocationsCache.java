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

package org.apache.jasper.compiler;

import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.xml.sax.InputSource;

import javax.servlet.ServletContext;

import org.apache.jasper.Constants;
import org.apache.jasper.JasperException;
import org.apache.jasper.xmlparser.ParserUtils;
import org.apache.jasper.xmlparser.TreeNode;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * A container for all tag libraries that are defined "globally"
 * for the web application.
 * 
 * Tag Libraries can be defined globally in one of two ways:
 *   1. Via <taglib> elements in web.xml:
 *      the uri and location of the tag-library are specified in
 *      the <taglib> element.
 *   2. Via packaged jar files that contain .tld files
 *      within the META-INF directory, or some subdirectory
 *      of it. The taglib is 'global' if it has the <uri>
 *      element defined.
 *
 * A mapping between the taglib URI and its associated TaglibraryInfoImpl
 * is maintained in this container.
 * Actually, that's what we'd like to do. However, because of the
 * way the classes TagLibraryInfo and TagInfo have been defined,
 * it is not currently possible to share an instance of TagLibraryInfo
 * across page invocations. A bug has been submitted to the spec lead.
 * In the mean time, all we do is save the 'location' where the
 * TLD associated with a taglib URI can be found.
 *
 * When a JSP page has a taglib directive, the mappings in this container
 * are first searched (see method getLocation()).
 * If a mapping is found, then the location of the TLD is returned.
 * If no mapping is found, then the uri specified
 * in the taglib directive is to be interpreted as the location for
 * the TLD of this tag library.
 *
 * @author Pierre Delisle
 * @author Jan Luehe
 */

public class TldLocationsCache {

    // Logger
    private Log log = LogFactory.getLog(TldLocationsCache.class);

    /**
     * The types of URI one may specify for a tag library
     */
    public static final int ABS_URI = 0;
    public static final int ROOT_REL_URI = 1;
    public static final int NOROOT_REL_URI = 2;

    private static final String WEB_XML = "/WEB-INF/web.xml";
    private static final String FILE_PROTOCOL = "file:";
    private static final String JAR_FILE_SUFFIX = ".jar";

    // Names of JARs that are known not to contain any TLDs
    private static HashSet<String> noTldJars;

    /**
     * The mapping of the 'global' tag library URI to the location (resource
     * path) of the TLD associated with that tag library. The location is
     * returned as a String array:
     *    [0] The location
     *    [1] If the location is a jar file, this is the location of the tld.
     */
    private Hashtable mappings;

    private boolean initialized;
    private ServletContext ctxt;
    private boolean redeployMode;

    //*********************************************************************
    // Constructor and Initilizations

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
    
    public TldLocationsCache(ServletContext ctxt) {
        this(ctxt, true);
    }

    /** Constructor. 
     *
     * @param ctxt the servlet context of the web application in which Jasper 
     * is running
     * @param redeployMode if true, then the compiler will allow redeploying 
     * a tag library from the same jar, at the expense of slowing down the
     * server a bit. Note that this may only work on JDK 1.3.1_01a and later,
     * because of JDK bug 4211817 fixed in this release.
     * If redeployMode is false, a faster but less capable mode will be used.
     */
    public TldLocationsCache(ServletContext ctxt, boolean redeployMode) {
        this.ctxt = ctxt;
        this.redeployMode = redeployMode;
        mappings = new Hashtable();
        initialized = false;
    }

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
     * Gets the 'location' of the TLD associated with the given taglib 'uri'.
     *
     * Returns null if the uri is not associated with any tag library 'exposed'
     * in the web application. A tag library is 'exposed' either explicitly in
     * web.xml or implicitly via the uri tag in the TLD of a taglib deployed
     * in a jar file (WEB-INF/lib).
     * 
     * @param uri The taglib uri
     *
     * @return An array of two Strings: The first element denotes the real
     * path to the TLD. If the path to the TLD points to a jar file, then the
     * second element denotes the name of the TLD entry in the jar file.
     * Returns null if the uri is not associated with any tag library 'exposed'
     * in the web application.
     */
    public String[] getLocation(String uri) throws JasperException {
        if (!initialized) {
            init();
        }
        return (String[]) mappings.get(uri);
    }

    /** 
     * Returns the type of a URI:
     *     ABS_URI
     *     ROOT_REL_URI
     *     NOROOT_REL_URI
     */
    public static int uriType(String uri) {
        if (uri.indexOf(':') != -1) {
            return ABS_URI;
        } else if (uri.startsWith("/")) {
            return ROOT_REL_URI;
        } else {
            return NOROOT_REL_URI;
        }
    }

    private void init() throws JasperException {
        if (initialized) return;
        try {
            processWebDotXml();
            scanJars();
            processTldsInFileSystem("/WEB-INF/");
            initialized = true;
        } catch (Exception ex) {
            throw new JasperException(Localizer.getMessage(
                    "jsp.error.internal.tldinit", ex.getMessage()));
        }
    }

    /*
     * Populates taglib map described in web.xml.
     */    
    private void processWebDotXml() throws Exception {

        InputStream is = null;

        try {
            // Acquire input stream to web application deployment descriptor
            String altDDName = (String)ctxt.getAttribute(
                                                    Constants.ALT_DD_ATTR);
            URL uri = null;
            if (altDDName != null) {
                try {
                    uri = new URL(FILE_PROTOCOL+altDDName.replace('\\', '/'));
                } catch (MalformedURLException e) {
                    if (log.isWarnEnabled()) {
                        log.warn(Localizer.getMessage(
                                            "jsp.error.internal.filenotfound",
                                            altDDName));
                    }
                }
            } else {
                uri = ctxt.getResource(WEB_XML);
                if (uri == null && log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage(
                                            "jsp.error.internal.filenotfound",
                                            WEB_XML));
                }
            }

            if (uri == null) {
                return;
            }
            is = uri.openStream();
            InputSource ip = new InputSource(is);
            ip.setSystemId(uri.toExternalForm()); 

            // Parse the web application deployment descriptor
            TreeNode webtld = null;
            // altDDName is the absolute path of the DD
            if (altDDName != null) {
                webtld = new ParserUtils().parseXMLDocument(altDDName, ip);
            } else {
                webtld = new ParserUtils().parseXMLDocument(WEB_XML, ip);
            }

            // Allow taglib to be an element of the root or jsp-config (JSP2.0)
            TreeNode jspConfig = webtld.findChild("jsp-config");
            if (jspConfig != null) {
                webtld = jspConfig;
            }
            Iterator taglibs = webtld.findChildren("taglib");
            while (taglibs.hasNext()) {

                // Parse the next <taglib> element
                TreeNode taglib = (TreeNode) taglibs.next();
                String tagUri = null;
                String tagLoc = null;
                TreeNode child = taglib.findChild("taglib-uri");
                if (child != null)
                    tagUri = child.getBody();
                child = taglib.findChild("taglib-location");
                if (child != null)
                    tagLoc = child.getBody();

                // Save this location if appropriate
                if (tagLoc == null)
                    continue;
                if (uriType(tagLoc) == NOROOT_REL_URI)
                    tagLoc = "/WEB-INF/" + tagLoc;
                String tagLoc2 = null;
                if (tagLoc.endsWith(JAR_FILE_SUFFIX)) {
                    tagLoc = ctxt.getResource(tagLoc).toString();
                    tagLoc2 = "META-INF/taglib.tld";
                }
                mappings.put(tagUri, new String[] { tagLoc, tagLoc2 });
            }
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Throwable t) {}
            }
        }
    }

    /**
     * Scans the given JarURLConnection for TLD files located in META-INF
     * (or a subdirectory of it), adding an implicit map entry to the taglib
     * map for any TLD that has a <uri> element.
     *
     * @param conn The JarURLConnection to the JAR file to scan
     * @param ignore true if any exceptions raised when processing the given
     * JAR should be ignored, false otherwise
     */
    private void scanJar(JarURLConnection conn, boolean ignore)
                throws JasperException {

        JarFile jarFile = null;
        String resourcePath = conn.getJarFileURL().toString();
        try {
            if (redeployMode) {
                conn.setUseCaches(false);
            }
            jarFile = conn.getJarFile();
            Enumeration entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = (JarEntry) entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith("META-INF/")) continue;
                if (!name.endsWith(".tld")) continue;
                InputStream stream = jarFile.getInputStream(entry);
                try {
                    String uri = getUriFromTld(resourcePath, stream);
                    // Add implicit map entry only if its uri is not already
                    // present in the map
                    if (uri != null && mappings.get(uri) == null) {
                        mappings.put(uri, new String[]{ resourcePath, name });
                    }
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
            if (!redeployMode) {
                // if not in redeploy mode, close the jar in case of an error
                if (jarFile != null) {
                    try {
                        jarFile.close();
                    } catch (Throwable t) {
                        // ignore
                    }
                }
            }
            if (!ignore) {
                throw new JasperException(ex);
            }
        } finally {
            if (redeployMode) {
                // if in redeploy mode, always close the jar
                if (jarFile != null) {
                    try {
                        jarFile.close();
                    } catch (Throwable t) {
                        // ignore
                    }
                }
            }
        }
    }

    /*
     * Searches the filesystem under /WEB-INF for any TLD files, and adds
     * an implicit map entry to the taglib map for any TLD that has a <uri>
     * element.
     */
    private void processTldsInFileSystem(String startPath)
            throws Exception {

        Set dirList = ctxt.getResourcePaths(startPath);
        if (dirList != null) {
            Iterator it = dirList.iterator();
            while (it.hasNext()) {
                String path = (String) it.next();
                if (path.endsWith("/")) {
                    processTldsInFileSystem(path);
                }
                if (!path.endsWith(".tld")) {
                    continue;
                }
                InputStream stream = ctxt.getResourceAsStream(path);
                String uri = null;
                try {
                    uri = getUriFromTld(path, stream);
                } finally {
                    if (stream != null) {
                        try {
                            stream.close();
                        } catch (Throwable t) {
                            // do nothing
                        }
                    }
                }
                // Add implicit map entry only if its uri is not already
                // present in the map
                if (uri != null && mappings.get(uri) == null) {
                    mappings.put(uri, new String[] { path, null });
                }
            }
        }
    }

    /*
     * Returns the value of the uri element of the given TLD, or null if the
     * given TLD does not contain any such element.
     */
    private String getUriFromTld(String resourcePath, InputStream in) 
        throws JasperException
    {
        // Parse the tag library descriptor at the specified resource path
        TreeNode tld = new ParserUtils().parseXMLDocument(resourcePath, in);
        TreeNode uri = tld.findChild("uri");
        if (uri != null) {
            String body = uri.getBody();
            if (body != null)
                return body;
        }

        return null;
    }

    /*
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
                            scanJar((JarURLConnection) conn, true);
                        }
                    } else {
                        String urlStr = urls[i].toString();
                        if (urlStr.startsWith(FILE_PROTOCOL)
                                && urlStr.endsWith(JAR_FILE_SUFFIX)
                                && needScanJar(loader, webappLoader, urlStr)) {
                            URL jarURL = new URL("jar:" + urlStr + "!/");
                            scanJar((JarURLConnection) jarURL.openConnection(),
                                    true);
                        }
                    }
                }
            }

            loader = loader.getParent();
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
}

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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.servlet.ServletContext;

import org.apache.jasper.JasperException;
import org.apache.jasper.util.ExceptionUtils;
import org.apache.jasper.xmlparser.ParserUtils;
import org.apache.jasper.xmlparser.TreeNode;
import org.apache.tomcat.JarScanner;
import org.apache.tomcat.JarScannerCallback;

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

    /**
     * The types of URI one may specify for a tag library
     */
    public static final int ABS_URI = 0;
    public static final int ROOT_REL_URI = 1;
    public static final int NOROOT_REL_URI = 2;

    private static final String WEB_INF = "/WEB-INF/";
    private static final String WEB_INF_LIB = "/WEB-INF/lib/";
    private static final String JAR_EXT = ".jar";
    private static final String TLD_EXT = ".tld";

    // Names of JARs that are known not to contain any TLDs
    private static Set<String> noTldJars = null;

    /**
     * The mapping of the 'global' tag library URI to the location (resource
     * path) of the TLD associated with that tag library. The location is
     * returned as a String array:
     *    [0] The location
     *    [1] If the location is a jar file, this is the location of the tld.
     */
    private Hashtable<String, TldLocation> mappings;

    private boolean initialized;
    private ServletContext ctxt;

    /** Constructor. 
     *
     * @param ctxt the servlet context of the web application in which Jasper 
     * is running
     */
    public TldLocationsCache(ServletContext ctxt) {
        this.ctxt = ctxt;
        mappings = new Hashtable<String, TldLocation>();
        initialized = false;
    }

    /**
     * Sets the list of JARs that are known not to contain any TLDs.
     *
     * @param jarNames List of comma-separated names of JAR files that are 
     * known not to contain any TLDs 
     */
    public static void setNoTldJars(String jarNames) {
        if (jarNames == null) {
            noTldJars = null;
        } else {
            if (noTldJars == null) {
                noTldJars = new HashSet<String>();
            } else {
                noTldJars.clear();
            }
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
    public TldLocation getLocation(String uri) throws JasperException {
        if (!initialized) {
            init();
        }
        return mappings.get(uri);
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

    /*
     * Keep processing order in sync with o.a.c.startup.TldConfig
     *
     * This supports a Tomcat-specific extension to the TLD search
     * order defined in the JSP spec. It allows tag libraries packaged as JAR
     * files to be shared by web applications by simply dropping them in a 
     * location that all web applications have access to (e.g.,
     * <CATALINA_HOME>/lib). It also supports some of the weird and
     * wonderful arrangements present when Tomcat gets embedded.
     *
     */
    private void init() throws JasperException {
        if (initialized) return;
        try {
            tldScanWebXml();
            tldScanResourcePaths(WEB_INF);
            
            JarScanner jarScanner = JarScannerFactory.getJarScanner(ctxt);
            if (jarScanner != null) {
                jarScanner.scan(ctxt,
                        Thread.currentThread().getContextClassLoader(),
                        new TldJarScannerCallback(), noTldJars);
            }

            initialized = true;
        } catch (Exception ex) {
            throw new JasperException(Localizer.getMessage(
                    "jsp.error.internal.tldinit", ex.getMessage()));
        }
    }

    private class TldJarScannerCallback implements JarScannerCallback {

        @Override
        public void scan(JarURLConnection urlConn) throws IOException {
            tldScanJar(urlConn);
        }

        @Override
        public void scan(File file) throws IOException {
            File metaInf = new File(file, "META-INF");
            if (metaInf.isDirectory()) {
                tldScanDir(metaInf);
            }
        }
    }

    /*
     * Populates taglib map described in web.xml.
     * 
     * This is not kept in sync with o.a.c.startup.TldConfig as the Jasper only
     * needs the URI to TLD mappings from scan web.xml whereas TldConfig needs
     * to scan the actual TLD files.
     */    
    private void tldScanWebXml() throws Exception {

        WebXml webXml = null;
        try {
            webXml = new WebXml(ctxt);
            
            // Parse the web application deployment descriptor
            TreeNode webtld = null;
            webtld = new ParserUtils().parseXMLDocument(webXml.getSystemId(),
                    webXml.getInputSource());

            // Allow taglib to be an element of the root or jsp-config (JSP2.0)
            TreeNode jspConfig = webtld.findChild("jsp-config");
            if (jspConfig != null) {
                webtld = jspConfig;
            }
            Iterator<TreeNode> taglibs = webtld.findChildren("taglib");
            while (taglibs.hasNext()) {

                // Parse the next <taglib> element
                TreeNode taglib = taglibs.next();
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
                TldLocation location;
                if (tagLoc.endsWith(JAR_EXT)) {
                    location = new TldLocation("META-INF/taglib.tld", ctxt.getResource(tagLoc).toString());
                } else {
                    location = new TldLocation(tagLoc);
                }
                mappings.put(tagUri, location);
            }
        } finally {
            if (webXml != null) {
                webXml.close();
            }
        }
    }

    /*
     * Scans the web application's sub-directory identified by startPath,
     * along with its sub-directories, for TLDs and adds an implicit map entry
     * to the taglib map for any TLD that has a <uri> element.
     *
     * Initially, rootPath equals /WEB-INF/. The /WEB-INF/classes and
     * /WEB-INF/lib sub-directories are excluded from the search, as per the
     * JSP 2.0 spec.
     * 
     * Keep code in sync with o.a.c.startup.TldConfig
     */
    private void tldScanResourcePaths(String startPath)
            throws Exception {

        Set<String> dirList = ctxt.getResourcePaths(startPath);
        if (dirList != null) {
            Iterator<String> it = dirList.iterator();
            while (it.hasNext()) {
                String path = it.next();
                if (!path.endsWith(TLD_EXT)
                        && (path.startsWith(WEB_INF_LIB)
                                || path.startsWith("/WEB-INF/classes/"))) {
                    continue;
                }
                if (path.endsWith(TLD_EXT)) {
                    if (path.startsWith("/WEB-INF/tags/") &&
                            !path.endsWith("implicit.tld")) {
                        continue;
                    }
                    InputStream stream = ctxt.getResourceAsStream(path);
                    try {
                        tldScanStream(path, null, stream);
                    } finally {
                        if (stream != null) {
                            try {
                                stream.close();
                            } catch (Throwable t) {
                                // do nothing
                            }
                        }
                    }
                } else {
                    tldScanResourcePaths(path);
                }
            }
        }
    }

    /*
     * Scans the directory identified by startPath, along with its
     * sub-directories, for TLDs.
     *
     * Keep in sync with o.a.c.startup.TldConfig
     */
    private void tldScanDir(File start) throws IOException {

        File[] fileList = start.listFiles();
        if (fileList != null) {
            for (int i = 0; i < fileList.length; i++) {
                // Scan recursively
                if (fileList[i].isDirectory()) {
                    tldScanDir(fileList[i]);
                } else if (fileList[i].getAbsolutePath().endsWith(TLD_EXT)) {
                    InputStream stream = null;
                    try {
                        stream = new FileInputStream(fileList[i]);
                        tldScanStream(
                                fileList[i].toURI().toString(), null, stream);
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
    }

    /*
     * Scans the given JarURLConnection for TLD files located in META-INF
     * (or a subdirectory of it), adding an implicit map entry to the taglib
     * map for any TLD that has a <uri> element.
     *
     * @param conn The JarURLConnection to the JAR file to scan
     * 
     * Keep in sync with o.a.c.startup.TldConfig
     */
    private void tldScanJar(JarURLConnection conn) throws IOException {

        JarFile jarFile = null;
        String resourcePath = conn.getJarFileURL().toString();
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
                tldScanStream(resourcePath, name, stream);
            }
        } finally {
            if (jarFile != null) {
                try {
                    jarFile.close();
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                }
            }
        }
    }

    /*
     * Scan the TLD contents in the specified input stream and add any new URIs
     * to the map.
     * 
     * @param resourcePath  Path of the resource
     * @param entryName     If the resource is a JAR file, the name of the entry
     *                      in the JAR file
     * @param stream        The input stream for the resource
     * @throws IOException
     */
    private void tldScanStream(String resourcePath, String entryName,
            InputStream stream) throws IOException {
        try {
            // Parse the tag library descriptor at the specified resource path
            String uri = null;

            TreeNode tld =
                new ParserUtils().parseXMLDocument(resourcePath, stream);
            TreeNode uriNode = tld.findChild("uri");
            if (uriNode != null) {
                String body = uriNode.getBody();
                if (body != null)
                    uri = body;
            }

            // Add implicit map entry only if its uri is not already
            // present in the map
            if (uri != null && mappings.get(uri) == null) {
                TldLocation location;
                if (entryName == null) {
                    location = new TldLocation(resourcePath);
                } else {
                    location = new TldLocation(entryName, resourcePath);
                }
                mappings.put(uri, location);
            }
        } catch (JasperException e) {
            // Hack - makes exception handling simpler
            throw new IOException(e);
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

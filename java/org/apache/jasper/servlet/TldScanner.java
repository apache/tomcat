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
package org.apache.jasper.servlet;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.ServletContext;
import jakarta.servlet.descriptor.JspConfigDescriptor;
import jakarta.servlet.descriptor.TaglibDescriptor;

import org.apache.jasper.compiler.JarScannerFactory;
import org.apache.jasper.compiler.Localizer;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.Jar;
import org.apache.tomcat.JarScanType;
import org.apache.tomcat.JarScanner;
import org.apache.tomcat.JarScannerCallback;
import org.apache.tomcat.util.descriptor.tld.ImplicitTldRuleSet;
import org.apache.tomcat.util.descriptor.tld.TaglibXml;
import org.apache.tomcat.util.descriptor.tld.TldParser;
import org.apache.tomcat.util.descriptor.tld.TldResourcePath;
import org.xml.sax.SAXException;

/**
 * Scans for and loads Tag Library Descriptors contained in a web application.
 */
public class TldScanner {
    private final Log log = LogFactory.getLog(TldScanner.class); // must not be static
    private static final String MSG = "org.apache.jasper.servlet.TldScanner";
    private static final String TLD_EXT = ".tld";
    private static final String WEB_INF = "/WEB-INF/";
    private static final String WEB_INF_TAGS = "/WEB-INF/tags/";
    private static final String IMPLICIT_TLD = "/implicit.tld";
    private final ServletContext context;
    private final TldParser tldParser;
    private final TldParser implicitTldParser;
    private final Map<String,TldResourcePath> uriTldResourcePathMap = new HashMap<>();
    private final Map<TldResourcePath,TaglibXml> tldResourcePathTaglibXmlMap = new HashMap<>();
    private final List<String> listeners = new ArrayList<>();

    /**
     * Initialise with the application's ServletContext.
     *
     * @param context        the application's servletContext
     * @param namespaceAware should the XML parser used to parse TLD files be configured to be name space aware
     * @param validation     should the XML parser used to parse TLD files be configured to use validation
     * @param blockExternal  should the XML parser used to parse TLD files be configured to be block references to
     *                           external entities
     */
    public TldScanner(ServletContext context, boolean namespaceAware, boolean validation, boolean blockExternal) {
        this.context = context;

        this.tldParser = new TldParser(namespaceAware, validation, blockExternal);
        this.implicitTldParser =
                new TldParser(namespaceAware, validation, new ImplicitTldRuleSet(), blockExternal);
    }

    /**
     * Scan for TLDs in all places defined by the specification:
     * <ol>
     * <li>Tag libraries defined by the platform</li>
     * <li>Entries from &lt;jsp-config&gt; in web.xml</li>
     * <li>A resources under /WEB-INF</li>
     * <li>In jar files from /WEB-INF/lib</li>
     * <li>Additional entries from the container</li>
     * </ol>
     *
     * @throws IOException  if there was a problem scanning for or loading a TLD
     * @throws SAXException if there was a problem parsing a TLD
     */
    public void scan() throws IOException, SAXException {
        scanPlatform();
        scanJspConfig();
        scanResourcePaths(WEB_INF);
        scanJars();
    }

    /**
     * Returns the map of URI to TldResourcePath built by this scanner.
     *
     * @return the map of URI to TldResourcePath
     */
    public Map<String,TldResourcePath> getUriTldResourcePathMap() {
        return uriTldResourcePathMap;
    }

    /**
     * Returns the map of TldResourcePath to parsed XML files built by this scanner.
     *
     * @return the map of TldResourcePath to parsed XML files
     */
    public Map<TldResourcePath,TaglibXml> getTldResourcePathTaglibXmlMap() {
        return tldResourcePathTaglibXmlMap;
    }

    /**
     * Returns a list of all listeners declared by scanned TLDs.
     *
     * @return a list of listener class names
     */
    public List<String> getListeners() {
        return listeners;
    }

    /**
     * Set the class loader used by the digester to create objects as a result of this scan. Normally this only needs to
     * be set when using JspC.
     *
     * @param classLoader Class loader to use when creating new objects while parsing TLDs
     */
    public void setClassLoader(ClassLoader classLoader) {
        tldParser.setClassLoader(classLoader);
        implicitTldParser.setClassLoader(classLoader);
    }

    /**
     * Scan for TLDs required by the platform specification.
     */
    protected void scanPlatform() {
    }

    /**
     * Scan for TLDs defined in &lt;jsp-config&gt;.
     *
     * @throws IOException  Error reading resources
     * @throws SAXException XML parsing error
     */
    protected void scanJspConfig() throws IOException, SAXException {
        JspConfigDescriptor jspConfigDescriptor = context.getJspConfigDescriptor();
        if (jspConfigDescriptor == null) {
            return;
        }

        Collection<TaglibDescriptor> descriptors = jspConfigDescriptor.getTaglibs();
        for (TaglibDescriptor descriptor : descriptors) {
            String taglibURI = descriptor.getTaglibURI();
            String resourcePath = descriptor.getTaglibLocation();
            // Note: Whilst the Servlet 2.4 DTD implies that the location must
            // be a context-relative path starting with '/', JSP.7.3.6.1 states
            // explicitly how paths that do not start with '/' should be
            // handled.
            if (!resourcePath.startsWith("/")) {
                resourcePath = WEB_INF + resourcePath;
            }
            if (uriTldResourcePathMap.containsKey(taglibURI)) {
                log.warn(Localizer.getMessage(MSG + ".webxmlSkip", resourcePath, taglibURI));
                continue;
            }

            if (log.isTraceEnabled()) {
                log.trace(Localizer.getMessage(MSG + ".webxmlAdd", resourcePath, taglibURI));
            }

            URL url = context.getResource(resourcePath);
            if (url != null) {
                TldResourcePath tldResourcePath;
                if (resourcePath.endsWith(".jar")) {
                    // if the path points to a jar file, the TLD is presumed to be
                    // inside at META-INF/taglib.tld
                    tldResourcePath = new TldResourcePath(url, resourcePath, "META-INF/taglib.tld");
                } else {
                    tldResourcePath = new TldResourcePath(url, resourcePath);
                }
                // parse TLD but store using the URI supplied in the descriptor
                TaglibXml tld = tldParser.parse(tldResourcePath);
                uriTldResourcePathMap.put(taglibURI, tldResourcePath);
                tldResourcePathTaglibXmlMap.put(tldResourcePath, tld);
                if (tld.getListeners() != null) {
                    listeners.addAll(tld.getListeners());
                }
            } else {
                log.warn(Localizer.getMessage(MSG + ".webxmlFailPathDoesNotExist", resourcePath, taglibURI));
            }
        }
    }

    /**
     * Scan web application resources for TLDs, recursively.
     *
     * @param startPath the directory resource to scan
     *
     * @throws IOException  if there was a problem scanning for or loading a TLD
     * @throws SAXException if there was a problem parsing a TLD
     */
    protected void scanResourcePaths(String startPath) throws IOException, SAXException {

        boolean found = false;
        Set<String> dirList = context.getResourcePaths(startPath);
        if (dirList != null) {
            for (String path : dirList) {
                if (path.startsWith("/WEB-INF/classes/")) {
                    // Skip: JSP.7.3.1
                } else if (path.startsWith("/WEB-INF/lib/")) {
                    // Skip: JSP.7.3.1
                } else if (path.endsWith("/")) {
                    scanResourcePaths(path);
                } else if (path.startsWith(WEB_INF_TAGS)) {
                    // JSP 7.3.1: in /WEB-INF/tags only consider implicit.tld
                    if (path.endsWith(IMPLICIT_TLD)) {
                        found = true;
                        try {
                            parseTld(path);
                        } catch (SAXException e) {
                            // An implicit TLD that is not specification
                            // compliant must not prevent the web application
                            // from starting. It is parsed again when a tag
                            // from the tag file is used and the error will
                            // be reported to the developer at that point.
                            log.warn(Localizer.getMessage(
                                    MSG + ".implicitTldInvalid", path, e.getMessage()));
                        }
                    }
                } else if (path.endsWith(TLD_EXT)) {
                    found = true;
                    parseTld(path);
                }
            }
        }
        if (found) {
            if (log.isDebugEnabled()) {
                log.debug(Localizer.getMessage("jsp.tldCache.tldInResourcePath", startPath));
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug(Localizer.getMessage("jsp.tldCache.noTldInResourcePath", startPath));
            }
        }
    }

    /**
     * Scan for TLDs in JARs in /WEB-INF/lib.
     */
    public void scanJars() {
        JarScanner scanner = JarScannerFactory.getJarScanner(context);
        TldScannerCallback callback = new TldScannerCallback();
        scanner.scan(JarScanType.TLD, context, callback);
        if (callback.scanFoundNoTLDs()) {
            log.info(Localizer.getMessage("jsp.tldCache.noTldSummary"));
        }
    }

    /**
     * Parses a TLD from the given resource path string. If the resource no
     * longer exists - it may have been removed since it was listed by
     * {@link ServletContext#getResourcePaths(String)} - the TLD is skipped and
     * a warning is logged.
     *
     * @param resourcePath the resource path
     * @throws IOException if an I/O error occurs
     * @throws SAXException if a parsing error occurs
     */
    protected void parseTld(String resourcePath) throws IOException, SAXException {
        URL url = context.getResource(resourcePath);
        if (url == null) {
            // The resource may have been removed after it was discovered by
            // getResourcePaths(). Skip it rather than aborting the scan, as is
            // done for a missing resource referenced by web.xml.
            log.warn(Localizer.getMessage(MSG + ".resourcePathDoesNotExist", resourcePath));
            return;
        }
        parseTld(new TldResourcePath(url, resourcePath));
    }

    /**
     * Parses a TLD from the given resource path.
     *
     * @param path the TLD resource path
     * @throws IOException if an I/O error occurs
     * @throws SAXException if a parsing error occurs
     */
    protected void parseTld(TldResourcePath path) throws IOException, SAXException {
        TaglibXml tld;
        if (isImplicitTld(path.getWebappPath())) {
            // JSP.7.3.2: an implicit TLD only allows version information and
            // a short name. The strict rule set rejects everything else, so
            // that such a TLD is rejected here just as it is when the tag
            // file is translated.
            tld = implicitTldParser.parse(path);
        } else {
            tld = tldParser.parse(path);
        }
        String uri = tld.getUri();
        // JSP.7.3.6.1: the first mapping for a taglib URI wins. A TLD with a
        // previously seen URI found via a different resource path (for
        // example, an unpacked JAR in WEB-INF/classes/META-INF or the same
        // TLD shipped in two JARs) is a duplicate.
        boolean duplicateUri = false;
        if (uri != null) {
            duplicateUri = uriTldResourcePathMap.containsKey(uri);
            if (!duplicateUri) {
                uriTldResourcePathMap.put(uri, path);
            }
        }

        if (tldResourcePathTaglibXmlMap.containsKey(path)) {
            // TLD has already been parsed as a result of processing web.xml
            return;
        }

        tldResourcePathTaglibXmlMap.put(path, tld);
        if (tld.getListeners() != null && !duplicateUri) {
            // The listeners of a duplicate tag library have already been
            // registered when the tag library was first discovered.
            listeners.addAll(tld.getListeners());
        }
    }

    private static boolean isImplicitTld(String webappPath) {
        return webappPath != null && webappPath.startsWith(WEB_INF_TAGS) &&
                webappPath.endsWith(IMPLICIT_TLD);
    }

    class TldScannerCallback implements JarScannerCallback {
        private boolean foundJarWithoutTld = false;
        private boolean foundFileWithoutTld = false;


        @Override
        public void scan(Jar jar, String webappPath, boolean isWebapp) throws IOException {
            boolean found = false;
            URL jarFileUrl = jar.getJarFileURL();
            jar.nextEntry();
            for (String entryName = jar.getEntryName(); entryName != null; jar.nextEntry(), entryName =
                    jar.getEntryName()) {
                if (!(entryName.startsWith("META-INF/") && entryName.endsWith(TLD_EXT))) {
                    continue;
                }
                found = true;
                TldResourcePath tldResourcePath = new TldResourcePath(jarFileUrl, webappPath, entryName);
                try {
                    parseTld(tldResourcePath);
                } catch (SAXException e) {
                    throw new IOException(e);
                }
            }
            if (found) {
                if (log.isDebugEnabled()) {
                    log.debug(Localizer.getMessage("jsp.tldCache.tldInJar", jarFileUrl.toString()));
                }
            } else {
                foundJarWithoutTld = true;
                if (log.isDebugEnabled()) {
                    log.debug(Localizer.getMessage("jsp.tldCache.noTldInJar", jarFileUrl.toString()));
                }
            }
        }

        @Override
        public void scan(File file, final String webappPath, boolean isWebapp) throws IOException {
            File metaInf = new File(file, "META-INF");
            if (!metaInf.isDirectory()) {
                return;
            }
            foundFileWithoutTld = false;
            final Path filePath = file.toPath();
            Files.walkFileTree(metaInf.toPath(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path fileName = file.getFileName();
                    if (fileName == null || !fileName.toString().toLowerCase(Locale.ENGLISH).endsWith(TLD_EXT)) {
                        return FileVisitResult.CONTINUE;
                    }

                    foundFileWithoutTld = true;
                    String resourcePath;
                    if (webappPath == null) {
                        resourcePath = null;
                    } else {
                        String subPath = file.subpath(filePath.getNameCount(), file.getNameCount()).toString();
                        if ('/' != File.separatorChar) {
                            subPath = subPath.replace(File.separatorChar, '/');
                        }
                        resourcePath = webappPath + "/" + subPath;
                    }

                    try {
                        URL url = file.toUri().toURL();
                        TldResourcePath path = new TldResourcePath(url, resourcePath);
                        parseTld(path);
                    } catch (SAXException e) {
                        throw new IOException(e);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            if (foundFileWithoutTld) {
                if (log.isDebugEnabled()) {
                    log.debug(Localizer.getMessage("jsp.tldCache.tldInDir", file.getAbsolutePath()));
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug(Localizer.getMessage("jsp.tldCache.noTldInDir", file.getAbsolutePath()));
                }
            }
        }

        @Override
        public void scanWebInfClasses() throws IOException {
            // This is used when scanAllDirectories is enabled and one or more
            // JARs have been unpacked into WEB-INF/classes as happens with some
            // IDEs.

            Set<String> paths = context.getResourcePaths(WEB_INF + "classes/META-INF");
            if (paths == null) {
                return;
            }

            for (String path : paths) {
                if (path.endsWith(TLD_EXT)) {
                    try {
                        parseTld(path);
                    } catch (SAXException e) {
                        throw new IOException(e);
                    }
                }
            }
        }


        boolean scanFoundNoTLDs() {
            return foundJarWithoutTld;
        }
    }
}

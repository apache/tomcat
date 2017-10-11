/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.util.scan;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import javax.servlet.ServletContext;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.Jar;
import org.apache.tomcat.JarScanFilter;
import org.apache.tomcat.JarScanType;
import org.apache.tomcat.JarScanner;
import org.apache.tomcat.JarScannerCallback;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.UriUtil;
import org.apache.tomcat.util.compat.JreCompat;
import org.apache.tomcat.util.res.StringManager;

/**
 * The default {@link JarScanner} implementation scans the WEB-INF/lib directory
 * followed by the provided classloader and then works up the classloader
 * hierarchy. This implementation is sufficient to meet the requirements of the
 * Servlet 3.0 specification as well as to provide a number of Tomcat specific
 * extensions. The extensions are:
 * <ul>
 *   <li>Scanning the classloader hierarchy (enabled by default)</li>
 *   <li>Testing all files to see if they are JARs (disabled by default)</li>
 *   <li>Testing all directories to see if they are exploded JARs
 *       (disabled by default)</li>
 * </ul>
 * All of the extensions may be controlled via configuration.
 */
public class StandardJarScanner implements JarScanner {

    private static final Log log = LogFactory.getLog(StandardJarScanner.class);

    /**
     * The string resources for this package.
     */
    private static final StringManager sm = StringManager.getManager(Constants.Package);

    private static final Set<ClassLoader> CLASSLOADER_HIERARCHY;

    static {
        Set<ClassLoader> cls = new HashSet<>();

        ClassLoader cl = StandardJarScanner.class.getClassLoader();
        while (cl != null) {
            cls.add(cl);
            cl = cl.getParent();
        }

        CLASSLOADER_HIERARCHY = Collections.unmodifiableSet(cls);
    }

    /**
     * Controls the classpath scanning extension.
     */
    private boolean scanClassPath = true;
    public boolean isScanClassPath() {
        return scanClassPath;
    }
    public void setScanClassPath(boolean scanClassPath) {
        this.scanClassPath = scanClassPath;
    }

    /**
     * Controls the JAR file Manifest scanning extension.
     */
    private boolean scanManifest = true;
    public boolean isScanManifest() {
        return scanManifest;
    }
    public void setScanManifest(boolean scanManifest) {
        this.scanManifest = scanManifest;
    }

    /**
     * Controls the testing all files to see of they are JAR files extension.
     */
    private boolean scanAllFiles = false;
    public boolean isScanAllFiles() {
        return scanAllFiles;
    }
    public void setScanAllFiles(boolean scanAllFiles) {
        this.scanAllFiles = scanAllFiles;
    }

    /**
     * Controls the testing all directories to see of they are exploded JAR
     * files extension.
     */
    private boolean scanAllDirectories = true;
    public boolean isScanAllDirectories() {
        return scanAllDirectories;
    }
    public void setScanAllDirectories(boolean scanAllDirectories) {
        this.scanAllDirectories = scanAllDirectories;
    }

    /**
     * Controls the testing of the bootstrap classpath which consists of the
     * runtime classes provided by the JVM and any installed system extensions.
     */
    private boolean scanBootstrapClassPath = false;
    public boolean isScanBootstrapClassPath() {
        return scanBootstrapClassPath;
    }
    public void setScanBootstrapClassPath(boolean scanBootstrapClassPath) {
        this.scanBootstrapClassPath = scanBootstrapClassPath;
    }

    /**
     * Controls the filtering of the results from the scan for JARs
     */
    private JarScanFilter jarScanFilter = new StandardJarScanFilter();
    @Override
    public JarScanFilter getJarScanFilter() {
        return jarScanFilter;
    }
    @Override
    public void setJarScanFilter(JarScanFilter jarScanFilter) {
        this.jarScanFilter = jarScanFilter;
    }

    /**
     * Scan the provided ServletContext and class loader for JAR files. Each JAR
     * file found will be passed to the callback handler to be processed.
     *
     * @param scanType      The type of JAR scan to perform. This is passed to
     *                          the filter which uses it to determine how to
     *                          filter the results
     * @param context       The ServletContext - used to locate and access
     *                      WEB-INF/lib
     * @param callback      The handler to process any JARs found
     */
    @Override
    public void scan(JarScanType scanType, ServletContext context,
            JarScannerCallback callback) {

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("jarScan.webinflibStart"));
        }

        Set<URL> processedURLs = new HashSet<>();

        // Scan WEB-INF/lib
        Set<String> dirList = context.getResourcePaths(Constants.WEB_INF_LIB);
        if (dirList != null) {
            for (String path : dirList) {
                if (path.endsWith(Constants.JAR_EXT) &&
                        getJarScanFilter().check(scanType,
                                path.substring(path.lastIndexOf('/')+1))) {
                    // Need to scan this JAR
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("jarScan.webinflibJarScan", path));
                    }
                    URL url = null;
                    try {
                        url = context.getResource(path);
                        processedURLs.add(url);
                        process(scanType, callback, url, path, true, null);
                    } catch (IOException e) {
                        log.warn(sm.getString("jarScan.webinflibFail", url), e);
                    }
                } else {
                    if (log.isTraceEnabled()) {
                        log.trace(sm.getString("jarScan.webinflibJarNoScan", path));
                    }
                }
            }
        }

        // Scan WEB-INF/classes
        try {
            URL webInfURL = context.getResource(Constants.WEB_INF_CLASSES);
            if (webInfURL != null) {
                // WEB-INF/classes will also be included in the URLs returned
                // by the web application class loader so ensure the class path
                // scanning below does not re-scan this location.
                processedURLs.add(webInfURL);

                if (isScanAllDirectories()) {
                    URL url = context.getResource(Constants.WEB_INF_CLASSES + "/META-INF");
                    if (url != null) {
                        try {
                            callback.scanWebInfClasses();
                        } catch (IOException e) {
                            log.warn(sm.getString("jarScan.webinfclassesFail"), e);
                        }
                    }
                }
            }
        } catch (MalformedURLException e) {
            // Ignore. Won't happen. URLs are of the correct form.
        }

        // Scan the classpath
        if (isScanClassPath()) {
            doScanClassPath(scanType, context, callback, processedURLs);
        }
    }


    protected void doScanClassPath(JarScanType scanType, ServletContext context,
            JarScannerCallback callback, Set<URL> processedURLs) {
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("jarScan.classloaderStart"));
        }

        ClassLoader stopLoader = null;
        if (!isScanBootstrapClassPath()) {
            // Stop when we reach the bootstrap class loader
            stopLoader = ClassLoader.getSystemClassLoader().getParent();
        }

        ClassLoader classLoader = context.getClassLoader();

        // JARs are treated as application provided until the common class
        // loader is reached.
        boolean isWebapp = true;

        // Use a Deque so URLs can be removed as they are processed
        // and new URLs can be added as they are discovered during
        // processing.
        Deque<URL> classPathUrlsToProcess = new LinkedList<>();

        while (classLoader != null && classLoader != stopLoader) {
            if (classLoader instanceof URLClassLoader) {
                if (isWebapp) {
                    isWebapp = isWebappClassLoader(classLoader);
                }

                classPathUrlsToProcess.addAll(
                        Arrays.asList(((URLClassLoader) classLoader).getURLs()));

                processURLs(scanType, callback, processedURLs, isWebapp, classPathUrlsToProcess);
            }
            classLoader = classLoader.getParent();
        }

        if (JreCompat.isJre9Available()) {
            // The application and platform class loaders are not
            // instances of URLClassLoader. Use the class path in this
            // case.
            addClassPath(classPathUrlsToProcess);
            // Also add any modules
            JreCompat.getInstance().addBootModulePath(classPathUrlsToProcess);
            processURLs(scanType, callback, processedURLs, false, classPathUrlsToProcess);
        }
    }


    protected void processURLs(JarScanType scanType, JarScannerCallback callback,
            Set<URL> processedURLs, boolean isWebapp, Deque<URL> classPathUrlsToProcess) {
        while (!classPathUrlsToProcess.isEmpty()) {
            URL url = classPathUrlsToProcess.pop();

            if (processedURLs.contains(url)) {
                // Skip this URL it has already been processed
                continue;
            }

            ClassPathEntry cpe = new ClassPathEntry(url);

            // JARs are scanned unless the filter says not to.
            // Directories are scanned for pluggability scans or
            // if scanAllDirectories is enabled unless the
            // filter says not to.
            if ((cpe.isJar() ||
                    scanType == JarScanType.PLUGGABILITY ||
                    isScanAllDirectories()) &&
                            getJarScanFilter().check(scanType,
                                    cpe.getName())) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("jarScan.classloaderJarScan", url));
                }
                try {
                    processedURLs.add(url);
                    process(scanType, callback, url, null, isWebapp, classPathUrlsToProcess);
                } catch (IOException ioe) {
                    log.warn(sm.getString("jarScan.classloaderFail", url), ioe);
                }
            } else {
                // JAR / directory has been skipped
                if (log.isTraceEnabled()) {
                    log.trace(sm.getString("jarScan.classloaderJarNoScan", url));
                }
            }
        }
    }


    protected void addClassPath(Deque<URL> classPathUrlsToProcess) {
        String classPath = System.getProperty("java.class.path");

        if (classPath == null || classPath.length() == 0) {
            return;
        }

        String[] classPathEntries = classPath.split(File.pathSeparator);
        for (String classPathEntry : classPathEntries) {
            File f = new File(classPathEntry);
            try {
                classPathUrlsToProcess.add(f.toURI().toURL());
            } catch (MalformedURLException e) {
                log.warn(sm.getString("jarScan.classPath.badEntry", classPathEntry), e);
            }
        }
    }


    /*
     * Since class loader hierarchies can get complicated, this method attempts
     * to apply the following rule: A class loader is a web application class
     * loader unless it loaded this class (StandardJarScanner) or is a parent
     * of the class loader that loaded this class.
     *
     * This should mean:
     *   the webapp class loader is an application class loader
     *   the shared class loader is an application class loader
     *   the server class loader is not an application class loader
     *   the common class loader is not an application class loader
     *   the system class loader is not an application class loader
     *   the bootstrap class loader is not an application class loader
     */
    private static boolean isWebappClassLoader(ClassLoader classLoader) {
        return !CLASSLOADER_HIERARCHY.contains(classLoader);
    }


    /*
     * Scan a URL for JARs with the optional extensions to look at all files
     * and all directories.
     */
    protected void process(JarScanType scanType, JarScannerCallback callback,
            URL url, String webappPath, boolean isWebapp, Deque<URL> classPathUrlsToProcess)
            throws IOException {

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("jarScan.jarUrlStart", url));
        }

        if ("jar".equals(url.getProtocol()) || url.getPath().endsWith(Constants.JAR_EXT)) {
            try (Jar jar = JarFactory.newInstance(url)) {
                if (isScanManifest()) {
                    processManifest(jar, isWebapp, classPathUrlsToProcess);
                }
                callback.scan(jar, webappPath, isWebapp);
            }
        } else if ("file".equals(url.getProtocol())) {
            File f;
            try {
                f = new File(url.toURI());
                if (f.isFile() && isScanAllFiles()) {
                    // Treat this file as a JAR
                    URL jarURL = UriUtil.buildJarUrl(f);
                    try (Jar jar = JarFactory.newInstance(jarURL)) {
                        if (isScanManifest()) {
                            processManifest(jar, isWebapp, classPathUrlsToProcess);
                        }
                        callback.scan(jar, webappPath, isWebapp);
                    }
                } else if (f.isDirectory()) {
                    if (scanType == JarScanType.PLUGGABILITY) {
                        callback.scan(f, webappPath, isWebapp);
                    } else {
                        File metainf = new File(f.getAbsoluteFile() + File.separator + "META-INF");
                        if (metainf.isDirectory()) {
                            callback.scan(f, webappPath, isWebapp);
                        }
                    }
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                // Wrap the exception and re-throw
                IOException ioe = new IOException();
                ioe.initCause(t);
                throw ioe;
            }
        }
    }


    private static void processManifest(Jar jar, boolean isWebapp,
            Deque<URL> classPathUrlsToProcess) throws IOException {

        // Not processed for web application JARs nor if the caller did not
        // provide a Deque of URLs to append to.
        if (isWebapp || classPathUrlsToProcess == null) {
            return;
        }

        Manifest manifest = jar.getManifest();
        if (manifest != null) {
            Attributes attributes = manifest.getMainAttributes();
            String classPathAttribute = attributes.getValue("Class-Path");
            if (classPathAttribute == null) {
                return;
            }
            String[] classPathEntries = classPathAttribute.split(" ");
            for (String classPathEntry : classPathEntries) {
                classPathEntry = classPathEntry.trim();
                if (classPathEntry.length() == 0) {
                    continue;
                }
                URL jarURL = jar.getJarFileURL();
                URL classPathEntryURL;
                try {
                    URI jarURI = jarURL.toURI();
                    /*
                     * Note: Resolving the relative URLs from the manifest has the
                     *       potential to introduce security concerns. However, since
                     *       only JARs provided by the container and NOT those provided
                     *       by web applications are processed, there should be no
                     *       issues.
                     *       If this feature is ever extended to include JARs provided
                     *       by web applications, checks should be added to ensure that
                     *       any relative URL does not step outside the web application.
                     */
                    URI classPathEntryURI = jarURI.resolve(classPathEntry);
                    classPathEntryURL = classPathEntryURI.toURL();
                } catch (Exception e) {
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("jarScan.invalidUri", jarURL), e);
                    }
                    continue;
                }
                classPathUrlsToProcess.add(classPathEntryURL);
            }
        }
    }


    private static class ClassPathEntry {

        private final boolean jar;
        private final String name;

        public ClassPathEntry(URL url) {
            String path = url.getPath();
            int end = path.lastIndexOf(Constants.JAR_EXT);
            if (end != -1) {
                jar = true;
                int start = path.lastIndexOf('/', end);
                name = path.substring(start + 1, end + 4);
            } else {
                jar = false;
                if (path.endsWith("/")) {
                    path = path.substring(0, path.length() - 1);
                }
                int start = path.lastIndexOf('/');
                name = path.substring(start + 1);
            }

        }

        public boolean isJar() {
            return jar;
        }

        public String getName() {
            return name;
        }
    }
}

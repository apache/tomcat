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
package org.apache.catalina.webresources;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;

/**
 * Represents a {@link org.apache.catalina.WebResourceSet} based on a JAR file
 * that is nested inside a packed WAR file. This is only intended for internal
 * use within Tomcat and therefore cannot be created via configuration.
 */
public class JarWarResourceSet extends AbstractArchiveResourceSet {

    private final String archivePath;

    /**
     * Creates a new {@link org.apache.catalina.WebResourceSet} based on a JAR
     * file that is nested inside a WAR.
     *
     * @param root          The {@link WebResourceRoot} this new
     *                          {@link org.apache.catalina.WebResourceSet} will
     *                          be added to.
     * @param webAppMount   The path within the web application at which this
     *                          {@link org.apache.catalina.WebResourceSet} will
     *                          be mounted.
     * @param base          The absolute path to the WAR file on the file system
     *                          in which the JAR is located.
     * @param archivePath   The path within the WAR file where the JAR file is
     *                          located.
     * @param internalPath  The path within this new {@link
     *                          org.apache.catalina.WebResourceSet} where
     *                          resources will be served from. E.g. for a
     *                          resource JAR, this would be "META-INF/resources"
     */
    public JarWarResourceSet(WebResourceRoot root, String webAppMount,
            String base, String archivePath, String internalPath)
            throws IllegalArgumentException {
        setRoot(root);
        setWebAppMount(webAppMount);
        setBase(base);
        this.archivePath = archivePath;
        setInternalPath(internalPath);

        if (getRoot().getState().isAvailable()) {
            try {
                start();
            } catch (LifecycleException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Override
    public WebResource getResource(String path) {
        checkPath(path);
        String webAppMount = getWebAppMount();
        WebResourceRoot root = getRoot();

        /*
         * Implementation notes
         *
         * The path parameter passed into this method always starts with '/'.
         *
         * The path parameter passed into this method may or may not end with a
         * '/'. JarFile.getEntry() will return a matching directory entry
         * whether or not the name ends in a '/'. However, if the entry is
         * requested without the '/' subsequent calls to JarEntry.isDirectory()
         * will return false.
         *
         * Paths in JARs never start with '/'. Leading '/' need to be removed
         * before any JarFile.getEntry() call.
         */

        // If the JAR has been mounted below the web application root, return
        // an empty resource for requests outside of the mount point.

        if (path.startsWith(webAppMount)) {
            String pathInJar = getInternalPath() + path.substring(
                    webAppMount.length(), path.length());
            // Always strip off the leading '/' to get the JAR path
            if (pathInJar.charAt(0) == '/') {
                pathInJar = pathInJar.substring(1);
            }
            if (pathInJar.equals("")) {
                // Special case
                return new JarResourceRoot(root, new File(getBase()),
                        pathInJar, path);
            } else {
                JarEntry jarEntry = null;
                if (!(pathInJar.charAt(pathInJar.length() - 1) == '/')) {
                    jarEntry = jarFileEntries.get(pathInJar + '/');
                    if (jarEntry != null) {
                        path = path + '/';
                    }
                }
                if (jarEntry == null) {
                    jarEntry = jarFileEntries.get(pathInJar);
                }
                if (jarEntry == null) {
                    return new EmptyResource(root, path);
                } else {
                    return new JarWarResource(root, getBase(), baseUrl, jarEntry,
                            archivePath, getInternalPath(), path);
                }
            }
        } else {
            return new EmptyResource(root, path);
        }
    }


    //-------------------------------------------------------- Lifecycle methods
    @Override
    protected void initInternal() throws LifecycleException {

        try (JarFile warFile = new JarFile(getBase())) {
            JarEntry jarFileInWar = warFile.getJarEntry(archivePath);
            InputStream jarFileIs = warFile.getInputStream(jarFileInWar);

            try (JarInputStream jarIs = new JarInputStream(jarFileIs)) {
                JarEntry entry = jarIs.getNextJarEntry();
                while (entry != null) {
                    jarFileEntries.put(entry.getName(), entry);
                    entry = jarIs.getNextJarEntry();
                }
            }

        } catch (IOException ioe) {
            throw new IllegalArgumentException(ioe);
        }

        try {
            this.baseUrl = (new File(getBase())).toURI().toURL().toString();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }
}

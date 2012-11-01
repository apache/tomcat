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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.util.ResourceSet;

/**
 * Represents a {@link org.apache.catalina.WebResourceSet} based on a JAR file.
 */
public class JarResourceSet extends AbstractResourceSet {

    private JarFile base;
    private String baseUrl;
    private final String internalPath;

    /**
     * Creates a new {@link org.apache.catalina.WebResourceSet} based on a JAR
     * file.
     *
     * @param root          The {@link WebResourceRoot} this new
     *                          {@link org.apache.catalina.WebResourceSet} will
     *                          be added to.
     * @param base          The absolute path to the JAR file on the file system
     *                          from which the resources will be served.
     * @param webAppMount   The path within the web application at which this
     *                          {@link org.apache.catalina.WebResourceSet} will
     *                          be mounted.
     * @param internalPath  The path within this new {@link
     *                          org.apache.catalina.WebResourceSet} where
     *                          resources will be served from. E.g. for a
     *                          resource JAR, this would be "META-INF/resources"
     */
    public JarResourceSet(WebResourceRoot root, String base, String webAppMount,
            String internalPath) throws IllegalArgumentException {
        setRoot(root);
        setBase(base);
        setWebAppMount(webAppMount);
        this.internalPath = internalPath;

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
         *  Paths in JARs never start with '/'. Leading '/' need to be removed
         *  before any JarFile.getEntry() call.
         */

        // If the JAR has been mounted below the web application root, return
        // an empty resource for requests outside of the mount point.

        if (path.startsWith(webAppMount)) {
            String pathInJar = internalPath + path.substring(
                    webAppMount.length(), path.length());
            // Always strip off the leading '/' to get the JAR path
            if (pathInJar.charAt(0) == '/') {
                pathInJar = pathInJar.substring(1);
            }
            if (pathInJar.equals("")) {
                // Special case
                return new JarResourceRoot(root, new File(base.getName()),
                        pathInJar, path);
            } else {
                JarEntry jarEntry = null;
                if (!(pathInJar.charAt(pathInJar.length() - 1) == '/')) {
                    jarEntry = base.getJarEntry(pathInJar + '/');
                    if (jarEntry != null) {
                        path = path + '/';
                    }
                }
                if (jarEntry == null) {
                    jarEntry = base.getJarEntry(pathInJar);
                }
                if (jarEntry == null) {
                    return new EmptyResource(root, path);
                } else {
                    return new JarResource(root, base, baseUrl, jarEntry,
                            internalPath, path);
                }
            }
        } else {
            return new EmptyResource(root, path);
        }
    }

    @Override
    public String[] list(String path) {
        checkPath(path);
        String webAppMount = getWebAppMount();

        ArrayList<String> result = new ArrayList<>();
        if (path.startsWith(webAppMount)) {
            String pathInJar =
                    internalPath + path.substring(webAppMount.length());
            // Always strip off the leading '/' to get the JAR path
            if (pathInJar.charAt(0) == '/') {
                pathInJar = pathInJar.substring(1);
            }
            Enumeration<JarEntry> entries = base.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.length() > pathInJar.length() &&
                        name.startsWith(pathInJar)) {
                    if (name.charAt(name.length() - 1) == '/') {
                        name = name.substring(
                                pathInJar.length(), name.length() - 1);
                    } else {
                        name = name.substring(pathInJar.length());
                    }
                    if (name.length() == 0) {
                        continue;
                    }
                    if (name.charAt(0) == '/') {
                        name = name.substring(1);
                    }
                    if (name.length() > 0 && name.lastIndexOf('/') == -1) {
                        result.add(name);
                    }
                }
            }
        }
        return result.toArray(new String[result.size()]);
    }

    @Override
    public Set<String> listWebAppPaths(String path) {
        checkPath(path);
        String webAppMount = getWebAppMount();

        ResourceSet<String> result = new ResourceSet<>();
        if (path.startsWith(webAppMount)) {
            String pathInJar =
                    internalPath + path.substring(webAppMount.length());
            // Always strip off the leading '/' to get the JAR path and make
            // sure it ends in '/'
            if (pathInJar.charAt(pathInJar.length() - 1) != '/') {
                pathInJar = pathInJar.substring(1) + '/';
            }
            if (pathInJar.charAt(0) == '/') {
                pathInJar = pathInJar.substring(1);
            }

            Enumeration<JarEntry> entries = base.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.length() > pathInJar.length() &&
                        name.startsWith(pathInJar)) {
                    int nextSlash = name.indexOf('/', pathInJar.length());
                    if (nextSlash == -1 || nextSlash == name.length() - 1) {
                        if (name.startsWith(pathInJar)) {
                            result.add(webAppMount + '/' +
                                    name.substring(internalPath.length()));
                        }
                    }
                }
            }
        }
        result.setLocked(true);
        return result;
    }

    @Override
    public boolean mkdir(String path) {
        checkPath(path);

        return false;
    }

    @Override
    public boolean write(String path, InputStream is, boolean overwrite) {
        checkPath(path);

        if (is == null) {
            throw new NullPointerException(
                    sm.getString("dirResourceSet.writeNpe"));
        }

        return false;
    }

    //-------------------------------------------------------- Lifecycle methods
    @Override
    protected void initInternal() throws LifecycleException {

        try {
            this.base = new JarFile(getBase());
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

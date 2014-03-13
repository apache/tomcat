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
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.Manifest;

import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.util.ResourceSet;

public abstract class AbstractArchiveResourceSet extends AbstractResourceSet {

    private final HashMap<String,JarEntry> jarFileEntries = new HashMap<>();
    private URL baseUrl;
    private String baseUrlString;
    private Manifest manifest;


    protected final void setManifest(Manifest manifest) {
        this.manifest = manifest;
    }

    protected final void setBaseUrl(URL baseUrl) {
        this.baseUrl = baseUrl;
        if (baseUrl == null) {
            this.baseUrlString = null;
        } else {
            this.baseUrlString = baseUrl.toString();
        }
    }

    @Override
    public final URL getBaseUrl() {
        return baseUrl;
    }

    protected final String getBaseUrlString() {
        return baseUrlString;
    }

    protected final HashMap<String,JarEntry> getJarFileEntries() {
        return jarFileEntries;
    }


    @Override
    public final String[] list(String path) {
        checkPath(path);
        String webAppMount = getWebAppMount();

        ArrayList<String> result = new ArrayList<>();
        if (path.startsWith(webAppMount)) {
            String pathInJar =
                    getInternalPath() + path.substring(webAppMount.length());
            // Always strip off the leading '/' to get the JAR path
            if (pathInJar.length() > 0 && pathInJar.charAt(0) == '/') {
                pathInJar = pathInJar.substring(1);
            }
            Iterator<String> entries = jarFileEntries.keySet().iterator();
            while (entries.hasNext()) {
                String name = entries.next();
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
        } else {
            if (!path.endsWith("/")) {
                path = path + "/";
            }
            if (webAppMount.startsWith(path)) {
                int i = webAppMount.indexOf('/', path.length());
                if (i == -1) {
                    return new String[] {webAppMount.substring(path.length())};
                } else {
                    return new String[] {
                            webAppMount.substring(path.length(), i)};
                }
            }
        }
        return result.toArray(new String[result.size()]);
    }

    @Override
    public final Set<String> listWebAppPaths(String path) {
        checkPath(path);
        String webAppMount = getWebAppMount();

        ResourceSet<String> result = new ResourceSet<>();
        if (path.startsWith(webAppMount)) {
            String pathInJar =
                    getInternalPath() + path.substring(webAppMount.length());
            // Always strip off the leading '/' to get the JAR path and make
            // sure it ends in '/'
            if (pathInJar.length() > 0) {
                if (pathInJar.charAt(pathInJar.length() - 1) != '/') {
                    pathInJar = pathInJar.substring(1) + '/';
                }
                if (pathInJar.charAt(0) == '/') {
                    pathInJar = pathInJar.substring(1);
                }
            }

            Iterator<String> entries = jarFileEntries.keySet().iterator();
            while (entries.hasNext()) {
                String name = entries.next();
                if (name.length() > pathInJar.length() &&
                        name.startsWith(pathInJar)) {
                    int nextSlash = name.indexOf('/', pathInJar.length());
                    if (nextSlash == -1 || nextSlash == name.length() - 1) {
                        if (name.startsWith(pathInJar)) {
                            result.add(webAppMount + '/' +
                                    name.substring(getInternalPath().length()));
                        }
                    }
                }
            }
        } else {
            if (!path.endsWith("/")) {
                path = path + "/";
            }
            if (webAppMount.startsWith(path)) {
                int i = webAppMount.indexOf('/', path.length());
                if (i == -1) {
                    result.add(webAppMount + "/");
                } else {
                    result.add(webAppMount.substring(0, i + 1));
                }
            }
        }
        result.setLocked(true);
        return result;
    }

    @Override
    public final boolean mkdir(String path) {
        checkPath(path);

        return false;
    }

    @Override
    public final boolean write(String path, InputStream is, boolean overwrite) {
        checkPath(path);

        if (is == null) {
            throw new NullPointerException(
                    sm.getString("dirResourceSet.writeNpe"));
        }

        return false;
    }

    @Override
    public final WebResource getResource(String path) {
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
            if (pathInJar.length() > 0 && pathInJar.charAt(0) == '/') {
                pathInJar = pathInJar.substring(1);
            }
            if (pathInJar.equals("")) {
                // Special case
                // This is a directory resource so the path must end with /
                if (!path.endsWith("/")) {
                    path = path + "/";
                }
                return new JarResourceRoot(root, new File(getBase()),
                        baseUrlString, path);
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
                    return createArchiveResource(jarEntry, path, manifest);
                }
            }
        } else {
            return new EmptyResource(root, path);
        }
    }

    protected abstract WebResource createArchiveResource(JarEntry jarEntry,
            String webAppPath, Manifest manifest);

    @Override
    public final boolean isReadOnly() {
        return true;
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        if (readOnly) {
            // This is the hard-coded default - ignore the call
            return;
        }

        throw new IllegalArgumentException(
                sm.getString("abstractArchiveResourceSet.setReadOnlyFalse"));
    }
}

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
import java.util.Set;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.util.ResourceSet;

/**
 * Represents a {@link org.apache.catalina.WebResourceSet} based on a single
 * file.
 */
public class FileResourceSet extends AbstractFileResourceSet {

    /**
     * A no argument constructor is required for this to work with the digester.
     */
    public FileResourceSet() {
        super("/");
    }

    /**
     * Creates a new {@link org.apache.catalina.WebResourceSet} based on a
     * file.
     *
     * @param root          The {@link WebResourceRoot} this new
     *                          {@link org.apache.catalina.WebResourceSet} will
     *                          be added to.
     * @param webAppMount   The path within the web application at which this
     *                          {@link org.apache.catalina.WebResourceSet} will
     *                          be mounted. For example, to add a directory of
     *                          JARs to a web application, the directory would
     *                          be mounted at "WEB-INF/lib/"
     * @param base          The absolute path to the file on the file system
     *                          from which the resource will be served.
     * @param internalPath  The path within this new {@link
     *                          org.apache.catalina.WebResourceSet} where
     *                          resources will be served from.
     */
    public FileResourceSet(WebResourceRoot root, String webAppMount,
            String base, String internalPath) {
        super(internalPath);
        setRoot(root);
        setWebAppMount(webAppMount);
        setBase(base);

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
        if (path.equals(webAppMount)) {
            File f = file("", true);
            if (f == null) {
                return new EmptyResource(root, path);
            }
            return new FileResource(root, path, f, isReadOnly(), null);
        }

        if (path.charAt(path.length() - 1) != '/') {
            path = path + '/';
        }

        if (webAppMount.startsWith(path)) {
            String name = path.substring(0, path.length() - 1);
            name = name.substring(name.lastIndexOf('/') + 1);
            if (name.length() > 0) {
                return new VirtualResource(root, path, name);
            }
        }
        return new EmptyResource(root, path);
    }

    @Override
    public String[] list(String path) {
        checkPath(path);

        if (path.charAt(path.length() - 1) != '/') {
            path = path + '/';
        }
        String webAppMount = getWebAppMount();

        if (webAppMount.startsWith(path)) {
            webAppMount = webAppMount.substring(path.length());
            if (webAppMount.equals(getFileBase().getName())) {
                return new String[] {getFileBase().getName()};
            } else {
                // Virtual directory
                int i = webAppMount.indexOf('/');
                if (i > 0) {
                    return new String[] {webAppMount.substring(0, i)};
                }
            }
        }

        return EMPTY_STRING_ARRAY;
    }

    @Override
    public Set<String> listWebAppPaths(String path) {
        checkPath(path);

        ResourceSet<String> result = new ResourceSet<>();

        if (path.charAt(path.length() - 1) != '/') {
            path = path + '/';
        }
        String webAppMount = getWebAppMount();

        if (webAppMount.startsWith(path)) {
            webAppMount = webAppMount.substring(path.length());
            if (webAppMount.equals(getFileBase().getName())) {
                result.add(path + getFileBase().getName());
            } else {
                // Virtual directory
                int i = webAppMount.indexOf('/');
                if (i > 0) {
                    result.add(path + webAppMount.substring(0, i + 1));
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
        return false;
    }

    @Override
    protected void checkType(File file) {
        if (file.isFile() == false) {
            throw new IllegalArgumentException(sm.getString("fileResourceSet.notFile",
                    getBase(), File.separator, getInternalPath()));
        }
    }
}

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
package org.apache.catalina;

import java.io.InputStream;
import java.net.URL;
import java.util.Set;

/**
 * Represents a set of resources that are part of a web application. Examples
 * include a directory structure, a resources JAR and a WAR file.
 */
public interface WebResourceSet extends Lifecycle {
    /**
     * Obtain the object that represents the resource at the given path. Note
     * the resource at that path may not exist.
     *
     * @param path  The path for the resource of interest relative to the root
     *              of the web application. It must start with '/'.
     *
     * @return  The object that represents the resource at the given path
     */
    WebResource getResource(String path);

    /**
     * Obtain the list of the names of all of the files and directories located
     * in the specified directory.
     *
     * @param path  The path for the resource of interest relative to the root
     *              of the web application. It must start with '/'.
     *
     * @return  The list of resources. If path does not refer to a directory
     *          then a zero length array will be returned.
     */
    String[] list(String path);

    /**
     * Obtain the Set of the web applications pathnames of all of the files and
     * directories located in the specified directory. Paths representing
     * directories will end with a "/" character.
     *
     * @param path  The path for the resource of interest relative to the root
     *              of the web application. It must start with '/'.
     *
     * @return  The Set of resources. If path does not refer to a directory
     *          then an empty set will be returned.
     */
    Set<String> listWebAppPaths(String path);

    /**
     * Create a new directory at the given path.
     *
     * @param path  The path for the new resource to create relative to the root
     *              of the web application. It must start with '/'.
     *
     * @return  <code>true</code> if the directory was created, otherwise
     *          <code>false</code>
     */
    boolean mkdir(String path);

    /**
     * Create a new resource at the requested path using the provided
     * InputStream.
     *
     * @param path      The path to be used for the new Resource. It is relative
     *                  to the root of the web application and must start with
     *                  '/'.
     * @param is        The InputStream that will provide the content for the
     *                  new Resource.
     * @param overwrite If <code>true</code> and the resource already exists it
     *                  will be overwritten. If <code>false</code> and the
     *                  resource already exists the write will fail.
     *
     * @return  <code>true</code> if and only if the new Resource is written
     */
    boolean write(String path, InputStream is, boolean overwrite);

    void setRoot(WebResourceRoot root);

    /**
     * Should resources returned by this resource set only be included in any
     * results when the lookup is explicitly looking for class loader resources.
     * i.e. should these resources be excluded from look ups that are explicitly
     * looking for static (non-class loader) resources.
     *
     * @return <code>true</code> if these resources should only be used for
     *         class loader resource lookups, otherwise <code>false</code>
     */
    boolean getClassLoaderOnly();

    void setClassLoaderOnly(boolean classLoaderOnly);

    /**
     * Should resources returned by this resource set only be included in any
     * results when the lookup is explicitly looking for static (non-class
     * loader) resources. i.e. should these resources be excluded from look ups
     * that are explicitly looking for class loader resources.
     *
     * @return <code>true</code> if these resources should only be used for
     *         static (non-class loader) resource lookups, otherwise
     *         <code>false</code>
     */
    boolean getStaticOnly();

    void setStaticOnly(boolean staticOnly);

    /**
     * Obtain the base URL for this set of resources. One of the uses of this is
     * to grant read permissions to the resources when running under a security
     * manager.
     *
     * @return The base URL for this set of resources
     */
    URL getBaseUrl();

    /**
     * Configures whether or not this set of resources is read-only.
     *
     * @param readOnly <code>true</code> if this set of resources should be
     *                 configured to be read-only
     *
     * @throws IllegalArgumentException if an attempt is made to configure a
     *         {@link WebResourceSet} that is hard-coded to be read-only as
     *         writable
     */
    void setReadOnly(boolean readOnly);

    /**
     * Obtains the current value of the read-only setting for this set of
     * resources.
     *
     * @return <code>true</code> if this set of resources is configured to be
     *         read-only, otherwise <code>false</code>
     */
    boolean isReadOnly();

    /**
     * Implementations may cache some information to improve performance. This
     * method triggers the clean-up of those resources.
     */
    void gc();
}

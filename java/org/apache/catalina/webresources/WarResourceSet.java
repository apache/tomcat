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

import java.util.jar.JarEntry;
import java.util.jar.Manifest;

import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;

/**
 * Represents a {@link org.apache.catalina.WebResourceSet} based on a WAR file.
 */
public class WarResourceSet extends AbstractSingleArchiveResourceSet {

    /**
     * A no argument constructor is required for this to work with the digester.
     */
    public WarResourceSet() {
    }


    /**
     * Creates a new {@link org.apache.catalina.WebResourceSet} based on a WAR
     * file.
     *
     * @param root          The {@link WebResourceRoot} this new
     *                          {@link org.apache.catalina.WebResourceSet} will
     *                          be added to.
     * @param webAppMount   The path within the web application at which this
     *                          {@link org.apache.catalina.WebResourceSet} will
     *                          be mounted.
     * @param base          The absolute path to the WAR file on the file system
     *                          from which the resources will be served.
     *
     * @throws IllegalArgumentException if the webAppMount is not valid (valid
     *         paths must start with '/')
     */
    public WarResourceSet(WebResourceRoot root, String webAppMount, String base)
            throws IllegalArgumentException {
        super(root, webAppMount, base, "/");
    }


    @Override
    protected WebResource createArchiveResource(JarEntry jarEntry,
            String webAppPath, Manifest manifest) {
        return new WarResource(this, webAppPath, getBaseUrlString(), jarEntry);
    }
}

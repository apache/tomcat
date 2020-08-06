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

import java.net.URL;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.Manifest;

import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.WebResourceSet;
import org.apache.tomcat.util.scan.JarIndex;

/**
 * Represents a {@link org.apache.catalina.WebResourceSet} based on a JAR file.
 */
public class JarResourceSet extends AbstractSingleArchiveResourceSet {

    /**
     * A no argument constructor is required for this to work with the digester.
     */
    public JarResourceSet() {
    }


    /**
     * Creates a new {@link org.apache.catalina.WebResourceSet} based on a JAR
     * file.
     *
     * @param root          The {@link WebResourceRoot} this new
     *                          {@link org.apache.catalina.WebResourceSet} will
     *                          be added to.
     * @param webAppMount   The path within the web application at which this
     *                          {@link org.apache.catalina.WebResourceSet} will
     *                          be mounted.
     * @param base          The absolute path to the JAR file on the file system
     *                          from which the resources will be served.
     * @param internalPath  The path within this new {@link
     *                          org.apache.catalina.WebResourceSet} where
     *                          resources will be served from. E.g. for a
     *                          resource JAR, this would be "META-INF/resources"
     *
     * @throws IllegalArgumentException if the webAppMount or internalPath is
     *         not valid (valid paths must start with '/')
     */
    public JarResourceSet(WebResourceRoot root, String webAppMount, String base,
            String internalPath) throws IllegalArgumentException {
        super(root, webAppMount, base, internalPath);
    }

    @Override
    public WebResource getResource(String path, List<List<WebResourceSet>> allResources) {
        JarIndex jarIndex = getJarIndex();
        if(jarIndex==null){
            return getResource(path);
        }
        String webAppMount = getWebAppMount();
        if (path.startsWith(getWebAppMount())) {
            String pathInJar = getInternalPath() + path.substring(
                    webAppMount.length());
            if (pathInJar.length() > 0 && pathInJar.charAt(0) == '/') {
                pathInJar = pathInJar.substring(1);
            }
            List<String> jarFiles = jarIndex.get(pathInJar);
            if(jarFiles!=null){
                for(String jarFile:jarFiles){
                    for(List<WebResourceSet> list:allResources){
                        for(WebResourceSet wrs:list){
                            URL baseUrl = wrs.getBaseUrl();
                            if(baseUrl.toString().endsWith(jarFile)){
                                return wrs.getResource(path);
                            }
                        }
                    }
                }
            }
        }
        return getResource(path);
    }


    @Override
    protected WebResource createArchiveResource(JarEntry jarEntry,
            String webAppPath, Manifest manifest) {
        return new JarResource(this, webAppPath, getBaseUrlString(), jarEntry);
    }
}

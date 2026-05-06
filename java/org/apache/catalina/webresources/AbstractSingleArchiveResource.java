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

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Abstract {@link org.apache.catalina.WebResource} implementation for resources within a single archive (e.g., a JAR file).
 */
public abstract class AbstractSingleArchiveResource extends AbstractArchiveResource {

    /**
     * Constructs a new AbstractSingleArchiveResource.
     *
     * @param archiveResourceSet The archive resource set
     * @param webAppPath         The web app path
     * @param baseUrl            The base URL
     * @param jarEntry           The JAR entry
     * @param codeBaseUrl        The code base URL
     */
    protected AbstractSingleArchiveResource(AbstractArchiveResourceSet archiveResourceSet, String webAppPath,
            String baseUrl, JarEntry jarEntry, String codeBaseUrl) {
        super(archiveResourceSet, webAppPath, baseUrl, jarEntry, codeBaseUrl);
    }

    /**
     * Gets the JAR input stream wrapper for this resource.
     *
     * @return the JAR input stream wrapper, or {@code null} if an error occurs
     */
    @Override
    protected JarInputStreamWrapper getJarInputStreamWrapper() {
        JarFile jarFile = null;
        try {
            jarFile = getArchiveResourceSet().openJarFile();
            // Need to create a new JarEntry so the certificates can be read
            JarEntry jarEntry = jarFile.getJarEntry(getResource().getName());
            InputStream is = jarFile.getInputStream(jarEntry);
            return new JarInputStreamWrapper(jarEntry, is);
        } catch (IOException ioe) {
            if (getLog().isDebugEnabled()) {
                getLog().debug(sm.getString("jarResource.getInputStreamFail", getResource().getName(), getBaseUrl()),
                        ioe);
            }
            if (jarFile != null) {
                getArchiveResourceSet().closeJarFile();
            }
            return null;
        }
    }
}

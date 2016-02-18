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
import java.util.jar.JarInputStream;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Represents a single resource (file or directory) that is located within a
 * JAR that in turn is located in a WAR file.
 */
public class JarWarResource extends AbstractArchiveResource {

    private static final Log log = LogFactory.getLog(JarWarResource.class);

    private final String archivePath;

    public JarWarResource(AbstractArchiveResourceSet archiveResourceSet, String webAppPath,
            String baseUrl, JarEntry jarEntry, String archivePath) {
        super(archiveResourceSet, webAppPath, "jar:war:" + baseUrl + "*/" + archivePath,
                jarEntry, "jar:" + baseUrl + "!/" + archivePath);
        this.archivePath = archivePath;
    }

    @Override
    protected JarInputStreamWrapper getJarInputStreamWrapper() {
        JarFile warFile = null;
        JarInputStream jarIs = null;
        JarEntry entry = null;
        try {
            warFile = getArchiveResourceSet().openJarFile();
            JarEntry jarFileInWar = warFile.getJarEntry(archivePath);
            InputStream isInWar = warFile.getInputStream(jarFileInWar);

            jarIs = new JarInputStream(isInWar);
            entry = jarIs.getNextJarEntry();
            while (entry != null &&
                    !entry.getName().equals(getResource().getName())) {
                entry = jarIs.getNextJarEntry();
            }

            if (entry == null) {
                return null;
            }

            return new JarInputStreamWrapper(entry, jarIs);
        } catch (IOException e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("jarResource.getInputStreamFail",
                        getResource().getName(), getBaseUrl()), e);
            }
            return null;
        } finally {
            if (entry == null) {
                if (jarIs != null) {
                    try {
                        jarIs.close();
                    } catch (IOException ioe) {
                        // Ignore
                    }
                }
                if (warFile != null) {
                    getArchiveResourceSet().closeJarFile();
                }
            }
        }
    }

    @Override
    protected Log getLog() {
        return log;
    }
}

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
import java.util.jar.Manifest;

import org.apache.catalina.WebResourceRoot;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Represents a single resource (file or directory) that is located within a
 * JAR that in turn is located in a WAR file.
 */
public class JarWarResource extends AbstractArchiveResource {

    private static final Log log = LogFactory.getLog(JarResource.class);

    private final String archivePath;

    public JarWarResource(WebResourceRoot root, String webAppPath, String base,
            String baseUrl, JarEntry jarEntry, String archivePath,
            String internalPath, Manifest manifest) {
        super(root, webAppPath, base, "jar:war:" + baseUrl + "^/" + archivePath,
                jarEntry, internalPath, manifest);
        this.archivePath = archivePath;
    }

    @Override
    protected JarInputStreamWrapper getJarInputStreamWrapper() {
        try {
            JarFile warFile = new JarFile(getBase());
            JarEntry jarFileInWar = warFile.getJarEntry(archivePath);
            InputStream isInWar = warFile.getInputStream(jarFileInWar);

            JarInputStream jarIs = new JarInputStream(isInWar);
            JarEntry entry = jarIs.getNextJarEntry();
            while (entry != null &&
                    !entry.getName().equals(getResource().getName())) {
                entry = jarIs.getNextJarEntry();
            }

            if (entry == null) {
                try {
                    jarIs.close();
                } catch (IOException ioe) {
                    // Ignore
                }
                try {
                    warFile.close();
                } catch (IOException ioe) {
                    // Ignore
                }
                return null;
            }

            return new JarInputStreamWrapper(warFile, entry, jarIs);
        } catch (IOException e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("fileResource.getInputStreamFail",
                        getResource().getName(), getBaseUrl()), e);
            }
            return null;
        }
    }

    @Override
    protected Log getLog() {
        return log;
    }
}

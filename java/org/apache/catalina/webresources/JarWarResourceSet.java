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
import java.util.HashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.tomcat.util.buf.UriUtil;

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
     *
     * @throws IllegalArgumentException if the webAppMount or internalPath is
     *         not valid (valid paths must start with '/')
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
    protected WebResource createArchiveResource(JarEntry jarEntry,
            String webAppPath, Manifest manifest) {
        return new JarWarResource(this, webAppPath, getBaseUrlString(), jarEntry, archivePath);
    }


    /**
     * {@inheritDoc}
     * <p>
     * JarWar can't optimise for a single resource so the Map is always
     * returned.
     */
    @Override
    protected HashMap<String,JarEntry> getArchiveEntries(boolean single) {
        synchronized (archiveLock) {
            if (archiveEntries == null) {
                JarFile warFile = null;
                InputStream jarFileIs = null;
                archiveEntries = new HashMap<>();
                try {
                    warFile = openJarFile();
                    JarEntry jarFileInWar = warFile.getJarEntry(archivePath);
                    jarFileIs = warFile.getInputStream(jarFileInWar);

                    try (JarInputStream jarIs = new JarInputStream(jarFileIs)) {
                        JarEntry entry = jarIs.getNextJarEntry();
                        boolean hasMetaInf = false;
                        while (entry != null) {
                            if (!hasMetaInf && entry.getName().startsWith("META-INF/")) {
                                hasMetaInf = true;
                            }
                            archiveEntries.put(entry.getName(), entry);
                            entry = jarIs.getNextJarEntry();
                        }
                        setManifest(jarIs.getManifest());
                        // Hacks to work-around JarInputStream swallowing these
                        // entries. The attributes for these entries will be
                        // incomplete. Making the attributes available would
                        // require (re-)reading the stream as a ZipInputStream
                        // and creating JarEntry objects from the ZipEntries.
                        if (hasMetaInf) {
                            JarEntry metaInfDir = new JarEntry("META-INF/");
                            archiveEntries.put(metaInfDir.getName(), metaInfDir);
                        }
                        if (jarIs.getManifest() != null) {
                            JarEntry manifest = new JarEntry("META-INF/MANIFEST.MF");
                            archiveEntries.put(manifest.getName(), manifest);
                        }
                    }
                } catch (IOException ioe) {
                    // Should never happen
                    archiveEntries = null;
                    throw new IllegalStateException(ioe);
                } finally {
                    if (warFile != null) {
                        closeJarFile();
                    }
                    if (jarFileIs != null) {
                        try {
                            jarFileIs.close();
                        } catch (IOException e) {
                            // Ignore
                        }
                    }
                }
            }
            return archiveEntries;
        }
    }


    /**
     * {@inheritDoc}
     * <p>
     * Should never be called since {@link #getArchiveEntries(boolean)} always
     * returns a Map.
     */
    @Override
    protected JarEntry getArchiveEntry(String pathInArchive) {
        throw new IllegalStateException("Coding error");
    }


    //-------------------------------------------------------- Lifecycle methods
    @Override
    protected void initInternal() throws LifecycleException {

        try (JarFile warFile = new JarFile(getBase())) {
            JarEntry jarFileInWar = warFile.getJarEntry(archivePath);
            InputStream jarFileIs = warFile.getInputStream(jarFileInWar);

            try (JarInputStream jarIs = new JarInputStream(jarFileIs)) {
                setManifest(jarIs.getManifest());
            }
        } catch (IOException ioe) {
            throw new IllegalArgumentException(ioe);
        }

        try {
            setBaseUrl(UriUtil.buildJarSafeUrl(new File(getBase())));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }
}

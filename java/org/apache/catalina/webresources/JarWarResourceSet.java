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
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.tomcat.util.buf.UriUtil;
import org.apache.tomcat.util.compat.JreCompat;

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
    protected Map<String,JarEntry> getArchiveEntries(boolean single) {
        synchronized (archiveLock) {
            if (archiveEntries == null) {
                JarFile warFile = null;
                InputStream jarFileIs = null;
                archiveEntries = new HashMap<>();
                boolean multiRelease = false;
                try {
                    warFile = openJarFile();
                    JarEntry jarFileInWar = warFile.getJarEntry(archivePath);
                    jarFileIs = warFile.getInputStream(jarFileInWar);

                    try (TomcatJarInputStream jarIs = new TomcatJarInputStream(jarFileIs)) {
                        JarEntry entry = jarIs.getNextJarEntry();
                        while (entry != null) {
                            archiveEntries.put(entry.getName(), entry);
                            entry = jarIs.getNextJarEntry();
                        }
                        Manifest m = jarIs.getManifest();
                        setManifest(m);
                        if (m != null && JreCompat.isJre9Available()) {
                            String value = m.getMainAttributes().getValue("Multi-Release");
                            if (value != null) {
                                multiRelease = Boolean.parseBoolean(value);
                            }
                        }
                        // Hack to work-around JarInputStream swallowing these
                        // entries. TomcatJarInputStream is used above which
                        // extends JarInputStream and the method that creates
                        // the entries over-ridden so we can a) tell if the
                        // entries are present and b) cache them so we can
                        // access them here.
                        entry = jarIs.getMetaInfEntry();
                        if (entry != null) {
                            archiveEntries.put(entry.getName(), entry);
                        }
                        entry = jarIs.getManifestEntry();
                        if (entry != null) {
                            archiveEntries.put(entry.getName(), entry);
                        }
                    }
                    if (multiRelease) {
                        processArchivesEntriesForMultiRelease();
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


    protected void processArchivesEntriesForMultiRelease() {

        int targetVersion = JreCompat.getInstance().jarFileRuntimeMajorVersion();

        Map<String,VersionedJarEntry> versionedEntries = new HashMap<>();
        Iterator<Entry<String,JarEntry>> iter = archiveEntries.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<String,JarEntry> entry = iter.next();
            String name = entry.getKey();
            if (name.startsWith("META-INF/versions/")) {
                // Remove the multi-release version
                iter.remove();

                // Get the base name and version for this versioned entry
                int i = name.indexOf('/', 18);
                if (i > 0) {
                    String baseName = name.substring(i + 1);
                    int version = Integer.parseInt(name.substring(18, i));

                    // Ignore any entries targeting for a later version than
                    // the target for this runtime
                    if (version <= targetVersion) {
                        VersionedJarEntry versionedJarEntry = versionedEntries.get(baseName);
                        if (versionedJarEntry == null) {
                            // No versioned entry found for this name. Create
                            // one.
                            versionedEntries.put(baseName,
                                    new VersionedJarEntry(version, entry.getValue()));
                        } else {
                            // Ignore any entry for which we have already found
                            // a later version
                            if (version > versionedJarEntry.getVersion()) {
                                // Replace the entry targeted at an earlier
                                // version
                                versionedEntries.put(baseName,
                                        new VersionedJarEntry(version, entry.getValue()));
                            }
                        }
                    }
                }
            }
        }

        for (Entry<String,VersionedJarEntry> versionedJarEntry : versionedEntries.entrySet()) {
            archiveEntries.put(versionedJarEntry.getKey(),
                    versionedJarEntry.getValue().getJarEntry());
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


    @Override
    protected boolean isMultiRelease() {
        // This always returns false otherwise the superclass will call
        // #getArchiveEntry(String)
        return false;
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


    private static final class VersionedJarEntry {
        private final int version;
        private final JarEntry jarEntry;

        public VersionedJarEntry(int version, JarEntry jarEntry) {
            this.version = version;
            this.jarEntry = jarEntry;
        }


        public int getVersion() {
            return version;
        }


        public JarEntry getJarEntry() {
            return jarEntry;
        }
    }
}

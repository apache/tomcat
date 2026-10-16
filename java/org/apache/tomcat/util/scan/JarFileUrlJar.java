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
package org.apache.tomcat.util.scan;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.tomcat.Jar;

/**
 * Implementation of {@link Jar} that is optimised for file based JAR URLs that refer directly to a JAR file (e.g. URLs
 * of the form jar:file: ... .jar!/ or file:... .jar).
 */
public class JarFileUrlJar implements Jar {

    private final JarFile jarFile;
    private final URL jarFileURL;
    private final boolean multiRelease;
    private Enumeration<JarEntry> entries;
    private Set<String> entryNamesSeen;
    private Map<String,JarEntry> multiReleaseMap;
    private JarEntry entry = null;

    /**
     * Constructs a new JarFileUrlJar.
     *
     * @param url the URL of the JAR file
     * @param startsWithJar whether the URL starts with "jar:"
     * @throws IOException if an I/O error occurs
     */
    public JarFileUrlJar(URL url, boolean startsWithJar) throws IOException {
        if (startsWithJar) {
            // jar:file:...
            JarURLConnection jarConn = (JarURLConnection) url.openConnection();
            jarConn.setUseCaches(false);
            jarFile = jarConn.getJarFile();
            jarFileURL = jarConn.getJarFileURL();
        } else {
            // file:...
            File f;
            try {
                f = new File(url.toURI());
            } catch (URISyntaxException e) {
                throw new IOException(e);
            }
            jarFile = new JarFile(f, true, ZipFile.OPEN_READ, Runtime.version());
            jarFileURL = url;
        }
        boolean multiReleaseValue = false;
        try {
            multiReleaseValue = jarFile.isMultiRelease();
        } catch (IllegalStateException ignore) {
            /*
             * ISE can be thrown if the JAR URL is bad, for example:
             * https://github.com/spring-projects/spring-boot/issues/33633
             *
             * The Javadoc does not document that ISE and given what it does for a vanilla IOE, this looks like a Java
             * bug, it should return false instead.
             */
        }
        multiRelease = multiReleaseValue;
    }


    @Override
    public URL getJarFileURL() {
        return jarFileURL;
    }


    @Override
    public InputStream getInputStream(String name) throws IOException {
        // JarFile#getEntry() is multi-release aware
        ZipEntry entry = jarFile.getEntry(name);
        if (entry == null) {
            return null;
        } else {
            return jarFile.getInputStream(entry);
        }
    }

    @Override
    public long getLastModified(String name) throws IOException {
        // JarFile#getEntry() is multi-release aware
        ZipEntry entry = jarFile.getEntry(name);
        if (entry == null) {
            return -1;
        } else {
            return entry.getTime();
        }
    }

    @Override
    public boolean exists(String name) throws IOException {
        // JarFile#getEntry() is multi-release aware
        ZipEntry entry = jarFile.getEntry(name);
        return entry != null;
    }

    @Override
    public String getURL(String entry) {
        return "jar:" + getJarFileURL().toExternalForm() + "!/" + entry;
    }

    @Override
    public void close() {
        if (jarFile != null) {
            try {
                jarFile.close();
            } catch (IOException ignore) {
                // Ignore
            }
        }
    }

    @Override
    public void nextEntry() {
        // JarFile#entries() is NOT multi-release aware
        if (entries == null) {
            entries = jarFile.entries();
            if (multiRelease) {
                entryNamesSeen = new HashSet<>();
            }
        }

        if (multiRelease) {
            // Need to ensure that:
            // - the one, correct entry is returned where multiple versions
            // are available
            // - that the order of entries in the JAR doesn't prevent the
            // correct entries being returned
            // - the case where an entry appears in the versions location
            // but not in the base location is handled correctly

            // The look-up of the entry to use is based on the 'base' name of
            // each entry and the highest applicable version of each class, so
            // that the correct entry is selected independently of the order of
            // the entries in the JAR.
            if (multiReleaseMap == null) {
                multiReleaseMap = populateMultiReleaseMap();
            }

            // Enumerate the entries until one is reached that represents an
            // entry that has not been seen before.
            String name;
            while (true) {
                if (entries.hasMoreElements()) {
                    entry = entries.nextElement();
                    name = entry.getName();
                    // Get 'base' name
                    if (name.startsWith("META-INF/versions/")) {
                        int i = name.indexOf('/', 18);
                        if (i == -1) {
                            continue;
                        }
                        name = name.substring(i + 1);
                    }
                    if (name.isEmpty() || entryNamesSeen.contains(name)) {
                        continue;
                    }
                    entryNamesSeen.add(name);

                    JarEntry versionedEntry = multiReleaseMap.get(name);
                    if (versionedEntry != null) {
                        // Use the highest applicable version of this class
                        entry = versionedEntry;
                    } else {
                        // Use the base entry. Classes with a version that
                        // applies to this runtime were resolved from the
                        // multi-release map above.
                        entry = jarFile.getJarEntry(name);
                    }

                    if (entry == null) {
                        // There is no entry that applies to this runtime, for
                        // example the entry only exists in the versions
                        // location with a version later than this runtime
                        continue;
                    }
                } else {
                    entry = null;
                }
                break;
            }
        } else {
            if (entries.hasMoreElements()) {
                entry = entries.nextElement();
            } else {
                entry = null;
            }
        }
    }

    @Override
    public String getEntryName() {
        if (entry == null) {
            return null;
        } else {
            return entry.getName();
        }
    }

    @Override
    public InputStream getEntryInputStream() throws IOException {
        if (entry == null) {
            return null;
        } else {
            return jarFile.getInputStream(entry);
        }
    }

    @Override
    public Manifest getManifest() throws IOException {
        return jarFile.getManifest();
    }


    @Override
    public void reset() throws IOException {
        entries = null;
        entryNamesSeen = null;
        multiReleaseMap = null;
        entry = null;
    }


    /**
     * Identifies, for each class that appears in the versions location of the JAR, the entry for the highest version
     * that applies to the current runtime. Keyed by 'base' name, which is the name with the
     * <code>META-INF/versions/&lt;version&gt;/</code> prefix removed.
     *
     * @return the map of 'base' name to the entry to use for that name
     */
    private Map<String,JarEntry> populateMultiReleaseMap() {
        int targetVersion = Runtime.version().feature();

        Map<String,Integer> mrVersions = new HashMap<>();
        Map<String,JarEntry> result = new HashMap<>();

        // Tracking the base name and the latest valid version found is
        // sufficient to be able to create the map of entries to use
        Enumeration<JarEntry> jarEntries = jarFile.entries();
        while (jarEntries.hasMoreElements()) {
            JarEntry jarEntry = jarEntries.nextElement();
            String name = jarEntry.getName();
            if (name.startsWith("META-INF/versions/") && name.endsWith(".class")) {
                int i = name.indexOf('/', 18);
                if (i > 0) {
                    String baseName = name.substring(i + 1);
                    int version;
                    try {
                        version = Integer.parseInt(name, 18, i, 10);
                    } catch (NumberFormatException e) {
                        continue;
                    }

                    // Ignore any entries targeting for a later version than
                    // the target for this runtime
                    if (version <= targetVersion) {
                        Integer mappedVersion = mrVersions.get(baseName);
                        // Ignore any entry for which a later version has
                        // already been found
                        if (mappedVersion == null || version > mappedVersion.intValue()) {
                            mrVersions.put(baseName, Integer.valueOf(version));
                            result.put(baseName, jarEntry);
                        }
                    }
                }
            }
        }

        return result;
    }
}

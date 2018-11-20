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
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import org.apache.tomcat.Jar;
import org.apache.tomcat.util.compat.JreCompat;

/**
 * Implementation of {@link Jar} that is optimised for file based JAR URLs that
 * refer directly to a JAR file (e.g URLs of the form jar:file: ... .jar!/ or
 * file:... .jar).
 */
public class JarFileUrlJar implements Jar {

    private final JarFile jarFile;
    private final URL jarFileURL;
    private final boolean multiRelease;
    private Enumeration<JarEntry> entries;
    private Set<String> entryNamesSeen;
    private JarEntry entry = null;

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
            jarFile = JreCompat.getInstance().jarFileNewInstance(f);
            jarFileURL = url;
        }
        multiRelease = JreCompat.getInstance().jarFileIsMultiRelease(jarFile);
    }


    @Override
    public URL getJarFileURL() {
        return jarFileURL;
    }


    @Override
    @Deprecated
    public boolean entryExists(String name) {
        return false;
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
        StringBuilder result = new StringBuilder("jar:");
        result.append(getJarFileURL().toExternalForm());
        result.append("!/");
        result.append(entry);

        return result.toString();
    }

    @Override
    public void close() {
        if (jarFile != null) {
            try {
                jarFile.close();
            } catch (IOException e) {
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
            //   are available
            // - that the order of entries in the JAR doesn't prevent the
            //   correct entries being returned
            // - the case where an entry appears in the versions location
            //   but not in the the base location is handled correctly

            // Enumerate the entries until one is reached that represents an
            // entry that has not been seen before.
            String name = null;
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
                    if (name.length() == 0 || entryNamesSeen.contains(name)) {
                        continue;
                    }

                    entryNamesSeen.add(name);

                    // JarFile.getJarEntry is version aware so use it
                    entry = jarFile.getJarEntry(entry.getName());
                    break;
                } else {
                    entry = null;
                    break;
                }
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
        entry = null;
    }
}

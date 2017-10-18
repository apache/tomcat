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

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.apache.tomcat.util.compat.JreCompat;

/**
 * Implementation of {@link Jar} that is optimised for file based JAR URLs (e.g
 * URLs of the form jar:file:...).
 */
public class FileUrlJar implements Jar {

    private JarFile jarFile;
    private final boolean multiRelease;
    private Enumeration<JarEntry> entries;
    private Set<String> entryNamesSeen;
    private JarEntry entry = null;

    public FileUrlJar(URL url) throws IOException {
        JarURLConnection jarConn = (JarURLConnection) url.openConnection();
        jarConn.setUseCaches(false);
        // JarFile returned will be multi-release aware if the OS supports it.
        jarFile = jarConn.getJarFile();
        multiRelease = JreCompat.getInstance().jarFileIsMultiRelease(jarFile);
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
                entryNamesSeen = new HashSet<String>();
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
    public void reset() throws IOException {
        entries = null;
        entryNamesSeen = null;
        entry = null;
    }
}

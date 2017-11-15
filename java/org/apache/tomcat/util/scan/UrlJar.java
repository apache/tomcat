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
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.Manifest;

import org.apache.tomcat.util.compat.JreCompat;

/**
 * Implementation of {@link Jar} that is optimised for non-file based JAR URLs
 * (e.g. JNDI based URLs of the form jar:jndi:...).
 */
public class UrlJar implements Jar {

    private NonClosingJarInputStream jarInputStream = null;
    private URL url = null;
    private JarEntry entry = null;
    private Map<String,String> mrMap = null;

    public UrlJar(URL url) throws IOException {
        this.url = url;
        this.jarInputStream = createJarInputStream();

        boolean multiRelease = false;
        if (JreCompat.isJre9Available()) {
            Manifest manifest = jarInputStream.getManifest();
            if (manifest != null) {
                String mrValue = manifest.getMainAttributes().getValue("Multi-Release");
                if (mrValue != null) {
                    multiRelease = Boolean.valueOf(mrValue).booleanValue();
                }
            }
        }
        if (multiRelease) {
            populateMrMap();
        }
    }

    @Override
    @Deprecated
    public boolean entryExists(String name) throws IOException {
        return false;
    }

    @Override
    public InputStream getInputStream(String name) throws IOException {
        JarEntry entry = jarInputStream.getNextJarEntry();
        while (entry != null) {
            if (name.equals(entry.getName())) {
                break;
            }
            entry = jarInputStream.getNextJarEntry();
        }

        if (entry == null) {
            return null;
        } else {
            return jarInputStream;
        }
    }

    @Override
    public void close() {
        if (jarInputStream != null) {
            try {
                jarInputStream.reallyClose();
            } catch (IOException ioe) {
                // Ignore
            }
        }
    }

    private NonClosingJarInputStream createJarInputStream() throws IOException {
        JarURLConnection jarConn = (JarURLConnection) url.openConnection();
        URL resourceURL = jarConn.getJarFileURL();
        URLConnection resourceConn = resourceURL.openConnection();
        resourceConn.setUseCaches(false);
        return new NonClosingJarInputStream(resourceConn.getInputStream());
    }

    @Override
    public void nextEntry() {
        try {
            entry = jarInputStream.getNextJarEntry();
            if (mrMap != null) {
                // Skip base entries where there is a multi-release entry
                // Skip multi-release entries that are not being used
                while (entry != null &&
                        (mrMap.keySet().contains(entry.getName()) ||
                                entry.getName().startsWith("META-INF/versions/") &&
                                !mrMap.values().contains(entry.getName()))) {
                    entry = jarInputStream.getNextJarEntry();
                }
            } else {
                // Skip multi-release entries
                while (entry != null && entry.getName().startsWith("META-INF/versions/")) {
                    entry = jarInputStream.getNextJarEntry();
                }
            }
            } catch (IOException ioe) {
            entry = null;
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
        return jarInputStream;
    }

    @Override
    public void reset() throws IOException {
        close();
        entry = null;
        jarInputStream = createJarInputStream();
    }


    private void populateMrMap() throws IOException {
        int targetVersion = JreCompat.getInstance().jarFileRuntimeMajorVersion();

        Map<String,Integer> mrVersions = new HashMap<String,Integer>();

        JarEntry jarEntry = jarInputStream.getNextJarEntry();

        // Tracking the base name and the latest valid version found is
        // sufficient to be able to create the renaming map required
        while (jarEntry != null) {
            String name = jarEntry.getName();
            if (name.startsWith("META-INF/versions/") && name.endsWith(".class")) {

                // Get the base name and version for this versioned entry
                int i = name.indexOf('/', 18);
                if (i > 0) {
                    String baseName = name.substring(i + 1);
                    int version = Integer.parseInt(name.substring(18, i));

                    // Ignore any entries targeting for a later version than
                    // the target for this runtime
                    if (version <= targetVersion) {
                        Integer mappedVersion = mrVersions.get(baseName);
                        if (mappedVersion == null) {
                            // No version found for this name. Create one.
                            mrVersions.put(baseName, Integer.valueOf(version));
                        } else {
                            // Ignore any entry for which we have already found
                            // a later version
                            if (version > mappedVersion.intValue()) {
                                // Replace the earlier version
                                mrVersions.put(baseName, Integer.valueOf(version));
                            }
                        }
                    }
                }
            }
            jarEntry = jarInputStream.getNextJarEntry();
        }

        mrMap = new HashMap<String,String>();

        for (Entry<String,Integer> mrVersion : mrVersions.entrySet()) {
            mrMap.put(mrVersion.getKey() , "META-INF/versions/" + mrVersion.getValue().toString() +
                    "/" +  mrVersion.getKey());
        }

        // Reset stream back to the beginning of the JAR
        close();
        jarInputStream = createJarInputStream();
    }
}

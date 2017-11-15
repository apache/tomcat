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
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.Manifest;

import org.apache.tomcat.Jar;
import org.apache.tomcat.util.compat.JreCompat;

/**
 * Base implementation of Jar for implementations that use a JarInputStream to
 * access the JAR file.
 */
public abstract class AbstractInputStreamJar implements Jar {

    private final URL jarFileURL;

    private NonClosingJarInputStream jarInputStream = null;
    private JarEntry entry = null;
    private Boolean multiRelease = null;
    private Map<String,String> mrMap = null;

    public AbstractInputStreamJar(URL jarFileUrl) {
        this.jarFileURL = jarFileUrl;
    }


    @Override
    public URL getJarFileURL() {
        return jarFileURL;
    }


    @Override
    public void nextEntry() {
        if (jarInputStream == null) {
            try {
                reset();
            } catch (IOException e) {
                entry = null;
                return;
            }
        }
        try {
            entry = jarInputStream.getNextJarEntry();
            if (multiRelease.booleanValue()) {
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
        // Given how the entry name is used, there is no requirement to convert
        // the name for a multi-release entry to the corresponding base name.
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
    public InputStream getInputStream(String name) throws IOException {
        gotoEntry(name);
        if (entry == null) {
            return null;
        } else {
            // Clear the entry so that multiple calls to this method for the
            // same entry will result in a new InputStream for each call
            // (BZ 60798)
            entry = null;
            return jarInputStream;
        }
    }


    @Override
    public long getLastModified(String name) throws IOException {
        gotoEntry(name);
        if (entry == null) {
            return -1;
        } else {
            return entry.getTime();
        }
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
    public Manifest getManifest() throws IOException {
        reset();
        return jarInputStream.getManifest();
    }


    @Override
    public void reset() throws IOException {
        closeStream();
        entry = null;
        jarInputStream = createJarInputStream();
        // Only perform multi-release processing on first access
        if (multiRelease == null) {
            if (JreCompat.isJre9Available()) {
                Manifest manifest = jarInputStream.getManifest();
                if (manifest == null) {
                    multiRelease = Boolean.FALSE;
                } else {
                    String mrValue = manifest.getMainAttributes().getValue("Multi-Release");
                    if (mrValue == null) {
                        multiRelease = Boolean.FALSE;
                    } else {
                        multiRelease = Boolean.valueOf(mrValue);
                    }
                }
            } else {
                multiRelease = Boolean.FALSE;
            }
            if (multiRelease.booleanValue()) {
                if (mrMap == null) {
                    populateMrMap();
                }
            }
        }
    }


    protected void closeStream() {
        if (jarInputStream != null) {
            try {
                jarInputStream.reallyClose();
            } catch (IOException ioe) {
                // Ignore
            }
        }
    }


    protected abstract NonClosingJarInputStream createJarInputStream() throws IOException;


    private void gotoEntry(String name) throws IOException {
        boolean needsReset = true;
        if (multiRelease == null) {
            reset();
            needsReset = false;
        }

        // Need to convert requested name to multi-release name (if one exists)
        if (multiRelease.booleanValue()) {
            String mrName = mrMap.get(name);
            if (mrName != null) {
                name = mrName;
            }
        } else if (name.startsWith("META-INF/versions/")) {
            entry = null;
            return;
        }

        if (entry != null && name.equals(entry.getName())) {
            return;
        }
        if (needsReset) {
            reset();
        }

        JarEntry jarEntry = jarInputStream.getNextJarEntry();
        while (jarEntry != null) {
            if (name.equals(jarEntry.getName())) {
                entry = jarEntry;
                break;
            }
            jarEntry = jarInputStream.getNextJarEntry();
        }
    }


    private void populateMrMap() throws IOException {
        int targetVersion = JreCompat.getInstance().jarFileRuntimeMajorVersion();

        Map<String,Integer> mrVersions = new HashMap<>();

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

        mrMap = new HashMap<>();

        for (Entry<String,Integer> mrVersion : mrVersions.entrySet()) {
            mrMap.put(mrVersion.getKey() , "META-INF/versions/" + mrVersion.getValue().toString() +
                    "/" +  mrVersion.getKey());
        }

        // Reset stream back to the beginning of the JAR
        closeStream();
        jarInputStream = createJarInputStream();
    }
}

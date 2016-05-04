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
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import org.apache.tomcat.Jar;

/**
 * Implementation of {@link Jar} that is optimised for file based JAR URLs that
 * refer directly to a JAR file (e.g URLs of the form jar:file: ... .jar!/ or
 * file:... .jar) .
 */
public class JarFileUrlJar implements Jar {

    private final JarFile jarFile;
    private final URL jarFileURL;
    private Enumeration<JarEntry> entries;
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
            jarFile = new JarFile(f);
            jarFileURL = url;
        }
    }


    @Override
    public URL getJarFileURL() {
        return jarFileURL;
    }


    @Override
    public boolean entryExists(String name) {
        ZipEntry entry = jarFile.getEntry(name);
        return entry != null;
    }

    @Override
    public InputStream getInputStream(String name) throws IOException {
        ZipEntry entry = jarFile.getEntry(name);
        if (entry == null) {
            return null;
        } else {
            return jarFile.getInputStream(entry);
        }
    }

    @Override
    public long getLastModified(String name) throws IOException {
        ZipEntry entry = jarFile.getEntry(name);
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
        if (entries == null) {
            entries = jarFile.entries();
        }
        if (entries.hasMoreElements()) {
            entry = entries.nextElement();
        } else {
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
        entry = null;
    }
}

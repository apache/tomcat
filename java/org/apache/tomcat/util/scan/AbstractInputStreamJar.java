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
import java.util.jar.JarEntry;
import java.util.jar.Manifest;

import org.apache.tomcat.Jar;

/**
 * Base implementation of Jar for implementations that use a JarInputStream to
 * access the JAR file.
 */
public abstract class AbstractInputStreamJar implements Jar {

    private final URL jarFileURL;

    private NonClosingJarInputStream jarInputStream = null;
    private JarEntry entry = null;

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
                jarInputStream = createJarInputStream();
            } catch (IOException e) {
                entry = null;
                return;
            }
        }
        try {
            entry = jarInputStream.getNextJarEntry();
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
    public boolean entryExists(String name) throws IOException {
        gotoEntry(name);
        return entry != null;
    }


    @Override
    public InputStream getInputStream(String name) throws IOException {
        gotoEntry(name);
        if (entry == null) {
            return null;
        } else {
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
        if (entry != null && name.equals(entry.getName())) {
            return;
        }
        reset();
        JarEntry jarEntry = jarInputStream.getNextJarEntry();
        while (jarEntry != null) {
            if (name.equals(jarEntry.getName())) {
                entry = jarEntry;
                break;
            }
            jarEntry = jarInputStream.getNextJarEntry();
        }
    }
}

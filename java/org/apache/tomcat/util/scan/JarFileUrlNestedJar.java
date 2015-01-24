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
import java.util.jar.JarEntry;
import java.util.jar.JarFile;


/**
 * Implementation of {@link Jar} that is optimised for file based JAR URLs that
 * refer to a JAR file nested inside a WAR
 * (e.g URLs of the form jar:file: ... .war!/ ... .jar).
 */
public class JarFileUrlNestedJar implements Jar {

    private final URL jarFileURL;
    private final JarFile warFile;
    private final JarEntry jarEntry;
    private NonClosingJarInputStream jarInputStream = null;
    private JarEntry entry = null;

    public JarFileUrlNestedJar(URL url) throws IOException {
        jarFileURL = url;
        JarURLConnection jarConn = (JarURLConnection) url.openConnection();
        jarConn.setUseCaches(false);
        warFile = jarConn.getJarFile();

        String urlAsString = url.toString();
        int pathStart = urlAsString.indexOf("!/") + 2;
        String jarPath = urlAsString.substring(pathStart);
        jarEntry = warFile.getJarEntry(jarPath);
    }


    @Override
    public URL getJarFileURL() {
        return jarFileURL;
    }



    @Override
    public boolean entryExists(String name) throws IOException {
        reset();
        JarEntry entry = jarInputStream.getNextJarEntry();
        while (entry != null) {
            if (name.equals(entry.getName())) {
                break;
            }
            entry = jarInputStream.getNextJarEntry();
        }

        return entry != null;
    }


    @Override
    public InputStream getInputStream(String name) throws IOException {
        reset();
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
    public long getLastModified(String name) throws IOException {
        reset();
        JarEntry entry = jarInputStream.getNextJarEntry();
        while (entry != null) {
            if (name.equals(entry.getName())) {
                break;
            }
            entry = jarInputStream.getNextJarEntry();
        }

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
        closeInner();
        if (warFile != null) {
            try {
                warFile.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }


    private void closeInner() {
        if (jarInputStream != null) {
            try {
                jarInputStream.reallyClose();
            } catch (IOException ioe) {
                // Ignore
            }
        }
    }

    private NonClosingJarInputStream createJarInputStream() throws IOException {
        return new NonClosingJarInputStream(warFile.getInputStream(jarEntry));
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
        if (jarInputStream == null) {
            jarInputStream = createJarInputStream();
        }
        return jarInputStream;
    }


    @Override
    public void reset() throws IOException {
        closeInner();
        jarInputStream = createJarInputStream();
    }
}

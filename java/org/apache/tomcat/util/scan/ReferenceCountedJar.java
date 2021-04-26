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
import java.util.jar.Manifest;

import org.apache.tomcat.Jar;

/**
 * This class provides a wrapper around {@link Jar} that uses reference counting
 * to close and re-create the wrapped {@link Jar} instance as required.
 */
public class ReferenceCountedJar implements Jar {

    private final URL url;
    private Jar wrappedJar;
    private int referenceCount = 0;

    public ReferenceCountedJar(URL url) throws IOException {
        this.url = url;
        open();
    }


    /*
     * Note: Returns this instance so it can be used with try-with-resources
     */
    private synchronized ReferenceCountedJar open() throws IOException {
        if (wrappedJar == null) {
            wrappedJar = JarFactory.newInstance(url);
        }
        referenceCount++;
        return this;
    }


    @Override
    public synchronized void close() {
        referenceCount--;
        if (referenceCount == 0) {
            wrappedJar.close();
            wrappedJar = null;
        }
    }


    @Override
    public URL getJarFileURL() {
        return url;
    }


    @Override
    public InputStream getInputStream(String name) throws IOException {
        try (ReferenceCountedJar jar = open()) {
            return jar.wrappedJar.getInputStream(name);
        }
    }


    @Override
    public long getLastModified(String name) throws IOException {
        try (ReferenceCountedJar jar = open()) {
            return jar.wrappedJar.getLastModified(name);
        }
    }


    @Override
    public boolean exists(String name) throws IOException {
        try (ReferenceCountedJar jar = open()) {
            return jar.wrappedJar.exists(name);
        }
    }


    @Override
    public void nextEntry() {
        try (ReferenceCountedJar jar = open()) {
            jar.wrappedJar.nextEntry();
        } catch (IOException ioe) {
            throw new IllegalStateException(ioe);
        }
    }


    @Override
    public String getEntryName() {
        try (ReferenceCountedJar jar = open()) {
            return jar.wrappedJar.getEntryName();
        } catch (IOException ioe) {
            throw new IllegalStateException(ioe);
        }
    }


    @Override
    public InputStream getEntryInputStream() throws IOException {
        try (ReferenceCountedJar jar = open()) {
            return jar.wrappedJar.getEntryInputStream();
        }
    }


    @Override
    public String getURL(String entry) {
        try (ReferenceCountedJar jar = open()) {
            return jar.wrappedJar.getURL(entry);
        } catch (IOException ioe) {
            throw new IllegalStateException(ioe);
        }
    }


    @Override
    public Manifest getManifest() throws IOException {
        try (ReferenceCountedJar jar = open()) {
            return jar.wrappedJar.getManifest();
        }
    }


    @Override
    public void reset() throws IOException {
        try (ReferenceCountedJar jar = open()) {
            jar.wrappedJar.reset();
        }
    }
}

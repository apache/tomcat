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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.jar.Manifest;

import org.apache.tomcat.Jar;

/**
 * This class provides a wrapper around {@link Jar} that uses reference counting to close and re-create the wrapped
 * {@link Jar} instance as required.
 */
public class ReferenceCountedJar implements Jar {

    private final URL url;
    private Jar wrappedJar;
    private int referenceCount = 0;

    /**
     * Creates a new reference-counted wrapper for the JAR at the given URL.
     *
     * @param url the URL of the JAR file
     *
     * @throws IOException if the JAR cannot be opened
     */
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
        if (referenceCount <= 0) {
            // More closes than opens have been requested. Ignore the extra
            // close rather than letting the count go negative, which would
            // prevent the wrapped Jar from ever being closed again.
            return;
        }
        referenceCount--;
        if (referenceCount == 0) {
            wrappedJar.close();
            wrappedJar = null;
        }
    }


    /*
     * Transfers the reference obtained by open() to the given stream obtained from the wrapped Jar. The reference is
     * released when - and only when - the stream is closed, so the wrapped Jar remains open while the stream is in
     * use. A null stream releases the reference immediately.
     */
    private InputStream retainReference(InputStream is) {
        if (is == null) {
            close();
            return null;
        }

        return new FilterInputStream(is) {

            private boolean released = false;

            @Override
            public synchronized void close() throws IOException {
                if (released) {
                    return;
                }
                released = true;
                try {
                    in.close();
                } finally {
                    ReferenceCountedJar.this.close();
                }
            }
        };
    }


    @Override
    public URL getJarFileURL() {
        return url;
    }


    @Override
    public InputStream getInputStream(String name) throws IOException {
        ReferenceCountedJar jar = open();
        try {
            return jar.retainReference(jar.wrappedJar.getInputStream(name));
        } catch (IOException | RuntimeException e) {
            jar.close();
            throw e;
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
        ReferenceCountedJar jar = open();
        try {
            return jar.retainReference(jar.wrappedJar.getEntryInputStream());
        } catch (IOException | RuntimeException e) {
            jar.close();
            throw e;
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

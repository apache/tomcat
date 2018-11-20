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
package org.apache.tomcat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.jar.Manifest;

/**
 * Provides an abstraction for use by the various classes that need to scan
 * JARs. The classes provided by the JRE for accessing JARs
 * ({@link java.util.jar.JarFile} and {@link java.util.jar.JarInputStream}) have
 * significantly different performance characteristics depending on the form of
 * the URL used to access the JAR. For file based JAR {@link java.net.URL}s,
 * {@link java.util.jar.JarFile} is faster but for non-file based
 * {@link java.net.URL}s, {@link java.util.jar.JarFile} creates a copy of the
 * JAR in the temporary directory so {@link java.util.jar.JarInputStream} is
 * faster.
 */
public interface Jar extends AutoCloseable {

    /**
     * @return The URL for accessing the JAR file.
     */
    URL getJarFileURL();

    /**
     * Obtain an {@link InputStream} for a given entry in a JAR. The caller is
     * responsible for closing the stream.
     *
     * @param name  Entry to obtain an {@link InputStream} for
     * @return      An {@link InputStream} for the specified entry or null if
     *              the entry does not exist
     *
     * @throws IOException if an I/O error occurs while processing the JAR file
     */
    InputStream getInputStream(String name) throws IOException;

    /**
     * Obtain the last modified time for the given resource in the JAR.
     *
     * @param name  Entry to obtain the modification time for
     *
     * @return The time (in the same format as
     *         {@link System#currentTimeMillis()} that the resource was last
     *         modified. Returns -1 if the entry does not exist
     *
     * @throws IOException if an I/O error occurs while processing the JAR file
     */
    long getLastModified(String name) throws IOException;

    /**
     * Determine if the given resource in present in the JAR.
     *
     * @param name  Entry to look for
     *
     * @return {@code true} if the entry is present in the JAR, otherwise
     *         {@code false}
     *
     * @throws IOException if an I/O error occurs while processing the JAR file
     */
    boolean exists(String name) throws IOException;

    /**
     * Close any resources associated with this JAR.
     */
    @Override
    void close();

    /**
     * Moves the internal pointer to the next entry in the JAR.
     */
    void nextEntry();

    /**
     * Obtains the name of the current entry.
     *
     * @return  The entry name
     */
    String getEntryName();

    /**
     * Obtains the input stream for the current entry.
     *
     * @return  The input stream
     * @throws IOException  If the stream cannot be obtained
     */
    InputStream getEntryInputStream() throws IOException;

    /**
     * Obtain, in String form, the URL for an entry in this JAR. Note that for
     * JARs nested in WAR files, the Tomcat specific war:file:... form will not
     * be used, rather the jar:jar:file:... form (that the JRE does not
     * understand will be used). Note that this means that any code using these
     * URLs will need to understand the jar:jar:file:... form and use the
     * {@link org.apache.tomcat.util.scan.JarFactory} to ensure resources are
     * accessed correctly.
     *
     * @param entry The entry to generate the URL for
     *
     * @return a URL for the specified entry in the JAR
     */
    String getURL(String entry);

    /**
     * Obtain the manifest for the JAR file.
     *
     * @return The manifest for this JAR file.
     *
     * @throws IOException If an I/O error occurs trying to obtain the manifest
     */
    Manifest getManifest() throws IOException;

    /**
     * Resets the internal pointer used to track JAR entries to the beginning of
     * the JAR.
     *
     * @throws IOException  If the pointer cannot be reset
     */
    void reset() throws IOException;
}

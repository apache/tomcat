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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.apache.catalina.webresources.TomcatURLStreamHandlerFactory;

public class TestReferenceCountedJar {

    @Rule
    public TemporaryFolder testDir = new TemporaryFolder();

    private static final String ENTRY_NAME = "test/resource.txt";
    private static final String CONTENT = "hello";

    @Before
    public void register() {
        TomcatURLStreamHandlerFactory.register();
    }


    private File createJar() throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

        File file = new File(testDir.getRoot(), "test.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(file), manifest)) {
            jos.putNextEntry(new JarEntry(ENTRY_NAME));
            jos.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }
        return file;
    }


    private static String readFully(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read = is.read(buffer);
        while (read > 0) {
            baos.write(buffer, 0, read);
            read = is.read(buffer);
        }
        return baos.toString("UTF-8");
    }


    /*
     * The reference obtained internally when a stream is opened must be held until the stream is closed. Without it,
     * reading a stream obtained after the wrapper has been closed and re-opened (the bug 62976 scenario) would fail
     * because the wrapped JAR is closed as soon as getInputStream() returns.
     */
    @Test
    public void testInputStreamRemainsValidWhenWrapperHasNoOtherReference() throws Exception {
        File file = createJar();
        URI jarUri = URI.create("jar:" + file.toURI().toURL().toExternalForm() + "!/");

        ReferenceCountedJar jar = new ReferenceCountedJar(jarUri.toURL());
        // Close the reference obtained by the constructor so the wrapper no longer holds any reference.
        jar.close();

        // This re-opens the wrapped JAR. The returned stream must stay usable even though no other reference is held.
        try (InputStream is = jar.getInputStream(ENTRY_NAME)) {
            Assert.assertNotNull(is);
            Assert.assertEquals(CONTENT, readFully(is));
        }

        // Re-reading after the stream has been closed (releasing the reference it held) must still work.
        try (InputStream is = jar.getInputStream(ENTRY_NAME)) {
            Assert.assertEquals(CONTENT, readFully(is));
        }
    }


    /*
     * When a caller holds a reference and iterates the entries (as jasper does when parsing tag files), the reference
     * obtained for the current entry stream must be held until that stream is closed and must not, on close, release
     * the caller's reference.
     */
    @Test
    public void testEntryInputStreamHoldsReferenceWhileIterating() throws Exception {
        File file = createJar();
        URI jarUri = URI.create("jar:" + file.toURI().toURL().toExternalForm() + "!/");

        // Retain the constructor reference, as a real caller does across nextEntry()/getEntryInputStream().
        try (ReferenceCountedJar jar = new ReferenceCountedJar(jarUri.toURL())) {
            boolean found = false;
            jar.nextEntry();
            while (jar.getEntryName() != null) {
                if (ENTRY_NAME.equals(jar.getEntryName())) {
                    try (InputStream is = jar.getEntryInputStream()) {
                        Assert.assertEquals(CONTENT, readFully(is));
                    }
                    found = true;
                }
                jar.nextEntry();
            }
            Assert.assertTrue(found);

            // Closing the entry stream must not have released the caller's reference.
            try (InputStream is = jar.getInputStream(ENTRY_NAME)) {
                Assert.assertEquals(CONTENT, readFully(is));
            }
        }
    }


    /*
     * Closing the returned stream more than once must release the held reference only once so the reference count is
     * not decremented below the value to which the close of the stream should return it.
     */
    @Test
    public void testStreamDoubleCloseReleasesReferenceOnce() throws Exception {
        File file = createJar();
        URI jarUri = URI.create("jar:" + file.toURI().toURL().toExternalForm() + "!/");

        // Here the constructor reference is retained, as a normal caller would do.
        try (ReferenceCountedJar jar = new ReferenceCountedJar(jarUri.toURL())) {
            InputStream is = jar.getInputStream(ENTRY_NAME);
            is.close();
            is.close();

            // The extra close must not have released the caller's reference. The JAR must still be readable through
            // the caller's (still open) reference without being re-opened.
            try (InputStream is2 = jar.getInputStream(ENTRY_NAME)) {
                Assert.assertEquals(CONTENT, readFully(is2));
            }
        }
    }


    /*
     * A request for a stream for a name that does not exist must release the opened reference immediately and return
     * null rather than holding a reference that is never released.
     */
    @Test
    public void testMissingEntryReleasesReference() throws Exception {
        File file = createJar();
        URI jarUri = URI.create("jar:" + file.toURI().toURL().toExternalForm() + "!/");

        ReferenceCountedJar jar = new ReferenceCountedJar(jarUri.toURL());
        jar.close();

        Assert.assertNull(jar.getInputStream("test/does-not-exist.txt"));

        // The failed request must not have left a reference held: a later successful request must still work.
        try (InputStream is = jar.getInputStream(ENTRY_NAME)) {
            Assert.assertEquals(CONTENT, readFully(is));
        }
    }


    /*
     * Extra close() calls (more closes than opens) must not drive the reference count below zero. If the count were to
     * go negative, subsequent open()/close() cycles would never return it to zero and the wrapped Jar would never be
     * closed again, leaking it.
     */
    @Test
    public void testExtraCloseDoesNotGoNegative() throws Exception {
        File file = createJar();
        URI jarUri = URI.create("jar:" + file.toURI().toURL().toExternalForm() + "!/");

        ReferenceCountedJar jar = new ReferenceCountedJar(jarUri.toURL());
        Field countField = ReferenceCountedJar.class.getDeclaredField("referenceCount");
        countField.setAccessible(true);

        jar.close();
        Assert.assertEquals(0, countField.getInt(jar));

        // Extra closes must be ignored rather than decrementing below zero.
        jar.close();
        jar.close();
        Assert.assertEquals(0, countField.getInt(jar));

        // A later open()/close() cycle must still be able to return to zero and release the wrapped Jar.
        try (InputStream is = jar.getInputStream(ENTRY_NAME)) {
            Assert.assertEquals(CONTENT, readFully(is));
        }
        Assert.assertEquals(0, countField.getInt(jar));
    }
}

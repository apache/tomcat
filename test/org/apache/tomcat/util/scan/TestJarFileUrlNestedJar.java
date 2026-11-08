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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.Jar;

public class TestJarFileUrlNestedJar {

    private static final String TEST_ENTRY = "Test.class";
    private static final byte[] TEST_CONTENT = "test content".getBytes(StandardCharsets.UTF_8);

    /*
     * Creates a WAR that contains a nested JAR at the given path. The nested JAR contains a single entry named
     * TEST_ENTRY with the content TEST_CONTENT.
     */
    private File createWar(String nestedJarPath) throws IOException {
        File file = File.createTempFile("nested-", ".war");
        file.deleteOnExit();

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

        try (FileOutputStream fos = new FileOutputStream(file);
                JarOutputStream war = new JarOutputStream(fos, manifest)) {

            war.putNextEntry(new JarEntry(nestedJarPath));
            war.write(createNestedJar());
            war.closeEntry();
        }

        return file;
    }


    private static byte[] createNestedJar() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

        try (JarOutputStream jar = new JarOutputStream(baos, manifest)) {
            jar.putNextEntry(new JarEntry(TEST_ENTRY));
            jar.write(TEST_CONTENT);
            jar.closeEntry();
        }

        return baos.toByteArray();
    }


    private static byte[] readJar(Jar jar) throws IOException {
        try (InputStream is = jar.getInputStream(TEST_ENTRY)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[1024];
            int len;
            while ((len = is.read(data)) > 0) {
                buffer.write(data, 0, len);
            }
            return buffer.toByteArray();
        }
    }


    /*
     * A nested JAR referenced with a plain entry name must be readable.
     */
    @Test
    public void testPlainEntryName() throws Exception {
        String nestedPath = "WEB-INF/lib/nested.jar";
        File war = createWar(nestedPath);

        try (Jar jar =
                new JarFileUrlNestedJar(new URI("jar:" + war.toURI().toString() + "!/" + nestedPath).toURL())) {
            Assert.assertTrue(jar.exists(TEST_ENTRY));
            Assert.assertArrayEquals(TEST_CONTENT, readJar(jar));
        }
    }


    /*
     * A percent-encoded entry name must resolve to the same entry that the JDK resolves it to, rather than resulting in
     * a JAR with no usable content.
     */
    @Test
    public void testEncodedEntryName() throws Exception {
        File war = createWar("WEB-INF/lib/my jar.jar");

        try (Jar jar = new JarFileUrlNestedJar(
                new URI("jar:" + war.toURI().toString() + "!/WEB-INF/lib/my%20jar.jar").toURL())) {
            Assert.assertTrue(jar.exists(TEST_ENTRY));
            Assert.assertArrayEquals(TEST_CONTENT, readJar(jar));
        }
    }


    /*
     * A URL that does not refer to an existing entry must fail when the JAR is constructed rather than later when the
     * content is accessed.
     */
    @Test
    public void testMissingEntryThrows() throws Exception {
        File war = createWar("WEB-INF/lib/nested.jar");

        Assert.assertThrows(IOException.class, () -> new JarFileUrlNestedJar(
                new URI("jar:" + war.toURI().toString() + "!/WEB-INF/lib/missing.jar").toURL()));
    }


    /*
     * A URL that does not name an entry (it refers to the whole archive) must fail when the JAR is constructed rather
     * than later when the content is accessed. JarFactory routes such URLs elsewhere. This covers direct construction
     * of JarFileUrlNestedJar with such a URL.
     */
    @Test
    public void testEmptyEntryNameThrows() throws Exception {
        File war = createWar("WEB-INF/lib/nested.jar");

        Assert.assertThrows(FileNotFoundException.class,
                () -> new JarFileUrlNestedJar(new URI("jar:" + war.toURI().toString() + "!/").toURL()));
    }


    /*
     * A URL that does not contain the JAR entry separator must fail with an IOException before the JAR can be used. In
     * practice the JDK's jar URL handler rejects such a spec ("no !/ in spec") with a MalformedURLException when the
     * URL is created, so a URL of this form never reaches JarFileUrlNestedJar via JarFactory.
     */
    @Test
    public void testMissingSeparatorThrows() throws Exception {
        File war = createWar("WEB-INF/lib/nested.jar");

        Assert.assertThrows(IOException.class,
                () -> new JarFileUrlNestedJar(new URI("jar:" + war.toURI().toString() + "!x").toURL()));
    }
}

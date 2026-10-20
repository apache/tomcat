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
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.apache.tomcat.Jar;

public class TestJarFileUrlJar {

    @Rule
    public TemporaryFolder testDir = new TemporaryFolder();

    private static final String VERSIONS = "META-INF/versions/";
    private static final String BASE_CLASS = "test/Foo.class";

    private File createMultiReleaseJar(String... entryNames) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Multi-Release", "true");

        File file = new File(testDir.getRoot(), "mr.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(file), manifest)) {
            for (String entryName : entryNames) {
                jos.putNextEntry(new JarEntry(entryName));
                jos.write(new byte[] { 1, 2, 3 });
                jos.closeEntry();
            }
        }
        return file;
    }

    private static String readEntry(Jar jar) throws IOException {
        try (InputStream is = jar.getEntryInputStream()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read = is.read(buffer);
            while (read > 0) {
                baos.write(buffer, 0, read);
                read = is.read(buffer);
            }
            return baos.toString("ISO-8859-1");
        }
    }

    private static List<String> entryNames(Jar jar) {
        List<String> names = new ArrayList<>();
        jar.nextEntry();
        while (jar.getEntryName() != null) {
            names.add(jar.getEntryName());
            jar.nextEntry();
        }
        return names;
    }

    /*
     * The entry selected for a class that is present with multiple versions must be the highest version that applies
     * to the current runtime, regardless of the order in which the entries appear in the JAR.
     */
    @Test
    public void testMultiReleaseVersionSelectionOrderIndependent() throws Exception {
        // The lower version first
        File file = createMultiReleaseJar(VERSIONS + "9/" + BASE_CLASS, VERSIONS + "11/" + BASE_CLASS, BASE_CLASS);
        try (Jar jar = new JarFileUrlJar(file.toURI().toURL(), false)) {
            Assert.assertTrue(entryNames(jar).contains(VERSIONS + "11/" + BASE_CLASS));
        }

        // The higher version first
        file = createMultiReleaseJar(VERSIONS + "11/" + BASE_CLASS, VERSIONS + "9/" + BASE_CLASS, BASE_CLASS);
        try (Jar jar = new JarFileUrlJar(file.toURI().toURL(), false)) {
            Assert.assertTrue(entryNames(jar).contains(VERSIONS + "11/" + BASE_CLASS));
        }

        // The base entry first
        file = createMultiReleaseJar(BASE_CLASS, VERSIONS + "9/" + BASE_CLASS, VERSIONS + "11/" + BASE_CLASS);
        try (Jar jar = new JarFileUrlJar(file.toURI().toURL(), false)) {
            Assert.assertTrue(entryNames(jar).contains(VERSIONS + "11/" + BASE_CLASS));
        }

        // Versioned entries only
        file = createMultiReleaseJar(VERSIONS + "9/" + BASE_CLASS, VERSIONS + "11/" + BASE_CLASS);
        try (Jar jar = new JarFileUrlJar(file.toURI().toURL(), false)) {
            Assert.assertTrue(entryNames(jar).contains(VERSIONS + "11/" + BASE_CLASS));
        }
    }


    /*
     * Entries that are only available for a later version than the current runtime must not be selected. The base
     * entry is used where one exists.
     */
    @Test
    public void testMultiReleaseFutureVersion() throws Exception {
        // The name is unlikely to ever be a valid class but it is certain to be later than the current runtime
        String futureClass = VERSIONS + "999999/" + BASE_CLASS;

        // Future versioned entry first, base entry available
        File file = createMultiReleaseJar(futureClass, BASE_CLASS);
        try (Jar jar = new JarFileUrlJar(file.toURI().toURL(), false)) {
            List<String> names = entryNames(jar);
            Assert.assertTrue(names.contains(BASE_CLASS));
            Assert.assertFalse(names.contains(futureClass));
        }

        // Future versioned entry only
        file = createMultiReleaseJar(futureClass);
        try (Jar jar = new JarFileUrlJar(file.toURI().toURL(), false)) {
            List<String> names = entryNames(jar);
            Assert.assertFalse(names.contains(futureClass));
            Assert.assertFalse(names.contains(BASE_CLASS));
        }
    }


    /*
     * The entry selected must be the entry the current runtime would use. The content of the selected entry is used
     * here to make the selection visible.
     */
    @Test
    public void testMultiReleaseSelectedEntryContent() throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Multi-Release", "true");

        File file = new File(testDir.getRoot(), "content.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(file), manifest)) {
            jos.putNextEntry(new JarEntry(VERSIONS + "9/" + BASE_CLASS));
            jos.write("nine".getBytes("ISO-8859-1"));
            jos.closeEntry();
            jos.putNextEntry(new JarEntry(VERSIONS + "11/" + BASE_CLASS));
            jos.write("eleven".getBytes("ISO-8859-1"));
            jos.closeEntry();
            jos.putNextEntry(new JarEntry(BASE_CLASS));
            jos.write("base".getBytes("ISO-8859-1"));
            jos.closeEntry();
        }

        try (Jar jar = new JarFileUrlJar(file.toURI().toURL(), false)) {
            String selected = null;
            jar.nextEntry();
            while (jar.getEntryName() != null) {
                if (jar.getEntryName().endsWith("Foo.class")) {
                    selected = readEntry(jar);
                }
                jar.nextEntry();
            }
            Assert.assertEquals("eleven", selected);
        }
    }


    /*
     * Re-enumeration after reset() must produce the same selection. Entries under a version directory that cannot be
     * parsed must not be returned. Versioned entries for resources (non-class entries) are handled the same way as by
     * the stream based implementations: the base entry is used where one exists and the versioned entries are
     * ignored.
     */
    @Test
    public void testMultiReleaseResetAndUnusableEntries() throws Exception {
        String unparseable = VERSIONS + "abc/" + BASE_CLASS;
        String versionedResource = VERSIONS + "9/test/resource.txt";
        String resource = "test/resource.txt";

        File file = createMultiReleaseJar(unparseable, versionedResource, VERSIONS + "9/" + BASE_CLASS,
                VERSIONS + "11/" + BASE_CLASS, BASE_CLASS, resource);
        try (Jar jar = new JarFileUrlJar(file.toURI().toURL(), false)) {
            List<String> names = entryNames(jar);
            Assert.assertFalse(names.contains(unparseable));
            Assert.assertFalse(names.contains(VERSIONS + "9/" + BASE_CLASS));
            Assert.assertTrue(names.contains(VERSIONS + "11/" + BASE_CLASS));
            Assert.assertFalse(names.contains(BASE_CLASS));
            Assert.assertFalse(names.contains(versionedResource));
            Assert.assertTrue(names.contains(resource));

            jar.reset();
            Assert.assertEquals(names, entryNames(jar));
        }
    }


    /*
     * The version selection must work the same way for a JAR opened via a jar: URL.
     */
    @Test
    public void testMultiReleaseVersionSelectionViaJarUrl() throws Exception {
        File file = createMultiReleaseJar(VERSIONS + "9/" + BASE_CLASS, VERSIONS + "11/" + BASE_CLASS, BASE_CLASS);
        URL url = URI.create("jar:" + file.toURI().toURL().toExternalForm() + "!/").toURL();
        try (Jar jar = new JarFileUrlJar(url, true)) {
            List<String> names = entryNames(jar);
            Assert.assertTrue(names.contains(VERSIONS + "11/" + BASE_CLASS));
            Assert.assertFalse(names.contains(VERSIONS + "9/" + BASE_CLASS));
            Assert.assertFalse(names.contains(BASE_CLASS));
        }
    }
}

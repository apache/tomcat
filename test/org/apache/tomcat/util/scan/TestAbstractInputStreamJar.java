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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.apache.catalina.util.IOTools;
import org.apache.catalina.webresources.TomcatURLStreamHandlerFactory;
import org.apache.tomcat.Jar;

public class TestAbstractInputStreamJar {

    private static final String VERSIONS = "META-INF/versions/";
    private static final String BASE_CLASS = "test/Foo.class";

    @Rule
    public TemporaryFolder testDir = new TemporaryFolder();

    @Before
    public void register() {
        TomcatURLStreamHandlerFactory.register();
    }


    @Test
    public void testNestedJarGetInputStream() throws Exception {
        File f = new File("test/webresources/war-url-connection.war");
        StringBuilder sb = new StringBuilder("war:");
        sb.append(f.toURI().toURL());
        sb.append("*/WEB-INF/lib/test.jar");

        Jar jar = JarFactory.newInstance(new URL(sb.toString()));

        InputStream is1 = jar.getInputStream("META-INF/resources/index.html");
        ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
        IOTools.flow(is1, baos1);

        InputStream is2 = jar.getInputStream("META-INF/resources/index.html");
        ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
        IOTools.flow(is2, baos2);

        Assert.assertArrayEquals(baos1.toByteArray(), baos2.toByteArray());
    }


    /*
     * Creates a multi-release JAR. The content of each entry is its own name so the entry that was read can be
     * identified.
     */
    private Jar createJar(String... entryNames) throws IOException {
        return createJar(true, entryNames);
    }

    private Jar createJar(boolean multiRelease, String... entryNames) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (multiRelease) {
            manifest.getMainAttributes().putValue("Multi-Release", "true");
        }

        File file = new File(testDir.getRoot(), "mr.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(file), manifest)) {
            for (String entryName : entryNames) {
                jos.putNextEntry(new JarEntry(entryName));
                jos.write(entryName.getBytes("ISO-8859-1"));
                jos.closeEntry();
            }
        }
        return new UrlJar(URI.create("jar:" + file.toURI().toURL().toExternalForm() + "!/").toURL());
    }

    private static String read(Jar jar, String name) throws IOException {
        try (InputStream is = jar.getInputStream(name)) {
            if (is == null) {
                return null;
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            IOTools.flow(is, baos);
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


    @Test
    public void testMultiReleaseVersionSelected() throws Exception {
        String v9 = VERSIONS + "9/" + BASE_CLASS;
        String v11 = VERSIONS + "11/" + BASE_CLASS;
        try (Jar jar = createJar(v9, v11, BASE_CLASS)) {
            // Requests for the base name are served from the highest applicable version
            Assert.assertEquals(v11, read(jar, BASE_CLASS));

            jar.reset();
            List<String> names = entryNames(jar);
            Assert.assertTrue(names.contains(v11));
            Assert.assertFalse(names.contains(v9));
            Assert.assertFalse(names.contains(BASE_CLASS));
        }
    }


    /*
     * A leading '/' is not significant when identifying a versioned entry. The base entry must remain reachable and
     * must be served from the versioned entry, using the entry's real name.
     */
    @Test
    public void testMultiReleaseLeadingSlash() throws Exception {
        String versioned = "/" + VERSIONS + "9/" + BASE_CLASS;
        try (Jar jar = createJar(versioned, BASE_CLASS)) {
            Assert.assertEquals(versioned, read(jar, BASE_CLASS));
        }
    }


    /*
     * Version directories that are not used by the JDK (sign, leading zero, below the minimum, not a number) are not
     * versioned entries. The base entry must be served from the base entry.
     */
    @Test
    public void testMultiReleaseIgnoredVersionDirectories() throws Exception {
        String[] versions = { "+9", "-9", "09", "7", "1", "abc", "99999999999999" };
        for (String version : versions) {
            try (Jar jar = createJar(VERSIONS + version + "/" + BASE_CLASS, BASE_CLASS)) {
                Assert.assertEquals(version, BASE_CLASS, read(jar, BASE_CLASS));

                jar.reset();
                Assert.assertTrue(version, entryNames(jar).contains(BASE_CLASS));
            }
        }
    }


    @Test
    public void testMultiReleaseEntryDirectlyBelowVersionsDirectory() throws Exception {
        try (Jar jar = createJar(VERSIONS + "Foo.class", VERSIONS + "9/" + BASE_CLASS, BASE_CLASS)) {
            Assert.assertEquals(VERSIONS + "9/" + BASE_CLASS, read(jar, BASE_CLASS));

            jar.reset();
            List<String> names = entryNames(jar);
            Assert.assertTrue(names.contains(VERSIONS + "9/" + BASE_CLASS));
            Assert.assertFalse(names.contains(BASE_CLASS));
        }
    }


    /*
     * Entries below META-INF/versions/ that are not versioned entries (the version directory is a sign, has a leading
     * zero, is below the minimum, is not a number, or is missing) are ordinary entries. They must be returned under
     * their full name, as JarFileUrlJar does, and must be readable by that name.
     */
    @Test
    public void testMultiReleaseUnversionedEntriesAreReturned() throws Exception {
        String[] unversioned = { VERSIONS + "+9/" + BASE_CLASS, VERSIONS + "-9/" + BASE_CLASS,
                VERSIONS + "09/" + BASE_CLASS, VERSIONS + "7/" + BASE_CLASS, VERSIONS + "abc/" + BASE_CLASS,
                VERSIONS + "99999999999999/" + BASE_CLASS, VERSIONS + "Foo.class" };

        for (String entryName : unversioned) {
            try (Jar jar = createJar(entryName, BASE_CLASS)) {
                List<String> names = entryNames(jar);
                Assert.assertTrue(entryName, names.contains(entryName));
                Assert.assertTrue(entryName, names.contains(BASE_CLASS));

                Assert.assertEquals(entryName, entryName, read(jar, entryName));
                Assert.assertEquals(entryName, BASE_CLASS, read(jar, BASE_CLASS));
            }
        }
    }


    /*
     * Nothing is versioned in a JAR that is not a multi-release JAR. Every entry must be returned, in order, and every
     * entry must be readable by name, including those below META-INF/versions/.
     */
    @Test
    public void testNotMultiRelease() throws Exception {
        String versioned = VERSIONS + "9/" + BASE_CLASS;
        String[] entryNames = { "a.txt", "b.txt", versioned, BASE_CLASS, "c.txt", "d.txt", "e.txt" };

        try (Jar jar = createJar(false, entryNames)) {
            Assert.assertEquals(List.of(entryNames), entryNames(jar));

            for (String entryName : entryNames) {
                Assert.assertEquals(entryName, entryName, read(jar, entryName));
            }

            jar.reset();
            Assert.assertEquals(List.of(entryNames), entryNames(jar));
        }
    }


    /*
     * An entry for a lower version is not used when a higher version is available. That must also be the case when the
     * entry name has a leading '/'.
     */
    @Test
    public void testMultiReleaseUnselectedLeadingSlashEntryIsHidden() throws Exception {
        String lower = "/" + VERSIONS + "9/" + BASE_CLASS;
        String higher = VERSIONS + "11/" + BASE_CLASS;
        try (Jar jar = createJar(lower, higher, BASE_CLASS)) {
            Assert.assertEquals(higher, read(jar, BASE_CLASS));

            jar.reset();
            Assert.assertEquals(List.of(higher), entryNames(jar));
        }
    }


    /*
     * Multi-release applies to all types of resource, not just classes. The base name must be served from the highest
     * version that applies to the current runtime.
     */
    @Test
    public void testMultiReleaseVersionedResourceIsUsed() throws Exception {
        String base = "test/resource.txt";
        String v9 = VERSIONS + "9/" + base;
        String v11 = VERSIONS + "11/" + base;
        try (Jar jar = createJar(v9, v11, base)) {
            Assert.assertEquals(v11, read(jar, base));

            jar.reset();
            Assert.assertEquals(List.of(v11), entryNames(jar));
        }
    }


    /*
     * A resource that only exists in the versions location is available under its base name.
     */
    @Test
    public void testMultiReleaseVersionedResourceWithoutBaseEntry() throws Exception {
        String base = "test/resource.txt";
        String v9 = VERSIONS + "9/" + base;
        try (Jar jar = createJar(v9)) {
            Assert.assertEquals(v9, read(jar, base));

            jar.reset();
            Assert.assertEquals(List.of(v9), entryNames(jar));
        }
    }
}

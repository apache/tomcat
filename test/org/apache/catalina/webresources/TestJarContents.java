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
package org.apache.catalina.webresources;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.catalina.WebResourceSet;
import org.apache.tomcat.util.compat.JreCompat;

public class TestJarContents {

    private static File empty;
    private static File jar;
    private static TesterWebResourceRoot root;
    private static WebResourceSet webResourceSet;
    private static JarResourceSet test;
    private static JarContents testJarContentsObject;

    @BeforeClass
    public static void setup() {
        try {
        empty = new File("test/webresources/dir3");
        jar = new File("test/webresources/dir1.jar");

        root = new TesterWebResourceRoot();

        // Use empty dir for root of web app.
        webResourceSet = new DirResourceSet(root, "/", empty.getAbsolutePath(), "/");
        root.setMainResources(webResourceSet);

        // If this JAR was in a web application, this is equivalent to how it
        // would be added
        test = new JarResourceSet(root, "/", jar.getAbsolutePath(), "/META-INF/resources");
        test.setStaticOnly(true);
        root.addJarResources(test);

        testJarContentsObject = new JarContents(new JarFile("test/webresources/dir1.jar"));

        } catch (Exception e) {
            Assert.fail("Error happened while testing JarContents, " + e.getMessage());
        }
    }

    @Test
    public void testMightContainResource() {
        Assert.assertTrue(testJarContentsObject.mightContainResource("/d1/d1-f1.txt"));

        Assert.assertTrue(testJarContentsObject.mightContainResource(
                "d1/d1-f1.txt"));

        Assert.assertFalse(testJarContentsObject.mightContainResource(
                "/d7/d1-f1.txt"));

        // A path that only contains slashes (once the webapp root and any
        // leading slashes have been removed) refers to the root of the
        // archive. That cannot be ruled out, so the method must return
        // true.
        Assert.assertTrue(testJarContentsObject.mightContainResource(
                "/"));

        Assert.assertTrue(testJarContentsObject.mightContainResource(
                "/////"));

        Assert.assertTrue(testJarContentsObject.mightContainResource(""));
    }

    @Test
    public void testEmptyPath() {
        // Must not throw and must return true since nothing can be ruled
        // out.
        Assert.assertTrue(testJarContentsObject.mightContainResource(""));
    }

    @Test(expected = NullPointerException.class)
    public void testNullPointerExceptions() {
        testJarContentsObject.mightContainResource(null);
    }

    @Test
    public void testMalformedEntryNames() throws Exception {
        // Malformed entry names must not prevent the filter being built.
        // Entries below a version directory that does not parse as a version
        // number are treated as regular entries, hashed under their full name.
        // Entries below a version directory that does parse are hashed under
        // their base name, as they are when the archive entries are processed
        // by JarWarResourceSet. Entries with an empty name are skipped. A
        // previously well-formed entry must still be resolvable.
        File f = File.createTempFile("test-jar-contents-malformed", ".jar");
        f.deleteOnExit();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(f))) {
            out.putNextEntry(new ZipEntry(""));
            out.write("x".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("META-INF/versions/9x/foo.txt"));
            out.write("x".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("META-INF/versions//bar.txt"));
            out.write("x".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("META-INF/versions/99999999999999/ovf.txt"));
            out.write("x".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("/META-INF/versions/9x/lead.txt"));
            out.write("x".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("META-INF/versions/+9/plus.txt"));
            out.write("x".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("META-INF/versions/-9/minus.txt"));
            out.write("x".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("META-INF/versions/9/ok.txt"));
            out.write("x".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("real.txt"));
            out.write("y".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        try (JarFile jarFile = new JarFile(f)) {
            JarContents jarContents = new JarContents(jarFile);

            Assert.assertTrue(jarContents.mightContainResource("real.txt"));
            Assert.assertTrue(
                    jarContents.mightContainResource("META-INF/versions/9x/foo.txt"));
            Assert.assertTrue(
                    jarContents.mightContainResource("META-INF/versions//bar.txt"));
            Assert.assertTrue(jarContents.mightContainResource(
                    "META-INF/versions/99999999999999/ovf.txt"));
            // A leading slash is not significant
            Assert.assertTrue(
                    jarContents.mightContainResource("/META-INF/versions/9x/lead.txt"));
            // Signed version segments are invalid
            Assert.assertTrue(jarContents.mightContainResource("META-INF/versions/+9/plus.txt"));
            Assert.assertTrue(jarContents.mightContainResource("META-INF/versions/-9/minus.txt"));
            // Valid version segments record the base name
            Assert.assertTrue(jarContents.mightContainResource("ok.txt"));
        }
    }

    @Test
    public void testVersionedEntryForCurrentRuntime() throws Exception {
        // An entry below the version directory for the running Java version
        // is resolved under its base name. An entry targeting a later
        // version is not applicable and remains under its full name.
        int feature = JreCompat.getInstance().jarFileRuntimeMajorVersion();
        String current = "META-INF/versions/" + feature + "/current.txt";
        String later = "META-INF/versions/" + (feature + 1) + "/later.txt";
        File f = createJar(current, later);

        try (JarFile jarFile = new JarFile(f)) {
            JarContents jarContents = new JarContents(jarFile);

            Assert.assertTrue(jarContents.mightContainResource("current.txt"));
            Assert.assertTrue(jarContents.mightContainResource(later));
        }
    }

    @Test
    public void testLeadingZeroVersion() throws Exception {
        // The JDK does not treat a version directory with a leading zero as a
        // versioned entry so the entry stays under its full name.
        File f = createJar("META-INF/versions/09/zero.txt");

        try (JarFile jarFile = new JarFile(f)) {
            JarContents jarContents = new JarContents(jarFile);

            Assert.assertTrue(jarContents.mightContainResource("META-INF/versions/09/zero.txt"));
        }
    }

    @Test
    public void testEntryDirectlyBelowVersionsDirectory() throws Exception {
        // There is no version directory so the entry stays under its full
        // name and building the filter must not fail.
        File f = createJar("META-INF/versions/Foo.class", "real.txt");

        try (JarFile jarFile = new JarFile(f)) {
            JarContents jarContents = new JarContents(jarFile);

            Assert.assertTrue(jarContents.mightContainResource("META-INF/versions/Foo.class"));
            Assert.assertTrue(jarContents.mightContainResource("real.txt"));
        }
    }

    private static File createJar(String... names) throws Exception {
        File f = File.createTempFile("test-jar-contents", ".jar");
        f.deleteOnExit();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(f))) {
            for (String name : names) {
                out.putNextEntry(new ZipEntry(name));
                out.write("x".getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return f;
    }
}

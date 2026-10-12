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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.WebResource;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestJarWarResourceSet extends TomcatBaseTest {

    @Before
    public void register() {
        TomcatURLStreamHandlerFactory.register();
    }


    @Test
    public void testJarWarMetaInf() throws LifecycleException  {
        Tomcat tomcat = getTomcatInstance();

        File warFile = new File("test/webresources/war-url-connection.war");
        Context ctx = tomcat.addContext("", warFile.getAbsolutePath());

        tomcat.start();

        StandardRoot root = (StandardRoot) ctx.getResources();

        WebResource[] results = root.getClassLoaderResources("/META-INF");

        Assert.assertNotNull(results);
        Assert.assertEquals(1, results.length);
        Assert.assertNotNull(results[0].getURL());
    }

    @Test
    public void testBloomFilterIsNotRebuiltOnEveryIndexRead() {
        // Regression test: the bloom filter for the JAR nested in the WAR
        // must be built once when the archive index is built and then
        // reused. It previously was re-created on every call to
        // getArchiveEntries(), adding work linear in the number of entries
        // of the JAR to each resource lookup.
        StandardRoot resourceRoot = new StandardRoot();
        resourceRoot.setArchiveIndexStrategy("BLOOM");
        File warFile = new File("test/webresources/war-url-connection.war");
        JarWarResourceSet resourceSet = new JarWarResourceSet(resourceRoot, "/WEB-INF/classes",
                warFile.getAbsolutePath(), "WEB-INF/lib/test.jar", "/");

        Map<String,JarEntry> entries = resourceSet.getArchiveEntries(true);
        Assert.assertNotNull(entries);
        JarContents jarContents = resourceSet.jarContents;
        Assert.assertNotNull(jarContents);

        Assert.assertSame(entries, resourceSet.getArchiveEntries(true));
        Assert.assertSame(jarContents, resourceSet.jarContents);

        resourceSet.gc();
    }

    @Test
    public void testMultiReleaseWithInvalidVersionDirectories() throws Exception {
        // An entry below a directory whose name is not a valid version number
        // must not stop the archive index being built. Such an entry is not a
        // versioned entry, so it stays in the index under its full name. An
        // entry below a valid version directory is indexed under its base
        // name.
        File warFile = File.createTempFile("test-jar-war-resource-set", ".war");
        warFile.deleteOnExit();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(warFile))) {
            out.putNextEntry(new ZipEntry("WEB-INF/lib/test.jar"));
            out.write(createMultiReleaseJar());
            out.closeEntry();
        }

        StandardRoot resourceRoot = new StandardRoot();
        resourceRoot.setArchiveIndexStrategy("BLOOM");
        JarWarResourceSet resourceSet = new JarWarResourceSet(resourceRoot, "/WEB-INF/classes",
                warFile.getAbsolutePath(), "WEB-INF/lib/test.jar", "/");

        Map<String,JarEntry> entries = resourceSet.getArchiveEntries(true);

        Assert.assertTrue(entries.containsKey("META-INF/versions/9x/foo.txt"));
        Assert.assertTrue(entries.containsKey("META-INF/versions//bar.txt"));
        Assert.assertTrue(entries.containsKey("META-INF/versions/99999999999999/ovf.txt"));
        Assert.assertTrue(entries.containsKey("ok.txt"));
        Assert.assertEquals("META-INF/versions/9/ok.txt", entries.get("ok.txt").getName());
        Assert.assertFalse(entries.containsKey("META-INF/versions/9/ok.txt"));
        Assert.assertTrue(entries.containsKey("real.txt"));

        // The bloom filter is built from the resulting index
        JarContents jarContents = resourceSet.jarContents;
        Assert.assertNotNull(jarContents);
        Assert.assertTrue(jarContents.mightContainResource("META-INF/versions/9x/foo.txt", ""));
        Assert.assertTrue(jarContents.mightContainResource("ok.txt", ""));
        Assert.assertTrue(jarContents.mightContainResource("real.txt", ""));

        // The index and the bloom filter have to agree on the name an entry is
        // stored under, otherwise these lookups would be rejected by the filter
        WebResource invalidVersion =
                resourceSet.getResource("/WEB-INF/classes/META-INF/versions/9x/foo.txt");
        Assert.assertTrue(invalidVersion instanceof JarWarResource);
        Assert.assertTrue(invalidVersion.exists());

        WebResource versioned = resourceSet.getResource("/WEB-INF/classes/ok.txt");
        Assert.assertTrue(versioned instanceof JarWarResource);
        Assert.assertTrue(versioned.exists());

        resourceSet.gc();
    }

    private static byte[] createMultiReleaseJar() throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Multi-Release", "true");

        String[] names = { "META-INF/versions/9/ok.txt", "META-INF/versions/9x/foo.txt",
                "META-INF/versions//bar.txt", "META-INF/versions/99999999999999/ovf.txt", "real.txt" };

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JarOutputStream jos = new JarOutputStream(baos, manifest)) {
            for (String name : names) {
                jos.putNextEntry(new JarEntry(name));
                jos.write("x".getBytes(StandardCharsets.UTF_8));
                jos.closeEntry();
            }
        }

        return baos.toByteArray();
    }
}

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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;

public abstract class AbstractTestResourceSet {

    protected WebResourceRoot resourceRoot;

    protected abstract WebResourceRoot getWebResourceRoot();
    protected abstract boolean isWriteable();

    public String getMount() {
        return "";
    }

    public abstract String getBaseDir();

    @Before
    public final void setup() throws LifecycleException {
        resourceRoot = getWebResourceRoot();
        resourceRoot.start();
    }

    @After
    public final void teardown() throws LifecycleException {
        resourceRoot.stop();
        resourceRoot.destroy();
    }

    @Test(expected = IllegalArgumentException.class)
    public final void testGetResourceEmpty() {
        resourceRoot.getResource("");
    }

    //------------------------------------------------------------ getResource()

    @Test
    public final void testGetResourceRoot() {
        WebResource webResource = resourceRoot.getResource(getMount() + "/");
        Assert.assertTrue(webResource.isDirectory());
        Assert.assertEquals("", webResource.getName());
        Assert.assertEquals(getMount() + "/", webResource.getWebappPath());
    }

    @Test
    public final void testGetResourceDirA() {
        WebResource webResource = resourceRoot.getResource(getMount() + "/d1");
        Assert.assertTrue(webResource.isDirectory());
        Assert.assertEquals("d1", webResource.getName());
        Assert.assertEquals(getMount() + "/d1/", webResource.getWebappPath());
    }

    @Test
    public final void testGetResourceDirB() {
        WebResource webResource = resourceRoot.getResource(getMount() + "/d1/");
        Assert.assertTrue(webResource.isDirectory());
        Assert.assertEquals("d1", webResource.getName());
        Assert.assertEquals(getMount() + "/d1/", webResource.getWebappPath());
    }

    @Test
    public final void testGetResourceFile() {
        WebResource webResource =
                resourceRoot.getResource(getMount() + "/d1/d1-f1.txt");
        Assert.assertTrue(webResource.isFile());
        Assert.assertEquals("d1-f1.txt", webResource.getName());
        Assert.assertEquals(
                getMount() + "/d1/d1-f1.txt", webResource.getWebappPath());
    }

    //------------------------------------------------------------------- list()

    @Test(expected = IllegalArgumentException.class)
    public final void testListEmpty() {
        resourceRoot.list("");
    }

    @Test
    public final void testListRoot() {
        String[] results = resourceRoot.list(getMount() + "/");

        Set<String> expected = new HashSet<>();
        expected.add("d1");
        expected.add("d2");
        expected.add("f1.txt");
        expected.add("f2.txt");

        for (String result : results) {
            Assert.assertTrue(result, expected.remove(result));
        }
        Assert.assertEquals(0, expected.size());
    }

    @Test
    public final void testListDirA() {
        String[] results = resourceRoot.list(getMount() + "/d1");

        Set<String> expected = new HashSet<>();
        expected.add("d1-f1.txt");

        for (String result : results) {
            Assert.assertTrue(result, expected.remove(result));
        }
        Assert.assertEquals(0, expected.size());
    }

    @Test
    public final void testListDirB() {
        String[] results = resourceRoot.list(getMount() + "/d1/");

        Set<String> expected = new HashSet<>();
        expected.add("d1-f1.txt");

        for (String result : results) {
            Assert.assertTrue(result, expected.remove(result));
        }
        Assert.assertEquals(0, expected.size());
    }

    @Test
    public final void testListFile() {
        String[] results = resourceRoot.list(getMount() + "/d1/d1-f1.txt");

        Assert.assertNotNull(results);
        Assert.assertEquals(0, results.length);
    }

    //-------------------------------------------------------- listWebAppPaths()

    @Test(expected = IllegalArgumentException.class)
    public final void testListWebAppPathsEmpty() {
        resourceRoot.listWebAppPaths("");
    }

    @Test
    public final void testListWebAppPathsRoot() {
        Set<String> results = resourceRoot.listWebAppPaths(getMount() + "/");

        Set<String> expected = new HashSet<>();
        expected.add(getMount() + "/d1/");
        expected.add(getMount() + "/d2/");
        expected.add(getMount() + "/f1.txt");
        expected.add(getMount() + "/f2.txt");

        for (String result : results) {
            Assert.assertTrue(result, expected.remove(result));
        }
        Assert.assertEquals(0, expected.size());
    }

    @Test
    public final void testListWebAppPathsDirA() {
        Set<String> results = resourceRoot.listWebAppPaths(getMount() + "/d1");

        Set<String> expected = new HashSet<>();
        expected.add(getMount() + "/d1/d1-f1.txt");

        for (String result : results) {
            Assert.assertTrue(result, expected.remove(result));
        }
        Assert.assertEquals(0, expected.size());
    }

    @Test
    public final void testListWebAppPathsDirB() {
        Set<String> results = resourceRoot.listWebAppPaths(getMount() + "/d1/");

        Set<String> expected = new HashSet<>();
        expected.add(getMount() + "/d1/d1-f1.txt");

        for (String result : results) {
            Assert.assertTrue(result, expected.remove(result));
        }
        Assert.assertEquals(0, expected.size());
    }

    @Test
    public final void testListWebAppPathsFile() {
        Set<String> results =
                resourceRoot.listWebAppPaths(getMount() + "/d1/d1-f1.txt");

        Assert.assertNull(results);
    }

    //------------------------------------------------------------------ mkdir()

    @Test(expected = IllegalArgumentException.class)
    public final void testMkdirEmpty() {
        resourceRoot.mkdir("");
    }

    @Test
    public final void testMkdirRoot() {
        Assert.assertFalse(resourceRoot.mkdir(getMount() + "/"));
    }

    @Test
    public final void testMkdirDirA() {
        Assert.assertFalse(resourceRoot.mkdir(getMount() + "/d1"));
    }

    @Test
    public final void testMkdirDirB() {
        Assert.assertFalse(resourceRoot.mkdir(getMount() + "/d1/"));
    }

    @Test
    public final void testMkdirFile() {
        Assert.assertFalse(resourceRoot.mkdir(getMount() + "/d1/d1-f1.txt"));
    }

    @Test
    public final void testMkdirNew() {
        if (isWriteable()) {
            Assert.assertTrue(resourceRoot.mkdir(getMount() + "/new-test"));

            File file = new File(getBaseDir(), "new-test");
            Assert.assertTrue(file.isDirectory());
            Assert.assertTrue(file.delete());
        } else {
            Assert.assertFalse(resourceRoot.mkdir(getMount() + "/new-test"));
        }
    }

    //------------------------------------------------------------------ write()

    @Test(expected = IllegalArgumentException.class)
    public final void testWriteEmpty() {
        InputStream is = new ByteArrayInputStream("test".getBytes());
        resourceRoot.write("", is, false);
    }

    @Test
    public final void testWriteRoot() {
        InputStream is = new ByteArrayInputStream("test".getBytes());
        Assert.assertFalse(resourceRoot.write(getMount() + "/", is, false));
    }

    @Test
    public final void testWriteDirA() {
        InputStream is = new ByteArrayInputStream("test".getBytes());
        Assert.assertFalse(resourceRoot.write(getMount() + "/d1", is, false));
    }

    @Test
    public final void testWriteDirB() {
        InputStream is = new ByteArrayInputStream("test".getBytes());
        Assert.assertFalse(resourceRoot.write(getMount() + "/d1/", is, false));
    }

    @Test
    public final void testWriteFile() {
        InputStream is = new ByteArrayInputStream("test".getBytes());
        Assert.assertFalse(resourceRoot.write(
                getMount() + "/d1/d1-f1.txt", is, false));
    }

    @Test(expected = NullPointerException.class)
    public final void testWriteNew() {
        resourceRoot.write(getMount() + "/new-test", null, false);
    }

    @Test
    public final void testWrite() {
        InputStream is = new ByteArrayInputStream("test".getBytes());
        if (isWriteable()) {
            Assert.assertTrue(resourceRoot.write(
                    getMount() + "/new-test", is, false));
            File file = new File(getBaseDir(), "new-test");
            Assert.assertTrue(file.exists());
            Assert.assertTrue(file.delete());
        } else {
            Assert.assertFalse(resourceRoot.write(
                    getMount() + "/new-test", is, false));
        }
    }
}

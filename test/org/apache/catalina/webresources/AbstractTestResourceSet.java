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
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.Manifest;

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

    public abstract File getBaseDir();

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
        doTestGetResourceRoot(true);
    }

    @Test
    public final void testGetResourceRootNoSlash() {
        doTestGetResourceRoot(false);
    }


    private void doTestGetResourceRoot(boolean slash) {
        String mount = getMount();
        if (!slash && mount.length() == 0) {
            return;
        }
        mount = mount + (slash ? "/" : "");

        WebResource webResource = resourceRoot.getResource(mount);

        Assert.assertTrue(webResource.isDirectory());
        String expected;
        if (getMount().length() > 0) {
            expected = getMount().substring(1);
        } else {
            expected = "";
        }
        Assert.assertEquals(expected, webResource.getName());
        Assert.assertEquals(mount + (!slash ? "/" : ""), webResource.getWebappPath());
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

    @Test
    public final void testGetResourceCaseSensitive() {
        WebResource webResource =
                resourceRoot.getResource(getMount() + "/d1/d1-F1.txt");
        Assert.assertFalse(webResource.exists());
    }

    @Test
    public final void testGetResourceTraversal() {
        WebResource webResource = null;
        try {
            webResource = resourceRoot.getResource(getMount() + "/../");
        } catch (IllegalArgumentException iae) {
            // Expected if mount point is zero length
            Assert.assertTrue(getMount().length() == 0);
            return;
        }

        Assert.assertFalse(webResource.exists());
    }


    //------------------------------------------------------------------- list()

    @Test(expected = IllegalArgumentException.class)
    public final void testListEmpty() {
        resourceRoot.list("");
    }

    @Test
    public final void testListRoot() {
        doTestListRoot(true);
    }

    @Test
    public final void testListRootNoSlash() {
        doTestListRoot(false);
    }


    private void doTestListRoot(boolean slash) {
        String mount = getMount();
        if (!slash && mount.length() == 0) {
            return;
        }

        String[] results = resourceRoot.list(mount + (slash ? "/" : ""));

        Set<String> expected = new HashSet<>();
        expected.add("d1");
        expected.add("d2");
        expected.add("f1.txt");
        expected.add("f2.txt");

        // Directories created by Subversion 1.6 and earlier clients
        Set<String> optional = new HashSet<>();
        optional.add(".svn");
        // Files visible in some tests only
        optional.add(getMount() + ".ignore-me.txt");
        optional.add("META-INF");

        for (String result : results) {
            Assert.assertTrue(result,
                    expected.remove(result) || optional.remove(result));
        }
        Assert.assertEquals(0, expected.size());
    }

    @Test
    public final void testListDirA() {
        String[] results = resourceRoot.list(getMount() + "/d1");

        Set<String> expected = new HashSet<>();
        expected.add("d1-f1.txt");

        // Directories created by Subversion 1.6 and earlier clients
        Set<String> optional = new HashSet<>();
        optional.add(".svn");
        // Files visible in some tests only
        optional.add(".ignore-me.txt");

        for (String result : results) {
            Assert.assertTrue(result,
                    expected.remove(result) || optional.remove(result));
        }
        Assert.assertEquals(0, expected.size());
    }

    @Test
    public final void testListDirB() {
        String[] results = resourceRoot.list(getMount() + "/d1/");

        Set<String> expected = new HashSet<>();
        expected.add("d1-f1.txt");

        // Directories created by Subversion 1.6 and earlier clients
        Set<String> optional = new HashSet<>();
        optional.add(".svn");
        // Files visible in some tests only
        optional.add(".ignore-me.txt");

        for (String result : results) {
            Assert.assertTrue(result,
                    expected.remove(result) || optional.remove(result));
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
        doTestListWebAppPathsRoot(true);
    }

    @Test
    public final void testListWebAppPathsRootNoSlash() {
        doTestListWebAppPathsRoot(false);
    }


    private void doTestListWebAppPathsRoot(boolean slash) {
        String mount = getMount();
        if (!slash && mount.length() == 0) {
            return;
        }

        Set<String> results = resourceRoot.listWebAppPaths(mount + (slash ? "/" : ""));

        Set<String> expected = new HashSet<>();
        expected.add(getMount() + "/d1/");
        expected.add(getMount() + "/d2/");
        expected.add(getMount() + "/f1.txt");
        expected.add(getMount() + "/f2.txt");

        // Directories created by Subversion 1.6 and earlier clients
        Set<String> optional = new HashSet<>();
        optional.add(getMount() + "/.svn/");
        // Files visible in some tests only
        optional.add(getMount() + "/.ignore-me.txt");
        // Files visible in some configurations only
        optional.add(getMount() + "/META-INF/");

        for (String result : results) {
            Assert.assertTrue(result,
                    expected.remove(result) || optional.remove(result));
        }
        Assert.assertEquals(0, expected.size());
    }

    @Test
    public final void testListWebAppPathsDirA() {
        Set<String> results = resourceRoot.listWebAppPaths(getMount() + "/d1");

        Set<String> expected = new HashSet<>();
        expected.add(getMount() + "/d1/d1-f1.txt");

        // Directories created by Subversion 1.6 and earlier clients
        Set<String> optional = new HashSet<>();
        optional.add(getMount() + "/d1/.svn/");
        // Files visible in some tests only
        optional.add(getMount() + "/d1/.ignore-me.txt");

        for (String result : results) {
            Assert.assertTrue(result,
                    expected.remove(result) || optional.remove(result));
        }
        Assert.assertEquals(0, expected.size());
    }

    @Test
    public final void testListWebAppPathsDirB() {
        Set<String> results = resourceRoot.listWebAppPaths(getMount() + "/d1/");

        Set<String> expected = new HashSet<>();
        expected.add(getMount() + "/d1/d1-f1.txt");

        // Directories created by Subversion 1.6 and earlier clients
        Set<String> optional = new HashSet<>();
        optional.add(getMount() + "/d1/.svn/");
        // Files visible in some tests only
        optional.add(getMount() + "/d1/.ignore-me.txt");

        for (String result : results) {
            Assert.assertTrue(result,
                    expected.remove(result) || optional.remove(result));
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
        WebResource d1 = resourceRoot.getResource(getMount() + "/d1");
        if (d1.exists()) {
            Assert.assertFalse(resourceRoot.mkdir(getMount() + "/d1"));
        } else if (d1.isVirtual()) {
            Assert.assertTrue(resourceRoot.mkdir(getMount() + "/d1"));
            File file = new File(getBaseDir(), "d1");
            Assert.assertTrue(file.isDirectory());
            Assert.assertTrue(file.delete());
        } else {
            Assert.fail("Unhandled condition in unit test");
        }
    }

    @Test
    public final void testMkdirDirB() {
        WebResource d1 = resourceRoot.getResource(getMount() + "/d1/");
        if (d1.exists()) {
            Assert.assertFalse(resourceRoot.mkdir(getMount() + "/d1/"));
        } else if (d1.isVirtual()) {
            Assert.assertTrue(resourceRoot.mkdir(getMount() + "/d1/"));
            File file = new File(getBaseDir(), "d1");
            Assert.assertTrue(file.isDirectory());
            Assert.assertTrue(file.delete());
        } else {
            Assert.fail("Unhandled condition in unit test");
        }
    }

    @Test
    public final void testMkdirFile() {
        Assert.assertFalse(resourceRoot.mkdir(getMount() + "/d1/d1-f1.txt"));
    }

    @Test
    public final void testMkdirNew() {
        String newDirName = getNewDirName();
        if (isWriteable()) {
            Assert.assertTrue(resourceRoot.mkdir(getMount() + "/" + newDirName));

            File file = new File(getBaseDir(), newDirName);
            Assert.assertTrue(file.isDirectory());
            Assert.assertTrue(file.delete());
        } else {
            Assert.assertFalse(resourceRoot.mkdir(getMount() + "/" + newDirName));
        }
    }

    protected abstract String getNewDirName();

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
        WebResource d1 = resourceRoot.getResource(getMount() + "/d1");
        InputStream is = new ByteArrayInputStream("test".getBytes());
        if (d1.exists()) {
            Assert.assertFalse(resourceRoot.write(getMount() + "/d1", is, false));
        } else if (d1.isVirtual()) {
            Assert.assertTrue(resourceRoot.write(
                    getMount() + "/d1", is, false));
            File file = new File(getBaseDir(), "d1");
            Assert.assertTrue(file.exists());
            Assert.assertTrue(file.delete());
        } else {
            Assert.fail("Unhandled condition in unit test");
        }
    }

    @Test
    public final void testWriteDirB() {
        WebResource d1 = resourceRoot.getResource(getMount() + "/d1/");
        InputStream is = new ByteArrayInputStream("test".getBytes());
        if (d1.exists()) {
            Assert.assertFalse(resourceRoot.write(getMount() + "/d1/", is, false));
        } else if (d1.isVirtual()) {
            Assert.assertTrue(resourceRoot.write(
                    getMount() + "/d1/", is, false));
            File file = new File(getBaseDir(), "d1");
            Assert.assertTrue(file.exists());
            Assert.assertTrue(file.delete());
        } else {
            Assert.fail("Unhandled condition in unit test");
        }
    }

    @Test
    public final void testWriteFile() {
        InputStream is = new ByteArrayInputStream("test".getBytes());
        Assert.assertFalse(resourceRoot.write(
                getMount() + "/d1/d1-f1.txt", is, false));
    }

    @Test(expected = NullPointerException.class)
    public final void testWriteNull() {
        resourceRoot.write(getMount() + "/" + getNewFileNameNull(), null, false);
    }

    protected abstract String getNewFileNameNull();

    @Test
    public final void testWrite() {
        String newFileName = getNewFileName();
        InputStream is = new ByteArrayInputStream("test".getBytes());
        if (isWriteable()) {
            Assert.assertTrue(resourceRoot.write(
                    getMount() + "/" + newFileName, is, false));
            File file = new File(getBaseDir(), newFileName);
            Assert.assertTrue(file.exists());
            Assert.assertTrue(file.delete());
        } else {
            Assert.assertFalse(resourceRoot.write(
                    getMount() + "/" + newFileName, is, false));
        }
    }

    protected abstract String getNewFileName();

    // ------------------------------------------------------ getCanonicalPath()

    @Test
    public final void testGetCanonicalPathExists() {
        WebResource exists =
                resourceRoot.getResource(getMount() + "/d1/d1-f1.txt");
        String existsCanonicalPath = exists.getCanonicalPath();

        URL existsUrl = exists.getURL();
        if ("file".equals(existsUrl.getProtocol())) {
            // Should have a canonical path
            Assert.assertNotNull(existsCanonicalPath);
        } else {
            Assert.assertNull(existsCanonicalPath);
        }
    }

    @Test
    public final void testGetCanonicalPathDoesNotExist() {
        WebResource exists =
                resourceRoot.getResource(getMount() + "/d1/d1-f1.txt");
        WebResource doesNotExist =
                resourceRoot.getResource(getMount() + "/d1/dummy.txt");
        String doesNotExistCanonicalPath = doesNotExist.getCanonicalPath();

        URL existsUrl = exists.getURL();
        if ("file".equals(existsUrl.getProtocol())) {
            // Should be possible to construct a canonical path for a resource
            // that doesn't exist given that a resource that does exist in the
            // same directory has a URL with the file protocol
            Assert.assertNotNull(doesNotExistCanonicalPath);
        } else {
            Assert.assertNull(doesNotExistCanonicalPath);
        }
    }


    // ----------------------------------------------------------- getManifest()

    @Test
    public final void testGetManifest() {
        WebResource exists = resourceRoot.getResource(getMount() + "/d1/d1-f1.txt");
        boolean manifestExists = resourceRoot.getResource("/META-INF/MANIFEST.MF").exists();
        Manifest m = exists.getManifest();
        if (getMount().equals("") && manifestExists) {
            Assert.assertNotNull(m);
        } else {
            Assert.assertNull(m);
        }
    }


    // ------------------------------------------------------------ constructors

    public abstract void testNoArgConstructor();
}

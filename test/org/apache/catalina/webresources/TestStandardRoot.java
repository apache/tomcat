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
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.TrackedWebResource;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.webresources.StandardRoot.BaseLocation;

public class TestStandardRoot extends TomcatBaseTest {

    private static final File file;
    private static final String fileUrl;

    static {
        file = new File("/foo");
        String url = null;
        try {
            url = file.toURI().toURL().toExternalForm();
        } catch (MalformedURLException e) {
            // Ignore
        }
        fileUrl = url;
    }

    @Test
    public void testBaseLocation01() throws Exception {
        doTestBaseLocation(URI.create(fileUrl).toURL(), file.getAbsolutePath(), null);
    }

    @Test
    public void testBaseLocation02() throws Exception {
        doTestBaseLocation(URI.create("jar:" + fileUrl + "!/").toURL(), file.getAbsolutePath(), null);
    }

    @Test
    public void testBaseLocation03() throws Exception {
        doTestBaseLocation(URI.create("jar:" + fileUrl + "!/bar").toURL(), file.getAbsolutePath(), "bar");
    }

    @Test
    public void testBaseLocation04() throws Exception {
        doTestBaseLocation(URI.create("jar:" + fileUrl + "!/bar/bar").toURL(), file.getAbsolutePath(), "bar/bar");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testBaseLocation05() throws Exception {
        doTestBaseLocation(URI.create("http://localhost:8080/foo").toURL(), null, null);
    }

    private void doTestBaseLocation(URL url, String expectedBasePath,
            String expectedArchivePath) {
        BaseLocation baseLocation = new BaseLocation(url);
        Assert.assertEquals(expectedBasePath, baseLocation.getBasePath());
        Assert.assertEquals(expectedArchivePath, baseLocation.getArchivePath());
    }

    @Test
    public void testArchiveIndexStrategy() {
        WebResourceRoot root = new StandardRoot();
        root.setArchiveIndexStrategy(WebResourceRoot.ArchiveIndexStrategy.BLOOM.name());
        Assert.assertEquals(WebResourceRoot.ArchiveIndexStrategy.BLOOM.name(), root.getArchiveIndexStrategy());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testArchiveIndexStrategyUnrecognized() {
        WebResourceRoot root = new StandardRoot();
        root.setArchiveIndexStrategy("UNRECOGNIZED");
    }

    @Test
    public void testGetResourceWhileStopping() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        File docBase = new File("test/webresources/dir1");
        Context context = tomcat.addContext("", docBase.getAbsolutePath());
        tomcat.start();

        StandardRoot resourceRoot = (StandardRoot) context.getResources();
        resourceRoot.setCachingAllowed(false);

        // stopInternal() removes the main resources before the state moves to
        // STOPPING, so requests that are in progress during that window do not
        // see them. Such requests must still be given a non-existent resource
        // rather than null. The window is held open by a tracked resource that
        // blocks while stopInternal() closes it.
        CountDownLatch closing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        resourceRoot.registerTrackedResource(new TrackedWebResource() {

            @Override
            public String getName() {
                return "test";
            }

            @Override
            public Exception getCreatedBy() {
                return null;
            }

            @Override
            public void close() {
                closing.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        Thread stopThread = new Thread(() -> {
            try {
                resourceRoot.stop();
            } catch (LifecycleException e) {
                // Not expected, the assertions below will fail
            }
        });
        stopThread.start();

        try {
            Assert.assertTrue(closing.await(30, TimeUnit.SECONDS));

            WebResource resource = resourceRoot.getResource("/missing.txt");
            Assert.assertNotNull(resource);
            Assert.assertFalse(resource.exists());
        } finally {
            // Never leave the stop thread blocked if an assertion fails
            release.countDown();
        }

        stopThread.join(30000);

        // Leave the resource root running for the standard tear-down
        resourceRoot.start();
    }
}

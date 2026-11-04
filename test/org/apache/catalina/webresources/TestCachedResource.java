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
import java.net.JarURLConnection;
import java.net.URL;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestCachedResource extends TomcatBaseTest {

    // https://bz.apache.org/bugzilla/show_bug.cgi?id=63872
    @Test
    public void testUrlFileFromDirectory() throws Exception {

        Tomcat tomcat = getTomcatInstance();
        File docBase = new File("test/webresources/dir1");
        Context ctx = tomcat.addWebapp("/test", docBase.getAbsolutePath());
        tomcat.start();

        WebResourceRoot root = ctx.getResources();

        URL d1 = root.getResource("/d1").getURL();

        URL d1f1 = d1.toURI().resolve("d1-f1.txt").toURL();

        try (InputStream is = d1f1.openStream()) {
            Assert.assertNotNull(is);
        }
    }


    // https://bz.apache.org/bugzilla/show_bug.cgi?id=63970
    @Test
    public void testCachedJarUrlConnection() throws Exception {

        Tomcat tomcat = getTomcatInstance();
        File docBase = new File("test/webresources/war-url-connection.war");
        Context ctx = tomcat.addWebapp("/test", docBase.getAbsolutePath());
        tomcat.start();

        WebResourceRoot root = ctx.getResources();

        // WAR contains a resources JAR so this should return a JAR URL
        URL webinf = root.getResource("/index.html").getURL();

        Assert.assertEquals("jar", webinf.getProtocol());
        JarURLConnection jarConn = null;
        try {
            jarConn = (JarURLConnection) webinf.openConnection();
        } catch (ClassCastException e) {
            // Ignore
        }
        Assert.assertNotNull(jarConn);
    }


    @Test
    public void testDirectoryListingsPackedWar() throws Exception {

        Tomcat tomcat = getTomcatInstance();
        File docBase = new File("test/webresources/war-url-connection.war");
        Context ctx = tomcat.addWebapp("/test", docBase.getAbsolutePath());
        ((StandardHost) tomcat.getHost()).setUnpackWARs(false);
        tomcat.start();

        WebResourceRoot root = ctx.getResources();

        URL d1 = root.getResource("/").getURL();

        try (InputStream is = d1.openStream()) {
            Assert.assertNotNull(is);
        }
    }


    @Test
    public void testCollectionsAreNotSharedBetweenClassLoaderResourceValues()
            throws Exception {

        // Regression test: a collection cached for a path with
        // useClassLoaderResources set to false must not be served to a caller
        // using true (or vice versa). Cache entries are keyed on path only and
        // each entry is valid for a single value of useClassLoaderResources.
        // A class loader resource lookup for the class loader root ('' maps
        // to the path '/WEB-INF/classes' which is also the key used by the
        // static lookup) hits the same cache entry. The packed WAR provides a
        // resource set that is only visible to class loader resource lookups
        // (the JAR in WEB-INF/lib mounted at WEB-INF/classes).
        Tomcat tomcat = getTomcatInstance();
        File docBase = new File("test/webresources/war-url-connection.war");
        Context ctx = tomcat.addWebapp("/test", docBase.getAbsolutePath());
        ((StandardHost) tomcat.getHost()).setUnpackWARs(false);
        tomcat.start();

        WebResourceRoot root = ctx.getResources();

        // This populates the cache entry for the path with
        // useClassLoaderResources set to false.
        WebResource[] withoutClassLoaderResources = root.getResources("/WEB-INF/classes");
        // Repeated lookups within the cache TTL are served from the cache.
        Assert.assertEquals(withoutClassLoaderResources.length,
                root.getResources("/WEB-INF/classes").length);

        // This previously returned the cached collection created with
        // useClassLoaderResources set to false, which does not include the
        // JAR in WEB-INF/lib.
        WebResource[] withClassLoaderResources = root.getClassLoaderResources("");
        Assert.assertTrue(withClassLoaderResources.length > withoutClassLoaderResources.length);

        // The collection cached for the class loader resource lookup must not
        // be served to the static lookup either.
        Assert.assertEquals(withoutClassLoaderResources.length,
                root.getResources("/WEB-INF/classes").length);
    }


    @Test
    public void testDirectoryListingsWar() throws Exception {

        Tomcat tomcat = getTomcatInstance();
        File docBase = new File("test/webresources/war-url-connection.war");
        Context ctx = tomcat.addWebapp("/test", docBase.getAbsolutePath());
        tomcat.start();

        WebResourceRoot root = ctx.getResources();

        URL d1 = root.getResource("/").getURL();

        try (InputStream is = d1.openStream()) {
            Assert.assertNotNull(is);
        }
    }


    @Test
    public void testDirectoryListingsDir() throws Exception {

        Tomcat tomcat = getTomcatInstance();
        File docBase = new File("test/webresources/dir1");
        Context ctx = tomcat.addWebapp("/test", docBase.getAbsolutePath());
        tomcat.start();

        WebResourceRoot root = ctx.getResources();

        URL d1 = root.getResource("/d1").getURL();

        try (InputStream is = d1.openStream()) {
            Assert.assertNotNull(is);
        }
    }


    @Test
    public void testGetContentWebInfClasses() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        File docBase = new File("test/webapp");
        Context ctx = tomcat.addWebapp("/test", docBase.getAbsolutePath());
        tomcat.start();

        URL url = ctx.getLoader().getClassLoader().getResource("bug69623-a.mdd");
        Object o = url.getContent();
        /*
         * Could test the actual content but a non-null return without an exception is enough to demonstrate the bug has
         * not occurred.
         */
        Assert.assertTrue(o instanceof ByteArrayInputStream);
    }


    @Test
    public void testGetContentWebInfLib() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        File docBase = new File("test/webapp");
        Context ctx = tomcat.addWebapp("/test", docBase.getAbsolutePath());
        tomcat.start();

        URL url = ctx.getLoader().getClassLoader().getResource("bug69623-b.mdd");
        Object o = url.getContent();
        /*
         * Could test the actual content but a non-null return without an exception is enough to demonstrate the bug has
         * not occurred.
         */
        Assert.assertTrue(o instanceof ByteArrayInputStream);
    }
}

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
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.WebResourceRoot;
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

        URL d1f1 = new URL(d1, "d1-f1.txt");

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

        // WAR contains a resoucres JAR so this should return a JAR URL
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

}

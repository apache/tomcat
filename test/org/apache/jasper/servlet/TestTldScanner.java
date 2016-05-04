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
package org.apache.jasper.servlet;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.Jar;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.scan.JarFactory;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.easymock.EasyMock;

public class TestTldScanner extends TomcatBaseTest {

    @Test
    public void testWithWebapp() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        File appDir = new File("test/webapp-3.0");
        Context context = tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());
        tomcat.start();

        TldScanner scanner =
                new TldScanner(context.getServletContext(), true, true, true);
        scanner.scan();
        Assert.assertEquals(5, scanner.getUriTldResourcePathMap().size());
        Assert.assertEquals(1, scanner.getListeners().size());
    }


    @Test
    public void testBug55807() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        Context context = tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());
        ((StandardJarScanner) context.getJarScanner()).setScanAllDirectories(true);
        tomcat.start();

        ByteChunk res = new ByteChunk();
        Map<String,List<String>> headers = new HashMap<>();

        getUrl("http://localhost:" + getPort() + "/test/bug5nnnn/bug55807.jsp",
                res, headers);

        // Check request completed
        String result = res.toString();
        assertEcho(result, "OK");

        // Check the dependencies count
        Assert.assertTrue(result.contains("<p>DependenciesCount: 1</p>"));

        // Check the right timestamp was used in the dependency
        File tld = new File("test/webapp/WEB-INF/classes/META-INF/bug55807.tld");
        String expected = "<p>/WEB-INF/classes/META-INF/bug55807.tld : " +
                tld.lastModified() + "</p>";
        Assert.assertTrue(result.contains(expected));


        // Check content type
        Assert.assertTrue(headers.get("Content-Type").get(0).startsWith("text/html"));
    }


    /** Assertion for text printed by tags:echo */
    private static void assertEcho(String result, String expected) {
        Assert.assertTrue(result, result.indexOf("<p>" + expected + "</p>") > 0);
    }

    @Test
    public void testBug57647() throws Exception {
        TldScanner scanner = EasyMock.createMock(TldScanner.class);
        Constructor<TldScanner.TldScannerCallback> constructor =
                TldScanner.TldScannerCallback.class.getDeclaredConstructor(TldScanner.class);
        constructor.setAccessible(true);
        TldScanner.TldScannerCallback callback = constructor.newInstance(scanner);

        File webappDir = new File("webapps/examples");
        Assert.assertFalse(callback.scanFoundNoTLDs());
        scan(callback, webappDir, "WEB-INF/lib/taglibs-standard-spec-1.2.5.jar");
        Assert.assertTrue(callback.scanFoundNoTLDs());
        scan(callback, webappDir, "WEB-INF/lib/taglibs-standard-impl-1.2.5.jar");
        Assert.assertTrue(callback.scanFoundNoTLDs());
    }

    private static void scan(TldScanner.TldScannerCallback callback, File webapp, String path)
            throws Exception {
        String fullPath = new File(webapp, path).toURI().toString();
        URL jarUrl = new URL("jar:" + fullPath + "!/");
        try (Jar jar = JarFactory.newInstance(jarUrl)) {
            callback.scan(jar, path, true);
        }
    }
}


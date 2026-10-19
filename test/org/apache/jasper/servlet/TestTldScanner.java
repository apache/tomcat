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
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import jakarta.servlet.ServletContext;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.Jar;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.tld.TldResourcePath;
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
    public void testScanImplicitTldInvalid() throws Exception {
        // JSP.7.3.2 only allows version information and a short name in an
        // implicit.tld. Elements such as <uri>, <listener> and <tag> must be
        // rejected, so scanning must not register anything from this file.
        Tomcat tomcat = getTomcatInstance();
        File appDir = new File(getTemporaryDirectory(), "implicit-tld-invalid");
        createImplicitTld(appDir, "<uri>http://tomcat.apache.org/from-implicit-tld</uri>\n" +
                "  <listener>\n" +
                "    <listener-class>com.example.TesterListener</listener-class>\n" +
                "  </listener>\n" +
                "  <tag>\n" +
                "    <name>Foo</name>\n" +
                "    <tag-class>com.example.TesterTag</tag-class>\n" +
                "    <body-content>empty</body-content>\n" +
                "  </tag>");
        Context context = tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());
        tomcat.start();

        TldScanner scanner =
                new TldScanner(context.getServletContext(), true, true, true);
        scanner.scan();

        Assert.assertNull(scanner.getUriTldResourcePathMap().get(
                "http://tomcat.apache.org/from-implicit-tld"));
        Assert.assertEquals(0, scanner.getListeners().size());
        Assert.assertEquals(0, scanner.getTldResourcePathTaglibXmlMap().size());
    }


    @Test
    public void testScanImplicitTldValid() throws Exception {
        // A specification compliant implicit.tld only contributes the short
        // name which is used for tagdir based lookups, not for URI based ones.
        Tomcat tomcat = getTomcatInstance();
        File appDir = new File(getTemporaryDirectory(), "implicit-tld-valid");
        createImplicitTld(appDir, null);
        Context context = tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());
        tomcat.start();

        TldScanner scanner =
                new TldScanner(context.getServletContext(), true, true, true);
        scanner.scan();

        // The specification compliant file is still parsed and cached but it
        // contributes nothing to URI based lookups.
        Assert.assertEquals(0, scanner.getUriTldResourcePathMap().size());
        Assert.assertEquals(0, scanner.getListeners().size());
        Assert.assertEquals(1, scanner.getTldResourcePathTaglibXmlMap().size());
    }


    @Test
    public void testParseTldMissingResourceIsSkipped() throws Exception {
        // A resource listed by getResourcePaths() may be gone by the time it
        // is resolved with getResource() (e.g. a file removed during a
        // development-mode scan). The scan must skip it with a warning rather
        // than propagate a NullPointerException out of the constructor.
        ServletContext mockContext = EasyMock.createMock(ServletContext.class);
        EasyMock.expect(mockContext.getResourcePaths("/WEB-INF/"))
                .andReturn(Collections.singleton("/WEB-INF/vanished.tld")).once();
        EasyMock.expect(mockContext.getResource("/WEB-INF/vanished.tld")).andReturn(null).once();
        EasyMock.replay(mockContext);

        TldScanner scanner = new TldScanner(mockContext, true, true, true);
        try (LogCapture logCapture = attachLogCapture(Level.WARNING, TldScanner.class.getName())) {
            scanner.scanResourcePaths("/WEB-INF/");
            Assert.assertTrue(logCapture.containsText(
                    "Failed to process TLD at resource path [/WEB-INF/vanished.tld]"));
        }

        EasyMock.verify(mockContext);
        // The vanished TLD contributed nothing.
        Assert.assertEquals(0, scanner.getUriTldResourcePathMap().size());
        Assert.assertEquals(0, scanner.getTldResourcePathTaglibXmlMap().size());
    }


    @Test
    public void testListenersNotDuplicatedForDuplicateTaglibUri() throws Exception {
        // The same tag library may be discovered more than once, for example
        // a TLD declared in web.xml that is also found unpacked in
        // WEB-INF/classes/META-INF or shipped in two JARs. The first mapping
        // wins for the taglib URI and the listeners of the duplicate must not
        // be registered again, otherwise the listener class is instantiated
        // twice and receives each event twice.
        ServletContext mockContext = EasyMock.createMock(ServletContext.class);
        EasyMock.replay(mockContext);

        TldScanner scanner = new TldScanner(mockContext, true, true, true);

        File appDir = new File(getTemporaryDirectory(), "duplicate-listeners");
        TldResourcePath first = createTld(appDir, "one",
                "<uri>http://tomcat.apache.org/duplicate</uri>\n" +
                "  <listener>\n" +
                "    <listener-class>com.example.DuplicateListener</listener-class>\n" +
                "  </listener>");
        TldResourcePath second = createTld(appDir, "two",
                "<uri>http://tomcat.apache.org/duplicate</uri>\n" +
                "  <listener>\n" +
                "    <listener-class>com.example.DuplicateListener</listener-class>\n" +
                "  </listener>");

        scanner.parseTld(first);
        scanner.parseTld(second);

        EasyMock.verify(mockContext);
        Assert.assertEquals(1, scanner.getListeners().size());
        Assert.assertEquals("com.example.DuplicateListener", scanner.getListeners().get(0));
        // The first mapping wins for the taglib URI.
        Assert.assertEquals(1, scanner.getUriTldResourcePathMap().size());
        Assert.assertSame(first,
                scanner.getUriTldResourcePathMap().get("http://tomcat.apache.org/duplicate"));
        // Both parsed TLDs remain cached since they may also be referenced
        // directly by resource path.
        Assert.assertEquals(2, scanner.getTldResourcePathTaglibXmlMap().size());
    }


    private static TldResourcePath createTld(File appDir, String name,
            String extraElements) throws Exception {
        File tld = new File(appDir, name + "/example.tld");
        Files.createDirectories(tld.getParentFile().toPath());
        String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<taglib xmlns=\"http://java.sun.com/xml/ns/j2ee\"\n" +
                "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "    xsi:schemaLocation=\"http://java.sun.com/xml/ns/j2ee\n" +
                "        http://java.sun.com/xml/ns/j2ee/web-jsptaglibrary_2_0.xsd\"\n" +
                "    version=\"2.0\">\n" +
                "  <tlib-version>1.0</tlib-version>\n" +
                "  <short-name>example</short-name>\n" +
                "  " + extraElements + "\n" +
                "</taglib>\n";
        Files.writeString(tld.toPath(), content, StandardCharsets.UTF_8);
        return new TldResourcePath(tld.toURI().toURL(), "/" + name + "/example.tld");
    }


    private static void createImplicitTld(File appDir, String extraElements) throws Exception {
        File implicitTld = new File(appDir, "WEB-INF/tags/display/implicit.tld");
        Files.createDirectories(implicitTld.getParentFile().toPath());
        StringBuilder content = new StringBuilder(
                "<taglib xmlns=\"http://java.sun.com/xml/ns/javaee\"\n" +
                "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "    xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee\n" +
                "        http://java.sun.com/xml/ns/javaee/web-jsptaglibrary_2_1.xsd\"\n" +
                "    version=\"2.1\">\n" +
                "  <tlib-version>1.0</tlib-version>\n" +
                "  <short-name>display</short-name>\n");
        if (extraElements != null) {
            content.append("  ").append(extraElements).append('\n');
        }
        content.append("</taglib>\n");
        Files.writeString(implicitTld.toPath(), content, StandardCharsets.UTF_8);
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
        String contentType = getSingleHeader("Content-Type", headers);
        Assert.assertTrue(contentType.startsWith("text/html"));
    }


    /** Assertion for text printed by tags:echo */
    private static void assertEcho(String result, String expected) {
        Assert.assertTrue(result, result.indexOf("<p>" + expected + "</p>") > 0);
    }

    @Test
    public void testBug57647() throws Exception {
        TldScanner scanner = EasyMock.createMock(TldScanner.class);
        Field f = TldScanner.class.getDeclaredField("log");
        f.setAccessible(true);
        f.set(scanner, LogFactory.getLog(TldScanner.class));
        Constructor<TldScanner.TldScannerCallback> constructor =
                TldScanner.TldScannerCallback.class.getDeclaredConstructor(TldScanner.class);
        constructor.setAccessible(true);
        TldScanner.TldScannerCallback callback = constructor.newInstance(scanner);

        File webappDir = new File("webapps/examples");
        Assert.assertFalse(callback.scanFoundNoTLDs());
        scan(callback, webappDir, "WEB-INF/lib/taglibs-standard-spec-1.2.5-migrated-0.0.1.jar");
        Assert.assertTrue(callback.scanFoundNoTLDs());
        scan(callback, webappDir, "WEB-INF/lib/taglibs-standard-impl-1.2.5-migrated-0.0.1.jar");
        Assert.assertTrue(callback.scanFoundNoTLDs());
    }

    private static void scan(TldScanner.TldScannerCallback callback, File webapp, String path)
            throws Exception {
        String fullPath = new File(webapp, path).toURI().toString();
        URL jarUrl = URI.create("jar:" + fullPath + "!/").toURL();
        try (Jar jar = JarFactory.newInstance(jarUrl)) {
            callback.scan(jar, path, true);
        }
    }
}


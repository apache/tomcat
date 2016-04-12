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
package org.apache.catalina.servlets;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import javax.servlet.http.HttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Assert;
import org.junit.Test;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.websocket.server.WsContextListener;

public class TestDefaultServlet extends TomcatBaseTest {

    /*
     * Test attempting to access special paths (WEB-INF/META-INF) using
     * DefaultServlet.
     */
    @Test
    public void testGetSpecials() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        String contextPath = "/examples";

        File appDir = new File(getBuildDirectory(), "webapps" + contextPath);
        // app dir is relative to server home
        tomcat.addWebapp(null, "/examples", appDir.getAbsolutePath());

        tomcat.start();

        final ByteChunk res = new ByteChunk();

        int rc =getUrl("http://localhost:" + getPort() + contextPath +
                "/WEB-INF/web.xml", res, null);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);

        rc =getUrl("http://localhost:" + getPort() + contextPath +
                "/WEB-INF/doesntexistanywhere", res, null);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);

        rc =getUrl("http://localhost:" + getPort() + contextPath +
                "/WEB-INF/", res, null);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);

        rc =getUrl("http://localhost:" + getPort() + contextPath +
                "/META-INF/MANIFEST.MF", res, null);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);

        rc =getUrl("http://localhost:" + getPort() + contextPath +
                "/META-INF/doesntexistanywhere", res, null);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);

    }

    /*
     * Verify serving of gzipped resources from context root.
     */
    @Test
    public void testGzippedFile() throws Exception {

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");

        File gzipIndex = new File(appDir, "index.html.gz");
        long gzipSize = gzipIndex.length();

        File index = new File(appDir, "index.html");
        long indexSize = index.length();

        // app dir is relative to server home
        Context ctxt = tomcat.addContext("", appDir.getAbsolutePath());
        Wrapper defaultServlet = Tomcat.addServlet(ctxt, "default",
                "org.apache.catalina.servlets.DefaultServlet");
        defaultServlet.addInitParameter("gzip", "true");
        ctxt.addServletMapping("/", "default");

        ctxt.addMimeMapping("html", "text/html");

        tomcat.start();

        TestCompressedClient gzipClient = new TestCompressedClient(getPort());

        gzipClient.reset();
        gzipClient.setRequest(new String[] {
                "GET /index.html HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Connection: Close" + CRLF +
                "Accept-Encoding: gzip, br" + CRLF + CRLF });
        gzipClient.connect();
        gzipClient.processRequest();
        assertTrue(gzipClient.isResponse200());
        List<String> responseHeaders = gzipClient.getResponseHeaders();
        assertTrue(responseHeaders.contains("Content-Encoding: gzip"));
        assertTrue(responseHeaders.contains("Content-Length: " + gzipSize));
        assertTrue(responseHeaders.contains("Vary: accept-encoding"));

        gzipClient.reset();
        gzipClient.setRequest(new String[] {
                "GET /index.html HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Connection: Close" + CRLF+ CRLF });
        gzipClient.connect();
        gzipClient.processRequest();
        assertTrue(gzipClient.isResponse200());
        responseHeaders = gzipClient.getResponseHeaders();
        assertTrue(responseHeaders.contains("Content-Type: text/html"));
        assertFalse(responseHeaders.contains("Content-Encoding: gzip"));
        assertTrue(responseHeaders.contains("Content-Length: " + indexSize));
        assertTrue(responseHeaders.contains("Vary: accept-encoding"));
    }

    /*
     * Verify serving of brotli compressed resources from context root.
     */
    @Test
    public void testBrotliCompressedFile() throws Exception {

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");

        long brSize = new File(appDir, "index.html.br").length();
        long indexSize = new File(appDir, "index.html").length();

        // app dir is relative to server home
        Context ctxt = tomcat.addContext("", appDir.getAbsolutePath());
        Wrapper defaultServlet = Tomcat.addServlet(ctxt, "default",
                "org.apache.catalina.servlets.DefaultServlet");
        defaultServlet.addInitParameter("precompressed", "true");

        ctxt.addServletMapping("/", "default");
        ctxt.addMimeMapping("html", "text/html");

        tomcat.start();

        TestCompressedClient client = new TestCompressedClient(getPort());

        client.reset();
        client.setRequest(new String[] {
                "GET /index.html HTTP/1.1" + CRLF +
                        "Host: localhost" + CRLF +
                        "Connection: Close" + CRLF +
                        "Accept-Encoding: br, gzip" + CRLF + CRLF });
        client.connect();
        client.processRequest();
        assertTrue(client.isResponse200());
        List<String> responseHeaders = client.getResponseHeaders();
        assertTrue(responseHeaders.contains("Content-Encoding: br"));
        assertTrue(responseHeaders.contains("Content-Length: " + brSize));
        assertTrue(responseHeaders.contains("Vary: accept-encoding"));

        client.reset();
        client.setRequest(new String[] {
                "GET /index.html HTTP/1.1" + CRLF +
                        "Host: localhost" + CRLF +
                        "Connection: Close" + CRLF+ CRLF });
        client.connect();
        client.processRequest();
        assertTrue(client.isResponse200());
        responseHeaders = client.getResponseHeaders();
        assertTrue(responseHeaders.contains("Content-Type: text/html"));
        assertFalse(responseHeaders.contains("Content-Encoding"));
        assertTrue(responseHeaders.contains("Content-Length: " + indexSize));
        assertTrue(responseHeaders.contains("Vary: accept-encoding"));
    }

    /*
     * Verify serving of custom compressed resources from context root.
     */
    @Test
    public void testCustomCompressedFile() throws Exception {

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");

        long brSize = new File(appDir, "index.html.br").length();
        long gzSize = new File(appDir, "index.html.gz").length();

        // app dir is relative to server home
        Context ctxt = tomcat.addContext("", appDir.getAbsolutePath());
        Wrapper defaultServlet = Tomcat.addServlet(ctxt, "default",
                DefaultServlet.class.getName());
        defaultServlet.addInitParameter("precompressed", "gzip=.gz,custom=.br");

        ctxt.addServletMapping("/", "default");
        ctxt.addMimeMapping("html", "text/html");

        tomcat.start();

        TestCompressedClient client = new TestCompressedClient(getPort());

        client.reset();
        client.setRequest(new String[] {
                "GET /index.html HTTP/1.1" + CRLF +
                        "Host: localhost" + CRLF +
                        "Connection: Close" + CRLF +
                        "Accept-Encoding: br, gzip ; q = 0.5 , custom" + CRLF + CRLF });
        client.connect();
        client.processRequest();
        assertTrue(client.isResponse200());
        List<String> responseHeaders = client.getResponseHeaders();
        assertTrue(responseHeaders.contains("Content-Encoding: custom"));
        assertTrue(responseHeaders.contains("Content-Length: " + brSize));
        assertTrue(responseHeaders.contains("Vary: accept-encoding"));

        client.reset();
        client.setRequest(new String[] {
                "GET /index.html HTTP/1.1" + CRLF +
                        "Host: localhost" + CRLF +
                        "Connection: Close" + CRLF +
                        "Accept-Encoding: br;q=1,gzip,custom" + CRLF + CRLF });
        client.connect();
        client.processRequest();
        assertTrue(client.isResponse200());
        responseHeaders = client.getResponseHeaders();
        assertTrue(responseHeaders.contains("Content-Encoding: gzip"));
        assertTrue(responseHeaders.contains("Content-Length: " + gzSize));
        assertTrue(responseHeaders.contains("Vary: accept-encoding"));
    }

    /*
     * Verify that "*" and "identity" values are handled correctly in accept-encoding header.
     */
    @Test
    public void testIdentityAndStarAcceptEncodings() throws Exception {

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");

        long brSize = new File(appDir, "index.html.br").length();
        long indexSize = new File(appDir, "index.html").length();

        // app dir is relative to server home
        Context ctxt = tomcat.addContext("", appDir.getAbsolutePath());
        Wrapper defaultServlet = Tomcat.addServlet(ctxt, "default",
                DefaultServlet.class.getName());
        defaultServlet.addInitParameter("precompressed", "br=.br,gzip=.gz");

        ctxt.addServletMapping("/", "default");
        ctxt.addMimeMapping("html", "text/html");

        tomcat.start();

        TestCompressedClient client = new TestCompressedClient(getPort());

        client.reset();
        client.setRequest(new String[] {
                "GET /index.html HTTP/1.1" + CRLF +
                        "Host: localhost" + CRLF +
                        "Connection: Close" + CRLF +
                        "Accept-Encoding: gzip;q=0.9,*" + CRLF + CRLF });
        client.connect();
        client.processRequest();
        assertTrue(client.isResponse200());
        List<String> responseHeaders = client.getResponseHeaders();
        assertTrue(responseHeaders.contains("Content-Encoding: br"));
        assertTrue(responseHeaders.contains("Content-Length: " + brSize));
        assertTrue(responseHeaders.contains("Vary: accept-encoding"));

        client.reset();
        client.setRequest(new String[] {
                "GET /index.html HTTP/1.1" + CRLF +
                        "Host: localhost" + CRLF +
                        "Connection: Close" + CRLF +
                        "Accept-Encoding: gzip;q=0.9,br;q=0,identity," + CRLF + CRLF });
        client.connect();
        client.processRequest();
        assertTrue(client.isResponse200());
        responseHeaders = client.getResponseHeaders();
        assertFalse(responseHeaders.contains("Content-Encoding"));
        assertTrue(responseHeaders.contains("Content-Length: " + indexSize));
        assertTrue(responseHeaders.contains("Vary: accept-encoding"));
    }

    /*
     * Verify preferring of brotli in default configuration for actual Firefox and Chrome requests.
     */
    @Test
    public void testBrotliPreference() throws Exception {

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");

        long brSize = new File(appDir, "index.html.br").length();

        // app dir is relative to server home
        Context ctxt = tomcat.addContext("", appDir.getAbsolutePath());
        Wrapper defaultServlet = Tomcat.addServlet(ctxt, "default",
                DefaultServlet.class.getName());
        defaultServlet.addInitParameter("precompressed", "true");

        ctxt.addServletMapping("/", "default");
        ctxt.addMimeMapping("html", "text/html");

        tomcat.start();

        TestCompressedClient client = new TestCompressedClient(getPort());

        // Firefox 45 Accept-Encoding
        client.reset();
        client.setRequest(new String[] {
                "GET /index.html HTTP/1.1" + CRLF +
                        "Host: localhost" + CRLF +
                        "Connection: Close" + CRLF +
                        "Accept-Encoding: gzip, deflate, br" + CRLF + CRLF });
        client.connect();
        client.processRequest();
        assertTrue(client.isResponse200());
        List<String> responseHeaders = client.getResponseHeaders();
        assertTrue(responseHeaders.contains("Content-Encoding: br"));
        assertTrue(responseHeaders.contains("Content-Length: " + brSize));
        assertTrue(responseHeaders.contains("Vary: accept-encoding"));

        // Chrome 50 Accept-Encoding
        client.reset();
        client.setRequest(new String[] {
                "GET /index.html HTTP/1.1" + CRLF +
                        "Host: localhost" + CRLF +
                        "Connection: Close" + CRLF +
                        "Accept-Encoding: gzip, deflate, sdch, br" + CRLF + CRLF });
        client.connect();
        client.processRequest();
        assertTrue(client.isResponse200());
        responseHeaders = client.getResponseHeaders();
        assertTrue(responseHeaders.contains("Content-Encoding: br"));
        assertTrue(responseHeaders.contains("Content-Length: " + brSize));
        assertTrue(responseHeaders.contains("Vary: accept-encoding"));
    }

    /*
     * Test https://bz.apache.org/bugzilla/show_bug.cgi?id=50026
     * Verify serving of resources from context root with subpath mapping.
     */
    @Test
    public void testGetWithSubpathmount() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        String contextPath = "/examples";

        File appDir = new File(getBuildDirectory(), "webapps" + contextPath);
        // app dir is relative to server home
        org.apache.catalina.Context ctx =
            tomcat.addWebapp(null, "/examples", appDir.getAbsolutePath());
        ctx.addApplicationListener(WsContextListener.class.getName());

        // Override the default servlet with our own mappings
        Tomcat.addServlet(ctx, "default2", new DefaultServlet());
        ctx.addServletMapping("/", "default2");
        ctx.addServletMapping("/servlets/*", "default2");
        ctx.addServletMapping("/static/*", "default2");

        tomcat.start();

        final ByteChunk res = new ByteChunk();

        // Make sure DefaultServlet isn't exposing special directories
        // by remounting the webapp under a sub-path

        int rc =getUrl("http://localhost:" + getPort() + contextPath +
                "/static/WEB-INF/web.xml", res, null);

        assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);
        rc =getUrl("http://localhost:" + getPort() + contextPath +
                "/static/WEB-INF/doesntexistanywhere", res, null);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);

        assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);
        rc =getUrl("http://localhost:" + getPort() + contextPath +
                "/static/WEB-INF/", res, null);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);

        rc =getUrl("http://localhost:" + getPort() + contextPath +
                "/static/META-INF/MANIFEST.MF", res, null);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);

        rc =getUrl("http://localhost:" + getPort() + contextPath +
                "/static/META-INF/doesntexistanywhere", res, null);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);

        // Make sure DefaultServlet is serving resources relative to the
        // context root regardless of where the it is mapped

        final ByteChunk rootResource = new ByteChunk();
        rc =getUrl("http://localhost:" + getPort() + contextPath +
                "/index.html", rootResource, null);
        assertEquals(HttpServletResponse.SC_OK, rc);

        final ByteChunk subpathResource = new ByteChunk();
        rc =getUrl("http://localhost:" + getPort() + contextPath +
                "/servlets/index.html", subpathResource, null);
        assertEquals(HttpServletResponse.SC_OK, rc);

        assertFalse(rootResource.toString().equals(subpathResource.toString()));

        rc =getUrl("http://localhost:" + getPort() + contextPath +
                "/static/index.html", res, null);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);

    }

    /*
     * Test https://bz.apache.org/bugzilla/show_bug.cgi?id=50413 Serving a
     * custom error page
     */
    @Test
    public void testCustomErrorPage() throws Exception {
        File appDir = new File(getTemporaryDirectory(), "MyApp");
        File webInf = new File(appDir, "WEB-INF");
        addDeleteOnTearDown(appDir);
        if (!webInf.mkdirs() && !webInf.isDirectory()) {
            fail("Unable to create directory [" + webInf + "]");
        }

        File webxml = new File(appDir, "WEB-INF/web.xml");
        try (FileOutputStream fos = new FileOutputStream(webxml);
                Writer w = new OutputStreamWriter(fos, "UTF-8");) {
            w.write("<?xml version='1.0' encoding='UTF-8'?>\n"
                    + "<web-app xmlns='http://java.sun.com/xml/ns/j2ee' "
                    + " xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'"
                    + " xsi:schemaLocation='http://java.sun.com/xml/ns/j2ee "
                    + " http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd'"
                    + " version='2.4'>\n"
                    + "<error-page>\n<error-code>404</error-code>\n"
                    + "<location>/404.html</location>\n</error-page>\n"
                    + "</web-app>\n");
        }

        File error404 = new File(appDir, "404.html");
        try (FileOutputStream fos = new FileOutputStream(error404);
                Writer w = new OutputStreamWriter(fos, "ISO-8859-1")) {
            w.write("It is 404.html");
        }

        Tomcat tomcat = getTomcatInstance();
        String contextPath = "/MyApp";
        tomcat.addWebapp(null, contextPath, appDir.getAbsolutePath());
        tomcat.start();

        TestCustomErrorClient client =
                new TestCustomErrorClient(tomcat.getConnector().getLocalPort());

        client.reset();
        client.setRequest(new String[] {
                "GET /MyApp/missing HTTP/1.0" +CRLF + CRLF });
        client.connect();
        client.processRequest();
        assertTrue(client.isResponse404());
        assertEquals("It is 404.html", client.getResponseBody());

        SimpleDateFormat format = new SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        String tomorrow = format.format(new Date(System.currentTimeMillis()
                + 24 * 60 * 60 * 1000));

        // https://bz.apache.org/bugzilla/show_bug.cgi?id=50413
        //
        client.reset();
        client.setRequest(new String[] {
                "GET /MyApp/missing HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Connection: close" + CRLF +
                "If-Modified-Since: " + tomorrow + CRLF + CRLF });
        client.connect();
        client.processRequest();
        assertTrue(client.isResponse404());
        assertEquals("It is 404.html", client.getResponseBody());

        // https://bz.apache.org/bugzilla/show_bug.cgi?id=50413#c6
        //
        client.reset();
        client.setRequest(new String[] {
                "GET /MyApp/missing HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Connection: close" + CRLF +
                "Range: bytes=0-100" + CRLF + CRLF });
        client.connect();
        client.processRequest();
        assertTrue(client.isResponse404());
        assertEquals("It is 404.html", client.getResponseBody());
    }

    /*
     * Test what happens if a custom 404 page is configured,
     * but its file is actually missing.
     */
    @Test
    public void testCustomErrorPageMissing() throws Exception {
        File appDir = new File(getTemporaryDirectory(), "MyApp");
        File webInf = new File(appDir, "WEB-INF");
        addDeleteOnTearDown(appDir);
        if (!webInf.mkdirs() && !webInf.isDirectory()) {
            fail("Unable to create directory [" + webInf + "]");
        }

        File webxml = new File(appDir, "WEB-INF/web.xml");
        try (FileOutputStream fos = new FileOutputStream(webxml);
                Writer w = new OutputStreamWriter(fos, "UTF-8");) {
            w.write("<?xml version='1.0' encoding='UTF-8'?>\n"
                    + "<web-app xmlns='http://java.sun.com/xml/ns/j2ee' "
                    + " xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'"
                    + " xsi:schemaLocation='http://java.sun.com/xml/ns/j2ee "
                    + " http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd'"
                    + " version='2.4'>\n"
                    + "<error-page>\n<error-code>404</error-code>\n"
                    + "<location>/404-absent.html</location>\n</error-page>\n"
                    + "</web-app>\n");
        }

        Tomcat tomcat = getTomcatInstance();
        String contextPath = "/MyApp";
        tomcat.addWebapp(null, contextPath, appDir.getAbsolutePath());
        tomcat.start();

        TestCustomErrorClient client =
                new TestCustomErrorClient(tomcat.getConnector().getLocalPort());

        client.reset();
        client.setRequest(new String[] {
                "GET /MyApp/missing HTTP/1.0" + CRLF + CRLF });
        client.connect();
        client.processRequest();
        assertTrue(client.isResponse404());
    }

    /*
     * Verifies that the same Content-Length is returned for both GET and HEAD
     * operations when a static resource served by the DefaultServlet is
     * included.
     */
    @Test
    public void testBug57601() throws Exception {
        Tomcat tomcat = getTomcatInstanceTestWebapp(false, true);

        Map<String,List<String>> resHeaders= new HashMap<>();
        String path = "http://localhost:" + getPort() + "/test/bug5nnnn/bug57601.jsp";
        ByteChunk out = new ByteChunk();

        int rc = getUrl(path, out, resHeaders);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        String length = resHeaders.get("Content-Length").get(0);
        Assert.assertEquals(Long.parseLong(length), out.getLength());
        out.recycle();

        rc = headUrl(path, out, resHeaders);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertEquals(0, out.getLength());
        Assert.assertEquals(length, resHeaders.get("Content-Length").get(0));

        tomcat.stop();
    }

    public static int getUrl(String path, ByteChunk out,
            Map<String, List<String>> resHead) throws IOException {
        out.recycle();
        return TomcatBaseTest.getUrl(path, out, resHead);
    }

    private static class TestCustomErrorClient extends SimpleHttpClient {

        public TestCustomErrorClient(int port) {
            setPort(port);
        }

        @Override
        public boolean isResponseBodyOK() {
            return true;
        }
    }

    private static class TestCompressedClient extends SimpleHttpClient {

        public TestCompressedClient(int port) {
            setPort(port);
        }

        @Override
        public boolean isResponseBodyOK() {
            return true;
        }
    }
}

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
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.SAXParserFactory;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.servlets.WebdavServlet.PropertyStore;
import org.apache.catalina.servlets.WebdavServlet.ProppatchOperation;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.util.XMLWriter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.websocket.server.WsContextListener;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

public class TestWebdavServlet extends TomcatBaseTest {

    /*
     * Test attempting to access special paths (WEB-INF/META-INF) using WebdavServlet
     */
    @Test
    public void testGetSpecials() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        String contextPath = "/examples";

        File appDir = new File(getBuildDirectory(), "webapps" + contextPath);
        // app dir is relative to server home
        Context ctx = tomcat.addWebapp(null, "/examples", appDir.getAbsolutePath());

        Tomcat.addServlet(ctx, "webdav", new WebdavServlet());
        ctx.addServletMappingDecoded("/*", "webdav");

        tomcat.start();

        final ByteChunk res = new ByteChunk();

        int rc =getUrl("http://localhost:" + getPort() + contextPath +
                "/WEB-INF/web.xml", res, null);
        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);

        rc =getUrl("http://localhost:" + getPort() + contextPath +
                "/WEB-INF/doesntexistanywhere", res, null);
        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);

        rc =getUrl("http://localhost:" + getPort() + contextPath +
                "/WEB-INF/", res, null);
        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);

        rc =getUrl("http://localhost:" + getPort() + contextPath +
                "/META-INF/MANIFEST.MF", res, null);
        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);

        rc =getUrl("http://localhost:" + getPort() + contextPath +
                "/META-INF/doesntexistanywhere", res, null);
        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);

    }

    /*
     * Test https://bz.apache.org/bugzilla/show_bug.cgi?id=50026
     * Verify protection of special paths with re-mount of web app resource root.
     */
    @Test
    public void testGetWithSubpathmount() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        String contextPath = "/examples";

        File appDir = new File(getBuildDirectory(), "webapps" + contextPath);
        // app dir is relative to server home
        Context ctx = tomcat.addWebapp(null, "/examples", appDir.getAbsolutePath());

        Tomcat.addServlet(ctx, "webdav", new WebdavServlet());
        ctx.addServletMappingDecoded("/webdav/*", "webdav");
        ctx.addApplicationListener(WsContextListener.class.getName());

        tomcat.start();

        final ByteChunk res = new ByteChunk();

        // Make sure WebdavServlet isn't exposing special directories
        // by remounting the webapp under a sub-path

        int rc =getUrl("http://localhost:" + getPort() + contextPath +
                "/webdav/WEB-INF/web.xml", res, null);

        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);
        rc =getUrl("http://localhost:" + getPort() + contextPath +
                "/webdav/WEB-INF/doesntexistanywhere", res, null);
        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);

        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);
        rc =getUrl("http://localhost:" + getPort() + contextPath +
                "/webdav/WEB-INF/", res, null);
        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);

        rc =getUrl("http://localhost:" + getPort() + contextPath +
                "/webdav/META-INF/MANIFEST.MF", res, null);
        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);

        rc =getUrl("http://localhost:" + getPort() + contextPath +
                "/webdav/META-INF/doesntexistanywhere", res, null);
        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);

        // Make sure WebdavServlet is serving resources
        // relative to the map/mount point
        final ByteChunk rootResource = new ByteChunk();
        rc =getUrl("http://localhost:" + getPort() + contextPath +
                "/index.html", rootResource, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        final ByteChunk subpathResource = new ByteChunk();
        rc =getUrl("http://localhost:" + getPort() + contextPath +
                "/webdav/index.html", subpathResource, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        Assert.assertEquals(rootResource.toString(), subpathResource.toString());

        rc =getUrl("http://localhost:" + getPort() + contextPath +
                "/webdav/static/index.html", res, null);
        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);

    }

    public static int getUrl(String path, ByteChunk out,
            Map<String, List<String>> resHead) throws IOException {
        out.recycle();
        return TomcatBaseTest.getUrl(path, out, resHead);
    }

    /*
     * Bug 66609
     */
    @Test
    public void testDirectoryListing() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        Context ctxt = tomcat.addContext("", appDir.getAbsolutePath());

        Wrapper defaultServlet = Tomcat.addServlet(ctxt, "webdav", new WebdavServlet());
        defaultServlet.addInitParameter("listings", "true");

        ctxt.addServletMappingDecoded("/*", "webdav");
        ctxt.addMimeMapping("html", "text/html");

        tomcat.start();

        Client client = new Client();
        client.setPort(getPort());
        client.setRequest(new String[] { "PROPFIND /bug66609/ HTTP/1.1" + SimpleHttpClient.CRLF +
                                         "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                                         SimpleHttpClient.CRLF});
        client.connect();
        client.sendRequest();

        client.setUseContentLength(true);
        client.readResponse(true);

        // This will throw an exception if the XML is not valid
        SAXParserFactory.newInstance().newSAXParser().getXMLReader().parse(new InputSource(new StringReader(client.getResponseBody())));
    }

    private static final String CONTENT = "FOOBAR";

    private static final String LOCK_BODY =
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
            "<D:lockinfo xmlns:D='DAV:'>\n" +
            "  <D:lockscope><D:exclusive/></D:lockscope>\n" +
            "  <D:locktype><D:write/></D:locktype>\n" +
            "  <D:owner>someone</D:owner>\n" +
            "</D:lockinfo>";

    private static final String LOCK_SHARED_BODY =
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
            "<D:lockinfo xmlns:D='DAV:'>\n" +
            "  <D:lockscope><D:shared/></D:lockscope>\n" +
            "  <D:locktype><D:write/></D:locktype>\n" +
            "  <D:owner>someone</D:owner>\n" +
            "</D:lockinfo>";

    private static final String PROPFIND_PROP =
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?> \n" +
            "<D:propfind xmlns:D=\"DAV:\">\n" +
            "  <D:prop>\n" +
            "    <D:getcontenttype/>\n" +
            "    <T:customprop xmlns:T=\"http://tomcat.apache.org/testsuite\"/>\n" +
            "    <T:othercustomprop xmlns:T=\"http://tomcat.apache.org/testsuite\"/>\n" +
            "    <D:getcontentlength/>\n" +
            "  </D:prop>\n" +
            "</D:propfind>";

    private static final String PROPFIND_PROPNAME =
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
            "<D:propfind xmlns:D=\"DAV:\">\n" +
            "  <D:propname/>\n" +
            "</D:propfind>";

    private static final String PROPPATCH_PROPNAME =
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
            "<D:propertyupdate xmlns:D=\"DAV:\" xmlns:T=\"http://tomcat.apache.org/testsuite\">\n" +
            "  <D:set>\n" +
            "    <D:prop>\n" +
            "      <T:customprop>\n" +
            "        <T:myvalue/>\n" +
            "      </T:customprop>\n" +
            "    </D:prop>\n" +
            "  </D:set>\n" +
            "  <D:remove>\n" +
            "    <D:prop><T:othercustomprop/></D:prop>\n" +
            "  </D:remove>\n" +
            "</D:propertyupdate>";

    @Test
    public void testBasicProperties() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Create a temp webapp that can be safely written to
        File tempWebapp = new File(getTemporaryDirectory(), "webdav-properties");
        Assert.assertTrue(tempWebapp.mkdirs());
        Context ctxt = tomcat.addContext("", tempWebapp.getAbsolutePath());
        Wrapper webdavServlet = Tomcat.addServlet(ctxt, "webdav", new WebdavServlet());
        webdavServlet.addInitParameter("listings", "true");
        webdavServlet.addInitParameter("secret", "foo");
        webdavServlet.addInitParameter("readonly", "false");
        ctxt.addServletMappingDecoded("/*", "webdav");
        ctxt.addMimeMapping("txt", "text/plain");
        tomcat.start();

        Client client = new Client();
        client.setPort(getPort());

        // Create a test file
        client.setRequest(new String[] { "PUT /file1.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: 6" + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + CONTENT });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // Add lock to check for lock discovery
        client.setRequest(new String[] { "LOCK /file2.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: " + LOCK_BODY.length() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + LOCK_BODY });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("urn:uuid:"));

        client.setRequest(new String[] { "PROPFIND / HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("urn:uuid:"));

        client.setRequest(new String[] { "PROPPATCH /file1.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: " + PROPPATCH_PROPNAME.length() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + PROPPATCH_PROPNAME });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("<T:othercustomprop"));
        validateXml(client.getResponseBody());

        client.setRequest(new String[] { "PROPFIND /file1.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: " + PROPFIND_PROPNAME.length() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + PROPFIND_PROPNAME });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("<D:getcontenttype/>"));

        client.setRequest(new String[] { "PROPFIND /file1.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: " + PROPFIND_PROP.length() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + PROPFIND_PROP });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("<D:getcontenttype>"));
        Assert.assertFalse(client.getResponseBody().contains("<D:getlastmodified>"));
        Assert.assertTrue(client.getResponseBody().contains("<myvalue xmlns=\"http://tomcat.apache.org/testsuite\">"));

        client.setRequest(new String[] { "MOVE /file1.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Destination: /file3.txt" + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        client.setRequest(new String[] { "PROPFIND /file3.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: " + PROPFIND_PROP.length() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + PROPFIND_PROP });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("<D:getcontenttype>"));
        Assert.assertFalse(client.getResponseBody().contains("<D:getlastmodified>"));
        Assert.assertTrue(client.getResponseBody().contains("<myvalue xmlns=\"http://tomcat.apache.org/testsuite\">"));
        validateXml(client.getResponseBody());

    }

    @Test
    public void testBasicOperations() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Create a temp webapp that can be safely written to
        File tempWebapp = new File(getTemporaryDirectory(), "webdav-webapp");
        Assert.assertTrue(tempWebapp.mkdirs());
        Context ctxt = tomcat.addContext("", tempWebapp.getAbsolutePath());
        Wrapper webdavServlet = Tomcat.addServlet(ctxt, "webdav", new WebdavServlet());
        webdavServlet.addInitParameter("listings", "true");
        webdavServlet.addInitParameter("secret", "foo");
        webdavServlet.addInitParameter("readonly", "false");
        webdavServlet.addInitParameter("useStrongETags", "true");
        ctxt.addServletMappingDecoded("/*", "webdav");
        tomcat.start();

        ctxt.getResources().setCacheMaxSize(10);
        ctxt.getResources().setCacheObjectMaxSize(1);

        Client client = new Client();
        client.setPort(getPort());

        // Create a few files
        client.setRequest(new String[] { "PUT /file1.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: 6" + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + CONTENT });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        client.setRequest(new String[] { "PUT /file2.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: 12" + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + CONTENT + CONTENT });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append(CONTENT);
        }
        client.setRequest(new String[] { "PUT /file12.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: " + String.valueOf(sb.length()) + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + sb.toString() });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        client.setRequest(new String[] { "MKCOL /myfolder HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        client.setRequest(new String[] { "PUT /myfolder/file3.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: 6" + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + CONTENT });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // Verify that listing the file works
        client.setRequest(new String[] { "PROPFIND / HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("<D:getcontentlength>12<"));

        // Lock /myfolder
        client.setRequest(new String[] { "LOCK /myfolder HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: " + LOCK_BODY.length() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + LOCK_BODY });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("urn:uuid:"));
        String lockToken = null;
        for (String header : client.getResponseHeaders()) {
            if (header.startsWith("Lock-Token: ")) {
                lockToken = header.substring("Lock-Token: ".length());
            }
        }
        Assert.assertNotNull(lockToken);

        // Try to add /myfolder/file4.txt to myfolder without lock token
        client.setRequest(new String[] { "PUT /myfolder/file4.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: 6" + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + CONTENT });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_LOCKED, client.getStatusCode());

        // Same but provide the lock token
        client.setRequest(new String[] { "PUT /myfolder/file4.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: 6" + SimpleHttpClient.CRLF +
                "If: </myfolder/> (" + lockToken + ")" + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + CONTENT });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // Add lock for /myfolder/file5.txt
        client.setRequest(new String[] { "LOCK /myfolder/file5.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: " + LOCK_BODY.length() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + LOCK_BODY });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_LOCKED, client.getStatusCode());

        client.setRequest(new String[] { "UNLOCK /myfolder/file5.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Lock-Token: <my:locktoken>" + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CONFLICT, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("<D:href>/myfolder</D:href>"));

        // Unlock /myfolder
        client.setRequest(new String[] { "UNLOCK /myfolder/file5.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Lock-Token: " + lockToken + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_NO_CONTENT, client.getStatusCode());

        client.setRequest(new String[] { "LOCK /myfolder/file5.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: " + LOCK_BODY.length() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + LOCK_BODY });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("urn:uuid:"));
        String lockTokenFile = null;
        for (String header : client.getResponseHeaders()) {
            if (header.startsWith("Lock-Token: ")) {
                lockTokenFile = header.substring("Lock-Token: ".length());
            }
        }
        Assert.assertNotNull(lockTokenFile);

        client.setRequest(new String[] { "LOCK /myfolder HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: " + LOCK_BODY.length() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + LOCK_BODY });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("/myfolder/file5.txt"));
        Assert.assertTrue(client.getResponseBody().contains("HTTP/1.1 423"));

        client.setRequest(new String[] { "PUT /myfolder/file5.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: 6" + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + CONTENT });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_LOCKED, client.getStatusCode());

        // Same but with lock token and lock null
        client.setRequest(new String[] { "PUT /myfolder/file5.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: 6" + SimpleHttpClient.CRLF +
                "If: (" + lockTokenFile + ")" + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + CONTENT });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_NO_CONTENT, client.getStatusCode());

        // Verify that this also removes the lock by doing another PUT without the token
        client.setRequest(new String[] { "DELETE /myfolder/file5.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "If: (" + lockTokenFile + ")" + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_NO_CONTENT, client.getStatusCode());

        client.setRequest(new String[] { "PUT /myfolder/file5.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: 6" + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + CONTENT });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // Lock /myfolder again
        client.setRequest(new String[] { "LOCK /myfolder HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Timeout: Second-20" + SimpleHttpClient.CRLF +
                "Content-Length: " + LOCK_BODY.length() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + LOCK_BODY });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("urn:uuid:"));
        lockToken = null;
        for (String header : client.getResponseHeaders()) {
            if (header.startsWith("Lock-Token: ")) {
                lockToken = header.substring("Lock-Token: ".length());
            }
        }
        Assert.assertNotNull(lockToken);

        // Copy /myfolder/file5.txt to /myfolder/file6.txt without lock (should not work)
        client.setRequest(new String[] { "COPY /myfolder/file5.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Destination: http://localhost:" + getPort() + "/myfolder/file6.txt"  + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_LOCKED, client.getStatusCode());

        client.setRequest(new String[] { "COPY /myfolder HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Destination: /myfolder2"  + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_CREATED, client.getStatusCode());

        // Delete /myfolder/file4.txt
        client.setRequest(new String[] { "DELETE /myfolder/file4.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "If: (" + lockToken + ")" + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_NO_CONTENT, client.getStatusCode());

        // Copy /myfolder/file5.txt to /file7.txt without lock (should work)
        client.setRequest(new String[] { "COPY /myfolder/file5.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Destination: http://localhost:" + getPort() + "/file7.txt"  + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        StringBuilder sb2 = new StringBuilder();
        for (int i = 0; i < 3000; i++) {
            sb2.append(CONTENT);
        }
        client.setRequest(new String[] { "PUT /file6.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: " + String.valueOf(sb2.length()) +  SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + sb2.toString() });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // Verify that everything created is there
        client.setRequest(new String[] { "PROPFIND / HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        Assert.assertFalse(client.getResponseBody().contains("/myfolder/file4.txt"));
        Assert.assertTrue(client.getResponseBody().contains("/file7.txt"));
        Assert.assertTrue(client.getResponseBody().contains("Second-"));
        Assert.assertTrue(client.getResponseBody().contains("d1dc021f456864e84f9a37b7a6f51c51301128a0"));
        Assert.assertTrue(client.getResponseBody().contains("f3390fe2e5546dac3d1968970df1a222a3a39c00"));
        String timeoutValue = client.getResponseBody().substring(client.getResponseBody().indexOf("Second-"));
        timeoutValue = timeoutValue.substring("Second-".length(), timeoutValue.indexOf('<'));
        Assert.assertTrue(Integer.valueOf(timeoutValue).intValue() <= 20);

        // Unlock /myfolder again
        client.setRequest(new String[] { "UNLOCK /myfolder/ HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Lock-Token: " + lockToken + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_NO_CONTENT, client.getStatusCode());

        // Delete /myfolder
        client.setRequest(new String[] { "DELETE /myfolder HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_NO_CONTENT, client.getStatusCode());

        client.setRequest(new String[] { "DELETE /myfolder2 HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_NO_CONTENT, client.getStatusCode());

        client.setRequest(new String[] { "PROPFIND / HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        Assert.assertFalse(client.getResponseBody().contains("/myfolder"));
        validateXml(client.getResponseBody());
    }


    @Test
    public void testCopyOutsideSubpath() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Create a temp webapp that can be safely written to
        File tempWebapp = new File(getTemporaryDirectory(), "webdav-subpath");
        File subPath = new File(tempWebapp, "aaa");
        Assert.assertTrue(subPath.mkdirs());

        Context ctxt = tomcat.addContext("", tempWebapp.getAbsolutePath());
        Wrapper webdavServlet = Tomcat.addServlet(ctxt, "webdav", new WebdavServlet());
        webdavServlet.addInitParameter("listings", "true");
        webdavServlet.addInitParameter("readonly", "false");
        webdavServlet.addInitParameter("serveSubpathOnly", "true");
        ctxt.addServletMappingDecoded("/aaa/*", "webdav");
        tomcat.start();

        ctxt.getResources().setCacheMaxSize(10);
        ctxt.getResources().setCacheObjectMaxSize(1);

        Client client = new Client();
        client.setPort(getPort());

        // Create a file
        client.setRequest(new String[] { "PUT /aaa/file1.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: 6" + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + CONTENT });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // Copy file1.txt to file2.txt
        client.setRequest(new String[] { "COPY /aaa/file1.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Destination: http://localhost:" + getPort() + "/aaa/file2.txt"  + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // Move file2.txt to file3.txt
        client.setRequest(new String[] { "MOVE /aaa/file2.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Destination: http://localhost:" + getPort() + "/aaa/file3.txt"  + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // Copy file1.txt outside sub-path
        client.setRequest(new String[] { "COPY /aaa/file1.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Destination: http://localhost:" + getPort() + "/file1.txt"  + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_FORBIDDEN, client.getStatusCode());

        // Move file1.txt outside sub-path
        client.setRequest(new String[] { "MOVE /aaa/file1.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Destination: http://localhost:" + getPort() + "/file1.txt"  + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_FORBIDDEN, client.getStatusCode());
}


    @Test
    public void testSharedLocks() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Create a temp webapp that can be safely written to
        File tempWebapp = new File(getTemporaryDirectory(), "webdav-lock");
        Assert.assertTrue(tempWebapp.mkdirs());
        Context ctxt = tomcat.addContext("", tempWebapp.getAbsolutePath());
        Wrapper webdavServlet = Tomcat.addServlet(ctxt, "webdav", new WebdavServlet());
        webdavServlet.addInitParameter("listings", "true");
        webdavServlet.addInitParameter("secret", "foo");
        webdavServlet.addInitParameter("readonly", "false");
        ctxt.addServletMappingDecoded("/*", "webdav");
        tomcat.start();

        Client client = new Client();
        client.setPort(getPort());

        // Create a few folders and files
        client.setRequest(new String[] { "MKCOL /myfolder HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        client.setRequest(new String[] { "MKCOL /myfolder/myfolder2/ HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        client.setRequest(new String[] { "MKCOL /myfolder/myfolder3 HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        client.setRequest(new String[] { "MKCOL /myfolder/myfolder2/myfolder4 HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        client.setRequest(new String[] { "MKCOL /myfolder/myfolder2/myfolder4/myfolder5 HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        client.setRequest(new String[] { "PUT /file1.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: 6" + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + CONTENT });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        client.setRequest(new String[] { "PUT /myfolder/myfolder3/file2.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: 6" + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + CONTENT });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        client.setRequest(new String[] { "PUT /myfolder/myfolder2/myfolder4/file3.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: 6" + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + CONTENT });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // Lock /myfolder
        client.setRequest(new String[] { "LOCK /myfolder HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: " + LOCK_SHARED_BODY.length() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + LOCK_SHARED_BODY });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("urn:uuid:"));
        String lockToken = null;
        for (String header : client.getResponseHeaders()) {
            if (header.startsWith("Lock-Token: ")) {
                lockToken = header.substring("Lock-Token: ".length());
            }
        }
        Assert.assertNotNull(lockToken);

        client.setRequest(new String[] { "LOCK /myfolder/myfolder2/myfolder4/myfolder5 HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "If: (" + lockToken + ")" + SimpleHttpClient.CRLF +
                "Content-Length: " + LOCK_BODY.length() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + LOCK_BODY });
        client.connect();
        client.processRequest(true);
        // This should conflict, submitting a token does not help
        Assert.assertEquals(WebdavStatus.SC_LOCKED, client.getStatusCode());

        // Lock refresh /myfolder
        client.setRequest(new String[] { "LOCK /myfolder HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Timeout: Infinite" + SimpleHttpClient.CRLF +
                "If: (" + lockToken + ")" + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());

        client.setRequest(new String[] { "LOCK /myfolder/myfolder2/myfolder4 HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: " + LOCK_SHARED_BODY.length() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + LOCK_SHARED_BODY });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("urn:uuid:"));
        String lockToken2 = null;
        for (String header : client.getResponseHeaders()) {
            if (header.startsWith("Lock-Token: ")) {
                lockToken2 = header.substring("Lock-Token: ".length());
            }
        }
        Assert.assertNotNull(lockToken2);

        // Take a second different lock on the same collection
        client.setRequest(new String[] { "LOCK /myfolder/myfolder2/myfolder4 HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: " + LOCK_SHARED_BODY.length() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + LOCK_SHARED_BODY });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("urn:uuid:"));
        String lockToken3 = null;
        for (String header : client.getResponseHeaders()) {
            if (header.startsWith("Lock-Token: ")) {
                lockToken3 = header.substring("Lock-Token: ".length());
            }
        }
        Assert.assertNotNull(lockToken3);
        validateXml(client.getResponseBody());

        client.setRequest(new String[] { "PROPFIND / HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("urn:uuid:"));
        Assert.assertTrue(client.getResponseBody().contains("Second-"));
        String timeoutValue = client.getResponseBody().substring(client.getResponseBody().indexOf("Second-"));
        timeoutValue = timeoutValue.substring("Second-".length(), timeoutValue.indexOf('<'));
        Assert.assertTrue(Integer.valueOf(timeoutValue).intValue() > 100000);
        validateXml(client.getResponseBody());

        client.setRequest(new String[] { "PUT /myfolder/myfolder2/myfolder4/myfolder5/file4.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: 6" + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + CONTENT });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_LOCKED, client.getStatusCode());

        client.setRequest(new String[] { "PUT /myfolder/myfolder2/myfolder4/myfolder5/file4.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "If: (" + lockToken + ")" + SimpleHttpClient.CRLF +
                "Content-Length: 6" + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + CONTENT });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_PRECONDITION_FAILED, client.getStatusCode());

        client.setRequest(new String[] { "PUT /myfolder/myfolder2/myfolder4/myfolder5/file4.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "If: </myfolder/myfolder2/myfolder4/myfolder5> (" + lockToken + ")" + SimpleHttpClient.CRLF +
                "Content-Length: 6" + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + CONTENT });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        client.setRequest(new String[] { "UNLOCK /myfolder/myfolder3 HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Lock-Token: " + lockToken2 + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CONFLICT, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("<D:lock-token-matches-request-uri/>"));

        client.setRequest(new String[] { "UNLOCK /myfolder/myfolder2/myfolder4/myfolder5 HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Lock-Token: " + lockToken2 + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_NO_CONTENT, client.getStatusCode());

        client.setRequest(new String[] { "UNLOCK /myfolder/myfolder2/myfolder4/myfolder5/file4.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Lock-Token: " + lockToken3 + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_NO_CONTENT, client.getStatusCode());

        client.setRequest(new String[] { "PUT /myfolder/myfolder2/myfolder4/myfolder5/file4.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: 6" + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + CONTENT });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_LOCKED, client.getStatusCode());

        client.setRequest(new String[] { "UNLOCK /myfolder/myfolder2/myfolder4/myfolder5/file4.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Lock-Token: " + lockToken + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_NO_CONTENT, client.getStatusCode());

        client.setRequest(new String[] { "PUT /myfolder/myfolder2/myfolder4/myfolder5/file4.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: 12" + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + CONTENT  + CONTENT});
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_NO_CONTENT, client.getStatusCode());

        client.setRequest(new String[] { "PROPFIND / HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        // Verify all the shared locks are cleared
        Assert.assertFalse(client.getResponseBody().contains("urn:uuid:"));
        validateXml(client.getResponseBody());

    }

    @Test
    public void testIfHeader() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Create a temp webapp that can be safely written to
        File tempWebapp = new File(getTemporaryDirectory(), "webdav-if");
        File folder = new File(tempWebapp, "/myfolder/myfolder2/myfolder4/myfolder5");
        Assert.assertTrue(folder.mkdirs());
        File file = new File(folder, "myfile.txt");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(CONTENT.getBytes());
        }
        folder = new File(tempWebapp, "/myfolder/myfolder3/myfolder6");
        Assert.assertTrue(folder.mkdirs());
        folder = new File(tempWebapp, "/myfolder/myfolder7/myfolder8/myfolder9");
        Assert.assertTrue(folder.mkdirs());
        Context ctxt = tomcat.addContext("", tempWebapp.getAbsolutePath());
        Wrapper webdavServlet = Tomcat.addServlet(ctxt, "webdav", new WebdavServlet());
        webdavServlet.addInitParameter("listings", "true");
        webdavServlet.addInitParameter("secret", "foo");
        webdavServlet.addInitParameter("readonly", "false");
        ctxt.addServletMappingDecoded("/*", "webdav");
        tomcat.start();

        Client client = new Client();
        client.setPort(getPort());

        client.setRequest(new String[] { "LOCK /myfolder/myfolder3 HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: " + LOCK_BODY.length() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + LOCK_BODY });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("urn:uuid:"));
        String lockToken = null;
        for (String header : client.getResponseHeaders()) {
            if (header.startsWith("Lock-Token: ")) {
                lockToken = header.substring("Lock-Token: ".length());
            }
        }
        Assert.assertNotNull(lockToken);

        client.setRequest(new String[] { "LOCK /myfolder/myfolder7 HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: " + LOCK_SHARED_BODY.length() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + LOCK_SHARED_BODY });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("urn:uuid:"));
        String lockToken2 = null;
        for (String header : client.getResponseHeaders()) {
            if (header.startsWith("Lock-Token: ")) {
                lockToken2 = header.substring("Lock-Token: ".length());
            }
        }
        Assert.assertNotNull(lockToken2);

        client.setRequest(new String[] { "LOCK /myfolder/myfolder7/myfolder8 HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: " + LOCK_SHARED_BODY.length() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + LOCK_SHARED_BODY });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("urn:uuid:"));
        String lockToken3 = null;
        for (String header : client.getResponseHeaders()) {
            if (header.startsWith("Lock-Token: ")) {
                lockToken3 = header.substring("Lock-Token: ".length());
            }
        }
        Assert.assertNotNull(lockToken3);

        client.setRequest(new String[] { "PUT /myfolder/myfolder2/myfolder4/myfolder5/file4.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "If: </myfolder/myfolder3/myfolder6> (<urn:uuid:5e1e2275b1cd9c17845e7e08>)" + // Obvious wrong token
                " </myfolder/myfolder7/myfolder8/myfolder9> (" + lockToken + " " + lockToken2 + " " + lockToken3 + ")" + // lockToken is not there
                " </myfolder/myfolder2/myfolder4> (<urn:uuid:7329872398754923752> [W/\"4-1729375899470\"])" + // Not locked
                " </myfolder/myfolder7/myfolder8> (" + lockToken + ")" + SimpleHttpClient.CRLF + // lockToken is not there
                "Content-Length: 6" + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + CONTENT });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_PRECONDITION_FAILED, client.getStatusCode());

        client.setRequest(new String[] { "PUT /myfolder/myfolder2/myfolder4/myfolder5/file4.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "If: </myfolder/myfolder3/myfolder6> (<urn:uuid:5e1e2275b1cd9c17845e7e08>)" + // Obvious wrong token
                " </myfolder/myfolder2/myfolder4> (<urn:uuid:7329872398754923752> [W/\"4-1729375899470\"])" + // Not locked
                " </myfolder/myfolder7/myfolder8> (" + lockToken + ")" + // lockToken is not there
                " </myfolder/myfolder7/myfolder8/myfolder9> (" + lockToken2 + " " + lockToken3 + ")" + SimpleHttpClient.CRLF + // Correct
                "Content-Length: 6" + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + CONTENT });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_CREATED, client.getStatusCode());

    }

    @Test
    public void testPropertyStore() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Create a temp webapp that can be safely written to
        File tempWebapp = new File(getTemporaryDirectory(), "webdav-store");
        Assert.assertTrue(tempWebapp.mkdirs());
        Context ctxt = tomcat.addContext("", tempWebapp.getAbsolutePath());
        Wrapper webdavServlet = Tomcat.addServlet(ctxt, "webdav", new WebdavServlet());
        webdavServlet.addInitParameter("listings", "true");
        webdavServlet.addInitParameter("secret", "foo");
        webdavServlet.addInitParameter("readonly", "false");
        webdavServlet.addInitParameter("propertyStore", "org.apache.catalina.servlets.TestWebdavServlet$CustomPropertyStore");
        webdavServlet.addInitParameter("store.propertyName", "mytestproperty");
        webdavServlet.addInitParameter("store.propertyValue", "testvalue");
        ctxt.addServletMappingDecoded("/*", "webdav");
        ctxt.addMimeMapping("txt", "text/plain");
        tomcat.start();

        Client client = new Client();
        client.setPort(getPort());

        client.setRequest(new String[] { "PROPFIND / HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains(">testvalue</mytestproperty>"));
        validateXml(client.getResponseBody());

    }

    public static class CustomPropertyStore implements PropertyStore {

        private String propertyName = null;
        private String propertyValue = null;

        @Override
        public void init() {
        }

        @Override
        public void destroy() {
        }

        @Override
        public void periodicEvent() {
        }

        @Override
        public void copy(String source, String destination) {
        }

        @Override
        public void delete(String resource) {
        }

        @Override
        public boolean propfind(String resource, Node property, boolean nameOnly, XMLWriter generatedXML) {
            generatedXML.writeElement(null, "https://tomcat.apache.org/testsuite", propertyName, XMLWriter.OPENING);
            generatedXML.writeText(propertyValue);
            generatedXML.writeElement(null, propertyName, XMLWriter.CLOSING);
            return true;
        }

        @Override
        public void proppatch(String resource, ArrayList<ProppatchOperation> operations) {
        }

        /**
         * @return the propertyName
         */
        public String getPropertyName() {
            return this.propertyName;
        }

        /**
         * @param propertyName the propertyName to set
         */
        public void setPropertyName(String propertyName) {
            this.propertyName = propertyName;
        }

        /**
         * @return the propertyValue
         */
        public String getPropertyValue() {
            return this.propertyValue;
        }

        /**
         * @param propertyValue the propertyValue to set
         */
        public void setPropertyValue(String propertyValue) {
            this.propertyValue = propertyValue;
        }

    }

    private void validateXml(String xmlContent) throws Exception {
        SAXParserFactory.newInstance().newSAXParser().getXMLReader().parse(new InputSource(new StringReader(xmlContent)));
    }

    private static final class Client extends SimpleHttpClient {

        @Override
        public boolean isResponseBodyOK() {
            return true;
        }
    }

}

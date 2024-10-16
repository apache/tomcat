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
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.SAXParserFactory;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.websocket.server.WsContextListener;
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

    private static final String PROPFIND_PROP =
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?> \n" +
            "<D:propfind xmlns:D=\"DAV:\">\n" +
            "  <D:prop>\n" +
            "    <D:getcontenttype/>\n" +
            "    <D:getcontentlength/>\n" +
            "  </D:prop>\n" +
            "</D:propfind>";

    private static final String PROPFIND_PROPNAME =
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
            "<D:propfind xmlns:D=\"DAV:\">\n" +
            "  <D:propname/>\n" +
            "</D:propfind>";

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
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("opaquelocktoken:"));

        client.setRequest(new String[] { "PROPFIND / HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("opaquelocktoken:"));

        client.setRequest(new String[] { "PROPFIND / HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: " + PROPFIND_PROPNAME.length() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + PROPFIND_PROPNAME });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("<D:getcontenttype/>"));

        client.setRequest(new String[] { "PROPFIND / HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Content-Length: " + PROPFIND_PROP.length() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + PROPFIND_PROP });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("<D:getcontenttype>"));
        Assert.assertFalse(client.getResponseBody().contains("<D:getlastmodified>"));

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
        ctxt.addServletMappingDecoded("/*", "webdav");
        tomcat.start();

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
        Assert.assertTrue(client.getResponseBody().contains("opaquelocktoken:"));
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
                "If: " + lockToken + SimpleHttpClient.CRLF +
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

        // Unlock /myfolder
        client.setRequest(new String[] { "UNLOCK /myfolder HTTP/1.1" + SimpleHttpClient.CRLF +
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
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("opaquelocktoken:"));
        String lockTokenFile = null;
        for (String header : client.getResponseHeaders()) {
            if (header.startsWith("Lock-Token: ")) {
                lockTokenFile = header.substring("Lock-Token: ".length());
            }
        }
        Assert.assertNotNull(lockTokenFile);

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
                "If: " + lockTokenFile + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + CONTENT });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // Verify that this also removes the lock by doing another PUT without the token
        client.setRequest(new String[] { "DELETE /myfolder/file5.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "If: " + lockTokenFile + SimpleHttpClient.CRLF +
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
                "Content-Length: " + LOCK_BODY.length() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + LOCK_BODY });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("opaquelocktoken:"));
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
                "Destination: /myfolder/file6.txt"  + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_LOCKED, client.getStatusCode());

        // Delete /myfolder/file4.txt
        client.setRequest(new String[] { "DELETE /myfolder/file4.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "If: " + lockToken + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_NO_CONTENT, client.getStatusCode());

        // Copy /myfolder/file5.txt to /file7.txt without lock (should work)
        client.setRequest(new String[] { "COPY /myfolder/file5.txt HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Destination: /file7.txt"  + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
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

        // Unlock /myfolder again
        client.setRequest(new String[] { "UNLOCK /myfolder HTTP/1.1" + SimpleHttpClient.CRLF +
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

        client.setRequest(new String[] { "PROPFIND / HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "Connection: Close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        Assert.assertFalse(client.getResponseBody().contains("/myfolder"));

    }

    private static final class Client extends SimpleHttpClient {

        @Override
        public boolean isResponseBodyOK() {
            return true;
        }
    }
}

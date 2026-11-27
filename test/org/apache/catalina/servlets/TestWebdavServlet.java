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
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.parsers.SAXParserFactory;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.authenticator.BasicAuthenticator;
import org.apache.catalina.servlets.WebdavServlet.PropertyStore;
import org.apache.catalina.servlets.WebdavServlet.ProppatchOperation;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.util.XMLWriter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.apache.tomcat.websocket.server.WsContextListener;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

public class TestWebdavServlet extends TomcatBaseTest {

    @Test
    public void testGetSpecial_allowSpecialPaths_rootdavcontext() throws Exception {
        testGetSpecials(true, false);
    }

    @Test
    public void testGetSpecial_allowSpecialPaths_nonrootdavcontext() throws Exception {
        testGetSpecials(true, true);
    }

    @Test
    public void testGetSpecial_disallowSpecialPaths_rootdavcontext() throws Exception {
        testGetSpecials(false, false);
    }

    @Test
    public void testGetSpecial_disallowSpecialPaths_nonrootdavcontext() throws Exception {
        testGetSpecials(false, true);
    }

    /* Test attempting to access special paths (WEB-INF/META-INF) using WebdavServlet */
    private void testGetSpecials(boolean allowSpecialPaths, boolean useSubpathWebdav) throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Create a temp webapp that can be safely written to
        File tempWebapp = new File(getTemporaryDirectory(), "webdav-specialpath"+UUID.randomUUID());
        tempWebapp.deleteOnExit();
        Assert.assertTrue("Failed to mkdirs on "+tempWebapp.getCanonicalPath(),tempWebapp.mkdirs());
        Assert.assertTrue(new File(tempWebapp,"WEB-INF").mkdir());
        Assert.assertTrue(new File(tempWebapp,"META-INF").mkdir());
        try (FileWriter fw = new FileWriter(new File(tempWebapp, "WEB-INF-desc.xml"))) {
            fw.write("<CheckSpecial>This is not a special file</CheckSpecial>");
        }
        try (FileWriter fw = new FileWriter(new File(tempWebapp, "WEB-INF/web.xml"))) {
            fw.write("<web>...</web>");
        }
        try (FileWriter fw = new FileWriter(new File(tempWebapp, "META-INF/context.xml"))) {
            fw.write("<context>...</context>");
        }

        Context ctx = tomcat.addContext("", tempWebapp.getAbsolutePath());
        Wrapper webdavServlet = Tomcat.addServlet(ctx, "webdav", new WebdavServlet());

        webdavServlet.addInitParameter("listings", "true");
        webdavServlet.addInitParameter("allowSpecialPaths", allowSpecialPaths ? "true" : "false");

        String contextPath="";
        if (useSubpathWebdav) {
            ctx.addServletMapping("/webdav/*", "webdav");
            contextPath = "/webdav";
        } else {
            ctx.addServletMapping("/*", "webdav");
        }

        tomcat.start();

        final ByteChunk res = new ByteChunk();

        int rc = 0;

        // Notice: Special paths /WEB-INF and /META-INF are protected by StandardContextValve.
        // allowSpecialPaths works only when webdav re-mount to a non-root path.
        rc = getUrl("http://localhost:" + getPort() + contextPath + "/WEB-INF/web.xml", res, null);
        Assert.assertEquals(
                useSubpathWebdav && allowSpecialPaths ? HttpServletResponse.SC_OK : HttpServletResponse.SC_NOT_FOUND,
                rc);

        rc = getUrl("http://localhost:" + getPort() + contextPath + "/WEB-INF/doesntexistanywhere", res, null);
        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);

        rc = getUrl("http://localhost:" + getPort() + contextPath + "/META-INF/context.xml", res, null);
        Assert.assertEquals(
                useSubpathWebdav && allowSpecialPaths ? HttpServletResponse.SC_OK : HttpServletResponse.SC_NOT_FOUND,
                rc);

        rc = getUrl("http://localhost:" + getPort() + contextPath + "/META-INF/doesntexistanywhere", res, null);
        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);

        rc = getUrl("http://localhost:" + getPort() + contextPath + "/WEB-INF-desc.xml", res, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
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
        ctx.addServletMapping("/webdav/*", "webdav");
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

        ctxt.addServletMapping("/*", "webdav");
        ctxt.addMimeMapping("html", "text/html");

        tomcat.start();

        Client client = new Client();
        client.setPort(getPort());
        // @formatter:off
        client.setRequest(new String[] {
                "PROPFIND /bug66609/ HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.sendRequest();

        client.setUseContentLength(true);
        client.readResponse(true);

        // This will throw an exception if the XML is not valid
        SAXParserFactory.newInstance().newSAXParser().getXMLReader().parse(new InputSource(new StringReader(client.getResponseBody())));
    }

    private static final String CONTENT = "FOOBAR";

    private static final String WEBDAV_USER = "webdav-rw";
    private static final String WEBDAV_PWD = "pwd";
    private static final String APP_ADMIN_ROLE = "app-admin";

    // @formatter:off
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
    // @formatter:on

    @Test
    public void testBasicProperties() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Create a temp webapp that can be safely written to
        File tempWebapp = new File(getTemporaryDirectory(), "webdav-properties");
        tempWebapp.deleteOnExit();
        Assert.assertTrue(tempWebapp.mkdirs());
        Context ctxt = tomcat.addContext("", tempWebapp.getAbsolutePath());
        Wrapper webdavServlet = Tomcat.addServlet(ctxt, "webdav", new WebdavServlet());
        webdavServlet.addInitParameter("listings", "true");
        webdavServlet.addInitParameter("secret", "foo");
        webdavServlet.addInitParameter("readonly", "false");
        ctxt.addServletMapping("/*", "webdav");
        ctxt.addMimeMapping("txt", "text/plain");
        tomcat.start();

        Client client = new Client();
        client.setPort(getPort());

        // Create a test file
        // @formatter:off
        client.setRequest(new String[] {
                "PUT /file1.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: 6" + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                CONTENT
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // Add lock to check for lock discovery
        // @formatter:off
        client.setRequest(new String[] {
                "LOCK /file2.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: " + LOCK_BODY.length() + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                LOCK_BODY
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("urn:uuid:"));

        // @formatter:off
        client.setRequest(new String[] {
                "PROPFIND / HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("urn:uuid:"));

        // @formatter:off
        client.setRequest(new String[] {
                "PROPPATCH /file1.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: " + PROPPATCH_PROPNAME.length() + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                PROPPATCH_PROPNAME
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("<T:othercustomprop"));
        validateXml(client.getResponseBody());

        // @formatter:off
        client.setRequest(new String[] {
                "PROPFIND /file1.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: " + PROPFIND_PROPNAME.length() + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                PROPFIND_PROPNAME
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("<D:getcontenttype/>"));

        // @formatter:off
        client.setRequest(new String[] {
                "PROPFIND /file1.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: " + PROPFIND_PROP.length() + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                PROPFIND_PROP
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("<D:getcontenttype>"));
        Assert.assertFalse(client.getResponseBody().contains("<D:getlastmodified>"));
        Assert.assertTrue(client.getResponseBody().contains("<myvalue xmlns=\"http://tomcat.apache.org/testsuite\">"));

        // @formatter:off
        client.setRequest(new String[] {
                "MOVE /file1.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Destination: /file3.txt" + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // @formatter:off
        client.setRequest(new String[] {
                "PROPFIND /file3.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: " + PROPFIND_PROP.length() + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                PROPFIND_PROP
                });
        // @formatter:on
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
        tempWebapp.deleteOnExit();
        Assert.assertTrue(tempWebapp.mkdirs());
        Context ctxt = tomcat.addContext("", tempWebapp.getAbsolutePath());
        Wrapper webdavServlet = Tomcat.addServlet(ctxt, "webdav", new WebdavServlet());
        webdavServlet.addInitParameter("listings", "true");
        webdavServlet.addInitParameter("secret", "foo");
        webdavServlet.addInitParameter("readonly", "false");
        webdavServlet.addInitParameter("useStrongETags", "true");
        ctxt.addServletMapping("/*", "webdav");
        tomcat.start();

        ctxt.getResources().setCacheMaxSize(10);
        ctxt.getResources().setCacheObjectMaxSize(1);

        Client client = new Client();
        client.setPort(getPort());

        // Create a few files
        // @formatter:off
        client.setRequest(new String[] {
                "PUT /file1.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: 6" + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                CONTENT
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // @formatter:off

        client.setRequest(new String[] {
                "PUT /file2.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: 12" + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                CONTENT +
                CONTENT
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append(CONTENT);
        }
        // @formatter:off
        client.setRequest(new String[] {
                "PUT /file12.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: " + String.valueOf(sb.length()) + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                sb.toString()
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // @formatter:off
        client.setRequest(new String[] {
                "MKCOL /myfolder HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // @formatter:off
        client.setRequest(new String[] {
                "PUT /myfolder/file3.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: 6" + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                CONTENT
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // Verify that listing the file works
        // @formatter:off
        client.setRequest(new String[] {
                "PROPFIND / HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("<D:getcontentlength>12<"));

        // Lock /myfolder
        // @formatter:off
        client.setRequest(new String[] {
                "LOCK /myfolder HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Timeout: Second-fwe, Second-259" + CRLF +
                "Content-Length: " + LOCK_BODY.length() + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                LOCK_BODY
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        String clientBody = client.getResponseBody();
        Assert.assertTrue(clientBody.contains("urn:uuid:"));
        Assert.assertTrue(clientBody.contains("Second-25"));
        String lockToken = null;
        for (String header : client.getResponseHeaders()) {
            if (header.startsWith("Lock-Token: ")) {
                lockToken = header.substring("Lock-Token: ".length());
            }
        }
        Assert.assertNotNull(lockToken);

        // Try to add /myfolder/file4.txt to myfolder without lock token
        // @formatter:off
        client.setRequest(new String[] {
                "PUT /myfolder/file4.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: 6" + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                CONTENT
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_LOCKED, client.getStatusCode());

        // Same but provide the lock token
        // @formatter:off
        client.setRequest(new String[] {"PUT /myfolder/file4.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: 6" + CRLF +
                "If: </myfolder/> (" + lockToken + ")" + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                CONTENT
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // Add lock for /myfolder/file5.txt
        // @formatter:off
        client.setRequest(new String[] {
                "LOCK /myfolder/file5.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: " + LOCK_BODY.length() + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                LOCK_BODY
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_LOCKED, client.getStatusCode());

        // @formatter:off
        client.setRequest(new String[] {
                "UNLOCK /myfolder/file5.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Lock-Token: <my:locktoken>" + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CONFLICT, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("<D:href>/myfolder</D:href>"));

        // Unlock /myfolder
        // @formatter:off
        client.setRequest(new String[] {
                "UNLOCK /myfolder/file5.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Lock-Token: " + lockToken + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_NO_CONTENT, client.getStatusCode());

        // @formatter:off
        client.setRequest(new String[] {
                "LOCK /myfolder/file5.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: " + LOCK_BODY.length() + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                LOCK_BODY
                });
        // @formatter:on
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

        // @formatter:off
        client.setRequest(new String[] {
                "LOCK /myfolder HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: " + LOCK_BODY.length() + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                LOCK_BODY
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("/myfolder/file5.txt"));
        Assert.assertTrue(client.getResponseBody().contains("HTTP/1.1 423"));

        // @formatter:off

        client.setRequest(new String[] {
                "PUT /myfolder/file5.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: 6" + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                CONTENT
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_LOCKED, client.getStatusCode());

        // Same but with lock token and lock null
        // @formatter:off
        client.setRequest(new String[] {
                "PUT /myfolder/file5.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: 6" + CRLF +
                "If: (" + lockTokenFile + ")" + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                CONTENT
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_NO_CONTENT, client.getStatusCode());

        // Verify that this also removes the lock by doing another PUT without the token
        // @formatter:off
        client.setRequest(new String[] {
                "DELETE /myfolder/file5.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "If: (" + lockTokenFile + ")" + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_NO_CONTENT, client.getStatusCode());

        // @formatter:off
        client.setRequest(new String[] {
                "PUT /myfolder/file5.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: 6" + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                CONTENT
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // Lock /myfolder again
        // @formatter:off
        client.setRequest(new String[] {
                "LOCK /myfolder HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Timeout: Second-20" + CRLF +
                "Content-Length: " + LOCK_BODY.length() + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                LOCK_BODY
                });
        // @formatter:on
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
        // @formatter:off
        client.setRequest(new String[] {
                "COPY /myfolder/file5.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Destination: http://localhost:" + getPort() + "/myfolder/file6.txt"  + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_LOCKED, client.getStatusCode());

        // @formatter:off

        client.setRequest(new String[] {
                "COPY /myfolder HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Destination: /myfolder2"  + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_CREATED, client.getStatusCode());

        // Delete /myfolder/file4.txt
        // @formatter:off
        client.setRequest(new String[] {
                "DELETE /myfolder/file4.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "If: (" + lockToken + ")" + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_NO_CONTENT, client.getStatusCode());

        // Copy /myfolder/file5.txt to /file7.txt without lock (should work)
        // @formatter:off
        client.setRequest(new String[] {
                "COPY /myfolder/file5.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Destination: http://localhost:" + getPort() + "/file7.txt"  + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        StringBuilder sb2 = new StringBuilder();
        for (int i = 0; i < 3000; i++) {
            sb2.append(CONTENT);
        }
        // @formatter:off
        client.setRequest(new String[] {
                "PUT /file6.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: " + String.valueOf(sb2.length()) +  CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                sb2.toString()
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // Verify that everything created is there
        // @formatter:off
        client.setRequest(new String[] {
                "PROPFIND / HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        Assert.assertFalse(client.getResponseBody().contains("/myfolder/file4.txt"));
        Assert.assertTrue(client.getResponseBody().contains("/file7.txt"));
        Assert.assertTrue(client.getResponseBody().contains("Second-"));
        // SHA-256 hash for "FOOBAR...FOOBAR" (repeats 3000 times)
        Assert.assertTrue(client.getResponseBody().contains(
                "bb94e8d310800b24310036b168aa5a946e27f9572b3d99f956f3a3ed2e7d3045"));
        // SHA-256 hash for "FOOBAR"
        Assert.assertTrue(client.getResponseBody().contains(
                "24c422e681f1c1bd08286c7aaf5d23a5f088dcdb0b219806b3a9e579244f00c5"));
        String timeoutValue = client.getResponseBody().substring(client.getResponseBody().indexOf("Second-"));
        timeoutValue = timeoutValue.substring("Second-".length(), timeoutValue.indexOf('<'));
        Assert.assertTrue(Integer.valueOf(timeoutValue).intValue() <= 20);

        // Unlock /myfolder again
        // @formatter:off
        client.setRequest(new String[] {
                "UNLOCK /myfolder/ HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Lock-Token: " + lockToken + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_NO_CONTENT, client.getStatusCode());

        // Delete /myfolder
        // @formatter:off
        client.setRequest(new String[] {
                "DELETE /myfolder HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_NO_CONTENT, client.getStatusCode());

        // @formatter:off
        client.setRequest(new String[] {
                "DELETE /myfolder2 HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_NO_CONTENT, client.getStatusCode());

        // @formatter:off
        client.setRequest(new String[] { "PROPFIND / HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
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
        tempWebapp.deleteOnExit();
        File subPath = new File(tempWebapp, "aaa");
        Assert.assertTrue(subPath.mkdirs());

        Context ctxt = tomcat.addContext("", tempWebapp.getAbsolutePath());
        Wrapper webdavServlet = Tomcat.addServlet(ctxt, "webdav", new WebdavServlet());
        webdavServlet.addInitParameter("listings", "true");
        webdavServlet.addInitParameter("readonly", "false");
        webdavServlet.addInitParameter("serveSubpathOnly", "true");
        ctxt.addServletMapping("/aaa/*", "webdav");
        tomcat.start();

        ctxt.getResources().setCacheMaxSize(10);
        ctxt.getResources().setCacheObjectMaxSize(1);

        Client client = new Client();
        client.setPort(getPort());

        // Create a file
        // @formatter:off
        client.setRequest(new String[] {
                "PUT /aaa/file1.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: 6" + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                CONTENT
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // Copy file1.txt to file2.txt
        // @formatter:off
        client.setRequest(new String[] {
                "COPY /aaa/file1.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Destination: http://localhost:" + getPort() + "/aaa/file2.txt"  + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // Move file2.txt to file3.txt
        // @formatter:off
        client.setRequest(new String[] {
                "MOVE /aaa/file2.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Destination: http://localhost:" + getPort() + "/aaa/file3.txt"  + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // Copy file1.txt outside sub-path
        // @formatter:off
        client.setRequest(new String[] {
                "COPY /aaa/file1.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Destination: http://localhost:" + getPort() + "/file1.txt"  + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_FORBIDDEN, client.getStatusCode());

        // Move file1.txt outside sub-path
        // @formatter:off
        client.setRequest(new String[] {
                "MOVE /aaa/file1.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Destination: http://localhost:" + getPort() + "/file1.txt"  + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_FORBIDDEN, client.getStatusCode());
}


    @Test
    public void testSharedLocks() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Create a temp webapp that can be safely written to
        File tempWebapp = new File(getTemporaryDirectory(), "webdav-lock");
        tempWebapp.deleteOnExit();
        Assert.assertTrue(tempWebapp.mkdirs());
        Context ctxt = tomcat.addContext("", tempWebapp.getAbsolutePath());
        Wrapper webdavServlet = Tomcat.addServlet(ctxt, "webdav", new WebdavServlet());
        webdavServlet.addInitParameter("listings", "true");
        webdavServlet.addInitParameter("secret", "foo");
        webdavServlet.addInitParameter("readonly", "false");
        ctxt.addServletMapping("/*", "webdav");
        tomcat.start();

        Client client = new Client();
        client.setPort(getPort());

        // Create a few folders and files
        // @formatter:off
        client.setRequest(new String[] {
                "MKCOL /myfolder HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // @formatter:off

        client.setRequest(new String[] {
                "MKCOL /myfolder/myfolder2/ HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // @formatter:off
        client.setRequest(new String[] {
                "MKCOL /myfolder/myfolder3 HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // @formatter:off
        client.setRequest(new String[] {
                "MKCOL /myfolder/myfolder2/myfolder4 HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // @formatter:off
        client.setRequest(new String[] {
                "MKCOL /myfolder/myfolder2/myfolder4/myfolder5 HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // @formatter:off

        client.setRequest(new String[] {
                "PUT /file1.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: 6" + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                CONTENT
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // @formatter:off

        client.setRequest(new String[] {
                "PUT /myfolder/myfolder3/file2.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: 6" + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                CONTENT
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // @formatter:off
        client.setRequest(new String[] {
                "PUT /myfolder/myfolder2/myfolder4/file3.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: 6" + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                CONTENT
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // Lock /myfolder
        // @formatter:off
        client.setRequest(new String[] {
                "LOCK /myfolder HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: " + LOCK_SHARED_BODY.length() + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                LOCK_SHARED_BODY
                });
        // @formatter:on
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

        // @formatter:off
        client.setRequest(new String[] {
                "LOCK /myfolder/myfolder2/myfolder4/myfolder5 HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "If: (" + lockToken + ")" + CRLF +
                "Content-Length: " + LOCK_BODY.length() + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                LOCK_BODY
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        // This should conflict, submitting a token does not help
        Assert.assertEquals(WebdavStatus.SC_LOCKED, client.getStatusCode());

        // Lock refresh /myfolder
        // @formatter:off
        client.setRequest(new String[] {
                "LOCK /myfolder HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Timeout: Infinite" + CRLF +
                "If: (" + lockToken + ")" + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());

        // @formatter:off
        client.setRequest(new String[] {
                "LOCK /myfolder/myfolder2/myfolder4 HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: " + LOCK_SHARED_BODY.length() + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                LOCK_SHARED_BODY
                });
        // @formatter:on
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
        // @formatter:off
        client.setRequest(new String[] {
                "LOCK /myfolder/myfolder2/myfolder4 HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: " + LOCK_SHARED_BODY.length() + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                LOCK_SHARED_BODY
                });
        // @formatter:on
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

        // @formatter:off
        client.setRequest(new String[] {
                "PROPFIND / HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("urn:uuid:"));
        Assert.assertTrue(client.getResponseBody().contains("Second-"));
        String timeoutValue = client.getResponseBody().substring(client.getResponseBody().indexOf("Second-"));
        timeoutValue = timeoutValue.substring("Second-".length(), timeoutValue.indexOf('<'));
        Assert.assertTrue(Integer.valueOf(timeoutValue).intValue() > 100000);
        validateXml(client.getResponseBody());

        // @formatter:off
        client.setRequest(new String[] {
                "PUT /myfolder/myfolder2/myfolder4/myfolder5/file4.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: 6" + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                CONTENT
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_LOCKED, client.getStatusCode());

        // @formatter:off
        client.setRequest(new String[] {
                "PUT /myfolder/myfolder2/myfolder4/myfolder5/file4.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "If: (" + lockToken + ")" + CRLF +
                "Content-Length: 6" + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                CONTENT
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_PRECONDITION_FAILED, client.getStatusCode());

        // @formatter:off
        client.setRequest(new String[] {
                "PUT /myfolder/myfolder2/myfolder4/myfolder5/file4.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "If: </myfolder/myfolder2/myfolder4/myfolder5> (" + lockToken + ")" + CRLF +
                "Content-Length: 6" + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                CONTENT
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // @formatter:off
        client.setRequest(new String[] {
                "UNLOCK /myfolder/myfolder3 HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Lock-Token: " + lockToken2 + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CONFLICT, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("<D:lock-token-matches-request-uri/>"));

        // @formatter:off
        client.setRequest(new String[] {
                "UNLOCK /myfolder/myfolder2/myfolder4/myfolder5 HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Lock-Token: " + lockToken2 + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_NO_CONTENT, client.getStatusCode());

        // @formatter:off
        client.setRequest(new String[] {
                "UNLOCK /myfolder/myfolder2/myfolder4/myfolder5/file4.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Lock-Token: " + lockToken3 + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_NO_CONTENT, client.getStatusCode());

        // @formatter:off

        client.setRequest(new String[] {
                "PUT /myfolder/myfolder2/myfolder4/myfolder5/file4.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: 6" + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                CONTENT
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_LOCKED, client.getStatusCode());

        // @formatter:off

        client.setRequest(new String[] {
                "UNLOCK /myfolder/myfolder2/myfolder4/myfolder5/file4.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Lock-Token: " + lockToken + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_NO_CONTENT, client.getStatusCode());

        // @formatter:off

        client.setRequest(new String[] {
                "PUT /myfolder/myfolder2/myfolder4/myfolder5/file4.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: 12" + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                CONTENT  +
                CONTENT
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_NO_CONTENT, client.getStatusCode());

        // @formatter:off
        client.setRequest(new String[] {
                "PROPFIND / HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
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
        tempWebapp.deleteOnExit();
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
        ctxt.addServletMapping("/*", "webdav");
        tomcat.start();

        Client client = new Client();
        client.setPort(getPort());

        // @formatter:off
        client.setRequest(new String[] {
                "LOCK /myfolder/myfolder3 HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: " + LOCK_BODY.length() + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                LOCK_BODY
                });
        // @formatter:on
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

        // @formatter:off
        client.setRequest(new String[] {
                "LOCK /myfolder/myfolder7 HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: " + LOCK_SHARED_BODY.length() + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                LOCK_SHARED_BODY
                });
        // @formatter:on
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

        // @formatter:off
        client.setRequest(new String[] {
                "LOCK /myfolder/myfolder7/myfolder8 HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: " + LOCK_SHARED_BODY.length() + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                LOCK_SHARED_BODY
                });
        // @formatter:on
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

        // @formatter:off
        client.setRequest(new String[] {
                "PUT /myfolder/myfolder2/myfolder4/myfolder5/file4.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "If: </myfolder/myfolder3/myfolder6> (<urn:uuid:5e1e2275b1cd9c17845e7e08>)" + // Obvious wrong token
                " </myfolder/myfolder7/myfolder8/myfolder9> (" + lockToken + " " + lockToken2 + " " + lockToken3 + ")" + // lockToken is not there
                " </myfolder/myfolder2/myfolder4> (<urn:uuid:7329872398754923752> [W/\"4-1729375899470\"])" + // Not locked
                " </myfolder/myfolder7/myfolder8> (" + lockToken + ")" + CRLF + // lockToken is not there
                "Content-Length: 6" + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                CONTENT
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_PRECONDITION_FAILED, client.getStatusCode());

        // @formatter:off
        client.setRequest(new String[] {
                "PUT /myfolder/myfolder2/myfolder4/myfolder5/file4.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "If: </myfolder/myfolder3/myfolder6> (<urn:uuid:5e1e2275b1cd9c17845e7e08>)" + // Obvious wrong token
                " </myfolder/myfolder2/myfolder4> (<urn:uuid:7329872398754923752> [W/\"4-1729375899470\"])" + // Not locked
                " </myfolder/myfolder7/myfolder8> (" + lockToken + ")" + // lockToken is not there
                " </myfolder/myfolder7/myfolder8/myfolder9> (" + lockToken2 + " " + lockToken3 + ")" + CRLF + // Correct
                "Content-Length: 6" + CRLF +
                "Connection: Close" + CRLF +
                CRLF +
                CONTENT
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_CREATED, client.getStatusCode());
    }

    /*
     * An If header value that contains an empty list is invalid syntax (RFC 4918 requires at least one entry in a
     * list). With strict If processing (the default) such a header must fail the precondition check. Without strict
     * If processing the header is ignored, as for any other invalid If header syntax.
     */
    @Test
    public void testIfHeaderEmptyListStrict() throws Exception {
        Client client = doTestIfHeaderEmptyList("webdav-emptyif-strict", true);
        client.setRequest(new String[] {
                "PUT /file1.txt HTTP/1.1" + CRLF +
                    "Host: localhost:" + getPort() + CRLF +
                    "If: ()" + CRLF +
                    "Content-Length: 6" + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF +
                    CONTENT
                });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_PRECONDITION_FAILED, client.getStatusCode());
    }


    @Test
    public void testIfHeaderEmptyListNonStrict() throws Exception {
        Client client = doTestIfHeaderEmptyList("webdav-emptyif-nonstrict", false);
        client.setRequest(new String[] {
                "PUT /file1.txt HTTP/1.1" + CRLF +
                    "Host: localhost:" + getPort() + CRLF +
                    "If: ()" + CRLF +
                    "Content-Length: 6" + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF +
                    CONTENT
                });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());
    }


    private Client doTestIfHeaderEmptyList(String webappName, boolean strictIfProcessing) throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Create a temp webapp that can be safely written to
        File tempWebapp = new File(getTemporaryDirectory(), webappName);
        tempWebapp.deleteOnExit();
        Assert.assertTrue(tempWebapp.mkdirs());
        Context ctxt = tomcat.addContext("", tempWebapp.getAbsolutePath());
        Wrapper webdavServlet = Tomcat.addServlet(ctxt, "webdav", new WebdavServlet());
        webdavServlet.addInitParameter("readonly", "false");
        webdavServlet.addInitParameter("strictIfProcessing", Boolean.toString(strictIfProcessing));
        ctxt.addServletMapping("/*", "webdav");
        ctxt.addMimeMapping("txt", "text/plain");
        tomcat.start();

        Client client = new Client();
        client.setPort(getPort());
        return client;
    }


    @Test
    public void testPropertyStore() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Create a temp webapp that can be safely written to
        File tempWebapp = new File(getTemporaryDirectory(), "webdav-store");
        tempWebapp.deleteOnExit();
        Assert.assertTrue(tempWebapp.mkdirs());
        Context ctxt = tomcat.addContext("", tempWebapp.getAbsolutePath());
        Wrapper webdavServlet = Tomcat.addServlet(ctxt, "webdav", new WebdavServlet());
        webdavServlet.addInitParameter("listings", "true");
        webdavServlet.addInitParameter("secret", "foo");
        webdavServlet.addInitParameter("readonly", "false");
        webdavServlet.addInitParameter("propertyStore", "org.apache.catalina.servlets.TestWebdavServlet$CustomPropertyStore");
        webdavServlet.addInitParameter("store.propertyName", "mytestproperty");
        webdavServlet.addInitParameter("store.propertyValue", "testvalue");
        ctxt.addServletMapping("/*", "webdav");
        ctxt.addMimeMapping("txt", "text/plain");
        tomcat.start();

        Client client = new Client();
        client.setPort(getPort());

        // @formatter:off
        client.setRequest(new String[] {
                "PROPFIND / HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Connection: Close" + CRLF +
                CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains(">testvalue</mytestproperty>"));
        validateXml(client.getResponseBody());
    }


    /*
     * Only tests LOCK bodies exceeding limit. Other tests cover valid LOCK bodies.
     */
    @Test
    public void testLockBodyLimit() throws Exception {
        doTestLimit("LOCK", LOCK_BODY);
    }


    /*
     * Only tests PROPFIND bodies exceeding limit. Other tests cover valid PROPFIND bodies.
     */
    @Test
    public void testPropFindBodyLimit() throws Exception {
        doTestLimit("PROPFIND", PROPFIND_PROP);
    }


    /*
     * The XML error reports start with a declaration that states UTF-8, so the
     * response needs to name a content type with a matching charset. Without a
     * content type the response writer falls back to the default body charset,
     * which is ISO-8859-1.
     */
    @Test
    public void testErrorReportsUseTheDeclaredCharset() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Create a temp webapp that can be safely written to
        File tempWebapp = new File(getTemporaryDirectory(), "webdav-errorreport");
        tempWebapp.deleteOnExit();
        Assert.assertTrue(tempWebapp.mkdirs());
        Context ctxt = tomcat.addContext("", tempWebapp.getAbsolutePath());
        Wrapper webdavServlet = Tomcat.addServlet(ctxt, "webdav", new WebdavServlet());
        webdavServlet.addInitParameter("listings", "true");
        webdavServlet.addInitParameter("secret", "foo");
        webdavServlet.addInitParameter("readonly", "false");
        ctxt.addServletMapping("/*", "webdav");
        tomcat.start();

        Client client = new Client();
        client.setPort(getPort());

        // Create a collection
        // @formatter:off
        client.setRequest(new String[] {
                "MKCOL /myfolder HTTP/1.1" + CRLF +
                    "Host: localhost:" + getPort() + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // Create a file in the collection
        // @formatter:off
        client.setRequest(new String[] {
                "PUT /myfolder/file.txt HTTP/1.1" + CRLF +
                    "Host: localhost:" + getPort() + CRLF +
                    "Content-Length: " + CONTENT.length() + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF +
                    CONTENT
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CREATED, client.getStatusCode());

        // Lock the file
        // @formatter:off
        client.setRequest(new String[] {
                "LOCK /myfolder/file.txt HTTP/1.1" + CRLF +
                    "Host: localhost:" + getPort() + CRLF +
                    "Content-Length: " + LOCK_BODY.length() + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF +
                    LOCK_BODY
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());

        // Unlock the file with a token that locks nothing, which is reported
        // with an error document
        // @formatter:off
        client.setRequest(new String[] {
                "UNLOCK /myfolder/file.txt HTTP/1.1" + CRLF +
                    "Host: localhost:" + getPort() + CRLF +
                    "Lock-Token: <my:token>" + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_CONFLICT, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("<D:lock-token-matches-request-uri>"));
        assertXmlContentType(client);
        validateXml(client.getResponseBody());

        // Lock the collection, which conflicts with the lock of the child
        // file and is reported with a multi-status document
        // @formatter:off
        client.setRequest(new String[] {
                "LOCK /myfolder HTTP/1.1" + CRLF +
                    "Host: localhost:" + getPort() + CRLF +
                    "Content-Length: " + LOCK_BODY.length() + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF +
                    LOCK_BODY
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("<D:href>/myfolder/file.txt</D:href>"));
        assertXmlContentType(client);
        validateXml(client.getResponseBody());

        // Delete the collection, which cannot delete the locked file and is
        // reported with a multi-status document
        // @formatter:off
        client.setRequest(new String[] {
                "DELETE /myfolder HTTP/1.1" + CRLF +
                    "Host: localhost:" + getPort() + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("<D:href>/myfolder/file.txt</D:href>"));
        assertXmlContentType(client);
        validateXml(client.getResponseBody());
    }


    @Test
    public void testTrailingSlashExactPathConstraint() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Create a temp webapp that can be safely written to
        File tempWebapp = new File(getTemporaryDirectory(), "webdav-trailing-slash-" + UUID.randomUUID());
        tempWebapp.deleteOnExit();
        Assert.assertTrue(tempWebapp.mkdirs());
        File docDir = new File(tempWebapp, "doc");
        Assert.assertTrue(docDir.mkdirs());

        File putFile = new File(docDir, "put.txt");
        File deleteFile = new File(docDir, "delete.txt");
        File proppatchFile = new File(docDir, "proppatch.txt");
        File newFile = new File(docDir, "new.txt");
        createFile(putFile);
        createFile(deleteFile);
        createFile(proppatchFile);

        tomcat.addUser(WEBDAV_USER, WEBDAV_PWD);
        tomcat.addRole(WEBDAV_USER, WEBDAV_USER);

        Context ctxt = tomcat.addContext("", tempWebapp.getAbsolutePath());
        Wrapper webdavServlet = Tomcat.addServlet(ctxt, "webdav", new WebdavServlet());
        webdavServlet.addInitParameter("listings", "true");
        webdavServlet.addInitParameter("readonly", "false");
        ctxt.addServletMapping("/webdav/*", "webdav");

        SecurityCollection protectedFiles = new SecurityCollection();
        protectedFiles.addPattern("/webdav/doc/put.txt");
        protectedFiles.addPattern("/webdav/doc/delete.txt");
        protectedFiles.addPattern("/webdav/doc/proppatch.txt");
        SecurityConstraint protectedFilesConstraint = new SecurityConstraint();
        protectedFilesConstraint.addAuthRole(APP_ADMIN_ROLE);
        protectedFilesConstraint.addCollection(protectedFiles);
        ctxt.addConstraint(protectedFilesConstraint);

        SecurityCollection webdavCollection = new SecurityCollection();
        webdavCollection.addPattern("/webdav/*");
        SecurityConstraint webdavConstraint = new SecurityConstraint();
        webdavConstraint.addAuthRole(WEBDAV_USER);
        webdavConstraint.addCollection(webdavCollection);
        ctxt.addConstraint(webdavConstraint);

        LoginConfig lc = new LoginConfig();
        lc.setAuthMethod("BASIC");
        ctxt.setLoginConfig(lc);
        ctxt.getPipeline().addValve(new BasicAuthenticator());

        tomcat.start();

        Client client = new Client();
        client.setPort(getPort());
        String authorization = basicAuthorization(WEBDAV_USER, WEBDAV_PWD);

        // Plain requests must be denied and leave the files unchanged
        sendRequest(client, createRequest("PUT", "/webdav/doc/put.txt", authorization, CONTENT));
        Assert.assertEquals(HttpServletResponse.SC_FORBIDDEN, client.getStatusCode());
        Assert.assertEquals(CONTENT, Files.readString(putFile.toPath()));

        sendRequest(client, createRequest("DELETE", "/webdav/doc/delete.txt", authorization, null));
        Assert.assertEquals(HttpServletResponse.SC_FORBIDDEN, client.getStatusCode());
        Assert.assertTrue(deleteFile.exists());

        sendRequest(client, createRequest("PROPPATCH", "/webdav/doc/proppatch.txt", authorization,
                PROPPATCH_PROPNAME));
        Assert.assertEquals(HttpServletResponse.SC_FORBIDDEN, client.getStatusCode());

        // The trailing slash bypasses the exact path constraint, but the path
        // must not be normalized to the protected file
        sendRequest(client, createRequest("PUT", "/webdav/doc/put.txt/", authorization, "REPLACED"));
        Assert.assertEquals(HttpServletResponse.SC_CONFLICT, client.getStatusCode());
        Assert.assertEquals(CONTENT, Files.readString(putFile.toPath()));

        sendRequest(client, createRequest("DELETE", "/webdav/doc/delete.txt/", authorization, null));
        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, client.getStatusCode());
        Assert.assertTrue(deleteFile.exists());

        sendRequest(client, createRequest("PROPPATCH", "/webdav/doc/proppatch.txt/", authorization,
                PROPPATCH_PROPNAME));
        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, client.getStatusCode());

        // A trailing slash must not create a new file. The slash is never part
        // of a file name; it indicates a collection.
        sendRequest(client, createRequest("PUT", "/webdav/doc/new.txt/", authorization, CONTENT));
        Assert.assertEquals(HttpServletResponse.SC_CONFLICT, client.getStatusCode());
        Assert.assertFalse(newFile.exists());
    }


    @Test
    public void testTrailingSlashExtensionConstraint() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Create a temp webapp that can be safely written to
        File tempWebapp = new File(getTemporaryDirectory(), "webdav-trailing-slash-jsp-" + UUID.randomUUID());
        tempWebapp.deleteOnExit();
        Assert.assertTrue(tempWebapp.mkdirs());

        Context ctxt = tomcat.addContext("", tempWebapp.getAbsolutePath());
        Wrapper webdavServlet = Tomcat.addServlet(ctxt, "webdav", new WebdavServlet());
        webdavServlet.addInitParameter("readonly", "false");
        ctxt.addServletMapping("/webdav/*", "webdav");

        SecurityCollection jspCollection = new SecurityCollection();
        jspCollection.addPattern("*.jsp");
        jspCollection.addMethod("PUT");
        SecurityConstraint jspConstraint = new SecurityConstraint();
        jspConstraint.addAuthRole(APP_ADMIN_ROLE);
        jspConstraint.addCollection(jspCollection);
        ctxt.addConstraint(jspConstraint);

        LoginConfig lc = new LoginConfig();
        lc.setAuthMethod("BASIC");
        ctxt.setLoginConfig(lc);
        ctxt.getPipeline().addValve(new BasicAuthenticator());

        tomcat.start();

        Client client = new Client();
        client.setPort(getPort());
        File plantedJsp = new File(tempWebapp, "planted.jsp");

        // Plain request is protected by the *.jsp constraint
        sendRequest(client, createRequest("PUT", "/webdav/planted.jsp", null, "PLANTED-JSP-42"));
        Assert.assertEquals(HttpServletResponse.SC_UNAUTHORIZED, client.getStatusCode());
        Assert.assertFalse(plantedJsp.exists());

        // The trailing slash bypasses the *.jsp constraint, but the path must
        // not be normalized to a JSP and written
        sendRequest(client, createRequest("PUT", "/webdav/planted.jsp/", null, "PLANTED-JSP-42"));
        Assert.assertEquals(HttpServletResponse.SC_CONFLICT, client.getStatusCode());
        Assert.assertFalse(plantedJsp.exists());

        sendRequest(client, createRequest("GET", "/planted.jsp", null, null));
        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, client.getStatusCode());
    }


    private void createFile(File file) throws IOException {
        try (FileWriter fw = new FileWriter(file)) {
            fw.write(CONTENT);
        }
    }


    private String basicAuthorization(String user, String password) {
        String credentials = Base64.getEncoder()
                .encodeToString((user + ":" + password).getBytes(StandardCharsets.ISO_8859_1));
        return "Authorization: Basic " + credentials;
    }


    private String createRequest(String method, String path, String authorization, String body) {
        StringBuilder request = new StringBuilder();
        request.append(method);
        request.append(' ');
        request.append(path);
        request.append(" HTTP/1.1");
        request.append(CRLF);
        request.append("Host: localhost:");
        request.append(getPort());
        request.append(CRLF);
        if (authorization != null) {
            request.append(authorization);
            request.append(CRLF);
        }
        if (body != null) {
            request.append("Content-Length: ");
            request.append(body.length());
            request.append(CRLF);
        }
        request.append("Connection: Close");
        request.append(CRLF);
        request.append(CRLF);
        if (body != null) {
            request.append(body);
        }
        return request.toString();
    }


    private void sendRequest(Client client, String request) throws IOException, InterruptedException {
        client.resetResponse();
        client.setRequest(new String[] { request });
        client.connect();
        client.processRequest(true);
    }


    private void doTestLimit(String method, String requestBody) throws Exception {

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        Context ctxt = tomcat.addContext("", appDir.getAbsolutePath());

        Wrapper webdavServlet = Tomcat.addServlet(ctxt, "webdav", new WebdavServlet());
        webdavServlet.addInitParameter("listings", "true");
        webdavServlet.addInitParameter("secret", "foo");
        webdavServlet.addInitParameter("readonly", "false");
        webdavServlet.addInitParameter("useStrongETags", "true");
        webdavServlet.addInitParameter("maxRequestBodySize", "10");

        ctxt.addServletMapping("/*", "webdav");
        tomcat.start();

        // With content length
        Client client = new Client();
        client.setPort(getPort());

        // @formatter:off
        client.setRequest(new String[] {
                method + " / HTTP/1.1" + CRLF +
                    "Host: localhost:" + getPort() + CRLF +
                    "Content-Length: " + requestBody.length() + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF +
                    requestBody
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_REQUEST_TOO_LONG, client.getStatusCode());

        // Without content length
        client.reset();

        // @formatter:off
        client.setRequest(new String[] {
                method + " / HTTP/1.1" + CRLF +
                    "Host: localhost:" + getPort() + CRLF +
                    "Transfer-Encoding: chunked" + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF +
                    Integer.toHexString(requestBody.length()) + CRLF +
                    requestBody + CRLF +
                    "0" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(WebdavStatus.SC_REQUEST_TOO_LONG, client.getStatusCode());
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


    private static void assertXmlContentType(SimpleHttpClient client) {
        for (String header : client.getResponseHeaders()) {
            if (header.regionMatches(true, 0, "Content-Type: ", 0, "Content-Type: ".length())) {
                String contentType = header.substring("Content-Type: ".length()).trim();
                int semicolon = contentType.indexOf(';');
                Assert.assertTrue("The content type has no charset: " + contentType, semicolon >= 0);
                Assert.assertEquals("text/xml", contentType.substring(0, semicolon).trim());
                Assert.assertEquals("charset=UTF-8", contentType.substring(semicolon + 1).trim());
                return;
            }
        }
        Assert.fail("The response has no content type");
    }

    /*
     * Properties that are requested more than once are only processed once.
     */
    @Test
    public void testPropfindDuplicateProperties() throws Exception {
        String body = propfindBody("urn:test:a", "p", "p", "q", "p");
        Client client = doTestPropfind(null, "0", body, 1);

        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        Assert.assertEquals(1, count(client.getResponseBody(), "<ns0:p/>"));
        Assert.assertEquals(1, count(client.getResponseBody(), "<ns0:q/>"));
    }


    /*
     * The namespace of a property that can't be found is declared once on the root element rather than for every
     * property of every resource.
     */
    @Test
    public void testPropfindNamespaceDeclaredOnce() throws Exception {
        String body = propfindBody("urn:test:a", "p", "q");
        Client client = doTestPropfind(null, "1", body, 3);

        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        String responseBody = client.getResponseBody();
        // Collection plus 3 files
        Assert.assertEquals(4, count(responseBody, "<D:response>"));
        Assert.assertEquals(1, count(responseBody, "urn:test:a"));
        Assert.assertEquals(1, count(responseBody, "<D:multistatus xmlns:D=\"DAV:\" xmlns:ns0=\"urn:test:a\">"));
        Assert.assertEquals(4, count(responseBody, "<ns0:p/>"));
        Assert.assertEquals(4, count(responseBody, "<ns0:q/>"));
    }


    @Test
    public void testPropfindNamespacesDeclaredOnce() throws Exception {
        String body = "<D:propfind xmlns:D=\"DAV:\" xmlns:A=\"urn:test:a\" xmlns:B=\"urn:test:b\"><D:prop>" +
                "<A:p/><B:p/><A:q/><D:unknown/></D:prop></D:propfind>";
        Client client = doTestPropfind(null, "0", body, 1);

        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());
        String responseBody = client.getResponseBody();
        Assert.assertEquals(1, count(responseBody, "xmlns:ns0=\"urn:test:a\""));
        Assert.assertEquals(1, count(responseBody, "xmlns:ns1=\"urn:test:b\""));
        Assert.assertEquals(1, count(responseBody, "<ns0:p/>"));
        Assert.assertEquals(1, count(responseBody, "<ns1:p/>"));
        Assert.assertEquals(1, count(responseBody, "<ns0:q/>"));
        Assert.assertEquals(1, count(responseBody, "<D:unknown/>"));
    }


    @Test
    public void testPropfindMaxPropertiesDefault() throws Exception {
        String[] names = new String[100];
        for (int i = 0; i < names.length; i++) {
            names[i] = "p" + i;
        }
        startPropfind(null, 1);
        Client client = sendPropfind("0", propfindBody("urn:test:a", names));
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());

        String[] tooMany = new String[101];
        for (int i = 0; i < tooMany.length; i++) {
            tooMany[i] = "p" + i;
        }
        client = sendPropfind("0", propfindBody("urn:test:a", tooMany));
        Assert.assertEquals(WebdavStatus.SC_REQUEST_TOO_LONG, client.getStatusCode());
    }


    /*
     * Properties that are requested more than once do not count towards the limit.
     */
    @Test
    public void testPropfindMaxPropertiesConfigured() throws Exception {
        startPropfind("2", 1);
        Client client = sendPropfind("0", propfindBody("urn:test:a", "p", "q", "p", "q", "p"));
        Assert.assertEquals(WebdavStatus.SC_MULTI_STATUS, client.getStatusCode());

        client = sendPropfind("0", propfindBody("urn:test:a", "p", "q", "r"));
        Assert.assertEquals(WebdavStatus.SC_REQUEST_TOO_LONG, client.getStatusCode());
    }


    private static String propfindBody(String namespace, String... names) {
        StringBuilder sb = new StringBuilder("<D:propfind xmlns:D=\"DAV:\" xmlns:T=\"");
        sb.append(namespace);
        sb.append("\"><D:prop>");
        for (String name : names) {
            sb.append("<T:").append(name).append("/>");
        }
        sb.append("</D:prop></D:propfind>");
        return sb.toString();
    }


    private static int count(String text, String search) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(search, index)) != -1) {
            count++;
            index += search.length();
        }
        return count;
    }


    private Client doTestPropfind(String maxPropfindProperties, String depth, String body, int files)
            throws Exception {
        startPropfind(maxPropfindProperties, files);
        return sendPropfind(depth, body);
    }


    private void startPropfind(String maxPropfindProperties, int files) throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File tempWebapp = new File(getTemporaryDirectory(), "webdav-propfind" + UUID.randomUUID());
        tempWebapp.deleteOnExit();
        Assert.assertTrue(tempWebapp.mkdirs());
        for (int i = 0; i < files; i++) {
            File file = new File(tempWebapp, "file" + i + ".txt");
            Assert.assertTrue(file.createNewFile());
            file.deleteOnExit();
        }
        Context ctxt = tomcat.addContext("", tempWebapp.getAbsolutePath());
        Wrapper webdavServlet = Tomcat.addServlet(ctxt, "webdav", new WebdavServlet());
        webdavServlet.addInitParameter("listings", "true");
        if (maxPropfindProperties != null) {
            webdavServlet.addInitParameter("maxPropfindProperties", maxPropfindProperties);
        }
        ctxt.addServletMapping("/*", "webdav");
        tomcat.start();
    }


    private Client sendPropfind(String depth, String body) throws Exception {
        Client client = new Client();
        client.setPort(getPort());
        // @formatter:off
        client.setRequest(new String[] {
                "PROPFIND / HTTP/1.1" + CRLF +
                    "Host: localhost:" + getPort() + CRLF +
                    "Depth: " + depth + CRLF +
                    "Content-Length: " + body.length() + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF +
                    body
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        return client;
    }


    private static final class Client extends SimpleHttpClient {

        @Override
        public boolean isResponseBodyOK() {
            return true;
        }
    }

}

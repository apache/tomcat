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
package jakarta.servlet.http;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.collections.CaseInsensitiveKeyMap;
import org.apache.tomcat.util.net.TesterSupport.SimpleServlet;

public class TestHttpServlet extends TomcatBaseTest {

    @Test
    public void testBug53454() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        StandardContext ctx = (StandardContext) tomcat.addContext("", null);

        // Map the test Servlet
        LargeBodyServlet largeBodyServlet = new LargeBodyServlet();
        Tomcat.addServlet(ctx, "largeBodyServlet", largeBodyServlet);
        ctx.addServletMappingDecoded("/", "largeBodyServlet");

        tomcat.start();

        Map<String,List<String>> resHeaders= new HashMap<>();
        int rc = headUrl("http://localhost:" + getPort() + "/", new ByteChunk(),
               resHeaders);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertEquals(LargeBodyServlet.RESPONSE_LENGTH,
                resHeaders.get("Content-Length").get(0));
    }


    private static class LargeBodyServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;
        private static final String RESPONSE_LENGTH = "12345678901";

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setHeader("content-length", RESPONSE_LENGTH);
        }
    }


    /*
     * Verifies that the same Content-Length is returned for both GET and HEAD
     * operations when a Servlet includes content from another Servlet
     */
    @Test
    public void testBug57602() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        StandardContext ctx = (StandardContext) tomcat.addContext("", null);

        Bug57602ServletOuter outer = new Bug57602ServletOuter();
        Tomcat.addServlet(ctx, "Bug57602ServletOuter", outer);
        ctx.addServletMappingDecoded("/outer", "Bug57602ServletOuter");

        Bug57602ServletInner inner = new Bug57602ServletInner();
        Tomcat.addServlet(ctx, "Bug57602ServletInner", inner);
        ctx.addServletMappingDecoded("/inner", "Bug57602ServletInner");

        tomcat.start();

        Map<String,List<String>> resHeaders= new CaseInsensitiveKeyMap<>();
        String path = "http://localhost:" + getPort() + "/outer";
        ByteChunk out = new ByteChunk();

        int rc = getUrl(path, out, resHeaders);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        String length = getSingleHeader("Content-Length", resHeaders);
        Assert.assertEquals(Long.parseLong(length), out.getLength());
        out.recycle();

        rc = headUrl(path, out, resHeaders);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertEquals(0, out.getLength());
        Assert.assertEquals(length, resHeaders.get("Content-Length").get(0));

        tomcat.stop();
    }


    @Test
    public void testHeadWithChunking() throws Exception {
        doTestHead(new ChunkingServlet());
    }


    @Test
    public void testHeadWithResetBufferWriter() throws Exception {
        doTestHead(new ResetBufferServlet(true));
    }


    @Test
    public void testHeadWithResetBufferStream() throws Exception {
        doTestHead(new ResetBufferServlet(false));
    }


    @Test
    public void testHeadWithResetWriter() throws Exception {
        doTestHead(new ResetServlet(true));
    }


    @Test
    public void testHeadWithResetStream() throws Exception {
        doTestHead(new ResetServlet(false));
    }


    @Test
    public void testHeadWithNonBlocking() throws Exception {
        // Less than buffer size
        doTestHead(new NonBlockingWriteServlet(4 * 1024));
    }


    private void doTestHead(Servlet servlet) throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        StandardContext ctx = (StandardContext) tomcat.addContext("", null);

        Wrapper w = Tomcat.addServlet(ctx, "TestServlet", servlet);
        // Not all need/use this but it is simpler to set it for all
        w.setAsyncSupported(true);
        ctx.addServletMappingDecoded("/test", "TestServlet");

        tomcat.start();

        Map<String,List<String>> getHeaders = new CaseInsensitiveKeyMap<>();
        String path = "http://localhost:" + getPort() + "/test";
        ByteChunk out = new ByteChunk();

        int rc = getUrl(path, out, getHeaders);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        out.recycle();

        Map<String,List<String>> headHeaders = new HashMap<>();
        rc = headUrl(path, out, headHeaders);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        // Headers should be the same (apart from Date)
        Assert.assertEquals(getHeaders.size(), headHeaders.size());
        for (Map.Entry<String, List<String>> getHeader : getHeaders.entrySet()) {
            String headerName = getHeader.getKey();
            if ("date".equalsIgnoreCase(headerName)) {
                continue;
            }
            Assert.assertTrue(headerName, headHeaders.containsKey(headerName));
            List<String> getValues = getHeader.getValue();
            List<String> headValues = headHeaders.get(headerName);
            Assert.assertEquals(getValues.size(), headValues.size());
            for (String value : getValues) {
                Assert.assertTrue(headValues.contains(value));
            }
        }

        tomcat.stop();
    }


    @Test
    public void testDoOptions() throws Exception {
        doTestDoOptions(new OptionsServlet(), "GET, HEAD, OPTIONS");
    }


    @Test
    public void testDoOptionsSub() throws Exception {
        doTestDoOptions(new OptionsServletSub(), "GET, HEAD, POST, OPTIONS");
    }


    private void doTestDoOptions(Servlet servlet, String expectedAllow) throws Exception{
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        StandardContext ctx = (StandardContext) tomcat.addContext("", null);

        // Map the test Servlet
        Tomcat.addServlet(ctx, "servlet", servlet);
        ctx.addServletMappingDecoded("/", "servlet");

        tomcat.start();

        Map<String,List<String>> resHeaders= new HashMap<>();
        int rc = methodUrl("http://localhost:" + getPort() + "/", new ByteChunk(),
               DEFAULT_CLIENT_TIMEOUT_MS, null, resHeaders, "OPTIONS");

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertEquals(expectedAllow, resHeaders.get("Allow").get(0));
    }


    @Test
    public void testUnimplementedMethodHttp09() throws Exception {
        doTestUnimplementedMethod("0.9");
    }


    @Test
    public void testUnimplementedMethodHttp10() throws Exception {
        doTestUnimplementedMethod("1.0");
    }


    @Test
    public void testUnimplementedMethodHttp11() throws Exception {
        doTestUnimplementedMethod("1.1");
    }


    /*
     * See org.apache.coyote.http2.TestHttpServlet for the HTTP/2 version of
     * this test. It was placed in that package because it needed access to
     * package private classes.
     */


    private void doTestUnimplementedMethod(String httpVersion) {
        StringBuilder request = new StringBuilder("PUT /test");
        boolean isHttp09 = "0.9".equals(httpVersion);
        boolean isHttp10 = "1.0".equals(httpVersion);

        if (!isHttp09) {
            request.append(" HTTP/");
            request.append(httpVersion);
        }
        request.append(SimpleHttpClient.CRLF);

        request.append("Host: localhost:8080");
        request.append(SimpleHttpClient.CRLF);

        request.append("Connection: close");
        request.append(SimpleHttpClient.CRLF);

        request.append(SimpleHttpClient.CRLF);

        Client client = new Client(request.toString(), "0.9".equals(httpVersion));

        client.doRequest();

        if (isHttp09) {
            Assert.assertTrue( client.getResponseBody(), client.getResponseBody().contains(" 400 "));
        } else if (isHttp10) {
            Assert.assertTrue(client.getResponseLine(), client.isResponse400());
        } else {
            Assert.assertTrue(client.getResponseLine(), client.isResponse405());
        }
    }


    @Test
    public void testTrace() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.getConnector().setAllowTrace(true);

        // No file system docBase required
        StandardContext ctx = (StandardContext) tomcat.addContext("", null);

        // Map the test Servlet
        Tomcat.addServlet(ctx, "servlet", new SimpleServlet());
        ctx.addServletMappingDecoded("/", "servlet");

        tomcat.start();

        TraceClient client = new TraceClient();
        client.setPort(getPort());
        client.setRequest(new String[] {
                "TRACE / HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                "X-aaa: a1, a2" + SimpleHttpClient.CRLF +
                "X-aaa: a3" + SimpleHttpClient.CRLF +
                "Cookie: c1-v1" + SimpleHttpClient.CRLF +
                "Authorization: not-a-real-credential" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF});
        client.setUseContentLength(true);

        client.connect();
        client.sendRequest();
        client.readResponse(true);

        String body = client.getResponseBody();

        System.out.println(body);

        Assert.assertTrue(client.getResponseLine(), client.isResponse200());
        // Far from perfect but good enough
        body = body.toLowerCase(Locale.ENGLISH);
        Assert.assertTrue(body.contains("a1"));
        Assert.assertTrue(body.contains("a2"));
        Assert.assertTrue(body.contains("a3"));
        // Sensitive headers (cookies, WWW-Authenticate) must not be reflected
        // (since RFC 7231)
        Assert.assertFalse(body.contains("cookie"));
        Assert.assertFalse(body.contains("authorization"));

        client.disconnect();
    }


    private static final class TraceClient extends SimpleHttpClient {

        @Override
        public boolean isResponseBodyOK() {
            return true;
        }
    }


    private class Client extends SimpleHttpClient {

        public Client(String request, boolean isHttp09) {
            setRequest(new String[] {request});
            setUseHttp09(isHttp09);
        }

        private Exception doRequest() {

            Tomcat tomcat = getTomcatInstance();

            Context root = tomcat.addContext("", TEMP_DIR);
            Tomcat.addServlet(root, "TesterServlet", new TesterServlet());
            root.addServletMappingDecoded("/test", "TesterServlet");

            try {
                tomcat.start();
                setPort(tomcat.getConnector().getLocalPort());
                setRequestPause(20);

                // Open connection
                connect();

                processRequest(); // blocks until response has been read

                // Close the connection
                disconnect();
            } catch (Exception e) {
                e.printStackTrace();
                return e;
            }
            return null;
        }

        @Override
        public boolean isResponseBodyOK() {
            return false;
        }
    }


    private static class Bug57602ServletOuter extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            PrintWriter pw = resp.getWriter();
            pw.println("Header");
            req.getRequestDispatcher("/inner").include(req, resp);
            pw.println("Footer");
        }
    }


    private static class Bug57602ServletInner extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            PrintWriter pw = resp.getWriter();
            pw.println("Included");
        }
    }


    private static class ChunkingServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            PrintWriter pw = resp.getWriter();
            // Trigger chunking
            pw.write(new char[8192 * 16]);
            pw.println("Data");
        }
    }


    private static class ResetBufferServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final boolean useWriter;

        public ResetBufferServlet(boolean useWriter) {
            this.useWriter = useWriter;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");

            if (useWriter) {
                PrintWriter pw = resp.getWriter();
                pw.write(new char[4 * 1024]);
                resp.resetBuffer();
                pw.write(new char[4 * 1024]);
            } else {
                ServletOutputStream sos = resp.getOutputStream();
                sos.write(new byte [4 * 1024]);
                resp.resetBuffer();
                sos.write(new byte [4 * 1024]);
            }
        }
    }


    private static class ResetServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final boolean useWriter;

        public ResetServlet(boolean useWriter) {
            this.useWriter = useWriter;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");

            if (useWriter) {
                PrintWriter pw = resp.getWriter();
                resp.addHeader("aaa", "bbb");
                pw.write(new char[4 * 1024]);
                resp.resetBuffer();
                resp.addHeader("ccc", "ddd");
                pw.write(new char[4 * 1024]);
            } else {
                ServletOutputStream sos = resp.getOutputStream();
                resp.addHeader("aaa", "bbb");
                sos.write(new byte [4 * 1024]);
                resp.resetBuffer();
                resp.addHeader("ccc", "ddd");
                sos.write(new byte [4 * 1024]);
            }
        }
    }


    private static class NonBlockingWriteServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final int bytesToWrite;

        public NonBlockingWriteServlet(int bytesToWrite) {
            this.bytesToWrite = bytesToWrite;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            AsyncContext ac = req.startAsync(req, resp);
            ac.setTimeout(3000);
            WriteListener wListener = new NonBlockingWriteListener(ac, bytesToWrite);
            resp.getOutputStream().setWriteListener(wListener);
        }

        private static class NonBlockingWriteListener implements WriteListener {

            private final AsyncContext ac;
            private final ServletOutputStream sos;
            private int bytesToWrite;

            public NonBlockingWriteListener(AsyncContext ac, int bytesToWrite) throws IOException {
                this.ac = ac;
                this.sos = ac.getResponse().getOutputStream();
                this.bytesToWrite = bytesToWrite;
            }

            @Override
            public void onWritePossible() throws IOException {
                do {
                    // Write up to 1k a time
                    int bytesThisTime = Math.min(bytesToWrite, 1024);
                    sos.write(new byte[bytesThisTime]);
                    bytesToWrite -= bytesThisTime;
                } while (sos.isReady() && bytesToWrite > 0);

                if (sos.isReady() && bytesToWrite == 0) {
                    ac.complete();
                }
            }

            @Override
            public void onError(Throwable throwable) {
                throwable.printStackTrace();
            }
        }
    }


    private static class OptionsServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            PrintWriter pw = resp.getWriter();
            pw.print("OK");
        }
    }


    private static class OptionsServletSub extends OptionsServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            doGet(req, resp);
        }
    }
}

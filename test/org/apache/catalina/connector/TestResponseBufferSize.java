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
package org.apache.catalina.connector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestResponseBufferSize extends TomcatBaseTest {

    @Test
    public void testDefaultBufferSize() throws Exception {
        startTomcat(-1);

        Assert.assertEquals("8192", get("/buffer"));
        Map<String,List<String>> headers = new HashMap<>();
        Assert.assertEquals(12000, getBody("/body?size=12000", headers).length());
        // The body does not fit in the default buffer so it is not sent with a Content-Length
        Assert.assertNull(headers.get("Content-Length"));
    }


    @Test
    public void testConfiguredBufferSize() throws Exception {
        startTomcat(16384);

        Assert.assertEquals("16384", get("/buffer"));
        Map<String,List<String>> headers = new HashMap<>();
        Assert.assertEquals(12000, getBody("/body?size=12000", headers).length());
        // The body fits in the configured buffer so the length is known when the response is committed
        Assert.assertEquals("12000", headers.get("Content-Length").get(0));
    }


    @Test
    public void testConfiguredSmallerBufferSize() throws Exception {
        startTomcat(1024);

        Assert.assertEquals("1024", get("/buffer"));
        Map<String,List<String>> headers = new HashMap<>();
        Assert.assertEquals(3000, getBody("/body?size=3000", headers).length());
        Assert.assertNull(headers.get("Content-Length"));
    }


    @Test
    public void testSetBufferSizeCanIncreaseTheBuffer() throws Exception {
        startTomcat(16384);

        Assert.assertEquals("65536", get("/setBuffer?size=65536"));
    }


    @Test
    public void testSetBufferSizeDoesNotReduceTheBuffer() throws Exception {
        startTomcat(16384);

        // The buffer is at least the requested size
        Assert.assertEquals("16384", get("/setBuffer?size=100"));
    }


    /*
     * A buffer that is more than 16 times the configured size is discarded when the Response is recycled. This
     * recycles the same Response object, as the connector does between requests on a connection.
     */
    @Test
    public void testRecycleRestoresTheConfiguredBufferSize() throws Exception {
        Connector connector = new Connector("HTTP/1.1");
        ((AbstractHttp11Protocol<?>) connector.getProtocolHandler()).setResponseBufferSize(16384);
        Response response = connector.createResponse(new org.apache.coyote.Response());
        response.setRequest(connector.createRequest(new org.apache.coyote.Request()));

        Assert.assertEquals(16384, response.getBufferSize());

        // Not more than 16 times the configured size, the buffer is kept
        response.setBufferSize(16 * 16384);
        Assert.assertEquals(16 * 16384, response.getBufferSize());
        response.recycle();
        Assert.assertEquals(16 * 16384, response.getBufferSize());

        // More than 16 times the configured size, the buffer is discarded
        response.setBufferSize(300000);
        Assert.assertEquals(300000, response.getBufferSize());
        response.recycle();
        Assert.assertEquals(16384, response.getBufferSize());
    }


    /*
     * The same behaviour through the connector, with a buffer that is larger than 16 times the configured size.
     */
    @Test
    public void testRecycleThroughTheConnector() throws Exception {
        startTomcat(16384);

        Assert.assertEquals("300000", get("/setBuffer?size=300000"));
        Assert.assertEquals("16384", get("/buffer"));
    }


    /*
     * The response buffer has to be able to hold the encoded form of a character, including a surrogate pair.
     */
    @Test
    public void testMultiByteCharactersAreNotLost() throws Exception {
        startTomcat(1024);

        String text = "a\u00e9\u20ac\uD83D\uDE00".repeat(2000);
        Assert.assertEquals(text, getBody("/utf8?n=2000", new HashMap<>()));
    }


    @Test
    public void testFlushBufferCommitsWithoutContentLength() throws Exception {
        startTomcat(16384);

        Map<String,List<String>> headers = new HashMap<>();
        Assert.assertEquals(2000, getBody("/body?size=2000&flush=true", headers).length());
        Assert.assertNull(headers.get("Content-Length"));
    }


    private void startTomcat(int responseBufferSize) throws Exception {
        Tomcat tomcat = getTomcatInstance();
        if (responseBufferSize > 0) {
            ((AbstractHttp11Protocol<?>) tomcat.getConnector().getProtocolHandler())
                    .setResponseBufferSize(responseBufferSize);
        }
        Context ctx = getProgrammaticRootContext();
        Tomcat.addServlet(ctx, "buffer", new BufferServlet());
        ctx.addServletMapping("/buffer", "buffer");
        Tomcat.addServlet(ctx, "setBuffer", new SetBufferServlet());
        ctx.addServletMapping("/setBuffer", "setBuffer");
        Tomcat.addServlet(ctx, "body", new BodyServlet());
        ctx.addServletMapping("/body", "body");
        Tomcat.addServlet(ctx, "utf8", new Utf8Servlet());
        ctx.addServletMapping("/utf8", "utf8");
        tomcat.start();
    }


    private String get(String path) throws IOException {
        return getBody(path, new HashMap<>());
    }


    private String getBody(String path, Map<String,List<String>> headers) throws IOException {
        ByteChunk bc = new ByteChunk();
        bc.setCharset(StandardCharsets.UTF_8);
        int rc = getUrl("http://localhost:" + getPort() + path, bc, headers);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        return bc.toString();
    }


    private static class BufferServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("text/plain");
            resp.getWriter().print(resp.getBufferSize());
        }
    }


    private static class SetBufferServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setBufferSize(Integer.parseInt(req.getParameter("size")));
            resp.setContentType("text/plain");
            resp.getWriter().print(resp.getBufferSize());
        }
    }


    private static class Utf8Servlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("text/plain; charset=UTF-8");
            resp.getWriter().print("a\u00e9\u20ac\uD83D\uDE00".repeat(Integer.parseInt(req.getParameter("n"))));
        }
    }


    private static class BodyServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            int size = Integer.parseInt(req.getParameter("size"));
            resp.setContentType("text/plain");
            resp.getOutputStream().write(new byte[size]);
            if (Boolean.parseBoolean(req.getParameter("flush"))) {
                resp.flushBuffer();
            }
        }
    }
}

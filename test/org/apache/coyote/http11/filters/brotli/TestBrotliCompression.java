/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.coyote.http11.filters.brotli;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.coyote.http11.filters.BrotliEncoder;
import org.apache.tomcat.util.buf.ByteChunk;

/*
 * Tests with a running Tomcat. The responses are decoded with a decoder that is independent of the encoder.
 */
public class TestBrotliCompression extends TomcatBaseTest {

    private static byte[] body(int size) {
        byte[] unit = "{\"name\":\"Tomcat é€😀\",\"value\":12345}\n".getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[size];
        for (int i = 0; i < size; i++) {
            result[i] = unit[i % unit.length];
        }
        return result;
    }


    private static class BodyServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            byte[] body = body(Integer.parseInt(req.getParameter("size")));
            OutputStream os = resp.getOutputStream();
            // Written in pieces, as a servlet that is not buffering would do
            for (int offset = 0; offset < body.length; offset += 5000) {
                os.write(body, offset, Math.min(5000, body.length - offset));
            }
        }
    }


    private static class FlushServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("text/plain");
            OutputStream os = resp.getOutputStream();
            byte[] body = body(40_000);
            for (int i = 0; i < 4; i++) {
                os.write(body, i * 10_000, 10_000);
                resp.flushBuffer();
            }
        }
    }


    private static class AsyncServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setContentType("text/plain");
            AsyncContext ac = req.startAsync();
            ac.start(() -> {
                try {
                    OutputStream os = ac.getResponse().getOutputStream();
                    byte[] body = body(60_000);
                    os.write(body, 0, 30_000);
                    ac.getResponse().flushBuffer();
                    os.write(body, 30_000, 30_000);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                } finally {
                    ac.complete();
                }
            });
        }
    }


    /*
     * Commits the response, which sends the headers, then waits to be told to write the content.
     */
    private static class CommitServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        static volatile CountDownLatch committed = new CountDownLatch(1);
        static volatile CountDownLatch proceed = new CountDownLatch(1);

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("text/plain");
            resp.flushBuffer();
            committed.countDown();
            try {
                Assert.assertTrue(proceed.await(30, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
            resp.getOutputStream().write(varied(300_000));
        }
    }


    // Less repetitive than body(), so that the quality changes the size of the compressed content
    private static byte[] varied(int size) {
        StringBuilder sb = new StringBuilder();
        Random random = new Random(3);
        String[] words = "tomcat brotli quality response header stream window buffer compress server".split(" ");
        while (sb.length() < size) {
            sb.append(words[random.nextInt(words.length)]).append(random.nextInt(10) == 0 ? ". " : " ");
        }
        return sb.substring(0, size).getBytes(StandardCharsets.UTF_8);
    }


    private static class ETagServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setHeader("ETag", "W/\"v1\"");
            if ("W/\"v1\"".equals(req.getHeader("If-None-Match"))) {
                resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                return;
            }
            resp.setContentType("text/plain");
            resp.getOutputStream().write(body(20_000));
        }
    }


    @Before
    public void startTomcat() throws Exception {
        Assume.assumeTrue(BrotliEncoder.isAvailable() && BrotliDecoder.isAvailable());

        Tomcat tomcat = getTomcatInstance();
        Context ctx = getProgrammaticRootContext();
        Tomcat.addServlet(ctx, "body", new BodyServlet());
        ctx.addServletMapping("/body", "body");
        Tomcat.addServlet(ctx, "flush", new FlushServlet());
        ctx.addServletMapping("/flush", "flush");
        Wrapper async = Tomcat.addServlet(ctx, "async", new AsyncServlet());
        async.setAsyncSupported(true);
        ctx.addServletMapping("/async", "async");
        Tomcat.addServlet(ctx, "etag", new ETagServlet());
        ctx.addServletMapping("/etag", "etag");
        Tomcat.addServlet(ctx, "commit", new CommitServlet());
        ctx.addServletMapping("/commit", "commit");

        Assert.assertTrue(tomcat.getConnector().setProperty("compression", "on"));
        Assert.assertTrue(tomcat.getConnector().setProperty("compressionBrotliQuality", "4"));
        tomcat.start();
    }


    private int get(String path, String acceptEncoding, ByteChunk body, Map<String,List<String>> responseHeaders)
            throws IOException {
        Map<String,List<String>> requestHeaders = new HashMap<>();
        if (acceptEncoding != null) {
            requestHeaders.put("Accept-Encoding", List.of(acceptEncoding));
        }
        return getUrl("http://localhost:" + getPort() + path, body, requestHeaders, responseHeaders);
    }


    private static List<String> header(Map<String,List<String>> headers, String name) {
        for (Map.Entry<String,List<String>> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }


    private byte[] getDecoded(String path, String acceptEncoding, String expectedEncoding) throws IOException {
        ByteChunk body = new ByteChunk();
        Map<String,List<String>> responseHeaders = new HashMap<>();
        Assert.assertEquals(HttpServletResponse.SC_OK, get(path, acceptEncoding, body, responseHeaders));
        List<String> contentEncoding = header(responseHeaders, "Content-Encoding");
        if (expectedEncoding == null) {
            Assert.assertNull(contentEncoding);
        } else {
            Assert.assertEquals(List.of(expectedEncoding), contentEncoding);
            Assert.assertEquals(List.of("accept-encoding"), header(responseHeaders, "Vary"));
            // The length of the compressed content is not known
            Assert.assertNull(header(responseHeaders, "Content-Length"));
        }
        byte[] raw = new byte[body.getLength()];
        System.arraycopy(body.getBuffer(), body.getStart(), raw, 0, raw.length);
        if (expectedEncoding == null) {
            return raw;
        } else if ("br".equals(expectedEncoding)) {
            return BrotliDecoder.decode(raw);
        } else {
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                return gzip.readAllBytes();
            }
        }
    }


    @Test
    public void testNegotiation() throws IOException {
        byte[] expected = body(100_000);
        Assert.assertArrayEquals(expected, getDecoded("/body?size=100000", "br", "br"));
        Assert.assertArrayEquals(expected, getDecoded("/body?size=100000", "gzip, br", "br"));
        Assert.assertArrayEquals(expected, getDecoded("/body?size=100000", "gzip, deflate, br", "br"));
        Assert.assertArrayEquals(expected, getDecoded("/body?size=100000", "gzip", "gzip"));
        Assert.assertArrayEquals(expected, getDecoded("/body?size=100000", "br;q=0.5, gzip", "gzip"));
        Assert.assertArrayEquals(expected, getDecoded("/body?size=100000", "br;q=0, gzip", "gzip"));
        Assert.assertArrayEquals(expected, getDecoded("/body?size=100000", "*", "gzip"));
        Assert.assertArrayEquals(expected, getDecoded("/body?size=100000", "identity", null));
        Assert.assertArrayEquals(expected, getDecoded("/body?size=100000", "br;q=0", null));
        Assert.assertArrayEquals(expected, getDecoded("/body?size=100000", "deflate", null));
        Assert.assertArrayEquals(expected, getDecoded("/body?size=100000", null, null));
    }


    @Test
    public void testSizes() throws IOException {
        // Responses that are too small to be compressed are sent as they are
        Assert.assertArrayEquals(body(100), getDecoded("/body?size=100", "br", null));
        for (int size : new int[] { 2048, 2049, 16 * 1024, 16 * 1024 + 1, 1_000_000, 4_000_000 }) {
            Assert.assertArrayEquals("size " + size, body(size), getDecoded("/body?size=" + size, "br", "br"));
        }
    }


    @Test
    public void testResponseIsSmallerThanTheOriginal() throws IOException {
        ByteChunk body = new ByteChunk();
        Assert.assertEquals(HttpServletResponse.SC_OK, get("/body?size=100000", "br", body, new HashMap<>()));
        Assert.assertTrue(Integer.toString(body.getLength()), body.getLength() < 5_000);
    }


    @Test
    public void testFlushedResponse() throws IOException {
        Assert.assertArrayEquals(body(40_000), getDecoded("/flush", "br", "br"));
    }


    @Test
    public void testAsyncResponse() throws IOException {
        Assert.assertArrayEquals(body(60_000), getDecoded("/async", "br", "br"));
    }


    @Test
    public void testHeadRequest() throws IOException {
        ByteChunk body = new ByteChunk();
        Map<String,List<String>> responseHeaders = new HashMap<>();
        Map<String,List<String>> requestHeaders = new HashMap<>();
        requestHeaders.put("Accept-Encoding", List.of("br"));
        Assert.assertEquals(HttpServletResponse.SC_OK,
                methodUrl("http://localhost:" + getPort() + "/body?size=50000", body, 10_000, requestHeaders,
                        responseHeaders, "HEAD"));
        Assert.assertEquals(0, body.getLength());
        // The same headers as GET
        Assert.assertEquals(List.of("br"), header(responseHeaders, "Content-Encoding"));
    }


    @Test
    public void testConditionalRequest() throws IOException {
        ByteChunk body = new ByteChunk();
        Map<String,List<String>> responseHeaders = new HashMap<>();
        Assert.assertEquals(HttpServletResponse.SC_OK, get("/etag", "br", body, responseHeaders));
        Assert.assertEquals(List.of("br"), header(responseHeaders, "Content-Encoding"));
        Assert.assertEquals(List.of("W/\"v1\""), header(responseHeaders, "ETag"));

        Map<String,List<String>> requestHeaders = new HashMap<>();
        requestHeaders.put("Accept-Encoding", List.of("br"));
        requestHeaders.put("If-None-Match", List.of("W/\"v1\""));
        body = new ByteChunk();
        responseHeaders = new HashMap<>();
        Assert.assertEquals(HttpServletResponse.SC_NOT_MODIFIED,
                getUrl("http://localhost:" + getPort() + "/etag", body, requestHeaders, responseHeaders));
        Assert.assertEquals(0, body.getLength());
        Assert.assertNull(header(responseHeaders, "Content-Encoding"));
    }


    @Test
    public void testRepeatedRequestsOnTheSameConnection() throws IOException {
        for (int i = 0; i < 30; i++) {
            int size = 3000 + i * 7919;
            Assert.assertArrayEquals(body(size), getDecoded("/body?size=" + size, i % 3 == 0 ? "gzip" : "br",
                    i % 3 == 0 ? "gzip" : "br"));
            Assert.assertArrayEquals(body(500), getDecoded("/body?size=500", "br", null));
        }
    }


    /*
     * The quality of the compression of a response is the one that was configured when the response was committed, when
     * Brotli was chosen for it. It is not changed by a change to the configuration, e.g. with JMX, before the content
     * is written.
     */
    @Test
    public void testQualityIsFixedWhenTheResponseIsCommitted() throws Exception {
        Connector connector = getTomcatInstance().getConnector();

        // What the content is compressed to with the quality that is configured
        CommitServlet.committed = new CountDownLatch(1);
        CommitServlet.proceed = new CountDownLatch(1);
        CommitServlet.proceed.countDown();
        byte[] expected = getRaw("/commit");
        Assert.assertArrayEquals(varied(300_000), BrotliDecoder.decode(expected));

        for (String changed : new String[] { "11", "0", "-1" }) {
            CommitServlet.committed = new CountDownLatch(1);
            CommitServlet.proceed = new CountDownLatch(1);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<byte[]> response = executor.submit(() -> getRaw("/commit"));
                // The headers have been sent, with the content encoding
                Assert.assertTrue(CommitServlet.committed.await(30, TimeUnit.SECONDS));
                Assert.assertTrue(connector.setProperty("compressionBrotliQuality", changed));
                CommitServlet.proceed.countDown();
                Assert.assertArrayEquals("Quality changed to " + changed, expected, response.get(30, TimeUnit.SECONDS));
            } finally {
                Assert.assertTrue(connector.setProperty("compressionBrotliQuality", "4"));
                executor.shutdownNow();
            }
        }
    }


    private byte[] getRaw(String path) throws IOException {
        ByteChunk body = new ByteChunk();
        Map<String,List<String>> responseHeaders = new HashMap<>();
        Assert.assertEquals(HttpServletResponse.SC_OK, get(path, "br", body, responseHeaders));
        Assert.assertEquals(List.of("br"), header(responseHeaders, "Content-Encoding"));
        byte[] raw = new byte[body.getLength()];
        System.arraycopy(body.getBuffer(), body.getStart(), raw, 0, raw.length);
        return raw;
    }


    @Test
    public void testConcurrentRequests() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                int thread = t;
                results.add(executor.submit(() -> {
                    for (int i = 0; i < 15; i++) {
                        int size = 5000 + thread * 10_007 + i * 3_001;
                        ByteArrayOutputStream expected = new ByteArrayOutputStream();
                        expected.write(body(size));
                        if (!java.util.Arrays.equals(expected.toByteArray(),
                                getDecoded("/body?size=" + size, "br", "br"))) {
                            return Boolean.FALSE;
                        }
                    }
                    return Boolean.TRUE;
                }));
            }
            for (Future<Boolean> result : results) {
                Assert.assertTrue(result.get(60, TimeUnit.SECONDS).booleanValue());
            }
        } finally {
            executor.shutdownNow();
        }
    }
}

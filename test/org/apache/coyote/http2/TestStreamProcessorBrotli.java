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
package org.apache.coyote.http2;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.http11.filters.BrotliEncoder;
import org.apache.tomcat.util.http.Method;

/*
 * The content of the response is checked, with a decoder that is independent of the encoder, by the test that uses a
 * running Tomcat with HTTP/1.1. HTTP/2 uses the same filter so this test checks that it is selected and used.
 */
public class TestStreamProcessorBrotli extends Http2TestBase {

    private static final int SIZE = 64 * 1024;


    private static class BodyServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            OutputStream os = resp.getOutputStream();
            for (int i = 0; i < SIZE; i++) {
                os.write('X');
            }
        }
    }


    private static class VariedServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            // Less repetitive than the other content, so that the quality changes the size of the compressed content
            StringBuilder sb = new StringBuilder();
            Random random = new Random(3);
            String[] words = "tomcat brotli quality response header stream window buffer compress server".split(" ");
            while (sb.length() < 100_000) {
                sb.append(words[random.nextInt(words.length)]).append(random.nextInt(10) == 0 ? ". " : " ");
            }
            resp.getOutputStream().write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
    }


    private String get(String acceptEncoding) throws Exception {
        return get("/body", acceptEncoding, 4);
    }


    private String get(String path, String acceptEncoding, int quality) throws Exception {
        enableHttp2();

        Tomcat tomcat = getTomcatInstance();
        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMapping("/simple", "simple");
        Tomcat.addServlet(ctxt, "body", new BodyServlet());
        ctxt.addServletMapping("/body", "body");
        Tomcat.addServlet(ctxt, "varied", new VariedServlet());
        ctxt.addServletMapping("/varied", "varied");

        Assert.assertTrue(tomcat.getConnector().setProperty("compression", "on"));
        Assert.assertTrue(tomcat.getConnector().setProperty("compressionBrotliQuality", Integer.toString(quality)));

        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();
        // Include the content of the body in the trace
        output.setTraceBody(true);

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        List<Header> headers = new ArrayList<>(5);
        headers.add(new Header(":method", Method.GET));
        headers.add(new Header(":scheme", "http"));
        headers.add(new Header(":path", path));
        headers.add(new Header(":authority", "localhost:" + getPort()));
        if (acceptEncoding != null) {
            headers.add(new Header("accept-encoding", acceptEncoding));
        }

        buildGetRequest(frameHeader, headersPayload, null, headers, 3);
        writeFrame(frameHeader, headersPayload);
        readSimpleGetResponse();

        return output.getTrace();
    }


    // The content of the body is in the trace, a byte at a time. Only the first frame is needed.
    private static String body(String trace) {
        int start = trace.indexOf("3-Body-");
        Assert.assertTrue(trace, start > -1);
        return trace.substring(start + "3-Body-".length());
    }


    private static boolean isGzip(String trace) {
        String body = body(trace);
        return (body.charAt(0) & 0xFF) == 0x1F && (body.charAt(1) & 0xFF) == 0x8B;
    }


    @Test
    public void testBrotli() throws Exception {
        Assume.assumeTrue(BrotliEncoder.isAvailable());

        String trace = get("br");

        Assert.assertTrue(trace, trace.contains("3-Header-[:status]-[200]\n"));
        Assert.assertTrue(trace, trace.contains("3-Header-[vary]-[accept-encoding]\n"));
        Assert.assertTrue(trace, trace.contains("3-Header-[content-encoding]-[br]\n"));
        Assert.assertFalse(trace, trace.contains("3-Header-[content-length]"));
        Assert.assertTrue(trace, trace.contains("3-EndOfStream\n"));
        // The body is a Brotli stream, not a gzip stream. The filter that was used is checked, as the header is not
        // set by the filter. The content is decoded by the tests that use HTTP/1.1.
        Assert.assertFalse(trace, isGzip(trace));
        Assert.assertTrue(trace, body(trace).length() < 200);
    }


    /*
     * The quality that Brotli was chosen with is used. The trace has the first frame of the response, which is different
     * for different qualities. If the quality that is used did not come from the configuration, it would be the same.
     */
    @Test
    public void testConfiguredQualityIsUsed() throws Exception {
        Assume.assumeTrue(BrotliEncoder.isAvailable());

        String fastest = body(get("/varied", "br", 0));
        // A new Tomcat is needed for the next response
        tearDown();
        setUp();
        String smallest = body(get("/varied", "br", 11));

        Assert.assertEquals(fastest.length(), smallest.length());
        Assert.assertNotEquals(fastest, smallest);
    }


    @Test
    public void testGzipIsUsedWhenBrotliIsNotAccepted() throws Exception {
        Assume.assumeTrue(BrotliEncoder.isAvailable());

        String trace = get("gzip");

        Assert.assertTrue(trace, trace.contains("3-Header-[content-encoding]-[gzip]\n"));
        Assert.assertTrue(trace, trace.contains("3-EndOfStream\n"));
        Assert.assertTrue(trace, isGzip(trace));
    }


    @Test
    public void testNoCompressionWhenNothingIsAccepted() throws Exception {
        Assume.assumeTrue(BrotliEncoder.isAvailable());

        String trace = get(null);

        Assert.assertFalse(trace, trace.contains("content-encoding"));
        Assert.assertTrue(trace, body(trace).startsWith("XXXXXXXXXXXXXXXX"));
    }
}

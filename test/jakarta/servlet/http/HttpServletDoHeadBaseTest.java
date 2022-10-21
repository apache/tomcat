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
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.OutputBuffer;
import org.apache.catalina.connector.Response;
import org.apache.catalina.connector.ResponseFacade;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.http2.Http2TestBase;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.collections.CaseInsensitiveKeyMap;
import org.apache.tomcat.util.compat.JreCompat;

/*
 * Split into multiple tests as a single test takes so long it impacts the time
 * of an entire test run.
 */
public class HttpServletDoHeadBaseTest extends Http2TestBase {

    // Tomcat has a minimum output buffer size of 8 * 1024.
    // (8 * 1024) /16 = 512

    private static final String VALID = "** valid data **";
    private static final String INVALID = "* invalid data *";

    protected static final Integer BUFFERS[] = new Integer[] { Integer.valueOf (16), Integer.valueOf(8 * 1024), Integer.valueOf(16 * 1024) };

    protected static final Integer COUNTS[] = new Integer[] { Integer.valueOf(0), Integer.valueOf(1),
            Integer.valueOf(511), Integer.valueOf(512), Integer.valueOf(513),
            Integer.valueOf(1023), Integer.valueOf(1024), Integer.valueOf(1025) };

    @Parameter(2)
    public boolean useLegacy;
    @Parameter(3)
    public int bufferSize;
    @Parameter(4)
    public boolean useWriter;
    @Parameter(5)
    public int invalidWriteCount;
    @Parameter(6)
    public ResetType resetType;
    @Parameter(7)
    public int validWriteCount;
    @Parameter(8)
    public boolean explicitFlush;

    @Test
    public void testDoHead() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        configureAndStartWebApplication();

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
    public void testDoHeadHttp2() throws Exception {
        StringBuilder debug = new StringBuilder();
        try {
            http2Connect();

            // Get request
            byte[] frameHeaderGet = new byte[9];
            ByteBuffer headersPayloadGet = ByteBuffer.allocate(128);
            buildGetRequest(frameHeaderGet, headersPayloadGet, null, 3, "/test");
            writeFrame(frameHeaderGet, headersPayloadGet);

            // Want the headers frame for stream 3
            parser.readFrame();
            while (!output.getTrace().startsWith("3-HeadersStart\n")) {
                debug.append(output.getTrace());
                output.clearTrace();
                parser.readFrame();
            }
            String traceGet = output.getTrace();
            debug.append(output.getTrace());
            output.clearTrace();

            // Head request
            byte[] frameHeaderHead = new byte[9];
            ByteBuffer headersPayloadHead = ByteBuffer.allocate(128);
            buildHeadRequest(frameHeaderHead, headersPayloadHead, 5, "/test");
            writeFrame(frameHeaderHead, headersPayloadHead);

            // Want the headers frame for stream 5
            parser.readFrame();
            while (!output.getTrace().startsWith("5-HeadersStart\n")) {
                debug.append(output.getTrace());
                output.clearTrace();
                parser.readFrame();
            }
            String traceHead = output.getTrace();
            debug.append(output.getTrace());

            String[] getHeaders = traceGet.split("\n");
            String[] headHeaders = traceHead.split("\n");

            int i = 0;
            for (; i < getHeaders.length; i++) {
                // Headers should be the same, ignoring the first character which is the steam ID
                Assert.assertEquals(getHeaders[i] + "\n" + traceGet + traceHead, '3', getHeaders[i].charAt(0));
                Assert.assertEquals(headHeaders[i] + "\n" + traceGet + traceHead, '5', headHeaders[i].charAt(0));
                Assert.assertEquals(traceGet + traceHead, getHeaders[i].substring(1), headHeaders[i].substring(1));
            }

            // Stream 5 should have one more trace entry
            Assert.assertEquals("5-EndOfStream", headHeaders[i]);
        } catch (Exception t) {
            System.out.println(debug.toString());
            throw t;
        }
    }


    @Override
    @SuppressWarnings("removal")
    protected void configureAndStartWebApplication() throws LifecycleException {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        StandardContext ctxt = (StandardContext) tomcat.addContext("", null);

        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");

        HeadTestServlet s = new HeadTestServlet(bufferSize, useWriter, invalidWriteCount, resetType, validWriteCount, explicitFlush);
        Wrapper w = Tomcat.addServlet(ctxt, "HeadTestServlet", s);
        if (useLegacy) {
            w.addInitParameter(HttpServlet.LEGACY_DO_HEAD, "true");
        }
        ctxt.addServletMappingDecoded("/test", "HeadTestServlet");

        tomcat.start();
    }


    private static class HeadTestServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final int bufferSize;
        private final boolean useWriter;
        private final int invalidWriteCount;
        private final ResetType resetType;
        private final int validWriteCount;
        private final boolean explicitFlush;

        public HeadTestServlet(int bufferSize, boolean useWriter, int invalidWriteCount, ResetType resetType,
                int validWriteCount, boolean explicitFlush) {
            this.bufferSize = bufferSize;
            this.useWriter = useWriter;
            this.invalidWriteCount = invalidWriteCount;
            this.resetType = resetType;
            this.validWriteCount = validWriteCount;
            this.explicitFlush = explicitFlush;
        }

        @SuppressWarnings("removal")
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

            int originalBufferSize = resp.getBufferSize();
            int adjustedBufferSize = bufferSize;
            boolean resetBufferSize = false;

            if (Boolean.parseBoolean(getServletConfig().getInitParameter(LEGACY_DO_HEAD)) &&
                    JreCompat.isJre19Available() && "HEAD".equals(req.getMethod()) && useWriter &&
                    resetType != ResetType.NONE) {
                /*
                 * Using legacy HEAD handling with a Writer on Java 19+.
                 * HttpServlet wraps the response. The test is sensitive to
                 * buffer sizes. The size of the buffer HttpServlet uses varies
                 * with Java version. For the tests to pass the number of
                 * characters that can be written before the response is
                 * committed needs to be the same.
                 *
                 * Internally, the Tomcat response buffer defaults to 8192
                 * bytes. When a Writer is used, an additional 8192 byte buffer
                 * is used for char->byte.
                 *
                 * With Java <19, the char->byte buffer used by HttpServlet
                 * processing HEAD requests in legacy mode is created as a 8192
                 * byte buffer which is consistent with the buffer Tomcat uses
                 * internally.
                 *
                 * With Java 19+, the char->byte buffer used by HttpServlet
                 * processing HEAD requests in legacy mode is created as a 512
                 * byte buffer.
                 *
                 * If the response isn't reset, none of this matters as it is
                 * just the size of the response buffer and the size of the
                 * response that determines if chunked encoding is used.
                 * However, if the response is reset then things get interesting
                 * as the amount of response data that can be written before
                 * committing the response is the combination of the char->byte
                 * buffer and the response buffer. Because the char->byte buffer
                 * size in legacy mode varies with Java version, the size of the
                 * response buffer needs to be adjusted to compensate so that
                 * both GET and HEAD can write the same amount of data before
                 * committing the response. To make matters worse, to obtain the
                 * correct behaviour the response buffer size needs to be reset
                 * back to 8192 after the reset.
                 *
                 * This is why the legacy mode is problematic and the new
                 * approach of the container handling HEAD is better.
                 */

                // Response buffer is always no smaller than originalBufferSize
                if (adjustedBufferSize < originalBufferSize) {
                    adjustedBufferSize = originalBufferSize;
                }

                // Adjust for smaller initial char -> byte buffer in Java 19+
                // originalBufferSize initial size in Java <19
                // 512 initial size in Java 19+
                adjustedBufferSize = adjustedBufferSize + originalBufferSize - 512;

                resetBufferSize = true;
            }

            resp.setBufferSize(adjustedBufferSize);

            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");

            PrintWriter pw = null;
            OutputStream os = null;
            // Do this rather than repeated calls to getWriter() /
            // getOutputStream() to ensure that HEAD handling doesn't rely on
            // replacing the OutputStream / PrintWriter (an earlier
            // implementation did rely on this)
            if (useWriter) {
                pw = resp.getWriter();
            } else {
                os = resp.getOutputStream();
            }

            for (int i = 0; i < invalidWriteCount; i++) {
                write(INVALID, pw, os);
            }

            try {
                switch (resetType) {
                    case NONE: {
                        break;
                    }
                    case BUFFER: {
                        resp.resetBuffer();
                        if (resetBufferSize) {
                            resetResponseBuffer(resp, originalBufferSize);
                        }
                        break;
                    }
                    case FULL: {
                        resp.reset();
                        if (resetBufferSize) {
                            resetResponseBuffer(resp, originalBufferSize);
                        }
                        break;
                    }
                }
            } catch (IllegalStateException ise) {
                write("\nIllegalStateException\n", pw, os);
            }

            for (int i = 0; i < validWriteCount; i++) {
                write(VALID, pw, os);
            }

            if (explicitFlush) {
                resp.flushBuffer();
            }
        }

        private void write(String msg, PrintWriter pw, OutputStream os) throws IOException {
            if (useWriter) {
                pw.print(msg);
            } else {
                os.write(msg.getBytes(StandardCharsets.UTF_8));
            }
        }

        private void resetResponseBuffer(HttpServletResponse resp, int size) throws ServletException {
            // This bypasses various checks but that is OK in this case.
            try {
                ResponseFacade responseFacade = (ResponseFacade) ((HttpServletResponseWrapper) resp).getResponse();

                Field responseField = ResponseFacade.class.getDeclaredField("response");
                responseField.setAccessible(true);
                Response response = (Response) responseField.get(responseFacade);

                Field outputBufferField = Response.class.getDeclaredField("outputBuffer");
                outputBufferField.setAccessible(true);
                OutputBuffer outputBuffer = (OutputBuffer) outputBufferField.get(response);

                Field byteBufferField = OutputBuffer.class.getDeclaredField("bb");
                byteBufferField.setAccessible(true);
                byteBufferField.set(outputBuffer, ByteBuffer.allocate(Math.max(size, bufferSize)));
            } catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
                throw new ServletException(e);
            }
        }
    }


    static enum ResetType {
        NONE,
        BUFFER,
        FULL
    }
}

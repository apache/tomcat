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
package org.apache.catalina.nonblocking;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.SocketFactory;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.AsyncContextImpl;
import org.apache.catalina.startup.BytesStreamer;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.valves.TesterAccessLogValve;
import org.apache.coyote.Request;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestNonBlockingAPI extends TomcatBaseTest {

    private static final Log log = LogFactory.getLog(TestNonBlockingAPI.class);

    private static String TRAILER_HEADER_NAME = "x-test";
    private static String TRAILER_HEADER_VALUE = "abcde";

    private static final int CHUNK_SIZE = 1024 * 1024;
    private static final int WRITE_SIZE  = CHUNK_SIZE * 10;
    private static final byte[] DATA = new byte[WRITE_SIZE];
    private static final int WRITE_PAUSE_MS = 500;

    private static final Field CTX_REQUEST_FIELD;

    static {
        // Use this sequence for padding to make it easier to spot errors
        byte[] padding = new byte[] {'z', 'y', 'x', 'w', 'v', 'u', 't', 's',
                'r', 'q', 'p', 'o', 'n', 'm', 'l', 'k'};
        int blockSize = padding.length;

        for (int i = 0; i < WRITE_SIZE / blockSize; i++) {
            String hex = String.format("%01X", Integer.valueOf(i));
            int hexSize = hex.length();
            int padSize = blockSize - hexSize;

            System.arraycopy(padding, 0, DATA, i * blockSize, padSize);
            System.arraycopy(
                    hex.getBytes(), 0, DATA, i * blockSize + padSize, hexSize);
        }

        Field f = null;
        try {
            f = AsyncContextImpl.class.getDeclaredField("request");
            f.setAccessible(true);
        } catch (NoSuchFieldException | SecurityException e) {
            Assert.fail();
        }
        CTX_REQUEST_FIELD = f;
    }


    @Test
    public void testNonBlockingRead() throws Exception {
        doTestNonBlockingRead(false, false);
    }


    @Test
    public void testNonBlockingReadAsync() throws Exception {
        doTestNonBlockingRead(false, true);
    }


    @Test(expected=IOException.class)
    public void testNonBlockingReadIgnoreIsReady() throws Exception {
        doTestNonBlockingRead(true, false);
    }


    private void doTestNonBlockingRead(boolean ignoreIsReady, boolean async) throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        NBReadServlet servlet = new NBReadServlet(ignoreIsReady, async, null);
        String servletName = NBReadServlet.class.getName();
        Tomcat.addServlet(ctx, servletName, servlet);
        ctx.addServletMappingDecoded("/", servletName);

        tomcat.start();

        Map<String, List<String>> reqHeaders = new HashMap<>();
        int rc = postUrl(true, new DataWriter(async ? 0 : 500, async ? 2000000 : 5),
                "http://localhost:" + getPort() + "/", new ByteChunk(), reqHeaders, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        if (async) {
            Assert.assertEquals(2000000 * 8, servlet.listener.body.length());
            TestAsyncReadListener listener = (TestAsyncReadListener) servlet.listener;
            Assert.assertTrue(Math.abs(listener.containerThreadCount.get() - listener.notReadyCount.get())  <= 1);
            Assert.assertEquals(listener.isReadyCount.get(), listener.nonContainerThreadCount.get());
        } else {
            Assert.assertEquals(5 * 8, servlet.listener.body.length());
        }
    }


    @Test
    public void testNonBlockingReadChunkedNoSplits() throws Exception {
        String[] requestBody = new String[] {
                "14" + SimpleHttpClient.CRLF +
                "012345678901FINISHED" + SimpleHttpClient.CRLF +
                "0" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF};

        doTestNonBlockingReadChunked(requestBody);
    }


    @Test
    public void testNonBlockingReadChunkedSplitBeforeChunkHeader() throws Exception {
        String[] requestBody = new String[] {
                "",
                "14" + SimpleHttpClient.CRLF +
                "012345678901FINISHED" + SimpleHttpClient.CRLF +
                "0" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF};

        doTestNonBlockingReadChunked(requestBody);
    }


    @Test
    public void testNonBlockingReadChunkedSplitInChunkHeader() throws Exception {
        String[] requestBody = new String[] {
                "1",
                "4" + SimpleHttpClient.CRLF +
                "012345678901FINISHED" + SimpleHttpClient.CRLF +
                "0" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF};

        doTestNonBlockingReadChunked(requestBody);
    }


    @Test
    public void testNonBlockingReadChunkedSplitAfterChunkHeader() throws Exception {
        String[] requestBody = new String[] {
                "14",
                SimpleHttpClient.CRLF +
                "012345678901FINISHED" + SimpleHttpClient.CRLF +
                "0" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF};

        doTestNonBlockingReadChunked(requestBody);
    }


    @Test
    public void testNonBlockingReadChunkedSplitInHeaderCrlf() throws Exception {
        String[] requestBody = new String[] {
                "14\r",
                "\n" +
                "012345678901FINISHED" + SimpleHttpClient.CRLF +
                "0" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF};

        doTestNonBlockingReadChunked(requestBody);
    }


    @Test
    public void testNonBlockingReadChunkedSplitAfterHeaderCrlf() throws Exception {
        String[] requestBody = new String[] {
                "14" + SimpleHttpClient.CRLF,
                "012345678901FINISHED" + SimpleHttpClient.CRLF +
                "0" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF};

        doTestNonBlockingReadChunked(requestBody);
    }


    @Test
    public void testNonBlockingReadChunkedSplitBeforeExtensionDelimter() throws Exception {
        String[] requestBody = new String[] {
                "14",
                ";a=b" + SimpleHttpClient.CRLF +
                "012345678901FINISHED" + SimpleHttpClient.CRLF +
                "0" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF};

        doTestNonBlockingReadChunked(requestBody);
    }


    @Test
    public void testNonBlockingReadChunkedSplitAfterExtensionDelimter() throws Exception {
        String[] requestBody = new String[] {
                "14;",
                "a=b" + SimpleHttpClient.CRLF +
                "012345678901FINISHED" + SimpleHttpClient.CRLF +
                "0" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF};

        doTestNonBlockingReadChunked(requestBody);
    }


    @Test
    public void testNonBlockingReadChunkedSplitInExtension() throws Exception {
        String[] requestBody = new String[] {
                "14;a",
                "=b" + SimpleHttpClient.CRLF +
                "012345678901FINISHED" + SimpleHttpClient.CRLF +
                "0" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF};

        doTestNonBlockingReadChunked(requestBody);
    }


    @Test
    public void testNonBlockingReadChunkedSplitAfterExtension() throws Exception {
        String[] requestBody = new String[] {
                "14;a=b",
                SimpleHttpClient.CRLF +
                "012345678901FINISHED" + SimpleHttpClient.CRLF +
                "0" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF};

        doTestNonBlockingReadChunked(requestBody);
    }


    @Test
    public void testNonBlockingReadChunkedSplitInChunkBody() throws Exception {
        String[] requestBody = new String[] {
                "14" + SimpleHttpClient.CRLF +
                "012345",
                "678901FINISHED" + SimpleHttpClient.CRLF +
                "0" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF};

        doTestNonBlockingReadChunked(requestBody);
    }


    @Test
    public void testNonBlockingReadChunkedSplitBeforeChunkBodyCrlf() throws Exception {
        String[] requestBody = new String[] {
                "14" + SimpleHttpClient.CRLF +
                "012345678901FINISHED",
                SimpleHttpClient.CRLF +
                "0" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF};

        doTestNonBlockingReadChunked(requestBody);
    }


    @Test
    public void testNonBlockingReadChunkedSplitInChunkBodyCrlf() throws Exception {
        String[] requestBody = new String[] {
                "14" + SimpleHttpClient.CRLF +
                "012345678901FINISHED\r",
                "\n" +
                "0" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF};

        doTestNonBlockingReadChunked(requestBody);
    }


    @Test
    public void testNonBlockingReadChunkedSplitAfterChunkBodyCrlf() throws Exception {
        String[] requestBody = new String[] {
                "14" + SimpleHttpClient.CRLF +
                "012345678901FINISHED" + SimpleHttpClient.CRLF,
                "0" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF};

        doTestNonBlockingReadChunked(requestBody);
    }


    @Test
    public void testNonBlockingReadChunkedSplitBeforeEndChunkCrlf() throws Exception {
        String[] requestBody = new String[] {
                "14" + SimpleHttpClient.CRLF +
                "012345678901FINISHED" + SimpleHttpClient.CRLF +
                "0",
                SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF};

        doTestNonBlockingReadChunked(requestBody);
    }


    @Test
    public void testNonBlockingReadChunkedSplitInEndChunkCrlf() throws Exception {
        String[] requestBody = new String[] {
                "14" + SimpleHttpClient.CRLF +
                "012345678901FINISHED" + SimpleHttpClient.CRLF +
                "0" +
                "\r",
                "\n" +
                SimpleHttpClient.CRLF};

        doTestNonBlockingReadChunked(requestBody);
    }


    @Test
    public void testNonBlockingReadChunkedSplitAfterEndChunkCrlf() throws Exception {
        String[] requestBody = new String[] {
                "14" + SimpleHttpClient.CRLF +
                "012345678901FINISHED" + SimpleHttpClient.CRLF +
                "0" +
                SimpleHttpClient.CRLF,
                SimpleHttpClient.CRLF};

        doTestNonBlockingReadChunked(requestBody);
    }


    @Test
    public void testNonBlockingReadChunkedSplitBeforeTrailer() throws Exception {
        String[] requestBody = new String[] {
                "14" + SimpleHttpClient.CRLF +
                "012345678901FINISHED" + SimpleHttpClient.CRLF +
                "0" + SimpleHttpClient.CRLF,
                TRAILER_HEADER_NAME + ": " + TRAILER_HEADER_VALUE + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF};

        doTestNonBlockingReadChunked(requestBody, TRAILER_HEADER_VALUE);
    }


    @Test
    public void testNonBlockingReadChunkedSplitInTrailerName() throws Exception {
        String[] requestBody = new String[] {
                "14" + SimpleHttpClient.CRLF +
                "012345678901FINISHED" + SimpleHttpClient.CRLF +
                "0" + SimpleHttpClient.CRLF +
                "x-te",
                "st" + ": " + TRAILER_HEADER_VALUE + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF};

        doTestNonBlockingReadChunked(requestBody, TRAILER_HEADER_VALUE);
    }


    @Test
    public void testNonBlockingReadChunkedSplitAfterTrailerName() throws Exception {
        String[] requestBody = new String[] {
                "14" + SimpleHttpClient.CRLF +
                "012345678901FINISHED" + SimpleHttpClient.CRLF +
                "0" + SimpleHttpClient.CRLF +
                TRAILER_HEADER_NAME,
                ": " + TRAILER_HEADER_VALUE + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF};

        doTestNonBlockingReadChunked(requestBody, TRAILER_HEADER_VALUE);
    }


    @Test
    public void testNonBlockingReadChunkedSplitAfterTrailerDelimter() throws Exception {
        String[] requestBody = new String[] {
                "14" + SimpleHttpClient.CRLF +
                "012345678901FINISHED" + SimpleHttpClient.CRLF +
                "0" + SimpleHttpClient.CRLF +
                TRAILER_HEADER_NAME + ":",
                " " + TRAILER_HEADER_VALUE + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF};

        doTestNonBlockingReadChunked(requestBody, TRAILER_HEADER_VALUE);
    }


    @Test
    public void testNonBlockingReadChunkedSplitBeforeTrailerValue() throws Exception {
        String[] requestBody = new String[] {
                "14" + SimpleHttpClient.CRLF +
                "012345678901FINISHED" + SimpleHttpClient.CRLF +
                "0" + SimpleHttpClient.CRLF +
                TRAILER_HEADER_NAME + ": ",
                TRAILER_HEADER_VALUE + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF};

        doTestNonBlockingReadChunked(requestBody, TRAILER_HEADER_VALUE);
    }


    @Test
    public void testNonBlockingReadChunkedSplitInTrailerValue() throws Exception {
        String[] requestBody = new String[] {
                "14" + SimpleHttpClient.CRLF +
                "012345678901FINISHED" + SimpleHttpClient.CRLF +
                "0" + SimpleHttpClient.CRLF +
                TRAILER_HEADER_NAME + ": abc",
                "de" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF};

        doTestNonBlockingReadChunked(requestBody, TRAILER_HEADER_VALUE);
    }


    @Test
    public void testNonBlockingReadChunkedSplitAfterTrailerValue() throws Exception {
        String[] requestBody = new String[] {
                "14" + SimpleHttpClient.CRLF +
                "012345678901FINISHED" + SimpleHttpClient.CRLF +
                "0" + SimpleHttpClient.CRLF +
                TRAILER_HEADER_NAME + ": " + TRAILER_HEADER_VALUE,
                SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF};

        doTestNonBlockingReadChunked(requestBody, TRAILER_HEADER_VALUE);
    }


    @Test
    public void testNonBlockingReadChunkedSplitInTrailerCrlf() throws Exception {
        String[] requestBody = new String[] {
                "14" + SimpleHttpClient.CRLF +
                "012345678901FINISHED" + SimpleHttpClient.CRLF +
                "0" + SimpleHttpClient.CRLF +
                TRAILER_HEADER_NAME + ": " + TRAILER_HEADER_VALUE + "\r",
                "\n" +
                SimpleHttpClient.CRLF};

        doTestNonBlockingReadChunked(requestBody, TRAILER_HEADER_VALUE);
    }


    @Test
    public void testNonBlockingReadChunkedSplitAfterTrailerCrlf() throws Exception {
        String[] requestBody = new String[] {
                "14" + SimpleHttpClient.CRLF +
                "012345678901FINISHED" + SimpleHttpClient.CRLF +
                "0" + SimpleHttpClient.CRLF +
                TRAILER_HEADER_NAME + ": " + TRAILER_HEADER_VALUE + SimpleHttpClient.CRLF,
                SimpleHttpClient.CRLF};

        doTestNonBlockingReadChunked(requestBody, TRAILER_HEADER_VALUE);
    }


    @Test
    public void testNonBlockingReadChunkedSplitInFinalCrlf() throws Exception {
        String[] requestBody = new String[] {
                "14" + SimpleHttpClient.CRLF +
                "012345678901FINISHED" + SimpleHttpClient.CRLF +
                "0" +
                SimpleHttpClient.CRLF +
                "\r",
                "\n"};

        doTestNonBlockingReadChunked(requestBody);
    }


    @Test
    public void testNonBlockingReadChunkedSplitMaximum() throws Exception {
        String requestBody = new String(
                "14" + SimpleHttpClient.CRLF +
                "012345678901FINISHED" + SimpleHttpClient.CRLF +
                "0" + SimpleHttpClient.CRLF +
                TRAILER_HEADER_NAME + ": " + TRAILER_HEADER_VALUE + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF);

        String[] requestBodySplit = new String[requestBody.length()];
        for (int i = 0; i < requestBody.length(); i++) {
            requestBodySplit[i] = Character.toString(requestBody.charAt(i));
        }

        doTestNonBlockingReadChunked(requestBodySplit, TRAILER_HEADER_VALUE);
    }


    private void doTestNonBlockingReadChunked(String[] requestBody) throws Exception {
        doTestNonBlockingReadChunked(requestBody, null);
    }


    private void doTestNonBlockingReadChunked(String[] requestBody, String expectedTrailerFieldValue) throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        NBReadServlet servlet = new NBReadServlet(false, true, expectedTrailerFieldValue);
        String servletName = NBReadServlet.class.getName();
        Tomcat.addServlet(ctx, servletName, servlet);
        ctx.addServletMappingDecoded("/", servletName);

        tomcat.getConnector().setProperty("allowedTrailerHeaders", TRAILER_HEADER_NAME);

        tomcat.start();

        // Add the headers to the first part of the chunked body
        requestBody[0] =
                "GET / HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost" + getPort() + SimpleHttpClient.CRLF +
                "Transfer-Encoding: chunked" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF +
                requestBody[0];

        Client client = new Client();
        client.setPort(getPort());
        client.setRequest(requestBody);
        /*
         *  Reduce default pause to speed up test execution. Pause only needs to be long enough that each part of the
         *  request is read separately.
         */
        client.setRequestPause(200);
        client.connect();
        client.sendRequest();

        client.setUseContentLength(true);
        client.readResponse(true);

        Assert.assertTrue(client.isResponse200());
        Assert.assertTrue(client.isResponseBodyOK());
    }


    private static class Client extends SimpleHttpClient {
        @Override
        public boolean isResponseBodyOK() {
            return "OK".equals(getResponseBody());
        }
    }


    @Test
    public void testNonBlockingWrite() throws Exception {
        testNonBlockingWriteInternal(false);
    }

    @Test
    public void testNonBlockingWriteWithKeepAlive() throws Exception {
        testNonBlockingWriteInternal(true);
    }

    private void testNonBlockingWriteInternal(boolean keepAlive) throws Exception {
        AtomicBoolean asyncContextIsComplete = new AtomicBoolean(false);

        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        NBWriteServlet servlet = new NBWriteServlet(asyncContextIsComplete);
        String servletName = NBWriteServlet.class.getName();
        Tomcat.addServlet(ctx, servletName, servlet);
        ctx.addServletMappingDecoded("/", servletName);
        // Note: Low values of socket.txBufSize can trigger very poor
        //       performance. Set it just low enough to ensure that the
        //       non-blocking write servlet will see isReady() == false
        Assert.assertTrue(tomcat.getConnector().setProperty("socket.txBufSize", "1048576"));
        tomcat.start();

        SocketFactory factory = SocketFactory.getDefault();
        Socket s = factory.createSocket("localhost", getPort());

        InputStream is = s.getInputStream();
        byte[] buffer = new byte[8192];

        ByteChunk result = new ByteChunk();

        OutputStream os = s.getOutputStream();
        if (keepAlive) {
            os.write(("OPTIONS * HTTP/1.1\r\n" +
                    "Host: localhost:" + getPort() + "\r\n" +
                    "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            os.flush();
            // Make sure the entire response has been read.
            int read = is.read(buffer);
            // The response should end with CRLFCRLF
            Assert.assertEquals(buffer[read - 4], '\r');
            Assert.assertEquals(buffer[read - 3], '\n');
            Assert.assertEquals(buffer[read - 2], '\r');
            Assert.assertEquals(buffer[read - 1], '\n');
        }
        os.write(("GET / HTTP/1.1\r\n" +
                "Host: localhost:" + getPort() + "\r\n" +
                "Connection: close\r\n" +
                "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        os.flush();

        int read = 0;
        int readSinceLastPause = 0;
        while (read != -1) {
            read = is.read(buffer);
            if (readSinceLastPause == 0) {
                log.info("Reading data");
            }
            if (read > 0) {
                result.append(buffer, 0, read);
            }
            readSinceLastPause += read;
            if (readSinceLastPause > WRITE_SIZE / 16) {
                log.info("Read " + readSinceLastPause + " bytes, pause 500ms");
                readSinceLastPause = 0;
                Thread.sleep(500);
            }
        }

        os.close();
        is.close();
        s.close();

        // Validate the result.
        // Response line
        String resultString = result.toString();
        log.info("Client read " + resultString.length() + " bytes");
        int lineStart = 0;
        int lineEnd = resultString.indexOf('\n', 0);
        String line = resultString.substring(lineStart, lineEnd + 1);
        Assert.assertEquals("HTTP/1.1 200 \r\n", line);

        // Check headers - looking to see if response is chunked (it should be)
        boolean chunked = false;
        while (line.length() > 2) {
            lineStart = lineEnd + 1;
            lineEnd = resultString.indexOf('\n', lineStart);
            line = resultString.substring(lineStart, lineEnd + 1);
            if (line.startsWith("Transfer-Encoding:")) {
                Assert.assertEquals("Transfer-Encoding: chunked\r\n", line);
                chunked = true;
            }
        }
        Assert.assertTrue(chunked);

        // Now check body size
        int totalBodyRead = 0;
        int chunkSize = -1;

        while (chunkSize != 0) {
            // Chunk size in hex
            lineStart = lineEnd + 1;
            lineEnd = resultString.indexOf('\n', lineStart);
            line = resultString.substring(lineStart, lineEnd + 1);
            Assert.assertTrue(line.endsWith("\r\n"));
            line = line.substring(0, line.length() - 2);
            log.info("[" + line + "]");
            chunkSize = Integer.parseInt(line, 16);

            // Read the chunk
            lineStart = lineEnd + 1;
            lineEnd = resultString.indexOf('\n', lineStart);
            log.info("Start : "  + lineStart + ", End: " + lineEnd);
            if (lineEnd > lineStart) {
                line = resultString.substring(lineStart, lineEnd + 1);
            } else {
                line = resultString.substring(lineStart);
            }
            if (line.length() > 40) {
                log.info(line.substring(0, 32));
            } else {
                log.info(line);
            }
            if (chunkSize + 2 != line.length()) {
                log.error("Chunk wrong length. Was " + line.length() +
                        " Expected " + (chunkSize + 2));

                byte[] resultBytes = resultString.getBytes();

                // Find error
                boolean found = false;
                for (int i = totalBodyRead; i < (totalBodyRead + line.length()); i++) {
                    if (DATA[i] != resultBytes[lineStart + i - totalBodyRead]) {
                        int dataStart = i - 64;
                        if (dataStart < 0) {
                            dataStart = 0;
                        }
                        int dataEnd = i + 64;
                        if (dataEnd > DATA.length) {
                            dataEnd = DATA.length;
                        }
                        int resultStart = lineStart + i - totalBodyRead - 64;
                        if (resultStart < 0) {
                            resultStart = 0;
                        }
                        int resultEnd = lineStart + i - totalBodyRead + 64;
                        if (resultEnd > resultString.length()) {
                            resultEnd = resultString.length();
                        }
                        log.error("Mismatch tx: " + new String(
                                DATA, dataStart, dataEnd - dataStart));
                        log.error("Mismatch rx: " +
                                resultString.substring(resultStart, resultEnd));
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    log.error("No mismatch. Data truncated");
                }
            }

            Assert.assertTrue(line, line.endsWith("\r\n"));
            Assert.assertEquals(chunkSize + 2, line.length());

            totalBodyRead += chunkSize;
        }

        Assert.assertEquals(WRITE_SIZE, totalBodyRead);
        Assert.assertTrue("AsyncContext should have been completed.", asyncContextIsComplete.get());
    }


    @Test
    public void testNonBlockingWriteError01ListenerComplete() throws Exception {
        doTestNonBlockingWriteError01NoListenerComplete(true);
    }


    @Test
    public void testNonBlockingWriteError01NoListenerComplete() throws Exception {
        doTestNonBlockingWriteError01NoListenerComplete(false);
    }


    private void doTestNonBlockingWriteError01NoListenerComplete(boolean listenerCompletesOnError) throws Exception {
        AtomicBoolean asyncContextIsComplete = new AtomicBoolean(false);

        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        TesterAccessLogValve alv = new TesterAccessLogValve();
        ctx.getPipeline().addValve(alv);

        // Some CI platforms appear to have particularly large write buffers
        // and appear to ignore the socket.txBufSize below. Therefore, configure
        // configure the Servlet to keep writing until an error is encountered.
        NBWriteServlet servlet = new NBWriteServlet(asyncContextIsComplete, true, listenerCompletesOnError);
        String servletName = NBWriteServlet.class.getName();
        Tomcat.addServlet(ctx, servletName, servlet);
        ctx.addServletMappingDecoded("/", servletName);
        // Note: Low values of socket.txBufSize can trigger very poor
        //       performance. Set it just low enough to ensure that the
        //       non-blocking write servlet will see isReady() == false
        Assert.assertTrue(tomcat.getConnector().setProperty("socket.txBufSize", "524228"));
        tomcat.start();

        SocketFactory factory = SocketFactory.getDefault();
        Socket s = factory.createSocket("localhost", getPort());

        ByteChunk result = new ByteChunk();
        OutputStream os = s.getOutputStream();
        os.write(("GET / HTTP/1.1\r\n" +
                "Host: localhost:" + getPort() + "\r\n" +
                "Connection: close\r\n" +
                "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        os.flush();

        InputStream is = s.getInputStream();
        byte[] buffer = new byte[8192];

        int read = 0;
        int readSinceLastPause = 0;
        int readTotal = 0;
        while (read != -1 && readTotal < WRITE_SIZE / 32) {
            long start = System.currentTimeMillis();
            read = is.read(buffer);
            long end = System.currentTimeMillis();
            log.info("Client read [" + read + "] bytes in [" + (end - start) +
                    "] ms");
            if (read > 0) {
                result.append(buffer, 0, read);
            }
            readSinceLastPause += read;
            readTotal += read;
            if (readSinceLastPause > WRITE_SIZE / 64) {
                readSinceLastPause = 0;
                Thread.sleep(WRITE_PAUSE_MS);
            }
        }

        os.close();
        is.close();
        s.close();

        String resultString = result.toString();
        log.info("Client read " + resultString.length() + " bytes");
        int lineStart = 0;
        int lineEnd = resultString.indexOf('\n', 0);
        String line = resultString.substring(lineStart, lineEnd + 1);
        Assert.assertEquals("HTTP/1.1 200 \r\n", line);

        // Listeners are invoked and access valve entries created on a different
        // thread so give that thread a chance to complete its work.
        int count = 0;
        while (count < 100 && !servlet.wlistener.onErrorInvoked) {
            Thread.sleep(100);
            count ++;
        }

        while (count < 100 && !asyncContextIsComplete.get()) {
            Thread.sleep(100);
            count ++;
        }

        while (count < 100 && alv.getEntryCount() < 1) {
            Thread.sleep(100);
            count ++;
        }

        Assert.assertTrue("Error listener should have been invoked.", servlet.wlistener.onErrorInvoked);
        Assert.assertTrue("Async context should have been completed.", asyncContextIsComplete.get());

        // TODO Figure out why non-blocking writes with the NIO connector appear
        // to be slower on Linux
        alv.validateAccessLog(1, 500, WRITE_PAUSE_MS,
                WRITE_PAUSE_MS + 30 * 1000);
    }


    @Test
    public void testBug55438NonBlockingReadWriteEmptyRead() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        NBReadWriteServlet servlet = new NBReadWriteServlet();
        String servletName = NBReadWriteServlet.class.getName();
        Tomcat.addServlet(ctx, servletName, servlet);
        ctx.addServletMappingDecoded("/", servletName);

        tomcat.start();

        Map<String, List<String>> resHeaders = new HashMap<>();
        int rc = postUrl(false, new BytesStreamer() {
            @Override
            public byte[] next() {
                return new byte[] {};
            }

            @Override
            public int getLength() {
                return 0;
            }

            @Override
            public int available() {
                return 0;
            }
        }, "http://localhost:" +
                getPort() + "/", new ByteChunk(), resHeaders, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
    }


    public static class DataWriter implements BytesStreamer {
        int max = 5;
        int count = 0;
        long delay = 0;
        byte[] b = "WANTMORE".getBytes(StandardCharsets.ISO_8859_1);
        byte[] f = "FINISHED".getBytes(StandardCharsets.ISO_8859_1);

        public DataWriter(long delay, int max) {
            this.delay = delay;
            this.max = max;
        }

        @Override
        public int getLength() {
            return b.length * max;
        }

        @Override
        public int available() {
            if (count < max) {
                return b.length;
            } else {
                return 0;
            }
        }

        @Override
        public byte[] next() {
            if (count < max) {
                if (count > 0) {
                  try {
                      if (delay > 0) {
                        Thread.sleep(delay);
                      }
                  } catch (Exception x) {
                  }
                }
                count++;
                if (count < max) {
                  return b;
                } else {
                  return f;
                }
            } else {
                return null;
            }
        }

    }

    @WebServlet(asyncSupported = true)
    public static class NBReadServlet extends TesterServlet {
        private static final long serialVersionUID = 1L;
        private final boolean async;
        private final boolean ignoreIsReady;
        private final String expectedTrailerFieldValue;
        transient TestReadListener listener;

        public NBReadServlet(boolean ignoreIsReady, boolean async, String expectedTrailerFieldValue) {
            this.async = async;
            this.ignoreIsReady = ignoreIsReady;
            this.expectedTrailerFieldValue = expectedTrailerFieldValue;
        }

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            // step 1 - start async
            AsyncContext actx = req.startAsync();
            actx.setTimeout(Long.MAX_VALUE);
            actx.addListener(new AsyncListener() {

                @Override
                public void onTimeout(AsyncEvent event) throws IOException {
                    log.info("onTimeout");

                }

                @Override
                public void onStartAsync(AsyncEvent event) throws IOException {
                    log.info("onStartAsync");

                }

                @Override
                public void onError(AsyncEvent event) throws IOException {
                    log.info("AsyncListener.onError");

                }

                @Override
                public void onComplete(AsyncEvent event) throws IOException {
                    log.info("onComplete");

                }
            });
            // step 2 - notify on read
            ServletInputStream in = req.getInputStream();
            if (async) {
                listener = new TestAsyncReadListener(actx, false, ignoreIsReady, expectedTrailerFieldValue);
            } else {
                listener = new TestReadListener(actx, false, ignoreIsReady, expectedTrailerFieldValue);
            }
            in.setReadListener(listener);
        }
    }

    @WebServlet(asyncSupported = true)
    public static class NBWriteServlet extends TesterServlet {
        private static final long serialVersionUID = 1L;
        private final AtomicBoolean asyncContextIsComplete;
        private final boolean unlimited;
        private final boolean listenerCompletesOnError;
        public transient volatile TestWriteListener wlistener;

        public NBWriteServlet(AtomicBoolean asyncContextIsComplete) {
            this(asyncContextIsComplete, false, true);
        }


        public NBWriteServlet(AtomicBoolean asyncContextIsComplete, boolean unlimited, boolean listenerCompletesOnError) {
            this.asyncContextIsComplete = asyncContextIsComplete;
            this.unlimited = unlimited;
            this.listenerCompletesOnError = listenerCompletesOnError;
        }


        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            // step 1 - start async
            AsyncContext actx = req.startAsync();
            actx.setTimeout(Long.MAX_VALUE);
            actx.addListener(new AsyncListener() {

                @Override
                public void onTimeout(AsyncEvent event) throws IOException {
                    log.info("onTimeout");
                }

                @Override
                public void onStartAsync(AsyncEvent event) throws IOException {
                    log.info("onStartAsync");
                }

                @Override
                public void onError(AsyncEvent event) throws IOException {
                    log.info("AsyncListener.onError");
                    if (listenerCompletesOnError) {
                        event.getAsyncContext().complete();
                    }
                }

                @Override
                public void onComplete(AsyncEvent event) throws IOException {
                    log.info("onComplete");
                    asyncContextIsComplete.set(true);
                }
            });
            // step 2 - notify on read
            ServletOutputStream out = resp.getOutputStream();
            resp.setBufferSize(200 * 1024);
            wlistener = new TestWriteListener(actx, unlimited);
            out.setWriteListener(wlistener);
        }


    }

    @WebServlet(asyncSupported = true)
    public static class NBReadWriteServlet extends TesterServlet {
        private static final long serialVersionUID = 1L;
        public transient volatile TestReadWriteListener rwlistener;

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            // step 1 - start async
            AsyncContext actx = req.startAsync();
            actx.setTimeout(Long.MAX_VALUE);

            // step 2 - notify on read
            ServletInputStream in = req.getInputStream();
            rwlistener = new TestReadWriteListener(actx);
            in.setReadListener(rwlistener);
        }
    }

    private static class TestReadListener implements ReadListener {
        protected final AsyncContext ctx;
        protected final boolean usingNonBlockingWrite;
        protected final boolean ignoreIsReady;
        protected final String expectedTrailerFieldValue;
        protected final StringBuilder body = new StringBuilder();


        TestReadListener(AsyncContext ctx,
                boolean usingNonBlockingWrite,
                boolean ignoreIsReady,
                String expectedTrailerFieldValue) {
            this.ctx = ctx;
            this.usingNonBlockingWrite = usingNonBlockingWrite;
            this.ignoreIsReady = ignoreIsReady;
            this.expectedTrailerFieldValue = expectedTrailerFieldValue;
        }

        @Override
        public void onDataAvailable() throws IOException {
            ServletInputStream in = ctx.getRequest().getInputStream();
            String s = "";
            byte[] b = new byte[8192];
            int read = 0;
            do {
                read = in.read(b);
                if (read == -1) {
                    break;
                }
                s += new String(b, 0, read);
            } while (ignoreIsReady || in.isReady());
            log.info(s);
            body.append(s);
        }

        @Override
        public void onAllDataRead() {
            log.info("onAllDataRead totalData=" + body.toString().length());
            // If non-blocking writes are being used, don't write here as it
            // will inject unexpected data into the write output.
            if (!usingNonBlockingWrite) {
                String msg;
                if (body.toString().endsWith("FINISHED")) {
                    String trailerFieldValue = ((HttpServletRequest) ctx.getRequest()).getTrailerFields().get("x-test");
                    if (trailerFieldValue == null && expectedTrailerFieldValue == null ||
                            trailerFieldValue != null && trailerFieldValue.equals(expectedTrailerFieldValue)) {
                        msg = "OK";
                    } else {
                        System.out.println("Trailer value was [" + trailerFieldValue + "]");
                        msg = "FAILED";
                    }
                } else {
                    msg = "FAILED";
                }
                try {
                    ctx.getResponse().getOutputStream().print(msg);
                } catch (IOException ioe) {
                    // Ignore
                }
                ctx.complete();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            log.info("ReadListener.onError totalData=" + body.toString().length());
            throwable.printStackTrace();
        }
    }

    private static class TestAsyncReadListener extends TestReadListener {

        AtomicInteger isReadyCount = new AtomicInteger(0);
        AtomicInteger notReadyCount = new AtomicInteger(0);
        AtomicInteger containerThreadCount = new AtomicInteger(0);
        AtomicInteger nonContainerThreadCount = new AtomicInteger(0);

        TestAsyncReadListener(AsyncContext ctx, boolean usingNonBlockingWrite, boolean ignoreIsReady,
                String expectedTrailerFieldValue) {
            super(ctx, usingNonBlockingWrite, ignoreIsReady, expectedTrailerFieldValue);
        }

        @Override
        public void onDataAvailable() throws IOException {
            Request coyoteRequest;
            try {
                coyoteRequest = ((org.apache.catalina.connector.Request) CTX_REQUEST_FIELD.get(ctx)).getCoyoteRequest();
            } catch (IllegalArgumentException | IllegalAccessException e) {
                throw new IllegalStateException(e);
            }

            if (coyoteRequest.isRequestThread()) {
                containerThreadCount.incrementAndGet();
            } else {
                nonContainerThreadCount.incrementAndGet();
            }
            new Thread() {
                @Override
                public void run() {
                    try {
                        ServletInputStream in = ctx.getRequest().getInputStream();
                        byte[] b = new byte[1024];
                        int read = in.read(b);
                        if (read == -1) {
                            return;
                        }
                        body.append(new String(b, 0, read));
                        boolean isReady = ignoreIsReady || in.isReady();
                        if (isReady) {
                            isReadyCount.incrementAndGet();
                        } else {
                            notReadyCount.incrementAndGet();
                        }
                        if (isReady) {
                            onDataAvailable();
                        }
                    } catch (IOException e) {
                        onError(e);
                    }
                }
            }.start();
        }

        @Override
        public void onAllDataRead() {
            super.onAllDataRead();
            log.info("isReadyCount=" + isReadyCount + " notReadyCount=" + notReadyCount
                    + " containerThreadCount=" + containerThreadCount
                    + " nonContainerThreadCount=" + nonContainerThreadCount);
        }

        @Override
        public void onError(Throwable throwable) {
            super.onError(throwable);
            log.info("isReadyCount=" + isReadyCount + " notReadyCount=" + notReadyCount
                    + " containerThreadCount=" + containerThreadCount
                    + " nonContainerThreadCount=" + nonContainerThreadCount);
        }
    }

    private static class TestWriteListener implements WriteListener {
        AsyncContext ctx;
        private final boolean unlimited;
        int written = 0;
        public volatile boolean onErrorInvoked = false;

        TestWriteListener(AsyncContext ctx, boolean unlimited) {
            this.ctx = ctx;
            this.unlimited = unlimited;
        }

        @Override
        public void onWritePossible() throws IOException {
            long start = System.currentTimeMillis();
            int before = written;
            while ((written < WRITE_SIZE || unlimited) &&
                    ctx.getResponse().getOutputStream().isReady()) {
                ctx.getResponse().getOutputStream().write(
                        DATA, written, CHUNK_SIZE);
                written += CHUNK_SIZE;
            }
            if (written == WRITE_SIZE) {
                // Clear the output buffer else data may be lost when
                // calling complete
                ctx.getResponse().flushBuffer();
            }
            log.info("Write took: " + (System.currentTimeMillis() - start) +
                    " ms. Bytes before=" + before + " after=" + written);
            // only call complete if we have emptied the buffer
            if (ctx.getResponse().getOutputStream().isReady() &&
                    written == WRITE_SIZE) {
                // it is illegal to call complete
                // if there is a write in progress
                ctx.complete();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            log.info("WriteListener.onError");
            throwable.printStackTrace();
            onErrorInvoked = true;
        }

    }

    private static class TestReadWriteListener implements ReadListener {
        AsyncContext ctx;
        private final StringBuilder body = new StringBuilder();

        TestReadWriteListener(AsyncContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void onDataAvailable() throws IOException {
            ServletInputStream in = ctx.getRequest().getInputStream();
            String s = "";
            byte[] b = new byte[8192];
            int read = 0;
            do {
                read = in.read(b);
                if (read == -1) {
                    break;
                }
                s += new String(b, 0, read);
            } while (in.isReady());
            log.info("Read [" + s + "]");
            body.append(s);
        }

        @Override
        public void onAllDataRead() throws IOException {
            log.info("onAllDataRead");
            ServletOutputStream output = ctx.getResponse().getOutputStream();
            output.setWriteListener(new WriteListener() {
                @Override
                public void onWritePossible() throws IOException {
                    ServletOutputStream output = ctx.getResponse().getOutputStream();
                    if (output.isReady()) {
                        log.info("Writing [" + body.toString() + "]");
                        output.write(body.toString().getBytes("utf-8"));
                    }
                    ctx.complete();
                }

                @Override
                public void onError(Throwable throwable) {
                    log.info("ReadWriteListener.onError");
                    throwable.printStackTrace();
                }
            });
        }

        @Override
        public void onError(Throwable throwable) {
            log.info("ReadListener.onError");
            throwable.printStackTrace();
        }

    }

    public static int postUrlWithDisconnect(boolean stream, BytesStreamer streamer, String path,
            Map<String, List<String>> reqHead, Map<String, List<String>> resHead) throws IOException {

        URL url = URI.create(path).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setReadTimeout(1000000);
        if (reqHead != null) {
            for (Map.Entry<String, List<String>> entry : reqHead.entrySet()) {
                StringBuilder valueList = new StringBuilder();
                for (String value : entry.getValue()) {
                    if (valueList.length() > 0) {
                        valueList.append(',');
                    }
                    valueList.append(value);
                }
                connection.setRequestProperty(entry.getKey(), valueList.toString());
            }
        }
        if (streamer != null && stream) {
            if (streamer.getLength() > 0) {
                connection.setFixedLengthStreamingMode(streamer.getLength());
            } else {
                connection.setChunkedStreamingMode(1024);
            }
        }

        connection.connect();

        // Write the request body
        try (OutputStream os = connection.getOutputStream()) {
            while (streamer != null && streamer.available() > 0) {
                byte[] next = streamer.next();
                os.write(next);
                os.flush();
            }
        }

        int rc = connection.getResponseCode();
        if (resHead != null) {
            Map<String, List<String>> head = connection.getHeaderFields();
            resHead.putAll(head);
        }
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {

        }
        if (rc == HttpServletResponse.SC_OK) {
            connection.getInputStream().close();
            connection.disconnect();
        }
        return rc;
    }


    @Test
    public void testDelayedNBWrite() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context ctx = getProgrammaticRootContext();
        CountDownLatch latch1 = new CountDownLatch(1);
        DelayedNBWriteServlet servlet = new DelayedNBWriteServlet(latch1);
        String servletName = DelayedNBWriteServlet.class.getName();
        Tomcat.addServlet(ctx, servletName, servlet);
        ctx.addServletMappingDecoded("/", servletName);

        tomcat.start();

        CountDownLatch latch2 = new CountDownLatch(2);
        List<Throwable> exceptions = new ArrayList<>();

        Thread t = new Thread(
                new RequestExecutor("http://localhost:" + getPort() + "/", latch2, exceptions));
        t.start();

        latch1.await(3000, TimeUnit.MILLISECONDS);

        Thread t1 = new Thread(new RequestExecutor(
                "http://localhost:" + getPort() + "/?notify=true", latch2, exceptions));
        t1.start();

        latch2.await(3000, TimeUnit.MILLISECONDS);

        if (exceptions.size() > 0) {
            Assert.fail();
        }
    }

    @Test
    public void testDelayedNBReadWrite() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context ctx = getProgrammaticRootContext();
        CountDownLatch latch1 = new CountDownLatch(2);
        DelayedNBReadWriteServlet servlet = new DelayedNBReadWriteServlet(latch1);
        String servletName = DelayedNBReadWriteServlet.class.getName();
        Tomcat.addServlet(ctx, servletName, servlet);
        ctx.addServletMappingDecoded("/", servletName);

        tomcat.start();

        CountDownLatch latch2 = new CountDownLatch(1);
        List<Throwable> exceptions = new ArrayList<>();

        Thread t = new Thread(
            new RequestPostExecutor("http://localhost:" + getPort() + "/", latch2, exceptions));
        t.start();

        latch1.await(3000, TimeUnit.MILLISECONDS);
        latch2.await(3000, TimeUnit.MILLISECONDS);

        if (exceptions.size() > 0) {
            Assert.fail();
        }
    }

    private static final class RequestExecutor implements Runnable {
        private final String url;
        private final CountDownLatch latch;
        private final List<Throwable> exceptions;

        RequestExecutor(String url, CountDownLatch latch, List<Throwable> exceptions) {
            this.url = url;
            this.latch = latch;
            this.exceptions = exceptions;
        }

        @Override
        public void run() {
            try {
                ByteChunk result = new ByteChunk();
                int rc = getUrl(url, result, null);
                Assert.assertEquals(HttpServletResponse.SC_OK, rc);
                Assert.assertTrue(result.toString().contains("OK"));
            } catch (Throwable e) {
                e.printStackTrace();
                exceptions.add(e);
            } finally {
                latch.countDown();
            }
        }

    }

    private static final class RequestPostExecutor implements Runnable {
        private final String url;
        private final CountDownLatch latch;
        private final List<Throwable> exceptions;

        RequestPostExecutor(String url, CountDownLatch latch, List<Throwable> exceptions) {
            this.url = url;
            this.latch = latch;
            this.exceptions = exceptions;
        }

        @Override
        public void run() {
            try {
                ByteChunk result = new ByteChunk();
                int rc = postUrl("body".getBytes("utf-8"), url, result, null);
                Assert.assertEquals(HttpServletResponse.SC_OK, rc);
                Assert.assertTrue(result.toString().contains("OK"));
            } catch (Throwable e) {
                e.printStackTrace();
                exceptions.add(e);
            } finally {
                latch.countDown();
            }
        }

    }

    @WebServlet(asyncSupported = true)
    private static final class DelayedNBWriteServlet extends TesterServlet {
        private static final long serialVersionUID = 1L;
        private final Set<Emitter> emitters = new HashSet<>();
        private final transient CountDownLatch latch;

        DelayedNBWriteServlet(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            boolean notify = Boolean.parseBoolean(request.getParameter("notify"));
            AsyncContext ctx = request.startAsync();
            ctx.setTimeout(1000);
            if (!notify) {
                emitters.add(new Emitter(ctx));
                latch.countDown();
            } else {
                for (Emitter e : emitters) {
                    e.emit();
                }
                response.getOutputStream().println("OK");
                response.getOutputStream().flush();
                ctx.complete();
            }
        }

    }

    @WebServlet(asyncSupported = true)
    private static final class DelayedNBReadWriteServlet extends TesterServlet {
        private static final long serialVersionUID = 1L;
        private final transient CountDownLatch latch;

        DelayedNBReadWriteServlet(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
            final AsyncContext ctx = request.startAsync();
            ctx.setTimeout(1000);

            Thread readWriteListener =  new Thread(new ReadWriteListener(latch, ctx));
            readWriteListener.start();
        }
    }

    private static final class ReadWriteListener implements Runnable {
        private final transient CountDownLatch latch;
        private final transient AsyncContext ctx;

        ReadWriteListener(CountDownLatch latch, AsyncContext ctx){
            this.latch = latch;
            this.ctx = ctx;
        }

        @Override
        public void run() {
            try {
                setListeners();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void setListeners() throws IOException {
            final ServletInputStream is = ctx.getRequest().getInputStream();
            final ServletOutputStream os = ctx.getResponse().getOutputStream();

            is.setReadListener(new ReadListener() {
                @Override
                public void onDataAvailable() {

                    try {
                        byte buffer[] = new byte[1 * 4];
                        while (is.isReady() && !is.isFinished()) {
                            @SuppressWarnings("unused")
                            int ignore = is.read(buffer);
                        }
                        String body = new String(buffer, StandardCharsets.UTF_8);
                        Assert.assertTrue(body.equals("body"));

                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }

                @Override
                public void onAllDataRead() {
                    latch.countDown();
                }

                @Override
                public void onError(Throwable t) {
                }
            });

            os.setWriteListener(new WriteListener() {
                private boolean written = false;

                @Override
                public void onWritePossible() throws IOException {
                    ServletOutputStream out = ctx.getResponse().getOutputStream();
                    if (out.isReady() && !written) {
                        out.println("OK");
                        written = true;
                    }
                    if (out.isReady() && written) {
                        out.flush();
                        if (out.isReady()) {
                            ctx.complete();
                            latch.countDown();
                        }
                    }
                }

                @Override
                public void onError(Throwable t) {
                    t.printStackTrace();
                }

            });
        }

    }


    private static final class Emitter implements Serializable {

        private static final long serialVersionUID = 1L;

        private final transient AsyncContext ctx;

        Emitter(AsyncContext ctx) {
            this.ctx = ctx;
        }

        void emit() throws IOException {
            ctx.getResponse().getOutputStream().setWriteListener(new WriteListener() {
                private boolean written = false;

                @Override
                public void onWritePossible() throws IOException {
                    ServletOutputStream out = ctx.getResponse().getOutputStream();
                    if (out.isReady() && !written) {
                        out.println("OK");
                        written = true;
                    }
                    if (out.isReady() && written) {
                        out.flush();
                        if (out.isReady()) {
                            ctx.complete();
                        }
                    }
                }

                @Override
                public void onError(Throwable t) {
                    t.printStackTrace();
                }

            });
        }
    }


    /*
     * https://bz.apache.org/bugzilla/show_bug.cgi?id=61932
     */
    @Test
    public void testNonBlockingReadWithDispatch() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        NBReadWithDispatchServlet servlet = new NBReadWithDispatchServlet();
        String servletName = NBReadWithDispatchServlet.class.getName();
        Tomcat.addServlet(ctx, servletName, servlet);
        ctx.addServletMappingDecoded("/", servletName);

        tomcat.start();

        Map<String, List<String>> resHeaders = new HashMap<>();
        int rc = postUrl(true, new DataWriter(500, 5), "http://localhost:" +
                getPort() + "/", new ByteChunk(), resHeaders, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
    }


    @WebServlet(asyncSupported = true)
    private static final class NBReadWithDispatchServlet extends TesterServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            final CountDownLatch latch = new CountDownLatch(1);

            // Dispatch to "/error" will end up here
            if (req.getDispatcherType().equals(DispatcherType.ASYNC)) {
                // Return without writing anything. This will generate the
                // expected 200 response.
                return;
            }

            final AsyncContext asyncCtx = req.startAsync();
            final ServletInputStream is = req.getInputStream();
            is.setReadListener(new ReadListener() {

                @Override
                public void onDataAvailable() {

                    try {
                        byte buffer[] = new byte[1 * 1024];
                        while (is.isReady() && !is.isFinished()) {
                            is.read(buffer);
                        }

                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }

                @Override
                public void onAllDataRead() {
                    latch.countDown();
                }

                @Override
                public void onError(Throwable t) {
                }
            });

            new Thread(() -> {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                asyncCtx.dispatch("/error");
            }).start();
        }
    }


    @Test
    public void testCanceledPostChunked() throws Exception {
        doTestCanceledPost(new String[] {
                "POST / HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + SimpleHttpClient.CRLF +
                "Transfer-Encoding: Chunked" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF +
                "10" + SimpleHttpClient.CRLF +
                "This is 16 bytes" + SimpleHttpClient.CRLF
                });
    }


    @Test
    public void testCanceledPostNoChunking() throws Exception {
        doTestCanceledPost(new String[] {
                "POST / HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + SimpleHttpClient.CRLF +
                "Content-Length: 100" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF +
                "This is 16 bytes"
                });
    }


    /*
     * Tests an error on an non-blocking read when the client closes the
     * connection before fully writing the request body.
     *
     * Required sequence is:
     * - enter Servlet's service() method
     * - startAsync()
     * - configure non-blocking read
     * - read partial body
     * - close client connection
     * - error is triggered
     * - exit Servlet's service() method
     *
     * This test makes extensive use of instance fields in the Servlet that
     * would normally be considered very poor practice. It is only safe in this
     * test as the Servlet only processes a single request.
     */
    private void doTestCanceledPost(String[] request) throws Exception {

        CountDownLatch partialReadLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(1);

        AtomicBoolean testFailed = new AtomicBoolean(true);

        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        PostServlet postServlet = new PostServlet(partialReadLatch, completeLatch, testFailed);
        Wrapper wrapper = Tomcat.addServlet(ctx, "postServlet", postServlet);
        wrapper.setAsyncSupported(true);
        ctx.addServletMappingDecoded("/*", "postServlet");

        tomcat.start();

        ResponseOKClient client = new ResponseOKClient();
        client.setPort(getPort());
        client.setRequest(request);
        client.connect();
        client.sendRequest();

        // Wait server to read partial request body
        partialReadLatch.await();

        client.disconnect();

        completeLatch.await();

        Assert.assertFalse(testFailed.get());
    }


    private static final class ResponseOKClient extends SimpleHttpClient {

        @Override
        public boolean isResponseBodyOK() {
            return true;
        }
    }


    private static final class PostServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final transient CountDownLatch partialReadLatch;
        private final transient CountDownLatch completeLatch;
        private final AtomicBoolean testFailed;

        PostServlet(CountDownLatch doPostLatch, CountDownLatch completeLatch, AtomicBoolean testFailed) {
            this.partialReadLatch = doPostLatch;
            this.completeLatch = completeLatch;
            this.testFailed = testFailed;
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            AsyncContext ac = req.startAsync();
            ac.setTimeout(-1);
            CanceledPostAsyncListener asyncListener = new CanceledPostAsyncListener(completeLatch);
            ac.addListener(asyncListener);

            CanceledPostReadListener readListener = new CanceledPostReadListener(ac, partialReadLatch, testFailed);
            req.getInputStream().setReadListener(readListener);
        }
    }


    private static final class CanceledPostAsyncListener implements AsyncListener {

        private final transient CountDownLatch completeLatch;

        CanceledPostAsyncListener(CountDownLatch completeLatch) {
            this.completeLatch = completeLatch;
        }

        @Override
        public void onComplete(AsyncEvent event) throws IOException {
            System.out.println("complete");
            completeLatch.countDown();
        }

        @Override
        public void onTimeout(AsyncEvent event) throws IOException {
            System.out.println("onTimeout");
        }

        @Override
        public void onError(AsyncEvent event) throws IOException {
            System.out.println("onError-async");
        }

        @Override
        public void onStartAsync(AsyncEvent event) throws IOException {
            System.out.println("onStartAsync");
        }
    }

    private static final class CanceledPostReadListener implements ReadListener {

        private final AsyncContext ac;
        private final CountDownLatch partialReadLatch;
        private final AtomicBoolean testFailed;
        private int totalRead = 0;

        CanceledPostReadListener(AsyncContext ac, CountDownLatch partialReadLatch, AtomicBoolean testFailed) {
            this.ac = ac;
            this.partialReadLatch = partialReadLatch;
            this.testFailed = testFailed;
        }

        @Override
        public void onDataAvailable() throws IOException {
            ServletInputStream sis = ac.getRequest().getInputStream();
            boolean isReady;

            byte[] buffer = new byte[32];
            do {
                if (partialReadLatch.getCount() == 0) {
                    System.out.println("debug");
                }
                int bytesRead = sis.read(buffer);

                if (bytesRead == -1) {
                    return;
                }
                totalRead += bytesRead;
                isReady = sis.isReady();
                System.out.println("Read [" + bytesRead +
                        "], buffer [" + new String(buffer, 0, bytesRead, StandardCharsets.UTF_8) +
                        "], total read [" + totalRead +
                        "], isReady [" + isReady + "]");
            } while (isReady);
            if (totalRead == 16) {
                partialReadLatch.countDown();
            }
        }

        @Override
        public void onAllDataRead() throws IOException {
            ac.complete();
        }

        @Override
        public void onError(Throwable throwable) {
            throwable.printStackTrace();
            // This is the expected behaviour so clear the failed flag.
            testFailed.set(false);
            ac.complete();
        }
    }


    @Test
    public void testNonBlockingWriteError02NoSwallow() throws Exception {
        doTestNonBlockingWriteError02(false);
    }


    @Test
    public void testNonBlockingWriteError02Swallow() throws Exception {
        doTestNonBlockingWriteError02(true);
    }


    /*
     * Tests client disconnect in the following scenario:
     * - async with non-blocking IO
     * - response has been committed
     * - no data in buffers
     * - client disconnects
     * - server attempts a write
     */
    private void doTestNonBlockingWriteError02(boolean swallowIoException) throws Exception {
        CountDownLatch responseCommitLatch = new CountDownLatch(1);
        CountDownLatch clientCloseLatch = new CountDownLatch(1);
        CountDownLatch asyncCompleteLatch = new CountDownLatch(1);

        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        NBWriteServlet02 writeServlet =
                new NBWriteServlet02(responseCommitLatch, clientCloseLatch, asyncCompleteLatch, swallowIoException);
        Wrapper wrapper = Tomcat.addServlet(ctx, "writeServlet", writeServlet);
        wrapper.setAsyncSupported(true);
        ctx.addServletMappingDecoded("/*", "writeServlet");

        tomcat.start();

        ResponseOKClient client = new ResponseOKClient();
        client.setPort(getPort());
        client.setRequest(new String[] {
                "GET / HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF
                });
        client.connect();
        client.sendRequest();

        responseCommitLatch.await();

        client.disconnect();
        clientCloseLatch.countDown();

        Assert.assertTrue("Failed to complete async processing", asyncCompleteLatch.await(60, TimeUnit.SECONDS));
    }


    private static class NBWriteServlet02 extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final transient CountDownLatch responseCommitLatch;
        private final transient CountDownLatch clientCloseLatch;
        private final transient CountDownLatch asyncCompleteLatch;
        private final boolean swallowIoException;

        NBWriteServlet02(CountDownLatch responseCommitLatch, CountDownLatch clientCloseLatch,
                CountDownLatch asyncCompleteLatch, boolean swallowIoException) {
            this.responseCommitLatch = responseCommitLatch;
            this.clientCloseLatch = clientCloseLatch;
            this.asyncCompleteLatch = asyncCompleteLatch;
            this.swallowIoException = swallowIoException;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");

            AsyncContext ac = req.startAsync();
            ac.addListener(new TestAsyncListener02(asyncCompleteLatch));
            ac.setTimeout(5000);

            WriteListener writeListener =
                    new TestWriteListener02(ac, responseCommitLatch, clientCloseLatch, swallowIoException);
            resp.getOutputStream().setWriteListener(writeListener);
        }
    }


    private static class TestAsyncListener02 implements AsyncListener {

        private final CountDownLatch asyncCompleteLatch;

        TestAsyncListener02(CountDownLatch asyncCompleteLatch) {
            this.asyncCompleteLatch = asyncCompleteLatch;
        }

        @Override
        public void onComplete(AsyncEvent event) throws IOException {
            asyncCompleteLatch.countDown();
        }

        @Override
        public void onTimeout(AsyncEvent event) throws IOException {
            // NO-OP
        }

        @Override
        public void onError(AsyncEvent event) throws IOException {
            // NO-OP
        }

        @Override
        public void onStartAsync(AsyncEvent event) throws IOException {
            // NO-OP
        }

    }

    private static class TestWriteListener02 implements WriteListener {

        private final AsyncContext ac;
        private final CountDownLatch responseCommitLatch;
        private final CountDownLatch clientCloseLatch;
        private final boolean swallowIoException;
        private volatile AtomicInteger stage = new AtomicInteger(0);

        TestWriteListener02(AsyncContext ac, CountDownLatch responseCommitLatch,
                CountDownLatch clientCloseLatch, boolean swallowIoException) {
            this.ac = ac;
            this.responseCommitLatch = responseCommitLatch;
            this.clientCloseLatch = clientCloseLatch;
            this.swallowIoException = swallowIoException;
        }

        @Override
        public void onWritePossible() throws IOException {
            try {
                ServletOutputStream sos = ac.getResponse().getOutputStream();
                do {
                    if (stage.get() == 0) {
                        // Commit the response
                        ac.getResponse().flushBuffer();
                        responseCommitLatch.countDown();
                        stage.incrementAndGet();
                    } else if (stage.get() == 1) {
                        // Wait for the client to drop the connection
                        try {
                            clientCloseLatch.await();
                        } catch (InterruptedException e) {
                            // Ignore
                        }
                        sos.print("TEST");
                        stage.incrementAndGet();
                    } else if (stage.get() == 2) {
                        // This should trigger an error as the client closed the
                        // socket
                        sos.flush();
                        // Additional writes are required to trigger the error
                        // on solaris
                        sos.print("MORE");
                        log.info("Additional server write after client close to trigger exception");
                    }
                } while (sos.isReady());
            } catch (IOException ioe) {
                if (!swallowIoException) {
                    throw ioe;
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
            // NO-OP
        }
    }


    @Test
    public void testNonBlockingWriteWithClose() throws Exception {
        AtomicBoolean asyncContextIsComplete = new AtomicBoolean(false);
        AtomicBoolean asyncContextIsError = new AtomicBoolean(false);

        CountDownLatch beforeCloseLatch = new CountDownLatch(1);
        CountDownLatch afterCloseLatch = new CountDownLatch(1);

        AtomicInteger written = new AtomicInteger(-1);

        Tomcat tomcat = getTomcatInstance();
        // Note: Low values of socket.txBufSize can trigger very poor
        //       performance.
        Assert.assertTrue(tomcat.getConnector().setProperty("socket.txBufSize", "524228"));

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        TesterAccessLogValve alv = new TesterAccessLogValve();
        ctx.getPipeline().addValve(alv);

        NBWriteWithCloseServlet servlet = new NBWriteWithCloseServlet(
                asyncContextIsComplete, asyncContextIsError, beforeCloseLatch, afterCloseLatch, written);
        String servletName = NBWriteWithCloseServlet.class.getName();
        Tomcat.addServlet(ctx, servletName, servlet);
        ctx.addServletMappingDecoded("/", servletName);

        tomcat.start();

        SocketFactory factory = SocketFactory.getDefault();
        Socket s = factory.createSocket("localhost", getPort());

        OutputStream os = s.getOutputStream();
        os.write(("GET / HTTP/1.1\r\n" +
                "Host: localhost:" + getPort() + "\r\n" +
                "Connection: close\r\n" +
                "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        os.flush();

        // Wait for Servlet to fill write buffer
        beforeCloseLatch.await();
        // Close should return immediately
        long start = System.nanoTime();
        afterCloseLatch.await();
        long duration = System.nanoTime() - start;

        Assert.assertTrue("Close took [" + duration + "] ns", duration < 1_000_000_000);

        // Read the body
        InputStream is = s.getInputStream();
        int read = 0;
        byte[] buffer = new byte[8192];
        do {
            read = is.read(buffer);
        } while (read != -1);

        os.close();
        is.close();
        s.close();

        Assert.assertTrue(asyncContextIsComplete.get());
    }


    @WebServlet(asyncSupported = true)
    public static class NBWriteWithCloseServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;
        private final AtomicBoolean asyncContextIsComplete;
        private final AtomicBoolean asyncContextIsError;
        private final CountDownLatch beforeCloseLatch;
        private final CountDownLatch afterCloseLatch;
        private final AtomicInteger written;

        public NBWriteWithCloseServlet(AtomicBoolean asyncContextIsComplete, AtomicBoolean asyncContextIsError,
                CountDownLatch beforeCloseLatch, CountDownLatch afterCloseLatch, AtomicInteger written) {
            this.asyncContextIsComplete = asyncContextIsComplete;
            this.asyncContextIsError = asyncContextIsError;
            this.beforeCloseLatch = beforeCloseLatch;
            this.afterCloseLatch = afterCloseLatch;
            this.written = written;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            AsyncContext actx = req.startAsync();
            actx.setTimeout(Long.MAX_VALUE);
            actx.addListener(new AsyncListener() {

                @Override
                public void onTimeout(AsyncEvent event) throws IOException {
                    log.info("onTimeout");
                }

                @Override
                public void onStartAsync(AsyncEvent event) throws IOException {
                    log.info("onStartAsync");
                }

                @Override
                public void onError(AsyncEvent event) throws IOException {
                    log.info("AsyncListener.onError");
                    asyncContextIsError.set(true);
                }

                @Override
                public void onComplete(AsyncEvent event) throws IOException {
                    log.info("onComplete");
                    asyncContextIsComplete.set(true);
                }
            });

            // Write until buffer is full
            ServletOutputStream out = resp.getOutputStream();
            TestWriteListener03 writeListener = new TestWriteListener03(actx, beforeCloseLatch, afterCloseLatch);
            out.setWriteListener(writeListener);

            written.set(writeListener.written);
        }
    }


    private static class TestWriteListener03 implements WriteListener {
        private final AsyncContext ctx;
        private final CountDownLatch beforeCloseLatch;
        private final CountDownLatch afterCloseLatch;
        int written = 0;

        TestWriteListener03(AsyncContext ctx, CountDownLatch beforeCloseLatch, CountDownLatch afterCloseLatch) {
            this.ctx = ctx;
            this.beforeCloseLatch = beforeCloseLatch;
            this.afterCloseLatch = afterCloseLatch;
        }

        @Override
        public void onWritePossible() throws IOException {
            if (written == 0) {
                // Write until the buffer is full and then close the stream
                while (ctx.getResponse().getOutputStream().isReady()) {
                    ctx.getResponse().getOutputStream().write(DATA, written, CHUNK_SIZE);
                    written += CHUNK_SIZE;
                }
                beforeCloseLatch.countDown();
                ctx.getResponse().getOutputStream().close();
                afterCloseLatch.countDown();
            } else {
                ctx.complete();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            log.info("WriteListener.onError");
            throwable.printStackTrace();
        }
    }
}
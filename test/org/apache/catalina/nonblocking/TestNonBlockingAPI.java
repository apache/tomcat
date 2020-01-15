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
import java.net.HttpURLConnection;
import java.net.Socket;
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
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.SocketFactory;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.DispatcherType;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.BytesStreamer;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.valves.TesterAccessLogValve;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.ContainerThreadMarker;

public class TestNonBlockingAPI extends TomcatBaseTest {

    private static final Log log = LogFactory.getLog(TestNonBlockingAPI.class);

    private static final int CHUNK_SIZE = 1024 * 1024;
    private static final int WRITE_SIZE  = CHUNK_SIZE * 10;
    private static final byte[] DATA = new byte[WRITE_SIZE];
    private static final int WRITE_PAUSE_MS = 500;


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
        Context ctx = tomcat.addContext("", null);

        NBReadServlet servlet = new NBReadServlet(ignoreIsReady, async);
        String servletName = NBReadServlet.class.getName();
        Tomcat.addServlet(ctx, servletName, servlet);
        ctx.addServletMappingDecoded("/", servletName);

        tomcat.start();

        Map<String, List<String>> resHeaders = new HashMap<>();
        int rc = postUrl(true, new DataWriter(async ? 0 : 500, async ? 2000000 : 5),
                "http://localhost:" + getPort() + "/", new ByteChunk(), resHeaders, null);

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
    public void testNonBlockingWrite() throws Exception {
        testNonBlockingWriteInternal(false);
    }

    @Test
    public void testNonBlockingWriteWithKeepAlive() throws Exception {
        testNonBlockingWriteInternal(true);
    }

    private void testNonBlockingWriteInternal(boolean keepAlive) throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        NBWriteServlet servlet = new NBWriteServlet();
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
                        log.error("Mis-match tx: " + new String(
                                DATA, dataStart, dataEnd - dataStart));
                        log.error("Mis-match rx: " +
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
    }


    @Test
    public void testNonBlockingWriteError() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        TesterAccessLogValve alv = new TesterAccessLogValve();
        ctx.getPipeline().addValve(alv);

        NBWriteServlet servlet = new NBWriteServlet();
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
        while (count < 100 &&
                !(servlet.wlistener.onErrorInvoked || servlet.rlistener.onErrorInvoked)) {
            Thread.sleep(100);
            count ++;
        }

        while (count < 100 && alv.getEntryCount() < 1) {
            Thread.sleep(100);
            count ++;
        }

        Assert.assertTrue("Error listener should have been invoked.",
                servlet.wlistener.onErrorInvoked || servlet.rlistener.onErrorInvoked);

        // TODO Figure out why non-blocking writes with the NIO connector appear
        // to be slower on Linux
        alv.validateAccessLog(1, 500, WRITE_PAUSE_MS,
                WRITE_PAUSE_MS + 30 * 1000);
    }


    @Test
    public void testBug55438NonBlockingReadWriteEmptyRead() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

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
                if (count > 0)
                    try {
                        if (delay > 0)
                            Thread.sleep(delay);
                    } catch (Exception x) {
                    }
                count++;
                if (count < max)
                    return b;
                else
                    return f;
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
        transient TestReadListener listener;

        public NBReadServlet(boolean ignoreIsReady, boolean async) {
            this.async = async;
            this.ignoreIsReady = ignoreIsReady;
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
                listener = new TestAsyncReadListener(actx, false, ignoreIsReady);
            } else {
                listener = new TestReadListener(actx, false, ignoreIsReady);
            }
            in.setReadListener(listener);
        }
    }

    @WebServlet(asyncSupported = true)
    public static class NBWriteServlet extends TesterServlet {
        private static final long serialVersionUID = 1L;
        public transient volatile TestWriteListener wlistener;
        public transient volatile TestReadListener rlistener;

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
            rlistener = new TestReadListener(actx, true, false);
            in.setReadListener(rlistener);
            ServletOutputStream out = resp.getOutputStream();
            resp.setBufferSize(200 * 1024);
            wlistener = new TestWriteListener(actx);
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
        protected final StringBuilder body = new StringBuilder();
        public volatile boolean onErrorInvoked = false;


        public TestReadListener(AsyncContext ctx,
                boolean usingNonBlockingWrite,
                boolean ignoreIsReady) {
            this.ctx = ctx;
            this.usingNonBlockingWrite = usingNonBlockingWrite;
            this.ignoreIsReady = ignoreIsReady;
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
                    msg = "OK";
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
            onErrorInvoked = true;
        }
    }

    private static class TestAsyncReadListener extends TestReadListener {

        AtomicInteger isReadyCount = new AtomicInteger(0);
        AtomicInteger notReadyCount = new AtomicInteger(0);
        AtomicInteger containerThreadCount = new AtomicInteger(0);
        AtomicInteger nonContainerThreadCount = new AtomicInteger(0);

        public TestAsyncReadListener(AsyncContext ctx,
                boolean usingNonBlockingWrite, boolean ignoreIsReady) {
            super(ctx, usingNonBlockingWrite, ignoreIsReady);
        }

        @Override
        public void onDataAvailable() throws IOException {
            if (ContainerThreadMarker.isContainerThread()) {
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
        int written = 0;
        public volatile boolean onErrorInvoked = false;

        public TestWriteListener(AsyncContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void onWritePossible() throws IOException {
            long start = System.currentTimeMillis();
            int before = written;
            while (written < WRITE_SIZE &&
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

        public TestReadWriteListener(AsyncContext ctx) {
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

        URL url = new URL(path);
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


    @Ignore
    @Test
    public void testDelayedNBWrite() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context ctx = tomcat.addContext("", null);
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

    private static final class RequestExecutor implements Runnable {
        private final String url;
        private final CountDownLatch latch;
        private final List<Throwable> exceptions;

        public RequestExecutor(String url, CountDownLatch latch, List<Throwable> exceptions) {
            this.url = url;
            this.latch = latch;
            this.exceptions = exceptions;
        }

        @Override
        public void run() {
            try {
                ByteChunk result = new ByteChunk();
                int rc = getUrl(url, result, null);
                Assert.assertTrue(rc == HttpServletResponse.SC_OK);
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

        public DelayedNBWriteServlet(CountDownLatch latch) {
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
        Context ctx = tomcat.addContext("", null);

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

            Thread t = new Thread() {

                @Override
                public void run() {
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    asyncCtx.dispatch("/error");
                }
            };
            t.start();
        }
    }
}

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
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.SocketFactory;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.BytesStreamer;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.ByteChunk.ByteOutputChannel;

public class TestNonBlockingAPI extends TomcatBaseTest {

    private static final int CHUNK_SIZE = 1024 * 1024;
    private static final int WRITE_SIZE  = CHUNK_SIZE * 5;
    private static final byte[] DATA = new byte[WRITE_SIZE];


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
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        StandardContext ctx = (StandardContext) tomcat.addContext("",
                System.getProperty("java.io.tmpdir"));

        NBReadServlet servlet = new NBReadServlet();
        String servletName = NBReadServlet.class.getName();
        Tomcat.addServlet(ctx, servletName, servlet);
        ctx.addServletMapping("/", servletName);

        tomcat.start();

        Map<String, List<String>> resHeaders = new HashMap<>();
        int rc = postUrl(true, new DataWriter(500), "http://localhost:" +
                getPort() + "/", new ByteChunk(), resHeaders, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
    }


    @Test
    public void testNonBlockingWrite() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // Must have a real docBase - just use temp
        StandardContext ctx = (StandardContext) tomcat.addContext("",
                System.getProperty("java.io.tmpdir"));

        NBWriteServlet servlet = new NBWriteServlet();
        String servletName = NBWriteServlet.class.getName();
        Tomcat.addServlet(ctx, servletName, servlet);
        ctx.addServletMapping("/", servletName);
        tomcat.getConnector().setProperty("socket.txBufSize", "1024");
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
        while (read != -1) {
            read = is.read(buffer);
            if (read > 0) {
                result.append(buffer, 0, read);
            }
            readSinceLastPause += read;
            if (readSinceLastPause > WRITE_SIZE / 16) {
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
        System.out.println("Client read " + resultString.length() + " bytes");
        int lineStart = 0;
        int lineEnd = resultString.indexOf('\n', 0);
        String line = resultString.substring(lineStart, lineEnd + 1);
        Assert.assertEquals("HTTP/1.1 200 OK\r\n", line);

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
            System.out.println("[" + line + "]");
            chunkSize = Integer.parseInt(line, 16);

            // Read the chunk
            lineStart = lineEnd + 1;
            lineEnd = resultString.indexOf('\n', lineStart);
            System.out.println("Start : "  + lineStart + ", End: " + lineEnd);
            if (lineEnd > lineStart) {
                line = resultString.substring(lineStart, lineEnd + 1);
            } else {
                line = resultString.substring(lineStart);
            }
            if (line.length() > 40) {
                System.out.println(line.substring(0, 32));
            } else {
                System.out.println(line);
            }
            if (chunkSize + 2 != line.length()) {
                System.out.println("Chunk wrong length. Was " + line.length() +
                        " Expected " + (chunkSize + 2));

                byte[] resultBytes = resultString.getBytes();

                // Find error
                boolean found = false;
                for (int i = totalBodyRead; i < (totalBodyRead + line.length()); i++) {
                    if (DATA[i] != resultBytes[lineStart + i - totalBodyRead]) {
                        int dataStart = i - 16;
                        if (dataStart < 0) {
                            dataStart = 0;
                        }
                        int dataEnd = i + 16;
                        if (dataEnd > DATA.length) {
                            dataEnd = DATA.length;
                        }
                        int resultStart = lineStart + i - totalBodyRead - 16;
                        if (resultStart < 0) {
                            resultStart = 0;
                        }
                        int resultEnd = lineStart + i - totalBodyRead + 16;
                        if (resultEnd > resultString.length()) {
                            resultEnd = resultString.length();
                        }
                        System.out.println("Mis-match tx: " + new String(
                                DATA, dataStart, dataEnd - dataStart));
                        System.out.println("Mis-match rx: " +
                                resultString.substring(resultStart, resultEnd));
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    System.out.println("No mismatch. Data truncated");
                }
            }

            Assert.assertTrue(line.endsWith("\r\n"));
            Assert.assertEquals(chunkSize + 2, line.length());

            totalBodyRead += chunkSize;
        }

        Assert.assertEquals(WRITE_SIZE, totalBodyRead);
    }


    @Test
    public void testNonBlockingWriteError() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Not applicable to BIO. This test does not start a new thread for the
        // write so with BIO all the writes happen in the service() method just
        // like blocking IO.
        if (tomcat.getConnector().getProtocolHandlerClassName().equals(
                "org.apache.coyote.http11.Http11Protocol")) {
            return;
        }

        // Must have a real docBase - just use temp
        StandardContext ctx = (StandardContext) tomcat.addContext("", System.getProperty("java.io.tmpdir"));

        NBWriteServlet servlet = new NBWriteServlet();
        String servletName = NBWriteServlet.class.getName();
        Tomcat.addServlet(ctx, servletName, servlet);
        ctx.addServletMapping("/", servletName);
        tomcat.getConnector().setProperty("socket.txBufSize", "1024");
        tomcat.start();

        Map<String, List<String>> resHeaders = new HashMap<>();
        ByteChunk slowReader = new ByteChunk();
        slowReader.setLimit(1); // FIXME BUFFER IS BROKEN, 0 doesn't work
        slowReader.setByteOutputChannel(new ByteOutputChannel() {
            long counter = 0;
            long delta = 0;

            @Override
            public void realWriteBytes(byte[] cbuf, int off, int len) throws IOException {
                try {
                    if (len == 0)
                        return;
                    counter += len;
                    delta += len;
                    if (counter > WRITE_SIZE) {
                        System.out.println("ERROR Downloaded more than expected ERROR");
                    } else if (counter == WRITE_SIZE) {
                        System.out.println("Download complete(" + WRITE_SIZE + " bytes)");
                        // } else if (counter > (1966086)) {
                        // System.out.println("Download almost complete, missing bytes ("+counter+")");
                    } else if (delta > (WRITE_SIZE / 16)) {
                        System.out.println("Read " + counter + " bytes.");
                        delta = 0;
                        Thread.sleep(500);
                    }
                } catch (Exception x) {
                    throw new IOException(x);
                }
            }
        });
        int rc = postUrlWithDisconnect(true, new DataWriter(0), "http://localhost:" + getPort() + "/", resHeaders,
                null);
        slowReader.flushBuffer();
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        try {
            //allow the listeners to finish up
            Thread.sleep(1000);
        } catch (Exception e) {
        }
        Assert.assertTrue("Error listener should have been invoked.", servlet.wlistener.onErrorInvoked);
    }


    public static class DataWriter implements BytesStreamer {
        final int max = 5;
        int count = 0;
        long delay = 0;
        byte[] b = "WANTMORE".getBytes();
        byte[] f = "FINISHED".getBytes();

        public DataWriter(long delay) {
            this.delay = delay;
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
    public class NBReadServlet extends TesterServlet {
        private static final long serialVersionUID = 1L;
        public volatile TestReadListener listener;
        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            // step 1 - start async
            AsyncContext actx = req.startAsync();
            actx.setTimeout(Long.MAX_VALUE);
            actx.addListener(new AsyncListener() {

                @Override
                public void onTimeout(AsyncEvent event) throws IOException {
                    System.out.println("onTimeout");

                }

                @Override
                public void onStartAsync(AsyncEvent event) throws IOException {
                    System.out.println("onStartAsync");

                }

                @Override
                public void onError(AsyncEvent event) throws IOException {
                    System.out.println("AsyncListener.onError");

                }

                @Override
                public void onComplete(AsyncEvent event) throws IOException {
                    System.out.println("onComplete");

                }
            });
            // step 2 - notify on read
            ServletInputStream in = req.getInputStream();
            listener = new TestReadListener(actx);
            in.setReadListener(listener);

            listener.onDataAvailable();
        }
    }

    @WebServlet(asyncSupported = true)
    public class NBWriteServlet extends TesterServlet {
        private static final long serialVersionUID = 1L;
        public volatile TestWriteListener wlistener;
        public volatile TestReadListener rlistener;

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            // step 1 - start async
            AsyncContext actx = req.startAsync();
            actx.setTimeout(Long.MAX_VALUE);
            actx.addListener(new AsyncListener() {

                @Override
                public void onTimeout(AsyncEvent event) throws IOException {
                    System.out.println("onTimeout");

                }

                @Override
                public void onStartAsync(AsyncEvent event) throws IOException {
                    System.out.println("onStartAsync");

                }

                @Override
                public void onError(AsyncEvent event) throws IOException {
                    System.out.println("AsyncListener.onError");

                }

                @Override
                public void onComplete(AsyncEvent event) throws IOException {
                    System.out.println("onComplete");

                }
            });
            // step 2 - notify on read
            ServletInputStream in = req.getInputStream();
            rlistener = new TestReadListener(actx);
            in.setReadListener(rlistener);
            ServletOutputStream out = resp.getOutputStream();
            resp.setBufferSize(200 * 1024);
            wlistener = new TestWriteListener(actx);
            out.setWriteListener(wlistener);
            wlistener.onWritePossible();
        }


    }
    private class TestReadListener implements ReadListener {
        private final AsyncContext ctx;
        private final StringBuilder body = new StringBuilder();

        public TestReadListener(AsyncContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void onDataAvailable() {
            try {
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
                System.out.println(s);
                body.append(s);
            } catch (Exception x) {
                x.printStackTrace();
                ctx.complete();
            }
        }

        @Override
        public void onAllDataRead() {
            System.out.println("onAllDataRead");
            String msg;
            if (body.toString().endsWith("FINISHED")) {
                msg = "OK";
            } else {
                msg = "FAILED";
            }
            try {
                ctx.getResponse().getWriter().print(msg);
            } catch (IOException ioe) {
                // Ignore
            }
            ctx.complete();
        }

        @Override
        public void onError(Throwable throwable) {
            System.out.println("ReadListener.onError");
            throwable.printStackTrace();
        }
    }

    private class TestWriteListener implements WriteListener {
        AsyncContext ctx;
        int written = 0;
        public volatile boolean onErrorInvoked = false;

        public TestWriteListener(AsyncContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void onWritePossible() {
            try {
                long start = System.currentTimeMillis();
                long end = System.currentTimeMillis();
                int before = written;
                while (written < WRITE_SIZE &&
                        ctx.getResponse().getOutputStream().isReady()) {
                    ctx.getResponse().getOutputStream().write(
                            DATA, written, CHUNK_SIZE);
                    written += CHUNK_SIZE;
                }
                System.out.println("Write took:" + (end - start) +
                        " ms. Bytes before=" + before + " after=" + written);
                // only call complete if we have emptied the buffer
                if (ctx.getResponse().getOutputStream().isReady() &&
                        written == WRITE_SIZE) {
                    // it is illegal to call complete
                    // if there is a write in progress
                    ctx.complete();
                }
            } catch (Exception x) {
                x.printStackTrace();
            }

        }

        @Override
        public void onError(Throwable throwable) {
            System.out.println("WriteListener.onError");
            throwable.printStackTrace();
            onErrorInvoked = true;
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
        OutputStream os = null;
        try {
            os = connection.getOutputStream();
            while (streamer != null && streamer.available() > 0) {
                byte[] next = streamer.next();
                os.write(next);
                os.flush();
            }

        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException ioe) {
                    // Ignore
                }
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
            // Should never be null here but just to be safe
            if (os != null) {
                os.close();
            }
            connection.disconnect();
        }
        return rc;
    }
}

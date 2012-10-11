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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.ByteChunk.ByteOutputChannel;

public class TestNonBlockingAPI extends TomcatBaseTest {

    public static final long bytesToDownload = 1024 * 1024 * 5;

    @Override
    protected String getProtocol() {
        return Http11NioProtocol.class.getName();
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Test
    public void testNonBlockingRead() throws Exception {
        // Configure a context with digest auth and a single protected resource
        Tomcat tomcat = getTomcatInstance();
        // Must have a real docBase - just use temp
        StandardContext ctx = (StandardContext) tomcat.addContext("", System.getProperty("java.io.tmpdir"));

        NBReadServlet servlet = new NBReadServlet();
        String servletName = NBReadServlet.class.getName();
        Tomcat.addServlet(ctx, servletName, servlet);
        ctx.addServletMapping("/", servletName);

        tomcat.start();

        Map<String, List<String>> resHeaders = new HashMap<>();
        int rc = postUrl(true, new DataWriter(500), "http://localhost:" + getPort() + "/", new ByteChunk(),
                resHeaders, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
    }

    @Test
    public void testNonBlockingWrite() throws Exception {
        String bind = "localhost";
        // Configure a context with digest auth and a single protected resource
        Tomcat tomcat = getTomcatInstance();
        // Must have a real docBase - just use temp
        StandardContext ctx = (StandardContext) tomcat.addContext("", System.getProperty("java.io.tmpdir"));

        NBWriteServlet servlet = new NBWriteServlet();
        String servletName = NBWriteServlet.class.getName();
        Tomcat.addServlet(ctx, servletName, servlet);
        ctx.addServletMapping("/", servletName);
        tomcat.getConnector().setProperty("socket.txBufSize", "1024");
        tomcat.getConnector().setProperty("address", bind);
        System.out.println(tomcat.getConnector().getProperty("address"));
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
                    if (counter > bytesToDownload) {
                        System.out.println("ERROR Downloaded more than expected ERROR");
                    } else if (counter == bytesToDownload) {
                        System.out.println("Download complete(" + bytesToDownload + " bytes)");
                        // } else if (counter > (1966086)) {
                        // System.out.println("Download almost complete, missing bytes ("+counter+")");
                    } else if (delta > (bytesToDownload / 16)) {
                        System.out.println("Read " + counter + " bytes.");
                        delta = 0;
                        Thread.sleep(500);
                    }
                } catch (Exception x) {
                    throw new IOException(x);
                }
            }
        });
        int rc = postUrl(true, new DataWriter(0), "http://" + bind + ":" + getPort() + "/", slowReader, resHeaders,
                null);
        slowReader.flushBuffer();
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
    }


    @Test
    public void testNonBlockingWriteError() throws Exception {
        String bind = "localhost";
        // Configure a context with digest auth and a single protected resource
        Tomcat tomcat = getTomcatInstance();
        // Must have a real docBase - just use temp
        StandardContext ctx = (StandardContext) tomcat.addContext("", System.getProperty("java.io.tmpdir"));

        NBWriteServlet servlet = new NBWriteServlet();
        String servletName = NBWriteServlet.class.getName();
        Tomcat.addServlet(ctx, servletName, servlet);
        ctx.addServletMapping("/", servletName);
        tomcat.getConnector().setProperty("socket.txBufSize", "1024");
        tomcat.getConnector().setProperty("address", bind);
        System.out.println(tomcat.getConnector().getProperty("address"));
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
                    if (counter > bytesToDownload) {
                        System.out.println("ERROR Downloaded more than expected ERROR");
                    } else if (counter == bytesToDownload) {
                        System.out.println("Download complete(" + bytesToDownload + " bytes)");
                        // } else if (counter > (1966086)) {
                        // System.out.println("Download almost complete, missing bytes ("+counter+")");
                    } else if (delta > (bytesToDownload / 16)) {
                        System.out.println("Read " + counter + " bytes.");
                        delta = 0;
                        Thread.sleep(500);
                    }
                } catch (Exception x) {
                    throw new IOException(x);
                }
            }
        });
        int rc = postUrlWithDisconnect(true, new DataWriter(0), "http://" + bind + ":" + getPort() + "/", resHeaders,
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

            while (in.isReady()) {
                listener.onDataAvailable();
            }
            // step 3 - notify that we wish to read
            // ServletOutputStream out = resp.getOutputStream();
            // out.setWriteListener(new TestWriteListener(actx));

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
        AsyncContext ctx;

        public TestReadListener(AsyncContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void onDataAvailable() {
            try {
                ServletInputStream in = ctx.getRequest().getInputStream();
                String s = "";
                byte[] b = new byte[8192];
                while (in.isReady()) {
                    int read = in.read(b);
                    s += new String(b, 0, read);
                }
                System.out.println(s);
                if ("FINISHED".equals(s)) {
                    ctx.complete();
                    ctx.getResponse().getWriter().print("OK");
                } else {
                    in.isReady();
                }
            } catch (Exception x) {
                x.printStackTrace();
                ctx.complete();
            }

        }

        @Override
        public void onAllDataRead() {
            System.out.println("onAllDataRead");

        }

        @Override
        public void onError(Throwable throwable) {
            System.out.println("ReadListener.onError");
            throwable.printStackTrace();
        }
    }

    private class TestWriteListener implements WriteListener {
        long chunk = 1024 * 1024;
        AsyncContext ctx;
        long bytesToDownload = TestNonBlockingAPI.bytesToDownload;
        public volatile boolean onErrorInvoked = false;

        public TestWriteListener(AsyncContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void onWritePossible() {
            System.out.println("onWritePossible");
            try {
                long left = Math.max(bytesToDownload, 0);
                long start = System.currentTimeMillis();
                long end = System.currentTimeMillis();
                long before = left;
                while (left > 0 && ctx.getResponse().getOutputStream().canWrite()) {
                    byte[] b = new byte[(int) Math.min(chunk, bytesToDownload)];
                    Arrays.fill(b, (byte) 'X');
                    ctx.getResponse().getOutputStream().write(b);
                    bytesToDownload -= b.length;
                    left = Math.max(bytesToDownload, 0);
                }
                System.out.println("Write took:" + (end - start) + " ms. Bytes before=" + before + " after=" + left);
                // only call complete if we have emptied the buffer
                if (left == 0 && ctx.getResponse().getOutputStream().canWrite()) {
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

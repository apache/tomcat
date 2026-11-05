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

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestSendFile extends TomcatBaseTest {

    public static final int ITERATIONS = 10;
    public static final int EXPECTED_CONTENT_LENGTH = 100000;

    @Test
    public void testSendFile() throws Exception {

        Tomcat tomcat = getTomcatInstance();

        Context root = tomcat.addContext("", TEMP_DIR);

        File[] files = new File[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            files[i] = generateFile(TEMP_DIR, "-" + i, EXPECTED_CONTENT_LENGTH * (i + 1));
            addDeleteOnTearDown(files[i]);
        }

        for (int i = 0; i < ITERATIONS; i++) {
            WritingServlet servlet = new WritingServlet(files[i]);
            Tomcat.addServlet(root, "servlet" + i, servlet);
            root.addServletMapping("/servlet" + i, "servlet" + i);
        }

        tomcat.start();

        ByteChunk bc = new ByteChunk();
        Map<String,List<String>> respHeaders = new HashMap<>();
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.currentTimeMillis();
            int rc = getUrl("http://localhost:" + getPort() + "/servlet" + i, bc, null, respHeaders);
            Assert.assertEquals(HttpServletResponse.SC_OK, rc);
            System.out.println(
                    "Client received " + bc.getLength() + " bytes in " + (System.currentTimeMillis() - start) + " ms.");
            Assert.assertEquals("Expected [" + EXPECTED_CONTENT_LENGTH * (i + 1L) + "], was [" + bc.getLength() + "]",
                    EXPECTED_CONTENT_LENGTH * (i + 1L), bc.getLength());

            bc.recycle();
        }
    }

    public File generateFile(String dir, String suffix, int size) throws IOException {
        String name = "testSendFile-" + System.currentTimeMillis() + suffix + ".txt";
        File f = new File(dir, name);
        try (FileWriter fw = new FileWriter(f, false); BufferedWriter w = new BufferedWriter(fw)) {
            int defSize = 8192;
            while (size > 0) {
                int bytes = Math.min(size, defSize);
                char[] b = new char[bytes];
                Arrays.fill(b, 'X');
                w.write(b);
                size = size - bytes;
            }
            w.flush();
        }
        System.out.println("Created file:" + f.getAbsolutePath() + " with " + f.length() + " bytes.");
        return f;

    }


    private static class WritingServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final File f;

        WritingServlet(File f) {
            this.f = f;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

            resp.setContentType("'application/octet-stream");
            resp.setCharacterEncoding("ISO-8859-1");
            resp.setContentLengthLong(f.length());
            if (Boolean.TRUE.equals(req.getAttribute(Globals.SENDFILE_SUPPORTED_ATTR))) {
                req.setAttribute(Globals.SENDFILE_FILENAME_ATTR, f.getAbsolutePath());
                req.setAttribute(Globals.SENDFILE_FILE_START_ATTR, Long.valueOf(0));
                req.setAttribute(Globals.SENDFILE_FILE_END_ATTR, Long.valueOf(f.length()));
            } else {
                byte[] c = new byte[8192];
                try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(f))) {
                    int len = 0;
                    int written = 0;
                    long start = System.currentTimeMillis();
                    do {
                        len = in.read(c);
                        if (len > 0) {
                            resp.getOutputStream().write(c, 0, len);
                            written += len;
                        }
                    } while (len > 0);
                    System.out.println(
                            "Server Wrote " + written + " bytes in " + (System.currentTimeMillis() - start) + " ms.");
                }
            }
        }
    }


    @Test
    public void testKeepAliveTimeoutAfterAsyncSendfile() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context root = tomcat.addContext("", TEMP_DIR);
        File file = generateFile(TEMP_DIR, "-keepalive", 32 * 1024 * 1024);
        Tomcat.addServlet(root, "test", new WritingServlet(file));
        root.addServletMapping("/", "test");

        AbstractHttp11Protocol<?> protocol = (AbstractHttp11Protocol<?>) tomcat.getConnector().getProtocolHandler();
        protocol.setConnectionTimeout(2000);
        protocol.setKeepAliveTimeout(30000);

        tomcat.start();

        try (Socket socket = new Socket("localhost", getPort())) {
            OutputStream os = socket.getOutputStream();
            os.write("GET / HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            os.flush();

            // Do not read yet so that the file does not fit in the socket buffers and
            // sendfile has to complete asynchronously
            Thread.sleep(1000);

            InputStream is = socket.getInputStream();
            skipResponse(is, file.length());

            // The idle connection is governed by keepAliveTimeout, not connectionTimeout
            Thread.sleep(4000);
            socket.setSoTimeout(500);
            try {
                Assert.assertNotEquals("Connection closed before keepAliveTimeout expired", -1, is.read());
            } catch (SocketTimeoutException expected) {
                // The server has not closed the connection
            }
        } finally {
            Assert.assertTrue(file.delete());
        }
    }


    @Test
    public void testKeepAliveTimeoutStartsAfterAsyncSendfile() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context root = tomcat.addContext("", TEMP_DIR);
        File file = generateFile(TEMP_DIR, "-keepalive-start", 32 * 1024 * 1024);
        Tomcat.addServlet(root, "test", new WritingServlet(file));
        root.addServletMapping("/", "test");

        AbstractHttp11Protocol<?> protocol = (AbstractHttp11Protocol<?>) tomcat.getConnector().getProtocolHandler();
        protocol.setConnectionTimeout(30000);
        protocol.setKeepAliveTimeout(4000);

        tomcat.start();

        try (Socket socket = new Socket("localhost", getPort())) {
            OutputStream os = socket.getOutputStream();
            os.write("GET / HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            os.flush();

            // The transfer takes longer than keepAliveTimeout because the client does not read
            Thread.sleep(5000);

            InputStream is = socket.getInputStream();
            skipResponse(is, file.length());

            // keepAliveTimeout runs from the end of the transfer so the connection is still open
            socket.setSoTimeout(2500);
            try {
                Assert.assertNotEquals("Connection closed as soon as the transfer completed", -1, is.read());
            } catch (SocketTimeoutException expected) {
                // The server has not closed the connection
            }

            // and then closed when keepAliveTimeout expires
            socket.setSoTimeout(6000);
            Assert.assertEquals("Connection not closed by keepAliveTimeout", -1, is.read());
        } finally {
            Assert.assertTrue(file.delete());
        }
    }


    private static void skipResponse(InputStream is, long bodyLength) throws IOException {
        // Skip the response headers, up to and including the blank line
        int matched = 0;
        while (matched < 4) {
            int b = is.read();
            Assert.assertNotEquals("Connection closed while reading the response headers", -1, b);
            if (b == (matched % 2 == 0 ? '\r' : '\n')) {
                matched++;
            } else {
                matched = b == '\r' ? 1 : 0;
            }
        }
        // Skip the response body
        long remaining = bodyLength;
        byte[] buffer = new byte[65536];
        while (remaining > 0) {
            int read = is.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            Assert.assertNotEquals("Connection closed while reading the response body", -1, read);
            remaining -= read;
        }
    }


    @Test
    public void testBug60409() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context ctx = tomcat.addContext("", TEMP_DIR);
        File file = generateFile(TEMP_DIR, "", EXPECTED_CONTENT_LENGTH);
        Tomcat.addServlet(ctx, "test", new Bug60409Servlet(file));
        ctx.addServletMapping("/", "test");

        tomcat.start();

        ByteChunk bc = new ByteChunk();
        try {
            getUrl("http://localhost:" + getPort() + "/test/?" + Globals.SENDFILE_SUPPORTED_ATTR + "=true", bc, null);
        } catch (IOException ioe) {
            // Ignore possible IOE due to file delete on the server
            System.out.println("Ignored: " + ioe.getMessage());
        }

        CountDownLatch latch = new CountDownLatch(2);
        List<Throwable> throwables = new CopyOnWriteArrayList<>();
        new Thread(new RequestExecutor("http://localhost:" + getPort() + "/test/", latch, throwables)).start();
        new Thread(new RequestExecutor("http://localhost:" + getPort() + "/test/", latch, throwables)).start();

        latch.await(3000, TimeUnit.MILLISECONDS);

        if (throwables.size() > 0) {
            Assert.fail("[" + throwables.size() + "] throwables observed");
        }
    }

    private static final class Bug60409Servlet extends HttpServlet {
        private static final long serialVersionUID = 1L;
        private final File file;

        Bug60409Servlet(File file) {
            this.file = file;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            if (Boolean.valueOf(req.getParameter(Globals.SENDFILE_SUPPORTED_ATTR)).booleanValue()) {
                resp.setContentType("'application/octet-stream");
                resp.setCharacterEncoding("ISO-8859-1");
                resp.setContentLengthLong(file.length());
                req.setAttribute(Globals.SENDFILE_FILENAME_ATTR, file.getAbsolutePath());
                req.setAttribute(Globals.SENDFILE_FILE_START_ATTR, Long.valueOf(0));
                req.setAttribute(Globals.SENDFILE_FILE_END_ATTR, Long.valueOf(file.length()));
                if (!file.delete()) {
                    throw new ServletException("Failed to delete [" + file + "]");
                }
            } else {
                byte[] c = new byte[1024];
                Random rd = new Random();
                rd.nextBytes(c);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                resp.getOutputStream().write(c);
            }
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
                Assert.assertEquals(1024, result.getLength());
            } catch (Throwable e) {
                e.printStackTrace();
                exceptions.add(e);
            } finally {
                latch.countDown();
            }
        }

    }
}

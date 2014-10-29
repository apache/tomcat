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
package org.apache.coyote.http11.upgrade;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.Socket;

import javax.net.SocketFactory;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.WebConnection;

import org.junit.Assert;
import org.junit.Test;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.util.IOTools;

public class TestUpgrade extends TomcatBaseTest {

    private static final String MESSAGE = "This is a test.";

    @Test
    public void testSimpleUpgradeBlocking() throws Exception {
        doUpgrade(EchoBlocking.class);
    }

    @Test
    public void testSimpleUpgradeNonBlocking() throws Exception {
        doUpgrade(EchoNonBlocking.class);
    }

    @Test
    public void testMessagesBlocking() throws Exception {
        doTestMessages(EchoBlocking.class);
    }

    @Test
    public void testMessagesNonBlocking() throws Exception {
        doTestMessages(EchoNonBlocking.class);
    }

    @Test
    public void testSetNullReadListener() throws Exception {
        doTestCheckClosed(SetNullReadListener.class);
    }

    @Test
    public void testSetNullWriteListener() throws Exception {
        doTestCheckClosed(SetNullWriteListener.class);
    }

    @Test
    public void testSetReadListenerTwice() throws Exception {
        doTestCheckClosed(SetReadListenerTwice.class);
    }

    @Test
    public void testSetWriteListenerTwice() throws Exception {
        doTestCheckClosed(SetWriteListenerTwice.class);
    }

    @Test
    public void testFirstCallToOnWritePossible() throws Exception {
        doTestFixedResponse(FixedResponseNonBlocking.class);
    }

    private void doTestCheckClosed(
            Class<? extends HttpUpgradeHandler> upgradeHandlerClass)
                    throws Exception {
        UpgradeConnection conn = doUpgrade(upgradeHandlerClass);

        Reader r = conn.getReader();
        int c = r.read();

        Assert.assertEquals(-1, c);
    }

    private void doTestFixedResponse(
            Class<? extends HttpUpgradeHandler> upgradeHandlerClass)
                    throws Exception {
        UpgradeConnection conn = doUpgrade(upgradeHandlerClass);

        Reader r = conn.getReader();
        int c = r.read();

        Assert.assertEquals(FixedResponseNonBlocking.FIXED_RESPONSE, c);
    }

    private void doTestMessages (
            Class<? extends HttpUpgradeHandler> upgradeHandlerClass)
            throws Exception {
        UpgradeConnection conn = doUpgrade(upgradeHandlerClass);
        PrintWriter pw = new PrintWriter(conn.getWriter());
        BufferedReader reader = conn.getReader();

        pw.println(MESSAGE);
        pw.flush();

        Thread.sleep(500);

        pw.println(MESSAGE);
        pw.flush();

        // Note: BufferedReader.readLine() strips new lines
        //       ServletInputStream.readLine() does not strip new lines
        String response = reader.readLine();
        Assert.assertEquals(MESSAGE, response);
        response = reader.readLine();
        Assert.assertEquals(MESSAGE, response);
    }


    private UpgradeConnection doUpgrade(
            Class<? extends HttpUpgradeHandler> upgradeHandlerClass) throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        UpgradeServlet servlet = new UpgradeServlet(upgradeHandlerClass);
        Tomcat.addServlet(ctx, "servlet", servlet);
        ctx.addServletMapping("/", "servlet");

        tomcat.start();

        // Use raw socket so the necessary control is available after the HTTP
        // upgrade
        Socket socket =
                SocketFactory.getDefault().createSocket("localhost", getPort());

        socket.setSoTimeout(5000);

        InputStream is = socket.getInputStream();
        OutputStream os = socket.getOutputStream();

        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        Writer writer = new OutputStreamWriter(os);

        writer.write("GET / HTTP/1.1" + CRLF);
        writer.write("Host: whatever" + CRLF);
        writer.write(CRLF);
        writer.flush();

        String status = reader.readLine();

        Assert.assertNotNull(status);
        Assert.assertEquals("101", getStatusCode(status));

        // Skip the remaining response headers
        String line = reader.readLine();
        while (line != null && line.length() > 0) {
            // Skip
            line = reader.readLine();
        }

        return new UpgradeConnection(writer, reader);
    }

    private static class UpgradeServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final Class<? extends HttpUpgradeHandler> upgradeHandlerClass;

        public UpgradeServlet(Class<? extends HttpUpgradeHandler> upgradeHandlerClass) {
            this.upgradeHandlerClass = upgradeHandlerClass;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            req.upgrade(upgradeHandlerClass);
        }
    }

    private static class UpgradeConnection {
        private final Writer writer;
        private final BufferedReader reader;

        public UpgradeConnection(Writer writer, BufferedReader reader) {
            this.writer = writer;
            this.reader = reader;
        }

        public Writer getWriter() {
            return writer;
        }

        public BufferedReader getReader() {
            return reader;
        }
    }


    public static class EchoBlocking implements HttpUpgradeHandler {
        @Override
        public void init(WebConnection connection) {

            try (ServletInputStream sis = connection.getInputStream();
                 ServletOutputStream sos = connection.getOutputStream()){

                IOTools.flow(sis, sos);
            } catch (IOException ioe) {
                throw new IllegalStateException(ioe);
            }
        }

        @Override
        public void destroy() {
            // NO-OP
        }
    }


    public static class EchoNonBlocking implements HttpUpgradeHandler {

        private ServletInputStream sis;
        private ServletOutputStream sos;

        @Override
        public void init(WebConnection connection) {

            try {
                sis = connection.getInputStream();
                sos = connection.getOutputStream();
            } catch (IOException ioe) {
                throw new IllegalStateException(ioe);
            }

            sis.setReadListener(new EchoReadListener());
            sos.setWriteListener(new NoOpWriteListener());
        }

        @Override
        public void destroy() {
            // NO-OP
        }

        private class EchoReadListener extends NoOpReadListener {

            private byte[] buffer = new byte[8096];

            @Override
            public void onDataAvailable() {
                try {
                    while (sis.isReady()) {
                        int read = sis.read(buffer);
                        if (read > 0) {
                            if (sos.isReady()) {
                                sos.write(buffer, 0, read);
                            } else {
                                throw new IOException("Unable to echo data. " +
                                        "canWrite() returned false");
                            }
                        }
                    }
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            }
        }
    }


    public static class SetNullReadListener implements HttpUpgradeHandler {

        @Override
        public void init(WebConnection connection) {
            ServletInputStream sis;
            try {
                sis = connection.getInputStream();
            } catch (IOException ioe) {
                throw new IllegalStateException(ioe);
            }
            sis.setReadListener(null);
        }

        @Override
        public void destroy() {
            // NO-OP
        }
    }


    public static class SetNullWriteListener implements HttpUpgradeHandler {

        @Override
        public void init(WebConnection connection) {
            ServletOutputStream sos;
            try {
                sos = connection.getOutputStream();
            } catch (IOException ioe) {
                throw new IllegalStateException(ioe);
            }
            sos.setWriteListener(null);
        }

        @Override
        public void destroy() {
            // NO-OP
        }
    }


    public static class SetReadListenerTwice implements HttpUpgradeHandler {

        @Override
        public void init(WebConnection connection) {
            ServletInputStream sis;
            ServletOutputStream sos;
            try {
                sis = connection.getInputStream();
                sos = connection.getOutputStream();
            } catch (IOException ioe) {
                throw new IllegalStateException(ioe);
            }
            sos.setWriteListener(new NoOpWriteListener());
            ReadListener rl = new NoOpReadListener();
            sis.setReadListener(rl);
            sis.setReadListener(rl);
        }

        @Override
        public void destroy() {
            // NO-OP
        }
    }


    public static class SetWriteListenerTwice implements HttpUpgradeHandler {

        @Override
        public void init(WebConnection connection) {
            ServletInputStream sis;
            ServletOutputStream sos;
            try {
                sis = connection.getInputStream();
                sos = connection.getOutputStream();
            } catch (IOException ioe) {
                throw new IllegalStateException(ioe);
            }
            sis.setReadListener(new NoOpReadListener());
            WriteListener wl = new NoOpWriteListener();
            sos.setWriteListener(wl);
            sos.setWriteListener(wl);
        }

        @Override
        public void destroy() {
            // NO-OP
        }
    }


    public static class FixedResponseNonBlocking implements HttpUpgradeHandler {

        public static final char FIXED_RESPONSE = 'F';

        private ServletInputStream sis;
        private ServletOutputStream sos;

        @Override
        public void init(WebConnection connection) {

            try {
                sis = connection.getInputStream();
                sos = connection.getOutputStream();
            } catch (IOException ioe) {
                throw new IllegalStateException(ioe);
            }

            sis.setReadListener(new NoOpReadListener());
            sos.setWriteListener(new FixedResponseWriteListener());
        }

        @Override
        public void destroy() {
            // NO-OP
        }

        private class FixedResponseWriteListener extends NoOpWriteListener {
            @Override
            public void onWritePossible() {
                try {
                    sos.write(FIXED_RESPONSE);
                    sos.flush();
                } catch (IOException ioe) {
                    throw new IllegalStateException(ioe);
                }
            }
        }
    }


    private static class NoOpReadListener implements ReadListener {

        @Override
        public void onDataAvailable() {
            // NO-OP
        }

        @Override
        public void onAllDataRead() {
            // Always NO-OP for HTTP Upgrade
        }

        @Override
        public void onError(Throwable throwable) {
            // NO-OP
        }
    }


    private static class NoOpWriteListener implements WriteListener {

        @Override
        public void onWritePossible() {
            // NO-OP
        }

        @Override
        public void onError(Throwable throwable) {
            // NO-OP
        }
    }
}

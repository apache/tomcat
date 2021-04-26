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
import java.nio.charset.StandardCharsets;

import javax.net.SocketFactory;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.WebConnection;

import org.junit.Assert;
import org.junit.Test;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestUpgrade extends TomcatBaseTest {

    private static final String MESSAGE = "This is a test.";

    @Test
    public void testSimpleUpgradeBlocking() throws Exception {
        UpgradeConnection uc = doUpgrade(EchoBlocking.class);
        uc.shutdownInput();
        uc.shutdownOutput();
    }

    @Test
    public void testSimpleUpgradeNonBlocking() throws Exception {
        UpgradeConnection uc = doUpgrade(EchoNonBlocking.class);
        uc.shutdownInput();
        uc.shutdownOutput();
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
        UpgradeConnection uc = doUpgrade(upgradeHandlerClass);
        PrintWriter pw = new PrintWriter(uc.getWriter());
        BufferedReader reader = uc.getReader();

        pw.println(MESSAGE);
        pw.flush();

        Thread.sleep(500);

        pw.println(MESSAGE);
        pw.flush();

        uc.shutdownOutput();

        // Note: BufferedReader.readLine() strips new lines
        //       ServletInputStream.readLine() does not strip new lines
        String response = reader.readLine();
        Assert.assertEquals(MESSAGE, response);
        response = reader.readLine();
        Assert.assertEquals(MESSAGE, response);

        uc.shutdownInput();
        pw.close();
    }


    private UpgradeConnection doUpgrade(
            Class<? extends HttpUpgradeHandler> upgradeHandlerClass) throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        UpgradeServlet servlet = new UpgradeServlet(upgradeHandlerClass);
        Tomcat.addServlet(ctx, "servlet", servlet);
        ctx.addServletMappingDecoded("/", "servlet");

        tomcat.start();

        // Use raw socket so the necessary control is available after the HTTP
        // upgrade
        Socket socket =
                SocketFactory.getDefault().createSocket("localhost", getPort());

        socket.setSoTimeout(5000);

        UpgradeConnection uc = new UpgradeConnection(socket);

        uc.getWriter().write("GET / HTTP/1.1" + CRLF);
        uc.getWriter().write("Host: whatever" + CRLF);
        uc.getWriter().write("Upgrade: test" + CRLF);
        uc.getWriter().write(CRLF);
        uc.getWriter().flush();

        String status = uc.getReader().readLine();

        Assert.assertNotNull(status);
        Assert.assertEquals("101", getStatusCode(status));

        // Skip the remaining response headers
        String line = uc.getReader().readLine();
        while (line != null && line.length() > 0) {
            // Skip
            line = uc.getReader().readLine();
        }

        return uc;
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

            // In these tests only a single protocol is requested so it is safe
            // to echo it to the response.
            resp.setHeader("upgrade", req.getHeader("upgrade"));
            req.upgrade(upgradeHandlerClass);
        }
    }

    private static class UpgradeConnection {
        private final Socket socket;
        private final Writer writer;
        private final BufferedReader reader;

        public UpgradeConnection(Socket socket) {
            this.socket = socket;
            InputStream is;
            OutputStream os;
            try {
                is = socket.getInputStream();
                os = socket.getOutputStream();
            } catch (IOException ioe) {
                throw new IllegalArgumentException(ioe);
            }

            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8);

            this.writer = writer;
            this.reader = reader;
        }

        public Writer getWriter() {
            return writer;
        }

        public BufferedReader getReader() {
            return reader;
        }

        public void shutdownOutput() throws IOException {
            writer.flush();
            socket.shutdownOutput();
        }

        public void shutdownInput() throws IOException {
            socket.shutdownInput();
        }
    }


    public static class EchoBlocking implements HttpUpgradeHandler {
        @Override
        public void init(WebConnection connection) {

            try (ServletInputStream sis = connection.getInputStream();
                 ServletOutputStream sos = connection.getOutputStream()){
                byte[] buffer = new byte[8192];
                int read;
                while ((read = sis.read(buffer)) >= 0) {
                    sos.write(buffer, 0, read);
                    sos.flush();
                }
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

            EchoListener echoListener = new EchoListener(sis, sos);
            sis.setReadListener(echoListener);
            sos.setWriteListener(echoListener);
        }

        @Override
        public void destroy() {
            // NO-OP
        }


        private static class EchoListener implements ReadListener, WriteListener {

            private final ServletInputStream sis;
            private final ServletOutputStream sos;
            private final byte[] buffer = new byte[8192];

            public EchoListener(ServletInputStream sis, ServletOutputStream sos) {
                this.sis = sis;
                this.sos = sos;
            }

            @Override
            public void onWritePossible() throws IOException {
                if (sis.isFinished()) {
                    sis.close();
                    sos.close();
                }
                while (sis.isReady()) {
                    int read = sis.read(buffer);
                    if (read > 0) {
                        sos.write(buffer, 0, read);
                        if (!sos.isReady()) {
                            break;
                        }
                    }
                }
            }

            @Override
            public void onDataAvailable() throws IOException {
                if (sos.isReady()) {
                    onWritePossible();
                }
            }

            @Override
            public void onAllDataRead() throws IOException {
                if (sos.isReady()) {
                    onWritePossible();
                }
            }

            @Override
            public void onError(Throwable throwable) {
                throwable.printStackTrace();
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

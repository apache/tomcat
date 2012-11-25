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
import javax.servlet.http.ProtocolHandler;
import javax.servlet.http.WebConnection;

import junit.framework.Assert;

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

    private void doTestMessages (
            Class<? extends ProtocolHandler> upgradeHandlerClass)
            throws Exception {
        UpgradeConnection conn = doUpgrade(upgradeHandlerClass);
        PrintWriter pw = new PrintWriter(conn.getWriter());
        BufferedReader reader = conn.getReader();

        pw.println(MESSAGE);
        pw.flush();

        Thread.sleep(500);

        pw.println(MESSAGE);
        pw.flush();

        String response = reader.readLine();

        // Note: BufferedReader.readLine() strips new lines
        //       ServletInputStream.readLine() does not strip new lines
        Assert.assertEquals(MESSAGE, response);
    }


    private UpgradeConnection doUpgrade(
            Class<? extends ProtocolHandler> upgradeHandlerClass) throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        Context ctx =
                tomcat.addContext("", System.getProperty("java.io.tmpdir"));

        UpgradeServlet servlet = new UpgradeServlet(upgradeHandlerClass);
        Tomcat.addServlet(ctx, "servlet", servlet);
        ctx.addServletMapping("/", "servlet");

        tomcat.start();

        // Use raw socket so the necessary control is available after the HTTP
        // upgrade
        Socket socket =
                SocketFactory.getDefault().createSocket("localhost", getPort());

        socket.setSoTimeout(300000);

        InputStream is = socket.getInputStream();
        OutputStream os = socket.getOutputStream();

        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        Writer writer = new OutputStreamWriter(os);

        writer.write("GET / HTTP/1.1" + CRLF);
        writer.write("Host: whatever" + CRLF);
        writer.write(CRLF);
        writer.flush();

        String status = reader.readLine();

        Assert.assertEquals("HTTP/1.1 101 Switching Protocols",
                status.substring(0, 32));

        // Skip the remaining response headers
        while (reader.readLine().length() > 0) {
            // Skip
        }

        return new UpgradeConnection(writer, reader);
    }

    private static class UpgradeServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final Class<? extends ProtocolHandler> upgradeHandlerClass;

        public UpgradeServlet(Class<? extends ProtocolHandler> upgradeHandlerClass) {
            this.upgradeHandlerClass = upgradeHandlerClass;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            ProtocolHandler upgradeHandler;
            try {
                upgradeHandler = upgradeHandlerClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new ServletException(e);
            }

            req.upgrade(upgradeHandler);
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


    protected static class EchoBlocking implements ProtocolHandler {
        @Override
        public void init(WebConnection connection) {

            try (ServletInputStream sis = connection.getInputStream();
                 ServletOutputStream sos = connection.getOutputStream()){

                IOTools.flow(sis, sos);
            } catch (IOException ioe) {
                throw new IllegalStateException(ioe);
            }
        }
    }


    protected static class EchoNonBlocking implements ProtocolHandler {

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
            sos.setWriteListener(new EchoWriteListener());
        }

        private class EchoReadListener implements ReadListener {

            private byte[] buffer = new byte[8096];

            @Override
            public void onDataAvailable() {
                try {
                    while (sis.isReady()) {
                        int read = sis.read(buffer);
                        if (read > 0) {
                            if (sos.canWrite()) {
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

            @Override
            public void onAllDataRead() {
                // NO-OP for HTTP Upgrade
            }

            @Override
            public void onError(Throwable throwable) {
                // TODO Auto-generated method stub

            }
        }

        private class EchoWriteListener implements WriteListener {

            @Override
            public void onWritePossible() {
                // TODO Auto-generated method stub

            }

            @Override
            public void onError(Throwable throwable) {
                // TODO Auto-generated method stub

            }
        }
    }
}

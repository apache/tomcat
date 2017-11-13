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
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.TimeUnit;

import javax.net.SocketFactory;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.WebConnection;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.net.SocketWrapperBase.CompletionState;

public class TestUpgradeInternalHandler extends TomcatBaseTest {

    private static final String MESSAGE = "This is a test.";

    @Test
    public void testUpgradeInternal() throws Exception {
        Assume.assumeTrue(
                "Only supported on NIO 2",
                getTomcatInstance().getConnector().getProtocolHandlerClassName().contains("Nio2"));

        UpgradeConnection uc = doUpgrade(EchoAsync.class);
        PrintWriter pw = new PrintWriter(uc.getWriter());
        BufferedReader reader = uc.getReader();

        // Add extra sleep to avoid completing inline
        Thread.sleep(500);
        pw.println(MESSAGE);
        pw.flush();
        Thread.sleep(500);
        uc.shutdownOutput();

        // Note: BufferedReader.readLine() strips new lines
        //       ServletInputStream.readLine() does not strip new lines
        String response = reader.readLine();
        Assert.assertEquals(MESSAGE, response);

        uc.shutdownInput();
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

            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            Writer writer = new OutputStreamWriter(os);

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


    public static class EchoAsync implements InternalHttpUpgradeHandler {
        private SocketWrapperBase<?> wrapper;
        @Override
        public void init(WebConnection connection) {
            System.out.println("Init: " + connection);
            // Arbitrarily located in the init, could be in the initial read event, asynchronous, etc.
            // Note: the completion check used will not call the completion handler if the IO completed inline and without error.
            // Using a completion check that always calls complete would be easier here since the action is the same even with inline completion.
            final ByteBuffer buffer = ByteBuffer.allocate(1024);
            CompletionState state = wrapper.read(false, 10, TimeUnit.SECONDS, null, SocketWrapperBase.READ_DATA, new CompletionHandler<Long, Void>() {
                @Override
                public void completed(Long result, Void attachment) {
                    System.out.println("Read: " + result.longValue());
                    write(buffer);
                }
                @Override
                public void failed(Throwable exc, Void attachment) {
                    exc.printStackTrace();
                }
            }, buffer);
            System.out.println("CompletionState: " + state);
            if (state == CompletionState.INLINE) {
                write(buffer);
            }
        }

        private void write(ByteBuffer buffer) {
            buffer.flip();
            CompletionState state = wrapper.write(true, 10, TimeUnit.SECONDS, null, SocketWrapperBase.COMPLETE_WRITE, new CompletionHandler<Long, Void>() {
                @Override
                public void completed(Long result, Void attachment) {
                    System.out.println("Write: " + result.longValue());
                }
                @Override
                public void failed(Throwable exc, Void attachment) {
                    exc.printStackTrace();
                }
            }, buffer);
            System.out.println("CompletionState: " + state);
        }

        @Override
        public void pause() {
            // NO-OP
        }

        @Override
        public void destroy() {
            // NO-OP
        }

        @Override
        public SocketState upgradeDispatch(SocketEvent status) {
            System.out.println("Processing: " + status);
            switch (status) {
            case OPEN_READ:
                // Note: there's always an initial read event at the moment (reading should be skipped since it ends up in the internal buffer)
                break;
            case OPEN_WRITE:
                break;
            case STOP:
            case DISCONNECT:
            case ERROR:
            case TIMEOUT:
                return SocketState.CLOSED;
            }
            return SocketState.UPGRADED;
        }

        @Override
        public void setSocketWrapper(SocketWrapperBase<?> wrapper) {
            this.wrapper = wrapper;
        }

        @Override
        public void setSslSupport(SSLSupport sslSupport) {
            // NO-OP
        }
    }

}

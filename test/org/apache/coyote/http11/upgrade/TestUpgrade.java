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
import java.lang.reflect.Field;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.function.BooleanSupplier;

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
import org.apache.coyote.UpgradeToken;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.ContextBind;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.easymock.EasyMock;

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

    @Test
    public void testCleanEofWithBlockingRead() throws Exception {
        UpgradeConnection uc = doUpgrade(BlockingEof.class);

        // Half-close the connection from the client side. The blocking read in
        // the upgrade handler should then see the end of the input.
        uc.shutdownOutput();

        int c = uc.getReader().read();

        // End of input should be signalled by read() returning -1 so the
        // handler reports normal termination.
        Assert.assertEquals(BlockingEof.EOF_MARKER, c);

        uc.shutdownInput();
    }

    @Test
    public void testCleanEofWithNonBlockingRead() throws Exception {
        UpgradeConnection uc = doUpgrade(NonBlockingEof.class);

        uc.getWriter().write(MESSAGE);
        uc.getWriter().flush();

        // Half-close the connection from the client side. The read listener in
        // the upgrade handler should then see the end of the input.
        uc.shutdownOutput();

        int c = uc.getReader().read();

        // End of input should be reported via onAllDataRead() (normal
        // termination marker) rather than via onError().
        Assert.assertEquals(NonBlockingEof.EOF_MARKER, c);

        uc.shutdownInput();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testNoCallbacksAfterError() throws Exception {
        SocketWrapperBase<?> socketWrapper = EasyMock.createMock(SocketWrapperBase.class);
        ReadListener listener = EasyMock.createMock(ReadListener.class);

        ContextBind contextBind = new ContextBind() {
            @Override
            public ClassLoader bind(ClassLoader originalClassLoader) {
                return null;
            }

            @Override
            public void unbind(ClassLoader originalClassLoader) {
                // NO-OP
            }
        };
        UpgradeProcessorBase processor =
                new TesterUpgradeProcessor(new UpgradeToken(null, contextBind, null, "test"));

        IOException error = new IOException("Deliberately triggered for the test");

        socketWrapper.registerReadInterest();
        // The first call reports an error. No further socket state check or
        // listener call-back may occur once the error has been reported.
        EasyMock.expect(socketWrapper.isReadyForRead()).andThrow(error);
        listener.onError(error);
        EasyMock.expectLastCall().once();

        EasyMock.replay(socketWrapper, listener);

        UpgradeServletInputStream sis = new UpgradeServletInputStream(processor, socketWrapper,
                new UpgradeInfo());
        sis.setReadListener(listener);

        sis.onDataAvailable();
        sis.onDataAvailable();

        EasyMock.verify(socketWrapper, listener);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testWriteErrorReportedOnlyOnce() throws Exception {
        SocketWrapperBase<?> socketWrapper = EasyMock.createMock(SocketWrapperBase.class);
        WriteListener listener = EasyMock.createMock(WriteListener.class);

        ContextBind contextBind = new ContextBind() {
            @Override
            public ClassLoader bind(ClassLoader originalClassLoader) {
                return null;
            }

            @Override
            public void unbind(ClassLoader originalClassLoader) {
                // NO-OP
            }
        };
        UpgradeProcessorBase processor =
                new TesterUpgradeProcessor(new UpgradeToken(null, contextBind, null, "test"));

        IOException error = new IOException("Deliberately triggered for the test");

        socketWrapper.registerWriteInterest();
        // The flush fails. That failure is seen twice, once by the caller and
        // once by the close that reports the error. The listener must only be
        // notified once.
        EasyMock.expect(socketWrapper.flush(false)).andThrow(error);
        EasyMock.expect(socketWrapper.flush(false)).andThrow(error);
        listener.onError(error);
        EasyMock.expectLastCall().once();

        EasyMock.replay(socketWrapper, listener);

        UpgradeServletOutputStream sos = new UpgradeServletOutputStream(processor, socketWrapper,
                new UpgradeInfo());
        sos.setWriteListener(listener);

        sos.onWritePossible();
        // The call-back for the next dispatch must not trigger any further
        // socket processing.
        sos.onWritePossible();

        EasyMock.verify(socketWrapper, listener);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testOnAllDataReadReportedOnlyOnce() throws Exception {
        SocketWrapperBase<?> socketWrapper = EasyMock.createMock(SocketWrapperBase.class);

        ContextBind contextBind = new ContextBind() {
            @Override
            public ClassLoader bind(ClassLoader originalClassLoader) {
                return null;
            }

            @Override
            public void unbind(ClassLoader originalClassLoader) {
                // NO-OP
            }
        };
        UpgradeProcessorBase processor =
                new TesterUpgradeProcessor(new UpgradeToken(null, contextBind, null, "test"));

        // The first dispatch reports the socket as ready and the subsequent
        // read by the listener triggers the end of the data. The all data read
        // call-back must only be reported once, even if further dispatches
        // occur.
        socketWrapper.registerReadInterest();
        EasyMock.expect(socketWrapper.isReadyForRead()).andReturn(Boolean.TRUE);
        EasyMock.expect(socketWrapper.read(EasyMock.eq(false), EasyMock.anyObject(byte[].class),
                EasyMock.anyInt(), EasyMock.anyInt())).andReturn(-1);

        EasyMock.replay(socketWrapper);

        UpgradeServletInputStream sis = new UpgradeServletInputStream(processor, socketWrapper,
                new UpgradeInfo());
        RecordingReadListener listener = new RecordingReadListener(sis);
        sis.setReadListener(listener);

        sis.onDataAvailable();
        sis.onDataAvailable();

        EasyMock.verify(socketWrapper);
        Assert.assertEquals(1, listener.dataAvailableCount);
        Assert.assertEquals(1, listener.allDataReadCount);
        Assert.assertNull(listener.error);
    }

    @Test
    public void testUpgradeInfoUnregisteredWhenConnectionClosed() throws Exception {
        // The upgrade handler never closes the WebConnection so all clean-up
        // has to be performed by the container when the connection is closed.
        UpgradeConnection uc = doUpgrade(NoClose.class);

        AbstractHttp11Protocol<?> protocol =
                (AbstractHttp11Protocol<?>) getTomcatInstance().getConnector().getProtocolHandler();
        UpgradeGroupInfo groupInfo = protocol.getUpgradeGroupInfo("test");

        Set<UpgradeInfo> upgradeInfos = getUpgradeInfos(groupInfo);
        waitForCondition(() -> upgradeInfos.size() == 1, 5000);
        Assert.assertEquals(1, upgradeInfos.size());

        // Closing the connector closes the connection. This is the only event
        // that can trigger the clean-up of the upgrade registration.
        getTomcatInstance().stop();

        waitForCondition(upgradeInfos::isEmpty, 10000);

        // The registration must not outlive the connection.
        Assert.assertEquals("The UpgradeInfo remained registered after the connection was closed.", 0,
                upgradeInfos.size());

        uc.shutdownInput();
        uc.shutdownOutput();
    }

    @Test
    public void testZeroLengthReadReturnsZero() throws Exception {
        UpgradeConnection uc = doUpgrade(ZeroLengthReadBlocking.class);

        // Half-close the connection from the client side. The zero-length read
        // performed by the handler must neither block waiting for the end of
        // the input nor report the end of the input.
        uc.shutdownOutput();

        Assert.assertEquals(ZeroLengthReadBlocking.ZERO_READ_MARKER, uc.getReader().read());

        uc.shutdownInput();
    }


    @SuppressWarnings("unchecked")
    private static Set<UpgradeInfo> getUpgradeInfos(UpgradeGroupInfo groupInfo) throws Exception {
        Field upgradeInfosField = UpgradeGroupInfo.class.getDeclaredField("upgradeInfos");
        upgradeInfosField.setAccessible(true);
        return (Set<UpgradeInfo>) upgradeInfosField.get(groupInfo);
    }


    private static void waitForCondition(BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
    }

    private void doTestCheckClosed(
            Class<? extends HttpUpgradeHandler> upgradeHandlerClass)
                    throws Exception {
        UpgradeConnection conn = doUpgrade(upgradeHandlerClass);

        Reader r = conn.getReader();
        int c;
        try {
            c = r.read();
        } catch (SocketException se) {
            // Some platforms will throw an exception rather than returning -1
            c = -1;
        }

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
        Context ctx = getProgrammaticRootContext();

        UpgradeServlet servlet = new UpgradeServlet(upgradeHandlerClass);
        Tomcat.addServlet(ctx, "servlet", servlet);
        ctx.addServletMapping("/", "servlet");

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

        UpgradeServlet(Class<? extends HttpUpgradeHandler> upgradeHandlerClass) {
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

        UpgradeConnection(Socket socket) {
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


    public static class NoClose implements HttpUpgradeHandler {
        @Override
        public void init(WebConnection connection) {
            // Open the streams to make the upgraded connection fully active but
            // never close the WebConnection.
            try {
                connection.getInputStream();
                connection.getOutputStream();
            } catch (IOException ioe) {
                throw new IllegalStateException(ioe);
            }
        }

        @Override
        public void destroy() {
            // NO-OP
        }
    }


    public static class ZeroLengthReadBlocking implements HttpUpgradeHandler {

        public static final char ZERO_READ_MARKER = '0';

        @Override
        public void init(WebConnection connection) {

            try (ServletInputStream sis = connection.getInputStream();
                    ServletOutputStream sos = connection.getOutputStream()) {

                // A zero-length read is defined to read no bytes and return
                // zero, independent of the state of the input.
                int result = sis.read(new byte[8], 0, 0);

                sos.write(result == 0 ? (byte) ZERO_READ_MARKER : (byte) 'E');
                sos.flush();

                // Drain the input so the connection is closed cleanly once the
                // client has finished sending.
                byte[] buffer = new byte[128];
                while (sis.read(buffer) >= 0) {
                    // Drain
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

            EchoListener(ServletInputStream sis, ServletOutputStream sos) {
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


    public static class BlockingEof implements HttpUpgradeHandler {

        public static final char EOF_MARKER = 'O';

        @Override
        public void init(WebConnection connection) {
            try (ServletInputStream sis = connection.getInputStream();
                    ServletOutputStream sos = connection.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = sis.read(buffer)) >= 0) {
                    // Discard any data until the end of the input is reached
                }
                // A blocking read should report the end of the input by
                // returning -1 rather than by throwing an exception.
                if (read == -1) {
                    sos.write(EOF_MARKER);
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


    public static class NonBlockingEof implements HttpUpgradeHandler {

        public static final char EOF_MARKER = 'O';
        public static final char ERROR_MARKER = 'E';

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
            sis.setReadListener(new EofReadListener(sis, sos));
        }

        @Override
        public void destroy() {
            // NO-OP
        }

        private static class EofReadListener implements ReadListener {

            private final ServletInputStream sis;
            private final ServletOutputStream sos;

            EofReadListener(ServletInputStream sis, ServletOutputStream sos) {
                this.sis = sis;
                this.sos = sos;
            }

            @Override
            public void onDataAvailable() throws IOException {
                byte[] buffer = new byte[8192];
                while (sis.isReady()) {
                    if (sis.read(buffer) == -1) {
                        break;
                    }
                }
            }

            @Override
            public void onAllDataRead() throws IOException {
                // Normal end of input. Report that all data was read.
                sos.write(EOF_MARKER);
                sos.flush();
                sis.close();
                sos.close();
            }

            @Override
            public void onError(Throwable throwable) {
                try {
                    sos.write(ERROR_MARKER);
                    sos.flush();
                } catch (IOException ioe) {
                    // Ignore
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


    private static class RecordingReadListener implements ReadListener {

        private final ServletInputStream sis;
        private final byte[] buffer = new byte[8192];

        private int dataAvailableCount = 0;
        private int allDataReadCount = 0;
        private Throwable error = null;

        RecordingReadListener(ServletInputStream sis) {
            this.sis = sis;
        }

        @Override
        public void onDataAvailable() throws IOException {
            dataAvailableCount++;
            while (sis.isReady() && sis.read(buffer) > 0) {
                // Discard the data
            }
        }

        @Override
        public void onAllDataRead() {
            allDataReadCount++;
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
        }
    }


    private static class TesterUpgradeProcessor extends UpgradeProcessorBase {

        TesterUpgradeProcessor(UpgradeToken upgradeToken) {
            super(upgradeToken);
        }

        @Override
        protected Log getLog() {
            return LogFactory.getLog(TesterUpgradeProcessor.class);
        }

        @Override
        protected SocketState dispatch(SocketEvent status) {
            return null;
        }

        @Override
        public void close() {
            // NO-OP
        }

        @Override
        public ServletInputStream getInputStream() {
            return null;
        }

        @Override
        public ServletOutputStream getOutputStream() {
            return null;
        }

        @Override
        public void pause() {
            // NO-OP
        }

        @Override
        public void setSslSupport(SSLSupport sslSupport) {
            // NO-OP
        }
    }
}

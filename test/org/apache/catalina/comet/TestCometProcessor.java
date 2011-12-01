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
package org.apache.catalina.comet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import javax.net.SocketFactory;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.comet.CometEvent.EventType;
import org.apache.catalina.connector.CometEventImpl;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.valves.TesterAccessLogValve;
import org.apache.catalina.valves.ValveBase;

public class TestCometProcessor extends TomcatBaseTest {

    @Test
    public void testAsyncClose() throws Exception {

        if (!isCometSupported()) {
            return;
        }

        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();
        Context root = tomcat.addContext("", TEMP_DIR);
        Tomcat.addServlet(root, "comet", new SimpleCometServlet());
        root.addServletMapping("/comet", "comet");
        Tomcat.addServlet(root, "hello", new HelloWorldServlet());
        root.addServletMapping("/hello", "hello");
        root.getPipeline().addValve(new AsyncCometCloseValve());
        tomcat.getConnector().setProperty("connectionTimeout", "5000");
        tomcat.start();

        // Create connection to Comet servlet
        final Socket socket =
            SocketFactory.getDefault().createSocket("localhost", getPort());
        socket.setSoTimeout(5000);

        final OutputStream os = socket.getOutputStream();
        String requestLine = "POST http://localhost:" + getPort() +
                "/comet HTTP/1.1\r\n";
        os.write(requestLine.getBytes());
        os.write("transfer-encoding: chunked\r\n".getBytes());
        os.write("\r\n".getBytes());

        InputStream is = socket.getInputStream();
        ResponseReaderThread readThread = new ResponseReaderThread(is);
        readThread.start();

        // Wait for the comet request/response to finish
        int count = 0;
        while (count < 10 && !readThread.getResponse().endsWith("0\r\n\r\n")) {
            Thread.sleep(500);
            count++;
        }

        if (count == 10) {
            fail("Comet request did not complete");
        }

        // Send a standard HTTP request on the same connection
        requestLine = "GET http://localhost:" + getPort() +
                "/hello HTTP/1.1\r\n";
        os.write(requestLine.getBytes());
        os.write("\r\n".getBytes());

        // Check for the expected response
        count = 0;
        while (count < 10 && !readThread.getResponse().contains(
                HelloWorldServlet.RESPONSE_TEXT)) {
            Thread.sleep(500);
            count++;
        }

        if (count == 10) {
            fail("Non-comet request did not complete");
        }

        readThread.join();
        os.close();
        is.close();
    }

    @Test
    public void testSimpleCometClient() throws Exception {
        doSimpleCometTest(null);
    }

    @Test
    public void testSimpleCometClientBeginFail() throws Exception {
        doSimpleCometTest(SimpleCometServlet.FAIL_ON_BEGIN);
    }

    @Test
    public void testSimpleCometClientReadFail() throws Exception {
        doSimpleCometTest(SimpleCometServlet.FAIL_ON_READ);
    }

    @Test
    public void testSimpleCometClientEndFail() throws Exception {
        doSimpleCometTest(SimpleCometServlet.FAIL_ON_END);
    }

    private void doSimpleCometTest(String initParam) throws Exception {
        if (!isCometSupported()) {
            return;
        }

        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();
        Context root = tomcat.addContext("", TEMP_DIR);
        Wrapper w = Tomcat.addServlet(root, "comet", new SimpleCometServlet());
        if (initParam != null) {
            w.addInitParameter(initParam, "true");
        }
        root.addServletMapping("/", "comet");

        TesterAccessLogValve alv = new TesterAccessLogValve();
        root.getPipeline().addValve(alv);

        tomcat.start();

        // Create connection to Comet servlet
        final Socket socket =
            SocketFactory.getDefault().createSocket("localhost", getPort());
        socket.setSoTimeout(60000);

        final OutputStream os = socket.getOutputStream();
        String requestLine = "POST http://localhost:" + getPort() +
                "/ HTTP/1.1\r\n";
        os.write(requestLine.getBytes());
        os.write("transfer-encoding: chunked\r\n".getBytes());
        os.write("\r\n".getBytes());

        PingWriterThread writeThread = new PingWriterThread(4, os);
        writeThread.start();

        socket.setSoTimeout(25000);
        InputStream is = socket.getInputStream();
        ResponseReaderThread readThread = new ResponseReaderThread(is);
        readThread.start();
        readThread.join();
        os.close();
        is.close();

        String[] response = readThread.getResponse().split("\r\n");
        if (initParam == null) {
            // Normal response expected
            // Validate response
            assertEquals("HTTP/1.1 200 OK", response[0]);
            assertEquals("Server: Apache-Coyote/1.1", response[1]);
            assertTrue(response[2].startsWith("Set-Cookie: JSESSIONID="));
            assertEquals("Content-Type: text/plain;charset=ISO-8859-1", response[3]);
            assertEquals("Transfer-Encoding: chunked", response[4]);
            assertTrue(response[5].startsWith("Date: "));
            assertEquals("", response[6]);
            assertEquals("7", response[7]);
            assertEquals("BEGIN", response[8]);
            assertEquals("", response[9]);
            assertEquals("17", response[10]);
            assertEquals("Client: READ: 4 bytes", response[11]);
            assertEquals("", response[12]);
            assertEquals("17", response[13]);
            assertEquals("Client: READ: 4 bytes", response[14]);
            assertEquals("", response[15]);
            assertEquals("17", response[16]);
            assertEquals("Client: READ: 4 bytes", response[17]);
            assertEquals("", response[18]);
            assertEquals("17", response[19]);
            assertEquals("Client: READ: 4 bytes", response[20]);
            assertEquals("", response[21]);
            assertEquals("d", response[22]);
            assertEquals("Client: END", response[23]);
            assertEquals("", response[24]);
            assertEquals("0", response[25]);
            // Expect 26 lines
            assertEquals(26, response.length);
        } else {
            // Failure expected only expected for the fail on begin
            // Failure at any later stage and the response headers (including
            // the 200 response code will already have been sent to the client
            if (SimpleCometServlet.FAIL_ON_BEGIN.equals(initParam)) {
                assertEquals("HTTP/1.1 500 Internal Server Error", response[0]);
                alv.validateAccessLog(1, 500, 0, 1000);
            } else {
                assertEquals("HTTP/1.1 200 OK", response[0]);
                alv.validateAccessLog(1, 200, 0, 9000);
            }

        }
    }

    /**
     * Tests if the Comet connection is closed if the Tomcat connector is
     * stopped.
     */
    @Test
    public void testCometConnectorStop() throws Exception {

        if (!isCometSupported()) {
            return;
        }

        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();
        Context root = tomcat.addContext("", TEMP_DIR);
        Tomcat.addServlet(root, "comet", new SimpleCometServlet());
        root.addServletMapping("/", "comet");
        tomcat.start();

        // Create connection to Comet servlet
        final Socket socket =
            SocketFactory.getDefault().createSocket("localhost", getPort());
        socket.setSoTimeout(10000);

        final OutputStream os = socket.getOutputStream();
        String requestLine = "POST http://localhost:" + getPort() +
                "/ HTTP/1.1\r\n";
        os.write(requestLine.getBytes());
        os.write("transfer-encoding: chunked\r\n".getBytes());
        os.write("\r\n".getBytes());

        PingWriterThread writeThread = new PingWriterThread(100, os);
        writeThread.start();

        InputStream is = socket.getInputStream();
        ResponseReaderThread readThread = new ResponseReaderThread(is);
        readThread.start();

        // Allow the first couple of PING messages to be written
        Thread.sleep(3000);

        tomcat.getConnector().stop();

        // Wait for the write thread to stop
        int count = 0;
        while (writeThread.isAlive() && count < 50) {
            Thread.sleep(100);
            count ++;
        }

        // Wait for the read thread to stop
        while (readThread.isAlive() && count < 50) {
            Thread.sleep(100);
            count ++;
        }

        // Destroy the connector once the executor has sent the end event
        tomcat.getConnector().destroy();

        // Write should trigger an exception once the connector stops since the
        // socket should be closed
        assertNotNull("No exception in writing thread",
                writeThread.getException());

        // Termination of Read thread varies by platform and protocol
        // In all cases, the END event should be sent.
        assertTrue("Comet END event not received",
                readThread.getResponse().contains("Client: END"));
    }

    private boolean isCometSupported() {
        String protocol =
            getTomcatInstance().getConnector().getProtocolHandlerClassName();
        if (protocol.indexOf("Nio") == -1 && protocol.indexOf("Apr") == -1) {
            return false;
        } else {
            return true;
        }
    }

    private static class SimpleCometServlet extends HttpServlet
            implements CometProcessor {

        private static final long serialVersionUID = 1L;

        public static final String FAIL_ON_BEGIN = "failOnBegin";
        public static final String FAIL_ON_READ = "failOnRead";
        public static final String FAIL_ON_END = "failOnEnd";

        private boolean failOnBegin = false;
        private boolean failOnRead = false;
        private boolean failOnEnd = false;


        @Override
        public void init() throws ServletException {
            failOnBegin = Boolean.valueOf(getServletConfig().getInitParameter(
                    FAIL_ON_BEGIN)).booleanValue();
            failOnRead = Boolean.valueOf(getServletConfig().getInitParameter(
                    FAIL_ON_READ)).booleanValue();
            failOnEnd = Boolean.valueOf(getServletConfig().getInitParameter(
                    FAIL_ON_END)).booleanValue();
        }


        @Override
        public void event(CometEvent event) throws IOException,
                ServletException {

            HttpServletRequest request = event.getHttpServletRequest();
            HttpServletResponse response = event.getHttpServletResponse();

            HttpSession session = request.getSession(true);
            session.setMaxInactiveInterval(30);

            if (event.getEventType() == EventType.BEGIN) {
                if (failOnBegin) {
                    throw new IOException("Fail on begin");
                }
                response.setContentType("text/plain");
                response.getWriter().print("BEGIN" + "\r\n");
            } else if (event.getEventType() == EventType.READ) {
                if (failOnRead) {
                    throw new IOException("Fail on read");
                }
                InputStream is = request.getInputStream();
                int count = 0;
                while (is.available() > 0) {
                    is.read();
                    count ++;
                }
                String msg = "READ: " + count + " bytes";
                response.getWriter().print("Client: " + msg + "\r\n");
            } else if (event.getEventType() == EventType.END) {
                if (failOnEnd) {
                    throw new IOException("Fail on end");
                }
                String msg = "END";
                response.getWriter().print("Client: " + msg + "\r\n");
                event.close();
            } else {
                response.getWriter().print(event.getEventSubType() + "\r\n");
                event.close();
            }
            response.getWriter().flush();
        }
    }

    private static class PingWriterThread extends Thread {

        private final int pingCount;
        private final OutputStream os;
        private volatile Exception e = null;

        public PingWriterThread(int pingCount, OutputStream os) {
            this.pingCount = pingCount;
            this.os = os;
        }

        public Exception getException() {
            return e;
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < pingCount; i++) {
                    os.write("4\r\n".getBytes());
                    os.write("PING\r\n".getBytes());
                    os.flush();
                    Thread.sleep(1000);
                }
                os.write("0\r\n".getBytes());
                os.write("\r\n".getBytes());
            } catch (Exception e) {
                this.e = e;
            }
        }
    }

    private static class ResponseReaderThread extends Thread {

        private final InputStream is;
        private final StringBuilder response = new StringBuilder();

        public ResponseReaderThread(InputStream is) {
            this.is = is;
        }

        public String getResponse() {
            return response.toString();
        }

        @Override
        public void run() {
            try {
                int c = is.read();
                while (c > -1) {
                    response.append((char) c);
                    c = is.read();
                }
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    private static class AsyncCometCloseValve extends ValveBase {

        @Override
        public void invoke(Request request, Response response)
                throws IOException, ServletException {

            CometEventImpl event = new CometEventImpl(request, response);

            getNext().invoke(request, response);

            if (request.isComet()) {
                Thread t = new AsyncCometCloseThread(event);
                t.start();
            }
        }
    }

    private static class AsyncCometCloseThread extends Thread {

        private final CometEvent event;

        public AsyncCometCloseThread(CometEvent event) {
            this.event = event;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(2000);
                event.close();
            } catch (Exception e) {
                // Test should fail. Report what went wrong.
                e.printStackTrace();
            }
        }
    }
}

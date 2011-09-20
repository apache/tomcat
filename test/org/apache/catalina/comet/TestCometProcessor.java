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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.comet.CometEvent.EventType;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestCometProcessor extends TomcatBaseTest {

    @Test
    public void testSimpleCometClient() throws Exception {
        
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
        
        // Validate response
        String[] response = readThread.getResponse().split("\r\n");
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
        tomcat.getConnector().destroy();

        // Wait for the write thread to stop
        int count = 0;
        while (writeThread.isAlive() && count < 100) {
            Thread.sleep(100);
            count ++;
        }

        // Wait for the read thread to stop
        count = 0;
        while (readThread.isAlive() && count < 100) {
            Thread.sleep(100);
            count ++;
        }

        // Write should trigger an exception once the connector stops since the
        // socket should be closed
        assertNotNull("No exception in writing thread",
                writeThread.getException());
        // Read should terminate gracefully with an EOF
        assertNull("Read thread terminated with an exception",
                readThread.getException());
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

        @Override
        public void event(CometEvent event) throws IOException,
                ServletException {

            HttpServletRequest request = event.getHttpServletRequest();
            HttpServletResponse response = event.getHttpServletResponse();

            HttpSession session = request.getSession(true);
            session.setMaxInactiveInterval(30);

            if (event.getEventType() == EventType.BEGIN) {
                response.setContentType("text/plain");
                response.getWriter().print("BEGIN" + "\r\n");
            } else if (event.getEventType() == EventType.READ) {
                InputStream is = request.getInputStream();
                int count = 0;
                while (is.available() > 0) {
                    is.read();
                    count ++;
                }
                String msg = "READ: " + count + " bytes";
                response.getWriter().print("Client: " + msg + "\r\n");
            } else if (event.getEventType() == EventType.END) {
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
        
        private int pingCount;
        private OutputStream os;
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

        private InputStream is;
        private StringBuilder response = new StringBuilder();
        private volatile Exception e = null;

        public ResponseReaderThread(InputStream is) {
            this.is = is;
        }

        public String getResponse() {
            return response.toString();
        }

        public Exception getException() {
            return e;
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
                this.e = e;
            }
        }
    }
}

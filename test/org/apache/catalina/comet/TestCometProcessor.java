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

import org.apache.catalina.Context;
import org.apache.catalina.comet.CometEvent.EventType;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestCometProcessor extends TomcatBaseTest {

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
        
        Thread writeThread = new Thread(new Runnable() {
            
            @Override
            public void run() {
                try {
                    for (int i = 0; i < 4; i++) {
                        os.write("4\r\n".getBytes());
                        os.write("PING\r\n".getBytes());
                        os.flush();
                        Thread.sleep(1000);
                    }
                    os.write("0\r\n".getBytes());
                    os.write("\r\n".getBytes());
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });

        writeThread.start();
        
        StringBuffer buffer = new StringBuffer();
        socket.setSoTimeout(25000);
        InputStream is = socket.getInputStream();
        int c = is.read();
        while (c > -1) {
            buffer.append((char) c);
            c = is.read();
        }
        os.close();
        
        // Validate response
        String[] response = buffer.toString().split("\r\n");
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
                response.getWriter().println("BEGIN");
            } else if (event.getEventType() == EventType.READ) {
                InputStream is = request.getInputStream();
                int count = 0;
                while (is.available() > 0) {
                    is.read();
                    count ++;
                }
                String msg = "READ: " + count + " bytes";
                response.getWriter().println("Client: " + msg);
            } else if (event.getEventType() == EventType.END) {
                String msg = "END";
                response.getWriter().println("Client: " + msg);
                event.close();
            } else {
                response.getWriter().println(event.getEventSubType());
                event.close();
            }
            response.getWriter().flush();
        }
    }
}

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
package org.apache.catalina.connector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;


public class TestClientReadTimeout extends TomcatBaseTest {

    static Tomcat tomcat;

    @Test
    public void testTimeoutGets408() throws IOException, LifecycleException {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        ((StandardHost) tomcat.getHost()).setErrorReportValveClass(null);

        Tomcat.addServlet(ctx, "TestServlet", new SyncServlet());
        ctx.addServletMappingDecoded("/*", "TestServlet");

        tomcat.start();

        try (Socket socket = new Socket("localhost", getPort())) {
            String request = "GET /async HTTP/1.1\r\nHost: localhost\r\ncontent-length: 101\r\n\r\n";

            OutputStream os = socket.getOutputStream();
            os.write(request.getBytes(StandardCharsets.UTF_8));
            InputStream is = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String opening = null;
            try {
                opening = reader.readLine();
            } catch (SocketException e) {
                // Handled below. An exception here means opening will be null
            }
            if (tomcat.getConnector().getProtocolHandlerClassName().contains("Nio2")) {
                Assert.assertNull("NIO2 unexpectedly returned a response", opening);
            } else {
                Assert.assertNotNull("Didn't get back a response", opening);
                StringBuilder sb = new StringBuilder(opening);

                try {
                    Assert.assertTrue(
                            "expected status code " + HttpServletResponse.SC_REQUEST_TIMEOUT + " but got " + opening,
                            opening.startsWith("HTTP/1.1 " + HttpServletResponse.SC_REQUEST_TIMEOUT));
                    boolean connectionClose = false;
                    while (reader.ready()) {
                        String line = reader.readLine();
                        if (line == null) {
                            break;
                        }

                        sb.append("\n").append(line);
                        if ("connection: close".equalsIgnoreCase(line)) {
                            connectionClose = true;
                        }

                        Assert.assertFalse(line.contains("Exception Report"));
                        Assert.assertFalse(line.contains("Status Report"));
                    }

                    Assert.assertTrue("No 'Connection: close' header seen", connectionClose);
                } catch (Throwable t) {
                    Assert.fail("Response:\n" + sb);
                    t.printStackTrace();
                }
            }
        }
    }


    static final class SyncServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            try {
                while (req.getInputStream().read() != -1) {
                    // NO-OP - Any data read is ignored
                }
                resp.setStatus(200);
                resp.flushBuffer();
            } catch (ClientAbortException e) {
                // resp.sendError(408);
            }
        }
    }

}
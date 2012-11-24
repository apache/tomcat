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
import java.io.Writer;
import java.net.Socket;

import javax.net.SocketFactory;
import javax.servlet.ServletException;
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

public class TestUpgradeServletInputStream extends TomcatBaseTest {

    @Test
    public void testSimpleUpgrade() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        Context ctx =
                tomcat.addContext("", System.getProperty("java.io.tmpdir"));

        UpgradeServlet servlet = new UpgradeServlet();
        Tomcat.addServlet(ctx, "servlet", servlet);
        ctx.addServletMapping("/", "servlet");

        tomcat.start();

        // Use raw socket so the necessary control is available post the HTTP
        // upgrade
        Socket socket =
                SocketFactory.getDefault().createSocket("localhost", getPort());

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
    }

    private static class UpgradeServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            req.upgrade(new Echo());
        }
    }

    private static class Echo implements ProtocolHandler {

        @Override
        public void init(WebConnection connection) {
            // TODO
        }
    }
}

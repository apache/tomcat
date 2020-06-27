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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.unittest.TesterData;

/*
 * Various requests, usually originating from fuzzing, that have triggered an
 * incorrect response from Tomcat - usually a 500 response rather than a 400
 * response.
 */
@RunWith(Parameterized.class)
public class TestCoyoteAdapterRequestFuzzing extends TomcatBaseTest {

    private static final String VALUE_16K = TesterData.string('x', 16 * 1024);
    // Default max header count is 100
    private static final String HEADER_150 = TesterData.string("X-Tomcat-Test: a" + CRLF, 150);
    // Default max header count is 200 (need to keep under maxHeaderCount as well)
    private static final String COOKIE_250 = TesterData.string("Cookie: a=b;c=d;e=f;g=h" + CRLF, 75);

    @Parameterized.Parameters(name = "{index}: requestline[{0}], expected[{2}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        parameterSets.add(new Object[] { "GET /00 HTTP/1.1",
                                         "Host: lÿ#" + CRLF,
                                         "400" } );
        parameterSets.add(new Object[] { "GET *; HTTP/1.1",
                                         "Host: localhost" + CRLF,
                                         "400" } );
        parameterSets.add(new Object[] { "GET /02 HTTP/1.1",
                                         "Host: localhost" + CRLF +
                                         "Content-Length: \u00A0" + CRLF,
                                         "400" } );
        parameterSets.add(new Object[] { "GET /03 HTTP/1.1",
                                         "Content-Length: 1" + CRLF +
                                         "Content-Length: 1" + CRLF,
                                         "400" } );
        parameterSets.add(new Object[] { "GET /04 HTTP/1.1",
                                         "Transfer-Encoding: " + VALUE_16K + CRLF,
                                         "400" } );
        parameterSets.add(new Object[] { "GET /05 HTTP/1.1",
                                         "Expect: " + VALUE_16K + CRLF,
                                         "400" } );
        parameterSets.add(new Object[] { "GET /06 HTTP/1.1",
                                         "Connection: " + VALUE_16K + CRLF,
                                         "400" } );
        parameterSets.add(new Object[] { "GET /07 HTTP/1.1",
                                         "User-Agent: " + VALUE_16K + CRLF,
                                         "400" } );
        parameterSets.add(new Object[] { "GET /08 HTTP/1.1",
                                         HEADER_150,
                                         "400" } );
        parameterSets.add(new Object[] { "GET http://host/09 HTTP/1.0",
                                         HEADER_150,
                                         "400" } );
        parameterSets.add(new Object[] { "GET /10 HTTP/1.1",
                                         "Host: localhost" + CRLF +
                                         COOKIE_250,
                                         "400" } );

        return parameterSets;
    }

    @Parameter(0)
    public String requestLine;

    @Parameter(1)
    public String headers;

    @Parameter(2)
    public String expected;


    @Test
    public void doTest() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Assert.assertTrue(tomcat.getConnector().setProperty("restrictedUserAgents", "value-not-important"));

        File appDir = new File("test/webapp");
        Context ctxt = tomcat.addContext("", appDir.getAbsolutePath());
        Tomcat.addServlet(ctxt, "default", DefaultServlet.class.getName());
        ctxt.addServletMappingDecoded("/", "default");

        tomcat.start();

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] {requestLine + CRLF, headers + CRLF});

        client.connect();
        client.processRequest();

        // Expected response
        String line = client.getResponseLine();
        Assert.assertTrue(line + CRLF + client.getResponseBody(), line.startsWith("HTTP/1.1 " + expected + " "));
    }


    private static final class Client extends SimpleHttpClient {

        public Client(int port) {
            setPort(port);
            setRequestPause(0);
        }

        @Override
        protected OutputStream createOutputStream(Socket socket) throws IOException {
            // Override the default implementation so we can create a large
            // enough buffer to hold the entire request.
            // The default implementation uses the 8k buffer in the
            // StreamEncoder. Since some requests are larger than this, those
            // requests will be sent in several parts. If the first part is
            // sufficient for Tomcat to determine the request is invalid, Tomcat
            // will close the connection, causing the write of the remaining
            // parts to fail which in turn causes the test to fail.
            return new BufferedOutputStream(super.createOutputStream(socket), 32 * 1024);
        }

        @Override
        public boolean isResponseBodyOK() {
            // Response body varies. It is the response code that is of interest
            // in these tests.
            return true;
        }
    }
}

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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

/*
 * Various requests, usually originating from fuzzing, that have triggered an
 * incorrect response from Tomcat - usually a 500 response rather than a 400
 * response.
 */
@RunWith(Parameterized.class)
public class TestCoyoteAdapterRequestFuzzing extends TomcatBaseTest {

    @Parameterized.Parameters(name = "{index}: uri[{0}], host[{1}], expected[{2}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<Object[]>();

        parameterSets.add(new Object[] { "/", "lÿ#", "400" } );
        parameterSets.add(new Object[] { "*;", "", "400" } );

        return parameterSets;
    }

    @Parameter(0)
    public String uri;

    @Parameter(1)
    public String host;

    @Parameter(2)
    public String expected;


    @Test
    public void doTest() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-3.0");
        Context ctxt = tomcat.addContext("", appDir.getAbsolutePath());
        Tomcat.addServlet(ctxt, "default", DefaultServlet.class.getName());
        ctxt.addServletMapping("/", "default");

        tomcat.start();

        String request =
                "GET " + uri + " HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: " + host + SimpleHttpClient.CRLF +
                 SimpleHttpClient.CRLF;

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] {request});

        client.connect();
        client.processRequest();

        // Expected response
        String line = client.getResponseLine();
        Assert.assertTrue(line, line.startsWith("HTTP/1.1 " + expected + " "));
    }


    private static final class Client extends SimpleHttpClient {

        public Client(int port) {
            setPort(port);
        }

        @Override
        public boolean isResponseBodyOK() {
            // Response body varies. It is the response code that is of interest
            // in these tests.
            return true;
        }
    }
}

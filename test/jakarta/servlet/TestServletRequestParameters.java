/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jakarta.servlet;

import java.net.SocketException;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;

public class TestServletRequestParameters extends ServletRequestParametersBaseTest {

    @Test
    public void testClientDisconnect() throws Exception {

        Tomcat tomcat = getTomcatInstance();

        tomcat.getConnector().setMaxPostSize(20);
        Assert.assertTrue(tomcat.getConnector().setProperty("connectionTimeout", "1000"));

        // No file system docBase required
        StandardContext ctx = (StandardContext) getProgrammaticRootContext();

        // Map the test Servlet
        ParameterParsingServlet parameterParsingServlet = new ParameterParsingServlet();
        Tomcat.addServlet(ctx, "parameterParsingServlet", parameterParsingServlet);
        ctx.addServletMappingDecoded("/", "parameterParsingServlet");

        tomcat.start();

        TestParameterClient client = new TestParameterClient();
        client.setPort(getPort());
        client.setRequest(new String[] { "POST / HTTP/1.1" + CRLF + "Host: localhost:" + getPort() + CRLF +
                "Connection: close" + CRLF + "Transfer-Encoding: chunked" + CRLF +
                SimpleHttpClient.HTTP_HEADER_CONTENT_TYPE_FORM_URL_ENCODING + CRLF + "0a" + CRLF +
                "var1=val1&" + CRLF });

        client.setResponseBodyEncoding(StandardCharsets.UTF_8);
        client.connect();
        // Incomplete request will look timeout reading body and behave like a client disconnect
        // What the client will see will vary by OS. Expect errors.

        try {
            client.processRequest();
        } catch (SocketException e) {
            // Likely a connection reset.
        }

        // Connection should be closed by the server.
        //readLine() will receive an EOF reading the status line resuting in a null
        Assert.assertNull(client.getResponseLine());
    }
}

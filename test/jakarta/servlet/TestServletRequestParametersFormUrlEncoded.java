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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.buf.ByteChunk;

@RunWith(Parameterized.class)
public class TestServletRequestParametersFormUrlEncoded extends ServletRequestParametersBaseTest {

    @Parameterized.Parameters(name = "{index}: chunked[{0}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        for (Boolean chunked : booleans) {
            parameterSets.add(new Object[] { chunked });
        }

        return parameterSets;
    }

    @Parameter(0)
    public boolean chunked;


    @Test
    public void testBodyTooLarge() throws Exception {

        Tomcat tomcat = getTomcatInstance();

        tomcat.getConnector().setMaxPostSize(20);

        // No file system docBase required
        StandardContext ctx = (StandardContext) getProgrammaticRootContext();

        // Map the test Servlet
        ParameterParsingServlet parameterParsingServlet = new ParameterParsingServlet();
        Tomcat.addServlet(ctx, "parameterParsingServlet", parameterParsingServlet);
        ctx.addServletMappingDecoded("/", "parameterParsingServlet");

        tomcat.start();

        TestParameterClient client = new TestParameterClient();
        client.setPort(getPort());
        if (chunked) {
            client.setRequest(new String[] {
                    "POST / HTTP/1.1" + CRLF +
                    "Host: localhost:" + getPort() + CRLF +
                    "Connection: close" + CRLF +
                    "Transfer-Encoding: chunked" + CRLF +
                    "Content-Type: application/x-www-form-urlencoded" + CRLF +
                    CRLF +
                    "0a" + CRLF +
                    "var1=val1&" + CRLF +
                    "0a" + CRLF +
                    "var2=val2&" + CRLF +
                    "0a" + CRLF +
                    "var3=val3&" + CRLF +
                    "0" + CRLF });
        } else {
            client.setRequest(new String[] {
                    "POST / HTTP/1.1" + CRLF +
                    "Host: localhost:" + getPort() + CRLF +
                    "Connection: close" + CRLF +
                    "Content-Length: 50" + CRLF +
                    "Content-Type: application/x-www-form-urlencoded" + CRLF +
                    CRLF +
                    "01234567890123456789012345678901234567890123456789" });
        }
        client.setResponseBodyEncoding(StandardCharsets.UTF_8);
        client.connect();
        client.processRequest();

        Assert.assertEquals(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, client.getStatusCode());
    }

    @Test
    public void testBug69442_lowercase_content_type() throws Exception {
        Tomcat tomcat = getTomcatInstanceTestWebapp(false, true);
        tomcat.start();
        ByteChunk bc = new ByteChunk();
        Map<String,List<String>> reqHeaders = new HashMap<String,List<String>>();
        reqHeaders.put("Content-Type", Arrays.asList("application/x-www-form-urlencoded"));
        postUrl("username=Tomcat1&usertype=biz".getBytes(),
                "http://localhost:" + getPort() + "/test/bug6nnnn/bug69442.jsp", bc, reqHeaders,
                new HashMap<String,List<String>>());
        String responseBody = bc.toString();
        Assert.assertTrue(responseBody.contains("username=Tomcat1"));
        Assert.assertTrue(responseBody.contains("usertype=biz"));
    }

    @Test
    public void testBug69442_uppercase_content_type() throws Exception {
        Tomcat tomcat = getTomcatInstanceTestWebapp(false, true);
        tomcat.start();
        ByteChunk bc = new ByteChunk();
        Map<String,List<String>> reqHeaders = new HashMap<String,List<String>>();
        reqHeaders.put("Content-Type", Arrays.asList("application/x-www-form-urlencoded".toUpperCase()));
        postUrl("username=Tomcat1&usertype=biz".getBytes(),
                "http://localhost:" + getPort() + "/test/bug6nnnn/bug69442.jsp", bc, reqHeaders,
                new HashMap<String,List<String>>());
        String responseBody = bc.toString();
        Assert.assertTrue(responseBody.contains("username=Tomcat1"));
        Assert.assertTrue(responseBody.contains("usertype=biz"));
    }
}

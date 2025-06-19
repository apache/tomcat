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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;

@RunWith(Parameterized.class)
public class TestServletRequestParametersMultipartEncoded extends ServletRequestParametersBaseTest {

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

        tomcat.getConnector().setMaxPostSize(50);

        // No file system docBase required
        StandardContext ctx = (StandardContext) getProgrammaticRootContext();
        ctx.setAllowCasualMultipartParsing(true);

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
                    "Content-Type: multipart/form-data; boundary=AaBbCc" + CRLF +
                    CRLF +
                    "3f" + CRLF +
                    "--AaBbCc" + CRLF +
                    "Content-Disposition: form-data; name=\"var1\"" + CRLF +
                    CRLF +
                    "val1" + CRLF +
                    CRLF +
                    "3f" + CRLF +
                    "--AaBbCc" + CRLF +
                    "Content-Disposition: form-data; name=\"var2\"" + CRLF +
                    CRLF +
                    "val2" + CRLF +
                    CRLF +
                    "3f" + CRLF +
                    "--AaBbCc" + CRLF +
                    "Content-Disposition: form-data; name=\"var3\"" + CRLF +
                    CRLF +
                    "val3" + CRLF +
                    CRLF +
                    "0a" + CRLF +
                    "--AaBbCc--" + CRLF +
                    "0" + CRLF +
                    CRLF});
        } else {
            client.setRequest(new String[] {
                    "POST / HTTP/1.1" + CRLF +
                    "Host: localhost:" + getPort() + CRLF +
                    "Connection: close" + CRLF +
                    "Content-Length: 199" + CRLF +
                    "Content-Type: multipart/form-data; boundary=AaBbCc" + CRLF +
                    CRLF +
                    "--AaBbCc" + CRLF +
                    "Content-Disposition: form-data; name=\"var1\"" + CRLF +
                    CRLF +
                    "val1" + CRLF +
                    "--AaBbCc" + CRLF +
                    "Content-Disposition: form-data; name=\"var2\"" + CRLF +
                    CRLF +
                    "val2" + CRLF +
                    "--AaBbCc" + CRLF +
                    "Content-Disposition: form-data; name=\"var3\"" + CRLF +
                    CRLF +
                    "val3" + CRLF +
                    "--AaBbCc--"});
        }
        client.setResponseBodyEncoding(StandardCharsets.UTF_8);
        client.connect();
        client.processRequest();

        Assert.assertEquals(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, client.getStatusCode());
    }

    @Test
    public void testIgnoreUnsupportedPartField() throws Exception {

        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        StandardContext ctx = (StandardContext) getProgrammaticRootContext();
        ctx.setAllowCasualMultipartParsing(true);

        // Map the test Servlet
        HttpServlet echoPartServlet = new EchoPartServlet();
        Tomcat.addServlet(ctx, "echoPartServlet", echoPartServlet);
        ctx.addServletMappingDecoded("/", "echoPartServlet");

        tomcat.start();

        TestParameterClient client = new TestParameterClient();
        client.setPort(getPort());
        if (chunked) {
            client.setRequest(new String[] {
                    "POST / HTTP/1.1" + CRLF +
                    "Host: localhost:" + getPort() + CRLF +
                    "Connection: close" + CRLF +
                    "Transfer-Encoding: chunked" + CRLF +
                    "Content-Type: multipart/form-data; boundary=AaBbCc" + CRLF +
                    CRLF +
                    "62" + CRLF +
                    "--AaBbCc" + CRLF +
                    "Content-Disposition: form-data; name=\"var1\"" + CRLF +
                    "Content-ID: Invalid-Part-Header01" + CRLF +
                    CRLF +
                    "val1" + CRLF +
                    CRLF +
                    "62" + CRLF +
                    "--AaBbCc" + CRLF +
                    "Content-Disposition: form-data; name=\"var2\"" + CRLF +
                    "X-SOURCEID: Invalid-Part-Header02" + CRLF +
                    CRLF +
                    "val2" + CRLF +
                    CRLF +
                    "62" + CRLF +
                    "--AaBbCc" + CRLF +
                    "Content-Disposition: form-data; name=\"var3\"" + CRLF +
                    "X-SOURCEID: Invalid-Part-Header03" + CRLF +
                    CRLF +
                    "val3" + CRLF +
                    CRLF +
                    "0a" + CRLF +
                    "--AaBbCc--" + CRLF +
                    "0" + CRLF +
                    CRLF});
        } else {
            String payload="--AaBbCc" + CRLF +
                    "Content-Disposition: form-data; name=\"var1\"" + CRLF +
                    "Content-ID: Invalid-Part-Header01" + CRLF +
                    CRLF +
                    "val1" + CRLF +
                    "--AaBbCc" + CRLF +
                    "Content-Disposition: form-data; name=\"var2\"" + CRLF +
                    "X-SOURCEID: Invalid-Part-Header02" + CRLF +
                    CRLF +
                    "val2" + CRLF +
                    "--AaBbCc" + CRLF +
                    "Content-Disposition: form-data; name=\"var3\"" + CRLF +
                    "X-SOURCEID: Invalid-Part-Header03" + CRLF +
                    CRLF +
                    "val3" + CRLF +
                    "--AaBbCc--";
            client.setRequest(new String[] {
                    "POST / HTTP/1.1" + CRLF +
                    "Host: localhost:" + getPort() + CRLF +
                    "Connection: close" + CRLF +
                    "Content-Length: "+payload.length() + CRLF +
                    "Content-Type: multipart/form-data; boundary=AaBbCc" + CRLF +
                    CRLF +payload});
        }
        client.setResponseBodyEncoding(StandardCharsets.UTF_8);
        client.connect();
        client.processRequest();

        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        String bodyInLowercase = client.getResponseBody().toLowerCase();
        Assert.assertTrue(bodyInLowercase.contains("Content-Disposition".toLowerCase()));
        Assert.assertTrue(client.getResponseBody().contains("form-data; name=\"var3\""));
        Assert.assertFalse("Per rfc7578 section 4.8, supported fields of part are limited",client.getResponseBody().contains("Invalid-Part-Header"));
        Assert.assertFalse("Per rfc7578 section 4.8, supported fields of part are limited",bodyInLowercase.contains("Content-ID".toLowerCase()));
        Assert.assertFalse("Per rfc7578 section 4.8, supported fields of part are limited",bodyInLowercase.contains("X-SOURCEID".toLowerCase()));
    }

    private static class EchoPartServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            StringBuffer buf = new StringBuffer();
            for (Part part : req.getParts()) {
                buf.append(String.format("Part name:%s\n", part.getName()));
                for (String header : part.getHeaderNames()) {
                    for (String value : part.getHeaders(header)) {
                        buf.append(String.format("\tHeader: %s=%s\n", header, value));
                    }
                }
            }
            resp.getWriter().write(buf.toString());
            resp.getWriter().flush();
        }
    }
}

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
package org.apache.coyote.http11;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.ExpectationClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestHttp11OutputBuffer extends TomcatBaseTest {

    @Test
    public void testSendAck() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        Tomcat.addServlet(ctx, "echo", new EchoBodyServlet());
        ctx.addServletMappingDecoded("/echo", "echo");

        tomcat.start();

        ExpectationClient client = new ExpectationClient();

        client.setPort(tomcat.getConnector().getLocalPort());
        // Expected content doesn't end with a CR-LF so if it isn't chunked make
        // sure the content length is used as reading it line-by-line will fail
        // since there is no "line".
        client.setUseContentLength(true);

        client.connect();

        client.doRequestHeaders();
        Assert.assertTrue(client.isResponse100());

        client.doRequestBody();
        Assert.assertTrue(client.isResponse200());
        Assert.assertTrue(client.isResponseBodyOK());
    }


    @Test
    public void testHTTPHeaderBelow128() throws Exception {
        doTestHTTPHeaderValue("This should be OK", true);
    }


    @Test
    public void testHTTPHeader128To255() throws Exception {
        doTestHTTPHeaderValue("\u00A0 should be OK", true);
    }


    @Test
    public void testHTTPHeaderAbove255() throws Exception {
        doTestHTTPHeaderValue("\u0100 should fail", false);
    }


    private void doTestHTTPHeaderValue(String customHeaderValue, boolean valid) throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        Tomcat.addServlet(ctx, "header", new HeaderServlet(customHeaderValue));
        ctx.addServletMappingDecoded("/header", "header");

        tomcat.start();

        Map<String,List<String>> resHeaders = new HashMap<>();
        int rc = getUrl("http://localhost:" + getPort() + "/header", new ByteChunk(), resHeaders);

        if (valid) {
            Assert.assertEquals(HttpServletResponse.SC_OK, rc);
            List<String> values = resHeaders.get(HeaderServlet.CUSTOM_HEADER_NAME);
            Assert.assertNotNull(values);
            Assert.assertEquals(1, values.size());
            Assert.assertEquals(customHeaderValue, values.get(0));
        } else {
            Assert.assertEquals(HttpServletResponse.SC_OK, rc);
            List<String> values = resHeaders.get(HeaderServlet.CUSTOM_HEADER_NAME);
            Assert.assertNull(values);
        }
    }


    private static class HeaderServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private static final String CUSTOM_HEADER_NAME = "X-Test";

        private final String customHeaderValue;

        public HeaderServlet(String customHeaderValue) {
            this.customHeaderValue = customHeaderValue;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");

            resp.setHeader(CUSTOM_HEADER_NAME, customHeaderValue);

            resp.flushBuffer();
        }
    }
}

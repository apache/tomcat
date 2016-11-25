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
package org.apache.catalina.connector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.TestUtf8;
import org.apache.tomcat.util.buf.TestUtf8.Utf8TestCase;

public class TestInputBuffer extends TomcatBaseTest {

    @Test
    public void testUtf8Body() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context root = tomcat.addContext("", TEMP_DIR);
        Tomcat.addServlet(root, "Echo", new Utf8Echo());
        root.addServletMappingDecoded("/test", "Echo");

        tomcat.getConnector().setProperty("soTimeout", "300000");
        tomcat.start();

        for (Utf8TestCase testCase : TestUtf8.TEST_CASES) {
            String expected = null;
            if (testCase.invalidIndex == -1) {
                expected = testCase.outputReplaced;
            }
            doUtf8BodyTest(testCase.description, testCase.input, expected);
        }
    }


    @Test
    public void testBug60400() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context root = tomcat.addContext("", TEMP_DIR);
        Tomcat.addServlet(root, "Bug60400Servlet", new Bug60400Servlet());
        root.addServletMappingDecoded("/", "Bug60400Servlet");

        tomcat.getConnector().setProperty("appReadBufSize", "9000");
        tomcat.start();

        ByteChunk bc = new ByteChunk();
        byte[] requestBody = new byte[9500];
        Arrays.fill(requestBody, (byte) 1);
        int rc = postUrl(requestBody, "http://localhost:" + getPort() + "/", bc, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertEquals(requestBody.length, bc.getLength());
    }


    private void doUtf8BodyTest(String description, int[] input,
            String expected) throws Exception {

        byte[] bytes = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            bytes[i] = (byte) input[i];
        }

        ByteChunk bc = new ByteChunk();
        int rc = postUrl(bytes, "http://localhost:" + getPort() + "/test", bc,
                null);

        if (expected == null) {
            Assert.assertEquals(description, HttpServletResponse.SC_OK, rc);
            Assert.assertEquals(description, "FAILED", bc.toString());
        } else if (expected.length() == 0) {
            Assert.assertNull(description, bc.toString());
        } else {
            bc.setCharset(StandardCharsets.UTF_8);
            Assert.assertEquals(description, expected, bc.toString());
        }
    }


    private static class Utf8Echo extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            // Should use POST
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            req.setCharacterEncoding("UTF-8");
            Reader r = req.getReader();

            resp.setCharacterEncoding("UTF-8");
            resp.setContentType("text/plain");
            Writer w = resp.getWriter();

            try {
                // Copy one character at a time
                int c = r.read();
                while (c != -1) {
                    w.write(c);
                    c = r.read();
                }
                w.close();
            } catch (MalformedInputException mie) {
                resp.resetBuffer();
                w.write("FAILED");
            }
        }
    }


    private static class Bug60400Servlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            StringBuilder builder = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
            }
            resp.getWriter().print(builder);
        }
    }
}

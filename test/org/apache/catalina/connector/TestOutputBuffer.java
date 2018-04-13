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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

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

public class TestOutputBuffer extends TomcatBaseTest{

    /*
     * Expect that the buffered results are slightly slower since Tomcat now has
     * an internal buffer so an extra one just adds overhead.
     *
     * @see "https://bz.apache.org/bugzilla/show_bug.cgi?id=52328"
     */
    @Test
    public void testWriteSpeed() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context root = tomcat.addContext("", TEMP_DIR);

        for (int i = 1; i <= WritingServlet.EXPECTED_CONTENT_LENGTH; i*=10) {
            WritingServlet servlet = new WritingServlet(i);
            Tomcat.addServlet(root, "servlet" + i, servlet);
            root.addServletMappingDecoded("/servlet" + i, "servlet" + i);
        }

        tomcat.start();

        ByteChunk bc = new ByteChunk();

        for (int i = 1; i <= WritingServlet.EXPECTED_CONTENT_LENGTH; i*=10) {
            int rc = getUrl("http://localhost:" + getPort() +
                    "/servlet" + i, bc, null, null);
            Assert.assertEquals(HttpServletResponse.SC_OK, rc);
            Assert.assertEquals(
                    WritingServlet.EXPECTED_CONTENT_LENGTH, bc.getLength());

            bc.recycle();

            rc = getUrl("http://localhost:" + getPort() +
                    "/servlet" + i + "?useBuffer=y", bc, null, null);
            Assert.assertEquals(HttpServletResponse.SC_OK, rc);
            Assert.assertEquals(
                    WritingServlet.EXPECTED_CONTENT_LENGTH, bc.getLength());

            bc.recycle();
        }
    }

    @Test
    public void testBug52577() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context root = tomcat.addContext("", TEMP_DIR);

        Bug52577Servlet bug52577 = new Bug52577Servlet();
        Tomcat.addServlet(root, "bug52577", bug52577);
        root.addServletMappingDecoded("/", "bug52577");

        tomcat.start();

        ByteChunk bc = new ByteChunk();

        int rc = getUrl("http://localhost:" + getPort() + "/", bc, null, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertEquals("OK", bc.toString());
    }

    private static class WritingServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        protected static final int EXPECTED_CONTENT_LENGTH = 100000;

        private final String writeString;
        private final int writeCount;

        public WritingServlet(int writeLength) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < writeLength; i++) {
                sb.append('x');
            }
            writeString = sb.toString();
            writeCount = EXPECTED_CONTENT_LENGTH / writeLength;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            resp.setContentType("text/plain");
            resp.setCharacterEncoding("ISO-8859-1");

            Writer w = resp.getWriter();

            // Wrap with a buffer if necessary
            String useBufferStr = req.getParameter("useBuffer");
            if (useBufferStr != null) {
                w = new BufferedWriter(w);
            }

            long start = System.nanoTime();
            for (int i = 0; i < writeCount; i++) {
                w.write(writeString);
            }
            if (useBufferStr != null) {
                w.flush();
            }
            long lastRunNano = System.nanoTime() - start;

            System.out.println("Write length: " + writeString.length() +
                    ", Buffered: " + (useBufferStr == null ? "n" : "y") +
                    ", Time: " + lastRunNano + "ns");
        }
    }

    private static class Bug52577Servlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            Writer w = resp.getWriter();
            w.write("OK");
            resp.resetBuffer();
            w.write("OK");
        }
    }


    @Test
    public void testUtf8SurrogateBody() throws Exception {
        // Create test data. This is carefully constructed to trigger the edge
        // case. Small variations may cause the test to miss the edge case.
        StringBuffer sb = new StringBuffer();
        sb.append("a");

        for (int i = 0x10000; i < 0x11000; i++) {
            char[] chars = Character.toChars(i);
            sb.append(chars);
        }
        String data = sb.toString();

        Tomcat tomcat = getTomcatInstance();
        Context root = tomcat.addContext("", TEMP_DIR);
        Tomcat.addServlet(root, "Test", new Utf8WriteChars(data));
        root.addServletMappingDecoded("/test", "Test");

        tomcat.start();

        ByteChunk bc = new ByteChunk();
        getUrl("http://localhost:" + getPort() + "/test", bc, null);

        bc.setCharset(StandardCharsets.UTF_8);
        Assert.assertEquals(data, bc.toString());
    }


    private static class Utf8WriteChars extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final char[] chars;

        public Utf8WriteChars(String data) {
            chars = data.toCharArray();
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            resp.setCharacterEncoding("UTF-8");
            resp.setContentType("text/plain");
            Writer w = resp.getWriter();

            for (int i = 0; i < chars.length; i++) {
                w.write(chars[i]);
            }
        }
    }

}

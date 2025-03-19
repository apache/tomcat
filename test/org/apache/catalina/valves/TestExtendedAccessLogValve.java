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
package org.apache.catalina.valves;

import java.io.CharArrayWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

@RunWith(Parameterized.class)
public class TestExtendedAccessLogValve extends TomcatBaseTest {

    // Requests can return in the client before log() has been called
    private static final long SLEEP = 2;
    private static final long SLEEP_MAX = 21000;

    @Parameterized.Parameters(name = "{index}: pattern=[{0}]")
    public static Collection<Object[]> data() {
        List<Object[]> patterns = new ArrayList<>();
        patterns.add(new Object[]{"basic", "time cs-method cs-uri-stem cs-uri-query"});
        patterns.add(new Object[]{"ip", "time cs-method sc-status c-ip s-ip s-dns c-dns"});
        patterns.add(new Object[]{"headers", "time cs-method cs(Referer) cs(Cookie) sc(Content-Type)"});
        patterns.add(new Object[]{"bytes", "date time cs-method cs-uri bytes time-taken cached"});
        patterns.add(new Object[]{"time", "date time time-taken-ns time-taken-us time-taken-ms time-taken-fracsec time-taken-s"});
        patterns.add(new Object[]{"tomcat1", "x-threadname x-A(testSCAttr) x-C(COOKIE-1_3) x-O(Custom)"});
        patterns.add(new Object[]{"tomcat2", "x-R(testRAttr) x-S(sessionAttr) x-P(testParam)"});
        patterns.add(new Object[]{"tomcat3", "x-H(authType) x-H(characterEncoding) x-H(connectionId) x-H(contentLength)"});
        patterns.add(new Object[]{"tomcat4", "x-H(locale) x-H(protocol) x-H(remoteUser) x-H(requestedSessionId)"});
        patterns.add(new Object[]{"tomcat5", "x-H(requestedSessionIdFromCookie) x-H(requestedSessionIdValid) x-H(scheme) x-H(secure)"});
        return patterns;
    }

    @Parameter(0)
    public String name;

    @Parameter(1)
    public String logPattern;


    /**
     * Extend AbstractAccessLogValve to retrieve log output.
     */
    public final class TesterExtendedAccessLogValve extends ExtendedAccessLogValve {

        private CharArrayWriter writer;

        public TesterExtendedAccessLogValve(CharArrayWriter writer) {
            this.writer = writer;
        }

        @Override
        public void log(CharArrayWriter message) {
            try {
                message.writeTo(writer);
            } catch (IOException ex) {
                log.error("Could not write to writer", ex);
            }
        }
    }


    @Test
    public void testLogFormat() throws Exception {

        Tomcat tomcat = getTomcatInstance();
        Host host = tomcat.getHost();

        // Create temporary directory for logs
        File logDir = getTemporaryDirectory();

        CharArrayWriter writer = new CharArrayWriter();
        TesterExtendedAccessLogValve valve = new TesterExtendedAccessLogValve(writer);
        valve.setBuffered(false);
        valve.setPattern(logPattern);
        valve.setDirectory(logDir.getAbsolutePath());
        valve.setPrefix("access_log_" + name);

        host.getPipeline().addValve(valve);

        // Add test servlet
        Context ctx = getProgrammaticRootContext();
        Tomcat.addServlet(ctx, "testServlet", new HttpServlet() {
            private static final long serialVersionUID = 1L;
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                req.getServletContext().setAttribute("testSCAttr", "testSCAttrValue");
                req.getSession(true).setAttribute("sessionAttr", "sessionAttrValue");
                req.setAttribute("testRAttr", "testRValue");
                resp.addHeader("Custom", "value1");
                resp.addHeader("Custom", "value2");
                resp.getWriter().write("Test response");
            }
        });
        ctx.addServletMappingDecoded("/test", "testServlet");

        tomcat.start();

        String url = "http://localhost:" + getPort() + "/test?testParam=testValue";
        ByteChunk out = new ByteChunk();
        Map<String, List<String>> reqHead = new HashMap<>();
        List<String> cookieHeaders = new ArrayList<>();
        cookieHeaders.add("COOKIE-1_1=1_1;COOKIE-1_2=1_2;COOKIE-1_3=1_3");
        reqHead.put("Cookie", cookieHeaders);
        List<String> refererHeader = new ArrayList<>();
        refererHeader.add("/some/path");
        reqHead.put("Referer", refererHeader);
        List<String> contentTypeHeader = new ArrayList<>();
        contentTypeHeader.add("text/plain");
        reqHead.put("Content-Type", contentTypeHeader);
        Map<String, List<String>> resHead = new HashMap<>();
        int status = getUrl(url, out, reqHead, resHead);
        Assert.assertEquals(HttpServletResponse.SC_OK, status);

        long startWait = System.currentTimeMillis();
        String content = writer.toString();
        while (countLogLines(content) == 0 && System.currentTimeMillis() - startWait < SLEEP_MAX) {
            try {
                Thread.sleep(SLEEP);
            } catch (InterruptedException ex) {
                log.error("Exception during sleep", ex);
            }
            content = writer.toString();
        }

        processLogContent(content);
}


    private int countLogLines(String content) {
        int result = 0;
        String[] lines = content.split("\\r?\\n");
        for (String line : lines) {
            if (!line.startsWith("#") && !line.trim().isEmpty()) {
                result++;
            }
        }
        return result;
    }


    private void processLogContent(String content) {
        String[] lines = content.split("\\r?\\n");

        List<String> dataLines = new ArrayList<>();
        for (String line : lines) {
            if (!line.startsWith("#")) { // Skip directives
                dataLines.add(line.trim());
            } else {
                if (line.startsWith("#Fields: ")) {
                    Assert.assertEquals(line.substring("#Fields: ".length()), logPattern);
                }
            }
        }

        Assert.assertTrue("No data entries found", !dataLines.isEmpty());

        String entryLine = dataLines.get(0);
        System.out.println(name + ": " + entryLine);

        String[] parts = entryLine.split("\\s+");

        String[] expectedFields = logPattern.split("\\s+");

        Assert.assertEquals(expectedFields.length, parts.length);

        for (int i=0; i < expectedFields.length; i++) {
            checkField(expectedFields[i], parts[i]);
        }
    }


    private void checkField(String fieldId, String value) {
        if ("time".equals(fieldId)) {
            Assert.assertTrue("Invalid time format", isTimeFormat(value));
        } else if ("cs-method".equals(fieldId)) {
            Assert.assertEquals("GET", value);
        } else if (fieldId.startsWith("c-ip")) {
            // IPv4 with optional port
            Assert.assertTrue(value.matches("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?$"));
        } else if ("cs-uri-stem".equals(fieldId)) {
            Assert.assertEquals("/test", value);
        } else if (fieldId.equals("sc-status")) {
            Assert.assertEquals("200", value);
        } else if (fieldId.contains("Referer")) {
            Assert.assertTrue(value.equals("\"/some/path\""));
        } else if ("bytes".equals(fieldId)) {
            // Non-negative integer or '-'
            Assert.assertTrue(value.equals("-") || value.matches("\\d+"));
        } else if ("time-taken".equals(fieldId)) {
            // Fixed format (e.g., 0.015)
            Assert.assertTrue(value.matches("^\\d+(\\.\\d+)?$"));
        }
    }


    private boolean isTimeFormat(String s) {
        return Pattern.matches("^\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?$", s);
    }
}
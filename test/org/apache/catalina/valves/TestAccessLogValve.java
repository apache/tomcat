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
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.json.JSONParser;
import org.apache.tomcat.util.json.ParseException;


@RunWith(Parameterized.class)
public class TestAccessLogValve extends TomcatBaseTest {

    private static Log log = LogFactory.getLog(TestAccessLogValve.class);

    // Requests can return in the client before log() has been called
    private static final long SLEEP = 10;

    private static final String TEXT_TYPE = "text";
    private static final String JSON_TYPE = "json";

    private static final String DATE_PATTERN = "\\[\\d\\d/[A-Z][a-z][a-z]/\\d\\d\\d\\d:\\d\\d:\\d\\d:\\d\\d [-+]\\d\\d\\d\\d\\]";
    private static final String IP_PATTERN = "(127\\.0\\.\\d\\.\\d+|\\[::1\\])";
    private static final String UA_PATTERN = "[^\"]+";

    @Parameterized.Parameters(name = "{index}: Name[{0}], Type[{1}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        parameterSets.add(new Object[] {"pct-a", TEXT_TYPE, "/", "%a", IP_PATTERN});
        parameterSets.add(new Object[] {"pct-a", JSON_TYPE, "/", "%a", "\\{\"remoteAddr\":\"" + IP_PATTERN + "\"\\}"});
        parameterSets.add(new Object[] {"pct-A", TEXT_TYPE, "/", "%A", IP_PATTERN});
        parameterSets.add(new Object[] {"pct-A", JSON_TYPE, "/", "%A", "\\{\"localAddr\":\"" + IP_PATTERN + "\"\\}"});
        parameterSets.add(new Object[] {"pct-b", TEXT_TYPE, "/", "%b", "3"});
        parameterSets.add(new Object[] {"pct-b", JSON_TYPE, "/", "%b", "\\{\"size\":\"3\"\\}"});
        parameterSets.add(new Object[] {"pct-B", TEXT_TYPE, "/", "%B", "3"});
        parameterSets.add(new Object[] {"pct-B", JSON_TYPE, "/", "%B", "\\{\"byteSentNC\":\"3\"\\}"});
        parameterSets.add(new Object[] {"pct-D", TEXT_TYPE, "/", "%D", "\\d+"});
        parameterSets.add(new Object[] {"pct-D", JSON_TYPE, "/", "%D", "\\{\"elapsedTime\":\"\\d+\"\\}"});
        parameterSets.add(new Object[] {"pct-F", TEXT_TYPE, "/", "%F", "\\d+"});
        parameterSets.add(new Object[] {"pct-F", JSON_TYPE, "/", "%F", "\\{\"firstByteTime\":\"\\d+\"\\}"});
        parameterSets.add(new Object[] {"pct-h", TEXT_TYPE, "/", "%h", IP_PATTERN});
        parameterSets.add(new Object[] {"pct-h", JSON_TYPE, "/", "%h", "\\{\"host\":\"" + IP_PATTERN + "\"\\}"});
        parameterSets.add(new Object[] {"pct-H", TEXT_TYPE, "/", "%H", "HTTP/1.1"});
        parameterSets.add(new Object[] {"pct-H", JSON_TYPE, "/", "%H", "\\{\"protocol\":\"HTTP/1.1\"\\}"});
        parameterSets.add(new Object[] {"pct-I", TEXT_TYPE, "/", "%I", "http-nio2?-" + IP_PATTERN + "-auto-\\d+-exec-\\d+"});
        parameterSets.add(new Object[] {"pct-I", JSON_TYPE, "/", "%I", "\\{\"threadName\":\"http-nio2?-" + IP_PATTERN + "-auto-\\d+-exec-\\d+\"\\}"});
        parameterSets.add(new Object[] {"pct-l", TEXT_TYPE, "/", "%l", "-"});
        parameterSets.add(new Object[] {"pct-l", JSON_TYPE, "/", "%l", "\\{\"logicalUserName\":\"-\"\\}"});
        parameterSets.add(new Object[] {"pct-m", TEXT_TYPE, "/", "%m", "GET"});
        parameterSets.add(new Object[] {"pct-m", JSON_TYPE, "/", "%m", "\\{\"method\":\"GET\"\\}"});
        parameterSets.add(new Object[] {"pct-p", TEXT_TYPE, "/", "%p", "\\d+"});
        parameterSets.add(new Object[] {"pct-p", JSON_TYPE, "/", "%p", "\\{\"port\":\"\\d+\"\\}"});
        parameterSets.add(new Object[] {"pct-q", TEXT_TYPE, "/", "%q", ""});
        parameterSets.add(new Object[] {"pct-q", JSON_TYPE, "/", "%q", "\\{\"query\":\"\"\\}"});
        parameterSets.add(new Object[] {"pct-r", TEXT_TYPE, "/", "%r", "GET / HTTP/1.1"});
        parameterSets.add(new Object[] {"pct-r", JSON_TYPE, "/", "%r", "\\{\"request\":\"GET / HTTP/1.1\"\\}"});
        parameterSets.add(new Object[] {"pct-s", TEXT_TYPE, "/", "%s", "200"});
        parameterSets.add(new Object[] {"pct-s", JSON_TYPE, "/", "%s", "\\{\"statusCode\":\"200\"\\}"});
        parameterSets.add(new Object[] {"pct-S", TEXT_TYPE, "/", "%S", "[A-F0-9]{32}"});
        parameterSets.add(new Object[] {"pct-S", JSON_TYPE, "/", "%S", "\\{\"sessionId\":\"[A-F0-9]{32}\"\\}"});
        parameterSets.add(new Object[] {"pct-t", TEXT_TYPE, "/", "%t", DATE_PATTERN});
        parameterSets.add(new Object[] {"pct-t", JSON_TYPE, "/", "%t", "\\{\"time\":\"" + DATE_PATTERN + "\"\\}"});
        parameterSets.add(new Object[] {"pct-T", TEXT_TYPE, "/", "%T", "\\d+"});
        parameterSets.add(new Object[] {"pct-T", JSON_TYPE, "/", "%T", "\\{\"elapsedTimeS\":\"\\d+\"\\}"});
        parameterSets.add(new Object[] {"pct-u", TEXT_TYPE, "/", "%u", "-"});
        parameterSets.add(new Object[] {"pct-u", JSON_TYPE, "/", "%u", "\\{\"user\":\"-\"\\}"});
        parameterSets.add(new Object[] {"pct-U", TEXT_TYPE, "/", "%U", "/"});
        parameterSets.add(new Object[] {"pct-U", JSON_TYPE, "/", "%U", "\\{\"path\":\"/\"\\}"});
        parameterSets.add(new Object[] {"pct-v", TEXT_TYPE, "/", "%v", "localhost"});
        parameterSets.add(new Object[] {"pct-v", JSON_TYPE, "/", "%v", "\\{\"localServerName\":\"localhost\"\\}"});
        parameterSets.add(new Object[] {"pct-X", TEXT_TYPE, "/", "%X", "\\+"});
        parameterSets.add(new Object[] {"pct-X", JSON_TYPE, "/", "%X", "\\{\"connectionStatus\":\"\\+\"\\}"});
        parameterSets.add(new Object[] {"pct-a-remote", TEXT_TYPE, "/", "%{remote}a", IP_PATTERN});
        parameterSets.add(new Object[] {"pct-a-remote", JSON_TYPE, "/", "%{remote}a", "\\{\"remoteAddr-remote\":\"" + IP_PATTERN + "\"\\}"});
        parameterSets.add(new Object[] {"pct-a-peer", TEXT_TYPE, "/", "%{peer}a", IP_PATTERN});
        parameterSets.add(new Object[] {"pct-a-peer", JSON_TYPE, "/", "%{peer}a", "\\{\"remoteAddr-peer\":\"" + IP_PATTERN + "\"\\}"});
        parameterSets.add(new Object[] {"pct-p-local", TEXT_TYPE, "/", "%{local}p", "\\d+"});
        parameterSets.add(new Object[] {"pct-p-local", JSON_TYPE, "/", "%{local}p", "\\{\"port-local\":\"\\d+\"\\}"});
        parameterSets.add(new Object[] {"pct-p-remote", TEXT_TYPE, "/", "%{remote}p", "\\d+"});
        parameterSets.add(new Object[] {"pct-p-remote", JSON_TYPE, "/", "%{remote}p", "\\{\"port-remote\":\"\\d+\"\\}"});
        parameterSets.add(new Object[] {"pct-t-sec", TEXT_TYPE, "/", "%{sec}t", "\\d{10}"});
        parameterSets.add(new Object[] {"pct-t-sec", JSON_TYPE, "/", "%{sec}t", "\\{\"time-sec\":\"\\d{10}\"\\}"});
        parameterSets.add(new Object[] {"pct-t-msec", TEXT_TYPE, "/", "%{msec}t", "\\d{13}"});
        parameterSets.add(new Object[] {"pct-t-msec", JSON_TYPE, "/", "%{msec}t", "\\{\"time-msec\":\"\\d{13}\"\\}"});
        parameterSets.add(new Object[] {"pct-t-msec_frac", TEXT_TYPE, "/", "%{msec_frac}t", "\\d{3}"});
        parameterSets.add(new Object[] {"pct-t-msec_frac", JSON_TYPE, "/", "%{msec_frac}t", "\\{\"time-msec_frac\":\"\\d{3}\"\\}"});
        parameterSets.add(new Object[] {"pct-t-begin:sec", TEXT_TYPE, "/", "%{begin:sec}t", "\\d{10}"});
        parameterSets.add(new Object[] {"pct-t-begin:sec", JSON_TYPE, "/", "%{begin:sec}t", "\\{\"time-begin:sec\":\"\\d{10}\"\\}"});
        parameterSets.add(new Object[] {"pct-t-end:sec", TEXT_TYPE, "/", "%{end:sec}t", "\\d{10}"});
        parameterSets.add(new Object[] {"pct-t-end:sec", JSON_TYPE, "/", "%{end:sec}t", "\\{\"time-end:sec\":\"\\d{10}\"\\}"});
        parameterSets.add(new Object[] {"common", TEXT_TYPE, "/", "common",
            IP_PATTERN + " - - " + DATE_PATTERN + " \"GET / HTTP/1.1\" 200 3"});
        parameterSets.add(new Object[] {"common", JSON_TYPE, "/", "common",
            "\\{\"host\":\"" + IP_PATTERN + "\",\"logicalUserName\":\"-\",\"user\":\"-\",\"time\":\"" + DATE_PATTERN +
            "\",\"request\":\"GET / HTTP/1.1\",\"statusCode\":\"200\",\"size\":\"3\"\\}"});
        parameterSets.add(new Object[] {"combined", TEXT_TYPE, "/", "combined",
            IP_PATTERN + " - - " + DATE_PATTERN + " \"GET / HTTP/1.1\" 200 3 \"-\" \"" + UA_PATTERN + "\""});
        parameterSets.add(new Object[] {"combined", JSON_TYPE, "/", "combined",
            "\\{\"host\":\"" + IP_PATTERN + "\",\"logicalUserName\":\"-\",\"user\":\"-\",\"time\":\"" + DATE_PATTERN +
            "\",\"request\":\"GET / HTTP/1.1\",\"statusCode\":\"200\",\"size\":\"3\"" +
            ",\"requestHeaders\": \\{\"Referer\":\"-\",\"User-Agent\":\"" + UA_PATTERN + "\"\\}\\}"});

        return parameterSets;
    }

    @Parameter(0)
    public String name;

    @Parameter(1)
    public String type;

    @Parameter(2)
    public String path;

    @Parameter(3)
    public String logPattern;

    @Parameter(4)
    public String resultMatch;

    /**
     * Extend AbstractAccessLogValve to retrieve log output.
     */
    public final class TesterAccessLogValve extends AbstractAccessLogValve {

        private CharArrayWriter writer;

        public TesterAccessLogValve(CharArrayWriter writer) {
            this.writer = writer;
        }

        /**
         * Log the specified message to the log file, switching files if
         * the date has changed since the previous log call.
         *
         * @param message Message to be logged
         */
        @Override
        public void log(CharArrayWriter message) {
            try {
                message.writeTo(writer);
            } catch (IOException ex) {
                log.error("Could not write to writer", ex);
            }
        }
    }

    /**
     * Extend JsonAccessLogValve to retrieve log output.
     */
    public final class TesterJsonAccessLogValve extends JsonAccessLogValve {

        private CharArrayWriter writer;

        public TesterJsonAccessLogValve(CharArrayWriter writer) {
            this.writer = writer;
        }

        /**
         * Log the specified message to the log file, switching files if
         * the date has changed since the previous log call.
         *
         * @param message Message to be logged
         */
        @Override
        public void log(CharArrayWriter message) {
            try {
                message.writeTo(writer);
            } catch (IOException ex) {
                log.error("Could not write to writer", ex);
            }
        }
    }

    private static class TesterServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            req.getSession();
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            PrintWriter pw = resp.getWriter();
            pw.println("OK");
        }
    }

    @Test
    public void Test() throws LifecycleException, IOException {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        StandardContext ctx = (StandardContext) tomcat.addContext("", null);

        // Map the test Servlet
        TesterServlet servlet = new TesterServlet();
        Tomcat.addServlet(ctx, "servlet", servlet);
        ctx.addServletMappingDecoded("/", "servlet");

        CharArrayWriter writer = new CharArrayWriter();
        if (TEXT_TYPE.equals(type)) {
            TesterAccessLogValve valve = new TesterAccessLogValve(writer);
            valve.setPattern(logPattern);
            tomcat.getHost().getPipeline().addValve(valve);
        } else if (JSON_TYPE.equals(type)) {
            TesterJsonAccessLogValve valve = new TesterJsonAccessLogValve(writer);
            valve.setPattern(logPattern);
            tomcat.getHost().getPipeline().addValve(valve);
        } else {
            log.error("Unknown AccessLogValve type " + type);
            Assert.fail("Unknown AccessLogValve type " + type);
        }

        tomcat.start();

        String url = "http://localhost:" + getPort() + path;
        ByteChunk out = new ByteChunk();
        Map<String, List<String>> reqHead = new HashMap<>();
        Map<String, List<String>> resHead = new HashMap<>();
        int status = getUrl(url, out, reqHead, resHead);
        Assert.assertEquals(HttpServletResponse.SC_OK, status);
        try {
            Thread.currentThread().sleep(SLEEP);
        } catch (InterruptedException ex) {
            log.error("Exception during sleep", ex);
        }
        String result = writer.toString();
        boolean matches = Pattern.matches(resultMatch, result);
        if (!matches) {
            log.error("Resulting log line '" + result + "' does not match '" + resultMatch + "'");
        }
        Assert.assertTrue("Resulting log line '" + result + "' does not match '" + resultMatch + "'", matches);

        if (JSON_TYPE.equals(type)) {
            JSONParser parser = new JSONParser(result);
            try {
                parser.parse();
            } catch (ParseException ex) {
                log.error("Exception during Json result parsing", ex);
                Assert.fail("Could not parse Json result");
            }
        }
    }

}

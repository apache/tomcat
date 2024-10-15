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
import jakarta.servlet.http.HttpSession;

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
    private static final long SLEEP = 2;
    private static final long SLEEP_MAX = 1000;

    private static final String TEXT_TYPE = "text";
    private static final String JSON_TYPE = "json";

    private static final String RESPONSE = "OK\n";
    private static final String BYTES = Integer.toString(RESPONSE.length());

    private static final String REQUEST_HEADER = "myRequestHeader";
    private static final String REQUEST_HEADER_VALUE = "1 2 3 4 5 6 7 8 9";
    private static final String REQUEST_HEADER_VALUE_ENCODED = "1 2 3 4 5 6 7 8 9";

    private static final String RESPONSE_HEADER = "myResponseHeader";
    private static final String RESPONSE_HEADER_VALUE = "864\u00e4\u00f6\u00fc642";
    private static final String RESPONSE_HEADER_VALUE_ENCODED = "864\\\\u00e4\\\\u00f6\\\\u00fc642";

    private static final String REQUEST_ATTRIBUTE = "myRequestAttribute";
    private static final String REQUEST_ATTRIBUTE_VALUE = "987\u00e4\u00f6\u00fc654";
    private static final String REQUEST_ATTRIBUTE_VALUE_ENCODED = "987\\\\u00e4\\\\u00f6\\\\u00fc654";

    private static final String SESSION_ATTRIBUTE = "mySessionAttribute";
    private static final String SESSION_ATTRIBUTE_VALUE = "123\u00e4\u00f6\u00fc456";
    private static final String SESSION_ATTRIBUTE_VALUE_ENCODED = "123\\\\u00e4\\\\u00f6\\\\u00fc456";

    private static final String DATE_PATTERN = "\\[\\d\\d/[A-Z][a-z][a-z]/\\d\\d\\d\\d:\\d\\d:\\d\\d:\\d\\d [-+]\\d\\d\\d\\d\\]";
    private static final String LOCAL_IP_PATTERN = "(127\\.0\\.\\d\\.\\d+|\\[::1\\])";
    private static final String IP_PATTERN = "(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|([0-9a-fA-F]){1,4}(:([0-9a-fA-F]){1,4}){7})";
    private static final String UA_PATTERN = "[^\"]+";

    @Parameterized.Parameters(name = "{index}: Name[{0}], Type[{1}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        parameterSets.add(new Object[] {"pct-a", TEXT_TYPE, "/", "%a", LOCAL_IP_PATTERN});
        parameterSets.add(new Object[] {"pct-a", JSON_TYPE, "/", "%a", "\\{\"remoteAddr\":\"" + LOCAL_IP_PATTERN + "\"\\}"});
        parameterSets.add(new Object[] {"pct-A", TEXT_TYPE, "/", "%A", IP_PATTERN});
        parameterSets.add(new Object[] {"pct-A", JSON_TYPE, "/", "%A", "\\{\"localAddr\":\"" + IP_PATTERN + "\"\\}"});
        parameterSets.add(new Object[] {"pct-b", TEXT_TYPE, "/", "%b", BYTES});
        parameterSets.add(new Object[] {"pct-b", JSON_TYPE, "/", "%b", "\\{\"size\":\"" + BYTES + "\"\\}"});
        parameterSets.add(new Object[] {"pct-B", TEXT_TYPE, "/", "%B", BYTES});
        parameterSets.add(new Object[] {"pct-B", JSON_TYPE, "/", "%B", "\\{\"byteSentNC\":\"" + BYTES + "\"\\}"});
        parameterSets.add(new Object[] {"pct-D", TEXT_TYPE, "/", "%D", "\\d+"});
        parameterSets.add(new Object[] {"pct-D", JSON_TYPE, "/", "%D", "\\{\"elapsedTime\":\"\\d+\"\\}"});
        parameterSets.add(new Object[] {"pct-F", TEXT_TYPE, "/", "%F", "\\d+"});
        parameterSets.add(new Object[] {"pct-F", JSON_TYPE, "/", "%F", "\\{\"firstByteTime\":\"\\d+\"\\}"});
        parameterSets.add(new Object[] {"pct-h", TEXT_TYPE, "/", "%h", LOCAL_IP_PATTERN});
        parameterSets.add(new Object[] {"pct-h", JSON_TYPE, "/", "%h", "\\{\"host\":\"" + LOCAL_IP_PATTERN + "\"\\}"});
        parameterSets.add(new Object[] {"pct-H", TEXT_TYPE, "/", "%H", "HTTP/1.1"});
        parameterSets.add(new Object[] {"pct-H", JSON_TYPE, "/", "%H", "\\{\"protocol\":\"HTTP/1.1\"\\}"});
        parameterSets.add(new Object[] {"pct-I", TEXT_TYPE, "/", "%I", "http-nio2?-" + LOCAL_IP_PATTERN + "-auto-\\d+-exec-\\d+"});
        parameterSets.add(new Object[] {"pct-I", JSON_TYPE, "/", "%I", "\\{\"threadName\":\"http-nio2?-" + LOCAL_IP_PATTERN + "-auto-\\d+-exec-\\d+\"\\}"});
        parameterSets.add(new Object[] {"pct-l", TEXT_TYPE, "/", "%l", "-"});
        parameterSets.add(new Object[] {"pct-l", JSON_TYPE, "/", "%l", "\\{\"logicalUserName\":\"-\"\\}"});
        parameterSets.add(new Object[] {"pct-m", TEXT_TYPE, "/", "%m", "GET"});
        parameterSets.add(new Object[] {"pct-m", JSON_TYPE, "/", "%m", "\\{\"method\":\"GET\"\\}"});
        parameterSets.add(new Object[] {"pct-p", TEXT_TYPE, "/", "%p", "\\d+"});
        parameterSets.add(new Object[] {"pct-p", JSON_TYPE, "/", "%p", "\\{\"port\":\"\\d+\"\\}"});
        parameterSets.add(new Object[] {"pct-q", TEXT_TYPE, "/?data=123", "%q", "\\?data=123"});
        parameterSets.add(new Object[] {"pct-q", JSON_TYPE, "/?data=123", "%q", "\\{\"query\":\"\\?data=123\"\\}"});
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
        parameterSets.add(new Object[] {"pct-a-remote", TEXT_TYPE, "/", "%{remote}a", LOCAL_IP_PATTERN});
        parameterSets.add(new Object[] {"pct-a-remote", JSON_TYPE, "/", "%{remote}a", "\\{\"remoteAddr-remote\":\"" + LOCAL_IP_PATTERN + "\"\\}"});
        parameterSets.add(new Object[] {"pct-a-peer", TEXT_TYPE, "/", "%{peer}a", LOCAL_IP_PATTERN});
        parameterSets.add(new Object[] {"pct-a-peer", JSON_TYPE, "/", "%{peer}a", "\\{\"remoteAddr-peer\":\"" + LOCAL_IP_PATTERN + "\"\\}"});
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
        parameterSets.add(new Object[] {"pct-t-begin:umlaut_time_S", TEXT_TYPE, "/", "%{begin:'\u00c4'HH '\u00e4' mm '\u00f6' ss '\u00fc' SSS'\u00dc'}t", "\\\\u00c4\\d\\d \\\\u00e4 \\d\\d \\\\u00f6 \\d\\d \\\\u00fc \\d\\d\\d\\\\u00dc"});
        parameterSets.add(new Object[] {"pct-t-begin:umlaut_time_S", JSON_TYPE, "/", "%{begin:'\u00c4'HH '\u00e4' mm '\u00f6' ss '\u00fc' SSS'\u00dc'}t", "\\{\"time-begin:'\u00c4'HH '\u00e4' mm '\u00f6' ss '\u00fc' SSS'\u00dc'\":\"\\\\u00c4\\d\\d \\\\u00e4 \\d\\d \\\\u00f6 \\d\\d \\\\u00fc \\d\\d\\d\\\\u00dc\"\\}"});
        parameterSets.add(new Object[] {"common", TEXT_TYPE, "/", "common",
            LOCAL_IP_PATTERN + " - - " + DATE_PATTERN + " \"GET / HTTP/1.1\" 200 " + BYTES});
        parameterSets.add(new Object[] {"common", JSON_TYPE, "/", "common",
            "\\{\"host\":\"" + LOCAL_IP_PATTERN + "\",\"logicalUserName\":\"-\",\"user\":\"-\",\"time\":\"" + DATE_PATTERN +
            "\",\"request\":\"GET / HTTP/1.1\",\"statusCode\":\"200\",\"size\":\"" + BYTES + "\"\\}"});
        parameterSets.add(new Object[] {"combined", TEXT_TYPE, "/", "combined",
            LOCAL_IP_PATTERN + " - - " + DATE_PATTERN + " \"GET / HTTP/1.1\" 200 " + BYTES + " \"-\" \"" + UA_PATTERN + "\""});
        parameterSets.add(new Object[] {"combined", JSON_TYPE, "/", "combined",
            "\\{\"host\":\"" + LOCAL_IP_PATTERN + "\",\"logicalUserName\":\"-\",\"user\":\"-\",\"time\":\"" + DATE_PATTERN +
            "\",\"request\":\"GET / HTTP/1.1\",\"statusCode\":\"200\",\"size\":\"" + BYTES + "\"" +
            ",\"requestHeaders\": \\{\"Referer\":\"-\",\"User-Agent\":\"" + UA_PATTERN + "\"\\}\\}"});
        parameterSets.add(new Object[] {"verbatim-text", TEXT_TYPE, "/", "123\u00e4\u00f6%s\u00fc%b%D\u00dc456", "123\u00e4\u00f6200\u00fc" + BYTES + "\\d+\u00dc456"});
        parameterSets.add(new Object[] {"verbatim-text", JSON_TYPE, "/", "123\u00e4\u00f6%s\u00fc%b%D\u00dc456", "\\{\"statusCode\":\"200\",\"size\":\"" + BYTES + "\",\"elapsedTime\":\"\\d+\"\\}"});
        parameterSets.add(new Object[] {"merged-cookies", TEXT_TYPE, "/", "%{Cookie}i", "COOKIE-1_1=1_1; COOKIE-1_2=1_2; COOKIE-1_3=1_3,COOKIE-2_1=2_1;COOKIE-2_2=2_2;COOKIE-2_3=2_3"});
        parameterSets.add(new Object[] {"merged-cookies", JSON_TYPE, "/", "%{Cookie}i", "\\{\"requestHeaders\": \\{\"Cookie\":\"COOKIE-1_1=1_1; COOKIE-1_2=1_2; COOKIE-1_3=1_3,COOKIE-2_1=2_1;COOKIE-2_2=2_2;COOKIE-2_3=2_3\"\\}\\}"});
        parameterSets.add(new Object[] {"cookie", TEXT_TYPE, "/", "%{COOKIE-2_2}c", "2_2"});
        parameterSets.add(new Object[] {"cookie", JSON_TYPE, "/", "%{COOKIE-2_2}c", "\\{\"cookies\": \\{\"COOKIE-2_2\":\"2_2\"\\}\\}"});
        parameterSets.add(new Object[] {"request-header", TEXT_TYPE, "/", "%{" + REQUEST_HEADER + "}i", REQUEST_HEADER_VALUE_ENCODED});
        parameterSets.add(new Object[] {"request-header", JSON_TYPE, "/", "%{" + REQUEST_HEADER + "}i", "\\{\"requestHeaders\": \\{\"" + REQUEST_HEADER + "\":\"" + REQUEST_HEADER_VALUE_ENCODED + "\"\\}\\}"});
        parameterSets.add(new Object[] {"response-header", TEXT_TYPE, "/", "%{" + RESPONSE_HEADER + "}o", RESPONSE_HEADER_VALUE_ENCODED});
        parameterSets.add(new Object[] {"response-header", JSON_TYPE, "/", "%{" + RESPONSE_HEADER + "}o", "\\{\"responseHeaders\": \\{\"" + RESPONSE_HEADER + "\":\"" + RESPONSE_HEADER_VALUE_ENCODED + "\"\\}\\}"});
        parameterSets.add(new Object[] {"request-attribute", TEXT_TYPE, "/", "%{" + REQUEST_ATTRIBUTE + "}r", REQUEST_ATTRIBUTE_VALUE_ENCODED});
        parameterSets.add(new Object[] {"request-attribute", JSON_TYPE, "/", "%{" + REQUEST_ATTRIBUTE + "}r", "\\{\"requestAttributes\": \\{\"" + REQUEST_ATTRIBUTE + "\":\"" + REQUEST_ATTRIBUTE_VALUE_ENCODED + "\"\\}\\}"});
        parameterSets.add(new Object[] {"session-attribute", TEXT_TYPE, "/", "%{" + SESSION_ATTRIBUTE + "}s", SESSION_ATTRIBUTE_VALUE_ENCODED});
        parameterSets.add(new Object[] {"session-attribute", JSON_TYPE, "/", "%{" + SESSION_ATTRIBUTE + "}s", "\\{\"sessionAttributes\": \\{\"" + SESSION_ATTRIBUTE + "\":\"" + SESSION_ATTRIBUTE_VALUE_ENCODED + "\"\\}\\}"});

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
            req.setAttribute(REQUEST_ATTRIBUTE, REQUEST_ATTRIBUTE_VALUE);
            HttpSession session = req.getSession();
            session.setAttribute(SESSION_ATTRIBUTE, SESSION_ATTRIBUTE_VALUE);
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            resp.setHeader(RESPONSE_HEADER, RESPONSE_HEADER_VALUE);
            PrintWriter pw = resp.getWriter();
            pw.print(RESPONSE);
        }
    }

    @Test
    public void test() throws LifecycleException, IOException {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        StandardContext ctx = (StandardContext) getProgrammaticRootContext();

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
        List<String> cookieHeaders = new ArrayList<>();
        cookieHeaders.add("COOKIE-1_1=1_1; COOKIE-1_2=1_2; COOKIE-1_3=1_3");
        cookieHeaders.add("COOKIE-2_1=2_1;COOKIE-2_2=2_2;COOKIE-2_3=2_3");
        reqHead.put("Cookie", cookieHeaders);
        List<String> testHeaders = new ArrayList<>();
        testHeaders.add(REQUEST_HEADER_VALUE);
        reqHead.put(REQUEST_HEADER, testHeaders);
        int status = getUrl(url, out, reqHead, resHead);
        Assert.assertEquals(HttpServletResponse.SC_OK, status);
        long startWait = System.currentTimeMillis();
        String result = writer.toString();
        while ("".equals(result) && System.currentTimeMillis() - startWait < SLEEP_MAX) {
            try {
                Thread.sleep(SLEEP);
            } catch (InterruptedException ex) {
                log.error("Exception during sleep", ex);
            }
            result = writer.toString();
        }
        Assert.assertFalse("Access log line empty after " + (System.currentTimeMillis() - startWait) + " milliseconds", "".equals(result));
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

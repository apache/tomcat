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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.LifecycleException;
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
    private static final String IP_PATTERN = "(127\\.0\\.0\\.\\d+|\\[::1\\])";
    private static final String UA_PATTERN = "[^\"]+";

    @Parameterized.Parameters(name = "{index}: Name[{0}], Type[{1}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        parameterSets.add(new Object[] {"common", TEXT_TYPE, "/", "common",
            IP_PATTERN + " - - " + DATE_PATTERN + " \"GET / HTTP/1.1\" 404 700"});
        parameterSets.add(new Object[] {"common", JSON_TYPE, "/", "common",
            "\\{\"host\":\"" + IP_PATTERN + "\",\"logicalUserName\":\"-\",\"user\":\"-\",\"time\":\"" + DATE_PATTERN +
            "\",\"request\":\"GET / HTTP/1.1\",\"statusCode\":\"404\",\"size\":\"700\"\\}"});
        parameterSets.add(new Object[] {"combined", TEXT_TYPE, "/", "combined",
            IP_PATTERN + " - - " + DATE_PATTERN + " \"GET / HTTP/1.1\" 404 700 \"-\" \"" + UA_PATTERN + "\""});
        parameterSets.add(new Object[] {"combined", JSON_TYPE, "/", "combined",
            "\\{\"host\":\"" + IP_PATTERN + "\",\"logicalUserName\":\"-\",\"user\":\"-\",\"time\":\"" + DATE_PATTERN +
            "\",\"request\":\"GET / HTTP/1.1\",\"statusCode\":\"404\",\"size\":\"700\"" +
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

    @Test
    public void Test() {
        Tomcat tomcat = null;
        tomcat = getTomcatInstance();

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

        try {
            tomcat.start();
        } catch (LifecycleException ex) {
            log.error("Exception starting tomcat", ex);
        }

        ByteChunk res = null;
        try {
            res = getUrl("http://localhost:" + getPort() + path);
        } catch (IOException ex) {
            log.error("Exception retrieving response", ex);
            Assert.fail("Could not retrieve response");
        }
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

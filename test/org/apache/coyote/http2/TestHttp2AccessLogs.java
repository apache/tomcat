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
package org.apache.coyote.http2;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.AbstractAccessLogValve;

public class TestHttp2AccessLogs extends Http2TestBase {

    private static final int COUNT_LIMIT = 20;
    private static final long SLEEP = 50;

    private void configureAndStartWebApplication(CharArrayWriter writer) throws LifecycleException {
        Tomcat tomcat = getTomcatInstance();

        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");
        Wrapper w = Tomcat.addServlet(ctxt, "trailers", new TrailersServlet());
        w.setAsyncSupported(true);
        ctxt.addServletMappingDecoded("/trailers", "trailers");
        TesterAccessLogValve valve = new TesterAccessLogValve(writer);
        valve.setPattern("%{header-key}o %{trailer-key}o");
        tomcat.getHost().getPipeline().addValve(valve);
        tomcat.start();
    }


    @Test
    public void testHeadersAndTrailersInAccessLog() throws Exception {
        CharArrayWriter writer = new CharArrayWriter();

        enableHttp2(false);
        configureAndStartWebApplication(writer);
        openClientConnection(false);
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        // Validate the access log is as expected after the initial requests
        int count = 0;
        String result = writer.toString();
        String expectedInitial = "- -- -";
        while (count < COUNT_LIMIT && !expectedInitial.equals(result)) {
            try {
                Thread.sleep(SLEEP);
            } catch (InterruptedException ex) {
                log.error("Exception during sleep", ex);
            }
            result = writer.toString();
            count++;
        }
        Assert.assertEquals(result,  expectedInitial, result);
        writer.reset();

        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        byte[] dataFrameHeader = new byte[9];
        ByteBuffer dataPayload = ByteBuffer.allocate(256);
        byte[] trailerFrameHeader = new byte[9];
        ByteBuffer trailerPayload = ByteBuffer.allocate(256);

        buildPostRequest(headersFrameHeader, headersPayload, false, null, -1, "/trailers", dataFrameHeader, dataPayload,
                null, true, 3);
        buildTrailerHeaders(trailerFrameHeader, trailerPayload, 3);

        // Write the headers
        writeFrame(headersFrameHeader, headersPayload);
        // Body
        writeFrame(dataFrameHeader, dataPayload);

        parser.readFrame();
        parser.readFrame();

        String trace = output.getTrace();
        String[] lines = trace.split("\n");
        // Check the response code
        Assert.assertEquals("3-HeadersStart", lines[0]);
        Assert.assertEquals("3-Header-[:status]-[200]", lines[1]);

        // Validate the access log is as expected after the initial requests
        count = 0;
        result = writer.toString();
        String expected = "header-value -";
        while (count < COUNT_LIMIT && !expected.equals(result)) {
            try {
                Thread.sleep(SLEEP);
            } catch (InterruptedException ex) {
                log.error("Exception during sleep", ex);
            }
            result = writer.toString();
            count++;
        }
        Assert.assertEquals(result,  expected, result);
    }


    /**
     * Extend AbstractAccessLogValve to retrieve log output.
     */
    private final class TesterAccessLogValve extends AbstractAccessLogValve {

        private final CharArrayWriter writer;

        TesterAccessLogValve(CharArrayWriter writer) {
            this.writer = writer;
        }

        /**
         * Log the specified message to the log file, switching files if the date has changed since the previous log
         * call.
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


    private static class TrailersServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setTrailerFields(() -> {
                Map<String,String> trailers = new HashMap<>();
                trailers.put("trailer-key", "trailer-value");
                return trailers;
            });
            resp.addHeader("header-key", "header-value");
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getOutputStream().close();
        }
    }
}
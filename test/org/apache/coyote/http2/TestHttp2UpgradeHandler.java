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
package org.apache.coyote.http2;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;

public class TestHttp2UpgradeHandler extends Http2TestBase {

    // https://bz.apache.org/bugzilla/show_bug.cgi?id=60970
    @Test
    public void testLargeHeader() throws Exception {
        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        Context ctxt = tomcat.addContext("", null);
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");
        Tomcat.addServlet(ctxt, "large", new LargeHeaderServlet());
        ctxt.addServletMappingDecoded("/large", "large");

        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildGetRequest(frameHeader, headersPayload, null, 3, "/large");
        writeFrame(frameHeader, headersPayload);

        // Headers
        parser.readFrame();
        parser.readFrame();
        // Body
        parser.readFrame();

        Assert.assertEquals(
                "3-HeadersStart\n" +
                "3-Header-[:status]-[200]\n" +
                "3-Header-[x-ignore]-[...]\n" +
                "3-Header-[content-type]-[text/plain;charset=UTF-8]\n" +
                "3-Header-[content-length]-[2]\n" +
                "3-Header-[date]-[Wed, 11 Nov 2015 19:18:42 GMT]\n" +
                "3-HeadersEnd\n" +
                "3-Body-2\n" +
                "3-EndOfStream\n", output.getTrace());
    }


    @Test
    public void testUpgradeWithRequestBodyGet() throws Exception {
        doTestUpgradeWithRequestBody(false, false, false);
    }


    @Test
    public void testUpgradeWithRequestBodyGetTooBig() throws Exception {
        doTestUpgradeWithRequestBody(false, false, true);
    }


    @Test
    public void testUpgradeWithRequestBodyPost() throws Exception {
        doTestUpgradeWithRequestBody(true, false, false);
    }


    @Test
    public void testUpgradeWithRequestBodyPostTooBig() throws Exception {
        doTestUpgradeWithRequestBody(true, false, true);
    }


    @Test
    public void testUpgradeWithRequestBodyGetReader() throws Exception {
        doTestUpgradeWithRequestBody(false, true, false);
    }


    @Test
    public void testUpgradeWithRequestBodyGetReaderTooBig() throws Exception {
        doTestUpgradeWithRequestBody(false, true, true);
    }


    @Test
    public void testUpgradeWithRequestBodyPostReader() throws Exception {
        doTestUpgradeWithRequestBody(true, true, false);
    }


    @Test
    public void testUpgradeWithRequestBodyPostReaderTooBig() throws Exception {
        doTestUpgradeWithRequestBody(true, true, true);
    }


    private void doTestUpgradeWithRequestBody(boolean usePost, boolean useReader, boolean tooBig) throws Exception {
        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        Context ctxt = tomcat.addContext("", null);
        Tomcat.addServlet(ctxt, "ReadRequestBodyServlet", new ReadRequestBodyServlet());
        ctxt.addServletMappingDecoded("/", "ReadRequestBodyServlet");

        if (tooBig) {
            // Reduce maxSavePostSize rather than use a larger request body
            tomcat.getConnector().setProperty("maxSavePostSize", "10");
        }
        tomcat.start();

        openClientConnection();

        byte[] upgradeRequest = ((usePost ? "POST" : "GET") +
                " /" + (useReader ? "?useReader=true " : " ") + "HTTP/1.1\r\n" +
                "Host: localhost:" + getPort() + "\r\n" +
                "Content-Length: 18\r\n" +
                "Connection: Upgrade,HTTP2-Settings\r\n" +
                "Upgrade: h2c\r\n" +
                EMPTY_HTTP2_SETTINGS_HEADER +
                "\r\n" +
                "Small request body").getBytes(StandardCharsets.ISO_8859_1);
        os.write(upgradeRequest);
        os.flush();

        if (tooBig) {
            String[] responseHeaders = readHttpResponseHeaders();
            Assert.assertNotNull(responseHeaders);
            Assert.assertNotEquals(0, responseHeaders.length);
            Assert.assertEquals("HTTP/1.1 413 ", responseHeaders[0]);
        } else {
            Assert.assertTrue("Failed to read HTTP Upgrade response", readHttpUpgradeResponse());

            sendClientPreface();

            // - 101 response acts as acknowledgement of the HTTP2-Settings header
            // Need to read 5 frames
            // - settings (server settings - must be first)
            // - settings ack (for the settings frame in the client preface)
            // - ping
            // - headers (for response)
            // - data (for response body)
            parser.readFrame();
            parser.readFrame();
            parser.readFrame();
            parser.readFrame();
            parser.readFrame();

            Assert.assertEquals("0-Settings-[3]-[200]\n" +
                    "0-Settings-End\n" +
                    "0-Settings-Ack\n" +
                    "0-Ping-[0,0,0,0,0,0,0,1]\n" +
                    "1-HeadersStart\n" +
                    "1-Header-[:status]-[200]\n" +
                    "1-Header-[content-type]-[text/plain;charset=UTF-8]\n" +
                    "1-Header-[content-length]-[39]\n" +
                    "1-Header-[date]-[" + DEFAULT_DATE + "]\n" +
                    "1-HeadersEnd\n" +
                    "1-Body-39\n" +
                    "1-EndOfStream\n"
                    , output.getTrace());
        }
    }
}

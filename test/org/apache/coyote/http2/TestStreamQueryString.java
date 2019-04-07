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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.buf.HexUtils;

/*
 * See https://bz.apache.org/bugzilla/show_bug.cgi?id=60482
 */
@RunWith(Parameterized.class)
public class TestStreamQueryString extends Http2TestBase {

    @Parameters
    public static Collection<Object[]> inputs() {
        List<Object[]> result = new ArrayList<>();
        // Test ASCII characters from 32 to 126 inclusive
        for (int i = 32; i < 128; i++) {
            result.add(new String[] { "%" + HexUtils.toHexString(new byte[] { (byte) i})});
        }
        return result;
    }


    private final String queryValueToTest;


    public TestStreamQueryString(String queryValueToTest) {
        this.queryValueToTest = queryValueToTest;
    }


    @Test
    public void testQueryString() throws Exception {
        String queryValue = "xxx" + queryValueToTest + "xxx";

        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        Context ctxt = tomcat.addContext("", null);
        Tomcat.addServlet(ctxt, "query", new Query(queryValue));
        ctxt.addServletMappingDecoded("/query", "query");

        tomcat.start();

        openClientConnection();
        doHttpUpgrade(queryValue);
        sendClientPreface();
        validateHttp2InitialResponse();

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildGetRequest(frameHeader, headersPayload, null, 3,
                "/query?" + Query.PARAM_NAME + "=" + queryValue);
        writeFrame(frameHeader, headersPayload);

        readSimpleGetResponse();

        Assert.assertEquals(queryValue,
                "3-HeadersStart\n" +
                "3-Header-[:status]-[200]\n" +
                "3-Header-[content-type]-[text/plain;charset=UTF-8]\n" +
                "3-Header-[content-length]-[2]\n" +
                "3-Header-[date]-[Wed, 11 Nov 2015 19:18:42 GMT]\n" +
                "3-HeadersEnd\n" +
                "3-Body-2\n" +
                "3-EndOfStream\n", output.getTrace());
    }


    protected void doHttpUpgrade(String queryValue) throws IOException {
        byte[] upgradeRequest = ("GET /query?" + Query.PARAM_NAME + "=" + queryValue + " HTTP/1.1\r\n" +
                "Host: localhost:" + getPort() + "\r\n" +
                "Connection: "+ DEFAULT_CONNECTION_HEADER_VALUE + "\r\n" +
                "Upgrade: h2c\r\n" +
                EMPTY_HTTP2_SETTINGS_HEADER +
                "\r\n").getBytes(StandardCharsets.ISO_8859_1);
        os.write(upgradeRequest);
        os.flush();

        Assert.assertTrue("Failed to read HTTP Upgrade response",
                readHttpUpgradeResponse());
    }


    @Override
    protected void validateHttp2InitialResponse() throws Exception {
        // - 101 response acts as acknowledgement of the HTTP2-Settings header
        // Need to read 5 frames
        // - settings (server settings - must be first)
        // - settings ack (for the settings frame in the client preface)
        // - ping
        // - headers (for response)
        // - data (for response body)
        parser.readFrame(true);
        parser.readFrame(true);
        parser.readFrame(true);
        parser.readFrame(true);
        parser.readFrame(true);

        Assert.assertEquals("0-Settings-[3]-[200]\n" +
                "0-Settings-End\n" +
                "0-Settings-Ack\n" +
                "0-Ping-[0,0,0,0,0,0,0,1]\n" +
                "1-HeadersStart\n" +
                "1-Header-[:status]-[200]\n" +
                "1-Header-[content-type]-[text/plain;charset=UTF-8]\n" +
                "1-Header-[content-length]-[2]\n" +
                "1-Header-[date]-[Wed, 11 Nov 2015 19:18:42 GMT]\n" +
                "1-HeadersEnd\n" +
                "1-Body-2\n" +
                "1-EndOfStream\n", output.getTrace());

        output.clearTrace();
    }


    private static final class Query extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private static final String PARAM_NAME = "param";

        private final String expectedValue;

        public Query(String expectedValue) {
            String decoded;
            try {
                decoded = URLDecoder.decode(expectedValue, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                // Can't happen with UTF-8
                decoded = null;
            }
            this.expectedValue = decoded;
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            response.setContentType("text/plain");
            response.setCharacterEncoding("UTF-8");

            if (expectedValue.equals(request.getParameter(PARAM_NAME))) {
                response.getWriter().write("OK");
            } else {
                response.getWriter().write("FAIL");
            }
        }
    }
}

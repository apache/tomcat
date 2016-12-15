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
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");
        Tomcat.addServlet(ctxt, "query", new Query(queryValue));
        ctxt.addServletMappingDecoded("/query", "query");

        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
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
                "3-Header-[date]-[Wed, 11 Nov 2015 19:18:42 GMT]\n" +
                "3-HeadersEnd\n" +
                "3-Body-2\n" +
                "3-EndOfStream\n", output.getTrace());
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

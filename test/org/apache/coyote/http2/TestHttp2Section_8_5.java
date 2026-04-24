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
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.http.Method;

/**
 * Unit tests for Section 8.5 of <a href="https://tools.ietf.org/html/rfc9113#section-8.5">RFC 9113</a>.
 */
public class TestHttp2Section_8_5 extends Http2TestBase {

    @Test
    public void testConnectWithScheme() throws Exception {
        testConnectWithHeader(":scheme", "http");
    }


    @Test
    public void testConnectWithPath() throws Exception {
        testConnectWithHeader(":path", "/should/not/be/present");
    }


    private void testConnectWithHeader(String headerName, String headerValue) throws Exception {
        http2Connect();

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        List<Header> headers = new ArrayList<>(4);
        headers.add(new Header(":method", Method.CONNECT));
        headers.add(new Header(":authority", "localhost:" + getPort()));
        headers.add(new Header(headerName, headerValue));

        buildGetRequest(frameHeader, headersPayload, null, headers, 3);

        writeFrame(frameHeader, headersPayload);

        parser.readFrame();

        Assert.assertEquals("3-RST-[1]\n", output.getTrace());
    }


    @Test
    public void testConnectWithoutAuthority() throws Exception {
        http2Connect();

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        List<Header> headers = new ArrayList<>(4);
        headers.add(new Header(":method", Method.CONNECT));

        buildGetRequest(frameHeader, headersPayload, null, headers, 3);

        writeFrame(frameHeader, headersPayload);

        parser.readFrame();

        Assert.assertEquals("3-RST-[1]\n", output.getTrace());
    }
}

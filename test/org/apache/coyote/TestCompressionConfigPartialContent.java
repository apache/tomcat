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
package org.apache.coyote;

import java.util.Enumeration;

import org.junit.Assert;
import org.junit.Test;

/**
 * The ranges in a Content-Range header field refer to the bytes of the representation (RFC 9110, section 14.4) so a
 * partial response must not be compressed with a content coding. A transfer coding applies to the message rather than
 * to the representation so it is not affected.
 */
public class TestCompressionConfigPartialContent {

    @Test
    public void testFullResponseContentEncoding() {
        Response response = new Response();

        Assert.assertTrue(useCompression("accept-encoding", response));
        Assert.assertEquals("gzip", response.getMimeHeaders().getHeader("Content-Encoding"));
    }


    @Test
    public void testPartialResponseContentEncoding() {
        Response response = new Response();
        response.setStatus(206);
        response.getMimeHeaders().addValue("Content-Range").setString("bytes 0-99/1000");

        Assert.assertFalse(useCompression("accept-encoding", response));
        Assert.assertNull(response.getMimeHeaders().getHeader("Content-Encoding"));
    }


    @Test
    public void testMultipartPartialResponseContentEncoding() {
        // The Content-Range headers are in the body parts. There is none in the response headers.
        Response response = new Response();
        response.setStatus(206);
        response.setContentType("multipart/byteranges; boundary=abc");

        Assert.assertFalse(useCompression("accept-encoding", response));
        Assert.assertNull(response.getMimeHeaders().getHeader("Content-Encoding"));
    }


    @Test
    public void testPartialResponseVary() {
        Response response = new Response();
        response.setStatus(206);
        response.getMimeHeaders().addValue("Content-Range").setString("bytes 0-99/1000");

        Assert.assertFalse(useCompression("accept-encoding", response));
        Assert.assertEquals(1, countVaryAcceptEncoding(response));
    }


    @Test
    public void testPartialResponseExistingVary() {
        Response response = new Response();
        response.setStatus(206);
        response.getMimeHeaders().addValue("Vary").setString("Accept-Language, accept-encoding");

        Assert.assertFalse(useCompression("accept-encoding", response));
        Assert.assertEquals(1, countVaryAcceptEncoding(response));
        String vary = response.getMimeHeaders().values("Vary").nextElement();
        Assert.assertTrue(vary.toLowerCase().contains("accept-language"));
    }


    @Test
    public void testPartialResponseTransferEncoding() {
        Response response = new Response();
        response.setStatus(206);
        response.getMimeHeaders().addValue("Content-Range").setString("bytes 0-99/1000");

        Assert.assertTrue(useCompression("TE", response));
        Assert.assertEquals("gzip", response.getMimeHeaders().getHeader("Transfer-Encoding"));
    }


    @Test
    public void testMultipartPartialResponseTransferEncoding() {
        Response response = new Response();
        response.setStatus(206);
        response.setContentType("multipart/byteranges; boundary=abc");

        Assert.assertTrue(useCompression("TE", response));
        Assert.assertEquals("gzip", response.getMimeHeaders().getHeader("Transfer-Encoding"));
    }


    private static int countVaryAcceptEncoding(Response response) {
        int count = 0;
        Enumeration<String> values = response.getMimeHeaders().values("Vary");
        while (values.hasMoreElements()) {
            for (String token : values.nextElement().split(",")) {
                if ("accept-encoding".equalsIgnoreCase(token.trim())) {
                    count++;
                }
            }
        }
        return count;
    }


    private static boolean useCompression(String requestHeaderName, Response response) {
        CompressionConfig compressionConfig = new CompressionConfig();
        // Skip length and MIME type checks
        compressionConfig.setCompression("force");

        Request request = new Request();
        request.getMimeHeaders().addValue(requestHeaderName).setString("gzip");

        return compressionConfig.useCompression(request, response);
    }
}

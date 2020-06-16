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

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

/*
 * Implement here rather than jakarta.servlet.http.TestHttpServley because it
 * needs access to package private classes.
 */
public class TestHttpServlet extends Http2TestBase {

    @Test
    public void testUnimplementedMethodHttp2() throws Exception {
        http2Connect();

        // Build a POST request for a Servlet that does not implement POST
        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        byte[] dataFrameHeader = new byte[9];
        ByteBuffer dataPayload = ByteBuffer.allocate(0);

        buildPostRequest(headersFrameHeader, headersPayload, false, null, -1, "/empty", dataFrameHeader, dataPayload,
                null, null, null, 3);

        // Write the headers
        writeFrame(headersFrameHeader, headersPayload);
        // Body
        writeFrame(dataFrameHeader, dataPayload);

        parser.readFrame(true);
        parser.readFrame(true);

        String trace = output.getTrace();
        String[] lines = trace.split("\n");

        // Check the response code
        Assert.assertEquals("3-HeadersStart", lines[0]);
        Assert.assertEquals("3-Header-[:status]-[405]", lines[1]);
    }

}

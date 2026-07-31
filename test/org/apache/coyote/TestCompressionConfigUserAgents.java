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
package org.apache.coyote;

import org.junit.Assert;
import org.junit.Test;

public class TestCompressionConfigUserAgents {

    @Test
    public void testNoCompressionUserAgents() {
        CompressionConfig config = new CompressionConfig();
        config.setNoCompressionUserAgents("gorilla|MSIE|tigrus");

        Request request = new Request();
        request.getMimeHeaders().addValue("accept-encoding").setString("gzip");
        Response response;

        // Force mode (compressionLevel == 2) skips the user-agent check,
        // so use "on" mode where the check applies
        config.setCompression("on");

        // User-agent matching the pattern should not be compressed
        response = createResponse();
        request.getMimeHeaders().addValue("user-agent").setString("Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1)");
        Assert.assertFalse(config.useCompression(request, response));

        // No user-agent header should be compressed
        response = createResponse();
        request.getMimeHeaders().removeHeader("user-agent");
        Assert.assertTrue(config.useCompression(request, response));

        // User-agent not matching the pattern should be compressed
        response = createResponse();
        request.getMimeHeaders().removeHeader("user-agent");
        request.getMimeHeaders().addValue("user-agent").setString("Mozilla/5.0 (X11; Linux x86_64)");
        Assert.assertTrue(config.useCompression(request, response));

        // Force mode skips the user-agent check
        response = createResponse();
        config.setCompression("force");
        request.getMimeHeaders().removeHeader("user-agent");
        request.getMimeHeaders().addValue("user-agent").setString("Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1)");
        Assert.assertTrue(config.useCompression(request, response));
    }

    private Response createResponse() {
        Response response = new Response();
        response.setContentLength(4096);
        response.setContentType("text/html");
        return response;
    }
}

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

package org.apache.tomcat.integration.httpd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Valve;
import org.apache.catalina.startup.BytesStreamer;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestChunkedTransferEncodingWithProxy extends HttpdIntegrationBaseTest {

    private static final int PAYLOAD_SIZE = 10 * 1024 * 1024 * 100;

    private static final String HTTPD_CONFIG = """
                      LoadModule env_module modules/mod_env.so                                                                                                                                                                                                 \s
                      SetEnv proxy-sendchunked 1
                      LoadModule proxy_module modules/mod_proxy.so
                      LoadModule proxy_http_module modules/mod_proxy_http.so
                      ProxyPass /endpoint http://localhost:%{TOMCAT_PORT}/%{SERVLET_NAME}
                      ProxyPassReverse /endpoint http://localhost:%{TOMCAT_PORT}/%{SERVLET_NAME}
                  """;

    @Override
    protected List<Valve> getValveConfig() {
        return new ArrayList<>();
    }

    @Override
    protected String getHttpdConfig() {
        return HTTPD_CONFIG;
    }

    /**
     * Verify that chunked transfer encoding works correctly through the httpd reverse proxy
     * which sets proxy-sendchunked to minimize resource usage by using chunked encoding.
     */
    @Test
    public void testChunkedTransferEncoding() throws Exception {
        byte[] payload = new byte[PAYLOAD_SIZE];
        Arrays.fill(payload, (byte) 'A');

        BytesStreamer streamer = new BytesStreamer() {
            private boolean sent = false;

            @Override
            public int getLength() {
                return -1;
            }

            @Override
            public int available() {
                return sent ? 0 : payload.length;
            }

            @Override
            public byte[] next() {
                sent = true;
                return payload;
            }
        };

        ByteChunk res = new ByteChunk();
        Map<String, List<String>> reqHead = new HashMap<>();
        reqHead.put("Content-Type", List.of("application/octet-stream"));
        int rc = postUrl(true, streamer, "http://localhost:" + getHttpdPort() + "/endpoint", res, reqHead, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        RequestDescriptor requestDesc = SnoopResult.parse(res.toString());
        Assert.assertEquals(String.valueOf(PAYLOAD_SIZE), requestDesc.getRequestInfo("REQUEST-BODY-SIZE"));
    }
}

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
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Valve;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestBasicProxy extends HttpdIntegrationBaseTest {

    private static final String HTTPD_CONFIG = """
                      LoadModule proxy_module modules/mod_proxy.so
                      LoadModule proxy_http_module modules/mod_proxy_http.so
                      LoadModule headers_module modules/mod_headers.so
                      ProxyRequests Off
                      ProxyPreserveHost On
                      ProxyPass /snoop http://localhost:%{TOMCAT_PORT}/snoop
                      ProxyPassReverse /snoop http://localhost:%{TOMCAT_PORT}/snoop
                      RequestHeader set X-Forwarded-For 140.211.11.130                                                                                                                                                                    \s
                      RequestHeader set X-Forwarded-Proto "http"
                """;

    @Override
    protected List<Valve> getValveConfig() {
        return new ArrayList<>();
    }

    @Override
    protected String getHttpdConfig() {
        return HTTPD_CONFIG;
    }

    @Test
    public void testBasicProxying() throws Exception {
        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getHttpdPort() + "/snoop", res, false);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        RequestDescriptor requestDesc = SnoopResult.parse(res.toString());

        Assert.assertNotNull(requestDesc.getRequestInfo());
        Assert.assertEquals("127.0.0.1", requestDesc.getRequestInfo("REQUEST-REMOTE-ADDR"));
        Assert.assertEquals(getHttpdPort(), Integer.valueOf(requestDesc.getRequestInfo("REQUEST-SERVER-PORT")).intValue());
        Assert.assertEquals(getPort(), Integer.valueOf(requestDesc.getRequestInfo("REQUEST-LOCAL-PORT")).intValue());
        Assert.assertEquals("http", requestDesc.getRequestInfo("REQUEST-SCHEME"));
        Assert.assertEquals("false", requestDesc.getRequestInfo("REQUEST-IS-SECURE"));
        Assert.assertNotNull(requestDesc.getHeaders());
        Assert.assertNotNull(requestDesc.getHeader("X-Forwarded-For"));
        Assert.assertEquals("http", requestDesc.getHeader("X-Forwarded-Proto"));
    }
}

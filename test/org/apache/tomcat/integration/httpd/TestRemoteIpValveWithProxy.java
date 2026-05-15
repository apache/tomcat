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
import org.apache.catalina.valves.RemoteIpValve;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestRemoteIpValveWithProxy extends HttpdIntegrationBaseTest {
    private static final String HTTPD_CONFIG = """
                      LoadModule proxy_module modules/mod_proxy.so
                      LoadModule proxy_http_module modules/mod_proxy_http.so
                      LoadModule headers_module modules/mod_headers.so
                      ProxyRequests Off
                      ProxyPreserveHost On
                      ProxyPass /endpoint http://localhost:%{TOMCAT_PORT}/%{SERVLET_NAME}
                      ProxyPassReverse /endpoint http://localhost:%{TOMCAT_PORT}/%{SERVLET_NAME}
                      ProxyAddHeaders Off
                      RequestHeader set X-Forwarded-For 140.211.11.130
                      RequestHeader set X-Forwarded-Proto https
                      RequestHeader set X-Forwarded-Host whoamI.tomcat
                """;

    @Override
    protected List<Valve> getValveConfig() {
        List<Valve> valves = new ArrayList<>();

        RemoteIpValve remoteIpValve = new RemoteIpValve();
        remoteIpValve.setHostHeader("X-Forwarded-Host");
        valves.add(remoteIpValve);

        return valves;
    }

    @Override
    protected String getHttpdConfig() {
        return HTTPD_CONFIG;
    }

    @Test
    public void testRemoteIpValveProxying() throws Exception {
        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getHttpdPort() + "/endpoint", res, false);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        RequestDescriptor requestDesc = SnoopResult.parse(res.toString());

        Assert.assertNotNull(requestDesc.getRequestInfo());
        Assert.assertEquals("140.211.11.130", requestDesc.getRequestInfo("REQUEST-REMOTE-ADDR"));
        Assert.assertEquals(443, Integer.valueOf(requestDesc.getRequestInfo("REQUEST-SERVER-PORT")).intValue());
        Assert.assertEquals(getPort(), Integer.valueOf(requestDesc.getRequestInfo("REQUEST-LOCAL-PORT")).intValue());
        Assert.assertEquals("https", requestDesc.getRequestInfo("REQUEST-SCHEME"));
        Assert.assertEquals("true", requestDesc.getRequestInfo("REQUEST-IS-SECURE"));
        Assert.assertEquals("whoamI.tomcat", requestDesc.getRequestInfo("REQUEST-SERVER-NAME"));
        Assert.assertNotNull(requestDesc.getHeaders());
        Assert.assertNull(requestDesc.getHeader("X-Forwarded-For"));
        Assert.assertEquals("https", requestDesc.getHeader("X-Forwarded-Proto"));
    }
}

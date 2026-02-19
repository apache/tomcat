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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Valve;
import org.apache.catalina.valves.RemoteIpValve;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.TesterSupport;

public class TestSessionWithProxy extends HttpdIntegrationBaseTest {
    private static final String HTTPD_CONFIG = """
                    LoadModule proxy_module modules/mod_proxy.so
                    LoadModule proxy_http_module modules/mod_proxy_http.so
                    LoadModule headers_module modules/mod_headers.so
                    LoadModule ssl_module modules/mod_ssl.so
                    SSLSessionCache none
                    ProxyPass /endpoint http://localhost:%{TOMCAT_PORT}/%{SERVLET_NAME}
                    ProxyPassReverse /endpoint http://localhost:%{TOMCAT_PORT}/%{SERVLET_NAME}
                    Listen %{HTTPD_SSL_PORT} https
                    <VirtualHost *:%{HTTPD_SSL_PORT}>
                      ServerName localhost:%{HTTPD_SSL_PORT}
                      SSLEngine on
                      SSLCertificateFile "%{SSL_CERT_FILE}"
                      SSLCertificateKeyFile "%{SSL_KEY_FILE}"
                      ProxyPass /endpoint http://localhost:%{TOMCAT_PORT}/%{SERVLET_NAME}
                      ProxyPassReverse /endpoint http://localhost:%{TOMCAT_PORT}/%{SERVLET_NAME}
                      RequestHeader set X-Forwarded-Proto https
                    </VirtualHost>
              """;

    @Override
    protected List<Valve> getValveConfig() {
        List<Valve> valves = new ArrayList<>();

        RemoteIpValve remoteIpValve = new RemoteIpValve();
        valves.add(remoteIpValve);

        return valves;
    }

    @Override
    protected String getHttpdConfig() {
        return HTTPD_CONFIG;
    }

    /**
     * Verify that a session created through httpd can be retrieved
     * on a subsequent request using the session cookie.
     */
    @Test
    public void testSessionCookieSetAndRetrieved() throws Exception {
        // Create a session
        ByteChunk res = new ByteChunk();
        Map<String, List<String>> resHead = new HashMap<>();
        int rc = getUrl("http://localhost:" + getHttpdPort() + "/endpoint?createSession=true", res, null, resHead);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        RequestDescriptor requestDesc = SnoopResult.parse(res.toString());
        Assert.assertNotNull(requestDesc.getRequestInfo());
        String sessionId = requestDesc.getRequestInfo("SESSION-ID");
        Assert.assertNotNull(sessionId);
        Assert.assertEquals("true", requestDesc.getRequestInfo("SESSION-IS-NEW"));

        String setCookie = resHead.get("Set-Cookie").get(0);
        Assert.assertTrue(setCookie.contains("JSESSIONID"));

        // Send the session cookie back
        Map<String, List<String>> reqHead = new HashMap<>();
        reqHead.put("Cookie", List.of("JSESSIONID=" + sessionId));
        rc = getUrl("http://localhost:" + getHttpdPort() + "/endpoint", res, reqHead, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        requestDesc = SnoopResult.parse(res.toString());
        Assert.assertNotNull(requestDesc.getRequestInfo());
        Assert.assertEquals(sessionId, requestDesc.getRequestInfo("SESSION-ID"));
        Assert.assertEquals("false", requestDesc.getRequestInfo("SESSION-IS-NEW"));
    }

    /**
     * Verify that when SSL is used at httpd, but not Tomcat, and RemoteIpValve
     * sets the scheme to https, session cookies have the Secure flag.
     */
    @Test
    public void testSecureCookieWithSslTermination() throws Exception {
        TesterSupport.configureClientSsl();
        ByteChunk res = new ByteChunk();
        Map<String, List<String>> resHead = new HashMap<>();
        getUrl("https://localhost:" + getHttpdSslPort() + "/endpoint?createSession=true", res, null, resHead);
        Assert.assertTrue("Session cookie should have Secure flag", resHead.get("Set-Cookie").get(0).contains("Secure"));
    }

}

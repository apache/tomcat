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

import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Globals;
import org.apache.catalina.Valve;
import org.apache.catalina.valves.SSLValve;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.TesterSupport;

public class TestSSLValveWithProxy01 extends HttpdIntegrationBaseTest {
    private static final String HTTPD_CONFIG =
                      "LoadModule proxy_module modules/mod_proxy.so\n" +
                      "LoadModule proxy_http_module modules/mod_proxy_http.so\n" +
                      "LoadModule headers_module modules/mod_headers.so\n" +
                      "LoadModule ssl_module modules/mod_ssl.so\n" +
                      "SSLSessionCache none\n" +
                      "Listen %{HTTPD_SSL_PORT} https\n" +
                      "<VirtualHost *:%{HTTPD_SSL_PORT}>\n" +
                        "ServerName localhost:%{HTTPD_SSL_PORT}\n" +
                        "SSLEngine on\n" +
                        "SSLCertificateFile \"%{SSL_CERT_FILE}\"\n" +
                        "SSLCertificateKeyFile \"%{SSL_KEY_FILE}\"\n" +
                        "ProxyRequests Off\n" +
                        "ProxyPass /endpoint http://localhost:%{TOMCAT_PORT}/%{SERVLET_NAME}\n" +
                        "ProxyPassReverse /endpoint http://localhost:%{TOMCAT_PORT}/%{SERVLET_NAME}\n" +
                        "RequestHeader set SSL_CLIENT_CERT \"%{SSL_CLIENT_CERT}s\"\n" +
                        "RequestHeader set SSL_CIPHER \"%{SSL_CIPHER}s\"\n" +
                        "RequestHeader set SSL_SESSION_ID \"%{SSL_SESSION_ID}s\"\n" +
                        "RequestHeader set SSL_CIPHER_USEKEYSIZE \"%{SSL_CIPHER_USEKEYSIZE}s\"\n" +
                      "</VirtualHost>"
                ;

    @Override
    protected List<Valve> getValveConfig() {
        List<Valve> valves = new ArrayList<>();

        SSLValve sslValve = new SSLValve();
        valves.add(sslValve);

        return valves;
    }

    @Override
    protected String getHttpdConfig() {
        return HTTPD_CONFIG;
    }

    @Test
    public void testSSLValveProxying() throws Exception {
        TesterSupport.configureClientSsl();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("https://localhost:" + getHttpdSslPort() + "/endpoint", res, false);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        RequestDescriptor requestDesc = SnoopResult.parse(res.toString());

        Assert.assertNotNull(requestDesc.getAttributes());
        Assert.assertNotNull(requestDesc.getAttribute(Globals.CIPHER_SUITE_ATTR));
        Assert.assertNotNull(requestDesc.getAttribute(Globals.SSL_SESSION_ID_ATTR));
        Assert.assertNotNull(requestDesc.getAttribute(Globals.KEY_SIZE_ATTR));
        // No client certificate in this test, mod_ssl sends null which SSLValve correctly treats as absent.
        Assert.assertNull(requestDesc.getAttribute(Globals.CERTIFICATES_ATTR));
    }
}

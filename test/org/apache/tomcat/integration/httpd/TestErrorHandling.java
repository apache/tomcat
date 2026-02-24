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

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Valve;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestErrorHandling extends HttpdIntegrationBaseTest {

    private static final String HTTPD_CONFIG = """
                      LoadModule proxy_module modules/mod_proxy.so
                      LoadModule proxy_http_module modules/mod_proxy_http.so
                      ProxyRequests Off
                      ProxyPreserveHost On
                      ProxyPass / http://localhost:%{TOMCAT_PORT}/
                      ProxyPassReverse / http://localhost:%{TOMCAT_PORT}/
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
    public void test404NotFound() throws Exception {
        int rc = getUrl("http://localhost:" + getHttpdPort() + "/nonexistent", new ByteChunk(), false);
        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);
    }

    @Test
    public void test500InternalError() throws Exception {
        Context ctx = (Context) getTomcatInstance().getHost().findChildren()[0];
        Tomcat.addServlet(ctx, "error", new HttpServlet() {
            @Serial
            private static final long serialVersionUID = 1L;

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
                throw new ServletException("Expected error");
            }
        });
        ctx.addServletMappingDecoded("/error", "error");
        int rc = getUrl("http://localhost:" + getHttpdPort() + "/error", new ByteChunk(), false);
        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);
    }

}

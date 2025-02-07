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
package org.apache.catalina.authenticator;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class TestSpnegoAuthenticator extends TomcatBaseTest {

    private static String CONTEXT_PATH = "/context";
    private static String URI = "/test";

    @Test
    public void testLoginThrowsServletException() throws IOException {
        ByteChunk res = getUrl("http://localhost:" + getPort() + CONTEXT_PATH + URI, "login");
        Assert.assertEquals(ServletException.class.getName(), res.toString());
    }

    @Test
    public void testLogoutThrowsUnsupportedOperationException() throws IOException {
        ByteChunk res = getUrl("http://localhost:" + getPort() + CONTEXT_PATH + URI, "logout");
        Assert.assertEquals(UnsupportedOperationException.class.getName(), res.toString());
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        Tomcat tomcat = getTomcatInstance();

        // Add  servlet
        Context ctxt = tomcat.addContext(CONTEXT_PATH, null);
        Tomcat.addServlet(ctxt, "SpnegoServlet", new SpnegoServlet());
        ctxt.addServletMappingDecoded(URI, "SpnegoServlet");

        // Configure the authenticator
        LoginConfig lc = new LoginConfig();
        lc.setAuthMethod("SPNEGO");
        ctxt.setLoginConfig(lc);
        ctxt.getPipeline().addValve(new SpnegoAuthenticator());

        tomcat.start();
    }

    private static final class SpnegoServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            try {
                if (req.getHeader("action").equals("login")) {
                    req.login("user", "pwd");
                } else {
                    req.logout();
                }
            } catch (ServletException | UnsupportedOperationException e) {
                String response = e.getClass().getName();
                resp.getWriter().print(response);
            }
        }
    }

    public static ByteChunk getUrl(String path, String param) throws IOException {
        ByteChunk out = new ByteChunk();
        getUrl(path, out, Map.of("action", List.of(param)), null);
        return out;
    }
}

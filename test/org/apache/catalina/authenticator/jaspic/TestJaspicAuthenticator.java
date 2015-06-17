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
package org.apache.catalina.authenticator.jaspic;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;

import javax.security.auth.message.config.AuthConfigFactory;
import javax.servlet.http.HttpServletResponse;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.authenticator.jaspic.sam.TestAuthConfigProvider;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;

public class TestJaspicAuthenticator extends TomcatBaseTest {

    private static String CONTEXT_PATH = "/foo";
    private static final String URI_PROTECTED = "/protected";
    private static final String ROLE = "group";
    private Context context;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        Tomcat tomcat = getTomcatInstance();
        this.context = tomcat.addContext(CONTEXT_PATH, null);

        // Add protected servlet
        Tomcat.addServlet(context, "TesterServlet3", new TesterServlet());
        context.addServletMapping(URI_PROTECTED, "TesterServlet3");
        SecurityCollection collection = new SecurityCollection();
        collection.addPattern(URI_PROTECTED);

        SecurityConstraint constraint = new SecurityConstraint();
        constraint.addAuthRole(ROLE);
        constraint.addCollection(collection);
        context.addConstraint(constraint);

        // Configure the authenticator
        LoginConfig loginConfig = new LoginConfig();
        loginConfig.setAuthMethod("JASPIC-BASIC");
        context.setLoginConfig(loginConfig);
        context.getPipeline().addValve(new JaspicAuthenticator());

        AuthConfigFactory factory = AuthConfigFactory.getFactory();
        factory.registerConfigProvider(new TestAuthConfigProvider(), "HttpServlet", null,
                "Description");
        getTomcatInstance().start();
    }

    @Test
    public void shouldAuthenticateUsingRegistredJaspicProvider() throws Exception {
        // given
        String url = getUrl() + URI_PROTECTED + "?doLogin=true";
        ByteChunk byteChunk = new ByteChunk();

        // when
        int result = getUrl(url, byteChunk, new HashMap<String, List<String>>());

        // then
        assertEquals(HttpServletResponse.SC_OK, result);
        assertEquals("OK", byteChunk.toString());
    }

    @Test
    public void shouldFailAuthenticationUsingRegistredJaspicProvider() throws Exception {
        // given
        String url = getUrl() + URI_PROTECTED;
        ByteChunk byteChunk = new ByteChunk();

        // when
        int result = getUrl(url, byteChunk, new HashMap<String, List<String>>());

        // then
        assertEquals(HttpServletResponse.SC_FORBIDDEN, result);
    }

    private String getUrl() throws MalformedURLException {
        return new URL("http", "localhost", getPort(), CONTEXT_PATH).toString();
    }

}

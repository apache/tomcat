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

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.security.auth.message.config.AuthConfigFactory;

import org.hamcrest.CoreMatchers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.authenticator.jaspic.JaspicAuthenticator;
import org.apache.catalina.authenticator.jaspic.provider.TomcatAuthConfigProvider;
import org.apache.catalina.connector.Request;
import org.apache.catalina.startup.TesterMapRealm;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.codec.binary.Base64;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;

public class TestJaspicBasicAuthenticator extends TomcatBaseTest {

    private static final String AUTH_METHOD = "JASPIC-BASIC";
    private static final String USER = "user";
    private static final String PASSWORD = "password";

    private static final String ROLE = "role";
    private static final String URI = "/protected";
    private static final String REALM = "TestRealm";

    private static final String WWW_AUTHENTICATE = "WWW-Authenticate";
    private static final String CLIENT_AUTH_HEADER = "Authorization";


    @Override
    public void setUp() throws Exception {
        super.setUp();

        Tomcat tomcat = getTomcatInstance();
        Context ctxt = tomcat.addContext("/", null);

        // Add protected servlet
        Tomcat.addServlet(ctxt, "TesterServlet", new TesterServlet());
        ctxt.addServletMapping(URI, "TesterServlet");
        SecurityCollection collection = new SecurityCollection();
        collection.addPattern(URI);
        SecurityConstraint sc = new SecurityConstraint();
        sc.addAuthRole(ROLE);
        sc.addCollection(collection);
        ctxt.addConstraint(sc);

        // Configure the Realm
        TesterMapRealm realm = new TesterMapRealm();
        realm.addUser(USER, PASSWORD);
        realm.addUserRole(USER, ROLE);
        ctxt.setRealm(realm);

        // Configure the authenticator
        LoginConfig lc = new LoginConfig();
        lc.setAuthMethod(AUTH_METHOD);
        lc.setRealmName(REALM);
        ctxt.setLoginConfig(lc);

        AuthConfigFactory authConfigFactory = AuthConfigFactory.getFactory();
        TomcatAuthConfigProvider provider = new TomcatAuthConfigProvider(ctxt);
        authConfigFactory.registerConfigProvider(provider, JaspicAuthenticator.MESSAGE_LAYER, null,
                "Tomcat Jaspic");
        ctxt.getPipeline().addValve(new JaspicAuthenticator());

        tomcat.start();
    }


    @Test
    public void shouldFailWithoutAuthenticationHeaders() throws Exception {
        // given
        Map<String, List<String>> requestHeaders = new HashMap<>();

        // when
        ResponseDescriptor response = getLocalhostUrl("/protected", requestHeaders);

        // then
        assertEquals(401, response.getResponseCode());
    }


    @Test
    public void shouldReturnCorrectRealmName() throws Exception {
        // given
        Map<String, List<String>> requestHeaders = new HashMap<>();

        // when
        ResponseDescriptor response = getLocalhostUrl("/protected", requestHeaders);

        // then
        assertEquals(401, response.getResponseCode());
        List<String> authenitcateHeaders = response.getHeaders().get(WWW_AUTHENTICATE);
        assertNotNull(authenitcateHeaders);

        String authenticationHeader = authenitcateHeaders.iterator().next();
        assertNotNull(authenticationHeader);

        assertThat(authenticationHeader, CoreMatchers.containsString("Basic"));
        assertThat(authenticationHeader, CoreMatchers.containsString(REALM));
    }


    @Test
    public void shouldSuccedOnCorrectAuthenticationHeaders() throws Exception {
        // given
        Map<String, List<String>> requestHeaders = new HashMap<>();

        List<String> auth = new ArrayList<>();
        auth.addAll(getBasicHeaders(USER, PASSWORD));
        requestHeaders.put(CLIENT_AUTH_HEADER, auth);

        // when
        ResponseDescriptor response = getLocalhostUrl("/protected", requestHeaders);

        // then
        assertEquals(200, response.getResponseCode());
    }


    @Test
    public void shouldFailWithIncorrectCredentials() throws Exception {
        // given
        Map<String, List<String>> reqHeaders = new HashMap<>();

        List<String> auth = new ArrayList<>();
        auth.addAll(getBasicHeaders(USER, "wrong password"));
        reqHeaders.put(CLIENT_AUTH_HEADER, auth);

        // when
        ResponseDescriptor response = getLocalhostUrl("/protected", reqHeaders);

        // then
        assertEquals(401, response.getResponseCode());
    }


    private Collection<String> getBasicHeaders(String username, String password) {
        List<String> basicHeaders = new ArrayList<>();
        basicHeaders.add("Basic " + encodeCredentials(username, password));
        return basicHeaders;
    }


    private String encodeCredentials(String username, String password) {
        String credentials = MessageFormat.format("{0}:{1}", username, password);
        return Base64.encodeBase64String(credentials.getBytes());
    }


    private ResponseDescriptor getLocalhostUrl(String url, Map<String, List<String>> requestHeaders)
            throws IOException {
        return getUrl("http://localhost:" + getPort() + url, requestHeaders);
    }


    private ResponseDescriptor getUrl(String url, Map<String, List<String>> requestHeaders)
            throws IOException {
        ByteChunk out = new ByteChunk();
        Map<String, List<String>> responseHeaders = new HashMap<>();
        int responseCode = getUrl(url, out, requestHeaders, responseHeaders);

        ResponseDescriptor testResponse = new ResponseDescriptor();
        testResponse.setBody(out.toString());
        testResponse.setResponseCode(responseCode);
        testResponse.setHeaders(responseHeaders);
        return testResponse;
    }

    private static class TesterRequest extends Request {

        @Override
        public String getRemoteAddr() {
            return "127.0.0.1";
        }
    }
}

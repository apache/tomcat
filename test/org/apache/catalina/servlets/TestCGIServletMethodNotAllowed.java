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
package org.apache.catalina.servlets;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

/**
 * A 405 response must include an Allow header (RFC 9110, section 15.5.6) that lists the methods the
 * {@link CGIServlet} currently accepts, which is the value it returns for an OPTIONS request. TRACE is only listed if
 * the connector allows it.
 */
public class TestCGIServletMethodNotAllowed extends TomcatBaseTest {

    @Test
    public void testDefaultMethodsTraceDisabled() throws Exception {
        doTest(null, false, "GET", "POST", "HEAD", "OPTIONS");
    }


    @Test
    public void testDefaultMethodsTraceEnabled() throws Exception {
        doTest(null, true, "GET", "POST", "HEAD", "OPTIONS", "TRACE");
    }


    @Test
    public void testConfiguredMethodsTraceDisabled() throws Exception {
        doTest("GET,PUT", false, "GET", "PUT", "HEAD", "OPTIONS");
    }


    @Test
    public void testConfiguredTraceDisabledByConnector() throws Exception {
        doTest("GET,TRACE", false, "GET", "HEAD", "OPTIONS");
    }


    @Test
    public void testConfiguredTraceEnabledByConnector() throws Exception {
        doTest("GET,TRACE", true, "GET", "HEAD", "OPTIONS", "TRACE");
    }


    private void doTest(String cgiMethods, boolean allowTrace, String... expectedAllow) throws Exception {
        Tomcat tomcat = getTomcatInstance();

        tomcat.getConnector().setAllowTrace(allowTrace);

        File docBase = new File(getTemporaryDirectory(), "cgi-method-not-allowed");
        Assert.assertTrue(docBase.mkdirs());
        addDeleteOnTearDown(docBase);

        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());
        ctx.setPrivileged(true);

        Wrapper w = Tomcat.addServlet(ctx, "cgi", new CGIServlet());
        if (cgiMethods != null) {
            w.addInitParameter("cgiMethods", cgiMethods);
        }
        ctx.addServletMapping("/*", "cgi");

        tomcat.start();

        Set<String> expected = new HashSet<>(Arrays.asList(expectedAllow));

        // None of the configurations allow DELETE
        AllowClient notAllowed = doRequest("DELETE");
        Assert.assertTrue(notAllowed.getResponseLine(), notAllowed.isResponse405());
        Assert.assertEquals(expected, notAllowed.getAllow());

        // The 405 response and the OPTIONS response must agree
        AllowClient options = doRequest("OPTIONS");
        Assert.assertTrue(options.getResponseLine(), options.isResponse200());
        Assert.assertEquals(expected, options.getAllow());
    }


    private AllowClient doRequest(String method) throws Exception {
        AllowClient client = new AllowClient();
        client.setPort(getPort());
        // @formatter:off
        client.setRequest(new String[] {
                method + " /test HTTP/1.1" + CRLF +
                    "Host: localhost:" + getPort() + CRLF +
                    "Content-Length: 0" + CRLF +
                    "Connection: close" + CRLF +
                    CRLF
                });
        // @formatter:on

        client.connect();
        client.processRequest();
        client.disconnect();

        return client;
    }


    private static class AllowClient extends SimpleHttpClient {

        @Override
        public boolean isResponseBodyOK() {
            return true;
        }

        public Set<String> getAllow() {
            for (String header : getResponseHeaders()) {
                if (header.toLowerCase(Locale.ENGLISH).startsWith("allow:")) {
                    Set<String> allow = new HashSet<>();
                    for (String method : header.substring("allow:".length()).split(",")) {
                        allow.add(method.trim());
                    }
                    return allow;
                }
            }
            Assert.fail("No Allow header");
            return null;
        }
    }
}

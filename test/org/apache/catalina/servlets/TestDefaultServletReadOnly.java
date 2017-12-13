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

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TestDefaultServletReadOnly extends TomcatBaseTest {

    @Parameterized.Parameters(name = "{index}: readOnly[{0}], optionsAllowHeader[{1}],"
                    + " putAndDeleteResponse[{2}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        parameterSets.add(
            new Object[]{"true", "Allow: GET, HEAD, POST, OPTIONS", "HTTP/1.1 403 "});
        parameterSets.add(
            new Object[]{"false", "Allow: GET, HEAD, POST, PUT, DELETE, OPTIONS", "HTTP/1.1 204 "});
        return parameterSets;
    }


    private final String readOnly;
    private final String optionsAllowHeader;
    private final String putAndDeleteResponse;

    public TestDefaultServletReadOnly(String readOnly, String optionsAllowHeader, String putAndDeleteResponse) {
        this.readOnly = readOnly;
        this.optionsAllowHeader = optionsAllowHeader;
        this.putAndDeleteResponse = putAndDeleteResponse;
    }

    @Test
    public void testOptionsAllowHeader() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");

        // app dir is relative to server home
        Context ctxt = tomcat.addContext("", appDir.getAbsolutePath());
        Wrapper defaultServlet = Tomcat.addServlet(ctxt, "default",
            DefaultServlet.class.getName());
        defaultServlet.addInitParameter("readonly", readOnly);
        ctxt.addServletMappingDecoded("/", "default");

        tomcat.start();

        TestReadOnlyClient client =
            new TestReadOnlyClient(tomcat.getConnector().getLocalPort());

        client.reset();
        client.setRequest(new String[] {
            "OPTIONS / HTTP/1.0" + CRLF + CRLF });
        client.connect();
        client.processRequest();
        Assert.assertTrue(client.isResponse200());
        List<String> responseHeaders = client.getResponseHeaders();
        Assert.assertTrue(responseHeaders.contains(optionsAllowHeader));
    }

    @Test
    public void testRestrictedHttpMethods() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");

        File index = new File(appDir, "test-readonly.html");
        index.createNewFile();

        // app dir is relative to server home
        Context ctxt = tomcat.addContext("", appDir.getAbsolutePath());
        Wrapper defaultServlet = Tomcat.addServlet(ctxt, "default",
            DefaultServlet.class.getName());
        defaultServlet.addInitParameter("readonly", readOnly);

        ctxt.addServletMappingDecoded("/", "default");

        tomcat.start();

        TestReadOnlyClient client =
            new TestReadOnlyClient(tomcat.getConnector().getLocalPort());

        client.reset();
        client.setRequest(new String[] {
            "GET /test-readonly.html HTTP/1.0" + CRLF + CRLF });
        client.connect();
        client.processRequest();
        Assert.assertTrue(client.isResponse200());

        client.reset();
        client.setRequest(new String[] {
            "HEAD /test-readonly.html HTTP/1.0" + CRLF + CRLF });
        client.connect();
        client.processRequest();
        Assert.assertTrue(client.isResponse200());

        client.reset();
        client.setRequest(new String[] {
            "POST /test-readonly.html HTTP/1.0" + CRLF + CRLF });
        client.connect();
        client.processRequest();
        Assert.assertTrue(client.isResponse200());

        client.reset();
        client.setRequest(new String[] {
            "PUT /test-readonly.html HTTP/1.0" + CRLF + CRLF });
        client.connect();
        client.processRequest();
        Assert.assertTrue(client.responseLineStartsWith(putAndDeleteResponse));

        client.reset();
        client.setRequest(new String[] {
            "DELETE /test-readonly.html HTTP/1.0" + CRLF + CRLF });
        client.connect();
        client.processRequest();
        Assert.assertTrue(client.responseLineStartsWith(putAndDeleteResponse));

        client.reset();
        client.setRequest(new String[] {
            "OPTIONS / HTTP/1.0" + CRLF + CRLF });
        client.connect();
        client.processRequest();
        Assert.assertTrue(client.isResponse200());
    }

    private static class TestReadOnlyClient extends SimpleHttpClient {

        public TestReadOnlyClient(int port) {
            setPort(port);
        }

        @Override
        public boolean isResponseBodyOK() {
            return true;
        }
    }
}

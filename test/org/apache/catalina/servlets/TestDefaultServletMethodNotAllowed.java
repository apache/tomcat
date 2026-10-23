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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.http.Method;

/**
 * Methods that the {@link DefaultServlet} does not support (PATCH and QUERY) must be rejected with a 405 response that
 * contains an Allow header that reflects the configuration of the {@link DefaultServlet}, not the one derived from the
 * methods that the {@link DefaultServlet} overrides.
 */
@RunWith(Parameterized.class)
public class TestDefaultServletMethodNotAllowed extends TomcatBaseTest {

    @Parameters(name = "{index}: readonly[{0}], trace[{1}], method[{2}]")
    public static Collection<Object[]> parameters() {
        String[] methods = new String[] { Method.PATCH, Method.QUERY };

        List<Object[]> parameterSets = new ArrayList<>();
        for (Boolean readOnly : booleans) {
            for (Boolean trace : booleans) {
                for (String method : methods) {
                    parameterSets.add(new Object[] { readOnly, trace, method });
                }
            }
        }
        return parameterSets;
    }


    @Parameter(0)
    public boolean readOnly;

    @Parameter(1)
    public boolean trace;

    @Parameter(2)
    public String method;


    @Test
    public void testHttp11() throws Exception {
        AllowClient client = doRequest("1.1");

        Assert.assertTrue(client.getResponseLine(), client.isResponse405());

        StringBuilder expected = new StringBuilder("OPTIONS, GET, HEAD");
        if (!readOnly) {
            expected.append(", PUT, DELETE");
        }
        if (trace) {
            expected.append(", TRACE");
        }
        Assert.assertEquals(expected.toString(), client.getAllow());
    }


    /*
     * 405 is not defined for HTTP/1.0 so a 400 is expected instead.
     */
    @Test
    public void testHttp10() throws Exception {
        AllowClient client = doRequest("1.0");

        Assert.assertTrue(client.getResponseLine(), client.isResponse400());
    }


    private AllowClient doRequest(String httpVersion) throws Exception {
        Tomcat tomcat = getTomcatInstance();

        tomcat.getConnector().setAllowTrace(trace);

        // A writable docBase is required else the resources are read-only whatever the servlet is configured to do
        File docBase = new File(getTemporaryDirectory(), "default-method-not-allowed");
        Assert.assertTrue(docBase.mkdirs());
        addDeleteOnTearDown(docBase);

        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());

        Wrapper w = Tomcat.addServlet(ctx, "default", new DefaultServlet());
        w.addInitParameter("readonly", Boolean.toString(readOnly));
        w.addInitParameter("allowPostAsGet", "false");
        ctx.addServletMapping("/", "default");

        tomcat.start();

        AllowClient client = new AllowClient();
        client.setPort(getPort());
        // @formatter:off
        client.setRequest(new String[] {
                method + " /test HTTP/" + httpVersion + CRLF +
                    "Host: localhost:" + getPort() + CRLF +
                    // QUERY requests without a Content-Type are rejected by the CoyoteAdapter
                    "Content-Type: text/plain" + CRLF +
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

        public String getAllow() {
            for (String header : getResponseHeaders()) {
                if (header.startsWith("Allow:")) {
                    return header.substring("Allow:".length()).trim();
                }
            }
            return null;
        }
    }
}

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
import java.util.Set;

import javax.servlet.Servlet;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameter;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public abstract class ServletOptionsBaseTest extends TomcatBaseTest {

    protected static final String COLLECTION_NAME = "collection";
    protected static final String FILE_NAME = "file";
    protected static final String UNKNOWN_NAME = "unknown";

    @Parameter(0)
    public boolean listings;

    @Parameter(1)
    public boolean readonly;

    @Parameter(2)
    public boolean trace;

    @Parameter(3)
    public String url;

    @Parameter(4)
    public String method;


    /*
     * Check that methods returned by OPTIONS are consistent with the return
     * http status code.
     * Method not present in options response -> 405 expected
     * Method present in options response     -> anything other than 405 expected
     */
    @Test
    public void testOptions() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        tomcat.getConnector().setAllowTrace(trace);

        File docBase = new File(getTemporaryDirectory(), "webdav");
        File collection = new File(docBase, COLLECTION_NAME);
        Assert.assertTrue(collection.mkdirs());
        File file = new File(docBase, FILE_NAME);
        Assert.assertTrue(file.createNewFile());

        addDeleteOnTearDown(docBase);

        // app dir is relative to server home
        org.apache.catalina.Context ctx =
            tomcat.addWebapp(null, "/servlet", docBase.getAbsolutePath());

        Wrapper w = Tomcat.addServlet(ctx, "servlet", createServlet());
        w.addInitParameter("listings", Boolean.toString(listings));
        w.addInitParameter("readonly", Boolean.toString(readonly));

        ctx.addServletMappingDecoded("/*", "servlet");

        tomcat.start();

        OptionsHttpClient client = new OptionsHttpClient();
        client.setPort(getPort());
        client.setRequest(new String[] {
                "OPTIONS /servlet/" + url + " HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Connection: close" + CRLF +
                CRLF });

        client.connect();
        client.processRequest();

        Assert.assertTrue(client.isResponse200());
        Set<String> allowed = client.getAllowedMethods();

        client.disconnect();
        client.reset();

        client.setRequest(new String[] {
                method + " /servlet/" + url + " HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Connection: close" + CRLF +
                CRLF });

        client.connect();
        client.processRequest();

        String msg = "Listings[" + listings + "], readonly [" + readonly +
                "], trace[ " + trace + "], url[" + url + "], method[" + method + "]";

        Assert.assertNotNull(client.getResponseLine());

        if (allowed.contains(method)) {
            Assert.assertFalse(msg, client.isResponse405());
        } else {
            Assert.assertTrue(msg, client.isResponse405());
            allowed = client.getAllowedMethods();
            Assert.assertFalse(msg, allowed.contains(method));
        }
    }


    protected abstract Servlet createServlet();


    private static class OptionsHttpClient extends SimpleHttpClient {

        @Override
        public boolean isResponseBodyOK() {
            return true;
        }

        public Set<String> getAllowedMethods() {
            String valueList = null;
            for (String header : getResponseHeaders()) {
                if (header.startsWith("Allow:")) {
                    valueList = header.substring(6).trim();
                    break;
                }
            }
            Assert.assertNotNull(valueList);
            String[] values = valueList.split(",");
            for (int i = 0; i < values.length; i++) {
                values[i] = values[i].trim();
            }
            Set<String> allowed = new HashSet<>(Arrays.asList(values));

            return allowed;
        }
    }
}

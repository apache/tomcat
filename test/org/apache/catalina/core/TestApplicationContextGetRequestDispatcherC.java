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
package org.apache.catalina.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

/*
 * Tests interaction of ServletContext.getRequestDispatcher() and the Connector/Context attributes
 * encodedSolidusHandling and encodedReverseSolidusHandling.
 */
@RunWith(value = Parameterized.class)
public class TestApplicationContextGetRequestDispatcherC extends TomcatBaseTest {

    @Parameters(name = "{index}: pathInfoRequest[{0}], pathInfoDispatcher[{1}]")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
            // Defaults
            { "/a/b/c", "/a/b/c", null, null },
            { "/a%2fb%5cc/%25", "/a/b/c/%", "decode", "decode" },
            { "/a%2fb%5cc/%25", "/a/b%5cc/%25", "decode", "passthrough" },
            { "/a%2fb%5cc/%25", "/a%2fb/c/%25", "passthrough", "decode" },
            { "/a%2fb%5cc/%25", "/a%2fb%5cc/%25", "passthrough", "passthrough" },
        });
    }


    @Parameter(0)
    public String pathInfoRequest;
    @Parameter(1)
    public String pathInfoDispatcher;
    @Parameter(2)
    public String encodedSolidusHandling;
    @Parameter(3)
    public String encodedReverseSolidusHandling;


    @Test
    public void testSomething() throws Exception {
        doTest();
    }


    private void doTest() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Configure the Connector
        tomcat.getConnector().setAllowBackslash(true);
        if (encodedSolidusHandling != null) {
            tomcat.getConnector().setEncodedSolidusHandling(encodedSolidusHandling);
        }
        if (encodedReverseSolidusHandling != null) {
            tomcat.getConnector().setEncodedReverseSolidusHandling(encodedReverseSolidusHandling);
        }

        // No file system docBase required
        Context ctx = tomcat.addContext("/test", null);
        ctx.addWelcomeFile("index.html");
        if (encodedSolidusHandling != null) {
            ctx.setEncodedSolidusHandling(encodedSolidusHandling);
        }
        if (encodedReverseSolidusHandling != null) {
            ctx.setEncodedReverseSolidusHandling(encodedReverseSolidusHandling);
        }

        // Servlet that performs a dispatch to ...
        Tomcat.addServlet(ctx, "rd", new Dispatch(pathInfoRequest));
        ctx.addServletMappingDecoded("/dispatch/*", "rd");

        // ... this Servlet
        Tomcat.addServlet(ctx, "target", new Target());
        ctx.addServletMappingDecoded("/target/*", "target");

        tomcat.start();

        StringBuilder url = new StringBuilder("http://localhost:");
        url.append(getPort());
        url.append("/test/dispatch");
        url.append(pathInfoRequest);

        ByteChunk bc = getUrl(url.toString());
        String body = bc.toString();

        Assert.assertEquals(pathInfoDispatcher + pathInfoDispatcher, body);
    }


    private static class Dispatch extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final String dispatchPath;

        Dispatch(String dispatchPath) {
            this.dispatchPath = dispatchPath;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            /*
             * The original pathInfo as presented by the client and the dispatchPath are currently the same for this
             * test, as is the handling configured in the Connector and Context. The test checks both the normal request
             * processing and the dispatch processing for the correct handling of %2f and %5c. The test could be
             * expanded so different settings are used for the Connector and the Context.
             */
            // Handle an edge case - the application needs to encode any paths used to obtain a RequestDispatcher
            String pathInfo = req.getPathInfo();
            if (pathInfo.endsWith("%")) {
                pathInfo = pathInfo.replace("%", "%25");
            }
            req.getServletContext().getRequestDispatcher("/target" + pathInfo + dispatchPath).forward(req, resp);
        }
    }


    private static class Target extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding(StandardCharsets.UTF_8);
            resp.getWriter().print(req.getPathInfo());
        }
    }
}

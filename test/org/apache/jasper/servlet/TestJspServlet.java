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
package org.apache.jasper.servlet;

import java.io.File;
import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.ErrorPage;
import org.apache.tomcat.util.http.Method;

public class TestJspServlet  extends TomcatBaseTest {

    @Test
    public void testBug56568a() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Use the test web application so JSP support is available and the
        // default JSP error page can be used.
        File appDir = new File("test/webapp");
        Context context = tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        // Create a servlet that always throws an exception for a PUT request
        Tomcat.addServlet(context, "Bug56568Servlet", new Bug56568aServlet());
        context.addServletMappingDecoded("/bug56568", "Bug56568Servlet");

        // Configure a JSP page to handle the 500 error response
        // The JSP page will see the same method as the original request (PUT)
        // PUT requests are normally blocked for JSPs
        ErrorPage ep = new ErrorPage();
        ep.setErrorCode(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        ep.setLocation("/jsp/error.jsp");
        context.addErrorPage(ep);

        tomcat.start();

        // When using JaCoCo, the CI system seems to need a longer timeout
        int rc = methodUrl("http://localhost:" + getPort() + "/test/bug56568",
                new ByteChunk(), 30000, null, null, Method.PUT);

        // Make sure we get the original 500 response and not a 405 response
        // which would indicate that error.jsp is complaining about being called
        // with the PUT method.
        Assert.assertEquals(500, rc);
    }


    @Test
    public void testBug56568b() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        int rc = methodUrl("http://localhost:" + getPort() + "/test/jsp/error.jsp",
                new ByteChunk(), 500000, null, null, Method.PUT);

        // Make sure we get a 200 response and not a 405 response
        // which would indicate that error.jsp is complaining about being called
        // with the PUT method.
        Assert.assertEquals(200, rc);
    }


    @Test
    public void testBug56568c() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        int rc = methodUrl("http://localhost:" + getPort() + "/test/jsp/test.jsp",
                new ByteChunk(), 500000, null, null, Method.PUT);

        // Make sure we get a 405 response which indicates that test.jsp is
        // complaining about being called with the PUT method.
        Assert.assertEquals(405, rc);
    }


    private static class Bug56568aServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doPut(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            throw new ServletException();
        }
    }
}

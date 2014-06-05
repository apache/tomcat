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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.ErrorPage;

public class TestJspServlet  extends TomcatBaseTest {

    @Test
    public void testBug56568() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Use the test web application so JSP support is available and the
        // default JSP error page can be used.
        File appDir = new File("test/webapp");
        Context context = tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        // Create a servlet that always throws an exception for a PUT request
        Tomcat.addServlet(context, "Bug56568Servlet", new Bug56568Servlet());
        context.addServletMapping("/bug56568", "Bug56568Servlet");

        // Configure a JSP page to handle the 500 error response
        // The JSP page will see the same method as the original request (PUT)
        // PUT requests are normally blocked for JSPs
        ErrorPage ep = new ErrorPage();
        ep.setErrorCode(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        // Note this location doesn't actually exist
        ep.setLocation("/WEB-INF/jsp/error.jsp");
        context.addErrorPage(ep);

        tomcat.start();

        int rc = methodUrl("http://localhost:" + getPort() + "/test/bug56568",
                new ByteChunk(), 5000, null, null, "PUT");

        // Make sure we get the original 500 response and not a 405 response
        // which would indicate that error.jsp is complaining about being called
        // with the PUT method.
        Assert.assertEquals(500, rc);
    }

    private static class Bug56568Servlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doPut(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            throw new ServletException();
        }
    }
}

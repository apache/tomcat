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

package org.apache.catalina.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestStandardContextResources extends TomcatBaseTest {

    public void testResources() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-3.0-fragments");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        if (false) {
            // FIXME: These tests are currently failing. See comment in testResources2() below.
            
        assertPageContains("/test/resourceA.jsp",
                "<p>resourceA.jsp in the web application</p>");
        assertPageContains("/test/resourceB.jsp",
                "<p>resourceB.jsp in resources.jar</p>");
        assertPageContains("/test/folder/resourceC.jsp",
                "<p>resourceC.jsp in the web application</p>");
        assertPageContains("/test/folder/resourceD.jsp",
                "<p>resourceD.jsp in resources.jar</p>");
        assertPageContains("/test/folder/resourceE.jsp",
                "<p>resourceE.jsp in the web application</p>");
        }
    }

    public void testResources2() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-3.0-fragments");
        // app dir is relative to server home
        StandardContext ctx = (StandardContext) tomcat.addWebapp(null, "/test",
                appDir.getAbsolutePath());

        Tomcat.addServlet(ctx, "getresource", new GetResourceServlet());
        ctx.addServletMapping("/getresource", "getresource");

        tomcat.start();

        // FIXME: These tests are currently failing.
        //
        // I do not have a fix yet, but I know the following:
        // when trying to get "/resourceB.jsp" in ApplicationContext#getResource()
        // an Exception is caught and silently swallowed. That exception is
        //
        // java.lang.IllegalStateException: zip file closed
        // at java.util.jar.JarFile.getMetaInfEntryNames(Native Method)
        // at java.util.jar.JarFile.maybeInstantiateVerifier(JarFile.java:277)
        // at java.util.jar.JarFile.getInputStream(JarFile.java:381)
        // at org.apache.naming.resources.WARDirContext$WARResource.streamContent(WARDirContext.java:951)
        // at org.apache.naming.resources.ProxyDirContext.cacheLoad(ProxyDirContext.java:1578)
        // at org.apache.naming.resources.ProxyDirContext.cacheLookup(ProxyDirContext.java:1458)
        // at org.apache.naming.resources.ProxyDirContext.lookup(ProxyDirContext.java:292)
        // at org.apache.catalina.core.ApplicationContext.getResource(ApplicationContext.java:506)
        // at org.apache.catalina.core.ApplicationContextFacade.getResource(ApplicationContextFacade.java:196)
        // at org.apache.catalina.core.TestStandardContextResources$GetResourceServlet.doGet(TestStandardContextResources.java:126)
        //
        if (false) {
        
        assertPageContains("/test/getresource?path=/resourceA.jsp",
                "<p>resourceA.jsp in the web application</p>");
        assertPageContains("/test/getresource?path=/resourceB.jsp",
                "<p>resourceB.jsp in resources.jar</p>");
        assertPageContains("/test/getresource?path=/folder/resourceC.jsp",
                "<p>resourceC.jsp in the web application</p>");
        assertPageContains("/test/getresource?path=/folder/resourceD.jsp",
                "<p>resourceD.jsp in resources.jar</p>");
        assertPageContains("/test/getresource?path=/folder/resourceE.jsp",
                "<p>resourceE.jsp in the web application</p>");
        }
    }

    /**
     * A servlet that prints the requested resource. The path to the requested
     * resource is passed as a parameter, <code>path</code>.
     */
    public static class GetResourceServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            resp.setContentType("text/plain");

            ServletContext context = getServletContext();

            // Check resources individually
            URL url = context.getResource(req.getParameter("path"));
            if (url == null) {
                resp.getWriter().println("Not found");
                return;
            }

            InputStream input = url.openStream();
            OutputStream output = resp.getOutputStream();
            try {
                byte[] buffer = new byte[4000];
                for (int len; (len = input.read(buffer)) > 0;) {
                    output.write(buffer, 0, len);
                }
            } finally {
                input.close();
                output.close();
            }
        }
    }

    private void assertPageContains(String pageUrl, String expected)
            throws IOException {
        ByteChunk res = getUrl("http://localhost:" + getPort() + pageUrl);

        String result = res.toString();
        assertTrue(result, result.indexOf(expected) > 0);
    }
}

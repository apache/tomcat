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
package org.apache.tomcat.util.buf;

import java.io.IOException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.SessionTrackingMode;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestMessageBytesIntegration extends TomcatBaseTest {

    /*
     * https://bz.apache.org/bugzilla/show_bug.cgi?id=66488
     */
    @Test
    public void testBytesStringBytesMixup() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        ctx.addApplicationListener("org.apache.tomcat.util.buf.TestMessageBytesIntegration$MixUpConfig");

        // Add servlet
        Tomcat.addServlet(ctx, "MixUpServlet", new MixUpServlet());
        ctx.addServletMappingDecoded("/mixup", "MixUpServlet");

        tomcat.start();

        ByteChunk body = new ByteChunk();
        Map<String,List<String>> requestHeaders = new HashMap<>();
        requestHeaders.put("Cookie", Arrays.asList("a=b; c=d"));
        getUrl("http://localhost:" + getPort() + "/mixup", body, requestHeaders, null);

        Assert.assertEquals("/mixup", body.toString());
    }


    private static class MixUpServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            // Convert all headers to String
            Enumeration<String> names = req.getHeaderNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                Enumeration<String> values = req.getHeaders(name);
                while (values.hasMoreElements()) {
                    String value = values.nextElement();
                    System.out.println("[" + name + "] - [" + value + "]");
                }
            }

            // Parsing cookies turns cookie header back to bytes (and triggers the bug)
            req.getCookies();

            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");

            resp.getWriter().print(req.getRequestURI());
        }
    }


    public static class MixUpConfig implements ServletContextListener {

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            sce.getServletContext().setSessionTrackingModes(new HashSet<>(Arrays.asList(SessionTrackingMode.URL)));
        }
    }
}

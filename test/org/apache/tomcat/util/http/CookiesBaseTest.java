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

package org.apache.tomcat.util.http;

import java.io.IOException;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.startup.Tomcat;

/**
 * Base Test case for {@link Cookies}. <b>Note</b> because of the use of
 * <code>final static</code> constants in {@link Cookies}, each of these tests
 * must be executed in a new JVM instance. The tests have been place in separate
 * classes to facilitate this when running the unit tests via Ant.
 */
public abstract class CookiesBaseTest extends TomcatBaseTest {

    /**
     * Servlet for cookie naming test.
     */
    public static class CookieName extends HttpServlet {

        private static final long serialVersionUID = 1L;
        
        private final String cookieName;

        public CookieName(String cookieName) {
            this.cookieName = cookieName;
        }
        
        public void doGet(HttpServletRequest req, HttpServletResponse res) 
                throws IOException {
            try {
                Cookie cookie = new Cookie(cookieName,"Value");
                res.addCookie(cookie);
                res.getWriter().write("Cookie name ok");
            } catch (IllegalArgumentException iae) {
                res.getWriter().write("Cookie name fail");
            }
        }
        
    }
    

    public static void addServlets(Tomcat tomcat) {
        // Must have a real docBase - just use temp
        StandardContext ctx = 
            tomcat.addContext("/", System.getProperty("java.io.tmpdir"));

        Tomcat.addServlet(ctx, "invalid", new CookieName("na;me"));
        ctx.addServletMapping("/invalid", "invalid");
        Tomcat.addServlet(ctx, "invalidFwd", new CookieName("na/me"));
        ctx.addServletMapping("/invalidFwd", "invalidFwd");
        Tomcat.addServlet(ctx, "invalidStrict", new CookieName("na?me"));
        ctx.addServletMapping("/invalidStrict", "invalidStrict");
        Tomcat.addServlet(ctx, "valid", new CookieName("name"));
        ctx.addServletMapping("/valid", "valid");

    }
    
    public abstract void testCookiesInstance() throws Exception;
    
}

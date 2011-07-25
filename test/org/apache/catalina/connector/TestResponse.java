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

package org.apache.catalina.connector;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTestJUnit4;
import org.apache.tomcat.util.buf.ByteChunk;

/**
 * Test case for {@link Request}. 
 */
public class TestResponse extends TomcatBaseTestJUnit4 {

    @Test
    public void testBug49598() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();
        
        // Must have a real docBase - just use temp
        File docBase = new File(System.getProperty("java.io.tmpdir"));
        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());

        Tomcat.addServlet(ctx, "servlet", new Bug49598Servlet());
        ctx.addServletMapping("/", "servlet");

        tomcat.start();
        
        Map<String,List<String>> headers = new HashMap<String,List<String>>();
        getUrl("http://localhost:" + getPort() + "/", new ByteChunk(), headers);
        
        // Check for headers without a name
        for (Map.Entry<String,List<String>> header : headers.entrySet()) {
            if (header.getKey() == null) {
                // Expected if this is the response line
                List<String> values = header.getValue();
                if (values.size() == 1 &&
                        values.get(0).startsWith("HTTP/1.1")) {
                    continue;
                }
                fail("Null header name detected for value " + values);
            }
        }
        
        // Check for exactly one Set-Cookie header
        int count = 0;
        for (String headerName : headers.keySet()) {
            if ("Set-Cookie".equals(headerName)) {
                count ++;
            }
        }
        assertEquals(1, count);
    }
    
    private static final class Bug49598Servlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            HttpSession session = req.getSession(true);
            session.invalidate();
            req.getSession(true);
        }
        
    }
}

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
package javax.servlet.http;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestHttpServlet extends TomcatBaseTest {

    @Test
    public void testBug53454() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        StandardContext ctx = (StandardContext)
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));

        // Map the test Servlet
        LargeBodyServlet largeBodyServlet = new LargeBodyServlet();
        Tomcat.addServlet(ctx, "largeBodyServlet", largeBodyServlet);
        ctx.addServletMapping("/", "largeBodyServlet");

        tomcat.start();

        Map<String,List<String>> resHeaders= new HashMap<>();
        int rc = headUrl("http://localhost:" + getPort() + "/", new ByteChunk(),
               resHeaders);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertEquals(LargeBodyServlet.RESPONSE_LENGTH,
                resHeaders.get("Content-Length").get(0));
    }


    private static class LargeBodyServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;
        private static final String RESPONSE_LENGTH = "12345678901";

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setHeader("content-length", RESPONSE_LENGTH);
        }
    }
}

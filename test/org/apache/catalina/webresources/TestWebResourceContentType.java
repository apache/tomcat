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
package org.apache.catalina.webresources;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.util.IOTools;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestWebResourceContentType extends TomcatBaseTest {

    @Test
    public void testContentType() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        File docBase = new File("test/webapp");
        Context ctx = tomcat.addContext("/test", docBase.getAbsolutePath());

        // Configure Context
        ctx.addMimeMapping("html", "text/html");
        ctx.addMimeMapping("txt", "text/plain");


        // Add custom default servlet
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/*", "default");

        tomcat.start();

        ByteChunk body = new ByteChunk();
        Map<String, List<String>> resHead = new HashMap<>();
        int rc = getUrl("http://localhost:" + getPort() + "/test/anything", body, resHead);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertEquals("text/html", resHead.get("Content-Type").get(0));
    }


    private static class DefaultServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            URL url = req.getServletContext().getResource("/index.html");
            URLConnection uConn = url.openConnection();
            resp.setContentType(uConn.getContentType());
            resp.setContentLengthLong(uConn.getContentLengthLong());

            try (InputStream is = uConn.getInputStream();
                    OutputStream os = resp.getOutputStream()) {
                IOTools.flow(is, os);
            }
        }
    }
}

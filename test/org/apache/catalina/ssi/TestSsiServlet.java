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
package org.apache.catalina.ssi;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestSsiServlet extends TomcatBaseTest {

    @Test
    public void testServlet() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        Context ctxt = tomcat.addContext("", appDir.getAbsolutePath());

        Tomcat.addServlet(ctxt, "default", new DefaultServlet());
        ctxt.addServletMappingDecoded("/", "default");

        Wrapper ssi = Tomcat.addServlet(ctxt, "ssi", new SSIServlet());
        ssi.addInitParameter("allowExec", "true");
        ctxt.addServletMappingDecoded("*.shtml", "ssi");

        tomcat.start();

        Map<String,List<String>> resHeaders= new HashMap<>();
        String path = "http://localhost:" + getPort() + "/index.shtml";
        ByteChunk out = new ByteChunk();

        int rc = getUrl(path, out, resHeaders);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        String body = new String(out.getBytes(), StandardCharsets.ISO_8859_1);
        Assert.assertTrue(body.contains("should fail[errmsg works!]"));
        Assert.assertTrue(body.contains("including works!"));
        Assert.assertTrue(body.contains("path is interpreted"));
        Assert.assertTrue(body.contains("path is relative"));
        Assert.assertTrue(body.contains("path is relative"));
        Assert.assertTrue(body.contains("path is relative"));
        Assert.assertTrue(body.contains("1k"));
        Assert.assertTrue(body.contains("SERVER_PROTOCOL"));

    }
}

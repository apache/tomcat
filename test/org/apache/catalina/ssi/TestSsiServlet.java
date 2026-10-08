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
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
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
        ctxt.addServletMapping("/", "default");

        Wrapper ssi = Tomcat.addServlet(ctxt, "ssi", new SSIServlet());
        ssi.addInitParameter("allowExec", "true");
        ctxt.addServletMapping("*.shtml", "ssi");

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
        // Any undefined variable renders as "(none)". The page only echoes
        // variables that must be defined, including the built-in date variables.
        Assert.assertFalse(body.contains("(none)"));

    }


    @Test
    public void testLargeDocumentLastModifiedHeader() throws Exception {
        // A document larger than the response buffer commits the unbuffered
        // response while the output is being generated. The Last-Modified
        // header must be set before that happens.
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File(getTemporaryDirectory(), "ssi-large");
        Assert.assertTrue(appDir.mkdirs() || appDir.isDirectory());
        addDeleteOnTearDown(appDir);
        File doc = new File(appDir, "large.shtml");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(doc), StandardCharsets.ISO_8859_1)) {
            writer.write("X".repeat(20000));
            writer.write("<!--#flastmod file=\"large.shtml\" -->TAIL");
        }

        Context ctxt = tomcat.addContext("", appDir.getAbsolutePath());
        Tomcat.addServlet(ctxt, "ssi", new SSIServlet());
        ctxt.addServletMapping("*.shtml", "ssi");

        tomcat.start();

        Map<String,List<String>> resHeaders = new HashMap<>();
        String path = "http://localhost:" + getPort() + "/large.shtml";
        ByteChunk out = new ByteChunk();

        int rc = getUrl(path, out, resHeaders);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        String body = new String(out.getBuffer(), 0, out.getLength(), StandardCharsets.ISO_8859_1);
        Assert.assertTrue(body.endsWith("TAIL"));
        // Headers are case-insensitive. SSIServlet uses the lower-case name.
        String lastModified = null;
        for (Map.Entry<String,List<String>> header : resHeaders.entrySet()) {
            if ("Last-Modified".equalsIgnoreCase(header.getKey())) {
                Assert.assertEquals(1, header.getValue().size());
                lastModified = header.getValue().get(0);
            }
        }
        Assert.assertNotNull(lastModified);
    }


    @Test
    public void testBadConditionalExpressionReportsError() throws Exception {
        // A conditional directive with an expression that cannot be parsed
        // stops processing of the document, but it must report the default
        // error message rather than truncating the response silently.
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File(getTemporaryDirectory(), "ssi-badif");
        Assert.assertTrue(appDir.mkdirs() || appDir.isDirectory());
        addDeleteOnTearDown(appDir);
        File doc = new File(appDir, "badif.shtml");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(doc), StandardCharsets.ISO_8859_1)) {
            writer.write("BEFORE-CONTENT\n");
            writer.write("<!--#if expr=\"this is not valid (\"-->\n");
            writer.write("AFTER-CONTENT\n");
        }

        Context ctxt = tomcat.addContext("", appDir.getAbsolutePath());
        Tomcat.addServlet(ctxt, "ssi", new SSIServlet());
        ctxt.addServletMapping("*.shtml", "ssi");

        tomcat.start();

        Map<String,List<String>> resHeaders = new HashMap<>();
        String path = "http://localhost:" + getPort() + "/badif.shtml";
        ByteChunk out = new ByteChunk();

        int rc = getUrl(path, out, resHeaders);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        String body = new String(out.getBuffer(), 0, out.getLength(), StandardCharsets.ISO_8859_1);
        Assert.assertTrue(body.contains("BEFORE-CONTENT"));
        Assert.assertTrue(body.contains("[an error occurred"));
        // Processing of the remainder of the document stops at the failing directive
        Assert.assertFalse(body.contains("AFTER-CONTENT"));
    }
}

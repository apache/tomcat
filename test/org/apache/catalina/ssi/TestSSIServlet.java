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
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

/**
 * Integration tests for {@link SSIServlet}.
 */
public class TestSSIServlet extends TomcatBaseTest {

    @Test
    public void testSSIEchoDateLocal() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        File docBase = getTemporaryDirectory();
        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());

        Wrapper wrapper = Tomcat.addServlet(ctx, "ssi", new SSIServlet());
        wrapper.addInitParameter("buffered", "true");
        wrapper.addInitParameter("debug", "0");
        ctx.addServletMappingDecoded("*.shtml", "ssi");

        // Create test SSI file
        File ssiFile = new File(docBase, "test.shtml");
        try (PrintWriter pw = new PrintWriter(new FileWriter(ssiFile))) {
            pw.println("<html><body>");
            pw.println("<!--#echo var=\"DATE_LOCAL\" -->");
            pw.println("</body></html>");
        }

        tomcat.start();

        ByteChunk res = new ByteChunk();
        res.setCharset(StandardCharsets.UTF_8);
        int rc = getUrl("http://localhost:" + getPort() + "/test.shtml", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String body = res.toString();
        Assert.assertNotNull(body);
        // DATE_LOCAL should be replaced with actual date
        Assert.assertFalse("DATE_LOCAL should be resolved",
                body.contains("DATE_LOCAL"));
    }


    @Test
    public void testSSISetAndEcho() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        File docBase = getTemporaryDirectory();
        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());

        Wrapper wrapper = Tomcat.addServlet(ctx, "ssi", new SSIServlet());
        wrapper.addInitParameter("buffered", "true");
        ctx.addServletMappingDecoded("*.shtml", "ssi");

        File ssiFile = new File(docBase, "set.shtml");
        try (PrintWriter pw = new PrintWriter(new FileWriter(ssiFile))) {
            pw.println("<html><body>");
            pw.println("<!--#set var=\"myVar\" value=\"Hello SSI\" -->");
            pw.println("<!--#echo var=\"myVar\" -->");
            pw.println("</body></html>");
        }

        tomcat.start();

        ByteChunk res = new ByteChunk();
        res.setCharset(StandardCharsets.UTF_8);
        int rc = getUrl("http://localhost:" + getPort() + "/set.shtml", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String body = res.toString();
        Assert.assertTrue("Should contain our variable value",
                body.contains("Hello SSI"));
    }


    @Test
    public void testSSIIfConditional() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        File docBase = getTemporaryDirectory();
        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());

        Wrapper wrapper = Tomcat.addServlet(ctx, "ssi", new SSIServlet());
        wrapper.addInitParameter("buffered", "true");
        ctx.addServletMappingDecoded("*.shtml", "ssi");

        File ssiFile = new File(docBase, "cond.shtml");
        try (PrintWriter pw = new PrintWriter(new FileWriter(ssiFile))) {
            pw.println("<html><body>");
            pw.println("<!--#set var=\"testvar\" value=\"yes\" -->");
            pw.println("<!--#if expr=\"$testvar = yes\" -->");
            pw.println("CONDITION_TRUE");
            pw.println("<!--#else -->");
            pw.println("CONDITION_FALSE");
            pw.println("<!--#endif -->");
            pw.println("</body></html>");
        }

        tomcat.start();

        ByteChunk res = new ByteChunk();
        res.setCharset(StandardCharsets.UTF_8);
        int rc = getUrl("http://localhost:" + getPort() + "/cond.shtml", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String body = res.toString();
        Assert.assertTrue("True branch should be shown",
                body.contains("CONDITION_TRUE"));
        Assert.assertFalse("False branch should not be shown",
                body.contains("CONDITION_FALSE"));
    }


    @Test
    public void testSSIWebInfBlocked() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        File docBase = getTemporaryDirectory();
        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());

        Wrapper wrapper = Tomcat.addServlet(ctx, "ssi", new SSIServlet());
        wrapper.addInitParameter("buffered", "true");
        ctx.addServletMappingDecoded("*.shtml", "ssi");

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() +
                "/WEB-INF/secret.shtml", res, null);

        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);
    }


    @Test
    public void testSSIMetaInfBlocked() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        File docBase = getTemporaryDirectory();
        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());

        Wrapper wrapper = Tomcat.addServlet(ctx, "ssi", new SSIServlet());
        wrapper.addInitParameter("buffered", "true");
        ctx.addServletMappingDecoded("*.shtml", "ssi");

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() +
                "/META-INF/data.shtml", res, null);

        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);
    }


    @Test
    public void testSSIResourceNotFound() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        File docBase = getTemporaryDirectory();
        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());

        Wrapper wrapper = Tomcat.addServlet(ctx, "ssi", new SSIServlet());
        wrapper.addInitParameter("buffered", "true");
        ctx.addServletMappingDecoded("*.shtml", "ssi");

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() +
                "/nonexistent.shtml", res, null);

        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);
    }


    @Test
    public void testSSIPrintenv() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        File docBase = getTemporaryDirectory();
        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());

        Wrapper wrapper = Tomcat.addServlet(ctx, "ssi", new SSIServlet());
        wrapper.addInitParameter("buffered", "true");
        ctx.addServletMappingDecoded("*.shtml", "ssi");

        File ssiFile = new File(docBase, "printenv.shtml");
        try (PrintWriter pw = new PrintWriter(new FileWriter(ssiFile))) {
            pw.println("<html><body><pre>");
            pw.println("<!--#printenv -->");
            pw.println("</pre></body></html>");
        }

        tomcat.start();

        ByteChunk res = new ByteChunk();
        res.setCharset(StandardCharsets.UTF_8);
        int rc = getUrl("http://localhost:" + getPort() +
                "/printenv.shtml", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String body = res.toString();
        // printenv should output server variables
        Assert.assertTrue("Should contain SERVER_NAME",
                body.contains("SERVER_NAME"));
    }


    @Test
    public void testSSIConfig() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        File docBase = getTemporaryDirectory();
        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());

        Wrapper wrapper = Tomcat.addServlet(ctx, "ssi", new SSIServlet());
        wrapper.addInitParameter("buffered", "true");
        ctx.addServletMappingDecoded("*.shtml", "ssi");

        File ssiFile = new File(docBase, "config.shtml");
        try (PrintWriter pw = new PrintWriter(new FileWriter(ssiFile))) {
            pw.println("<html><body>");
            pw.println("<!--#config timefmt=\"%Y\" -->");
            pw.println("<!--#echo var=\"DATE_LOCAL\" -->");
            pw.println("</body></html>");
        }

        tomcat.start();

        ByteChunk res = new ByteChunk();
        res.setCharset(StandardCharsets.UTF_8);
        int rc = getUrl("http://localhost:" + getPort() +
                "/config.shtml", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        String body = res.toString();
        // With timefmt=%Y, the date should be a 4-digit year
        Assert.assertTrue("Year should appear in output",
                body.matches("(?s).*20\\d{2}.*"));
    }


    @Test
    public void testSSIFsize() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        File docBase = getTemporaryDirectory();
        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());

        Wrapper wrapper = Tomcat.addServlet(ctx, "ssi", new SSIServlet());
        wrapper.addInitParameter("buffered", "true");
        wrapper.addInitParameter("isVirtualWebappRelative", "true");
        ctx.addServletMappingDecoded("*.shtml", "ssi");

        // Create a target file to check its size
        File targetFile = new File(docBase, "data.txt");
        try (PrintWriter pw = new PrintWriter(new FileWriter(targetFile))) {
            pw.print("Hello World");
        }

        File ssiFile = new File(docBase, "fsize.shtml");
        try (PrintWriter pw = new PrintWriter(new FileWriter(ssiFile))) {
            pw.println("<html><body>");
            pw.println("<!--#fsize virtual=\"/data.txt\" -->");
            pw.println("</body></html>");
        }

        tomcat.start();

        ByteChunk res = new ByteChunk();
        res.setCharset(StandardCharsets.UTF_8);
        int rc = getUrl("http://localhost:" + getPort() +
                "/fsize.shtml", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
    }
}

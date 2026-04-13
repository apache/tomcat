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

package org.apache.tomcat.security;

import java.io.File;
import java.io.FileWriter;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.servlets.CGIServlet;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.ssi.SSIFilter;
import org.apache.catalina.ssi.SSIServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.compat.JrePlatform;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

public class TestSecurity2019 extends TomcatBaseTest {

    // https://www.cve.org/CVERecord?id=CVE-2019-0221
    @Test
    public void testCVE_2019_0221_01() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File(getTemporaryDirectory(), "ssitest");
        Assert.assertTrue(appDir.mkdirs());
        addDeleteOnTearDown(appDir);

        File shtml = new File(appDir, "printenv.shtml");
        try (FileWriter fw = new FileWriter(shtml)) {
            fw.write("<!--#printenv -->");
        }

        Context ctx = tomcat.addContext("", appDir.getAbsolutePath());

        Tomcat.addServlet(ctx, "ssi", new SSIServlet());
        ctx.addServletMappingDecoded("*.shtml", "ssi");

        ctx.setPrivileged(true);

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/printenv.shtml?%3Ch1%3EXSS%3C/h1%3E", res, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertFalse("SSI printenv should not render unescaped HTML ", res.toString().contains("<h1>"));

    }

    @Test
    public void testCVE_2019_0221_02() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File(getTemporaryDirectory(), "ssitest");
        Assert.assertTrue(appDir.mkdirs());
        addDeleteOnTearDown(appDir);

        File shtml = new File(appDir, "printenv.shtml");
        try (FileWriter fw = new FileWriter(shtml)) {
            fw.write("<!--#printenv -->");
        }

        Context ctx = tomcat.addContext("", appDir.getAbsolutePath());

        FilterDef filterDef = new FilterDef();
        filterDef.setFilterClass(SSIFilter.class.getName());
        filterDef.setFilterName("ssi");
        ctx.addFilterDef(filterDef);

        FilterMap filterMap = new FilterMap();
        filterMap.setFilterName("ssi");
        filterMap.addURLPatternDecoded("*.shtml");
        ctx.addFilterMap(filterMap);

        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");
        ctx.addMimeMapping("shtml", "text/x-server-parsed-html");

        ctx.setPrivileged(true);

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/printenv.shtml?%3Ch1%3EXSS%3C/h1%3E", res, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertFalse("SSI printenv should not render unescaped HTML ", res.toString().contains("<h1>"));
    }

    // https://www.cve.org/CVERecord?id=CVE-2019-0232
    @Test
    public void testCVE_2019_0232() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File(getTemporaryDirectory(), "cgitest");
        Assert.assertTrue(appDir.mkdirs());
        addDeleteOnTearDown(appDir);

        File cgiDir = new File(appDir, "WEB-INF/cgi");
        Assert.assertTrue(cgiDir.mkdirs());

        File testScript;
        File maliciousScript;

        if (JrePlatform.IS_WINDOWS) {
            testScript = new File(cgiDir, "test.bat");
            try (FileWriter fw = new FileWriter(testScript)) {
                fw.write("@echo off\r\n");
                fw.write("echo Content-Type: text/plain\r\n");
                fw.write("echo.\r\n");
                fw.write("echo Query string: %QUERY_STRING%\r\n");
            }

            maliciousScript = new File(cgiDir, "malicious.bat");
            try (FileWriter fw = new FileWriter(maliciousScript)) {
                fw.write("@echo off\r\n");
                fw.write("echo vulnerable > \"" + new File(appDir, "vulnerable").getAbsolutePath() + "\"\r\n");
            }
        } else {
            testScript = new File(cgiDir, "test.sh");
            try (FileWriter fw = new FileWriter(testScript)) {
                fw.write("#!/bin/sh\n");
                fw.write("echo \"Content-Type: text/plain\"\n");
                fw.write("echo\n");
                fw.write("echo \"Query string: $QUERY_STRING\"\n");
            }

            maliciousScript = new File(cgiDir, "malicious.sh");
            try (FileWriter fw = new FileWriter(maliciousScript)) {
                fw.write("#!/bin/sh\n");
                fw.write("touch " + new File(appDir, "vulnerable").getAbsolutePath() + "\n");
            }
        }

        Assert.assertTrue(testScript.setExecutable(true));
        Assert.assertTrue(maliciousScript.setExecutable(true));

        Context ctx = tomcat.addContext("", appDir.getAbsolutePath());
        ctx.setPrivileged(true);

        Wrapper cgi = Tomcat.addServlet(ctx, "cgi", new CGIServlet());
        cgi.addInitParameter("cgiPathPrefix", "WEB-INF/cgi");
        cgi.addInitParameter("executable", "");
        cgi.addInitParameter("enableCmdLineArguments", "true");
        ctx.addServletMappingDecoded("/cgi/*", "cgi");

        tomcat.start();

        String scriptName = JrePlatform.IS_WINDOWS ? "test.bat" : "test.sh";
        String maliciousName = JrePlatform.IS_WINDOWS ? "malicious.bat" : "malicious.sh";

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/cgi/" + scriptName + "?firstName=Dimitris",
                res, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertTrue(res.toString().contains("Query string:"));

        res.recycle();
        getUrl("http://localhost:" + getPort() + "/cgi/" + scriptName + "?&" + maliciousName, res, null);
        Assert.assertFalse("CGI command injection succeeded", new File(appDir, "vulnerable").exists());
    }

}

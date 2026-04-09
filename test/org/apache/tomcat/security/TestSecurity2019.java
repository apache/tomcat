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

import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.ssi.SSIFilter;
import org.apache.catalina.ssi.SSIServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
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
}

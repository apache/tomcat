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
package org.apache.jasper.compiler;

import java.io.File;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestJspConfig extends TomcatBaseTest {

    @Test
    public void testServlet22NoEL() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-2.2");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/el-as-literal.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>00-${'hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>01-#{'hello world'}</p>") > 0);
    }

    @Test
    public void testServlet23NoEL() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-2.3");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/el-as-literal.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>00-${'hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>01-#{'hello world'}</p>") > 0);
    }

    @Test
    public void testServlet24NoEL() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-2.4");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/el-as-literal.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>00-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>01-#{'hello world'}</p>") > 0);
    }

    @Test
    public void testServlet25NoEL() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-2.5");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/el-as-literal.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>00-hello world</p>") > 0);
    }

    @Test
    public void testServlet30NoEL() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-3.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/el-as-literal.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>00-hello world</p>") > 0);
    }

    @Test
    public void testServlet31NoEL() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-3.1");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/el-as-literal.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>00-hello world</p>") > 0);
    }

    @Test
    public void testServlet40NoEL() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-4.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/el-as-literal.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>00-hello world</p>") > 0);
    }

    @Test
    public void testServlet50NoEL() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-5.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/el-as-literal.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>00-hello world</p>") > 0);
    }

    @Test
    public void testServlet60NoEL() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-6.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/el-as-literal.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>00-hello world</p>") > 0);
    }

    @Test
    public void testServlet61NoEL() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-6.1");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/el-as-literal.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>00-hello world</p>") > 0);
    }

    @Test
    public void testErrorOnELNotFound01() throws Exception {
        // Defaults

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/test/jsp/errorOnELNotFound/default.jsp", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String result = res.toString();
        Assert.assertTrue(result, result.indexOf("<p>00-OK</p>") > 0);
    }

    @Test
    public void testErrorOnELNotFound02() throws Exception {
        // Page directive true

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/test/jsp/errorOnELNotFound/page-directive-true.jsp", res,
                null);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);

        // Look for the non-i18n part of the Exception message
        String result = res.toString();
        Assert.assertTrue(result, result.indexOf("[unknown]") > 0);
    }

    @Test
    public void testErrorOnELNotFound03() throws Exception {
        // Page directive false

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/test/jsp/errorOnELNotFound/page-directive-false.jsp", res,
                null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String result = res.toString();
        Assert.assertTrue(result, result.indexOf("<p>00-OK</p>") > 0);
    }

    @Test
    public void testErrorOnELNotFound04() throws Exception {
        // web.xml true

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/test/jsp/errorOnELNotFound/web-xml-true.jsp", res, null);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);

        // Look for the non-i18n part of the Exception message
        String result = res.toString();
        Assert.assertTrue(result, result.indexOf("[unknown]") > 0);
    }

    @Test
    public void testErrorOnELNotFound05() throws Exception {
        // web.xml false

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/test/jsp/errorOnELNotFound/web-xml-false.jsp", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String result = res.toString();
        Assert.assertTrue(result, result.indexOf("<p>00-OK</p>") > 0);
    }

    @Test
    public void testErrorOnELNotFound06() throws Exception {
        // tag file true

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/test/jsp/errorOnELNotFound/tag-file-true.jsp", res, null);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);

        // Look for the non-i18n part of the Exception message
        String result = res.toString();
        Assert.assertTrue(result, result.indexOf("[unknown]") > 0);
    }

    @Test
    public void testErrorOnELNotFound07() throws Exception {
        // tag file false

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/test/jsp/errorOnELNotFound/tag-file-false.jsp", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String result = res.toString();
        Assert.assertTrue(result, result.indexOf("<p>00-OK</p>") > 0);
    }
}

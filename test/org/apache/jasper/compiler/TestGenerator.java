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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.TagData;
import javax.servlet.jsp.tagext.TagExtraInfo;
import javax.servlet.jsp.tagext.TagSupport;
import javax.servlet.jsp.tagext.VariableInfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestGenerator extends TomcatBaseTest {

    @Test
    public void testBug45015a() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir =
            new File("test/webapp-3.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug45nnn/bug45015a.jsp");

        String result = res.toString();
        // Beware of the differences between escaping in JSP attributes and
        // in Java Strings
        assertEcho(result, "00-hello 'world'");
        assertEcho(result, "01-hello 'world");
        assertEcho(result, "02-hello world'");
        assertEcho(result, "03-hello world'");
        assertEcho(result, "04-hello world\"");
        assertEcho(result, "05-hello \"world\"");
        assertEcho(result, "06-hello \"world");
        assertEcho(result, "07-hello world\"");
        assertEcho(result, "08-hello world'");
        assertEcho(result, "09-hello world\"");
    }

    @Test
    public void testBug45015b() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir =
            new File("test/webapp-3.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() +
                "/test/bug45nnn/bug45015b.jsp", new ByteChunk(), null);

        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);
    }

    @Test
    public void testBug45015c() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir =
            new File("test/webapp-3.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() +
                "/test/bug45nnn/bug45015c.jsp", new ByteChunk(), null);

        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);
    }

    @Test
    public void testBug48701Fail() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir =
            new File("test/webapp-3.0");
        // app dir is relative to server home
        StandardContext ctxt = (StandardContext) tomcat.addWebapp(null,
                "/test", appDir.getAbsolutePath());

        // This test needs the JSTL libraries
        File lib = new File("webapps/examples/WEB-INF/lib");
        ctxt.setAliases("/WEB-INF/lib=" + lib.getCanonicalPath());

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() +
                "/test/bug48nnn/bug48701-fail.jsp", new ByteChunk(), null);

        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);
    }

    @Test
    public void testBug48701UseBean() throws Exception {
        testBug48701("bug48nnn/bug48701-UseBean.jsp");
    }

    @Test
    public void testBug48701VariableInfo() throws Exception {
        testBug48701("bug48nnn/bug48701-VI.jsp");
    }

    @Test
    public void testBug48701TagVariableInfoNameGiven() throws Exception {
        testBug48701("bug48nnn/bug48701-TVI-NG.jsp");
    }

    @Test
    public void testBug48701TagVariableInfoNameFromAttribute() throws Exception {
        testBug48701("bug48nnn/bug48701-TVI-NFA.jsp");
    }

    private void testBug48701(String jsp) throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir =
            new File("test/webapp-3.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/" + jsp);

        String result = res.toString();
        assertEcho(result, "00-PASS");
    }

    public static class Bug48701 extends TagSupport {

        private static final long serialVersionUID = 1L;

        private String beanName = null;

        public void setBeanName(String beanName) {
            this.beanName = beanName;
        }

        public String getBeanName() {
            return beanName;
        }

        @Override
        public int doStartTag() throws JspException {
            Bean bean = new Bean();
            bean.setTime((new Date()).toString());
            pageContext.setAttribute("now", bean);
            return super.doStartTag();
        }


    }

    public static class Bug48701TEI extends TagExtraInfo {

        @Override
        public VariableInfo[] getVariableInfo(TagData data) {
            return new VariableInfo[] {
                    new VariableInfo("now", Bean.class.getCanonicalName(),
                            true, VariableInfo.AT_END)
                };
        }

    }

    public static class Bean {
        private String time;

        public void setTime(String time) {
            this.time = time;
        }

        public String getTime() {
            return time;
        }
    }

    @Test
    public void testBug49799() throws Exception {

        String[] expected = { "<p style=\"color:red\">00-Red</p>",
                              "<p>01-Not Red</p>",
                              "<p style=\"color:red\">02-Red</p>",
                              "<p>03-Not Red</p>",
                              "<p style=\"color:red\">04-Red</p>",
                              "<p>05-Not Red</p>"};

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-3.0");
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());
        tomcat.start();

        ByteChunk res = new ByteChunk();
        Map<String,List<String>> headers = new HashMap<String,List<String>>();

        getUrl("http://localhost:" + getPort() + "/test/bug49nnn/bug49799.jsp",
                res, headers);

        // Check request completed
        String result = res.toString();
        String[] lines = result.split("\n|\r|\r\n");
        int i = 0;
        for (String line : lines) {
            if (line.length() > 0) {
                assertEquals(expected[i], line);
                i++;
            }
        }
    }

    /** Assertion for text printed by tags:echo */
    private static void assertEcho(String result, String expected) {
        assertTrue(result.indexOf("<p>" + expected + "</p>") > 0);
    }
}

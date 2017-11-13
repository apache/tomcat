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

import java.io.IOException;
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

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestGenerator extends TomcatBaseTest {

    @Test
    public void testBug45015a() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

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
        getTomcatInstanceTestWebapp(false, true);

        int rc = getUrl("http://localhost:" + getPort() +
                "/test/bug45nnn/bug45015b.jsp", new ByteChunk(), null);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);
    }

    @Test
    public void testBug45015c() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        int rc = getUrl("http://localhost:" + getPort() +
                "/test/bug45nnn/bug45015c.jsp", new ByteChunk(), null);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);
    }

    @Test
    public void testBug48701Fail() throws Exception {
        getTomcatInstanceTestWebapp(true, true);

        int rc = getUrl("http://localhost:" + getPort() +
                "/test/bug48nnn/bug48701-fail.jsp", new ByteChunk(), null);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);
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
        getTomcatInstanceTestWebapp(false, true);

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

        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = new ByteChunk();
        Map<String,List<String>> headers = new HashMap<>();

        getUrl("http://localhost:" + getPort() + "/test/bug49nnn/bug49799.jsp",
                res, headers);

        // Check request completed
        String result = res.toString();
        String[] lines = result.split("\n|\r|\r\n");
        int i = 0;
        for (String line : lines) {
            if (line.length() > 0) {
                Assert.assertEquals(expected[i], line);
                i++;
            }
        }
    }

    /** Assertion for text printed by tags:echo */
    private static void assertEcho(String result, String expected) {
        Assert.assertTrue(result.indexOf("<p>" + expected + "</p>") > 0);
    }

    @Test
    public void testBug56529() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk bc = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() +
                "/test/bug5nnnn/bug56529.jsp", bc, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        String response = bc.toStringInternal();
        Assert.assertTrue(response,
                response.contains("[1:attribute1: '', attribute2: '']"));
        Assert.assertTrue(response,
                response.contains("[2:attribute1: '', attribute2: '']"));
   }

    public static class Bug56529 extends TagSupport {

        private static final long serialVersionUID = 1L;

        private String attribute1 = null;

        private String attribute2 = null;

        public void setAttribute1(String attribute1) {
            this.attribute1 = attribute1;
        }

        public String getAttribute1() {
            return attribute1;
        }

        public void setAttribute2(String attribute2) {
            this.attribute2 = attribute2;
        }

        public String getAttribute2() {
            return attribute2;
        }

        @Override
        public int doEndTag() throws JspException {
            try {
                pageContext.getOut().print(
                        "attribute1: '" + attribute1 + "', " + "attribute2: '"
                                + attribute2 + "'");
            } catch (IOException e) {
                throw new JspException(e);
            }
            return EVAL_PAGE;
        }

    }

    @Test
    public void testBug56581() throws LifecycleException {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = new ByteChunk();
        try {
            getUrl("http://localhost:" + getPort()
                    + "/test/bug5nnnn/bug56581.jsp", res, null);
            Assert.fail("An IOException was expected.");
        } catch (IOException expected) {
            // ErrorReportValve in Tomcat 8.0.9+ flushes and aborts the
            // connection when an unexpected error is encountered and response
            // has already been committed. It results in an exception here:
            // java.io.IOException: Premature EOF
        }

        String result = res.toString();
        Assert.assertTrue(result.startsWith("0 Hello world!\n"));
        Assert.assertTrue(result.endsWith("999 Hello world!\n"));
    }


    // https://bz.apache.org/bugzilla/show_bug.cgi?id=43400
    @Test
    public void testTagsWithEnums() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/bug43nnn/bug43400.jsp");

        String result = res.toString();
        System.out.println(result);
        assertEcho(result, "ASYNC");
    }
}

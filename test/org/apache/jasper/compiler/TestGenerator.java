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

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.beans.PropertyEditorSupport;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Scanner;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.PageContext;
import jakarta.servlet.jsp.tagext.BodyTagSupport;
import jakarta.servlet.jsp.tagext.DynamicAttributes;
import jakarta.servlet.jsp.tagext.JspIdConsumer;
import jakarta.servlet.jsp.tagext.Tag;
import jakarta.servlet.jsp.tagext.TagData;
import jakarta.servlet.jsp.tagext.TagExtraInfo;
import jakarta.servlet.jsp.tagext.TagSupport;
import jakarta.servlet.jsp.tagext.TryCatchFinally;
import jakarta.servlet.jsp.tagext.VariableInfo;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.jasper.servlet.JasperInitializer;
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
        getUrl("http://localhost:" + getPort() + "/test/bug49nnn/bug49799.jsp", res, null);

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

    @Test
    public void testTrimSpacesExtended01() throws Exception {
        doTestTrimSpacesExtended(false);
    }

    @Test
    public void testTrimSpacesExtended02() throws Exception {
        doTestTrimSpacesExtended(true);
    }

    private void doTestTrimSpacesExtended(boolean removeBlankLines) throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        Context ctxt = tomcat.addContext("", appDir.getAbsolutePath());
        ctxt.addServletContainerInitializer(new JasperInitializer(), null);

        Tomcat.initWebappDefaults(ctxt);
        Wrapper w = (Wrapper) ctxt.findChild("jsp");
        if (removeBlankLines) {
            w.addInitParameter("trimSpaces", "extended");
        }

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/jsp/trim-spaces-extended.jsp");

        String result = res.toString();
        Scanner scanner = new Scanner(result);
        int blankLineCount = 0;
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.length() == 0) {
                blankLineCount++;
            }
        }
        if (!removeBlankLines && blankLineCount == 0) {
            Assert.fail("TrimSpaceOptions.EXTENDED not configured but balnk lines have been removed");
        } else if (removeBlankLines && blankLineCount > 0) {
            Assert.fail("TrimSpaceOptions.EXTENDED does not allow the line to be just a new line character");
        }
        scanner.close();
    }

    @Test
    public void testEscape01() {
        String result = Generator.escape("\"\\\n\r");
        Assert.assertEquals("\\\"\\\\\\n\\r", result);
    }

    @Test
    public void testEscape02() {
        String result = Generator.escape("\\");
        Assert.assertEquals("\\\\", result);
    }

    @Test
    public void testEscape03() {
        String result = Generator.escape("xxx\\");
        Assert.assertEquals("xxx\\\\", result);
    }

    @Test
    public void testEscape04() {
        String result = Generator.escape("\\xxx");
        Assert.assertEquals("\\\\xxx", result);
    }

    @Test
    public void testQuote01() {
        String result = Generator.quote('\'');
        Assert.assertEquals("\'\\\'\'", result);
    }

    @Test
    public void testQuote02() {
        String result = Generator.quote('\\');
        Assert.assertEquals("\'\\\\\'", result);
    }

    @Test
    public void testQuote03() {
        String result = Generator.quote('\n');
        Assert.assertEquals("\'\\n\'", result);
    }

    @Test
    public void testQuote04() {
        String result = Generator.quote('\r');
        Assert.assertEquals("\'\\r\'", result);
    }

    @Test
    public void testQuote05() {
        String result = Generator.quote('x');
        Assert.assertEquals("\'x\'", result);
    }

    @Test
    public void testJspId() throws Exception {
        doTestJspId(false);
    }

    @Test
    public void testJspIdDocument() throws Exception {
        doTestJspId(true);
    }

    private void doTestJspId(boolean document) throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        String uri = "http://localhost:" + getPort() + "/test/jsp/generator/jsp-id.jsp";
        if (document) {
            uri += "x";
        }
        ByteChunk res = getUrl(uri);

        String result = res.toString();

        // Two tags should have different IDs
        String[] ids = new String[2];
        int start = 0;
        int end = 0;
        for (int i = 0; i < ids.length; i++) {
            start = result.indexOf("Jsp ID is [", start) + 11;
            end = result.indexOf("]", start);
            ids[i] = result.substring(start, end);
        }

        // Confirm the IDs are not the same
        Assert.assertNotEquals(ids[0], ids[1]);
    }

    @Test
    public void testTryCatchFinally02() throws Exception {
        doTestJsp("try-catch-finally-02.jsp");
    }

    public static class JspIdTag extends TagSupport implements JspIdConsumer {

        private static final long serialVersionUID = 1L;

        private volatile String jspId;

        @Override
        public int doStartTag() throws JspException {
            try {
                pageContext.getOut().print("<p>Jsp ID is [" + jspId + "]</p>");
            } catch (IOException ioe) {
                throw new JspException(ioe);
            }
            return super.doStartTag();
        }

        @Override
        public void setJspId(String jspId) {
            this.jspId = jspId;
        }
    }

    public static class TryCatchFinallyBodyTag extends BodyTagSupport implements TryCatchFinally {

        private static final long serialVersionUID = 1L;

        @Override
        public int doStartTag() throws JspException {
            try {
                pageContext.getOut().print("<p>OK</p>");
            } catch (IOException ioe) {
                throw new JspException(ioe);
            }
            return super.doStartTag();
        }

        @Override
        public void doCatch(Throwable t) throws Throwable {
            // NO-OP
        }

        @Override
        public void doFinally() {
            // NO-OP
        }
    }

    public static class TryCatchFinallyTag extends TagSupport implements TryCatchFinally {

        private static final long serialVersionUID = 1L;

        @Override
        public void doCatch(Throwable t) throws Throwable {
            // NO-OP
        }

        @Override
        public void doFinally() {
            // NO-OP
        }
    }

    public static class TesterBodyTag extends BodyTagSupport {

        private static final long serialVersionUID = 1L;

        @Override
        public int doStartTag() throws JspException {
            try {
                pageContext.getOut().print("<p>OK</p>");
            } catch (IOException ioe) {
                throw new JspException(ioe);
            }
            return super.doStartTag();
        }
    }

    public static class TesterTag implements Tag {

        private Tag parent;

        @Override
        public void setPageContext(PageContext pc) {
        }

        @Override
        public void setParent(Tag t) {
            parent = t;
        }

        @Override
        public Tag getParent() {
            return parent;
        }

        @Override
        public int doStartTag() throws JspException {
            return 0;
        }

        @Override
        public int doEndTag() throws JspException {
            return 0;
        }

        @Override
        public void release() {
        }
    }

    public static class TesterTagA extends TesterTag {
        private String data;

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }
    }

    public static class DataPropertyEditor extends PropertyEditorSupport {
    }

    public static class TesterScriptingTag extends TagSupport {

        private static final long serialVersionUID = 1L;

        private String attribute02;
        private String attribute03;

        public String getAttribute02() {
            return attribute02;
        }

        public void setAttribute02(String attribute02) {
            this.attribute02 = attribute02;
        }

        public String getAttribute03() {
            return attribute03;
        }

        public void setAttribute03(String attribute03) {
            this.attribute03 = attribute03;
        }
    }

    public static class TesterScriptingTagB extends TagSupport {

        private static final long serialVersionUID = 1L;

        private String attribute02;

        public String getAttribute02() {
            return attribute02;
        }

        public void setAttribute02(String attribute02) {
            this.attribute02 = attribute02;
        }
    }

    public static class TesterScriptingTagBTEI extends TagExtraInfo {

        @Override
        public VariableInfo[] getVariableInfo(TagData data) {
            return new VariableInfo[] {
                    new VariableInfo("variable01", "java.lang.String", true, VariableInfo.NESTED),
                    new VariableInfo(data.getAttribute("attribute02").toString(),
                            "java.lang.String", true, VariableInfo.NESTED),
                    new VariableInfo("variable03", "java.lang.String", false, VariableInfo.NESTED)
            };
        }

    }

    public static class TesterDynamicTag extends TagSupport implements DynamicAttributes {

        private static final long serialVersionUID = 1L;

        @Override
        public void setDynamicAttribute(String uri, String localName, Object value)
                throws JspException {
            // NO-OP
        }
    }

    public static class TesterAttributeTag extends TagSupport {

        private static final long serialVersionUID = 1L;

        private Object attribute01;
        private Object attribute02;
        private Object attribute03;
        private Object attribute04;
        private Object attribute05;
        private Object attribute06;

        public Object getAttribute01() {
            return attribute01;
        }

        public void setAttribute01(Object attribute01) {
            this.attribute01 = attribute01;
        }

        public Object getAttribute02() {
            return attribute02;
        }

        public void setAttribute02(Object attribute02) {
            this.attribute02 = attribute02;
        }

        public Object getAttribute03() {
            return attribute03;
        }

        public void setAttribute03(Object attribute03) {
            this.attribute03 = attribute03;
        }

        public Object getAttribute04() {
            return attribute04;
        }

        public void setAttribute04(Object attribute04) {
            this.attribute04 = attribute04;
        }

        public Object getAttribute05() {
            return attribute05;
        }

        public void setAttribute05(Object attribute05) {
            this.attribute05 = attribute05;
        }

        public Object getAttribute06() {
            return attribute06;
        }

        public void setAttribute06(Object attribute06) {
            this.attribute06 = attribute06;
        }
    }


    @Test
    public void testInfoConflictNone() throws Exception {
        doTestJsp("info-conflict-none.jsp");
    }

    @Test
    public void testInfoConflict() throws Exception {
        doTestJsp("info-conflict.jsp", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    @Test
    public void testTagWithVariable() throws Exception {
        doTestJsp("variable-tei-nested.jsp");
    }

    @Test
    public void testTagWithVariableFromAttr() throws Exception {
        doTestJsp("variable-from-attr-nested.jsp");
    }

    @Test
    public void testTagFileWithVariable() throws Exception {
        doTestJsp("variable-tagfile-nested.jsp");
    }

    @Test
    public void testTagFileWithVariableFromAttr() throws Exception {
        doTestJsp("variable-tagfile-from-attr-nested.jsp");
    }

    @Test
    public void testXpoweredBy() throws Exception {
        doTestJsp("x-powered-by.jsp");
    }

    @Test
    public void testXmlProlog01() throws Exception {
        doTestJsp("xml-prolog-01.jspx");
    }

    @Test
    public void testXmlProlog02() throws Exception {
        doTestJsp("xml-prolog-02.jspx");
    }

    @Test
    public void testXmlPrologTag() throws Exception {
        doTestJsp("xml-prolog-tag.jspx");
    }

    @Test
    public void testXmlDoctype01() throws Exception {
        doTestJsp("xml-doctype-01.jspx");
    }

    @Test
    public void testXmlDoctype02() throws Exception {
        doTestJsp("xml-doctype-02.jspx");
    }

    @Test
    public void testForward01() throws Exception {
        doTestJsp("forward-01.jsp");
    }

    @Test
    public void testForward02() throws Exception {
        doTestJsp("forward-02.jsp");
    }

    @Test
    public void testForward03() throws Exception {
        doTestJsp("forward-03.jsp");
    }

    @Test
    public void testForward04() throws Exception {
        doTestJsp("forward-04.jsp");
    }

    @Test
    public void testElement01() throws Exception {
        doTestJsp("element-01.jsp");
    }

    @Test
    public void testInclude01() throws Exception {
        doTestJsp("include-01.jsp");
    }

    @Test
    public void testSetProperty01() throws Exception {
        doTestJsp("setproperty-01.jsp");
    }

    @Test
    public void testUseBean01() throws Exception {
        doTestJsp("usebean-01.jsp", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    @Test
    public void testUseBean02() throws Exception {
        doTestJsp("usebean-02.jsp", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    @Test
    public void testUseBean03() throws Exception {
        doTestJsp("usebean-03.jsp");
    }

    @Test
    public void testUseBean04() throws Exception {
        doTestJsp("usebean-04.jsp", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    @Ignore // Requires specific Java settings
    @Test
    public void testUseBean05() throws Exception {
        // Whether this test passes or fails depends on the Java version used
        // and the JRE settings.
        // For the test to pass use --illegal-access=deny
        doTestJsp("usebean-05.jsp", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    @Test
    public void testUseBean06() throws Exception {
        doTestJsp("usebean-06.jsp", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    @Test
    public void testUseBean07() throws Exception {
        doTestJsp("usebean-07.jsp", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    @Test
    public void testUseBean08() throws Exception {
        doTestJsp("usebean-08.jsp", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    @Test
    public void testCustomTag01() throws Exception {
        doTestJsp("try-catch-finally-01.jsp");
    }

    @Test
    public void testCustomTag02() throws Exception {
        doTestJsp("customtag-02.jsp");
    }

    @Test
    public void testCustomTag03() throws Exception {
        doTestJsp("customtag-03.jsp");
    }

    @Test
    public void testCustomTag04() throws Exception {
        doTestJsp("customtag-04.jsp", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    @Test
    public void testTemplateText01() throws Exception {
        doTestJsp("templatetext-01.jsp");
    }

    @Test
    public void testTemplateText02() throws Exception {
        doTestJsp("templatetext-02.jsp");
    }

    @Test
    public void testInvoke01() throws Exception {
        doTestJsp("invoke-01.jsp");
    }

    @Test
    public void testDoBody01() throws Exception {
        doTestJsp("dobody-01.jsp");
    }

    @Test
    public void testScriptingVariables01() throws Exception {
        doTestJsp("scriptingvariables-01.jsp");
    }

    @Test
    public void testScriptingVariables02() throws Exception {
        doTestJsp("scriptingvariables-02.jsp");
    }

    @Test
    public void testAttribute01() throws Exception {
        doTestJsp("attribute-01.jsp");
    }

    @Test
    public void testAttribute02() throws Exception {
        doTestJsp("attribute-02.jsp");
    }

    @Test
    public void testAttribute03() throws Exception {
        doTestJsp("attribute-03.jsp");
    }

    @Test
    public void testAttribute04() throws Exception {
        doTestJsp("attribute-04.jsp");
    }

    @Test
    public void testSetters01() throws Exception {
        doTestJsp("setters-01.jsp");
    }

    @Test
    public void testCircular01() throws Exception {
        doTestJsp("circular-01.jsp");
    }

    @Test
    public void testDeferredMethod01() throws Exception {
        doTestJsp("deferred-method-01.jsp");
    }

    @Test
    public void testDeferredMethod02() throws Exception {
        doTestJsp("deferred-method-02.jsp");
    }

    @Test
    public void testBeanInfo01() throws Exception {
        BeanInfo bi = Introspector.getBeanInfo(TesterTagA.class);
        for (PropertyDescriptor pd : bi.getPropertyDescriptors()) {
            if (pd.getName().equals("data")) {
                pd.setPropertyEditorClass(DataPropertyEditor.class);
            }
        }

        doTestJsp("beaninfo-01.jsp");
    }

    @Test
    public void testBreakELInterpreter() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        // This should break all subsequent requests
        ByteChunk body = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/test/jsp/generator/break-el-interpreter.jsp", body, null);
        Assert.assertEquals(body.toString(), HttpServletResponse.SC_OK, rc);

        body.recycle();

        rc = getUrl("http://localhost:" + getPort() + "/test/jsp/generator/info.jsp", body, null);
        Assert.assertEquals(body.toString(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);
    }

    @Test
    public void testBreakStringInterpreter() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        // This should break all subsequent requests
        ByteChunk body = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/test/jsp/generator/break-string-interpreter.jsp", body, null);
        Assert.assertEquals(body.toString(), HttpServletResponse.SC_OK, rc);

        body.recycle();

        rc = getUrl("http://localhost:" + getPort() + "/test/jsp/generator/info.jsp", body, null);
        Assert.assertEquals(body.toString(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);
    }

    @Test
    public void testBug65390() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk body = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/test/bug6nnnn/bug65390.jsp", body, null);

        Assert.assertEquals(body.toString(), HttpServletResponse.SC_OK, rc);
    }

    private void doTestJsp(String jspName) throws Exception {
        doTestJsp(jspName, HttpServletResponse.SC_OK);
    }

    private void doTestJsp(String jspName, int expectedResponseCode) throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk body = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/test/jsp/generator/" + jspName, body, null);

        Assert.assertEquals(body.toString(), expectedResponseCode, rc);
    }
}

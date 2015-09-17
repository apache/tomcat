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
package org.apache.el;

import java.math.BigDecimal;
import java.util.Collections;

import javax.servlet.DispatcherType;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

/**
 * Tests EL with an without JSP attributes using a test web application. Similar
 * tests may be found in {@link TestELEvaluation} and
 * {@link org.apache.jasper.compiler.TestAttributeParser}.
 */
public class TestELInJsp extends TomcatBaseTest {

    @Test
    public void testBug36923() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/bug36923.jsp");

        String result = res.toString();
        assertEcho(result, "00-${hello world}");
    }

    @Test
    public void testBug42565() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/bug42565.jsp");

        String result = res.toString();
        assertEcho(result, "00-false");
        assertEcho(result, "01-false");
        assertEcho(result, "02-false");
        assertEcho(result, "03-false");
        assertEcho(result, "04-false");
        assertEcho(result, "05-false");
        assertEcho(result, "06-false");
        assertEcho(result, "07-false");
        assertEcho(result, "08-false");
        assertEcho(result, "09-false");
        assertEcho(result, "10-false");
        assertEcho(result, "11-false");
        assertEcho(result, "12-false");
        assertEcho(result, "13-false");
        assertEcho(result, "14-false");
        assertEcho(result, "15-false");
    }

    @Test
    public void testBug44994() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/bug44994.jsp");

        String result = res.toString();
        assertEcho(result, "00-none");
        assertEcho(result, "01-one");
        assertEcho(result, "02-many");
    }

    @Test
    public void testBug45427() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug45nnn/bug45427.jsp");

        String result = res.toString();
        // Warning: JSP attribute escaping != Java String escaping
        assertEcho(result, "00-hello world");
        assertEcho(result, "01-hello 'world");
        assertEcho(result, "02-hello \"world");
        assertEcho(result, "03-hello \"world");
        assertEcho(result, "04-hello world");
        assertEcho(result, "05-hello 'world");
        assertEcho(result, "06-hello 'world");
        assertEcho(result, "07-hello \"world");
        assertEcho(result, "08-hello world");
        assertEcho(result, "09-hello 'world");
        assertEcho(result, "10-hello \"world");
        assertEcho(result, "11-hello \"world");
        assertEcho(result, "12-hello world");
        assertEcho(result, "13-hello 'world");
        assertEcho(result, "14-hello 'world");
        assertEcho(result, "15-hello \"world");
    }

    @Test
    public void testBug45451() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug45nnn/bug45451a.jsp");

        String result = res.toString();
        // Warning: JSP attribute escaping != Java String escaping
        assertEcho(result, "00-\\'hello world\\'");
        assertEcho(result, "01-\\'hello world\\'");

        res = getUrl("http://localhost:" + getPort() + "/test/bug45nnn/bug45451b.jsp");
        result = res.toString();
        // Warning: JSP attribute escaping != Java String escaping
        // Warning: Attributes are always unescaped before passing to the EL
        //          processor
        assertEcho(result, "00-2");
        assertEcho(result, "01-${1+1}");
        assertEcho(result, "02-\\${1+1}");
        assertEcho(result, "03-\\\\${1+1}");
        assertEcho(result, "04-$500");
        // Inside an EL literal '\' is only used to escape '\', ''' and '"'
        assertEcho(result, "05-\\$");
        assertEcho(result, "06-\\${");
        assertEcho(result, "10-2");
        assertEcho(result, "11-${1+1}");
        assertEcho(result, "12-\\2");
        assertEcho(result, "13-\\${1+1}");
        assertEcho(result, "14-\\\\2");
        assertEcho(result, "15-$500");
        assertEcho(result, "16-\\$");
        assertEcho(result, "17-\\${");
        assertEcho(result, "20-2");
        assertEcho(result, "21-#{1+1}");
        assertEcho(result, "22-\\2");
        assertEcho(result, "23-\\#{1+1}");
        assertEcho(result, "24-\\\\2");
        assertEcho(result, "25-\\#");
        assertEcho(result, "26-\\#{");

        res = getUrl("http://localhost:" + getPort() + "/test/bug45nnn/bug45451c.jsp");
        result = res.toString();
        // Warning: JSP attribute escaping != Java String escaping
        // TODO - Currently we allow a single unescaped \ in attribute values
        //        Review if this should cause a warning/error
        assertEcho(result, "00-${1+1}");
        assertEcho(result, "01-\\${1+1}");
        assertEcho(result, "02-\\\\${1+1}");
        assertEcho(result, "03-\\\\\\${1+1}");
        assertEcho(result, "04-\\$500");
        assertEcho(result, "10-${1+1}");
        assertEcho(result, "11-\\${1+1}");
        assertEcho(result, "12-\\${1+1}");
        assertEcho(result, "13-\\\\${1+1}");
        assertEcho(result, "14-\\\\${1+1}");
        assertEcho(result, "15-\\$500");
        assertEcho(result, "20-#{1+1}");
        assertEcho(result, "21-\\#{1+1}");
        assertEcho(result, "22-\\#{1+1}");
        assertEcho(result, "23-\\\\#{1+1}");
        assertEcho(result, "24-\\\\#{1+1}");

        res = getUrl("http://localhost:" + getPort() + "/test/bug45nnn/bug45451d.jspx");
        result = res.toString();
        // Warning: JSP attribute escaping != Java String escaping
        // \\ Is *not* an escape sequence in XML attributes
        assertEcho(result, "00-2");
        assertEcho(result, "01-${1+1}");
        assertEcho(result, "02-\\${1+1}");
        assertEcho(result, "03-\\\\${1+1}");
        assertEcho(result, "04-$500");
        assertEcho(result, "10-2");
        assertEcho(result, "11-${1+1}");
        assertEcho(result, "12-\\${1+1}");
        assertEcho(result, "13-\\\\${1+1}");
        assertEcho(result, "14-\\\\\\${1+1}");
        assertEcho(result, "15-$500");
        assertEcho(result, "20-2");
        assertEcho(result, "21-#{1+1}");
        assertEcho(result, "22-\\#{1+1}");
        assertEcho(result, "23-\\\\#{1+1}");
        assertEcho(result, "24-\\\\\\#{1+1}");

        res = getUrl("http://localhost:" + getPort() + "/test/bug45nnn/bug45451e.jsp");
        result = res.toString();
        // Warning: JSP attribute escaping != Java String escaping
        // Warning: Attributes are always unescaped before passing to the EL
        //          processor
        assertEcho(result, "00-2");
        assertEcho(result, "01-${1+1}");
        assertEcho(result, "02-\\${1+1}");
        assertEcho(result, "03-\\\\${1+1}");
        assertEcho(result, "04-$500");
        assertEcho(result, "10-2");
        assertEcho(result, "11-${1+1}");
        assertEcho(result, "12-\\2");
        assertEcho(result, "13-\\${1+1}");
        assertEcho(result, "14-\\\\2");
        assertEcho(result, "15-$500");
        assertEcho(result, "20-#{1+1}");
        assertEcho(result, "21-\\#{1+1}");
        assertEcho(result, "22-\\#{1+1}");
        assertEcho(result, "23-\\\\#{1+1}");
        assertEcho(result, "24-\\\\#{1+1}");
    }

    @Test
    public void testBug45511() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug45nnn/bug45511.jsp");

        String result = res.toString();
        assertEcho(result, "00-true");
        assertEcho(result, "01-false");
    }

    @Test
    public void testBug46596() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/bug46596.jsp");
        String result = res.toString();
        assertEcho(result, "{OK}");
    }

    @Test
    public void testBug47413() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/bug47413.jsp");

        String result = res.toString();
        assertEcho(result, "00-hello world");
        assertEcho(result, "01-hello world");
        assertEcho(result, "02-3.22");
        assertEcho(result, "03-3.22");
        assertEcho(result, "04-17");
        assertEcho(result, "05-17");
        assertEcho(result, "06-hello world");
        assertEcho(result, "07-hello world");
        assertEcho(result, "08-0.0");
        assertEcho(result, "09-0.0");
        assertEcho(result, "10-0");
        assertEcho(result, "11-0");
    }

    @Test
    public void testBug48112() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug48nnn/bug48112.jsp");
        String result = res.toString();
        assertEcho(result, "{OK}");
    }

    @Test
    public void testBug49555() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/bug49nnn/bug49555.jsp");

        String result = res.toString();
        assertEcho(result, "00-" + TesterFunctions.Inner$Class.RETVAL);
    }

    @Test
    public void testBug51544() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug5nnnn/bug51544.jsp");

        String result = res.toString();
        assertEcho(result, "Empty list: true");
    }

    @Test
    public void testELMisc() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/el-misc.jsp");
        String result = res.toString();

        assertEcho(result, "00-\\\\\\\"${'hello world'}");
        assertEcho(result, "01-\\\\\\\"\\${'hello world'}");
        assertEcho(result, "02-\\\"${'hello world'}");
        assertEcho(result, "03-\\\"\\hello world");
        assertEcho(result, "2az-04");
        assertEcho(result, "05-a2z");
        assertEcho(result, "06-az2");
        assertEcho(result, "2az-07");
        assertEcho(result, "08-a2z");
        assertEcho(result, "09-az2");
        assertEcho(result, "10-${'foo'}bar");
        assertEcho(result, "11-\\\"}");
        assertEcho(result, "12-foo\\bar\\baz");
        assertEcho(result, "13-foo\\bar\\baz");
        assertEcho(result, "14-foo\\bar\\baz");
        assertEcho(result, "15-foo\\bar\\baz");
        assertEcho(result, "16-foo\\bar\\baz");
        assertEcho(result, "17-foo\\&apos;bar&apos;\\&quot;baz&quot;");
        assertEcho(result, "18-3");
        assertEcho(result, "19-4");
        assertEcho(result, "20-4");
        assertEcho(result, "21-[{value=11}, {value=12}, {value=13}, {value=14}]");
        assertEcho(result, "22-[{value=11}, {value=12}, {value=13}, {value=14}]");
    }

    @Test
    public void testScriptingExpression() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/script-expr.jsp");
        String result = res.toString();
        assertEcho(result, "00-hello world");
        assertEcho(result, "01-hello \"world");
        assertEcho(result, "02-hello \\\"world");
        assertEcho(result, "03-hello ${world");
        assertEcho(result, "04-hello \\${world");
        assertEcho(result, "05-hello world");
        assertEcho(result, "06-hello \"world");
        assertEcho(result, "07-hello \\\"world");
        assertEcho(result, "08-hello ${world");
        assertEcho(result, "09-hello \\${world");
        assertEcho(result, "10-hello <% world");
        assertEcho(result, "11-hello %> world");
    }

    @Test
    public void testELMethod() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/el-method.jsp");
        String result = res.toString();
        assertEcho(result, "00-Hello JUnit from Tomcat");
        assertEcho(result, "01-Hello JUnit from Tomcat");
        assertEcho(result, "02-Hello JUnit from Tomcat");
        assertEcho(result, "03-Hello JUnit from Tomcat");
        assertEcho(result, "04-Hello JUnit from Tomcat");
        assertEcho(result, "05-Hello JUnit from Tomcat");
    }

    @Test
    public void testBug56029() throws Exception {
        getTomcatInstanceTestWebapp(true, true);

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug5nnnn/bug56029.jspx");

        String result = res.toString();

        Assert.assertTrue(result.contains("[1]:[1]"));
    }


    @Test
    public void testBug56147() throws Exception {
        getTomcatInstanceTestWebapp(true, true);

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug5nnnn/bug56147.jsp");

        String result = res.toString();
        assertEcho(result, "00-OK");
    }


    @Test
    public void testBug56612() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug5nnnn/bug56612.jsp");

        String result = res.toString();
        Assert.assertTrue(result.contains("00-''"));
    }


    /*
     * java.lang should be imported by default
     */
    @Test
    public void testBug57141() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug5nnnn/bug57141.jsp");

        String result = res.toString();
        assertEcho(result, "00-true");
        assertEcho(result, "01-false");
        assertEcho(result, "02-2147483647");
    }


    /*
     * BZ https://bz.apache.org/bugzilla/show_bug.cgi?id=57142
     * javax.servlet, javax.servlet.http and javax.servlet.jsp should be
     * imported by default.
     */
    @Test
    public void testBug57142() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug5nnnn/bug57142.jsp");

        String result = res.toString();
        // javax.servlet
        assertEcho(result, "00-" + DispatcherType.ASYNC);
        // No obvious status fields for javax.servlet.http
        // Could hack something with HttpUtils...
        // No obvious status fields for javax.servlet.jsp
        // Wild card (package) import
        assertEcho(result, "01-" + BigDecimal.ROUND_UP);
        // Class import
        assertEcho(result, "02-" + Collections.EMPTY_LIST.size());
    }


    /*
     * BZ https://bz.apache.org/bugzilla/show_bug.cgi?id=57441
     * Can't validate function names defined in lambdas (or via imports)
     */
    @Test
    public void testBug57441() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug5nnnn/bug57441.jsp");

        String result = res.toString();
        assertEcho(result, "00-11");
    }


    // Assertion for text contained with <p></p>, e.g. printed by tags:echo
    private static void assertEcho(String result, String expected) {
        Assert.assertTrue(result, result.indexOf("<p>" + expected + "</p>") > 0);
    }
}

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

import java.io.File;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import org.apache.catalina.startup.Tomcat;
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
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-3.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug36923.jsp");

        String result = res.toString();
        assertEcho(result, "00-${hello world}");
    }

    @Test
    public void testBug42565() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-3.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug42565.jsp");

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
        Tomcat tomcat = getTomcatInstance();

        File appDir =
            new File("test/webapp-3.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug44994.jsp");

        String result = res.toString();
        assertEcho(result, "00-none");
        assertEcho(result, "01-one");
        assertEcho(result, "02-many");
    }

    @Test
    public void testBug45427() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir =
            new File("test/webapp-3.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug45nnn/bug45427.jsp");

        String result = res.toString();
        // Warning: JSP attribute escaping != Java String escaping
        assertEcho(result, "00-hello world");
        assertEcho(result, "01-hello 'world");
        assertEcho(result, "02-hello \"world");
        assertEcho(result, "03-hello world");
        assertEcho(result, "04-hello 'world");
        assertEcho(result, "05-hello \"world");
        assertEcho(result, "06-hello world");
        assertEcho(result, "07-hello 'world");
        assertEcho(result, "08-hello \"world");
        assertEcho(result, "09-hello world");
        assertEcho(result, "10-hello 'world");
        assertEcho(result, "11-hello \"world");
        assertEcho(result, "12-hello world");
        assertEcho(result, "13-hello 'world");
        assertEcho(result, "14-hello \"world");
        assertEcho(result, "15-hello world");
        assertEcho(result, "16-hello 'world");
        assertEcho(result, "17-hello \"world");
    }

    @Test
    public void testBug45451() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir =
            new File("test/webapp-3.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug45nnn/bug45451a.jsp");

        String result = res.toString();
        // Warning: JSP attribute escaping != Java String escaping
        assertEcho(result, "00-\\'hello world\\'");
        assertEcho(result, "01-\\'hello world\\'");
        assertEcho(result, "02-\\'hello world\\'");
        assertEcho(result, "03-\\'hello world\\'");

        res = getUrl("http://localhost:" + getPort() + "/test/bug45nnn/bug45451b.jsp");
        result = res.toString();
        // Warning: JSP attribute escaping != Java String escaping
        // Warning: Attributes are always unescaped before passing to the EL
        //          processor
        assertEcho(result, "00-2");
        assertEcho(result, "01-${1+1}");
        assertEcho(result, "02-\\${1+1}");
        assertEcho(result, "03-\\\\${1+1}");
        assertEcho(result, "04-2");
        assertEcho(result, "05-${1+1}");
        assertEcho(result, "06-\\2");
        assertEcho(result, "07-\\${1+1}");
        assertEcho(result, "08-\\\\2");
        assertEcho(result, "09-2");
        assertEcho(result, "10-#{1+1}");
        assertEcho(result, "11-\\2");
        assertEcho(result, "12-\\#{1+1}");
        assertEcho(result, "13-\\\\2");

        res = getUrl("http://localhost:" + getPort() + "/test/bug45nnn/bug45451c.jsp");
        result = res.toString();
        // Warning: JSP attribute escaping != Java String escaping
        // TODO - Currently we allow a single unescaped \ in attribute values
        //        Review if this should cause a warning/error
        assertEcho(result, "00-${1+1}");
        assertEcho(result, "01-\\${1+1}");
        assertEcho(result, "02-\\\\${1+1}");
        assertEcho(result, "03-\\\\\\${1+1}");
        assertEcho(result, "04-${1+1}");
        assertEcho(result, "05-\\${1+1}");
        assertEcho(result, "06-\\${1+1}");
        assertEcho(result, "07-\\\\${1+1}");
        assertEcho(result, "08-\\\\${1+1}");
        assertEcho(result, "09-#{1+1}");
        assertEcho(result, "10-\\#{1+1}");
        assertEcho(result, "11-\\#{1+1}");
        assertEcho(result, "12-\\\\#{1+1}");
        assertEcho(result, "13-\\\\#{1+1}");

        res = getUrl("http://localhost:" + getPort() + "/test/bug45nnn/bug45451d.jspx");
        result = res.toString();
        // Warning: JSP attribute escaping != Java String escaping
        // \\ Is *not* an escape sequence in XML attributes
        assertEcho(result, "00-2");
        assertEcho(result, "01-${1+1}");
        assertEcho(result, "02-\\${1+1}");
        assertEcho(result, "03-\\\\${1+1}");
        assertEcho(result, "04-2");
        assertEcho(result, "05-${1+1}");
        assertEcho(result, "06-\\${1+1}");
        assertEcho(result, "07-\\\\${1+1}");
        assertEcho(result, "08-\\\\\\${1+1}");
        assertEcho(result, "09-2");
        assertEcho(result, "10-#{1+1}");
        assertEcho(result, "11-\\#{1+1}");
        assertEcho(result, "12-\\\\#{1+1}");
        assertEcho(result, "13-\\\\\\#{1+1}");

        res = getUrl("http://localhost:" + getPort() + "/test/bug45nnn/bug45451e.jsp");
        result = res.toString();
        // Warning: JSP attribute escaping != Java String escaping
        // Warning: Attributes are always unescaped before passing to the EL
        //          processor
        assertEcho(result, "00-2");
        assertEcho(result, "01-${1+1}");
        assertEcho(result, "02-\\${1+1}");
        assertEcho(result, "03-\\\\${1+1}");
        assertEcho(result, "04-2");
        assertEcho(result, "05-${1+1}");
        assertEcho(result, "06-\\2");
        assertEcho(result, "07-\\${1+1}");
        assertEcho(result, "08-\\\\2");
        assertEcho(result, "09-#{1+1}");
        assertEcho(result, "10-\\#{1+1}");
        assertEcho(result, "11-\\#{1+1}");
        assertEcho(result, "12-\\\\#{1+1}");
        assertEcho(result, "13-\\\\#{1+1}");
    }

    @Test
    public void testBug45511() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir =
            new File("test/webapp-3.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug45nnn/bug45511.jsp");

        String result = res.toString();
        assertEcho(result, "00-true");
        assertEcho(result, "01-false");
    }

    @Test
    public void testBug46596() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir =
            new File("test/webapp-3.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug46596.jsp");
        String result = res.toString();
        assertEcho(result, "{OK}");
    }

    @Test
    public void testBug47413() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir =
            new File("test/webapp-3.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug47413.jsp");

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
        Tomcat tomcat = getTomcatInstance();

        File appDir =
            new File("test/webapp-3.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug48nnn/bug48112.jsp");
        String result = res.toString();
        assertEcho(result, "{OK}");
    }

    @Test
    public void testBug49555() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-3.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug49nnn/bug49555.jsp");

        String result = res.toString();
        assertEcho(result, "00-" + TesterFunctions.Inner$Class.RETVAL);
    }

    @Test
    public void testBug51544() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-3.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug5nnnn/bug51544.jsp");

        String result = res.toString();
        assertEcho(result, "Empty list: true");
    }

    @Test
    public void testELMisc() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir =
            new File("test/webapp-3.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/el-misc.jsp");
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
        assertEcho(result, "11-\"}");
        assertEcho(result, "12-foo\\bar\\baz");
        assertEcho(result, "13-foo\\bar\\baz");
        assertEcho(result, "14-foo\\bar\\baz");
        assertEcho(result, "15-foo\\bar\\baz");
        assertEcho(result, "16-foo\\bar\\baz");
        assertEcho(result, "17-foo\\bar\\baz");
    }

    @Test
    public void testScriptingExpression() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir =
            new File("test/webapp-3.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/script-expr.jsp");
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
        Tomcat tomcat = getTomcatInstance();

        File appDir =
            new File("test/webapp-3.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/el-method.jsp");
        String result = res.toString();
        assertEcho(result, "00-Hello JUnit from Tomcat");
        assertEcho(result, "01-Hello JUnit from Tomcat");
        assertEcho(result, "02-Hello JUnit from Tomcat");
        assertEcho(result, "03-Hello JUnit from Tomcat");
        assertEcho(result, "04-Hello JUnit from Tomcat");
        assertEcho(result, "05-Hello JUnit from Tomcat");
    }

    // Assertion for text contained with <p></p>, e.g. printed by tags:echo
    private static void assertEcho(String result, String expected) {
        assertTrue(result.indexOf("<p>" + expected + "</p>") > 0);
    }
}

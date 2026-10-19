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
import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.tagext.PageData;
import jakarta.servlet.jsp.tagext.TagLibraryValidator;
import jakarta.servlet.jsp.tagext.TagSupport;
import jakarta.servlet.jsp.tagext.ValidationMessage;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestValidator extends TomcatBaseTest {

    @Test
    public void testBug47331() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        int rc = getUrl("http://localhost:" + getPort() + "/test/bug47331.jsp", new ByteChunk(), null);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);
    }

    @Test
    public void testInvalidTldVersion() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = new ByteChunk();

        int rc = getUrl("http://localhost:" + getPort() + "/test/jsp/parsing/taglib-invalid-version.jsp", res, null);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);

        // The invalid jsp-version in the TLD must produce a clean translation
        // error. It previously triggered a raw NumberFormatException at
        // translation time since the value was parsed without a catch.
        String result = res.toString();
        Assert.assertTrue(result, result.contains("Invalid JSP version [2.1.1]"));
        Assert.assertFalse(result, result.contains("NumberFormatException"));
    }

    @Test
    public void testNullIdTlv() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = new ByteChunk();

        int rc = getUrl("http://localhost:" + getPort() + "/test/jsp/parsing/taglib-null-id-tlv.jsp", res, null);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);

        // The validator returns a ValidationMessage with a null id. The id
        // and the following separator must be omitted from the reported
        // message. They were previously appended unconditionally which
        // rendered the literal text "null: " in front of the message.
        String result = res.toString();
        Assert.assertTrue(result, result.contains("message without an id"));
        Assert.assertFalse(result, result.contains("null: message"));
        // The id of a ValidationMessage that has one is still reported
        Assert.assertTrue(result, result.contains("123: message with an id"));
    }

    @Test
    public void testDuplicateJspElementName() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = new ByteChunk();

        int rc = getUrl("http://localhost:" + getPort() + "/test/jsp/parsing/element-duplicate-name.jspx", res, null);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);

        // XML allows two attributes with the local name 'name' as long as
        // they use different namespace prefixes. jsp:element must report that
        // as a translation error. It previously sized the JspAttribute array
        // for a single 'name' attribute which left a null entry that later
        // triggered a NullPointerException.
        String result = res.toString();
        Assert.assertTrue(result, result.contains("attribute appears more than once"));
        Assert.assertFalse(result, result.contains("NullPointerException"));
    }

    @Test
    public void testTldVersions22() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-2.2");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/tld-versions.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>${'00-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'01-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>${'02-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'03-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>${'04-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'05-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>${'06-hello world'}</p>") > 0);
    }

    @Test
    public void testTldVersions23() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-2.3");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/tld-versions.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>${'00-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'01-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>${'02-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'03-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>${'04-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'05-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>${'06-hello world'}</p>") > 0);
    }

    @Test
    public void testTldVersions24() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-2.4");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/tld-versions.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>00-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'01-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>02-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'03-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>04-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'05-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>06-hello world</p>") > 0);
    }

    @Test
    public void testTldVersions25() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-2.5");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/tld-versions.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>00-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'01-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>02-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'03-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>04-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'05-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>06-hello world</p>") > 0);
    }

    @Test
    public void testTldVersions30() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-3.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/tld-versions.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>00-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'01-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>02-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'03-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>04-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'05-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>06-hello world</p>") > 0);
    }

    @Test
    public void testTldVersions31() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-3.1");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/tld-versions.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>00-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'01-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>02-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'03-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>04-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'05-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>06-hello world</p>") > 0);
    }

    @Test
    public void testTldVersions40() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-4.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/tld-versions.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>00-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'01-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>02-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'03-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>04-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'05-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>06-hello world</p>") > 0);
    }

    @Test
    public void testTldVersions50() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-5.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/tld-versions.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>00-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'01-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>02-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'03-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>04-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'05-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>06-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>07-hello world</p>") > 0);
    }

    @Test
    public void testTldVersions60() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-6.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/tld-versions.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>00-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'01-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>02-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'03-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>04-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'05-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>06-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>07-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>08-hello world</p>") > 0);
    }

    @Test
    public void testTldVersions61() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-6.1");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/tld-versions.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>00-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'01-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>02-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'03-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>04-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'05-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>06-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>07-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>08-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>09-hello world</p>") > 0);
    }

    @Test
    public void testTldVersions62() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-6.2");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/tld-versions.jsp");

        String result = res.toString();

        Assert.assertTrue(result.indexOf("<p>00-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'01-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>02-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'03-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>04-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>#{'05-hello world'}</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>06-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>07-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>08-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>09-hello world</p>") > 0);
        Assert.assertTrue(result.indexOf("<p>10-hello world</p>") > 0);
    }

    public static class NullIdValidator extends TagLibraryValidator {

        @Override
        public ValidationMessage[] validate(String prefix, String uri, PageData page) {
            return new ValidationMessage[] { new ValidationMessage(null, "message without an id"),
                    new ValidationMessage("123", "message with an id") };
        }
    }

    public static class Echo extends TagSupport {

        private static final long serialVersionUID = 1L;

        private String echo = null;

        public void setEcho(String echo) {
            this.echo = echo;
        }

        public String getEcho() {
            return echo;
        }

        @Override
        public int doStartTag() throws JspException {
            try {
                pageContext.getOut().print("<p>" + echo + "</p>");
            } catch (IOException ioe) {
                pageContext.getServletContext().log("Tag (Echo21) failure", ioe);
            }
            return super.doStartTag();
        }
    }
}

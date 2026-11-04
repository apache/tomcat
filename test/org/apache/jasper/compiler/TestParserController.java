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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestParserController extends TomcatBaseTest {

    @Test
    public void testIncludeInvalidUri() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = new ByteChunk();
        int sc = getUrl("http://localhost:" + getPort() + "/test/jsp/parsing/include-invalid-uri.jsp", res, null);

        Assert.assertEquals(500, sc);

        // The report should include the translation error for the invalid
        // include. The error handling code previously threw a
        // NullPointerException itself which was reported instead.
        String result = res.toString();
        Assert.assertTrue(result, result.contains("The include [a b.jsp] is invalid"));
        Assert.assertFalse(result, result.contains("Cannot invoke"));
    }

    @Test
    public void testJspRootDetection() throws Exception {
        // The pages for these tests are generated rather than stored with the
        // tests so that the output, which is compared exactly, always uses
        // Unix line endings for the template text
        Tomcat tomcat = getTomcatInstance();
        File appDir = new File(getTemporaryDirectory(), "root-detection");
        Assert.assertTrue(appDir.mkdirs() || appDir.isDirectory());

        // The root element may use any prefix as long as the prefix is bound
        // to the exact JSP namespace URI
        Files.writeString(new File(appDir, "jsp.jsp").toPath(),
                "<jsp:root xmlns:jsp=\"" + TagConstants.JSP_URI + "\" version=\"2.0\">\n" +
                        "<jsp:body>OK</jsp:body>\n</jsp:root>\n", StandardCharsets.UTF_8);
        Files.writeString(new File(appDir, "other.jsp").toPath(),
                "<mypfx:root xmlns:mypfx=\"" + TagConstants.JSP_URI + "\" version=\"2.0\">\n" +
                        "<mypfx:body>OK</mypfx:body>\n</mypfx:root>\n", StandardCharsets.UTF_8);

        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());
        tomcat.start();

        // Both pages must be parsed as JSP documents. They are handled as XML
        // syntax so, as the output below shows, only the body content is
        // written; the whitespace text between the elements is removed
        ByteChunk res = new ByteChunk();
        int sc = getUrl("http://localhost:" + getPort() + "/test/jsp.jsp", res, null);
        Assert.assertEquals(200, sc);
        Assert.assertEquals("OK", res.toString());

        res = new ByteChunk();
        sc = getUrl("http://localhost:" + getPort() + "/test/other.jsp", res, null);
        Assert.assertEquals(200, sc);
        Assert.assertEquals("OK", res.toString());
    }

    @Test
    public void testRootPrefixNamespaceIsNotJspDocument() throws Exception {
        // A namespace that only starts with the JSP namespace URI must not
        // trigger parsing as a JSP document
        Tomcat tomcat = getTomcatInstance();
        File appDir = new File(getTemporaryDirectory(), "root-prefix");
        Assert.assertTrue(appDir.mkdirs() || appDir.isDirectory());
        Files.writeString(new File(appDir, "prefix.jsp").toPath(),
                "<foo:root xmlns:foo=\"" + TagConstants.JSP_URI + "PageExtra\">\n" +
                        "<p>hello</p>\n</foo:root>\n", StandardCharsets.UTF_8);

        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());
        tomcat.start();

        ByteChunk res = new ByteChunk();
        int sc = getUrl("http://localhost:" + getPort() + "/test/prefix.jsp", res, null);

        Assert.assertEquals(200, sc);

        // The page must be handled as standard syntax: the template text,
        // including the line breaks written above, is output unchanged and no
        // XML declaration is added
        String result = res.toString();
        Assert.assertTrue(result, result.contains("PageExtra\">\n<p>hello</p>"));
        Assert.assertFalse(result, result.contains("<?xml"));
    }
}

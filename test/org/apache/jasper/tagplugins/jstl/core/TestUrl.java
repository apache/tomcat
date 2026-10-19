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
package org.apache.jasper.tagplugins.jstl.core;

import java.io.File;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.buf.ByteChunk;

public class TestUrl extends AbstractTestTag {

    @Override
    protected File getTagPluginsFile() {
        return new File("test/org/apache/jasper/tagplugins/jstl/core/url-tagPlugins.xml");
    }


    /*
     * A context path with a trailing slash must be joined with the value
     * without producing a doubled slash, as the standard handler strips the
     * trailing slash from the context before concatenation. Only the exact
     * context "/" was special cased before, so any other context ending in a
     * slash resolved to a path with "//" in it.
     */
    @Test
    public void testContextWithTrailingSlash() throws Exception {
        ByteChunk res = new ByteChunk();

        int rc = getUrl("http://localhost:" + getPort() +
                "/test/jstl/url-context-slash.jsp", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String body = res.toString();
        Assert.assertTrue(body, body.contains("TRAILING=[/jstltest/index.jsp]"));
        // The root context keeps resolving to just the value
        Assert.assertTrue(body, body.contains("ROOT=[/index.jsp]"));
        // A context without a trailing slash is simply concatenated
        Assert.assertTrue(body, body.contains("PLAIN=[/jstltest/index.jsp]"));
    }


    /*
     * The <c:param> plugin must add its parameters to the URL of its own
     * <c:url> tag, URL-encoded, the first one following an existing query
     * string with "&".
     */
    @Test
    public void testParams() throws Exception {
        ByteChunk res = new ByteChunk();

        int rc = getUrl("http://localhost:" + getPort() +
                "/test/jstl/url-params.jsp", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String body = res.toString();
        Assert.assertTrue(body, body.contains("U=[/test/a.jsp?x=1&sp=x+y&u2=%26%3D]"));
    }


    /*
     * A nested <c:url> in the body of another <c:url> (a nesting the JSTL tag
     * library validator accepts) must not disturb the outer tag. The plugins
     * used to exchange the working URL through the fixed page scope attribute
     * "url_without_param": the inner tag removed that attribute, so the outer
     * tag read it back as null and printed the literal "null". The working URL
     * is now shared with the body through the plugin context, so the inner tag
     * cannot interfere with the outer one.
     */
    @Test
    public void testNestedUrl() throws Exception {
        ByteChunk res = new ByteChunk();

        int rc = getUrl("http://localhost:" + getPort() +
                "/test/jstl/url-nested.jsp", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String body = res.toString();
        // The inner tag renders its URL, then the outer tag renders its own,
        // never the literal "null"
        Assert.assertTrue(body, body.contains("NEXT=[/test/b.jsp/test/a.jsp]"));
    }


    /*
     * The <c:url>/<c:param> plugin pair must not clobber an application page
     * scope attribute named "url_without_param". The plugins used to store the
     * working URL in such an attribute and remove it afterwards, overwriting
     * and then deleting the application attribute. The URL is now shared with
     * the body through the plugin context instead.
     */
    @Test
    public void testParamKeepsUserAttribute() throws Exception {
        ByteChunk res = new ByteChunk();

        int rc = getUrl("http://localhost:" + getPort() +
                "/test/jstl/url-clobber.jsp", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String body = res.toString();
        Assert.assertTrue(body, body.contains("KEEP=[keepme]"));
        // The parameter is still appended to the tag's own URL
        Assert.assertTrue(body, body.contains("U=[/test/a.jsp?p=q]"));
    }


    /*
     * A <c:param> nested in a plugin processed tag inside the <c:url> body,
     * as the JSTL tag library validator allows, must still reach the working
     * URL of the enclosing URL tag, as the standard handler adds it to the
     * closest URL tag ancestor. The append must stay conditional on the
     * enclosing tag having been executed.
     */
    @Test
    public void testParamNestedInIf() throws Exception {
        ByteChunk res = new ByteChunk();

        int rc = getUrl("http://localhost:" + getPort() +
                "/test/jstl/url-param-nested-if.jsp", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String body = res.toString();
        // The parameter of the executed <c:if> reaches the URL
        Assert.assertTrue(body, body.contains("U=[/test/a.jsp?p=q]"));
        // The parameter of the skipped <c:if> does not
        Assert.assertTrue(body, body.contains("U2=[/test/a.jsp]"));
    }


    /*
     * A <c:param> nested in a tag whose plugin falls back to the standard
     * handler, as <c:forEach> with a varStatus, is generated into the tag
     * method of that handler, where the working URL variable of an enclosing
     * plugin URL tag is out of scope. The page must still translate; the
     * standard parameter handler then reports that no standard URL tag
     * encloses it.
     */
    @Test
    public void testParamNestedInPluginFallback() throws Exception {
        ByteChunk res = new ByteChunk();

        int rc = getUrl("http://localhost:" + getPort() +
                "/test/jstl/url-param-fallback-nested.jsp", res, null);

        String body = res.toString();
        // The page must not fail at translation time
        Assert.assertFalse(body, body.contains("Unable to compile"));
        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);
    }
}

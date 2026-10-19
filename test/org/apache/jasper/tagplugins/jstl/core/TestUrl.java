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
}

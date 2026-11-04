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

public class TestImport extends AbstractTestTag {

    @Override
    protected File getTagPluginsFile() {
        return new File("test/org/apache/jasper/tagplugins/jstl/core/import-tagPlugins.xml");
    }

    /*
     * The <c:param> plugin must add its parameters to the URL of its own
     * <c:import> tag. The plugins previously exchanged the working URL
     * through a page scope attribute named "url_without_param". That fixed
     * name overwrote any application attribute of the same name while the
     * body was processed and removed it at the end. The URL is now shared
     * with the body through the plugin context instead.
     */
    @Test
    public void testParamKeepsUserAttribute() throws Exception {
        ByteChunk res = new ByteChunk();

        int rc = getUrl("http://localhost:" + getPort() +
                "/test/jstl/import-clobber.jsp", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String body = res.toString();
        Assert.assertTrue(body, body.contains("KEEP=[keepme]"));
    }


    /*
     * Parameters are appended to the URL, URL-encoded, the first one
     * following the existing query string with "&" (the target reads the
     * query string of its include dispatch from the Servlet include
     * attribute).
     */
    @Test
    public void testParams() throws Exception {
        ByteChunk res = new ByteChunk();

        int rc = getUrl("http://localhost:" + getPort() +
                "/test/jstl/import-params.jsp", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String body = res.toString();
        Assert.assertTrue(body, body.contains("QS=[a=b&sp=x+y&u=%26%3D]"));
    }


    /*
     * A nested <c:import> in the body of a non-varReader <c:import> is
     * rejected by the JSTL tag library validator before code generation
     * (only <c:param> children are allowed there), which is why the body of
     * the tag can never remove the working URL of the outer tag.
     */
    @Test
    public void testNestedImportRejected() throws Exception {
        ByteChunk res = new ByteChunk();

        int rc = getUrl("http://localhost:" + getPort() +
                "/test/jstl/import-nested.jsp", res, null);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);

        String body = res.toString();
        Assert.assertTrue(body, body.contains("Illegal child tag"));
    }
}

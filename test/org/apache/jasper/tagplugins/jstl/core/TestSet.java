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

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.buf.ByteChunk;

public class TestSet extends AbstractTestTag {

    @Test
    public void testBug54011() throws Exception {
        ByteChunk res = new ByteChunk();

        int rc = getUrl("http://localhost:" + getPort() +
                "/test/bug5nnnn/bug54012.jsp", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String body = res.toString();
        Assert.assertTrue(body.contains("OK"));
    }


    @Test
    public void testBug54338() throws Exception {
        ByteChunk res = new ByteChunk();

        int rc = getUrl("http://localhost:" + getPort() +
                "/test/bug5nnnn/bug54338.jsp", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String body = res.toString();
        Assert.assertTrue(body.contains("OK - 42"));
    }


    /*
     * A deferred value expression on a c:set with var is stored as a
     * mapping in the variable mapper of the EL context, not as an
     * attribute, so later expressions evaluate it lazily. Only the page
     * scope can hold such a mapping.
     */
    @Test
    public void testDeferredValue() throws Exception {
        ByteChunk res = new ByteChunk();

        int rc = getUrl("http://localhost:" + getPort() +
                "/test/jstl/set-deferred.jsp", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String body = res.toString();
        // The deferred value evaluates lazily when it is referenced, and is
        // not kept as an attribute
        Assert.assertTrue(body, body.contains("DEFERRED=[2]"));
        Assert.assertTrue(body, body.contains("ATTR=[null]"));
        // An explicit page scope behaves the same way
        Assert.assertTrue(body, body.contains("EXPLICIT=[2]"));
        // Setting the variable again replaces the mapping
        Assert.assertTrue(body, body.contains("REPLACE=[3]"));
        // A later plain value replaces the deferred one
        Assert.assertTrue(body, body.contains("FIRST=[lazy]"));
        Assert.assertTrue(body, body.contains("SECOND=[plain]"));
        // A null value removes the variable, deferred or not
        Assert.assertTrue(body, body.contains("THIRD=[]"));
        // A deferred value in another scope fails
        Assert.assertTrue(body, body.contains("ERR=[threw]"));
        Assert.assertTrue(body,
                body.contains("ERRMSG=[Invalid scope for ValueExpression in &lt;set&gt;. "
                        + "\"page\" scope is required but was \"request\"]"));
        Assert.assertTrue(body, body.contains("ERR2=[threw]"));
    }


    /*
     * A c:set with a value but neither var nor target sets nothing. The
     * standard handler treats the absent target as an invalid target and
     * fails, which the plugin has to keep doing.
     */
    @Test
    public void testNoVarNoTarget() throws Exception {
        ByteChunk res = new ByteChunk();

        int rc = getUrl("http://localhost:" + getPort() +
                "/test/jstl/set-no-var-target.jsp", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String body = res.toString();
        Assert.assertTrue(body, body.contains("ERR=[threw]"));
        Assert.assertTrue(body,
                body.contains("ERRMSG=[Attempt to set the property of an invalid object in &lt;set&gt;.]"));
        // A property makes no difference without a var or a target
        Assert.assertTrue(body, body.contains("ERR2=[threw]"));
    }
}

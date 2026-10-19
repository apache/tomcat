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

public class TestForTokens extends AbstractTestTag {

    /*
     * The reference implementation validates the begin, end and step
     * attributes in their setters, throwing a JspTagException. The plugin
     * must generate the equivalent checks rather than, for example, failing
     * with an ArithmeticException on step="0". The pages wrap the tag in
     * c:catch and render the caught exception, pinning the reference
     * exception type and message.
     */
    @Test
    public void testStepZero() throws Exception {
        doTestInvalidAttribute("fortokens-step-zero.jsp", "'step' <= 0");
    }


    @Test
    public void testBeginNegative() throws Exception {
        doTestInvalidAttribute("fortokens-begin-negative.jsp", "'begin' < 0");
    }


    private void doTestInvalidAttribute(String jsp, String message) throws Exception {
        ByteChunk res = new ByteChunk();

        int rc = getUrl("http://localhost:" + getPort() +
                "/test/jstl/" + jsp, res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertTrue(res.toString(), res.toString()
                .contains("jakarta.servlet.jsp.JspTagException: " + message));
    }
}

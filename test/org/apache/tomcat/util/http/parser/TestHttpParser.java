/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.util.http.parser;

import org.junit.Assert;
import org.junit.Test;

public class TestHttpParser {

    @Test
    public void testTokenDel() {
        Assert.assertFalse("DEL is not a token", HttpParser.isToken(127));
    }


    @Test
    public void testAbsolutePathRelaxedLargeInvalid() {
        HttpParser httpParser = new HttpParser(null, null);
        Assert.assertFalse(httpParser.isAbsolutePathRelaxed(Integer.MAX_VALUE));
    }


    @Test
    public void testAbsolutePathRelaxed01() {
        HttpParser httpParser = new HttpParser(null, null);
        Assert.assertFalse(httpParser.isAbsolutePathRelaxed('{'));
    }


    @Test
    public void testAbsolutePathRelaxed02() {
        HttpParser httpParser = new HttpParser("{", null);
        Assert.assertTrue(httpParser.isAbsolutePathRelaxed('{'));
    }


    @Test
    public void testAbsolutePathRelaxed03() {
        HttpParser httpParser = new HttpParser(null, "{");
        Assert.assertFalse(httpParser.isAbsolutePathRelaxed('{'));
    }


    @Test
    public void testAbsolutePathRelaxed04() {
        HttpParser httpParser = new HttpParser("\u1000", null);
        Assert.assertFalse(httpParser.isAbsolutePathRelaxed('{'));
    }


    @Test
    public void testAbsolutePathRelaxed05() {
        HttpParser httpParser = new HttpParser("", null);
        Assert.assertFalse(httpParser.isAbsolutePathRelaxed('{'));
    }


    @Test
    public void testQueryRelaxedLargeInvalid() {
        HttpParser httpParser = new HttpParser(null, null);
        Assert.assertFalse(httpParser.isQueryRelaxed(Integer.MAX_VALUE));
    }


    @Test
    public void testRequestTargetLargeInvalid() {
        Assert.assertTrue(HttpParser.isNotRequestTarget(Integer.MAX_VALUE));
    }


    @Test
    public void testHttpProtocolLargeInvalid() {
        Assert.assertFalse(HttpParser.isHttpProtocol(Integer.MAX_VALUE));
    }


    @Test
    public void testUserInfoLargeInvalid() {
        Assert.assertFalse(HttpParser.isUserInfo(Integer.MAX_VALUE));
    }


    @Test
    public void testAbsolutePathLargeInvalid() {
        Assert.assertFalse(HttpParser.isAbsolutePath(Integer.MAX_VALUE));
    }


    @Test
    public void testQueryLargeInvalid() {
        Assert.assertFalse(HttpParser.isQuery(Integer.MAX_VALUE));
    }


    @Test
    public void testUnquoteNull() {
        Assert.assertNull(HttpParser.unquote(null));
    }


    @Test
    public void testUnquoteShort() {
        String shortText = "a";
        Assert.assertEquals(shortText, HttpParser.unquote(shortText));
    }


    @Test
    public void testUnquoteUnquoted() {
        String shortText = "abcde";
        Assert.assertEquals(shortText, HttpParser.unquote(shortText));
    }


    @Test
    public void testUnquoteEscaped() {
        // Note: Test string is also Java escaped
        String shortText = "\"ab\\\"ab\"";
        String result = "ab\"ab";
        Assert.assertEquals(result, HttpParser.unquote(shortText));
    }


    @Test
    public void testUnquoteUnquotedEscaped() {
        // Note: Test string is also Java escaped
        String shortText = "ab\\\"ab";
        String result = "ab\"ab";
        Assert.assertEquals(result, HttpParser.unquote(shortText));
    }
}

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
    public void testUnquoteInvalid01() {
        // Note: Test string is also Java escaped
        String shortText = "aaa\\";
        Assert.assertNull(shortText, HttpParser.unquote(shortText));
    }


    @Test
    public void testTokenStringNull() {
        Assert.assertFalse(HttpParser.isToken(null));
    }


    @Test
    public void testTokenStringEmpty() {
        Assert.assertFalse(HttpParser.isToken(""));
    }


    @Test
    public void testTokenStringLws01() {
        Assert.assertFalse(HttpParser.isToken(" "));
    }


    @Test
    public void testTokenStringLws02() {
        Assert.assertFalse(HttpParser.isToken(" aaa"));
    }


    @Test
    public void testTokenStringLws03() {
        Assert.assertFalse(HttpParser.isToken("\taaa"));
    }


    @Test
    public void testTokenStringValid() {
        Assert.assertTrue(HttpParser.isToken("token"));
    }
}

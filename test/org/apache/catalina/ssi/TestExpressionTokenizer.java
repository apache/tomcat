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
package org.apache.catalina.ssi;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link ExpressionTokenizer}.
 */
public class TestExpressionTokenizer {

    @Test
    public void testEmptyExpression() {
        ExpressionTokenizer et = new ExpressionTokenizer("");
        Assert.assertFalse(et.hasMoreTokens());
        Assert.assertEquals(ExpressionTokenizer.TOKEN_END, et.nextToken());
    }


    @Test
    public void testWhitespaceOnlyExpression() {
        ExpressionTokenizer et = new ExpressionTokenizer("   ");
        Assert.assertFalse(et.hasMoreTokens());
        Assert.assertEquals(ExpressionTokenizer.TOKEN_END, et.nextToken());
    }


    @Test
    public void testSimpleString() {
        ExpressionTokenizer et = new ExpressionTokenizer("hello");
        Assert.assertTrue(et.hasMoreTokens());
        Assert.assertEquals(ExpressionTokenizer.TOKEN_STRING, et.nextToken());
        Assert.assertEquals("hello", et.getTokenValue());
        Assert.assertFalse(et.hasMoreTokens());
    }


    @Test
    public void testBraces() {
        ExpressionTokenizer et = new ExpressionTokenizer("(abc)");
        Assert.assertEquals(ExpressionTokenizer.TOKEN_LBRACE, et.nextToken());
        Assert.assertEquals(ExpressionTokenizer.TOKEN_STRING, et.nextToken());
        Assert.assertEquals("abc", et.getTokenValue());
        Assert.assertEquals(ExpressionTokenizer.TOKEN_RBRACE, et.nextToken());
    }


    @Test
    public void testEquality() {
        ExpressionTokenizer et = new ExpressionTokenizer("a = b");
        Assert.assertEquals(ExpressionTokenizer.TOKEN_STRING, et.nextToken());
        Assert.assertEquals("a", et.getTokenValue());
        Assert.assertEquals(ExpressionTokenizer.TOKEN_EQ, et.nextToken());
        Assert.assertEquals(ExpressionTokenizer.TOKEN_STRING, et.nextToken());
        Assert.assertEquals("b", et.getTokenValue());
    }


    @Test
    public void testNotEqual() {
        ExpressionTokenizer et = new ExpressionTokenizer("a != b");
        Assert.assertEquals(ExpressionTokenizer.TOKEN_STRING, et.nextToken());
        Assert.assertEquals(ExpressionTokenizer.TOKEN_NOT_EQ, et.nextToken());
        Assert.assertEquals(ExpressionTokenizer.TOKEN_STRING, et.nextToken());
    }


    @Test
    public void testNotOperator() {
        ExpressionTokenizer et = new ExpressionTokenizer("!abc");
        Assert.assertEquals(ExpressionTokenizer.TOKEN_NOT, et.nextToken());
        Assert.assertEquals(ExpressionTokenizer.TOKEN_STRING, et.nextToken());
        Assert.assertEquals("abc", et.getTokenValue());
    }


    @Test
    public void testAndOperator() {
        ExpressionTokenizer et = new ExpressionTokenizer("a && b");
        Assert.assertEquals(ExpressionTokenizer.TOKEN_STRING, et.nextToken());
        Assert.assertEquals(ExpressionTokenizer.TOKEN_AND, et.nextToken());
        Assert.assertEquals(ExpressionTokenizer.TOKEN_STRING, et.nextToken());
    }


    @Test
    public void testOrOperator() {
        ExpressionTokenizer et = new ExpressionTokenizer("a || b");
        Assert.assertEquals(ExpressionTokenizer.TOKEN_STRING, et.nextToken());
        Assert.assertEquals(ExpressionTokenizer.TOKEN_OR, et.nextToken());
        Assert.assertEquals(ExpressionTokenizer.TOKEN_STRING, et.nextToken());
    }


    @Test
    public void testComparisonOperators() {
        ExpressionTokenizer et;

        et = new ExpressionTokenizer("a > b");
        Assert.assertEquals(ExpressionTokenizer.TOKEN_STRING, et.nextToken());
        Assert.assertEquals(ExpressionTokenizer.TOKEN_GT, et.nextToken());
        Assert.assertEquals(ExpressionTokenizer.TOKEN_STRING, et.nextToken());

        et = new ExpressionTokenizer("a < b");
        Assert.assertEquals(ExpressionTokenizer.TOKEN_STRING, et.nextToken());
        Assert.assertEquals(ExpressionTokenizer.TOKEN_LT, et.nextToken());
        Assert.assertEquals(ExpressionTokenizer.TOKEN_STRING, et.nextToken());

        et = new ExpressionTokenizer("a >= b");
        Assert.assertEquals(ExpressionTokenizer.TOKEN_STRING, et.nextToken());
        Assert.assertEquals(ExpressionTokenizer.TOKEN_GE, et.nextToken());
        Assert.assertEquals(ExpressionTokenizer.TOKEN_STRING, et.nextToken());

        et = new ExpressionTokenizer("a <= b");
        Assert.assertEquals(ExpressionTokenizer.TOKEN_STRING, et.nextToken());
        Assert.assertEquals(ExpressionTokenizer.TOKEN_LE, et.nextToken());
        Assert.assertEquals(ExpressionTokenizer.TOKEN_STRING, et.nextToken());
    }


    @Test
    public void testQuotedString() {
        ExpressionTokenizer et = new ExpressionTokenizer("\"hello world\"");
        Assert.assertEquals(ExpressionTokenizer.TOKEN_STRING, et.nextToken());
        Assert.assertEquals("hello world", et.getTokenValue());
    }


    @Test
    public void testSingleQuotedString() {
        ExpressionTokenizer et = new ExpressionTokenizer("'hello world'");
        Assert.assertEquals(ExpressionTokenizer.TOKEN_STRING, et.nextToken());
        Assert.assertEquals("hello world", et.getTokenValue());
    }


    @Test
    public void testQuotedStringWithEscape() {
        ExpressionTokenizer et = new ExpressionTokenizer("\"hello\\\"world\"");
        Assert.assertEquals(ExpressionTokenizer.TOKEN_STRING, et.nextToken());
        Assert.assertEquals("hello\\\"world", et.getTokenValue());
    }


    @Test
    public void testRegexPattern() {
        ExpressionTokenizer et = new ExpressionTokenizer("/pattern/");
        Assert.assertEquals(ExpressionTokenizer.TOKEN_STRING, et.nextToken());
        Assert.assertEquals("/pattern/", et.getTokenValue());
    }


    @Test
    public void testRegexPatternWithEscape() {
        ExpressionTokenizer et = new ExpressionTokenizer("/patt\\/ern/");
        Assert.assertEquals(ExpressionTokenizer.TOKEN_STRING, et.nextToken());
        Assert.assertEquals("/patt\\/ern/", et.getTokenValue());
    }


    @Test
    public void testComplexExpression() {
        ExpressionTokenizer et = new ExpressionTokenizer(
                "\"abc\" = \"def\" && !\"ghi\"");

        Assert.assertEquals(ExpressionTokenizer.TOKEN_STRING, et.nextToken());
        Assert.assertEquals("abc", et.getTokenValue());
        Assert.assertEquals(ExpressionTokenizer.TOKEN_EQ, et.nextToken());
        Assert.assertEquals(ExpressionTokenizer.TOKEN_STRING, et.nextToken());
        Assert.assertEquals("def", et.getTokenValue());
        Assert.assertEquals(ExpressionTokenizer.TOKEN_AND, et.nextToken());
        Assert.assertEquals(ExpressionTokenizer.TOKEN_NOT, et.nextToken());
        Assert.assertEquals(ExpressionTokenizer.TOKEN_STRING, et.nextToken());
        Assert.assertEquals("ghi", et.getTokenValue());
        Assert.assertEquals(ExpressionTokenizer.TOKEN_END, et.nextToken());
    }


    @Test
    public void testGetIndex() {
        ExpressionTokenizer et = new ExpressionTokenizer("abc");
        Assert.assertEquals(0, et.getIndex());
        et.nextToken();
        Assert.assertEquals(3, et.getIndex());
    }


    @Test
    public void testIsMetaChar() {
        ExpressionTokenizer et = new ExpressionTokenizer("");

        Assert.assertTrue(et.isMetaChar(' '));
        Assert.assertTrue(et.isMetaChar('\t'));
        Assert.assertTrue(et.isMetaChar('('));
        Assert.assertTrue(et.isMetaChar(')'));
        Assert.assertTrue(et.isMetaChar('!'));
        Assert.assertTrue(et.isMetaChar('<'));
        Assert.assertTrue(et.isMetaChar('>'));
        Assert.assertTrue(et.isMetaChar('|'));
        Assert.assertTrue(et.isMetaChar('&'));
        Assert.assertTrue(et.isMetaChar('='));

        Assert.assertFalse(et.isMetaChar('a'));
        Assert.assertFalse(et.isMetaChar('0'));
        Assert.assertFalse(et.isMetaChar('"'));
    }


    @Test
    public void testTokenValueNullAfterNonString() {
        ExpressionTokenizer et = new ExpressionTokenizer("(");
        et.nextToken();
        Assert.assertNull(et.getTokenValue());
    }
}

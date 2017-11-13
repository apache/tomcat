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

import javax.el.ValueExpression;

import org.junit.Assert;
import org.junit.Test;

import org.apache.el.ExpressionFactoryImpl;
import org.apache.el.TesterFunctions;
import org.apache.jasper.el.ELContextImpl;

/**
 * Test the EL processing from JSP attributes. Similar tests may be found in
 * {@link org.apache.el.TestELEvaluation} and {@link org.apache.el.TestELInJsp}.
 */
public class TestAttributeParser {

    /**
     * Test use of spaces in ternary expressions. This was primarily an EL
     * parser bug.
     */
    @Test
    public void testBug42565() {
        Assert.assertEquals("false", evalAttr("${false?true:false}", '\"'));
        Assert.assertEquals("false", evalAttr("${false?true: false}", '\"'));
        Assert.assertEquals("false", evalAttr("${false?true :false}", '\"'));
        Assert.assertEquals("false", evalAttr("${false?true : false}", '\"'));
        Assert.assertEquals("false", evalAttr("${false? true:false}", '\"'));
        Assert.assertEquals("false", evalAttr("${false? true: false}", '\"'));
        Assert.assertEquals("false", evalAttr("${false? true :false}", '\"'));
        Assert.assertEquals("false", evalAttr("${false? true : false}", '\"'));
        Assert.assertEquals("false", evalAttr("${false ?true:false}", '\"'));
        Assert.assertEquals("false", evalAttr("${false ?true: false}", '\"'));
        Assert.assertEquals("false", evalAttr("${false ?true :false}", '\"'));
        Assert.assertEquals("false", evalAttr("${false ?true : false}", '\"'));
        Assert.assertEquals("false", evalAttr("${false ? true:false}", '\"'));
        Assert.assertEquals("false", evalAttr("${false ? true: false}", '\"'));
        Assert.assertEquals("false", evalAttr("${false ? true :false}", '\"'));
        Assert.assertEquals("false", evalAttr("${false ? true : false}", '\"'));
    }


    /**
     * Test use nested ternary expressions. Full tests in
     * {@link org.apache.el.TestELEvaluation}. This is just a smoke test to
     * ensure JSP attribute processing doesn't cause any additional issues.
     */
    @Test
    public void testBug44994() {
        Assert.assertEquals("none",
                evalAttr("${0 lt 0 ? 1 lt 0 ? 'many': 'one': 'none'}", '\"'));
        Assert.assertEquals("one",
                evalAttr("${0 lt 1 ? 1 lt 1 ? 'many': 'one': 'none'}", '\"'));
        Assert.assertEquals("many",
                evalAttr("${0 lt 2 ? 1 lt 2 ? 'many': 'one': 'none'}", '\"'));
    }


    /**
     * Test the quoting requirements of JSP attributes. This doesn't make use of
     * EL. See {@link #testBug45451()} for a test that combines JSP attribute
     * quoting and EL quoting.
     */
    @Test
    public void testBug45015() {
        // Warning: Java String quoting vs. JSP attribute quoting
        Assert.assertEquals("hello 'world'", evalAttr("hello 'world'", '\"'));
        Assert.assertEquals("hello 'world", evalAttr("hello 'world", '\"'));
        Assert.assertEquals("hello world'", evalAttr("hello world'", '\"'));
        Assert.assertEquals("hello world'", evalAttr("hello world\\'", '\"'));
        Assert.assertEquals("hello world\"", evalAttr("hello world\\\"", '\"'));
        Assert.assertEquals("hello \"world\"", evalAttr("hello \"world\"", '\"'));
        Assert.assertEquals("hello \"world", evalAttr("hello \"world", '\"'));
        Assert.assertEquals("hello world\"", evalAttr("hello world\"", '\"'));
        Assert.assertEquals("hello world'", evalAttr("hello world\\'", '\"'));
        Assert.assertEquals("hello world\"", evalAttr("hello world\\\"", '\"'));

        Assert.assertEquals("hello 'world'", evalAttr("hello 'world'", '\''));
        Assert.assertEquals("hello 'world", evalAttr("hello 'world", '\''));
        Assert.assertEquals("hello world'", evalAttr("hello world'", '\''));
        Assert.assertEquals("hello world'", evalAttr("hello world\\'", '\''));
        Assert.assertEquals("hello world\"", evalAttr("hello world\\\"", '\''));
        Assert.assertEquals("hello \"world\"", evalAttr("hello \"world\"", '\''));
        Assert.assertEquals("hello \"world", evalAttr("hello \"world", '\''));
        Assert.assertEquals("hello world\"", evalAttr("hello world\"", '\''));
        Assert.assertEquals("hello world'", evalAttr("hello world\\'", '\''));
        Assert.assertEquals("hello world\"", evalAttr("hello world\\\"", '\''));

    }

    @Test
    public void testBug45451() {
        Assert.assertEquals("2", evalAttr("${1+1}", '\"'));
        Assert.assertEquals("${1+1}", evalAttr("\\${1+1}", '\"'));
        Assert.assertEquals("\\2", evalAttr("\\\\${1+1}", '\"'));
    }

    @Test
    public void testBug49081() {
        Assert.assertEquals("#2", evalAttr("#${1+1}", '\"'));
    }

    @Test
    public void testLiteral() {
        // Inspired by work on bug 45451, comments from kkolinko on the dev
        // list and looking at the spec to find some edge cases

        // '\' is only an escape character inside a StringLiteral
        // Attribute escaping does not apply inside EL expressions
        Assert.assertEquals("\\", evalAttr("${'\\\\'}", '\"'));

        // Can use ''' inside '"' when quoting with '"' and vice versa without
        // escaping
        Assert.assertEquals("\\\"", evalAttr("${'\\\\\"'}", '\"'));
        Assert.assertEquals("\"\\", evalAttr("${'\\\"\\\\'}", '\"'));
        Assert.assertEquals("\\'", evalAttr("${'\\\\\\''}", '\"'));
        Assert.assertEquals("'\\", evalAttr("${'\\'\\\\'}", '\"'));

        // Quoting <% and %>
        Assert.assertEquals("hello <% world", evalAttr("hello <\\% world", '\"'));
        Assert.assertEquals("hello %> world", evalAttr("hello %> world", '\"'));

        // Test that the end of literal in EL expression is recognized in
        // parseEL(), be it quoted with single or double quotes. That is, that
        // AttributeParser correctly switches between parseLiteral and parseEL
        // methods.
        //
        // The test is based on the difference in how the '\' character is printed:
        // when in parseLiteral \\${ will be printed as ${'\'}${, but if we are still
        // inside of parseEL it will be printed as \${, thus preventing the EL
        // expression that follows from being evaluated.
        //
        Assert.assertEquals("foo\\bar\\baz", evalAttr("${\'foo\'}\\\\${\'bar\'}\\\\${\'baz\'}", '\"'));
        Assert.assertEquals("foo\\bar\\baz", evalAttr("${\'foo\'}\\\\${\"bar\"}\\\\${\'baz\'}", '\"'));
        Assert.assertEquals("foo\\bar\\baz", evalAttr("${\"foo\"}\\\\${\'bar\'}\\\\${\"baz\"}", '\"'));
    }

    @Test
    public void testScriptExpressionLiterals() {
        Assert.assertEquals(" \"hello world\" ", parseScriptExpression(
                " \"hello world\" ", (char) 0));
        Assert.assertEquals(" \"hello \\\"world\" ", parseScriptExpression(
                " \"hello \\\\\"world\" ", (char) 0));
    }

    private String evalAttr(String expression, char quote) {

        ExpressionFactoryImpl exprFactory = new ExpressionFactoryImpl();
        ELContextImpl ctx = new ELContextImpl(exprFactory);
        ctx.setFunctionMapper(new TesterFunctions.FMapper());
        ValueExpression ve = exprFactory.createValueExpression(ctx,
                AttributeParser.getUnquoted(expression, quote, false, false,
                        false, false),
                String.class);
        return (String) ve.getValue(ctx);
    }

    private String parseScriptExpression(String expression, char quote) {
        return AttributeParser.getUnquoted(expression, quote, false, false,
                false, false);
    }
}

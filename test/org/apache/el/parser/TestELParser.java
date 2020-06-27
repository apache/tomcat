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

package org.apache.el.parser;

import java.io.StringReader;

import jakarta.el.ELContext;
import jakarta.el.ELException;
import jakarta.el.ExpressionFactory;
import jakarta.el.ValueExpression;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import org.apache.jasper.el.ELContextImpl;
import org.apache.tomcat.util.collections.SynchronizedStack;

public class TestELParser {

    @Test
    public void testBug49081() {
        // OP's report
        testExpression("#${1+1}", "#2");

        // Variations on a theme
        testExpression("#", "#");
        testExpression("##", "##");
        testExpression("###", "###");
        testExpression("$", "$");
        testExpression("$$", "$$");
        testExpression("$$$", "$$$");
        testExpression("#$", "#$");
        testExpression("#$#", "#$#");
        testExpression("$#", "$#");
        testExpression("$#$", "$#$");

        testExpression("#{1+1}", "2");
        testExpression("##{1+1}", "#2");
        testExpression("###{1+1}", "##2");
        testExpression("${1+1}", "2");
        testExpression("$${1+1}", "$2");
        testExpression("$$${1+1}", "$$2");
        testExpression("#${1+1}", "#2");
        testExpression("#$#{1+1}", "#$2");
        testExpression("$#{1+1}", "$2");
        testExpression("$#${1+1}", "$#2");
    }

    @Test
    public void testJavaKeyWordSuffix() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        ELContext context = new ELContextImpl(factory);

        TesterBeanA beanA = new TesterBeanA();
        beanA.setInt("five");
        ValueExpression var =
            factory.createValueExpression(beanA, TesterBeanA.class);
        context.getVariableMapper().setVariable("beanA", var);

        // Should fail
        Exception e = null;
        try {
            factory.createValueExpression(context, "${beanA.int}",
                    String.class);
        } catch (ELException ele) {
            e = ele;
        }
        Assert.assertNotNull(e);
    }

    @Test
    public void testJavaKeyWordIdentifier() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        ELContext context = new ELContextImpl(factory);

        TesterBeanA beanA = new TesterBeanA();
        beanA.setInt("five");
        ValueExpression var =
            factory.createValueExpression(beanA, TesterBeanA.class);
        context.getVariableMapper().setVariable("this", var);

        // Should fail
        Exception e = null;
        try {
            factory.createValueExpression(context, "${this}", String.class);
        } catch (ELException ele) {
            e = ele;
        }
        Assert.assertNotNull(e);
    }


    @Test
    public void bug56179a() {
        doTestBug56179(0, "test == true");
    }

    @Test
    public void bug56179b() {
        doTestBug56179(1, "test == true");
    }

    @Test
    public void bug56179c() {
        doTestBug56179(2, "test == true");
    }

    @Test
    public void bug56179d() {
        doTestBug56179(3, "test == true");
    }

    @Test
    public void bug56179e() {
        doTestBug56179(4, "test == true");
    }

    @Test
    public void bug56179f() {
        doTestBug56179(5, "test == true");
    }

    @Test
    public void bug56179g() {
        doTestBug56179(0, "(test) == true");
    }

    @Test
    public void bug56179h() {
        doTestBug56179(1, "(test) == true");
    }

    @Test
    public void bug56179i() {
        doTestBug56179(2, "(test) == true");
    }

    @Test
    public void bug56179j() {
        doTestBug56179(3, "(test) == true");
    }

    @Test
    public void bug56179k() {
        doTestBug56179(4, "(test) == true");
    }

    @Test
    public void bug56179l() {
        doTestBug56179(5, "(test) == true");
    }

    @Test
    public void bug56179m() {
        doTestBug56179(5, "((test)) == true");
    }

    @Test
    public void bug56179n() {
        doTestBug56179(5, "(((test))) == true");
    }

    private void doTestBug56179(int parenthesesCount, String innerExpr) {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        ELContext context = new ELContextImpl(factory);

        ValueExpression var =
            factory.createValueExpression(Boolean.TRUE, Boolean.class);
        context.getVariableMapper().setVariable("test", var);

        StringBuilder expr = new StringBuilder();
        expr.append("${");
        for (int i = 0; i < parenthesesCount; i++) {
            expr.append("(");
        }
        expr.append(innerExpr);
        for (int i = 0; i < parenthesesCount; i++) {
            expr.append(")");
        }
        expr.append("}");
        ValueExpression ve = factory.createValueExpression(
                context, expr.toString(), String.class);

        String result = (String) ve.getValue(context);
        Assert.assertEquals("true", result);
    }

    @Test
    public void bug56185() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        ELContext context = new ELContextImpl(factory);

        TesterBeanC beanC = new TesterBeanC();
        ValueExpression var =
            factory.createValueExpression(beanC, TesterBeanC.class);
        context.getVariableMapper().setVariable("myBean", var);

        ValueExpression ve = factory.createValueExpression(context,
            "${(myBean.int1 > 1 and myBean.myBool) or "+
            "((myBean.myBool or myBean.myBool1) and myBean.int1 > 1)}",
            Boolean.class);
        Assert.assertEquals(Boolean.FALSE, ve.getValue(context));
        beanC.setInt1(2);
        beanC.setMyBool1(true);
        Assert.assertEquals(Boolean.TRUE, ve.getValue(context));
    }

    private void testExpression(String expression, String expected) {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        ELContext context = new ELContextImpl(factory);

        ValueExpression ve = factory.createValueExpression(
                context, expression, String.class);

        String result = (String) ve.getValue(context);
        Assert.assertEquals(expected, result);
    }

    /*
     * Test to explore if re-using Parser instances is faster.
     *
     * Tests on my laptop show:
     * - overhead by introducing the stack is in the noise for parsing even the
     *   simplest expression
     * - efficiency from re-using the ELParser is measurable for even a single
     *   reuse of the parser
     * - with large numbers of parses (~10k) performance for a trivial parse is
     *   three times faster
     * - around the 100 iterations mark GC overhead adds significant noise to
     *   the results - for consistent results you either need fewer parses to
     *   avoid triggering GC or more parses so the GC effects are evenly
     *   distributed between the runs
     *
     * Note that the test is single threaded.
     */
    @Ignore
    @Test
    public void testParserPerformance() throws ParseException {
        final int runs = 20;
        final int parseIterations = 10000;


        for (int j = 0; j < runs; j ++) {
            long start = System.nanoTime();
            SynchronizedStack<ELParser> stack = new SynchronizedStack<>();

            for (int i = 0; i < parseIterations; i ++) {
                ELParser parser = stack.pop();
                if (parser == null) {
                    parser = new ELParser(new StringReader("${'foo'}"));
                } else {
                    parser.ReInit(new StringReader("${'foo'}"));
                }
                parser.CompositeExpression();
                stack.push(parser);
            }
            long end = System.nanoTime();

            System.out.println(parseIterations +
                    " iterations using ELParser.ReInit(...) took " + (end - start) + "ns");
        }

        for (int j = 0; j < runs; j ++) {
            long start = System.nanoTime();
            for (int i = 0; i < parseIterations; i ++) {
                ELParser parser = new ELParser(new StringReader("${'foo'}"));
                parser.CompositeExpression();
            }
            long end = System.nanoTime();

            System.out.println(parseIterations +
                    " iterations using    new ELParser(...) took " + (end - start) + "ns");
        }
    }
}

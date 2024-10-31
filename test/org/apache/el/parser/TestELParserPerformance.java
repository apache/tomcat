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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.Callable;

import jakarta.el.ELBaseTest;
import jakarta.el.ELContext;
import jakarta.el.ELManager;
import jakarta.el.ExpressionFactory;
import jakarta.el.ValueExpression;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import org.apache.tomcat.util.collections.SynchronizedStack;

/*
 * This is a relative performance test so it remains part of the standard test run.
 */
public class TestELParserPerformance extends ELBaseTest {

    private static final long DEFAULT_TEST_ITERATIONS = 1000000;

    /*
     * Test to explore if re-using Parser instances is faster.
     *
     * Tests on my laptop show: - overhead by introducing the stack is in the noise for parsing even the simplest
     * expression - efficiency from re-using the ELParser is measurable for even a single reuse of the parser - with
     * large numbers of parses (~10k) performance for a trivial parse is three times faster - around the 100 iterations
     * mark GC overhead adds significant noise to the results - for consistent results you either need fewer parses to
     * avoid triggering GC or more parses so the GC effects are evenly distributed between the runs
     *
     * Note that the test is single threaded.
     */
    @Test
    public void testParserInstanceReuse() throws ParseException {
        final int runs = 20;
        final int parseIterations = 10000;

        long reinitTotalTime = 0;
        long newTotalTime = 0;

        for (int j = 0; j < runs; j++) {
            long start = System.nanoTime();
            SynchronizedStack<ELParser> stack = new SynchronizedStack<>();

            for (int i = 0; i < parseIterations; i++) {
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
            reinitTotalTime += (end - start);

            System.out.println(parseIterations + " iterations using ELParser.ReInit(...) took " + (end - start) + "ns");
        }

        for (int j = 0; j < runs; j++) {
            long start = System.nanoTime();
            for (int i = 0; i < parseIterations; i++) {
                ELParser parser = new ELParser(new StringReader("${'foo'}"));
                parser.CompositeExpression();
            }
            long end = System.nanoTime();
            newTotalTime += (end - start);

            System.out.println(parseIterations + " iterations using    new ELParser(...) took " + (end - start) + "ns");
        }

        Assert.assertTrue("Using new ElParser() was faster then using ELParser.ReInit", reinitTotalTime < newTotalTime);
    }


    /*
     * Ignored by default since this is an absolute test primarily for
     * https://bz.apache.org/bugzilla/show_bug.cgi?id=69338
     */
    @Ignore
    @Test
    public void testAstAnd() {

        ELManager manager = new ELManager();
        ELContext context = manager.getELContext();
        ExpressionFactory factory = ELManager.getExpressionFactory();

        long durations[] = new long[9];
        for (int j = 0; j < 5; j++) {
            for (int operandCount = 2; operandCount < 11; operandCount++) {

                StringBuilder sb = new StringBuilder("${true");
                for (int i = 2; i <= operandCount; i++) {
                    sb.append(" && true");
                }
                sb.append("}");

                String expression = sb.toString();

                long start = System.nanoTime();

                for (int i = 0; i < 2000000; i++) {
                    ValueExpression ve = factory.createValueExpression(context, expression, Boolean.class);
                    Boolean result = ve.getValue(context);
                    Assert.assertEquals(Boolean.TRUE, result);
                }

                long duration = System.nanoTime() - start;

                if (j > 0) {
                    durations[operandCount - 2] += duration;
                }
            }
        }
        for (int operandCount = 2; operandCount < 11; operandCount++) {
            System.out.println("Operand count [" + operandCount + "], duration [" + durations[operandCount - 2] + "]");
        }
        System.out.println("");
    }


    /*
     * Ignored by default since this is an absolute test primarily for
     * https://bz.apache.org/bugzilla/show_bug.cgi?id=69338
     */
    @Ignore
    @Test
    public void testAstNotEmpty() {

        ELManager manager = new ELManager();
        ELContext context = manager.getELContext();
        ExpressionFactory factory = ELManager.getExpressionFactory();

        for (int j = 0; j < 5; j++) {

            String expression = "${not empty 'abc'}";

            long start = System.nanoTime();

            for (int i = 0; i < 10000000; i++) {
                ValueExpression ve = factory.createValueExpression(context, expression, Boolean.class);
                Boolean result = ve.getValue(context);
                Assert.assertEquals(Boolean.TRUE, result);
            }

            long duration = System.nanoTime() - start;
            System.out.println("duration [" + duration + "]");

        }
        System.out.println("");
    }


    /*
     * Utility method for running tasks provided by the test*() methods.
     *
     * @param c           Callable to be executed.
     * @param description Label to be displayed in the results.
     *
     * @return Arbitrary response data provided by the Callable.
     */
    private Object runTest(Callable<Object> c, String description) throws Exception {

        Object result = null;
        for (int j = 0; j < 5; j++) {

            System.gc();
            long start = System.currentTimeMillis();

            for (int i = 0; i < DEFAULT_TEST_ITERATIONS; i++) {
                result = c.call();
            }

            long duration = System.currentTimeMillis() - start;
            System.out.println(description + " duration=[" + duration + "ms] or " +
                    (duration * 1000000 / DEFAULT_TEST_ITERATIONS + " ns each"));

        }
        System.out.println("Result: " + result);

        return result;
    }


    /*
     * Performance test of various expressions. This can be easily modified to explore the performance of other
     * expressions.
     *
     * The operations tested in this method allocate many objects. For most consistent results, use runtime arguments
     * such as these: -Xms3g -Xmx3g -XX:+UseParallelGC
     *
     * Ignored by default since this is an absolute test primarily for
     * https://bz.apache.org/bugzilla/show_bug.cgi?id=69381
     */
    @Ignore
    @Test
    public void testExpressions() throws Exception {

        ELManager manager = new ELManager();
        ELContext context = manager.getELContext();
        ExpressionFactory factory = ELManager.getExpressionFactory();
        TesterBeanD beanD = new TesterBeanD();
        TesterBeanE beanE = new TesterBeanE();
        beanD.setBean(beanE);
        beanD.setName("name");
        beanE.setName("name");
        beanD.setStringMap(new HashMap<>());
        beanD.setValList(new ArrayList<>(Arrays.asList("a", "b", "c")));

        ValueExpression var = factory.createValueExpression(beanD, TesterBeanD.class);
        context.getVariableMapper().setVariable("beanD", var);

        ValueExpression varB = factory.createValueExpression(beanE, TesterBeanE.class);
        context.getVariableMapper().setVariable("beanE", varB);

        var = factory.createValueExpression(TesterEnum.ONE, TesterEnum.class);
        context.getVariableMapper().setVariable("enumOne", var);

        String[] expressions = new String[] { "${beanD.name}", "${beanD.getName()}", "${beanE.getName()}" };
        for (String expression : expressions) {
            runTest(() -> {
                ValueExpression ve = factory.createValueExpression(context, expression, Object.class);
                return ve.getValue(context);
            }, expression);
        }
    }
}

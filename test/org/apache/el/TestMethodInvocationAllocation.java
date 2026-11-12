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
package org.apache.el;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.el.ELContext;
import jakarta.el.ExpressionFactory;
import jakarta.el.MethodExpression;
import jakarta.el.StandardELContext;
import jakarta.el.ValueExpression;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/**
 * Checks that evaluating an EL method invocation that has arguments does not copy every public method of the class on
 * each evaluation. The test measures the memory allocated by the thread so, unlike a test of elapsed time, the result
 * does not depend on the speed of the machine.
 */
public class TestMethodInvocationAllocation {

    private static final int WARM_UP = 20000;
    private static final int ITERATIONS = 20000;


    @Test
    public void testValueExpressionMethodWithArgument() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        ELContext context = createContext(factory);
        ValueExpression expression = factory.createValueExpression(context, "${map.containsKey('a')}", Object.class);

        Assert.assertEquals(Boolean.FALSE, expression.getValue(context));
        assertAllocationBelowCopyOfMethods(() -> expression.getValue(context));
    }


    @Test
    public void testMethodExpressionWithArgument() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        ELContext context = createContext(factory);
        MethodExpression expression = factory.createMethodExpression(context, "${map.containsKey('a')}",
                Object.class, new Class<?>[0]);

        Assert.assertEquals(Boolean.FALSE, expression.invoke(context, null));
        assertAllocationBelowCopyOfMethods(() -> expression.invoke(context, null));
    }


    private static ELContext createContext(ExpressionFactory factory) {
        StandardELContext context = new StandardELContext(factory);
        context.getVariableMapper().setVariable("map",
                factory.createValueExpression(new ConcurrentHashMap<String,String>(), Object.class));
        return context;
    }


    private static void assertAllocationBelowCopyOfMethods(Runnable evaluation) {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        Assume.assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
        com.sun.management.ThreadMXBean allocationBean = (com.sun.management.ThreadMXBean) bean;
        Assume.assumeTrue(allocationBean.isThreadAllocatedMemorySupported());
        long threadId = Thread.currentThread().getId();

        // The memory allocated each time Class.getMethods() copies the public methods of the class
        Class<?> clazz = ConcurrentHashMap.class;
        clazz.getMethods();
        long start = allocationBean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < 1000; i++) {
            clazz.getMethods();
        }
        long copyOfMethods = (allocationBean.getThreadAllocatedBytes(threadId) - start) / 1000;
        // The check is only meaningful if copying the methods allocates a lot
        Assume.assumeTrue(copyOfMethods > 5000);

        for (int i = 0; i < WARM_UP; i++) {
            evaluation.run();
        }
        start = allocationBean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < ITERATIONS; i++) {
            evaluation.run();
        }
        long perEvaluation = (allocationBean.getThreadAllocatedBytes(threadId) - start) / ITERATIONS;

        Assert.assertTrue("Allocated [" + perEvaluation + "] bytes for each evaluation. Copying the methods of the class"
                + " allocates [" + copyOfMethods + "] bytes", perEvaluation < copyOfMethods / 2);
    }
}

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
package org.apache.el.util;

import jakarta.el.ELContext;
import jakarta.el.ExpressionFactory;
import jakarta.el.MethodExpression;
import jakarta.el.MethodNotFoundException;

import org.junit.Assert;
import org.junit.Test;

import org.apache.jasper.el.ELContextImpl;

public class TestReflectionUtil {

    private static final Tester BASE = new Tester();

    /*
     * Expect failure as it is not possible to identify which method named
     * "testA()" is intended.
     */
    @Test(expected=MethodNotFoundException.class)
    public void testBug54370a() {
        ReflectionUtil.getMethod(null, BASE, "testA",
                new Class[] {null, String.class},
                new Object[] {null, ""});
    }

    /*
     * Expect failure as it is not possible to identify which method named
     * "testB()" is intended. Note: In EL null can always be coerced to a valid
     * value for a primitive.
     */
    @Test(expected=MethodNotFoundException.class)
    public void testBug54370b() {
        ReflectionUtil.getMethod(null, BASE, "testB",
                new Class[] {null, String.class},
                new Object[] {null, ""});
    }

    @Test
    public void testBug54370c() {
        ReflectionUtil.getMethod(null, BASE, "testC",
                new Class[] {null},
                new Object[] {null});
    }

    @Test
    public void testBug54370d() {
        ReflectionUtil.getMethod(null, BASE, "testD",
                new Class[] {null},
                new Object[] {null});
    }

    @Test
    public void testStaticMethodOnInstance() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        ELContext context = new ELContextImpl(factory);

        MethodExpression methodExpression = factory.createMethodExpression(context, "${\"1\".format(2)}", String.class, new Class<?>[] {});

        try {
            methodExpression.invoke(context, null);
        } catch (IllegalArgumentException iae) {
            // Ensure correct IllegalArgumentException is thrown
            String msg = iae.getMessage();
            Assert.assertTrue(msg, msg.contains("[format]"));
            return;
        }
        Assert.fail("No exception");
    }
}

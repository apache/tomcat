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
package org.apache.el.lang;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

/*
 * FunctionMapperImpl.Function must not decide equality from its hash code alone.
 */
public class TestFunctionMapperImpl {

    private static final Method METHOD;

    static {
        try {
            METHOD = Integer.class.getMethod("toString", int.class);
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }


    @Test
    public void testEqualsSameFunction() {
        FunctionMapperImpl.Function f1 = new FunctionMapperImpl.Function("fn", "convert", METHOD);
        FunctionMapperImpl.Function f2 = new FunctionMapperImpl.Function("fn", "convert", METHOD);

        Assert.assertEquals(f1, f2);
        Assert.assertEquals(f1.hashCode(), f2.hashCode());
    }


    @Test
    public void testNotEqualsDifferentLocalName() {
        FunctionMapperImpl.Function f1 = new FunctionMapperImpl.Function("fn", "convert", METHOD);
        FunctionMapperImpl.Function f2 = new FunctionMapperImpl.Function("fn", "other", METHOD);

        Assert.assertNotEquals(f1, f2);
    }


    @Test
    public void testNotEqualsDifferentPrefix() {
        FunctionMapperImpl.Function f1 = new FunctionMapperImpl.Function("fn1", "convert", METHOD);
        FunctionMapperImpl.Function f2 = new FunctionMapperImpl.Function("fn2", "convert", METHOD);

        Assert.assertNotEquals(f1, f2);
    }


    @Test
    public void testNotEqualsHashCollidingLocalNames() {
        // "Aa" and "BB" are a well known pair of strings with the same hash code
        Assert.assertEquals("Aa".hashCode(), "BB".hashCode());

        FunctionMapperImpl.Function f1 = new FunctionMapperImpl.Function("fn", "Aa", METHOD);
        FunctionMapperImpl.Function f2 = new FunctionMapperImpl.Function("fn", "BB", METHOD);

        Assert.assertNotEquals(f1, f2);
    }


    @Test
    public void testNotEqualsPrefixLocalNameBoundary() {
        // Same concatenation of prefix and local name but different functions
        FunctionMapperImpl.Function f1 = new FunctionMapperImpl.Function("ab", "c", METHOD);
        FunctionMapperImpl.Function f2 = new FunctionMapperImpl.Function("a", "bc", METHOD);

        Assert.assertNotEquals(f1, f2);

        Set<FunctionMapperImpl.Function> functions = new HashSet<>();
        functions.add(f1);
        functions.add(f2);
        Assert.assertEquals(2, functions.size());
    }


    @Test
    public void testNotEqualsOtherType() {
        FunctionMapperImpl.Function f1 = new FunctionMapperImpl.Function("fn", "convert", METHOD);

        Assert.assertNotEquals(f1, "fnconvert");
        Assert.assertNotEquals(f1, null);
    }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.el.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.el.ELContext;
import jakarta.el.ELManager;
import jakarta.el.ELProcessor;
import jakarta.el.ExpressionFactory;
import jakarta.el.ValueExpression;

import org.junit.Assert;
import org.junit.Test;

public class TestAstConcatenation {

    private static final List<String> simpleList = new ArrayList<>();
    private static final Map<String,String> simpleMap = new HashMap<>();
    private static final Set<String> simpleSet = new HashSet<>();

    static {
        simpleList.add("a");
        simpleList.add("b");
        simpleList.add("c");
        simpleList.add("b");
        simpleList.add("c");

        simpleMap.put("a", "1");
        simpleMap.put("b", "2");
        simpleMap.put("c", "3");

        simpleSet.add("a");
        simpleSet.add("b");
        simpleSet.add("c");
    }


    @Test
    public void testNullConcatNull() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("null += null", Integer.class);
        Assert.assertEquals(Integer.valueOf(0), result);
    }


    @Test
    public void testMapConcatMapNoConflicts() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("{'a':'1','b':'2'} += {'c':'3'}", Map.class);
        Assert.assertEquals(simpleMap, result);
    }


    @Test
    public void testMapConcatMapConflicts() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("{'a':'1','b':'3'} += {'b':'2','c':'3'}", Map.class);
        Assert.assertEquals(simpleMap, result);
    }


    @Test
    public void testSetConcatSetNoConflicts() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("{'a','b'} += {'c'}", Set.class);
        Assert.assertEquals(simpleSet, result);
    }


    @Test
    public void testSetConcatSetConflicts() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("{'a','b'} += {'b','c'}", Set.class);
        Assert.assertEquals(simpleSet, result);
    }


    @Test
    public void testSetConcatListNoConflicts() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("{'a','b'} += ['c']", Set.class);
        Assert.assertEquals(simpleSet, result);
    }


    @Test
    public void testSetConcatListConflicts() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("{'a','b'} += ['b','c','c']", Set.class);
        Assert.assertEquals(simpleSet, result);
    }


    @Test
    public void testListConcatList() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("['a','b','c'] += ['b','c']", List.class);
        Assert.assertEquals(simpleList, result);
    }


    @Test
    public void testListConcatSet() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("['a','b','c'] += {'b','c'}", List.class);
        Assert.assertEquals(simpleList, result);
    }


    /**
     * Test string concatenation.
     */
    @Test
    public void testConcatenation01() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("'a' += 'b'", String.class);
        Assert.assertEquals("ab", result);
    }

    /**
     * Test coercion to string then concatenation.
     */
    @Test
    public void testConcatenation02() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("1 += 2", String.class);
        Assert.assertEquals("12", result);
    }

    /**
     * Test string concatenation with whitespace.
     */
    @Test
    public void testConcatenation03() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("' a' += ' b '", String.class);
        Assert.assertEquals(" a b ", result);
    }

    /**
     * Test string concatenation with mixed types.
     */
    @Test
    public void testConcatenation04() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("'a' += 3", String.class);
        Assert.assertEquals("a3", result);
    }

    /**
     * Test operator precedence (+ before +=).
     */
    @Test
    public void testPrecedence01() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("1 + 2 += 3", String.class);
        Assert.assertEquals("33", result);
    }

    /**
     * Test operator precedence (+ before +=).
     */
    @Test
    public void testPrecedence02() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("1 += 2 + 3", String.class);
        Assert.assertEquals("15", result);
    }

    /**
     * Test operator precedence (+= before >).
     */
    @Test
    public void testPrecedence03() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("10 > 2 += 3", String.class);
        Assert.assertEquals("false", result);
    }

    /**
     * Test operator precedence (+= before >).
     */
    @Test
    public void testPrecedence04() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("1 += 2 > 3", String.class);
        Assert.assertEquals("true", result);
    }

    @Test
    public void testGetType() {
        ELProcessor processor = new ELProcessor();
        ELContext context = processor.getELManager().getELContext();
        ExpressionFactory factory = ELManager.getExpressionFactory();

        ValueExpression ve = factory.createValueExpression(
                context, "${'a' += 3}", String.class);

        Assert.assertEquals(String.class, ve.getType(context));
        Assert.assertEquals("a3", ve.getValue(context));
    }
}

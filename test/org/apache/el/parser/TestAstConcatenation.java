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

import jakarta.el.ELContext;
import jakarta.el.ELManager;
import jakarta.el.ELProcessor;
import jakarta.el.ExpressionFactory;
import jakarta.el.ValueExpression;

import org.junit.Assert;
import org.junit.Test;

public class TestAstConcatenation {

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

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
import jakarta.el.ELException;
import jakarta.el.ELProcessor;
import jakarta.el.ExpressionFactory;
import jakarta.el.StandardELContext;

import org.junit.Assert;
import org.junit.Test;

import org.apache.el.TesterFunctions;

public class TestAstFunction {

    @Test
    public void testImport01() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("Integer(1000)", Integer.class);
        Assert.assertEquals(Integer.valueOf(1000), result);
    }

    @Test
    public void testImport02() {
        ELProcessor processor = new ELProcessor();
        processor.getELManager().getELContext().getImportHandler().importStatic("java.lang.Integer.valueOf");
        Object result = processor.getValue("valueOf(1000)", Integer.class);
        Assert.assertEquals(Integer.valueOf(1000), result);
    }

    @Test
    public void testImportConstructorTooManyParameterSets() {
        // resolveClass path: the result of a constructor call is not treated as
        // a lambda so only a single set of method parameters is valid. The extra
        // set must be rejected rather than silently ignored.
        ELProcessor processor = new ELProcessor();
        try {
            processor.getValue("Integer(1000)(2000)", Object.class);
            Assert.fail("An ELException was expected");
        } catch (ELException e) {
            // Expected
        }
    }

    @Test
    public void testImportStaticTooManyParameterSets() {
        // resolveStatic path: the result of a static method call is not treated
        // as a lambda so only a single set of method parameters is valid. The
        // extra set must be rejected rather than silently ignored.
        ELProcessor processor = new ELProcessor();
        processor.getELManager().getELContext().getImportHandler().importStatic("java.lang.String.valueOf");
        try {
            processor.getValue("valueOf('a')('b')", Object.class);
            Assert.fail("An ELException was expected");
        } catch (ELException e) {
            // Expected
        }
    }

    @Test
    public void testUnresolvedNameTooManyParameterSets() {
        // A name that resolves to neither a class nor a static method must still
        // report the "function not found" error, not the too-many-parameters
        // error. This pins the placement of the parameter-set check so that it
        // cannot be hoisted above the resolution.
        ELProcessor processor = new ELProcessor();
        try {
            processor.getValue("unknown(1)(2)", Object.class);
            Assert.fail("An ELException was expected");
        } catch (ELException e) {
            Assert.assertTrue("Unexpected message: " + e.getMessage(),
                    e.getMessage().contains("not found"));
        }
    }

    @Test
    public void testVarargMethod() throws NoSuchMethodException, SecurityException {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        ELContext context = new StandardELContext(factory);
        context.getFunctionMapper().mapFunction("fn", "format",
                String.class.getMethod("format", String.class, Object[].class));

        //Object result = factory.createValueExpression(context, "${fn:format('%s-%s','one','two')}", String.class)
        //        .getValue(context);
        //Assert.assertEquals("one-two", result);

        Object result = factory.createValueExpression(context, "${fn:format('%s-%s','one,two'.split(','))}", String.class)
                .getValue(context);
        Assert.assertEquals("one-two", result);

        result = factory.createValueExpression(context, "${fn:format('%s','one')}", String.class).getValue(context);
        Assert.assertEquals("one", result);
    }

    @Test
    public void testVarargMethodEmptyVarargs01() throws NoSuchMethodException {
        // Java passes an empty array, never null, to a varargs method that is
        // called with no arguments
        ExpressionFactory factory = ExpressionFactory.newInstance();
        ELContext context = new StandardELContext(factory);
        context.getFunctionMapper().mapFunction("fn", "count",
                TesterFunctions.class.getMethod("varargsLength", String[].class));

        Object result = factory.createValueExpression(context, "${fn:count()}", Integer.class).getValue(context);
        Assert.assertEquals(Integer.valueOf(0), result);
    }

    @Test
    public void testVarargMethodEmptyVarargs02() throws NoSuchMethodException {
        // Java passes an empty array, never null, to the varargs parameter of a
        // mixed fixed and varargs method that is called with no varargs elements
        ExpressionFactory factory = ExpressionFactory.newInstance();
        ELContext context = new StandardELContext(factory);
        context.getFunctionMapper().mapFunction("fn", "count2",
                TesterFunctions.class.getMethod("varargsLength2", String.class, String[].class));

        Object result = factory.createValueExpression(context, "${fn:count2('x')}", Integer.class).getValue(context);
        Assert.assertEquals(Integer.valueOf(0), result);
    }
}

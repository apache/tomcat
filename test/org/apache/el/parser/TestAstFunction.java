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
import jakarta.el.ELProcessor;
import jakarta.el.ExpressionFactory;
import jakarta.el.StandardELContext;

import org.junit.Assert;
import org.junit.Test;

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
}

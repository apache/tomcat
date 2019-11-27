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

public class TestAstAssign {

    @Test
    public void testGetValue01() {
        ELProcessor processor = new ELProcessor();
        processor.defineBean("bean01", new TesterBeanB());

        Object result = processor.getValue(
                "bean01.text = 'hello'", String.class);

        Assert.assertEquals("hello", result);
    }


    @Test
    public void testGetValue02() {
        ELProcessor processor = new ELProcessor();
        processor.defineBean("bean01", new TesterBeanB());

        Object result = processor.getValue(
                "bean01.text = 'hello'; bean01.text", String.class);

        Assert.assertEquals("hello", result);
    }



    @Test
    public void testGetType01() {
        ELProcessor processor = new ELProcessor();
        ELContext context = processor.getELManager().getELContext();
        ExpressionFactory factory = ELManager.getExpressionFactory();

        processor.defineBean("bean01", new TesterBeanB());
        ValueExpression ve = factory.createValueExpression(
                context, "${bean01.text = 'hello'}", String.class);

        Assert.assertEquals(String.class, ve.getType(context));
        Assert.assertEquals("hello", ve.getValue(context));
    }


    @Test
    public void testGetType02() {
        ELProcessor processor = new ELProcessor();
        ELContext context = processor.getELManager().getELContext();
        ExpressionFactory factory = ELManager.getExpressionFactory();

        processor.defineBean("bean01", new TesterBeanB());
        ValueExpression ve = factory.createValueExpression(
                context, "${bean01.text = 'hello'; bean01.text}", String.class);

        Assert.assertEquals(String.class, ve.getType(context));
        Assert.assertEquals("hello", ve.getValue(context));
    }
}

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

import javax.el.ELContext;
import javax.el.ELManager;
import javax.el.ELProcessor;
import javax.el.ExpressionFactory;
import javax.el.ValueExpression;

import org.junit.Assert;
import org.junit.Test;

public class TestAstSemicolon {

    @Test
    public void testGetValue01() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("1;2", String.class);
        Assert.assertEquals("2", result);
    }


    @Test
    public void testGetValue02() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("1;2", Integer.class);
        Assert.assertEquals(Integer.valueOf(2), result);
    }


    @Test
    public void testGetValue03() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("1;2 + 3", Integer.class);
        Assert.assertEquals(Integer.valueOf(5), result);
    }


    @Test
    public void testGetType() {
        ELProcessor processor = new ELProcessor();
        ELContext context = processor.getELManager().getELContext();
        ExpressionFactory factory = ELManager.getExpressionFactory();

        ValueExpression ve = factory.createValueExpression(
                context, "${1+1;2+2}", Integer.class);

        Assert.assertEquals(Number.class, ve.getType(context));
        Assert.assertEquals(Integer.valueOf(4), ve.getValue(context));
    }
}

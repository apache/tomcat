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
import java.util.Collections;
import java.util.List;

import javax.el.ELContext;
import javax.el.ELManager;
import javax.el.ELProcessor;
import javax.el.ExpressionFactory;
import javax.el.ValueExpression;

import org.junit.Assert;
import org.junit.Test;

public class TestAstListData {

    private static final List<String> simpleList = new ArrayList<>();
    private static final List<Object> nestedList = new ArrayList<>();

    static {
        simpleList.add("a");
        simpleList.add("b");
        simpleList.add("c");
        simpleList.add("b");
        simpleList.add("c");
        nestedList.add(simpleList);
        nestedList.add(Collections.EMPTY_LIST);
        nestedList.add("d");
    }


    @Test
    public void testSimple01() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue(
                "['a','b','c', 'b', 'c']", List.class);
        Assert.assertEquals(simpleList, result);
    }


    @Test
    public void testSimple02() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("[]", List.class);
        Assert.assertEquals(Collections.EMPTY_LIST, result);
    }


    @Test
    public void testNested01() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue(
                "[['a','b','c','b','c'],[],'d']", List.class);
        Assert.assertEquals(nestedList, result);
    }


    @Test
    public void testGetType() {
        ELProcessor processor = new ELProcessor();
        ELContext context = processor.getELManager().getELContext();
        ExpressionFactory factory = ELManager.getExpressionFactory();

        ValueExpression ve = factory.createValueExpression(
                context, "${['a','b','c','b','c']}", List.class);

        Assert.assertEquals(List.class, ve.getType(context));
        Assert.assertEquals(simpleList, ve.getValue(context));
    }
}

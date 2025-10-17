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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import jakarta.el.ELContext;
import jakarta.el.ELManager;
import jakarta.el.ELProcessor;
import jakarta.el.ExpressionFactory;
import jakarta.el.ValueExpression;

import org.junit.Assert;
import org.junit.Test;

public class TestAstSetData {

    private static final Set<String> simpleSet = new HashSet<>();
    private static final Set<Object> nestedSet = new HashSet<>();

    static {
        simpleSet.add("a");
        simpleSet.add("b");
        simpleSet.add("c");

        nestedSet.add(simpleSet);
        nestedSet.add(Collections.EMPTY_SET);
        nestedSet.add("d");
    }


    @Test
    public void testSimple01() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("{'a','b','c'}", Set.class);
        Assert.assertEquals(simpleSet, result);
    }


    @Test
    public void testSimple02() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("{}", Set.class);
        Assert.assertEquals(Collections.EMPTY_SET, result);
    }


    @Test
    public void testNested01() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("{{'a','b','c'},{},'d'}", Set.class);
        Assert.assertEquals(nestedSet, result);
    }


    @Test
    public void testGetType() {
        ELProcessor processor = new ELProcessor();
        ELContext context = processor.getELManager().getELContext();
        ExpressionFactory factory = ELManager.getExpressionFactory();

        ValueExpression ve = factory.createValueExpression(
                context, "${{'a','b','c'}}", Set.class);

        Assert.assertEquals(Set.class, ve.getType(context));
        Assert.assertEquals(simpleSet, ve.getValue(context));
    }
}

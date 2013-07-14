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
import java.util.HashMap;
import java.util.Map;

import javax.el.ELContext;
import javax.el.ELManager;
import javax.el.ELProcessor;
import javax.el.ExpressionFactory;
import javax.el.ValueExpression;

import org.junit.Assert;
import org.junit.Test;

public class TestAstMapData {

    private static final Map<String,String> simpleMap = new HashMap<>();
    private static final Map<Object,Object> nestedMap = new HashMap<>();

    static {
        simpleMap.put("a", "1");
        simpleMap.put("b", "2");
        simpleMap.put("c", "3");

        nestedMap.put("simple", simpleMap);
        // {} will get parsed as an empty Set as there is nothing to hint to the
        // parser that Map is expected here.
        nestedMap.put("empty", Collections.EMPTY_SET);
        nestedMap.put("d", "4");
    }


    @Test
    public void testSimple01() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue(
                "{'a':'1','b':'2','c':'3'}", Map.class);
        Assert.assertEquals(simpleMap, result);
    }


    @Test
    public void testSimple02() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("{}", Map.class);
        Assert.assertEquals(Collections.EMPTY_MAP, result);
    }


    @Test
    public void testNested01() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue(
                "{'simple':{'a':'1','b':'2','c':'3'}," +
                "'empty':{}," +
                "'d':'4'}", Map.class);
        Assert.assertEquals(nestedMap, result);
    }


    @Test
    public void testGetType() {
        ELProcessor processor = new ELProcessor();
        ELContext context = processor.getELManager().getELContext();
        ExpressionFactory factory = ELManager.getExpressionFactory();

        ValueExpression ve = factory.createValueExpression(
                context, "${{'a':'1','b':'2','c':'3'}}", Map.class);

        Assert.assertEquals(Map.class, ve.getType(context));
        Assert.assertEquals(simpleMap, ve.getValue(context));
    }
}

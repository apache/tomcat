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

import jakarta.el.ELProcessor;

import org.junit.Assert;
import org.junit.Test;

public class TestAstPlus {

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
    public void testNullAddNull() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("null + null", Integer.class);
        Assert.assertEquals(Integer.valueOf(0), result);
    }


    @Test
    public void testMapAddMapNoConflicts() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("{'a':'1','b':'2'} + {'c':'3'}", Map.class);
        Assert.assertEquals(simpleMap, result);
    }


    @Test
    public void testMapAddMapConflicts() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("{'a':'1','b':'3'} + {'b':'2','c':'3'}", Map.class);
        Assert.assertEquals(simpleMap, result);
    }


    @Test
    public void testSetAddSetNoConflicts() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("{'a','b'} + {'c'}", Set.class);
        Assert.assertEquals(simpleSet, result);
    }


    @Test
    public void testSetAddSetConflicts() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("{'a','b'} + {'b','c'}", Set.class);
        Assert.assertEquals(simpleSet, result);
    }


    @Test
    public void testSetAddListNoConflicts() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("{'a','b'} + ['c']", Set.class);
        Assert.assertEquals(simpleSet, result);
    }


    @Test
    public void testSetAddListConflicts() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("{'a','b'} + ['b','c','c']", Set.class);
        Assert.assertEquals(simpleSet, result);
    }


    @Test
    public void testListAddList() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("['a','b','c'] + ['b','c']", List.class);
        Assert.assertEquals(simpleList, result);
    }


    @Test
    public void testListAddSet() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("['a','b','c'] + {'b','c'}", List.class);
        Assert.assertEquals(simpleList, result);
    }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.el.stream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.el.ELException;
import jakarta.el.ELProcessor;

import org.junit.Assert;
import org.junit.Test;

import org.apache.el.TesterBeanA;
import org.apache.el.lang.ELSupport;

public class TestCollectionOperations {

    private static final TesterBeanA bean01 = new TesterBeanA();
    private static final TesterBeanA bean02 = new TesterBeanA();
    private static final TesterBeanA bean03 = new TesterBeanA();
    private static final List<TesterBeanA> beans;

    static {
        List<TesterBeanA> list = new ArrayList<>();

        bean01.setValLong(1);
        bean01.setName("bean01");
        list.add(bean01);

        bean02.setValLong(2);
        bean02.setName("bean02");
        list.add(bean02);

        bean03.setValLong(3);
        bean03.setName("bean03");
        list.add(bean03);

        beans = Collections.unmodifiableList(list);
    }


    @Test
    public void testToList01() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("['a','b','c'].stream().toList()",
                List.class);
        List<String> expected = new ArrayList<>(3);
        expected.add("a");
        expected.add("b");
        expected.add("c");

        Assert.assertEquals(expected, result);
    }


    @Test
    public void testToList02() {
        ELProcessor processor = new ELProcessor();
        String[] src = new String[] { "a", "b", "c" };
        processor.defineBean("src", src);
        Object result = processor.getValue("src.stream().toList()",
                List.class);
        List<String> expected = new ArrayList<>(3);
        expected.add("a");
        expected.add("b");
        expected.add("c");

        Assert.assertEquals(expected, result);
    }


    @Test
    public void testFilter01() {
        ELProcessor processor = new ELProcessor();
        processor.defineBean("beans", beans);
        Object result = processor.getValue(
                "beans.stream().filter(b->b.valLong > 2).toList()",
                List.class);
        List<TesterBeanA> expected = new ArrayList<>(1);
        expected.add(bean03);

        Assert.assertEquals(expected, result);
    }


    @Test
    public void testMap01() {
        ELProcessor processor = new ELProcessor();
        processor.defineBean("beans", beans);
        Object result = processor.getValue(
                "beans.stream().map(b->b.name).toList()",
                List.class);
        List<String> expected = new ArrayList<>(3);
        expected.add("bean01");
        expected.add("bean02");
        expected.add("bean03");

        Assert.assertEquals(expected, result);
    }


    @Test
    public void testMap02() {
        ELProcessor processor = new ELProcessor();
        processor.defineBean("beans", beans);
        Object result = processor.getValue(
                "beans.stream().filter(b->b.valLong > 1).map(b->[b.name, b.valLong]).toList()",
                List.class);

        @SuppressWarnings("unchecked")
        List<List<Object>> list = (List<List<Object>>) result;

        Assert.assertEquals(2, list.size());
        Assert.assertEquals("bean02", list.get(0).get(0));
        Assert.assertEquals(Long.valueOf(2), list.get(0).get(1));
        Assert.assertEquals("bean03", list.get(1).get(0));
        Assert.assertEquals(Long.valueOf(3), list.get(1).get(1));
    }


    @Test
    public void testFlatMap01() {
        ELProcessor processor = new ELProcessor();
        processor.defineBean("beans", beans);
        Object result = processor.getValue(
                "beans.stream().flatMap(b->b.name.toCharArray().stream()).toList()",
                List.class);

        List<Character> expected = new ArrayList<>(18);
        expected.add(Character.valueOf('b'));
        expected.add(Character.valueOf('e'));
        expected.add(Character.valueOf('a'));
        expected.add(Character.valueOf('n'));
        expected.add(Character.valueOf('0'));
        expected.add(Character.valueOf('1'));
        expected.add(Character.valueOf('b'));
        expected.add(Character.valueOf('e'));
        expected.add(Character.valueOf('a'));
        expected.add(Character.valueOf('n'));
        expected.add(Character.valueOf('0'));
        expected.add(Character.valueOf('2'));
        expected.add(Character.valueOf('b'));
        expected.add(Character.valueOf('e'));
        expected.add(Character.valueOf('a'));
        expected.add(Character.valueOf('n'));
        expected.add(Character.valueOf('0'));
        expected.add(Character.valueOf('3'));

        Assert.assertEquals(expected, result);
    }


    @Test
    public void testDistinct01() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue(
                "['a', 'b', 'b', 'c'].stream().distinct().toList()",
                List.class);
        List<String> expected = new ArrayList<>(3);
        expected.add("a");
        expected.add("b");
        expected.add("c");

        Assert.assertEquals(expected, result);
    }


    @Test
    public void testSorted01() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue(
                "['c', 'd', 'b', 'a'].stream().sorted().toList()",
                List.class);
        List<String> expected = new ArrayList<>(4);
        expected.add("a");
        expected.add("b");
        expected.add("c");
        expected.add("d");

        Assert.assertEquals(expected, result);
    }


    @Test
    public void testSortedLambdaExpression01() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue(
                "['c', 'd', 'b', 'a'].stream().sorted((x,y)->x.compareTo(y)*-1).toList()",
                List.class);
        List<String> expected = new ArrayList<>(4);
        expected.add("d");
        expected.add("c");
        expected.add("b");
        expected.add("a");

        Assert.assertEquals(expected, result);
    }


    @Test
    public void testForEach01() {
        ELProcessor processor = new ELProcessor();
        processor.defineBean("beans", beans);
        processor.getValue(
                "beans.stream().forEach(b->b.setValLong(b.valLong + 1))",
                Object.class);

        Assert.assertEquals(2, bean01.getValLong());
        Assert.assertEquals(3, bean02.getValLong());
        Assert.assertEquals(4, bean03.getValLong());

        // Restore the beans to their default state
        processor.getValue(
                "beans.stream().forEach(b->b.setValLong(b.valLong - 1))",
                Object.class);

        Assert.assertEquals(1, bean01.getValLong());
        Assert.assertEquals(2, bean02.getValLong());
        Assert.assertEquals(3, bean03.getValLong());
    }


    @Test
    public void testPeek01() {
        ELProcessor processor = new ELProcessor();
        List<TesterBeanA> debug = new ArrayList<>();
        processor.defineBean("beans", beans);
        processor.defineBean("debug", debug);

        Object result = processor.getValue(
                "beans.stream().peek(b->debug.add(b)).toList()",
                Object.class);

        List<TesterBeanA> expected = new ArrayList<>(3);
        expected.add(bean01);
        expected.add(bean02);
        expected.add(bean03);

        Assert.assertEquals(expected, result);
        Assert.assertEquals(expected, debug);
    }


    @Test
    public void testLimit01() {
        ELProcessor processor = new ELProcessor();
        processor.defineBean("beans", beans);

        Object result = processor.getValue(
                "beans.stream().limit(2).toList()",
                Object.class);

        List<TesterBeanA> expected = new ArrayList<>(2);
        expected.add(bean01);
        expected.add(bean02);

        Assert.assertEquals(expected, result);
    }


    @Test
    public void testSubstreamStart01() {
        ELProcessor processor = new ELProcessor();
        processor.defineBean("beans", beans);

        Object result = processor.getValue(
                "beans.stream().substream(1).toList()",
                Object.class);

        List<TesterBeanA> expected = new ArrayList<>(2);
        expected.add(bean02);
        expected.add(bean03);

        Assert.assertEquals(expected, result);
    }


    @Test
    public void testSubstreamStartEnd01() {
        ELProcessor processor = new ELProcessor();
        processor.defineBean("beans", beans);

        Object result = processor.getValue(
                "beans.stream().substream(1,2).toList()",
                Object.class);

        List<TesterBeanA> expected = new ArrayList<>(2);
        expected.add(bean02);

        Assert.assertEquals(expected, result);
    }


    @Test
    public void testToArray01() {
        ELProcessor processor = new ELProcessor();
        processor.defineBean("beans", beans);

        Object result = processor.getValue(
                "beans.stream().toArray()",
                Object.class);

        Object[] expected = new Object[3];
        expected[0] = bean01;
        expected[1] = bean02;
        expected[2] = bean03;

        Assert.assertArrayEquals(expected, (Object[]) result);
    }


    @Test
    public void testReduceLambda01() {
        ELProcessor processor = new ELProcessor();

        Object result = processor.getValue(
                "[1,2,3,4,5].stream().reduce((x,y)->x+y)",
                Object.class);

        Assert.assertEquals(Long.valueOf(15), ((Optional) result).get());
    }


    @Test(expected=ELException.class)
    public void testReduceLambda02() {
        ELProcessor processor = new ELProcessor();

        Object result = processor.getValue(
                "[].stream().reduce((x,y)->x+y)",
                Object.class);

        ((Optional) result).get();
    }


    @Test
    public void testReduceLambdaSeed01() {
        ELProcessor processor = new ELProcessor();

        Object result = processor.getValue(
                "[1,2,3,4,5].stream().reduce(10, (x,y)->x+y)",
                Object.class);

        Assert.assertEquals(Long.valueOf(25), result);
    }


    @Test
    public void testMax01() {
        ELProcessor processor = new ELProcessor();

        Object result = processor.getValue(
                "[1,2,3,4,5].stream().max()",
                Object.class);

        Assert.assertEquals(Long.valueOf(5), ((Optional) result).get());
    }


    @Test
    public void testMax02() {
        ELProcessor processor = new ELProcessor();

        Object result = processor.getValue(
                "[5,4,3,2,1].stream().max()",
                Object.class);

        Assert.assertEquals(Long.valueOf(5), ((Optional) result).get());
    }


    @Test(expected=ELException.class)
    public void testMax03() {
        ELProcessor processor = new ELProcessor();

        Object result = processor.getValue(
                "[].stream().max()",
                Object.class);

        ((Optional) result).get();
    }


    @Test(expected=ELException.class)
    public void testMax04() {
        ELProcessor processor = new ELProcessor();
        processor.defineBean("beans", beans);

        processor.getValue(
                "beans.stream().max()",
                Object.class);
    }


    @Test
    public void testMaxLambda01() {
        ELProcessor processor = new ELProcessor();
        processor.defineBean("beans", beans);

        Object result = processor.getValue(
                "beans.stream().max((x,y)->x.name.compareTo(y.name))",
                Object.class);

        Assert.assertEquals(bean03, ((Optional) result).get());
    }


    @Test
    public void testMaxLambda02() {
        ELProcessor processor = new ELProcessor();
        processor.defineBean("beans", beans);
        processor.setVariable("comparison", "v->(x,y)->v(x).compareTo(v(y))");

        Object result = processor.getValue(
                "beans.stream().max(comparison(x->x.name))",
                Object.class);

        Assert.assertEquals(bean03, ((Optional) result).get());
    }
    @Test
    public void testMin01() {
        ELProcessor processor = new ELProcessor();

        Object result = processor.getValue(
                "[1,2,3,4,5].stream().min()",
                Object.class);

        Assert.assertEquals(Long.valueOf(1), ((Optional) result).get());
    }


    @Test
    public void testMin02() {
        ELProcessor processor = new ELProcessor();

        Object result = processor.getValue(
                "[5,4,3,2,1].stream().min()",
                Object.class);

        Assert.assertEquals(Long.valueOf(1), ((Optional) result).get());
    }


    @Test(expected=ELException.class)
    public void testMin03() {
        ELProcessor processor = new ELProcessor();

        Object result = processor.getValue(
                "[].stream().min()",
                Object.class);

        ((Optional) result).get();
    }


    @Test(expected=ELException.class)
    public void testMin04() {
        ELProcessor processor = new ELProcessor();
        processor.defineBean("beans", beans);

        processor.getValue(
                "beans.stream().min()",
                Object.class);
    }


    @Test
    public void testMinLambda01() {
        ELProcessor processor = new ELProcessor();
        processor.defineBean("beans", beans);

        Object result = processor.getValue(
                "beans.stream().min((x,y)->x.name.compareTo(y.name))",
                Object.class);

        Assert.assertEquals(bean01, ((Optional) result).get());
    }


    @Test
    public void testAverage01() {
        ELProcessor processor = new ELProcessor();

        Object result = processor.getValue(
                "[1,2,3,4,5].stream().average()",
                Object.class);

        Number average = (Number) ((Optional) result).get();
        Assert.assertTrue("Result: " + average.toString(),
                ELSupport.equals(null, Long.valueOf(3), average));
    }


    @Test
    public void testAverage02() {
        ELProcessor processor = new ELProcessor();

        Object result = processor.getValue(
                "[1,2,3,4,5,6].stream().average()",
                Object.class);

        Number average = (Number) ((Optional) result).get();
        Assert.assertTrue("Result: " + average.toString(),
                ELSupport.equals(null, Double.valueOf(3.5), average));
    }


    @Test(expected=ELException.class)
    public void testAverage03() {
        ELProcessor processor = new ELProcessor();

        Object result = processor.getValue(
                "[].stream().average()",
                Object.class);

        ((Optional) result).get();
    }


    @Test
    public void testAverage04() {
        ELProcessor processor = new ELProcessor();

        Object result = processor.getValue(
                "[].stream().average().orElseGet(()->10)",
                Object.class);

        Assert.assertEquals(Long.valueOf(10), result);
    }


    @Test
    public void testAverage05() {
        ELProcessor processor = new ELProcessor();

        Object result = processor.getValue(
                "[].stream().average().orElseGet(()->()->10)",
                Object.class);

        Assert.assertEquals(Long.valueOf(10), result);
    }


    @Test(expected=ELException.class)
    public void testAverage06() {
        ELProcessor processor = new ELProcessor();

        processor.getValue(
                "[].stream().average().orElseGet(10)",
                Object.class);
    }


    @Test
    public void testSum01() {
        ELProcessor processor = new ELProcessor();

        Object result = processor.getValue(
                "[1,2,3,4,5].stream().sum()",
                Object.class);

        Assert.assertTrue("Result: " + result.toString(),
                ELSupport.equals(null, Long.valueOf(15), result));
    }


    @Test
    public void testSum02() {
        ELProcessor processor = new ELProcessor();

        Object result = processor.getValue(
                "[].stream().sum()",
                Object.class);

        Assert.assertTrue("Result: " + result.toString(),
                ELSupport.equals(null, Long.valueOf(0), result));
    }


    @Test
    public void testCount01() {
        ELProcessor processor = new ELProcessor();

        Object result = processor.getValue(
                "[1,2,3,4,5].stream().count()",
                Object.class);

        Assert.assertTrue("Result: " + result.toString(),
                ELSupport.equals(null, Long.valueOf(5), result));
    }


    @Test
    public void testCount02() {
        ELProcessor processor = new ELProcessor();

        Object result = processor.getValue(
                "[].stream().count()",
                Object.class);

        Assert.assertTrue("Result: " + result.toString(),
                ELSupport.equals(null, Long.valueOf(0), result));
    }


    @Test
    public void testAnyMatch01() {
        ELProcessor processor = new ELProcessor();

        Optional result = (Optional) processor.getValue(
                "[1,2,3,4,5].stream().anyMatch(x->x==7)",
                Object.class);

        Assert.assertEquals(Boolean.FALSE, result.get());
    }


    @Test
    public void testAnyMatch02() {
        ELProcessor processor = new ELProcessor();

        Optional result = (Optional) processor.getValue(
                "[1,2,3,4,5].stream().anyMatch(x->x==3)",
                Object.class);

        Assert.assertEquals(Boolean.TRUE, result.get());
    }


    @Test(expected=ELException.class)
    public void testAnyMatch03() {
        ELProcessor processor = new ELProcessor();

        Optional result = (Optional) processor.getValue(
                "[].stream().anyMatch(x->x==7)",
                Object.class);

        result.get();
    }


    @Test
    public void testAllMatch01() {
        ELProcessor processor = new ELProcessor();

        Optional result = (Optional) processor.getValue(
                "[1,2,3,4,5].stream().allMatch(x->x>3)",
                Object.class);

        Assert.assertEquals(Boolean.FALSE, result.get());
    }


    @Test
    public void testAllMatch02() {
        ELProcessor processor = new ELProcessor();

        Optional result = (Optional) processor.getValue(
                "[1,2,3,4,5].stream().allMatch(x->x>0)",
                Object.class);

        Assert.assertEquals(Boolean.TRUE, result.get());
    }


    @Test
    public void testAllMatch03() {
        ELProcessor processor = new ELProcessor();

        Optional result = (Optional) processor.getValue(
                "[1,2,3,4,5].stream().allMatch(x->x>10)",
                Object.class);

        Assert.assertEquals(Boolean.FALSE, result.get());
    }


    @Test(expected=ELException.class)
    public void testAllMatch04() {
        ELProcessor processor = new ELProcessor();

        Optional result = (Optional) processor.getValue(
                "[].stream().allMatch(x->x==7)",
                Object.class);

        result.get();
    }


    @Test
    public void testNoneMatch01() {
        ELProcessor processor = new ELProcessor();

        Optional result = (Optional) processor.getValue(
                "[1,2,3,4,5].stream().allMatch(x->x>3)",
                Object.class);

        Assert.assertEquals(Boolean.FALSE, result.get());
    }


    @Test
    public void testNoneMatch02() {
        ELProcessor processor = new ELProcessor();

        Optional result = (Optional) processor.getValue(
                "[1,2,3,4,5].stream().noneMatch(x->x>0)",
                Object.class);

        Assert.assertEquals(Boolean.FALSE, result.get());
    }


    @Test
    public void testNoneMatch03() {
        ELProcessor processor = new ELProcessor();

        Optional result = (Optional) processor.getValue(
                "[1,2,3,4,5].stream().noneMatch(x->x>10)",
                Object.class);

        Assert.assertEquals(Boolean.TRUE, result.get());
    }


    @Test(expected=ELException.class)
    public void testNoneMatch04() {
        ELProcessor processor = new ELProcessor();

        Optional result = (Optional) processor.getValue(
                "[].stream().noneMatch(x->x==7)",
                Object.class);

        result.get();
    }


    @Test
    public void testFindFirst01() {
        ELProcessor processor = new ELProcessor();
        processor.defineBean("beans", beans);

        Optional result = (Optional) processor.getValue(
                "beans.stream().findFirst()",
                Object.class);

        Assert.assertEquals(bean01, result.get());
    }


    @Test(expected=ELException.class)
    public void testFindFirst02() {
        ELProcessor processor = new ELProcessor();

        Optional result = (Optional) processor.getValue(
                "[].stream().findFirst()",
                Object.class);

        result.get();
    }
}

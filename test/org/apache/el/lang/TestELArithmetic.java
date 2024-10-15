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
package org.apache.el.lang;

import java.math.BigDecimal;
import java.math.BigInteger;

import jakarta.el.ELProcessor;

import org.junit.Assert;
import org.junit.Test;

public class TestELArithmetic {
    private static final String a = "1.1";
    private static final BigInteger b =
        new BigInteger("1000000000000000000000");

    @Test
    public void testAdd01() throws Exception {
        Assert.assertEquals("1000000000000000000001.1",
                String.valueOf(ELArithmetic.add(a, b)));
    }

    @Test
    public void testAdd02() {
        ELProcessor processor = new ELProcessor();
        Long result = processor.eval("null + null");
        Assert.assertEquals(Long.valueOf(0), result);
    }

    @Test
    public void testSubtract01() throws Exception {
        Assert.assertEquals("-999999999999999999998.9",
                String.valueOf(ELArithmetic.subtract(a, b)));
    }

    @Test
    public void testSubtract02() {
        ELProcessor processor = new ELProcessor();
        Long result = processor.eval("null - null");
        Assert.assertEquals(Long.valueOf(0), result);
    }

    @Test
    public void testMultiply01() throws Exception {
        Assert.assertEquals("1100000000000000000000.0",
                String.valueOf(ELArithmetic.multiply(a, b)));
    }

    @Test
    public void testMultiply02() {
        ELProcessor processor = new ELProcessor();
        Long result = processor.eval("null * null");
        Assert.assertEquals(Long.valueOf(0), result);
    }

    @Test
    public void testDivide01() throws Exception {
        Assert.assertEquals("0.0",
                String.valueOf(ELArithmetic.divide(a, b)));
    }

    @Test
    public void testDivide02() {
        ELProcessor processor = new ELProcessor();
        Long result = processor.eval("null / null");
        Assert.assertEquals(Long.valueOf(0), result);
    }

    @Test
    public void testMod01() throws Exception {
        Assert.assertEquals("1.1",
                String.valueOf(ELArithmetic.mod(a, b)));
    }

    @Test
    public void testMod02() {
        ELProcessor processor = new ELProcessor();
        Long result = processor.eval("null % null");
        Assert.assertEquals(Long.valueOf(0), result);
    }

    @Test
    public void testMod03() {
        ELProcessor processor = new ELProcessor();
        // Large so BigInteger rather than Long is used internally
        BigInteger result = processor.eval("1 % -9999999999999999999");
        Assert.assertEquals(BigInteger.valueOf(1), result);
    }

    @Test
    public void testUnaryMinus01() {
        ELProcessor processor = new ELProcessor();
        Long result = processor.eval("-null");
        Assert.assertEquals(Long.valueOf(0), result);
    }

    @Test
    public void testBug47371bigDecimal() throws Exception {
        Assert.assertEquals(BigDecimal.valueOf(1),
                ELArithmetic.add("", BigDecimal.valueOf(1)));
    }

    @Test
    public void testBug47371double() throws Exception {
        Assert.assertEquals(Double.valueOf(7), ELArithmetic.add("", Double.valueOf(7)));
    }

    @Test
    public void testBug47371doubleString() throws Exception {
        Assert.assertEquals(Double.valueOf(2), ELArithmetic.add("", "2."));
    }

    @Test
    public void testBug47371bigInteger() throws Exception {
        Assert.assertEquals(BigInteger.valueOf(0),
                ELArithmetic.multiply("", BigInteger.valueOf(1)));
    }

    @Test
    public void testBug47371long() throws Exception {
        Assert.assertEquals(Long.valueOf(1), ELArithmetic.add("", Integer.valueOf(1)));
    }

    @Test
    public void testBug47371long2() throws Exception {
        Assert.assertEquals(Long.valueOf(-3), ELArithmetic.subtract("1", "4"));
    }

    @Test
    public void testBug47371doubleString2() throws Exception {
        Assert.assertEquals(Double.valueOf(2), ELArithmetic.add("1.", "1"));
    }
}

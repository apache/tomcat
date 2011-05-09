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

import java.math.BigInteger;

import junit.framework.TestCase;

public class TestELArithmetic extends TestCase {
    private static final String a = "1.1";
    private static final BigInteger b =
        new BigInteger("1000000000000000000000");

    public void testAdd() throws Exception {
        assertEquals("1000000000000000000001.1",
                String.valueOf(ELArithmetic.add(a, b)));
    }

    public void testSubtract() throws Exception {
        assertEquals("-999999999999999999998.9",
                String.valueOf(ELArithmetic.subtract(a, b)));
    }

    public void testMultiply() throws Exception {
        assertEquals("1100000000000000000000.0",
                String.valueOf(ELArithmetic.multiply(a, b)));
    }

    public void testDivide() throws Exception {
        assertEquals("0.0",
                String.valueOf(ELArithmetic.divide(a, b)));
    }

    public void testMod() throws Exception {
        assertEquals("1.1",
                String.valueOf(ELArithmetic.mod(a, b)));
    }

    public void testBug47371() throws Exception {
        assertEquals("1",
                String.valueOf(ELArithmetic.add("", Integer.valueOf(1))));
    }
}

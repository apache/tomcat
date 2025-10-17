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

import java.math.BigInteger;

import jakarta.el.ELProcessor;

import org.junit.Assert;
import org.junit.Test;

import org.apache.el.lang.ELSupport;

public class TestAstInteger {

    @Test
    public void testValidLong() {
        doTestValid("1234");
    }


    @Test
    public void testValidBigInteger() {
        doTestValid("12345678901234567890");
    }


    private void doTestValid(String value) {
        ELProcessor processor = new ELProcessor();
        Number result = processor.eval(value);
        Assert.assertTrue(ELSupport.equals(null, new BigInteger(value), result));
    }


    // Note: Testing an invalid integer would require a number larger than
    //       2^Integer.MAX_VALUE. It is not practical to test with a String of
    //       digits representing a number that large.
}

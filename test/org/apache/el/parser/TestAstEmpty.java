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

import jakarta.el.ELProcessor;

import org.junit.Assert;
import org.junit.Test;

public class TestAstEmpty {

    @Test
    public void test01() {
        ELProcessor processor = new ELProcessor();
        Boolean result = processor.eval("empty 'abc'");
        Assert.assertEquals(Boolean.FALSE, result);
    }


    @Test
    public void test02() {
        ELProcessor processor = new ELProcessor();
        Boolean result = processor.eval("empty ''");
        Assert.assertEquals(Boolean.TRUE, result);
    }

    @Test
    public void test03() {
        // An array with no elements is empty. This includes primitive arrays.
        ELProcessor processor = new ELProcessor();
        processor.defineBean("arr", new int[0]);
        Boolean result = processor.eval("empty arr");
        Assert.assertEquals(Boolean.TRUE, result);
    }



    @Test
    public void test04() {
        ELProcessor processor = new ELProcessor();
        processor.defineBean("arr", new int[] { 1, 2 });
        Boolean result = processor.eval("empty arr");
        Assert.assertEquals(Boolean.FALSE, result);
    }



    @Test
    public void test05() {
        ELProcessor processor = new ELProcessor();
        processor.defineBean("arr", new Object[0]);
        Boolean result = processor.eval("empty arr");
        Assert.assertEquals(Boolean.TRUE, result);
    }
}

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

import javax.el.ELProcessor;

import org.junit.Assert;
import org.junit.Test;

public class TestAstLambdaExpression {

    @Test
    public void testSpec01() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("(x->x+1)(1)", Integer.class);
        Assert.assertEquals(Integer.valueOf(2), result);
    }


    @Test
    public void testSpec02() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("((x,y)->x+y)(1,2)", Integer.class);
        Assert.assertEquals(Integer.valueOf(3), result);
    }


    @Test
    public void testSpec03() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.getValue("(()->64)", Integer.class);
        Assert.assertEquals(Integer.valueOf(64), result);
    }
}

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
import java.util.List;

import javax.el.ELProcessor;

import org.junit.Assert;
import org.junit.Test;

public class TestCollectionOperations {

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

}

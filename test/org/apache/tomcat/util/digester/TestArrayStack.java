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
package org.apache.tomcat.util.digester;

import java.util.EmptyStackException;

import org.junit.Assert;
import org.junit.Test;

public class TestArrayStack {

    @Test
    public void testPeekTopAndBottom() {
        ArrayStack<String> stack = new ArrayStack<>();
        stack.push("bottom");
        stack.push("top");

        Assert.assertEquals("top", stack.peek(0));
        Assert.assertEquals("bottom", stack.peek(1));
    }


    @Test(expected = EmptyStackException.class)
    public void testPeekEmptyStack() {
        new ArrayStack<String>().peek(0);
    }


    @Test(expected = EmptyStackException.class)
    public void testPeekBeyondBottom() {
        ArrayStack<String> stack = new ArrayStack<>();
        stack.push("only");

        stack.peek(1);
    }


    /*
     * A negative index is out of range rather than beyond the end of the
     * backing list, so it must signal EmptyStackException like the other
     * out of range indexes.
     */
    @Test(expected = EmptyStackException.class)
    public void testPeekNegativeIndex() {
        ArrayStack<String> stack = new ArrayStack<>();
        stack.push("only");

        stack.peek(-1);
    }


    @Test(expected = EmptyStackException.class)
    public void testPeekNegativeIndexEmptyStack() {
        new ArrayStack<String>().peek(-1);
    }


    @Test(expected = EmptyStackException.class)
    public void testPeekIntegerMinValue() {
        ArrayStack<String> stack = new ArrayStack<>();
        stack.push("only");

        stack.peek(Integer.MIN_VALUE);
    }


    @Test(expected = EmptyStackException.class)
    public void testPeekIntegerMaxValue() {
        ArrayStack<String> stack = new ArrayStack<>();
        stack.push("only");

        stack.peek(Integer.MAX_VALUE);
    }
}

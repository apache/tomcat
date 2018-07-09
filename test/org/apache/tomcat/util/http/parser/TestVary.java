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
package org.apache.tomcat.util.http.parser;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

public class TestVary {

    @Test
    public void testAll() throws IOException {
        Set<String> expected = new HashSet<>();
        expected.add("*");
        doTestVary("*", expected);
    }


    @Test
    public void testSingle() throws IOException {
        Set<String> expected = new HashSet<>();
        expected.add("host");
        doTestVary("Host", expected);
    }


    @Test
    public void testMultiple() throws IOException {
        Set<String> expected = new HashSet<>();
        expected.add("bar");
        expected.add("foo");
        expected.add("host");
        doTestVary("Host, Foo, Bar", expected);
    }


    @Test
    public void testEmptyString() throws IOException {
        doTestVary("", Collections.emptySet());
    }


    @Test
    public void testSingleInvalid() throws IOException {
        doTestVary("{{{", Collections.emptySet());
    }


    @Test
    public void testMultipleWithInvalidStart() throws IOException {
        Set<String> expected = new HashSet<>();
        expected.add("bar");
        expected.add("foo");
        expected.add("host");
        doTestVary("{{{, Host, Foo, Bar", expected);
    }


    @Test
    public void testMultipleWithInvalidMiddle() throws IOException {
        Set<String> expected = new HashSet<>();
        expected.add("bar");
        expected.add("foo");
        expected.add("host");
        doTestVary("Host, {{{, Foo, Bar", expected);
    }


    @Test
    public void testMultipleWithInvalidEnd() throws IOException {
        Set<String> expected = new HashSet<>();
        expected.add("bar");
        expected.add("foo");
        expected.add("host");
        doTestVary("Host, Foo, Bar, {{{", expected);
    }


    @Test
    public void testMultipleWithInvalidStart2() throws IOException {
        Set<String> expected = new HashSet<>();
        expected.add("bar");
        expected.add("foo");
        expected.add("host");
        doTestVary("OK {{{, Host, Foo, Bar", expected);
    }


    @Test
    public void testMultipleWithInvalidMiddle2() throws IOException {
        Set<String> expected = new HashSet<>();
        expected.add("bar");
        expected.add("foo");
        expected.add("host");
        doTestVary("Host, OK {{{, Foo, Bar", expected);
    }


    @Test
    public void testMultipleWithInvalidEnd2() throws IOException {
        Set<String> expected = new HashSet<>();
        expected.add("bar");
        expected.add("foo");
        expected.add("host");
        doTestVary("Host, Foo, Bar, OK {{{", expected);
    }


    private void doTestVary(String input, Set<String> expected) throws IOException {
        StringReader reader = new StringReader(input);
        Set<String> result = new HashSet<>();
        Vary.parseVary(reader, result);
        Assert.assertEquals(expected, result);
    }
}

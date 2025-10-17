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

public class TestTokenList {

    @Test
    public void testAll() throws IOException {
        Set<String> expected = new HashSet<>();
        expected.add("*");
        doTestVary("*", expected, true);
    }


    @Test
    public void testSingle() throws IOException {
        Set<String> expected = new HashSet<>();
        expected.add("host");
        doTestVary("Host", expected, true);
    }


    @Test
    public void testMultiple() throws IOException {
        Set<String> expected = new HashSet<>();
        expected.add("bar");
        expected.add("foo");
        expected.add("host");
        doTestVary("Host, Foo, Bar", expected, true);
    }


    @Test
    public void testEmptyString() throws IOException {
        doTestVary("", Collections.emptySet(), false);
    }


    @Test
    public void testSingleInvalid() throws IOException {
        doTestVary("{{{", Collections.emptySet(), false);
    }


    @Test
    public void testMultipleWithInvalidStart() throws IOException {
        Set<String> expected = new HashSet<>();
        expected.add("bar");
        expected.add("foo");
        expected.add("host");
        doTestVary("{{{, Host, Foo, Bar", expected, false);
    }


    @Test
    public void testMultipleWithInvalidMiddle() throws IOException {
        Set<String> expected = new HashSet<>();
        expected.add("bar");
        expected.add("foo");
        expected.add("host");
        doTestVary("Host, {{{, Foo, Bar", expected, false);
    }


    @Test
    public void testMultipleWithInvalidEnd() throws IOException {
        Set<String> expected = new HashSet<>();
        expected.add("bar");
        expected.add("foo");
        expected.add("host");
        doTestVary("Host, Foo, Bar, {{{", expected, false);
    }


    @Test
    public void testMultipleWithInvalidStart2() throws IOException {
        Set<String> expected = new HashSet<>();
        expected.add("bar");
        expected.add("foo");
        expected.add("host");
        doTestVary("OK {{{, Host, Foo, Bar", expected, false);
    }


    @Test
    public void testMultipleWithInvalidMiddle2() throws IOException {
        Set<String> expected = new HashSet<>();
        expected.add("bar");
        expected.add("foo");
        expected.add("host");
        doTestVary("Host, OK {{{, Foo, Bar", expected, false);
    }


    @Test
    public void testMultipleWithInvalidEnd2() throws IOException {
        Set<String> expected = new HashSet<>();
        expected.add("bar");
        expected.add("foo");
        expected.add("host");
        doTestVary("Host, Foo, Bar, OK {{{", expected, false);
    }


    private void doTestVary(String input, Set<String> expectedTokens, boolean expectedResult) throws IOException {
        StringReader reader = new StringReader(input);
        Set<String> tokens = new HashSet<>();
        boolean result = TokenList.parseTokenList(reader, tokens);
        Assert.assertEquals(expectedTokens, tokens);
        Assert.assertEquals(Boolean.valueOf(expectedResult), Boolean.valueOf(result));
    }


    @Test
    public void testMultipleHeadersValidWithoutNull() throws IOException {
        doTestMultipleHeadersValid(false);
    }


    @Test
    public void testMultipleHeadersValidWithNull() throws IOException {
        doTestMultipleHeadersValid(true);
    }


    private void doTestMultipleHeadersValid(boolean withNull) throws IOException {
        Set<String> expectedTokens = new HashSet<>();
        expectedTokens.add("bar");
        expectedTokens.add("foo");
        expectedTokens.add("foo2");

        Set<String> inputs = new HashSet<>();
        inputs.add("foo");
        if (withNull) {
            inputs.add(null);
        }
        inputs.add("bar, foo2");

        Set<String> tokens = new HashSet<>();


        boolean result = TokenList.parseTokenList(Collections.enumeration(inputs), tokens);
        Assert.assertEquals(expectedTokens, tokens);
        Assert.assertTrue(result);
    }


    @Test
    public void doTestMultipleHeadersInvalid() throws IOException {
        Set<String> expectedTokens = new HashSet<>();
        expectedTokens.add("bar");
        expectedTokens.add("bar2");
        expectedTokens.add("foo");
        expectedTokens.add("foo2");
        expectedTokens.add("foo3");

        Set<String> inputs = new HashSet<>();
        inputs.add("foo");
        inputs.add("bar2, }}}, foo3");
        inputs.add("bar, foo2");

        Set<String> tokens = new HashSet<>();


        boolean result = TokenList.parseTokenList(Collections.enumeration(inputs), tokens);
        Assert.assertEquals(expectedTokens, tokens);
        Assert.assertFalse(result);
    }


    @Test
    public void testMultipleWithEmptyStart() throws IOException {
        Set<String> expected = new HashSet<>();
        expected.add("bar");
        expected.add("foo");
        expected.add("host");
        doTestVary(",Host, Foo, Bar", expected, true);
    }


    @Test
    public void testMultipleWithEmptyMiddle() throws IOException {
        Set<String> expected = new HashSet<>();
        expected.add("bar");
        expected.add("foo");
        expected.add("host");
        doTestVary("Host, Foo,,Bar", expected, true);
    }


    @Test
    public void testMultipleWithEmptyEnd() throws IOException {
        Set<String> expected = new HashSet<>();
        expected.add("bar");
        expected.add("foo");
        expected.add("host");
        doTestVary("Host, Foo, Bar,", expected, true);
    }
}

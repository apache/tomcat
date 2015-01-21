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
package org.apache.catalina.startup;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;


public class TestBootstrap {

    @Test
    public void testEmptyNonQuoted() {
        doTest("");
    }

    @Test
    public void testOneNonQuoted() {
        doTest("a", "a");
    }

    @Test
    public void testTwoNonQuoted01() {
        doTest("a,b", "a", "b");
    }

    @Test
    public void testTwoNonQuoted02() {
        doTest("a,,b", "a", "b");
    }

    @Test
    public void testTwoNonQuoted03() {
        doTest(",a,b", "a", "b");
    }

    @Test
    public void testTwoNonQuoted04() {
        doTest("a,b,", "a", "b");
    }

    @Test
    public void testThreeNonQuoted() {
        doTest("a,b,c", "a", "b", "c");
    }

    @Test
    public void testEmptyQuoted() {
        doTest("\"\"");
    }

    @Test
    public void testOneQuoted01() {
        doTest("\"a\"", "a");
    }

    @Test
    public void testOneQuoted02() {
        doTest("\"aaa\"", "aaa");
    }

    @Test
    public void testOneQuoted03() {
        doTest("\"a,aa\"", "a,aa");
    }

    @Test
    public void testOneQuoted04() {
        doTest("\",aaa\"", ",aaa");
    }

    @Test
    public void testOneQuoted05() {
        doTest("\"aaa,\"", "aaa,");
    }

    @Test
    public void testTwoQuoted01() {
        doTest("\"aaa\",bbb", "aaa", "bbb");
    }

    @Test
    public void testTwoQuoted02() {
        doTest("\"a,aa\",bbb", "a,aa", "bbb");
    }

    @Test
    public void testTwoQuoted03() {
        doTest("\"aaa\",\"bbb\"", "aaa", "bbb");
    }

    @Test
    public void testTwoQuoted04() {
        doTest("\"aaa\",\",bbb\"", "aaa", ",bbb");
    }

    @Test
    public void testTwoQuoted05() {
        doTest("aaa,\"bbb,\"", "aaa", "bbb,");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testUnbalancedQuotes01() {
        doTest("\"", "ignored");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testUnbalancedQuotes02() {
        doTest("\"aaa", "ignored");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testUnbalancedQuotes03() {
        doTest("aaa\"", "ignored");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testUnbalancedQuotes04() {
        doTest("a\"a", "ignored");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testUnbalancedQuotes05() {
        doTest("b,\"", "ignored");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testUnbalancedQuotes06() {
        doTest("b,\"aaa", "ignored");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testUnbalancedQuotes07() {
        doTest("b,aaa\"", "ignored");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testUnbalancedQuotes08() {
        doTest("b,a\"a", "ignored");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testUnbalancedQuotes09() {
        doTest("\",b", "ignored");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testUnbalancedQuotes10() {
        doTest("\"aaa,b", "ignored");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testUnbalancedQuotes11() {
        doTest("aaa\",b", "ignored");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testUnbalancedQuotes12() {
        doTest("a\"a,b", "ignored");
    }

    private void doTest(String input, String... expected) {
        String[] result = Bootstrap.getPaths(input);

        assertArrayEquals(expected, result);
    }
}

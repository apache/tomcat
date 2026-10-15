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
package org.apache.tomcat.util.json;

import java.io.StringReader;
import java.math.BigInteger;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class TestJSONParser {

    private static final int MAX_TEST_DEPTH = 1000;

    @Test
    public void testUnicodeEscapeIsNotUnescapedTwice() throws ParseException {
        String json = "\"\\u005cn\"";

        Assert.assertEquals("\\n", new JSONParser(json).parse());
    }

    @Test
    public void testNestingAtMaximumDepthAccepted() throws ParseException {
        Assert.assertNotNull(new JSONParser(nest(MAX_TEST_DEPTH)).parse());
    }

    @Test(expected = ParseException.class)
    public void testNestingAboveMaximumDepthIsParseException() throws ParseException {
        new JSONParser(nest(MAX_TEST_DEPTH + 1)).parse();
    }

    @Test
    public void testDepthResetAfterError() throws ParseException {
        JSONParser parser = new JSONParser(nest(MAX_TEST_DEPTH + 1));
        try {
            parser.parse();
            Assert.fail("A ParseException was expected");
        } catch (ParseException e) {
            // Expected
        }
        parser.ReInit(new StringReader("[1]"));
        Assert.assertNotNull(parser.parse());
    }

    @Test
    public void testNativeNumbersBeyondLongRange() throws ParseException {
        JSONParser parser = new JSONParser("[9223372036854775808, -9223372036854775809]");
        parser.setNativeNumbers(true);
        List<Object> values = parser.parseArray();
        Assert.assertEquals(new BigInteger("9223372036854775808"), values.get(0));
        Assert.assertEquals(new BigInteger("-9223372036854775809"), values.get(1));
    }

    @Test
    public void testNativeNumbersWithinLongRange() throws ParseException {
        JSONParser parser = new JSONParser("[9223372036854775807, -9223372036854775808]");
        parser.setNativeNumbers(true);
        List<Object> values = parser.parseArray();
        Assert.assertEquals(Long.valueOf(Long.MAX_VALUE), values.get(0));
        Assert.assertEquals(Long.valueOf(Long.MIN_VALUE), values.get(1));
    }

    private static String nest(int depth) {
        StringBuilder result = new StringBuilder(depth * 2);
        for (int i = 0; i < depth; i++) {
            result.append('[');
        }
        for (int i = 0; i < depth; i++) {
            result.append(']');
        }
        return result.toString();
    }
}

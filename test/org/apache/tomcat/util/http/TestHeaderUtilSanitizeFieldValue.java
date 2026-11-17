/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.util.http;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

/**
 * Tests {@link HeaderUtil#sanitizeFieldValue(String)} for every character from 0 to 499.
 */
@RunWith(Parameterized.class)
public class TestHeaderUtilSanitizeFieldValue {

    private static final String VALID_STRING = "aaa";

    @Parameterized.Parameters(name = "{index}")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        for (int i = 0; i < 500; i++) {
            parameterSets.add(new Object[] { Character.valueOf((char) i) });
        }
        return parameterSets;
    }


    @Parameter(0)
    public Character charUnderTest;


    private boolean isCtl() {
        char c = charUnderTest.charValue();
        return c < 32 && c != 9 || c == 127;
    }


    private boolean isTooBig() {
        return charUnderTest.charValue() > 255;
    }


    private boolean isWhitespace() {
        char c = charUnderTest.charValue();
        return c == 9 || c == 32;
    }


    private void doTest(String input, String corrected, boolean edge) {
        String result = HeaderUtil.sanitizeFieldValue(input);

        if (isTooBig()) {
            Assert.assertNull(result);
        } else if (isCtl() || edge && isWhitespace()) {
            Assert.assertEquals(corrected, result);
        } else {
            // Valid character. The value must be returned as is.
            Assert.assertSame(input, result);
        }
    }


    @Test
    public void testAtStart() {
        // A CTL is replaced with a space and whitespace is trimmed
        doTest(charUnderTest + VALID_STRING, VALID_STRING, true);
    }


    @Test
    public void testInMiddle() {
        doTest(VALID_STRING + charUnderTest + VALID_STRING, VALID_STRING + ' ' + VALID_STRING, false);
    }


    @Test
    public void testAtEnd() {
        // A CTL is replaced with a space and whitespace is trimmed
        doTest(VALID_STRING + charUnderTest, VALID_STRING, true);
    }


    @Test
    public void testEmpty() {
        Assert.assertEquals("", HeaderUtil.sanitizeFieldValue(""));
    }


    @Test
    public void testWhitespaceOnly() {
        Assert.assertEquals("", HeaderUtil.sanitizeFieldValue(" \t "));
    }


    @Test
    public void testTrim() {
        Assert.assertEquals("a b", HeaderUtil.sanitizeFieldValue(" \ta b\t "));
    }
}

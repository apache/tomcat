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
package org.apache.coyote.http11.filters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class TestChunkedOutputFilter {

    private final String VALID_STRING = "aaa";

    @Parameterized.Parameters(name = "{index}")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        for (int i = 0; i < 500; i++) {
            Boolean valid;
            if (i < 32 && i != 9 || i == 127 || i > 255) {
                valid = Boolean.FALSE;
            } else {
                valid = Boolean.TRUE;
            }
            parameterSets.add(new Object[] { Character.valueOf((char) i), valid});
        }
        return parameterSets;
    }


    @Parameter(0)
    public Character charUnderTest;
    @Parameter(1)
    public boolean valid;


    @Test
    public void testAtStart() {
        StringBuilder sb = new StringBuilder(4);
        sb.append(charUnderTest);
        sb.append(VALID_STRING);

        String result = ChunkedOutputFilter.filterForHeaders(sb.toString());

        String expected;
        if (valid) {
            expected = sb.toString();
        } else {
            StringBuilder esb = new StringBuilder(4);
            esb.append(' ');
            esb.append(VALID_STRING);
            expected = esb.toString();
        }

        Assert.assertEquals(expected, result);
    }


    @Test
    public void testInMiddle() {
        StringBuilder sb = new StringBuilder(4);
        sb.append(VALID_STRING);
        sb.append(charUnderTest);
        sb.append(VALID_STRING);

        String result = ChunkedOutputFilter.filterForHeaders(sb.toString());

        String expected;
        if (valid) {
            expected = sb.toString();
        } else {
            StringBuilder esb = new StringBuilder(4);
            esb.append(VALID_STRING);
            esb.append(' ');
            esb.append(VALID_STRING);
            expected = esb.toString();
        }

        Assert.assertEquals(expected, result);
    }


    @Test
    public void testAtEnd() {
        StringBuilder sb = new StringBuilder(4);
        sb.append(VALID_STRING);
        sb.append(charUnderTest);

        String result = ChunkedOutputFilter.filterForHeaders(sb.toString());

        String expected;
        if (valid) {
            expected = sb.toString();
        } else {
            StringBuilder esb = new StringBuilder(4);
            esb.append(VALID_STRING);
            esb.append(' ');
            expected = esb.toString();
        }

        Assert.assertEquals(expected, result);
    }
}

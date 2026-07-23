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
package org.apache.catalina.authenticator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.authenticator.DigestAuthenticator.NonceInfo;

@RunWith(Parameterized.class)
public class TestDigestAuthenticatorNonceInfo {

    @Parameterized.Parameters
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();
        /*
         * Use a small window size (4) to minimise the number of operations required to test the edge cases.
         * NonceInfo.count starts at zero. The initial window is for 2, -1, 0, 1. Check that values outside this range
         * are rejected. Negative nonce values are not normally seen in real world usage but are used here to reduce
         * test verbosity.
         */
        parameterSets.add(new Object[] { Integer.valueOf(4), new long[] { -2 }, new boolean[] { false } });
        parameterSets.add(new Object[] { Integer.valueOf(4), new long[] { 3 }, new boolean[] { false } });
        /*
         * Check repeats for any of the valid initial values. Note: the first sequence is invalid because of the repeat
         * and because the repeated value is then outside of the window.
         */
        parameterSets.add(new Object[] { Integer.valueOf(4), new long[] { -1, -1 }, new boolean[] { true, false } });
        parameterSets.add(new Object[] { Integer.valueOf(4), new long[] { 0, 0 }, new boolean[] { true, false } });
        parameterSets.add(new Object[] { Integer.valueOf(4), new long[] { 1, 1 }, new boolean[] { true, false } });
        parameterSets.add(new Object[] { Integer.valueOf(4), new long[] { 2, 2 }, new boolean[] { true, false } });
        /*
         * Check repeats for any of the valid initial values. Note: the first two sequences are invalid because of the
         * repeat and because the repeated value is then outside of the window.
         */
        parameterSets.add(
                new Object[] { Integer.valueOf(4), new long[] { -1, 0, -1 }, new boolean[] { true, true, false } });
        parameterSets
                .add(new Object[] { Integer.valueOf(4), new long[] { 0, 1, 0 }, new boolean[] { true, true, false } });
        parameterSets
                .add(new Object[] { Integer.valueOf(4), new long[] { 1, 2, 1 }, new boolean[] { true, true, false } });
        parameterSets
                .add(new Object[] { Integer.valueOf(4), new long[] { 2, 3, 2 }, new boolean[] { true, true, false } });
        /*
         * Check repeats for any of the valid initial values. Note: the first three sequences are invalid because of the
         * repeat and because the repeated value is then outside of the window.
         */
        parameterSets.add(new Object[] { Integer.valueOf(4), new long[] { -1, 0, 1, -1 },
                new boolean[] { true, true, true, false } });
        parameterSets.add(new Object[] { Integer.valueOf(4), new long[] { 0, 1, 2, 0 },
                new boolean[] { true, true, true, false } });
        parameterSets.add(new Object[] { Integer.valueOf(4), new long[] { 1, 2, 3, 1 },
                new boolean[] { true, true, true, false } });
        parameterSets.add(new Object[] { Integer.valueOf(4), new long[] { 2, 3, 4, 2 },
                new boolean[] { true, true, true, false } });
        /*
         * Check repeats for any of the valid initial values. Note: all sequences are invalid because of the repeat and
         * because the repeated value is then outside of the window.
         */
        parameterSets.add(new Object[] { Integer.valueOf(4), new long[] { -1, 0, 1, 2, -1 },
                new boolean[] { true, true, true, true, false } });
        parameterSets.add(new Object[] { Integer.valueOf(4), new long[] { 0, 1, 2, 3, 0 },
                new boolean[] { true, true, true, true, false } });
        parameterSets.add(new Object[] { Integer.valueOf(4), new long[] { 1, 2, 3, 4, 1 },
                new boolean[] { true, true, true, true, false } });
        parameterSets.add(new Object[] { Integer.valueOf(4), new long[] { 2, 3, 4, 5, 2 },
                new boolean[] { true, true, true, true, false } });
        /*
         * Check sequence longer than window size that starts at the lower bound
         */
        parameterSets.add(new Object[] { Integer.valueOf(4), new long[] { -1, 0, 1, 2, 3, 4, 5 },
                new boolean[] { true, true, true, true, true, true, true } });
        /*
         * Check sequence longer than window size that starts at upper bound
         */
        parameterSets.add(new Object[] { Integer.valueOf(4), new long[] { 2, 3, 4, 5, 6, 7, 8 },
                new boolean[] { true, true, true, true, true, true, true } });
        /*
         * Check sequence longer than window size that starts at zero and then jumps to upper bound.
         */
        parameterSets.add(new Object[] { Integer.valueOf(4), new long[] { 0, 1, 2, 3, 6 },
                new boolean[] { true, true, true, true, true } });


        /*
         * Repeat tests with an odd window size (5). The initial window is for 3, -1, 0, 1, 2, with an offset of 2.
         * Check that values outside this range are rejected.
         */
        parameterSets.add(new Object[] { Integer.valueOf(5), new long[] { -2 }, new boolean[] { false } });
        parameterSets.add(new Object[] { Integer.valueOf(5), new long[] { 4 }, new boolean[] { false } });
        /*
         * Check repeats for any of the valid initial values. Note: the first sequence is invalid because of the repeat
         * and because the repeated value is then outside of the window.
         */
        parameterSets.add(new Object[] { Integer.valueOf(5), new long[] { -1, -1 }, new boolean[] { true, false } });
        parameterSets.add(new Object[] { Integer.valueOf(5), new long[] { 0, 0 }, new boolean[] { true, false } });
        parameterSets.add(new Object[] { Integer.valueOf(5), new long[] { 1, 1 }, new boolean[] { true, false } });
        parameterSets.add(new Object[] { Integer.valueOf(5), new long[] { 2, 2 }, new boolean[] { true, false } });
        parameterSets.add(new Object[] { Integer.valueOf(5), new long[] { 3, 3 }, new boolean[] { true, false } });
        /*
         * Check repeats for any of the valid initial values. Note: the first two sequences are invalid because of the
         * repeat and because the repeated value is then outside of the window.
         */
        parameterSets.add(
                new Object[] { Integer.valueOf(5), new long[] { -1, 0, -1 }, new boolean[] { true, true, false } });
        parameterSets
                .add(new Object[] { Integer.valueOf(5), new long[] { 0, 1, 0 }, new boolean[] { true, true, false } });
        parameterSets
                .add(new Object[] { Integer.valueOf(5), new long[] { 1, 2, 1 }, new boolean[] { true, true, false } });
        parameterSets
                .add(new Object[] { Integer.valueOf(5), new long[] { 2, 3, 2 }, new boolean[] { true, true, false } });
        parameterSets
                .add(new Object[] { Integer.valueOf(5), new long[] { 3, 4, 3 }, new boolean[] { true, true, false } });
        /*
         * Check repeats for any of the valid initial values. Note: the first three sequences are invalid because of the
         * repeat and because the repeated value is then outside of the window.
         */
        parameterSets.add(new Object[] { Integer.valueOf(5), new long[] { -1, 0, 1, -1 },
                new boolean[] { true, true, true, false } });
        parameterSets.add(new Object[] { Integer.valueOf(5), new long[] { 0, 1, 2, 0 },
                new boolean[] { true, true, true, false } });
        parameterSets.add(new Object[] { Integer.valueOf(5), new long[] { 1, 2, 3, 1 },
                new boolean[] { true, true, true, false } });
        parameterSets.add(new Object[] { Integer.valueOf(5), new long[] { 2, 3, 4, 2 },
                new boolean[] { true, true, true, false } });
        parameterSets.add(new Object[] { Integer.valueOf(5), new long[] { 3, 4, 5, 3 },
                new boolean[] { true, true, true, false } });
        /*
         * Check repeats for any of the valid initial values. Note: the first four sequences are invalid because of the
         * repeat and because the repeated value is then outside of the window.
         */
        parameterSets.add(new Object[] { Integer.valueOf(5), new long[] { -1, 0, 1, 2, -1 },
                new boolean[] { true, true, true, true, false } });
        parameterSets.add(new Object[] { Integer.valueOf(5), new long[] { 0, 1, 2, 3, 0 },
                new boolean[] { true, true, true, true, false } });
        parameterSets.add(new Object[] { Integer.valueOf(5), new long[] { 1, 2, 3, 4, 1 },
                new boolean[] { true, true, true, true, false } });
        parameterSets.add(new Object[] { Integer.valueOf(5), new long[] { 2, 3, 4, 5, 2 },
                new boolean[] { true, true, true, true, false } });
        parameterSets.add(new Object[] { Integer.valueOf(5), new long[] { 3, 4, 5, 6, 3 },
                new boolean[] { true, true, true, true, false } });
        /*
         * Check repeats for any of the valid initial values. Note: all sequences are invalid because of the repeat and
         * because the repeated value is then outside of the window.
         */
        parameterSets.add(new Object[] { Integer.valueOf(5), new long[] { -1, 0, 1, 2, 3, -1 },
                new boolean[] { true, true, true, true, true, false } });
        parameterSets.add(new Object[] { Integer.valueOf(5), new long[] { 0, 1, 2, 3, 4, 0 },
                new boolean[] { true, true, true, true, true, false } });
        parameterSets.add(new Object[] { Integer.valueOf(5), new long[] { 1, 2, 3, 4, 5, 1 },
                new boolean[] { true, true, true, true, true, false } });
        parameterSets.add(new Object[] { Integer.valueOf(5), new long[] { 2, 3, 4, 5, 6, 2 },
                new boolean[] { true, true, true, true, true, false } });
        parameterSets.add(new Object[] { Integer.valueOf(5), new long[] { 3, 4, 5, 6, 7, 3 },
                new boolean[] { true, true, true, true, true, false } });
        /*
         * Check sequence longer than window size that starts at the lower bound
         */
        parameterSets.add(new Object[] { Integer.valueOf(5), new long[] { -1, 0, 1, 2, 3, 4, 5, 6 },
                new boolean[] { true, true, true, true, true, true, true, true } });
        /*
         * Check sequence longer than window size that starts at upper bound
         */
        parameterSets.add(new Object[] { Integer.valueOf(5), new long[] { 3, 4, 5, 6, 7, 8, 9, 10 },
                new boolean[] { true, true, true, true, true, true, true, true } });
        /*
         * Check sequence longer than window size that starts at zero and then jumps to upper bound.
         */
        parameterSets.add(new Object[] { Integer.valueOf(5), new long[] { 0, 1, 2, 3, 4, 8 },
                new boolean[] { true, true, true, true, true, true } });

        return parameterSets;
    }


    @Parameter(0)
    public int windowSize;

    @Parameter(1)
    public long[] nonceCountSequence;

    @Parameter(2)
    public boolean[] expectedResponses;


    @Test
    public void testNonceCountSequence() {

        Assert.assertEquals("Inconsistent sequence size and expected response size", nonceCountSequence.length,
                expectedResponses.length);

        NonceInfo info = new NonceInfo(0, windowSize);

        for (int i = 0; i < nonceCountSequence.length; i++) {
            boolean response = info.nonceCountValid(nonceCountSequence[i]);
            if (response != expectedResponses[i]) {
                Assert.fail("Unexpected response at index [" + i + "]");
            }
        }
    }
}

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
package org.apache.tomcat.util.security;

import org.junit.Assert;
import org.junit.Test;

public class TestConcurrentMessageDigest {

    private static final String SHA256 = "SHA-256";
    private static final String SHA512 = "SHA-512";

    @Test(expected=IllegalStateException.class)
    public void testNoInit() throws Exception {
        // Need to use a different algorithm to the other tests otherwise if one
        // of the other tests is called first it will initialise the
        // ConcurrentDigest for that algorithm.
        ConcurrentMessageDigest.digest(SHA512, new byte[8]);
    }


    @Test
    public void testUncommon() throws Exception {
        ConcurrentMessageDigest.init(SHA256);
        ConcurrentMessageDigest.digest(SHA256, new byte[8]);
    }


    /*
     * A zero iteration count is not a valid number of times to apply the digest. It must fail rather than silently
     * applying the digest once.
     */
    @Test
    public void testZeroIterations() throws Exception {
        ConcurrentMessageDigest.init(SHA256);

        Assert.assertThrows(IllegalArgumentException.class,
                () -> ConcurrentMessageDigest.digest(SHA256, 0, new byte[8]));
    }


    /*
     * A negative iteration count is not a valid number of times to apply the digest. It must fail rather than
     * silently applying the digest once.
     */
    @Test
    public void testNegativeIterations() throws Exception {
        ConcurrentMessageDigest.init(SHA256);

        Assert.assertThrows(IllegalArgumentException.class,
                () -> ConcurrentMessageDigest.digest(SHA256, -5, new byte[8]));
    }


    /*
     * Multiple iterations must repeatedly digest the result of the previous iteration.
     */
    @Test
    public void testMultipleIterations() throws Exception {
        ConcurrentMessageDigest.init(SHA256);

        byte[] input = "input".getBytes("UTF-8");

        byte[] twice = ConcurrentMessageDigest.digest(SHA256, 2, input);
        byte[] expected = ConcurrentMessageDigest.digest(SHA256, ConcurrentMessageDigest.digest(SHA256, input));

        Assert.assertArrayEquals(expected, twice);

        byte[] threeTimes = ConcurrentMessageDigest.digest(SHA256, 3, input);
        expected = ConcurrentMessageDigest.digest(SHA256, expected);

        Assert.assertArrayEquals(expected, threeTimes);
    }
}

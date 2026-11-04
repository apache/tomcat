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
package org.apache.tomcat.util.buf;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestStringCache {

    private static final byte[] BYTES_VALID = new byte[] { 65, 66, 67, 68};
    private static final byte[] BYTES_INVALID = new byte[] {65, 66, -1, 67, 68};

    private static final ByteChunk INPUT_VALID = new ByteChunk();
    private static final ByteChunk INPUT_INVALID = new ByteChunk();

    private static final CodingErrorAction[] actions =
            new CodingErrorAction[] { CodingErrorAction.IGNORE, CodingErrorAction.REPLACE, CodingErrorAction.REPORT };

    static {
        INPUT_VALID.setBytes(BYTES_VALID, 0, BYTES_VALID.length);
        INPUT_VALID.setCharset(StandardCharsets.UTF_8);
        INPUT_INVALID.setBytes(BYTES_INVALID, 0, BYTES_INVALID.length);
        INPUT_INVALID.setCharset(StandardCharsets.UTF_8);
    }

    private final StringCache sc = new StringCache();
    private boolean byteEnabledBefore;

    @Before
    public void saveState() {
        byteEnabledBefore = sc.getByteEnabled();
    }

    @After
    public void restoreState() {
        sc.reset();
        sc.setByteEnabled(byteEnabledBefore);
    }


    @Test
    public void testCountersDoNotOverflow() throws Exception {
        sc.setByteEnabled(true);
        sc.reset();

        // Train the cache so subsequent lookups go through the hot path
        for (int i = 0; i < StringCache.trainThreshold * 2; i++) {
            INPUT_VALID.toString(CodingErrorAction.REPLACE, CodingErrorAction.REPLACE);
        }
        Assert.assertNotNull(StringCache.bcCache);

        // Set both counters one below Integer.MAX_VALUE; two cache hits must carry them past it
        StringCache.accessCount = Integer.MAX_VALUE - 1;
        StringCache.hitCount = Integer.MAX_VALUE - 1;

        INPUT_VALID.toString(CodingErrorAction.REPLACE, CodingErrorAction.REPLACE);
        INPUT_VALID.toString(CodingErrorAction.REPLACE, CodingErrorAction.REPLACE);

        Assert.assertEquals(Integer.MAX_VALUE + 1L, StringCache.accessCount);
        Assert.assertEquals(Integer.MAX_VALUE + 1L, StringCache.hitCount);
    }


    @Test
    public void testCodingErrorLookup() {

        sc.setByteEnabled(true);
        sc.reset();

        for (int i = 0; i < StringCache.trainThreshold * 2; i++) {
            for (CodingErrorAction malformedInputAction : actions) {
                try {
                    // UTF-8 doesn't have any unmappable characters
                    INPUT_VALID.toString(malformedInputAction, CodingErrorAction.IGNORE);
                    INPUT_INVALID.toString(malformedInputAction, CodingErrorAction.IGNORE);
                } catch (CharacterCodingException e) {
                    // Ignore
                }
            }
        }

        Assert.assertNotNull(StringCache.bcCache);

        // Check the valid input is cached correctly
        for (CodingErrorAction malformedInputAction : actions) {
            try {
                Assert.assertEquals("ABCD", INPUT_VALID.toString(malformedInputAction, CodingErrorAction.IGNORE));
            } catch (CharacterCodingException e) {
                // Should not happen for valid input
                Assert.fail();
            }
        }

        // Check the valid input is cached correctly
        try {
            Assert.assertEquals("ABCD", INPUT_INVALID.toString(CodingErrorAction.IGNORE, CodingErrorAction.IGNORE));
        } catch (CharacterCodingException e) {
            // Should not happen for invalid input with IGNORE
            Assert.fail();
        }
        try {
            Assert.assertEquals("AB\ufffdCD", INPUT_INVALID.toString(CodingErrorAction.REPLACE, CodingErrorAction.IGNORE));
        } catch (CharacterCodingException e) {
            // Should not happen for invalid input with REPLACE
            Assert.fail();
        }
        try {
            Assert.assertEquals("ABCD", INPUT_INVALID.toString(CodingErrorAction.REPORT, CodingErrorAction.IGNORE));
            // Should throw exception
            Assert.fail();
        } catch (CharacterCodingException e) {
            // Ignore
        }

    }
}

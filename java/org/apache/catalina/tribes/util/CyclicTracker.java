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
package org.apache.catalina.tribes.util;

/**
 * Tracks recently observed integers in a fixed-size cyclic window.
 * <p>
 * Values are ordered using the natural {@code long} sequence with overflow handling so {@code Long.MAX_VALUE} is
 * followed by {@code Long.MIN_VALUE}.
 */
public class CyclicTracker {

    private static final StringManager sm = StringManager.getManager(CyclicTracker.class);

    private final boolean[] seen;

    private boolean initialized = false;
    private long headValue;
    private int headIndex;


    public CyclicTracker(int size) {
        if (size < 1) {
            throw new IllegalArgumentException(sm.getString("cyclicTracker.size.tooSmall"));
        }
        seen = new boolean[size];
    }


    /**
     * Tracks the provided value.
     *
     * @param value The value to track
     *
     * @return {@code true} if the value had not previously been seen and is within the current tracking window,
     *             otherwise {@code false}
     */
    public synchronized boolean track(long value) {
        if (!initialized) {
            initialized = true;
            headValue = value;
            headIndex = 0;
            seen[0] = true;
            return true;
        }

        long behind = headValue - value;
        if (behind >= 0) {
            if (behind >= seen.length) {
                return false;
            }
            int index = toIndex(headIndex - (int) behind);
            if (seen[index]) {
                return false;
            }
            seen[index] = true;
            return true;
        }

        advance(distance(headValue, value));

        headValue = value;
        seen[headIndex] = true;
        return true;
    }

    /**
     * Returns the current head value.
     *
     * @return The current head value
     */
    public synchronized long getHeadValue() {
        if (!initialized) {
            throw new IllegalStateException("No value has been tracked");
        }
        return headValue;
    }


    private void advance(long delta) {
        if (Long.compareUnsigned(delta, seen.length) >= 0) {
            java.util.Arrays.fill(seen, false);
            headIndex = toIndex(headIndex + (int) (delta % seen.length));
            return;
        }

        for (int i = 1; i <= (int) delta; i++) {
            int index = toIndex(headIndex + i);
            seen[index] = false;
        }
        headIndex = toIndex(headIndex + (int) delta);
    }


    private int toIndex(int value) {
        int result = value % seen.length;
        if (result < 0) {
            result += seen.length;
        }
        return result;
    }


    private long distance(long from, long to) {
        return to - from;
    }
}

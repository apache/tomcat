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
package org.apache.catalina.session;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.session.ManagerBase.SessionTiming;

public class TestManagerBase {

    /*
     * Timing entries more than 2^31 milliseconds old must produce a sensible
     * rate rather than the result of an overflowing cast to int, which could
     * be negative or lead to a division by zero.
     */
    @Test
    public void testSessionRateWithSpanOverIntMax() {
        StandardManager manager = new StandardManager();

        long now = System.currentTimeMillis();

        manager.sessionCreationTiming.clear();
        manager.sessionCreationTiming.add(new SessionTiming(now - (1L << 32), 100));

        // Before the fix the delta was cast to int, yielding zero (division by
        // zero) or a small garbage value for spans that are a multiple of
        // 2^32
        Assert.assertEquals(0, manager.getSessionCreateRate());
    }

    @Test
    public void testSessionRateWithSpanOverflowingToNegative() {
        StandardManager manager = new StandardManager();

        long now = System.currentTimeMillis();

        // A span just under 2^32 casts to a negative int
        long span = (1L << 32) - 3000;

        manager.sessionCreationTiming.clear();
        for (int i = 0; i < 100; i++) {
            manager.sessionCreationTiming.add(new SessionTiming(now - span, 100));
        }

        // Before the fix this returned a negative rate
        Assert.assertEquals(0, manager.getSessionCreateRate());
    }
}

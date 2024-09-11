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
package org.apache.tomcat.util.http;

import org.junit.Assert;
import org.junit.Test;

public class TestFastHttpDateFormat {

    @Test
    public void testGetCurrentDateInSameSecond() {
        long now = System.currentTimeMillis();
        try {
            Thread.sleep(1000L - now % 1000);
        } catch (InterruptedException e) {
            // Ignore
        }
        now = System.currentTimeMillis();
        String s1 = FastHttpDateFormat.getCurrentDate();
        long lastMillisInSameSecond = now - now % 1000 + 900L;
        try {
            Thread.sleep(lastMillisInSameSecond - now);
        } catch (InterruptedException e) {
            // Ignore
        }
        String s2 = FastHttpDateFormat.getCurrentDate();
        Assert.assertEquals("Two same RFC5322 format dates are expected.", s1, s2);
    }

    @Test
    public void testGetCurrentDateNextToAnotherSecond() {
        long now = System.currentTimeMillis();

        try {
            Thread.sleep(2000L - now % 1000 + 500L);
        } catch (InterruptedException e) {
            // Ignore
        }
        now = System.currentTimeMillis();
        String s1 = FastHttpDateFormat.getCurrentDate();
        long firstMillisOfNextSecond = now - now % 1000 + 1100L;
        try {
            Thread.sleep(firstMillisOfNextSecond - now);
        } catch (InterruptedException e) {
            // Ignore
        }

        String s2 = FastHttpDateFormat.getCurrentDate();
        Assert.assertFalse("Two different RFC5322 format dates are expected.", s1.equals(s2));
    }
}

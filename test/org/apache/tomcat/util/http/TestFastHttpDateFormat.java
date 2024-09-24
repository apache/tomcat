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
    public void testGetCurrentDate() {
        /*
         * Run three iterations of the test. The runs are offset by 1300 seconds to ensure that FastHttpDateFormat is
         * using a 1s window aligned with the start of a second.
         */
        for (int i = 0; i < 3; i++) {
            if (i > 0) {
                try {
                    Thread.sleep(1300);
                } catch (InterruptedException e) {
                    Assert.fail("InterruptedException observed");
                }
            }
            String d1 = FastHttpDateFormat.getCurrentDate();
            long start = System.currentTimeMillis() / 1000;

            long t1 = 0;
            long t2 = 0;
            long t3 = start;
            String d2 = d1;

            /*
             * Run this test for 3s. Should normally see 3 changes of date. May, very rarely, see 2.
             */
            int changes = 0;
            while (t3 - start < 3) {

                // Copy results to next slot, dropping the oldest (t3 and d2)
                d2 = d1;
                t3 = t2;
                t2 = t1;

                // Get current results. The date must be obtained before the current time.
                d1 = FastHttpDateFormat.getCurrentDate();
                t1 = System.currentTimeMillis() / 1000;

                // Has the formatted date changed
                if (!d2.equals(d1)) {
                    changes++;
                    // Then the second must have changed
                    if (t1 == t2 && t2 == t3) {
                        Assert.fail("Formatted date changed withint the same second");
                    }
                }
            }
            Assert.assertTrue("Saw [" + changes + "] in formatted date", changes > 1 && changes < 4);
        }
    }
}

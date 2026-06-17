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

import org.junit.Assert;
import org.junit.Test;

public class TestCyclicTracker {

    @Test
    public void testRejectsDuplicateWithinWindow() {
        CyclicTracker tracker = new CyclicTracker(4);

        Assert.assertTrue(tracker.track(10));
        Assert.assertTrue(tracker.track(11));
        Assert.assertFalse(tracker.track(10));
        Assert.assertFalse(tracker.track(11));
    }


    @Test
    public void testAcceptsSkippedValueWithinWindow() {
        CyclicTracker tracker = new CyclicTracker(4);

        Assert.assertTrue(tracker.track(10));
        Assert.assertTrue(tracker.track(12));
        Assert.assertTrue(tracker.track(11));
        Assert.assertFalse(tracker.track(11));
    }


    @Test
    public void testRejectsValuesThatAreTooOld() {
        CyclicTracker tracker = new CyclicTracker(4);

        Assert.assertTrue(tracker.track(10));
        Assert.assertTrue(tracker.track(11));
        Assert.assertTrue(tracker.track(12));
        Assert.assertTrue(tracker.track(13));
        Assert.assertFalse(tracker.track(9));
    }


    @Test
    public void testAdvancePastWindowClearsOlderEntries() {
        CyclicTracker tracker = new CyclicTracker(4);

        Assert.assertTrue(tracker.track(10));
        Assert.assertTrue(tracker.track(20));
        Assert.assertTrue(tracker.track(17));
        Assert.assertFalse(tracker.track(16));
        Assert.assertFalse(tracker.track(10));
    }


    @Test
    public void testAcceptsValuesFarAhead() {
        CyclicTracker tracker = new CyclicTracker(4);

        Assert.assertTrue(tracker.track(10));
        Assert.assertTrue(tracker.track(Long.MIN_VALUE));
        Assert.assertFalse(tracker.track(Long.MIN_VALUE));
    }


    @Test
    public void testHandlesLongOverflow01() {
        CyclicTracker tracker = new CyclicTracker(4);

        Assert.assertTrue(tracker.track(Long.MAX_VALUE - 1));
        Assert.assertTrue(tracker.track(Long.MAX_VALUE));
        Assert.assertTrue(tracker.track(Long.MIN_VALUE));
        Assert.assertTrue(tracker.track(Long.MIN_VALUE + 1));

        Assert.assertFalse(tracker.track(Long.MAX_VALUE));
        Assert.assertFalse(tracker.track(Long.MAX_VALUE - 2));
    }


    @Test
    public void testHandlesLongOverflow02() {
        CyclicTracker tracker = new CyclicTracker(4);

        Assert.assertTrue(tracker.track(Long.MAX_VALUE - 1));
        Assert.assertTrue(tracker.track(Long.MIN_VALUE + 2));
        Assert.assertTrue(tracker.track(Long.MAX_VALUE));
        Assert.assertFalse(tracker.track(Long.MAX_VALUE - 2));
    }


    @Test
    public void testGetHeadValue() {
        CyclicTracker tracker = new CyclicTracker(4);

        Assert.assertTrue(tracker.track(10));
        Assert.assertEquals(10, tracker.getHeadValue());
        Assert.assertTrue(tracker.track(12));
        Assert.assertEquals(12, tracker.getHeadValue());
    }


    @Test(expected = IllegalStateException.class)
    public void testGetHeadValueNotInitialized() {
        CyclicTracker tracker = new CyclicTracker(4);

        tracker.getHeadValue();
    }


    @SuppressWarnings("unused")
    @Test(expected = IllegalArgumentException.class)
    public void testRejectsZeroSize() {
        new CyclicTracker(0);
    }
}

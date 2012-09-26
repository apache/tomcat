/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.collections;

import org.junit.Assert;
import org.junit.Test;

public class TestSynchronizedQueue {

    public void testPollEmpty() {
        SynchronizedQueue<Object> queue = new SynchronizedQueue<>();
        Assert.assertNull(queue.poll());
    }

    @Test
    public void testOfferPollOrder() {
        SynchronizedQueue<Object> queue = new SynchronizedQueue<>();

        Object o1 = new Object();
        Object o2 = new Object();
        Object o3 = new Object();
        Object o4 = new Object();

        queue.offer(o1);
        queue.offer(o2);
        queue.offer(o3);
        queue.offer(o4);

        Assert.assertSame(queue.poll(), o1);
        Assert.assertSame(queue.poll(), o2);
        Assert.assertSame(queue.poll(), o3);
        Assert.assertSame(queue.poll(), o4);

        Assert.assertNull(queue.poll());
    }

    @Test
    public void testExpandOfferPollOrder() {
        SynchronizedQueue<Object> queue = new SynchronizedQueue<>();

        Object o1 = new Object();
        Object o2 = new Object();
        Object o3 = new Object();
        Object o4 = new Object();

        for (int i = 0; i < 300; i++) {
            queue.offer(o1);
            queue.offer(o2);
            queue.offer(o3);
            queue.offer(o4);
        }

        for (int i = 0; i < 300; i++) {
            Assert.assertSame(queue.poll(), o1);
            Assert.assertSame(queue.poll(), o2);
            Assert.assertSame(queue.poll(), o3);
            Assert.assertSame(queue.poll(), o4);
        }

        Assert.assertNull(queue.poll());
    }

    @Test
    public void testExpandOfferPollOrder2() {
        SynchronizedQueue<Object> queue = new SynchronizedQueue<>();

        Object o1 = new Object();
        Object o2 = new Object();
        Object o3 = new Object();
        Object o4 = new Object();

        for (int i = 0; i < 100; i++) {
            queue.offer(o1);
            queue.offer(o2);
            queue.offer(o3);
            queue.offer(o4);
        }

        for (int i = 0; i < 50; i++) {
            Assert.assertSame(queue.poll(), o1);
            Assert.assertSame(queue.poll(), o2);
            Assert.assertSame(queue.poll(), o3);
            Assert.assertSame(queue.poll(), o4);
        }

        for (int i = 0; i < 200; i++) {
            queue.offer(o1);
            queue.offer(o2);
            queue.offer(o3);
            queue.offer(o4);
        }

        for (int i = 0; i < 250; i++) {
            Assert.assertSame(queue.poll(), o1);
            Assert.assertSame(queue.poll(), o2);
            Assert.assertSame(queue.poll(), o3);
            Assert.assertSame(queue.poll(), o4);
        }


        Assert.assertNull(queue.poll());
    }
}

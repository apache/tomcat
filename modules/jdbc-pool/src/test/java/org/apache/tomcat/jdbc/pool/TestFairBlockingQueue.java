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
package org.apache.tomcat.jdbc.pool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Expected functionality of the idle-connection queue the pool uses when
 * the fair queue is enabled: unbounded offering, most-recently-used
 * hand-off, blocking retrieval with timeout, fairness towards the
 * longest waiting consumer, and the collection operations the queue
 * deliberately leaves unsupported.
 */
class TestFairBlockingQueue {

    @Test
    void pollTimesOutOnEmptyQueue() throws Exception {
        FairBlockingQueue<String> queue = new FairBlockingQueue<>();
        long start = System.currentTimeMillis();
        assertNull(queue.poll(200, TimeUnit.MILLISECONDS));
        assertTrue(System.currentTimeMillis() - start >= 150,
                "poll must honour its timeout");
        assertEquals(0, queue.size());
    }

    @Test
    void mostRecentlyOfferedElementIsPolledFirst() {
        FairBlockingQueue<String> queue = new FairBlockingQueue<>();
        assertTrue(queue.offer("first"));
        assertTrue(queue.offer("second"));
        assertTrue(queue.offer("third"));
        assertEquals(3, queue.size());
        // the pool queue is a stack: the connection that was returned most
        // recently is the one handed out next
        assertEquals("third", queue.poll());
        assertEquals("second", queue.poll());
        assertEquals("first", queue.poll());
        assertEquals(0, queue.size());
        assertNull(queue.poll());
    }

    @Test
    void supportedCollectionOperationsTrackQueuedElements() {
        FairBlockingQueue<String> queue = new FairBlockingQueue<>();
        queue.offer("keep");
        queue.offer("drop");
        assertTrue(queue.contains("keep"));
        assertTrue(queue.contains("drop"));
        assertFalse(queue.isEmpty());

        assertTrue(queue.remove("drop"), "an idle element must be removable");
        assertFalse(queue.remove("drop"), "a second removal must report nothing removed");
        assertEquals(1, queue.size());
        assertFalse(queue.contains("drop"));

        queue.add("added");
        List<String> seen = new ArrayList<>();
        for (String element : queue) {
            seen.add(element);
        }
        assertEquals(2, seen.size(), "the iterator must see both queued elements");
        assertTrue(seen.contains("added"));
    }

    @Test
    void takerBlocksUntilElementArrives() throws Exception {
        final FairBlockingQueue<String> queue = new FairBlockingQueue<>();
        final AtomicReference<String> taken = new AtomicReference<>();
        final CountDownLatch done = new CountDownLatch(1);
        Thread taker = new Thread(() -> {
            try {
                taken.set(queue.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });
        taker.start();
        Thread.sleep(100);
        queue.offer("late");
        assertTrue(done.await(5, TimeUnit.SECONDS), "taker must be handed the element");
        assertEquals("late", taken.get());
        taker.join(1000);
    }

    @Test
    void handoffIsFairToLongestWaitingConsumer() throws Exception {
        final FairBlockingQueue<String> queue = new FairBlockingQueue<>();
        final AtomicReference<String> firstIn = new AtomicReference<>();
        final AtomicReference<String> secondIn = new AtomicReference<>();
        final CountDownLatch firstDone = new CountDownLatch(1);
        final CountDownLatch secondDone = new CountDownLatch(1);

        Thread first = new Thread(() -> {
            try {
                firstIn.set(queue.poll(10, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                firstDone.countDown();
            }
        });
        Thread second = new Thread(() -> {
            // ensure the first waiter registered first
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                secondIn.set(queue.poll(10, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                secondDone.countDown();
            }
        });
        first.start();
        Thread.sleep(50); // first waiter is queued first
        second.start();
        Thread.sleep(300); // both waiting

        queue.offer("one");
        queue.offer("two");

        assertTrue(firstDone.await(5, TimeUnit.SECONDS), "first waiter must be served");
        assertTrue(secondDone.await(5, TimeUnit.SECONDS), "second waiter must be served");
        assertSame("one", firstIn.get(),
                "the longest waiting consumer must receive the first element, got "
                        + firstIn.get());
        assertSame("two", secondIn.get());
    }

    @Test
    void putAndAddAllFillAnUnboundedQueue() throws Exception {
        FairBlockingQueue<String> queue = new FairBlockingQueue<>();
        int count = 50;
        for (int i = 0; i < count; i++) {
            queue.put("e" + i);
        }
        assertEquals(count, queue.size());
        assertEquals(Integer.MAX_VALUE - count, queue.remainingCapacity());

        List<String> drained = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            drained.add(queue.take());
        }
        assertEquals("e" + (count - 1), drained.get(0),
                "blocking take must follow the same most-recently-used order");
        assertEquals(0, queue.size());
        assertTrue(queue.isEmpty());
    }

    @Test
    void bulkCollectionOperationsAreUnsupportedByContract() {
        FairBlockingQueue<String> queue = new FairBlockingQueue<>();
        queue.offer("item");
        assertThrows(UnsupportedOperationException.class, queue::clear);
        assertThrows(UnsupportedOperationException.class,
                () -> queue.containsAll(Collections.singletonList("item")));
        assertThrows(UnsupportedOperationException.class,
                () -> queue.removeAll(Collections.emptyList()));
        assertThrows(UnsupportedOperationException.class,
                () -> queue.retainAll(Collections.emptyList()));
        assertThrows(UnsupportedOperationException.class,
                () -> queue.drainTo(new ArrayList<>()));
        assertEquals(1, queue.size(),
                "the unsupported operations must not mutate the queue");
    }
}

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
package org.apache.tomcat.util.threads;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
/**
 * Simple counter latch that allows code to keep an up and down counter, and waits while the latch holds a certain wait value.
 * and threads using the latch to wait if the count has reached a certain value.
 * The counter latch can be used to keep track of an atomic counter, since the operations {@link #countDown()}
 * and {@link #countUp()} are atomic.
 * When the latch reaches the wait value, threads will block. The counter latch can hence act like a 
 * count down latch or a count up latch, while letting you keep track of the counter as well.
 * This counter latch works opposite as the java.util.concurrent.CountDownLatch, since the CounterLatch only blocks on a single value and releases the threads on all other values.
 * @author fhanik
 * @see <a href="http://download.oracle.com/javase/6/docs/api/java/util/concurrent/CountDownLatch.html">CountDownLatch</a>
 *
 */
public class CounterLatch {

    private class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 1L;

        public Sync() {
        }

        @Override
        protected int tryAcquireShared(int arg) {
            return ((!released) && count.get() == signal) ? -1 : 1;
        }

        @Override
        protected boolean tryReleaseShared(int arg) {
            return true;
        }
    }

    private final Sync sync;
    private final AtomicLong count;
    private volatile long signal;
    private volatile boolean released = false;
    
    /**
     * Instantiates a CounterLatch object with an initial value and a wait value.
     * @param initial - initial value of the counter
     * @param waitValue - when the counter holds this value, 
     * threads calling {@link #await()} or {@link #await(long, TimeUnit)} 
     * will wait until the counter changes value or until they are interrupted.  
     */
    public CounterLatch(long initial, long waitValue) {
        this.signal = waitValue;
        this.count = new AtomicLong(initial);
        this.sync = new Sync();
    }

    /**
     * Causes the calling thread to wait if the counter holds the waitValue.
     * If the counter holds any other value, the thread will return
     * If the thread is interrupted or becomes interrupted an InterruptedException is thrown
     * @throws InterruptedException
     */
    public void await() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }

    /**
     * Causes the calling thread to wait if the counter holds the waitValue.
     * If the counter holds any other value, the thread will return
     * If the thread is interrupted or becomes interrupted an InterruptedException is thrown
     * @return true if the value changed, false if the timeout has elapsed
     * @throws InterruptedException
     */
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
    }

    /**
     * Increments the counter
     * @return the previous counter value
     */
    public long countUp() {
        long previous = count.getAndIncrement();
        if (previous == signal) {
            sync.releaseShared(0);
        }
        return previous;
    }

    /**
     * Decrements the counter
     * @return the previous counter value
     */
    public long countDown() {
        long previous = count.getAndDecrement();
        if (previous == signal) {
            sync.releaseShared(0);
        }
        return previous;
    }
    
    /**
     * Returns the current counter value
     * @return the current counter value
     */
    public long getCount() {
        return count.get();
    }
    
    /**
     * Performs an atomic update of the counter 
     * If the operation is successful and {@code expect==waitValue && expect!=update} waiting threads will be released.  
     * @param expect - the expected counter value
     * @param update - the new counter value
     * @return <code>true</code> if successful, <code>false</code> if the
     *         current value wasn't as expected
     */
    public boolean compareAndSet(long expect, long update) {
        boolean result = count.compareAndSet(expect, update);
        if (result && expect==signal && expect != update) {
            sync.releaseShared(0);
        }
        return result;
    }
    
    /**
     * returns true if there are threads blocked by this latch
     * @return true if there are threads blocked by this latch
     */
    public boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }
    
    /**
     * Returns a collection of the blocked threads
     * @return a collection of the blocked threads
     */
    public Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }
    
    /**
     * releases all waiting threads. This operation is permanent, and no threads will block,
     * even if the counter hits the {@code waitValue} until {@link #reset(long)} has been called.
     * @return <code>true</code> if this release of shared mode may permit a
     *         waiting acquire (shared or exclusive) to succeed; and
     *         <code>false</code> otherwise
     */
    public boolean releaseAll() {
        released = true;
        return sync.releaseShared(0);
    }
    
    /**
     * Resets the latch and initializes the counter with the new value.
     * @param value the new counter value
     * @see #releaseAll()
     */
    public void reset(long value) {
        this.count.set(value);
        released = false;
    }

}

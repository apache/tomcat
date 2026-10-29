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
package org.apache.tomcat.jdbc.pool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A simple implementation of a blocking queue with fairness waiting. invocations to method poll(...) will get handed
 * out in the order they were received. Locking is fine grained, a shared lock is only used during the first level of
 * contention, waiting is done in a lock per thread basis so that order is guaranteed once the thread goes into a
 * suspended monitor state. <br>
 * Not all of the methods of the {@link java.util.concurrent.BlockingQueue} are implemented.
 *
 * @param <E> Type of element in the queue
 */

public class FairBlockingQueue<E> implements BlockingQueue<E> {

    /**
     * This little sucker is used to reorder the way to do {@link java.util.concurrent.locks.Lock#lock()},
     * {@link java.util.concurrent.locks.Lock#unlock()} and {@link java.util.concurrent.CountDownLatch#countDown()}
     * during the {@link #poll(long, TimeUnit)} operation. On Linux, it performs much better if we count down while we
     * hold the global lock, on Solaris its the other way around. Until we have tested other platforms we only check for
     * Linux.
     */
    static final boolean isLinux = "Linux".equals(System.getProperty("os.name")) &&
            (!Boolean.getBoolean(FairBlockingQueue.class.getName() + ".ignoreOS"));

    /**
     * Phase one entry lock in order to give out per-thread-locks for the waiting phase we have a phase one lock during
     * the contention period.
     */
    final ReentrantLock lock = new ReentrantLock(false);

    /**
     * All the objects in the pool are stored in a simple linked list
     */
    final LinkedList<E> items;

    /**
     * All threads waiting for an object are stored in a linked list
     */
    final LinkedList<ExchangeCountDownLatch<E>> waiters;

    /**
     * Set once {@link #cancelWaiters()} has failed the waiters of this queue. The caller that cancels the waiters (a
     * closing pool) stops offering items to this queue afterwards, so an asynchronous poll that registers a waiter on a
     * cancelled queue could never be served.
     */
    private boolean waitersCancelled = false;

    /**
     * Creates a new fair blocking queue.
     */
    public FairBlockingQueue() {
        items = new LinkedList<>();
        waiters = new LinkedList<>();
    }

    // The queue methods below are used by the connection pool implementation.
    /**
     * Will always return true, queue is unbounded. {@inheritDoc}
     *
     * @throws NullPointerException - if the specified element is null
     */
    @Override
    public boolean offer(E e) {
        // The queue uses null as its absence-of-item sentinel when polling the
        // items, so a null element would make a stored item indistinguishable
        // from an empty queue and a null hand-off would silently deliver no
        // item to the waiter it is given to. Like the JDK queue
        // implementations, reject null elements.
        Objects.requireNonNull(e);
        // During the offer, we will grab the main lock
        final ReentrantLock lock = this.lock;
        lock.lock();
        ExchangeCountDownLatch<E> c = null;
        try {
            // Check to see if threads are waiting for an object
            if (!waiters.isEmpty()) {
                // If threads are waiting grab the latch for that thread
                c = waiters.poll();
                // Give the object to the thread instead of adding it to the pool
                c.setItem(e);
                if (isLinux) {
                    c.countDown();
                }
            } else {
                // We always add first, so that the most recently used object will be given out
                items.addFirst(e);
            }
        } finally {
            lock.unlock();
        }
        // If we exchanged an object with another thread, wake it up.
        if (!isLinux && c != null) {
            c.countDown();
        }
        // We have an unbounded queue, so always return true
        return true;
    }

    /**
     * Will never timeout, as it invokes the {@link #offer(Object)} method. Once a lock has been acquired, the
     * {@inheritDoc}
     */
    @Override
    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        return offer(e);
    }

    /**
     * Fair retrieval of an object in the queue. Objects are returned in the order the threads requested them.
     * {@inheritDoc}
     */
    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E result = null;
        final ReentrantLock lock = this.lock;
        // Acquire the global lock until we know what to do
        lock.lock();
        try {
            // Check to see if we have objects
            result = items.poll();
            if (result == null && timeout > 0) {
                // The queue is empty we will wait for an object
                ExchangeCountDownLatch<E> c = new ExchangeCountDownLatch<>(1);
                // Add to the bottom of the wait list
                waiters.addLast(c);
                // Unlock the global lock
                lock.unlock();
                boolean didtimeout = true;
                InterruptedException interruptedException = null;
                try {
                    // Wait for the specified timeout
                    didtimeout = !c.await(timeout, unit);
                } catch (InterruptedException ix) {
                    interruptedException = ix;
                }
                if (didtimeout) {
                    // If we timed out, or got interrupted
                    // remove ourselves from the waitlist
                    lock.lock();
                    try {
                        waiters.remove(c);
                    } finally {
                        lock.unlock();
                    }
                }
                // Return the item we received, can be null if we timed out
                result = c.getItem();
                if (null != interruptedException) {
                    // We got interrupted
                    if (null != result) {
                        // We got a result - clear the interrupt status
                        // don't propagate cause we have removed a connection from pool
                        Thread.interrupted();
                    } else {
                        throw interruptedException;
                    }
                }
            } else {
                // We have an object, release
                lock.unlock();
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        return result;
    }

    /**
     * Request an item from the queue asynchronously. If no item is available, the returned future registers a waiter on
     * the queue. Cancelling the future deregisters that waiter again, provided the item has not already been handed
     * over to the future. A future that is no longer needed must therefore be cancelled, so that a subsequent offer
     * does not hand an item over to a future nobody is waiting on. A
     * {@link java.util.concurrent.Future#get(long, TimeUnit)} call on the future that times out deregisters the waiter
     * in the same way and fails the future terminally, so that every later retrieval from it fails right away instead
     * of waiting for an item that can no longer be handed over. Once {@link #cancelWaiters()} has been called, no
     * waiter is registered anymore: the returned future is cancelled right away, as the caller that cancelled the
     * waiters offers no further item that could serve it.
     *
     * @return - a future pending the result from the queue poll request
     */
    public Future<E> pollAsync() {
        Future<E> result = null;
        final ReentrantLock lock = this.lock;
        // Grab the global lock
        lock.lock();
        try {
            // Check to see if we have objects in the queue
            E item = items.poll();
            if (item == null) {
                // Queue is empty, add ourselves as waiters
                ExchangeCountDownLatch<E> c = new ExchangeCountDownLatch<>(1);
                // Return a future that will wait for the object
                ItemFuture<E> future = new ItemFuture<>(c);
                // Tell the latch which future its item is meant for, before the
                // waiter becomes visible to cancelWaiters()
                c.setFuture(future);
                if (waitersCancelled) {
                    // The caller that cancelled the waiters offers no further
                    // item, so registering a waiter here would park its caller
                    // forever - hand out a future that is cancelled from the
                    // start instead
                    future.cancel(false);
                } else {
                    waiters.addLast(c);
                }
                result = future;
            } else {
                // Return a future with the item
                result = new ItemFuture<>(item);
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * Cancel the futures of all the asynchronous polls that are waiting for an item on this queue. A thread that is
     * blocked in one of the {@link Future#get()} methods of such a future observes a {@link CancellationException}
     * instead of waiting for an item that will never be offered, and later calls to {@link #pollAsync()} do not
     * register a waiter at all. A future an item has already been handed over to is not cancelled, it completes with
     * that item as usual. <br>
     * This makes a queue that is never fed again terminate the callers that wait on it, instead of leaving them parked
     * forever.
     */
    public void cancelWaiters() {
        final ReentrantLock lock = this.lock;
        List<ItemFuture<E>> futures;
        lock.lock();
        try {
            // The caller that cancels the waiters offers no further item from
            // this point on, so a waiter that registers after it can never be
            // served either
            waitersCancelled = true;
            futures = new ArrayList<>(waiters.size());
            for (ExchangeCountDownLatch<E> c : waiters) {
                ItemFuture<E> future = c.getFuture();
                if (future != null) {
                    // Only the asynchronous polls have a future to cancel, the
                    // waiters of poll(long,TimeUnit) time out on their own
                    futures.add(future);
                }
            }
        } finally {
            lock.unlock();
        }
        // Cancel outside of the lock, as the cancellation wakes up the callers
        // and deregisters their waiter itself
        for (ItemFuture<E> future : futures) {
            future.cancel(false);
        }
    }

    @Override
    public boolean remove(Object e) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return items.remove(e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int size() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return items.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Iterator<E> iterator() {
        return new FairIterator();
    }

    @Override
    public E poll() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return items.poll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean contains(Object e) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return items.contains(e);
        } finally {
            lock.unlock();
        }
    }


    // The queue methods below are not used by the connection pool implementation.
    @Override
    public boolean add(E e) {
        return offer(e);
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException - this operation is not supported
     */
    @Override
    public int drainTo(Collection<? super E> c, int maxElements) {
        throw new UnsupportedOperationException("int drainTo(Collection<? super E> c, int maxElements)");
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException - this operation is not supported
     */

    @Override
    public int drainTo(Collection<? super E> c) {
        return drainTo(c, Integer.MAX_VALUE);
    }

    @Override
    public void put(E e) throws InterruptedException {
        offer(e);
    }

    @Override
    public int remainingCapacity() {
        return Integer.MAX_VALUE - size();
    }

    @Override
    public E take() throws InterruptedException {
        return this.poll(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        Iterator<? extends E> i = c.iterator();
        while (i.hasNext()) {
            E e = i.next();
            offer(e);
        }
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException - this operation is not supported
     */
    @Override
    public void clear() {
        throw new UnsupportedOperationException("void clear()");

    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException - this operation is not supported
     */
    @Override
    public boolean containsAll(Collection<?> c) {
        throw new UnsupportedOperationException("boolean containsAll(Collection<?> c)");
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException - this operation is not supported
     */
    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException("boolean removeAll(Collection<?> c)");
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException - this operation is not supported
     */
    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException("boolean retainAll(Collection<?> c)");
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException - this operation is not supported
     */
    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException("Object[] toArray()");
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException - this operation is not supported
     */
    @Override
    public <T> T[] toArray(T[] a) {
        throw new UnsupportedOperationException("<T> T[] toArray(T[] a)");
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException - this operation is not supported
     */
    @Override
    public E element() {
        throw new UnsupportedOperationException("E element()");
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException - this operation is not supported
     */
    @Override
    public E peek() {
        throw new UnsupportedOperationException("E peek()");
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException - this operation is not supported
     */
    @Override
    public E remove() {
        throw new UnsupportedOperationException("E remove()");
    }


    /**
     * A future used to check if a connection has been made available. Cancelling the future deregisters its waiter from
     * the queue, as long as the item has not already been handed over to the future. <br>
     * A {@link #get(long, TimeUnit)} call that times out deregisters the waiter in the same way, and additionally fails
     * the future terminally: no offer can reach the deregistered latch anymore, so the future reports {@link #isDone()}
     * true without ever being cancelled, keeps failing every later {@link #get(long, TimeUnit)} with a
     * {@link TimeoutException} and every later {@link #get()} with an {@link ExecutionException} caused by one, and can
     * no longer be cancelled.
     *
     * @param <T> the type of the item
     */
    protected class ItemFuture<T> implements Future<T> {
        /** The item directly available in the future. */
        protected volatile T item = null;
        /** The latch used to wait for an item. */
        protected volatile ExchangeCountDownLatch<T> latch = null;
        /** Whether this future has been cancelled. */
        protected volatile boolean canceled = false;
        /**
         * Whether a {@link #get(long, TimeUnit)} call on this future timed out. Its waiter was then deregistered from
         * the queue, so no item can ever be handed over to this future anymore; the future is terminally failed and its
         * get() methods fail fast instead of parking on a latch that nothing can count down again.
         */
        protected volatile boolean abandoned = false;

        /**
         * Creates an ItemFuture with the given item already available.
         *
         * @param item the item
         */
        public ItemFuture(T item) {
            this.item = item;
        }

        /**
         * Creates an ItemFuture that waits on the given latch.
         *
         * @param latch the latch to wait on
         */
        public ItemFuture(ExchangeCountDownLatch<T> latch) {
            this.latch = latch;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (item != null) {
                // The item is already available, too late to cancel
                return false;
            }
            ExchangeCountDownLatch<T> l = latch;
            if (l == null) {
                return false;
            }
            final ReentrantLock lock = FairBlockingQueue.this.lock;
            lock.lock();
            try {
                // The item is handed over under the queue lock, so a non-null
                // item means an offer() has already committed to this future
                if (l.getItem() != null || canceled || abandoned) {
                    // The future is complete: it received its item, it was
                    // cancelled earlier, or a timed out get() call abandoned
                    // its waiter, so there is nothing left to cancel
                    return false;
                }
                canceled = true;
                // Deregister the waiter, so that a subsequent offer() does not
                // hand an item over to a future nobody is waiting on anymore
                waiters.remove(l);
            } finally {
                lock.unlock();
            }
            // Wake up any thread blocked in one of the get() methods, so that
            // it can observe the cancellation instead of waiting forever
            l.countDown();
            return true;
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            if (item != null) {
                return item;
            } else if (latch != null) {
                if (canceled) {
                    throw new CancellationException();
                }
                if (abandoned) {
                    throw abandonedFailure();
                }
                latch.await();
                // The latch is counted down by the offer() that hands over
                // the item, by cancel(), or by a timed get() call that
                // abandoned this future - check for each of them
                if (canceled) {
                    throw new CancellationException();
                }
                if (abandoned) {
                    throw abandonedFailure();
                }
                return latch.getItem();
            } else {
                throw new ExecutionException("ItemFuture incorrectly instantiated. Bug in the code?", new Exception());
            }
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            if (item != null) {
                return item;
            } else if (latch != null) {
                if (canceled) {
                    throw new CancellationException();
                }
                if (abandoned) {
                    throw abandonedTimeout();
                }
                boolean timedout = !latch.await(timeout, unit);
                if (canceled) {
                    throw new CancellationException();
                }
                if (abandoned) {
                    // Another timed get() call abandoned this future while
                    // this one was waiting, so this wait can no longer be
                    // served either
                    throw abandonedTimeout();
                }
                if (timedout) {
                    final ReentrantLock lock = FairBlockingQueue.this.lock;
                    T delivered;
                    boolean lostToCancel;
                    lock.lock();
                    try {
                        // The item is handed over under the queue lock, so an
                        // offer() that committed to this latch concurrently
                        // with the timeout is visible here
                        delivered = latch.getItem();
                        lostToCancel = canceled;
                        if (delivered == null && !lostToCancel) {
                            // An abandoned timed-out waiter would otherwise keep
                            // consuming offered items, so deregister it as well -
                            // this mirrors what the timed poll() method does
                            waiters.remove(latch);
                            // No offer() can reach the deregistered latch
                            // anymore and nothing else can complete the future,
                            // so fail it terminally: a later get() must not
                            // park on a latch that will never be counted down
                            abandoned = true;
                        }
                    } finally {
                        lock.unlock();
                    }
                    if (delivered != null) {
                        // An item may have been handed over concurrently with
                        // the timeout; in that case it is delivered rather than
                        // lost
                        return delivered;
                    }
                    if (lostToCancel) {
                        // A cancel() won the race against this timeout; its
                        // callers are told about the cancellation
                        throw new CancellationException();
                    }
                    // Wake up any other caller already blocked in a get()
                    // method, so it observes the abandonment instead of
                    // waiting on a latch nothing will count down again
                    latch.countDown();
                    throw new TimeoutException();
                } else {
                    return latch.getItem();
                }
            } else {
                throw new ExecutionException("ItemFuture incorrectly instantiated. Bug in the code?", new Exception());
            }
        }

        @Override
        public boolean isCancelled() {
            return canceled;
        }

        @Override
        public boolean isDone() {
            return (item != null || canceled || abandoned || (latch != null && latch.getItem() != null));
        }

        /**
         * The failure for a caller of {@link #get(long, TimeUnit)} of a future whose waiter was abandoned by an earlier
         * timeout. The wait cannot ever succeed anymore, so the timeout of this call is what the caller gets to hear
         * about, right away.
         *
         * @return the timeout failure to throw
         */
        private TimeoutException abandonedTimeout() {
            return new TimeoutException("The waiter of this future timed out on an earlier" +
                    " get() call and was deregistered from the queue; the future can no" + " longer receive an item");
        }

        /**
         * The failure for a caller of the untimed {@link #get()} of a future whose waiter was abandoned by an earlier
         * timeout. The get() method cannot declare a {@link TimeoutException}, so, the same way
         * {@link ConnectionPool.ConnectionFuture#get()} does, it reports the timeout as the cause of an
         * ExecutionException.
         *
         * @return the execution failure to throw
         */
        private ExecutionException abandonedFailure() {
            return new ExecutionException(abandonedTimeout());
        }

    }

    /**
     * A count down latch that can be used to exchange information.
     *
     * @param <T> the type of the item to exchange
     */
    protected class ExchangeCountDownLatch<T> extends CountDownLatch {
        /** The item to exchange. */
        protected volatile T item;
        /**
         * The future the item is exchanged with, set by {@link FairBlockingQueue#pollAsync()} and null for the waiters
         * of {@link FairBlockingQueue#poll(long, TimeUnit)}.
         */
        protected volatile ItemFuture<T> future;

        /**
         * Creates a new ExchangeCountDownLatch with the given count.
         *
         * @param i the count
         */
        public ExchangeCountDownLatch(int i) {
            super(i);
        }

        /**
         * Returns the exchanged item.
         *
         * @return the item
         */
        public T getItem() {
            return item;
        }

        /**
         * Sets the item to be exchanged.
         *
         * @param item the item
         */
        public void setItem(T item) {
            this.item = item;
        }

        /**
         * Returns the future the exchanged item is meant for.
         *
         * @return the future of an asynchronous poll, or null
         */
        public ItemFuture<T> getFuture() {
            return future;
        }

        /**
         * Sets the future the exchanged item is meant for.
         *
         * @param future the future of an asynchronous poll
         */
        public void setFuture(ItemFuture<T> future) {
            this.future = future;
        }
    }

    /**
     * Iterator safe from concurrent modification exceptions.
     */
    protected class FairIterator implements Iterator<E> {
        E[] elements = null;
        int index;
        E element = null;

        /**
         * Creates a new FairIterator with a snapshot of the current queue contents.
         */
        @SuppressWarnings("unchecked") // Can't create arrays of generic types
        public FairIterator() {
            final ReentrantLock lock = FairBlockingQueue.this.lock;
            lock.lock();
            try {
                elements = (E[]) new Object[FairBlockingQueue.this.items.size()];
                FairBlockingQueue.this.items.toArray(elements);
                index = 0;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public boolean hasNext() {
            return index < elements.length;
        }

        @Override
        public E next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            element = elements[index++];
            return element;
        }

        @Override
        public void remove() {
            final ReentrantLock lock = FairBlockingQueue.this.lock;
            lock.lock();
            try {
                if (element != null) {
                    FairBlockingQueue.this.items.remove(element);
                }
            } finally {
                lock.unlock();
            }
        }

    }
}

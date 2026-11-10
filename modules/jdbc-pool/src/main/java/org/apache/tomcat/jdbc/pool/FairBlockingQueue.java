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

import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * A simple implementation of a blocking queue with fairness waiting. Waiters of method poll(...) are handed items in
 * the order in which they registered as waiters. Locking is fine grained: a shared lock is only used during the first
 * level of contention, while waiting is done on a per-waiter {@link java.util.concurrent.CountDownLatch}, so order is
 * guaranteed once a thread has registered itself as a waiter - a caller that has not registered yet may still barge
 * ahead when it races for the shared lock. <br>
 * Not all of the methods of the {@link java.util.concurrent.BlockingQueue} are supported: the ones that are not throw
 * {@link UnsupportedOperationException}.
 *
 * @param <E> Type of element in the queue
 */

public class FairBlockingQueue<E> implements BlockingQueue<E> {

    /**
     * This little sucker is used to reorder the way to do {@link java.util.concurrent.locks.Lock#lock()},
     * {@link java.util.concurrent.locks.Lock#unlock()} and {@link java.util.concurrent.CountDownLatch#countDown()}
     * when {@link #offer(Object)} wakes a waiter whose {@link #poll(long, TimeUnit)} wait the offer completes. On
     * Linux, it performs much better if we count down while we hold the global lock, on Solaris its the other way
     * around. Until we have tested other platforms we only check for Linux.
     */
    static final boolean isLinux = "Linux".equals(System.getProperty("os.name")) &&
            (!Boolean.getBoolean(FairBlockingQueue.class.getName() + ".ignoreOS"));

    /**
     * Phase one entry lock: held only during the contention period, in order to register the per-waiter latch that
     * the waiting phase then parks on.
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
        final ReentrantLock lock = this.lock;
        lock.lock();
        ExchangeCountDownLatch<E> c = null;
        try {
            if (!waiters.isEmpty()) {
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
     * Fair retrieval of an object in the queue. Objects are returned in the order the threads requested them. If the
     * wait is interrupted but an object was handed over to this waiter all the same, the object is returned and the
     * interrupt status of the calling thread is restored instead of being cleared. On a queue whose waiters the
     * {@link #cancelWaiters()} method has failed, an empty queue hands back no item right away instead of parking the
     * caller: such a queue is never fed again, so waiting could only end in the same empty-handed return.
     * {@inheritDoc}
     */
    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E result = null;
        final ReentrantLock lock = this.lock;
        // Acquire the global lock until we know what to do
        lock.lock();
        try {
            result = items.poll();
            if (result == null && timeout > 0 && !waitersCancelled) {
                // The queue is empty we will wait for an object
                ExchangeCountDownLatch<E> c = new ExchangeCountDownLatch<>(1);
                waiters.addLast(c);
                lock.unlock();
                boolean didtimeout = true;
                InterruptedException interruptedException = null;
                try {
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
                        // We got a result - deliver it, the connection is not
                        // lost, but re-assert the interrupt status rather than
                        // swallow it: a method that returns normally after
                        // having noticed an interrupt must leave the status
                        // set, the way the JDK hand-off queues do, so that an
                        // interrupt meant for a later phase is not lost
                        Thread.currentThread().interrupt();
                    } else {
                        throw interruptedException;
                    }
                }
            } else {
                // We have an object, or there is none and none can arrive on
                // a queue whose waiters are failed; either way, do not park
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
        lock.lock();
        try {
            E item = items.poll();
            if (item == null) {
                // Queue is empty, add ourselves as waiters
                ExchangeCountDownLatch<E> c = new ExchangeCountDownLatch<>(1);
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
                result = new ItemFuture<>(item);
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * Fail all the polls that are waiting for an item on this queue. A thread blocked in one of the
     * {@link Future#get()} methods of an asynchronous poll observes a {@link CancellationException}, and a waiter of a
     * timed {@link #poll(long, TimeUnit)} is woken without an item, the way its timeout ends the wait. Later calls to
     * {@link #pollAsync()} and {@link #poll(long, TimeUnit)} do not register a waiter at all. A poll an item has
     * already been handed over to is not failed, it completes with that item as usual. <br>
     * This makes a queue that is never fed again terminate the callers that wait on it, instead of leaving them parked
     * forever - including the timed waiters, whose own timeout the caller cannot know: the pool hands out waits of
     * unbounded length, where waiting for the timeout would be waiting forever.
     */
    public void cancelWaiters() {
        final ReentrantLock lock = this.lock;
        List<ItemFuture<E>> futures;
        List<ExchangeCountDownLatch<E>> timedWaiters;
        lock.lock();
        try {
            // The caller that cancels the waiters offers no further item from
            // this point on, so a waiter that registers after it can never be
            // served either
            waitersCancelled = true;
            futures = new ArrayList<>(waiters.size());
            timedWaiters = new ArrayList<>(waiters.size());
            for (Iterator<ExchangeCountDownLatch<E>> it = waiters.iterator(); it.hasNext();) {
                ExchangeCountDownLatch<E> c = it.next();
                ItemFuture<E> future = c.getFuture();
                if (future != null) {
                    // The asynchronous polls are failed through their future
                    futures.add(future);
                } else {
                    // A timed poll has no future to cancel: deregister its
                    // waiter here, so no offer hands an item to a caller that
                    // is being failed, and wake it below with the item still
                    // null, so its poll returns null as a timeout does
                    it.remove();
                    timedWaiters.add(c);
                }
            }
        } finally {
            lock.unlock();
        }
        // Cancel and wake outside of the lock, as both wake up the callers; a
        // cancelled future deregisters its waiter itself
        for (ItemFuture<E> future : futures) {
            future.cancel(false);
        }
        for (ExchangeCountDownLatch<E> c : timedWaiters) {
            c.countDown();
        }
    }

    /**
     * Reports whether any caller is registered on this queue waiting for an
     * item - through a timed poll or an asynchronous one. A releaser that
     * decides whether an offered item has a prospective consumer cannot see
     * these registrations through the pool's wait counter, which counts only
     * the synchronous borrow waits.
     *
     * @return <code>true</code> if at least one caller is registered waiting
     *             for an item
     */
    public boolean hasRegisteredWaiter() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return !waiters.isEmpty();
        } finally {
            lock.unlock();
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

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }


    // The queue methods below are not used by the connection pool implementation.
    @Override
    public boolean add(E e) {
        return offer(e);
    }

    /**
     * Will never time out, as it invokes the {@link #offer(Object)} method: the element is always inserted, so the
     * timeout is never applied and, despite the declaration, {@link InterruptedException} is never thrown.
     * {@inheritDoc}
     */
    @Override
    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        return offer(e);
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException - this operation is not supported
     */
    @Override
    public int drainTo(Collection<? super E> c, int maxElements) {
        throw new UnsupportedOperationException(MessageFactory.get("fairBlockingQueue.unsupportedDrainTo"));
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

    /**
     * Delegates to {@link #poll(long, TimeUnit)} with an effectively unbounded timeout, so the wait also ends the way
     * that method documents for a queue whose waiters {@link #cancelWaiters()} has failed. {@inheritDoc}
     */
    @Override
    public E take() throws InterruptedException {
        return this.poll(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        Iterator<? extends E> i = c.iterator();
        boolean changed = false;
        while (i.hasNext()) {
            E e = i.next();
            changed |= offer(e);
        }
        return changed;
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException - this operation is not supported
     */
    @Override
    public void clear() {
        throw new UnsupportedOperationException(MessageFactory.get("fairBlockingQueue.unsupportedClear"));

    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException - this operation is not supported
     */
    @Override
    public boolean containsAll(Collection<?> c) {
        throw new UnsupportedOperationException(MessageFactory.get("fairBlockingQueue.unsupportedContainsAll"));
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException - this operation is not supported
     */
    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException(MessageFactory.get("fairBlockingQueue.unsupportedRemoveAll"));
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException - this operation is not supported
     */
    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException(MessageFactory.get("fairBlockingQueue.unsupportedRetainAll"));
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException - this operation is not supported
     */
    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException(MessageFactory.get("fairBlockingQueue.unsupportedToArray"));
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException - this operation is not supported
     */
    @Override
    public <T> T[] toArray(T[] a) {
        throw new UnsupportedOperationException(MessageFactory.get("fairBlockingQueue.unsupportedToArrayT"));
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException - this operation is not supported
     */
    @Override
    public E element() {
        throw new UnsupportedOperationException(MessageFactory.get("fairBlockingQueue.unsupportedElement"));
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException - this operation is not supported
     */
    @Override
    public E peek() {
        throw new UnsupportedOperationException(MessageFactory.get("fairBlockingQueue.unsupportedPeek"));
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException - this operation is not supported
     */
    @Override
    public E remove() {
        throw new UnsupportedOperationException(MessageFactory.get("fairBlockingQueue.unsupportedRemove"));
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
                throw new ExecutionException(MessageFactory.get("fairBlockingQueue.itemFutureInstantiationError"),
                        new Exception());
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
                throw new ExecutionException(MessageFactory.get("fairBlockingQueue.itemFutureInstantiationError"),
                        new Exception());
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
            return new TimeoutException(MessageFactory.get("fairBlockingQueue.itemFutureAbandoned"));
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
     * Iterator safe from concurrent modification exceptions. The iterator walks a snapshot of the queue contents taken
     * when it was created, but {@link #remove()} removes the last returned element - or, for element types with value
     * semantics, an element equal to it - from the live queue.
     * An element that was taken out of the queue and offered back as the same object between the snapshot and the
     * removal is therefore also removed from the live queue - like the weakly consistent iterators of the JDK, this
     * iterator makes no promise about seeing the current state of the queue.
     */
    protected class FairIterator implements Iterator<E> {
        E[] elements = null;
        int index;
        E element = null;
        boolean canRemove = false;

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
            canRemove = true;
            return element;
        }

        @Override
        public void remove() {
            if (!canRemove) {
                // Either next() has not been called yet, or the element it
                // returned has already been removed - the Iterator contract
                // demands an IllegalStateException, not a silent no-op
                throw new IllegalStateException();
            }
            final ReentrantLock lock = FairBlockingQueue.this.lock;
            lock.lock();
            try {
                FairBlockingQueue.this.items.remove(element);
            } finally {
                lock.unlock();
            }
            element = null;
            canRemove = false;
        }

    }
}

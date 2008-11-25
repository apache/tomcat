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

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * A simple implementation of a blocking queue with fairness waiting.
 * invocations to method poll(...) will get handed out in the order they were received.
 * @author Filip Hanik
 *
 */

public class FairBlockingQueue<E> implements BlockingQueue<E> {
    ReentrantLock lock = new ReentrantLock(false);

    LinkedList<E> items = null;

    LinkedList<ExchangeCountDownLatch<E>> waiters = null;

    public FairBlockingQueue() {
        items = new LinkedList<E>();
        waiters = new LinkedList<ExchangeCountDownLatch<E>>();
    }

    //------------------------------------------------------------------
    // USED BY CONPOOL IMPLEMENTATION
    //------------------------------------------------------------------
    /**
     * {@inheritDoc}
     */
    public boolean offer(E e) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        ExchangeCountDownLatch<E> c = null;
        try {
            if (waiters.size() > 0) {
                c = waiters.poll();
                c.setItem(e);
            } else {
                items.add(e);
            }
        } finally {
            lock.unlock();
        }
        if (c!=null) c.countDown();
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        return offer(e);
    }

    /**
     * {@inheritDoc}
     */
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E result = null;
        final ReentrantLock lock = this.lock;
        boolean error = true;
        lock.lock();
        try {
            result = items.poll();
            if (result==null && timeout>0) {
                ExchangeCountDownLatch<E> c = new ExchangeCountDownLatch<E>(1);
                waiters.addLast(c);
                lock.unlock();
                if (!c.await(timeout, unit)) {
                    lock.lock();
                    waiters.remove(c);
                    lock.unlock();
                }
                result = c.getItem();
            } else {
                lock.unlock();
            }
            error = false;
        } finally {
            if (error && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        return result;
    }
    
    /**
     * Request an item from the queue asynchronously
     * @return - a future pending the result from the queue poll request
     */
    public Future<E> pollAsync() {
        Future<E> result = null;
        final ReentrantLock lock = this.lock;
        boolean error = true;
        lock.lock();
        try {
            E item = items.poll();
            if (item==null) {
                ExchangeCountDownLatch<E> c = new ExchangeCountDownLatch<E>(1);
                waiters.addLast(c);
                lock.unlock();
                result = new ItemFuture(c);
            } else {
                lock.unlock();
                result = new ItemFuture(item);
            }
            error = false;
        } finally {
            if (error && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        return result;
    }
    
    /**
     * {@inheritDoc}
     */
    public boolean remove(Object e) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return items.remove(e);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * {@inheritDoc}
     */
    public int size() {
        return items.size();
    }

    /**
     * {@inheritDoc}
     */
    public Iterator<E> iterator() {
        return new FairIterator();
    }

    /**
     * {@inheritDoc}
     */
    public E poll() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return items.poll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean contains(Object e) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return items.contains(e);
        } finally {
            lock.unlock();
        }
    }


    //------------------------------------------------------------------
    // NOT USED BY CONPOOL IMPLEMENTATION
    //------------------------------------------------------------------
    /**
     * {@inheritDoc}
     */
    public boolean add(E e) {
        return offer(e);
    }

    /**
     * {@inheritDoc}
     * @throws UnsupportedOperation - this operation is not supported
     */
    public int drainTo(Collection<? super E> c, int maxElements) {
        throw new UnsupportedOperationException("int drainTo(Collection<? super E> c, int maxElements)");
    }

    /**
     * {@inheritDoc}
     * @throws UnsupportedOperation - this operation is not supported
     */
    public int drainTo(Collection<? super E> c) {
        return drainTo(c,Integer.MAX_VALUE);
    }

    /**
     * {@inheritDoc}
     */
    public void put(E e) throws InterruptedException {
        offer(e);
    }

    /**
     * {@inheritDoc}
     */
    public int remainingCapacity() {
        return Integer.MAX_VALUE - size();
    }

    /**
     * {@inheritDoc}
     */
    public E take() throws InterruptedException {
        return this.poll(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    /**
     * {@inheritDoc}
     */
    public boolean addAll(Collection<? extends E> c) {
        Iterator i = c.iterator();
        while (i.hasNext()) {
            E e = (E)i.next();
            offer(e);
        }
        return true;
    }

    /**
     * {@inheritDoc}
     * @throws UnsupportedOperation - this operation is not supported
     */
    public void clear() {
        throw new UnsupportedOperationException("void clear()");

    }

    /**
     * {@inheritDoc}
     * @throws UnsupportedOperation - this operation is not supported
     */
    public boolean containsAll(Collection<?> c) {
        throw new UnsupportedOperationException("boolean containsAll(Collection<?> c)");
    }

    /**
     * {@inheritDoc}
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * {@inheritDoc}
     * @throws UnsupportedOperation - this operation is not supported
     */
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException("boolean removeAll(Collection<?> c)");
    }

    /**
     * {@inheritDoc}
     * @throws UnsupportedOperation - this operation is not supported
     */
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException("boolean retainAll(Collection<?> c)");
    }

    /**
     * {@inheritDoc}
     * @throws UnsupportedOperation - this operation is not supported
     */
    public Object[] toArray() {
        throw new UnsupportedOperationException("Object[] toArray()");
    }

    /**
     * {@inheritDoc}
     * @throws UnsupportedOperation - this operation is not supported
     */
    public <T> T[] toArray(T[] a) {
        throw new UnsupportedOperationException("<T> T[] toArray(T[] a)");
    }

    /**
     * {@inheritDoc}
     * @throws UnsupportedOperation - this operation is not supported
     */
    public E element() {
        throw new UnsupportedOperationException("E element()");
    }

    /**
     * {@inheritDoc}
     * @throws UnsupportedOperation - this operation is not supported
     */
    public E peek() {
        throw new UnsupportedOperationException("E peek()");
    }

    /**
     * {@inheritDoc}
     * @throws UnsupportedOperation - this operation is not supported
     */
    public E remove() {
        throw new UnsupportedOperationException("E remove()");
    }



    //------------------------------------------------------------------
    // Non cancellable Future used to check and see if a connection has been made available
    //------------------------------------------------------------------
    protected class ItemFuture<T> implements Future<T> {
        protected volatile T item = null;
        protected volatile ExchangeCountDownLatch<T> latch = null;
        protected volatile boolean canceled = false;
        
        public ItemFuture(T item) {
            this.item = item;
        }
        
        public ItemFuture(ExchangeCountDownLatch<T> latch) {
            this.latch = latch;
        }
        
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false; //don't allow cancel for now
        }

        public T get() throws InterruptedException, ExecutionException {
            if (item!=null) {
                return item;
            } else if (latch!=null) {
                latch.await();
                return latch.getItem();
            } else {
                throw new ExecutionException("ItemFuture incorrectly instantiated. Bug in the code?", new Exception());
            }
        }

        public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            if (item!=null) {
                return item;
            } else if (latch!=null) {
                boolean timedout = !latch.await(timeout, unit);
                if (timedout) throw new TimeoutException();
                else return latch.getItem();
            } else {
                throw new ExecutionException("ItemFuture incorrectly instantiated. Bug in the code?", new Exception());
            }
        }

        public boolean isCancelled() {
            return false;
        }

        public boolean isDone() {
            return (item!=null || latch.getItem()!=null);
        }
        
    }

    //------------------------------------------------------------------
    // Count down latch that can be used to exchange information
    //------------------------------------------------------------------
    protected class ExchangeCountDownLatch<T> extends CountDownLatch {
        protected volatile T item;
        public ExchangeCountDownLatch(int i) {
            super(i);
        }
        public T getItem() {
            return item;
        }
        public void setItem(T item) {
            this.item = item;
        }
    }

    //------------------------------------------------------------------
    // Iterator safe from concurrent modification exceptions
    //------------------------------------------------------------------
    protected class FairIterator implements Iterator<E> {
        E[] elements = null;
        int index;
        E element = null;

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
        public boolean hasNext() {
            return index<elements.length;
        }

        public E next() {
            element = elements[index++];
            return element;
        }

        public void remove() {
            final ReentrantLock lock = FairBlockingQueue.this.lock;
            lock.lock();
            try {
                if (element!=null) {
                    FairBlockingQueue.this.items.remove(element);
                }
            } finally {
                lock.unlock();
            }
        }

    }
}

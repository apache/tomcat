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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 
 * A simple implementation of a blocking queue with fairness waiting.
 * invocations to method poll(...) will get handed out in the order they were received.
 * @author Filip Hanik
 * 
 */

public class FairBlockingQueue<E> implements BlockingQueue<E> {
    ReentrantLock lock = new ReentrantLock();
    
    LinkedList<E> items = null;
    
    LinkedList<ExchangeCountDownLatch<E>> waiters = null;
    
    public FairBlockingQueue() {
        items = new LinkedList<E>();
        waiters = new LinkedList<ExchangeCountDownLatch<E>>();
    }
    
    //------------------------------------------------------------------    
    // USED BY CONPOOL IMPLEMENTATION
    //------------------------------------------------------------------    
    public boolean offer(E e) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (waiters.size() > 0) {
                ExchangeCountDownLatch<E> c = waiters.poll();
                c.setItem(e);
                c.countDown();
            } else {
                items.add(e);
            }
        } finally {
            lock.unlock();
        }
        return true;
    }

    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        return offer(e);
    }
    
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E result = null;
        final ReentrantLock lock = this.lock;
        boolean error = true;
        lock.lock();
        try {
            result = items.poll();
            if (result==null) {
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
    
    public boolean remove(Object e) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return items.remove(e);
        } finally {
            lock.unlock();
        }
    }
    
    public int size() {
        return items.size();
    }
    
    public Iterator<E> iterator() {
        return new FairIterator();
    }
    
    public E poll() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return items.poll();
        } finally {
            lock.unlock();
        }
    }
    
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
    
    @Override
    public boolean add(E e) {
        return offer(e);
    }

    @Override
    public int drainTo(Collection<? super E> c, int maxElements) {
        throw new UnsupportedOperationException("int drainTo(Collection<? super E> c, int maxElements)");
    }

    @Override
    public int drainTo(Collection<? super E> c) {
        return drainTo(c,Integer.MAX_VALUE);
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
        Iterator i = c.iterator();
        while (i.hasNext()) {
            E e = (E)i.next();
            offer(e);
        }
        return true;
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("void clear()");
        
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        throw new UnsupportedOperationException("boolean containsAll(Collection<?> c)");
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException("boolean removeAll(Collection<?> c)");
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException("boolean retainAll(Collection<?> c)");
    }

    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException("Object[] toArray()");
    }

    @Override
    public <T> T[] toArray(T[] a) {
        throw new UnsupportedOperationException("<T> T[] toArray(T[] a)");
    }

    @Override
    public E element() {
        throw new UnsupportedOperationException("E element()");
    }

    @Override
    public E peek() {
        throw new UnsupportedOperationException("E peek()");
    }

    @Override
    public E remove() {
        throw new UnsupportedOperationException("E remove()");
    }





    //------------------------------------------------------------------    
    // Count down latch that can be used to exchange information
    //------------------------------------------------------------------    
    protected class ExchangeCountDownLatch<T> extends CountDownLatch {
        protected T item;
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
        @Override
        public boolean hasNext() {
            return index<elements.length;
        }

        @Override
        public E next() {
            element = elements[index++];
            return element;
        }

        @Override
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

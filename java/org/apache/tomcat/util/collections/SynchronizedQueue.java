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

/**
 * This is intended as a (mostly) GC-free alternative to {@link java.util.concurrent.ConcurrentLinkedQueue} when the
 * requirement is to create an unbounded queue with no requirement to shrink the queue. The aim is to provide the bare
 * minimum of required functionality as quickly as possible with minimum garbage.
 *
 * @param <T> The type of object managed by this queue
 */
public class SynchronizedQueue<T> {

    /**
     * The default initial capacity for a new queue.
     */
    public static final int DEFAULT_SIZE = 128;

    /**
     * The internal array backing the queue.
     */
    private Object[] queue;

    /**
     * The current capacity of the internal array.
     */
    private int size;

    /**
     * The index of the next insertion position.
     */
    private int insert = 0;

    /**
     * The index of the next removal position.
     */
    private int remove = 0;

    /**
     * Constructs a new SynchronizedQueue with the default initial capacity.
     */
    public SynchronizedQueue() {
        this(DEFAULT_SIZE);
    }

    /**
     * Constructs a new SynchronizedQueue with the specified initial capacity.
     *
     * @param initialSize the initial capacity
     */
    public SynchronizedQueue(int initialSize) {
        queue = new Object[initialSize];
        size = initialSize;
    }

    /**
     * Adds the specified element to the tail of this queue.
     *
     * @param t the element to add
     * @return true (as specified by {@link java.util.Queue#offer})
     */
    public synchronized boolean offer(T t) {
        queue[insert++] = t;

        // Wrap
        if (insert == size) {
            insert = 0;
        }

        if (insert == remove) {
            expand();
        }
        return true;
    }

    /**
     * Retrieves and removes the head of this queue, or returns null
     * if this queue is empty.
     *
     * @return the head of this queue, or null if empty
     */
    public synchronized T poll() {
        if (insert == remove) {
            // empty
            return null;
        }

        @SuppressWarnings("unchecked")
        T result = (T) queue[remove];
        queue[remove] = null;
        remove++;

        // Wrap
        if (remove == size) {
            remove = 0;
        }

        return result;
    }

    private void expand() {
        int newSize = size * 2;
        Object[] newQueue = new Object[newSize];

        System.arraycopy(queue, insert, newQueue, 0, size - insert);
        System.arraycopy(queue, 0, newQueue, size - insert, insert);

        insert = size;
        remove = 0;
        queue = newQueue;
        size = newSize;
    }

    /**
     * Returns the number of elements in this queue.
     *
     * @return the number of elements
     */
    public synchronized int size() {
        int result = insert - remove;
        if (result < 0) {
            result += size;
        }
        return result;
    }

    /**
     * Removes all elements from this queue.
     */
    public synchronized void clear() {
        queue = new Object[size];
        insert = 0;
        remove = 0;
    }
}

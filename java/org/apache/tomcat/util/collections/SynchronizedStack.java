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
 * This is intended as a (mostly) GC-free alternative to {@link java.util.Stack} when the requirement is to create a
 * pool of re-usable objects with no requirement to shrink the pool. The aim is to provide the bare minimum of required
 * functionality as quickly as possible with minimum garbage.
 *
 * @param <T> The type of object managed by this stack
 */
public class SynchronizedStack<T> {

    /**
     * The default initial capacity for a new stack.
     */
    public static final int DEFAULT_SIZE = 128;

    /**
     * Indicates no limit on stack size.
     */
    private static final int DEFAULT_LIMIT = -1;

    /**
     * The current capacity of the internal array.
     */
    private int size;

    /**
     * The maximum capacity, or -1 for unlimited.
     */
    private int limit;

    /*
     * Points to the next available object in the stack
     */
    private int index = -1;

    /**
     * The internal array backing the stack.
     */
    private Object[] stack;


    /**
     * Constructs a new SynchronizedStack with default size and no limit.
     */
    public SynchronizedStack() {
        this(DEFAULT_SIZE, DEFAULT_LIMIT);
    }

    /**
     * Constructs a new SynchronizedStack with the specified initial size and limit.
     *
     * @param size the initial capacity
     * @param limit the maximum capacity, or -1 for unlimited
     */
    public SynchronizedStack(int size, int limit) {
        if (limit > -1 && size > limit) {
            this.size = limit;
        } else {
            this.size = size;
        }
        this.limit = limit;
        stack = new Object[this.size];
    }


    /**
     * Pushes an object onto the stack.
     *
     * @param obj The object to push
     *
     * @return {@code true} if the object was pushed, {@code false} if the stack is full
     */
    public synchronized boolean push(T obj) {
        index++;
        if (index == size) {
            if (limit == -1 || size < limit) {
                expand();
            } else {
                index--;
                return false;
            }
        }
        stack[index] = obj;
        return true;
    }

    /**
     * Pops an object from the stack.
     *
     * @return the object at the top of the stack, or {@code null} if the stack is empty
     */
    @SuppressWarnings("unchecked")
    public synchronized T pop() {
        if (index == -1) {
            return null;
        }
        T result = (T) stack[index];
        stack[index--] = null;
        return result;
    }

    /**
     * Clears all objects from the stack.
     */
    public synchronized void clear() {
        if (index > -1) {
            for (int i = 0; i < index + 1; i++) {
                stack[i] = null;
            }
        }
        index = -1;
    }

    /**
     * Sets the maximum number of elements that can be pushed onto this stack.
     *
     * @param limit the maximum size, or -1 for unlimited
     */
    public synchronized void setLimit(int limit) {
        this.limit = limit;
    }

    private void expand() {
        int newSize = size * 2;
        if (limit != -1 && newSize > limit) {
            newSize = limit;
        }
        Object[] newStack = new Object[newSize];
        System.arraycopy(stack, 0, newStack, 0, size);
        // This is the only point where garbage is created by throwing away the
        // old array. Note it is only the array, not the contents, that becomes
        // garbage.
        stack = newStack;
        size = newSize;
    }
}

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
 * This is intended as a (mostly) GC-free alternative to
 * {@link java.util.concurrent.LinkedBlockingDeque} where the requirement is to
 * create a pool of re-usable objects with no requirement to shrink the pool.
 * The aim is to provide the bare minimum of required functionality as quickly
 * as possible with minimum garbage.
 */
public class ConcurrentStack<T> {

    private int size = 128;
    /*
     * Points to the next available object in the stack
     */
    private int index = -1;

    private Object[] stack = new Object[size];

    public synchronized void push(T obj) {
        index++;
        if (index == size) {
            expand();
        }
        stack[index] = obj;
    }

    @SuppressWarnings("unchecked")
    public synchronized T pop() {
        if (index == -1) {
            return null;
        }
        return (T) stack[index--];
    }

    private void expand() {
        int newSize = size * 2;
        Object[] newStack = new Object[newSize];
        System.arraycopy(stack, 0, newStack, 0, size);
        // This is the only point where garbage is created by throwing away the
        // old array. Note it is only the array, not the contents, that becomes
        // garbage.
        stack = newStack;
        size = newSize;
    }
}

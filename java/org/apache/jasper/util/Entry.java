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
package org.apache.jasper.util;

/**
 * Implementation of a list entry. It exposes links to previous and next
 * elements on package level only.
 */
public class Entry<T> {

    /** The content this entry is valid for. */
    private final T content;
    /** Pointer to next element in queue. */
    private Entry<T> next;
    /** Pointer to previous element in queue. */
    private Entry<T> previous;

    public Entry(T object) {
        content = object;
    }

    protected void setNext(final Entry<T> next) {
        this.next = next;
    }

    protected void setPrevious(final Entry<T> previous) {
        this.previous = previous;
    }

    public T getContent() {
        return content;
    }

    public Entry<T> getPrevious() {
        return previous;
    }

    public Entry<T> getNext() {
        return next;
    }

    @Override
    public String toString() {
        return content.toString();
    }
}

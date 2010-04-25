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
 * 
 * The JspQueue is supposed to hold a set of instances in sorted order. Sorting
 * order is determined by the instances' content. As this content may change
 * during instance lifetime, the Queue must be cheap to update - ideally in
 * constant time.
 * 
 * Access to the first element in the queue must happen in constant time.
 * 
 * Only a minimal set of operations is implemented.
 */
public class JspQueue<T> {

    /** Head of the queue. */
    private Entry<T> head;
    /** Last element of the queue. */
    private Entry<T> last;

    /** Initialize empty queue. */
    public JspQueue() {
        head = null;
        last = null;
    }

    /**
     * Adds an object to the end of the queue and returns the entry created for
     * said object. The entry can later be reused for moving the entry back to
     * the front of the list.
     * 
     * @param object
     *            the object to append to the end of the list.
     * @return a ticket for use when the object should be moved back to the
     *         front.
     * */
    public Entry<T> push(final T object) {
        Entry<T> entry = new Entry<T>(object);
        if (head == null) {
            head = last = entry;
        } else {
            last.setPrevious(entry);
            entry.setNext(last);
            last = entry;
        }

        return entry;
    }

    /**
     * Removes the head of the queue and returns its content.
     * 
     * @return the content of the head of the queue.
     **/
    public T pop() {
        T content = null;
        if (head != null) {
            content = head.getContent();
            if (head.getPrevious() != null)
                head.getPrevious().setNext(null);
            head = head.getPrevious();
        }
        return content;
    }

    /**
     * Moves the candidate to the front of the queue.
     * 
     * @param candidate
     *            the entry to move to the front of the queue.
     * */
    public void makeYoungest(final Entry<T> candidate) {
        if (candidate.getPrevious() != null) {
            Entry<T> candidateNext = candidate.getNext();
            Entry<T> candidatePrev = candidate.getPrevious();
            candidatePrev.setNext(candidateNext);
            if (candidateNext != null)
                candidateNext.setPrevious(candidatePrev);
            else
                head = candidatePrev;
            candidate.setNext(last);
            candidate.setPrevious(null);
            last.setPrevious(candidate);
            last = candidate;
        }
    }
}

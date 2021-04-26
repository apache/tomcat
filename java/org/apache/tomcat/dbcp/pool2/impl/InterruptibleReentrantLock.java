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
package org.apache.tomcat.dbcp.pool2.impl;

import java.util.Collection;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This sub-class was created to expose the waiting threads so that they can be
 * interrupted when the pool using the queue that uses this lock is closed. The
 * class is intended for internal use only.
 * <p>
 * This class is intended to be thread-safe.
 * </p>
 *
 * @since 2.0
 */
class InterruptibleReentrantLock extends ReentrantLock {

    private static final long serialVersionUID = 1L;

    /**
     * Create a new InterruptibleReentrantLock with the given fairness policy.
     *
     * @param fairness true means threads should acquire contended locks as if
     * waiting in a FIFO queue
     */
    public InterruptibleReentrantLock(final boolean fairness) {
        super(fairness);
    }

    /**
     * Interrupt the threads that are waiting on a specific condition
     *
     * @param condition the condition on which the threads are waiting.
     */
    public void interruptWaiters(final Condition condition) {
        final Collection<Thread> threads = getWaitingThreads(condition);
        for (final Thread thread : threads) {
            thread.interrupt();
        }
    }
}

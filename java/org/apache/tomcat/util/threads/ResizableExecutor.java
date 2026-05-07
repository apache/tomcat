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
package org.apache.tomcat.util.threads;

import java.util.concurrent.Executor;

/**
 * Executor that supports dynamic resizing of the thread pool and queue.
 */
public interface ResizableExecutor extends Executor {

    /**
     * Returns the current number of threads in the pool.
     *
     * @return the number of threads
     */
    int getPoolSize();

    /**
     * Returns the maximum number of threads in the pool.
     *
     * @return the maximum number of threads
     */
    int getMaxThreads();

    /**
     * Returns the approximate number of threads that are actively executing tasks.
     *
     * @return the number of threads
     */
    int getActiveCount();

    /**
     * Resize the thread pool.
     *
     * @param corePoolSize    The new core pool size
     * @param maximumPoolSize The new maximum pool size
     * @return True if the pool was resized successfully
     */
    boolean resizePool(int corePoolSize, int maximumPoolSize);

    /**
     * Resize the work queue.
     *
     * @param capacity The new queue capacity
     * @return True if the queue was resized successfully
     */
    boolean resizeQueue(int capacity);

}

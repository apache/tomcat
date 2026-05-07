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
package org.apache.catalina.startup;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;

/**
 * Provides a {@link ForkJoinWorkerThreadFactory} that provides {@link ForkJoinWorkerThread}s that won't trigger memory
 * leaks due to retained references to web application class loaders.
 * <p>
 * Note: This class must be available on the bootstrap class path for it to be visible to {@link ForkJoinPool}.
 */
public class SafeForkJoinWorkerThreadFactory implements ForkJoinWorkerThreadFactory {

    /**
     * Default constructor.
     */
    public SafeForkJoinWorkerThreadFactory() {
    }

    /**
     * Create a new {@link SafeForkJoinWorkerThread} for the given pool.
     *
     * @param pool the fork-join pool
     * @return a new worker thread with a safe context class loader
     */
    @Override
    public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
        return new SafeForkJoinWorkerThread(pool);
    }


    /**
     * A ForkJoinWorkerThread that sets its context class loader to the bootstrap class loader to
     * prevent memory leaks caused by retained references to web application class loaders.
     */
    private static class SafeForkJoinWorkerThread extends ForkJoinWorkerThread {

        /**
         * Create a new safe worker thread and set its context class loader to the bootstrap loader.
         *
         * @param pool the fork-join pool
         */
        protected SafeForkJoinWorkerThread(ForkJoinPool pool) {
            super(pool);
            setContextClassLoader(ForkJoinPool.class.getClassLoader());
        }
    }
}
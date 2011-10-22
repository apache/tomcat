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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

/**
 * A utility class to execute a {@link Callable} in a dedicated thread.
 * It can be used either with an instance to reuse the same thread for each call
 * to {@link #execute(Callable)} or with the static method
 * {@link #executeInOwnThread(Callable)}. When using an instance,
 * {@link #shutdown()} must be called when the instance is no longer needed to
 * dispose of the dedicated thread.
 */
public class DedicatedThreadExecutor {
    private final SingleThreadFactory singleThreadFactory =
        new SingleThreadFactory();
    private final ExecutorService executorService =
        Executors.newSingleThreadExecutor(singleThreadFactory);

    /**
     * Executes the given {@link Callable} with the thread spawned for the
     * current {@link DedicatedThreadExecutor} instance, and returns its result.
     *
     * @param <V>
     *            the type of the returned value
     * @param callable
     * @return the completed result
     */
    public <V> V execute(final Callable<V> callable) {
        final Future<V> futureTask = executorService.submit(callable);

        boolean interrupted = false;
        V result;
        while (true) {
            try {
                result = futureTask.get();
                break;
            } catch (InterruptedException e) {
                // keep waiting
                interrupted = true;
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        if (interrupted) {
            // set interruption status so that the caller may react to it
            Thread.currentThread().interrupt();
        }
        return result;
    }

    /**
     * Stops the dedicated thread and waits for its death.
     */
    public void shutdown() {
        executorService.shutdown();
        if (singleThreadFactory.singleThread != null) {
            boolean interrupted = false;
            while (true) {
                try {
                    singleThreadFactory.singleThread.join();
                    singleThreadFactory.singleThread = null;
                    break;
                } catch (InterruptedException e) {
                    // keep waiting
                    interrupted = true;
                }
            }
            if (interrupted) {
                // set interruption status so that the caller may react to it
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Executes the given {@link Callable} in a new thread and returns the
     * result after the thread is stopped.
     *
     * @param <V>
     * @param callable
     * @return the completed result
     */
    public static <V> V executeInOwnThread(
        final Callable<V> callable) {
        DedicatedThreadExecutor executor = new DedicatedThreadExecutor();
        try {
            return executor.execute(callable);
        } finally {
            executor.shutdown();
        }

    }

    // we use a ThreadFactory so that we can later call Thread.join().
    // Indeed, calling shutdown() on an ExecutorService will eventually stop the
    // thread but it might still be alive when execute() returns (race
    // condition).
    // This can lead to false alarms about potential memory leaks because the
    // thread may have a web application class loader for its context class
    // loader.
    private static class SingleThreadFactory implements ThreadFactory {
        private volatile Thread singleThread;

        @Override
        public Thread newThread(Runnable r) {
            if (singleThread != null) {
                throw new IllegalStateException(
                    "should not have been called more than once");
            }
            singleThread = new Thread(r);
            singleThread.setDaemon(true);
            return singleThread;
        }

    }
}

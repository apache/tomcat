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

import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;


/**
 * Provides a shared idle object eviction timer for all pools.
 * <p>
 * This class is currently implemented using {@link ScheduledThreadPoolExecutor}. This implementation may change in any
 * future release. This class keeps track of how many pools are using it. If no pools are using the timer, it is
 * cancelled. This prevents a thread being left running which, in application server environments, can lead to memory
 * leads and/or prevent applications from shutting down or reloading cleanly.
 * </p>
 * <p>
 * This class has package scope to prevent its inclusion in the pool public API. The class declaration below should
 * *not* be changed to public.
 * </p>
 * <p>
 * This class is intended to be thread-safe.
 * </p>
 *
 * @since 2.0
 */
class EvictionTimer {

    /**
     * Thread factory that creates a daemon thread, with the context class loader from this class.
     */
    private static class EvictorThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(final Runnable runnable) {
            final Thread thread = new Thread(null, runnable, "commons-pool-evictor");
            thread.setDaemon(true); // POOL-363 - Required for applications using Runtime.addShutdownHook().
            thread.setContextClassLoader(EvictorThreadFactory.class.getClassLoader());
            return thread;
        }
    }

    /**
     * Task that removes references to abandoned tasks and shuts
     * down the executor if there are no live tasks left.
     */
    private static class Reaper implements Runnable {
        @Override
        public void run() {
            synchronized (EvictionTimer.class) {
                for (final Entry<WeakReference<BaseGenericObjectPool<?>.Evictor>, WeakRunner<BaseGenericObjectPool<?>.Evictor>> entry : TASK_MAP
                        .entrySet()) {
                    if (entry.getKey().get() == null) {
                        executor.remove(entry.getValue());
                        TASK_MAP.remove(entry.getKey());
                    }
                }
                if (TASK_MAP.isEmpty() && executor != null) {
                    executor.shutdown();
                    executor.setCorePoolSize(0);
                    executor = null;
                }
            }
        }
    }

    /**
     * Runnable that runs the referent of a weak reference. When the referent is no
     * no longer reachable, run is no-op.
     * @param <R> The kind of Runnable.
     */
    private static class WeakRunner<R extends Runnable> implements Runnable {

        private final WeakReference<R> ref;

        /**
         * Constructs a new instance to track the given reference.
         *
         * @param ref the reference to track.
         */
        private WeakRunner(final WeakReference<R> ref) {
           this.ref = ref;
        }

        @Override
        public void run() {
            final Runnable task = ref.get();
            if (task != null) {
                task.run();
            } else {
                executor.remove(this);
                TASK_MAP.remove(ref);
            }
        }
    }


    /** Executor instance */
    private static ScheduledThreadPoolExecutor executor; //@GuardedBy("EvictionTimer.class")

    /** Keys are weak references to tasks, values are runners managed by executor. */
    private static final HashMap<
        WeakReference<BaseGenericObjectPool<?>.Evictor>,
        WeakRunner<BaseGenericObjectPool<?>.Evictor>> TASK_MAP = new HashMap<>(); // @GuardedBy("EvictionTimer.class")

    /**
     * Removes the specified eviction task from the timer.
     *
     * @param evictor   Task to be cancelled.
     * @param timeout   If the associated executor is no longer required, how
     *                  long should this thread wait for the executor to
     *                  terminate?
     * @param restarting The state of the evictor.
     */
    static synchronized void cancel(final BaseGenericObjectPool<?>.Evictor evictor, final Duration timeout,
            final boolean restarting) {
        if (evictor != null) {
            evictor.cancel();
            remove(evictor);
        }
        if (!restarting && executor != null && TASK_MAP.isEmpty()) {
            executor.shutdown();
            try {
                executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (final InterruptedException e) {
                // Swallow
                // Significant API changes would be required to propagate this
            }
            executor.setCorePoolSize(0);
            executor = null;
        }
    }

    /**
     * @return the number of eviction tasks under management.
     */
    static synchronized int getNumTasks() {
        return TASK_MAP.size();
    }

    /**
     * Gets the task map. Keys are weak references to tasks, values are runners managed by executor.
     *
     * @return the task map.
     */
    static HashMap<WeakReference<BaseGenericObjectPool<?>.Evictor>, WeakRunner<BaseGenericObjectPool<?>.Evictor>> getTaskMap() {
        return TASK_MAP;
    }

    /**
     * Removes evictor from the task set and executor.
     * Only called when holding the class lock.
     *
     * @param evictor Eviction task to remove
     */
    private static void remove(final BaseGenericObjectPool<?>.Evictor evictor) {
        for (final Entry<WeakReference<BaseGenericObjectPool<?>.Evictor>, WeakRunner<BaseGenericObjectPool<?>.Evictor>> entry : TASK_MAP.entrySet()) {
            if (entry.getKey().get() == evictor) {
                executor.remove(entry.getValue());
                TASK_MAP.remove(entry.getKey());
                break;
            }
        }
    }

    /**
     * Adds the specified eviction task to the timer. Tasks that are added with
     * a call to this method *must* call {@link
     * #cancel(BaseGenericObjectPool.Evictor, Duration, boolean)}
     * to cancel the task to prevent memory and/or thread leaks in application
     * server environments.
     *
     * @param task      Task to be scheduled.
     * @param delay     Delay in milliseconds before task is executed.
     * @param period    Time in milliseconds between executions.
     */
    static synchronized void schedule(
            final BaseGenericObjectPool<?>.Evictor task, final Duration delay, final Duration period) {
        if (null == executor) {
            executor = new ScheduledThreadPoolExecutor(1, new EvictorThreadFactory());
            executor.setRemoveOnCancelPolicy(true);
            executor.scheduleAtFixedRate(new Reaper(), delay.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
        }
        final WeakReference<BaseGenericObjectPool<?>.Evictor> ref = new WeakReference<>(task);
        final WeakRunner<BaseGenericObjectPool<?>.Evictor> runner = new WeakRunner<>(ref);
        final ScheduledFuture<?> scheduledFuture = executor.scheduleWithFixedDelay(runner, delay.toMillis(),
                period.toMillis(), TimeUnit.MILLISECONDS);
        task.setScheduledFuture(scheduledFuture);
        TASK_MAP.put(ref, runner);
    }

    /** Prevents instantiation */
    private EvictionTimer() {
        // Hide the default constructor
    }

    /**
     * @since 2.4.3
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("EvictionTimer []");
        return builder.toString();
    }

}

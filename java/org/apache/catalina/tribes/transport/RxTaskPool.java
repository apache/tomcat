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
package org.apache.catalina.tribes.transport;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A very simple thread pool class. The pool size is set at construction time and remains fixed. Threads are cycled
 * through a FIFO idle queue.
 */
public class RxTaskPool {

    final List<AbstractRxTask> idle = new ArrayList<>();
    final List<AbstractRxTask> used = new ArrayList<>();

    final Object mutex = new Object();
    boolean running = true;

    private int maxTasks;
    private int minTasks;

    private final TaskCreator creator;


    /**
     * Create a new fixed-size receive task pool.
     *
     * @param maxTasks maximum number of tasks in the pool
     * @param minTasks minimum number of tasks in the pool
     * @param creator factory for creating new receive tasks
     * @throws Exception if initialization fails
     */
    public RxTaskPool(int maxTasks, int minTasks, TaskCreator creator) throws Exception {
        // fill up the pool with worker threads
        this.maxTasks = maxTasks;
        this.minTasks = minTasks;
        this.creator = creator;
    }

    /**
     * Configure a receive task by associating it with this pool.
     *
     * @param task the task to configure
     */
    protected void configureTask(AbstractRxTask task) {
        synchronized (task) {
            task.setTaskPool(this);
        }
    }

    /**
     * Find an idle worker thread, if any. Could return null.
     *
     * @return a worker
     */
    public AbstractRxTask getRxTask() {
        AbstractRxTask worker = null;
        synchronized (mutex) {
            while (worker == null && running) {
                if (!idle.isEmpty()) {
                    try {
                        worker = idle.remove(0);
                    } catch (java.util.NoSuchElementException ignore) {
                        // Should never happen as access to idle is always synchronized on mutex
                    }
                } else if (used.size() < this.maxTasks && creator != null) {
                    worker = creator.createRxTask();
                    configureTask(worker);
                } else {
                    try {
                        mutex.wait();
                    } catch (InterruptedException x) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            if (worker != null) {
                used.add(worker);
            }
        }
        return worker;
    }

    /**
     * Return the number of idle tasks currently available in the pool.
     *
     * @return count of idle tasks
     */
    public int available() {
        synchronized (mutex) {
            return idle.size();
        }
    }

    /**
     * Called by the worker thread to return itself to the idle pool.
     *
     * @param worker The worker
     */
    public void returnWorker(AbstractRxTask worker) {
        if (running) {
            synchronized (mutex) {
                used.remove(worker);
                if (idle.size() < maxTasks && !idle.contains(worker)) {
                    idle.add(worker); // let max be the upper limit
                } else {
                    worker.close();
                }
                mutex.notifyAll();
            }
        } else {
            worker.close();
        }
    }

    /**
     * Return the maximum number of tasks allowed in the pool.
     *
     * @return maximum task count
     */
    public int getMaxThreads() {
        return maxTasks;
    }

    /**
     * Return the minimum number of tasks maintained in the pool.
     *
     * @return minimum task count
     */
    public int getMinThreads() {
        return minTasks;
    }

    /**
     * Stop the pool, closing all idle tasks and preventing new tasks from being acquired.
     */
    public void stop() {
        running = false;
        synchronized (mutex) {
            Iterator<AbstractRxTask> i = idle.iterator();
            while (i.hasNext()) {
                AbstractRxTask worker = i.next();
                returnWorker(worker);
                i.remove();
            }
        }
    }

    /**
     * Set the maximum number of tasks allowed in the pool.
     *
     * @param maxThreads maximum task count
     */
    public void setMaxTasks(int maxThreads) {
        this.maxTasks = maxThreads;
    }

    /**
     * Set the minimum number of tasks maintained in the pool.
     *
     * @param minThreads minimum task count
     */
    public void setMinTasks(int minThreads) {
        this.minTasks = minThreads;
    }

    /**
     * Return the task creator factory used by this pool.
     *
     * @return the task creator
     */
    public TaskCreator getTaskCreator() {
        return this.creator;
    }

    /**
     * Factory interface for creating receive tasks.
     */
    public interface TaskCreator {
        /**
         * Creates a new receive task.
         *
         * @return a new receive task instance
         */
        AbstractRxTask createRxTask();
    }
}

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


    public RxTaskPool(int maxTasks, int minTasks, TaskCreator creator) throws Exception {
        // fill up the pool with worker threads
        this.maxTasks = maxTasks;
        this.minTasks = minTasks;
        this.creator = creator;
    }

    protected void configureTask(AbstractRxTask task) {
        synchronized (task) {
            task.setTaskPool(this);
            // task.setName(task.getClass().getName() + "[" + inc() + "]");
            // task.setDaemon(true);
            // task.setPriority(Thread.MAX_PRIORITY);
            // task.start();
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
                if (idle.size() > 0) {
                    try {
                        worker = idle.remove(0);
                    } catch (java.util.NoSuchElementException x) {
                        // this means that there are no available workers
                        worker = null;
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
            } // while
            if (worker != null) {
                used.add(worker);
            }
        }
        return worker;
    }

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
                // if ( idle.size() < minThreads && !idle.contains(worker)) idle.add(worker);
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

    public int getMaxThreads() {
        return maxTasks;
    }

    public int getMinThreads() {
        return minTasks;
    }

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

    public void setMaxTasks(int maxThreads) {
        this.maxTasks = maxThreads;
    }

    public void setMinTasks(int minThreads) {
        this.minTasks = minThreads;
    }

    public TaskCreator getTaskCreator() {
        return this.creator;
    }

    public interface TaskCreator {
        AbstractRxTask createRxTask();
    }
}

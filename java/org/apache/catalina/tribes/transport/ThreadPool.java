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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author not attributable
 * @version 1.0
 */

public class ThreadPool
{
    /**
     * A very simple thread pool class.  The pool size is set at
     * construction time and remains fixed.  Threads are cycled
     * through a FIFO idle queue.
     */

    List idle = new LinkedList();
    List used = new LinkedList();
    
    Object mutex = new Object();
    boolean running = true;
    
    private static int counter = 1;
    private int maxThreads;
    private int minThreads;
    
    private ThreadCreator creator = null;

    private static synchronized int inc() {
        return counter++;
    }

    
    public ThreadPool (int maxThreads, int minThreads, ThreadCreator creator) throws Exception {
        // fill up the pool with worker threads
        this.maxThreads = maxThreads;
        this.minThreads = minThreads;
        this.creator = creator;
        //for (int i = 0; i < minThreads; i++) {
        for (int i = 0; i < maxThreads; i++) { //temporary fix for thread hand off problem
            WorkerThread thread = creator.getWorkerThread();
            setupThread(thread);
            idle.add (thread);
        }
    }
    
    protected void setupThread(WorkerThread thread) {
        synchronized (thread) {
            thread.setPool(this);
            thread.setName(thread.getClass().getName() + "[" + inc() + "]");
            thread.setDaemon(true);
            thread.setPriority(Thread.MAX_PRIORITY);
            thread.start();
            try {thread.wait(500); }catch ( InterruptedException x ) {}
        }
    }

    /**
     * Find an idle worker thread, if any.  Could return null.
     */
    public WorkerThread getWorker()
    {
        WorkerThread worker = null;
        synchronized (mutex) {
            while ( worker == null && running ) {
                if (idle.size() > 0) {
                    try {
                        worker = (WorkerThread) idle.remove(0);
                    } catch (java.util.NoSuchElementException x) {
                        //this means that there are no available workers
                        worker = null;
                    }
                } else if ( used.size() < this.maxThreads && creator != null) {
                    worker = creator.getWorkerThread();
                    setupThread(worker);
                } else {
                    try { mutex.wait(); } catch ( java.lang.InterruptedException x ) {Thread.currentThread().interrupted();}
                }
            }//while
            if ( worker != null ) used.add(worker);
        }
        return (worker);
    }
    
    public int available() {
        return idle.size();
    }

    /**
     * Called by the worker thread to return itself to the
     * idle pool.
     */
    public void returnWorker (WorkerThread worker) {
        if ( running ) {
            synchronized (mutex) {
                used.remove(worker);
                //if ( idle.size() < minThreads && !idle.contains(worker)) idle.add(worker);
                if ( idle.size() < maxThreads && !idle.contains(worker)) idle.add(worker); //let max be the upper limit
                else {
                    worker.setDoRun(false);
                    synchronized (worker){worker.notify();}
                }
                mutex.notify();
            }
        }else {
            worker.setDoRun(false);
            synchronized (worker){worker.notify();}
        }
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    public int getMinThreads() {
        return minThreads;
    }

    public void stop() {
        running = false;
        synchronized (mutex) {
            Iterator i = idle.iterator();
            while ( i.hasNext() ) {
                WorkerThread worker = (WorkerThread)i.next();
                returnWorker(worker);
                i.remove();
            }
        }
    }

    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
    }

    public void setMinThreads(int minThreads) {
        this.minThreads = minThreads;
    }

    public ThreadCreator getThreadCreator() {
        return this.creator;
    }
    
    public static interface ThreadCreator {
        public WorkerThread getWorkerThread();
    }
}

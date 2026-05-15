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

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * A Thread implementation that records the time at which it was created.
 */
public class TaskThread extends Thread {

    private static final Log log = LogFactory.getLog(TaskThread.class);
    private static final StringManager sm = StringManager.getManager(TaskThread.class);
    private final long creationTime;

    /**
     * Creates a new TaskThread with the specified parameters.
     *
     * @param group the thread group
     * @param target the runnable task
     * @param name the thread name
     */
    public TaskThread(ThreadGroup group, Runnable target, String name) {
        super(group, new WrappingRunnable(target), name);
        this.creationTime = System.currentTimeMillis();
    }

    /**
     * Creates a new TaskThread with the specified parameters and stack size.
     *
     * @param group the thread group
     * @param target the runnable task
     * @param name the thread name
     * @param stackSize the desired stack size
     */
    public TaskThread(ThreadGroup group, Runnable target, String name, long stackSize) {
        super(group, new WrappingRunnable(target), name, stackSize);
        this.creationTime = System.currentTimeMillis();
    }

    /**
     * Returns the time at which this thread was created.
     *
     * @return the creation time in milliseconds since the epoch
     */
    public final long getCreationTime() {
        return creationTime;
    }

    /**
     * Wraps a {@link Runnable} to swallow any {@link StopPooledThreadException} instead of letting it go and
     * potentially trigger a break in a debugger.
     */
    private record WrappingRunnable(Runnable wrappedRunnable) implements Runnable {
        @Override
        public void run() {
            try {
                wrappedRunnable.run();
            } catch (StopPooledThreadException exc) {
                // expected : we just swallow the exception to avoid disturbing debuggers like eclipse's
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("taskThread.exiting"), exc);
                }
            }
        }
    }

}

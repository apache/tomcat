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

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public class InlineExecutorService extends AbstractExecutorService {

    private volatile boolean shutdown;
    private volatile boolean taskRunning;
    private volatile boolean terminated;

    private final Object lock = new Object();

    @Override
    public void shutdown() {
        shutdown = true;
        synchronized (lock) {
            terminated = !taskRunning;
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown();
        return null;
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public boolean isTerminated() {
        return terminated;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        synchronized (lock) {
            if (terminated) {
                return true;
            }
            lock.wait(unit.toMillis(timeout));
            return terminated;
        }
    }

    @Override
    public void execute(Runnable command) {
        synchronized (lock) {
            if (shutdown) {
                throw new RejectedExecutionException();
            }
            taskRunning = true;
        }
        command.run();
        synchronized (lock) {
            taskRunning = false;
            if (shutdown) {
                terminated = true;
                lock.notifyAll();
            }
        }
    }
}

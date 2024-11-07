/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.util.threads;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.tomcat.util.res.StringManager;

/**
 * An executor that uses a new virtual thread for each task.
 */
public class VirtualThreadExecutor extends AbstractExecutorService {

    private static final StringManager sm = StringManager.getManager(VirtualThreadExecutor.class);

    private CountDownLatch shutdown = new CountDownLatch(1);

    private Thread.Builder threadBuilder;

    public VirtualThreadExecutor(String namePrefix) {
        threadBuilder = Thread.ofVirtual().name(namePrefix, 0);
    }

    @Override
    public void execute(Runnable command) {
        if (isShutdown()) {
            throw new RejectedExecutionException(
                    sm.getString("virtualThreadExecutor.taskRejected", command.toString(), this.toString()));
        }
        threadBuilder.start(command);
    }

    @Override
    public void shutdown() {
        shutdown.countDown();
    }

    /**
     * {@inheritDoc}
     * <p>
     * The VirtualThreadExecutor does not track in-progress tasks so calling this method is equivalent to calling
     * {@link #shutdown()}.
     */
    @Override
    public List<Runnable> shutdownNow() {
        shutdown();
        return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
        return shutdown.getCount() == 0;
    }

    /**
     * {@inheritDoc}
     * <p>
     * The VirtualThreadExecutor does not track in-progress tasks so calling this method is equivalent to calling
     * {@link #isShutdown()}.
     */
    @Override
    public boolean isTerminated() {
        return isShutdown();
    }

    /**
     * {@inheritDoc}
     * <p>
     * The VirtualThreadExecutor does not track in-progress tasks so calling this method is effectively waiting for
     * {@link #shutdown()} to be called.
     */
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return shutdown.await(timeout, unit);
    }
}

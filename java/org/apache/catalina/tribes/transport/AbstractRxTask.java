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

import org.apache.catalina.tribes.io.ListenCallback;

/**
 * Abstract base class for receive tasks in the Catalina Tribes framework.
 */
public abstract class AbstractRxTask implements Runnable {

    /** Option flag for using direct buffers. */
    public static final int OPTION_DIRECT_BUFFER = ReceiverBase.OPTION_DIRECT_BUFFER;

    /** The callback. */
    private ListenCallback callback;
    /** The task pool. */
    private RxTaskPool pool;
    @Deprecated
    private boolean doRun = true;
    /** The options. */
    private int options;
    /** Whether to use the buffer pool. */
    protected boolean useBufferPool = true;

    /**
     * Constructs a new AbstractRxTask.
     *
     * @param callback The callback
     */
    public AbstractRxTask(ListenCallback callback) {
        this.callback = callback;
    }

    /**
     * Sets the task pool.
     *
     * @param pool The task pool
     */
    public void setTaskPool(RxTaskPool pool) {
        this.pool = pool;
    }

    /**
     * Sets the options.
     *
     * @param options The options
     */
    public void setOptions(int options) {
        this.options = options;
    }

    /**
     * Sets the callback.
     *
     * @param callback The callback
     */
    public void setCallback(ListenCallback callback) {
        this.callback = callback;
    }

    /**
     * Sets doRun field which is unused.
     *
     * @param doRun New value
     *
     * @deprecated Will be removed in Tomcat 10
     */
    @Deprecated
    public void setDoRun(boolean doRun) {
        this.doRun = doRun;
    }

    /**
     * Gets the task pool.
     *
     * @return The task pool
     */
    public RxTaskPool getTaskPool() {
        return pool;
    }

    /**
     * Gets the options.
     *
     * @return The options
     */
    public int getOptions() {
        return options;
    }

    /**
     * Gets the callback.
     *
     * @return The callback
     */
    public ListenCallback getCallback() {
        return callback;
    }

    /**
     * Gets doRun field which is unused.
     *
     * @return Current field value
     *
     * @deprecated Will be removed in Tomcat 10
     */
    @Deprecated
    public boolean isDoRun() {
        return doRun;
    }

    /**
     * Closes this task.
     */
    public void close() {
        doRun = false;
    }

    /**
     * Sets whether to use the buffer pool.
     *
     * @param usebufpool {@code true} to use the buffer pool
     */
    public void setUseBufferPool(boolean usebufpool) {
        useBufferPool = usebufpool;
    }

    /**
     * Gets whether the buffer pool is being used.
     *
     * @return {@code true} if the buffer pool is being used
     */
    public boolean getUseBufferPool() {
        return useBufferPool;
    }
}

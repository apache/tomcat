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
package org.apache.catalina.tribes.group.interceptors;

/**
 * MBean interface for MessageDispatchInterceptor.
 */
public interface MessageDispatchInterceptorMBean {

    /**
     * Get the option flag.
     * @return the option flag
     */
    int getOptionFlag();

    /**
     * Check if always send is enabled.
     * @return whether always send is enabled
     */
    boolean isAlwaysSend();

    /**
     * Set always send.
     * @param alwaysSend whether to always send
     */
    void setAlwaysSend(boolean alwaysSend);

    /**
     * Get the maximum queue size.
     * @return the maximum queue size
     */
    long getMaxQueueSize();

    /**
     * Get the current queue size.
     * @return the current queue size
     */
    long getCurrentSize();

    /**
     * Get the keep alive time.
     * @return the keep alive time
     */
    long getKeepAliveTime();

    /**
     * Get the maximum spare threads.
     * @return the maximum spare threads
     */
    int getMaxSpareThreads();

    /**
     * Get the maximum threads.
     * @return the maximum threads
     */
    int getMaxThreads();

    // pool stats
    /**
     * Get the pool size.
     * @return the pool size
     */
    int getPoolSize();

    /**
     * Get the active count.
     * @return the active count
     */
    int getActiveCount();

    /**
     * Get the task count.
     * @return the task count
     */
    long getTaskCount();

    /**
     * Get the completed task count.
     * @return the completed task count
     */
    long getCompletedTaskCount();

}

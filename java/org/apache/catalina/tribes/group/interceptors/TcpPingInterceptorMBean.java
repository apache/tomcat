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
 * MBean interface for managing the TcpPingInterceptor.
 */
public interface TcpPingInterceptorMBean {

    /**
     * Returns the socket option flag used for ping messages.
     *
     * @return the option flag
     */
    int getOptionFlag();

    /**
     * Returns the ping interval in milliseconds.
     *
     * @return the ping interval
     */
    long getInterval();

    /**
     * Returns whether a dedicated ping thread is used.
     *
     * @return true if a dedicated thread is used
     */
    boolean getUseThread();

}

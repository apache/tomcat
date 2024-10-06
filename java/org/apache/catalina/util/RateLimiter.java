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

package org.apache.catalina.util;

import javax.servlet.FilterConfig;

public interface RateLimiter {

    /**
     * @return the actual duration of a time window in seconds
     */
    int getDuration();

    /**
     * sets the configured duration value in seconds
     *
     * @param duration
     */
    void setDuration(int duration);

    /**
     * @return the maximum number of requests allowed per time window
     */
    int getRequests();

    /**
     * sets the configured number of requests allowed per time window
     *
     * @param requests
     */
    void setRequests(int requests);

    /**
     * increments the number of requests by the given ipAddress in the current time window
     *
     * @param ipAddress the ip address
     * @return the new value after incrementing
     */
    int increment(String ipAddress);

    /**
     * cleanup no longer needed resources
     */
    void destroy();

    /**
     * pass the FilterConfig to configure the filter
     *
     * @param filterConfig
     */
    void setFilterConfig(FilterConfig filterConfig);
}

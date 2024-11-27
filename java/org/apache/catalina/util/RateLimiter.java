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

import jakarta.servlet.FilterConfig;

public interface RateLimiter {

    /**
     * @return the actual duration of a time window in seconds
     */
    int getDuration();

    /**
     * Sets the configured duration value in seconds.
     *
     * @param duration The duration of the time window in seconds
     */
    void setDuration(int duration);

    /**
     * @return the maximum number of requests allowed per time window
     */
    int getRequests();

    /**
     * Sets the configured number of requests allowed per time window.
     *
     * @param requests The number of requests per time window
     */
    void setRequests(int requests);

    /**
     * Increments the number of requests by the given ipAddress in the current time window.
     *
     * @param ipAddress the ip address
     *
     * @return the new value after incrementing
     */
    int increment(String ipAddress);

    /**
     * Cleanup no longer needed resources.
     */
    void destroy();

    /**
     * Pass the FilterConfig to configure the filter.
     *
     * @param filterConfig The FilterConfig used to configure the associated filter
     */
    void setFilterConfig(FilterConfig filterConfig);

    /**
     * @return name of RateLimit policy
     */
    String getPolicyName();

    /**
     * Sets the policy name, otherwise an auto-generated name is used.
     *
     * @param name of rate limit policy
     */
    void setPolicyName(String name);

    /**
     * @return full representation of current policy
     *
     * @see <a href="https://datatracker.ietf.org/doc/draft-ietf-httpapi-ratelimit-headers">RateLimit header fields for
     *          HTTP (draft)</a>
     */
    default String getPolicy() {
        // enclose policy name with double quotes. e.g. "fixed-01";q=3000;w=60
        return "\"" + getPolicyName() + "\";q=" + getRequests() + ";w=" + getDuration();
    }

    /**
     * Provide the quota header for this rate limit for a given request count within the current time window.
     *
     * @param requestCount  The request count within the current time window
     *
     * @return the quota header for the given value of request count
     *
     * @see <a href="https://datatracker.ietf.org/doc/draft-ietf-httpapi-ratelimit-headers">RateLimit header fields for
     *          HTTP (draft)</a>
     */
    default String getQuota(int requestCount) {
        // Local copy to ensure consistency
        int maxRequests = getRequests();
        int remaining = (requestCount < 0 || requestCount > maxRequests) ? 0 : (maxRequests - requestCount);
        return "\"" + getPolicyName() + "\";r=" + remaining;
    }
}

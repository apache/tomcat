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

/**
 * Rate limiter interface.
 * @see <a HREF=
 *          "https://www.ietf.org/archive/id/draft-ietf-httpapi-ratelimit-headers-08.html">ietf
 *          Ratelimit Headers Draft</a>
 */
public interface RateLimiter {

    /**
     * @return full representation of current policy
     * @see https://www.ietf.org/archive/id/draft-ietf-httpapi-ratelimit-headers-08.html#name-ratelimit-policy-field
     */
    default String getPolicy() {
        StringBuffer buf = new StringBuffer();
        buf.append('"').append(getPolicyName()).append('"').append(";q=").append(getRequests()).append(";w=")
                .append(getDuration());
        return buf.toString();
    }

    /**
     * @return policy name
     */
    String getPolicyName();

    /**
     * Sets the name of rate limit policy.
     * 
     * @param policy name of rate limiter
     */
    void setPolicyName(String name);

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
     * @param identifier of quota unit
     * 
     * @return the new value after incrementing
     */
    int increment(String identifier);

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
     * Minimum set of rate limit header fields.
     * 
     * @see <a HREF=
     *          "https://www.ietf.org/archive/id/draft-ietf-httpapi-ratelimit-headers-08.html#name-service-limit-item">ietf
     *          Ratelimit Headers Draft</a>
     */
    public static class RateLimitItem {
        private String policyName;
        private int remainingQuota;

        /**
         * @param name               of policy associated
         * @param remaining          quota units for the identified policy
         * @param timeLeftUntilReset number of seconds until the quota associated with the quota policy resets
         */
        public RateLimitItem(String policyName, int remainingQuota) {
            this.policyName = policyName;
            this.remainingQuota = remainingQuota;
        }

        @Override
        public String toString() {
            StringBuffer buf = new StringBuffer();
            buf.append('"').append(this.policyName).append('"').append(";r=").append(this.remainingQuota);
            return buf.toString();
        }
    }
}
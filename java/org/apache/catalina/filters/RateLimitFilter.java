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

package org.apache.catalina.filters;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.GenericFilter;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.util.TimeBucketCounter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.threads.ScheduledThreadPoolExecutor;

/**
 * <p>
 * Servlet filter that can help mitigate Denial of Service (DoS) and Brute Force attacks by limiting the number of a
 * requests that are allowed from a single IP address within a time window (also referred to as a time bucket), e.g. 300
 * Requests per 60 seconds.
 * </p>
 * <p>
 * The filter works by incrementing a counter in a time bucket for each IP address, and if the counter exceeds the
 * allowed limit then further requests from that IP are dropped with a &quot;429 Too many requests&quot; response until
 * the bucket time ends and a new bucket starts.
 * </p>
 * <p>
 * The filter is optimized for efficiency and low overhead, so it converts some configured values to more efficient
 * values. For example, a configuration of a 60 seconds time bucket is converted to 65.536 seconds. That allows for very
 * fast bucket calculation using bit shift arithmetic. In order to remain true to the user intent, the configured number
 * of requests is then multiplied by the same ratio, so a configuration of 100 Requests per 60 seconds, has the real
 * values of 109 Requests per 65 seconds.
 * </p>
 * <p>
 * It is common to set up different restrictions for different URIs. For example, a login page or authentication script
 * is typically expected to get far less requests than the rest of the application, so you can add a filter definition
 * that would allow only 5 requests per 15 seconds and map those URIs to it.
 * </p>
 * <p>
 * You can set <code>enforce</code> to <code>false</code> to disable the termination of requests that exceed the allowed
 * limit. Then your application code can inspect the Request Attribute
 * <code>org.apache.catalina.filters.RateLimitFilter.Count</code> and decide how to handle the request based on other
 * information that it has, e.g. allow more requests to certain users based on roles, etc.
 * </p>
 * <p>
 * <strong>WARNING:</strong> if Tomcat is behind a reverse proxy then you must make sure that the Rate Limit Filter sees
 * the client IP address, so if for example you are using the <a href="#Remote_IP_Filter">Remote IP Filter</a>, then the
 * filter mapping for the Rate Limit Filter must come <em>after</em> the mapping of the Remote IP Filter to ensure that
 * each request has its IP address resolved before the Rate Limit Filter is applied. Failure to do so will count
 * requests from different IPs in the same bucket and will result in a self inflicted DoS attack.
 * </p>
 */
public class RateLimitFilter extends GenericFilter {

    private static final long serialVersionUID = 1L;

    /**
     * default duration in seconds
     */
    public static final int DEFAULT_BUCKET_DURATION = 60;

    /**
     * default number of requests per duration
     */
    public static final int DEFAULT_BUCKET_REQUESTS = 300;

    /**
     * default value for enforce
     */
    public static final boolean DEFAULT_ENFORCE = true;

    /**
     * default status code to return if requests per duration exceeded
     */
    public static final int DEFAULT_STATUS_CODE = 429;

    /**
     * default status message to return if requests per duration exceeded
     */
    public static final String DEFAULT_STATUS_MESSAGE = "Too many requests";

    /**
     * request attribute that will contain the number of requests per duration
     */
    public static final String RATE_LIMIT_ATTRIBUTE_COUNT = "org.apache.catalina.filters.RateLimitFilter.Count";

    /**
     * init-param to set the bucket duration in seconds
     */
    public static final String PARAM_BUCKET_DURATION = "bucketDuration";

    /**
     * init-param to set the bucket number of requests
     */
    public static final String PARAM_BUCKET_REQUESTS = "bucketRequests";

    /**
     * init-param to set the enforce flag
     */
    public static final String PARAM_ENFORCE = "enforce";

    /**
     * init-param to set a custom status code if requests per duration exceeded
     */
    public static final String PARAM_STATUS_CODE = "statusCode";

    /**
     * init-param to set a custom status message if requests per duration exceeded
     */
    public static final String PARAM_STATUS_MESSAGE = "statusMessage";

    transient TimeBucketCounter bucketCounter;

    private int actualRequests;

    private int bucketRequests = DEFAULT_BUCKET_REQUESTS;

    private int bucketDuration = DEFAULT_BUCKET_DURATION;

    private boolean enforce = DEFAULT_ENFORCE;

    private int statusCode = DEFAULT_STATUS_CODE;

    private String statusMessage = DEFAULT_STATUS_MESSAGE;

    private transient Log log = LogFactory.getLog(RateLimitFilter.class);

    private static final StringManager sm = StringManager.getManager(RateLimitFilter.class);

    /**
     * @return the actual maximum allowed requests per time bucket
     */
    public int getActualRequests() {
        return actualRequests;
    }

    /**
     * @return the actual duration of a time bucket in milliseconds
     */
    public int getActualDurationInSeconds() {
        return bucketCounter.getActualDuration() / 1000;
    }

    @Override
    public void init() throws ServletException {

        FilterConfig config = getFilterConfig();

        String param;
        param = config.getInitParameter(PARAM_BUCKET_DURATION);
        if (param != null) {
            bucketDuration = Integer.parseInt(param);
        }

        param = config.getInitParameter(PARAM_BUCKET_REQUESTS);
        if (param != null) {
            bucketRequests = Integer.parseInt(param);
        }

        param = config.getInitParameter(PARAM_ENFORCE);
        if (param != null) {
            enforce = Boolean.parseBoolean(param);
        }

        param = config.getInitParameter(PARAM_STATUS_CODE);
        if (param != null) {
            statusCode = Integer.parseInt(param);
        }

        param = config.getInitParameter(PARAM_STATUS_MESSAGE);
        if (param != null) {
            statusMessage = param;
        }

        ScheduledExecutorService executorService =
                (ScheduledExecutorService) getServletContext().getAttribute(ScheduledThreadPoolExecutor.class.getName());
        if (executorService == null) {
            executorService = new java.util.concurrent.ScheduledThreadPoolExecutor(1);
        }
        bucketCounter = new TimeBucketCounter(bucketDuration, executorService);

        actualRequests = (int) Math.round(bucketCounter.getRatio() * bucketRequests);

        log.info(sm.getString("rateLimitFilter.initialized", super.getFilterName(), Integer.valueOf(bucketRequests),
                Integer.valueOf(bucketDuration), Integer.valueOf(getActualRequests()),
                Integer.valueOf(getActualDurationInSeconds()), (!enforce ? "Not " : "") + "enforcing"));
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        String ipAddr = request.getRemoteAddr();
        int reqCount = bucketCounter.increment(ipAddr);

        request.setAttribute(RATE_LIMIT_ATTRIBUTE_COUNT, Integer.valueOf(reqCount));

        if (enforce && (reqCount > actualRequests)) {

            ((HttpServletResponse) response).sendError(statusCode, statusMessage);
            log.warn(sm.getString("rateLimitFilter.maxRequestsExceeded", super.getFilterName(),
                    Integer.valueOf(reqCount), ipAddr, Integer.valueOf(getActualRequests()),
                    Integer.valueOf(getActualDurationInSeconds())));

            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        this.bucketCounter.destroy();
        super.destroy();
    }
}

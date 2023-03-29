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

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.GenericFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.util.TimeBucketCounter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

import java.io.IOException;

public class RateLimitFilter extends GenericFilter {

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
     * filter init-param to set the bucket duration in seconds
     */
    public static final String PARAM_BUCKET_DURATION = "ratelimit.bucket.duration";

    /**
     * filter init-param to set the bucket number of requests
     */
    public static final String PARAM_BUCKET_REQUESTS = "ratelimit.bucket.requests";

    /**
     * filter init-param to set the enforce flag
     */
    public static final String PARAM_ENFORCE = "ratelimit.enforce";

    /**
     * filter init-param to set a custom status code if requests per duration exceeded
     */
    public static final String PARAM_STATUS_CODE = "ratelimit.status.code";

    /**
     * filter init-param to set a custom status message if requests per duration exceeded
     */
    public static final String PARAM_STATUS_MESSAGE = "ratelimit.status.message";

    TimeBucketCounter bucketCounter;

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
        if (param != null)
            bucketDuration = Integer.parseInt(param);

        param = config.getInitParameter(PARAM_BUCKET_REQUESTS);
        if (param != null)
            bucketRequests = Integer.parseInt(param);

        param = config.getInitParameter(PARAM_ENFORCE);
        if (param != null)
            enforce = Boolean.parseBoolean(param);

        param = config.getInitParameter(PARAM_STATUS_CODE);
        if (param != null)
            statusCode = Integer.parseInt(param);

        param = config.getInitParameter(PARAM_STATUS_MESSAGE);
        if (param != null)
            statusMessage = param;

        bucketCounter = new TimeBucketCounter(bucketDuration);

        actualRequests = (int) Math.round(bucketCounter.getRatio() * bucketRequests);

        log.info(sm.getString("rateLimitFilter.initialized",
            super.getFilterName(), bucketRequests, bucketDuration, getActualRequests(),
            getActualDurationInSeconds(), (!enforce ? "Not " : "") + "enforcing")
        );
    }

    /**
     * The <code>doFilter</code> method of the Filter is called by the container
     * each time a request/response pair is passed through the chain due to a
     * client request for a resource at the end of the chain. The FilterChain
     * passed in to this method allows the Filter to pass on the request and
     * response to the next entity in the chain.
     * <p>
     * A typical implementation of this method would follow the following
     * pattern:- <br>
     * 1. Examine the request<br>
     * 2. Optionally wrap the request object with a custom implementation to
     * filter content or headers for input filtering <br>
     * 3. Optionally wrap the response object with a custom implementation to
     * filter content or headers for output filtering <br>
     * 4. a) <strong>Either</strong> invoke the next entity in the chain using
     * the FilterChain object (<code>chain.doFilter()</code>), <br>
     * 4. b) <strong>or</strong> not pass on the request/response pair to the
     * next entity in the filter chain to block the request processing<br>
     * 5. Directly set headers on the response after invocation of the next
     * entity in the filter chain.
     *
     * @param request  The request to process
     * @param response The response associated with the request
     * @param chain    Provides access to the next filter in the chain for this
     *                 filter to pass the request and response to for further
     *                 processing
     * @throws IOException      if an I/O error occurs during this filter's
     *                          processing of the request
     * @throws ServletException if the processing fails for any other reason
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                            throws IOException, ServletException {

        String ipAddr = request.getRemoteAddr();
        int reqCount = bucketCounter.increment(ipAddr);

        request.setAttribute(RATE_LIMIT_ATTRIBUTE_COUNT, reqCount);

        if (enforce && (reqCount > actualRequests)) {

            ((HttpServletResponse) response).sendError(statusCode, statusMessage);
            log.warn(sm.getString("rateLimitFilter.maxRequestsExceeded",
                super.getFilterName(), reqCount, ipAddr, getActualRequests(), getActualDurationInSeconds())
            );

            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Called by the web container to indicate to a filter that it is being
     * taken out of service. This method is only called once all threads within
     * the filter's doFilter method have exited or after a timeout period has
     * passed. After the web container calls this method, it will not call the
     * doFilter method again on this instance of the filter. <br>
     * <br>
     * <p>
     * This method gives the filter an opportunity to clean up any resources
     * that are being held (for example, memory, file handles, threads) and make
     * sure that any persistent state is synchronized with the filter's current
     * state in memory.
     * <p>
     * The default implementation is a NO-OP.
     */
    @Override
    public void destroy() {
        this.bucketCounter.destroy();
        super.destroy();
    }
}

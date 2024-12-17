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
package org.apache.catalina.valves;


import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.servlet.ServletException;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;


/**
 * This is a concrete implementation of {@link ValveBase} that enforces a limit on the number of HTTP request parameters.
 * The features of this implementation include:
 * <ul>
 * <li>URL-specific parameter limits that can be defined using regular expressions</li>
 * <li>Configurable through Tomcat's <code>server.xml</code> or <code>context.xml</code></li>
 * </ul>
 * <p>
 * The default limit, specified by Connector's value, applies to all requests unless a more specific
 * URL pattern is matched. URL patterns and their corresponding limits can be configured via a regular expression
 * mapping through the <code>urlPatternLimits</code> attribute.
 * </p>
 * <p>
 * The Valve checks each incoming request and enforces the appropriate limit. If a request exceeds the allowed number
 * of parameters, a <code>400 Bad Request</code> response is returned.
 * </p>
 * <p>
 * Example configuration in <code>context.xml</code>:
 * <pre>
 * {@code
 * <Context>
 *     <Valve className="org.apache.catalina.valves.ParameterLimitValve"
 *            urlPatternLimits="/api/.*=150,/admin/.*=50" />
 * </Context>
 * }
 * </pre>
 * <p>
 * The configuration allows for flexible control over different sections of your application, such as applying higher
 * limits for API endpoints and stricter limits for admin areas.
 * </p>
 *
 * @author Dimitris Soumis
 */

public class ParameterLimitValve extends ValveBase {

    /**
     * Map for URL-specific limits
     */
    private final Map<Pattern, Integer> urlPatternLimits = new HashMap<>();

    /**
     * Set the mapping of URL patterns to their corresponding parameter limits. The format of the input string should
     * be a comma-separated list of URL pattern and parameter limit pairs, where the pattern is a regular expression.
     * <p>
     * Example: <code>/api/.*=150,/admin/.*=50</code>
     *
     * @param urlPatternConfig A string containing URL pattern to parameter limit mappings, in the format "pattern=limit"
     */

    public void setUrlPatternLimits(String urlPatternConfig) {
        String[] urlLimitPairs = urlPatternConfig.split(",");
        for (String pair : urlLimitPairs) {
            String[] urlLimit = pair.split("=");
            Pattern pattern = Pattern.compile(urlLimit[0]);
            int limit = Integer.parseInt(urlLimit[1]);
            urlPatternLimits.put(pattern, Integer.valueOf(limit));
        }
    }

    /**
     * Invoke the next Valve in the sequence. Before invoking, checks if any of the defined patterns
     * matches the URI of the request and if it does, enforces the corresponding parameter limit for the request.
     *
     * @param request  The servlet request to be processed
     * @param response The servlet response to be created
     *
     * @exception IOException      if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        String requestURI = request.getRequestURI();

        // Iterate over the URL patterns and apply corresponding limits
        for (Map.Entry<Pattern, Integer> entry : urlPatternLimits.entrySet()) {
            Pattern pattern = entry.getKey();
            int limit = entry.getValue().intValue();

            if (pattern.matcher(requestURI).matches()) {
                request.setMaxParameterCount(limit);
                break;
            }
        }

        // Continue processing the request
        getNext().invoke(request, response);
    }
}

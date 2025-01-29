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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import jakarta.servlet.ServletException;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.UDecoder;
import org.apache.tomcat.util.file.ConfigFileLoader;
import org.apache.tomcat.util.file.ConfigurationSource;


/**
 * This is a concrete implementation of {@link ValveBase} that enforces a limit on the number of HTTP request parameters.
 * The features of this implementation include:
 * <ul>
 * <li>URL-specific parameter limits that can be defined using regular expressions</li>
 * <li>Configurable through Tomcat's <code>server.xml</code> or <code>context.xml</code></li>
 * <li> Requires a <code>parameter_limit.config</code> file containing the URL-specific parameter limits.
 * It must be placed in the Host configuration folder or in the WEB-INF folder of the web application.</li>
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
 * Example, configuration in <code>context.xml</code>:
 * <pre>
 * {@code
 * <Context>
 *     <Valve className="org.apache.catalina.valves.ParameterLimitValve"
 * </Context>
 * }
 * and in <code>parameter_limit.config</code>:
 * </pre>
 * <pre>
 * {@code
 * /api/.*=150
 * /admin/.*=50
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
    protected Map<Pattern, Integer> urlPatternLimits = new ConcurrentHashMap<>();

    /**
     * Relative path to the configuration file. Note: If the valve's container is a context, this will be relative to
     * /WEB-INF/.
     */
    protected String resourcePath = "parameter_limit.config";

    /**
     * Will be set to true if the valve is associated with a context.
     */
    protected boolean context = false;

    public ParameterLimitValve() {
        super(true);
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public void setResourcePath(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();
        containerLog = LogFactory.getLog(getContainer().getLogName() + ".parameterLimit");
    }

    @Override
    protected void startInternal() throws LifecycleException {

        super.startInternal();

        InputStream is = null;

        // Process configuration file for this valve
        if (getContainer() instanceof Context) {
            context = true;
            String webInfResourcePath = "/WEB-INF/" + resourcePath;
            is = ((Context) getContainer()).getServletContext().getResourceAsStream(webInfResourcePath);
            if (containerLog.isDebugEnabled()) {
                if (is == null) {
                    containerLog.debug(sm.getString("parameterLimitValve.noConfiguration", webInfResourcePath));
                } else {
                    containerLog.debug(sm.getString("parameterLimitValve.readConfiguration", webInfResourcePath));
                }
            }
        } else {
            String resourceName = Container.getConfigPath(getContainer(), resourcePath);
            try {
                ConfigurationSource.Resource resource = ConfigFileLoader.getSource().getResource(resourceName);
                is = resource.getInputStream();
            } catch (IOException e) {
                if (containerLog.isDebugEnabled()) {
                    containerLog.debug(sm.getString("parameterLimitValve.noConfiguration", resourceName), e);
                }
            }
        }

        if (is == null) {
            // Will use management operations to configure the valve dynamically
            return;
        }

        try (InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr)) {
            setUrlPatternLimits(reader);
        } catch (IOException ioe) {
            containerLog.error(sm.getString("parameterLimitValve.closeError"), ioe);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                containerLog.error(sm.getString("parameterLimitValve.closeError"), e);
            }
        }

    }

    public void setUrlPatternLimits(String urlPatternConfig) {
        urlPatternLimits.clear();
        setUrlPatternLimits(new BufferedReader(new StringReader(urlPatternConfig)));
    }

    /**
     * Set the mapping of URL patterns to their corresponding parameter limits.
     * The input should be provided line by line, where each line contains a pattern and a limit, separated by the last '='.
     * <p>
     * Example:
     * <pre>
     * /api/.*=50
     * /api======/.*=150
     * /urlEncoded%20api=2
     * # This is a comment
     * </pre>
     *
     * @param reader A BufferedReader containing URL pattern to parameter limit mappings, with each pair on a separate line.
     */
    public void setUrlPatternLimits(BufferedReader reader) {
        if (containerLog == null && getContainer() != null) {
            containerLog = LogFactory.getLog(getContainer().getLogName() + ".parameterLimit");
        }
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                // Trim whitespace from the line
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    // Skip empty lines or comments
                    continue;
                }

                int lastEqualsIndex = line.lastIndexOf('=');
                if (lastEqualsIndex == -1) {
                    throw new IllegalArgumentException(sm.getString("parameterLimitValve.invalidLine", line));
                }

                String patternString = line.substring(0, lastEqualsIndex).trim();
                String limitString = line.substring(lastEqualsIndex + 1).trim();

                Pattern pattern = Pattern.compile(UDecoder.URLDecode(patternString, StandardCharsets.UTF_8));
                int limit = Integer.parseInt(limitString);
                if (containerLog != null && containerLog.isTraceEnabled()) {
                    containerLog.trace("Add pattern " + pattern + " and limit " + limit);
                }
                urlPatternLimits.put(pattern, Integer.valueOf(limit));
            }
        } catch (IOException e) {
            containerLog.error(sm.getString("parameterLimitValve.readError"), e);
        }
    }

    @Override
    protected void stopInternal() throws LifecycleException {
        super.stopInternal();
        urlPatternLimits.clear();
    }

    /**
     * Checks if any of the defined patterns matches the URI of the request and if it does,
     * enforces the corresponding parameter limit for the request. Then invoke the next Valve in the sequence.
     *
     * @param request  The servlet request to be processed
     * @param response The servlet response to be created
     *
     * @exception IOException      if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {

        if (urlPatternLimits == null || urlPatternLimits.isEmpty()) {
            getNext().invoke(request, response);
            return;
        }

        String requestURI = context ? request.getRequestPathMB().toString() : request.getDecodedRequestURI();

        // Iterate over the URL patterns and apply corresponding limits
        for (Map.Entry<Pattern, Integer> entry : urlPatternLimits.entrySet()) {
            Pattern pattern = entry.getKey();
            int limit = entry.getValue().intValue();

            if (pattern.matcher(requestURI).matches()) {
                request.setMaxParameterCount(limit);
                break;
            }
        }

        // Invoke the next valve to continue processing the request
        getNext().invoke(request, response);
    }

}

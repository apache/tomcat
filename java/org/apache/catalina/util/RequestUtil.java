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

import java.net.URL;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.catalina.connector.Request;

/**
 * General purpose request parsing and encoding utility methods.
 */
public final class RequestUtil {
    /**
     * Default constructor.
     */
    public RequestUtil() {
    }

    /**
     * Build an appropriate return value for {@link HttpServletRequest#getRequestURL()} based on the provided request
     * object. Note that this will also work for instances of {@link jakarta.servlet.http.HttpServletRequestWrapper}.
     *
     * @param request The request object for which the URL should be built
     *
     * @return The request URL for the given request object
     */
    public static StringBuffer getRequestURL(HttpServletRequest request) {
        StringBuffer url = new StringBuffer();
        String scheme = request.getScheme();
        int port = request.getServerPort();
        if (port < 0) {
            // Work around java.net.URL bug
            port = 80;
        }

        url.append(scheme);
        url.append("://");
        url.append(request.getServerName());
        if ((scheme.equals("http") && (port != 80)) || (scheme.equals("https") && (port != 443))) {
            url.append(':');
            url.append(port);
        }
        url.append(request.getRequestURI());

        return url;
    }


    /**
     * Strip parameters for given path.
     *
     * @param input   the input path
     * @param request the request to add the parameters to
     *
     * @return the cleaned path
     */
    public static String stripPathParams(String input, Request request) {
        // Shortcut
        if (input.indexOf(';') < 0) {
            return input;
        }

        StringBuilder sb = new StringBuilder(input.length());
        int pos = 0;
        int limit = input.length();
        while (pos < limit) {
            int nextSemiColon = input.indexOf(';', pos);
            if (nextSemiColon < 0) {
                nextSemiColon = limit;
            }
            sb.append(input, pos, nextSemiColon);
            int followingSlash = input.indexOf('/', nextSemiColon);
            if (followingSlash < 0) {
                pos = limit;
            } else {
                pos = followingSlash;
            }
            if (request != null && nextSemiColon + 1 < pos) {
                String pathVariablesString = input.substring(nextSemiColon + 1, pos);
                String[] pathVariables = pathVariablesString.split(";");
                for (String pathVariable : pathVariables) {
                    int equals = pathVariable.indexOf('=');
                    if (equals > -1 && equals + 1 < pathVariable.length()) {
                        String name = pathVariable.substring(0, equals);
                        String value = pathVariable.substring(equals + 1);
                        request.addPathParameter(name, value);
                    }
                }
            }
        }

        return sb.toString();
    }


    /**
     * Tests whether the provided URL is for a resource contained within the same web application as the request.
     *
     * @param request The request to test
     * @param url     The URL to test
     *
     * @return {@code true} if the provided URL is for a resource contained within the same web application as the
     * request, otherwise {@code false}
     */
    public static boolean isSameWebApplication(HttpServletRequest request, URL url) {
        // Does this URL match down to (and including) the context path?
        if (!request.getScheme().equalsIgnoreCase(url.getProtocol())) {
            return false;
        }
        if (!request.getServerName().equalsIgnoreCase(url.getHost())) {
            return false;
        }
        int serverPort = request.getServerPort();
        if (serverPort == -1) {
            if ("https".equals(request.getScheme())) {
                serverPort = 443;
            } else {
                serverPort = 80;
            }
        }
        int urlPort = url.getPort();
        if (urlPort == -1) {
            if ("https".equals(url.getProtocol())) {
                urlPort = 443;
            } else {
                urlPort = 80;
            }
        }
        if (serverPort != urlPort) {
            return false;
        }

        /*
         * This isn't perfect but is the best that can be done without running the full mapping logic on the url to
         * determine which web application that url will map to.
         */
        if (!url.getPath().startsWith(request.getServletContext().getContextPath())) {
            return false;
        }

        return true;
    }
}

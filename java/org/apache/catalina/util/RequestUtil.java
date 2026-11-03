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
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import javax.servlet.http.HttpServletRequest;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.apache.tomcat.util.buf.UDecoder;
import org.apache.tomcat.util.res.StringManager;

/**
 * General purpose request parsing and encoding utility methods.
 */
public final class RequestUtil {

    private static final StringManager sm = StringManager.getManager(RequestUtil.class);

    /**
     * Default constructor.
     */
    public RequestUtil() {
    }

    /**
     * Build an appropriate return value for {@link HttpServletRequest#getRequestURL()} based on the provided request
     * object. Note that this will also work for instances of {@link javax.servlet.http.HttpServletRequestWrapper}.
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
     *             request, otherwise {@code false}
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
         * May not be perfect, but make a best efforts attempt to determine whether the URL belongs to current request
         * or not.
         *
         * Stripping of path parameters, decoding and normalization should all be unnecessary but are included here as
         * hardening against the application using untrusted data when constructing the URL.
         */
        String urlPath = url.getPath();
        urlPath = stripPathParams(urlPath, null);
        urlPath = UDecoder.URLDecode(urlPath, StandardCharsets.UTF_8);
        urlPath = org.apache.tomcat.util.http.RequestUtil.normalize(urlPath);
        if (urlPath == null) {
            // Normalization failed. Path tried to escape the root.
            return false;
        }

        // Context path will not end with "/"
        String requestContextPath = request.getServletContext().getContextPath();

        if (urlPath.equals(requestContextPath) || urlPath.startsWith(requestContextPath + "/")) {
            return true;
        }

        return false;
    }


    /**
     * Behaves the same way as {@link HttpServletRequest#getHeader(String)} but with the addition that, if multiple
     * headers of the specified name are present, the values are concatenated (with commas) before returning a single
     * combined value.
     *
     * @param request    The request from which the header value(s) should be retrieved
     * @param headerName The name of the HTTP header for which the merged value should be obtained
     *
     * @return The merged value for the given HTTP header.
     */
    public static String getMergedHeaderValue(HttpServletRequest request, String headerName) {
        Enumeration<String> values = request.getHeaders(headerName);
        if (!values.hasMoreElements()) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        result.append(values.nextElement());
        while (values.hasMoreElements()) {
            result.append(',');
            result.append(values.nextElement());
        }
        return result.toString();
    }


    public static String getContextPathUndecoded(Request request) {

        int lastSlash = request.getMappingData().contextSlashCount;
        // Special case handling for the root context
        if (lastSlash == 0) {
            return "";
        }

        String uri = request.getRequestURI();
        Context context = request.getContext();
        String canonicalContextPath = request.getServletContext().getContextPath();
        Connector connector = request.getConnector();
        EncodedSolidusHandling encodedSolidusHandling = connector.getEncodedSolidusHandlingInternal();

        int pos = 0;
        if (!context.getAllowMultipleLeadingForwardSlashInPath()) {
            // Ensure that the returned value only starts with a single '/'.
            // This prevents the value being misinterpreted as a protocol-relative
            // URI if used with sendRedirect().
            do {
                pos++;
            } while (pos < uri.length() && uri.charAt(pos) == '/');
            pos--;
            uri = uri.substring(pos);
        }

        char[] uriChars = uri.toCharArray();
        // Need at least the number of slashes in the context path
        while (lastSlash > 0) {
            pos = nextSlash(uriChars, pos + 1, encodedSolidusHandling);
            if (pos == -1) {
                break;
            }
            lastSlash--;
        }
        // Now allow for path parameters, normalization and/or encoding.
        // Essentially, keep extending the candidate path up to the next slash
        // until the decoded and normalized candidate path (with the path
        // parameters removed) is the same as the canonical path.
        String candidate;
        if (pos == -1) {
            candidate = uri;
        } else {
            candidate = uri.substring(0, pos);
        }
        candidate = removePathParameters(candidate);
        candidate = UDecoder.URLDecode(candidate, connector.getURICharset());
        candidate = org.apache.tomcat.util.http.RequestUtil.normalize(candidate);
        boolean match = canonicalContextPath.equals(candidate);
        while (!match && pos != -1) {
            pos = nextSlash(uriChars, pos + 1, encodedSolidusHandling);
            if (pos == -1) {
                candidate = uri;
            } else {
                candidate = uri.substring(0, pos);
            }
            candidate = removePathParameters(candidate);
            candidate = UDecoder.URLDecode(candidate, connector.getURICharset());
            candidate = org.apache.tomcat.util.http.RequestUtil.normalize(candidate);
            match = canonicalContextPath.equals(candidate);
        }
        if (match) {
            if (pos == -1) {
                return uri;
            } else {
                return uri.substring(0, pos);
            }
        } else {
            // Should never happen
            throw new IllegalStateException(
                    sm.getString("requestUtil.getContextPath.ise", canonicalContextPath, uri));
        }
    }


    private static String removePathParameters(String input) {
        int nextSemiColon = input.indexOf(';');
        // Shortcut
        if (nextSemiColon == -1) {
            return input;
        }
        StringBuilder result = new StringBuilder(input.length());
        result.append(input, 0, nextSemiColon);
        while (true) {
            int nextSlash = input.indexOf('/', nextSemiColon);
            if (nextSlash == -1) {
                break;
            }
            nextSemiColon = input.indexOf(';', nextSlash);
            if (nextSemiColon == -1) {
                result.append(input.substring(nextSlash));
                break;
            } else {
                result.append(input, nextSlash, nextSemiColon);
            }
        }

        return result.toString();
    }


    private static int nextSlash(char[] uri, int startPos, EncodedSolidusHandling encodedSolidusHandling) {
        int len = uri.length;
        int pos = startPos;
        while (pos < len) {
            if (uri[pos] == '/') {
                return pos;
            } else if (encodedSolidusHandling == EncodedSolidusHandling.DECODE &&
                    uri[pos] == '%' && pos + 2 < len && uri[pos + 1] == '2' &&
                    (uri[pos + 2] == 'f' || uri[pos + 2] == 'F')) {
                return pos;
            }
            pos++;
        }
        return -1;
    }
}

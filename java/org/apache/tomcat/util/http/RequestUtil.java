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
package org.apache.tomcat.util.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;

/**
 * Utility methods for HTTP request processing.
 */
public class RequestUtil {

    /**
     * Hide default constructor as this is a utility class.
     */
    private RequestUtil() {
        // Hide default constructor as this is a utility class
    }


    /**
     * Normalize a relative URI path. This method normalizes "/./", "/../", "//" and "\". If the input path is an
     * attempt to 'escape the root' (e.g. /../input.txt) then {@code null} is returned to prevent attempts to 'escape
     * the root'. URI paths containing null bytes will be rejected.
     *
     * @param path Relative path to be normalized
     *
     * @return The normalized path or {@code null} if the input path attempts to 'escape the root'.
     */
    public static String normalize(String path) {
        return normalize(path, true);
    }


    /**
     * Normalize a relative URI path. This method normalizes "/./", "/../" and "//". This method optionally normalizes
     * "\". If the input path is an attempt to 'escape the root' (e.g. /../input.txt) then {@code null} is returned to
     * prevent attempts to 'escape the root'. URI paths containing null bytes will be rejected.
     *
     * @param path             Relative path to be normalized
     * @param replaceBackSlash Should '\\' be normalized to '/'
     *
     * @return The normalized path or {@code null} if the input path attempts to 'escape the root'.
     */
    public static String normalize(String path, boolean replaceBackSlash) {

        // Keep behaviour aligned with CoyoteAdapter.normalize()
        if (path == null) {
            return null;
        }

        // Reject paths containing null bytes
        if (path.indexOf(0) > -1) {
            return null;
        }

        // Create a place for the normalized path
        String normalized = path;

        if (replaceBackSlash && normalized.indexOf('\\') >= 0) {
            normalized = normalized.replace('\\', '/');
        }

        // Add a leading "/" if necessary
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }

        boolean addedTrailingSlash = false;
        if (normalized.endsWith("/.") || normalized.endsWith("/..")) {
            normalized = normalized + "/";
            addedTrailingSlash = true;
        }

        // Resolve occurrences of "//" in the normalized path
        // Single pass so that a long run of '/' is not O(n^2)
        int doubleSlash = normalized.indexOf("//");
        if (doubleSlash >= 0) {
            StringBuilder sb = new StringBuilder(normalized.length());
            sb.append(normalized, 0, doubleSlash + 1);
            for (int i = doubleSlash + 2; i < normalized.length(); i++) {
                char c = normalized.charAt(i);
                if (c == '/' && sb.charAt(sb.length() - 1) == '/') {
                    continue;
                }
                sb.append(c);
            }
            normalized = sb.toString();
        }

        // Resolve occurrences of "/./" and "/../" in the normalized path. Single pass over the path segments so that
        // many such segments are not O(n^2). At this point there are no empty segments except after a trailing '/'.
        if (normalized.contains("/./") || normalized.contains("/../")) {
            int length = normalized.length();
            StringBuilder sb = new StringBuilder(length);
            int start = 1;
            while (start <= length) {
                int end = normalized.indexOf('/', start);
                if (end < 0) {
                    end = length;
                }
                int segmentLength = end - start;
                if (segmentLength == 0) {
                    // Only occurs for the trailing '/'
                    sb.append('/');
                } else if (segmentLength == 1 && normalized.charAt(start) == '.') {
                    // "/./" - ignore
                } else if (segmentLength == 2 && normalized.charAt(start) == '.' &&
                        normalized.charAt(start + 1) == '.') {
                    if (sb.length() == 0) {
                        return null; // Trying to go outside our context
                    }
                    sb.setLength(sb.lastIndexOf("/"));
                } else {
                    sb.append('/').append(normalized, start, end);
                }
                start = end + 1;
            }
            normalized = sb.toString();
        }

        if (normalized.length() > 1 && addedTrailingSlash) {
            // Remove the trailing '/' we added to that input and output are
            // consistent w.r.t. to the presence of the trailing '/'.
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        // Return the normalized path that we have completed
        return normalized;
    }


    /**
     * Check if the given origin matches the origin of the request.
     *
     * @param request The HTTP servlet request
     * @param origin  The origin to check
     * @return True if the origin matches the request's origin
     */
    public static boolean isSameOrigin(HttpServletRequest request, String origin) {
        // Build scheme://host:port from request
        StringBuilder target = new StringBuilder();
        String scheme = request.getScheme();
        String host = request.getServerName();
        if (scheme == null || host == null) {
            return false;
        }
        scheme = scheme.toLowerCase(Locale.ENGLISH);
        target.append(scheme).append("://").append(host);

        int port = request.getServerPort();
        // Origin may or may not include the (default) port.
        // At this point target doesn't include a port.
        if (target.length() == origin.length()) {
            // origin and target can only be equal if both are using default
            // ports. Therefore, only append the port to the target if a
            // non-default port is used.
            if (("http".equals(scheme) || "ws".equals(scheme)) && port != 80 ||
                    ("https".equals(scheme) || "wss".equals(scheme)) && port != 443) {
                target.append(':');
                target.append(port);
            }
        } else {
            // origin and target can only be equal if:
            // a) origin includes an explicit default port
            // b) origin is using a non-default port
            // Either way, add the port to the target so it can be compared
            target.append(':');
            target.append(port);
        }


        // Both scheme and host are case-insensitive and the CORS same origin check (from the Fetch/CORS
        // specification) compares them case-insensitively.
        return origin.contentEquals(target);
    }


    /**
     * Checks if a given origin is valid or not. Criteria:
     * <ul>
     * <li>If an encoded character is present in origin, it's not valid.</li>
     * <li>If origin is "null", it's valid.</li>
     * <li>Origin should be a valid {@link URI}</li>
     * </ul>
     *
     * @param origin The origin URI
     *
     * @return <code>true</code> if the origin was valid
     *
     * @see <a href="http://tools.ietf.org/html/rfc6454">RFC 6454</a>
     * @see <a href="http://tools.ietf.org/html/rfc3986">RFC 3986</a>
     */
    public static boolean isValidOrigin(String origin) {
        // Checks for encoded characters. Helps prevent CRLF injection.
        if (origin.contains("%")) {
            return false;
        }

        // "null" is a valid origin
        if ("null".equals(origin)) {
            return true;
        }

        // RFC6454, section 4. "If uri-scheme is file, the implementation MAY
        // return an implementation-defined value.". No limits are placed on
        // that value so treat all file URIs as valid origins.
        if (origin.startsWith("file://")) {
            return true;
        }

        URI originURI;
        try {
            originURI = new URI(origin);
        } catch (URISyntaxException e) {
            return false;
        }
        // If scheme for URI is null, return false. Return true otherwise.
        return originURI.getScheme() != null;

    }
}

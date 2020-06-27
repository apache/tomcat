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

import jakarta.servlet.http.HttpServletRequest;

public class RequestUtil {

    private RequestUtil() {
        // Hide default constructor as this is a utility class
    }


    /**
     * Normalize a relative URI path that may have relative values ("/./",
     * "/../", and so on ) it it.  <strong>WARNING</strong> - This method is
     * useful only for normalizing application-generated paths.  It does not
     * try to perform security checks for malicious input.
     *
     * @param path Relative path to be normalized
     *
     * @return The normalized path or <code>null</code> if the path cannot be
     *         normalized
     */
    public static String normalize(String path) {
        return normalize(path, true);
    }


    /**
     * Normalize a relative URI path that may have relative values ("/./",
     * "/../", and so on ) it it.  <strong>WARNING</strong> - This method is
     * useful only for normalizing application-generated paths.  It does not
     * try to perform security checks for malicious input.
     *
     * @param path Relative path to be normalized
     * @param replaceBackSlash Should '\\' be replaced with '/'
     *
     * @return The normalized path or <code>null</code> if the path cannot be
     *         normalized
     */
    public static String normalize(String path, boolean replaceBackSlash) {

        if (path == null) {
            return null;
        }

        // Create a place for the normalized path
        String normalized = path;

        if (replaceBackSlash && normalized.indexOf('\\') >= 0)
            normalized = normalized.replace('\\', '/');

        // Add a leading "/" if necessary
        if (!normalized.startsWith("/"))
            normalized = "/" + normalized;

        boolean addedTrailingSlash = false;
        if (normalized.endsWith("/.") || normalized.endsWith("/..")) {
            normalized = normalized + "/";
            addedTrailingSlash = true;
        }

        // Resolve occurrences of "//" in the normalized path
        while (true) {
            int index = normalized.indexOf("//");
            if (index < 0) {
                break;
            }
            normalized = normalized.substring(0, index) + normalized.substring(index + 1);
        }

        // Resolve occurrences of "/./" in the normalized path
        while (true) {
            int index = normalized.indexOf("/./");
            if (index < 0) {
                break;
            }
            normalized = normalized.substring(0, index) + normalized.substring(index + 2);
        }

        // Resolve occurrences of "/../" in the normalized path
        while (true) {
            int index = normalized.indexOf("/../");
            if (index < 0) {
                break;
            }
            if (index == 0) {
                return null;  // Trying to go outside our context
            }
            int index2 = normalized.lastIndexOf('/', index - 1);
            normalized = normalized.substring(0, index2) + normalized.substring(index + 3);
        }

        if (normalized.length() > 1 && addedTrailingSlash) {
            // Remove the trailing '/' we added to that input and output are
            // consistent w.r.t. to the presence of the trailing '/'.
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        // Return the normalized path that we have completed
        return normalized;
    }


    public static boolean isSameOrigin(HttpServletRequest request, String origin) {
        // Build scheme://host:port from request
        StringBuilder target = new StringBuilder();
        String scheme = request.getScheme();
        if (scheme == null) {
            return false;
        } else {
            scheme = scheme.toLowerCase(Locale.ENGLISH);
        }
        target.append(scheme);
        target.append("://");

        String host = request.getServerName();
        if (host == null) {
            return false;
        }
        target.append(host);

        int port = request.getServerPort();
        // Origin may or may not include the (default) port.
        // At this point target doesn't include a port.
        if (target.length() == origin.length()) {
            // origin and target can only be equal if both are using default
            // ports. Therefore only append the port to the target if a
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


        // Both scheme and host are case-insensitive but the CORS spec states
        // this check should be case-sensitive
        return origin.equals(target.toString());
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
     * @return <code>true</code> if the origin was valid
     * @see <a href="http://tools.ietf.org/html/rfc952">RFC952</a>
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

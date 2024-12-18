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

import jakarta.servlet.ServletRequest;

import org.apache.catalina.Globals;
import org.apache.tomcat.util.descriptor.web.FilterMap;

/**
 * General purpose utility methods related to filters and filter processing.
 */
public class FilterUtil {

    private FilterUtil() {
        // Utility class. Hide default constructor.
    }


    /**
     * Tests if the provided, context-relative, request path matches the provided filter mapping.
     *
     * @param filterMap   Filter mapping being checked
     * @param requestPath Context-relative request path of this request
     *
     * @return <code>true</code> if the context-relative request path matches the requirements of the specified filter
     *             mapping; otherwise, return <code>false</code>.
     */
    public static boolean matchFiltersURL(FilterMap filterMap, String requestPath) {

        // Check the specific "*" special URL pattern, which also matches
        // named dispatches
        if (filterMap.getMatchAllUrlPatterns()) {
            return true;
        }

        if (requestPath == null) {
            return false;
        }

        // Match on context relative request path
        String[] testPaths = filterMap.getURLPatterns();

        for (String testPath : testPaths) {
            if (matchFiltersURL(testPath, requestPath)) {
                return true;
            }
        }

        // No match
        return false;
    }


    /**
     * Return <code>true</code> if the context-relative request path matches the requirements of the specified filter
     * mapping; otherwise, return <code>false</code>.
     *
     * @param testPath    URL mapping being checked
     * @param requestPath Context-relative request path of this request
     */
    private static boolean matchFiltersURL(String testPath, String requestPath) {

        if (testPath == null) {
            return false;
        }

        // Case 1 - Exact Match
        if (testPath.equals(requestPath)) {
            return true;
        }

        // Case 2 - Path Match ("/.../*")
        if (testPath.equals("/*")) {
            return true;
        }
        if (testPath.endsWith("/*")) {
            if (testPath.regionMatches(0, requestPath, 0, testPath.length() - 2)) {
                if (requestPath.length() == (testPath.length() - 2)) {
                    return true;
                } else if ('/' == requestPath.charAt(testPath.length() - 2)) {
                    return true;
                }
            }
            return false;
        }

        // Case 3 - Extension Match
        if (testPath.startsWith("*.")) {
            int slash = requestPath.lastIndexOf('/');
            int period = requestPath.lastIndexOf('.');
            if ((slash >= 0) && (period > slash) && (period != requestPath.length() - 1) &&
                    ((requestPath.length() - period) == (testPath.length() - 1))) {
                return testPath.regionMatches(2, requestPath, period + 1, testPath.length() - 2);
            }
        }

        // Case 4 - "Default" Match
        return false; // NOTE - Not relevant for selecting filters
    }


    public static String getRequestPath(ServletRequest request) {
        String result = null;
        Object attribute = request.getAttribute(Globals.DISPATCHER_REQUEST_PATH_ATTR);
        if (attribute != null) {
            result = attribute.toString();
        }
        return result;
    }
}

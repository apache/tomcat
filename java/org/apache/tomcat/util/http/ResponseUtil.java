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

import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import org.apache.tomcat.util.http.parser.Vary;

public class ResponseUtil {

    private static final String VARY_HEADER = "vary";
    private static final String VARY_ALL = "*";

    private ResponseUtil() {
        // Utility class. Hide default constructor.
    }


    public static void addVaryFieldName(HttpServletResponse response, String name) {
        Collection<String> varyHeaders = response.getHeaders(VARY_HEADER);

        // Short-cut if only * has been set
        if (varyHeaders.size() == 1 && varyHeaders.iterator().next().trim().equals(VARY_ALL)) {
            // No need to add an additional field
            return;
        }

        // Short-cut if no headers have been set
        if (varyHeaders.size() == 0) {
            response.addHeader(VARY_HEADER, name);
            return;
        }

        // Short-cut if "*" is added
        if (VARY_ALL.equals(name.trim())) {
            response.setHeader(VARY_HEADER, VARY_ALL);
            return;
        }

        // May be dealing with an application set header, or multiple headers.
        // Header names overlap so can't use String.contains(). Have to parse
        // the existing values, check if the new value is already present and
        // then add it if not. The good news is field names are tokens which
        // makes parsing simpler.
        Set<String> fieldNames = new HashSet<>();

        for (String varyHeader : varyHeaders) {
            StringReader input = new StringReader(varyHeader);
            try {
                Vary.parseVary(input, fieldNames);
            } catch (IOException ioe) {
                // Should never happen
            }
        }

        if (fieldNames.contains(VARY_ALL)) {
            // '*' has been added without removing other values. Optimise.
            response.setHeader(VARY_HEADER, VARY_ALL);
            return;
        }

        // Build single header to replace current multiple headers
        // Replace existing header(s) to ensure any invalid values are removed
        fieldNames.add(name);
        StringBuilder varyHeader = new StringBuilder();
        varyHeader.append(name);
        for (String fieldName : fieldNames) {
            varyHeader.append(',');
            varyHeader.append(fieldName);
        }
        response.setHeader(VARY_HEADER, varyHeader.toString());
    }
}

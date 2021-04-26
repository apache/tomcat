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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.tomcat.util.http.parser.TokenList;

public class ResponseUtil {

    private static final String VARY_HEADER = "vary";
    private static final String VARY_ALL = "*";

    private ResponseUtil() {
        // Utility class. Hide default constructor.
    }


    public static void addVaryFieldName(MimeHeaders headers, String name) {
        addVaryFieldName(new HeaderAdapter(headers), name);
    }


    public static void addVaryFieldName(HttpServletResponse response, String name) {
        addVaryFieldName(new ResponseAdapter(response), name);
    }


    private static void addVaryFieldName(Adapter adapter, String name) {

        Collection<String> varyHeaders = adapter.getHeaders(VARY_HEADER);

        // Short-cut if only * has been set
        if (varyHeaders.size() == 1 && varyHeaders.iterator().next().trim().equals(VARY_ALL)) {
            // No need to add an additional field
            return;
        }

        // Short-cut if no headers have been set
        if (varyHeaders.size() == 0) {
            adapter.addHeader(VARY_HEADER, name);
            return;
        }

        // Short-cut if "*" is added
        if (VARY_ALL.equals(name.trim())) {
            adapter.setHeader(VARY_HEADER, VARY_ALL);
            return;
        }

        // May be dealing with an application set header, or multiple headers.
        // Header names overlap so can't use String.contains(). Have to parse
        // the existing values, check if the new value is already present and
        // then add it if not. The good news is field names are tokens which
        // makes parsing simpler.
        LinkedHashSet<String> fieldNames = new LinkedHashSet<>();

        for (String varyHeader : varyHeaders) {
            StringReader input = new StringReader(varyHeader);
            try {
                TokenList.parseTokenList(input, fieldNames);
            } catch (IOException ioe) {
                // Should never happen
            }
        }

        if (fieldNames.contains(VARY_ALL)) {
            // '*' has been added without removing other values. Optimise.
            adapter.setHeader(VARY_HEADER, VARY_ALL);
            return;
        }

        // Build single header to replace current multiple headers
        // Replace existing header(s) to ensure any invalid values are removed
        fieldNames.add(name);
        StringBuilder varyHeader = new StringBuilder();
        Iterator<String> iter = fieldNames.iterator();
        // There must be at least one value as one is added just above
        varyHeader.append(iter.next());
        while (iter.hasNext()) {
            varyHeader.append(',');
            varyHeader.append(iter.next());
        }
        adapter.setHeader(VARY_HEADER, varyHeader.toString());
    }


    private static interface Adapter {

        Collection<String> getHeaders(String name);

        void setHeader(String name, String value);

        void addHeader(String name, String value);
    }


    private static final class HeaderAdapter implements Adapter {
        private final MimeHeaders headers;

        public HeaderAdapter(MimeHeaders headers) {
            this.headers = headers;
        }

        @Override
        public Collection<String> getHeaders(String name) {
            Enumeration<String> values = headers.values(name);
            List<String> result = new ArrayList<>();
            while (values.hasMoreElements()) {
                result.add(values.nextElement());
            }
            return result;
        }

        @Override
        public void setHeader(String name, String value) {
            headers.setValue(name).setString(value);
        }

        @Override
        public void addHeader(String name, String value) {
            headers.addValue(name).setString(value);
        }
    }


    private static final class ResponseAdapter implements Adapter {
        private final HttpServletResponse response;

        public ResponseAdapter(HttpServletResponse response) {
            this.response = response;
        }

        @Override
        public Collection<String> getHeaders(String name) {
            return response.getHeaders(name);
        }

        @Override
        public void setHeader(String name, String value) {
            response.setHeader(name, value);
        }

        @Override
        public void addHeader(String name, String value) {
            response.addHeader(name, value);
        }
    }
}

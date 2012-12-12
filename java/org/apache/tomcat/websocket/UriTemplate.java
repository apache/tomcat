/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.websocket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tomcat.util.res.StringManager;

/**
 * Extracts path parameters from URIs used to create web socket connections
 * using the URI template defined for the associated Endpoint.
 */
public class UriTemplate {

    private static StringManager sm = StringManager.getManager(Constants.PACKAGE_NAME);
    private final String template;
    private final Pattern pattern;
    private final List<String> names = new ArrayList<>();


    public UriTemplate(String template) {
        this.template = template;
        // +10 is just a guess at this point
        StringBuilder pattern = new StringBuilder(template.length() + 10);
        int pos = 0;
        int end = 0;
        int start = template.indexOf('{');
        while (start > -1) {
            end = template.indexOf('}', start);
            pattern.append('(');
            pattern.append(Pattern.quote(template.substring(pos, start)));
            pattern.append(")([^/]*)");
            names.add(template.substring(start + 1, end));
            pos = end + 1;
            start = template.indexOf('{', pos);
        }
        // No more matches, append current position to end
        if (pos < template.length()) {
            pattern.append('(');
            pattern.append(template.substring(pos));
            pattern.append(")?");
        }
        this.pattern = Pattern.compile(pattern.toString());
    }


    public boolean contains(String name) {
        return names.contains(name);
    }


    /**
     * Extract the path parameters from the provided pathInfo based on the
     * template with which this UriTemplate was constructed.
     *
     * @param pathInfo The pathInfo from which the path parameters are to be
     *            extracted
     * @return A map of parameter names to values
     */
    public Map<String,String> match(String pathInfo) {
        Map<String,String> result = new HashMap<>();
        Matcher m = pattern.matcher(pathInfo);
        if (!m.matches()) {
            throw new IllegalArgumentException(sm.getString(
                    "uriTemplate.noMatch", template, pattern, pathInfo));
        }
        int group = 2;
        for (String name : names) {
            String value = m.group(group);
            if (value != null && value.length() > 0) {
                result.put(name, value);
            }
            group += 2;
        }
        return result;
    }
}

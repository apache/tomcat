/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
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
package org.apache.tomcat.manager2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Shared helpers for access log records, independent of the format that produced them. The field names are the
 * attribute names of {@code org.apache.catalina.valves.JsonAccessLogValve}, so that the pattern based and the JSON
 * access log format expose the same fields.
 */
final class AccessLogSupport {


    /**
     * The directive to regular expression and field name mapping. The regular expressions are capturing groups; the
     * character values that the valve writes for "not available" ({@code -}) are accepted by the numeric ones.
     */
    private static final Map<Character, String[]> DIRECTIVES;
    static {
        Map<Character, String[]> map = new HashMap<>();
        map.put('a', new String[] { "(\\S+)", "remoteAddr" });
        map.put('A', new String[] { "(\\S+)", "localAddr" });
        map.put('b', new String[] { "(-?\\d+|-)", "size" });
        map.put('B', new String[] { "(-?\\d+|-)", "byteSentNC" });
        map.put('D', new String[] { "(\\d+)", "elapsedTime" });
        map.put('F', new String[] { "(\\d+)", "firstByteTime" });
        map.put('h', new String[] { "(\\S+)", "host" });
        map.put('H', new String[] { "(\\S+)", "protocol" });
        map.put('I', new String[] { "(\\S+)", "threadName" });
        map.put('l', new String[] { "(\\S+)", "logicalUserName" });
        map.put('m', new String[] { "(\\S+)", "method" });
        map.put('p', new String[] { "(\\d+)", "port" });
        map.put('q', new String[] { "(\\S*)", "query" });
        map.put('r', new String[] { "([^\\\"]*)", "request" });
        map.put('s', new String[] { "(\\d{3}|-)", "statusCode" });
        map.put('S', new String[] { "(\\S+)", "sessionId" });
        map.put('t', new String[] { "(\\[[^\\]]*\\])", "time" });
        map.put('T', new String[] { "(\\d+(?:\\.\\d+)?)", "elapsedTimeS" });
        map.put('u', new String[] { "(\\S+)", "user" });
        map.put('U', new String[] { "(\\S+)", "path" });
        map.put('v', new String[] { "(\\S+)", "localServerName" });
        map.put('X', new String[] { "(\\S+)", "connectionStatus" });
        DIRECTIVES = Collections.unmodifiableMap(map);
    }


    /**
     * The field name prefix of the keyed directives ({@code %{name}x}).
     */
    private static final Map<Character, String> KEYED_PREFIXES;
    static {
        Map<Character, String> map = new HashMap<>();
        map.put('a', "remoteAddr");
        map.put('c', "cookie");
        map.put('i', "header");
        map.put('L', "identifier");
        map.put('o', "responseHeader");
        map.put('p', "port");
        map.put('r', "requestAttribute");
        map.put('s', "sessionAttribute");
        map.put('t', "time");
        KEYED_PREFIXES = Collections.unmodifiableMap(map);
    }


    private AccessLogSupport() {
        // Utility class, do not instantiate
    }


    static Map<Character, String[]> directives() {
        return DIRECTIVES;
    }


    /**
     * The field name of a keyed directive or {@code null} if the directive is not supported.
     */
    static String keyedField(char directive, String key) {
        String prefix = KEYED_PREFIXES.get(directive);
        if (prefix == null) {
            return null;
        }
        return prefix + "-" + key;
    }


    /**
     * The ordered list of all field names a record can have: the fields of the capture groups plus, when the request
     * line is logged but not the method, path, query or protocol individually, the fields derived from it (inserted
     * directly after the request).
     */
    static List<String> displayFields(List<String> groupFields) {
        List<String> fields = new ArrayList<>(groupFields);
        if (fields.contains("request")) {
            boolean missing = !fields.contains("method") || !fields.contains("path") || !fields.contains("query") ||
                    !fields.contains("protocol");
            if (missing) {
                int at = fields.indexOf("request") + 1;
                List<String> derived = new ArrayList<>();
                for (String name : new String[] { "method", "path", "query", "protocol" }) {
                    if (!fields.contains(name)) {
                        derived.add(name);
                    }
                }
                fields.addAll(at, derived);
            }
        }
        return fields;
    }


    /**
     * Normalize a parsed record: convert the common {@code -} marker to {@code null}, remove the square brackets of the
     * time and convert the numeric fields to numbers.
     */
    static void normalize(Map<String, Object> record) {
        Object time = record.get("time");
        if (time instanceof String t && t.startsWith("[") && t.endsWith("]")) {
            record.put("time", t.substring(1, t.length() - 1));
        }
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            if (entry.getValue() instanceof String s) {
                if ("-".equals(s)) {
                    entry.setValue(null);
                } else {
                    entry.setValue(numberOf(entry.getKey(), s));
                }
            }
        }
    }


    /**
     * Split the request line of a record into method, path, query and protocol when those fields are not present
     * individually.
     */
    static void deriveFromRequest(Map<String, Object> record) {
        if (!(record.get("request") instanceof String request) || record.containsKey("method")) {
            return;
        }
        int i1 = request.indexOf(' ');
        if (i1 <= 0) {
            return;
        }
        String method = request.substring(0, i1);
        int i2 = request.indexOf(' ', i1 + 1);
        String target = i2 >= 0 ? request.substring(i1 + 1, i2) : request.substring(i1 + 1);
        String protocol = i2 >= 0 && i2 + 1 < request.length() ? request.substring(i2 + 1) : null;
        record.putIfAbsent("method", method);
        int q = target.indexOf('?');
        if (q >= 0) {
            record.putIfAbsent("path", target.substring(0, q));
            record.putIfAbsent("query", target.substring(q + 1));
        } else {
            record.putIfAbsent("path", target);
        }
        if (protocol != null) {
            record.putIfAbsent("protocol", protocol);
        }
    }


    private static Object numberOf(String key, String value) {
        switch (key) {
            case "statusCode": {
                try {
                    return Integer.valueOf(value);
                } catch (NumberFormatException e) {
                    return value;
                }
            }
            case "port":
            case "elapsedTime":
            case "firstByteTime": {
                try {
                    return Long.valueOf(value);
                } catch (NumberFormatException e) {
                    return value;
                }
            }
            case "size":
            case "byteSentNC": {
                try {
                    return Long.valueOf(value);
                } catch (NumberFormatException e) {
                    return value;
                }
            }
            case "elapsedTimeS": {
                try {
                    return Double.valueOf(value);
                } catch (NumberFormatException e) {
                    return value;
                }
            }
            default:
                return value;
        }
    }
}

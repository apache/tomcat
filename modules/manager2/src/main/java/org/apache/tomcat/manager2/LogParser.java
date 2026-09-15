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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tomcat.util.json.JSONParser;


/**
 * Parsers for the two log families displayed by manager2.
 * <p>
 * <strong>Server logs</strong> are JULI files: one record per line, either in the plain one-line format
 * ({@code org.apache.juli.OneLineFormatter}: {@code 13-Sep-2026 14:53:07.449 INFO [main] org.apache... Message}) or the
 * JSON format ({@code org.apache.juli.JsonFormatter}:
 * {@code {"time":"...","level":"INFO","thread":"main","class":"...","method":"...","message":"..."}}). In the plain
 * format, the stack trace of a record continues on the following lines.
 * <p>
 * <strong>Access logs</strong> are written by {@code org.apache.catalina.valves.AccessLogValve} (pattern based) or
 * {@code org.apache.catalina.valves.JsonAccessLogValve} (one JSON object per line, one attribute per pattern element).
 * For the pattern based format the valve's pattern is converted to a regular expression, so the fields that are
 * available (and therefore filterable) depend on the configured pattern.
 */
public final class LogParser {


    /**
     * A compiled access log pattern: the regular expression, the field names of the capture groups and the ordered list
     * of all field names a record can have (including the fields derived from the request line).
     */
    public static final class AccessParser {

        private final Pattern regex;

        private final List<String> groupFields;

        private final List<String> fields;

        AccessParser(Pattern regex, List<String> groupFields, List<String> fields) {
            this.regex = regex;
            this.groupFields = groupFields;
            this.fields = fields;
        }


        /**
         * The ordered list of field names a record of this format can have.
         */
        public List<String> getFields() {
            return fields;
        }


        /**
         * Parse one access log line.
         *
         * @param line the line to parse
         * 
         * @return the parsed record (field names as keys) or {@code null} if the line does not match the pattern
         */
        public Map<String, Object> parse(String line) {
            Matcher m = regex.matcher(line);
            if (!m.matches()) {
                return null;
            }
            Map<String, Object> record = new LinkedHashMap<>();
            for (int i = 0; i < groupFields.size(); i++) {
                String value = m.group(i + 1);
                if (value != null) {
                    record.put(groupFields.get(i), value);
                }
            }
            AccessLogSupport.normalize(record);
            AccessLogSupport.deriveFromRequest(record);
            return record;
        }
    }


    /**
     * The single line format of the JULI one line log formatter:
     * {@code dd-MMM-yyyy HH:mm:ss.SSS LEVEL [thread] source.message}
     */
    private static final Pattern TEXT_LOG_LINE = Pattern.compile(
            "^(\\d{2}-[A-Za-z]{3}-\\d{4} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}) (\\S+)" + " \\[([^\\]]*)\\] (\\S+) (.*)$");


    /**
     * Parse one line of a plain text JULI log file.
     *
     * @param line the line to parse
     * 
     * @return a record with the fields {@code time}, {@code level}, {@code thread}, {@code source} and {@code message}
     *             or {@code null} if the line is not a record (for example a continuation line of a stack trace)
     */
    public static Map<String, Object> parseTextLogLine(String line) {
        Matcher m = TEXT_LOG_LINE.matcher(line);
        if (!m.matches()) {
            return null;
        }
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("time", m.group(1));
        record.put("level", m.group(2));
        record.put("thread", m.group(3));
        record.put("source", m.group(4));
        record.put("message", m.group(5));
        return record;
    }


    /**
     * Parse one line of a JSON JULI log file ({@code org.apache.juli.JsonFormatter} output).
     *
     * @param line the line to parse
     * 
     * @return a record with the fields {@code time}, {@code level}, {@code thread}, {@code source}, {@code message}
     *             and, when the record has an exception, {@code throwable} (a list of strings); or {@code null} if the
     *             line is not a JSON object
     */
    public static Map<String, Object> parseJsonLogLine(String line) {
        if (line.isEmpty() || line.charAt(0) != '{') {
            return null;
        }
        try {
            Object parsed = new JSONParser(line).parseObject();
            if (!(parsed instanceof Map<?, ?> obj)) {
                return null;
            }
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("time", stringOf(obj.get("time")));
            record.put("level", stringOf(obj.get("level")));
            record.put("thread", stringOf(obj.get("thread")));
            String className = stringOf(obj.get("class"));
            String methodName = stringOf(obj.get("method"));
            if (className != null || methodName != null) {
                StringBuilder source = new StringBuilder();
                if (className != null) {
                    source.append(className);
                }
                if (className != null && methodName != null) {
                    source.append('.');
                }
                if (methodName != null) {
                    source.append(methodName);
                }
                record.put("source", source.toString());
            }
            record.put("message", stringOf(obj.get("message")));
            if (obj.get("throwable") instanceof List<?> thrown && !thrown.isEmpty()) {
                record.put("throwable", thrown);
            }
            return record;
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Parse one line of a JSON access log ({@code org.apache.catalina.valves.JsonAccessLogValve} output).
     *
     * @param line the line to parse
     * 
     * @return the parsed record (the attribute names of the JSON object as keys) or {@code null} if the line is not a
     *             JSON object
     */
    public static Map<String, Object> parseJsonAccessLine(String line) {
        if (line.isEmpty() || line.charAt(0) != '{') {
            return null;
        }
        try {
            Object parsed = new JSONParser(line).parseObject();
            if (!(parsed instanceof Map<?, ?> obj)) {
                return null;
            }
            Map<String, Object> record = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : obj.entrySet()) {
                record.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            AccessLogSupport.normalize(record);
            AccessLogSupport.deriveFromRequest(record);
            return record;
        } catch (Exception e) {
            return null;
        }
    }


    private static String stringOf(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof String s ? s : String.valueOf(value);
    }


    /**
     * Compile an access log pattern into a line parser. The directive to field name mapping is the one of
     * {@code org.apache.catalina.valves.JsonAccessLogValve}, so that both access log formats expose the same field
     * names.
     *
     * @param pattern the access log pattern (for example {@code %h %l %u %t "%r" %s %b})
     * 
     * @return the compiled parser
     * 
     * @throws IllegalArgumentException if the pattern uses an unsupported directive or produces a regular expression
     *                                      with more than the maximum number of capture groups
     */
    public static AccessParser compile(String pattern) {
        StringBuilder regex = new StringBuilder(pattern.length() * 2);
        List<String> groupFields = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c != '%') {
                literal.append(c);
                continue;
            }
            if (literal.length() > 0) {
                regex.append(Pattern.quote(literal.toString()));
                literal.setLength(0);
            }
            if (i + 1 >= pattern.length()) {
                throw new IllegalArgumentException("Dangling '%' in access log pattern: " + pattern);
            }
            char next = pattern.charAt(++i);
            if (next == '%') {
                literal.append('%');
                continue;
            }
            if (next == '{') {
                int close = pattern.indexOf('}', i + 1);
                if (close < 0 || close + 1 >= pattern.length()) {
                    throw new IllegalArgumentException("Malformed access log pattern: " + pattern);
                }
                String key = pattern.substring(i + 1, close);
                char directive = pattern.charAt(close + 1);
                String field = AccessLogSupport.keyedField(directive, key);
                if (field == null) {
                    throw new IllegalArgumentException(
                            "Unsupported directive %{%s}%c in access log pattern".formatted(key, directive));
                }
                regex.append("([^\\\"]*)");
                groupFields.add(field);
                i = close + 1;
                continue;
            }
            String[] directive = AccessLogSupport.directives().get(next);
            if (directive == null) {
                throw new IllegalArgumentException("Unsupported directive %c in access log pattern".formatted(next));
            }
            regex.append(directive[0]);
            groupFields.add(directive[1]);
        }
        if (literal.length() > 0) {
            regex.append(Pattern.quote(literal.toString()));
        }

        Pattern regexPattern;
        try {
            regexPattern = Pattern.compile(regex.toString());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid access log pattern: " + pattern, e);
        }

        return new AccessParser(regexPattern, groupFields, AccessLogSupport.displayFields(groupFields));
    }


    private LogParser() {
        // Utility class, do not instantiate
    }
}

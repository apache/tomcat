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
package org.apache.juli;

import java.util.logging.LogManager;
import java.util.logging.LogRecord;

/**
 * Provides the same information as the one line format but using JSON formatting. All the information of the LogRecord
 * is included as a one line JSON document, including the full stack trace of the associated exception if any.
 * <p>
 * The LogRecord is mapped as attributes:
 * <ul>
 * <li>time: the log record timestamp, with the default format as {@code yyyy-MM-dd'T'HH:mm:ss.SSSX}</li>
 * <li>level: the log level</li>
 * <li>thread: the current on which the log occurred</li>
 * <li>class: the class from which the log originated</li>
 * <li>method: the method from which the log originated</li>
 * <li>message: the log message</li>
 * <li>throwable: the full stack trace from an exception, if present, represented as an array of string (the message
 * first, then one string per stack trace element prefixed by a whitespace, then moving on to the cause exception if
 * any)</li>
 * </ul>
 */
public class JsonFormatter extends OneLineFormatter {

    private static final String DEFAULT_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSX";

    public JsonFormatter() {
        String timeFormat = LogManager.getLogManager().getProperty(JsonFormatter.class.getName() + ".timeFormat");
        if (timeFormat == null) {
            timeFormat = DEFAULT_TIME_FORMAT;
        }
        setTimeFormat(timeFormat);
    }

    @Override
    public String format(LogRecord record) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');

        // Timestamp
        sb.append("\"time\": \"");
        addTimestamp(sb, record.getMillis());
        sb.append("\", ");

        // Severity
        sb.append("\"level\": \"");
        sb.append(record.getLevel().getLocalizedName());
        sb.append("\", ");

        // Thread
        sb.append("\"thread\": \"");
        final String threadName = Thread.currentThread().getName();
        if (threadName != null && threadName.startsWith(AsyncFileHandler.THREAD_PREFIX)) {
            // If using the async handler can't get the thread name from the
            // current thread.
            sb.append(getThreadName(record.getThreadID()));
        } else {
            sb.append(threadName);
        }
        sb.append("\", ");

        // Source
        sb.append("\"class\": \"");
        sb.append(record.getSourceClassName());
        sb.append("\", ");
        sb.append("\"method\": \"");
        sb.append(record.getSourceMethodName());
        sb.append("\", ");

        // Message
        sb.append("\"message\": \"");
        sb.append(JSONFilter.escape(formatMessage(record)));

        Throwable t = record.getThrown();
        if (t != null) {
            sb.append("\", ");

            // Stack trace
            sb.append("\"throwable\": [");
            boolean first = true;
            do {
                if (!first) {
                    sb.append(',');
                } else {
                    first = false;
                }
                sb.append('\"').append(JSONFilter.escape(t.toString())).append('\"');
                for (StackTraceElement element : t.getStackTrace()) {
                    sb.append(',').append('\"').append(' ').append(JSONFilter.escape(element.toString())).append('\"');
                }
                t = t.getCause();
            } while (t != null);
            sb.append(']');
        } else {
            sb.append('\"');
        }

        sb.append('}');
        // New line for next record
        sb.append(System.lineSeparator());

        return sb.toString();
    }


    /**
     * Provides escaping of values so they can be included in a JSON document. Escaping is based on the definition of
     * JSON found in <a href="https://www.rfc-editor.org/rfc/rfc8259.html">RFC 8259</a>.
     */
    public static class JSONFilter {

        /**
         * Escape the given string.
         *
         * @param input the string
         *
         * @return the escaped string
         */
        public static String escape(String input) {
            return escape(input, 0, input.length()).toString();
        }

        /**
         * Escape the given char sequence.
         *
         * @param input  the char sequence
         * @param off    the offset on which escaping will start
         * @param length the length which should be escaped
         *
         * @return the escaped char sequence corresponding to the specified range
         */
        public static CharSequence escape(CharSequence input, int off, int length) {
            /*
             * While any character MAY be escaped, only U+0000 to U+001F (control characters), U+0022 (quotation mark)
             * and U+005C (reverse solidus) MUST be escaped.
             */
            StringBuilder escaped = null;
            int lastUnescapedStart = off;
            for (int i = off; i < length; i++) {
                char c = input.charAt(i);
                if (c < 0x20 || c == 0x22 || c == 0x5c || Character.isHighSurrogate(c) || Character.isLowSurrogate(c)) {
                    if (escaped == null) {
                        escaped = new StringBuilder(length + 20);
                    }
                    if (lastUnescapedStart < i) {
                        escaped.append(input.subSequence(lastUnescapedStart, i));
                    }
                    lastUnescapedStart = i + 1;
                    char popular = getPopularChar(c);
                    if (popular > 0) {
                        escaped.append('\\').append(popular);
                    } else {
                        escaped.append("\\u");
                        escaped.append(String.format("%04X", Integer.valueOf(c)));
                    }
                }
            }
            if (escaped == null) {
                if (off == 0 && length == input.length()) {
                    return input;
                } else {
                    return input.subSequence(off, length - off);
                }
            } else {
                if (lastUnescapedStart < length) {
                    escaped.append(input.subSequence(lastUnescapedStart, length));
                }
                return escaped.toString();
            }
        }

        private JSONFilter() {
            // Utility class. Hide the default constructor.
        }

        private static char getPopularChar(char c) {
            switch (c) {
                case '"':
                case '\\':
                case '/':
                    return c;
                case 0x8:
                    return 'b';
                case 0xc:
                    return 'f';
                case 0xa:
                    return 'n';
                case 0xd:
                    return 'r';
                case 0x9:
                    return 't';
                default:
                    return 0;
            }
        }

    }
}

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
package org.apache.tomcat.util.security;

/**
 * Provides utility methods to escape content for different contexts. It is
 * critical that the escaping used is correct for the context in which the data
 * is to be used.
 */
public class Escape {

    private Escape() {
        // Hide default constructor for this utility class
    }


    /**
     * Escape content for use in HTML. This escaping is suitable for the
     * following uses:
     * <ul>
     * <li>Element content when the escaped data will be placed directly inside
     *     tags such as &lt;p&gt;, &lt;td&gt; etc.</li>
     * <li>Attribute values when the attribute value is quoted with &quot; or
     *     &#x27;.</li>
     * </ul>
     *
     * @param content   The content to escape
     *
     * @return  The escaped content or {@code null} if the content was
     *          {@code null}
     */
    public static String htmlElementContent(String content) {
        if (content == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '<') {
                sb.append("&lt;");
            } else if (c == '>') {
                sb.append("&gt;");
            } else if (c == '\'') {
                sb.append("&#39;");
            } else if (c == '&') {
                sb.append("&amp;");
            } else if (c == '"') {
                sb.append("&quot;");
            } else if (c == '/') {
                sb.append("&#47;");
            } else {
                sb.append(c);
            }
        }

        return (sb.length() > content.length()) ? sb.toString() : content;
    }


    /**
     * Convert the object to a string via {@link Object#toString()} and HTML
     * escape the resulting string for use in HTML content.
     *
     * @param obj       The object to convert to String and then escape
     *
     * @return The escaped content or <code>&quot;?&quot;</code> if obj is
     *         {@code null}
     */
    public static String htmlElementContent(Object obj) {
        if (obj == null) {
            return "?";
        }

        try {
            return htmlElementContent(obj.toString());
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Escape content for use in XML.
     *
     * @param content   The content to escape
     *
     * @return  The escaped content or {@code null} if the content was
     *          {@code null}
     */
    public static String xml(String content) {
        return xml(null, content);
    }


    /**
     * Escape content for use in XML.
     *
     * @param ifNull    The value to return if content is {@code null}
     * @param content   The content to escape
     *
     * @return  The escaped content or the value of {@code ifNull} if the
     *          content was {@code null}
     */
    public static String xml(String ifNull, String content) {
        return xml(ifNull, false, content);
    }


    /**
     * Escape content for use in XML.
     *
     * @param ifNull        The value to return if content is {@code null}
     * @param escapeCRLF    Should CR and LF also be escaped?
     * @param content       The content to escape
     *
     * @return  The escaped content or the value of ifNull if the content was
     *          {@code null}
     */
    public static String xml(String ifNull, boolean escapeCRLF, String content) {
        if (content == null) {
            return ifNull;
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '<') {
                sb.append("&lt;");
            } else if (c == '>') {
                sb.append("&gt;");
            } else if (c == '\'') {
                sb.append("&apos;");
            } else if (c == '&') {
                sb.append("&amp;");
            } else if (c == '"') {
                sb.append("&quot;");
            } else if (escapeCRLF && c == '\r') {
                sb.append("&#13;");
            } else if (escapeCRLF && c == '\n') {
                sb.append("&#10;");
            } else {
                sb.append(c);
            }
        }

        return (sb.length() > content.length()) ? sb.toString(): content;
    }
}

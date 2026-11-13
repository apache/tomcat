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
package org.apache.tomcat.util.http.parser;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parser for the HTTP {@code Forwarded} header defined by RFC 7239.
 *
 * @since 12.0.0-M2
 */
public final class Forwarded {

    private Forwarded() {
        // Utility class. Hide default constructor.
    }


    /**
     * Parse the given {@code Forwarded} field values. Each field value is parsed on its own so that a syntax error in
     * one field value cannot change how another field value is interpreted.
     *
     * @param fieldValues The field values, in the order they were received
     *
     * @return The forwarded elements in header order or {@code null} if any field value is not syntactically valid
     *
     * @throws IOException If an I/O error occurs while reading the field values
     */
    public static List<Element> parse(Enumeration<String> fieldValues) throws IOException {
        List<Element> result = new ArrayList<>();
        while (fieldValues.hasMoreElements()) {
            if (!parse(new StringReader(fieldValues.nextElement()), result)) {
                return null;
            }
        }
        return result;
    }


    /*
     * Parse a single field value. Returns false if the field value is not syntactically valid.
     */
    static boolean parse(Reader input, List<Element> result) throws IOException {
        while (true) {
            int c = HttpParser.skipWhitespace(input);
            if (c == -1) {
                return true;
            }
            if (c == ',') {
                // Empty list element
                input.read();
                continue;
            }

            Element element = new Element();
            while (true) {
                c = HttpParser.skipWhitespace(input);
                if (c == ';') {
                    input.read();
                    continue;
                }
                if (c == ',' || c == -1) {
                    break;
                }

                // readToken() returns the empty string at the end of the input
                String name = HttpParser.readToken(input);
                if (name == null || name.isEmpty()) {
                    return false;
                }
                if (HttpParser.skipConstant(input, "=") != SkipResult.FOUND) {
                    return false;
                }
                boolean quoted = HttpParser.skipWhitespace(input) == '"';
                String value = HttpParser.readTokenOrQuotedString(input, false);
                // An empty quoted-string is a valid value but an empty token is not
                if (value == null || value.isEmpty() && !quoted) {
                    return false;
                }
                element.put(name.toLowerCase(Locale.ENGLISH), value);

                c = HttpParser.skipWhitespace(input);
                if (c == ';') {
                    input.read();
                } else if (c == ',') {
                    input.read();
                    break;
                } else if (c == -1) {
                    break;
                } else {
                    return false;
                }
            }
            if (!element.params.isEmpty()) {
                result.add(element);
            }
        }
    }


    /**
     * A single forwarded element, that is, the parameters added by one proxy.
     *
     * @since 12.0.0-M2
     */
    public static final class Element {

        private final Map<String,String> params = new LinkedHashMap<>();
        private boolean valid = true;

        private Element() {
        }

        private void put(String name, String value) {
            if (params.putIfAbsent(name, value) != null) {
                valid = false;
            }
        }

        /**
         * Obtain the value of the {@code for} parameter.
         *
         * @return The unquoted value or {@code null} if the parameter is not present
         */
        public String getFor() {
            return params.get("for");
        }

        /**
         * Obtain the value of the {@code by} parameter.
         *
         * @return The unquoted value or {@code null} if the parameter is not present
         */
        public String getBy() {
            return params.get("by");
        }

        /**
         * Obtain the value of the {@code host} parameter.
         *
         * @return The unquoted value or {@code null} if the parameter is not present
         */
        public String getHost() {
            return params.get("host");
        }

        /**
         * Obtain the value of the {@code proto} parameter.
         *
         * @return The unquoted value or {@code null} if the parameter is not present
         */
        public String getProto() {
            return params.get("proto");
        }

        /**
         * Obtain the {@code for} parameter as a node.
         *
         * @return The node or {@code null} if the parameter is absent or is not a valid node
         */
        public Node getForNode() {
            return Node.parse(getFor());
        }

        /**
         * Is this element free of duplicate parameters?
         *
         * @return {@code false} if any parameter name appears more than once
         */
        public boolean isValid() {
            return valid;
        }

        /**
         * Obtain the canonical header value of this element. Values that are not tokens are written as quoted
         * strings.
         *
         * @return The element serialised as a {@code Forwarded} header list element
         */
        public String toHeaderValue() {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String,String> entry : params.entrySet()) {
                if (!sb.isEmpty()) {
                    sb.append(';');
                }
                sb.append(entry.getKey());
                sb.append('=');
                String value = entry.getValue();
                if (HttpParser.isToken(value)) {
                    sb.append(value);
                } else {
                    sb.append('"');
                    for (int i = 0; i < value.length(); i++) {
                        char c = value.charAt(i);
                        if (c == '"' || c == '\\') {
                            sb.append('\\');
                        }
                        sb.append(c);
                    }
                    sb.append('"');
                }
            }
            return sb.toString();
        }
    }


    /**
     * A node identifier as defined by RFC 7239, section 6.
     *
     * @since 12.0.0-M2
     */
    public static final class Node {

        /**
         * The type of node name.
         */
        public enum Type {
            /** An IPv4 address. */
            IPV4,
            /** An IPv6 address. */
            IPV6,
            /** The identifier {@code unknown}. */
            UNKNOWN,
            /** An obfuscated identifier. */
            OBFUSCATED
        }

        private final Type type;
        private final String name;
        private final String port;

        private Node(Type type, String name, String port) {
            this.type = type;
            this.name = name;
            this.port = port;
        }

        /**
         * Parse a node identifier.
         *
         * @param value The node, without any surrounding quotes
         *
         * @return The node or {@code null} if the value is not a node as defined by RFC 7239
         */
        public static Node parse(String value) {
            if (value == null || value.isEmpty()) {
                return null;
            }

            Type type;
            String name;
            String port;
            if (value.charAt(0) == '[') {
                int end = value.indexOf(']');
                if (end < 0) {
                    return null;
                }
                try {
                    HttpParser.readHostIPv6(new StringReader(value.substring(0, end + 1)));
                } catch (IllegalArgumentException | IOException e) {
                    return null;
                }
                type = Type.IPV6;
                name = value.substring(1, end);
                String rest = value.substring(end + 1);
                if (rest.isEmpty()) {
                    port = null;
                } else if (rest.charAt(0) == ':') {
                    port = rest.substring(1);
                } else {
                    return null;
                }
            } else {
                int colon = value.indexOf(':');
                if (colon < 0) {
                    name = value;
                    port = null;
                } else {
                    name = value.substring(0, colon);
                    port = value.substring(colon + 1);
                }
                if ("unknown".equalsIgnoreCase(name)) {
                    type = Type.UNKNOWN;
                } else if (isObfuscated(name)) {
                    type = Type.OBFUSCATED;
                } else if (isIPv4(name)) {
                    type = Type.IPV4;
                } else {
                    return null;
                }
            }

            if (port != null && !isPort(port)) {
                return null;
            }
            return new Node(type, name, port);
        }

        private static boolean isObfuscated(String s) {
            if (s.length() < 2 || s.charAt(0) != '_') {
                return false;
            }
            for (int i = 1; i < s.length(); i++) {
                char c = s.charAt(i);
                if (!(HttpParser.isAlpha(c) || HttpParser.isNumeric(c) || c == '.' || c == '_' || c == '-')) {
                    return false;
                }
            }
            return true;
        }

        private static boolean isPort(String s) {
            if (s.isEmpty()) {
                return false;
            }
            if (s.charAt(0) == '_') {
                return isObfuscated(s);
            }
            // RFC 7239: port = 1*5DIGIT
            if (s.length() > 5) {
                return false;
            }
            for (int i = 0; i < s.length(); i++) {
                if (!HttpParser.isNumeric(s.charAt(i))) {
                    return false;
                }
            }
            return true;
        }

        /*
         * Strict dotted-quad: four decimal octets, 0-255, no leading zeros. HttpParser.readHostIPv4() is not suitable
         * since it falls back to domain names.
         */
        private static boolean isIPv4(String s) {
            int octets = 0;
            int start = 0;
            while (true) {
                int end = s.indexOf('.', start);
                String octet = end < 0 ? s.substring(start) : s.substring(start, end);
                if (octet.isEmpty() || octet.length() > 3 || octet.length() > 1 && octet.charAt(0) == '0') {
                    return false;
                }
                for (int i = 0; i < octet.length(); i++) {
                    if (!HttpParser.isNumeric(octet.charAt(i))) {
                        return false;
                    }
                }
                if (Integer.parseInt(octet) > 255) {
                    return false;
                }
                octets++;
                if (end < 0) {
                    break;
                }
                start = end + 1;
            }
            return octets == 4;
        }

        /**
         * Obtain the type of this node.
         *
         * @return The node type
         */
        public Type getType() {
            return type;
        }

        /**
         * Obtain the node name. IPv6 addresses are returned without the enclosing brackets.
         *
         * @return The node name
         */
        public String getName() {
            return name;
        }

        /**
         * Obtain the node port.
         *
         * @return The raw node port or {@code null} if there is none
         */
        public String getPort() {
            return port;
        }

        /**
         * Is this node an IP address?
         *
         * @return {@code true} if the node is an IPv4 or IPv6 address
         */
        public boolean isAddress() {
            return type == Type.IPV4 || type == Type.IPV6;
        }
    }
}

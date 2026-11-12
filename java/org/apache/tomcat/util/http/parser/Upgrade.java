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
import java.util.List;

/**
 * Represents an HTTP Upgrade protocol value with optional version.
 */
public class Upgrade {

    /**
     * The protocol name.
     */
    private final String protocolName;

    /**
     * The protocol version, or null if not specified.
     */
    private final String protocolVersion;


    /**
     * Constructs a new Upgrade.
     *
     * @param protocolName the protocol name
     * @param protocolVersion the protocol version, or null
     */
    private Upgrade(String protocolName, String protocolVersion) {
        this.protocolName = protocolName;
        this.protocolVersion = protocolVersion;
    }


    /**
     * Returns the protocol name.
     *
     * @return the protocol name
     */
    public String getProtocolName() {
        return protocolName;
    }


    /**
     * Returns the protocol version, or null if not specified.
     *
     * @return the protocol version
     */
    public String getProtocolVersion() {
        return protocolVersion;
    }


    @Override
    public String toString() {
        if (protocolVersion == null) {
            return protocolName;
        } else {
            return protocolName + "/" + protocolVersion;
        }
    }


    /**
     * Parses Upgrade header values into a list of Upgrade instances.
     *
     * @param headerValues the enumeration of header values
     * @return the list of parsed upgrades, or null if parsing fails
     */
    public static List<Upgrade> parse(Enumeration<String> headerValues) {
        try {
            List<Upgrade> result = new ArrayList<>();
            boolean sawHeader = false;

            while (headerValues.hasMoreElements()) {
                sawHeader = true;
                String headerValue = headerValues.nextElement();
                if (headerValue == null) {
                    // Invalid
                    return null;
                }

                Reader r = new StringReader(headerValue);
                SkipResult skipComma;
                do {
                    // Empty list elements must be ignored (RFC 9110, section 5.6.1.2)
                    SkipResult skipEmpty = HttpParser.skipConstant(r, ",");
                    while (skipEmpty == SkipResult.FOUND) {
                        skipEmpty = HttpParser.skipConstant(r, ",");
                    }
                    if (skipEmpty == SkipResult.EOF) {
                        // Nothing left in this header value once any empty
                        // list elements are skipped. This header contributes
                        // no protocols, whether or not earlier headers did.
                        break;
                    }
                    // Skip any leading whitespace
                    HttpParser.skipWhitespace(r);
                    String protocolName = HttpParser.readToken(r);
                    if (protocolName == null || protocolName.isEmpty()) {
                        // Invalid
                        return null;
                    }
                    String protocolVersion = null;
                    if (HttpParser.skipConstant(r, "/") == SkipResult.FOUND) {
                        protocolVersion = HttpParser.readToken(r);
                        if (protocolVersion == null || protocolVersion.isEmpty()) {
                            // Invalid
                            return null;
                        }
                    }
                    HttpParser.skipWhitespace(r);

                    skipComma = HttpParser.skipConstant(r, ",");
                    if (skipComma == SkipResult.NOT_FOUND) {
                        // Invalid
                        return null;
                    }

                    result.add(new Upgrade(protocolName, protocolVersion));
                    // SkipResult.EOF will exit this inner loop
                } while (skipComma == SkipResult.FOUND);
            }

            if (sawHeader && result.isEmpty()) {
                // An Upgrade header must name at least one protocol (RFC
                // 9110, section 7.8). One or more headers that reduce to an
                // empty list once empty list elements are discarded is
                // invalid. No Upgrade header at all is not an error; it
                // yields the empty list below, as before this change.
                return null;
            }

            return result;
        } catch (IOException ioe) {
            // Should never happen with Strings
            return null;
        }
    }
}

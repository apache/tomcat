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

public class Upgrade {

    private final String protocolName;
    private final String protocolVersion;


    private Upgrade(String protocolName, String protocolVersion) {
        this.protocolName = protocolName;
        this.protocolVersion = protocolVersion;
    }


    public String getProtocolName() {
        return protocolName;
    }


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


    public static List<Upgrade> parse (Enumeration<String> headerValues) {
        try {
            List<Upgrade> result = new ArrayList<>();

            while (headerValues.hasMoreElements()) {
                String headerValue = headerValues.nextElement();
                if (headerValue == null) {
                    // Invalid
                    return null;
                }

                Reader r = new StringReader(headerValue);
                SkipResult skipComma;
                do {
                    // Skip any leading LWS
                    HttpParser.skipLws(r);
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
                    HttpParser.skipLws(r);

                    skipComma = HttpParser.skipConstant(r, ",");
                    if (skipComma == SkipResult.NOT_FOUND) {
                        // Invalid
                        return null;
                    }

                    result.add(new Upgrade(protocolName, protocolVersion));
                    // SkipResult.EOF will exit this inner loop
                } while (skipComma == SkipResult.FOUND);
            }

            return result;
        } catch (IOException ioe) {
            // Should never happen with Strings
            return null;
        }
    }
}

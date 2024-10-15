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
package org.apache.tomcat.util.http.parser;

import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class MediaType {

    private final String type;
    private final String subtype;
    private final LinkedHashMap<String,String> parameters;
    private final String charset;
    private volatile String noCharset;
    private volatile String withCharset;

    protected MediaType(String type, String subtype, LinkedHashMap<String,String> parameters) {
        this.type = type;
        this.subtype = subtype;
        this.parameters = parameters;

        String cs = parameters.get("charset");
        if (cs != null && cs.length() > 0 && cs.charAt(0) == '"') {
            cs = HttpParser.unquote(cs);
        }
        this.charset = cs;
    }

    public String getType() {
        return type;
    }

    public String getSubtype() {
        return subtype;
    }

    public String getCharset() {
        return charset;
    }

    public int getParameterCount() {
        return parameters.size();
    }

    public String getParameterValue(String parameter) {
        return parameters.get(parameter.toLowerCase(Locale.ENGLISH));
    }

    @Override
    public String toString() {
        if (withCharset == null) {
            synchronized (this) {
                if (withCharset == null) {
                    StringBuilder result = new StringBuilder();
                    result.append(type);
                    result.append('/');
                    result.append(subtype);
                    for (Map.Entry<String,String> entry : parameters.entrySet()) {
                        String value = entry.getValue();
                        if (value == null || value.length() == 0) {
                            continue;
                        }
                        result.append(';');
                        result.append(entry.getKey());
                        result.append('=');
                        result.append(value);
                    }

                    withCharset = result.toString();
                }
            }
        }
        return withCharset;
    }

    public String toStringNoCharset() {
        if (noCharset == null) {
            synchronized (this) {
                if (noCharset == null) {
                    StringBuilder result = new StringBuilder();
                    result.append(type);
                    result.append('/');
                    result.append(subtype);
                    for (Map.Entry<String,String> entry : parameters.entrySet()) {
                        if (entry.getKey().equalsIgnoreCase("charset")) {
                            continue;
                        }
                        result.append(';');
                        result.append(entry.getKey());
                        result.append('=');
                        result.append(entry.getValue());
                    }

                    noCharset = result.toString();
                }
            }
        }
        return noCharset;
    }

    /**
     * Parses a MediaType value, either from an HTTP header or from an application.
     *
     * @param input a reader over the header text
     *
     * @return a MediaType parsed from the input, or null if not valid
     *
     * @throws IOException if there was a problem reading the input
     */
    public static MediaType parseMediaType(StringReader input) throws IOException {

        // Type (required)
        String type = HttpParser.readToken(input);
        if (type == null || type.length() == 0) {
            return null;
        }

        if (HttpParser.skipConstant(input, "/") == SkipResult.NOT_FOUND) {
            return null;
        }

        // Subtype (required)
        String subtype = HttpParser.readToken(input);
        if (subtype == null || subtype.length() == 0) {
            return null;
        }

        LinkedHashMap<String,String> parameters = new LinkedHashMap<>();

        SkipResult lookForSemiColon = HttpParser.skipConstant(input, ";");
        if (lookForSemiColon == SkipResult.NOT_FOUND) {
            return null;
        }
        while (lookForSemiColon == SkipResult.FOUND) {
            String attribute = HttpParser.readToken(input);

            String value = "";
            if (HttpParser.skipConstant(input, "=") == SkipResult.FOUND) {
                value = HttpParser.readTokenOrQuotedString(input, true);
            }

            if (attribute != null) {
                parameters.put(attribute.toLowerCase(Locale.ENGLISH), value);
            }

            lookForSemiColon = HttpParser.skipConstant(input, ";");
            if (lookForSemiColon == SkipResult.NOT_FOUND) {
                return null;
            }
        }

        return new MediaType(type, subtype, parameters);
    }

}

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

import org.apache.tomcat.util.res.StringManager;

/*
 * Unlike other HTTP parsers, this is a stateless (state is held by the calling code), streaming parser as chunk headers
 * are read as part of the request body and it is not always possible to buffer then entire chunk header in memory.
 */
public class ChunkExtension {

    private static final StringManager sm = StringManager.getManager(ChunkExtension.class);

    public static State parse(byte b, State state) throws IOException {

        char c = (char) (0xFF & b);

        switch (state) {
            case PRE_NAME:
                if (HttpParser.isWhiteSpace(c)) {
                    return State.PRE_NAME;
                } else if (HttpParser.isToken(c)) {
                    return State.NAME;
                }
                break;
            case NAME:
                if (HttpParser.isWhiteSpace(c)) {
                    return State.POST_NAME;
                } else if (HttpParser.isToken(c)) {
                    return State.NAME;
                } else if (c == '=') {
                    return State.EQUALS;
                } else if (c == '\r') {
                    return State.CR;
                }
                break;
            case POST_NAME:
                if (HttpParser.isWhiteSpace(c)) {
                    return State.POST_NAME;
                } else if (c == '=') {
                    return State.EQUALS;
                } else if (c == '\r') {
                    return State.CR;
                }
                break;
            case EQUALS:
                if (HttpParser.isWhiteSpace(c)) {
                    return State.EQUALS;
                } else if (HttpParser.isToken(c)) {
                    return State.VALUE;
                } else if (c == '"') {
                    return State.QUOTED_VALUE;
                }
                break;
            case VALUE:
                if (HttpParser.isToken(c)) {
                    return State.VALUE;
                } else if (HttpParser.isWhiteSpace(c)) {
                    return State.POST_VALUE;
                } else if (c == ';') {
                    return State.PRE_NAME;
                } else if (c == '\r') {
                    return State.CR;
                }
                break;
            case QUOTED_VALUE:
                if (c == '"') {
                    return State.POST_VALUE;
                } else if (c == '\\' || c == 127) {
                    throw new IOException(sm.getString("chunkExtension.invalid"));
                } else if (c == '\t') {
                    return State.QUOTED_VALUE;
                } else if (c > 31) {
                    return State.QUOTED_VALUE;
                }
                break;
            case POST_VALUE:
                if (HttpParser.isWhiteSpace(c)) {
                    return State.POST_VALUE;
                } else if (c == ';') {
                    return State.PRE_NAME;
                } else if (c == '\r') {
                    return State.CR;
                }
                break;
            case CR:
                break;
        }

        throw new IOException(sm.getString("chunkExtension.invalid"));
    }


    private ChunkExtension() {
        // Tomcat doesn't use this data. It only parses it to ensure that it is correctly formatted.
    }


    public enum State {
        PRE_NAME,
        NAME,
        POST_NAME,
        EQUALS,
        VALUE,
        QUOTED_VALUE,
        POST_VALUE,
        CR
    }
}

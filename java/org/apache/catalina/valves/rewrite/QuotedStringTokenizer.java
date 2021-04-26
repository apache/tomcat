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
package org.apache.catalina.valves.rewrite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.tomcat.util.res.StringManager;

public class QuotedStringTokenizer {

    protected static final StringManager sm = StringManager.getManager(QuotedStringTokenizer.class);

    private Iterator<String> tokenIterator;
    private int tokenCount;
    private int returnedTokens = 0;

    enum WordMode {
        SPACES, QUOTED, ESCAPED, SIMPLE, COMMENT
    }

    public QuotedStringTokenizer(String text) {
        List<String> tokens;
        if (text != null) {
            tokens = tokenizeText(text);
        } else {
            tokens = Collections.emptyList();
        }
        this.tokenCount = tokens.size();
        this.tokenIterator = tokens.iterator();
    }

    private List<String> tokenizeText(String inputText) {
        List<String> tokens = new ArrayList<>();
        int pos = 0;
        int length = inputText.length();
        WordMode currentMode = WordMode.SPACES;
        StringBuilder currentToken = new StringBuilder();
        while (pos < length) {
            char currentChar = inputText.charAt(pos);
            switch (currentMode) {
            case SPACES:
                currentMode = handleSpaces(currentToken, currentChar);
                break;
            case QUOTED:
                currentMode = handleQuoted(tokens, currentToken, currentChar);
                break;
            case ESCAPED:
                currentToken.append(currentChar);
                currentMode = WordMode.QUOTED;
                break;
            case SIMPLE:
                currentMode = handleSimple(tokens, currentToken, currentChar);
                break;
            case COMMENT:
                if (currentChar == '\r' || currentChar == '\n') {
                    currentMode = WordMode.SPACES;
                }
                break;
            default:
                throw new IllegalStateException(sm.getString("quotedStringTokenizer.tokenizeError",
                                inputText, Integer.valueOf(pos), currentMode));
            }
            pos++;
        }
        String possibleLastToken = currentToken.toString();
        if (!possibleLastToken.isEmpty()) {
            tokens.add(possibleLastToken);
        }
        return tokens;
    }

    private WordMode handleSimple(List<String> tokens, StringBuilder currentToken, char currentChar) {
        if (Character.isWhitespace(currentChar)) {
            tokens.add(currentToken.toString());
            currentToken.setLength(0);
            return WordMode.SPACES;
        } else {
            currentToken.append(currentChar);
        }
        return WordMode.SIMPLE;
    }

    private WordMode handleQuoted(List<String> tokens, StringBuilder currentToken, char currentChar) {
        if (currentChar == '"') {
            tokens.add(currentToken.toString());
            currentToken.setLength(0);
            return WordMode.SPACES;
        } else if (currentChar == '\\') {
            return WordMode.ESCAPED;
        } else {
            currentToken.append(currentChar);
        }
        return WordMode.QUOTED;
    }

    private WordMode handleSpaces(StringBuilder currentToken, char currentChar) {
        if (!Character.isWhitespace(currentChar)) {
            if (currentChar == '"') {
                return WordMode.QUOTED;
            } else if (currentChar == '#') {
                return WordMode.COMMENT;
            } else {
                currentToken.append(currentChar);
                return WordMode.SIMPLE;
            }
        }
        return WordMode.SPACES;
    }

    public boolean hasMoreTokens() {
        return tokenIterator.hasNext();
    }

    public String nextToken() {
        returnedTokens++;
        return tokenIterator.next();
    }

    public int countTokens() {
        return tokenCount - returnedTokens;
    }
}

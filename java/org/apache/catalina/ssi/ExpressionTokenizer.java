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
package org.apache.catalina.ssi;


/**
 * Parses an expression string to return the individual tokens. This is patterned similar to the StreamTokenizer in the
 * JDK but customized for SSI conditional expression parsing.
 */
public class ExpressionTokenizer {
    /** Token type for a string literal. */
    public static final int TOKEN_STRING = 0;
    /** Token type for the AND operator. */
    public static final int TOKEN_AND = 1;
    /** Token type for the OR operator. */
    public static final int TOKEN_OR = 2;
    /** Token type for the NOT operator. */
    public static final int TOKEN_NOT = 3;
    /** Token type for the equality operator. */
    public static final int TOKEN_EQ = 4;
    /** Token type for the not-equal operator. */
    public static final int TOKEN_NOT_EQ = 5;
    /** Token type for a right brace. */
    public static final int TOKEN_RBRACE = 6;
    /** Token type for a left brace. */
    public static final int TOKEN_LBRACE = 7;
    /** Token type for the greater-than-or-equal operator. */
    public static final int TOKEN_GE = 8;
    /** Token type for the less-than-or-equal operator. */
    public static final int TOKEN_LE = 9;
    /** Token type for the greater-than operator. */
    public static final int TOKEN_GT = 10;
    /** Token type for the less-than operator. */
    public static final int TOKEN_LT = 11;
    /** Token type indicating end of expression. */
    public static final int TOKEN_END = 12;
    private final char[] expr;
    private String tokenVal = null;
    private int index;
    private final int length;


    /**
     * Creates a new parser for the specified expression.
     *
     * @param expr The expression
     */
    public ExpressionTokenizer(String expr) {
        this.expr = expr.trim().toCharArray();
        this.length = this.expr.length;
    }


    /**
     * Checks if there are more tokens available.
     *
     * @return {@code true} if there are more tokens
     */
    public boolean hasMoreTokens() {
        return index < length;
    }


    /**
     * Returns the current index in the expression.
     *
     * @return the current index for error reporting purposes
     */
    public int getIndex() {
        return index;
    }


    /**
     * Checks if the given character is a meta character used for tokenization.
     *
     * @param c the character to check
     * @return {@code true} if the character is whitespace or an operator character
     */
    protected boolean isMetaChar(char c) {
        return Character.isWhitespace(c) || c == '(' || c == ')' || c == '!' || c == '<' || c == '>' || c == '|' ||
                c == '&' || c == '=';
    }


    /**
     * Parses the next token from the expression.
     *
     * @return the next token type constant
     */
    public int nextToken() {
        // Skip any leading white space
        while (index < length && Character.isWhitespace(expr[index])) {
            index++;
        }
        // Clear the current token val
        tokenVal = null;
        if (index == length) {
            return TOKEN_END; // End of string
        }
        int start = index;
        char currentChar = expr[index];
        char nextChar = (char) 0;
        index++;
        if (index < length) {
            nextChar = expr[index];
        }
        // Check for a known token start
        switch (currentChar) {
            case '(':
                return TOKEN_LBRACE;
            case ')':
                return TOKEN_RBRACE;
            case '=':
                return TOKEN_EQ;
            case '!':
                if (nextChar == '=') {
                    index++;
                    return TOKEN_NOT_EQ;
                }
                return TOKEN_NOT;
            case '|':
                if (nextChar == '|') {
                    index++;
                    return TOKEN_OR;
                }
                break;
            case '&':
                if (nextChar == '&') {
                    index++;
                    return TOKEN_AND;
                }
                break;
            case '>':
                if (nextChar == '=') {
                    index++;
                    return TOKEN_GE; // Greater than or equal
                }
                return TOKEN_GT; // Greater than
            case '<':
                if (nextChar == '=') {
                    index++;
                    return TOKEN_LE; // Less than or equal
                }
                return TOKEN_LT; // Less than
            default:
                // Otherwise it's a string
                break;
        }
        int end;
        if (currentChar == '"' || currentChar == '\'') {
            // It's a quoted string and the end is the next unescaped quote
            boolean escaped = false;
            start++;
            for (; index < length; index++) {
                if (expr[index] == '\\' && !escaped) {
                    escaped = true;
                    continue;
                }
                if (expr[index] == currentChar && !escaped) {
                    break;
                }
                escaped = false;
            }
            end = index;
            index++; // Skip the end quote
        } else if (currentChar == '/') {
            // It's a regular expression and the end is the next unescaped /
            boolean escaped = false;
            for (; index < length; index++) {
                if (expr[index] == '\\' && !escaped) {
                    escaped = true;
                    continue;
                }
                if (expr[index] == currentChar && !escaped) {
                    break;
                }
                escaped = false;
            }
            end = ++index;
        } else {
            // End is the next whitespace character
            for (; index < length; index++) {
                if (isMetaChar(expr[index])) {
                    break;
                }
            }
            end = index;
        }
        // Extract the string from the array
        this.tokenVal = new String(expr, start, end - start);
        return TOKEN_STRING;
    }


    /**
     * Returns the string value of the most recently parsed token.
     *
     * @return the string value if the token was of type TOKEN_STRING, otherwise {@code null}
     */
    public String getTokenValue() {
        return tokenVal;
    }
}
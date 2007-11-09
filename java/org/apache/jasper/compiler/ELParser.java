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

package org.apache.jasper.compiler;

/**
 * This class implements a parser for EL expressions.
 * 
 * It takes strings of the form xxx${..}yyy${..}zzz etc, and turn it into a
 * ELNode.Nodes.
 * 
 * Currently, it only handles text outside ${..} and functions in ${ ..}.
 * 
 * @author Kin-man Chung
 */

public class ELParser {

    private Token curToken; // current token

    private ELNode.Nodes expr;

    private ELNode.Nodes ELexpr;

    private int index; // Current index of the expression

    private String expression; // The EL expression
    
    private char type;

    private boolean escapeBS; // is '\' an escape char in text outside EL?

    private static final String reservedWords[] = { "and", "div", "empty",
            "eq", "false", "ge", "gt", "instanceof", "le", "lt", "mod", "ne",
            "not", "null", "or", "true" };

    public ELParser(String expression) {
        index = 0;
        this.expression = expression;
        expr = new ELNode.Nodes();
    }

    /**
     * Parse an EL expression
     * 
     * @param expression
     *            The input expression string of the form Char* ('${' Char*
     *            '}')* Char*
     * @return Parsed EL expression in ELNode.Nodes
     */
    public static ELNode.Nodes parse(String expression) {
        ELParser parser = new ELParser(expression);
        while (parser.hasNextChar()) {
            String text = parser.skipUntilEL();
            if (text.length() > 0) {
                parser.expr.add(new ELNode.Text(text));
            }
            ELNode.Nodes elexpr = parser.parseEL();
            if (!elexpr.isEmpty()) {
                parser.expr.add(new ELNode.Root(elexpr, parser.type));
            }
        }
        return parser.expr;
    }

    /**
     * Parse an EL expression string '${...}'
     * 
     * @return An ELNode.Nodes representing the EL expression TODO: Currently
     *         only parsed into functions and text strings. This should be
     *         rewritten for a full parser.
     */
    private ELNode.Nodes parseEL() {

        StringBuffer buf = new StringBuffer();
        ELexpr = new ELNode.Nodes();
        while (hasNext()) {
            curToken = nextToken();
            if (curToken instanceof Char) {
                if (curToken.toChar() == '}') {
                    break;
                }
                buf.append(curToken.toChar());
            } else {
                // Output whatever is in buffer
                if (buf.length() > 0) {
                    ELexpr.add(new ELNode.ELText(buf.toString()));
                }
                if (!parseFunction()) {
                    ELexpr.add(new ELNode.ELText(curToken.toString()));
                }
            }
        }
        if (buf.length() > 0) {
            ELexpr.add(new ELNode.ELText(buf.toString()));
        }

        return ELexpr;
    }

    /**
     * Parse for a function FunctionInvokation ::= (identifier ':')? identifier
     * '(' (Expression (,Expression)*)? ')' Note: currently we don't parse
     * arguments
     */
    private boolean parseFunction() {
        if (!(curToken instanceof Id) || isELReserved(curToken.toString())) {
            return false;
        }
        String s1 = null; // Function prefix
        String s2 = curToken.toString(); // Function name
        int mark = getIndex();
        if (hasNext()) {
            Token t = nextToken();
            if (t.toChar() == ':') {
                if (hasNext()) {
                    Token t2 = nextToken();
                    if (t2 instanceof Id) {
                        s1 = s2;
                        s2 = t2.toString();
                        if (hasNext()) {
                            t = nextToken();
                        }
                    }
                }
            }
            if (t.toChar() == '(') {
                ELexpr.add(new ELNode.Function(s1, s2));
                return true;
            }
        }
        setIndex(mark);
        return false;
    }

    /**
     * Test if an id is a reserved word in EL
     */
    private boolean isELReserved(String id) {
        int i = 0;
        int j = reservedWords.length;
        while (i < j) {
            int k = (i + j) / 2;
            int result = reservedWords[k].compareTo(id);
            if (result == 0) {
                return true;
            }
            if (result < 0) {
                i = k + 1;
            } else {
                j = k;
            }
        }
        return false;
    }

    /**
     * Skip until an EL expression ('${' || '#{') is reached, allowing escape
     * sequences '\\' and '\$' and '\#'.
     * 
     * @return The text string up to the EL expression
     */
    private String skipUntilEL() {
        char prev = 0;
        StringBuffer buf = new StringBuffer();
        while (hasNextChar()) {
            char ch = nextChar();
            if (prev == '\\') {
                prev = 0;
                if (ch == '\\') {
                    buf.append('\\');
                    if (!escapeBS)
                        prev = '\\';
                } else if (ch == '$' || ch == '#') {
                    buf.append(ch);
                }
                // else error!
            } else if (prev == '$' || prev == '#') {
                if (ch == '{') {
                    this.type = prev;
                    prev = 0;
                    break;
                }
                buf.append(prev);
                prev = 0;
            }
            if (ch == '\\' || ch == '$' || ch == '#') {
                prev = ch;
            } else {
                buf.append(ch);
            }
        }
        if (prev != 0) {
            buf.append(prev);
        }
        return buf.toString();
    }

    /*
     * @return true if there is something left in EL expression buffer other
     * than white spaces.
     */
    private boolean hasNext() {
        skipSpaces();
        return hasNextChar();
    }

    /*
     * @return The next token in the EL expression buffer.
     */
    private Token nextToken() {
        skipSpaces();
        if (hasNextChar()) {
            char ch = nextChar();
            if (Character.isJavaIdentifierStart(ch)) {
                StringBuffer buf = new StringBuffer();
                buf.append(ch);
                while ((ch = peekChar()) != -1
                        && Character.isJavaIdentifierPart(ch)) {
                    buf.append(ch);
                    nextChar();
                }
                return new Id(buf.toString());
            }

            if (ch == '\'' || ch == '"') {
                return parseQuotedChars(ch);
            } else {
                // For now...
                return new Char(ch);
            }
        }
        return null;
    }

    /*
     * Parse a string in single or double quotes, allowing for escape sequences
     * '\\', and ('\"', or "\'")
     */
    private Token parseQuotedChars(char quote) {
        StringBuffer buf = new StringBuffer();
        buf.append(quote);
        while (hasNextChar()) {
            char ch = nextChar();
            if (ch == '\\') {
                ch = nextChar();
                if (ch == '\\' || ch == quote) {
                    buf.append(ch);
                }
                // else error!
            } else if (ch == quote) {
                buf.append(ch);
                break;
            } else {
                buf.append(ch);
            }
        }
        return new QuotedString(buf.toString());
    }

    /*
     * A collection of low level parse methods dealing with character in the EL
     * expression buffer.
     */

    private void skipSpaces() {
        while (hasNextChar()) {
            if (expression.charAt(index) > ' ')
                break;
            index++;
        }
    }

    private boolean hasNextChar() {
        return index < expression.length();
    }

    private char nextChar() {
        if (index >= expression.length()) {
            return (char) -1;
        }
        return expression.charAt(index++);
    }

    private char peekChar() {
        if (index >= expression.length()) {
            return (char) -1;
        }
        return expression.charAt(index);
    }

    private int getIndex() {
        return index;
    }

    private void setIndex(int i) {
        index = i;
    }

    /*
     * Represents a token in EL expression string
     */
    private static class Token {

        char toChar() {
            return 0;
        }

        public String toString() {
            return "";
        }
    }

    /*
     * Represents an ID token in EL
     */
    private static class Id extends Token {
        String id;

        Id(String id) {
            this.id = id;
        }

        public String toString() {
            return id;
        }
    }

    /*
     * Represents a character token in EL
     */
    private static class Char extends Token {

        private char ch;

        Char(char ch) {
            this.ch = ch;
        }

        char toChar() {
            return ch;
        }

        public String toString() {
            return (new Character(ch)).toString();
        }
    }

    /*
     * Represents a quoted (single or double) string token in EL
     */
    private static class QuotedString extends Token {

        private String value;

        QuotedString(String v) {
            this.value = v;
        }

        public String toString() {
            return value;
        }
    }

    public char getType() {
        return type;
    }
}

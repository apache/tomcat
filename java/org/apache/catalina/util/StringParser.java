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


package org.apache.catalina.util;


/**
 * Utility class for string parsing that is higher performance than
 * StringParser for simple delimited text cases.  Parsing is performed
 * by setting the string, and then using the <code>findXxxx()</code> and
 * <code>skipXxxx()</code> families of methods to remember significant
 * offsets.  To retrieve the parsed substrings, call the <code>extract()</code>
 * method with the appropriate saved offset values.
 *
 * @author Craig R. McClanahan
 *
 * @deprecated Unused. Will be removed in Tomcat 9 onwards.
 */
@Deprecated
public final class StringParser {


    // ----------------------------------------------------------- Constructors


    /**
     * Construct a string parser with no preset string to be parsed.
     */
    public StringParser() {

        this(null);

    }


    /**
     * Construct a string parser that is initialized to parse the specified
     * string.
     *
     * @param string The string to be parsed
     */
    public StringParser(String string) {

        super();
        setString(string);

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The characters of the current string, as a character array.  Stored
     * when the string is first specified to speed up access to characters
     * being compared during parsing.
     */
    private char chars[] = null;


    /**
     * The zero-relative index of the current point at which we are
     * positioned within the string being parsed.  <strong>NOTE</strong>:
     * the value of this index can be one larger than the index of the last
     * character of the string (i.e. equal to the string length) if you
     * parse off the end of the string.  This value is useful for extracting
     * substrings that include the end of the string.
     */
    private int index = 0;


    /**
     * The length of the String we are currently parsing.  Stored when the
     * string is first specified to avoid repeated recalculations.
     */
    private int length = 0;


    /**
     * The String we are currently parsing.
     */
    private String string = null;


    // ------------------------------------------------------------- Properties


    /**
     * Return the zero-relative index of our current parsing position
     * within the string being parsed.
     */
    public int getIndex() {

        return (this.index);

    }


    /**
     * Return the length of the string we are parsing.
     */
    public int getLength() {
        return length;
    }


    /**
     * Set the String we are currently parsing.  The parser state is also reset
     * to begin at the start of this string.
     *
     * @param string The string to be parsed.
     */
    public void setString(String string) {

        this.string = string;
        if (string != null) {
            this.length = string.length();
            chars = this.string.toCharArray();
        } else {
            this.length = 0;
            chars = new char[0];
        }
        reset();
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Advance the current parsing position by one, if we are not already
     * past the end of the string.
     */
    public void advance() {

        if (index < length)
            index++;
    }


    /**
     * Extract and return a substring that starts at the specified position,
     * and ends at the character before the specified position.  If this is
     * not possible, a zero-length string is returned.
     *
     * @param start Starting index, zero relative, inclusive
     * @param end Ending index, zero relative, exclusive
     */
    public String extract(int start, int end) {

        if ((start < 0) || (start >= end) || (end > length))
            return ("");
        else
            return (string.substring(start, end));

    }


    /**
     * Return the index of the next occurrence of the specified character,
     * or the index of the character after the last position of the string
     * if no more occurrences of this character are found.  The current
     * parsing position is updated to the returned value.
     *
     * @param ch Character to be found
     */
    public int findChar(char ch) {

        while ((index < length) && (ch != chars[index]))
            index++;
        return (index);

    }


    /**
     * Reset the current state of the parser to the beginning of the
     * current string being parsed.
     */
    public void reset() {
        index = 0;
    }
}

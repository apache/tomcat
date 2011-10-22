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


package org.apache.tomcat.lite.util;

import java.util.ArrayList;
import java.util.Locale;
import java.util.TreeMap;


/**
 * Utility class for string parsing that is higher performance than
 * StringParser for simple delimited text cases.  Parsing is performed
 * by setting the string, and then using the <code>findXxxx()</code> and
 * <code>skipXxxx()</code> families of methods to remember significant
 * offsets.  To retrieve the parsed substrings, call the <code>extract()</code>
 * method with the appropriate saved offset values.
 *
 * @author Craig R. McClanahan
 */
public final class LocaleParser {

    public LocaleParser() {
        this(null);
    }

    public LocaleParser(String string) {
        setString(string);
    }

    public TreeMap parseLocale(String value) {
      // Store the accumulated languages that have been requested in
      // a local collection, sorted by the quality value (so we can
      // add Locales in descending order).  The values will be ArrayLists
      // containing the corresponding Locales to be added
      TreeMap locales = new TreeMap();

      // Preprocess the value to remove all whitespace
      int white = value.indexOf(' ');
      if (white < 0)
          white = value.indexOf('\t');
      if (white >= 0) {
          StringBuilder sb = new StringBuilder();
          int len = value.length();
          for (int i = 0; i < len; i++) {
              char ch = value.charAt(i);
              if ((ch != ' ') && (ch != '\t'))
                  sb.append(ch);
          }
          value = sb.toString();
      }

      LocaleParser parser = this;
      // Process each comma-delimited language specification
      parser.setString(value);        // ASSERT: parser is available to us
      int length = parser.getLength();
      while (true) {

          // Extract the next comma-delimited entry
          int start = parser.getIndex();
          if (start >= length)
              break;
          int end = parser.findChar(',');
          String entry = parser.extract(start, end).trim();
          parser.advance();   // For the following entry

          // Extract the quality factor for this entry
          double quality = 1.0;
          int semi = entry.indexOf(";q=");
          if (semi >= 0) {
              try {
                  quality = Double.parseDouble(entry.substring(semi + 3));
              } catch (NumberFormatException e) {
                  quality = 0.0;
              }
              entry = entry.substring(0, semi);
          }

          // Skip entries we are not going to keep track of
          if (quality < 0.00005)
              continue;       // Zero (or effectively zero) quality factors
          if ("*".equals(entry))
              continue;       // FIXME - "*" entries are not handled

          // Extract the language and country for this entry
          String language = null;
          String country = null;
          String variant = null;
          int dash = entry.indexOf('-');
          if (dash < 0) {
              language = entry;
              country = "";
              variant = "";
          } else {
              language = entry.substring(0, dash);
              country = entry.substring(dash + 1);
              int vDash = country.indexOf('-');
              if (vDash > 0) {
                  String cTemp = country.substring(0, vDash);
                  variant = country.substring(vDash + 1);
                  country = cTemp;
              } else {
                  variant = "";
              }
          }

          // Add a new Locale to the list of Locales for this quality level
          Locale locale = new Locale(language, country, variant);
          Double key = new Double(-quality);  // Reverse the order
          ArrayList values = (ArrayList) locales.get(key);
          if (values == null) {
              values = new ArrayList();
              locales.put(key, values);
          }
          values.add(locale);

      }

      return locales;
    }

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

        return (this.length);

    }


    /**
     * Return the String we are currently parsing.
     */
    public String getString() {

        return (this.string);

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
     * and extends to the end of the string being parsed.  If this is not
     * possible, a zero-length string is returned.
     *
     * @param start Starting index, zero relative, inclusive
     */
    public String extract(int start) {

        if ((start < 0) || (start >= length))
            return ("");
        else
            return (string.substring(start));

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
     * Return the index of the next occurrence of a non-whitespace character,
     * or the index of the character after the last position of the string
     * if no more non-whitespace characters are found.  The current
     * parsing position is updated to the returned value.
     */
    public int findText() {

        while ((index < length) && isWhite(chars[index]))
            index++;
        return (index);

    }


    /**
     * Return the index of the next occurrence of a whitespace character,
     * or the index of the character after the last position of the string
     * if no more whitespace characters are found.  The current parsing
     * position is updated to the returned value.
     */
    public int findWhite() {

        while ((index < length) && !isWhite(chars[index]))
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


    /**
     * Advance the current parsing position while it is pointing at the
     * specified character, or until it moves past the end of the string.
     * Return the final value.
     *
     * @param ch Character to be skipped
     */
    public int skipChar(char ch) {

        while ((index < length) && (ch == chars[index]))
            index++;
        return (index);

    }


    /**
     * Advance the current parsing position while it is pointing at a
     * non-whitespace character, or until it moves past the end of the string.
     * Return the final value.
     */
    public int skipText() {

        while ((index < length) && !isWhite(chars[index]))
            index++;
        return (index);

    }


    /**
     * Advance the current parsing position while it is pointing at a
     * whitespace character, or until it moves past the end of the string.
     * Return the final value.
     */
    public int skipWhite() {

        while ((index < length) && isWhite(chars[index]))
            index++;
        return (index);

    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Is the specified character considered to be whitespace?
     *
     * @param ch Character to be checked
     */
    protected boolean isWhite(char ch) {

        if ((ch == ' ') || (ch == '\t') || (ch == '\r') || (ch == '\n'))
            return (true);
        else
            return (false);

    }


}

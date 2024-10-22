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
package org.apache.tomcat.util.http;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * The <code>IfHeader</code> class represents the state lists defined
 * through the HTTP <em>If</em> header, which is specified in RFC 2518 as
 * follows :
 * <pre>
 *    If = "If" ":" ( 1*No-tag-list | 1*Tagged-list)
 *    No-tag-list = List
 *    Tagged-list = Resource 1*List
 *    Resource = Coded-URL
 *    List = "(" 1*(["Not"](State-etag | "[" entity-tag "]")) ")"
 *    State-etag = Coded-URL
 *    Coded-URL = "&lt;" absoluteURI "&gt;"
 * </pre>
 * <p>
 * Reformulating this specification into proper EBNF as specified by N. Wirth
 * we get the following productions, which map to the parse METHODS of this
 * class. Any whitespace is ignored except for white space surrounding and
 * within words which is considered significant.
 * <pre>
 *    If = "If:" ( Tagged | Untagged ).
 *    Tagged = { "&lt;" Word "&gt;" Untagged } .
 *    Untagged = { "(" IfList ")" } .
 *    IfList = { [ "Not" ] ( ("&lt;" Word "&gt;" ) | ( "[" Word "]" ) ) } .
 *    Word = characters .
 * </pre>
 * <p>
 * An <em>If</em> header either contains untagged <em>IfList</em> entries or
 * tagged <em>IfList</em> entries but not a mixture of both. An <em>If</em>
 * header containing tagged entries is said to be of <em>tagged</em> type while
 * an <em>If</em> header containing untagged entries is said to be of
 * <em>untagged</em> type.
 * <p>
 * An <em>IfList</em> is a list of tokens - words enclosed in <em>&lt; &gt;</em>
 * - and etags - words enclosed in <em>[ ]</em>. An <em>IfList</em> matches a
 * (token, etag) tuple if all entries in the list match. If an entry in the list
 * is prefixed with the word <em>Not</em> (parsed case insensitively) the entry
 * must not match the concrete token or etag.
 * <p>
 * Example: The <em>ifList</em> <code>(&lt;token&gt; [etag])</code> only matches
 * if the concret token has the value <code>token</code> and the conrete etag
 * has the value <code>etag</code>. On the other hand, the <em>ifList</em>
 * <code>(Not &lt;notoken&gt;)</code> matches any token which is not
 * <code>notoken</code> (in this case the concrete value of the etag is
 * not taken into consideration).
 *
 * This class was contributed by Apache Jackrabbit
 *
 * @author Felix Meschberger
 */
public class WebdavIfHeader {

    private static final Log log = LogFactory.getLog(WebdavIfHeader.class);
    private static final StringManager sm =
            StringManager.getManager(WebdavIfHeader.class.getPackage().getName());

    /**
     * The string representation of the header value
     */
    private final String headerValue;

    /**
     * The list of untagged state entries
     */
    private final IfHeaderInterface ifHeader;

    /**
     * The list of resources present in the If header.
     */
    private List<String> resources = new ArrayList<>();

    /**
     * The list of all positive tokens present in the If header.
     */
    private List<String> allTokens = new ArrayList<>();

    /**
     * The list of all NOT tokens present in the If header.
     */
    private List<String> allNotTokens = new ArrayList<>();

    private String uriPrefix;

    /**
     * Create a Untagged <code>IfHeader</code> if the given lock tokens.
     *
     * @param tokens the tokens
     *
     * @throws IOException If the parsing of the <code>IfHeader</code> fails
     */
    public WebdavIfHeader(String[] tokens) throws IOException {
        allTokens.addAll(Arrays.asList(tokens));
        StringBuffer b = new StringBuffer();
        for (String token : tokens) {
            b.append("(").append("<");
            b.append(token);
            b.append(">").append(")");
        }
        headerValue = b.toString();
        ifHeader = parse();
    }

    /**
     * Parses the <em>If</em> header and creates and internal representation
     * which is easy to query.
     *
     * @param uriPrefix The uri prefix to use for the absolute href
     * @param ifHeaderValue the if header
     *
     * @throws IOException If the parsing of the <code>IfHeader</code> fails
     */
    public WebdavIfHeader(String uriPrefix, String ifHeaderValue) throws IOException {
        this.uriPrefix = uriPrefix;
        headerValue = ifHeaderValue;
        ifHeader = parse();
    }

    /**
     * @return If String.
     */
    public String getHeaderName() {
        return "If";
    }

    /**
     * Return the String representation of the If header present on
     * the given request or <code>null</code>.
     *
     * @return If header value as String or <code>null</code>.
     */
    public String getHeaderValue() {
        return headerValue;
    }

    /**
     * Returns true if an If header was present in the given request. False otherwise.
     *
     * @return  true if an If header was present.
     */
    public boolean hasValue() {
        return ifHeader != null;
    }

    /**
     * Tries to match the contents of the <em>If</em> header with the given
     * token and etag values with the restriction to only check for the tag.
     * <p>
     * If the <em>If</em> header is of untagged type, the untagged <em>IfList</em>
     * is matched against the token and etag given: A match of the token and
     * etag is found if at least one of the <em>IfList</em> entries match the
     * token and etag tuple.
     *
     * @param tag The tag to identify the <em>IfList</em> to match the token
     * and etag against.
     * @param tokens The tokens to compare.
     * @param etag The ETag value to compare.
     *
     * @return If the <em>If</em> header is of untagged type the result is
     *      <code>true</code> if any of the <em>IfList</em> entries matches
     *      the token and etag values. For tagged type <em>If</em> header the
     *      result is <code>true</code> if either no entry for the given tag
     *      exists in the <em>If</em> header or if the <em>IfList</em> for the
     *      given tag matches the token and etag given.
     */
    public boolean matches(String tag, List<String> tokens, String etag) {
        if (ifHeader == null) {
            if (log.isTraceEnabled()) {
                log.trace("matches: No If header, assume match");
            }
            return true;
        } else {
            return ifHeader.matches(tag, tokens, etag);
        }
    }

    /**
     * @return an iterator over all resources present in the if header.
     */
    public Iterator<String> getResources() {
        return resources.iterator();
    }

    /**
     * @return an iterator over all tokens present in the if header, that were
     * not denied by a leading NOT statement.
     */
    public Iterator<String> getAllTokens() {
        return allTokens.iterator();
    }

    /**
     * @return an iterator over all NOT tokens present in the if header, that
     * were explicitly denied.
     */
    public Iterator<String> getAllNotTokens() {
        return allNotTokens.iterator();
    }

    /**
     * Parse the original header value and build the internal IfHeaderInterface
     * object that is easy to query.
     */
    private IfHeaderInterface parse()
            throws IOException  {
        IfHeaderInterface ifHeader;
        if (headerValue != null && headerValue.length() > 0) {
            StringReader reader = null;
            int firstChar = 0;

            try {
                reader = new StringReader(headerValue);
                // get the first character to decide - expect '(' or '<'
                try {
                    reader.mark(1);
                    firstChar = readWhiteSpace(reader);
                    reader.reset();
                } catch (IOException ignore) {
                    // may be thrown according to API but is only thrown by the
                    // StringReader class if the reader is already closed.
                }

                if (firstChar == '(') {
                    ifHeader = parseUntagged(reader);
                } else if (firstChar == '<') {
                    ifHeader = parseTagged(reader);
                } else {
                    logIllegalState("If", firstChar, "(<", null);
                    ifHeader = null;
                }

            } finally  {
                if (reader != null) {
                    reader.close();
                }
            }

        } else {
            if (log.isTraceEnabled()) {
                log.trace("IfHeader: No If header in request");
            }
            ifHeader = null;
        }
        return ifHeader;
    }

    //---------- internal IF header parser -------------------------------------
    /**
     * Parses a tagged type <em>If</em> header. This method implements the
     * <em>Tagged</em> production given in the class comment :
     * <pre>
     *    Tagged = { "<" Word ">" Untagged } .
     * </pre>
     *
     * @param reader the reader
     * @return the parsed map
     */
    private IfHeaderMap parseTagged(StringReader reader)
        throws IOException {
        IfHeaderMap map = new IfHeaderMap();
        while (true) {
            // read next non-white space
            int c = readWhiteSpace(reader);
            if (c < 0) {
                // end of input, no more entries
                break;
            } else if (c == '<') {
                // start a tag with an IfList
                String resource = readWord(reader, '>');
                if (resource != null) {
                    // go to untagged after reading the resource
                    map.put(resource, parseUntagged(reader));
                    resources.add(resource);
                } else {
                    break;
                }
            } else {
                // unexpected character
                // catchup to end of input or start of a tag
                logIllegalState("Tagged", c, "<", reader);
            }
        }

        return map;
    }

    /**
     * Parses an untagged type <em>If</em> header. This method implements the
     * <em>Untagged</em> production given in the class comment :
     * <pre>
     *    Untagged = { "(" IfList ")" } .
     * </pre>
     *
     * @param reader The <code>StringReader</code> to read from for parsing
     *
     * @return An <code>ArrayList</code> of {@link IfList} entries.
     */
    private IfHeaderList parseUntagged(StringReader reader)
            throws IOException  {
        IfHeaderList list = new IfHeaderList();
        while (true) {
            // read next non white space
            reader.mark(1);
            int c = readWhiteSpace(reader);
            if (c < 0) {
                // end of input, no more IfLists
                break;

            } else if (c == '(') {
                // start of an IfList, parse
                list.add(parseIfList(reader));

            } else if (c == '<') {
                // start of a tag, return current list
                reader.reset();
                break;

            } else {
                // unexpected character
                // catchup to end of input or start of an IfList
                logIllegalState("Untagged", c, "(", reader);
            }
        }
        return list;
    }

    /**
     * Parses an <em>IfList</em> in the <em>If</em> header. This method
     * implements the <em>Tagged</em> production given in the class comment :
     * <pre>
     *    IfList = { [ "Not" ] ( ("<" Word ">" ) | ( "[" Word "]" ) ) } .
     * </pre>
     *
     * @param reader The <code>StringReader</code> to read from for parsing
     *
     * @return The {@link IfList} for the input <em>IfList</em>.
     *
     * @throws IOException if a problem occurs during reading.
     */
    private IfList parseIfList(StringReader reader) throws IOException {
        IfList res = new IfList();
        boolean positive = true;
        String word;

        ReadLoop:
        while (true) {
            int nextChar = readWhiteSpace(reader);
            switch (nextChar) {
                case 'N':
                case 'n':
                    // read not

                    // check whether o or O
                    int not = reader.read();
                    if (not != 'o' && not != 'O') {
                        logIllegalState("IfList-Not", not, "o", null);
                        break;
                    }

                    // check whether t or T
                    not = reader.read();
                    if (not !='t' && not != 'T') {
                        logIllegalState("IfList-Not", not, "t", null);
                        break;
                    }

                    // read Not ok
                    positive = false;
                    break;

                case '<':
                    // state token
                    word = readWord(reader, '>');
                    if (word != null) {
                        res.add(new IfListEntryToken(word, positive));
                        // also add the token to the list of all tokens
                        if (positive) {
                            allTokens.add(word);
                        } else {
                            allNotTokens.add(word);
                        }
                        positive = true;
                    }
                    break;

                case '[':
                    // etag
                    word = readWord(reader, ']');
                    if (word != null) {
                        res.add(new IfListEntryEtag(word, positive));
                        positive = true;
                    }
                    break;

                case ')':
                    // correct end of list, end the loop
                    if (log.isTraceEnabled()) {
                        log.trace("parseIfList: End of If list, terminating loop");
                    }
                    break ReadLoop;

                default:
                    logIllegalState("IfList", nextChar, "nN<[)", reader);

                    // abort loop if EOF
                    if (nextChar < 0) {
                        break ReadLoop;
                    }

                    break;
            }
        }

        // return the current list anyway
        return res;
    }

    /**
     * Returns the first non-whitespace character from the reader or -1 if
     * the end of the reader is encountered.
     *
     * @param reader The <code>Reader</code> to read from
     *
     * @return The first non-whitespace character or -1 in case of EOF.
     *
     * @throws IOException if a problem occurs during reading.
     */
    private int readWhiteSpace(Reader reader) throws IOException {
        int c = reader.read();
        while (c >= 0 && Character.isWhitespace((char) c)) {
             c = reader.read();
        }
        return c;
    }

    /**
     * Reads from the input until the end character is encountered and returns
     * the string up to but not including this end character. If the end of input
     * is reached before reading the end character <code>null</code> is
     * returned.
     * <p>
     * Note that this method does not support any escaping.
     *
     * @param reader The <code>Reader</code> to read from
     * @param end The ending character limiting the word.
     *
     * @return The string read up to but not including the ending character or
     *      <code>null</code> if the end of input is reached before the ending
     *      character has been read.
     *
     * @throws IOException if a problem occurs during reading.
     */
    private String readWord(Reader reader, char end) throws IOException {
        StringBuffer buf = new StringBuffer();

        // read the word value
        int c = reader.read();
        for (; c >= 0 && c != end; c=reader.read()) {
            buf.append((char) c);
        }

        // check whether we succeeded
        if (c < 0) {
            log.error("readWord: Unexpected end of input reading word");
            return null;
        }

        // build the string and return it
        return buf.toString();
    }

    /**
     * Logs an unexpected character with the corresponding state and list of
     * expected characters. If the reader parameter is not null, characters
     * are read until either the end of the input is reached or any of the
     * characters in the expChar string is read.
     *
     * @param state The name of the current parse state. This method logs this
     *      name in the message. The intended value would probably be the
     *      name of the EBNF production during which the error occurs.
     * @param effChar The effective character read.
     * @param expChar The list of characters acceptable in the current state.
     * @param reader The reader to be caught up to any of the expected
     *      characters. If <code>null</code> the input is not caught up to
     *      any of the expected characters (of course ;-).
     */
    private void logIllegalState(String state, int effChar, String expChar,
                                 StringReader reader) {

        // format the effective character to be logged
        String effString = (effChar < 0) ? "<EOF>" : String.valueOf((char) effChar);

        // log the error
        log.error(sm.getString("webdavifheader.unexpectedCharacter", effString, state, expChar));

        // catch up if a reader is given
        if (reader != null && effChar >= 0) {
            try {
                if (log.isTraceEnabled()) {
                    log.trace("logIllegalState: Catch up to any of "+expChar);
                }
                do {
                    reader.mark(1);
                    effChar = reader.read();
                } while (effChar >= 0 && expChar.indexOf(effChar) < 0);
                if (effChar >= 0) {
                    reader.reset();
                }
            } catch (IOException ioe) {
                log.error(sm.getString("webdavifheader.ioError", expChar));
            }
        }
    }

    //---------- internal If header structure ----------------------------------

    /**
     * The <code>IfListEntry</code> abstract class is the base class for
     * entries in an <em>IfList</em> production. This abstract base class
     * provides common functionality to both types of entries, namely tokens
     * enclosed in angle brackets (<code>&lt; &gt;</code>) and etags enclosed
     * in square brackets (<code>[ ]</code>).
     */
    private abstract static class IfListEntry {

        /**
         * The entry string value - the semantics of this value depends on the
         * implementing class.
         */
        protected final String value;

        /** Flag to indicate, whether this is a positive match or not */
        protected final boolean positive;

        /** The cached result of the {@link #toString} method. */
        protected String stringValue;

        /**
         * Sets up the final fields of this abstract class. The meaning of
         * value parameter depends solely on the implementing class. From the
         * point of view of this abstract class, it is simply a string value.
         *
         * @param value The string value of this instance
         * @param positive <code>true</code> if matches are positive
         */
        protected IfListEntry(String value, boolean positive) {
            this.value = value;
            this.positive = positive;
        }

        /**
         * Matches the value from the parameter to the internal string value.
         * If the parameter and the {@link #value} field match, the method
         * returns <code>true</code> for positive matches and <code>false</code>
         * for negative matches.
         * <p>
         * This helper method can be called by implementations to evaluate the
         * concrete match on the correct value parameter. See
         * {@link #match(String, String)} for the external API method.
         *
         * @param value The string value to compare to the {@link #value}
         *      field.
         *
         * @return <code>true</code> if the value parameter and the
         *      {@link #value} field match and the {@link #positive} field is
         *      <code>true</code> or if the values do not match and the
         *      {@link #positive} field is <code>false</code>.
         */
        protected boolean match(String value) {
            return positive == this.value.equals(value);
        }

        /**
         * Matches the entry's value to the the token or etag. Depending on the
         * concrete implementation, only one of the parameters may be evaluated
         * while the other may be ignored.
         * <p>
         * Implementing METHODS may call the helper method {@link #match(String)}
         * for the actual matching.
         *
         * @param token The token value to compare
         * @param etag The etag value to compare
         *
         * @return <code>true</code> if the token/etag matches the <em>IfList</em>
         *      entry.
         */
        public abstract boolean match(String token, String etag);

        /**
         * Returns a short type name for the implementation. This method is
         * used by the {@link #toString} method to build the string representation
         * if the instance.
         *
         * @return The type name of the implementation.
         */
        protected abstract String getType();

        /**
         * @return the value of this entry
         */
        protected String getValue() {
            return value;
        }

        /**
         * Returns the String representation of this entry. This method uses the
         * {@link #getType} to build the string representation.
         *
         * @return the String representation of this entry.
         */
        @Override
        public String toString() {
            if (stringValue == null) {
                stringValue = getType() + ": " + (positive ? "" : "!") + getValue();
            }
            return stringValue;
        }
    }

    /**
     * The <code>IfListEntryToken</code> extends the {@link IfListEntry}
     * abstract class to represent an entry for token matching.
     */
    private static class IfListEntryToken extends IfListEntry {

        /**
         * Creates a token matching entry.
         *
         * @param token The token value pertinent to this instance.
         * @param positive <code>true</code> if this is a positive match entry.
         */
        IfListEntryToken(String token, boolean positive) {
            super(token, positive);
        }

        /**
         * Matches the token parameter to the stored token value and returns
         * <code>true</code> if the values match and if the match is positive.
         * <code>true</code> is also returned for negative matches if the values
         * do not match.
         *
         * @param token The token value to compare
         * @param etag The etag value to compare, which is ignored in this
         *      implementation.
         *
         * @return <code>true</code> if the token matches the <em>IfList</em>
         *      entry's token value.
         */
        @Override
        public boolean match(String token, String etag) {
            return token == null || super.match(token);
        }

        /**
         * Returns the type name of this implementation, which is fixed to
         * be <em>Token</em>.
         *
         * @return The fixed string <em>Token</em> as the type name.
         */
        @Override
        protected String getType() {
            return "Token";
        }
    }

    /**
     * The <code>IfListEntryToken</code> extends the {@link IfListEntry}
     * abstract class to represent an entry for etag matching.
     */
    private static class IfListEntryEtag extends IfListEntry {

        /**
         * Creates an etag matching entry.
         *
         * @param etag The etag value pertinent to this instance.
         * @param positive <code>true</code> if this is a positive match entry.
         */
        IfListEntryEtag(String etag, boolean positive) {
            super(etag, positive);
        }

        /**
         * Matches the etag parameter to the stored etag value and returns
         * <code>true</code> if the values match and if the match is positive.
         * <code>true</code> is also returned for negative matches if the values
         * do not match.
         *
         * @param token The token value to compare, which is ignored in this
         *      implementation.
         * @param etag The etag value to compare
         *
         * @return <code>true</code> if the etag matches the <em>IfList</em>
         *      entry's etag value.
         */
        @Override
        public boolean match(String token, String etag) {
            return super.match(etag);
        }

        /**
         * Returns the type name of this implementation, which is fixed to
         * be <em>ETag</em>.
         *
         * @return The fixed string <em>ETag</em> as the type name.
         */
        @Override
        protected String getType() {
            return "ETag";
        }
    }

    /**
     * The <code>IfList</code> class extends the <code>ArrayList</code> class
     * with the limitation to only support adding {@link IfListEntry} objects
     * and adding a {@link #match} method.
     * <p>
     * This class is a container for data contained in the <em>If</em>
     * production <em>IfList</em>
     * <pre>
     *    IfList = { [ "Not" ] ( ("&lt;" Word "&gt;" ) | ( "[" Word "]" ) ) } .
     * </pre>
     * <p>
     */
    private static class IfList extends ArrayList<IfListEntry> {

        private static final long serialVersionUID = 1L;

        /**
         * Adds the {@link IfListEntry} at the end of the list.
         *
         * @param entry The {@link IfListEntry} to add to the list
         *
         * @return <code>true</code> (as per the general contract of Collection.add).
         */
        @Override
        public boolean add(IfListEntry entry) {
            return super.add(entry);
        }

        /**
         * Adds the {@link IfListEntry} at the indicated position of the list.
         *
         * @param index the index
         * @param entry the entry
         *
         * @throws IndexOutOfBoundsException if index is out of range
         *      <code>(index &lt; 0 || index &gt; size())</code>.
         */
        @Override
        public void add(int index, IfListEntry entry) {
            super.add(index, entry);
        }

        /**
         * Returns <code>true</code> if all {@link IfListEntry} objects in the
         * list match the given token and etag. If the list is entry, it is
         * considered to match the token and etag.
         *
         * @param tokens The token to compare.
         * @param etag The etag to compare.
         *
         * @return <code>true</code> if all entries in the list match the
         *      given tag and token.
         */
        public boolean match(List<String> tokens, String etag) {
            if (log.isTraceEnabled()) {
                log.trace("match: Trying to match token=" + tokens + ", etag=" + etag);
            }
            for (int i=0; i < size(); i++) {
                IfListEntry ile = get(i);
                boolean match = false;
                for (String token : tokens) {
                    if (ile.match(token, etag)) {
                        match = true;
                    }
                }
                if (!match) {
                    if (log.isTraceEnabled()) {
                        log.trace("match: Entry " + i + "-" + ile + " does not match");
                    }
                    return false;
                }
            }
            // invariant: all entries matched

            return true;
        }
    }

    /**
     * The <code>IfHeaderInterface</code> interface abstracts away the difference of
     * tagged and untagged <em>If</em> header lists. The single method provided
     * by this interface is to check whether a request may be applied to a
     * resource with given token and etag.
     */
    private interface IfHeaderInterface {

        /**
         * Matches the resource, token, and etag against this
         * <code>IfHeaderInterface</code> instance.
         *
         * @param resource The resource to match this instance against. This
         *      must be absolute URI of the resource as defined in Section 3
         *      (URI Syntactic Components) of RFC 2396 Uniform Resource
         *      Identifiers (URI): Generic Syntax.
         * @param tokens The resource's lock token to match
         * @param etag The resource's etag to match
         *
         * @return <code>true</code> if the header matches the resource with
         *      token and etag, which means that the request is applicable
         *      to the resource according to the <em>If</em> header.
         */
        boolean matches(String resource, List<String> tokens, String etag);
    }

    /**
     * The <code>IfHeaderList</code> class implements the {@link IfHeaderInterface}
     * interface to support untagged lists of {@link IfList}s. This class
     * implements the data container for the production :
     * <pre>
     *    Untagged = { "(" IfList ")" } .
     * </pre>
     */
    private static class IfHeaderList extends ArrayList<IfList> implements IfHeaderInterface {

        private static final long serialVersionUID = 1L;

        /**
         * Matches a list of {@link IfList}s against the token and etag. If any of
         * the {@link IfList}s matches, the method returns <code>true</code>.
         * On the other hand <code>false</code> is only returned if non of the
         * {@link IfList}s match.
         *
         * @param resource The resource to match, which is ignored by this
         *      implementation. A value of <code>null</code> is therefor
         *      acceptable.
         * @param tokens The tokens to compare.
         * @param etag The ETag value to compare.
         *
         * @return <code>True</code> if any of the {@link IfList}s matches the token
         *      and etag, else <code>false</code> is returned.
         */
        @Override
        public boolean matches(String resource, List<String> tokens, String etag) {
            if (log.isTraceEnabled()) {
                log.trace("matches: Trying to match token=" + tokens + ", etag=" + etag);
            }

            for (IfList il : this) {
                if (il.match(tokens, etag)) {
                    if (log.isTraceEnabled()) {
                        log.trace("matches: Found match with " + il);
                    }
                    return true;
                }
            }
            // invariant: no match found

            return false;
        }
    }

    /**
     * The <code>IfHeaderMap</code> class implements the {@link IfHeaderInterface}
     * interface to support tagged lists of {@link IfList}s. This class
     * implements the data container for the production :
     * <pre>
     *    Tagged = { "&lt;" Word "&gt;" "(" IfList ")" } .
     * </pre>
     */
    private class IfHeaderMap extends HashMap<String, IfHeaderList> implements IfHeaderInterface {

        private static final long serialVersionUID = 1L;

        /**
         * Matches the token and etag for the given resource. If the resource is
         * not mentioned in the header, a match is assumed and <code>true</code>
         * is returned in this case.
         *
         * @param resource The absolute URI of the resource for which to find
         *      a match.
         * @param tokens The tokens to compare.
         * @param etag The etag to compare.
         *
         * @return <code>true</code> if either no entry exists for the resource
         *      or if the entry for the resource matches the token and etag.
         */
        @Override
        public boolean matches(String resource, List<String> tokens, String etag) {
            if (log.isTraceEnabled()) {
                log.trace("matches: Trying to match resource=" + resource + ", token=" + tokens + "," + etag);
            }

            String uri;
            String path;
            if (resource.startsWith("/")) {
                path = resource;
                uri = WebdavIfHeader.this.uriPrefix + resource;
            } else {
                path = resource.substring(WebdavIfHeader.this.uriPrefix.length());
                uri = resource;
            }
            IfHeaderList list = get(path);
            if (list == null) {
                list = get(uri);
            }
            if (list == null) {
                if (log.isTraceEnabled()) {
                    log.trace("matches: No entry for tag " + resource + ", assuming mismatch");
                }
                return false;
            } else {
                return list.matches(resource, tokens, etag);
            }
        }
    }
}

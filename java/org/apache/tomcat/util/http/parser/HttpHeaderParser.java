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
import java.nio.ByteBuffer;

import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.HeaderUtil;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.res.StringManager;

public class HttpHeaderParser {

    private static final StringManager sm = StringManager.getManager(HttpHeaderParser.class);

    private static final byte CR = (byte) '\r';
    private static final byte LF = (byte) '\n';
    private static final byte SP = (byte) ' ';
    private static final byte HT = (byte) '\t';
    private static final byte COLON = (byte) ':';
    private static final byte A = (byte) 'A';
    private static final byte a = (byte) 'a';
    private static final byte Z = (byte) 'Z';
    private static final byte LC_OFFSET = A - a;

    private final HeaderDataSource source;
    private final MimeHeaders headers;
    private final boolean tolerantEol;
    private final HeaderParseData headerData = new HeaderParseData();

    private HeaderParsePosition headerParsePos = HeaderParsePosition.HEADER_START;
    private byte prevChr = 0;
    private byte chr = 0;


    public HttpHeaderParser(HeaderDataSource source, MimeHeaders headers, boolean tolerantEol) {
        this.source = source;
        this.headers = headers;
        this.tolerantEol = tolerantEol;
    }


    public void recycle() {
        chr = 0;
        prevChr = 0;
        headerParsePos = HeaderParsePosition.HEADER_START;
        headerData.recycle();
    }


    /**
     * Parse an HTTP header.
     *
     * @return One of {@link HeaderParseStatus#NEED_MORE_DATA}, {@link HeaderParseStatus#HAVE_MORE_HEADERS} or
     *             {@link HeaderParseStatus#DONE}.
     *
     * @throws IOException If an error occurs during the parsing of the headers
     */
    public HeaderParseStatus parseHeader() throws IOException {

        while (headerParsePos == HeaderParsePosition.HEADER_START) {

            // Read new bytes if needed
            if (source.getHeaderByteBuffer().position() >= source.getHeaderByteBuffer().limit()) {
                if (!source.fillHeaderBuffer()) {
                    return HeaderParseStatus.NEED_MORE_DATA;
                }
            }

            prevChr = chr;
            chr = source.getHeaderByteBuffer().get();

            if (chr == CR && prevChr != CR) {
                // Possible start of CRLF - process the next byte.
            } else if (chr == LF) {
                if (!tolerantEol && prevChr != CR) {
                    throw new IllegalArgumentException(sm.getString("httpHeaderParser.invalidCrlfNoCR"));
                }
                return HeaderParseStatus.DONE;
            } else {
                if (prevChr == CR) {
                    // Must have read two bytes (first was CR, second was not LF)
                    source.getHeaderByteBuffer().position(source.getHeaderByteBuffer().position() - 2);
                } else {
                    // Must have only read one byte
                    source.getHeaderByteBuffer().position(source.getHeaderByteBuffer().position() - 1);
                }
                break;
            }
        }

        if (headerParsePos == HeaderParsePosition.HEADER_START) {
            // Mark the current buffer position
            headerData.start = source.getHeaderByteBuffer().position();
            headerData.lineStart = headerData.start;
            headerParsePos = HeaderParsePosition.HEADER_NAME;
        }

        //
        // Reading the header name
        // Header name is always US-ASCII
        //

        while (headerParsePos == HeaderParsePosition.HEADER_NAME) {

            // Read new bytes if needed
            if (source.getHeaderByteBuffer().position() >= source.getHeaderByteBuffer().limit()) {
                if (!source.fillHeaderBuffer()) {
                    return HeaderParseStatus.NEED_MORE_DATA;
                }
            }

            int pos = source.getHeaderByteBuffer().position();
            chr = source.getHeaderByteBuffer().get();
            if (chr == COLON) {
                if (headerData.start == pos) {
                    // Zero length header name - not valid.
                    // skipLine() will handle the error
                    return skipLine();
                }
                headerParsePos = HeaderParsePosition.HEADER_VALUE_START;
                headerData.headerValue = headers.addValue(source.getHeaderByteBuffer().array(), headerData.start,
                        pos - headerData.start);
                pos = source.getHeaderByteBuffer().position();
                // Mark the current buffer position
                headerData.start = pos;
                headerData.realPos = pos;
                headerData.lastSignificantChar = pos;
                break;
            } else if (!HttpParser.isToken(chr)) {
                // Non-token characters are illegal in header names
                // Parsing continues so the error can be reported in context
                headerData.lastSignificantChar = pos;
                source.getHeaderByteBuffer().position(source.getHeaderByteBuffer().position() - 1);
                // skipLine() will handle the error
                return skipLine();
            }

            // chr is next byte of header name. Convert to lowercase.
            if (chr >= A && chr <= Z) {
                source.getHeaderByteBuffer().put(pos, (byte) (chr - LC_OFFSET));
            }
        }

        // Skip the line and ignore the header
        if (headerParsePos == HeaderParsePosition.HEADER_SKIPLINE) {
            return skipLine();
        }

        //
        // Reading the header value (which can be spanned over multiple lines)
        //

        while (headerParsePos == HeaderParsePosition.HEADER_VALUE_START ||
                headerParsePos == HeaderParsePosition.HEADER_VALUE ||
                headerParsePos == HeaderParsePosition.HEADER_MULTI_LINE) {

            if (headerParsePos == HeaderParsePosition.HEADER_VALUE_START) {
                // Skipping spaces
                while (true) {
                    // Read new bytes if needed
                    if (source.getHeaderByteBuffer().position() >= source.getHeaderByteBuffer().limit()) {
                        if (!source.fillHeaderBuffer()) {
                            return HeaderParseStatus.NEED_MORE_DATA;
                        }
                    }

                    chr = source.getHeaderByteBuffer().get();
                    if (chr != SP && chr != HT) {
                        headerParsePos = HeaderParsePosition.HEADER_VALUE;
                        source.getHeaderByteBuffer().position(source.getHeaderByteBuffer().position() - 1);
                        // Avoids prevChr = chr at start of header value
                        // parsing which causes problems when chr is CR
                        // (in the case of an empty header value)
                        chr = 0;
                        break;
                    }
                }
            }
            if (headerParsePos == HeaderParsePosition.HEADER_VALUE) {

                // Reading bytes until the end of the line
                boolean eol = false;
                while (!eol) {

                    // Read new bytes if needed
                    if (source.getHeaderByteBuffer().position() >= source.getHeaderByteBuffer().limit()) {
                        if (!source.fillHeaderBuffer()) {
                            return HeaderParseStatus.NEED_MORE_DATA;
                        }
                    }

                    prevChr = chr;
                    chr = source.getHeaderByteBuffer().get();
                    if (chr == CR && prevChr != CR) {
                        // CR is only permitted at the start of a CRLF sequence.
                        // Possible start of CRLF - process the next byte.
                    } else if (chr == LF) {
                        if (!tolerantEol && prevChr != CR) {
                            throw new IllegalArgumentException(sm.getString("httpHeaderParser.invalidCrlfNoCR"));
                        }
                        eol = true;
                    } else if (prevChr == CR) {
                        // Invalid value - also need to delete header
                        return skipLine();
                    } else if (HttpParser.isControl(chr) && chr != HT) {
                        // Invalid value - also need to delete header
                        return skipLine();
                    } else if (chr == SP || chr == HT) {
                        source.getHeaderByteBuffer().put(headerData.realPos, chr);
                        headerData.realPos++;
                    } else {
                        source.getHeaderByteBuffer().put(headerData.realPos, chr);
                        headerData.realPos++;
                        headerData.lastSignificantChar = headerData.realPos;
                    }
                }

                // Ignore whitespaces at the end of the line
                headerData.realPos = headerData.lastSignificantChar;

                // Checking the first character of the new line. If the character
                // is a LWS, then it's a multiline header
                headerParsePos = HeaderParsePosition.HEADER_MULTI_LINE;
            }
            // Read new bytes if needed
            if (source.getHeaderByteBuffer().position() >= source.getHeaderByteBuffer().limit()) {
                if (!source.fillHeaderBuffer()) {
                    return HeaderParseStatus.NEED_MORE_DATA;
                }
            }

            byte peek = source.getHeaderByteBuffer().get(source.getHeaderByteBuffer().position());
            if (headerParsePos == HeaderParsePosition.HEADER_MULTI_LINE) {
                if (peek != SP && peek != HT) {
                    headerParsePos = HeaderParsePosition.HEADER_START;
                    break;
                } else {
                    // Copying one extra space in the buffer (since there must
                    // be at least one space inserted between the lines)
                    source.getHeaderByteBuffer().put(headerData.realPos, peek);
                    headerData.realPos++;
                    headerParsePos = HeaderParsePosition.HEADER_VALUE_START;
                }
            }
        }
        // Set the header value
        headerData.headerValue.setBytes(source.getHeaderByteBuffer().array(), headerData.start,
                headerData.lastSignificantChar - headerData.start);
        headerData.recycle();
        return HeaderParseStatus.HAVE_MORE_HEADERS;
    }


    private HeaderParseStatus skipLine() throws IOException {
        // Parse the rest of the invalid header so we can construct a useful
        // exception and/or debug message.
        headerParsePos = HeaderParsePosition.HEADER_SKIPLINE;
        boolean eol = false;

        // Reading bytes until the end of the line
        while (!eol) {

            // Read new bytes if needed
            if (source.getHeaderByteBuffer().position() >= source.getHeaderByteBuffer().limit()) {
                if (!source.fillHeaderBuffer()) {
                    return HeaderParseStatus.NEED_MORE_DATA;
                }
            }

            int pos = source.getHeaderByteBuffer().position();
            prevChr = chr;
            chr = source.getHeaderByteBuffer().get();
            if (chr == CR) {
                // Skip
            } else if (chr == LF) {
                if (!tolerantEol && prevChr != CR) {
                    throw new IllegalArgumentException(sm.getString("httpHeaderParser.invalidCrlfNoCR"));
                }
                eol = true;
            } else {
                headerData.lastSignificantChar = pos;
            }
        }

        throw new IllegalArgumentException(sm.getString("httpHeaderParser.invalidHeader",
                HeaderUtil.toPrintableString(source.getHeaderByteBuffer().array(), headerData.lineStart,
                        headerData.lastSignificantChar - headerData.lineStart + 1)));
    }


    public enum HeaderParseStatus {
        DONE,
        HAVE_MORE_HEADERS,
        NEED_MORE_DATA
    }


    public enum HeaderParsePosition {
        /**
         * Start of a new header. A CRLF here means that there are no more headers. Any other character starts a header
         * name.
         */
        HEADER_START,
        /**
         * Reading a header name. All characters of header are HTTP_TOKEN_CHAR. Header name is followed by ':'. No
         * whitespace is allowed.<br>
         * Any non-HTTP_TOKEN_CHAR (this includes any whitespace) encountered before ':' will result in the whole line
         * being ignored.
         */
        HEADER_NAME,
        /**
         * Skipping whitespace before text of header value starts, either on the first line of header value (just after
         * ':') or on subsequent lines when it is known that subsequent line starts with SP or HT.
         */
        HEADER_VALUE_START,
        /**
         * Reading the header value. We are inside the value. Either on the first line or on any subsequent line. We
         * come into this state from HEADER_VALUE_START after the first non-SP/non-HT byte is encountered on the line.
         */
        HEADER_VALUE,
        /**
         * Before reading a new line of a header. Once the next byte is peeked, the state changes without advancing our
         * position. The state becomes either HEADER_VALUE_START (if that first byte is SP or HT), or HEADER_START
         * (otherwise).
         */
        HEADER_MULTI_LINE,
        /**
         * Reading all bytes until the next CRLF. The line is being ignored.
         */
        HEADER_SKIPLINE
    }


    private static class HeaderParseData {
        /**
         * The first character of the header line.
         */
        int lineStart = 0;
        /**
         * When parsing header name: first character of the header.<br>
         * When skipping broken header line: first character of the header.<br>
         * When parsing header value: first character after ':'.
         */
        int start = 0;
        /**
         * When parsing header name: not used (stays as 0).<br>
         * When skipping broken header line: not used (stays as 0).<br>
         * When parsing header value: starts as the first character after ':'. Then is increased as far as more bytes of
         * the header are harvested. Bytes from buf[pos] are copied to buf[realPos]. Thus the string from [start] to
         * [realPos-1] is the prepared value of the header, with whitespaces removed as needed.<br>
         */
        int realPos = 0;
        /**
         * When parsing header name: not used (stays as 0).<br>
         * When skipping broken header line: last non-CR/non-LF character.<br>
         * When parsing header value: position after the last not-LWS character.<br>
         */
        int lastSignificantChar = 0;
        /**
         * MB that will store the value of the header. It is null while parsing header name and is created after the
         * name has been parsed.
         */
        MessageBytes headerValue = null;

        public void recycle() {
            lineStart = 0;
            start = 0;
            realPos = 0;
            lastSignificantChar = 0;
            headerValue = null;
        }
    }


    public interface HeaderDataSource {
        /**
         * Read more data into the header buffer. The implementation is expected to determine if blocking or not
         * blocking IO should be used.
         *
         * @return {@code true} if more data was added to the buffer, otherwise {@code false}
         *
         * @throws IOException If an I/O error occurred while obtaining more header data
         */
        boolean fillHeaderBuffer() throws IOException;

        /**
         * Obtain a reference to the buffer containing the header data.
         *
         * @return The buffer containing the header data
         */
        ByteBuffer getHeaderByteBuffer();
    }
}

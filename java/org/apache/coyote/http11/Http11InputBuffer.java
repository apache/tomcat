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
package org.apache.coyote.http11;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

/**
 * InputBuffer for HTTP that provides request header parsing as well as transfer
 * encoding.
 */
public class Http11InputBuffer implements InputBuffer {

    // -------------------------------------------------------------- Constants

    private static final Log log = LogFactory.getLog(Http11InputBuffer.class);

    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager(Http11InputBuffer.class);


    private static final boolean[] HTTP_TOKEN_CHAR = new boolean[128];
    static {
        for (int i = 0; i < 128; i++) {
            if (i < 32) {
                HTTP_TOKEN_CHAR[i] = false;
            } else if (i == 127) {
                HTTP_TOKEN_CHAR[i] = false;
            } else if (i == '(') {
                HTTP_TOKEN_CHAR[i] = false;
            } else if (i == ')') {
                HTTP_TOKEN_CHAR[i] = false;
            } else if (i == '<') {
                HTTP_TOKEN_CHAR[i] = false;
            } else if (i == '>') {
                HTTP_TOKEN_CHAR[i] = false;
            } else if (i == '@') {
                HTTP_TOKEN_CHAR[i] = false;
            } else if (i == ',') {
                HTTP_TOKEN_CHAR[i] = false;
            } else if (i == ';') {
                HTTP_TOKEN_CHAR[i] = false;
            } else if (i == ':') {
                HTTP_TOKEN_CHAR[i] = false;
            } else if (i == '\\') {
                HTTP_TOKEN_CHAR[i] = false;
            } else if (i == '\"') {
                HTTP_TOKEN_CHAR[i] = false;
            } else if (i == '/') {
                HTTP_TOKEN_CHAR[i] = false;
            } else if (i == '[') {
                HTTP_TOKEN_CHAR[i] = false;
            } else if (i == ']') {
                HTTP_TOKEN_CHAR[i] = false;
            } else if (i == '?') {
                HTTP_TOKEN_CHAR[i] = false;
            } else if (i == '=') {
                HTTP_TOKEN_CHAR[i] = false;
            } else if (i == '{') {
                HTTP_TOKEN_CHAR[i] = false;
            } else if (i == '}') {
                HTTP_TOKEN_CHAR[i] = false;
            } else if (i == ' ') {
                HTTP_TOKEN_CHAR[i] = false;
            } else {
                HTTP_TOKEN_CHAR[i] = true;
            }
        }
    }


    private static final byte[] CLIENT_PREFACE_START =
            "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);

    /**
     * Associated Coyote request.
     */
    private final Request request;


    /**
     * Headers of the associated request.
     */
    private final MimeHeaders headers;


    /**
     * State.
     */
    private boolean parsingHeader;


    /**
     * Swallow input ? (in the case of an expectation)
     */
    private boolean swallowInput;


    /**
     * The read buffer.
     */
    private byte[] buf;


    /**
     * Last valid byte.
     */
    private int lastValid;


    /**
     * Position in the buffer.
     */
    private int pos;


    /**
     * Pos of the end of the header in the buffer, which is also the
     * start of the body.
     */
    private int end;


    /**
     * Wrapper that provides access to the underlying socket.
     */
    private SocketWrapperBase<?> wrapper;


    /**
     * Underlying input buffer.
     */
    private InputBuffer inputStreamInputBuffer;


    /**
     * Filter library.
     * Note: Filter[Constants.CHUNKED_FILTER] is always the "chunked" filter.
     */
    private InputFilter[] filterLibrary;


    /**
     * Active filters (in order).
     */
    private InputFilter[] activeFilters;


    /**
     * Index of the last active filter.
     */
    private int lastActiveFilter;


    /**
     * Parsing state - used for non blocking parsing so that
     * when more data arrives, we can pick up where we left off.
     */
    private boolean parsingRequestLine;
    private int parsingRequestLinePhase = 0;
    private boolean parsingRequestLineEol = false;
    private int parsingRequestLineStart = 0;
    private int parsingRequestLineQPos = -1;
    private HeaderParsePosition headerParsePos;
    private final HeaderParseData headerData = new HeaderParseData();

    /**
     * Maximum allowed size of the HTTP request line plus headers plus any
     * leading blank lines.
     */
    private final int headerBufferSize;

    /**
     * Known size of the NioChannel read buffer.
     */
    private int socketReadBufferSize;


    // ----------------------------------------------------------- Constructors

    public Http11InputBuffer(Request request, int headerBufferSize) {

        this.request = request;
        headers = request.getMimeHeaders();

        this.headerBufferSize = headerBufferSize;

        filterLibrary = new InputFilter[0];
        activeFilters = new InputFilter[0];
        lastActiveFilter = -1;

        parsingHeader = true;
        parsingRequestLine = true;
        parsingRequestLinePhase = 0;
        parsingRequestLineEol = false;
        parsingRequestLineStart = 0;
        parsingRequestLineQPos = -1;
        headerParsePos = HeaderParsePosition.HEADER_START;
        swallowInput = true;

        inputStreamInputBuffer = new SocketInputBuffer();
    }


    // ------------------------------------------------------------- Properties

    /**
     * Add an input filter to the filter library.
     *
     * @throws NullPointerException if the supplied filter is null
     */
    void addFilter(InputFilter filter) {

        if (filter == null) {
            throw new NullPointerException(sm.getString("iib.filter.npe"));
        }

        InputFilter[] newFilterLibrary =
            new InputFilter[filterLibrary.length + 1];
        for (int i = 0; i < filterLibrary.length; i++) {
            newFilterLibrary[i] = filterLibrary[i];
        }
        newFilterLibrary[filterLibrary.length] = filter;
        filterLibrary = newFilterLibrary;

        activeFilters = new InputFilter[filterLibrary.length];
    }


    /**
     * Get filters.
     */
    InputFilter[] getFilters() {
        return filterLibrary;
    }


    /**
     * Add an input filter to the filter library.
     */
    void addActiveFilter(InputFilter filter) {

        if (lastActiveFilter == -1) {
            filter.setBuffer(inputStreamInputBuffer);
        } else {
            for (int i = 0; i <= lastActiveFilter; i++) {
                if (activeFilters[i] == filter)
                    return;
            }
            filter.setBuffer(activeFilters[lastActiveFilter]);
        }

        activeFilters[++lastActiveFilter] = filter;

        filter.setRequest(request);
    }


    /**
     * Set the swallow input flag.
     */
    void setSwallowInput(boolean swallowInput) {
        this.swallowInput = swallowInput;
    }


    // ---------------------------------------------------- InputBuffer Methods

    @Override
    public int doRead(ByteChunk chunk) throws IOException {

        if (lastActiveFilter == -1)
            return inputStreamInputBuffer.doRead(chunk);
        else
            return activeFilters[lastActiveFilter].doRead(chunk);

    }


    // ------------------------------------------------------- Protected Methods

    /**
     * Recycle the input buffer. This should be called when closing the
     * connection.
     */
    void recycle() {
        wrapper = null;
        request.recycle();

        for (int i = 0; i <= lastActiveFilter; i++) {
            activeFilters[i].recycle();
        }

        lastValid = 0;
        pos = 0;
        lastActiveFilter = -1;
        parsingHeader = true;
        swallowInput = true;

        headerParsePos = HeaderParsePosition.HEADER_START;
        parsingRequestLine = true;
        parsingRequestLinePhase = 0;
        parsingRequestLineEol = false;
        parsingRequestLineStart = 0;
        parsingRequestLineQPos = -1;
        headerData.recycle();
    }


    /**
     * End processing of current HTTP request.
     * Note: All bytes of the current request should have been already
     * consumed. This method only resets all the pointers so that we are ready
     * to parse the next HTTP request.
     */
    void nextRequest() {
        request.recycle();

        // Copy leftover bytes to the beginning of the buffer
        if (lastValid - pos > 0 && pos > 0) {
            System.arraycopy(buf, pos, buf, 0, lastValid - pos);
        }
        // Always reset pos to zero
        lastValid = lastValid - pos;
        pos = 0;

        // Recycle filters
        for (int i = 0; i <= lastActiveFilter; i++) {
            activeFilters[i].recycle();
        }

        // Reset pointers
        lastActiveFilter = -1;
        parsingHeader = true;
        swallowInput = true;

        headerParsePos = HeaderParsePosition.HEADER_START;
        parsingRequestLine = true;
        parsingRequestLinePhase = 0;
        parsingRequestLineEol = false;
        parsingRequestLineStart = 0;
        parsingRequestLineQPos = -1;
        headerData.recycle();
    }


    /**
     * Read the request line. This function is meant to be used during the
     * HTTP request header parsing. Do NOT attempt to read the request body
     * using it.
     *
     * @throws IOException If an exception occurs during the underlying socket
     * read operations, or if the given buffer is not big enough to accommodate
     * the whole line.
     * @return true if data is properly fed; false if no data is available
     * immediately and thread should be freed
     */
    boolean parseRequestLine(boolean keptAlive) throws IOException {

        //check state
        if ( !parsingRequestLine ) return true;
        //
        // Skipping blank lines
        //
        if ( parsingRequestLinePhase < 2 ) {
            byte chr = 0;
            do {

                // Read new bytes if needed
                if (pos >= lastValid) {
                    if (keptAlive) {
                        // Haven't read any request data yet so use the keep-alive
                        // timeout.
                        wrapper.setReadTimeout(wrapper.getEndpoint().getKeepAliveTimeout());
                    }
                    if (!fill(false)) {
                        // A read is pending, so no longer in initial state
                        parsingRequestLinePhase = 1;
                        return false;
                    }
                    // At least one byte of the request has been received.
                    // Switch to the socket timeout.
                    wrapper.setReadTimeout(wrapper.getEndpoint().getSoTimeout());
                }
                if (!keptAlive && pos == 0 && lastValid >= CLIENT_PREFACE_START.length - 1) {
                    boolean prefaceMatch = true;
                    for (int i = 0; i < CLIENT_PREFACE_START.length && prefaceMatch; i++) {
                        if (CLIENT_PREFACE_START[i] != buf[i]) {
                            prefaceMatch = false;
                        }
                    }
                    if (prefaceMatch) {
                        // HTTP/2 preface matched
                        parsingRequestLinePhase = -1;
                        return false;
                    }
                }
                // Set the start time once we start reading data (even if it is
                // just skipping blank lines)
                if (request.getStartTime() < 0) {
                    request.setStartTime(System.currentTimeMillis());
                }
                chr = buf[pos++];
            } while ((chr == Constants.CR) || (chr == Constants.LF));
            pos--;

            parsingRequestLineStart = pos;
            parsingRequestLinePhase = 2;
            if (log.isDebugEnabled()) {
                log.debug("Received ["
                        + new String(buf, pos, lastValid - pos,
                                StandardCharsets.ISO_8859_1)
                        + "]");
            }
        }
        if ( parsingRequestLinePhase == 2 ) {
            //
            // Reading the method name
            // Method name is a token
            //
            boolean space = false;
            while (!space) {
                // Read new bytes if needed
                if (pos >= lastValid) {
                    if (!fill(false)) //request line parsing
                        return false;
                }
                // Spec says method name is a token followed by a single SP but
                // also be tolerant of multiple SP and/or HT.
                if (buf[pos] == Constants.SP || buf[pos] == Constants.HT) {
                    space = true;
                    request.method().setBytes(buf, parsingRequestLineStart, pos - parsingRequestLineStart);
                } else if (!HTTP_TOKEN_CHAR[buf[pos]]) {
                    throw new IllegalArgumentException(sm.getString("iib.invalidmethod"));
                }
                pos++;
            }
            parsingRequestLinePhase = 3;
        }
        if ( parsingRequestLinePhase == 3 ) {
            // Spec says single SP but also be tolerant of multiple SP and/or HT
            boolean space = true;
            while (space) {
                // Read new bytes if needed
                if (pos >= lastValid) {
                    if (!fill(false)) //request line parsing
                        return false;
                }
                if (buf[pos] == Constants.SP || buf[pos] == Constants.HT) {
                    pos++;
                } else {
                    space = false;
                }
            }
            parsingRequestLineStart = pos;
            parsingRequestLinePhase = 4;
        }
        if (parsingRequestLinePhase == 4) {
            // Mark the current buffer position

            int end = 0;
            //
            // Reading the URI
            //
            boolean space = false;
            while (!space) {
                // Read new bytes if needed
                if (pos >= lastValid) {
                    if (!fill(false)) //request line parsing
                        return false;
                }
                if (buf[pos] == Constants.SP || buf[pos] == Constants.HT) {
                    space = true;
                    end = pos;
                } else if ((buf[pos] == Constants.CR)
                           || (buf[pos] == Constants.LF)) {
                    // HTTP/0.9 style request
                    parsingRequestLineEol = true;
                    space = true;
                    end = pos;
                } else if ((buf[pos] == Constants.QUESTION)
                           && (parsingRequestLineQPos == -1)) {
                    parsingRequestLineQPos = pos;
                }
                pos++;
            }
            if (parsingRequestLineQPos >= 0) {
                request.queryString().setBytes(buf, parsingRequestLineQPos + 1,
                                               end - parsingRequestLineQPos - 1);
                request.requestURI().setBytes(buf, parsingRequestLineStart, parsingRequestLineQPos - parsingRequestLineStart);
            } else {
                request.requestURI().setBytes(buf, parsingRequestLineStart, end - parsingRequestLineStart);
            }
            parsingRequestLinePhase = 5;
        }
        if ( parsingRequestLinePhase == 5 ) {
            // Spec says single SP but also be tolerant of multiple and/or HT
            boolean space = true;
            while (space) {
                // Read new bytes if needed
                if (pos >= lastValid) {
                    if (!fill(false)) //request line parsing
                        return false;
                }
                if (buf[pos] == Constants.SP || buf[pos] == Constants.HT) {
                    pos++;
                } else {
                    space = false;
                }
            }
            parsingRequestLineStart = pos;
            parsingRequestLinePhase = 6;

            // Mark the current buffer position
            end = 0;
        }
        if (parsingRequestLinePhase == 6) {
            //
            // Reading the protocol
            // Protocol is always US-ASCII
            //
            while (!parsingRequestLineEol) {
                // Read new bytes if needed
                if (pos >= lastValid) {
                    if (!fill(false)) //request line parsing
                        return false;
                }

                if (buf[pos] == Constants.CR) {
                    end = pos;
                } else if (buf[pos] == Constants.LF) {
                    if (end == 0)
                        end = pos;
                    parsingRequestLineEol = true;
                }
                pos++;
            }

            if ( (end - parsingRequestLineStart) > 0) {
                request.protocol().setBytes(buf, parsingRequestLineStart, end - parsingRequestLineStart);
            } else {
                request.protocol().setString("");
            }
            parsingRequestLine = false;
            parsingRequestLinePhase = 0;
            parsingRequestLineEol = false;
            parsingRequestLineStart = 0;
            return true;
        }
        throw new IllegalStateException("Invalid request line parse phase:"+parsingRequestLinePhase);
    }


    /**
     * Parse the HTTP headers.
     */
    boolean parseHeaders() throws IOException {
        if (!parsingHeader) {
            throw new IllegalStateException(
                    sm.getString("iib.parseheaders.ise.error"));
        }

        HeaderParseStatus status = HeaderParseStatus.HAVE_MORE_HEADERS;

        do {
            status = parseHeader();
            // Checking that
            // (1) Headers plus request line size does not exceed its limit
            // (2) There are enough bytes to avoid expanding the buffer when
            // reading body
            // Technically, (2) is technical limitation, (1) is logical
            // limitation to enforce the meaning of headerBufferSize
            // From the way how buf is allocated and how blank lines are being
            // read, it should be enough to check (1) only.
            if (pos > headerBufferSize
                    || buf.length - pos < socketReadBufferSize) {
                throw new IllegalArgumentException(
                        sm.getString("iib.requestheadertoolarge.error"));
            }
        } while ( status == HeaderParseStatus.HAVE_MORE_HEADERS );
        if (status == HeaderParseStatus.DONE) {
            parsingHeader = false;
            end = pos;
            return true;
        } else {
            return false;
        }
    }


    int getParsingRequestLinePhase() {
        return parsingRequestLinePhase;
    }


    /**
     * End request (consumes leftover bytes).
     *
     * @throws IOException an underlying I/O error occurred
     */
    void endRequest() throws IOException {

        if (swallowInput && (lastActiveFilter != -1)) {
            int extraBytes = (int) activeFilters[lastActiveFilter].end();
            pos = pos - extraBytes;
        }
    }


    /**
     * Available bytes in the buffers (note that due to encoding, this may not
     * correspond).
     */
    int available(boolean read) {
        int available = lastValid - pos;
        if ((available == 0) && (lastActiveFilter >= 0)) {
            for (int i = 0; (available == 0) && (i <= lastActiveFilter); i++) {
                available = activeFilters[i].available();
            }
        }
        if (available > 0 || !read) {
            return available;
        }

        try {
            fill(false);
            available = lastValid - pos;
        } catch (IOException ioe) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("iib.available.readFail"), ioe);
            }
            // Not ideal. This will indicate that data is available which should
            // trigger a read which in turn will trigger another IOException and
            // that one can be thrown.
            available = 1;
        }
        return available;
    }


    /**
     * Has all of the request body been read? There are subtle differences
     * between this and available() &gt; 0 primarily because of having to handle
     * faking non-blocking reads with the blocking IO connector.
     */
    boolean isFinished() {
        if (lastValid > pos) {
            // Data to read in the buffer so not finished
            return false;
        }

        /*
         * Don't use fill(false) here because in the following circumstances
         * BIO will block - possibly indefinitely
         * - client is using keep-alive and connection is still open
         * - client has sent the complete request
         * - client has not sent any of the next request (i.e. no pipelining)
         * - application has read the complete request
         */

        // Check the InputFilters

        if (lastActiveFilter >= 0) {
            return activeFilters[lastActiveFilter].isFinished();
        } else {
            // No filters. Assume request is not finished. EOF will signal end of
            // request.
            return false;
        }
    }

    ByteBuffer getLeftover() {
        int available = lastValid - pos;
        if (available > 0) {
            return ByteBuffer.wrap(buf, pos, available);
        } else {
            return null;
        }
    }


    void init(SocketWrapperBase<?> socketWrapper) {

        wrapper = socketWrapper;

        int bufLength = headerBufferSize +
                wrapper.getSocketBufferHandler().getReadBuffer().capacity();
        if (buf == null || buf.length < bufLength) {
            buf = new byte[bufLength];
        }
    }



    // --------------------------------------------------------- Private Methods

    /**
     * Attempts to read some data into the input buffer.
     *
     * @return <code>true</code> if more data was added to the input buffer
     *         otherwise <code>false</code>
     */
    private boolean fill(boolean block) throws IOException {

        if (parsingHeader) {
            if (lastValid >= headerBufferSize) {
                throw new IllegalArgumentException
                    (sm.getString("iib.requestheadertoolarge.error"));
            }
        } else {
            lastValid = pos = end;
        }

        int nRead = wrapper.read(block, buf, pos, buf.length - pos);
        if (nRead > 0) {
            lastValid = pos + nRead;
            return true;
        } else if (nRead == -1) {
            throw new EOFException(sm.getString("iib.eof.error"));
        } else {
            return false;
        }

    }


    /**
     * Parse an HTTP header.
     *
     * @return false after reading a blank line (which indicates that the
     * HTTP header parsing is done
     */
    private HeaderParseStatus parseHeader()
        throws IOException {

        //
        // Check for blank line
        //

        byte chr = 0;
        while (headerParsePos == HeaderParsePosition.HEADER_START) {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill(false)) {//parse header
                    headerParsePos = HeaderParsePosition.HEADER_START;
                    return HeaderParseStatus.NEED_MORE_DATA;
                }
            }

            chr = buf[pos];

            if (chr == Constants.CR) {
                // Skip
            } else if (chr == Constants.LF) {
                pos++;
                return HeaderParseStatus.DONE;
            } else {
                break;
            }

            pos++;

        }

        if ( headerParsePos == HeaderParsePosition.HEADER_START ) {
            // Mark the current buffer position
            headerData.start = pos;
            headerParsePos = HeaderParsePosition.HEADER_NAME;
        }

        //
        // Reading the header name
        // Header name is always US-ASCII
        //

        while (headerParsePos == HeaderParsePosition.HEADER_NAME) {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill(false)) { //parse header
                    return HeaderParseStatus.NEED_MORE_DATA;
                }
            }

            chr = buf[pos];
            if (chr == Constants.COLON) {
                headerParsePos = HeaderParsePosition.HEADER_VALUE_START;
                headerData.headerValue = headers.addValue(buf, headerData.start, pos - headerData.start);
                pos++;
                // Mark the current buffer position
                headerData.start = pos;
                headerData.realPos = pos;
                headerData.lastSignificantChar = pos;
                break;
            } else if (chr < 0 || !HTTP_TOKEN_CHAR[chr]) {
                // If a non-token header is detected, skip the line and
                // ignore the header
                headerData.lastSignificantChar = pos;
                return skipLine();
            }

            // chr is next byte of header name. Convert to lowercase.
            if ((chr >= Constants.A) && (chr <= Constants.Z)) {
                buf[pos] = (byte) (chr - Constants.LC_OFFSET);
            }
            pos++;
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

            if ( headerParsePos == HeaderParsePosition.HEADER_VALUE_START ) {
                // Skipping spaces
                while (true) {
                    // Read new bytes if needed
                    if (pos >= lastValid) {
                        if (!fill(false)) {//parse header
                            //HEADER_VALUE_START
                            return HeaderParseStatus.NEED_MORE_DATA;
                        }
                    }

                    chr = buf[pos];
                    if (chr == Constants.SP || chr == Constants.HT) {
                        pos++;
                    } else {
                        headerParsePos = HeaderParsePosition.HEADER_VALUE;
                        break;
                    }
                }
            }
            if ( headerParsePos == HeaderParsePosition.HEADER_VALUE ) {

                // Reading bytes until the end of the line
                boolean eol = false;
                while (!eol) {

                    // Read new bytes if needed
                    if (pos >= lastValid) {
                        if (!fill(false)) {//parse header
                            //HEADER_VALUE
                            return HeaderParseStatus.NEED_MORE_DATA;
                        }
                    }

                    chr = buf[pos];
                    if (chr == Constants.CR) {
                        // Skip
                    } else if (chr == Constants.LF) {
                        eol = true;
                    } else if (chr == Constants.SP || chr == Constants.HT) {
                        buf[headerData.realPos] = chr;
                        headerData.realPos++;
                    } else {
                        buf[headerData.realPos] = chr;
                        headerData.realPos++;
                        headerData.lastSignificantChar = headerData.realPos;
                    }

                    pos++;
                }

                // Ignore whitespaces at the end of the line
                headerData.realPos = headerData.lastSignificantChar;

                // Checking the first character of the new line. If the character
                // is a LWS, then it's a multiline header
                headerParsePos = HeaderParsePosition.HEADER_MULTI_LINE;
            }
            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill(false)) {//parse header
                    //HEADER_MULTI_LINE
                    return HeaderParseStatus.NEED_MORE_DATA;
                }
            }

            chr = buf[pos];
            if ( headerParsePos == HeaderParsePosition.HEADER_MULTI_LINE ) {
                if ( (chr != Constants.SP) && (chr != Constants.HT)) {
                    headerParsePos = HeaderParsePosition.HEADER_START;
                    break;
                } else {
                    // Copying one extra space in the buffer (since there must
                    // be at least one space inserted between the lines)
                    buf[headerData.realPos] = chr;
                    headerData.realPos++;
                    headerParsePos = HeaderParsePosition.HEADER_VALUE_START;
                }
            }
        }
        // Set the header value
        headerData.headerValue.setBytes(buf, headerData.start,
                headerData.lastSignificantChar - headerData.start);
        headerData.recycle();
        return HeaderParseStatus.HAVE_MORE_HEADERS;
    }


    private HeaderParseStatus skipLine() throws IOException {
        headerParsePos = HeaderParsePosition.HEADER_SKIPLINE;
        boolean eol = false;

        // Reading bytes until the end of the line
        while (!eol) {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill(false)) {
                    return HeaderParseStatus.NEED_MORE_DATA;
                }
            }

            if (buf[pos] == Constants.CR) {
                // Skip
            } else if (buf[pos] == Constants.LF) {
                eol = true;
            } else {
                headerData.lastSignificantChar = pos;
            }

            pos++;
        }
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("iib.invalidheader", new String(buf,
                    headerData.start,
                    headerData.lastSignificantChar - headerData.start + 1,
                    StandardCharsets.ISO_8859_1)));
        }

        headerParsePos = HeaderParsePosition.HEADER_START;
        return HeaderParseStatus.HAVE_MORE_HEADERS;
    }


    // ----------------------------------------------------------- Inner classes

    private static enum HeaderParseStatus {
        DONE, HAVE_MORE_HEADERS, NEED_MORE_DATA
    }


    private static enum HeaderParsePosition {
        /**
         * Start of a new header. A CRLF here means that there are no more
         * headers. Any other character starts a header name.
         */
        HEADER_START,
        /**
         * Reading a header name. All characters of header are HTTP_TOKEN_CHAR.
         * Header name is followed by ':'. No whitespace is allowed.<br>
         * Any non-HTTP_TOKEN_CHAR (this includes any whitespace) encountered
         * before ':' will result in the whole line being ignored.
         */
        HEADER_NAME,
        /**
         * Skipping whitespace before text of header value starts, either on the
         * first line of header value (just after ':') or on subsequent lines
         * when it is known that subsequent line starts with SP or HT.
         */
        HEADER_VALUE_START,
        /**
         * Reading the header value. We are inside the value. Either on the
         * first line or on any subsequent line. We come into this state from
         * HEADER_VALUE_START after the first non-SP/non-HT byte is encountered
         * on the line.
         */
        HEADER_VALUE,
        /**
         * Before reading a new line of a header. Once the next byte is peeked,
         * the state changes without advancing our position. The state becomes
         * either HEADER_VALUE_START (if that first byte is SP or HT), or
         * HEADER_START (otherwise).
         */
        HEADER_MULTI_LINE,
        /**
         * Reading all bytes until the next CRLF. The line is being ignored.
         */
        HEADER_SKIPLINE
    }


    private static class HeaderParseData {
        /**
         * When parsing header name: first character of the header.<br>
         * When skipping broken header line: first character of the header.<br>
         * When parsing header value: first character after ':'.
         */
        int start = 0;
        /**
         * When parsing header name: not used (stays as 0).<br>
         * When skipping broken header line: not used (stays as 0).<br>
         * When parsing header value: starts as the first character after ':'.
         * Then is increased as far as more bytes of the header are harvested.
         * Bytes from buf[pos] are copied to buf[realPos]. Thus the string from
         * [start] to [realPos-1] is the prepared value of the header, with
         * whitespaces removed as needed.<br>
         */
        int realPos = 0;
        /**
         * When parsing header name: not used (stays as 0).<br>
         * When skipping broken header line: last non-CR/non-LF character.<br>
         * When parsing header value: position after the last not-LWS character.<br>
         */
        int lastSignificantChar = 0;
        /**
         * MB that will store the value of the header. It is null while parsing
         * header name and is created after the name has been parsed.
         */
        MessageBytes headerValue = null;
        public void recycle() {
            start = 0;
            realPos = 0;
            lastSignificantChar = 0;
            headerValue = null;
        }
    }


    // ------------------------------------- InputStreamInputBuffer Inner Class

    /**
     * This class is an input buffer which will read its data from an input
     * stream.
     */
    private class SocketInputBuffer implements InputBuffer {

        @Override
        public int doRead(ByteChunk chunk) throws IOException {

            if (pos >= lastValid) {
                // The application is reading the HTTP request body which is
                // always a blocking operation.
                if (!fill(true))
                    return -1;
            }

            int length = lastValid - pos;
            chunk.setBytes(buf, pos, length);
            pos = lastValid;

            return length;
        }
    }
}

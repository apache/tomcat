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
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jni.Socket;
import org.apache.tomcat.jni.Status;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.HeaderUtil;
import org.apache.tomcat.util.http.parser.HttpParser;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.SocketWrapper;

/**
 * Implementation of InputBuffer which provides HTTP request header parsing as
 * well as transfer decoding.
 *
 * @author <a href="mailto:remm@apache.org">Remy Maucherat</a>
 */
public class InternalAprInputBuffer extends AbstractInputBuffer<Long> {

    private static final Log log =
        LogFactory.getLog(InternalAprInputBuffer.class);

    // ----------------------------------------------------------- Constructors


    /**
     * Alternate constructor.
     */
    public InternalAprInputBuffer(Request request, int headerBufferSize,
            boolean rejectIllegalHeader, HttpParser httpParser) {

        this.request = request;
        headers = request.getMimeHeaders();

        buf = new byte[headerBufferSize];
        if (headerBufferSize < (8 * 1024)) {
            bbuf = ByteBuffer.allocateDirect(6 * 1500);
        } else {
            bbuf = ByteBuffer.allocateDirect((headerBufferSize / 1500 + 1) * 1500);
        }

        this.rejectIllegalHeaderName = rejectIllegalHeader;
        this.httpParser = httpParser;

        inputStreamInputBuffer = new SocketInputBuffer();

        filterLibrary = new InputFilter[0];
        activeFilters = new InputFilter[0];
        lastActiveFilter = -1;

        parsingHeader = true;
        swallowInput = true;

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Direct byte buffer used to perform actual reading.
     */
    private ByteBuffer bbuf;


    /**
     * Underlying socket.
     */
    private long socket;
    private SocketWrapper<Long> wrapper;


    // --------------------------------------------------------- Public Methods

    /**
     * Recycle the input buffer. This should be called when closing the
     * connection.
     */
    @Override
    public void recycle() {
        socket = 0;
        wrapper = null;
        super.recycle();
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
    @Override
    public boolean parseRequestLine(boolean useAvailableData)
        throws IOException {

        int start = 0;

        //
        // Skipping blank lines
        //

        do {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (useAvailableData) {
                    return false;
                }
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }
            // Set the start time once we start reading data (even if it is
            // just skipping blank lines)
            if (request.getStartTime() < 0) {
                request.setStartTime(System.currentTimeMillis());
            }
            chr = buf[pos++];
        } while (chr == Constants.CR || chr == Constants.LF);

        pos--;

        // Mark the current buffer position
        start = pos;

        if (pos >= lastValid) {
            if (useAvailableData) {
                return false;
            }
            if (!fill())
                throw new EOFException(sm.getString("iib.eof.error"));
        }

        //
        // Reading the method name
        // Method name is a token
        //

        boolean space = false;

        while (!space) {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            // Spec says method name is a token followed by a single SP but
            // also be tolerant of multiple SP and/or HT.
            if (buf[pos] == Constants.SP || buf[pos] == Constants.HT) {
                space = true;
                request.method().setBytes(buf, start, pos - start);
            } else if (!HttpParser.isToken(buf[pos])) {
                String invalidMethodValue = parseInvalid(start, buf);
                throw new IllegalArgumentException(sm.getString("iib.invalidmethod", invalidMethodValue));
            }

            pos++;

        }

        // Spec says single SP but also says be tolerant of multiple SP and/or HT
        while (space) {
            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }
            if (buf[pos] == Constants.SP || buf[pos] == Constants.HT) {
                pos++;
            } else {
                space = false;
            }
        }

        // Mark the current buffer position
        start = pos;
        int end = 0;
        int questionPos = -1;

        //
        // Reading the URI
        //

        boolean eol = false;

        while (!space) {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            if (buf[pos -1] == Constants.CR && buf[pos] != Constants.LF) {
                // CR not followed by LF so not an HTTP/0.9 request and
                // therefore invalid. Trigger error handling.
                // Avoid unknown protocol triggering an additional error
                request.protocol().setString(Constants.HTTP_11);
                String invalidRequestTarget = parseInvalid(start, buf);
                throw new IllegalArgumentException(sm.getString("iib.invalidRequestTarget", invalidRequestTarget));
            }

            // Spec says single SP but it also says be tolerant of HT
            if (buf[pos] == Constants.SP || buf[pos] == Constants.HT) {
                space = true;
                end = pos;
            } else if (buf[pos] == Constants.CR) {
                // HTTP/0.9 style request. CR is optional. LF is not.
            } else if (buf[pos] == Constants.LF) {
                // HTTP/0.9 style request
                // Stop this processing loop
                space = true;
                // Set blank protocol (indicates HTTP/0.9)
                request.protocol().setString("");
                // Skip the protocol processing
                eol = true;
                if (buf[pos - 1] == Constants.CR) {
                    end = pos - 1;
                } else {
                    end = pos;
                }
            } else if ((buf[pos] == Constants.QUESTION) && (questionPos == -1)) {
                questionPos = pos;
            } else if (questionPos != -1 && !httpParser.isQueryRelaxed(buf[pos])) {
                // %nn decoding will be checked at the point of decoding
                String invalidRequestTarget = parseInvalid(start, buf);
                throw new IllegalArgumentException(sm.getString("iib.invalidRequestTarget", invalidRequestTarget));
            } else if (httpParser.isNotRequestTargetRelaxed(buf[pos])) {
                // This is a general check that aims to catch problems early
                // Detailed checking of each part of the request target will
                // happen in AbstractHttp11Processor#prepareRequest()
                String invalidRequestTarget = parseInvalid(start, buf);
                throw new IllegalArgumentException(sm.getString("iib.invalidRequestTarget", invalidRequestTarget));
            }
            pos++;
        }
        request.unparsedURI().setBytes(buf, start, end - start);
        if (questionPos >= 0) {
            request.queryString().setBytes(buf, questionPos + 1,
                                           end - questionPos - 1);
            request.requestURI().setBytes(buf, start, questionPos - start);
        } else {
            request.requestURI().setBytes(buf, start, end - start);
        }

        // Spec says single SP but also says be tolerant of multiple and/or HT
        while (space && !eol) {
            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }
            if (buf[pos] == Constants.SP || buf[pos] == Constants.HT) {
                pos++;
            } else {
                space = false;
            }
        }


        // Mark the current buffer position
        start = pos;
        end = 0;

        //
        // Reading the protocol
        // Protocol is always "HTTP/" DIGIT "." DIGIT
        //

        while (!eol) {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            if (buf[pos] == Constants.CR) {
                // Possible end of request line. Need LF next.
            } else if (buf[pos - 1] == Constants.CR && buf[pos] == Constants.LF) {
                end = pos - 1;
                eol = true;
            } else if (!HttpParser.isHttpProtocol(buf[pos])) {
                String invalidProtocol = parseInvalid(start, buf);
                throw new IllegalArgumentException(sm.getString("iib.invalidHttpProtocol", invalidProtocol));
            }

            pos++;

        }

        if ((end - start) > 0) {
            request.protocol().setBytes(buf, start, end - start);
        }

        if (request.protocol().isNull()) {
            throw new IllegalArgumentException(sm.getString("iib.invalidHttpProtocol"));
        }

        return true;
    }


    /**
     * Parse the HTTP headers.
     */
    @Override
    public boolean parseHeaders()
        throws IOException {
        if (!parsingHeader) {
            throw new IllegalStateException(
                    sm.getString("iib.parseheaders.ise.error"));
        }

        while (parseHeader()) {
            // Loop until there are no more headers
        }

        parsingHeader = false;
        end = pos;
        return true;
    }


    /**
     * Parse an HTTP header.
     *
     * @return false after reading a blank line (which indicates that the
     * HTTP header parsing is done
     */
    @SuppressWarnings("null") // headerValue cannot be null
    private boolean parseHeader() throws IOException {

        while (true) {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            prevChr = chr;
            chr = buf[pos];

            if (chr == Constants.CR && prevChr != Constants.CR) {
                // Possible start of CRLF - process the next byte.
            } else if (prevChr == Constants.CR && chr == Constants.LF) {
                pos++;
                return false;
            } else {
                if (prevChr == Constants.CR) {
                    // Must have read two bytes (first was CR, second was not LF)
                    pos--;
                }
                break;
            }

            pos++;
        }

        // Mark the current buffer position
        int start = pos;
        int lineStart = start;

        //
        // Reading the header name
        // Header name is always US-ASCII
        //

        boolean colon = false;
        MessageBytes headerValue = null;

        while (!colon) {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            if (buf[pos] == Constants.COLON) {
                colon = true;
                headerValue = headers.addValue(buf, start, pos - start);
            } else if (!HttpParser.isToken(buf[pos])) {
                // Non-token characters are illegal in header names
                // Parsing continues so the error can be reported in context
                // skipLine() will handle the error
                skipLine(lineStart, start);
                return true;
            }
            chr = buf[pos];
            if ((chr >= Constants.A) && (chr <= Constants.Z)) {
                buf[pos] = (byte) (chr - Constants.LC_OFFSET);
            }

            pos++;

        }

        // Mark the current buffer position
        start = pos;
        int realPos = pos;

        //
        // Reading the header value (which can be spanned over multiple lines)
        //

        boolean eol = false;
        boolean validLine = true;

        while (validLine) {

            boolean space = true;

            // Skipping spaces
            while (space) {

                // Read new bytes if needed
                if (pos >= lastValid) {
                    if (!fill())
                        throw new EOFException(sm.getString("iib.eof.error"));
                }

                if ((buf[pos] == Constants.SP) || (buf[pos] == Constants.HT)) {
                    pos++;
                } else {
                    space = false;
                }

            }

            int lastSignificantChar = realPos;

            // Reading bytes until the end of the line
            while (!eol) {

                // Read new bytes if needed
                if (pos >= lastValid) {
                    if (!fill())
                        throw new EOFException(sm.getString("iib.eof.error"));
                }

                prevChr = chr;
                chr = buf[pos];
                if (chr == Constants.CR) {
                    // Possible start of CRLF - process the next byte.
                } else if (prevChr == Constants.CR && chr == Constants.LF) {
                    eol = true;
                } else if (prevChr == Constants.CR) {
                    // Invalid value
                    // Delete the header (it will be the most recent one)
                    headers.removeHeader(headers.size() - 1);
                    skipLine(lineStart, start);
                    return true;
                } else if (chr != Constants.HT && HttpParser.isControl(chr)) {
                    // Invalid value
                    // Delete the header (it will be the most recent one)
                    headers.removeHeader(headers.size() - 1);
                    skipLine(lineStart, start);
                    return true;
                } else if (buf[pos] == Constants.SP) {
                    buf[realPos] = buf[pos];
                    realPos++;
                } else {
                    buf[realPos] = buf[pos];
                    realPos++;
                    lastSignificantChar = realPos;
                }

                pos++;

            }

            realPos = lastSignificantChar;

            // Checking the first character of the new line. If the character
            // is a LWS, then it's a multiline header

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            byte peek = buf[pos];
            if (peek != Constants.SP && peek != Constants.HT) {
                validLine = false;
            } else {
                eol = false;
                // Copying one extra space in the buffer (since there must
                // be at least one space inserted between the lines)
                buf[realPos] = peek;
                realPos++;
            }

        }

        // Set the header value
        headerValue.setBytes(buf, start, realPos - start);

        return true;

    }


    private void skipLine(int lineStart, int start) throws IOException {
        boolean eol = false;
        int lastRealByte = start;
        if (pos - 1 > start) {
            lastRealByte = pos - 1;
        }

        while (!eol) {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            prevChr = chr;
            chr = buf[pos];

            if (chr == Constants.CR) {
                // Skip
            } else if (prevChr == Constants.CR && chr == Constants.LF) {
                eol = true;
            } else {
                lastRealByte = pos;
            }
            pos++;
        }

        if (rejectIllegalHeaderName || log.isDebugEnabled()) {
            String message = sm.getString("iib.invalidheader", HeaderUtil.toPrintableString(
                    buf, lineStart, lastRealByte - lineStart + 1));
            if (rejectIllegalHeaderName) {
                throw new IllegalArgumentException(message);
            }
            log.debug(message);
        }
    }


    // ---------------------------------------------------- InputBuffer Methods


    /**
     * Read some bytes.
     */
    @Override
    public int doRead(ByteChunk chunk, Request req)
        throws IOException {

        if (lastActiveFilter == -1)
            return inputStreamInputBuffer.doRead(chunk, req);
        else
            return activeFilters[lastActiveFilter].doRead(chunk,req);

    }


    // ------------------------------------------------------ Protected Methods

    @Override
    protected void init(SocketWrapper<Long> socketWrapper,
            AbstractEndpoint<Long> endpoint) throws IOException {

        wrapper = socketWrapper;
        socket = socketWrapper.getSocket().longValue();
        Socket.setrbb(this.socket, bbuf);
    }


    @Override
    protected boolean fill(boolean block) throws IOException {
        // Ignore the block parameter and just call fill
        return fill();
    }


    /**
     * Fill the internal buffer using data from the underlying input stream.
     *
     * @return false if at end of stream
     */
    protected boolean fill()
        throws IOException {

        int nRead = 0;

        if (parsingHeader) {

            if (lastValid == buf.length) {
                throw new IllegalArgumentException
                    (sm.getString("iib.requestheadertoolarge.error"));
            }

            bbuf.clear();
            nRead = Socket.recvbb(socket, 0, buf.length - lastValid);
            if (nRead > 0) {
                bbuf.limit(nRead);
                bbuf.get(buf, pos, nRead);
                lastValid = pos + nRead;
            } else {
                if ((-nRead) == Status.EAGAIN) {
                    return false;
                } else {
                    throw new IOException(sm.getString("iib.failedread"));
                }
            }

        } else {

            if (buf.length - end < 4500) {
                // In this case, the request header was really large, so we allocate a
                // brand new one; the old one will get GCed when subsequent requests
                // clear all references
                buf = new byte[buf.length];
                end = 0;
            }
            pos = end;
            lastValid = pos;
            bbuf.clear();
            nRead = Socket.recvbb(socket, 0, buf.length - lastValid);
            if (nRead > 0) {
                bbuf.limit(nRead);
                bbuf.get(buf, pos, nRead);
                lastValid = pos + nRead;
            } else {
                if ((-nRead) == Status.ETIMEDOUT || (-nRead) == Status.TIMEUP) {
                    throw new SocketTimeoutException(sm.getString("iib.failedread"));
                } else if (nRead == 0) {
                    // APR_STATUS_IS_EOF, since native 1.1.22
                    return false;
                } else if (-nRead == Status.APR_EGENERAL && wrapper.isSecure()) {
                    // Not entirely sure why this is necessary. Testing to date has not
                    // identified any issues with this but log it so it can be tracked
                    // if it is suspected of causing issues in the future.
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("iib.apr.sslGeneralError",
                                Long.valueOf(socket), wrapper));
                    }
                } else {
                    throw new IOException(sm.getString("iib.failedread"));
                }
            }

        }

        return (nRead > 0);
    }


    // ------------------------------------- InputStreamInputBuffer Inner Class


    /**
     * This class is an input buffer which will read its data from an input
     * stream.
     */
    protected class SocketInputBuffer
        implements InputBuffer {


        /**
         * Read bytes into the specified chunk.
         */
        @Override
        public int doRead(ByteChunk chunk, Request req )
            throws IOException {

            if (pos >= lastValid) {
                if (!fill())
                    return -1;
            }

            int length = lastValid - pos;
            chunk.setBytes(buf, pos, length);
            pos = lastValid;

            return (length);
        }
    }
}

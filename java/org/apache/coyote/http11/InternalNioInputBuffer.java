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
import java.nio.channels.Selector;
import java.nio.charset.Charset;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint;
import org.apache.tomcat.util.net.NioSelectorPool;

/**
 * Implementation of InputBuffer which provides HTTP request header parsing as
 * well as transfer decoding.
 *
 * @author <a href="mailto:remm@apache.org">Remy Maucherat</a>
 * @author Filip Hanik
 */
public class InternalNioInputBuffer extends AbstractInputBuffer {

    private static final org.apache.juli.logging.Log log =
        org.apache.juli.logging.LogFactory.getLog(InternalNioInputBuffer.class);

    private static final Charset DEFAULT_CHARSET =
        Charset.forName("ISO-8859-1");

    // -------------------------------------------------------------- Constants

    enum HeaderParseStatus {DONE, HAVE_MORE_HEADERS, NEED_MORE_DATA}
    enum HeaderParsePosition {HEADER_START, HEADER_NAME, HEADER_VALUE,
        HEADER_MULTI_LINE, HEADER_SKIPLINE}
    // ----------------------------------------------------------- Constructors
    

    /**
     * Alternate constructor.
     */
    public InternalNioInputBuffer(Request request, int headerBufferSize) {

        this.request = request;
        headers = request.getMimeHeaders();

        this.headerBufferSize = headerBufferSize;

        inputStreamInputBuffer = new SocketInputBuffer();

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
        headerData.recycle();
        swallowInput = true;

    }

    /**
     * Parsing state - used for non blocking parsing so that
     * when more data arrives, we can pick up where we left off.
     */
    protected boolean parsingRequestLine;
    protected int parsingRequestLinePhase = 0;
    protected boolean parsingRequestLineEol = false;
    protected int parsingRequestLineStart = 0;
    protected int parsingRequestLineQPos = -1;
    protected HeaderParsePosition headerParsePos;

    /**
     * Underlying socket.
     */
    protected NioChannel socket;
    
    /**
     * Selector pool, for blocking reads and blocking writes
     */
    protected NioSelectorPool pool;


    /**
     * Maximum allowed size of the HTTP request line plus headers.
     */
    private final int headerBufferSize;

    /**
     * Known size of the NioChannel read buffer.
     */
    private int socketReadBufferSize;

    /**
     * Additional size we allocate to the buffer to be more effective when
     * skipping empty lines that may precede the request.
     */
    private static final int skipBlankLinesSize = 1024;

    /**
     * How many bytes in the buffer are occupied by skipped blank lines that
     * precede the request.
     */
    private int skipBlankLinesBytes;

    // ------------------------------------------------------------- Properties


    /**
     * Set the underlying socket.
     */
    public void setSocket(NioChannel socket) {
        this.socket = socket;
        socketReadBufferSize = socket.getBufHandler().getReadBuffer().capacity();
        int bufLength = skipBlankLinesSize + headerBufferSize
                + socketReadBufferSize;
        if (buf == null || buf.length < bufLength) {
            buf = new byte[bufLength];
        }
    }
    
    /**
     * Get the underlying socket input stream.
     */
    public NioChannel getSocket() {
        return socket;
    }

    public void setSelectorPool(NioSelectorPool pool) { 
        this.pool = pool;
    }
    
    public NioSelectorPool getSelectorPool() {
        return pool;
    }


    // --------------------------------------------------------- Public Methods
    /**
     * Issues a non blocking read
     * @return int
     * @throws IOException
     */
    public int nbRead() throws IOException {
        return readSocket(true,false);
    }

    /**
     * Recycle the input buffer. This should be called when closing the 
     * connection.
     */
    @Override
    public void recycle() {
        // Recycle filters
        for (int i = 0; i <= lastActiveFilter; i++) {
            activeFilters[i].recycle();
        }
        // This must be after filters since it resets the lastFilterIndex
        super.recycle();
        socket = null;
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
    @Override
    public void nextRequest() {
        super.nextRequest();
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
    @Override
    public boolean parseRequestLine(boolean useAvailableDataOnly)
        throws IOException {

        //check state
        if ( !parsingRequestLine ) return true;
        //
        // Skipping blank lines
        //
        if ( parsingRequestLinePhase == 0 ) {
            byte chr = 0;
            do {
                
                // Read new bytes if needed
                if (pos >= lastValid) {
                    if (useAvailableDataOnly) {
                        return false;
                    }
                    // Ignore bytes that were read
                    pos = lastValid = 0;
                    // Do a simple read with a short timeout
                    if ( readSocket(true, false)==0 ) return false;
                }
                chr = buf[pos++];
            } while ((chr == Constants.CR) || (chr == Constants.LF));
            pos--;
            if (pos >= skipBlankLinesSize) {
                // Move data, to have enough space for further reading
                // of headers and body
                System.arraycopy(buf, pos, buf, 0, lastValid - pos);
                lastValid -= pos;
                pos = 0;
            }
            skipBlankLinesBytes = pos;
            parsingRequestLineStart = pos;
            parsingRequestLinePhase = 2;
            if (log.isDebugEnabled()) {
                log.debug("Received ["
                        + new String(buf, pos, lastValid - pos, DEFAULT_CHARSET)
                        + "]");
            }
        }
        if ( parsingRequestLinePhase == 2 ) {
            //
            // Reading the method name
            // Method name is always US-ASCII
            //
            boolean space = false;
            while (!space) {
                // Read new bytes if needed
                if (pos >= lastValid) {
                    if (!fill(true, false)) //request line parsing
                        return false;
                }
                // Spec says no CR or LF in method name
                if (buf[pos] == Constants.CR || buf[pos] == Constants.LF) {
                    throw new IllegalArgumentException(
                            sm.getString("iib.invalidmethod"));
                }
                if (buf[pos] == Constants.SP || buf[pos] == Constants.HT) {
                    space = true;
                    request.method().setBytes(buf, parsingRequestLineStart, pos - parsingRequestLineStart);
                }
                pos++;
            }
            parsingRequestLinePhase = 3;
        }
        if ( parsingRequestLinePhase == 3 ) {
            // Spec says single SP but also be tolerant of multiple and/or HT
            boolean space = true;
            while (space) {
                // Read new bytes if needed
                if (pos >= lastValid) {
                    if (!fill(true, false)) //request line parsing
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
                    if (!fill(true,false)) //request line parsing
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
            request.unparsedURI().setBytes(buf, parsingRequestLineStart, end - parsingRequestLineStart);
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
                    if (!fill(true, false)) //request line parsing
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
        }
        if (parsingRequestLinePhase == 6) { 
            // Mark the current buffer position
            
            end = 0;
            //
            // Reading the protocol
            // Protocol is always US-ASCII
            //
            while (!parsingRequestLineEol) {
                // Read new bytes if needed
                if (pos >= lastValid) {
                    if (!fill(true, false)) //request line parsing
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
    
    private void expand(int newsize) {
        if ( newsize > buf.length ) {
            if (parsingHeader) {
                throw new IllegalArgumentException(
                        sm.getString("iib.requestheadertoolarge.error"));
            }
            // Should not happen
            log.warn("Expanding buffer size. Old size: " + buf.length
                    + ", new size: " + newsize, new Exception());
            byte[] tmp = new byte[newsize];
            System.arraycopy(buf,0,tmp,0,buf.length);
            buf = tmp;
        }
    }
    
    /**
     * Perform blocking read with a timeout if desired
     * @param timeout boolean - if we want to use the timeout data
     * @param block - true if the system should perform a blocking read, false otherwise
     * @return boolean - true if data was read, false is no data read, EOFException if EOF is reached
     * @throws IOException if a socket exception occurs
     * @throws EOFException if end of stream is reached
     */
    
    private int readSocket(boolean timeout, boolean block) throws IOException {
        int nRead = 0;
        socket.getBufHandler().getReadBuffer().clear();
        if ( block ) {
            Selector selector = null;
            try {
                selector = getSelectorPool().get();
            } catch ( IOException x ) {
                // Ignore
            }
            try {
                NioEndpoint.KeyAttachment att = (NioEndpoint.KeyAttachment)socket.getAttachment(false);
                if ( att == null ) throw new IOException("Key must be cancelled.");
                nRead = getSelectorPool().read(socket.getBufHandler().getReadBuffer(),socket,selector,att.getTimeout());
            } catch ( EOFException eof ) {
                nRead = -1;
            } finally { 
                if ( selector != null ) getSelectorPool().put(selector);
            }
        } else {
            nRead = socket.read(socket.getBufHandler().getReadBuffer());
        }
        if (nRead > 0) {
            socket.getBufHandler().getReadBuffer().flip();
            socket.getBufHandler().getReadBuffer().limit(nRead);
            expand(nRead + pos);
            socket.getBufHandler().getReadBuffer().get(buf, pos, nRead);
            lastValid = pos + nRead;
            return nRead;
        } else if (nRead == -1) {
            //return false;
            throw new EOFException(sm.getString("iib.eof.error"));
        } else {
            return 0;
        }
    }

    /**
     * Parse the HTTP headers.
     */
    @Override
    public boolean parseHeaders()
        throws IOException {
        HeaderParseStatus status = HeaderParseStatus.HAVE_MORE_HEADERS;
        
        do {
            status = parseHeader();
        } while ( status == HeaderParseStatus.HAVE_MORE_HEADERS );
        if (status == HeaderParseStatus.DONE) {
            parsingHeader = false;
            end = pos;
            // Checking that
            // (1) Headers plus request line size does not exceed its limit
            // (2) There are enough bytes to avoid expanding the buffer when
            // reading body
            // Technically, (2) is technical limitation, (1) is logical
            // limitation to enforce the meaning of headerBufferSize
            // From the way how buf is allocated and how blank lines are being
            // read, it should be enough to check (1) only.
            if (end - skipBlankLinesBytes > headerBufferSize
                    || buf.length - end < socketReadBufferSize) {
                throw new IllegalArgumentException(
                        sm.getString("iib.requestheadertoolarge.error"));
            }
            return true;
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
    public HeaderParseStatus parseHeader()
        throws IOException {

        //
        // Check for blank line
        //

        byte chr = 0;
        while (headerParsePos == HeaderParsePosition.HEADER_START) {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill(true,false)) {//parse header 
                    headerParsePos = HeaderParsePosition.HEADER_START;
                    return HeaderParseStatus.NEED_MORE_DATA;
                }
            }

            chr = buf[pos];

            if ((chr == Constants.CR) || (chr == Constants.LF)) {
                if (chr == Constants.LF) {
                    pos++;
                    return HeaderParseStatus.DONE;
                }
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
                if (!fill(true,false)) { //parse header 
                    return HeaderParseStatus.NEED_MORE_DATA;
                }
            }

            if (buf[pos] == Constants.COLON) {
                headerParsePos = HeaderParsePosition.HEADER_VALUE;
                headerData.headerValue = headers.addValue(buf, headerData.start, pos - headerData.start);
            } else if (!HTTP_TOKEN_CHAR[buf[pos]]) {
                // If a non-token header is detected, skip the line and
                // ignore the header
                return skipLine();
            }
            chr = buf[pos];
            if ((chr >= Constants.A) && (chr <= Constants.Z)) {
                buf[pos] = (byte) (chr - Constants.LC_OFFSET);
            }

            pos++;
            if ( headerParsePos == HeaderParsePosition.HEADER_VALUE ) { 
                // Mark the current buffer position
                headerData.start = pos;
                headerData.realPos = pos;
            }
        }

        
        while (headerParsePos == HeaderParsePosition.HEADER_SKIPLINE) {
            return skipLine();
        }

        //
        // Reading the header value (which can be spanned over multiple lines)
        //

        boolean eol = false;

        while (headerParsePos == HeaderParsePosition.HEADER_VALUE ||
               headerParsePos == HeaderParsePosition.HEADER_MULTI_LINE) {
            if ( headerParsePos == HeaderParsePosition.HEADER_VALUE ) {
            
                boolean space = true;

                // Skipping spaces
                while (space) {

                    // Read new bytes if needed
                    if (pos >= lastValid) {
                        if (!fill(true,false)) {//parse header 
                            //HEADER_VALUE, should already be set
                            return HeaderParseStatus.NEED_MORE_DATA;
                        }
                    }

                    if ((buf[pos] == Constants.SP) || (buf[pos] == Constants.HT)) {
                        pos++;
                    } else {
                        space = false;
                    }

                }

                headerData.lastSignificantChar = headerData.realPos;

                // Reading bytes until the end of the line
                while (!eol) {

                    // Read new bytes if needed
                    if (pos >= lastValid) {
                        if (!fill(true,false)) {//parse header 
                            //HEADER_VALUE
                            return HeaderParseStatus.NEED_MORE_DATA;
                        }

                    }

                    if (buf[pos] == Constants.CR) {
                        // Skip
                    } else if (buf[pos] == Constants.LF) {
                        eol = true;
                    } else if (buf[pos] == Constants.SP) {
                        buf[headerData.realPos] = buf[pos];
                        headerData.realPos++;
                    } else {
                        buf[headerData.realPos] = buf[pos];
                        headerData.realPos++;
                        headerData.lastSignificantChar = headerData.realPos;
                    }

                    pos++;

                }

                headerData.realPos = headerData.lastSignificantChar;

                // Checking the first character of the new line. If the character
                // is a LWS, then it's a multiline header
                headerParsePos = HeaderParsePosition.HEADER_MULTI_LINE;
            }
            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill(true,false)) {//parse header
                    
                    //HEADER_MULTI_LINE
                    return HeaderParseStatus.NEED_MORE_DATA;
                }
            }

            chr = buf[pos];
            if ( headerParsePos == HeaderParsePosition.HEADER_MULTI_LINE ) {
                if ( (chr != Constants.SP) && (chr != Constants.HT)) {
                    headerParsePos = HeaderParsePosition.HEADER_START;
                } else {
                    eol = false;
                    // Copying one extra space in the buffer (since there must
                    // be at least one space inserted between the lines)
                    buf[headerData.realPos] = chr;
                    headerData.realPos++;
                    headerParsePos = HeaderParsePosition.HEADER_VALUE;
                }
            }
        }
        // Set the header value
        headerData.headerValue.setBytes(buf, headerData.start, headerData.realPos - headerData.start);
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
                if (!fill(true,false)) {
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
                    DEFAULT_CHARSET)));
        }

        headerParsePos = HeaderParsePosition.HEADER_START;
        return HeaderParseStatus.HAVE_MORE_HEADERS;
    }

    protected HeaderParseData headerData = new HeaderParseData();
    public static class HeaderParseData {
        int start = 0;
        int realPos = 0;
        int lastSignificantChar = 0;
        MessageBytes headerValue = null;
        public void recycle() {
            start = 0;
            realPos = 0;
            lastSignificantChar = 0;
            headerValue = null;
        }
    }


    /**
     * Available bytes (note that due to encoding, this may not correspond )
     */
    public int available() {
        int result = (lastValid - pos);
        if ((result == 0) && (lastActiveFilter >= 0)) {
            for (int i = 0; (result == 0) && (i <= lastActiveFilter); i++) {
                result = activeFilters[i].available();
            }
        }
        return result;
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * Fill the internal buffer using data from the underlying input stream.
     * 
     * @return false if at end of stream
     */
    @Override
    protected boolean fill(boolean block) throws IOException, EOFException {
        return fill(true,block);
    }

    protected boolean fill(boolean timeout, boolean block) throws IOException, EOFException {
        

        boolean read = false;

        if (parsingHeader) {

            if (lastValid == buf.length) {
                throw new IllegalArgumentException
                    (sm.getString("iib.requestheadertoolarge.error"));
            }

            // Do a simple read with a short timeout
            read = readSocket(timeout,block)>0;
        } else {
            lastValid = pos = end;
            // Do a simple read with a short timeout
            read = readSocket(timeout, block)>0;
        }
        return read;
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
                if (!fill(true,true)) //read body, must be blocking, as the thread is inside the app
                    return -1;
            }

            int length = lastValid - pos;
            chunk.setBytes(buf, pos, length);
            pos = lastValid;

            return (length);

        }


    }


    public int getParsingRequestLinePhase() {
        return parsingRequestLinePhase;
    }


}

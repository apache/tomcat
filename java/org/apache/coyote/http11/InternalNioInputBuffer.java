/*
 *  Copyright 1999-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.net.NioEndpoint.KeyAttachment;
import org.apache.tomcat.util.net.NioEndpoint.Poller;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.net.NioChannel;

/**
 * Implementation of InputBuffer which provides HTTP request header parsing as
 * well as transfer decoding.
 *
 * @author <a href="mailto:remm@apache.org">Remy Maucherat</a>
 * @author Filip Hanik
 */
public class InternalNioInputBuffer implements InputBuffer {


    // -------------------------------------------------------------- Constants


    // ----------------------------------------------------------- Constructors


    /**
     * Alternate constructor.
     */
    public InternalNioInputBuffer(Request request, int headerBufferSize, 
                                  long readTimeout) {

        this.request = request;
        headers = request.getMimeHeaders();

        buf = new byte[headerBufferSize];
//        if (headerBufferSize < (8 * 1024)) {
//            bbuf = ByteBuffer.allocateDirect(6 * 1500);
//        } else {
//            bbuf = ByteBuffer.allocateDirect((headerBufferSize / 1500 + 1) * 1500);
//        }

        inputStreamInputBuffer = new SocketInputBuffer();

        filterLibrary = new InputFilter[0];
        activeFilters = new InputFilter[0];
        lastActiveFilter = -1;

        parsingHeader = true;
        swallowInput = true;

        if (readTimeout < 0) {
            this.readTimeout = -1;
        } else {
            this.readTimeout = readTimeout;
        }

    }


    // -------------------------------------------------------------- Variables


    /**
     * The string manager for this package.
     */
    protected static StringManager sm =
        StringManager.getManager(Constants.Package);


    // ----------------------------------------------------- Instance Variables


    /**
     * Associated Coyote request.
     */
    protected Request request;


    /**
     * Headers of the associated request.
     */
    protected MimeHeaders headers;


    /**
     * State.
     */
    protected boolean parsingHeader;


    /**
     * Swallow input ? (in the case of an expectation)
     */
    protected boolean swallowInput;


    /**
     * Pointer to the current read buffer.
     */
    protected byte[] buf;


    /**
     * Last valid byte.
     */
    protected int lastValid;


    /**
     * Position in the buffer.
     */
    protected int pos;


    /**
     * Pos of the end of the header in the buffer, which is also the
     * start of the body.
     */
    protected int end;



    /**
     * Underlying socket.
     */
    protected NioChannel socket;


    /**
     * Underlying input buffer.
     */
    protected InputBuffer inputStreamInputBuffer;


    /**
     * Filter library.
     * Note: Filter[0] is always the "chunked" filter.
     */
    protected InputFilter[] filterLibrary;


    /**
     * Active filters (in order).
     */
    protected InputFilter[] activeFilters;


    /**
     * Index of the last active filter.
     */
    protected int lastActiveFilter;


    /**
     * The socket timeout used when reading the first block of the request
     * header.
     */
    protected long readTimeout;
    private Poller poller;

    // ------------------------------------------------------------- Properties


    /**
     * Set the underlying socket.
     */
    public void setSocket(NioChannel socket) {
        this.socket = socket;
    }


    /**
     * Get the underlying socket input stream.
     */
    public NioChannel getSocket() {
        return socket;
    }

    public Poller getPoller() {
        return poller;
    }

    /**
     * Add an input filter to the filter library.
     */
    public void addFilter(InputFilter filter) {

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
    public InputFilter[] getFilters() {

        return filterLibrary;

    }


    /**
     * Clear filters.
     */
    public void clearFilters() {

        filterLibrary = new InputFilter[0];
        lastActiveFilter = -1;

    }


    /**
     * Add an input filter to the filter library.
     */
    public void addActiveFilter(InputFilter filter) {

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
    public void setSwallowInput(boolean swallowInput) {
        this.swallowInput = swallowInput;
    }

    public void setPoller(Poller poller) {
        this.poller = poller;
    }

    // --------------------------------------------------------- Public Methods


    /**
     * Recycle the input buffer. This should be called when closing the 
     * connection.
     */
    public void recycle() {

        // Recycle Request object
        request.recycle();

        socket = null;
        lastValid = 0;
        pos = 0;
        lastActiveFilter = -1;
        parsingHeader = true;
        swallowInput = true;

    }


    /**
     * End processing of current HTTP request.
     * Note: All bytes of the current request should have been already 
     * consumed. This method only resets all the pointers so that we are ready
     * to parse the next HTTP request.
     */
    public void nextRequest() {

        // Recycle Request object
        request.recycle();

        //System.out.println("LV-pos: " + (lastValid - pos));
        // Copy leftover bytes to the beginning of the buffer
        if (lastValid - pos > 0) {
            int npos = 0;
            int opos = pos;
            while (lastValid - opos > opos - npos) {
                System.arraycopy(buf, opos, buf, npos, opos - npos);
                npos += pos;
                opos += pos;
            }
            System.arraycopy(buf, opos, buf, npos, lastValid - opos);
        }

        // Recycle filters
        for (int i = 0; i <= lastActiveFilter; i++) {
            activeFilters[i].recycle();
        }

        // Reset pointers
        lastValid = lastValid - pos;
        pos = 0;
        lastActiveFilter = -1;
        parsingHeader = true;
        swallowInput = true;

    }


    /**
     * End request (consumes leftover bytes).
     * 
     * @throws IOException an undelying I/O error occured
     */
    public void endRequest()
        throws IOException {

        if (swallowInput && (lastActiveFilter != -1)) {
            int extraBytes = (int) activeFilters[lastActiveFilter].end();
            pos = pos - extraBytes;
        }

    }


    /**
     * Read the request line. This function is meant to be used during the 
     * HTTP request header parsing. Do NOT attempt to read the request body 
     * using it.
     *
     * @throws IOException If an exception occurs during the underlying socket
     * read operations, or if the given buffer is not big enough to accomodate
     * the whole line.
     * @return true if data is properly fed; false if no data is available 
     * immediately and thread should be freed
     */
    public boolean parseRequestLine(boolean useAvailableData)
        throws IOException {

        int start = 0;

        //
        // Skipping blank lines
        //

        byte chr = 0;
        do {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (useAvailableData) {
                    return false;
                }
                if (readTimeout == -1) {
                    if (!fill()) //request line parsing
                        throw new EOFException(sm.getString("iib.eof.error"));
                } else {
                    // Do a simple read with a short timeout
                    if ( !readSocket(true, false) ) return false;
                }
            }

            chr = buf[pos++];

        } while ((chr == Constants.CR) || (chr == Constants.LF));

        pos--;

        // Mark the current buffer position
        start = pos;

        if (pos >= lastValid) {
            if (useAvailableData) {
                return false;
            }
            if (readTimeout == -1) {
                if (!fill()) //request line parsing
                    throw new EOFException(sm.getString("iib.eof.error"));
            } else {
                // Do a simple read with a short timeout
                if ( !readSocket(true, false) ) return false;
            }
        }

        //
        // Reading the method name
        // Method name is always US-ASCII
        //

        boolean space = false;

        while (!space) {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill()) //request line parsing
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            if (buf[pos] == Constants.SP) {
                space = true;
                request.method().setBytes(buf, start, pos - start);
            }

            pos++;

        }

        // Mark the current buffer position
        start = pos;
        int end = 0;
        int questionPos = -1;

        //
        // Reading the URI
        //

        space = false;
        boolean eol = false;

        while (!space) {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill()) //request line parsing
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            if (buf[pos] == Constants.SP) {
                space = true;
                end = pos;
            } else if ((buf[pos] == Constants.CR) 
                       || (buf[pos] == Constants.LF)) {
                // HTTP/0.9 style request
                eol = true;
                space = true;
                end = pos;
            } else if ((buf[pos] == Constants.QUESTION) 
                       && (questionPos == -1)) {
                questionPos = pos;
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

        // Mark the current buffer position
        start = pos;
        end = 0;

        //
        // Reading the protocol
        // Protocol is always US-ASCII
        //

        while (!eol) {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill()) //reques line parsing
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            if (buf[pos] == Constants.CR) {
                end = pos;
            } else if (buf[pos] == Constants.LF) {
                if (end == 0)
                    end = pos;
                eol = true;
            }

            pos++;

        }

        if ((end - start) > 0) {
            request.protocol().setBytes(buf, start, end - start);
        } else {
            request.protocol().setString("");
        }

        return true;

    }
    
    private void expand(int newsize) {
        if ( newsize > buf.length ) {
            byte[] tmp = new byte[newsize];
            System.arraycopy(buf,0,tmp,0,buf.length);
            buf = tmp;
            tmp = null;
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
    private boolean readSocket(boolean timeout, boolean block) throws IOException {
        int nRead = 0;
        long start = System.currentTimeMillis();
        boolean timedOut = false;
        do {
            
            socket.getBufHandler().getReadBuffer().clear();
            nRead = socket.read(socket.getBufHandler().getReadBuffer());
            if (nRead > 0) {
                socket.getBufHandler().getReadBuffer().flip();
                socket.getBufHandler().getReadBuffer().limit(nRead);
                expand(nRead + pos);
                socket.getBufHandler().getReadBuffer().get(buf, pos, nRead);
                lastValid = pos + nRead;
                return true;
            } else if (nRead == -1) {
                //return false;
                throw new EOFException(sm.getString("iib.eof.error"));
            } else if ( !block ) {
                return false;
            } else {
                timedOut = (readTimeout != -1) && ((System.currentTimeMillis()-start)>readTimeout);
                if ( !timedOut && nRead == 0 )  {
                    try {
                        final SelectionKey key = socket.getIOChannel().keyFor(poller.getSelector());
                        final KeyAttachment att = (KeyAttachment)key.attachment();
                        //to do, add in a check, we might have just timed out on the wait,
                        //so there is no need to register us again.
                        boolean addToQueue = false;
                        try { addToQueue = ((key.interestOps()&SelectionKey.OP_READ) != SelectionKey.OP_READ); } catch ( CancelledKeyException ckx ){ throw new IOException("Socket key cancelled.");}
                        if ( addToQueue ) {
                            synchronized (att.getMutex()) {
                                addToReadQueue(key, att);
                                att.getMutex().wait(readTimeout);
                            }
                        }//end if
                    }catch ( Exception x ) {}
                }
             }
        }while ( nRead == 0 && (!timedOut) );
        //else throw new IOException(sm.getString("iib.failedread"));
        //return false; //timeout
        throw new IOException("read timed out.");
    }

    private void addToReadQueue(final SelectionKey key, final KeyAttachment att) {
        att.setWakeUp(true);
        poller.addEvent(
            new Runnable() {
            public void run() {
                try {
                    if (key != null) key.interestOps(SelectionKey.OP_READ);
                } catch (CancelledKeyException ckx) {
                    try {
                        if ( att != null ) {
                            att.setError(true); //set to collect this socket immediately
                            att.setWakeUp(false);
                        }
                        try {socket.close();}catch (Exception ignore){}
                        if ( socket.isOpen() ) socket.close(true);
                    } catch (Exception ignore) {}
                }
            }
        });
    }


    /**
     * Parse the HTTP headers.
     */
    public void parseHeaders()
        throws IOException {

        while (parseHeader()) {
        }

        parsingHeader = false;
        end = pos;

    }


    /**
     * Parse an HTTP header.
     * 
     * @return false after reading a blank line (which indicates that the
     * HTTP header parsing is done
     */
    public boolean parseHeader()
        throws IOException {

        //
        // Check for blank line
        //

        byte chr = 0;
        while (true) {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill()) //parse header
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            chr = buf[pos];

            if ((chr == Constants.CR) || (chr == Constants.LF)) {
                if (chr == Constants.LF) {
                    pos++;
                    return false;
                }
            } else {
                break;
            }

            pos++;

        }

        // Mark the current buffer position
        int start = pos;

        //
        // Reading the header name
        // Header name is always US-ASCII
        //

        boolean colon = false;
        MessageBytes headerValue = null;

        while (!colon) {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill()) //parse header
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            if (buf[pos] == Constants.COLON) {
                colon = true;
                headerValue = headers.addValue(buf, start, pos - start);
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
                    if (!fill()) //parse header
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
                    if (!fill()) //parse header
                        throw new EOFException(sm.getString("iib.eof.error"));
                }

                if (buf[pos] == Constants.CR) {
                } else if (buf[pos] == Constants.LF) {
                    eol = true;
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
                if (!fill()) //parse header
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            chr = buf[pos];
            if ((chr != Constants.SP) && (chr != Constants.HT)) {
                validLine = false;
            } else {
                eol = false;
                // Copying one extra space in the buffer (since there must
                // be at least one space inserted between the lines)
                buf[realPos] = chr;
                realPos++;
            }

        }

        // Set the header value
        headerValue.setBytes(buf, start, realPos - start);

        return true;

    }


    // ---------------------------------------------------- InputBuffer Methods


    /**
     * Read some bytes.
     */
    public int doRead(ByteChunk chunk, Request req) 
        throws IOException {

        if (lastActiveFilter == -1)
            return inputStreamInputBuffer.doRead(chunk, req);
        else
            return activeFilters[lastActiveFilter].doRead(chunk,req);

    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Fill the internal buffer using data from the undelying input stream.
     * 
     * @return false if at end of stream
     */
    protected boolean fill()
        throws IOException {

        boolean read = false;

        if (parsingHeader) {

            if (lastValid == buf.length) {
                throw new IOException
                    (sm.getString("iib.requestheadertoolarge.error"));
            }

            // Do a simple read with a short timeout
            read = readSocket(true,true);
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
            // Do a simple read with a short timeout
            read = readSocket(true, true);
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
        public int doRead(ByteChunk chunk, Request req ) 
            throws IOException {

            if (pos >= lastValid) {
                if (!fill()) //read body
                    return -1;
            }

            int length = lastValid - pos;
            chunk.setBytes(buf, pos, length);
            pos = lastValid;

            return (length);

        }


    }


}

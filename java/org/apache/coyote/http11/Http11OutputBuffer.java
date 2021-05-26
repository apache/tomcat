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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.apache.coyote.ActionCode;
import org.apache.coyote.CloseNowException;
import org.apache.coyote.Response;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

/**
 * Provides buffering for the HTTP headers (allowing responses to be reset
 * before they have been committed) and the link to the Socket for writing the
 * headers (once committed) and the response body. Note that buffering of the
 * response body happens at a higher level.
 */
public class Http11OutputBuffer implements HttpOutputBuffer {

    // -------------------------------------------------------------- Variables

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Http11OutputBuffer.class);


    // ----------------------------------------------------- Instance Variables

    /**
     * Associated Coyote response.
     */
    protected final Response response;


    private volatile boolean ackSent = false;


    /**
     * Finished flag.
     */
    protected boolean responseFinished;


    /**
     * The buffer used for header composition.
     */
    protected final ByteBuffer headerBuffer;


    /**
     * Filter library for processing the response body.
     */
    protected OutputFilter[] filterLibrary;


    /**
     * Active filters for the current request.
     */
    protected OutputFilter[] activeFilters;


    /**
     * Index of the last active filter.
     */
    protected int lastActiveFilter;


    /**
     * Underlying output buffer.
     */
    protected HttpOutputBuffer outputStreamOutputBuffer;


    /**
     * Wrapper for socket where data will be written to.
     */
    protected SocketWrapperBase<?> socketWrapper;


    /**
     * Bytes written to client for the current request
     */
    protected long byteCount = 0;


    protected Http11OutputBuffer(Response response, int headerBufferSize) {

        this.response = response;

        headerBuffer = ByteBuffer.allocate(headerBufferSize);

        filterLibrary = new OutputFilter[0];
        activeFilters = new OutputFilter[0];
        lastActiveFilter = -1;

        responseFinished = false;

        outputStreamOutputBuffer = new SocketOutputBuffer();
    }


    // ------------------------------------------------------------- Properties

    /**
     * Add an output filter to the filter library. Note that calling this method
     * resets the currently active filters to none.
     *
     * @param filter The filter to add
     */
    public void addFilter(OutputFilter filter) {

        OutputFilter[] newFilterLibrary = Arrays.copyOf(filterLibrary, filterLibrary.length + 1);
        newFilterLibrary[filterLibrary.length] = filter;
        filterLibrary = newFilterLibrary;

        activeFilters = new OutputFilter[filterLibrary.length];
    }


    /**
     * Get filters.
     *
     * @return The current filter library containing all possible filters
     */
    public OutputFilter[] getFilters() {
        return filterLibrary;
    }


    /**
     * Add an output filter to the active filters for the current response.
     * <p>
     * The filter does not have to be present in {@link #getFilters()}.
     * <p>
     * A filter can only be added to a response once. If the filter has already
     * been added to this response then this method will be a NO-OP.
     *
     * @param filter The filter to add
     */
    public void addActiveFilter(OutputFilter filter) {

        if (lastActiveFilter == -1) {
            filter.setBuffer(outputStreamOutputBuffer);
        } else {
            for (int i = 0; i <= lastActiveFilter; i++) {
                if (activeFilters[i] == filter) {
                    return;
                }
            }
            filter.setBuffer(activeFilters[lastActiveFilter]);
        }

        activeFilters[++lastActiveFilter] = filter;

        filter.setResponse(response);
    }


    // --------------------------------------------------- OutputBuffer Methods

    @Override
    public int doWrite(ByteBuffer chunk) throws IOException {

        if (!response.isCommitted()) {
            // Send the connector a request for commit. The connector should
            // then validate the headers, send them (using sendHeaders) and
            // set the filters accordingly.
            response.action(ActionCode.COMMIT, null);
        }

        if (lastActiveFilter == -1) {
            return outputStreamOutputBuffer.doWrite(chunk);
        } else {
            return activeFilters[lastActiveFilter].doWrite(chunk);
        }
    }


    @Override
    public long getBytesWritten() {
        if (lastActiveFilter == -1) {
            return outputStreamOutputBuffer.getBytesWritten();
        } else {
            return activeFilters[lastActiveFilter].getBytesWritten();
        }
    }


    // ----------------------------------------------- HttpOutputBuffer Methods

    /**
     * Flush the response.
     *
     * @throws IOException an underlying I/O error occurred
     */
    @Override
    public void flush() throws IOException {
        if (lastActiveFilter == -1) {
            outputStreamOutputBuffer.flush();
        } else {
            activeFilters[lastActiveFilter].flush();
        }
    }


    @Override
    public void end() throws IOException {
        if (responseFinished) {
            return;
        }

        if (lastActiveFilter == -1) {
            outputStreamOutputBuffer.end();
        } else {
            activeFilters[lastActiveFilter].end();
        }

        responseFinished = true;
    }


    // --------------------------------------------------------- Public Methods

    /**
     * Reset the header buffer if an error occurs during the writing of the
     * headers so the error response can be written.
     */
    void resetHeaderBuffer() {
        headerBuffer.position(0).limit(headerBuffer.capacity());
    }


    /**
     * Recycle the output buffer. This should be called when closing the
     * connection.
     */
    public void recycle() {
        nextRequest();
        socketWrapper = null;
    }


    /**
     * End processing of current HTTP request.
     * Note: All bytes of the current request should have been already
     * consumed. This method only resets all the pointers so that we are ready
     * to parse the next HTTP request.
     */
    public void nextRequest() {
        // Recycle filters
        for (int i = 0; i <= lastActiveFilter; i++) {
            activeFilters[i].recycle();
        }
        // Recycle response object
        response.recycle();
        // Reset pointers
        headerBuffer.position(0).limit(headerBuffer.capacity());
        lastActiveFilter = -1;
        ackSent = false;
        responseFinished = false;
        byteCount = 0;
    }


    public void init(SocketWrapperBase<?> socketWrapper) {
        this.socketWrapper = socketWrapper;
    }


    public void sendAck() throws IOException {
        // It possible that the protocol configuration is changed between the
        // request being received and the first read of the body. That could led
        // to multiple calls to this method so ensure the ACK is only sent once.
        if (!response.isCommitted() && !ackSent) {
            ackSent = true;
            socketWrapper.write(isBlocking(), Constants.ACK_BYTES, 0, Constants.ACK_BYTES.length);
            if (flushBuffer(true)) {
                throw new IOException(sm.getString("iob.failedwrite.ack"));
            }
        }
    }


    /**
     * Commit the response.
     *
     * @throws IOException an underlying I/O error occurred
     */
    protected void commit() throws IOException {
        response.setCommitted(true);

        if (headerBuffer.position() > 0) {
            // Sending the response header buffer
            headerBuffer.flip();
            try {
                SocketWrapperBase<?> socketWrapper = this.socketWrapper;
                if (socketWrapper != null) {
                    socketWrapper.write(isBlocking(), headerBuffer);
                } else {
                    throw new CloseNowException(sm.getString("iob.failedwrite"));
                }
            } finally {
                headerBuffer.position(0).limit(headerBuffer.capacity());
            }
        }
    }


    /**
     * Send the response status line.
     */
    public void sendStatus() {
        // Write protocol name
        write(Constants.HTTP_11_BYTES);
        headerBuffer.put(Constants.SP);

        // Write status code
        int status = response.getStatus();
        switch (status) {
        case 200:
            write(Constants._200_BYTES);
            break;
        case 400:
            write(Constants._400_BYTES);
            break;
        case 404:
            write(Constants._404_BYTES);
            break;
        default:
            write(status);
        }

        headerBuffer.put(Constants.SP);

        // The reason phrase is optional but the space before it is not. Skip
        // sending the reason phrase. Clients should ignore it (RFC 7230) and it
        // just wastes bytes.

        headerBuffer.put(Constants.CR).put(Constants.LF);
    }


    /**
     * Send a header.
     *
     * @param name Header name
     * @param value Header value
     */
    public void sendHeader(MessageBytes name, MessageBytes value) {
        write(name);
        headerBuffer.put(Constants.COLON).put(Constants.SP);
        write(value);
        headerBuffer.put(Constants.CR).put(Constants.LF);
    }


    /**
     * End the header block.
     */
    public void endHeaders() {
        headerBuffer.put(Constants.CR).put(Constants.LF);
    }


    /**
     * This method will write the contents of the specified message bytes
     * buffer to the output stream, without filtering. This method is meant to
     * be used to write the response header.
     *
     * @param mb data to be written
     */
    private void write(MessageBytes mb) {
        if (mb.getType() != MessageBytes.T_BYTES) {
            mb.toBytes();
            ByteChunk bc = mb.getByteChunk();
            // Need to filter out CTLs excluding TAB. ISO-8859-1 and UTF-8
            // values will be OK. Strings using other encodings may be
            // corrupted.
            byte[] buffer = bc.getBuffer();
            for (int i = bc.getOffset(); i < bc.getLength(); i++) {
                // byte values are signed i.e. -128 to 127
                // The values are used unsigned. 0 to 31 are CTLs so they are
                // filtered (apart from TAB which is 9). 127 is a control (DEL).
                // The values 128 to 255 are all OK. Converting those to signed
                // gives -128 to -1.
                if ((buffer[i] > -1 && buffer[i] <= 31 && buffer[i] != 9) ||
                        buffer[i] == 127) {
                    buffer[i] = ' ';
                }
            }
        }
        write(mb.getByteChunk());
    }


    /**
     * This method will write the contents of the specified byte chunk to the
     * output stream, without filtering. This method is meant to be used to
     * write the response header.
     *
     * @param bc data to be written
     */
    private void write(ByteChunk bc) {
        // Writing the byte chunk to the output buffer
        int length = bc.getLength();
        checkLengthBeforeWrite(length);
        headerBuffer.put(bc.getBytes(), bc.getStart(), length);
    }


    /**
     * This method will write the contents of the specified byte
     * buffer to the output stream, without filtering. This method is meant to
     * be used to write the response header.
     *
     * @param b data to be written
     */
    public void write(byte[] b) {
        checkLengthBeforeWrite(b.length);

        // Writing the byte chunk to the output buffer
        headerBuffer.put(b);
    }


    /**
     * This method will write the specified integer to the output stream. This
     * method is meant to be used to write the response header.
     *
     * @param value data to be written
     */
    private void write(int value) {
        // From the Tomcat 3.3 HTTP/1.0 connector
        String s = Integer.toString(value);
        int len = s.length();
        checkLengthBeforeWrite(len);
        for (int i = 0; i < len; i++) {
            char c = s.charAt (i);
            headerBuffer.put((byte) c);
        }
    }


    /**
     * Checks to see if there is enough space in the buffer to write the
     * requested number of bytes.
     */
    private void checkLengthBeforeWrite(int length) {
        // "+ 4": BZ 57509. Reserve space for CR/LF/COLON/SP characters that
        // are put directly into the buffer following this write operation.
        if (headerBuffer.position() + length + 4 > headerBuffer.capacity()) {
            throw new HeadersTooLargeException(
                    sm.getString("iob.responseheadertoolarge.error"));
        }
    }


    //------------------------------------------------------ Non-blocking writes

    /**
     * Writes any remaining buffered data.
     *
     * @param block     Should this method block until the buffer is empty
     * @return  <code>true</code> if data remains in the buffer (which can only
     *          happen in non-blocking mode) else <code>false</code>.
     * @throws IOException Error writing data
     */
    protected boolean flushBuffer(boolean block) throws IOException  {
        return socketWrapper.flush(block);
    }


    /**
     * Is standard Servlet blocking IO being used for output?
     * @return <code>true</code> if this is blocking IO
     */
    protected final boolean isBlocking() {
        return response.getWriteListener() == null;
    }


    protected final boolean isReady() {
        boolean result = !hasDataToWrite();
        if (!result) {
            socketWrapper.registerWriteInterest();
        }
        return result;
    }


    public boolean hasDataToWrite() {
        return socketWrapper.hasDataToWrite();
    }


    public void registerWriteInterest() {
        socketWrapper.registerWriteInterest();
    }


    boolean isChunking() {
        for (int i = 0; i < lastActiveFilter; i++) {
            if (activeFilters[i] == filterLibrary[Constants.CHUNKED_FILTER]) {
                return true;
            }
        }
        return false;
    }


    // ------------------------------------------ SocketOutputBuffer Inner Class

    /**
     * This class is an output buffer which will write data to a socket.
     */
    protected class SocketOutputBuffer implements HttpOutputBuffer {

        /**
         * Write chunk.
         */
        @Override
        public int doWrite(ByteBuffer chunk) throws IOException {
            try {
                int len = chunk.remaining();
                SocketWrapperBase<?> socketWrapper = Http11OutputBuffer.this.socketWrapper;
                if (socketWrapper != null) {
                    socketWrapper.write(isBlocking(), chunk);
                } else {
                    throw new CloseNowException(sm.getString("iob.failedwrite"));
                }
                len -= chunk.remaining();
                byteCount += len;
                return len;
            } catch (IOException ioe) {
                response.action(ActionCode.CLOSE_NOW, ioe);
                // Re-throw
                throw ioe;
            }
        }

        @Override
        public long getBytesWritten() {
            return byteCount;
        }

        @Override
        public void end() throws IOException {
            socketWrapper.flush(true);
        }

        @Override
        public void flush() throws IOException {
            socketWrapper.flush(isBlocking());
        }
    }
}

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

import org.apache.coyote.ActionCode;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Response;
import org.apache.coyote.http11.filters.GzipOutputFilter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
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
public class Http11OutputBuffer implements OutputBuffer {

    // -------------------------------------------------------------- Variables

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Http11OutputBuffer.class);


    /**
     * Logger.
     */
    private static final Log log = LogFactory.getLog(Http11OutputBuffer.class);


    // ----------------------------------------------------- Instance Variables

    /**
     * Associated Coyote response.
     */
    protected Response response;


    /**
     * Finished flag.
     */
    protected boolean responseFinished;


    /**
     * The buffer used for header composition.
     */
    protected byte[] headerBuffer;


    /**
     * Position in the header buffer.
     */
    protected int pos;


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
    protected OutputBuffer outputStreamOutputBuffer;


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

        headerBuffer = new byte[headerBufferSize];

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

        OutputFilter[] newFilterLibrary = new OutputFilter[filterLibrary.length + 1];
        for (int i = 0; i < filterLibrary.length; i++) {
            newFilterLibrary[i] = filterLibrary[i];
        }
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
                if (activeFilters[i] == filter)
                    return;
            }
            filter.setBuffer(activeFilters[lastActiveFilter]);
        }

        activeFilters[++lastActiveFilter] = filter;

        filter.setResponse(response);
    }


    // --------------------------------------------------- OutputBuffer Methods

    @Override
    public int doWrite(ByteChunk chunk) throws IOException {

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


    // --------------------------------------------------------- Public Methods

    /**
     * Flush the response.
     *
     * @throws IOException an underlying I/O error occurred
     */
    public void flush() throws IOException {
        // go through the filters and if there is gzip filter
        // invoke it to flush
        for (int i = 0; i <= lastActiveFilter; i++) {
            if (activeFilters[i] instanceof GzipOutputFilter) {
                if (log.isDebugEnabled()) {
                    log.debug("Flushing the gzip filter at position " + i +
                            " of the filter chain...");
                }
                ((GzipOutputFilter) activeFilters[i]).flush();
                break;
            }
        }

        // Flush the current buffer(s)
        flushBuffer(isBlocking());
    }


    /**
     * Reset the header buffer if an error occurs during the writing of the
     * headers so the error response can be written.
     */
    void resetHeaderBuffer() {
        pos = 0;
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
        pos = 0;
        lastActiveFilter = -1;
        responseFinished = false;
        byteCount = 0;
    }


    /**
     * Finish writing the response.
     *
     * @throws IOException an underlying I/O error occurred
     */
    public void finishResponse() throws IOException {
        if (responseFinished) {
            return;
        }

        if (lastActiveFilter != -1) {
            activeFilters[lastActiveFilter].end();
        }

        flushBuffer(true);

        responseFinished = true;
    }


    public void init(SocketWrapperBase<?> socketWrapper) {
        this.socketWrapper = socketWrapper;
    }


    public void sendAck() throws IOException {
        if (!response.isCommitted()) {
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

        if (pos > 0) {
            // Sending the response header buffer
            socketWrapper.write(isBlocking(), headerBuffer, 0, pos);
        }
    }


    /**
     * Send the response status line.
     */
    public void sendStatus() {
        // Write protocol name
        write(Constants.HTTP_11_BYTES);
        headerBuffer[pos++] = Constants.SP;

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
            write(String.valueOf(status));
        }

        headerBuffer[pos++] = Constants.SP;

        // The reason phrase is optional but the space before it is not. Skip
        // sending the reason phrase. Clients should ignore it (RFC 7230) and it
        // just wastes bytes.

        headerBuffer[pos++] = Constants.CR;
        headerBuffer[pos++] = Constants.LF;
    }


    /**
     * Send a header.
     *
     * @param name Header name
     * @param value Header value
     */
    public void sendHeader(MessageBytes name, MessageBytes value) {
        write(name);
        headerBuffer[pos++] = Constants.COLON;
        headerBuffer[pos++] = Constants.SP;
        write(value);
        headerBuffer[pos++] = Constants.CR;
        headerBuffer[pos++] = Constants.LF;
    }


    /**
     * End the header block.
     */
    public void endHeaders() {
        headerBuffer[pos++] = Constants.CR;
        headerBuffer[pos++] = Constants.LF;
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
        System.arraycopy(bc.getBytes(), bc.getStart(), headerBuffer, pos, length);
        pos = pos + length;
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
        System.arraycopy(b, 0, headerBuffer, pos, b.length);
        pos = pos + b.length;
    }


    /**
     * This method will write the contents of the specified String to the
     * output stream, without filtering. This method is meant to be used to
     * write the response header.
     *
     * @param s data to be written
     */
    private void write(String s) {
        if (s == null) {
            return;
        }

        // From the Tomcat 3.3 HTTP/1.0 connector
        int len = s.length();
        checkLengthBeforeWrite(len);
        for (int i = 0; i < len; i++) {
            char c = s.charAt (i);
            // Note:  This is clearly incorrect for many strings,
            // but is the only consistent approach within the current
            // servlet framework.  It must suffice until servlet output
            // streams properly encode their output.
            if (((c <= 31) && (c != 9)) || c == 127 || c > 255) {
                c = ' ';
            }
            headerBuffer[pos++] = (byte) c;
        }
    }


    /**
     * Checks to see if there is enough space in the buffer to write the
     * requested number of bytes.
     */
    private void checkLengthBeforeWrite(int length) {
        // "+ 4": BZ 57509. Reserve space for CR/LF/COLON/SP characters that
        // are put directly into the buffer following this write operation.
        if (pos + length + 4 > headerBuffer.length) {
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


    // ------------------------------------------ SocketOutputBuffer Inner Class

    /**
     * This class is an output buffer which will write data to a socket.
     */
    protected class SocketOutputBuffer implements OutputBuffer {

        /**
         * Write chunk.
         */
        @Override
        public int doWrite(ByteChunk chunk) throws IOException {
            int len = chunk.getLength();
            int start = chunk.getStart();
            byte[] b = chunk.getBuffer();
            socketWrapper.write(isBlocking(), b, start, len);
            byteCount += len;
            return len;
        }

        @Override
        public long getBytesWritten() {
            return byteCount;
        }
    }
}

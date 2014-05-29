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
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Iterator;
import java.util.concurrent.LinkedBlockingDeque;

import org.apache.coyote.ActionCode;
import org.apache.coyote.ByteBufferHolder;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Response;
import org.apache.coyote.http11.filters.GzipOutputFilter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.CharChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.HttpMessages;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.SocketWrapper;
import org.apache.tomcat.util.res.StringManager;

public abstract class AbstractOutputBuffer<S> implements OutputBuffer {

    // ----------------------------------------------------- Instance Variables


    /**
     * Associated Coyote response.
     */
    protected Response response;


    /**
     * Committed flag.
     */
    protected boolean committed;


    /**
     * Finished flag.
     */
    protected boolean finished;


    /**
     * The buffer used for header composition.
     */
    protected byte[] headerBuffer;


    /**
     * Position in the buffer.
     */
    protected int pos;


    /**
     * Filter library.
     * Note: Filter[0] is always the "chunked" filter.
     */
    protected OutputFilter[] filterLibrary;


    /**
     * Active filter (which is actually the top of the pipeline).
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
     * Bytes written to client for the current request
     */
    protected long byteCount = 0;

    /**
     * Socket buffering.
     */
    protected int socketBuffer = -1;

    /**
     * For "non-blocking" writes use an external set of buffers. Although the
     * API only allows one non-blocking write at a time, due to buffering and
     * the possible need to write HTTP headers, there may be more than one write
     * to the OutputBuffer.
     */
    protected final LinkedBlockingDeque<ByteBufferHolder> bufferedWrites =
            new LinkedBlockingDeque<>();

    /**
     * The max size of the buffered write buffer
     */
    protected int bufferedWriteSize = 64*1024; //64k default write buffer


    protected AbstractOutputBuffer(Response response, int headerBufferSize) {

        this.response = response;

        headerBuffer = new byte[headerBufferSize];

        filterLibrary = new OutputFilter[0];
        activeFilters = new OutputFilter[0];
        lastActiveFilter = -1;

        committed = false;
        finished = false;

        // Cause loading of HttpMessages
        HttpMessages.getInstance(response.getLocale()).getMessage(200);
    }


    // -------------------------------------------------------------- Variables

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);

    /**
     * Logger.
     */
    private static final org.apache.juli.logging.Log log
        = org.apache.juli.logging.LogFactory.getLog(AbstractOutputBuffer.class);

    // ------------------------------------------------------------- Properties


    /**
     * Add an output filter to the filter library.
     */
    public void addFilter(OutputFilter filter) {

        OutputFilter[] newFilterLibrary =
            new OutputFilter[filterLibrary.length + 1];
        for (int i = 0; i < filterLibrary.length; i++) {
            newFilterLibrary[i] = filterLibrary[i];
        }
        newFilterLibrary[filterLibrary.length] = filter;
        filterLibrary = newFilterLibrary;

        activeFilters = new OutputFilter[filterLibrary.length];

    }


    /**
     * Get filters.
     */
    public OutputFilter[] getFilters() {

        return filterLibrary;

    }


    /**
     * Add an output filter to the filter library.
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


    /**
     * Set the socket buffer flag.
     */
    public void setSocketBuffer(int socketBuffer) {
        this.socketBuffer = socketBuffer;
    }


    /**
     * Get the socket buffer flag.
     */
    public int getSocketBuffer() {
        return socketBuffer;
    }


    public void setBufferedWriteSize(int bufferedWriteSize) {
        this.bufferedWriteSize = bufferedWriteSize;
    }


    public int getBufferedWriteSize() {
        return bufferedWriteSize;
    }


    // --------------------------------------------------- OutputBuffer Methods

    /**
     * Write the contents of a byte chunk.
     *
     * @param chunk byte chunk
     * @return number of bytes written
     * @throws IOException an underlying I/O error occurred
     */
    @Override
    public int doWrite(ByteChunk chunk, Response res)
        throws IOException {

        if (!committed) {

            // Send the connector a request for commit. The connector should
            // then validate the headers, send them (using sendHeaders) and
            // set the filters accordingly.
            response.action(ActionCode.COMMIT, null);

        }

        if (lastActiveFilter == -1)
            return outputStreamOutputBuffer.doWrite(chunk, res);
        else
            return activeFilters[lastActiveFilter].doWrite(chunk, res);

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
    public void flush()
        throws IOException {

        if (!committed) {

            // Send the connector a request for commit. The connector should
            // then validate the headers, send them (using sendHeader) and
            // set the filters accordingly.
            response.action(ActionCode.COMMIT, null);

        }

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
     * Reset current response.
     *
     * @throws IllegalStateException if the response has already been committed
     */
    public void reset() {

        if (committed) {
            throw new IllegalStateException(sm.getString("iob.illegalreset"));
        }

        // These will need to be reset if the reset was triggered by the error
        // handling if the headers were too large
        pos = 0;
        byteCount = 0;
    }

    /**
     * Recycle the output buffer. This should be called when closing the
     * connection.
     */
    public void recycle() {
        // Sub-classes may wish to do more than this.
        nextRequest();
        bufferedWrites.clear();
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
        committed = false;
        finished = false;
        byteCount = 0;
    }


    /**
     * End request.
     *
     * @throws IOException an underlying I/O error occurred
     */
    public void endRequest() throws IOException {

        if (!committed) {
            // Send the connector a request for commit. The connector should
            // then validate the headers, send them (using sendHeader) and
            // set the filters accordingly.
            response.action(ActionCode.COMMIT, null);
        }

        if (finished)
            return;

        if (lastActiveFilter != -1)
            activeFilters[lastActiveFilter].end();

        flushBuffer(true);

        finished = true;
    }


    public abstract void init(SocketWrapper<S> socketWrapper,
            AbstractEndpoint<S> endpoint) throws IOException;

    public abstract void sendAck() throws IOException;

    protected abstract void commit() throws IOException;


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
            write(status);
        }

        headerBuffer[pos++] = Constants.SP;

        // Write message
        String message = null;
        if (org.apache.coyote.Constants.USE_CUSTOM_STATUS_MSG_IN_HEADER &&
                HttpMessages.isSafeInHttpHeader(response.getMessage())) {
            message = response.getMessage();
        }
        if (message == null) {
            write(HttpMessages.getInstance(
                    response.getLocale()).getMessage(status));
        } else {
            write(message);
        }

        // End the response status line
        if (org.apache.coyote.Constants.IS_SECURITY_ENABLED){
           AccessController.doPrivileged(
                new PrivilegedAction<Void>(){
                    @Override
                    public Void run(){
                        headerBuffer[pos++] = Constants.CR;
                        headerBuffer[pos++] = Constants.LF;
                        return null;
                    }
                }
           );
        } else {
            headerBuffer[pos++] = Constants.CR;
            headerBuffer[pos++] = Constants.LF;
        }

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
    protected void write(MessageBytes mb) {

        if (mb.getType() == MessageBytes.T_BYTES) {
            ByteChunk bc = mb.getByteChunk();
            write(bc);
        } else if (mb.getType() == MessageBytes.T_CHARS) {
            CharChunk cc = mb.getCharChunk();
            write(cc);
        } else {
            write(mb.toString());
        }

    }


    /**
     * This method will write the contents of the specified message bytes
     * buffer to the output stream, without filtering. This method is meant to
     * be used to write the response header.
     *
     * @param bc data to be written
     */
    protected void write(ByteChunk bc) {

        // Writing the byte chunk to the output buffer
        int length = bc.getLength();
        checkLengthBeforeWrite(length);
        System.arraycopy(bc.getBytes(), bc.getStart(), headerBuffer, pos, length);
        pos = pos + length;

    }


    /**
     * This method will write the contents of the specified char
     * buffer to the output stream, without filtering. This method is meant to
     * be used to write the response header.
     *
     * @param cc data to be written
     */
    protected void write(CharChunk cc) {

        int start = cc.getStart();
        int end = cc.getEnd();
        checkLengthBeforeWrite(end-start);
        char[] cbuf = cc.getBuffer();
        for (int i = start; i < end; i++) {
            char c = cbuf[i];
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
    protected void write(String s) {

        if (s == null)
            return;

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
     * This method will print the specified integer to the output stream,
     * without filtering. This method is meant to be used to write the
     * response header.
     *
     * @param i data to be written
     */
    protected void write(int i) {

        write(String.valueOf(i));

    }


    /**
     * Checks to see if there is enough space in the buffer to write the
     * requested number of bytes.
     */
    private void checkLengthBeforeWrite(int length) {
        if (pos + length > headerBuffer.length) {
            throw new HeadersTooLargeException(
                    sm.getString("iob.responseheadertoolarge.error"));
        }
    }


    //------------------------------------------------------ Non-blocking writes

    protected abstract boolean hasMoreDataToFlush();
    protected abstract void registerWriteInterest() throws IOException;


    /**
     * Writes any remaining buffered data.
     *
     * @param block     Should this method block until the buffer is empty
     * @return  <code>true</code> if data remains in the buffer (which can only
     *          happen in non-blocking mode) else <code>false</code>.
     * @throws IOException
     */
    protected abstract boolean flushBuffer(boolean block) throws IOException;


    /**
     * Is standard Servlet blocking IO being used for output?
     */
    protected final boolean isBlocking() {
        return response.getWriteListener() == null;
    }


    protected final boolean isReady() throws IOException {
        boolean result = !hasDataToWrite();
        if (!result) {
            registerWriteInterest();
        }
        return result;
    }


    public boolean hasDataToWrite() {
        return hasMoreDataToFlush() || hasBufferedData();
    }


    protected boolean hasBufferedData() {
        boolean result = false;
        if (bufferedWrites!=null) {
            Iterator<ByteBufferHolder> iter = bufferedWrites.iterator();
            while (!result && iter.hasNext()) {
                result = iter.next().hasData();
            }
        }
        return result;
    }
}

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
import java.io.OutputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;

import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.CharChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.HttpMessages;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.res.StringManager;

import org.apache.coyote.ActionCode;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Response;

/**
 * Output buffer.
 * 
 * @author <a href="mailto:remm@apache.org">Remy Maucherat</a>
 */
public class InternalOutputBuffer 
    implements OutputBuffer, ByteChunk.ByteOutputChannel {

    // -------------------------------------------------------------- Constants


    // ----------------------------------------------------------- Constructors


    /**
     * Default constructor.
     */
    public InternalOutputBuffer(Response response) {
        this(response, Constants.DEFAULT_HTTP_HEADER_BUFFER_SIZE);
    }


    /**
     * Alternate constructor.
     */
    public InternalOutputBuffer(Response response, int headerBufferSize) {

        this.response = response;
        headers = response.getMimeHeaders();

        headerBuffer = new byte[headerBufferSize];
        buf = headerBuffer;

        outputStreamOutputBuffer = new OutputStreamOutputBuffer();

        filterLibrary = new OutputFilter[0];
        activeFilters = new OutputFilter[0];
        lastActiveFilter = -1;

        socketBuffer = new ByteChunk();
        socketBuffer.setByteOutputChannel(this);

        committed = false;
        finished = false;

    }


    // -------------------------------------------------------------- Variables


    /**
     * The string manager for this package.
     */
    protected static StringManager sm =
        StringManager.getManager(Constants.Package);


    // ----------------------------------------------------- Instance Variables


    /**
     * Associated Coyote response.
     */
    protected Response response;


    /**
     * Headers of the associated request.
     */
    protected MimeHeaders headers;


    /**
     * Committed flag.
     */
    protected boolean committed;


    /**
     * Finished flag.
     */
    protected boolean finished;


    /**
     * Pointer to the current read buffer.
     */
    protected byte[] buf;


    /**
     * Position in the buffer.
     */
    protected int pos;


    /**
     * HTTP header buffer.
     */
    protected byte[] headerBuffer;


    /**
     * Underlying output stream.
     */
    protected OutputStream outputStream;


    /**
     * Underlying output buffer.
     */
    protected OutputBuffer outputStreamOutputBuffer;


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
     * Socket buffer.
     */
    protected ByteChunk socketBuffer;


    /**
     * Socket buffer (extra buffering to reduce number of packets sent).
     */
    protected boolean useSocketBuffer = false;


    // ------------------------------------------------------------- Properties


    /**
     * Set the underlying socket output stream.
     */
    public void setOutputStream(OutputStream outputStream) {

        // FIXME: Check for null ?

        this.outputStream = outputStream;

    }


    /**
     * Get the underlying socket output stream.
     */
    public OutputStream getOutputStream() {

        return outputStream;

    }


    /**
     * Set the socket buffer size.
     */
    public void setSocketBuffer(int socketBufferSize) {

        if (socketBufferSize > 500) {
            useSocketBuffer = true;
            socketBuffer.allocate(socketBufferSize, socketBufferSize);
        } else {
            useSocketBuffer = false;
        }

    }


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
     * Clear filters.
     */
    public void clearFilters() {

        filterLibrary = new OutputFilter[0];
        lastActiveFilter = -1;

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


    // --------------------------------------------------------- Public Methods


    /**
     * Flush the response.
     * 
     * @throws IOException an undelying I/O error occured
     */
    public void flush()
        throws IOException {

        if (!committed) {

            // Send the connector a request for commit. The connector should
            // then validate the headers, send them (using sendHeader) and 
            // set the filters accordingly.
            response.action(ActionCode.ACTION_COMMIT, null);

        }

        // Flush the current buffer
        if (useSocketBuffer) {
            socketBuffer.flushBuffer();
        }

    }


    /**
     * Reset current response.
     * 
     * @throws IllegalStateException if the response has already been committed
     */
    public void reset() {

        if (committed)
            throw new IllegalStateException(/*FIXME:Put an error message*/);

        // Recycle Request object
        response.recycle();

    }


    /**
     * Recycle the output buffer. This should be called when closing the 
     * connection.
     */
    public void recycle() {

        // Recycle Request object
        response.recycle();
        socketBuffer.recycle();

        outputStream = null;
        buf = headerBuffer;
        pos = 0;
        lastActiveFilter = -1;
        committed = false;
        finished = false;

    }


    /**
     * End processing of current HTTP request.
     * Note: All bytes of the current request should have been already 
     * consumed. This method only resets all the pointers so that we are ready
     * to parse the next HTTP request.
     */
    public void nextRequest() {

        // Recycle Request object
        response.recycle();
        socketBuffer.recycle();

        // Determine the header buffer used for next request
        buf = headerBuffer;

        // Recycle filters
        for (int i = 0; i <= lastActiveFilter; i++) {
            activeFilters[i].recycle();
        }

        // Reset pointers
        pos = 0;
        lastActiveFilter = -1;
        committed = false;
        finished = false;

    }


    /**
     * End request.
     * 
     * @throws IOException an undelying I/O error occured
     */
    public void endRequest()
        throws IOException {

        if (!committed) {

            // Send the connector a request for commit. The connector should
            // then validate the headers, send them (using sendHeader) and 
            // set the filters accordingly.
            response.action(ActionCode.ACTION_COMMIT, null);

        }

        if (finished)
            return;

        if (lastActiveFilter != -1)
            activeFilters[lastActiveFilter].end();

        if (useSocketBuffer) {
            socketBuffer.flushBuffer();
        }

        finished = true;

    }


    // ------------------------------------------------ HTTP/1.1 Output Methods


    /**
     * Send an acknoledgement.
     */
    public void sendAck()
        throws IOException {

        if (!committed)
            outputStream.write(Constants.ACK_BYTES);

    }


    /**
     * Send the response status line.
     */
    public void sendStatus() {

        // Write protocol name
        write(Constants.HTTP_11_BYTES);
        buf[pos++] = Constants.SP;

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

        buf[pos++] = Constants.SP;

        // Write message
        String message = response.getMessage();
        if (message == null) {
            write(getMessage(status));
        } else {
            write(message);
        }

        // End the response status line
        if (System.getSecurityManager() != null){
           AccessController.doPrivileged(
                new PrivilegedAction(){
                    public Object run(){
                        buf[pos++] = Constants.CR;
                        buf[pos++] = Constants.LF;
                        return null;
                    }
                }
           );
        } else {
            buf[pos++] = Constants.CR;
            buf[pos++] = Constants.LF;
        }

    }

    private String getMessage(final int message){
        if (System.getSecurityManager() != null){
           return (String)AccessController.doPrivileged(
                new PrivilegedAction(){
                    public Object run(){
                        return HttpMessages.getMessage(message); 
                    }
                }
           );
        } else {
            return HttpMessages.getMessage(message);
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
        buf[pos++] = Constants.COLON;
        buf[pos++] = Constants.SP;
        write(value);
        buf[pos++] = Constants.CR;
        buf[pos++] = Constants.LF;

    }


    /**
     * Send a header.
     * 
     * @param name Header name
     * @param value Header value
     */
    public void sendHeader(ByteChunk name, ByteChunk value) {

        write(name);
        buf[pos++] = Constants.COLON;
        buf[pos++] = Constants.SP;
        write(value);
        buf[pos++] = Constants.CR;
        buf[pos++] = Constants.LF;

    }


    /**
     * Send a header.
     * 
     * @param name Header name
     * @param value Header value
     */
    public void sendHeader(String name, String value) {

        write(name);
        buf[pos++] = Constants.COLON;
        buf[pos++] = Constants.SP;
        write(value);
        buf[pos++] = Constants.CR;
        buf[pos++] = Constants.LF;

    }


    /**
     * End the header block.
     */
    public void endHeaders() {

        buf[pos++] = Constants.CR;
        buf[pos++] = Constants.LF;

    }


    // --------------------------------------------------- OutputBuffer Methods


    /**
     * Write the contents of a byte chunk.
     * 
     * @param chunk byte chunk
     * @return number of bytes written
     * @throws IOException an undelying I/O error occured
     */
    public int doWrite(ByteChunk chunk, Response res) 
        throws IOException {

        if (!committed) {

            // Send the connector a request for commit. The connector should
            // then validate the headers, send them (using sendHeaders) and 
            // set the filters accordingly.
            response.action(ActionCode.ACTION_COMMIT, null);

        }

        if (lastActiveFilter == -1)
            return outputStreamOutputBuffer.doWrite(chunk, res);
        else
            return activeFilters[lastActiveFilter].doWrite(chunk, res);

    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Commit the response.
     * 
     * @throws IOException an undelying I/O error occured
     */
    protected void commit()
        throws IOException {

        // The response is now committed
        committed = true;
        response.setCommitted(true);

        if (pos > 0) {
            // Sending the response header buffer
            if (useSocketBuffer) {
                socketBuffer.append(buf, 0, pos);
            } else {
                outputStream.write(buf, 0, pos);
            }
        }

    }


    /**
     * This method will write the contents of the specyfied message bytes 
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
     * This method will write the contents of the specyfied message bytes 
     * buffer to the output stream, without filtering. This method is meant to
     * be used to write the response header.
     * 
     * @param bc data to be written
     */
    protected void write(ByteChunk bc) {

        // Writing the byte chunk to the output buffer
        System.arraycopy(bc.getBytes(), bc.getStart(), buf, pos,
                         bc.getLength());
        pos = pos + bc.getLength();

    }


    /**
     * This method will write the contents of the specyfied char 
     * buffer to the output stream, without filtering. This method is meant to
     * be used to write the response header.
     * 
     * @param cc data to be written
     */
    protected void write(CharChunk cc) {

        int start = cc.getStart();
        int end = cc.getEnd();
        char[] cbuf = cc.getBuffer();
        for (int i = start; i < end; i++) {
            char c = cbuf[i];
            // Note:  This is clearly incorrect for many strings,
            // but is the only consistent approach within the current
            // servlet framework.  It must suffice until servlet output
            // streams properly encode their output.
            if ((c <= 31) && (c != 9)) {
                c = ' ';
            } else if (c == 127) {
                c = ' ';
            }
            buf[pos++] = (byte) c;
        }

    }


    /**
     * This method will write the contents of the specyfied byte 
     * buffer to the output stream, without filtering. This method is meant to
     * be used to write the response header.
     * 
     * @param b data to be written
     */
    public void write(byte[] b) {

        // Writing the byte chunk to the output buffer
        System.arraycopy(b, 0, buf, pos, b.length);
        pos = pos + b.length;

    }


    /**
     * This method will write the contents of the specyfied String to the 
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
        for (int i = 0; i < len; i++) {
            char c = s.charAt (i);
            // Note:  This is clearly incorrect for many strings,
            // but is the only consistent approach within the current
            // servlet framework.  It must suffice until servlet output
            // streams properly encode their output.
            if ((c <= 31) && (c != 9)) {
                c = ' ';
            } else if (c == 127) {
                c = ' ';
            }
            buf[pos++] = (byte) c;
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
     * Callback to write data from the buffer.
     */
    public void realWriteBytes(byte cbuf[], int off, int len)
        throws IOException {
        if (len > 0) {
            outputStream.write(cbuf, off, len);
        }
    }


    // ----------------------------------- OutputStreamOutputBuffer Inner Class


    /**
     * This class is an output buffer which will write data to an output
     * stream.
     */
    protected class OutputStreamOutputBuffer 
        implements OutputBuffer {


        /**
         * Write chunk.
         */
        public int doWrite(ByteChunk chunk, Response res) 
            throws IOException {

            if (useSocketBuffer) {
                socketBuffer.append(chunk.getBuffer(), chunk.getStart(), 
                                   chunk.getLength());
            } else {
                outputStream.write(chunk.getBuffer(), chunk.getStart(), 
                                   chunk.getLength());
            }
            return chunk.getLength();

        }


    }


}

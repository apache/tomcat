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

import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.juli.logging.Log;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.SocketWrapper;
import org.apache.tomcat.util.res.StringManager;

public abstract class AbstractInputBuffer<S> implements InputBuffer{

    protected static final boolean[] HTTP_TOKEN_CHAR = new boolean[128];

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


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
            } else if (i == '\t') {
                HTTP_TOKEN_CHAR[i] = false;
            } else {
                HTTP_TOKEN_CHAR[i] = true;
            }
        }
    }


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
     * Underlying input buffer.
     */
    protected InputBuffer inputStreamInputBuffer;


    /**
     * Filter library.
     * Note: Filter[Constants.CHUNKED_FILTER] is always the "chunked" filter.
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


    // ------------------------------------------------------------- Properties

    /**
     * Add an input filter to the filter library.
     *
     * @throws NullPointerException if the supplied filter is null
     */
    public void addFilter(InputFilter filter) {

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
    public InputFilter[] getFilters() {

        return filterLibrary;

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


    /**
     * Implementations are expected to call {@link Request#setStartTime(long)}
     * as soon as the first byte is read from the request.
     */
    public abstract boolean parseRequestLine(boolean useAvailableDataOnly)
        throws IOException;

    public abstract boolean parseHeaders() throws IOException;

    /**
     * Attempts to read some data into the input buffer.
     *
     * @return <code>true</code> if more data was added to the input buffer
     *         otherwise <code>false</code>
     */
    protected abstract boolean fill(boolean block) throws IOException;

    protected abstract void init(SocketWrapper<S> socketWrapper,
            AbstractEndpoint<S> endpoint) throws IOException;

    protected abstract Log getLog();


    // --------------------------------------------------------- Public Methods

    /**
     * Recycle the input buffer. This should be called when closing the
     * connection.
     */
    public void recycle() {

        // Recycle Request object
        request.recycle();

        // Recycle filters
        for (int i = 0; i <= lastActiveFilter; i++) {
            activeFilters[i].recycle();
        }

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
     * @throws IOException an underlying I/O error occurred
     */
    public void endRequest() throws IOException {

        if (swallowInput && (lastActiveFilter != -1)) {
            int extraBytes = (int) activeFilters[lastActiveFilter].end();
            pos = pos - extraBytes;
        }
    }


    /**
     * Available bytes in the buffers (note that due to encoding, this may not
     * correspond).
     */
    public int available() {
        int available = lastValid - pos;
        if ((available == 0) && (lastActiveFilter >= 0)) {
            for (int i = 0; (available == 0) && (i <= lastActiveFilter); i++) {
                available = activeFilters[i].available();
            }
        }
        if (available > 0) {
            return available;
        }

        try {
            fill(false);
            available = lastValid - pos;
        } catch (IOException ioe) {
            if (getLog().isDebugEnabled()) {
                getLog().debug(sm.getString("iib.available.readFail"), ioe);
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
     * between this and available() > 0 primarily because of having to handle
     * faking non-blocking reads with the blocking IO connector.
     */
    public boolean isFinished() {
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

    /**
     * Is standard Servlet blocking IO being used for input?
     */
    protected final boolean isBlocking() {
        return request.getReadListener() == null;
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
}

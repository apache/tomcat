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
package org.apache.coyote.http11.filters;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.coyote.http11.InputFilter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.ApplicationBufferHandler;
import org.apache.tomcat.util.res.StringManager;

/**
 * Identity input filter.
 *
 * @author Remy Maucherat
 */
public class IdentityInputFilter implements InputFilter, ApplicationBufferHandler {

    private static final StringManager sm = StringManager.getManager(IdentityInputFilter.class);


    // -------------------------------------------------------------- Constants

    protected static final String ENCODING_NAME = "identity";
    protected static final ByteChunk ENCODING = new ByteChunk();


    // ----------------------------------------------------- Static Initializer

    static {
        ENCODING.setBytes(ENCODING_NAME.getBytes(StandardCharsets.ISO_8859_1),
                0, ENCODING_NAME.length());
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * Content length.
     */
    protected long contentLength = -1;


    /**
     * Remaining bytes.
     */
    protected long remaining = 0;


    /**
     * Next buffer in the pipeline.
     */
    protected InputBuffer buffer;


    /**
     * ByteBuffer used to read leftover bytes.
     */
    protected ByteBuffer tempRead;


    private final int maxSwallowSize;


    public IdentityInputFilter(int maxSwallowSize) {
        this.maxSwallowSize = maxSwallowSize;
    }


    // ---------------------------------------------------- InputBuffer Methods

    @Override
    public int doRead(ApplicationBufferHandler handler) throws IOException {

        int result = -1;

        if (contentLength >= 0) {
            if (remaining > 0) {
                int nRead = buffer.doRead(handler);
                if (nRead > remaining) {
                    // The chunk is longer than the number of bytes remaining
                    // in the body; changing the chunk length to the number
                    // of bytes remaining
                    handler.getByteBuffer().limit(handler.getByteBuffer().position() + (int) remaining);
                    result = (int) remaining;
                } else {
                    result = nRead;
                }
                if (nRead > 0) {
                    remaining = remaining - nRead;
                }
            } else {
                // No more bytes left to be read : return -1 and clear the
                // buffer
                if (handler.getByteBuffer() != null) {
                    handler.getByteBuffer().position(0).limit(0);
                }
                result = -1;
            }
        }

        return result;

    }


    // ---------------------------------------------------- InputFilter Methods


    /**
     * Read the content length from the request.
     */
    @Override
    public void setRequest(Request request) {
        contentLength = request.getContentLengthLong();
        remaining = contentLength;
    }


    @Override
    public long end() throws IOException {

        final boolean maxSwallowSizeExceeded = (maxSwallowSize > -1 && remaining > maxSwallowSize);
        long swallowed = 0;

        // Consume extra bytes.
        while (remaining > 0) {

            int nread = buffer.doRead(this);
            tempRead = null;
            if (nread > 0 ) {
                swallowed += nread;
                remaining = remaining - nread;
                if (maxSwallowSizeExceeded && swallowed > maxSwallowSize) {
                    // Note: We do not fail early so the client has a chance to
                    // read the response before the connection is closed. See:
                    // https://httpd.apache.org/docs/2.0/misc/fin_wait_2.html#appendix
                    throw new IOException(sm.getString("inputFilter.maxSwallow"));
                }
            } else { // errors are handled higher up.
                remaining = 0;
            }
        }

        // If too many bytes were read, return the amount.
        return -remaining;

    }


    /**
     * Amount of bytes still available in a buffer.
     */
    @Override
    public int available() {
        // No data buffered here. Try the next filter in the chain.
        return buffer.available();
    }


    /**
     * Set the next buffer in the filter pipeline.
     */
    @Override
    public void setBuffer(InputBuffer buffer) {
        this.buffer = buffer;
    }


    /**
     * Make the filter ready to process the next request.
     */
    @Override
    public void recycle() {
        contentLength = -1;
        remaining = 0;
    }


    /**
     * Return the name of the associated encoding; Here, the value is
     * "identity".
     */
    @Override
    public ByteChunk getEncodingName() {
        return ENCODING;
    }


    @Override
    public boolean isFinished() {
        // Only finished if a content length is defined and there is no data
        // remaining
        return contentLength > -1 && remaining <= 0;
    }


    @Override
    public void setByteBuffer(ByteBuffer buffer) {
        tempRead = buffer;
    }


    @Override
    public ByteBuffer getByteBuffer() {
        return tempRead;
    }


    @Override
    public void expand(int size) {
        // no-op
    }
}

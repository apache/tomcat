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
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.SocketWrapperBase;

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

    public InternalAprInputBuffer(Request request, int headerBufferSize) {
        super(request, headerBufferSize);
        inputStreamInputBuffer = new SocketInputBuffer();
    }


    // ----------------------------------------------------- Instance Variables

    private SocketWrapperBase<Long> wrapper;


    // --------------------------------------------------------- Public Methods

    /**
     * Recycle the input buffer. This should be called when closing the
     * connection.
     */
    @Override
    public void recycle() {
        wrapper = null;
        super.recycle();
    }


    // ------------------------------------------------------ Protected Methods

    @Override
    protected final Log getLog() {
        return log;
    }


    @Override
    protected void init(SocketWrapperBase<Long> socketWrapper,
            AbstractEndpoint<Long> endpoint) throws IOException {

        wrapper = socketWrapper;

        int bufLength = Math.max(headerBufferSize * 2, 8192);
        if (buf == null || buf.length < bufLength) {
            buf = new byte[bufLength];
        }
    }


    @Override
    protected boolean fill(boolean block) throws IOException {

        if (parsingHeader) {
            if (lastValid > headerBufferSize) {
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
        }

        return false;
    }


    // ------------------------------------- InputStreamInputBuffer Inner Class

    /**
     * This class is an input buffer which will read its data from an input
     * stream.
     */
    protected class SocketInputBuffer implements InputBuffer {

        /**
         * Read bytes into the specified chunk.
         */
        @Override
        public int doRead(ByteChunk chunk, Request req )
            throws IOException {

            if (pos >= lastValid) {
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

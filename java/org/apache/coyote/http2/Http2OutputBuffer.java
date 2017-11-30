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
package org.apache.coyote.http2;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.coyote.Response;
import org.apache.coyote.http11.HttpOutputBuffer;
import org.apache.coyote.http11.OutputFilter;
import org.apache.coyote.http2.Stream.StreamOutputBuffer;

public class Http2OutputBuffer implements HttpOutputBuffer {

    private final Response coyoteResponse;
    private HttpOutputBuffer next;


    /**
     * Add a filter at the start of the existing processing chain. Subsequent
     * calls to the {@link HttpOutputBuffer} methods of this object will be
     * passed to the filter. If appropriate, the filter will then call the same
     * method on the next HttpOutputBuffer in the chain until the call reaches
     * the StreamOutputBuffer.
     *
     * @param filter    The filter to add to the start of the processing chain
     */
    public void addFilter(OutputFilter filter) {
        filter.setBuffer(next);
        next = filter;
    }


    public Http2OutputBuffer(Response coyoteResponse, StreamOutputBuffer streamOutputBuffer) {
        this.coyoteResponse = coyoteResponse;
        this.next = streamOutputBuffer;
    }


    @Override
    public int doWrite(ByteBuffer chunk) throws IOException {
        if (!coyoteResponse.isCommitted()) {
            coyoteResponse.sendHeaders();
        }
        return next.doWrite(chunk);
    }


    @Override
    public long getBytesWritten() {
        return next.getBytesWritten();
    }


    @Override
    public void end() throws IOException {
        next.end();
    }


    @Override
    public void flush() throws IOException {
        next.flush();
    }
}

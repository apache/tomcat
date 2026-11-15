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

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.apache.coyote.Response;
import org.apache.coyote.http11.HttpOutputBuffer;
import org.apache.coyote.http11.OutputFilter;
import org.apache.coyote.http2.Stream.StreamOutputBuffer;

/**
 * HTTP/2 output buffer that wraps a chain of {@link OutputFilter} instances and delegates to a
 * {@link StreamOutputBuffer}.
 */
public class Http2OutputBuffer implements HttpOutputBuffer {

    private final Response coyoteResponse;
    private HttpOutputBuffer next;
    // Filters that hold resources that must be released, e.g. memory that is not managed by the JVM. Guarded by this.
    private List<Closeable> closeableFilters;
    // Guarded by this
    private boolean closed;


    /**
     * Add a filter at the start of the existing processing chain. Subsequent calls to the {@link HttpOutputBuffer}
     * methods of this object will be passed to the filter. If appropriate, the filter will then call the same method on
     * the next HttpOutputBuffer in the chain until the call reaches the StreamOutputBuffer.
     *
     * @param filter The filter to add to the start of the processing chain
     */
    public void addFilter(OutputFilter filter) {
        filter.setBuffer(next);
        next = filter;
        // As for HTTP/1.1, the filter is told which response it is for once the headers have been processed
        filter.setResponse(coyoteResponse);
        if (filter instanceof Closeable) {
            // The response may be committed, which adds the filters, by a thread other than the one that closes them
            boolean closeNow;
            synchronized (this) {
                closeNow = closed;
                if (!closeNow) {
                    if (closeableFilters == null) {
                        closeableFilters = new ArrayList<>(1);
                    }
                    closeableFilters.add((Closeable) filter);
                }
            }
            if (closeNow) {
                // Closed before the filter was added so there will not be another chance
                close((Closeable) filter);
            }
        }
    }


    /**
     * Release the resources held by the filters. This is needed for a stream that is closed before the response has
     * ended, which the filters would otherwise not be told about. The filters are not recycled as they are not reused.
     * A filter that is added after this method has been called is closed when it is added.
     * <p>
     * The response may still be being written by another thread, e.g. if an asynchronous timeout has fired, so the
     * filters that are closed must allow for that.
     */
    void close() {
        List<Closeable> filters;
        synchronized (this) {
            closed = true;
            filters = closeableFilters;
            closeableFilters = null;
        }
        if (filters != null) {
            for (Closeable filter : filters) {
                close(filter);
            }
        }
    }


    private static void close(Closeable filter) {
        try {
            filter.close();
        } catch (IOException ioe) {
            // Nothing that can be done. The filters release what they can.
        }
    }


    /**
     * Creates the output buffer.
     *
     * @param coyoteResponse       The Coyote response
     * @param streamOutputBuffer   The underlying stream output buffer
     */
    public Http2OutputBuffer(Response coyoteResponse, StreamOutputBuffer streamOutputBuffer) {
        this.coyoteResponse = coyoteResponse;
        this.next = streamOutputBuffer;
    }


    @Override
    public int doWrite(ByteBuffer chunk) throws IOException {
        if (!coyoteResponse.isCommitted()) {
            coyoteResponse.commit();
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

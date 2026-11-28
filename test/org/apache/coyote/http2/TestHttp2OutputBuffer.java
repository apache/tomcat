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
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

import org.apache.coyote.Response;
import org.apache.coyote.http11.HttpOutputBuffer;
import org.apache.coyote.http11.OutputFilter;

public class TestHttp2OutputBuffer {

    private static class TesterFilter implements OutputFilter {

        int recycled;

        @Override
        public int doWrite(ByteBuffer chunk) {
            return chunk.remaining();
        }

        @Override
        public long getBytesWritten() {
            return 0;
        }

        @Override
        public void end() {
            // NO-OP
        }

        @Override
        public void flush() {
            // NO-OP
        }

        @Override
        public void setResponse(Response response) {
            // NO-OP
        }

        @Override
        public void recycle() {
            recycled++;
        }

        @Override
        public void setBuffer(HttpOutputBuffer buffer) {
            // NO-OP
        }
    }


    private static final class TesterCloseableFilter extends TesterFilter implements Closeable {

        int closed;
        IOException failure;

        @Override
        public void close() throws IOException {
            closed++;
            if (failure != null) {
                throw failure;
            }
        }
    }


    /*
     * A filter that holds resources is closed when the stream is closed, which may be before the response has ended.
     * Other filters are not touched as the response may still be being written.
     */
    @Test
    public void testCloseableFiltersAreClosedOnce() {
        Http2OutputBuffer buffer = new Http2OutputBuffer(new Response(), null);
        TesterFilter plain = new TesterFilter();
        TesterCloseableFilter first = new TesterCloseableFilter();
        TesterCloseableFilter second = new TesterCloseableFilter();
        buffer.addFilter(first);
        buffer.addFilter(plain);
        buffer.addFilter(second);

        buffer.close();
        Assert.assertEquals(1, first.closed);
        Assert.assertEquals(1, second.closed);
        Assert.assertEquals(0, plain.recycled);

        // Closing again does nothing
        buffer.close();
        Assert.assertEquals(1, first.closed);
        Assert.assertEquals(1, second.closed);
    }


    @Test
    public void testFailureToCloseAFilterDoesNotPreventTheOthersBeingClosed() {
        Http2OutputBuffer buffer = new Http2OutputBuffer(new Response(), null);
        TesterCloseableFilter first = new TesterCloseableFilter();
        first.failure = new IOException("Test failure");
        TesterCloseableFilter second = new TesterCloseableFilter();
        buffer.addFilter(first);
        buffer.addFilter(second);

        buffer.close();
        Assert.assertEquals(1, first.closed);
        Assert.assertEquals(1, second.closed);
    }


    /*
     * The response can be committed, which adds the filters, after the stream has been closed. A filter that is added
     * after the buffer is closed must not escape clean-up.
     */
    @Test
    public void testFilterAddedAfterCloseIsClosed() {
        Http2OutputBuffer buffer = new Http2OutputBuffer(new Response(), null);
        buffer.close();

        TesterCloseableFilter filter = new TesterCloseableFilter();
        buffer.addFilter(filter);
        Assert.assertEquals(1, filter.closed);

        // A filter that does not need to be closed is simply added
        TesterFilter plain = new TesterFilter();
        buffer.addFilter(plain);
        Assert.assertEquals(0, plain.recycled);

        // Closing again does not close the filter again
        buffer.close();
        Assert.assertEquals(1, filter.closed);

        // And a failure to close it does not escape
        TesterCloseableFilter failing = new TesterCloseableFilter();
        failing.failure = new IOException("Test failure");
        buffer.addFilter(failing);
        Assert.assertEquals(1, failing.closed);
    }


    /*
     * Filters are added by one thread while the buffer is closed by another. Whichever happens first, every filter
     * must be closed, and only once.
     */
    @Test
    public void testConcurrentRegistrationAndClose() throws Exception {
        int filtersPerRound = 20;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 200; round++) {
                Http2OutputBuffer buffer = new Http2OutputBuffer(new Response(), null);
                TesterCloseableFilter[] filters = new TesterCloseableFilter[filtersPerRound];
                for (int i = 0; i < filters.length; i++) {
                    filters[i] = new TesterCloseableFilter();
                }
                CyclicBarrier start = new CyclicBarrier(2);
                int closeAfter = round % filtersPerRound;
                Future<?> adding = executor.submit(() -> {
                    start.await();
                    for (TesterCloseableFilter filter : filters) {
                        buffer.addFilter(filter);
                    }
                    return null;
                });
                Future<?> closing = executor.submit(() -> {
                    start.await();
                    // Close at different points while the filters are being added
                    for (int i = 0; i < closeAfter * 50; i++) {
                        Thread.onSpinWait();
                    }
                    buffer.close();
                    return null;
                });
                adding.get(30, TimeUnit.SECONDS);
                closing.get(30, TimeUnit.SECONDS);
                for (int i = 0; i < filters.length; i++) {
                    Assert.assertEquals("Filter " + i + " of round " + round, 1, filters[i].closed);
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }


    @Test
    public void testCloseWithoutFilters() {
        new Http2OutputBuffer(new Response(), null).close();
    }
}

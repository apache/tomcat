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
package org.apache.coyote.http11.filters.brotli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import org.apache.coyote.CompressionConfig;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.coyote.http11.HttpOutputBuffer;
import org.apache.coyote.http11.filters.BrotliEncoder;
import org.apache.coyote.http11.filters.BrotliOutputFilter;

public class TestBrotliOutputFilter {

    private static final int QUALITY = 4;


    @Before
    public void checkBrotliIsAvailable() {
        Assume.assumeTrue(BrotliEncoder.isAvailable());
    }


    private static class CollectingBuffer implements HttpOutputBuffer {

        private final ByteArrayOutputStream collected = new ByteArrayOutputStream();
        private boolean ended;
        private boolean fail;

        @Override
        public int doWrite(ByteBuffer chunk) throws IOException {
            if (fail) {
                throw new IOException("Test failure");
            }
            int len = chunk.remaining();
            byte[] bytes = new byte[len];
            chunk.get(bytes);
            collected.write(bytes);
            return len;
        }

        @Override
        public long getBytesWritten() {
            return collected.size();
        }

        @Override
        public void end() {
            ended = true;
        }

        @Override
        public void flush() {
            // NO-OP
        }
    }


    private static BrotliOutputFilter newFilter(CollectingBuffer buffer, int quality) {
        BrotliOutputFilter filter = new BrotliOutputFilter();
        filter.setResponse(brotliResponse(quality));
        filter.setBuffer(buffer);
        return filter;
    }


    /*
     * A response for which Brotli has been chosen, with the quality that is configured, as it is for a request.
     */
    private static Response brotliResponse(CompressionConfig compressionConfig) {
        Request request = new Request();
        request.getMimeHeaders().addValue("accept-encoding").setString("br");
        Response response = new Response();
        Assert.assertTrue(compressionConfig.useCompression(request, response));
        Assert.assertTrue(CompressionConfig.isBrotli(response));
        return response;
    }


    private static Response brotliResponse(int quality) {
        CompressionConfig compressionConfig = new CompressionConfig();
        compressionConfig.setCompression("force");
        compressionConfig.setCompressionBrotliQuality(quality);
        return brotliResponse(compressionConfig);
    }


    private static byte[] compress(byte[] data, int quality, int chunkSize) throws IOException {
        CollectingBuffer buffer = new CollectingBuffer();
        BrotliOutputFilter filter = newFilter(buffer, quality);
        for (int offset = 0; offset < data.length; offset += chunkSize) {
            int len = Math.min(chunkSize, data.length - offset);
            filter.doWrite(ByteBuffer.wrap(data, offset, len));
        }
        filter.end();
        Assert.assertTrue(buffer.ended);
        filter.recycle();
        return buffer.collected.toByteArray();
    }


    private static byte[] text(int size) {
        byte[] result = new byte[size];
        byte[] unit = "<p>Tomcat é€😀 compresses this response</p>\n".getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < size; i++) {
            result[i] = unit[i % unit.length];
        }
        return result;
    }


    private static byte[] random(int size, long seed) {
        byte[] result = new byte[size];
        new Random(seed).nextBytes(result);
        return result;
    }


    @Test
    public void testRoundTripReproducesTheOriginalBytes() throws IOException {
        for (int quality : new int[] { 0, QUALITY, 9 }) {
            for (int size : new int[] { 0, 1, 2, 100, 16 * 1024 - 1, 16 * 1024, 16 * 1024 + 1, 70_001, 1_000_000 }) {
                byte[] text = text(size);
                Assert.assertArrayEquals("text, size " + size + ", quality " + quality, text,
                        BrotliDecoder.decode(compress(text, quality, 8 * 1024)));
                byte[] random = random(size, size);
                Assert.assertArrayEquals("random, size " + size + ", quality " + quality, random,
                        BrotliDecoder.decode(compress(random, quality, 8 * 1024)));
            }
        }
    }


    @Test
    public void testLargeResponse() throws IOException {
        byte[] data = random(6_000_000, 42);
        System.arraycopy(text(2_000_000), 0, data, 1_000_000, 2_000_000);
        Assert.assertArrayEquals(data, BrotliDecoder.decode(compress(data, 1, 100_000)));
    }


    @Test
    public void testCompressesAndEmptyResponseIsValid() throws IOException {
        byte[] compressed = compress(text(100_000), QUALITY, 4096);
        Assert.assertTrue(compressed.length > 0 && compressed.length < 5_000);

        byte[] empty = compress(new byte[0], QUALITY, 1);
        Assert.assertTrue(empty.length > 0);
        Assert.assertEquals(0, BrotliDecoder.decode(empty).length);
    }


    @Test
    public void testWritesOfAnySizeAndBufferType() throws IOException {
        byte[] data = text(300_000);
        Random random = new Random(7);
        CollectingBuffer buffer = new CollectingBuffer();
        BrotliOutputFilter filter = newFilter(buffer, QUALITY);
        int offset = 0;
        while (offset < data.length) {
            int len = Math.min(1 + random.nextInt(50_000), data.length - offset);
            ByteBuffer chunk;
            if (random.nextBoolean()) {
                chunk = ByteBuffer.allocateDirect(len);
                chunk.put(data, offset, len).flip();
            } else {
                chunk = ByteBuffer.wrap(data, offset, len);
            }
            Assert.assertEquals(len, filter.doWrite(chunk));
            Assert.assertFalse(chunk.hasRemaining());
            offset += len;
        }
        filter.end();
        Assert.assertArrayEquals(data, BrotliDecoder.decode(buffer.collected.toByteArray()));
    }


    @Test
    public void testFlushMakesTheDataAvailable() throws IOException {
        CollectingBuffer buffer = new CollectingBuffer();
        BrotliOutputFilter filter = newFilter(buffer, QUALITY);
        byte[] first = "first part of the response".getBytes(StandardCharsets.UTF_8);
        byte[] second = " and the second part".getBytes(StandardCharsets.UTF_8);

        filter.doWrite(ByteBuffer.wrap(first));
        filter.flush();
        // What has been written so far can be decoded without waiting for the end of the response
        Assert.assertArrayEquals(first, BrotliDecoder.decodePartial(buffer.collected.toByteArray()));

        filter.doWrite(ByteBuffer.wrap(second));
        filter.flush();
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        expected.write(first);
        expected.write(second);
        Assert.assertArrayEquals(expected.toByteArray(), BrotliDecoder.decodePartial(buffer.collected.toByteArray()));

        filter.end();
        Assert.assertArrayEquals(expected.toByteArray(), BrotliDecoder.decode(buffer.collected.toByteArray()));
    }


    @Test
    public void testFlushBeforeAnyDataAndRepeatedFlush() throws IOException {
        CollectingBuffer buffer = new CollectingBuffer();
        BrotliOutputFilter filter = newFilter(buffer, QUALITY);
        filter.flush();
        filter.doWrite(ByteBuffer.wrap("x".getBytes(StandardCharsets.UTF_8)));
        filter.flush();
        filter.flush();
        filter.end();
        Assert.assertEquals("x", new String(BrotliDecoder.decode(buffer.collected.toByteArray()),
                StandardCharsets.UTF_8));
    }


    /*
     * HTTP/2 can signal the end of the response more than once. Completing the compressed stream a second time would
     * write a second stream, and fail if the output has already been closed.
     */
    @Test
    public void testEndMoreThanOnce() throws IOException {
        CollectingBuffer buffer = new CollectingBuffer();
        BrotliOutputFilter filter = newFilter(buffer, QUALITY);
        byte[] data = text(50_000);
        filter.doWrite(ByteBuffer.wrap(data));
        filter.end();
        byte[] first = buffer.collected.toByteArray();
        filter.end();
        filter.end();
        Assert.assertArrayEquals(first, buffer.collected.toByteArray());
        Assert.assertArrayEquals(data, BrotliDecoder.decode(first));

        // An empty response
        buffer = new CollectingBuffer();
        filter = newFilter(buffer, QUALITY);
        filter.end();
        byte[] empty = buffer.collected.toByteArray();
        filter.end();
        Assert.assertArrayEquals(empty, buffer.collected.toByteArray());
        Assert.assertEquals(0, BrotliDecoder.decode(empty).length);
    }


    @Test
    public void testFilterCanBeReusedAfterRecycle() throws IOException {
        CollectingBuffer buffer = new CollectingBuffer();
        BrotliOutputFilter filter = newFilter(buffer, QUALITY);
        for (int i = 1; i <= 3; i++) {
            buffer.collected.reset();
            byte[] data = text(10_000 * i);
            filter.doWrite(ByteBuffer.wrap(data));
            filter.end();
            filter.recycle();
            Assert.assertArrayEquals(data, BrotliDecoder.decode(buffer.collected.toByteArray()));
        }
        // A response that is abandoned
        filter.doWrite(ByteBuffer.wrap(text(1000)));
        filter.recycle();
        // Recycling more than once is safe
        filter.recycle();
        buffer.collected.reset();
        filter.doWrite(ByteBuffer.wrap(text(2000)));
        filter.end();
        Assert.assertArrayEquals(text(2000), BrotliDecoder.decode(buffer.collected.toByteArray()));
    }


    @Test
    public void testFailureOfTheNextBufferIsReportedAndTheResponseIsNotContinued() throws IOException {
        CollectingBuffer buffer = new CollectingBuffer();
        BrotliOutputFilter filter = newFilter(buffer, QUALITY);
        buffer.fail = true;
        // Enough data that compressed data is produced, and so written, before the end of the response
        byte[] data = random(2_000_000, 3);
        try {
            filter.doWrite(ByteBuffer.wrap(data));
            Assert.fail("Expected IOException");
        } catch (IOException expected) {
            // Expected
        }
        // The Brotli stream of the response is not complete. Another stream must not be started in the same response.
        buffer.fail = false;
        int written = buffer.collected.size();
        try {
            filter.doWrite(ByteBuffer.wrap(text(5000)));
            Assert.fail("Expected IOException");
        } catch (IOException expected) {
            // Expected
        }
        assertEndFails(filter, buffer);
        Assert.assertEquals(written, buffer.collected.size());

        // The filter is used for the next response once it has been recycled
        filter.recycle();
        buffer.collected.reset();
        filter.doWrite(ByteBuffer.wrap(text(5000)));
        filter.end();
        Assert.assertArrayEquals(text(5000), BrotliDecoder.decode(buffer.collected.toByteArray()));
    }


    /*
     * As for gzip, a failure of flush() is not reported by flush(). It must not allow a second Brotli stream to be
     * started in the response, which would be sent in the same response after an incomplete first stream.
     */
    @Test
    public void testFlushFailureDoesNotStartANewStream() throws IOException {
        CollectingBuffer buffer = new CollectingBuffer();
        BrotliOutputFilter filter = newFilter(buffer, QUALITY);
        filter.doWrite(ByteBuffer.wrap(text(50_000)));

        buffer.fail = true;
        // Not reported
        filter.flush();
        buffer.fail = false;
        int written = buffer.collected.size();

        try {
            filter.doWrite(ByteBuffer.wrap(text(50_000)));
            Assert.fail("Expected IOException");
        } catch (IOException expected) {
            // Expected
        }
        // flush() has nothing to do and the end of the response writes nothing more
        filter.flush();
        assertEndFails(filter, buffer);
        Assert.assertEquals(written, buffer.collected.size());
    }


    /*
     * The Brotli stream can not be completed after a failure. The response must not be ended as if it were complete,
     * as the client would be given an incomplete Brotli stream as if it were the whole response. The failure is not
     * reported by flush() so, if the application does not write again, end() is the only place it can be reported.
     */
    @Test
    public void testEndAfterAFlushFailureDoesNotCompleteTheResponse() throws IOException {
        CollectingBuffer buffer = new CollectingBuffer();
        BrotliOutputFilter filter = newFilter(buffer, QUALITY);
        filter.doWrite(ByteBuffer.wrap(text(50_000)));

        buffer.fail = true;
        // Not reported
        filter.flush();
        buffer.fail = false;

        assertEndFails(filter, buffer);
        // Reported each time
        assertEndFails(filter, buffer);
    }


    /*
     * As gzip does, a failure to complete the stream is reported and the response is not ended.
     */
    @Test
    public void testFailureToCompleteTheStreamIsReported() throws IOException {
        CollectingBuffer buffer = new CollectingBuffer();
        BrotliOutputFilter filter = newFilter(buffer, QUALITY);
        filter.doWrite(ByteBuffer.wrap(text(10_000)));

        buffer.fail = true;
        assertEndFails(filter, buffer);
        assertEndFails(filter, buffer);

        // Recycling makes the filter usable for the next response
        filter.recycle();
        buffer.fail = false;
        buffer.collected.reset();
        filter.doWrite(ByteBuffer.wrap(text(3000)));
        filter.end();
        Assert.assertTrue(buffer.ended);
        Assert.assertArrayEquals(text(3000), BrotliDecoder.decode(buffer.collected.toByteArray()));
    }


    private static void assertEndFails(BrotliOutputFilter filter, CollectingBuffer buffer) {
        try {
            filter.end();
            Assert.fail("Expected IOException");
        } catch (IOException expected) {
            // Expected
        }
        Assert.assertFalse("The response was ended", buffer.ended);
    }


    /*
     * HTTP/2 closes the filter if the stream is closed before the response has ended.
     */
    @Test
    public void testCloseReleasesTheEncoderAndEndsTheUseOfTheFilter() throws IOException {
        CollectingBuffer buffer = new CollectingBuffer();
        BrotliOutputFilter filter = newFilter(buffer, QUALITY);
        filter.doWrite(ByteBuffer.wrap(text(100_000)));
        filter.close();
        // More than once is safe
        filter.close();
        int written = buffer.collected.size();

        try {
            filter.doWrite(ByteBuffer.wrap(text(1000)));
            Assert.fail("Expected IOException");
        } catch (IOException expected) {
            // Expected
        }
        assertEndFails(filter, buffer);
        Assert.assertEquals(written, buffer.collected.size());

        // Closing a filter that has not been used, and one that has ended, is safe
        newFilter(new CollectingBuffer(), QUALITY).close();
        buffer = new CollectingBuffer();
        filter = newFilter(buffer, QUALITY);
        filter.doWrite(ByteBuffer.wrap(text(1000)));
        filter.end();
        filter.close();
        // The response was complete so ending it again, as can happen with HTTP/2, is not a failure
        filter.end();
        Assert.assertArrayEquals(text(1000), BrotliDecoder.decode(buffer.collected.toByteArray()));
    }


    /*
     * The filter can be closed by a thread other than the one that is writing, and so using the encoder. The write
     * must either complete or fail. It must never use the encoder after it has been released.
     */
    @Test
    public void testCloseWhileWriting() throws Exception {
        byte[] data = random(1_000_000, 5);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 100; i++) {
                int delay = i % 4;
                BrotliOutputFilter filter = newFilter(new CollectingBuffer(), 1);
                Future<?> writing = executor.submit(() -> {
                    try {
                        for (int offset = 0; offset < data.length; offset += 8192) {
                            filter.doWrite(ByteBuffer.wrap(data, offset, Math.min(8192, data.length - offset)));
                        }
                        filter.end();
                    } catch (IOException ioe) {
                        // Closed while writing
                    }
                    return null;
                });
                Future<?> closing = executor.submit(() -> {
                    Thread.sleep(delay);
                    filter.close();
                    return null;
                });
                closing.get(30, TimeUnit.SECONDS);
                writing.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }


    @Test
    public void testEncoderFailureAndRelease() throws IOException {
        BrotliEncoder encoder = BrotliEncoder.create(QUALITY);
        ByteBuffer output = ByteBuffer.allocate(1024);
        encoder.compress(ByteBuffer.wrap(text(100)), output, BrotliEncoder.PROCESS);
        do {
            encoder.compress(ByteBuffer.allocate(0), output, BrotliEncoder.FINISH);
        } while (!encoder.isFinished());
        Assert.assertTrue(encoder.isFinished());
        // No more input is accepted once the stream is complete
        try {
            encoder.compress(ByteBuffer.wrap(text(100)), output, BrotliEncoder.PROCESS);
            Assert.fail("Expected IOException");
        } catch (IOException expected) {
            // Expected
        }
        encoder.close();
        // Closing more than once is safe
        encoder.close();
    }


    /*
     * The native memory is released by close(), so the encoder must not be used afterwards.
     */
    @Test
    public void testEncoderIsNotUsableAfterClose() throws IOException {
        BrotliEncoder encoder = BrotliEncoder.create(QUALITY);
        ByteBuffer output = ByteBuffer.allocate(1024);
        encoder.compress(ByteBuffer.wrap(text(100)), output, BrotliEncoder.PROCESS);
        encoder.close();
        try {
            encoder.compress(ByteBuffer.wrap(text(100)), output, BrotliEncoder.PROCESS);
            Assert.fail("Expected IOException");
        } catch (IOException expected) {
            // Expected
        }
        try {
            encoder.hasMoreOutput();
            Assert.fail("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // Expected
        }
        try {
            encoder.isFinished();
            Assert.fail("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // Expected
        }
        // Closing again is safe
        encoder.close();
    }


    /*
     * An encoder that is closed by another thread, as can happen if a response is abandoned, must either complete the
     * call or fail. It must never use memory that has been released.
     */
    @Test
    public void testCloseWhileCompressing() throws Exception {
        byte[] data = random(2_000_000, 11);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 100; i++) {
                int delay = i % 5;
                BrotliEncoder encoder = BrotliEncoder.create(1);
                Future<Boolean> compressing = executor.submit(() -> {
                    ByteBuffer input = ByteBuffer.wrap(data);
                    ByteBuffer output = ByteBuffer.allocate(8192);
                    try {
                        while (input.hasRemaining()) {
                            encoder.compress(input, output, BrotliEncoder.PROCESS);
                            output.clear();
                        }
                        return Boolean.TRUE;
                    } catch (IOException ioe) {
                        // Closed while compressing
                        return Boolean.FALSE;
                    }
                });
                Future<?> closing = executor.submit(() -> {
                    Thread.sleep(delay);
                    encoder.close();
                    return null;
                });
                closing.get(30, TimeUnit.SECONDS);
                compressing.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }


    /*
     * The quality is the one that was used to choose Brotli for the response, not the one that is configured when the
     * first data is written, or when the filter is told which response it is for. The configuration can be changed at
     * any time, e.g. with JMX. The output is the same for the same data and quality, so it shows which was used. See
     * also TestBrotliCompression.testQualityIsFixedWhenTheResponseIsCommitted().
     */
    @Test
    public void testQualityIsTheOneThatBrotliWasChosenWith() throws IOException {
        byte[] data = text(300_000);
        byte[] fastest = compress(data, 0, 8192);
        byte[] smallest = compress(data, 9, 8192);
        Assert.assertNotEquals(fastest.length, smallest.length);

        CompressionConfig compressionConfig = new CompressionConfig();
        compressionConfig.setCompression("force");
        compressionConfig.setCompressionBrotliQuality(0);
        Response response = brotliResponse(compressionConfig);
        Assert.assertEquals(0, CompressionConfig.getBrotliQuality(response));

        // The configuration changes, e.g. with JMX, after Brotli was chosen. Including to disable Brotli, which is not
        // a quality that can be used.
        compressionConfig.setCompressionBrotliQuality(-1);
        CollectingBuffer buffer = new CollectingBuffer();
        BrotliOutputFilter filter = new BrotliOutputFilter();
        filter.setBuffer(buffer);
        filter.setResponse(response);
        write(filter, data);
        Assert.assertArrayEquals(fastest, buffer.collected.toByteArray());

        // The same filter is used for the next response, as for HTTP/1.1
        compressionConfig.setCompressionBrotliQuality(9);
        response = brotliResponse(compressionConfig);
        compressionConfig.setCompressionBrotliQuality(0);
        filter.recycle();
        buffer.collected.reset();
        filter.setResponse(response);
        write(filter, data);
        Assert.assertArrayEquals(smallest, buffer.collected.toByteArray());
    }


    // Written as compress() writes, as the output depends on how the data is written
    private static void write(BrotliOutputFilter filter, byte[] data) throws IOException {
        for (int offset = 0; offset < data.length; offset += 8192) {
            filter.doWrite(ByteBuffer.wrap(data, offset, Math.min(8192, data.length - offset)));
        }
        filter.end();
    }


    @Test
    public void testConcurrentResponses() throws Exception {
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int seed = t;
                results.add(executor.submit(() -> {
                    for (int i = 0; i < 25; i++) {
                        byte[] data = (i % 2 == 0) ? text(20_000 + seed * 1000 + i) : random(30_000 + i, seed * 100 + i);
                        if (!java.util.Arrays.equals(data, BrotliDecoder.decode(compress(data, QUALITY, 7_000)))) {
                            return Boolean.FALSE;
                        }
                    }
                    return Boolean.TRUE;
                }));
            }
            for (Future<Boolean> result : results) {
                Assert.assertTrue(result.get(60, TimeUnit.SECONDS).booleanValue());
            }
        } finally {
            executor.shutdownNow();
        }
    }
}

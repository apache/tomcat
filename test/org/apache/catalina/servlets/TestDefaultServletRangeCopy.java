/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.servlets;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;

import org.junit.Assert;
import org.junit.Test;

public class TestDefaultServletRangeCopy {

    @Test
    public void testCopyRangeContinuesAfterShortRead() throws IOException {
        byte[] source = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7 };
        TesterServletOutputStream output = new TesterServletOutputStream();

        IOException exception = copy(new ShortReadingInputStream(source), output, 0, 7);

        Assert.assertNull(exception);
        Assert.assertArrayEquals(source, output.toByteArray());
    }


    @Test
    public void testCopyRangeReturnsEofExceptionAfterShortRead() throws IOException {
        byte[] source = new byte[] { 0, 1, 2 };
        TesterServletOutputStream output = new TesterServletOutputStream();

        IOException exception = copy(new ShortReadingInputStream(source), output, 0, 7);

        Assert.assertTrue(exception instanceof EOFException);
        Assert.assertArrayEquals(source, output.toByteArray());
    }


    @Test
    public void testCopyRangeReturnsEofExceptionForEmptyStream() throws IOException {
        TesterServletOutputStream output = new TesterServletOutputStream();

        IOException exception = copy(new ByteArrayInputStream(new byte[0]), output, 0, 7);

        Assert.assertTrue(exception instanceof EOFException);
        Assert.assertEquals(0, output.size());
    }


    @Test
    public void testCopyRangeAtBufferBoundaries() throws IOException {
        int bufferSize = new DefaultServlet().input;
        int[] sourceLengths = new int[] { bufferSize - 1, bufferSize, bufferSize + 1 };

        for (int sourceLength : sourceLengths) {
            byte[] source = new byte[sourceLength];
            for (int i = 0; i < source.length; i++) {
                source[i] = (byte) i;
            }
            TesterServletOutputStream output = new TesterServletOutputStream();

            IOException exception = copy(new ByteArrayInputStream(source), output, 0, sourceLength - 1);

            Assert.assertNull(exception);
            Assert.assertArrayEquals(source, output.toByteArray());
        }
    }


    @Test
    public void testCopyRangeWithNonZeroStart() throws IOException {
        byte[] source = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 };
        TesterServletOutputStream output = new TesterServletOutputStream();

        IOException exception = copy(new ShortReadingInputStream(source), output, 2, 9);

        Assert.assertNull(exception);
        Assert.assertArrayEquals(Arrays.copyOfRange(source, 2, 10), output.toByteArray());
    }


    @Test
    public void testCopyRangeReturnsInputIOException() throws IOException {
        IOException expected = new IOException();
        InputStream input = new InputStream() {

            @Override
            public int read() throws IOException {
                throw expected;
            }

            @Override
            public int read(byte[] target, int offset, int length) throws IOException {
                throw expected;
            }
        };

        IOException actual = copy(input, new TesterServletOutputStream(), 0, 7);

        Assert.assertSame(expected, actual);
    }


    private static IOException copy(InputStream input, ServletOutputStream output, long start, long end)
            throws IOException {
        DefaultServlet servlet = new DefaultServlet();
        try (InputStream bufferedInput = new BufferedInputStream(input, servlet.input)) {
            return servlet.copyNoThrow(bufferedInput, output, start, end);
        }
    }


    private static class ShortReadingInputStream extends InputStream {

        private static final int MAX_READ_SIZE = 3;

        private final byte[] bytes;
        private int position;

        private ShortReadingInputStream(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public int read() {
            return position < bytes.length ? bytes[position++] & 0xFF : -1;
        }

        @Override
        public int read(byte[] target, int offset, int length) {
            if (position == bytes.length) {
                return -1;
            }
            int count = Math.min(Math.min(length, MAX_READ_SIZE), bytes.length - position);
            System.arraycopy(bytes, position, target, offset, count);
            position += count;
            return count;
        }
    }


    private static class TesterServletOutputStream extends ServletOutputStream {

        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        @Override
        public void write(int value) {
            output.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            output.write(bytes, offset, length);
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            // NO-OP
        }

        private int size() {
            return output.size();
        }

        private byte[] toByteArray() {
            return output.toByteArray();
        }
    }
}

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

import org.apache.tomcat.util.res.StringManager;

/**
 * A streaming Brotli encoder, as defined by <a href="https://www.rfc-editor.org/rfc/rfc7932">RFC 7932</a>.
 * <p>
 * The implementation uses the Brotli library of the operating system through the Foreign Function and Memory API so it
 * is only available if that library can be found and the FFM implementation has been built and is supported by the
 * JRE. Use {@link #isAvailable()} to check.
 */
public interface BrotliEncoder extends AutoCloseable {

    /**
     * Process the input. The encoder may buffer it.
     */
    int PROCESS = 0;

    /**
     * Process the input and make all of it available as output so that it can be decoded.
     */
    int FLUSH = 1;

    /**
     * Process the input and complete the stream.
     */
    int FINISH = 2;


    /**
     * Consume as much of the input as possible and produce as much of the output as will fit.
     *
     * @param input     The data to compress. The position is advanced by the amount of data consumed.
     * @param output    The buffer to write the compressed data to. The position is advanced by the amount of data
     *                      written.
     * @param operation One of {@link #PROCESS}, {@link #FLUSH} or {@link #FINISH}
     *
     * @throws IOException If the encoder fails
     */
    void compress(ByteBuffer input, ByteBuffer output, int operation) throws IOException;


    /**
     * Is there output that did not fit in the output buffer of the previous call to
     * {@link #compress(ByteBuffer, ByteBuffer, int)}?
     *
     * @return {@code true} if there is more output
     */
    boolean hasMoreOutput();


    /**
     * Has the stream been completed and all the output been produced?
     *
     * @return {@code true} if the stream has finished
     */
    boolean isFinished();


    /**
     * Release the resources used by the encoder. It is safe to call this method more than once.
     */
    @Override
    void close();


    /**
     * Is the Brotli encoder available?
     *
     * @return {@code true} if encoders can be created
     */
    static boolean isAvailable() {
        return Holder.AVAILABLE;
    }


    /**
     * Create an encoder.
     *
     * @param quality The compression quality, from 0 (fastest) to 11 (smallest)
     *
     * @return A new encoder
     *
     * @throws IOException If the encoder is not available or could not be created
     */
    static BrotliEncoder create(int quality) throws IOException {
        try {
            return (BrotliEncoder) Class.forName(Holder.IMPLEMENTATION).getConstructor(int.class).newInstance(
                    Integer.valueOf(quality));
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IOException(Holder.sm.getString("brotliEncoder.unavailable"), e);
        }
    }


    /**
     * The implementation is loaded when it is first needed as it is only present if it has been built and it can only
     * be used with a JRE that supports it.
     */
    final class Holder {

        private static final StringManager sm = StringManager.getManager(Holder.class);

        private static final String IMPLEMENTATION = "org.apache.coyote.http11.filters.brotli.FfmBrotliEncoder";

        private static final boolean AVAILABLE = isImplementationAvailable();

        private static boolean isImplementationAvailable() {
            try {
                return ((Boolean) Class.forName(IMPLEMENTATION).getMethod("isAvailable").invoke(null)).booleanValue();
            } catch (ReflectiveOperationException | LinkageError e) {
                return false;
            }
        }

        private Holder() {
        }
    }
}

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

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import java.util.Optional;

import org.apache.coyote.http11.filters.BrotliEncoder;
import org.apache.tomcat.util.res.StringManager;

/**
 * A {@link BrotliEncoder} that uses the Brotli encoder library, <code>libbrotlienc</code>, of the operating system.
 * <p>
 * The native memory used by this class is bounded. The input and output buffers are each {@value #BUFFER_SIZE} bytes
 * and the memory used by the library for the state of the encoder depends on the quality and on the window size
 * ({@value #WINDOW_BITS} bits). All of it is released by {@link #close()}. If an encoder is abandoned without being
 * closed, it is released when the encoder is garbage collected. The memory is allocated and freed directly, rather
 * than with an {@link Arena}, because the memory of an automatic arena is not released until the garbage collector runs,
 * which may not be for some time as it does not see native memory, and closing a shared arena is expensive for every
 * thread of the JVM.
 * <p>
 * An encoder is not designed to be used by more than one thread at the same time but it can be used by different
 * threads one after the other. The methods are synchronized so that the native memory can not be used after it has
 * been released.
 */
public final class FfmBrotliEncoder implements BrotliEncoder {

    private static final StringManager sm = StringManager.getManager(FfmBrotliEncoder.class);

    // Parameter and operation values from encode.h
    private static final int PARAMETER_QUALITY = 1;
    private static final int PARAMETER_WINDOW_BITS = 2;

    // The size of the window, as a power of two. Responses are usually small so the default of 22 is not needed.
    private static final int WINDOW_BITS = 18;

    private static final int BUFFER_SIZE = 16 * 1024;

    private static final Cleaner CLEANER = Cleaner.create();

    private static final MethodHandle CREATE_INSTANCE;
    private static final MethodHandle SET_PARAMETER;
    private static final MethodHandle COMPRESS_STREAM;
    private static final MethodHandle HAS_MORE_OUTPUT;
    private static final MethodHandle IS_FINISHED;
    private static final MethodHandle DESTROY_INSTANCE;
    private static final MethodHandle MALLOC;
    private static final MethodHandle FREE;

    static {
        MethodHandle createInstance = null;
        MethodHandle setParameter = null;
        MethodHandle compressStream = null;
        MethodHandle hasMoreOutput = null;
        MethodHandle isFinished = null;
        MethodHandle destroyInstance = null;
        MethodHandle malloc = null;
        MethodHandle free = null;
        // size_t is handled as a long
        if (ValueLayout.ADDRESS.byteSize() == Long.BYTES) {
            SymbolLookup lookup = findLibrary();
            if (lookup != null) {
                Linker linker = Linker.nativeLinker();
                ValueLayout.OfInt i = ValueLayout.JAVA_INT;
                ValueLayout a = ValueLayout.ADDRESS;
                createInstance = downcall(linker, lookup, "BrotliEncoderCreateInstance", FunctionDescriptor.of(a, a, a, a));
                setParameter = downcall(linker, lookup, "BrotliEncoderSetParameter", FunctionDescriptor.of(i, a, i, i));
                compressStream = downcall(linker, lookup, "BrotliEncoderCompressStream",
                        FunctionDescriptor.of(i, a, i, a, a, a, a, a));
                hasMoreOutput = downcall(linker, lookup, "BrotliEncoderHasMoreOutput", FunctionDescriptor.of(i, a));
                isFinished = downcall(linker, lookup, "BrotliEncoderIsFinished", FunctionDescriptor.of(i, a));
                destroyInstance = downcall(linker, lookup, "BrotliEncoderDestroyInstance", FunctionDescriptor.ofVoid(a));
                SymbolLookup libc = linker.defaultLookup();
                malloc = downcall(linker, libc, "malloc", FunctionDescriptor.of(a, ValueLayout.JAVA_LONG));
                free = downcall(linker, libc, "free", FunctionDescriptor.ofVoid(a));
            }
        }
        CREATE_INSTANCE = createInstance;
        SET_PARAMETER = setParameter;
        COMPRESS_STREAM = compressStream;
        HAS_MORE_OUTPUT = hasMoreOutput;
        IS_FINISHED = isFinished;
        DESTROY_INSTANCE = destroyInstance;
        MALLOC = malloc;
        FREE = free;
    }


    private static SymbolLookup findLibrary() {
        for (String name : new String[] { System.mapLibraryName("brotlienc"), "libbrotlienc.so.1",
                "libbrotlienc.1.dylib" }) {
            try {
                return SymbolLookup.libraryLookup(name, Arena.global());
            } catch (IllegalArgumentException | UnsatisfiedLinkError e) {
                // Try the next name
            }
        }
        return null;
    }


    private static MethodHandle downcall(Linker linker, SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
        Optional<MemorySegment> symbol = lookup.find(name);
        return symbol.map(s -> linker.downcallHandle(s, descriptor)).orElse(null);
    }


    /**
     * Is <code>libbrotlienc</code> available?
     *
     * @return {@code true} if encoders can be created
     */
    public static boolean isAvailable() {
        return DESTROY_INSTANCE != null && CREATE_INSTANCE != null && SET_PARAMETER != null &&
                COMPRESS_STREAM != null && HAS_MORE_OUTPUT != null && IS_FINISHED != null && MALLOC != null &&
                FREE != null;
    }


    // The native memory is released by close() or, if that is not called, by the Cleaner. Neither the Cleaner nor the
    // garbage collector are relied on for normal use as the amount of native memory that is held is not seen by the
    // garbage collector, which may therefore not run for some time.
    private final MemorySegment state;
    private final Cleaner.Cleanable cleanable;
    private final MemorySegment input;
    private final MemorySegment output;
    // The pointers that are passed to the library. The sizes are size_t values.
    private final MemorySegment availableIn;
    private final MemorySegment nextIn;
    private final MemorySegment availableOut;
    private final MemorySegment nextOut;
    private boolean closed;


    /**
     * Create an encoder.
     *
     * @param quality The compression quality, from 0 (fastest) to 11 (smallest)
     *
     * @throws IOException If the encoder could not be created
     */
    public FfmBrotliEncoder(int quality) throws IOException {
        if (!isAvailable()) {
            throw new IOException(sm.getString("ffmBrotliEncoder.unavailable"));
        }
        // What is allocated is freed using the address that malloc returned
        MemorySegment newBuffers = MemorySegment.NULL;
        MemorySegment newState = MemorySegment.NULL;
        try {
            // One block for the buffers and the pointers
            long size = 2L * BUFFER_SIZE + 4L * Long.BYTES;
            newBuffers = (MemorySegment) MALLOC.invoke(size);
            if (newBuffers.address() == 0) {
                throw new IOException(sm.getString("ffmBrotliEncoder.createFail"));
            }
            MemorySegment buffers = newBuffers.reinterpret(size);
            input = buffers.asSlice(0, BUFFER_SIZE);
            output = buffers.asSlice(BUFFER_SIZE, BUFFER_SIZE);
            availableIn = buffers.asSlice(2L * BUFFER_SIZE, Long.BYTES);
            nextIn = buffers.asSlice(2L * BUFFER_SIZE + Long.BYTES, Long.BYTES);
            availableOut = buffers.asSlice(2L * BUFFER_SIZE + 2L * Long.BYTES, Long.BYTES);
            nextOut = buffers.asSlice(2L * BUFFER_SIZE + 3L * Long.BYTES, Long.BYTES);
            newState = (MemorySegment) CREATE_INSTANCE.invoke(MemorySegment.NULL, MemorySegment.NULL,
                    MemorySegment.NULL);
            if (MemorySegment.NULL.equals(newState)) {
                throw new IOException(sm.getString("ffmBrotliEncoder.createFail"));
            }
            if (((int) SET_PARAMETER.invoke(newState, PARAMETER_QUALITY, quality)) == 0 ||
                    ((int) SET_PARAMETER.invoke(newState, PARAMETER_WINDOW_BITS, WINDOW_BITS)) == 0) {
                throw new IOException(sm.getString("ffmBrotliEncoder.parameterFail", Integer.valueOf(quality)));
            }
        } catch (Throwable t) {
            // Release whatever has been allocated. The Cleaner is not registered yet.
            new Destroyer(newState, newBuffers).run();
            if (t instanceof IOException) {
                throw (IOException) t;
            }
            throw new IOException(sm.getString("ffmBrotliEncoder.createFail"), t);
        }
        Cleaner.Cleanable newCleanable;
        try {
            newCleanable = CLEANER.register(this, new Destroyer(newState, newBuffers));
        } catch (Throwable t) {
            new Destroyer(newState, newBuffers).run();
            throw new IOException(sm.getString("ffmBrotliEncoder.createFail"), t);
        }
        state = newState;
        cleanable = newCleanable;
    }


    @Override
    public synchronized void compress(ByteBuffer inputBuffer, ByteBuffer outputBuffer, int operation)
            throws IOException {
        if (closed) {
            throw new IOException(sm.getString("ffmBrotliEncoder.closed"));
        }
        int inputSize = Math.min(inputBuffer.remaining(), BUFFER_SIZE);
        int outputSize = Math.min(outputBuffer.remaining(), BUFFER_SIZE);
        MemorySegment.copy(MemorySegment.ofBuffer(inputBuffer), 0, input, 0, inputSize);
        availableIn.set(ValueLayout.JAVA_LONG, 0, inputSize);
        nextIn.set(ValueLayout.ADDRESS, 0, input);
        availableOut.set(ValueLayout.JAVA_LONG, 0, outputSize);
        nextOut.set(ValueLayout.ADDRESS, 0, output);
        try {
            if (((int) COMPRESS_STREAM.invoke(state, operation, availableIn, nextIn, availableOut, nextOut,
                    MemorySegment.NULL)) == 0) {
                throw new IOException(sm.getString("ffmBrotliEncoder.compressFail"));
            }
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            throw new IOException(sm.getString("ffmBrotliEncoder.compressFail"), t);
        } finally {
            // The state must not be destroyed by the Cleaner while the library is using it
            Reference.reachabilityFence(this);
        }
        int consumed = inputSize - (int) availableIn.get(ValueLayout.JAVA_LONG, 0);
        int produced = outputSize - (int) availableOut.get(ValueLayout.JAVA_LONG, 0);
        inputBuffer.position(inputBuffer.position() + consumed);
        MemorySegment.copy(output, 0, MemorySegment.ofBuffer(outputBuffer), 0, produced);
        outputBuffer.position(outputBuffer.position() + produced);
    }


    @Override
    public synchronized boolean hasMoreOutput() {
        checkOpen();
        try {
            return ((int) HAS_MORE_OUTPUT.invoke(state)) != 0;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        } finally {
            Reference.reachabilityFence(this);
        }
    }


    @Override
    public synchronized boolean isFinished() {
        checkOpen();
        try {
            return ((int) IS_FINISHED.invoke(state)) != 0;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        } finally {
            Reference.reachabilityFence(this);
        }
    }


    @Override
    public synchronized void close() {
        closed = true;
        // Runs the Destroyer, if it has not been run, and only once
        cleanable.clean();
    }


    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException(sm.getString("ffmBrotliEncoder.closed"));
        }
    }


    /**
     * Releases the native memory. This must not refer to the encoder, otherwise the encoder could not be garbage
     * collected and the Cleaner would not be able to release the memory of an abandoned encoder.
     */
    private static final class Destroyer implements Runnable {

        private final MemorySegment state;
        private final MemorySegment buffers;

        private Destroyer(MemorySegment state, MemorySegment buffers) {
            this.state = state;
            this.buffers = buffers;
        }

        @Override
        public void run() {
            try {
                if (!MemorySegment.NULL.equals(state)) {
                    DESTROY_INSTANCE.invoke(state);
                }
            } catch (Throwable t) {
                // Nothing that can be done
            }
            try {
                if (buffers.address() != 0) {
                    FREE.invoke(buffers);
                }
            } catch (Throwable t) {
                // Nothing that can be done
            }
        }
    }
}

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
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * A decoder that uses <code>libbrotlidec</code>, so that the tests do not depend on the code that is being tested.
 */
final class BrotliDecoder {

    private static final int ERROR = 0;
    private static final int SUCCESS = 1;
    private static final int NEEDS_MORE_INPUT = 2;
    private static final int NEEDS_MORE_OUTPUT = 3;

    private static final MethodHandle CREATE;
    private static final MethodHandle DECOMPRESS;
    private static final MethodHandle DESTROY;

    static {
        Linker linker = Linker.nativeLinker();
        SymbolLookup lookup = SymbolLookup.libraryLookup(System.mapLibraryName("brotlidec"), Arena.global());
        ValueLayout a = ValueLayout.ADDRESS;
        CREATE = linker.downcallHandle(lookup.find("BrotliDecoderCreateInstance").get(),
                FunctionDescriptor.of(a, a, a, a));
        DECOMPRESS = linker.downcallHandle(lookup.find("BrotliDecoderDecompressStream").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, a, a, a, a, a, a));
        DESTROY = linker.downcallHandle(lookup.find("BrotliDecoderDestroyInstance").get(), FunctionDescriptor.ofVoid(a));
    }


    static boolean isAvailable() {
        return DESTROY != null;
    }


    /**
     * Decode a complete Brotli stream.
     */
    static byte[] decode(byte[] compressed) throws IOException {
        return decode(compressed, true);
    }


    /**
     * Decode as much of a Brotli stream as is present, which does not have to be complete.
     */
    static byte[] decodePartial(byte[] compressed) throws IOException {
        return decode(compressed, false);
    }


    private static byte[] decode(byte[] compressed, boolean complete) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment input = arena.allocate(Math.max(compressed.length, 1));
            MemorySegment.copy(MemorySegment.ofArray(compressed), 0, input, 0, compressed.length);
            MemorySegment output = arena.allocate(64 * 1024);
            MemorySegment availableIn = arena.allocate(Long.BYTES);
            MemorySegment nextIn = arena.allocate(ValueLayout.ADDRESS);
            MemorySegment availableOut = arena.allocate(Long.BYTES);
            MemorySegment nextOut = arena.allocate(ValueLayout.ADDRESS);
            availableIn.set(ValueLayout.JAVA_LONG, 0, compressed.length);
            nextIn.set(ValueLayout.ADDRESS, 0, input);
            MemorySegment state = (MemorySegment) CREATE.invoke(MemorySegment.NULL, MemorySegment.NULL,
                    MemorySegment.NULL);
            try {
                int status;
                do {
                    availableOut.set(ValueLayout.JAVA_LONG, 0, output.byteSize());
                    nextOut.set(ValueLayout.ADDRESS, 0, output);
                    status = (int) DECOMPRESS.invoke(state, availableIn, nextIn, availableOut, nextOut,
                            MemorySegment.NULL);
                    int produced = (int) (output.byteSize() - availableOut.get(ValueLayout.JAVA_LONG, 0));
                    result.write(output.asSlice(0, produced).toArray(ValueLayout.JAVA_BYTE));
                } while (status == NEEDS_MORE_OUTPUT);
                if (status == ERROR || complete && status != SUCCESS) {
                    throw new IOException("Brotli decode status " + status);
                }
                if (status == SUCCESS && availableIn.get(ValueLayout.JAVA_LONG, 0) != 0) {
                    throw new IOException("Data after the end of the Brotli stream");
                }
                if (status != SUCCESS && status != NEEDS_MORE_INPUT) {
                    throw new IOException("Brotli decode status " + status);
                }
            } finally {
                DESTROY.invoke(state);
            }
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            throw new IOException(t);
        }
        return result.toByteArray();
    }


    private BrotliDecoder() {
    }
}

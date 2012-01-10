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
package org.apache.tomcat.util.buf;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;

/**
 * Efficient conversion of character to bytes.
 *
 * This uses the standard JDK mechanism - a writer - but provides mechanisms to
 * recycle all the objects that are used. Input is buffered to improve
 * performance.
 */
public final class C2BConverter {

    private final BufferedWriter writer;
    private final WriteConvertor conv;
    private final IntermediateOutputStream ios;
    private final ByteChunk bb;

    /**
     * Create a converter, with bytes going to a byte buffer.
     */
    public C2BConverter(ByteChunk output, String encoding) throws IOException {
        this.bb = output;
        ios = new IntermediateOutputStream(output);
        conv = new WriteConvertor(ios, B2CConverter.getCharset(encoding));
        writer = new BufferedWriter(conv);
    }

    /**
     * Reset the internal state, empty the buffers.
     * The encoding remain in effect, the internal buffers remain allocated.
     */
    public final void recycle() {
        // Disable any output
        ios.disable();
        // Flush out the BufferedWriter and WriteConvertor
        try {
            writer.flush();
        } catch (IOException e) {
            // TODO Better logging
            e.printStackTrace();
        }
        // Re-enable ready for re-use
        ios.enable();
        bb.recycle();
    }

    /**
     * Generate the bytes using the specified encoding.
     */
    public final void convert(char c[], int off, int len) throws IOException {
        writer.write(c, off, len);
    }

    /**
     * Generate the bytes using the specified encoding.
     */
    public final void convert(String s, int off, int len) throws IOException {
        writer.write(s, off, len);
    }

    /**
     * Generate the bytes using the specified encoding.
     */
    public final void convert(String s) throws IOException {
        writer.write(s);
    }

    /**
     * Generate the bytes using the specified encoding.
     */
    public final void convert(char c) throws IOException {
        writer.write(c);
    }

    /**
     * Flush any internal buffers into the ByteOutput or the internal byte[].
     */
    public final void flushBuffer() throws IOException {
        writer.flush();
    }
}

// -------------------- Private implementation --------------------
/**
 * Special writer class, where close() is overridden. The default implementation
 * would set byteOutputter to null, and the writer can't be recycled.
 *
 * Note that the flush method will empty the internal buffers _and_ call
 * flush on the output stream - that's why we use an intermediary output stream
 * that overrides flush(). The idea is to  have full control: flushing the
 * char->byte converter should be independent of flushing the OutputStream.
 *
 * When a WriteConverter is created, it'll allocate one or 2 byte buffers,
 * with a 8k size that can't be changed ( at least in JDK1.1 -> 1.4 ). It would
 * also allocate a ByteOutputter or equivalent - again some internal buffers.
 *
 * It is essential to keep  this object around and reuse it. You can use either
 * pools or per thread data - but given that in most cases a converter will be
 * needed for every thread and most of the time only 1 ( or 2 ) encodings will
 * be used, it is far better to keep it per thread and eliminate the pool
 * overhead too.
 */
 final class WriteConvertor extends OutputStreamWriter {

    /**
     * Create a converter.
     */
    public WriteConvertor(IntermediateOutputStream out, Charset charset) {
        super(out, charset);
    }

    /**
     * This is a NOOP.
     */
    @Override
    public final void close() throws IOException {
        // NOTHING
        // Calling super.close() would reset out and cb.
    }

    /**
     *  Flush the characters only.
     */
    @Override
    public final void flush() throws IOException {
        // Will flushBuffer and out()
        // flushBuffer put any remaining chars in the byte[]
        super.flush();
    }

    @Override
    public final void write(char cbuf[], int off, int len) throws IOException {
        // Will do the conversion and call write on the output stream
        super.write( cbuf, off, len );
    }
}


/**
 * Special output stream where close() is overridden, so super.close()
 * is never called.
 *
 * This allows recycling. It can also be disabled, so callbacks will
 * not be called if recycling the converter and if data was not flushed.
 */
final class IntermediateOutputStream extends OutputStream {
    private final ByteChunk tbuff;
    private boolean enabled = true;

    public IntermediateOutputStream(ByteChunk tbuff) {
        this.tbuff=tbuff;
    }

    @Override
    public final void close() throws IOException {
        // shouldn't be called - we filter it out in writer
        throw new IOException("close() called - shouldn't happen ");
    }

    @Override
    public final void flush() throws IOException {
        // nothing - write will go directly to the buffer,
        // we don't keep any state
    }

    @Override
    public final void write(byte cbuf[], int off, int len) throws IOException {
        // will do the conversion and call write on the output stream
        if( enabled ) {
            tbuff.append( cbuf, off, len );
        }
    }

    @Override
    public final void write(int i) throws IOException {
        throw new IOException("write( int ) called - shouldn't happen ");
    }

    // -------------------- Internal methods --------------------

    /**
     * Temporary disable - this is used to recycle the converter without
     * generating an output if the buffers were not flushed.
     */
    final void disable() {
        enabled = false;
    }

    /**
     * Re-enable - used to recycle the converter.
     */
    final void enable() {
        enabled = true;
    }
}

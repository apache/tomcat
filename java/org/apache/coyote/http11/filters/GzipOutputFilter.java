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
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

import org.apache.coyote.Response;
import org.apache.coyote.http11.HttpOutputBuffer;
import org.apache.coyote.http11.OutputFilter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * Gzip output filter.
 */
public class GzipOutputFilter implements OutputFilter {
    public static final int DEFAULT_BUFFER_SIZE = 512;
    protected static final Log log = LogFactory.getLog(GzipOutputFilter.class);
    private static final StringManager sm = StringManager.getManager(GzipOutputFilter.class);


    // ----------------------------------------------------- Instance Variables

    /**
     * Next buffer in the pipeline.
     */
    protected HttpOutputBuffer buffer;


    /**
     * Compression output stream.
     */
    protected GZIPOutputStream compressionStream = null;


    /**
     * Fake internal output stream.
     */
    protected final OutputStream fakeOutputStream = new FakeOutputStream();

    /**
     * Compression level for gzip. Valid values are -1 (default), or 1-9
     */
    private int level = Deflater.DEFAULT_COMPRESSION;

    /**
     * Buffer size for gzip compression stream. Default is Deflater default size 512.
     */
    private int bufferSize = DEFAULT_BUFFER_SIZE;

    // --------------------------------------------------- OutputBuffer Methods

    @Override
    public int doWrite(ByteBuffer chunk) throws IOException {
        if (compressionStream == null) {
            compressionStream = new GZIPOutputStream(fakeOutputStream, bufferSize, true) {{
                this.def.setLevel(level);
            }};
        }
        int len = chunk.remaining();
        if (chunk.hasArray()) {
            compressionStream.write(chunk.array(), chunk.arrayOffset() + chunk.position(), len);
            chunk.position(chunk.position() + len);
        } else {
            byte[] bytes = new byte[len];
            chunk.get(bytes);
            compressionStream.write(bytes, 0, len);
        }
        return len;
    }


    @Override
    public long getBytesWritten() {
        return buffer.getBytesWritten();
    }


    // --------------------------------------------------- OutputFilter Methods

    /**
     * {@inheritDoc} Added to allow flushing to happen for the gzip'ed outputstream.
     */
    @Override
    public void flush() throws IOException {
        if (compressionStream != null) {
            try {
                if (log.isTraceEnabled()) {
                    log.trace("Flushing the compression stream!");
                }
                compressionStream.flush();
            } catch (IOException ioe) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("gzipOutputFilter.flushFail"), ioe);
                }
            }
        }
        buffer.flush();
    }


    @Override
    public void setResponse(Response response) {
        // NOOP: No need for parameters from response in this filter
    }


    @Override
    public void setBuffer(HttpOutputBuffer buffer) {
        this.buffer = buffer;
    }


    @Override
    public void end() throws IOException {
        if (compressionStream == null) {
            compressionStream = new GZIPOutputStream(fakeOutputStream, bufferSize, true) {{
                this.def.setLevel(level);
            }};
        }
        compressionStream.finish();
        compressionStream.close();
        buffer.end();
    }


    @Override
    public void recycle() {
        // Set compression stream to null
        compressionStream = null;
    }

    /**
     * Set the compression level for gzip.
     * @param level The compression level. Valid values are -1 (default), or 1-9.
     *                  -1 uses the default compression level.
     *                  1 gives best speed, 9 gives best compression.
     */
    public void setLevel(int level) {
        if (level < -1 || level > 9) {
            throw new IllegalArgumentException(sm.getString("gzipOutputFilter.invalidLevel", Integer.valueOf(level)));
        }
        this.level = level;
    }

    /**
     * Set the buffer size for gzip compression stream.
     *
     * @param bufferSize The buffer size in bytes. Must be positive.
     */
    public void setBufferSize(int bufferSize) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException(sm.getString("gzipOutputFilter.invalidBufferSize", Integer.valueOf(bufferSize)));
        }
        this.bufferSize = bufferSize;
    }
    // ------------------------------------------- FakeOutputStream Inner Class


    protected class FakeOutputStream extends OutputStream {
        protected final ByteBuffer outputChunk = ByteBuffer.allocate(1);

        @Override
        public void write(int b) throws IOException {
            // Shouldn't get used for good performance, but is needed for
            // compatibility with Sun JDK 1.4.0
            outputChunk.put(0, (byte) (b & 0xff));
            buffer.doWrite(outputChunk);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            buffer.doWrite(ByteBuffer.wrap(b, off, len));
        }

        @Override
        public void flush() throws IOException {
            // NOOP
        }

        @Override
        public void close() throws IOException {
            // NOOP
        }
    }


}

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
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.coyote.Response;
import org.apache.coyote.http11.HttpOutputBuffer;
import org.apache.coyote.http11.OutputFilter;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.http.fileupload.ByteArrayOutputStream;

/**
 * Chunked output filter.
 *
 * @author Remy Maucherat
 */
public class ChunkedOutputFilter implements OutputFilter {

    private static final byte[] LAST_CHUNK_BYTES = {(byte) '0', (byte) '\r', (byte) '\n'};
    private static final byte[] CRLF_BYTES = {(byte) '\r', (byte) '\n'};
    private static final byte[] END_CHUNK_BYTES =
        {(byte) '0', (byte) '\r', (byte) '\n', (byte) '\r', (byte) '\n'};

    private static final Set<String> disallowedTrailerFieldNames = new HashSet<>();

    static {
        // Always add these in lower case
        disallowedTrailerFieldNames.add("age");
        disallowedTrailerFieldNames.add("cache-control");
        disallowedTrailerFieldNames.add("content-length");
        disallowedTrailerFieldNames.add("content-encoding");
        disallowedTrailerFieldNames.add("content-range");
        disallowedTrailerFieldNames.add("content-type");
        disallowedTrailerFieldNames.add("date");
        disallowedTrailerFieldNames.add("expires");
        disallowedTrailerFieldNames.add("location");
        disallowedTrailerFieldNames.add("retry-after");
        disallowedTrailerFieldNames.add("trailer");
        disallowedTrailerFieldNames.add("transfer-encoding");
        disallowedTrailerFieldNames.add("vary");
        disallowedTrailerFieldNames.add("warning");
    }

    /**
     * Next buffer in the pipeline.
     */
    protected HttpOutputBuffer buffer;


    /**
     * Chunk header.
     */
    protected final ByteBuffer chunkHeader = ByteBuffer.allocate(10);


    protected final ByteBuffer lastChunk = ByteBuffer.wrap(LAST_CHUNK_BYTES);
    protected final ByteBuffer crlfChunk = ByteBuffer.wrap(CRLF_BYTES);
    /**
     * End chunk.
     */
    protected final ByteBuffer endChunk = ByteBuffer.wrap(END_CHUNK_BYTES);


    private Response response;


    public ChunkedOutputFilter() {
        chunkHeader.put(8, (byte) '\r');
        chunkHeader.put(9, (byte) '\n');
    }


    // --------------------------------------------------- OutputBuffer Methods

    @Override
    public int doWrite(ByteBuffer chunk) throws IOException {

        int result = chunk.remaining();

        if (result <= 0) {
            return 0;
        }

        int pos = calculateChunkHeader(result);

        chunkHeader.position(pos + 1).limit(chunkHeader.position() + 9 - pos);
        buffer.doWrite(chunkHeader);

        buffer.doWrite(chunk);

        chunkHeader.position(8).limit(10);
        buffer.doWrite(chunkHeader);

        return result;
    }


    private int calculateChunkHeader(int len) {
        // Calculate chunk header
        int pos = 7;
        int current = len;
        while (current > 0) {
            int digit = current % 16;
            current = current / 16;
            chunkHeader.put(pos--, HexUtils.getHex(digit));
        }
        return pos;
    }


    @Override
    public long getBytesWritten() {
        return buffer.getBytesWritten();
    }


    // --------------------------------------------------- OutputFilter Methods

    @Override
    public void setResponse(Response response) {
        this.response = response;
    }


    @Override
    public void setBuffer(HttpOutputBuffer buffer) {
        this.buffer = buffer;
    }


    @Override
    public void flush() throws IOException {
        // No data buffered in this filter. Flush next buffer.
        buffer.flush();
    }


    @Override
    public void end() throws IOException {

        Supplier<Map<String,String>> trailerFieldsSupplier = response.getTrailerFields();
        Map<String,String> trailerFields = null;

        if (trailerFieldsSupplier != null) {
            trailerFields = trailerFieldsSupplier.get();
        }

        if (trailerFields == null) {
            // Write end chunk
            buffer.doWrite(endChunk);
            endChunk.position(0).limit(endChunk.capacity());
        } else {
            buffer.doWrite(lastChunk);
            lastChunk.position(0).limit(lastChunk.capacity());

           ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
           OutputStreamWriter osw = new OutputStreamWriter(baos, StandardCharsets.ISO_8859_1);
            for (Map.Entry<String,String> trailerField : trailerFields.entrySet()) {
                // Ignore disallowed headers
                if (disallowedTrailerFieldNames.contains(
                        trailerField.getKey().toLowerCase(Locale.ENGLISH))) {
                    continue;
                }
                osw.write(trailerField.getKey());
                osw.write(':');
                osw.write(' ');
                osw.write(trailerField.getValue());
                osw.write("\r\n");
            }
            osw.close();
            buffer.doWrite(ByteBuffer.wrap(baos.toByteArray()));

            buffer.doWrite(crlfChunk);
            crlfChunk.position(0).limit(crlfChunk.capacity());
        }
        buffer.end();
    }


    @Override
    public void recycle() {
        response = null;
    }
}

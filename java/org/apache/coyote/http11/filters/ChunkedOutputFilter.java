/*
 *  Copyright 1999-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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

import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.HexUtils;

import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Response;
import org.apache.coyote.http11.OutputFilter;

/**
 * Chunked output filter.
 * 
 * @author Remy Maucherat
 */
public class ChunkedOutputFilter implements OutputFilter {


    // -------------------------------------------------------------- Constants


    protected static final String ENCODING_NAME = "chunked";
    protected static final ByteChunk ENCODING = new ByteChunk();


    /**
     * End chunk.
     */
    protected static final ByteChunk END_CHUNK = new ByteChunk();


    // ----------------------------------------------------- Static Initializer


    static {
        ENCODING.setBytes(ENCODING_NAME.getBytes(), 0, ENCODING_NAME.length());
        byte[] END_CHUNK_BYTES = {(byte) '0', (byte) '\r', (byte) '\n', 
                                  (byte) '\r', (byte) '\n'};
        END_CHUNK.setBytes(END_CHUNK_BYTES, 0, END_CHUNK_BYTES.length);
    }


    // ------------------------------------------------------------ Constructor


    /**
     * Default constructor.
     */
    public ChunkedOutputFilter() {
        chunkLength = new byte[10];
        chunkLength[8] = (byte) '\r';
        chunkLength[9] = (byte) '\n';
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Next buffer in the pipeline.
     */
    protected OutputBuffer buffer;


    /**
     * Buffer used for chunk length conversion.
     */
    protected byte[] chunkLength = new byte[10];


    /**
     * Chunk header.
     */
    protected ByteChunk chunkHeader = new ByteChunk();


    // ------------------------------------------------------------- Properties


    // --------------------------------------------------- OutputBuffer Methods


    /**
     * Write some bytes.
     * 
     * @return number of bytes written by the filter
     */
    public int doWrite(ByteChunk chunk, Response res)
        throws IOException {

        int result = chunk.getLength();

        if (result <= 0) {
            return 0;
        }

        // Calculate chunk header
        int pos = 7;
        int current = result;
        while (current > 0) {
            int digit = current % 16;
            current = current / 16;
            chunkLength[pos--] = HexUtils.HEX[digit];
        }
        chunkHeader.setBytes(chunkLength, pos + 1, 9 - pos);
        buffer.doWrite(chunkHeader, res);

        buffer.doWrite(chunk, res);

        chunkHeader.setBytes(chunkLength, 8, 2);
        buffer.doWrite(chunkHeader, res);

        return result;

    }


    // --------------------------------------------------- OutputFilter Methods


    /**
     * Some filters need additional parameters from the response. All the 
     * necessary reading can occur in that method, as this method is called
     * after the response header processing is complete.
     */
    public void setResponse(Response response) {
    }


    /**
     * Set the next buffer in the filter pipeline.
     */
    public void setBuffer(OutputBuffer buffer) {
        this.buffer = buffer;
    }


    /**
     * End the current request. It is acceptable to write extra bytes using
     * buffer.doWrite during the execution of this method.
     */
    public long end()
        throws IOException {

        // Write end chunk
        buffer.doWrite(END_CHUNK, null);
        
        return 0;

    }


    /**
     * Make the filter ready to process the next request.
     */
    public void recycle() {
    }


    /**
     * Return the name of the associated encoding; Here, the value is 
     * "identity".
     */
    public ByteChunk getEncodingName() {
        return ENCODING;
    }


}

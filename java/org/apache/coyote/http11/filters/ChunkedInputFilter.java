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
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.apache.coyote.ActionCode;
import org.apache.coyote.BadRequestException;
import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.coyote.http11.Constants;
import org.apache.coyote.http11.InputFilter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.http.parser.HttpHeaderParser;
import org.apache.tomcat.util.http.parser.HttpHeaderParser.HeaderDataSource;
import org.apache.tomcat.util.http.parser.HttpHeaderParser.HeaderParseStatus;
import org.apache.tomcat.util.net.ApplicationBufferHandler;
import org.apache.tomcat.util.res.StringManager;

/**
 * Chunked input filter. Parses chunked data according to <a href=
 * "http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.6.1">http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.6.1</a><br>
 *
 * @author Remy Maucherat
 */
public class ChunkedInputFilter implements InputFilter, ApplicationBufferHandler, HeaderDataSource {

    private static final StringManager sm = StringManager.getManager(ChunkedInputFilter.class);


    // -------------------------------------------------------------- Constants

    protected static final String ENCODING_NAME = "chunked";
    protected static final ByteChunk ENCODING = new ByteChunk();


    // ----------------------------------------------------- Static Initializer

    static {
        ENCODING.setBytes(ENCODING_NAME.getBytes(StandardCharsets.ISO_8859_1), 0, ENCODING_NAME.length());
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * Next buffer in the pipeline.
     */
    protected InputBuffer buffer;


    /**
     * Number of bytes remaining in the current chunk.
     */
    protected int remaining = 0;


    /**
     * Byte chunk used to read bytes.
     */
    protected ByteBuffer readChunk;


    /**
     * Buffer used to store trailing headers. Is normally in read mode.
     */
    protected final ByteBuffer trailingHeaders;


    /**
     * Request being parsed.
     */
    private final Request request;


    /**
     * Limit for extension size.
     */
    private final long maxExtensionSize;


    private final int maxSwallowSize;

    private final Set<String> allowedTrailerHeaders;

    /*
     * Parsing state.
     */
    private volatile ParseState parseState = ParseState.CHUNK_HEADER;
    private volatile boolean crFound = false;
    private volatile int chunkSizeDigitsRead = 0;
    private volatile boolean parsingExtension = false;
    private volatile long extensionSize;
    private final HttpHeaderParser httpHeaderParser;

    // ----------------------------------------------------------- Constructors

    public ChunkedInputFilter(final Request request, int maxTrailerSize, Set<String> allowedTrailerHeaders,
            int maxExtensionSize, int maxSwallowSize) {
        this.request = request;
        this.trailingHeaders = ByteBuffer.allocate(maxTrailerSize);
        this.trailingHeaders.limit(0);
        this.allowedTrailerHeaders = allowedTrailerHeaders;
        this.maxExtensionSize = maxExtensionSize;
        this.maxSwallowSize = maxSwallowSize;
        this.httpHeaderParser = new HttpHeaderParser(this, request.getMimeTrailerFields(), false);
    }


    // ---------------------------------------------------- InputBuffer Methods

    @Override
    public int doRead(ApplicationBufferHandler handler) throws IOException {
        while (true) {
            switch (parseState) {
                case CHUNK_HEADER:
                    if (!parseChunkHeader()) {
                        return 0;
                    }
                    break;
                case CHUNK_BODY:
                    return parseChunkBody(handler);
                case CHUNK_BODY_CRLF:
                    if (!parseCRLF()) {
                        return 0;
                    }
                    parseState = ParseState.CHUNK_HEADER;
                    break;
                case TRAILER_FIELDS:
                    if (!parseTrailerFields()) {
                        return 0;
                    }
                    /*
                     * If on a non-container thread the dispatch in parseTrailerFields() will have triggered the
                     * recycling of this filter so return -1 here else the non-container thread may try and continue
                     * reading.
                     */
                    return -1;
                case FINISHED:
                    return -1;
                case ERROR:
                default:
                    throw new IOException(sm.getString("chunkedInputFilter.error"));
            }
        }
    }


    // ---------------------------------------------------- InputFilter Methods

    @Override
    public void setRequest(Request request) {
        // NO-OP - Request is fixed and passed to constructor.
    }


    @Override
    public long end() throws IOException {
        long swallowed = 0;
        int read = 0;
        // Consume extra bytes : parse the stream until the end chunk is found
        while ((read = doRead(this)) >= 0) {
            swallowed += read;
            if (maxSwallowSize > -1 && swallowed > maxSwallowSize) {
                throwBadRequestException(sm.getString("inputFilter.maxSwallow"));
            }
        }
        // Excess bytes copied to trailingHeaders need to be restored in readChunk for the next request.
        if (trailingHeaders.remaining() > 0) {
            readChunk.position(readChunk.position() - trailingHeaders.remaining());
        }
        // Return the number of extra bytes which were consumed
        return readChunk.remaining();
    }


    @Override
    public int available() {
        int available = 0;
        if (readChunk != null) {
            available = readChunk.remaining();
        }

        // Handle some edge cases
        if (available == 1 && parseState == ParseState.CHUNK_BODY_CRLF) {
            // Either just the CR or just the LF are left in the buffer. There is no data to read.
            available = 0;
        } else if (available == 2 && !crFound && parseState == ParseState.CHUNK_BODY_CRLF) {
            // Just CRLF is left in the buffer. There is no data to read.
            available = 0;
        }

        if (available == 0) {
            // No data buffered here. Try the next filter in the chain.
            return buffer.available();
        } else {
            return available;
        }
    }


    @Override
    public void setBuffer(InputBuffer buffer) {
        this.buffer = buffer;
    }


    @Override
    public void recycle() {
        remaining = 0;
        if (readChunk != null) {
            readChunk.position(0).limit(0);
        }
        trailingHeaders.clear();
        trailingHeaders.limit(0);
        parseState = ParseState.CHUNK_HEADER;
        crFound = false;
        chunkSizeDigitsRead = 0;
        parsingExtension = false;
        extensionSize = 0;
        httpHeaderParser.recycle();
    }


    @Override
    public ByteChunk getEncodingName() {
        return ENCODING;
    }


    @Override
    public boolean isFinished() {
        return parseState == ParseState.FINISHED;
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * Read bytes from the previous buffer.
     *
     * @return The byte count which has been read
     *
     * @throws IOException Read error
     */
    protected int readBytes() throws IOException {
        return buffer.doRead(this);
    }


    @Override
    public boolean fillHeaderBuffer() throws IOException {
        // fill() automatically detects if blocking or non-blocking IO should be used
        if (fill()) {
            if (trailingHeaders.position() == trailingHeaders.capacity()) {
                // At maxTrailerSize. Any further read will exceed maxTrailerSize.
                throw new BadRequestException(sm.getString("chunkedInputFilter.maxTrailer"));
            }

            // Configure trailing headers for appending additional data
            int originalPos = trailingHeaders.position();
            trailingHeaders.position(trailingHeaders.limit());
            trailingHeaders.limit(trailingHeaders.capacity());

            if (readChunk.remaining() > trailingHeaders.remaining()) {
                // readChunk has more data available than trailingHeaders has space so adjust limit
                int originalLimit = readChunk.limit();
                readChunk.limit(readChunk.position() + trailingHeaders.remaining());
                trailingHeaders.put(readChunk);
                readChunk.limit(originalLimit);
            } else {
                // readChunk has less data than trailingHeaders has remaining so no need to adjust limit.
                trailingHeaders.put(readChunk);
            }

            // Configure trailing headers for reading
            trailingHeaders.limit(trailingHeaders.position());
            trailingHeaders.position(originalPos);
            return true;
        }
        return false;
    }


    private boolean fill() throws IOException {
        if (readChunk == null || readChunk.position() >= readChunk.limit()) {
            // Automatically detects if blocking or non-blocking should be used
            int read = readBytes();
            if (read < 0) {
                // Unexpected end of stream
                throwBadRequestException(sm.getString("chunkedInputFilter.invalidHeader"));
            } else if (read == 0) {
                return false;
            }
        }
        return true;
    }


    /**
     * Parse the header of a chunk. A chunk header can look like one of the following:<br>
     * A10CRLF<br>
     * F23;chunk-extension to be ignoredCRLF
     * <p>
     * The letters before CRLF or ';' (whatever comes first) must be valid hex digits. We should not parse
     * F23IAMGONNAMESSTHISUP34CRLF as a valid header according to the spec.
     *
     * @return {@code true} if the read is complete or {@code false if incomplete}. In complete reads can only happen
     *             with non-blocking I/O.
     *
     * @throws IOException Read error
     */
    private boolean parseChunkHeader() throws IOException {

        boolean eol = false;

        while (!eol) {
            if (!fill()) {
                return false;
            }

            byte chr = readChunk.get(readChunk.position());
            if (chr == Constants.CR || chr == Constants.LF) {
                parsingExtension = false;
                if (!parseCRLF()) {
                    return false;
                }
                eol = true;
            } else if (chr == Constants.SEMI_COLON && !parsingExtension) {
                // First semi-colon marks the start of the extension. Further
                // semi-colons may appear to separate multiple chunk-extensions.
                // These need to be processed as part of parsing the extensions.
                parsingExtension = true;
                extensionSize++;
            } else if (!parsingExtension) {
                int charValue = HexUtils.getDec(chr);
                if (charValue != -1 && chunkSizeDigitsRead < 8) {
                    chunkSizeDigitsRead++;
                    remaining = (remaining << 4) | charValue;
                } else {
                    // Isn't valid hex so this is an error condition
                    throwBadRequestException(sm.getString("chunkedInputFilter.invalidHeader"));
                }
            } else {
                // Extension 'parsing'
                // Note that the chunk-extension is neither parsed nor
                // validated. Currently it is simply ignored.
                extensionSize++;
                if (maxExtensionSize > -1 && extensionSize > maxExtensionSize) {
                    throwBadRequestException(sm.getString("chunkedInputFilter.maxExtension"));
                }
            }

            // Parsing the CRLF increments pos
            if (!eol) {
                readChunk.position(readChunk.position() + 1);
            }
        }

        if (chunkSizeDigitsRead == 0 || remaining < 0) {
            throwBadRequestException(sm.getString("chunkedInputFilter.invalidHeader"));
        } else {
            chunkSizeDigitsRead = 0;
        }

        if (remaining == 0) {
            parseState = ParseState.TRAILER_FIELDS;
        } else {
            parseState = ParseState.CHUNK_BODY;
        }

        return true;
    }


    private int parseChunkBody(ApplicationBufferHandler handler) throws IOException {
        int result = 0;

        if (!fill()) {
            return 0;
        }

        if (remaining > readChunk.remaining()) {
            result = readChunk.remaining();
            remaining = remaining - result;
            if (readChunk != handler.getByteBuffer()) {
                handler.setByteBuffer(readChunk.duplicate());
            }
            readChunk.position(readChunk.limit());
        } else {
            result = remaining;
            if (readChunk != handler.getByteBuffer()) {
                handler.setByteBuffer(readChunk.duplicate());
                handler.getByteBuffer().limit(readChunk.position() + remaining);
            }
            readChunk.position(readChunk.position() + remaining);
            remaining = 0;

            parseState = ParseState.CHUNK_BODY_CRLF;
        }

        return result;
    }


    /**
     * Parse CRLF at end of chunk.
     *
     * @return {@code true} if the read is complete or {@code false if incomplete}. In complete reads can only happen
     *             with non-blocking I/O.
     *
     * @throws IOException An error occurred parsing CRLF
     */
    private boolean parseCRLF() throws IOException {

        boolean eol = false;

        while (!eol) {
            if (!fill()) {
                return false;
            }

            byte chr = readChunk.get(readChunk.position());
            if (chr == Constants.CR) {
                if (crFound) {
                    throwBadRequestException(sm.getString("chunkedInputFilter.invalidCrlfCRCR"));
                }
                crFound = true;
            } else if (chr == Constants.LF) {
                if (!crFound) {
                    throwBadRequestException(sm.getString("chunkedInputFilter.invalidCrlfNoCR"));
                }
                eol = true;
            } else {
                throwBadRequestException(sm.getString("chunkedInputFilter.invalidCrlf"));
            }

            readChunk.position(readChunk.position() + 1);
        }

        crFound = false;
        return true;
    }


    /**
     * Parse end chunk data.
     *
     * @return {@code true} if the read is complete or {@code false if incomplete}. In complete reads can only happen
     *             with non-blocking I/O.
     *
     * @throws IOException Error propagation
     */
    private boolean parseTrailerFields() throws IOException {
        // Handle optional trailer headers
        HeaderParseStatus status = HeaderParseStatus.HAVE_MORE_HEADERS;
        do {
            try {
                status = httpHeaderParser.parseHeader();
            } catch (IllegalArgumentException iae) {
                parseState = ParseState.ERROR;
                throw new BadRequestException(iae);
            }
        } while (status == HeaderParseStatus.HAVE_MORE_HEADERS);
        if (status == HeaderParseStatus.DONE) {
            parseState = ParseState.FINISHED;
            request.getMimeTrailerFields().filter(allowedTrailerHeaders);
            if (request.getReadListener() != null) {
                /*
                 * Perform the dispatch back to the container for the onAllDataRead() event. For non-chunked input this
                 * would be performed when isReady() is next called.
                 *
                 * Chunked input returns one chunk at a time for non-blocking reads. A consequence of this is that
                 * reading the final chunk returns -1 which signals the end of stream. The application code reading the
                 * request body probably won't call isReady() after receiving the -1 return value since it already knows
                 * it is at end of stream. Therefore we trigger the dispatch back to the container here which in turn
                 * ensures the onAllDataRead() event is fired.
                 */
                request.action(ActionCode.DISPATCH_READ, null);
                request.action(ActionCode.DISPATCH_EXECUTE, null);
            }
            return true;
        } else {
            return false;
        }
    }


    private void throwBadRequestException(String msg) throws IOException {
        parseState = ParseState.ERROR;
        throw new BadRequestException(msg);
    }


    @Override
    public void setByteBuffer(ByteBuffer buffer) {
        readChunk = buffer;
    }


    @Override
    public ByteBuffer getByteBuffer() {
        return readChunk;
    }


    @Override
    public ByteBuffer getHeaderByteBuffer() {
        return trailingHeaders;
    }


    @Override
    public void expand(int size) {
        // no-op
    }


    private enum ParseState {
        CHUNK_HEADER,
        CHUNK_BODY,
        CHUNK_BODY_CRLF,
        TRAILER_FIELDS,
        FINISHED,
        ERROR
    }
}

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

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.coyote.http11.Constants;
import org.apache.coyote.http11.InputFilter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.net.ApplicationBufferHandler;
import org.apache.tomcat.util.res.StringManager;

/**
 * Chunked input filter. Parses chunked data according to
 * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.6.1">http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.6.1</a><br>
 *
 * @author Remy Maucherat
 */
public class ChunkedInputFilter implements InputFilter, ApplicationBufferHandler {

    private static final StringManager sm = StringManager.getManager(
            ChunkedInputFilter.class.getPackage().getName());


    // -------------------------------------------------------------- Constants

    protected static final String ENCODING_NAME = "chunked";
    protected static final ByteChunk ENCODING = new ByteChunk();


    // ----------------------------------------------------- Static Initializer

    static {
        ENCODING.setBytes(ENCODING_NAME.getBytes(StandardCharsets.ISO_8859_1),
                0, ENCODING_NAME.length());
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
     * Flag set to true when the end chunk has been read.
     */
    protected boolean endChunk = false;


    /**
     * Byte chunk used to store trailing headers.
     */
    protected final ByteChunk trailingHeaders = new ByteChunk();


    /**
     * Flag set to true if the next call to doRead() must parse a CRLF pair
     * before doing anything else.
     */
    protected boolean needCRLFParse = false;


    /**
     * Request being parsed.
     */
    private Request request;


    /**
     * Limit for extension size.
     */
    private final long maxExtensionSize;


    /**
     * Limit for trailer size.
     */
    private final int maxTrailerSize;


    /**
     * Size of extensions processed for this request.
     */
    private long extensionSize;


    private final int maxSwallowSize;


    /**
     * Flag that indicates if an error has occurred.
     */
    private boolean error;


    private final Set<String> allowedTrailerHeaders;

    // ----------------------------------------------------------- Constructors

    public ChunkedInputFilter(int maxTrailerSize, Set<String> allowedTrailerHeaders,
            int maxExtensionSize, int maxSwallowSize) {
        this.trailingHeaders.setLimit(maxTrailerSize);
        this.allowedTrailerHeaders = allowedTrailerHeaders;
        this.maxExtensionSize = maxExtensionSize;
        this.maxTrailerSize = maxTrailerSize;
        this.maxSwallowSize = maxSwallowSize;
    }


    // ---------------------------------------------------- InputBuffer Methods

    /**
     * @deprecated Unused. Will be removed in Tomcat 9. Use
     *             {@link #doRead(ApplicationBufferHandler)}
     */
    @Deprecated
    @Override
    public int doRead(ByteChunk chunk) throws IOException {
        if (endChunk) {
            return -1;
        }

        checkError();

        if(needCRLFParse) {
            needCRLFParse = false;
            parseCRLF(false);
        }

        if (remaining <= 0) {
            if (!parseChunkHeader()) {
                throwIOException(sm.getString("chunkedInputFilter.invalidHeader"));
            }
            if (endChunk) {
                parseEndChunk();
                return -1;
            }
        }

        int result = 0;

        if (readChunk == null || readChunk.position() >= readChunk.limit()) {
            if (readBytes() < 0) {
                throwIOException(sm.getString("chunkedInputFilter.eos"));
            }
        }

        if (remaining > readChunk.remaining()) {
            result = readChunk.remaining();
            remaining = remaining - result;
            chunk.setBytes(readChunk.array(), readChunk.arrayOffset() + readChunk.position(), result);
            readChunk.position(readChunk.limit());
        } else {
            result = remaining;
            chunk.setBytes(readChunk.array(), readChunk.arrayOffset() + readChunk.position(), remaining);
            readChunk.position(readChunk.position() + remaining);
            remaining = 0;
            //we need a CRLF
            if ((readChunk.position() + 1) >= readChunk.limit()) {
                //if we call parseCRLF we overrun the buffer here
                //so we defer it to the next call BZ 11117
                needCRLFParse = true;
            } else {
                parseCRLF(false); //parse the CRLF immediately
            }
        }

        return result;
    }

    @Override
    public int doRead(ApplicationBufferHandler handler) throws IOException {
        if (endChunk) {
            return -1;
        }

        checkError();

        if(needCRLFParse) {
            needCRLFParse = false;
            parseCRLF(false);
        }

        if (remaining <= 0) {
            if (!parseChunkHeader()) {
                throwIOException(sm.getString("chunkedInputFilter.invalidHeader"));
            }
            if (endChunk) {
                parseEndChunk();
                return -1;
            }
        }

        int result = 0;

        if (readChunk == null || readChunk.position() >= readChunk.limit()) {
            if (readBytes() < 0) {
                throwIOException(sm.getString("chunkedInputFilter.eos"));
            }
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
            //we need a CRLF
            if ((readChunk.position() + 1) >= readChunk.limit()) {
                //if we call parseCRLF we overrun the buffer here
                //so we defer it to the next call BZ 11117
                needCRLFParse = true;
            } else {
                parseCRLF(false); //parse the CRLF immediately
            }
        }

        return result;
    }


    // ---------------------------------------------------- InputFilter Methods

    /**
     * Read the content length from the request.
     */
    @Override
    public void setRequest(Request request) {
        this.request = request;
    }


    /**
     * End the current request.
     */
    @Override
    public long end() throws IOException {
        long swallowed = 0;
        int read = 0;
        // Consume extra bytes : parse the stream until the end chunk is found
        while ((read = doRead(this)) >= 0) {
            swallowed += read;
            if (maxSwallowSize > -1 && swallowed > maxSwallowSize) {
                throwIOException(sm.getString("inputFilter.maxSwallow"));
            }
        }

        // Return the number of extra bytes which were consumed
        return readChunk.remaining();
    }


    /**
     * Amount of bytes still available in a buffer.
     */
    @Override
    public int available() {
        return readChunk != null ? readChunk.remaining() : 0;
    }


    /**
     * Set the next buffer in the filter pipeline.
     */
    @Override
    public void setBuffer(InputBuffer buffer) {
        this.buffer = buffer;
    }


    /**
     * Make the filter ready to process the next request.
     */
    @Override
    public void recycle() {
        remaining = 0;
        if (readChunk != null) {
            readChunk.position(0).limit(0);
        }
        endChunk = false;
        needCRLFParse = false;
        trailingHeaders.recycle();
        trailingHeaders.setLimit(maxTrailerSize);
        extensionSize = 0;
        error = false;
    }


    /**
     * Return the name of the associated encoding; Here, the value is
     * "identity".
     */
    @Override
    public ByteChunk getEncodingName() {
        return ENCODING;
    }


    @Override
    public boolean isFinished() {
        return endChunk;
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * Read bytes from the previous buffer.
     * @return The byte count which has been read
     * @throws IOException Read error
     */
    protected int readBytes() throws IOException {
        return buffer.doRead(this);
    }


    /**
     * Parse the header of a chunk.
     * A chunk header can look like one of the following:<br>
     * A10CRLF<br>
     * F23;chunk-extension to be ignoredCRLF
     *
     * <p>
     * The letters before CRLF or ';' (whatever comes first) must be valid hex
     * digits. We should not parse F23IAMGONNAMESSTHISUP34CRLF as a valid
     * header according to the spec.
     * @return <code>true</code> if the chunk header has been
     *  successfully parsed
     * @throws IOException Read error
     */
    protected boolean parseChunkHeader() throws IOException {

        int result = 0;
        boolean eol = false;
        int readDigit = 0;
        boolean extension = false;

        while (!eol) {

            if (readChunk == null || readChunk.position() >= readChunk.limit()) {
                if (readBytes() <= 0)
                    return false;
            }

            byte chr = readChunk.get(readChunk.position());
            if (chr == Constants.CR || chr == Constants.LF) {
                parseCRLF(false);
                eol = true;
            } else if (chr == Constants.SEMI_COLON && !extension) {
                // First semi-colon marks the start of the extension. Further
                // semi-colons may appear to separate multiple chunk-extensions.
                // These need to be processed as part of parsing the extensions.
                extension = true;
                extensionSize++;
            } else if (!extension) {
                //don't read data after the trailer
                int charValue = HexUtils.getDec(chr);
                if (charValue != -1 && readDigit < 8) {
                    readDigit++;
                    result = (result << 4) | charValue;
                } else {
                    //we shouldn't allow invalid, non hex characters
                    //in the chunked header
                    return false;
                }
            } else {
                // Extension 'parsing'
                // Note that the chunk-extension is neither parsed nor
                // validated. Currently it is simply ignored.
                extensionSize++;
                if (maxExtensionSize > -1 && extensionSize > maxExtensionSize) {
                    throwIOException(sm.getString("chunkedInputFilter.maxExtension"));
                }
            }

            // Parsing the CRLF increments pos
            if (!eol) {
                readChunk.position(readChunk.position() + 1);
            }
        }

        if (readDigit == 0 || result < 0) {
            return false;
        }

        if (result == 0) {
            endChunk = true;
        }

        remaining = result;
        return true;
    }


    /**
     * Parse CRLF at end of chunk.
     *
     * @param   tolerant    Should tolerant parsing (LF and CRLF) be used? This
     *                      is recommended (RFC2616, section 19.3) for message
     *                      headers.
     * @throws IOException An error occurred parsing CRLF
     */
    protected void parseCRLF(boolean tolerant) throws IOException {

        boolean eol = false;
        boolean crfound = false;

        while (!eol) {
            if (readChunk == null || readChunk.position() >= readChunk.limit()) {
                if (readBytes() <= 0) {
                    throwIOException(sm.getString("chunkedInputFilter.invalidCrlfNoData"));
                }
            }

            byte chr = readChunk.get(readChunk.position());
            if (chr == Constants.CR) {
                if (crfound) {
                    throwIOException(sm.getString("chunkedInputFilter.invalidCrlfCRCR"));
                }
                crfound = true;
            } else if (chr == Constants.LF) {
                if (!tolerant && !crfound) {
                    throwIOException(sm.getString("chunkedInputFilter.invalidCrlfNoCR"));
                }
                eol = true;
            } else {
                throwIOException(sm.getString("chunkedInputFilter.invalidCrlf"));
            }

            readChunk.position(readChunk.position() + 1);
        }
    }


    /**
     * Parse end chunk data.
     * @throws IOException Error propagation
     */
    protected void parseEndChunk() throws IOException {
        // Handle optional trailer headers
        while (parseHeader()) {
            // Loop until we run out of headers
        }
    }


    private boolean parseHeader() throws IOException {

        MimeHeaders headers = request.getMimeHeaders();

        byte chr = 0;

        // Read new bytes if needed
        if (readChunk == null || readChunk.position() >= readChunk.limit()) {
            if (readBytes() <0) {
               throwEOFException(sm.getString("chunkedInputFilter.eosTrailer"));
            }
        }

        // readBytes() above will set readChunk unless it returns a value < 0
        chr = readChunk.get(readChunk.position());

        // CRLF terminates the request
        if (chr == Constants.CR || chr == Constants.LF) {
            parseCRLF(false);
            return false;
        }

        // Mark the current buffer position
        int startPos = trailingHeaders.getEnd();

        //
        // Reading the header name
        // Header name is always US-ASCII
        //

        boolean colon = false;
        while (!colon) {

            // Read new bytes if needed
            if (readChunk == null || readChunk.position() >= readChunk.limit()) {
                if (readBytes() <0) {
                    throwEOFException(sm.getString("chunkedInputFilter.eosTrailer"));
                }
            }

            // readBytes() above will set readChunk unless it returns a value < 0
            chr = readChunk.get(readChunk.position());
            if ((chr >= Constants.A) && (chr <= Constants.Z)) {
                chr = (byte) (chr - Constants.LC_OFFSET);
            }

            if (chr == Constants.COLON) {
                colon = true;
            } else {
                trailingHeaders.append(chr);
            }

            readChunk.position(readChunk.position() + 1);

        }
        int colonPos = trailingHeaders.getEnd();

        //
        // Reading the header value (which can be spanned over multiple lines)
        //

        boolean eol = false;
        boolean validLine = true;
        int lastSignificantChar = 0;

        while (validLine) {

            boolean space = true;

            // Skipping spaces
            while (space) {

                // Read new bytes if needed
                if (readChunk == null || readChunk.position() >= readChunk.limit()) {
                    if (readBytes() <0) {
                        throwEOFException(sm.getString("chunkedInputFilter.eosTrailer"));
                    }
                }

                chr = readChunk.get(readChunk.position());
                if ((chr == Constants.SP) || (chr == Constants.HT)) {
                    readChunk.position(readChunk.position() + 1);
                    // If we swallow whitespace, make sure it counts towards the
                    // limit placed on trailing header size
                    int newlimit = trailingHeaders.getLimit() -1;
                    if (trailingHeaders.getEnd() > newlimit) {
                        throwIOException(sm.getString("chunkedInputFilter.maxTrailer"));
                    }
                    trailingHeaders.setLimit(newlimit);
                } else {
                    space = false;
                }

            }

            // Reading bytes until the end of the line
            while (!eol) {

                // Read new bytes if needed
                if (readChunk == null || readChunk.position() >= readChunk.limit()) {
                    if (readBytes() <0) {
                        throwEOFException(sm.getString("chunkedInputFilter.eosTrailer"));
                    }
                }

                chr = readChunk.get(readChunk.position());
                if (chr == Constants.CR || chr == Constants.LF) {
                    parseCRLF(true);
                    eol = true;
                } else if (chr == Constants.SP) {
                    trailingHeaders.append(chr);
                } else {
                    trailingHeaders.append(chr);
                    lastSignificantChar = trailingHeaders.getEnd();
                }

                if (!eol) {
                    readChunk.position(readChunk.position() + 1);
                }
            }

            // Checking the first character of the new line. If the character
            // is a LWS, then it's a multiline header

            // Read new bytes if needed
            if (readChunk == null || readChunk.position() >= readChunk.limit()) {
                if (readBytes() <0) {
                    throwEOFException(sm.getString("chunkedInputFilter.eosTrailer"));
                }
            }

            chr = readChunk.get(readChunk.position());
            if ((chr != Constants.SP) && (chr != Constants.HT)) {
                validLine = false;
            } else {
                eol = false;
                // Copying one extra space in the buffer (since there must
                // be at least one space inserted between the lines)
                trailingHeaders.append(chr);
            }

        }

        String headerName = new String(trailingHeaders.getBytes(), startPos,
                colonPos - startPos, StandardCharsets.ISO_8859_1);

        if (allowedTrailerHeaders.contains(headerName.toLowerCase(Locale.ENGLISH))) {
            MessageBytes headerValue = headers.addValue(headerName);

            // Set the header value
            headerValue.setBytes(trailingHeaders.getBytes(), colonPos,
                    lastSignificantChar - colonPos);
        }

        return true;
    }


    private void throwIOException(String msg) throws IOException {
        error = true;
        throw new IOException(msg);
    }


    private void throwEOFException(String msg) throws IOException {
        error = true;
        throw new EOFException(msg);
    }


    private void checkError() throws IOException {
        if (error) {
            throw new IOException(sm.getString("chunkedInputFilter.error"));
        }
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
    public void expand(int size) {
        // no-op
    }
}

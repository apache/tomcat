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
import java.nio.charset.Charset;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.coyote.http11.Constants;
import org.apache.coyote.http11.InputFilter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;

/**
 * Chunked input filter. Parses chunked data according to
 * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.6.1">http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.6.1</a><br>
 * 
 * @author Remy Maucherat
 * @author Filip Hanik
 */
public class ChunkedInputFilter implements InputFilter {


    // -------------------------------------------------------------- Constants


    protected static final String ENCODING_NAME = "chunked";
    protected static final ByteChunk ENCODING = new ByteChunk();


    // ----------------------------------------------------- Static Initializer


    static {
        ENCODING.setBytes(ENCODING_NAME.getBytes(Charset.defaultCharset()), 0,
                ENCODING_NAME.length());
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
     * Position in the buffer.
     */
    protected int pos = 0;


    /**
     * Last valid byte in the buffer.
     */
    protected int lastValid = 0;


    /**
     * Read bytes buffer.
     */
    protected byte[] buf = null;


    /**
     * Byte chunk used to read bytes.
     */
    protected ByteChunk readChunk = new ByteChunk();


    /**
     * Flag set to true when the end chunk has been read.
     */
    protected boolean endChunk = false;


    /**
     * Byte chunk used to store trailing headers.
     */
    protected ByteChunk trailingHeaders = new ByteChunk();

    /**
     * Flag set to true if the next call to doRead() must parse a CRLF pair
     * before doing anything else.
     */
    protected boolean needCRLFParse = false;


    /**
     * Request being parsed.
     */
    private Request request;
    
    // ----------------------------------------------------------- Constructors
    public ChunkedInputFilter(int maxTrailerSize) {
        this.trailingHeaders.setLimit(maxTrailerSize);
    }

    // ---------------------------------------------------- InputBuffer Methods


    /**
     * Read bytes.
     * 
     * @return If the filter does request length control, this value is
     * significant; it should be the number of bytes consumed from the buffer,
     * up until the end of the current request body, or the buffer length, 
     * whichever is greater. If the filter does not do request body length
     * control, the returned value should be -1.
     */
    @Override
    public int doRead(ByteChunk chunk, Request req)
        throws IOException {

        if (endChunk)
            return -1;

        if(needCRLFParse) {
            needCRLFParse = false;
            parseCRLF(false);
        }

        if (remaining <= 0) {
            if (!parseChunkHeader()) {
                throw new IOException("Invalid chunk header");
            }
            if (endChunk) {
                parseEndChunk();
                return -1;
            }
        }

        int result = 0;

        if (pos >= lastValid) {
            if (readBytes() < 0) {
                throw new IOException(
                        "Unexpected end of stream whilst reading request body");
            }
        }

        if (remaining > (lastValid - pos)) {
            result = lastValid - pos;
            remaining = remaining - result;
            chunk.setBytes(buf, pos, result);
            pos = lastValid;
        } else {
            result = remaining;
            chunk.setBytes(buf, pos, remaining);
            pos = pos + remaining;
            remaining = 0;
            //we need a CRLF
            if ((pos+1) >= lastValid) {   
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
    public long end()
        throws IOException {

        // Consume extra bytes : parse the stream until the end chunk is found
        while (doRead(readChunk, null) >= 0) {
            // NOOP: Just consume the input
        }

        // Return the number of extra bytes which were consumed
        return (lastValid - pos);

    }


    /**
     * Amount of bytes still available in a buffer.
     */
    @Override
    public int available() {
        return (lastValid - pos);
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
        pos = 0;
        lastValid = 0;
        endChunk = false;
        needCRLFParse = false;
        trailingHeaders.recycle();
    }


    /**
     * Return the name of the associated encoding; Here, the value is 
     * "identity".
     */
    @Override
    public ByteChunk getEncodingName() {
        return ENCODING;
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Read bytes from the previous buffer.
     */
    protected int readBytes()
        throws IOException {

        int nRead = buffer.doRead(readChunk, null);
        pos = readChunk.getStart();
        lastValid = pos + nRead;
        buf = readChunk.getBytes();

        return nRead;

    }


    /**
     * Parse the header of a chunk.
     * A chunk header can look like one of the following:<br />
     * A10CRLF<br />
     * F23;chunk-extension to be ignoredCRLF
     *
     * <p>
     * The letters before CRLF or ';' (whatever comes first) must be valid hex
     * digits. We should not parse F23IAMGONNAMESSTHISUP34CRLF as a valid
     * header according to the spec.
     */
    protected boolean parseChunkHeader()
        throws IOException {

        int result = 0;
        boolean eol = false;
        boolean readDigit = false;
        boolean trailer = false;

        while (!eol) {

            if (pos >= lastValid) {
                if (readBytes() <= 0)
                    return false;
            }

            if (buf[pos] == Constants.CR || buf[pos] == Constants.LF) {
                parseCRLF(false);
                eol = true;
            } else if (buf[pos] == Constants.SEMI_COLON) {
                trailer = true;
            } else if (!trailer) { 
                //don't read data after the trailer
                int charValue = HexUtils.getDec(buf[pos]);
                if (charValue != -1) {
                    readDigit = true;
                    result *= 16;
                    result += charValue;
                } else {
                    //we shouldn't allow invalid, non hex characters
                    //in the chunked header
                    return false;
                }
            }

            // Parsing the CRLF increments pos
            if (!eol) {
                pos++;
            }

        }

        if (!readDigit)
            return false;

        if (result == 0)
            endChunk = true;

        remaining = result;
        if (remaining < 0)
            return false;

        return true;

    }


    /**
     * Parse CRLF at end of chunk.
     * @deprecated  Use {@link #parseCRLF(boolean)}
     */
    @Deprecated
    protected boolean parseCRLF() throws IOException {
        parseCRLF(false);
        return true;
    }

    /**
     * Parse CRLF at end of chunk.
     *
     * @param   tolerant    Should tolerant parsing (LF and CRLF) be used? This
     *                      is recommended (RFC2616, section 19.3) for message
     *                      headers.
     */
    protected void parseCRLF(boolean tolerant) throws IOException {

        boolean eol = false;
        boolean crfound = false;

        while (!eol) {

            if (pos >= lastValid) {
                if (readBytes() <= 0)
                    throw new IOException("Invalid CRLF");
            }

            if (buf[pos] == Constants.CR) {
                if (crfound) throw new IOException("Invalid CRLF, two CR characters encountered.");
                crfound = true;
            } else if (buf[pos] == Constants.LF) {
                if (!tolerant && !crfound) {
                    throw new IOException("Invalid CRLF, no CR character encountered.");
                }
                eol = true;
            } else {
                throw new IOException("Invalid CRLF");
            }

            pos++;

        }
    }


    /**
     * Parse end chunk data.
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
        if (pos >= lastValid) {
            if (readBytes() <0)
                throw new EOFException("Unexpected end of stream whilst reading trailer headers for chunked request");
        }
    
        chr = buf[pos];
    
        // CRLF terminates the request
        if (chr == Constants.CR || chr == Constants.LF) {
            parseCRLF(false);
            return false;
        }
    
        // Mark the current buffer position
        int start = trailingHeaders.getEnd();
    
        //
        // Reading the header name
        // Header name is always US-ASCII
        //
    
        boolean colon = false;
        while (!colon) {
    
            // Read new bytes if needed
            if (pos >= lastValid) {
                if (readBytes() <0)
                    throw new EOFException("Unexpected end of stream whilst reading trailer headers for chunked request");
            }
    
            chr = buf[pos];
            if ((chr >= Constants.A) && (chr <= Constants.Z)) {
                chr = (byte) (chr - Constants.LC_OFFSET);
            }

            if (chr == Constants.COLON) {
                colon = true;
            } else {
                trailingHeaders.append(chr);
            }
    
            pos++;
    
        }
        MessageBytes headerValue = headers.addValue(trailingHeaders.getBytes(),
                start, trailingHeaders.getEnd() - start);
    
        // Mark the current buffer position
        start = trailingHeaders.getEnd();

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
                if (pos >= lastValid) {
                    if (readBytes() <0)
                        throw new EOFException("Unexpected end of stream whilst reading trailer headers for chunked request");
                }
    
                chr = buf[pos];
                if ((chr == Constants.SP) || (chr == Constants.HT)) {
                    pos++;
                } else {
                    space = false;
                }
    
            }
    
            // Reading bytes until the end of the line
            while (!eol) {
    
                // Read new bytes if needed
                if (pos >= lastValid) {
                    if (readBytes() <0)
                        throw new EOFException("Unexpected end of stream whilst reading trailer headers for chunked request");
                }
    
                chr = buf[pos];
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
                    pos++;
                }
            }
    
            // Checking the first character of the new line. If the character
            // is a LWS, then it's a multiline header
    
            // Read new bytes if needed
            if (pos >= lastValid) {
                if (readBytes() <0)
                    throw new EOFException("Unexpected end of stream whilst reading trailer headers for chunked request");
            }
    
            chr = buf[pos];
            if ((chr != Constants.SP) && (chr != Constants.HT)) {
                validLine = false;
            } else {
                eol = false;
                // Copying one extra space in the buffer (since there must
                // be at least one space inserted between the lines)
                trailingHeaders.append(chr);
            }
    
        }
    
        // Set the header value
        headerValue.setBytes(trailingHeaders.getBytes(), start,
                lastSignificantChar - start);
    
        return true;
    }
}

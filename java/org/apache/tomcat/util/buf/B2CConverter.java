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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.Locale;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * NIO based character decoder.
 */
public class B2CConverter {

    private static final Log log = LogFactory.getLog(B2CConverter.class);
    private static final StringManager sm = StringManager.getManager(B2CConverter.class);

    private static final CharsetCache charsetCache = new CharsetCache();


    // Protected so unit tests can use it
    protected static final int LEFTOVER_SIZE = 9;

    /**
     * Obtain the Charset for the given encoding
     *
     * @param enc The name of the encoding for the required charset
     *
     * @return The Charset corresponding to the requested encoding
     *
     * @throws UnsupportedEncodingException If the requested Charset is not available
     */
    public static Charset getCharset(String enc) throws UnsupportedEncodingException {

        // Encoding names should all be ASCII
        String lowerCaseEnc = enc.toLowerCase(Locale.ENGLISH);

        Charset charset = charsetCache.getCharset(lowerCaseEnc);

        if (charset == null) {
            // Pre-population of the cache means this must be invalid
            throw new UnsupportedEncodingException(sm.getString("b2cConverter.unknownEncoding", lowerCaseEnc));
        }
        return charset;
    }


    private final CharsetDecoder decoder;
    private ByteBuffer bb = null;
    private CharBuffer cb = null;

    /**
     * Leftover buffer used for incomplete characters.
     */
    private final ByteBuffer leftovers;

    public B2CConverter(Charset charset) {
        this(charset, false);
    }

    public B2CConverter(Charset charset, boolean replaceOnError) {
        byte[] left = new byte[LEFTOVER_SIZE];
        leftovers = ByteBuffer.wrap(left);
        CodingErrorAction action;
        if (replaceOnError) {
            action = CodingErrorAction.REPLACE;
        } else {
            action = CodingErrorAction.REPORT;
        }
        decoder = charset.newDecoder();
        decoder.onMalformedInput(action);
        decoder.onUnmappableCharacter(action);
    }

    /**
     * Reset the decoder state.
     */
    public void recycle() {
        try {
            decoder.reset();
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.warn(sm.getString("b2cConverter.decoderResetFail", decoder.charset()), t);
        }
        leftovers.position(0);
    }

    /**
     * Convert the given bytes to characters.
     *
     * @param bc         byte input
     * @param cc         char output
     * @param endOfInput Is this all of the available data
     *
     * @throws IOException If the conversion can not be completed
     */
    public void convert(ByteChunk bc, CharChunk cc, boolean endOfInput) throws IOException {
        if ((bb == null) || (bb.array() != bc.getBuffer())) {
            // Create a new byte buffer if anything changed
            bb = ByteBuffer.wrap(bc.getBuffer(), bc.getStart(), bc.getLength());
        } else {
            // Initialize the byte buffer
            bb.limit(bc.getEnd());
            bb.position(bc.getStart());
        }
        if ((cb == null) || (cb.array() != cc.getBuffer())) {
            // Create a new char buffer if anything changed
            cb = CharBuffer.wrap(cc.getBuffer(), cc.getEnd(), cc.getBuffer().length - cc.getEnd());
        } else {
            // Initialize the char buffer
            cb.limit(cc.getBuffer().length);
            cb.position(cc.getEnd());
        }
        CoderResult result = null;
        // Parse leftover if any are present
        if (leftovers.position() > 0) {
            int pos = cb.position();
            // Loop until one char is decoded or there is a decoder error
            do {
                leftovers.put(bc.subtractB());
                leftovers.flip();
                result = decoder.decode(leftovers, cb, endOfInput);
                leftovers.position(leftovers.limit());
                leftovers.limit(leftovers.array().length);
            } while (result.isUnderflow() && (cb.position() == pos));
            if (result.isError() || result.isMalformed()) {
                result.throwException();
            }
            bb.position(bc.getStart());
            leftovers.position(0);
        }
        // Do the decoding and get the results into the byte chunk and the char
        // chunk
        result = decoder.decode(bb, cb, endOfInput);
        if (result.isError() || result.isMalformed()) {
            result.throwException();
        } else if (result.isOverflow()) {
            // Propagate current positions to the byte chunk and char chunk, if
            // this continues the char buffer will get resized
            bc.setStart(bb.position());
            cc.setEnd(cb.position());
        } else if (result.isUnderflow()) {
            // Propagate current positions to the byte chunk and char chunk
            bc.setStart(bb.position());
            cc.setEnd(cb.position());
            // Put leftovers in the leftovers byte buffer
            if (bc.getLength() > 0) {
                leftovers.limit(leftovers.array().length);
                leftovers.position(bc.getLength());
                bc.subtract(leftovers.array(), 0, bc.getLength());
            }
        }
    }

    /**
     * Convert the given bytes to characters.
     *
     * @param bc         byte input
     * @param cc         char output
     * @param ic         byte input channel
     * @param endOfInput Is this all of the available data
     *
     * @throws IOException If the conversion can not be completed
     */
    public void convert(ByteBuffer bc, CharBuffer cc, ByteChunk.ByteInputChannel ic, boolean endOfInput)
            throws IOException {
        if ((bb == null) || (bb.array() != bc.array())) {
            // Create a new byte buffer if anything changed
            bb = ByteBuffer.wrap(bc.array(), bc.arrayOffset() + bc.position(), bc.remaining());
        } else {
            // Initialize the byte buffer
            bb.limit(bc.limit());
            bb.position(bc.position());
        }
        if ((cb == null) || (cb.array() != cc.array())) {
            // Create a new char buffer if anything changed
            cb = CharBuffer.wrap(cc.array(), cc.limit(), cc.capacity() - cc.limit());
        } else {
            // Initialize the char buffer
            cb.limit(cc.capacity());
            cb.position(cc.limit());
        }
        CoderResult result = null;
        // Parse leftover if any are present
        if (leftovers.position() > 0) {
            int pos = cb.position();
            // Loop until one char is decoded or there is a decoder error
            do {
                byte chr;
                if (bc.remaining() == 0) {
                    int n = ic.realReadBytes();
                    chr = n < 0 ? -1 : bc.get();
                } else {
                    chr = bc.get();
                }
                leftovers.put(chr);
                leftovers.flip();
                result = decoder.decode(leftovers, cb, endOfInput);
                leftovers.position(leftovers.limit());
                leftovers.limit(leftovers.array().length);
            } while (result.isUnderflow() && (cb.position() == pos));
            if (result.isError() || result.isMalformed()) {
                result.throwException();
            }
            bb.position(bc.position());
            leftovers.position(0);
        }
        // Do the decoding and get the results into the byte chunk and the char
        // chunk
        result = decoder.decode(bb, cb, endOfInput);
        if (result.isError() || result.isMalformed()) {
            result.throwException();
        } else if (result.isOverflow()) {
            // Propagate current positions to the byte chunk and char chunk, if
            // this continues the char buffer will get resized
            bc.position(bb.position());
            cc.limit(cb.position());
        } else if (result.isUnderflow()) {
            // Propagate current positions to the byte chunk and char chunk
            bc.position(bb.position());
            cc.limit(cb.position());
            // Put leftovers in the leftovers byte buffer
            if (bc.remaining() > 0) {
                leftovers.limit(leftovers.array().length);
                leftovers.position(bc.remaining());
                bc.get(leftovers.array(), 0, bc.remaining());
            }
        }
    }


    public Charset getCharset() {
        return decoder.charset();
    }
}

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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.tomcat.util.res.StringManager;

/**
 * NIO based character decoder.
 */
public class B2CConverter {

    private static final StringManager sm =
        StringManager.getManager(Constants.Package);

    private static final Map<String, Charset> encodingToCharsetCache =
            new HashMap<>();

    public static final Charset ISO_8859_1;
    public static final Charset UTF_8;

    static {
        for (Charset charset: Charset.availableCharsets().values()) {
            encodingToCharsetCache.put(
                    charset.name().toLowerCase(Locale.US), charset);
            for (String alias : charset.aliases()) {
                encodingToCharsetCache.put(
                        alias.toLowerCase(Locale.US), charset);
            }
        }
        Charset iso88591 = null;
        Charset utf8 = null;
        try {
            iso88591 = getCharset("ISO-8859-1");
            utf8 = getCharset("UTF-8");
        } catch (UnsupportedEncodingException e) {
            // Impossible. All JVMs must support these.
            e.printStackTrace();
        }
        ISO_8859_1 = iso88591;
        UTF_8 = utf8;
    }

    public static Charset getCharset(String enc)
            throws UnsupportedEncodingException {

        // Encoding names should all be ASCII
        String lowerCaseEnc = enc.toLowerCase(Locale.US);

        return getCharsetLower(lowerCaseEnc);
    }

    /**
     * Only to be used when it is known that the encoding name is in lower case.
     */
    public static Charset getCharsetLower(String lowerCaseEnc)
            throws UnsupportedEncodingException {

        Charset charset = encodingToCharsetCache.get(lowerCaseEnc);

        if (charset == null) {
            // Pre-population of the cache means this must be invalid
            throw new UnsupportedEncodingException(
                    sm.getString("b2cConverter.unknownEncoding", lowerCaseEnc));
        }
        return charset;
    }

    protected CharsetDecoder decoder = null;
    protected ByteBuffer bb = null;
    protected CharBuffer cb = null;

    /**
     * Leftover buffer used for incomplete characters.
     */
    protected ByteBuffer leftovers = null;

    public B2CConverter(String encoding) throws IOException {
        byte[] left = new byte[4];
        leftovers = ByteBuffer.wrap(left);
        decoder = getCharset(encoding).newDecoder();
    }

    /**
     * Reset the decoder state.
     */
    public void recycle() {
        decoder.reset();
        leftovers.position(0);
    }

    public boolean isUndeflow() {
        return (leftovers.position() > 0);
    }

    /**
     * Convert the given bytes to characters.
     *
     * @param bc byte input
     * @param cc char output
     */
    public void convert(ByteChunk bc, CharChunk cc)
            throws IOException {
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
            cb = CharBuffer.wrap(cc.getBuffer(), cc.getEnd(),
                    cc.getBuffer().length - cc.getEnd());
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
                leftovers.put(bc.substractB());
                leftovers.flip();
                result = decoder.decode(leftovers, cb, false);
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
        result = decoder.decode(bb, cb, false);
        if (result.isError() || result.isMalformed()) {
            result.throwException();
        } else if (result.isOverflow()) {
            // Propagate current positions to the byte chunk and char chunk, if
            // this continues the char buffer will get resized
            bc.setOffset(bb.position());
            cc.setEnd(cb.position());
        } else if (result.isUnderflow()) {
            // Propagate current positions to the byte chunk and char chunk
            bc.setOffset(bb.position());
            cc.setEnd(cb.position());
            // Put leftovers in the leftovers byte buffer
            if (bc.getLength() > 0) {
                leftovers.limit(leftovers.array().length);
                leftovers.position(bc.getLength());
                bc.substract(leftovers.array(), 0, bc.getLength());
            }
        }
    }
}

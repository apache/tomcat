/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.buf;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

import org.apache.tomcat.util.res.StringManager;

/**
 * This is a very basic ASN.1 parser that provides the limited functionality required by Tomcat. It is a long way from a
 * complete parser.
 * <p>
 * TODO: Consider extending/re-writing this parser and refactoring the SpnegoTokenFixer to use it.
 */
public class Asn1Parser {

    private static final StringManager sm = StringManager.getManager(Asn1Parser.class);

    public static final int TAG_INTEGER = 0x02;
    public static final int TAG_OCTET_STRING = 0x04;
    public static final int TAG_NULL = 0x05;
    public static final int TAG_OID = 0x06;
    public static final int TAG_UTF8STRING = 0x0C;
    public static final int TAG_SEQUENCE = 0x30;
    public static final int TAG_ATTRIBUTE_BASE = 0xA0;

    private final byte[] source;

    private int pos = 0;

    /*
     * This is somewhat of a hack to work around the simplified design of the parsing API that could result in ambiguous
     * results when nested sequences were optional. Checking the current nesting level of sequence tags enables a user
     * of the parser to determine if an optional sequence is present or not.
     *
     * See https://bz.apache.org/bugzilla/show_bug.cgi?id=67675#c24
     */
    private Deque<Integer> nestedSequenceEndPositions = new ArrayDeque<>();


    public Asn1Parser(byte[] source) {
        this.source = source;
    }


    public boolean eof() {
        return pos == source.length;
    }


    public int peekTag() {
        return source[pos] & 0xFF;
    }


    public void parseTagSequence() {
        /*
         * Check to see if the parser has completely parsed, based on end position for the sequence, any previous
         * sequences and remove those sequences from the sequence nesting tracking mechanism if they have been
         * completely parsed.
         */
        while (nestedSequenceEndPositions.size() > 0) {
            if (nestedSequenceEndPositions.peekLast().intValue() <= pos) {
                nestedSequenceEndPositions.pollLast();
            } else {
                break;
            }
        }
        // Add the new sequence to the sequence nesting tracking mechanism.
        parseTag(TAG_SEQUENCE);
        nestedSequenceEndPositions.addLast(Integer.valueOf(-1));
    }


    public void parseTag(int tag) {
        int value = next();
        if (value != tag) {
            throw new IllegalArgumentException(
                    sm.getString("asn1Parser.tagMismatch", Integer.valueOf(tag), Integer.valueOf(value)));
        }
    }


    public void parseFullLength() {
        int len = parseLength();
        if (len + pos != source.length) {
            throw new IllegalArgumentException(sm.getString("asn1Parser.lengthInvalid", Integer.valueOf(len),
                    Integer.valueOf(source.length - pos)));
        }
    }


    public int parseLength() {
        int len = next();
        if (len > 127) {
            int bytes = len - 128;
            len = 0;
            for (int i = 0; i < bytes; i++) {
                len = len << 8;
                len = len + next();
            }
        }
        /*
         * If this is the first length parsed after a sequence has been added to the sequence nesting tracking mechansim
         * it must be the length of the sequence so update the entry to record the end position of the sequence. Note
         * that position recorded is actually the start of the first element after the sequence ends.
         */
        if (nestedSequenceEndPositions.peekLast() != null && nestedSequenceEndPositions.peekLast().intValue() == -1) {
            nestedSequenceEndPositions.pollLast();
            nestedSequenceEndPositions.addLast(Integer.valueOf(pos + len));
        }
        return len;
    }


    public BigInteger parseInt() {
        byte[] val = parseBytes(TAG_INTEGER);
        return new BigInteger(val);
    }


    public byte[] parseOctetString() {
        return parseBytes(TAG_OCTET_STRING);
    }


    public void parseNull() {
        parseBytes(TAG_NULL);
    }


    public byte[] parseOIDAsBytes() {
        return parseBytes(TAG_OID);
    }


    public String parseUTF8String() {
        byte[] val = parseBytes(TAG_UTF8STRING);
        return new String(val, StandardCharsets.UTF_8);
    }


    public byte[] parseAttributeAsBytes(int index) {
        return parseBytes(TAG_ATTRIBUTE_BASE + index);
    }


    private byte[] parseBytes(int tag) {
        parseTag(tag);
        int len = parseLength();
        byte[] result = new byte[len];
        System.arraycopy(source, pos, result, 0, result.length);
        pos += result.length;
        return result;
    }


    public void parseBytes(byte[] dest) {
        System.arraycopy(source, pos, dest, 0, dest.length);
        pos += dest.length;
    }


    private int next() {
        return source[pos++] & 0xFF;
    }


    public int getNestedSequenceLevel() {
        return nestedSequenceEndPositions.size();
    }
}

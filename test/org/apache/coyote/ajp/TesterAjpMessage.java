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
package org.apache.coyote.ajp;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Extends {@link AjpMessage} to provide additional methods for reading from the message. TODO: See if it makes sense
 * for any/all of these methods to be transferred to AjpMessage
 */
public class TesterAjpMessage extends AjpMessage {

    private final List<Header> headers = new ArrayList<>();
    private final List<Attribute> attributes = new ArrayList<>();
    private transient Charset charset = StandardCharsets.UTF_8;


    public TesterAjpMessage(int packetSize) {
        super(packetSize);
    }

    public byte readByte() {
        return buf[pos++];
    }

    public int readInt() {
        int val = (buf[pos++] & 0xFF) << 8;
        val += buf[pos++] & 0xFF;
        return val;
    }

    public String readString() {
        int len = readInt();
        return readString(len);
    }

    public String readString(int len) {
        CharBuffer buf = getCharset().decode(ByteBuffer.wrap(this.buf, pos, len));
        pos += len;
        // Read end of string marker
        readByte();

        return new String(buf.array(), buf.arrayOffset(), buf.length());
    }

    public String readHeaderName() {
        byte b = readByte();
        if ((b & 0xFF) == 0xA0) {
            // Coded header
            return Constants.getResponseHeaderForCode(readByte() - 1);
        } else {
            int len = (b & 0xFF) << 8;
            len += getByte() & 0xFF;
            return readString(len);
        }
    }


    public void addHeader(int code, String value) {
        headers.add(new Header(code, value));
    }


    public void addHeader(String name, String value) {
        headers.add(new Header(name, value));
    }


    public void addAttribute(int code, String value) {
        attributes.add(new Attribute(code, value));
    }


    public void addAttribute(String name, String value) {
        attributes.add(new Attribute(name, value));
    }

    public Charset getCharset() {
        return charset;
    }

    public void setCharset(Charset charset) {
        this.charset = charset;
    }

    @Override
    public void end() {
        // Add the header count
        appendInt(headers.size());

        for (Header header : headers) {
            header.append(this);
        }

        for (Attribute attribute : attributes) {
            attribute.append(this);
        }

        // Terminator
        appendByte(0xFF);

        len = pos;
        int dLen = len - 4;

        buf[0] = (byte) 0x12;
        buf[1] = (byte) 0x34;
        buf[2] = (byte) ((dLen >>> 8) & 0xFF);
        buf[3] = (byte) (dLen & 0xFF);
    }


    @Override
    public void reset() {
        super.reset();
        headers.clear();
    }


    public void appendString(String string) {
        byte[] bytes = string.getBytes(StandardCharsets.ISO_8859_1);
        appendBytes(bytes, 0, bytes.length);
    }


    private static class Header {
        private final int code;
        private final String name;
        private final String value;

        Header(int code, String value) {
            this.code = code;
            this.name = null;
            this.value = value;
        }

        Header(String name, String value) {
            this.code = 0;
            this.name = name;
            this.value = value;
        }

        public void append(TesterAjpMessage message) {
            if (code == 0) {
                message.appendString(name);
            } else {
                message.appendInt(code);
            }
            message.appendString(value);
        }
    }


    private static class Attribute {
        private final int code;
        private final String name;
        private final String value;

        Attribute(int code, String value) {
            this.code = code;
            this.name = null;
            this.value = value;
        }

        Attribute(String name, String value) {
            this.code = 0;
            this.name = name;
            this.value = value;
        }

        public void append(TesterAjpMessage message) {
            if (code == 0) {
                message.appendByte(0x0A);
                message.appendString(name);
            } else {
                message.appendByte(code);
            }
            message.appendString(value);
        }
    }
}

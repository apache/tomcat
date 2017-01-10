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
package org.apache.coyote.http2;

import java.nio.ByteBuffer;

import org.apache.tomcat.util.res.StringManager;

final class Hpack {

    private static final StringManager sm = StringManager.getManager(Hpack.class);

    private static final byte LOWER_DIFF = 'a' - 'A';
    static final int DEFAULT_TABLE_SIZE = 4096;
    private static final int MAX_INTEGER_OCTETS = 8; //not sure what a good value for this is, but the spec says we need to provide an upper bound

    /**
     * table that contains powers of two,
     * used as both bitmask and to quickly calculate 2^n
     */
    private static final int[] PREFIX_TABLE;


    static final HeaderField[] STATIC_TABLE;
    static final int STATIC_TABLE_LENGTH;

    static {
        PREFIX_TABLE = new int[32];
        for (int i = 0; i < 32; ++i) {
            int n = 0;
            for (int j = 0; j < i; ++j) {
                n = n << 1;
                n |= 1;
            }
            PREFIX_TABLE[i] = n;
        }

        HeaderField[] fields = new HeaderField[62];
        //note that zero is not used
        fields[1] = new HeaderField(":authority", null);
        fields[2] = new HeaderField(":method", "GET");
        fields[3] = new HeaderField(":method", "POST");
        fields[4] = new HeaderField(":path", "/");
        fields[5] = new HeaderField(":path", "/index.html");
        fields[6] = new HeaderField(":scheme", "http");
        fields[7] = new HeaderField(":scheme", "https");
        fields[8] = new HeaderField(":status", "200");
        fields[9] = new HeaderField(":status", "204");
        fields[10] = new HeaderField(":status", "206");
        fields[11] = new HeaderField(":status", "304");
        fields[12] = new HeaderField(":status", "400");
        fields[13] = new HeaderField(":status", "404");
        fields[14] = new HeaderField(":status", "500");
        fields[15] = new HeaderField("accept-charset", null);
        fields[16] = new HeaderField("accept-encoding", "gzip, deflate");
        fields[17] = new HeaderField("accept-language", null);
        fields[18] = new HeaderField("accept-ranges", null);
        fields[19] = new HeaderField("accept", null);
        fields[20] = new HeaderField("access-control-allow-origin", null);
        fields[21] = new HeaderField("age", null);
        fields[22] = new HeaderField("allow", null);
        fields[23] = new HeaderField("authorization", null);
        fields[24] = new HeaderField("cache-control", null);
        fields[25] = new HeaderField("content-disposition", null);
        fields[26] = new HeaderField("content-encoding", null);
        fields[27] = new HeaderField("content-language", null);
        fields[28] = new HeaderField("content-length", null);
        fields[29] = new HeaderField("content-location", null);
        fields[30] = new HeaderField("content-range", null);
        fields[31] = new HeaderField("content-type", null);
        fields[32] = new HeaderField("cookie", null);
        fields[33] = new HeaderField("date", null);
        fields[34] = new HeaderField("etag", null);
        fields[35] = new HeaderField("expect", null);
        fields[36] = new HeaderField("expires", null);
        fields[37] = new HeaderField("from", null);
        fields[38] = new HeaderField("host", null);
        fields[39] = new HeaderField("if-match", null);
        fields[40] = new HeaderField("if-modified-since", null);
        fields[41] = new HeaderField("if-none-match", null);
        fields[42] = new HeaderField("if-range", null);
        fields[43] = new HeaderField("if-unmodified-since", null);
        fields[44] = new HeaderField("last-modified", null);
        fields[45] = new HeaderField("link", null);
        fields[46] = new HeaderField("location", null);
        fields[47] = new HeaderField("max-forwards", null);
        fields[48] = new HeaderField("proxy-authenticate", null);
        fields[49] = new HeaderField("proxy-authorization", null);
        fields[50] = new HeaderField("range", null);
        fields[51] = new HeaderField("referer", null);
        fields[52] = new HeaderField("refresh", null);
        fields[53] = new HeaderField("retry-after", null);
        fields[54] = new HeaderField("server", null);
        fields[55] = new HeaderField("set-cookie", null);
        fields[56] = new HeaderField("strict-transport-security", null);
        fields[57] = new HeaderField("transfer-encoding", null);
        fields[58] = new HeaderField("user-agent", null);
        fields[59] = new HeaderField("vary", null);
        fields[60] = new HeaderField("via", null);
        fields[61] = new HeaderField("www-authenticate", null);
        STATIC_TABLE = fields;
        STATIC_TABLE_LENGTH = STATIC_TABLE.length - 1;
    }

    static class HeaderField {
        final String name;
        final String value;
        final int size;

        HeaderField(String name, String value) {
            this.name = name;
            this.value = value;
            if (value != null) {
                this.size = 32 + name.length() + value.length();
            } else {
                this.size = -1;
            }
        }
    }

    /**
     * Decodes an integer in the HPACK prefix format. If the return value is -1
     * it means that there was not enough data in the buffer to complete the decoding
     * sequence.
     * <p/>
     * If this method returns -1 then the source buffer will not have been modified.
     *
     * @param source The buffer that contains the integer
     * @param n      The encoding prefix length
     * @return The encoded integer, or -1 if there was not enough data
     */
    static int decodeInteger(ByteBuffer source, int n) throws HpackException {
        if (source.remaining() == 0) {
            return -1;
        }
        int count = 1;
        int sp = source.position();
        int mask = PREFIX_TABLE[n];

        int i = mask & source.get();
        int b;
        if (i < PREFIX_TABLE[n]) {
            return i;
        } else {
            int m = 0;
            do {
                if(count++ > MAX_INTEGER_OCTETS) {
                    throw new HpackException(sm.getString("hpack.integerEncodedOverTooManyOctets",
                            Integer.valueOf(MAX_INTEGER_OCTETS)));
                }
                if (source.remaining() == 0) {
                    //we have run out of data
                    //reset
                    source.position(sp);
                    return -1;
                }
                b = source.get();
                i = i + (b & 127) * (PREFIX_TABLE[m] + 1);
                m += 7;
            } while ((b & 128) == 128);
        }
        return i;
    }

    /**
     * Encodes an integer in the HPACK prefix format.
     * <p/>
     * This method assumes that the buffer has already had the first 8-n bits filled.
     * As such it will modify the last byte that is already present in the buffer, and
     * potentially add more if required
     *
     * @param source The buffer that contains the integer
     * @param value  The integer to encode
     * @param n      The encoding prefix length
     */
    static void encodeInteger(ByteBuffer source, int value, int n) {
        int twoNminus1 = PREFIX_TABLE[n];
        int pos = source.position() - 1;
        if (value < twoNminus1) {
            source.put(pos, (byte) (source.get(pos) | value));
        } else {
            source.put(pos, (byte) (source.get(pos) | twoNminus1));
            value = value - twoNminus1;
            while (value >= 128) {
                source.put((byte) (value % 128 + 128));
                value = value / 128;
            }
            source.put((byte) value);
        }
    }


    static char toLower(char c) {
        if (c >= 'A' && c <= 'Z') {
            return (char) (c + LOWER_DIFF);
        }
        return c;
    }

    private Hpack() {}

}

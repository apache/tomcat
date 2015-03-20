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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.tomcat.util.res.StringManager;

public class HPackHuffman {

    protected static final StringManager sm = StringManager.getManager(HPackHuffman.class);

    private static final HuffmanCode[] HUFFMAN_CODES;

    /**
     * array based tree representation of a huffman code.
     * <p/>
     * the high two bytes corresponds to the tree node if the bit is set, and the low two bytes for if it is clear
     * if the high bit is set it is a terminal node, otherwise it contains the next node position.
     */
    private static final int[] DECODING_TABLE;

    private static final int LOW_TERMINAL_BIT = (0b10000000) << 8;
    private static final int HIGH_TERMINAL_BIT = (0b10000000) << 24;
    private static final int LOW_MASK = 0b0111111111111111;


    static {

        HuffmanCode[] codes = new HuffmanCode[257];

        codes[0] = new HuffmanCode(0x1ff8, 13);
        codes[1] = new HuffmanCode(0x7fffd8, 23);
        codes[2] = new HuffmanCode(0xfffffe2, 28);
        codes[3] = new HuffmanCode(0xfffffe3, 28);
        codes[4] = new HuffmanCode(0xfffffe4, 28);
        codes[5] = new HuffmanCode(0xfffffe5, 28);
        codes[6] = new HuffmanCode(0xfffffe6, 28);
        codes[7] = new HuffmanCode(0xfffffe7, 28);
        codes[8] = new HuffmanCode(0xfffffe8, 28);
        codes[9] = new HuffmanCode(0xffffea, 24);
        codes[10] = new HuffmanCode(0x3ffffffc, 30);
        codes[11] = new HuffmanCode(0xfffffe9, 28);
        codes[12] = new HuffmanCode(0xfffffea, 28);
        codes[13] = new HuffmanCode(0x3ffffffd, 30);
        codes[14] = new HuffmanCode(0xfffffeb, 28);
        codes[15] = new HuffmanCode(0xfffffec, 28);
        codes[16] = new HuffmanCode(0xfffffed, 28);
        codes[17] = new HuffmanCode(0xfffffee, 28);
        codes[18] = new HuffmanCode(0xfffffef, 28);
        codes[19] = new HuffmanCode(0xffffff0, 28);
        codes[20] = new HuffmanCode(0xffffff1, 28);
        codes[21] = new HuffmanCode(0xffffff2, 28);
        codes[22] = new HuffmanCode(0x3ffffffe, 30);
        codes[23] = new HuffmanCode(0xffffff3, 28);
        codes[24] = new HuffmanCode(0xffffff4, 28);
        codes[25] = new HuffmanCode(0xffffff5, 28);
        codes[26] = new HuffmanCode(0xffffff6, 28);
        codes[27] = new HuffmanCode(0xffffff7, 28);
        codes[28] = new HuffmanCode(0xffffff8, 28);
        codes[29] = new HuffmanCode(0xffffff9, 28);
        codes[30] = new HuffmanCode(0xffffffa, 28);
        codes[31] = new HuffmanCode(0xffffffb, 28);
        codes[32] = new HuffmanCode(0x14, 6);
        codes[33] = new HuffmanCode(0x3f8, 10);
        codes[34] = new HuffmanCode(0x3f9, 10);
        codes[35] = new HuffmanCode(0xffa, 12);
        codes[36] = new HuffmanCode(0x1ff9, 13);
        codes[37] = new HuffmanCode(0x15, 6);
        codes[38] = new HuffmanCode(0xf8, 8);
        codes[39] = new HuffmanCode(0x7fa, 11);
        codes[40] = new HuffmanCode(0x3fa, 10);
        codes[41] = new HuffmanCode(0x3fb, 10);
        codes[42] = new HuffmanCode(0xf9, 8);
        codes[43] = new HuffmanCode(0x7fb, 11);
        codes[44] = new HuffmanCode(0xfa, 8);
        codes[45] = new HuffmanCode(0x16, 6);
        codes[46] = new HuffmanCode(0x17, 6);
        codes[47] = new HuffmanCode(0x18, 6);
        codes[48] = new HuffmanCode(0x0, 5);
        codes[49] = new HuffmanCode(0x1, 5);
        codes[50] = new HuffmanCode(0x2, 5);
        codes[51] = new HuffmanCode(0x19, 6);
        codes[52] = new HuffmanCode(0x1a, 6);
        codes[53] = new HuffmanCode(0x1b, 6);
        codes[54] = new HuffmanCode(0x1c, 6);
        codes[55] = new HuffmanCode(0x1d, 6);
        codes[56] = new HuffmanCode(0x1e, 6);
        codes[57] = new HuffmanCode(0x1f, 6);
        codes[58] = new HuffmanCode(0x5c, 7);
        codes[59] = new HuffmanCode(0xfb, 8);
        codes[60] = new HuffmanCode(0x7ffc, 15);
        codes[61] = new HuffmanCode(0x20, 6);
        codes[62] = new HuffmanCode(0xffb, 12);
        codes[63] = new HuffmanCode(0x3fc, 10);
        codes[64] = new HuffmanCode(0x1ffa, 13);
        codes[65] = new HuffmanCode(0x21, 6);
        codes[66] = new HuffmanCode(0x5d, 7);
        codes[67] = new HuffmanCode(0x5e, 7);
        codes[68] = new HuffmanCode(0x5f, 7);
        codes[69] = new HuffmanCode(0x60, 7);
        codes[70] = new HuffmanCode(0x61, 7);
        codes[71] = new HuffmanCode(0x62, 7);
        codes[72] = new HuffmanCode(0x63, 7);
        codes[73] = new HuffmanCode(0x64, 7);
        codes[74] = new HuffmanCode(0x65, 7);
        codes[75] = new HuffmanCode(0x66, 7);
        codes[76] = new HuffmanCode(0x67, 7);
        codes[77] = new HuffmanCode(0x68, 7);
        codes[78] = new HuffmanCode(0x69, 7);
        codes[79] = new HuffmanCode(0x6a, 7);
        codes[80] = new HuffmanCode(0x6b, 7);
        codes[81] = new HuffmanCode(0x6c, 7);
        codes[82] = new HuffmanCode(0x6d, 7);
        codes[83] = new HuffmanCode(0x6e, 7);
        codes[84] = new HuffmanCode(0x6f, 7);
        codes[85] = new HuffmanCode(0x70, 7);
        codes[86] = new HuffmanCode(0x71, 7);
        codes[87] = new HuffmanCode(0x72, 7);
        codes[88] = new HuffmanCode(0xfc, 8);
        codes[89] = new HuffmanCode(0x73, 7);
        codes[90] = new HuffmanCode(0xfd, 8);
        codes[91] = new HuffmanCode(0x1ffb, 13);
        codes[92] = new HuffmanCode(0x7fff0, 19);
        codes[93] = new HuffmanCode(0x1ffc, 13);
        codes[94] = new HuffmanCode(0x3ffc, 14);
        codes[95] = new HuffmanCode(0x22, 6);
        codes[96] = new HuffmanCode(0x7ffd, 15);
        codes[97] = new HuffmanCode(0x3, 5);
        codes[98] = new HuffmanCode(0x23, 6);
        codes[99] = new HuffmanCode(0x4, 5);
        codes[100] = new HuffmanCode(0x24, 6);
        codes[101] = new HuffmanCode(0x5, 5);
        codes[102] = new HuffmanCode(0x25, 6);
        codes[103] = new HuffmanCode(0x26, 6);
        codes[104] = new HuffmanCode(0x27, 6);
        codes[105] = new HuffmanCode(0x6, 5);
        codes[106] = new HuffmanCode(0x74, 7);
        codes[107] = new HuffmanCode(0x75, 7);
        codes[108] = new HuffmanCode(0x28, 6);
        codes[109] = new HuffmanCode(0x29, 6);
        codes[110] = new HuffmanCode(0x2a, 6);
        codes[111] = new HuffmanCode(0x7, 5);
        codes[112] = new HuffmanCode(0x2b, 6);
        codes[113] = new HuffmanCode(0x76, 7);
        codes[114] = new HuffmanCode(0x2c, 6);
        codes[115] = new HuffmanCode(0x8, 5);
        codes[116] = new HuffmanCode(0x9, 5);
        codes[117] = new HuffmanCode(0x2d, 6);
        codes[118] = new HuffmanCode(0x77, 7);
        codes[119] = new HuffmanCode(0x78, 7);
        codes[120] = new HuffmanCode(0x79, 7);
        codes[121] = new HuffmanCode(0x7a, 7);
        codes[122] = new HuffmanCode(0x7b, 7);
        codes[123] = new HuffmanCode(0x7ffe, 15);
        codes[124] = new HuffmanCode(0x7fc, 11);
        codes[125] = new HuffmanCode(0x3ffd, 14);
        codes[126] = new HuffmanCode(0x1ffd, 13);
        codes[127] = new HuffmanCode(0xffffffc, 28);
        codes[128] = new HuffmanCode(0xfffe6, 20);
        codes[129] = new HuffmanCode(0x3fffd2, 22);
        codes[130] = new HuffmanCode(0xfffe7, 20);
        codes[131] = new HuffmanCode(0xfffe8, 20);
        codes[132] = new HuffmanCode(0x3fffd3, 22);
        codes[133] = new HuffmanCode(0x3fffd4, 22);
        codes[134] = new HuffmanCode(0x3fffd5, 22);
        codes[135] = new HuffmanCode(0x7fffd9, 23);
        codes[136] = new HuffmanCode(0x3fffd6, 22);
        codes[137] = new HuffmanCode(0x7fffda, 23);
        codes[138] = new HuffmanCode(0x7fffdb, 23);
        codes[139] = new HuffmanCode(0x7fffdc, 23);
        codes[140] = new HuffmanCode(0x7fffdd, 23);
        codes[141] = new HuffmanCode(0x7fffde, 23);
        codes[142] = new HuffmanCode(0xffffeb, 24);
        codes[143] = new HuffmanCode(0x7fffdf, 23);
        codes[144] = new HuffmanCode(0xffffec, 24);
        codes[145] = new HuffmanCode(0xffffed, 24);
        codes[146] = new HuffmanCode(0x3fffd7, 22);
        codes[147] = new HuffmanCode(0x7fffe0, 23);
        codes[148] = new HuffmanCode(0xffffee, 24);
        codes[149] = new HuffmanCode(0x7fffe1, 23);
        codes[150] = new HuffmanCode(0x7fffe2, 23);
        codes[151] = new HuffmanCode(0x7fffe3, 23);
        codes[152] = new HuffmanCode(0x7fffe4, 23);
        codes[153] = new HuffmanCode(0x1fffdc, 21);
        codes[154] = new HuffmanCode(0x3fffd8, 22);
        codes[155] = new HuffmanCode(0x7fffe5, 23);
        codes[156] = new HuffmanCode(0x3fffd9, 22);
        codes[157] = new HuffmanCode(0x7fffe6, 23);
        codes[158] = new HuffmanCode(0x7fffe7, 23);
        codes[159] = new HuffmanCode(0xffffef, 24);
        codes[160] = new HuffmanCode(0x3fffda, 22);
        codes[161] = new HuffmanCode(0x1fffdd, 21);
        codes[162] = new HuffmanCode(0xfffe9, 20);
        codes[163] = new HuffmanCode(0x3fffdb, 22);
        codes[164] = new HuffmanCode(0x3fffdc, 22);
        codes[165] = new HuffmanCode(0x7fffe8, 23);
        codes[166] = new HuffmanCode(0x7fffe9, 23);
        codes[167] = new HuffmanCode(0x1fffde, 21);
        codes[168] = new HuffmanCode(0x7fffea, 23);
        codes[169] = new HuffmanCode(0x3fffdd, 22);
        codes[170] = new HuffmanCode(0x3fffde, 22);
        codes[171] = new HuffmanCode(0xfffff0, 24);
        codes[172] = new HuffmanCode(0x1fffdf, 21);
        codes[173] = new HuffmanCode(0x3fffdf, 22);
        codes[174] = new HuffmanCode(0x7fffeb, 23);
        codes[175] = new HuffmanCode(0x7fffec, 23);
        codes[176] = new HuffmanCode(0x1fffe0, 21);
        codes[177] = new HuffmanCode(0x1fffe1, 21);
        codes[178] = new HuffmanCode(0x3fffe0, 22);
        codes[179] = new HuffmanCode(0x1fffe2, 21);
        codes[180] = new HuffmanCode(0x7fffed, 23);
        codes[181] = new HuffmanCode(0x3fffe1, 22);
        codes[182] = new HuffmanCode(0x7fffee, 23);
        codes[183] = new HuffmanCode(0x7fffef, 23);
        codes[184] = new HuffmanCode(0xfffea, 20);
        codes[185] = new HuffmanCode(0x3fffe2, 22);
        codes[186] = new HuffmanCode(0x3fffe3, 22);
        codes[187] = new HuffmanCode(0x3fffe4, 22);
        codes[188] = new HuffmanCode(0x7ffff0, 23);
        codes[189] = new HuffmanCode(0x3fffe5, 22);
        codes[190] = new HuffmanCode(0x3fffe6, 22);
        codes[191] = new HuffmanCode(0x7ffff1, 23);
        codes[192] = new HuffmanCode(0x3ffffe0, 26);
        codes[193] = new HuffmanCode(0x3ffffe1, 26);
        codes[194] = new HuffmanCode(0xfffeb, 20);
        codes[195] = new HuffmanCode(0x7fff1, 19);
        codes[196] = new HuffmanCode(0x3fffe7, 22);
        codes[197] = new HuffmanCode(0x7ffff2, 23);
        codes[198] = new HuffmanCode(0x3fffe8, 22);
        codes[199] = new HuffmanCode(0x1ffffec, 25);
        codes[200] = new HuffmanCode(0x3ffffe2, 26);
        codes[201] = new HuffmanCode(0x3ffffe3, 26);
        codes[202] = new HuffmanCode(0x3ffffe4, 26);
        codes[203] = new HuffmanCode(0x7ffffde, 27);
        codes[204] = new HuffmanCode(0x7ffffdf, 27);
        codes[205] = new HuffmanCode(0x3ffffe5, 26);
        codes[206] = new HuffmanCode(0xfffff1, 24);
        codes[207] = new HuffmanCode(0x1ffffed, 25);
        codes[208] = new HuffmanCode(0x7fff2, 19);
        codes[209] = new HuffmanCode(0x1fffe3, 21);
        codes[210] = new HuffmanCode(0x3ffffe6, 26);
        codes[211] = new HuffmanCode(0x7ffffe0, 27);
        codes[212] = new HuffmanCode(0x7ffffe1, 27);
        codes[213] = new HuffmanCode(0x3ffffe7, 26);
        codes[214] = new HuffmanCode(0x7ffffe2, 27);
        codes[215] = new HuffmanCode(0xfffff2, 24);
        codes[216] = new HuffmanCode(0x1fffe4, 21);
        codes[217] = new HuffmanCode(0x1fffe5, 21);
        codes[218] = new HuffmanCode(0x3ffffe8, 26);
        codes[219] = new HuffmanCode(0x3ffffe9, 26);
        codes[220] = new HuffmanCode(0xffffffd, 28);
        codes[221] = new HuffmanCode(0x7ffffe3, 27);
        codes[222] = new HuffmanCode(0x7ffffe4, 27);
        codes[223] = new HuffmanCode(0x7ffffe5, 27);
        codes[224] = new HuffmanCode(0xfffec, 20);
        codes[225] = new HuffmanCode(0xfffff3, 24);
        codes[226] = new HuffmanCode(0xfffed, 20);
        codes[227] = new HuffmanCode(0x1fffe6, 21);
        codes[228] = new HuffmanCode(0x3fffe9, 22);
        codes[229] = new HuffmanCode(0x1fffe7, 21);
        codes[230] = new HuffmanCode(0x1fffe8, 21);
        codes[231] = new HuffmanCode(0x7ffff3, 23);
        codes[232] = new HuffmanCode(0x3fffea, 22);
        codes[233] = new HuffmanCode(0x3fffeb, 22);
        codes[234] = new HuffmanCode(0x1ffffee, 25);
        codes[235] = new HuffmanCode(0x1ffffef, 25);
        codes[236] = new HuffmanCode(0xfffff4, 24);
        codes[237] = new HuffmanCode(0xfffff5, 24);
        codes[238] = new HuffmanCode(0x3ffffea, 26);
        codes[239] = new HuffmanCode(0x7ffff4, 23);
        codes[240] = new HuffmanCode(0x3ffffeb, 26);
        codes[241] = new HuffmanCode(0x7ffffe6, 27);
        codes[242] = new HuffmanCode(0x3ffffec, 26);
        codes[243] = new HuffmanCode(0x3ffffed, 26);
        codes[244] = new HuffmanCode(0x7ffffe7, 27);
        codes[245] = new HuffmanCode(0x7ffffe8, 27);
        codes[246] = new HuffmanCode(0x7ffffe9, 27);
        codes[247] = new HuffmanCode(0x7ffffea, 27);
        codes[248] = new HuffmanCode(0x7ffffeb, 27);
        codes[249] = new HuffmanCode(0xffffffe, 28);
        codes[250] = new HuffmanCode(0x7ffffec, 27);
        codes[251] = new HuffmanCode(0x7ffffed, 27);
        codes[252] = new HuffmanCode(0x7ffffee, 27);
        codes[253] = new HuffmanCode(0x7ffffef, 27);
        codes[254] = new HuffmanCode(0x7fffff0, 27);
        codes[255] = new HuffmanCode(0x3ffffee, 26);
        codes[256] = new HuffmanCode(0x3fffffff, 30);
        HUFFMAN_CODES = codes;

        //lengths determined by experimentation, just set it to something large then see how large it actually ends up
        int[] codingTree = new int[256];
        //the current position in the tree
        int pos = 0;
        int allocated = 1; //the next position to allocate to
        //map of the current state at a given position
        //only used while building the tree
        HuffmanCode[] currentCode = new HuffmanCode[256];
        currentCode[0] = new HuffmanCode(0, 0);

        final Set<HuffmanCode> allCodes = new HashSet<>();
        allCodes.addAll(Arrays.asList(HUFFMAN_CODES));

        while (!allCodes.isEmpty()) {
            int length = currentCode[pos].length;
            int code = currentCode[pos].value;

            int newLength = length + 1;
            HuffmanCode high = new HuffmanCode(code << 1 | 1, newLength);
            HuffmanCode low = new HuffmanCode(code << 1, newLength);
            int newVal = 0;
            boolean highTerminal = allCodes.remove(high);
            if (highTerminal) {
                //bah, linear search
                int i = 0;
                for (i = 0; i < codes.length; ++i) {
                    if (codes[i].equals(high)) {
                        break;
                    }
                }
                newVal = LOW_TERMINAL_BIT | i;
            } else {
                int highPos = allocated++;
                currentCode[highPos] = high;
                newVal = highPos;
            }
            newVal <<= 16;
            boolean lowTerminal = allCodes.remove(low);
            if (lowTerminal) {
                //bah, linear search
                int i = 0;
                for (i = 0; i < codes.length; ++i) {
                    if (codes[i].equals(low)) {
                        break;
                    }
                }
                newVal |= LOW_TERMINAL_BIT | i;
            } else {
                int lowPos = allocated++;
                currentCode[lowPos] = low;
                newVal |= lowPos;
            }
            codingTree[pos] = newVal;
            pos++;
        }
        DECODING_TABLE = codingTree;
    }

    /**
     * Decodes a huffman encoded string into the target StringBuilder. There must be enough space left in the buffer
     * for this method to succeed.
     *
     * @param data   The byte buffer
     * @param length The data length
     * @param target The target for the decompressed data
     */
    public static void decode(ByteBuffer data, int length, StringBuilder target) throws HpackException {
        assert data.remaining() >= length;
        int treePos = 0;
        boolean eosBits = true;
        for (int i = 0; i < length; ++i) {
            byte b = data.get();
            int bitPos = 7;
            while (bitPos >= 0) {
                int val = DECODING_TABLE[treePos];
                if (((1 << bitPos) & b) == 0) {
                    eosBits = false;
                    //bit not set, we want the lower part of the tree
                    if ((val & LOW_TERMINAL_BIT) == 0) {
                        treePos = val & LOW_MASK;
                    } else {
                        target.append((char) (val & LOW_MASK));
                        treePos = 0;
                        eosBits = true;
                    }
                } else {
                    //bit not set, we want the lower part of the tree
                    if ((val & HIGH_TERMINAL_BIT) == 0) {
                        treePos = (val >> 16) & LOW_MASK;
                    } else {
                        target.append((char) ((val >> 16) & LOW_MASK));
                        treePos = 0;
                        eosBits = true;
                    }
                }
                bitPos--;
            }
        }
        if (!eosBits) {
            throw new HpackException(sm.getString("hpackhuffman.huffmanEncodedHpackValueDidNotEndWithEOS"));
        }
    }


    /**
     * Encodes the given string into the buffer. If there is not enough space in the buffer, or the encoded
     * version is bigger than the original it will return false and not modify the buffers position
     *
     * @param buffer   The buffer to encode into
     * @param toEncode The string to encode
     * @param forceLowercase If the string should be encoded in lower case
     * @return true if encoding succeeded
     */
    public static boolean encode(ByteBuffer buffer, String toEncode, boolean forceLowercase) {
        if (buffer.remaining() <= toEncode.length()) {
            return false;
        }
        int start = buffer.position();
        //this sucks, but we need to put the length first
        //and we don't really have any option but to calculate it in advance to make sure we have left enough room
        //so we end up iterating twice
        int length = 0;
        for (int i = 0; i < toEncode.length(); ++i) {
            byte c = (byte) toEncode.charAt(i);
            if(forceLowercase) {
                c = Hpack.toLower(c);
            }
            HuffmanCode code = HUFFMAN_CODES[c];
            length += code.length;
        }
        int byteLength = length / 8 + (length % 8 == 0 ? 0 : 1);

        buffer.put((byte) (1 << 7));
        Hpack.encodeInteger(buffer, byteLength, 7);


        int bytePos = 0;
        byte currentBufferByte = 0;
        for (int i = 0; i < toEncode.length(); ++i) {
            byte c = (byte) toEncode.charAt(i);
            if(forceLowercase) {
                c = Hpack.toLower(c);
            }
            HuffmanCode code = HUFFMAN_CODES[c];
            if (code.length + bytePos <= 8) {
                //it fits in the current byte
                currentBufferByte |= ((code.value & 0xFF) << 8 - (code.length + bytePos));
                bytePos += code.length;
            } else {
                //it does not fit, it may need up to 4 bytes
                int val = code.value;
                int rem = code.length;
                while (rem > 0) {
                    if (!buffer.hasRemaining()) {
                        buffer.position(start);
                        return false;
                    }
                    int remainingInByte = 8 - bytePos;
                    if (rem > remainingInByte) {
                        currentBufferByte |= (val >> (rem - remainingInByte));
                    } else {
                        currentBufferByte |= (val << (remainingInByte - rem));
                    }
                    if (rem > remainingInByte) {
                        buffer.put(currentBufferByte);
                        currentBufferByte = 0;
                        bytePos = 0;
                    } else {
                        bytePos = rem;
                    }
                    rem -= remainingInByte;
                }
            }
            if (bytePos == 8) {
                if (!buffer.hasRemaining()) {
                    buffer.position(start);
                    return false;
                }
                buffer.put(currentBufferByte);
                currentBufferByte = 0;
                bytePos = 0;
            }
            if (buffer.position() - start > toEncode.length()) {
                //the encoded version is longer than the original
                //just return false
                buffer.position(start);
                return false;
            }
        }
        if (bytePos > 0) {
            //add the EOS bytes if we have not finished on a single byte
            if (!buffer.hasRemaining()) {
                buffer.position(start);
                return false;
            }
            buffer.put((byte) (currentBufferByte | ((0xFF) >> bytePos)));
        }
        return true;
    }

    protected static class HuffmanCode {
        /**
         * The value of the least significan't bits of the code
         */
        int value;
        /**
         * length of the code, in bits
         */
        int length;

        public HuffmanCode(int value, int length) {
            this.value = value;
            this.length = length;
        }

        public int getValue() {
            return value;
        }

        public int getLength() {
            return length;
        }

        @Override
        public boolean equals(Object o) {


            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            HuffmanCode that = (HuffmanCode) o;

            if (length != that.length) return false;
            if (value != that.value) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = value;
            result = 31 * result + length;
            return result;
        }

        @Override
        public String toString() {
            return "HuffmanCode{" +
                    "value=" + value +
                    ", length=" + length +
                    '}';
        }
    }
}

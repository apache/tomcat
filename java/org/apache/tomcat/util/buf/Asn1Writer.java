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

public class Asn1Writer {

    public static byte[] writeSequence(byte[]... components) {
        int len = 0;
        for (byte[] component : components) {
            len += component.length;
        }

        byte[] combined = new byte[len];
        int pos = 0;
        for (byte[] component : components) {
            System.arraycopy(component, 0, combined, pos, component.length);
            pos += component.length;
        }

        return writeTag((byte) 0x30, combined);
    }


    public static byte[] writeInteger(int value) {
        // How many bytes required to write the value? No more than 4 for int.
        int valueSize = 1;
        while ((value >> (valueSize * 8)) > 0) {
            valueSize++;
        }

        byte[] valueBytes = new byte[valueSize];
        int i = 0;
        while (valueSize > 0) {
            valueBytes[i] = (byte) (value >> (8 * (valueSize - 1)));
            value = value >> 8;
            valueSize--;
            i++;
        }

        return writeTag((byte) 0x02, valueBytes);
    }

    public static byte[] writeOctetString(byte[] data) {
        return writeTag((byte) 0x04, data);
    }

    public static byte[] writeTag(byte tagId, byte[] data) {
        int dataSize = data.length;
        // How many bytes to write the length?
        int lengthSize = 1;
        if (dataSize >127) {
            // 1 byte we have is now used to record how many bytes we need to
            // record a length > 127
            // Result is lengthSize = 1 + number of bytes to record length
            do {
                lengthSize++;
            }
            while ((dataSize >> (lengthSize * 8)) > 0);
        }

        // 1 for tag + lengthSize + dataSize
        byte[] result = new byte[1 + lengthSize + dataSize];
        result[0] = tagId;
        if (dataSize < 128) {
            result[1] = (byte) dataSize;
        } else {
            // lengthSize is 1 + number of bytes for length
            result[1] = (byte) (127 + lengthSize);
            int i = lengthSize;
            while (dataSize > 0) {
                result[i] = (byte) (dataSize & 0xFF);
                dataSize = dataSize >> 8;
                i--;
            }
        }

        System.arraycopy(data, 0, result, 1 + lengthSize, data.length);

        return result;
    }
}

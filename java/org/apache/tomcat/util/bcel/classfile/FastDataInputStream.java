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
package org.apache.tomcat.util.bcel.classfile;

import java.io.BufferedInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * A "FastDataInputStream" that get the numbers from buffer and from the target
 * directly instead of "DataInputStream".
 */
class FastDataInputStream extends BufferedInputStream implements DataInput {

    private final byte readBuffer[] = new byte[8];


    public FastDataInputStream(InputStream in, int size) {
        super(in, size);
    }


    @Override
    public final int read(byte b[]) throws IOException {
        return this.read(b, 0, b.length);
    }


    @Override
    public final void readFully(byte b[]) throws IOException {
        readFully(b, 0, b.length);
    }


    @Override
    public final void readFully(byte b[], int off, int len) throws IOException {
        if (len < 0)
            throw new IndexOutOfBoundsException();
        // Total read
        int sum = 0;
        // Current read
        int cur = 0;
        for(; sum < len; sum += cur){
            cur = read(b, off + sum, len - sum);
            if(cur < 0)
                throw new EOFException();
            sum += cur;
        }
    }


    @Override
    public boolean readBoolean() throws IOException {
        if (pos >= count) {
            fillNew();
            if (pos >= count)
                throw new EOFException();
        }
        int ch = this.buf[pos++] & 0xff;
        return (ch != 0);
    }


    @Override
    public final byte readByte() throws IOException {
        if (pos >= count) {
            fillNew();
            if (pos >= count)
                throw new EOFException();
        }
        return this.buf[pos++];
    }


    @Override
    public int readUnsignedByte() throws IOException {
        if (pos >= count) {
            fillNew();
            if (pos >= count)
                throw new EOFException();
        }
        int ch = this.buf[pos++] & 0xff;
        return ch;
    }


    @Override
    public final short readShort() throws IOException {
        if(pos + 1 >= count){
            fillNew();
            if(pos + 1 >= count) throw new EOFException();
        }
        int ch1 = this.buf[pos++] & 0xff;
        int ch2 = this.buf[pos++] & 0xff;
        return (short)((ch1 << 8) + ch2);
    }


    @Override
    public int readUnsignedShort() throws IOException{
        if(pos + 1 >= count) {
            fillNew();
            if(pos + 1 >= count) throw new EOFException();
        }

        int ch1 = this.buf[pos++] & 0xff;
        int ch2 = this.buf[pos++] & 0xff;
        return (ch1 << 8) + ch2;
    }


    @Override
    public final char readChar() throws IOException {
        if(pos + 1 >= count) {
            fillNew();
            if(pos + 1 >= count) throw new EOFException();
        }
        int ch1 = this.buf[pos++] & 0xff;
        int ch2 = this.buf[pos++] & 0xff;
        return (char)((ch1 << 8) + ch2);
    }


    @Override
    public final int readInt() throws IOException {
        if(pos + 3 >= count){
            fillNew();
            if(pos + 3 >= count) throw new EOFException();
        }
        int ch1 = this.buf[pos++] & 0xff;
        int ch2 = this.buf[pos++] & 0xff;
        int ch3 = this.buf[pos++] & 0xff;
        int ch4 = this.buf[pos++] & 0xff;
        return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + ch4);
    }


    @Override
    public final long readLong() throws IOException {
        readFully(readBuffer, 0, 8);
        return (((long)readBuffer[0] << 56) +
                ((long)(readBuffer[1] & 255) << 48) +
                ((long)(readBuffer[2] & 255) << 40) +
                ((long)(readBuffer[3] & 255) << 32) +
                ((long)(readBuffer[4] & 255) << 24) +
                ((readBuffer[5] & 255) << 16) +
                ((readBuffer[6] & 255) <<  8) +
                (readBuffer[7] & 255));
    }


    @Override
    public final float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }


    @Override
    public double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }


    @Override
    public final String readUTF() throws IOException {
        return DataInputStream.readUTF(this);
    }


    private void fillNew() throws IOException {
        int remain = 0;
        if(pos < count){
            remain = count - pos;
            System.arraycopy(buf, pos, buf, 0, remain);
        }
        pos = 0;
        int n = this.in.read(buf, remain, buf.length - remain);
        count = pos + n + remain;
    }


    @Override
    public int skipBytes(int n) throws IOException {
        int avail = count - pos;
        // Total Skipped
        int sum = 0;
        // Current skipped
        int cur = 0;
        if (avail <= 0) {
            // buffer is exhausted, read via stream directly
            while (sum < n && (cur = (int) in.skip(n - sum)) > 0) {
                sum += cur;
            }
            return sum;
        }
        // Data in the buffer is not enough
        if(n > avail){
            // Skip the data in buffer
            pos += avail;
            sum += avail;
            // Read via stream
            while (sum < n && (cur = (int) in.skip(n - sum)) > 0) {
                sum += cur;
            }
            return sum;
        }
        pos += n;
        return n;
    }


    @Override
    public String readLine() throws IOException {
        // Unimplemented
        throw new UnsupportedOperationException();
    }
}
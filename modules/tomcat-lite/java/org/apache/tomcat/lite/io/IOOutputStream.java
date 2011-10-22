/*
 */
package org.apache.tomcat.lite.io;

import java.io.CharConversionException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.MessageFormat;

/**
 * Same methods with ServletOutputStream.
 *
 * There is no restriction in using the Writer and InputStream at the
 * same time - the servlet layer will impose it for compat. You can also use
 * IOBuffer directly.
 *
 * If you mix stream and writer:
 *  - call BufferWriter.push() to make sure all chars are sent down
 *  - the BufferOutputStream doesn't cache any data, all goes to the
 *   IOBuffer.
 *  - flush() on BufferOutputStream and BufferWriter will send the data
 *  to the network and block until it gets to the socket ( so it can
 *  throw exception ).
 *  - You can also use non-blocking flush methods in IOBuffer, and a
 *  callback  if you want to know when the write was completed.
 *
 * @author Costin Manolache
 */
public class IOOutputStream extends OutputStream {

    IOBuffer bb;
    IOChannel ch;
    int bufferSize = 8 * 1024;

    int wSinceFlush = 0;

    public IOOutputStream(IOBuffer out, IOChannel httpMessage) {
        this.bb = out;
        ch = httpMessage;
    }

    public void recycle() {
        wSinceFlush = 0;
        bufferSize = 8 * 1024;
    }

    public void reset() {
        wSinceFlush = 0;
        bb.clear();
    }

    public int getWrittenSinceFlush() {
        return wSinceFlush;
    }


    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int size) {
        if (size > bufferSize) {
            bufferSize = size;
        }
    }

    private void updateSize(int cnt) throws IOException {
        wSinceFlush += cnt;
        if (wSinceFlush > bufferSize) {
            flush();
        }
    }

    @Override
    public void write(int b) throws IOException {
        bb.append((char) b);
        updateSize(1);
    }

    @Override
    public void write(byte data[]) throws IOException {
      write(data, 0, data.length);
    }

    @Override
    public void write(byte data[], int start, int len) throws IOException {
        bb.append(data, start, len);
        updateSize(len);
    }

    public void flush() throws IOException {
        if (ch != null) {
            ch.startSending();

            ch.waitFlush(Long.MAX_VALUE);
        }
        wSinceFlush = 0;
    }

    public void close() throws IOException {
        flush();
        bb.close();
    }


    public void write(ByteBuffer source) throws IOException {
        write(source.array(), source.position(), source.remaining());
        source.position(source.limit());
    }

    public void print(String s) throws IOException {
        if (s==null) s="null";
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt (i);

            //
            // XXX NOTE:  This is clearly incorrect for many strings,
            // but is the only consistent approach within the current
            // servlet framework.  It must suffice until servlet output
            // streams properly encode their output.
            //
            if ((c & 0xff00) != 0) {    // high order byte must be zero
                String errMsg = "Not ISO-8859-1";
                Object[] errArgs = new Object[1];
                errArgs[0] = new Character(c);
                errMsg = MessageFormat.format(errMsg, errArgs);
                throw new CharConversionException(errMsg);
            }
            write (c);
        }
    }


    public void print(boolean b) throws IOException {
        String msg;
        if (b) {
            msg = "true";
        } else {
            msg = "false";
        }
        print(msg);
    }

    public void print(char c) throws IOException {
        print(String.valueOf(c));
    }

    public void print(int i) throws IOException {
        print(String.valueOf(i));
    }

    public void print(long l) throws IOException {
        print(String.valueOf(l));
    }

    public void print(float f) throws IOException {
        print(String.valueOf(f));
    }

    public void print(double d) throws IOException {
        print(String.valueOf(d));
    }

    public void println() throws IOException {
        print("\r\n");
    }

    public void println(String s) throws IOException {
        print(s);
        println();
    }

    public void println(boolean b) throws IOException {
        print(b);
        println();
    }

    public void println(char c) throws IOException {
        print(c);
        println();
    }

    public void println(int i) throws IOException {
        print(i);
        println();
    }

    public void println(long l) throws IOException {
        print(l);
        println();
    }

    public void println(float f) throws IOException {
        print(f);
        println();
    }

    public void println(double d) throws IOException {
        print(d);
        println();
    }
}

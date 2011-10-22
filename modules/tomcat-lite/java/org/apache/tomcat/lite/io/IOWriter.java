/*
 */
package org.apache.tomcat.lite.io;

import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.HashMap;
import java.util.Map;

/**
 * Converts chars to bytes, and associated encoding.
 *
 * Replaces C2B from old tomcat.
 *
 * @author Costin Manolache
 */
public class IOWriter extends Writer {

    IOBuffer iob;
    Map<String, CharsetEncoder> encoders = new HashMap<String, CharsetEncoder>();
    CharsetEncoder encoder;

    private static boolean REUSE = true;
    String enc;
    private boolean closed;
    IOChannel ioCh;

    public IOWriter(IOChannel iob) {
        this.ioCh = iob;
        if (iob != null) {
            this.iob = iob.getOut();
        }
    }

    public void setEncoding(String charset) {
        if (charset == null) {
            charset = "UTF-8";
        }
        enc = charset;
        encoder = getEncoder(charset);
        if (encoder == null) {
            encoder = Charset.forName(charset).newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
            if (REUSE) {
                encoders.put(charset, encoder);
            }
        }
    }

    CharsetEncoder getEncoder(String charset) {
        if (charset == null) {
            charset = "UTF-8";
        }
        encoder = REUSE ? encoders.get(charset) : null;
        if (encoder == null) {
            encoder = Charset.forName(charset).newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
            if (REUSE) {
                encoders.put(charset, encoder);
            }
        }
        return encoder;
    }

    public String getEncoding() {
        return enc;
    }

    public void recycle() {
        if (encoder != null) {
            encoder.reset();
        }
        closed = false;
        enc = null;
    }


    private void checkClosed() throws IOException {
        if (closed) throw new IOException("closed");
    }

    @Override
    public void close() throws IOException {
        closed = true;
        // flush the buffer ?
        ByteBuffer out = iob.getWriteBuffer();
        encoder.flush(out);
        iob.releaseWriteBuffer(1);

        iob.close();
    }

    /**
     * Used if a bucket ends on a char boundary
     */
    CBuffer underFlowBuffer = CBuffer.newInstance();

    public void encode1(CBuffer cc,
            BBuffer bb, CharsetEncoder encoder, boolean eof) {
        CharBuffer c = cc.getNioBuffer();
        ByteBuffer b = bb.getWriteByteBuffer(c.remaining() * 2);
        encode1(c, b, encoder, eof);
        cc.returnNioBuffer(c);
        bb.limit(b.position());
    }

    /**
     *
     * @param cc
     * @return
     */
    public void encode1(CharBuffer c,
            ByteBuffer b, CharsetEncoder encoder, boolean eof) {

        // TODO: buffer growth in caller

        CoderResult res = encoder.encode(c, b, eof);
        if (res == CoderResult.OVERFLOW) {
            // bb is full - next call will get a larger buffer ( it
            // grows ) or maybe will be flushed.
        }
        if (res == CoderResult.UNDERFLOW && c.remaining() > 0 && !eof) {
            // TODO: if eof -> exception ?
            // cc has remaining chars - for example a surrogate start.
            underFlowBuffer.put(c);
        }

    }

    public void encodeAll(CBuffer cc,
            BBuffer bb, CharsetEncoder encoder, boolean eof) {
        while (cc.length() > 0) {
            encode1(cc, bb, encoder, eof);
        }
    }

    public void encodeAll(CBuffer cc,
            BBuffer bb, String cs) {
        encodeAll(cc, bb, getEncoder(cs), true);
    }

    @Override
    public void flush() throws IOException {
        if (ioCh != null) {
            ioCh.startSending();
        }
    }

    // TODO: use it for utf-8
    public static int char2utf8(byte[] ba, int off, char c, char c1) {
        int i = 0;
        if (c < 0x80) {
            ba[off++] = (byte) (c & 0xFF);
            return 1;
        } else if (c < 0x800)
        {
            ba[off++] = (byte) (0xC0 | c >> 6);
            ba[off++] = (byte) (0x80 | c & 0x3F);
            return 2;
        }
        else if (c < 0x10000)
        {
            ba[off++] = (byte) ((0xE0 | c >> 12));
            ba[off++] = (byte) ((0x80 | c >> 6 & 0x3F));
            ba[off++] = (byte) ((0x80 | c & 0x3F));
            return 3;
        }
        else if (c < 0x200000)
        {
            ba[off++] = (byte) ((0xF0 | c >> 18));
            ba[off++] = (byte) ((0x80 | c >> 12 & 0x3F));
            ba[off++] = (byte) ((0x80 | c >> 6 & 0x3F));
            ba[off++] = (byte) ((0x80 | c & 0x3F));
            return 4;
        }


        return i;
    }


    /**
     * Just send the chars to the byte[], without flushing down.
     *
     * @throws IOException
     */
    public void push() throws IOException {
        // we don't cache here.
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        checkClosed();
        CharBuffer cb = CharBuffer.wrap(cbuf, off, len);

        while (cb.remaining() > 0) {
            ByteBuffer wb = iob.getWriteBuffer();
            encode1(cb, wb, encoder, false);
            iob.releaseWriteBuffer(1);
        }
    }


}

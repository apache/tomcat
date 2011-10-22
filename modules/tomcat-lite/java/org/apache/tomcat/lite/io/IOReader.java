/*
 */
package org.apache.tomcat.lite.io;

import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;
import java.nio.charset.UnmappableCharacterException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Conversion from Bytes to Chars and support for decoding.
 *
 * Replaces tomcat B2CConverter with NIO equivalent. B2CConverter was a hack
 * (re)using an dummy InputStream backed by a ByteChunk.
 *
 * @author Costin Manolache
 */
public class IOReader extends Reader {

    IOBuffer iob;
    Map<String, CharsetDecoder> decoders = new HashMap<String, CharsetDecoder>();
    CharsetDecoder decoder;

    private static boolean REUSE = true;
    String enc;
    private boolean closed;
    public static final String DEFAULT_ENCODING = "ISO-8859-1";
    long timeout = 0;

    public IOReader(IOBuffer iob) {
        this.iob = iob;
    }

    public void setTimeout(long to) {
        timeout = to;
    }

    public void setEncoding(String charset) {
        enc = charset;
        if (enc == null) {
            enc = DEFAULT_ENCODING;
        }
        decoder = REUSE ? decoders.get(enc) : null;
        if (decoder == null) {
            decoder = Charset.forName(enc).newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
            if (REUSE) {
                decoders.put(enc, decoder);
            }
        }
    }

    public String getEncoding() {
        return enc;
    }

    public void recycle() {
        if (decoder != null) {
            decoder.reset();
        }
        closed = false;
        enc = null;
    }

    private void checkClosed() throws IOException {
        if (closed) throw new IOException("closed");
    }

    public boolean ready() {
        return iob.peekFirst() != null;
    }

    public int read(java.nio.CharBuffer target) throws IOException {
        int len = target.remaining();
        char[] cbuf = new char[len];
        int n = read(cbuf, 0, len);
        if (n > 0)
            target.put(cbuf, 0, n);
        return n;
    }

    public int read() throws IOException {
        char cb[] = new char[1];
        if (read(cb, 0, 1) == -1)
            return -1;
        else
            return cb[0];
    }

    @Override
    public void close() throws IOException {
        closed = true;
        iob.close();
    }

    /**
     * Used if a bucket ends on a char boundary
     */
    BBuffer underFlowBuffer = BBuffer.allocate(10);
    public static AtomicInteger underFlows = new AtomicInteger();

    /**
     * Decode all bytes - for example a URL or header.
     */
    public void decodeAll(BBucket bb, CBuffer c) {

        while (bb.hasRemaining()) {
            CharBuffer charBuffer = c.getAppendCharBuffer();
            CoderResult res = decode1(bb, charBuffer, true);
            c.returnAppendCharBuffer(charBuffer);
            if (res != CoderResult.OVERFLOW) {
                if (res == CoderResult.UNDERFLOW || bb.hasRemaining()) {
                    System.err.println("Ignored trailing bytes " + bb.remaining());
                }
                return;
            }
        }

    }

    /**
     * Do one decode pass.
     */
    public CoderResult decode1(BBucket bb, CharBuffer c, boolean eof) {
        ByteBuffer b = bb.getByteBuffer();

        if (underFlowBuffer.hasRemaining()) {
            // Need to get rid of the underFlow first
            for (int i = 0; i < 10; i++) {
                underFlowBuffer.put(b.get());
                bb.position(b.position());
                ByteBuffer ub = underFlowBuffer.getByteBuffer();
                CoderResult res = decoder.decode(ub, c, eof);
                if (! ub.hasRemaining()) {
                    // underflow resolved
                    break;
                }
                if (res == CoderResult.OVERFLOW) {
                    return res;
                }
            }
            if (underFlowBuffer.hasRemaining()) {
                throw new RuntimeException("Can't resolve underflow after " +
                		"10 bytes");
            }
        }

        CoderResult res = decoder.decode(b, c, eof);
        bb.position(b.position());

        if (res == CoderResult.UNDERFLOW && bb.hasRemaining()) {
            // b ends on a boundary
            underFlowBuffer.append(bb.array(), bb.position(), bb.remaining());
            bb.position(bb.limit());
        }
        return res;
    }

    @Override
    public int read(char[] cbuf, int offset, int length) throws IOException {
        checkClosed();
        if (length == 0) {
            return 0;
        }
        // we can either allocate a new CharBuffer or use a
        // static one and copy. Seems simpler this way - needs some
        // load test, but InputStreamReader seems to do the same.
        CharBuffer out = CharBuffer.wrap(cbuf, offset, length);

        CoderResult result = CoderResult.UNDERFLOW;

        BBucket bucket = iob.peekFirst();

        // Consume as much as possible without blocking
        while (result == CoderResult.UNDERFLOW) {
            // fill the buffer if needed
            if (bucket == null || ! bucket.hasRemaining()) {
                if (out.position() > offset) {
                    // we could return the result without blocking read
                    break;
                }
                bucket = null;
                while (bucket == null) {
                    iob.waitData(timeout);
                    bucket = iob.peekFirst();
                    if (bucket == null && iob.isClosedAndEmpty()) {
                        // EOF, we couldn't decode anything
                        break;
                    }
                }

                if (bucket == null) {
                    // eof
                    break;
                }
            }

            result = decode1(bucket, out, false);
        }

        if (result == CoderResult.UNDERFLOW && iob.isClosedAndEmpty()) {
            // Flush out any remaining data
            ByteBuffer bytes = bucket == null ?
                    underFlowBuffer.getByteBuffer() : bucket.getByteBuffer();
            result = decoder.decode(bytes, out, true);
            if (bucket == null) {
                underFlowBuffer.position(bytes.position());
            } else {
                bucket.position(bytes.position());
            }

            decoder.flush(out);
            decoder.reset();
        }

        if (result.isMalformed()) {
            throw new MalformedInputException(result.length());
        } else if (result.isUnmappable()) {
            throw new UnmappableCharacterException(result.length());
        }

        int rd = out.position() - offset;
        return rd == 0 ? -1 : rd;
    }
}

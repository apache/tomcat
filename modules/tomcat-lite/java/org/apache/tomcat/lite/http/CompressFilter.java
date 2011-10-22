/*
 */
package org.apache.tomcat.lite.http;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.tomcat.lite.io.BBucket;
import org.apache.tomcat.lite.io.IOBuffer;

import com.jcraft.jzlib.JZlib;
import com.jcraft.jzlib.ZStream;

public class CompressFilter {

    // Stream format: RFC1950
    // 1CMF 1FLG [4DICTID] DATA 4ADLER
    // CMF:  CINFO + CM (compression method). == x8
    // 78 == deflate with 32k window, i.e. max window

    // FLG: 2bit level, 1 bit FDICT, 5 bit FCHECK
    // Cx, Dx - no dict; Fx, Ex - dict ( for BEST_COMPRESSION )

    // Overhead: 6 bytes without dict, 10 with dict
    // data is encoded in blocks - there is a 'block end' marker and
    // 'last block'.

    // Flush: http://www.bolet.org/~pornin/deflate-flush.html
    // inflater needs about 9 bits
    // Z_SYNC_FLUSH: send empty block, 00 00 FF FF - seems recomended
    // PPP can skip this - there is a record format on top
    // Z_PARTIAL_FLUSH: standard for SSH

    ZStream cStream;
    ZStream dStream;

    byte[] dict;
    long dictId;

    public CompressFilter() {
    }

    public void recycle() {
        if (cStream == null) {
            return;
        }
        cStream.free();
        cStream = null;
        dStream.free();
        dStream = null;
    }

    public void init() {
        if (cStream != null) {
            return;
        }
        // can't call: cStream.free(); - will kill the adler, NPE
        cStream = new ZStream();
        // BEST_COMRESSION results in 256Kb per Deflate
        // 15 == default = 32k window
        cStream.deflateInit(JZlib.Z_BEST_SPEED, 10);

        dStream = new ZStream();
        dStream.inflateInit();

    }

    CompressFilter setDictionary(byte[] dict, long id) {
        init();
        this.dict = dict;
        this.dictId = id;
        cStream.deflateSetDictionary(dict, dict.length);
        return this;
    }

    void compress(IOBuffer in, IOBuffer out) throws IOException {
        init();
        BBucket bb = in.popFirst();

        while (bb != null) {
            // TODO: only the last one needs flush

            // TODO: size missmatches ?
            compress(bb, out, false);
            bb = in.popFirst();
        }

        if (in.isClosedAndEmpty()) {
            compressEnd(out);
        }
    }

    void compress(BBucket bb, IOBuffer out, boolean last) throws IOException {
        // TODO: only the last one needs flush

        // TODO: size missmatches ?
        init();
        int flush = JZlib.Z_PARTIAL_FLUSH;

        cStream.next_in = bb.array();
        cStream.next_in_index = bb.position();
        cStream.avail_in = bb.remaining();

        while (true) {
            ByteBuffer outB = out.getWriteBuffer();
            cStream.next_out = outB.array();
            cStream.next_out_index = outB.position();
            cStream.avail_out = outB.remaining();

            int err = cStream.deflate(flush);
            check(err, cStream);
            outB.position(cStream.next_out_index);
            out.releaseWriteBuffer(1);
            if (cStream.avail_out > 0 || cStream.avail_in == 0) {
                break;
            }
        }

        if (last) {
            compressEnd(out);
        }
    }

    private void compressEnd(IOBuffer out) throws IOException {
        while (true) {
            ByteBuffer outB = out.getWriteBuffer();
            cStream.next_out = outB.array();

            cStream.next_out_index = outB.position();
            cStream.avail_out = outB.remaining();
            cStream.deflate(JZlib.Z_FINISH);
            cStream.deflateEnd();

            outB.position(cStream.next_out_index);
            out.releaseWriteBuffer(1);
            if (cStream.avail_out > 0) {
                break;
            }
        }
    }

    void decompress(IOBuffer in, IOBuffer out) throws IOException {
        decompress(in, out, in.available());
    }

    void decompress(IOBuffer in, IOBuffer out, int len) throws IOException {
        init();
        BBucket bb = in.peekFirst();

        while (bb != null && len > 0) {
            dStream.next_in = bb.array();
            dStream.next_in_index = bb.position();
            int rd = Math.min(bb.remaining(), len);
            dStream.avail_in = rd;

            while (true) {
                ByteBuffer outB = out.getWriteBuffer();

                dStream.next_out = outB.array();
                dStream.next_out_index = outB.position();
                dStream.avail_out = outB.remaining();

                int err = dStream.inflate(JZlib.Z_SYNC_FLUSH);
                if (err == JZlib.Z_NEED_DICT && dict != null) {
                    // dStream.adler has the dict id - not sure how to check
                    if (dictId != 0 && dStream.adler != dictId) {
                        throw new IOException("Invalid dictionary");
                    }
                    if (dictId == 0) {
                        // initDict should pass a real dict id.
                        System.err.println("Missing dict ID: " + dStream.adler);
                    }
                    dStream.inflateSetDictionary(dict, dict.length);
                    err = dStream.inflate(JZlib.Z_SYNC_FLUSH);
                }
                outB.position(dStream.next_out_index);
                out.releaseWriteBuffer(1);

                if (err == JZlib.Z_STREAM_END) {
                    err = dStream.inflateEnd();
                    out.close();
                    check(err, dStream);
                    // move in back, not consummed
                    bb.position(dStream.next_in_index);
                    return;
                }
                check(err, dStream);

                if (dStream.avail_out > 0 || dStream.avail_in == 0) {
                    break;
                }
            }

            in.advance(rd); // consummed
            len -= rd;
            bb = in.peekFirst();
        }

        if (in.isClosedAndEmpty()) {
            // Shouldn't happen - input was not properly closed..
            // This should throw an exception, inflateEnd will check the CRC
            int err = dStream.inflateEnd();
            out.close();
            check(err, dStream);
            out.close();
        }
    }

    private void check(int err, ZStream stream) throws IOException {
        if (err != JZlib.Z_OK) {
            throw new IOException(err + " " + stream.msg);
        }
    }

    boolean isCompressed(HttpMessage http) {
        return false;
    }

    boolean needsCompression(HttpMessage in, HttpMessage out) {
        return false;
    }


}


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
package org.apache.tomcat.spdy;

import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import org.apache.tomcat.spdy.SpdyConnection.CompressSupport;

/**
 * Java6 Deflater with the workaround from tomcat http filters.
 */
class CompressDeflater6 implements CompressSupport {
    public static final long DICT_ID = 3751956914L;

    // Make sure to use the latest from net/spdy/spdy_framer.cc, not from spec
    private static final String SPDY_DICT_S =
              "optionsgetheadpostputdeletetraceacceptaccept-charsetaccept-encodingaccept-"
            + "languageauthorizationexpectfromhostif-modified-sinceif-matchif-none-matchi"
            + "f-rangeif-unmodifiedsincemax-forwardsproxy-authorizationrangerefererteuser"
            + "-agent10010120020120220320420520630030130230330430530630740040140240340440"
            + "5406407408409410411412413414415416417500501502503504505accept-rangesageeta"
            + "glocationproxy-authenticatepublicretry-afterservervarywarningwww-authentic"
            + "ateallowcontent-basecontent-encodingcache-controlconnectiondatetrailertran"
            + "sfer-encodingupgradeviawarningcontent-languagecontent-lengthcontent-locati"
            + "oncontent-md5content-rangecontent-typeetagexpireslast-modifiedset-cookieMo"
            + "ndayTuesdayWednesdayThursdayFridaySaturdaySundayJanFebMarAprMayJunJulAugSe"
            + "pOctNovDecchunkedtext/htmlimage/pngimage/jpgimage/gifapplication/xmlapplic"
            + "ation/xhtmltext/plainpublicmax-agecharset=iso-8859-1utf-8gzipdeflateHTTP/1"
            + ".1statusversionurl ";

    private static final byte[] SPDY_DICT = SPDY_DICT_S.getBytes();
    // C code uses this - not in the spec
    static {
        SPDY_DICT[SPDY_DICT.length - 1] = (byte) 0;
    }

    private Deflater zipOut;
    private Inflater zipIn;

    private byte[] decompressBuffer;
    private int decMax;

    private byte[] compressBuffer;

    public CompressDeflater6() {
    }

    public static CompressDeflater6 get() {
        // TODO: code to plug in v7-specific. It is marginally faster.
        return new CompressDeflater6();
    }

    public void recycle() {
        // TODO
    }

    public void init() {
        if (zipOut != null) {
            return;
        }
        try {
            // false is important - otherwise 'bad method'
            zipOut = new Deflater(Deflater.DEFAULT_COMPRESSION, false);
            zipOut.setDictionary(SPDY_DICT);
            zipIn = new Inflater();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public synchronized void compress(SpdyFrame frame, int start)
            throws IOException {
        init();

        if (compressBuffer == null) {
            compressBuffer = new byte[frame.data.length];
        }

        // last byte for flush ?
        zipOut.setInput(frame.data, start, frame.endData - start - 1);
        int coff = start;
        zipOut.setLevel(Deflater.DEFAULT_COMPRESSION);
        while (true) {
            int rd = zipOut.deflate(compressBuffer, coff, compressBuffer.length - coff);
            if (rd == 0) {
                // needsInput needs to be called - we're done with this frame ?
                zipOut.setInput(frame.data, frame.endData - 1, 1);
                zipOut.setLevel(Deflater.BEST_SPEED);
                while (true) {
                    rd = zipOut.deflate(compressBuffer, coff, compressBuffer.length - coff);
                    coff += rd;
                    if (rd == 0) {
                        break;
                    }
                    byte[] b = new byte[compressBuffer.length * 2];
                    System.arraycopy(compressBuffer, 0, b, 0, coff);
                    compressBuffer = b;
                }
                zipOut.setLevel(Deflater.DEFAULT_COMPRESSION);
                break;
            }
            coff += rd;
        }

        byte[] tmp = frame.data;
        frame.data = compressBuffer;
        compressBuffer = tmp;
        frame.endData = coff;
    }

    @Override
    public synchronized void decompress(SpdyFrame frame, int start)
            throws IOException {
        // stream id ( 4 ) + unused ( 2 )
        // nvCount is compressed in impl - spec is different
        init();


        if (decompressBuffer == null) {
            decompressBuffer = new byte[frame.data.length];
        }

        // will read from dec buffer to frame.data
        decMax = frame.endData;

        int off = start;

        zipIn.setInput(frame.data, start, decMax - start);

        while (true) {
            int rd;
            try {
                rd = zipIn.inflate(decompressBuffer, off, decompressBuffer.length - off);
                if (rd == 0 && zipIn.needsDictionary()) {
                    zipIn.setDictionary(SPDY_DICT);
                    continue;
                }
            } catch (DataFormatException e) {
                throw new IOException(e);
            }
            if (rd == 0) {
                break;
            }
            if (rd == -1) {
                break;
            }
            off += rd;
            byte[] b = new byte[decompressBuffer.length * 2];
            System.arraycopy(decompressBuffer, 0, b, 0, off);
            decompressBuffer = b;

        }
        byte[] tmpBuf = decompressBuffer;
        decompressBuffer = frame.data;
        frame.data = tmpBuf;

        frame.off = start;
        frame.endData = off;
    }
}

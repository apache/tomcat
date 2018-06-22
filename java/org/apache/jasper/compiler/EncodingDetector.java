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
package org.apache.jasper.compiler;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/*
 * The BoM detection is derived from:
 * https://svn.us.apache.org/viewvc/tomcat/trunk/java/org/apache/jasper/xmlparser/XMLEncodingDetector.java?annotate=1742248
 *
 * The prolog is always at least as specific as the BOM therefore any encoding
 * specified in the prolog should take priority over the BOM.
 */
class EncodingDetector {

    private static final XMLInputFactory XML_INPUT_FACTORY;
    static {
        XML_INPUT_FACTORY = XMLInputFactory.newInstance();
    }

    private final String encoding;
    private final int skip;
    private final boolean encodingSpecifiedInProlog;


    /*
     * TODO: Refactor Jasper InputStream creation and handling so the
     *       InputStream passed to this method is buffered and therefore saves
     *       on multiple opening and re-opening of the same file.
     */
    EncodingDetector(InputStream is) throws IOException {
        // Keep buffer size to a minimum here. BoM will be no more than 4 bytes
        // so that is the maximum we need to buffer
        BufferedInputStream bis = new BufferedInputStream(is, 4);
        bis.mark(4);

        BomResult bomResult = processBom(bis);

        // Reset the stream back to the start to allow the XML prolog detection
        // to work. Skip any BoM we discovered.
        bis.reset();
        for (int i = 0; i < bomResult.skip; i++) {
            bis.read();
        }

        String prologEncoding = getPrologEncoding(bis);
        if (prologEncoding == null) {
            encodingSpecifiedInProlog = false;
            encoding = bomResult.encoding;
        } else {
            encodingSpecifiedInProlog = true;
            encoding = prologEncoding;
        }
        skip = bomResult.skip;
    }


    String getEncoding() {
        return encoding;
    }


    int getSkip() {
        return skip;
    }


    boolean isEncodingSpecifiedInProlog() {
        return encodingSpecifiedInProlog;
    }


    private String getPrologEncoding(InputStream stream) {
        String encoding = null;
        try {
            XMLStreamReader xmlStreamReader = XML_INPUT_FACTORY.createXMLStreamReader(stream);
            encoding = xmlStreamReader.getCharacterEncodingScheme();
        } catch (XMLStreamException e) {
            // Ignore
        }
        return encoding;
    }


    private BomResult processBom(InputStream stream) {
        // Read first four bytes (or as many are available) and determine
        // encoding
        try {
            final byte[] b4 = new byte[4];
            int count = 0;
            int singleByteRead;
            while (count < 4) {
                singleByteRead = stream.read();
                if (singleByteRead == -1) {
                    break;
                }
                b4[count] = (byte) singleByteRead;
                count++;
            }

            return parseBom(b4, count);
        } catch (IOException ioe) {
            // Failed.
            return new BomResult("UTF-8", 0);
        }
    }


    private BomResult parseBom(byte[] b4, int count) {

        if (count < 2) {
            return new BomResult("UTF-8", 0);
        }

        // UTF-16, with BOM
        int b0 = b4[0] & 0xFF;
        int b1 = b4[1] & 0xFF;
        if (b0 == 0xFE && b1 == 0xFF) {
            // UTF-16, big-endian
            return new BomResult("UTF-16BE", 2);
        }
        if (b0 == 0xFF && b1 == 0xFE) {
            // UTF-16, little-endian
            return new BomResult("UTF-16LE", 2);
        }

        // default to UTF-8 if we don't have enough bytes to make a
        // good determination of the encoding
        if (count < 3) {
            return new BomResult("UTF-8", 0);
        }

        // UTF-8 with a BOM
        int b2 = b4[2] & 0xFF;
        if (b0 == 0xEF && b1 == 0xBB && b2 == 0xBF) {
            return new BomResult("UTF-8", 3);
        }

        // default to UTF-8 if we don't have enough bytes to make a
        // good determination of the encoding
        if (count < 4) {
            return new BomResult("UTF-8", 0);
        }

        // Other encodings. No BOM. Try and ID encoding.
        int b3 = b4[3] & 0xFF;
        if (b0 == 0x00 && b1 == 0x00 && b2 == 0x00 && b3 == 0x3C) {
            // UCS-4, big endian (1234)
            return new BomResult("ISO-10646-UCS-4", 0);
        }
        if (b0 == 0x3C && b1 == 0x00 && b2 == 0x00 && b3 == 0x00) {
            // UCS-4, little endian (4321)
            return new BomResult("ISO-10646-UCS-4", 0);
        }
        if (b0 == 0x00 && b1 == 0x00 && b2 == 0x3C && b3 == 0x00) {
            // UCS-4, unusual octet order (2143)
            // REVISIT: What should this be?
            return new BomResult("ISO-10646-UCS-4", 0);
        }
        if (b0 == 0x00 && b1 == 0x3C && b2 == 0x00 && b3 == 0x00) {
            // UCS-4, unusual octet order (3412)
            // REVISIT: What should this be?
            return new BomResult("ISO-10646-UCS-4", 0);
        }
        if (b0 == 0x00 && b1 == 0x3C && b2 == 0x00 && b3 == 0x3F) {
            // UTF-16, big-endian, no BOM
            // (or could turn out to be UCS-2...
            // REVISIT: What should this be?
            return new BomResult("UTF-16BE", 0);
        }
        if (b0 == 0x3C && b1 == 0x00 && b2 == 0x3F && b3 == 0x00) {
            // UTF-16, little-endian, no BOM
            // (or could turn out to be UCS-2...
            return new BomResult("UTF-16LE", 0);
        }
        if (b0 == 0x4C && b1 == 0x6F && b2 == 0xA7 && b3 == 0x94) {
            // EBCDIC
            // a la xerces1, return CP037 instead of EBCDIC here
            return new BomResult("CP037", 0);
        }

        // default encoding
        return new BomResult("UTF-8", 0);
    }


    private static class BomResult {

        public final String encoding;
        public final int skip;

        public BomResult(String encoding,  int skip) {
            this.encoding = encoding;
            this.skip = skip;
        }
    }
}

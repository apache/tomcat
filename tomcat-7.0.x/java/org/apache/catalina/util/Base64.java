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

package org.apache.catalina.util;

import java.io.UnsupportedEncodingException;

import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.CharChunk;

/**
 * This class provides encode/decode for RFC 2045 Base64 as defined by
 * RFC 2045, N. Freed and N. Borenstein.  <a
 * href="http://www.ietf.org/rfc/rfc2045.txt">RFC 2045</a>:
 * Multipurpose Internet Mail Extensions (MIME) Part One: Format of
 * Internet Message Bodies. Reference 1996
 *
 * @author Jeffrey Rodriguez
 * @version $Id$
 */
public final class  Base64
{
    private static final int  BASELENGTH         = 255;
    private static final int  LOOKUPLENGTH       = 64;
    private static final int  TWENTYFOURBITGROUP = 24;
    private static final int  EIGHTBIT           = 8;
    private static final int  SIXTEENBIT         = 16;
    private static final int  FOURBYTE           = 4;
    private static final int  SIGN               = -128;
    private static final byte PAD                = (byte) '=';
    private static byte [] base64Alphabet       = new byte[BASELENGTH];
    private static byte [] lookUpBase64Alphabet = new byte[LOOKUPLENGTH];

    static
    {
        for (int i = 0; i < BASELENGTH; i++ )
        {
            base64Alphabet[i] = -1;
        }
        for (int i = 'Z'; i >= 'A'; i--)
        {
            base64Alphabet[i] = (byte) (i - 'A');
        }
        for (int i = 'z'; i>= 'a'; i--)
        {
            base64Alphabet[i] = (byte) (i - 'a' + 26);
        }
        for (int i = '9'; i >= '0'; i--)
        {
            base64Alphabet[i] = (byte) (i - '0' + 52);
        }

        base64Alphabet['+']  = 62;
        base64Alphabet['/']  = 63;

        for (int i = 0; i <= 25; i++ )
            lookUpBase64Alphabet[i] = (byte) ('A' + i);

        for (int i = 26,  j = 0; i <= 51; i++, j++ )
            lookUpBase64Alphabet[i] = (byte) ('a'+ j);

        for (int i = 52,  j = 0; i <= 61; i++, j++ )
            lookUpBase64Alphabet[i] = (byte) ('0' + j);

        lookUpBase64Alphabet[62] = (byte) '+';
        lookUpBase64Alphabet[63] = (byte) '/';
    }

    /**
     * Encodes hex octets into Base64.
     *
     * @param binaryData Array containing binary data to encode.
     * @return Base64-encoded data.
     */
    public static String encode(byte[] binaryData) {
        int      lengthDataBits    = binaryData.length*EIGHTBIT;
        int      fewerThan24bits   = lengthDataBits%TWENTYFOURBITGROUP;
        int      numberTriplets    = lengthDataBits/TWENTYFOURBITGROUP;
        byte     encodedData[]     = null;


        if (fewerThan24bits != 0)
        {
            //data not divisible by 24 bit
            encodedData = new byte[ (numberTriplets + 1 ) * 4 ];
        }
        else
        {
            // 16 or 8 bit
            encodedData = new byte[ numberTriplets * 4 ];
        }

        byte k = 0, l = 0, b1 = 0, b2 = 0, b3 = 0;

        int encodedIndex = 0;
        int dataIndex   = 0;
        int i           = 0;
        //log.debug("number of triplets = " + numberTriplets);
        for ( i = 0; i<numberTriplets; i++ )
        {
            dataIndex = i*3;
            b1 = binaryData[dataIndex];
            b2 = binaryData[dataIndex + 1];
            b3 = binaryData[dataIndex + 2];

            //log.debug("b1= " + b1 +", b2= " + b2 + ", b3= " + b3);

            l  = (byte)(b2 & 0x0f);
            k  = (byte)(b1 & 0x03);

            encodedIndex = i * 4;
            byte val1 = ((b1 & SIGN)==0)?(byte)(b1>>2):(byte)((b1)>>2^0xc0);
            byte val2 = ((b2 & SIGN)==0)?(byte)(b2>>4):(byte)((b2)>>4^0xf0);
            byte val3 = ((b3 & SIGN)==0)?(byte)(b3>>6):(byte)((b3)>>6^0xfc);

            encodedData[encodedIndex]   = lookUpBase64Alphabet[ val1 ];
            //log.debug( "val2 = " + val2 );
            //log.debug( "k4   = " + (k<<4) );
            //log.debug(  "vak  = " + (val2 | (k<<4)) );
            encodedData[encodedIndex+1] =
                lookUpBase64Alphabet[ val2 | ( k<<4 )];
            encodedData[encodedIndex+2] =
                lookUpBase64Alphabet[ (l <<2 ) | val3 ];
            encodedData[encodedIndex+3] = lookUpBase64Alphabet[ b3 & 0x3f ];
        }

        // form integral number of 6-bit groups
        dataIndex    = i*3;
        encodedIndex = i*4;
        if (fewerThan24bits == EIGHTBIT )
        {
            b1 = binaryData[dataIndex];
            k = (byte) ( b1 &0x03 );
            //log.debug("b1=" + b1);
            //log.debug("b1<<2 = " + (b1>>2) );
            byte val1 = ((b1 & SIGN)==0)?(byte)(b1>>2):(byte)((b1)>>2^0xc0);
            encodedData[encodedIndex]     = lookUpBase64Alphabet[ val1 ];
            encodedData[encodedIndex + 1] = lookUpBase64Alphabet[ k<<4 ];
            encodedData[encodedIndex + 2] = PAD;
            encodedData[encodedIndex + 3] = PAD;
        }
        else if (fewerThan24bits == SIXTEENBIT)
        {

            b1 = binaryData[dataIndex];
            b2 = binaryData[dataIndex +1 ];
            l = (byte) (b2 & 0x0f);
            k = (byte) (b1 & 0x03);

            byte val1 = ((b1 & SIGN) == 0)?(byte)(b1>>2):(byte)((b1)>>2^0xc0);
            byte val2 = ((b2 & SIGN) == 0)?(byte)(b2>>4):(byte)((b2)>>4^0xf0);

            encodedData[encodedIndex]     = lookUpBase64Alphabet[ val1 ];
            encodedData[encodedIndex + 1] =
                lookUpBase64Alphabet[ val2 | ( k<<4 )];
            encodedData[encodedIndex + 2] = lookUpBase64Alphabet[ l<<2 ];
            encodedData[encodedIndex + 3] = PAD;
        }

        String result;
        try {
            result = new String(encodedData, "ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            // Should never happen but in case it does...
            result = new String(encodedData);
        }
        return result;
    }

    /**
     * Decodes Base64 data into octets
     *
     * @param base64DataBC Byte array containing Base64 data
     * @param decodedDataCC The decoded data chars
     */
    public static void decode( ByteChunk base64DataBC, CharChunk decodedDataCC)
    {
        int start = base64DataBC.getStart();
        int end = base64DataBC.getEnd();
        byte[] base64Data = base64DataBC.getBuffer();
        
        decodedDataCC.recycle();
        
        // handle the edge case, so we don't have to worry about it later
        if(end - start == 0) { return; }

        int      numberQuadruple    = (end - start)/FOURBYTE;
        byte     b1=0,b2=0,b3=0, b4=0, marker0=0, marker1=0;

        // Throw away anything not in base64Data

        int encodedIndex = 0;
        int dataIndex = start;
        char[] decodedData = null;
        
        {
            // this sizes the output array properly - rlw
            int lastData = end - start;
            // ignore the '=' padding
            while (base64Data[start+lastData-1] == PAD)
            {
                if (--lastData == 0)
                {
                    return;
                }
            }
            decodedDataCC.allocate(lastData - numberQuadruple, -1);
            decodedDataCC.setEnd(lastData - numberQuadruple);
            decodedData = decodedDataCC.getBuffer();
        }

        for (int i = 0; i < numberQuadruple; i++)
        {
            dataIndex = start + i * 4;
            marker0   = base64Data[dataIndex + 2];
            marker1   = base64Data[dataIndex + 3];

            b1 = base64Alphabet[base64Data[dataIndex]];
            b2 = base64Alphabet[base64Data[dataIndex +1]];

            if (marker0 != PAD && marker1 != PAD)
            {
                //No PAD e.g 3cQl
                b3 = base64Alphabet[ marker0 ];
                b4 = base64Alphabet[ marker1 ];

                decodedData[encodedIndex]   = (char) ((  b1 <<2 | b2>>4 ) & 0xff);
                decodedData[encodedIndex + 1] =
                    (char) ((((b2 & 0xf)<<4 ) |( (b3>>2) & 0xf) ) & 0xff);
                decodedData[encodedIndex + 2] = (char) (( b3<<6 | b4 ) & 0xff);
            }
            else if (marker0 == PAD)
            {
                //Two PAD e.g. 3c[Pad][Pad]
                decodedData[encodedIndex]   = (char) ((  b1 <<2 | b2>>4 ) & 0xff);
            }
            else if (marker1 == PAD)
            {
                //One PAD e.g. 3cQ[Pad]
                b3 = base64Alphabet[ marker0 ];

                decodedData[encodedIndex]   = (char) ((  b1 <<2 | b2>>4 ) & 0xff);
                decodedData[encodedIndex + 1] =
                    (char) ((((b2 & 0xf)<<4 ) |( (b3>>2) & 0xf) ) & 0xff);
            }
            encodedIndex += 3;
        }
    }


    /**
     * Decodes Base64 data into octets
     *
     * @param base64DataBC Byte array containing Base64 data
     * @param decodedDataBC The decoded data bytes
     */
    public static void decode( ByteChunk base64DataBC, ByteChunk decodedDataBC)
    {
        int start = base64DataBC.getStart();
        int end = base64DataBC.getEnd();
        byte[] base64Data = base64DataBC.getBuffer();
        
        decodedDataBC.recycle();
        
        // handle the edge case, so we don't have to worry about it later
        if(end - start == 0) { return; }

        int      numberQuadruple    = (end - start)/FOURBYTE;
        byte     b1=0,b2=0,b3=0, b4=0, marker0=0, marker1=0;

        // Throw away anything not in base64Data

        int encodedIndex = 0;
        int dataIndex = start;
        byte[] decodedData = null;
        
        {
            // this sizes the output array properly - rlw
            int lastData = end - start;
            // ignore the '=' padding
            while (base64Data[start+lastData-1] == PAD)
            {
                if (--lastData == 0)
                {
                    return;
                }
            }
            decodedDataBC.allocate(lastData - numberQuadruple, -1);
            decodedDataBC.setEnd(lastData - numberQuadruple);
            decodedData = decodedDataBC.getBuffer();
        }

        for (int i = 0; i < numberQuadruple; i++)
        {
            dataIndex = start + i * 4;
            marker0   = base64Data[dataIndex + 2];
            marker1   = base64Data[dataIndex + 3];

            b1 = base64Alphabet[base64Data[dataIndex]];
            b2 = base64Alphabet[base64Data[dataIndex +1]];

            if (marker0 != PAD && marker1 != PAD)
            {
                //No PAD e.g 3cQl
                b3 = base64Alphabet[ marker0 ];
                b4 = base64Alphabet[ marker1 ];

                decodedData[encodedIndex]   = (byte) ((  b1 <<2 | b2>>4 ) & 0xff);
                decodedData[encodedIndex + 1] =
                    (byte) ((((b2 & 0xf)<<4 ) |( (b3>>2) & 0xf) ) & 0xff);
                decodedData[encodedIndex + 2] = (byte) (( b3<<6 | b4 ) & 0xff);
            }
            else if (marker0 == PAD)
            {
                //Two PAD e.g. 3c[Pad][Pad]
                decodedData[encodedIndex]   = (byte) ((  b1 <<2 | b2>>4 ) & 0xff);
            }
            else if (marker1 == PAD)
            {
                //One PAD e.g. 3cQ[Pad]
                b3 = base64Alphabet[ marker0 ];

                decodedData[encodedIndex]   = (byte) ((  b1 <<2 | b2>>4 ) & 0xff);
                decodedData[encodedIndex + 1] =
                    (byte) ((((b2 & 0xf)<<4 ) |( (b3>>2) & 0xf) ) & 0xff);
            }
            encodedIndex += 3;
        }
    }


}

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

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.DatatypeConverter;

import org.junit.Test;

import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.CharChunk;

public class TesterBase64Performance {

    private static final int SIZE = 1000000;

    @Test
    public void testDecode() throws Exception {

        List<ByteChunk> inputs = new ArrayList<>(SIZE);
        List<ByteChunk> warmups = new ArrayList<>(SIZE);
        List<String> results = new ArrayList<>(SIZE);

        for (int i = 0; i < SIZE; i++) {
            String decodedString = "abc" + Integer.valueOf(i) +
                    ":abc" + Integer.valueOf(i);
            byte[] decodedBytes =
                    decodedString.getBytes(B2CConverter.ISO_8859_1);
            String encodedString =
                    DatatypeConverter.printBase64Binary(decodedBytes);
            byte[] encodedBytes =
                    encodedString.getBytes(B2CConverter.ISO_8859_1);

            ByteChunk bc = new ByteChunk(encodedBytes.length);
            bc.append(encodedBytes, 0, encodedBytes.length);

            inputs.add(bc);
        }


        for (int i = 0; i < SIZE; i++) {
            String decodedString = "zyx" + Integer.valueOf(i) +
                    ":zyx" + Integer.valueOf(i);
            byte[] decodedBytes =
                    decodedString.getBytes(B2CConverter.ISO_8859_1);
            String encodedString =
                    DatatypeConverter.printBase64Binary(decodedBytes);
            byte[] encodedBytes =
                    encodedString.getBytes(B2CConverter.ISO_8859_1);

            ByteChunk bc = new ByteChunk(encodedBytes.length);
            bc.append(encodedBytes, 0, encodedBytes.length);

            warmups.add(bc);
        }

        //Warm up
        for (ByteChunk bc : warmups) {
            CharChunk cc = new CharChunk(bc.getLength());
            Base64.decode(bc, cc);
            results.add(cc.toString());
        }
        results.clear();

        for (ByteChunk bc : warmups) {
            byte[] decodedBytes =
                    DatatypeConverter.parseBase64Binary(bc.toString());
            String decodedString =
                    new String(decodedBytes, B2CConverter.ISO_8859_1);
            results.add(decodedString);
        }
        results.clear();

        long startTomcat = System.currentTimeMillis();
        for (ByteChunk bc : inputs) {
            CharChunk cc = new CharChunk(bc.getLength());
            Base64.decode(bc, cc);
            results.add(cc.toString());
        }
        long stopTomcat =  System.currentTimeMillis();
        System.out.println("Tomcat: " + (stopTomcat - startTomcat) + " ms");

        results.clear();

        long startJre = System.currentTimeMillis();
        for (ByteChunk bc : inputs) {
            byte[] decodedBytes =
                    DatatypeConverter.parseBase64Binary(bc.toString());
            String decodedString =
                    new String(decodedBytes, B2CConverter.ISO_8859_1);
            results.add(decodedString);
        }
        long stopJre =  System.currentTimeMillis();
        System.out.println("JRE: " + (stopJre - startJre) + " ms");
    }
}

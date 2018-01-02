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
package org.apache.tomcat.util.net;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.net.TLSClientHelloExtractor.ExtractorResult;

public class TestTLSClientHelloExtractor {

    @Test
    public void testInputNeedRead01() throws IOException {
        ByteBuffer testInput = ByteBuffer.allocate(1024);
        doTestInputNeedRead(testInput);
    }


    @Test(expected=IOException.class)
    public void testInputMalformed01() throws IOException {
        ByteBuffer testInput = ByteBuffer.allocate(1024);

        // TLS handshake
        testInput.put((byte) 22);
        // TLS 1.0
        testInput.put((byte) 3);
        testInput.put((byte) 1);
        // Record length 0 (correct, but not legal)
        testInput.put((byte) 0);
        testInput.put((byte) 0);

        doTestInputNeedRead(testInput);
    }


    @Test(expected=IOException.class)
    public void testInputMalformed02() throws IOException {
        ByteBuffer testInput = ByteBuffer.allocate(1024);

        // TLS handshake
        testInput.put((byte) 22);
        // TLS 1.0
        testInput.put((byte) 3);
        testInput.put((byte) 1);
        // Record length 4
        testInput.put((byte) 0);
        testInput.put((byte) 4);
        // Type 1 (client hello)
        testInput.put((byte) 1);
        // Client hello size 0 (correct, but not legal)
        testInput.put((byte) 0);
        testInput.put((byte) 0);
        testInput.put((byte) 0);

        doTestInputNeedRead(testInput);
    }


    public void doTestInputMalformed(ByteBuffer input) throws IOException {
        TLSClientHelloExtractor extractor = new TLSClientHelloExtractor(input);
        // Expect this to fail
        extractor.getResult();
    }


    public void doTestInputNeedRead(ByteBuffer input) throws IOException {
        TLSClientHelloExtractor extractor = new TLSClientHelloExtractor(input);
        // Expect this to fail
        ExtractorResult result = extractor.getResult();
        Assert.assertEquals(ExtractorResult.NEED_READ, result);
    }
}

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
package org.apache.coyote.http11;

import org.junit.Assert;
import org.junit.Test;

import org.apache.coyote.ajp.AjpNioProtocol;

public class TestAbstractHttp11ProtocolResponseBufferSize {

    @Test
    public void testDefaultIsNotConfigured() {
        Http11NioProtocol protocol = new Http11NioProtocol();
        Assert.assertEquals(-1, protocol.getResponseBufferSize());
        Assert.assertEquals(-1, protocol.getDesiredBufferSize());
    }


    @Test
    public void testConfiguredSizeIsUsed() {
        Http11NioProtocol protocol = new Http11NioProtocol();
        protocol.setResponseBufferSize(16384);
        Assert.assertEquals(16384, protocol.getResponseBufferSize());
        Assert.assertEquals(16384, protocol.getDesiredBufferSize());
    }


    @Test
    public void testMinimumSizeIsAccepted() {
        Http11NioProtocol protocol = new Http11NioProtocol();
        protocol.setResponseBufferSize(1024);
        Assert.assertEquals(1024, protocol.getDesiredBufferSize());
    }


    @Test
    public void testInvalidSizeIsRejected() {
        /*
         * Sizes that are too small to hold the encoded form of one character would lose output, see
         * TestResponseBufferSize.testMultiByteCharactersAreNotLost()
         */
        for (int size : new int[] { 0, -1, 1, 2, 3, 4, 1023, -8192, Integer.MIN_VALUE }) {
            Http11NioProtocol protocol = new Http11NioProtocol();
            try {
                protocol.setResponseBufferSize(size);
                Assert.fail("Size [" + size + "] was accepted");
            } catch (IllegalArgumentException expected) {
                // Expected
            }
            // The previous value is kept
            Assert.assertEquals(-1, protocol.getResponseBufferSize());
        }
    }


    /*
     * The packet size of an AJP connector limits the buffer so the new attribute must not change it.
     */
    @Test
    public void testAjpDesiredBufferSizeIsUnchanged() {
        AjpNioProtocol protocol = new AjpNioProtocol();
        Assert.assertEquals(protocol.getPacketSize() - org.apache.coyote.ajp.Constants.SEND_HEAD_LEN,
                protocol.getDesiredBufferSize());
    }
}

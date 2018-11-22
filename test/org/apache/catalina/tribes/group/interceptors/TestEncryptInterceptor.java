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
package org.apache.catalina.tribes.group.interceptors;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsNot;

import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelInterceptor;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.group.ChannelInterceptorBase;
import org.apache.catalina.tribes.group.InterceptorPayload;
import org.apache.catalina.tribes.io.ChannelData;
import org.apache.catalina.tribes.io.XByteBuffer;

/**
 * Tests the EncryptInterceptor.
 *
 * Many of the tests in this class use strings as input and output, even
 * though the interceptor actually operates on byte arrays. This is done
 * for readability for the tests and their outputs.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestEncryptInterceptor {
    private static final String encryptionKey128 = "cafebabedeadbeefbeefcafecafebabe";
    private static final String encryptionKey192 = "cafebabedeadbeefbeefcafecafebabedeadbeefbeefcafe";
    private static final String encryptionKey256 = "cafebabedeadbeefcafebabedeadbeefcafebabedeadbeefcafebabedeadbeef";

    EncryptInterceptor src;
    EncryptInterceptor dest;

    @Before
    public void setup() {
        src = new EncryptInterceptor();
        src.setEncryptionKey(encryptionKey128);

        dest = new EncryptInterceptor();
        dest.setEncryptionKey(encryptionKey128);

        src.setNext(new PipedInterceptor(dest));
        dest.setPrevious(new ValueCaptureInterceptor());
    }

    @Test
    public void testBasic() throws Exception {
        src.start(Channel.SND_TX_SEQ);
        dest.start(Channel.SND_TX_SEQ);

        String testInput = "The quick brown fox jumps over the lazy dog.";

        Assert.assertEquals("Basic roundtrip failed",
                     testInput,
                     roundTrip(testInput, src, dest));
    }

    @Test
    public void testMultipleMessages() throws Exception {
        src.start(Channel.SND_TX_SEQ);
        dest.start(Channel.SND_TX_SEQ);

        String testInput = "The quick brown fox jumps over the lazy dog.";

        Assert.assertEquals("Basic roundtrip failed",
                     testInput,
                     roundTrip(testInput, src, dest));

        Assert.assertEquals("Second roundtrip failed",
                testInput,
                roundTrip(testInput, src, dest));

        Assert.assertEquals("Third roundtrip failed",
                testInput,
                roundTrip(testInput, src, dest));

        Assert.assertEquals("Fourth roundtrip failed",
                testInput,
                roundTrip(testInput, src, dest));

        Assert.assertEquals("Fifth roundtrip failed",
                testInput,
                roundTrip(testInput, src, dest));
    }

    @Test
    public void testTinyPayload() throws Exception {
        src.start(Channel.SND_TX_SEQ);
        dest.start(Channel.SND_TX_SEQ);

        String testInput = "x";

        Assert.assertEquals("Tiny payload roundtrip failed",
                     testInput,
                     roundTrip(testInput, src, dest));
    }

    @Test
    public void testLargePayload() throws Exception {
        src.start(Channel.SND_TX_SEQ);
        dest.start(Channel.SND_TX_SEQ);

        byte[] bytes = new byte[1024*1024];

        Assert.assertArrayEquals("Huge payload roundtrip failed",
                          bytes,
                          roundTrip(bytes, src, dest));
    }

    @Test
    @Ignore("Too big for default settings. Breaks Gump, Eclipse, ...")
    public void testHugePayload() throws Exception {
        src.start(Channel.SND_TX_SEQ);
        dest.start(Channel.SND_TX_SEQ);

        byte[] bytes = new byte[1024*1024*1024];

        Assert.assertArrayEquals("Huge payload roundtrip failed",
                          bytes,
                          roundTrip(bytes, src, dest));
    }

    @Test
    public void testCustomProvider() throws Exception {
        src.setProviderName("SunJCE"); // Explicitly set the provider name
        dest.setProviderName("SunJCE");
        src.start(Channel.SND_TX_SEQ);
        dest.start(Channel.SND_TX_SEQ);

        String testInput = "The quick brown fox jumps over the lazy dog.";

        Assert.assertEquals("Failed to set custom provider name",
                     testInput,
                     roundTrip(testInput, src, dest));
    }

    @Test
    public void test192BitKey() throws Exception {
        src.setEncryptionKey(encryptionKey192);
        dest.setEncryptionKey(encryptionKey192);
        src.start(Channel.SND_TX_SEQ);
        dest.start(Channel.SND_TX_SEQ);

        String testInput = "The quick brown fox jumps over the lazy dog.";

        Assert.assertEquals("Failed to set custom provider name",
                     testInput,
                     roundTrip(testInput, src, dest));
    }

    @Test
    public void test256BitKey() throws Exception {
        src.setEncryptionKey(encryptionKey256);
        dest.setEncryptionKey(encryptionKey256);
        src.start(Channel.SND_TX_SEQ);
        dest.start(Channel.SND_TX_SEQ);

        String testInput = "The quick brown fox jumps over the lazy dog.";

        Assert.assertEquals("Failed to set custom provider name",
                     testInput,
                     roundTrip(testInput, src, dest));
    }

    /**
     * Actually go through the interceptor's send/receive message methods.
     */
    private static String roundTrip(String input, EncryptInterceptor src, EncryptInterceptor dest) throws Exception {
        byte[] bytes = input.getBytes("UTF-8");

        bytes = roundTrip(bytes, src, dest);

        return new String(bytes, "UTF-8");
    }

    /**
     * Actually go through the interceptor's send/receive message methods.
     */
    private static byte[] roundTrip(byte[] input, EncryptInterceptor src, EncryptInterceptor dest) throws Exception {
        ChannelData msg = new ChannelData(false);
        msg.setMessage(new XByteBuffer(input, false));
        src.sendMessage(null, msg, null);

        return ((ValueCaptureInterceptor)dest.getPrevious()).getValue();
    }

    @Test
    @Ignore("ECB mode isn't because it's insecure")
    public void testECB() throws Exception {
        src.setEncryptionAlgorithm("AES/ECB/PKCS5Padding");
        src.start(Channel.SND_TX_SEQ);
        dest.setEncryptionAlgorithm("AES/ECB/PKCS5Padding");
        dest.start(Channel.SND_TX_SEQ);

        String testInput = "The quick brown fox jumps over the lazy dog.";

        Assert.assertEquals("Failed in ECB mode",
                     testInput,
                     roundTrip(testInput, src, dest));
    }

    @Test
    public void testOFB() throws Exception {
        src.setEncryptionAlgorithm("AES/OFB/PKCS5Padding");
        src.start(Channel.SND_TX_SEQ);
        dest.setEncryptionAlgorithm("AES/OFB/PKCS5Padding");
        dest.start(Channel.SND_TX_SEQ);

        String testInput = "The quick brown fox jumps over the lazy dog.";

        Assert.assertEquals("Failed in OFB mode",
                     testInput,
                     roundTrip(testInput, src, dest));
    }

    @Test
    public void testCFB() throws Exception {
        src.setEncryptionAlgorithm("AES/CFB/PKCS5Padding");
        src.start(Channel.SND_TX_SEQ);
        dest.setEncryptionAlgorithm("AES/CFB/PKCS5Padding");
        dest.start(Channel.SND_TX_SEQ);

        String testInput = "The quick brown fox jumps over the lazy dog.";

        Assert.assertEquals("Failed in CFB mode",
                     testInput,
                     roundTrip(testInput, src, dest));
    }

    @Test
    @Ignore("GCM mode is unsupported because it requires a custom initialization vector")
    public void testGCM() throws Exception {
        src.setEncryptionAlgorithm("AES/GCM/PKCS5Padding");
        src.start(Channel.SND_TX_SEQ);
        dest.setEncryptionAlgorithm("AES/GCM/PKCS5Padding");
        dest.start(Channel.SND_TX_SEQ);

        String testInput = "The quick brown fox jumps over the lazy dog.";

        Assert.assertEquals("Failed in GCM mode",
                     testInput,
                     roundTrip(testInput, src, dest));
    }

    @Test
    public void testViaFile() throws Exception {
        src.start(Channel.SND_TX_SEQ);
        src.setNext(new ValueCaptureInterceptor());

        String testInput = "The quick brown fox jumps over the lazy dog.";

        ChannelData msg = new ChannelData(false);
        msg.setMessage(new XByteBuffer(testInput.getBytes("UTF-8"), false));
        src.sendMessage(null, msg, null);

        byte[] bytes = ((ValueCaptureInterceptor)src.getNext()).getValue();

        try (FileOutputStream out = new FileOutputStream("message.bin")) {
            out.write(bytes);
        }

        dest.start(Channel.SND_TX_SEQ);

        bytes = new byte[8192];
        int read;

        try (FileInputStream in = new FileInputStream("message.bin")) {
            read = in.read(bytes);
        }

        msg = new ChannelData(false);
        XByteBuffer xbb = new XByteBuffer(read, false);
        xbb.append(bytes, 0, read);
        msg.setMessage(xbb);

        dest.messageReceived(msg);
    }

    @Test
    public void testMessageUniqueness() throws Exception {
        src.start(Channel.SND_TX_SEQ);
        src.setNext(new ValueCaptureInterceptor());

        String testInput = "The quick brown fox jumps over the lazy dog.";

        ChannelData msg = new ChannelData(false);
        msg.setMessage(new XByteBuffer(testInput.getBytes("UTF-8"), false));
        src.sendMessage(null, msg, null);

        byte[] cipherText1 = ((ValueCaptureInterceptor)src.getNext()).getValue();

        msg.setMessage(new XByteBuffer(testInput.getBytes("UTF-8"), false));
        src.sendMessage(null, msg, null);

        byte[] cipherText2 = ((ValueCaptureInterceptor)src.getNext()).getValue();

        Assert.assertThat("Two identical cleartexts encrypt to the same ciphertext",
                cipherText1, IsNot.not(IsEqual.equalTo(cipherText2)));
    }

    @Test
    public void testPickup() throws Exception {
        File file = new File("message.bin");
        if(!file.exists()) {
            System.err.println("File message.bin does not exist. Skipping test.");
            return;
        }

        dest.start(Channel.SND_TX_SEQ);

        byte[] bytes = new byte[8192];
        int read;

        try (FileInputStream in = new FileInputStream("message.bin")) {
            read = in.read(bytes);
        }

        ChannelData msg = new ChannelData(false);
        XByteBuffer xbb = new XByteBuffer(read, false);
        xbb.append(bytes, 0, read);
        msg.setMessage(xbb);

        dest.messageReceived(msg);
    }

    /**
     * Interceptor that delivers directly to a destination.
     */
    private static class PipedInterceptor
        extends ChannelInterceptorBase
    {
        private ChannelInterceptor dest;

        public PipedInterceptor(ChannelInterceptor dest) {
            if(null == dest)
                throw new IllegalArgumentException("Destination must not be null");

            this.dest = dest;
        }

        @Override
        public void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload)
                throws ChannelException {
            dest.messageReceived(msg);
        }
    }

    /**
     * Interceptor that simply captures the latest message sent to or received by it.
     */
    private static class ValueCaptureInterceptor
        extends ChannelInterceptorBase
    {
        private byte[] value;

        @Override
        public void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload)
                throws ChannelException {
            value = msg.getMessage().getBytes();
        }

        @Override
        public void messageReceived(ChannelMessage msg) {
            value = msg.getMessage().getBytes();
        }

        public byte[] getValue() {
            return value;
        }
    }
}

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
import java.util.Collection;

import javax.crypto.Cipher;

import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsNot;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelException;
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
public class TestEncryptInterceptor extends EncryptionInterceptorBaseTest {

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
        Assume.assumeTrue("Skipping test192BitKey because the JVM does not support it",
                192 <= Cipher.getMaxAllowedKeyLength("AES"));

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
        Assume.assumeTrue("Skipping test256BitKey because the JVM does not support it",
                256 <= Cipher.getMaxAllowedKeyLength("AES"));

        src.setEncryptionKey(encryptionKey256);
        dest.setEncryptionKey(encryptionKey256);
        src.start(Channel.SND_TX_SEQ);
        dest.start(Channel.SND_TX_SEQ);

        String testInput = "The quick brown fox jumps over the lazy dog.";

        Assert.assertEquals("Failed to set custom provider name",
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
    public void testGCM() throws Exception {
        src.setEncryptionAlgorithm("AES/GCM/NoPadding");
        src.start(Channel.SND_TX_SEQ);
        dest.setEncryptionAlgorithm("AES/GCM/NoPadding");
        dest.start(Channel.SND_TX_SEQ);

        String testInput = "The quick brown fox jumps over the lazy dog.";

        Assert.assertEquals("Failed in GCM mode",
                     testInput,
                     roundTrip(testInput, src, dest));
    }

    /*
     * ECB mode isn't supported because it's insecure.
     */
    @Test
    public void testECB() throws Exception {
        try {
            src.setEncryptionAlgorithm("AES/ECB/PKCS5Padding");
            src.start(Channel.SND_TX_SEQ);

            // start() should trigger IllegalArgumentException
            Assert.fail("ECB mode is not being refused");
        } catch (IllegalArgumentException iae) {
            // Expected
        }
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

        try (FileOutputStream out = new FileOutputStream(MESSAGE_FILE)) {
            out.write(bytes);
        }

        dest.start(Channel.SND_TX_SEQ);

        bytes = new byte[8192];
        int read;

        try (FileInputStream in = new FileInputStream(MESSAGE_FILE)) {
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

        MatcherAssert.assertThat("Two identical cleartexts encrypt to the same ciphertext",
                cipherText1, IsNot.not(IsEqual.equalTo(cipherText2)));
    }

    @Test
    public void testPickup() throws Exception {
        File file = new File(MESSAGE_FILE);
        if(!file.exists()) {
            System.err.println("File message.bin does not exist. Skipping test.");
            return;
        }

        dest.start(Channel.SND_TX_SEQ);

        byte[] bytes = new byte[8192];
        int read;

        try (FileInputStream in = new FileInputStream(file)) {
            read = in.read(bytes);
        }

        ChannelData msg = new ChannelData(false);
        XByteBuffer xbb = new XByteBuffer(read, false);
        xbb.append(bytes, 0, read);
        msg.setMessage(xbb);

        dest.messageReceived(msg);
    }

    /*
     * This test isn't guaranteed to catch any multithreaded issues, but it
     * gives a good exercise.
     */
    @Test
    public void testMultithreaded() throws Exception {
        String inputValue = "A test string to fight over.";
        final byte[] bytes = inputValue.getBytes("UTF-8");
        int numThreads = 100;
        final int messagesPerThread = 10;

        dest.setPrevious(new ValuesCaptureInterceptor());

        src.start(Channel.SND_TX_SEQ);
        dest.start(Channel.SND_TX_SEQ);

        Runnable job = new Runnable() {
            @Override
            public void run() {
                try {
                    ChannelData msg = new ChannelData(false);
                    XByteBuffer xbb = new XByteBuffer(1024, false);
                    xbb.append(bytes, 0, bytes.length);
                    msg.setMessage(xbb);

                    for(int i=0; i<messagesPerThread; ++i) {
                      src.sendMessage(null, msg, null);
                    }
                } catch (ChannelException e) {
                    Assert.fail("Encountered exception sending messages: " + e.getMessage());
                }
            }
        };

        Thread[] threads = new Thread[numThreads];
        for(int i=0; i<numThreads; ++i) {
            threads[i] = new Thread(job);
            threads[i].setName("Message-Thread-" + i);
        }

        for(int i=0; i<numThreads; ++i) {
          threads[i].start();
        }

        for(int i=0; i<numThreads; ++i) {
          threads[i].join();
        }

        // Check all received messages to make sure they are not corrupted
        Collection<byte[]> messages = ((ValuesCaptureInterceptor)dest.getPrevious()).getValues();

        Assert.assertEquals("Did not receive all expected messages",
                numThreads * messagesPerThread, messages.size());

        for(byte[] message : messages) {
          Assert.assertArrayEquals("Message is corrupted", message, bytes);
        }
    }

    @Test
    public void testTcpFailureDetectorDetection() {
        src.setPrevious(new TcpFailureDetector());

        try {
            src.start(Channel.SND_TX_SEQ);
            Assert.fail("EncryptInterceptor should detect TcpFailureDetector and throw an error");
        } catch (EncryptInterceptor.ChannelConfigException cce) {
            // Expected behavior
        } catch (AssertionError ae) {
            // This is the junit assertion being thrown
            throw ae;
        } catch (Throwable t) {
            Assert.fail("EncryptionInterceptor should throw ChannelConfigException, not " + t.getClass().getName());
        }
    }
}

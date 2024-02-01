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
import java.security.Security;
import java.util.ArrayList;
import java.util.Collection;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;

import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelInterceptor;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.group.ChannelInterceptorBase;
import org.apache.catalina.tribes.group.InterceptorPayload;
import org.apache.catalina.tribes.io.ChannelData;
import org.apache.catalina.tribes.io.XByteBuffer;

public class EncryptionInterceptorBaseTest {

    protected static final String MESSAGE_FILE = "message.bin";

    protected static final String encryptionKey128 = "cafebabedeadbeefbeefcafecafebabe";
    protected static final String encryptionKey192 = "cafebabedeadbeefbeefcafecafebabedeadbeefbeefcafe";
    protected static final String encryptionKey256 = "cafebabedeadbeefcafebabedeadbeefcafebabedeadbeefcafebabedeadbeef";

    protected EncryptInterceptor src;
    protected EncryptInterceptor dest;


    @BeforeClass
    public static void setupClass() {
        Security.setProperty("jdk.tls.disabledAlgorithms", "");
        Security.setProperty("crypto.policy", "unlimited");
    }


    @AfterClass
    public static void cleanup() {
        File f = new File(MESSAGE_FILE);
        if (f.isFile()) {
            Assert.assertTrue(f.delete());
        }
    }


    @Before
    public void setup() {
        src = new EncryptInterceptor();
        src.setEncryptionKey(encryptionKey128);

        dest = new EncryptInterceptor();
        dest.setEncryptionKey(encryptionKey128);

        src.setNext(new PipedInterceptor(dest));
        dest.setPrevious(new ValueCaptureInterceptor());
    }


    /**
     * Actually go through the interceptor's send/receive message methods.
     *
     * @param input The clear text message to sent
     * @param src   The interceptor to use to encrypt the message
     * @param dest  The interceptor to use to decrypt the message
     *
     * @return The clear text message received
     */
    protected static String roundTrip(String input, EncryptInterceptor src, EncryptInterceptor dest) throws Exception {
        byte[] bytes = input.getBytes("UTF-8");

        bytes = roundTrip(bytes, src, dest);

        return new String(bytes, "UTF-8");
    }


    /**
     * Actually go through the interceptor's send/receive message methods.
     *
     * @param input The clear text message to sent
     * @param src   The interceptor to use to encrypt the message
     * @param dest  The interceptor to use to decrypt the message
     *
     * @return The clear text message received
     */
    protected static byte[] roundTrip(byte[] input, EncryptInterceptor src, EncryptInterceptor dest) throws Exception {
        ChannelData msg = new ChannelData(false);
        msg.setMessage(new XByteBuffer(input, false));
        src.sendMessage(null, msg, null);

        return ((ValueCaptureInterceptor) dest.getPrevious()).getValue();
    }


    /**
     * Interceptor that delivers directly to a destination.
     */
    protected static class PipedInterceptor extends ChannelInterceptorBase {
        private ChannelInterceptor dest;

        PipedInterceptor(ChannelInterceptor dest) {
            if (null == dest) {
                throw new IllegalArgumentException("Destination must not be null");
            }

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
    protected static class ValueCaptureInterceptor extends ChannelInterceptorBase {
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

    /**
     * Interceptor that simply captures all messages sent to or received by it.
     */
    protected static class ValuesCaptureInterceptor extends ChannelInterceptorBase {
        private ArrayList<byte[]> messages = new ArrayList<>();

        @Override
        public void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload)
                throws ChannelException {
            synchronized (messages) {
                messages.add(msg.getMessage().getBytes());
            }
        }

        @Override
        public void messageReceived(ChannelMessage msg) {
            synchronized (messages) {
                messages.add(msg.getMessage().getBytes());
            }
        }

        @SuppressWarnings("unchecked")
        public Collection<byte[]> getValues() {
            return (Collection<byte[]>) messages.clone();
        }
    }

}

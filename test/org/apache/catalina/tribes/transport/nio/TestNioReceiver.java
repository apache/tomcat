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
package org.apache.catalina.tribes.transport.nio;

import java.lang.reflect.Field;
import java.nio.channels.DatagramChannel;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.tribes.group.GroupChannel;

public class TestNioReceiver {

    /*
     * A stop that raced with the start of the listener thread used to leave
     * the listening flag set, because the thread re-set it after the stop had
     * cleared it. Every later start then exited at the "already started"
     * guard and the receiver never listened again. The thread now only sets
     * the flag when the selector is still there to select on.
     */
    @Test
    public void testListenWithANullSelectorDoesNotSetTheFlag() throws Exception {
        NioReceiver receiver = new NioReceiver();
        receiver.setChannel(new GroupChannel());
        receiver.listen();
        Assert.assertFalse(receiver.isListening());
    }

    /*
     * A stale listener thread of a previous start that hits the "already
     * started" guard must leave the flag of the live listener alone, so that
     * it cannot tear a running receiver down.
     */
    @Test
    public void testStaleThreadDoesNotClearTheFlagOfALiveListener() {
        NioReceiver receiver = new NioReceiver();
        receiver.setChannel(new GroupChannel());
        receiver.setListen(true);
        receiver.run();
        Assert.assertTrue(receiver.isListening());
    }

    /*
     * The headline race: the receiver is fully stopped before the listener
     * thread gets to run. The thread must then not set the listening flag, so
     * the receiver is not left marked as listening without anything selecting,
     * which would block every later start at the "already started" guard.
     */
    @Test
    public void testListenAfterStopDoesNotSetTheFlag() throws Exception {
        NioReceiver receiver = new NioReceiver();
        receiver.setChannel(new GroupChannel());
        receiver.setAddress("127.0.0.1");
        receiver.setPort(0);
        receiver.bind();
        receiver.stopListening();
        receiver.listen();
        Assert.assertFalse(receiver.isListening());
    }

    /*
     * The thread that set the listening flag itself clears it again when the
     * selector is closed and nulled underneath it by a stop.
     */
    @Test
    public void testListenerThreadClearsTheFlagOnStop() throws Exception {
        NioReceiver receiver = new NioReceiver();
        receiver.setChannel(new GroupChannel());
        receiver.setAddress("127.0.0.1");
        receiver.setPort(0);
        receiver.bind();
        Thread thread = new Thread(receiver);
        thread.start();
        long deadline = System.currentTimeMillis() + 10000;
        while (!receiver.isListening() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        Assert.assertTrue("the listener thread never started listening", receiver.isListening());
        receiver.stopListening();
        thread.join(10000);
        Assert.assertFalse("the listener thread did not stop", thread.isAlive());
        Assert.assertFalse(receiver.isListening());
    }

    /*
     * The datagram channel is bound by bind() but only registered with the
     * selector in listen(), so stopListening() cannot reach it. The listener
     * thread that runs after the stop must still close it during its cleanup,
     * even when it never got to select.
     */
    @Test
    public void testListenAfterStopClosesTheBoundDatagramChannel() throws Exception {
        NioReceiver receiver = new NioReceiver();
        receiver.setChannel(new GroupChannel());
        receiver.setAddress("127.0.0.1");
        receiver.setPort(0);
        receiver.setUdpPort(45678);
        receiver.bind();
        Field field = NioReceiver.class.getDeclaredField("datagramChannel");
        field.setAccessible(true);
        DatagramChannel datagramChannel = (DatagramChannel) field.get(receiver);
        Assert.assertNotNull("bind() did not open the datagram channel", datagramChannel);
        receiver.stopListening();
        receiver.listen();
        Assert.assertFalse("the datagram channel was not closed", datagramChannel.isOpen());
        Assert.assertNull("the datagram channel reference was not cleared", field.get(receiver));
        Assert.assertFalse(receiver.isListening());
    }
}

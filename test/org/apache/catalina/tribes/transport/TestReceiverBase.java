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
package org.apache.catalina.tribes.transport;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.tribes.group.GroupChannel;
import org.apache.catalina.tribes.transport.nio.NioReceiver;

public class TestReceiverBase {

    // The ".invalid" top level domain is guaranteed never to resolve
    private static final String UNRESOLVABLE_HOST = "tomcat-tribes-does-not-exist.invalid";

    private static class ReceiverBaseImpl extends ReceiverBase {

        @Override
        public AbstractRxTask createRxTask() {
            return null;
        }
    }

    /*
     * An unresolvable bind address must fail the start of the receiver rather than silently binding the cluster
     * listener to the wildcard address, which would expose the receiver on all interfaces.
     */
    @Test
    public void testUnresolvableAddressFailsClosed() {
        NioReceiver receiver = new NioReceiver();
        receiver.setChannel(new GroupChannel());
        receiver.setAddress(UNRESOLVABLE_HOST);
        receiver.setPort(0);
        IOException ioe = Assert.assertThrows(IOException.class, receiver::start);
        Assert.assertNull("A failed resolution must not produce a bind address", receiver.getBind());
        Assert.assertFalse(receiver.isListening());
        receiver.stop();
        Assert.assertTrue(ioe.getMessage().contains(UNRESOLVABLE_HOST));
    }

    @Test
    public void testBindWithUnresolvableAddressThrows() {
        ReceiverBaseImpl receiver = new ReceiverBaseImpl();
        receiver.setAddress(UNRESOLVABLE_HOST);
        try (ServerSocket socket = new ServerSocket()) {
            IOException ioe = Assert.assertThrows(IOException.class, () -> receiver.bind(socket, 0, 3, false));
            Assert.assertTrue(ioe.getMessage().contains(UNRESOLVABLE_HOST));
        } catch (IOException ioe) {
            Assert.fail(ioe.getMessage());
        }
        Assert.assertNull(receiver.getBind());
    }

    @Test
    public void testBindUdpWithUnresolvableAddressThrows() {
        ReceiverBaseImpl receiver = new ReceiverBaseImpl();
        receiver.setAddress(UNRESOLVABLE_HOST);
        try (DatagramSocket socket = new DatagramSocket(null)) {
            IOException ioe = Assert.assertThrows(IOException.class, () -> receiver.bindUdp(socket, 0, 3));
            Assert.assertTrue(ioe.getMessage().contains(UNRESOLVABLE_HOST));
        } catch (IOException ioe) {
            Assert.fail(ioe.getMessage());
        }
    }

    @Test
    public void testBindWithResolvableAddress() throws UnknownHostException {
        ReceiverBaseImpl receiver = new ReceiverBaseImpl();
        receiver.setAddress("127.0.0.1");
        Assert.assertEquals(InetAddress.getByName("127.0.0.1"), receiver.getBind());
        try (ServerSocket socket = new ServerSocket()) {
            receiver.bind(socket, 0, 1, false);
            // A resolved address must be used, never the wildcard address
            Assert.assertFalse(socket.getInetAddress().isAnyLocalAddress());
        } catch (IOException ioe) {
            Assert.fail(ioe.getMessage());
        }
    }

    @Test
    public void testBindUdpWithResolvableAddress() throws UnknownHostException {
        ReceiverBaseImpl receiver = new ReceiverBaseImpl();
        receiver.setAddress("127.0.0.1");
        Assert.assertEquals(InetAddress.getByName("127.0.0.1"), receiver.getBind());
        try (DatagramSocket socket = new DatagramSocket(null)) {
            receiver.bindUdp(socket, 0, 1);
            Assert.assertFalse(socket.getLocalAddress().isAnyLocalAddress());
        } catch (IOException ioe) {
            Assert.fail(ioe.getMessage());
        }
    }

    /*
     * A failed resolution must not be cached, so that a later attempt after the configuration is corrected succeeds.
     */
    @Test
    public void testBindSucceedsAfterCorrectedAddress() {
        ReceiverBaseImpl receiver = new ReceiverBaseImpl();
        receiver.setAddress(UNRESOLVABLE_HOST);
        try (ServerSocket socket = new ServerSocket()) {
            Assert.assertThrows(IOException.class, () -> receiver.bind(socket, 0, 1, false));
            receiver.setAddress("127.0.0.1");
            receiver.bind(socket, 0, 1, false);
            Assert.assertFalse(socket.getInetAddress().isAnyLocalAddress());
        } catch (IOException ioe) {
            Assert.fail(ioe.getMessage());
        }
    }
}

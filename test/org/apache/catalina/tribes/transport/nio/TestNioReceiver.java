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

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Pipe;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.tribes.group.GroupChannel;
import org.apache.catalina.tribes.jmx.JmxRegistry;
import org.apache.catalina.tribes.transport.RxTaskPool;

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
        receiver.setUdpPort(0);
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

    /*
     * A stale listener thread that exits must not make stopListening() believe that the live listener has finished.
     */
    @Test
    public void testStaleThreadExitKeepsLiveListenerCounted() throws Exception {
        NioReceiver receiver = new NioReceiver();
        receiver.setChannel(new GroupChannel());
        receiver.setAddress("127.0.0.1");
        receiver.setPort(0);
        receiver.bind();
        Field field = NioReceiver.class.getDeclaredField("runningListeners");
        field.setAccessible(true);
        AtomicInteger running = (AtomicInteger) field.get(receiver);
        Thread live = new Thread(receiver);
        live.start();
        try {
            long deadline = System.currentTimeMillis() + 10000;
            while (!receiver.isListening() && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            Assert.assertTrue(receiver.isListening());
            Assert.assertEquals(1, running.get());
            // Hits the already started guard and returns
            receiver.run();
            Assert.assertEquals(1, running.get());
            Assert.assertTrue(receiver.isListening());
        } finally {
            receiver.stopListening();
            live.join(10000);
        }
        Assert.assertEquals(0, running.get());
    }

    @Test
    public void testStaleListenerDoesNotCloseRestartedReceiver() throws Exception {
        NioReceiver receiver = new NioReceiver();
        receiver.setChannel(new GroupChannel());
        receiver.setAddress("127.0.0.1");
        receiver.setPort(0);
        receiver.setUdpPort(0);

        Field serverChannelField = NioReceiver.class.getDeclaredField("serverChannel");
        serverChannelField.setAccessible(true);
        Field datagramChannelField = NioReceiver.class.getDeclaredField("datagramChannel");
        datagramChannelField.setAccessible(true);
        Field selectorField = NioReceiver.class.getDeclaredField("selector");
        selectorField.setAccessible(true);

        try {
            receiver.bind();
            ServerSocketChannel oldServer = (ServerSocketChannel) serverChannelField.get(receiver);
            DatagramChannel oldDatagram = (DatagramChannel) datagramChannelField.get(receiver);
            @SuppressWarnings("unchecked")
            AtomicReference<Selector> selectorReference =
                    (AtomicReference<Selector>) selectorField.get(receiver);
            Selector oldSelector = selectorReference.get();

            receiver.stopListening();
            receiver.bind();
            ServerSocketChannel newServer = (ServerSocketChannel) serverChannelField.get(receiver);
            DatagramChannel newDatagram = (DatagramChannel) datagramChannelField.get(receiver);
            Selector newSelector = selectorReference.get();

            receiver.closeListenerResources(oldSelector, oldServer, oldDatagram);

            Assert.assertTrue(newServer.isOpen());
            Assert.assertTrue(newDatagram.isOpen());
            Assert.assertTrue(newSelector.isOpen());
            Assert.assertSame(newServer, serverChannelField.get(receiver));
            Assert.assertSame(newDatagram, datagramChannelField.get(receiver));
            Assert.assertSame(newSelector, selectorReference.get());
        } finally {
            receiver.stop();
        }
    }

    @Test
    public void testListenerSetupFailureClosesCapturedResources() throws Exception {
        NioReceiver receiver = new NioReceiver() {
            @Override
            protected void registerChannel(Selector selector, SelectableChannel channel, int ops, Object attach)
                    throws Exception {
                if (channel instanceof DatagramChannel) {
                    throw new IOException("Datagram registration failed");
                }
                super.registerChannel(selector, channel, ops, attach);
            }
        };
        receiver.setChannel(new GroupChannel());
        receiver.setAddress("127.0.0.1");
        receiver.setPort(0);
        receiver.setUdpPort(0);

        try {
            receiver.bind();
            Assert.assertThrows(IOException.class, receiver::listen);
            assertReleased(receiver);
            Assert.assertFalse(receiver.isListening());
        } finally {
            receiver.stop();
        }
    }

    /*
     * A TCP bind that exhausts the auto-bind retries throws out of bind()
     * after the server channel and the selector were already opened. Both
     * must be released, or every failed start leaks a descriptor and a
     * selector that nothing can reach any more.
     */
    @Test
    public void testFailedTcpBindReleasesTheOpenedResources() throws Exception {
        NioReceiver receiver = new NioReceiver();
        receiver.setChannel(new GroupChannel());
        receiver.setAddress("127.0.0.1");
        receiver.setAutoBind(1);
        try (ServerSocket blocker = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            receiver.setPort(blocker.getLocalPort());
            try {
                receiver.bind();
                Assert.fail("the bind to an occupied port did not fail");
            } catch (IOException expected) {
                // expected
            }
        }
        assertReleased(receiver);
    }

    /*
     * When the UDP bind is what fails, the TCP server channel was already
     * bound and registered with the selector. The failed start must release
     * the server channel and the selector as well, not just the half set up
     * datagram channel.
     */
    @Test
    public void testFailedUdpBindReleasesTheOpenedResources() throws Exception {
        NioReceiver receiver = new NioReceiver();
        receiver.setChannel(new GroupChannel());
        receiver.setAddress("127.0.0.1");
        receiver.setAutoBind(1);
        receiver.setPort(0);
        // Without SO_REUSEADDR on the receiver side as well, the bind to the
        // occupied UDP port fails deterministically on every platform
        receiver.setSoReuseAddress(false);
        try (DatagramSocket blocker = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"))) {
            receiver.setUdpPort(blocker.getLocalPort());
            try {
                receiver.bind();
                Assert.fail("the UDP bind to an occupied port did not fail");
            } catch (IOException expected) {
                // expected
            }
        }
        assertReleased(receiver);
    }

    @Test
    public void testFailedStartReleasesResourcesAndCanRestart() throws Exception {
        GroupChannel channel = new GroupChannel();
        channel.setName("TestNioReceiver-" + System.nanoTime());
        NioReceiver receiver = new NioReceiver();
        receiver.setChannel(channel);
        receiver.setAddress("127.0.0.1");
        receiver.setPort(0);
        receiver.setAutoBind(1);
        receiver.setSoReuseAddress(false);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        receiver.setExecutor(executor);
        MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
        ObjectName objectName = new ObjectName("ClusterChannel:type=Channel,channel=" + channel.getName() +
                ",component=Receiver");
        try {
            int udpPort;
            try (DatagramSocket blocker = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"))) {
                udpPort = blocker.getLocalPort();
                receiver.setUdpPort(udpPort);
                Assert.assertThrows(IOException.class, receiver::start);
                assertReleased(receiver);
                Assert.assertEquals(-1, receiver.getBoundPort());
                Assert.assertEquals(-1, receiver.getBoundSecurePort());
                Assert.assertEquals(-1, receiver.getBoundUdpPort());
                Assert.assertFalse(receiver.isListening());
                Assert.assertNull(receiver.getExecutor());
                Assert.assertTrue(executor.isShutdown());
                Assert.assertNull(receiver.getChannel());
                Assert.assertFalse(mbeanServer.isRegistered(objectName));
            }

            // As on a normal restart, the coordinator must reattach the channel before starting the receiver.
            receiver.setChannel(channel);
            receiver.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!receiver.isListening() && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            Assert.assertTrue("the receiver did not start after the UDP port was released", receiver.isListening());
            Assert.assertTrue(receiver.getBoundPort() > 0);
            Assert.assertEquals(udpPort, receiver.getBoundUdpPort());
            Assert.assertEquals(0, receiver.getPort());
            Assert.assertEquals(udpPort, receiver.getUdpPort());
            Assert.assertFalse(receiver.getExecutor().isShutdown());
            Assert.assertTrue(mbeanServer.isRegistered(objectName));
        } finally {
            receiver.stop();
            JmxRegistry.removeRegistry(channel, true);
        }
        assertReleased(receiver);
        Assert.assertFalse(mbeanServer.isRegistered(objectName));
    }

    @Test
    public void testStopBeforeListenClosesBoundDatagramChannel() throws Exception {
        NioReceiver receiver = new NioReceiver();
        receiver.setChannel(new GroupChannel());
        receiver.setAddress("127.0.0.1");
        receiver.setPort(0);
        receiver.setUdpPort(0);
        try {
            receiver.bind();
            Field field = NioReceiver.class.getDeclaredField("datagramChannel");
            field.setAccessible(true);
            DatagramChannel datagramChannel = (DatagramChannel) field.get(receiver);
            Assert.assertTrue(datagramChannel.isOpen());
            receiver.stop();
            Assert.assertFalse("stop must close UDP even before listen registers it", datagramChannel.isOpen());
            assertReleased(receiver);
        } finally {
            receiver.stop();
        }
    }

    @Test
    public void testPoolFailurePreservesCauseAndSuppressedCleanupFailure() {
        RuntimeException startupFailure = new IllegalStateException("pool initialization failed");
        RuntimeException cleanupFailure = new IllegalStateException("cleanup failed");
        NioReceiver receiver = new NioReceiver() {
            @Override
            public void setPool(RxTaskPool pool) {
                throw startupFailure;
            }

            @Override
            public void stop() {
                super.stop();
                throw cleanupFailure;
            }
        };
        receiver.setChannel(new GroupChannel());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        receiver.setExecutor(executor);
        try {
            IOException ioe = Assert.assertThrows(IOException.class, receiver::start);
            Assert.assertSame(startupFailure, ioe.getCause());
            Assert.assertArrayEquals(new Throwable[] { cleanupFailure }, startupFailure.getSuppressed());
            Assert.assertTrue(executor.isShutdown());
            Assert.assertNull(receiver.getExecutor());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testRejectedTaskIsResetBeforeReturningToPool() throws Exception {
        NioReceiver receiver = new NioReceiver();
        receiver.setChannel(new GroupChannel());
        RxTaskPool pool = new RxTaskPool(1, 1, receiver::createRxTask);
        receiver.setPool(pool);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.shutdown();
        receiver.setExecutor(executor);

        Pipe pipe = Pipe.open();
        try (Pipe.SourceChannel source = pipe.source(); Pipe.SinkChannel sink = pipe.sink();
                Selector selector = Selector.open()) {
            Field selectorField = NioReceiver.class.getDeclaredField("selector");
            selectorField.setAccessible(true);
            @SuppressWarnings("unchecked")
            AtomicReference<Selector> receiverSelector = (AtomicReference<Selector>) selectorField.get(receiver);
            receiverSelector.set(selector);
            source.configureBlocking(false);
            SelectionKey key = source.register(selector, SelectionKey.OP_READ);

            Assert.assertThrows(RejectedExecutionException.class, () -> receiver.readDataFromSocket(key));
            Assert.assertEquals(1, pool.available());
            receiver.events();
            Assert.assertFalse(key.isValid());

            Object task = pool.getRxTask();
            Field keyField = NioReplicationTask.class.getDeclaredField("key");
            keyField.setAccessible(true);
            Assert.assertNull(keyField.get(task));
            pool.returnWorker((NioReplicationTask) task);
        } finally {
            receiver.stop();
        }
    }

    private void assertReleased(NioReceiver receiver) throws Exception {
        Field serverChannelField = NioReceiver.class.getDeclaredField("serverChannel");
        serverChannelField.setAccessible(true);
        ServerSocketChannel serverChannel = (ServerSocketChannel) serverChannelField.get(receiver);
        Assert.assertNull("the server channel reference was not cleared", serverChannel);

        Field datagramChannelField = NioReceiver.class.getDeclaredField("datagramChannel");
        datagramChannelField.setAccessible(true);
        DatagramChannel datagramChannel = (DatagramChannel) datagramChannelField.get(receiver);
        Assert.assertNull("the datagram channel reference was not cleared", datagramChannel);

        Field selectorField = NioReceiver.class.getDeclaredField("selector");
        selectorField.setAccessible(true);
        AtomicReference<?> selector = (AtomicReference<?>) selectorField.get(receiver);
        Assert.assertNull("the selector reference was not cleared", selector.get());
    }
}

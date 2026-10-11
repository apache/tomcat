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
package org.apache.catalina.tribes.membership;

import java.io.ByteArrayInputStream;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.group.GroupChannel;
import org.apache.catalina.tribes.io.ChannelData;
import org.apache.catalina.tribes.io.XByteBuffer;

public class TestMcastService {

    /*
     * The shutdown message that stop() broadcasts routinely fails when the network device is down
     * or the socket is already broken. That must not skip leaving the multicast group and closing
     * the socket, nor prevent the service facade from releasing its implementation.
     */
    @Test(timeout = 60000)
    public void testStopWithFailingShutdownSendCompletes() throws Exception {
        int base = 40000 + (int) Math.floorMod(System.nanoTime(), 10000);
        StringBuilder properties = new StringBuilder();
        properties.append("mcastAddress=228.0.0.4\n");
        properties.append("mcastPort=").append(base).append("\n");
        properties.append("tcpListenHost=127.0.0.1\n");
        properties.append("tcpListenPort=").append(base + 1).append("\n");
        properties.append("tcpSecurePort=0\n");
        properties.append("udpListenPort=0\n");
        properties.append("mcastFrequency=100\n");
        properties.append("memberDropTime=3000\n");
        properties.append("recoveryEnabled=false\n");
        properties.append("recoveryCounter=10\n");
        properties.append("recoverySleepTime=3000\n");

        GroupChannel channel = new GroupChannel();
        channel.setName("TestMcastService-" + System.nanoTime());

        McastService service = new McastService();
        service.setChannel(channel);
        Properties props = new Properties();
        props.load(new ByteArrayInputStream(properties.toString().getBytes("UTF-8")));
        service.setProperties(props);
        service.start(Channel.MBR_TX_SEQ);

        McastServiceImpl impl = service.impl;
        Assert.assertNotNull(impl);

        // Break the socket so that the shutdown message cannot be sent
        impl.socket.close();

        // Stop must complete and release the implementation despite the failing shutdown send
        service.stop(Channel.MBR_TX_SEQ);

        Assert.assertNull("the membership implementation must be released on stop", service.impl);
    }

    /*
     * The soTimeout and ttl reported through MBean must reflect the configuration, whether it is
     * supplied through the properties file or through the setters. Previously the getters read
     * private fields that only the setters wrote, so a property-based configuration that the socket
     * did apply was reported as the default 0.
     */
    @Test
    public void testSoTimeoutAndTtlReflectProperties() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("mcastAddress", "228.0.0.4");
        properties.setProperty("mcastPort", "45564");
        properties.setProperty("memberDropTime", "3000");
        properties.setProperty("mcastFrequency", "500");
        properties.setProperty("tcpListenHost", "127.0.0.1");
        properties.setProperty("tcpListenPort", "4000");
        properties.setProperty("mcastSoTimeout", "1234");
        properties.setProperty("mcastTTL", "5");

        McastService service = new McastService();
        service.setProperties(properties);

        Assert.assertEquals(1234, service.getSoTimeout());
        Assert.assertEquals(5, service.getTtl());

        // The setters keep overriding, with the setter value taking precedence
        service.setSoTimeout(7);
        service.setTtl(9);
        Assert.assertEquals(7, service.getSoTimeout());
        Assert.assertEquals(9, service.getTtl());

        // A later setProperties replaces the whole configuration, and with it the setter values,
        // as that is what a subsequent start() would apply
        properties.remove("mcastSoTimeout");
        properties.remove("mcastTTL");
        service.setProperties(properties);
        Assert.assertEquals(0, service.getSoTimeout());
        Assert.assertEquals(0, service.getTtl());
    }

    /*
     * When neither the properties nor a setter provides a value, the reported soTimeout and ttl
     * default to 0, as they did before.
     */
    @Test
    public void testSoTimeoutAndTtlDefaultToZero() throws Exception {
        McastService service = new McastService();
        Assert.assertEquals(0, service.getSoTimeout());
        Assert.assertEquals(0, service.getTtl());
    }

    /*
     * A non-positive send frequency makes the sender spin sending pings without a pause, and a
     * non-positive drop time expires every member on every cycle. Such configuration must be
     * rejected when the service starts, before any socket or thread is created.
     */
    @Test
    public void testStartRejectsNonPositiveTimingProperties() throws Exception {
        String[] invalid = { "mcastFrequency=0", "mcastFrequency=-1", "memberDropTime=0", "memberDropTime=-5" };
        for (String entry : invalid) {
            String[] property = entry.split("=");
            Properties props = new Properties();
            props.setProperty("mcastAddress", "228.0.0.4");
            props.setProperty("mcastPort", "45564");
            props.setProperty("tcpListenHost", "127.0.0.1");
            props.setProperty("tcpListenPort", "4000");
            props.setProperty("tcpSecurePort", "0");
            props.setProperty("udpListenPort", "0");
            props.setProperty("mcastFrequency", "500");
            props.setProperty("memberDropTime", "3000");
            props.setProperty(property[0], property[1]);

            GroupChannel channel = new GroupChannel();
            channel.setName("TestMcastService-" + System.nanoTime());

            McastService service = new McastService();
            service.setChannel(channel);
            service.setProperties(props);
            try {
                service.start(Channel.MBR_TX_SEQ);
                Assert.fail("start() must reject a non-positive " + property[0]);
            } catch (IllegalArgumentException expected) {
                // The configuration must be rejected before anything is started
            }
            Assert.assertNull(service.impl);
        }
    }

    /*
     * The 65535 byte protocol limit of an IP packet includes the IP and UDP headers, so a
     * broadcast that fits within it can still exceed what the socket is able to send. Such a
     * broadcast must be rejected by the size check rather than fail later in the send.
     */
    @Test(timeout = 60000)
    public void testBroadcastRejectsPacketsTheSocketCannotCarry() throws Exception {
        int base = 40000 + (int) Math.floorMod(System.nanoTime(), 10000);
        Properties props = new Properties();
        props.setProperty("mcastAddress", "228.0.0.4");
        props.setProperty("mcastPort", String.valueOf(base));
        props.setProperty("tcpListenHost", "127.0.0.1");
        props.setProperty("tcpListenPort", String.valueOf(base + 1));
        props.setProperty("tcpSecurePort", "0");
        props.setProperty("udpListenPort", "0");
        props.setProperty("mcastFrequency", "100");
        props.setProperty("memberDropTime", "3000");
        props.setProperty("recoveryEnabled", "false");
        props.setProperty("recoveryCounter", "10");
        props.setProperty("recoverySleepTime", "3000");

        GroupChannel channel = new GroupChannel();
        channel.setName("TestMcastService-" + System.nanoTime());

        McastService service = new McastService();
        service.setChannel(channel);
        service.setProperties(props);
        service.start(Channel.MBR_TX_SEQ);
        try {
            // Build a packet just above the sendable size but still within the 65535 byte packet
            // limit the old check used, and small enough for this platform to actually send
            ChannelData probe = new ChannelData();
            probe.setMessage(new XByteBuffer(new byte[0], false));
            int overhead = XByteBuffer.createDataPackage(probe).length;
            Assert.assertTrue(overhead > 0 && overhead < 128);
            byte[] messageBytes = new byte[McastServiceImpl.MAX_SEND_SIZE + 8 - overhead];
            Assert.assertEquals(McastServiceImpl.MAX_SEND_SIZE + 8,
                    XByteBuffer.createDataPackage(new ChannelData(probe.getUniqueId(),
                            new XByteBuffer(messageBytes, false), 0)).length);

            ChannelData message = new ChannelData();
            message.setMessage(new XByteBuffer(messageBytes, false));
            message.setTimestamp(System.currentTimeMillis());
            try {
                service.broadcast(message);
                Assert.fail("A broadcast above the sendable size must be rejected");
            } catch (ChannelException expected) {
                // The size check must reject it
            }
        } finally {
            service.stop(Channel.MBR_TX_SEQ);
        }
    }
}

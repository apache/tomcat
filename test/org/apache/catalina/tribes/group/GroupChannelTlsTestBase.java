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
package org.apache.catalina.tribes.group;

import java.io.Serializable;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelListener;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.transport.ReceiverBase;

@RunWith(Parameterized.class)
public class GroupChannelTlsTestBase {

    @Parameterized.Parameters(name = "{index}: {0} {1} {2}")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();
        for (Boolean explicit : TomcatBaseTest.booleans) {
            parameterSets.add(new Object[] { explicit, "TLSv1.2", "SHA256" });
            parameterSets.add(new Object[] { explicit, "TLSv1.3", "SHA256" });
            parameterSets.add(new Object[] { explicit, "TLSv1.3", "SHA384" });
        }
        return parameterSets;
    }

    @Parameter(0)
    public boolean explicit;
    @Parameter(1)
    public String protocol;
    @Parameter(2)
    public String digest;

    @Test
    public void testPskDefaults() {
        GroupChannel channel = new GroupChannel();
        Assert.assertEquals("SHA256", channel.getPskDigest());
        Assert.assertEquals("TLSv1.3", channel.getPskProtocol());
    }


    @Test
    public void testSecureMessage() throws Exception {
        GroupChannel sender = createChannel(protocol, digest);
        GroupChannel receiver = createChannel(protocol, digest);
        CountDownLatch received = new CountDownLatch(1);
        receiver.addChannelListener(new ChannelListener() {
            @Override
            public boolean accept(Serializable msg, Member member) {
                return true;
            }

            @Override
            public void messageReceived(Serializable msg, Member member) {
                if ("test".equals(msg)) {
                    received.countDown();
                }
            }
        });

        try {
            sender.start(Channel.SND_RX_SEQ | Channel.SND_TX_SEQ);
            receiver.start(Channel.SND_RX_SEQ | Channel.SND_TX_SEQ);
            sender.send(new Member[] { receiver.getLocalMember(false) }, "test",
                    Channel.SEND_OPTIONS_SECURE | Channel.SEND_OPTIONS_USE_ACK);
            Assert.assertTrue(received.await(5, TimeUnit.SECONDS));
        } finally {
            sender.stop(Channel.DEFAULT);
            receiver.stop(Channel.DEFAULT);
        }
    }


    private static GroupChannel createChannel(String protocol, String digest) throws Exception {
        GroupChannel channel = new GroupChannel();
        channel.setPskIdentity("tribes-test");
        channel.setPskKey("000102030405060708090a0b0c0d0e0f");
        channel.setPskProtocol(protocol);
        channel.setPskDigest(digest);
        ReceiverBase receiver = (ReceiverBase) channel.getChannelReceiver();
        receiver.setHost("localhost");
        try (ServerSocket socket = new ServerSocket(0)) {
            receiver.setSecurePort(socket.getLocalPort());
        }
        return channel;
    }
}

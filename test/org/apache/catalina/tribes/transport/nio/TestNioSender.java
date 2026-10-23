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

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.channels.Selector;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.tribes.membership.MemberImpl;

public class TestNioSender {

    /*
     * A timeout too large for the int SO_TIMEOUT must be capped rather than overflow to a negative value that the
     * socket rejects.
     */
    @Test
    public void testConnectWithTimeoutAboveIntegerMax() throws Exception {
        try (DatagramSocket target = new DatagramSocket(0, InetAddress.getLoopbackAddress());
                Selector selector = Selector.open()) {
            MemberImpl member = new MemberImpl("127.0.0.1", 4000, 0);
            member.setUdpPort(target.getLocalPort());

            NioSender sender = new NioSender();
            sender.setDestination(member);
            sender.setUdpBased(true);
            sender.setSelector(selector);
            sender.setTimeout(Long.MAX_VALUE);
            try {
                sender.connect();
                Assert.assertTrue(sender.isConnected());
            } finally {
                sender.disconnect();
            }
        }
    }
}

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
import org.apache.catalina.tribes.group.GroupChannel;

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
}

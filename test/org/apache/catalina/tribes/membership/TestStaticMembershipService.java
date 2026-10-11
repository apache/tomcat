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

import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.group.GroupChannel;

public class TestStaticMembershipService {

    /*
     * A non-positive ping interval makes the ping thread spin without a pause, and a non-positive
     * expiration time expires every member on every cycle. Such configuration must be rejected
     * when the service initializes its provider.
     */
    @Test
    public void testStartRejectsNonPositiveTimingProperties() throws Exception {
        String[] invalid = { "pingInterval=0", "pingInterval=-1", "expirationTime=0", "expirationTime=-5" };
        for (String entry : invalid) {
            String[] property = entry.split("=");
            Properties props = new Properties();
            props.setProperty(property[0], property[1]);

            GroupChannel channel = new GroupChannel();
            channel.setName("TestStaticMembershipService-" + System.nanoTime());

            StaticMembershipService service = new StaticMembershipService();
            service.setChannel(channel);
            service.setProperties(props);
            // The local member must be part of the static member list
            service.addStaticMember(new StaticMember("127.0.0.1", 4001, -1));
            service.setLocalMemberProperties("127.0.0.1", 4001, 0, 0);
            try {
                service.start(Channel.MBR_TX_SEQ);
                Assert.fail("start() must reject a non-positive " + property[0]);
            } catch (IllegalArgumentException expected) {
                // The configuration must be rejected before the provider is built
            }
        }
    }

    /*
     * A valid configuration, as setDefaults provides it, must still start and stop cleanly.
     */
    @Test
    public void testValidTimingPropertiesStartAndStop() throws Exception {
        GroupChannel channel = new GroupChannel();
        channel.setName("TestStaticMembershipService-" + System.nanoTime());

        StaticMembershipService service = new StaticMembershipService();
        service.setChannel(channel);
        service.addStaticMember(new StaticMember("127.0.0.1", 4002, -1));
        service.setLocalMemberProperties("127.0.0.1", 4002, 0, 0);

        int level = Channel.MBR_RX_SEQ | Channel.MBR_TX_SEQ;
        service.start(level);
        service.stop(level);
    }
}

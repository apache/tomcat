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

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.tribes.util.UUIDGenerator;

public class TestMemberImpl {

    /*
     * The 65535 byte protocol limit of an IP packet includes the IP and UDP headers, so a member
     * ping that fits within it can still be too large for the multicast socket to send. The payload
     * size check must bound the ping at the size that can actually be sent, not the packet limit.
     */
    @Test
    public void testPayloadIsBoundedAtTheSendableSize() throws Exception {
        MemberImpl member = new MemberImpl("localhost", 4000, 100);
        member.setUniqueId(UUIDGenerator.randomUUID(false));
        int base = member.getData(false, false).length;

        // A payload that fills the ping exactly up to the sendable size is accepted
        member.setPayload(new byte[McastServiceImpl.MAX_SEND_SIZE - base]);

        // One byte more passes the 65535 byte packet limit but cannot be carried by the socket
        try {
            member.setPayload(new byte[McastServiceImpl.MAX_SEND_SIZE - base + 1]);
            Assert.fail("A payload that takes the ping above the sendable size must be rejected");
        } catch (IllegalArgumentException expected) {
            // The size check must reject it
        }
    }

    /*
     * Every variable length component of the ping must be bounded in the same way as the payload, and
     * a rejected change must leave the member unmodified and usable.
     */
    @Test
    public void testOtherComponentsAreBoundedAtTheSendableSize() throws Exception {
        MemberImpl member = new MemberImpl("localhost", 4000, 100);
        member.setUniqueId(UUIDGenerator.randomUUID(false));
        byte[] before = member.getData(false, false);
        int base = before.length;
        int tooLarge = McastServiceImpl.MAX_SEND_SIZE - base + 1;

        try {
            member.setCommand(new byte[tooLarge + member.getCommand().length]);
            Assert.fail("An oversized command must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected
        }
        try {
            member.setDomain(new byte[tooLarge + member.getDomain().length]);
            Assert.fail("An oversized domain must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected
        }
        try {
            member.setUniqueId(new byte[tooLarge + member.getUniqueId().length]);
            Assert.fail("An oversized unique ID must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected
        }
        try {
            member.setHost(new byte[tooLarge + member.getHost().length]);
            Assert.fail("An oversized host must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected
        }

        // Rejected changes leave the member unmodified and usable
        Assert.assertEquals(base, member.getData(false, true).length);
        member.setCommand(new byte[4]);
    }
}

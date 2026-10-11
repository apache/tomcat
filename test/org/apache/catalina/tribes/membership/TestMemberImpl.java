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
}

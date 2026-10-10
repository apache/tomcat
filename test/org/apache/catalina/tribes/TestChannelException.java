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
package org.apache.catalina.tribes;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.tribes.membership.MemberImpl;

public class TestChannelException {

    /**
     * Verify that getMessage() and toString() work for exceptions created without a detail message, instead of
     * throwing a NullPointerException because the base message is null.
     *
     * @throws Exception if the test experiences an unexpected error
     */
    @Test
    public void testMessageWithoutDetail() throws Exception {
        ChannelException e = new ChannelException();
        String message = e.getMessage();
        Assert.assertNotNull(message);
        Assert.assertTrue(message, message.contains(ChannelException.class.getName()));
        Assert.assertTrue(message, message.contains("No faulty members identified."));
        Assert.assertNotNull(e.toString());

        ChannelException withCause = new ChannelException(new IllegalStateException("root"));
        Assert.assertTrue(withCause.getMessage(), withCause.getMessage().contains("root"));
    }

    /**
     * Verify the unchanged formatting for a detail message and for faulty members.
     *
     * @throws Exception if the test experiences an unexpected error
     */
    @Test
    public void testMessageWithDetail() throws Exception {
        ChannelException e = new ChannelException("detail");
        Assert.assertTrue(e.getMessage(), e.getMessage().startsWith("detail"));

        e.addFaultyMember(new MemberImpl("10.0.0.1", 4000, -1), new Exception("failed"));
        String message = e.getMessage();
        Assert.assertTrue(message, message.startsWith("detail"));
        Assert.assertTrue(message, message.contains("Faulty members:"));
        Assert.assertTrue(message, message.contains("10.0.0.1"));
    }
}

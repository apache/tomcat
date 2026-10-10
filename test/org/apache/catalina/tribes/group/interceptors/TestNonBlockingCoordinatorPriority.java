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
package org.apache.catalina.tribes.group.interceptors;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.membership.MemberImpl;

public class TestNonBlockingCoordinatorPriority {

    @Test
    public void testHasHigherPriority() throws Exception {
        NonBlockingCoordinator coordinator = new NonBlockingCoordinator();

        // Under absolute ordering, lower host bytes mean a higher rank
        Member high = new MemberImpl("10.0.0.1", 4000, -1);
        Member low = new MemberImpl("10.0.0.2", 4000, -1);

        Assert.assertTrue("A membership with the higher priority member must win",
                coordinator.hasHigherPriority(new Member[] { low, high }, new Member[] { low }));
        Assert.assertFalse("A membership with the lower priority member must not win",
                coordinator.hasHigherPriority(new Member[] { low }, new Member[] { high, low }));
        Assert.assertFalse("Identical leaders must not win",
                coordinator.hasHigherPriority(new Member[] { high }, new Member[] { high }));
        Assert.assertFalse("An empty local membership must never be outranked",
                coordinator.hasHigherPriority(new Member[] { high }, new Member[0]));
        Assert.assertTrue("An empty complete membership is treated as higher priority",
                coordinator.hasHigherPriority(null, new Member[] { high }));
        Assert.assertFalse("A null local membership must never be outranked",
                coordinator.hasHigherPriority(new Member[] { high }, null));
    }
}

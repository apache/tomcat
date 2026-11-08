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
package org.apache.catalina.tribes.tipis;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.group.GroupChannel;
import org.apache.catalina.tribes.membership.MemberImpl;

public class TestLazyReplicatedMap {

    /*
     * The backup node selection snapshots the members and computes an index in two steps. A member added in between
     * must not let the index point behind the end of the snapshot, which aborted the publication with an array index
     * out of bounds exception, losing the entry that was being stored. The index is computed for the size of the
     * snapshot.
     */
    @Test
    public void testBackupIndexWithinMemberSnapshot() throws Exception {
        Member member = new MemberImpl("localhost", 4007, -1);
        Member other = new MemberImpl("localhost", 4008, -1);

        LazyReplicatedMap<String,String> map = new LazyReplicatedMap<>((key, value) -> {
            // Not made primary here
        }, new GroupChannel(), 1000, "TestLazyReplicatedMap", null) {

            @Override
            public Member[] getMapMembers() {
                // The snapshot is smaller than the member list that was just extended
                mapMembers.put(member, Long.valueOf(0));
                mapMembers.put(other, Long.valueOf(0));
                currentNode = 1;
                return new Member[] { member };
            }
        };

        // The channel is not started, so the publication to the backup fails as a channel
        // exception that the selection contains, while the entry survives to be stored locally
        Member[] backup = map.publishEntryInfo("key", "value");
        Assert.assertEquals(0, backup.length);
    }

    @Test
    public void testNextBackupIndexClampsToSize() {
        LazyReplicatedMap<String,String> map = new LazyReplicatedMap<>((key, value) -> {
            // Not made primary here
        }, new GroupChannel(), 1000, "TestLazyReplicatedMap", null);

        Assert.assertEquals(-1, map.getNextBackupIndex(0));
        Assert.assertEquals(0, map.getNextBackupIndex(3));
        Assert.assertEquals(1, map.getNextBackupIndex(3));
        Assert.assertEquals(2, map.getNextBackupIndex(3));
        // Rotation index is now beyond a smaller list
        Assert.assertEquals(0, map.getNextBackupIndex(2));
        Assert.assertEquals(1, map.getNextBackupIndex(2));
    }
}

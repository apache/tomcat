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
     * The backup node selection snapshots the members and computes an index against the current
     * members in two steps. A member added in between lets the index point behind the end of the
     * snapshot, which aborted the publication with an array index out of bounds exception, losing
     * the entry that was being stored. The index must fall back to the start of the snapshot.
     */
    @Test
    public void testBackupIndexBeyondMemberSnapshotFallsBack() throws Exception {
        Member member = new MemberImpl("localhost", 4007, -1);

        // A member added between the two steps of the selection, shown directly: one member in
        // the snapshot, an index computed for a member list that has meanwhile grown
        LazyReplicatedMap<String,String> map = new LazyReplicatedMap<String,String>(
                (key, value) -> {
                    // Not made primary here
                }, new GroupChannel(), 1000, "TestLazyReplicatedMap", null) {

            @Override
            public Member[] getMapMembers() {
                return new Member[] { member };
            }

            @Override
            public int getNextBackupIndex() {
                return 1;
            }
        };

        // The channel is not started, so the publication to the backup fails as a channel
        // exception that the selection contains, while the entry survives to be stored locally
        Member[] backup = map.publishEntryInfo("key", "value");
        Assert.assertEquals(0, backup.length);
    }
}

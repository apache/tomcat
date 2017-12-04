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

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map.Entry;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.membership.Membership.MbrEntry;

public class TestMembership {

    @Test
    public void testClone() throws Exception {
        Member m1 = new MemberImpl("localhost", 1, 1000);
        Member m2 = new MemberImpl("localhost", 2, 1000);
        Member m3 = new MemberImpl("localhost", 3, 1000);

        Membership original = new Membership(m1);
        original.addMember(m2);
        original.addMember(m3);

        Membership clone = original.clone();

        Assert.assertFalse(original == clone);
        Assert.assertTrue(original.getClass() == clone.getClass());

        Assert.assertTrue(original.local == clone.local);

        Assert.assertFalse(original.map == clone.map);
        Assert.assertTrue(original.map.size() == clone.map.size());
        Iterator<Entry<Member, MbrEntry>> originalEntries = original.map.entrySet().iterator();
        Iterator<Entry<Member, MbrEntry>> cloneEntries = clone.map.entrySet().iterator();
        while (originalEntries.hasNext()) {
            Entry<Member, MbrEntry> originalEntry = originalEntries.next();
            Entry<Member, MbrEntry> cloneEntry = cloneEntries.next();
            Assert.assertTrue(originalEntry.getKey() == cloneEntry.getKey());
            Assert.assertTrue(originalEntry.getValue() == cloneEntry.getValue());
        }

        Assert.assertTrue(original.memberComparator == clone.memberComparator);
        Assert.assertFalse(original.members == clone.members);
        Assert.assertArrayEquals(original.members, clone.members);

        // Need to use reflection to access lock since it is a private field
        Field f = Membership.class.getDeclaredField("membersLock");
        f.setAccessible(true);
        Object originalLock = f.get(original);
        Object cloneLock = f.get(clone);
        Assert.assertFalse(originalLock == cloneLock);
    }
}

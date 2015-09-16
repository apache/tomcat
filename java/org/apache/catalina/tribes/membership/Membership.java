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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.catalina.tribes.Member;

/**
 * A <b>membership</b> implementation using simple multicast.
 * This is the representation of a multicast membership.
 * This class is responsible for maintaining a list of active cluster nodes in the cluster.
 * If a node fails to send out a heartbeat, the node will be dismissed.
 *
 * @author Peter Rossbach
 */
public class Membership implements Cloneable {

    protected static final Member[] EMPTY_MEMBERS = new Member[0];

    private final Object membersLock = new Object();

    /**
     * The local member.
     */
    protected final Member local;

    /**
     * A map of all the members in the cluster.
     */
    protected HashMap<Member, MbrEntry> map = new HashMap<>(); // Guarded by membersLock

    /**
     * A list of all the members in the cluster.
     */
    protected volatile Member[] members = EMPTY_MEMBERS; // Guarded by membersLock

    /**
     * Comparator for sorting members by alive time.
     */
    protected final Comparator<Member> memberComparator;

    @Override
    public Object clone() {
        synchronized (membersLock) {
            Membership clone = new Membership(local, memberComparator);
            @SuppressWarnings("unchecked")
            final HashMap<Member, MbrEntry> tmpclone = (HashMap<Member, MbrEntry>) map.clone();
            clone.map = tmpclone;
            clone.members = new Member[members.length];
            System.arraycopy(members, 0, clone.members, 0, members.length);
            return clone;
        }
    }

    /**
     * Constructs a new membership
     * @param local - has to be the name of the local member. Used to filter the local member from the cluster membership
     * @param includeLocal - TBA
     */
    public Membership(Member local, boolean includeLocal) {
        this(local, new MemberComparator(), includeLocal);
    }

    public Membership(Member local) {
        this(local, false);
    }

    public Membership(Member local, Comparator<Member> comp) {
        this(local, comp, false);
    }

    public Membership(Member local, Comparator<Member> comp, boolean includeLocal) {
        this.local = local;
        this.memberComparator = comp;
        if (includeLocal) {
            addMember(local);
        }
    }

    /**
     * Reset the membership and start over fresh. i.e., delete all the members
     * and wait for them to ping again and join this membership.
     */
    public void reset() {
        synchronized (membersLock) {
            map.clear();
            members = EMPTY_MEMBERS ;
        }
    }

    /**
     * Notify the membership that this member has announced itself.
     *
     * @param member - the member that just pinged us
     * @return - true if this member is new to the cluster, false otherwise.<br>
     * - false if this member is the local member or updated.
     */
    public boolean memberAlive(Member member) {
        // Ignore ourselves
        if (member.equals(local)) {
            return false;
        }

        boolean result = false;
        synchronized (membersLock) {
            MbrEntry entry = map.get(member);
            if (entry == null) {
                entry = addMember(member);
                result = true;
            } else {
                // Update the member alive time
                Member updateMember = entry.getMember();
                if (updateMember.getMemberAliveTime() != member.getMemberAliveTime()) {
                    // Update fields that can change
                    updateMember.setMemberAliveTime(member.getMemberAliveTime());
                    updateMember.setPayload(member.getPayload());
                    updateMember.setCommand(member.getCommand());
                    // Re-order. Can't sort in place since a call to
                    // getMembers() may then receive an intermediate result.
                    Member[] newMembers = new Member[members.length];
                    System.arraycopy(members, 0, newMembers, 0, members.length);
                    Arrays.sort(newMembers, memberComparator);
                    members = newMembers;
                }
            }
            entry.accessed();
        }
        return result;
    }

    /**
     * Add a member to this component and sort array with memberComparator
     *
     * @param member The member to add
     *
     * @return The member entry created for this new member.
     */
    public MbrEntry addMember(Member member) {
        MbrEntry entry = new MbrEntry(member);
        synchronized (membersLock) {
            if (!map.containsKey(member) ) {
                map.put(member, entry);
                Member results[] = new Member[members.length + 1];
                System.arraycopy(members, 0, results, 0, members.length);
                results[members.length] = member;
                Arrays.sort(results, memberComparator);
                members = results;
            }
        }
        return entry;
    }

    /**
     * Remove a member from this component.
     *
     * @param member The member to remove
     */
    public void removeMember(Member member) {
        synchronized (membersLock) {
            map.remove(member);
            int n = -1;
            for (int i = 0; i < members.length; i++) {
                if (members[i] == member || members[i].equals(member)) {
                    n = i;
                    break;
                }
            }
            if (n < 0) return;
            Member results[] = new Member[members.length - 1];
            int j = 0;
            for (int i = 0; i < members.length; i++) {
                if (i != n) {
                    results[j++] = members[i];
                }
            }
            members = results;
        }
    }

    /**
     * Runs a refresh cycle and returns a list of members that has expired.
     * This also removes the members from the membership, in such a way that
     * getMembers() = getMembers() - expire()
     * @param maxtime - the max time a member can remain unannounced before it is considered dead.
     * @return the list of expired members
     */
    public Member[] expire(long maxtime) {
        synchronized (membersLock) {
            if (!hasMembers()) {
               return EMPTY_MEMBERS;
            }

            ArrayList<Member> list = null;
            Iterator<MbrEntry> i = map.values().iterator();
            while (i.hasNext()) {
                MbrEntry entry = i.next();
                if (entry.hasExpired(maxtime)) {
                    if (list == null) {
                        // Only need a list when members are expired (smaller gc)
                        list = new java.util.ArrayList<>();
                    }
                    list.add(entry.getMember());
                }
            }

            if (list != null) {
                Member[] result = new Member[list.size()];
                list.toArray(result);
                for (int j=0; j<result.length; j++) {
                    removeMember(result[j]);
                }
                return result;
            } else {
                return EMPTY_MEMBERS ;
            }
        }
    }

    /**
     * Returning that service has members or not.
     *
     * @return <code>true</code> if there are one or more members, otherwise
     *         <code>false</code>
     */
    public boolean hasMembers() {
        return members.length > 0;
    }


    public Member getMember(Member mbr) {
        Member[] members = this.members;
        if (members.length > 0) {
            for (int i = 0; i < members.length; i++) {
                if (members[i].equals(mbr)) {
                    return members[i];
                }
            }
        }
        return null;
    }

    public boolean contains(Member mbr) {
        return getMember(mbr) != null;
    }

    /**
     * Returning a list of all the members in the membership.
     * We not need a copy: add and remove generate new arrays.
     *
     * @return An array of the current members
     */
    public Member[] getMembers() {
        return members;
    }


    // --------------------------------------------- Inner Class

    private static class MemberComparator implements Comparator<Member>, Serializable {

        private static final long serialVersionUID = 1L;

        @Override
        public int compare(Member m1, Member m2) {
            // Longer alive time, means sort first
            long result = m2.getMemberAliveTime() - m1.getMemberAliveTime();
            if (result < 0) {
                return -1;
            } else if (result == 0) {
                return 0;
            } else {
                return 1;
            }
        }
    }

    /**
     * Inner class that represents a member entry
     */
    protected static class MbrEntry {

        protected final Member mbr;
        protected long lastHeardFrom;

        public MbrEntry(Member mbr) {
           this.mbr = mbr;
        }

        /**
         * Indicate that this member has been accessed.
         */
        public void accessed(){
           lastHeardFrom = System.currentTimeMillis();
        }

        /**
         * Obtain the member associated with this entry.
         *
         * @return The member for this entry.
         */
        public Member getMember() {
            return mbr;
        }

        /**
         * Check if this member has expired.
         *
         * @param maxtime The time threshold
         *
         * @return <code>true</code> if the member has expired, otherwise
         *         <code>false</false>
         */
        public boolean hasExpired(long maxtime) {
            long delta = System.currentTimeMillis() - lastHeardFrom;
            return delta > maxtime;
        }
    }
}

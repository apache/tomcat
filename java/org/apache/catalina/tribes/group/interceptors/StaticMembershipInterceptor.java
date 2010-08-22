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

import java.util.ArrayList;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.group.AbsoluteOrder;
import org.apache.catalina.tribes.group.ChannelInterceptorBase;

public class StaticMembershipInterceptor
    extends ChannelInterceptorBase {
    protected ArrayList<Member> members = new ArrayList<Member>();
    protected Member localMember = null;

    public StaticMembershipInterceptor() {
        super();
    }

    public void addStaticMember(Member member) {
        synchronized (members) {
            if (!members.contains(member)) members.add(member);
        }
    }

    public void removeStaticMember(Member member) {
        synchronized (members) {
            if (members.contains(member)) members.remove(member);
        }
    }

    public void setLocalMember(Member member) {
        this.localMember = member;
    }

    /**
     * has members
     */
    @Override
    public boolean hasMembers() {
        return super.hasMembers() || (members.size()>0);
    }

    /**
     * Get all current cluster members
     * @return all members or empty array
     */
    @Override
    public Member[] getMembers() {
        if ( members.size() == 0 ) return super.getMembers();
        else {
            synchronized (members) {
                Member[] others = super.getMembers();
                Member[] result = new Member[members.size() + others.length];
                for (int i = 0; i < others.length; i++) result[i] = others[i];
                for (int i = 0; i < members.size(); i++) result[i + others.length] = members.get(i);
                AbsoluteOrder.absoluteOrder(result);
                return result;
            }//sync
        }//end if
    }

    /**
     *
     * @param mbr Member
     * @return Member
     */
    @Override
    public Member getMember(Member mbr) {
        if ( members.contains(mbr) ) return members.get(members.indexOf(mbr));
        else return super.getMember(mbr);
    }

    /**
     * Return the member that represents this node.
     *
     * @return Member
     */
    @Override
    public Member getLocalMember(boolean incAlive) {
        if (this.localMember != null ) return localMember;
        else return super.getLocalMember(incAlive);
    }
    
    /**
     * Send notifications upwards
     * @param svc int
     * @throws ChannelException
     */
    @Override
    public void start(int svc) throws ChannelException {
        if ( (Channel.SND_RX_SEQ&svc)==Channel.SND_RX_SEQ ) super.start(Channel.SND_RX_SEQ); 
        if ( (Channel.SND_TX_SEQ&svc)==Channel.SND_TX_SEQ ) super.start(Channel.SND_TX_SEQ); 
        final Member[] mbrs = members.toArray(new Member[members.size()]);
        final ChannelInterceptorBase base = this;
        Thread t = new Thread() {
            @Override
            public void run() {
                for (int i=0; i<mbrs.length; i++ ) {
                    base.memberAdded(mbrs[i]);
                }
            }
        };
        t.start();
        super.start(svc & (~Channel.SND_RX_SEQ) & (~Channel.SND_TX_SEQ));
    }

}
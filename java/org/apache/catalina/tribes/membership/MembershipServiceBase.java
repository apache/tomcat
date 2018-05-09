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

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.MembershipListener;
import org.apache.catalina.tribes.MembershipService;

public abstract class MembershipServiceBase implements MembershipService, MembershipListener {

    /**
     * The implementation specific properties
     */
    protected Properties properties = new Properties();
    protected volatile MembershipListener listener;
    protected Channel channel;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Properties getProperties() {
        return properties;
    }

    @Override
    public boolean hasMembers() {
        if (getMembershipProvider() == null ) return false;
        return getMembershipProvider().hasMembers();
    }

    @Override
    public Member getMember(Member mbr) {
        if (getMembershipProvider() == null) return null;
        return getMembershipProvider().getMember(mbr);
    }

    @Override
    public Member[] getMembers() {
        if (getMembershipProvider() == null) return Membership.EMPTY_MEMBERS;
        return getMembershipProvider().getMembers();
    }

    @Override
    public String[] getMembersByName() {
        Member[] currentMembers = getMembers();
        String [] membernames ;
        if(currentMembers != null) {
            membernames = new String[currentMembers.length];
            for (int i = 0; i < currentMembers.length; i++) {
                membernames[i] = currentMembers[i].toString() ;
            }
        } else
            membernames = new String[0] ;
        return membernames ;
    }

    @Override
    public Member findMemberByName(String name) {
        Member[] currentMembers = getMembers();
        for (int i = 0; i < currentMembers.length; i++) {
            if (name.equals(currentMembers[i].toString()))
                return currentMembers[i];
        }
        return null;
    }

    @Override
    public void setMembershipListener(MembershipListener listener) {
        this.listener = listener;
    }

    @Override
    public void removeMembershipListener(){
        listener = null;
    }

    @Override
    public void memberAdded(Member member) {
        MembershipListener listener = this.listener;
        if (listener != null) listener.memberAdded(member);
    }

    @Override
    public void memberDisappeared(Member member) {
        MembershipListener listener = this.listener;
        if (listener != null) listener.memberDisappeared(member);
    }

    @Override
    public void broadcast(ChannelMessage message) throws ChannelException {
        // no-op
    }

    @Override
    public Channel getChannel() {
        return channel;
    }

    @Override
    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    @Override
    public void start() throws Exception {
        start(MembershipService.MBR_RX);
        start(MembershipService.MBR_TX);
    }
}
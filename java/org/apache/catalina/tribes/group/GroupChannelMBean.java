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
package org.apache.catalina.tribes.group;

import java.io.Serializable;

import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelListener;
import org.apache.catalina.tribes.ErrorHandler;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.MembershipListener;
import org.apache.catalina.tribes.UniqueId;

public interface GroupChannelMBean {

    // Attributes
    public boolean getOptionCheck();

    public boolean getHeartbeat();

    public long getHeartbeatSleeptime();

    // Operations
    public void start(int svc) throws ChannelException;

    public void stop(int svc) throws ChannelException;

    public UniqueId send(Member[] destination, Serializable msg, int options)
            throws ChannelException;

    public UniqueId send(Member[] destination, Serializable msg, int options, ErrorHandler handler)
            throws ChannelException;

    public void addMembershipListener(MembershipListener listener);

    public void addChannelListener(ChannelListener listener);

    public void removeMembershipListener(MembershipListener listener);

    public void removeChannelListener(ChannelListener listener);

    public boolean hasMembers() ;

    public Member[] getMembers() ;

    public Member getLocalMember(boolean incAlive);

}

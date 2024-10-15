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
    boolean getOptionCheck();

    boolean getHeartbeat();

    long getHeartbeatSleeptime();

    // Operations
    void start(int svc) throws ChannelException;

    void stop(int svc) throws ChannelException;

    UniqueId send(Member[] destination, Serializable msg, int options) throws ChannelException;

    UniqueId send(Member[] destination, Serializable msg, int options, ErrorHandler handler) throws ChannelException;

    void addMembershipListener(MembershipListener listener);

    void addChannelListener(ChannelListener listener);

    void removeMembershipListener(MembershipListener listener);

    void removeChannelListener(ChannelListener listener);

    boolean hasMembers();

    Member[] getMembers();

    Member getLocalMember(boolean incAlive);

}

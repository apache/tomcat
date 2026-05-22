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

/**
 * MBean interface for managing a GroupChannel.
 */
public interface GroupChannelMBean {

    // Attributes
    /**
     * Returns whether option flag checking is enabled.
     *
     * @return {@code true} if option checking is enabled
     */
    boolean getOptionCheck();

    /**
     * Returns whether the internal heartbeat is enabled.
     *
     * @return {@code true} if heartbeat is enabled
     */
    boolean getHeartbeat();

    /**
     * Returns the heartbeat sleep interval in milliseconds.
     *
     * @return the heartbeat sleep time in milliseconds
     */
    long getHeartbeatSleeptime();

    // Operations
    /**
     * Starts the channel with the given service type.
     *
     * @param svc The service type
     * @throws ChannelException if an error occurs during start
     */
    void start(int svc) throws ChannelException;

    /**
     * Stops the channel with the given service type.
     *
     * @param svc The service type
     * @throws ChannelException if an error occurs during stop
     */
    void stop(int svc) throws ChannelException;

    /**
     * Sends a message to the specified destination members.
     *
     * @param destination The destination members
     * @param msg         The message to send
     * @param options     Send options flags
     * @return UniqueId for the sent message
     * @throws ChannelException if an error occurs during send
     */
    UniqueId send(Member[] destination, Serializable msg, int options) throws ChannelException;

    /**
     * Sends a message to the specified destination members with an error handler.
     *
     * @param destination The destination members
     * @param msg         The message to send
     * @param options     Send options flags
     * @param handler     Error handler for the send operation
     * @return UniqueId for the sent message
     * @throws ChannelException if an error occurs during send
     */
    UniqueId send(Member[] destination, Serializable msg, int options, ErrorHandler handler) throws ChannelException;

    /**
     * Adds a membership listener to receive membership change notifications.
     *
     * @param listener The membership listener to add
     */
    void addMembershipListener(MembershipListener listener);

    /**
     * Adds a channel listener to receive incoming messages.
     *
     * @param listener The channel listener to add
     */
    void addChannelListener(ChannelListener listener);

    /**
     * Removes a previously registered membership listener.
     *
     * @param listener The membership listener to remove
     */
    void removeMembershipListener(MembershipListener listener);

    /**
     * Removes a previously registered channel listener.
     *
     * @param listener The channel listener to remove
     */
    void removeChannelListener(ChannelListener listener);

    /**
     * Returns whether the channel has any members.
     *
     * @return {@code true} if there are members in the channel
     */
    boolean hasMembers();

    /**
     * Returns the current members of the channel.
     *
     * @return Array of current members
     */
    Member[] getMembers();

    /**
     * Returns the local member of the channel.
     *
     * @param incAlive Whether to update the alive timestamp
     * @return The local member
     */
    Member getLocalMember(boolean incAlive);

}

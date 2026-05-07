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

import org.apache.catalina.tribes.Member;

/**
 * MBean interface for McastService.
 */
public interface McastServiceMBean {

    // Attributes
    /**
     * Get the multicast address.
     * @return the address
     */
    String getAddress();

    /**
     * Get the multicast port.
     * @return the port
     */
    int getPort();

    /**
     * Get the heartbeat frequency.
     * @return the frequency
     */
    long getFrequency();

    /**
     * Get the drop time.
     * @return the drop time
     */
    long getDropTime();

    /**
     * Get the bind address.
     * @return the bind address
     */
    String getBind();

    /**
     * Get the TTL.
     * @return the TTL
     */
    int getTtl();

    /**
     * Get the domain.
     * @return the domain
     */
    byte[] getDomain();

    /**
     * Get the socket timeout.
     * @return the socket timeout
     */
    int getSoTimeout();

    /**
     * Get whether recovery is enabled.
     * @return whether recovery is enabled
     */
    boolean getRecoveryEnabled();

    /**
     * Get the recovery counter.
     * @return the recovery counter
     */
    int getRecoveryCounter();

    /**
     * Get the recovery sleep time.
     * @return the recovery sleep time
     */
    long getRecoverySleepTime();

    /**
     * Get whether local loopback is disabled.
     * @return whether local loopback is disabled
     */
    boolean getLocalLoopbackDisabled();

    /**
     * Get the local member name.
     * @return the local member name
     */
    String getLocalMemberName();

    // Operation
    /**
     * Get the properties.
     * @return the properties
     */
    Properties getProperties();

    /**
     * Check if there are members.
     * @return true if there are members
     */
    boolean hasMembers();

    /**
     * Get the member names.
     * @return the member names
     */
    String[] getMembersByName();

    /**
     * Find a member by name.
     * @param name the member name
     * @return the member
     */
    Member findMemberByName(String name);
}

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
 * MBean interface for the static membership service.
 */
public interface StaticMembershipServiceMBean {

    // Attributes
    /**
     * Returns the member expiration time in milliseconds.
     * @return the expiration time
     */
    long getExpirationTime();

    /**
     * Returns the connection timeout in milliseconds.
     * @return the connection timeout
     */
    int getConnectTimeout();

    /**
     * Returns the RPC timeout in milliseconds.
     * @return the RPC timeout
     */
    long getRpcTimeout();

    /**
     * Returns whether a background ping thread is in use.
     * @return true if a background thread is used
     */
    boolean getUseThread();

    /**
     * Returns the ping interval in milliseconds.
     * @return the ping interval
     */
    long getPingInterval();

    // Operation
    /**
     * Returns the configuration properties.
     * @return the properties
     */
    Properties getProperties();

    /**
     * Checks if there are any members in the cluster.
     * @return true if there are members
     */
    boolean hasMembers();

    /**
     * Returns the names of all cluster members.
     * @return array of member names
     */
    String[] getMembersByName();

    /**
     * Finds a member by name.
     * @param name the member name
     * @return the member, or null if not found
     */
    Member findMemberByName(String name);
}

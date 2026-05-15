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
package org.apache.catalina.tribes.membership.cloud;

import java.util.Properties;

import org.apache.catalina.tribes.Member;

/**
 * MBean interface for the CloudMembershipService.
 */
public interface CloudMembershipServiceMBean {

    // Attributes
    /**
     * Returns the connection timeout in milliseconds.
     *
     * @return the connection timeout
     */
    int getConnectTimeout();

    /**
     * Returns the read timeout in milliseconds.
     *
     * @return the read timeout
     */
    int getReadTimeout();

    /**
     * Returns the member expiration time in milliseconds.
     *
     * @return the expiration time
     */
    long getExpirationTime();

    // Operation
    /**
     * Returns the properties for this service.
     *
     * @return the properties
     */
    Properties getProperties();

    /**
     * Returns whether there are members in the cluster.
     *
     * @return {@code true} if there are members
     */
    boolean hasMembers();

    /**
     * Returns the names of all members in the cluster.
     *
     * @return the member names
     */
    String[] getMembersByName();

    /**
     * Finds a member by name.
     *
     * @param name the member name
     *
     * @return the member, or {@code null} if not found
     */
    Member findMemberByName(String name);
}

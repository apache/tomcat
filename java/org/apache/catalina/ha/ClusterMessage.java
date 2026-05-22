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
package org.apache.catalina.ha;

import java.io.Serializable;

import org.apache.catalina.tribes.Member;

/**
 * Interface for messages exchanged between cluster nodes.
 */
public interface ClusterMessage extends Serializable {
    /**
     * Returns the member associated with this message.
     *
     * @return the member address
     */
    Member getAddress();

    /**
     * Set the member associated with the message.
     *
     * @param member the member
     */
    void setAddress(Member member);

    /**
     * Returns the unique identifier for this message.
     *
     * @return the unique ID
     */
    String getUniqueId();

    /**
     * Returns the timestamp of this message.
     *
     * @return the timestamp in milliseconds
     */
    long getTimestamp();

    /**
     * Set the timestamp for this message.
     *
     * @param timestamp the timestamp
     */
    void setTimestamp(long timestamp);
}

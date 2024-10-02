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
package org.apache.catalina.tribes;

import java.util.Properties;

public interface MembershipProvider {

    /**
     * Initialize the membership provider with the specified configuration.
     *
     * @param properties configuration
     *
     * @throws Exception if an error occurs
     */
    void init(Properties properties) throws Exception;

    /**
     * Start the membership provider.
     *
     * @param level the readiness level
     *                  <ul>
     *                  <li>Channel.DEFAULT - will start all services</li>
     *                  <li>Channel.MBR_RX_SEQ - starts the membership receiver</li>
     *                  <li>Channel.MBR_TX_SEQ - starts the membership broadcaster</li>
     *                  </ul>
     *
     * @throws Exception if an error occurs
     */
    void start(int level) throws Exception;

    /**
     * Stop the membership provider.
     *
     * @param level the readiness level
     *                  <ul>
     *                  <li>Channel.DEFAULT - will stop all services</li>
     *                  <li>Channel.MBR_RX_SEQ - stops the membership receiver</li>
     *                  <li>Channel.MBR_TX_SEQ - stops the membership broadcaster</li>
     *                  </ul>
     *
     * @return {@code true} if successful
     *
     * @throws Exception if an error occurs
     */
    boolean stop(int level) throws Exception;

    /**
     * Set the associated membership listener.
     *
     * @param listener the listener
     */
    void setMembershipListener(MembershipListener listener);

    /**
     * Set the associated membership service.
     *
     * @param service the service
     */
    void setMembershipService(MembershipService service);

    /**
     * @return {@code true} if there are members
     */
    boolean hasMembers();

    /**
     * Get the specified member from the associated membership.
     *
     * @param mbr the member
     *
     * @return the member
     */
    Member getMember(Member mbr);

    /**
     * Get the members from the associated membership.
     *
     * @return the members
     */
    Member[] getMembers();
}

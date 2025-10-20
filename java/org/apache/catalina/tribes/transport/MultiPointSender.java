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
package org.apache.catalina.tribes.transport;

import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.Member;

public interface MultiPointSender extends DataSender {

    /**
     * Send the specified message.
     *
     * @param destination the message destinations
     * @param data        the data to send
     *
     * @throws ChannelException if an error occurs
     */
    void sendMessage(Member[] destination, ChannelMessage data) throws ChannelException;

    /**
     * Set the maximum retry attempts.
     *
     * @param attempts the retry count
     */
    void setMaxRetryAttempts(int attempts);

    /**
     * Configure the use of a direct buffer.
     *
     * @param directBuf {@code true} to use a direct buffer
     */
    void setDirectBuffer(boolean directBuf);

    /**
     * Send to the specified member.
     *
     * @param member the member
     */
    void add(Member member);

    /**
     * Stop sending to the specified member.
     *
     * @param member the member
     */
    void remove(Member member);

}

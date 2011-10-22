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
package org.apache.cometd.bayeux;

import java.util.Map;

/**
 * A Bayeux Message<br/>
 * A Bayeux message is a Map of String/Object key value pairs representing the data in the message.
 * The message contains information about the channel it was published through and who the sender was
 *
 * @author Greg Wilkins
 * @author Filip Hanik
 */
public interface Message extends Map<String,Object>
{
    /**
     * Returns a reference to the client that sent this message
     * @return Client - may be null
     */
    public Client getClient();
    /**
     * Returns a reference to the channel that this message was published throuhg
     * @return Channel - may be null
     */
    public Channel getChannel();
    /**
     * Returns the unique id of this message
     * @return String
     */
    public String getId();

    /**
     * Sets the time to live in milliseconds. If the message hasn't been delivered
     * when the time passed after the creation time is longer than the TTL the message will
     * expire and removed from any delivery queues.
     * @param ttl long
     */
    public void setTTL(long ttl);

    /**
     * Returns the time to live (in milliseconds) for this message
     * @return long
     */
    public long getTTL();

    /**
     * returns the timestamp in milliseconds(System.currentTimeMillis()) of when this message was created.
     * @return long
     */
    public long getCreationTime();
}



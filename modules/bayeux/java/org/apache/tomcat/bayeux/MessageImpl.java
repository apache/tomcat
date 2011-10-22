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
package org.apache.tomcat.bayeux;

import java.util.HashMap;

import org.apache.cometd.bayeux.Channel;
import org.apache.cometd.bayeux.Client;
import org.apache.cometd.bayeux.Message;

public class MessageImpl extends HashMap<String,Object> implements Message {

    protected Channel channel;
    protected Client client;
    protected String id;
    private long TTL = 1000*60*5; //5min is the default TTL for a message
    protected long creationTime = System.currentTimeMillis();

    public Object clone() {
        MessageImpl copy = new MessageImpl(id);
        copy.putAll(this);
        copy.channel = channel;
        copy.client = client;
        copy.id = id;
        copy.creationTime = creationTime;
        copy.TTL = TTL;
        return copy;
    }

    protected MessageImpl(String id) {
        assert id != null;
        this.id = id;
    }

    public Channel getChannel() {
        return channel;
    }

    public Client getClient() {
        return client;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public long getTTL() {
        return TTL;
    }

    public String getId() {
        return id;
    }

    protected void setChannel(Channel channel) {
        this.channel = channel;
    }

    protected void setClient(Client client) {
        this.client = client;
    }

    public void setTTL(long TTL) {
        this.TTL = TTL;
    }
}
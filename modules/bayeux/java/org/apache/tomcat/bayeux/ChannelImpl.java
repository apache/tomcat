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

import java.util.LinkedList;

import org.apache.cometd.bayeux.Channel;
import org.apache.cometd.bayeux.Client;
import org.apache.cometd.bayeux.DataFilter;
import java.util.Collections;
import java.util.List;
import org.apache.cometd.bayeux.Message;
import java.util.Iterator;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
/**
 *
 * @author Filip Hanik
 * @version 1.0
 */
public class ChannelImpl implements Channel {

    private static final Log log = LogFactory.getLog(ChannelImpl.class);

    /**
     * The unique id of this channel
     */
    protected String id = null;

    /**
     * A list of the current subscribers
     */
    protected LinkedList<Client> subscribers = new LinkedList<Client>();

    /**
     * A list of the current filters
     */
    protected LinkedList<DataFilter> filters = new LinkedList<DataFilter>();

    /**
     * Is this channel persistent, default value is true
     */
    protected boolean persistent = true;

    /**
     * Creates a new channel
     * @param id String - the id of the channel, can not be null
     */
    protected ChannelImpl(String id) {
        assert id != null;
        this.id = id;
    }

    /**
     * returns the id of this channel
     * @return String
     */
    public String getId() {
        return id;
    }

    /**
     * Returns true if this channel matches the pattern to its id.
     * The channel pattern can be a complete name like <code>/service/mychannel</code>
     * or it can be a wild card pattern like <code>/service/app2/**</code>
     * @param pattern String according to the Bayeux specification section 2.2.1 Channel Globbing, can not be null.
     * @return boolean true if the id of this channel matches the pattern
     */
    public boolean matches(String pattern) {
        if (pattern == null)
            throw new NullPointerException("Channel pattern must not be null.");
        if (getId().equals(pattern))
            return true;
        int wildcardPos = pattern.indexOf("/*");
        if (wildcardPos == -1)
            return false;
        boolean multiSegment = pattern.indexOf("**") != -1;
        String leadSubstring = pattern.substring(0, wildcardPos);
        if (leadSubstring == null)
            return false;
        if (multiSegment)
            return getId().startsWith(leadSubstring);
        else {
            if (getId().length() <= wildcardPos + 2)
                return false;
            return !(getId().substring(wildcardPos + 2).contains("/"));
        }
    }



    /**
     * @return returns a non modifiable list of the subscribers for this channel.
     */
    public List<Client> getSubscribers() {
        return Collections.unmodifiableList(subscribers);
    }

    /**
     * @return true if the Channel will persist without any subscription.
     */
    public boolean isPersistent() {
        return persistent;
    }

    public void publish(Message msg) {
        publish(new Message[] {msg});
    }

    public void publish(Message[] msgs) {
        if (msgs==null) return;
        MessageImpl[] imsgs = new MessageImpl[msgs.length];
        for (int i=0; msgs!=null && i<msgs.length; i++) {
            Message data = msgs[i];

            if (!(data instanceof MessageImpl))
                throw new IllegalArgumentException("Invalid message class, you can only publish messages "+
                                                   "created through the Bayeux.newMessage() method");
            if (log.isDebugEnabled()) {
                log.debug("Publishing message:"+data+" to channel:"+this);
            }
            //clone it so that we can set this channel as a reference
            MessageImpl msg = (MessageImpl)((MessageImpl)data).clone();
            //this is the channel it was delivered through
            msg.setChannel(this);
            //pass through filters
            for (Iterator<DataFilter> it = filters.iterator(); it.hasNext(); ) {
                it.next().filter(msg);
            }
            imsgs[i] = msg;
        }
        //deliver it to the clients
        for (Iterator<Client> it = subscribers.iterator(); it.hasNext(); ) {
            ClientImpl c = (ClientImpl)it.next();
            c.deliverInternal(this,imsgs);
        }

    }

    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    public void subscribe(Client subscriber) {
        if (!subscribers.contains((subscriber))) {
            subscribers.addLast(subscriber);
            ((ClientImpl)subscriber).subscribed(this);
        }
    }

    public Client unsubscribe(Client subscriber) {
        if (subscribers.remove(subscriber)) {
            ((ClientImpl)subscriber).unsubscribed(this);
            return subscriber;
        } else
            return null;
    }

    public void addFilter(DataFilter filter) {
        if (!filters.contains(filter))
            filters.addLast(filter);
    }

    public DataFilter removeFilter(DataFilter filter) {
        if ( filters.remove(filter) ) return filter;
        else return null;
    }

    public String toString() {
        StringBuilder buf = new StringBuilder(super.toString());
        buf.append("; channelId=").append(getId());
        return buf.toString();
    }

}
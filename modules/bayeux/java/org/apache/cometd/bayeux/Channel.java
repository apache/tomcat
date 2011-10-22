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

import java.util.List;

/**
 * A Bayeux Channel represents a channel used to receive messages from and to publish messages to.
 * In order to publish messages to or receive messages from, one must subscribe to the channel.
 * This is easily done by invoking the <code>subscribe</code> method.
 * A channel is created by calling the <code>Bayeux.getChannel(channelId,true)</code> method.
 * A channel can be created either server side by invoking the getChannel, or client side
 * by using the /meta/subscribe message without a wildcard.
 * @author Greg Wilkins
 * @author Filip Hanik
 */
public interface Channel
{
    /**
     * Returns the id for this channel. The id is unique within bayeux session.
     * @return String - will never be null.
     */
    public String getId();

    /**
     * Publishes a message to all the subscribers of this channel.
     * The <code>from</code> is contained within the message, by calling
     * <code>msg.getClient()</code>
     * @param data - the message to be published, can not be null.
     */
    public void publish(Message msg);

    /**
     * Publishes more than one message to all the subscribers of this channel.
     * The <code>from</code> is contained within the message, by calling
     * <code>msg[x].getClient()</code>
     * @param data - the message to be published, can not be null.
     */
    public void publish(Message[] msgs);

    /**
     * Non persistent channels are removed when the last subscription is
     * removed. Persistent channels survive periods without any subscribers.
     * @return true if the Channel will persist without any subscription.
     */
    public boolean isPersistent();

    /**
     * @param persistent true if the Channel will persist without any subscription.
     * @see isPersistent
     */
    public void setPersistent(boolean persistent);

    /**
     * Subscribes a client to a channel.
     * @param subscriber - the client to be subscribed. If the client
     * already is subscribed, this call will not create a duplicate subscription.
     */
    public void subscribe(Client subscriber);

    /**
     * Unsubscribes a client from a channel
     * @param subscriber - the client to be subscribed.
     * @return - returns the client that was unsubscribed, or null if the client wasn't subscribed.
     */
    public Client unsubscribe(Client subscriber);

    /**
     * returns a non modifiable list of all the subscribers to this
     * channel.
     * @return a list of subscribers
     */
    public List<Client> getSubscribers();

    /**
     * Adds a data filter to this channel. All messages received by this channel
     * will run through this filter.
     * @param filter Filter
     */
    public void addFilter(DataFilter filter);

    /**
     * Removes a filter from this channel.
     * returns the filter that was removed, or null if the filter wasn't in the channel.
     * @param filter Filter
     * @return Filter - null if no filter was removed otherwise it returns the filter that was removed.
     */
    public DataFilter removeFilter(DataFilter filter);
}
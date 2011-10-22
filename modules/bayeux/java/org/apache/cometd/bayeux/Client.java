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



/** A Bayeux Client.
 * <p>
 * A client may subscribe to channels and publish messages to channels.
 * Client instances should not be directly created by uses, but should
 * be obtained via the {@link Bayeux#getClient(String)} or {@link Bayeux#newClient(String, Listener)}
 * methods.
 * </p>
 * <p>
 * Three types of client may be represented by this interface:<nl>
 * <li>The server representation of a remote client connected via HTTP,
 *     automatically created by the Bayeux server when a connect message comes in</li>
 * <li>A server side client, created by the application using the {@link Bayeux#newClient(String, Listener)} method</li>
 * <li>A java client connected to a remote Bayeux server - not implemented</li>
 * </nl>
 * @author Greg Wilkins
 * @author Filip Hanik
 */
public interface Client
{
    /**
     * Returns a unique id for this client. The id is unique within this Bayeux session.
     * @return String - will not be null
     */
    public String getId();

    /**
     * Returns true if this client is holding messages to be delivered to the remote client.
     * This method always returns false for local clients, since messages are delivered instantly using the
     * Listener(callback) object
     * @return boolean
     */
    public boolean hasMessages();

    /**
     * Deliver a message to this client only
     * Deliver a message directly to the client. The message is not
     * filtered or published to a channel.
     * @param message
     */
    public void deliver(Message message);

    /**
     * Deliver a batch of messages to this client only
     * Deliver a batch messages directly to the client. The messages are not
     * filtered or published to a channel.
     * @param message
     */
    public void deliver(Message[] message);

    /**
     * @return True if the client is local. False if this client is either a remote HTTP client or
     * a java client to a remote server.
     */
    public boolean isLocal();

    /**
     * Starts a batch, no messages will be delivered until endBatch is called.
     * Batches can be nested, and messages will only be delivered after
     * the last endBatch has been called.
     */
    public void startBatch();

    /**
     * Ends a batch. since batches can be nested, messages will only be delivered
     * after the endBatch has been called as many times as startBatch has.
     */
    public void endBatch();


}
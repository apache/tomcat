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

import org.apache.catalina.tribes.group.InterceptorPayload;

/**
 * A ChannelInterceptor is an interceptor that intercepts messages and membership messages in the channel stack. This
 * allows interceptors to modify the message or perform other actions when a message is sent or received.
 * <p>
 * Interceptors are tied together in a linked list.
 *
 * @see org.apache.catalina.tribes.group.ChannelInterceptorBase
 */
public interface ChannelInterceptor extends MembershipListener, Heartbeat {

    /**
     * An interceptor can react to a message based on a set bit on the message options. When a message is sent, the
     * options can be retrieved from ChannelMessage.getOptions() and if the bit is set, this interceptor will react to
     * it.
     * <p>
     * A simple evaluation if an interceptor should react to the message would be: <br>
     * <code>boolean react = (getOptionFlag() == (getOptionFlag() &amp; ChannelMessage.getOptions()));</code> <br>
     * The default option is 0, meaning there is no way for the application to trigger the interceptor. The interceptor
     * itself will decide.
     *
     * @return int
     *
     * @see ChannelMessage#getOptions()
     */
    int getOptionFlag();

    /**
     * Sets the option flag
     *
     * @param flag int
     *
     * @see #getOptionFlag()
     */
    void setOptionFlag(int flag);

    /**
     * Set the next interceptor in the list of interceptors
     *
     * @param next ChannelInterceptor
     */
    void setNext(ChannelInterceptor next);

    /**
     * Retrieve the next interceptor in the list
     *
     * @return ChannelInterceptor - returns the next interceptor in the list or null if no more interceptors exist
     */
    ChannelInterceptor getNext();

    /**
     * Set the previous interceptor in the list
     *
     * @param previous ChannelInterceptor
     */
    void setPrevious(ChannelInterceptor previous);

    /**
     * Retrieve the previous interceptor in the list
     *
     * @return ChannelInterceptor - returns the previous interceptor in the list or null if no more interceptors exist
     */
    ChannelInterceptor getPrevious();

    /**
     * The <code>sendMessage</code> method is called when a message is being sent to one more destinations. The
     * interceptor can modify any of the parameters and then pass on the message down the stack by invoking
     * <code>getNext().sendMessage(destination,msg,payload)</code>.
     * <p>
     * Alternatively the interceptor can stop the message from being sent by not invoking
     * <code>getNext().sendMessage(destination,msg,payload)</code>.
     * <p>
     * If the message is to be sent asynchronous the application can be notified of completion and errors by passing in
     * an error handler attached to a payload object.
     * <p>
     * The ChannelMessage.getAddress contains Channel.getLocalMember, and can be overwritten to simulate a message sent
     * from another node.
     *
     * @param destination Member[] - the destination for this message
     * @param msg         ChannelMessage - the message to be sent
     * @param payload     InterceptorPayload - the payload, carrying an error handler and future useful data, can be
     *                        null
     *
     * @throws ChannelException if a serialization error happens.
     *
     * @see ErrorHandler
     * @see InterceptorPayload
     */
    void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload) throws ChannelException;

    /**
     * The <code>messageReceived</code> is invoked when a message is received. <code>ChannelMessage.getAddress()</code>
     * is the sender, or the reply-to address if it has been overwritten.
     *
     * @param data ChannelMessage
     */
    void messageReceived(ChannelMessage data);

    /**
     * The <code>heartbeat()</code> method gets invoked periodically to allow interceptors to clean up resources, time
     * out object and perform actions that are unrelated to sending/receiving data.
     */
    @Override
    void heartbeat();

    /**
     * Intercepts the <code>Channel.hasMembers()</code> method
     *
     * @return boolean - if the channel has members in its membership group
     *
     * @see Channel#hasMembers()
     */
    boolean hasMembers();

    /**
     * Intercepts the <code>Channel.getMembers()</code> method
     *
     * @return the members
     *
     * @see Channel#getMembers()
     */
    Member[] getMembers();

    /**
     * Intercepts the <code>Channel.getLocalMember(boolean)</code> method
     *
     * @param incAliveTime boolean
     *
     * @return the member that represents this node
     *
     * @see Channel#getLocalMember(boolean)
     */
    Member getLocalMember(boolean incAliveTime);

    /**
     * Intercepts the <code>Channel.getMember(Member)</code> method
     *
     * @param mbr Member
     *
     * @return Member - the actual member information, including stay alive
     *
     * @see Channel#getMember(Member)
     */
    Member getMember(Member mbr);

    /**
     * Starts up the channel. This can be called multiple times for individual services to start The svc parameter can
     * be the logical or value of any constants
     *
     * @param svc one of:
     *                <ul>
     *                <li>Channel.DEFAULT - will start all services</li>
     *                <li>Channel.MBR_RX_SEQ - starts the membership receiver</li>
     *                <li>Channel.MBR_TX_SEQ - starts the membership broadcaster</li>
     *                <li>Channel.SND_TX_SEQ - starts the replication transmitter</li>
     *                <li>Channel.SND_RX_SEQ - starts the replication receiver</li>
     *                </ul>
     *
     * @throws ChannelException if a startup error occurs or the service is already started.
     *
     * @see Channel
     */
    void start(int svc) throws ChannelException;

    /**
     * Shuts down the channel. This can be called multiple times for individual services to shut down.
     * The svc parameter can be the logical or value of any constants
     *
     * @param svc one of:
     *                <ul>
     *                <li>Channel.DEFAULT - will shut down all services</li>
     *                <li>Channel.MBR_RX_SEQ - stops the membership receiver</li>
     *                <li>Channel.MBR_TX_SEQ - stops the membership broadcaster</li>
     *                <li>Channel.SND_TX_SEQ - stops the replication transmitter</li>
     *                <li>Channel.SND_RX_SEQ - stops the replication receiver</li>
     *                </ul>
     *
     * @throws ChannelException if a startup error occurs or the service is already started.
     *
     * @see Channel
     */
    void stop(int svc) throws ChannelException;

    /**
     * Fire an event.
     *
     * @param event the event
     */
    void fireInterceptorEvent(InterceptorEvent event);

    /**
     * Return the channel that is related to this interceptor
     *
     * @return Channel
     */
    Channel getChannel();

    /**
     * Set the channel that is related to this interceptor
     *
     * @param channel The channel
     */
    void setChannel(Channel channel);

    interface InterceptorEvent {
        int getEventType();

        String getEventTypeDesc();

        ChannelInterceptor getInterceptor();
    }
}

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

/**
 * The Member interface, defines a member in the group.
 * Each member can carry a set of properties, defined by the actual implementation.<BR>
 * A member is identified by the host/ip/uniqueId<br>
 * The host is what interface the member is listening to, to receive data<br>
 * The port is what port the member is listening to, to receive data<br>
 * The uniqueId defines the session id for the member. This is an important feature
 * since a member that has crashed and the starts up again on the same port/host is
 * not guaranteed to be the same member, so no state transfers will ever be confused
 */
public interface Member {

    /**
     * When a member leaves the cluster, the payload of the memberDisappeared member
     * will be the following bytes. This indicates a soft shutdown, and not a crash
     */
    public static final byte[] SHUTDOWN_PAYLOAD = new byte[] {66, 65, 66, 89, 45, 65, 76, 69, 88};

    /**
     * Returns the name of this node, should be unique within the group.
     */
    public String getName();

    /**
     * Returns the listen host for the ChannelReceiver implementation
     * @return IPv4 or IPv6 representation of the host address this member listens to incoming data
     * @see ChannelReceiver
     */
    public byte[] getHost();

    /**
     * Returns the listen port for the ChannelReceiver implementation
     * @return the listen port for this member, -1 if its not listening on an insecure port
     * @see ChannelReceiver
     */
    public int getPort();

    /**
     * Returns the secure listen port for the ChannelReceiver implementation.
     * Returns -1 if its not listening to a secure port.
     * @return the listen port for this member, -1 if its not listening on a secure port
     * @see ChannelReceiver
     */
    public int getSecurePort();

    /**
     * Returns the UDP port that this member is listening to for UDP messages.
     * @return the listen UDP port for this member, -1 if its not listening on a UDP port
     */
    public int getUdpPort();


    /**
     * Contains information on how long this member has been online.
     * The result is the number of milli seconds this member has been
     * broadcasting its membership to the group.
     * @return nr of milliseconds since this member started.
     */
    public long getMemberAliveTime();

    public void setMemberAliveTime(long memberAliveTime);

    /**
     * The current state of the member
     * @return boolean - true if the member is functioning correctly
     */
    public boolean isReady();
    /**
     * The current state of the member
     * @return boolean - true if the member is suspect, but the crash has not been confirmed
     */
    public boolean isSuspect();

    /**
     *
     * @return boolean - true if the member has been confirmed to malfunction
     */
    public boolean isFailing();

    /**
     * returns a UUID unique for this member over all sessions.
     * If the member crashes and restarts, the uniqueId will be different.
     * @return byte[]
     */
    public byte[] getUniqueId();

    /**
     * returns the payload associated with this member
     * @return byte[]
     */
    public byte[] getPayload();

    public void setPayload(byte[] payload);

    /**
     * returns the command associated with this member
     * @return byte[]
     */
    public byte[] getCommand();

    public void setCommand(byte[] command);

    /**
     * Domain for this cluster
     * @return byte[]
     */
    public byte[] getDomain();

    /**
     * Highly optimized version of serializing a member into a byte array
     * Returns a cached byte[] reference, do not modify this data
     * @param getalive  calculate memberAlive time
     */
    public byte[] getData(boolean getalive);

    /**
     * Highly optimized version of serializing a member into a byte array
     * Returns a cached byte[] reference, do not modify this data
     * @param getalive  calculate memberAlive time
     * @param reset     reset the cached data package, and create a new one
     */
    public byte[] getData(boolean getalive, boolean reset);

    /**
     * Length of a message obtained by {@link #getData(boolean)} or
     * {@link #getData(boolean, boolean)}.
     */
    public int getDataLength();
}

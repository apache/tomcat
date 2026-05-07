/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.coyote.http11.upgrade;

/**
 * Structure to hold statistical information about connections that have been established using the HTTP/1.1 upgrade
 * mechanism. Bytes sent/received will always be populated. Messages sent/received will be populated if that makes sense
 * for the protocol and the information is exposed by the protocol implementation.
 */
public class UpgradeInfo {

    /**
     * Constructs a new UpgradeInfo.
     */
    public UpgradeInfo() {
    }

    /**
     * The parent group info for aggregated statistics.
     */
    private UpgradeGroupInfo groupInfo = null;
    /**
     * Counter for bytes sent.
     */
    private volatile long bytesSent = 0;
    /**
     * Counter for bytes received.
     */
    private volatile long bytesReceived = 0;
    /**
     * Counter for messages sent.
     */
    private volatile long msgsSent = 0;
    /**
     * Counter for messages received.
     */
    private volatile long msgsReceived = 0;


    /**
     * Returns the parent group info.
     *
     * @return the group info
     */
    public UpgradeGroupInfo getGlobalProcessor() {
        return groupInfo;
    }


    /**
     * Sets the parent group info.
     *
     * @param groupInfo the group info
     */
    public void setGroupInfo(UpgradeGroupInfo groupInfo) {
        if (groupInfo == null) {
            if (this.groupInfo != null) {
                this.groupInfo.removeUpgradeInfo(this);
                this.groupInfo = null;
            }
        } else {
            this.groupInfo = groupInfo;
            groupInfo.addUpgradeInfo(this);
        }
    }


    /**
     * Returns the number of bytes sent.
     *
     * @return the bytes sent
     */
    public long getBytesSent() {
        return bytesSent;
    }

    /**
     * Resets the bytes sent counter.
     *
     * @param bytesSent the new value
     */
    public void setBytesSent(long bytesSent) {
        this.bytesSent = bytesSent;
    }

    /**
     * Adds to the bytes sent counter.
     *
     * @param bytesSent the amount to add
     */
    public void addBytesSent(long bytesSent) {
        this.bytesSent += bytesSent;
    }


    /**
     * Returns the number of bytes received.
     *
     * @return the bytes received
     */
    public long getBytesReceived() {
        return bytesReceived;
    }

    /**
     * Resets the bytes received counter.
     *
     * @param bytesReceived the new value
     */
    public void setBytesReceived(long bytesReceived) {
        this.bytesReceived = bytesReceived;
    }

    /**
     * Adds to the bytes received counter.
     *
     * @param bytesReceived the amount to add
     */
    public void addBytesReceived(long bytesReceived) {
        this.bytesReceived += bytesReceived;
    }


    /**
     * Returns the number of messages sent.
     *
     * @return the messages sent
     */
    public long getMsgsSent() {
        return msgsSent;
    }

    /**
     * Resets the messages sent counter.
     *
     * @param msgsSent the new value
     */
    public void setMsgsSent(long msgsSent) {
        this.msgsSent = msgsSent;
    }

    /**
     * Adds to the messages sent counter.
     *
     * @param msgsSent the amount to add
     */
    public void addMsgsSent(long msgsSent) {
        this.msgsSent += msgsSent;
    }


    /**
     * Returns the number of messages received.
     *
     * @return the messages received
     */
    public long getMsgsReceived() {
        return msgsReceived;
    }

    /**
     * Resets the messages received counter.
     *
     * @param msgsReceived the new value
     */
    public void setMsgsReceived(long msgsReceived) {
        this.msgsReceived = msgsReceived;
    }

    /**
     * Adds to the messages received counter.
     *
     * @param msgsReceived the amount to add
     */
    public void addMsgsReceived(long msgsReceived) {
        this.msgsReceived += msgsReceived;
    }
}

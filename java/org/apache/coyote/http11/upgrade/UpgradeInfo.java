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

import java.util.concurrent.atomic.LongAdder;

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
    private final LongAdder bytesSent = new LongAdder();

    /**
     * Counter for bytes received.
     */
    private final LongAdder bytesReceived = new LongAdder();

    /**
     * Counter for messages sent.
     */
    private final LongAdder msgsSent = new LongAdder();

    /**
     * Counter for messages received.
     */
    private final LongAdder msgsReceived = new LongAdder();


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
        return bytesSent.longValue();
    }

    /**
     * Resets the bytes sent counter.
     *
     * @param bytesSent the new value
     */
    public void setBytesSent(long bytesSent) {
        this.bytesSent.reset();
        if (bytesSent > 0) {
            this.bytesSent.add(bytesSent);
        }
    }

    /**
     * Adds to the bytes sent counter.
     *
     * @param bytesSent the amount to add
     */
    public void addBytesSent(long bytesSent) {
        this.bytesSent.add(bytesSent);
    }


    /**
     * Returns the number of bytes received.
     *
     * @return the bytes received
     */
    public long getBytesReceived() {
        return bytesReceived.longValue();
    }

    /**
     * Resets the bytes received counter.
     *
     * @param bytesReceived the new value
     */
    public void setBytesReceived(long bytesReceived) {
        this.bytesReceived.reset();
        if (bytesReceived > 0) {
            this.bytesReceived.add(bytesReceived);
        }
    }

    /**
     * Adds to the bytes received counter.
     *
     * @param bytesReceived the amount to add
     */
    public void addBytesReceived(long bytesReceived) {
        this.bytesReceived.add(bytesReceived);
    }


    /**
     * Returns the number of messages sent.
     *
     * @return the messages sent
     */
    public long getMsgsSent() {
        return msgsSent.longValue();
    }

    /**
     * Resets the messages sent counter.
     *
     * @param msgsSent the new value
     */
    public void setMsgsSent(long msgsSent) {
        this.msgsSent.reset();
        if (msgsSent > 0) {
            this.msgsSent.add(msgsSent);
        }
    }

    /**
     * Adds to the messages sent counter.
     *
     * @param msgsSent the amount to add
     */
    public void addMsgsSent(long msgsSent) {
        this.msgsSent.add(msgsSent);
    }


    /**
     * Returns the number of messages received.
     *
     * @return the messages received
     */
    public long getMsgsReceived() {
        return msgsReceived.longValue();
    }

    /**
     * Resets the messages received counter.
     *
     * @param msgsReceived the new value
     */
    public void setMsgsReceived(long msgsReceived) {
        this.msgsReceived.reset();
        if (msgsReceived > 0) {
            this.msgsReceived.add(msgsReceived);
        }
    }

    /**
     * Adds to the messages received counter.
     *
     * @param msgsReceived the amount to add
     */
    public void addMsgsReceived(long msgsReceived) {
        this.msgsReceived.add(msgsReceived);
    }
}

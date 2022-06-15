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
 * Structure to hold statistical information about connections that have been
 * established using the HTTP/1.1 upgrade mechanism. Bytes sent/received will
 * always be populated. Messages sent/received will be populated if that makes
 * sense for the protocol and the information is exposed by the protocol
 * implementation.
 */
public class UpgradeInfo  {

    private UpgradeGroupInfo groupInfo = null;
    private volatile long bytesSent = 0;
    private volatile long bytesReceived = 0;
    private volatile long msgsSent = 0;
    private volatile long msgsReceived = 0;



    public UpgradeGroupInfo getGlobalProcessor() {
        return groupInfo;
    }


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


    public long getBytesSent() {
        return bytesSent;
    }
    public void setBytesSent(long bytesSent) {
        this.bytesSent = bytesSent;
    }
    public void addBytesSent(long bytesSent) {
        this.bytesSent += bytesSent;
    }


    public long getBytesReceived() {
        return bytesReceived;
    }
    public void setBytesReceived(long bytesReceived) {
        this.bytesReceived = bytesReceived;
    }
    public void addBytesReceived(long bytesReceived) {
        this.bytesReceived += bytesReceived;
    }


    public long getMsgsSent() {
        return msgsSent;
    }
    public void setMsgsSent(long msgsSent) {
        this.msgsSent = msgsSent;
    }
    public void addMsgsSent(long msgsSent) {
        this.msgsSent += msgsSent;
    }


    public long getMsgsReceived() {
        return msgsReceived;
    }
    public void setMsgsReceived(long msgsReceived) {
        this.msgsReceived = msgsReceived;
    }
    public void addMsgsReceived(long msgsReceived) {
        this.msgsReceived += msgsReceived;
    }
}

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

    private UpgradeGroupInfo groupInfo = null;
    private final LongAdder bytesSent = new LongAdder();
    private final LongAdder bytesReceived = new LongAdder();
    private final LongAdder msgsSent = new LongAdder();
    private final LongAdder msgsReceived = new LongAdder();


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
        return bytesSent.longValue();
    }

    public void setBytesSent(long bytesSent) {
        this.bytesSent.reset();
        if (bytesSent > 0) {
            this.bytesSent.add(bytesSent);
        }
    }

    public void addBytesSent(long bytesSent) {
        this.bytesSent.add(bytesSent);
    }


    public long getBytesReceived() {
        return bytesReceived.longValue();
    }

    public void setBytesReceived(long bytesReceived) {
        this.bytesReceived.reset();
        if (bytesReceived > 0) {
            this.bytesReceived.add(bytesReceived);
        }
    }

    public void addBytesReceived(long bytesReceived) {
        this.bytesReceived.add(bytesReceived);
    }


    public long getMsgsSent() {
        return msgsSent.longValue();
    }

    public void setMsgsSent(long msgsSent) {
        this.msgsSent.reset();
        if (msgsSent > 0) {
            this.msgsSent.add(msgsSent);
        }
    }

    public void addMsgsSent(long msgsSent) {
        this.msgsSent.add(msgsSent);
    }


    public long getMsgsReceived() {
        return msgsReceived.longValue();
    }

    public void setMsgsReceived(long msgsReceived) {
        this.msgsReceived.reset();
        if (msgsReceived > 0) {
            this.msgsReceived.add(msgsReceived);
        }
    }

    public void addMsgsReceived(long msgsReceived) {
        this.msgsReceived.add(msgsReceived);
    }
}

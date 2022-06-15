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

import java.util.ArrayList;
import java.util.List;

import org.apache.tomcat.util.modeler.BaseModelMBean;

/**
 *  This aggregates the data collected from each UpgradeInfo instance.
 */
public class UpgradeGroupInfo extends BaseModelMBean {

    private final List<UpgradeInfo> upgradeInfos = new ArrayList<>();

    private long deadBytesReceived = 0;
    private long deadBytesSent = 0;
    private long deadMsgsReceived = 0;
    private long deadMsgsSent = 0;


    public synchronized void addUpgradeInfo(UpgradeInfo ui) {
        upgradeInfos.add(ui);
    }


    public synchronized void removeUpgradeInfo(UpgradeInfo ui) {
        if (ui != null) {
            deadBytesReceived += ui.getBytesReceived();
            deadBytesSent += ui.getBytesSent();
            deadMsgsReceived += ui.getMsgsReceived();
            deadMsgsSent += ui.getMsgsSent();

            upgradeInfos.remove(ui);
        }
    }


    public synchronized long getBytesReceived() {
        long bytes = deadBytesReceived;
        for (UpgradeInfo ui : upgradeInfos) {
            bytes += ui.getBytesReceived();
        }
        return bytes;
    }
    public synchronized void setBytesReceived(long bytesReceived) {
        deadBytesReceived = bytesReceived;
        for (UpgradeInfo ui : upgradeInfos) {
            ui.setBytesReceived(bytesReceived);
        }
    }


    public synchronized long getBytesSent() {
        long bytes = deadBytesSent;
        for (UpgradeInfo ui : upgradeInfos) {
            bytes += ui.getBytesSent();
        }
        return bytes;
    }
    public synchronized void setBytesSent(long bytesSent) {
        deadBytesSent = bytesSent;
        for (UpgradeInfo ui : upgradeInfos) {
            ui.setBytesSent(bytesSent);
        }
    }


    public synchronized long getMsgsReceived() {
        long msgs = deadMsgsReceived;
        for (UpgradeInfo ui : upgradeInfos) {
            msgs += ui.getMsgsReceived();
        }
        return msgs;
    }
    public synchronized void setMsgsReceived(long msgsReceived) {
        deadMsgsReceived = msgsReceived;
        for (UpgradeInfo ui : upgradeInfos) {
            ui.setMsgsReceived(msgsReceived);
        }
    }


    public synchronized long getMsgsSent() {
        long msgs = deadMsgsSent;
        for (UpgradeInfo ui : upgradeInfos) {
            msgs += ui.getMsgsSent();
        }
        return msgs;
    }
    public synchronized void setMsgsSent(long msgsSent) {
        deadMsgsSent = msgsSent;
        for (UpgradeInfo ui : upgradeInfos) {
            ui.setMsgsSent(msgsSent);
        }
    }


    public void resetCounters() {
        setBytesReceived(0);
        setBytesSent(0);
        setMsgsReceived(0);
        setMsgsSent(0);
    }
}

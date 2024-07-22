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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import org.apache.tomcat.util.modeler.BaseModelMBean;

/**
 * This aggregates the data collected from each UpgradeInfo instance.
 */
public class UpgradeGroupInfo extends BaseModelMBean {

    private final Set<UpgradeInfo> upgradeInfos = (new ConcurrentHashMap<UpgradeInfo,Boolean>()).keySet(Boolean.TRUE);

    private LongAdder deadBytesReceived = new LongAdder();
    private LongAdder deadBytesSent = new LongAdder();
    private LongAdder deadMsgsReceived = new LongAdder();
    private LongAdder deadMsgsSent = new LongAdder();


    public void addUpgradeInfo(UpgradeInfo ui) {
        upgradeInfos.add(ui);
    }


    public void removeUpgradeInfo(UpgradeInfo ui) {
        if (ui != null) {
            deadBytesReceived.add(ui.getBytesReceived());
            deadBytesSent.add(ui.getBytesSent());
            deadMsgsReceived.add(ui.getMsgsReceived());
            deadMsgsSent.add(ui.getMsgsSent());

            upgradeInfos.remove(ui);
        }
    }


    public long getBytesReceived() {
        long bytes = deadBytesReceived.longValue();
        for (UpgradeInfo ui : upgradeInfos) {
            bytes += ui.getBytesReceived();
        }
        return bytes;
    }

    public void setBytesReceived(long bytesReceived) {
        deadBytesReceived.reset();
        if (bytesReceived != 0) {
            deadBytesReceived.add(bytesReceived);
        }
        for (UpgradeInfo ui : upgradeInfos) {
            ui.setBytesReceived(bytesReceived);
        }
    }


    public long getBytesSent() {
        long bytes = deadBytesSent.longValue();
        for (UpgradeInfo ui : upgradeInfos) {
            bytes += ui.getBytesSent();
        }
        return bytes;
    }

    public void setBytesSent(long bytesSent) {
        deadBytesSent.reset();
        if (bytesSent != 0) {
            deadBytesSent.add(bytesSent);
        }
        for (UpgradeInfo ui : upgradeInfos) {
            ui.setBytesSent(bytesSent);
        }
    }


    public long getMsgsReceived() {
        long msgs = deadMsgsReceived.longValue();
        for (UpgradeInfo ui : upgradeInfos) {
            msgs += ui.getMsgsReceived();
        }
        return msgs;
    }

    public void setMsgsReceived(long msgsReceived) {
        deadMsgsReceived.reset();
        if (msgsReceived != 0) {
            deadMsgsReceived.add(msgsReceived);
        }
        for (UpgradeInfo ui : upgradeInfos) {
            ui.setMsgsReceived(msgsReceived);
        }
    }


    public long getMsgsSent() {
        long msgs = deadMsgsSent.longValue();
        for (UpgradeInfo ui : upgradeInfos) {
            msgs += ui.getMsgsSent();
        }
        return msgs;
    }

    public void setMsgsSent(long msgsSent) {
        deadMsgsSent.reset();
        if (msgsSent != 0) {
            deadMsgsSent.add(msgsSent);
        }
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

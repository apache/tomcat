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

    /**
     * Constructs a new UpgradeGroupInfo.
     */
    public UpgradeGroupInfo() {
    }

    /**
     * The set of active upgrade connections.
     */
    private final Set<UpgradeInfo> upgradeInfos = (new ConcurrentHashMap<UpgradeInfo,Boolean>()).keySet(Boolean.TRUE);

    /**
     * Bytes received from completed connections.
     */
    private final LongAdder deadBytesReceived = new LongAdder();

    /**
     * Bytes sent to completed connections.
     */
    private final LongAdder deadBytesSent = new LongAdder();

    /**
     * Messages received from completed connections.
     */
    private final LongAdder deadMsgsReceived = new LongAdder();

    /**
     * Messages sent to completed connections.
     */
    private final LongAdder deadMsgsSent = new LongAdder();


    /**
     * Adds an active upgrade connection to this group.
     *
     * @param ui the upgrade connection
     */
    public void addUpgradeInfo(UpgradeInfo ui) {
        upgradeInfos.add(ui);
    }


    /**
     * Removes an upgrade connection from this group.
     *
     * @param ui the upgrade connection to remove
     */
    public void removeUpgradeInfo(UpgradeInfo ui) {
        if (ui != null) {
            deadBytesReceived.add(ui.getBytesReceived());
            deadBytesSent.add(ui.getBytesSent());
            deadMsgsReceived.add(ui.getMsgsReceived());
            deadMsgsSent.add(ui.getMsgsSent());

            upgradeInfos.remove(ui);
        }
    }


    /**
     * Returns the total bytes received across all connections.
     *
     * @return the total bytes received
     */
    public long getBytesReceived() {
        long bytes = deadBytesReceived.longValue();
        for (UpgradeInfo ui : upgradeInfos) {
            bytes += ui.getBytesReceived();
        }
        return bytes;
    }

    /**
     * Resets the bytes received counter.
     *
     * @param bytesReceived the new value (ignored, used for MBean reset)
     */
    public void setBytesReceived(long bytesReceived) {
        deadBytesReceived.reset();
        if (bytesReceived != 0) {
            deadBytesReceived.add(bytesReceived);
        }
        for (UpgradeInfo ui : upgradeInfos) {
            ui.setBytesReceived(bytesReceived);
        }
    }


    /**
     * Returns the total bytes sent across all connections.
     *
     * @return the total bytes sent
     */
    public long getBytesSent() {
        long bytes = deadBytesSent.longValue();
        for (UpgradeInfo ui : upgradeInfos) {
            bytes += ui.getBytesSent();
        }
        return bytes;
    }

    /**
     * Resets the bytes sent counter.
     *
     * @param bytesSent the new value (ignored, used for MBean reset)
     */
    public void setBytesSent(long bytesSent) {
        deadBytesSent.reset();
        if (bytesSent != 0) {
            deadBytesSent.add(bytesSent);
        }
        for (UpgradeInfo ui : upgradeInfos) {
            ui.setBytesSent(bytesSent);
        }
    }


    /**
     * Returns the total messages received across all connections.
     *
     * @return the total messages received
     */
    public long getMsgsReceived() {
        long msgs = deadMsgsReceived.longValue();
        for (UpgradeInfo ui : upgradeInfos) {
            msgs += ui.getMsgsReceived();
        }
        return msgs;
    }

    /**
     * Resets the messages received counter.
     *
     * @param msgsReceived the new value (ignored, used for MBean reset)
     */
    public void setMsgsReceived(long msgsReceived) {
        deadMsgsReceived.reset();
        if (msgsReceived != 0) {
            deadMsgsReceived.add(msgsReceived);
        }
        for (UpgradeInfo ui : upgradeInfos) {
            ui.setMsgsReceived(msgsReceived);
        }
    }


    /**
     * Returns the total messages sent across all connections.
     *
     * @return the total messages sent
     */
    public long getMsgsSent() {
        long msgs = deadMsgsSent.longValue();
        for (UpgradeInfo ui : upgradeInfos) {
            msgs += ui.getMsgsSent();
        }
        return msgs;
    }

    /**
     * Resets the messages sent counter.
     *
     * @param msgsSent the new value (ignored, used for MBean reset)
     */
    public void setMsgsSent(long msgsSent) {
        deadMsgsSent.reset();
        if (msgsSent != 0) {
            deadMsgsSent.add(msgsSent);
        }
        for (UpgradeInfo ui : upgradeInfos) {
            ui.setMsgsSent(msgsSent);
        }
    }


    /**
     * Resets all counters to zero.
     */
    public void resetCounters() {
        setBytesReceived(0);
        setBytesSent(0);
        setMsgsReceived(0);
        setMsgsSent(0);
    }
}

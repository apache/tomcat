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
    private final LongAdder completedConnectionsBytesReceived = new LongAdder();

    /**
     * Bytes sent to completed connections.
     */
    private final LongAdder completedConnectionsBytesSent = new LongAdder();

    /**
     * Messages received from completed connections.
     */
    private final LongAdder completedConnectionsMsgsReceived = new LongAdder();

    /**
     * Messages sent to completed connections.
     */
    private final LongAdder completedConnectionsMsgsSent = new LongAdder();


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
            completedConnectionsBytesReceived.add(ui.getBytesReceived());
            completedConnectionsBytesSent.add(ui.getBytesSent());
            completedConnectionsMsgsReceived.add(ui.getMsgsReceived());
            completedConnectionsMsgsSent.add(ui.getMsgsSent());

            upgradeInfos.remove(ui);
        }
    }


    /**
     * Returns the total bytes received across all connections.
     *
     * @return the total bytes received
     */
    public long getBytesReceived() {
        long bytes = completedConnectionsBytesReceived.longValue();
        for (UpgradeInfo ui : upgradeInfos) {
            bytes += ui.getBytesReceived();
        }
        return bytes;
    }

    /**
     * Resets the bytes received counter.
     *
     * @param bytesReceived the value to restore (0 to reset completely,
     *        positive values are added to the bytes received counter)
     */
    public void setBytesReceived(long bytesReceived) {
        completedConnectionsBytesReceived.reset();
        if (bytesReceived > 0) {
            completedConnectionsBytesReceived.add(bytesReceived);
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
        long bytes = completedConnectionsBytesSent.longValue();
        for (UpgradeInfo ui : upgradeInfos) {
            bytes += ui.getBytesSent();
        }
        return bytes;
    }

    /**
     * Resets the bytes sent counter.
     *
     * @param bytesSent the value to restore (0 to reset completely,
     *        positive values are added to the bytes sent counter)
     */
    public void setBytesSent(long bytesSent) {
        completedConnectionsBytesSent.reset();
        if (bytesSent > 0) {
            completedConnectionsBytesSent.add(bytesSent);
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
        long msgs = completedConnectionsMsgsReceived.longValue();
        for (UpgradeInfo ui : upgradeInfos) {
            msgs += ui.getMsgsReceived();
        }
        return msgs;
    }

    /**
     * Resets the messages received counter.
     *
     * @param msgsReceived the value to restore (0 to reset completely,
     *        positive values are added to the messages received counter)
     */
    public void setMsgsReceived(long msgsReceived) {
        completedConnectionsMsgsReceived.reset();
        if (msgsReceived > 0) {
            completedConnectionsMsgsReceived.add(msgsReceived);
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
        long msgs = completedConnectionsMsgsSent.longValue();
        for (UpgradeInfo ui : upgradeInfos) {
            msgs += ui.getMsgsSent();
        }
        return msgs;
    }

    /**
     * Resets the messages sent counter.
     *
     * @param msgsSent the value to restore (0 to reset completely,
     *        positive values are added to the message sent counter)
     */
    public void setMsgsSent(long msgsSent) {
        completedConnectionsMsgsSent.reset();
        if (msgsSent > 0) {
            completedConnectionsMsgsSent.add(msgsSent);
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

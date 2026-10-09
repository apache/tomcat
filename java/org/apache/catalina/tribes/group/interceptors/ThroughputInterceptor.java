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
package org.apache.catalina.tribes.group.interceptors;

import java.text.DecimalFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.group.ChannelInterceptorBase;
import org.apache.catalina.tribes.group.InterceptorPayload;
import org.apache.catalina.tribes.io.ChannelData;
import org.apache.catalina.tribes.io.XByteBuffer;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Interceptor that measures and reports message throughput statistics.
 */
public class ThroughputInterceptor extends ChannelInterceptorBase implements ThroughputInterceptorMBean {

    /**
     * Constructs a new ThroughputInterceptor.
     */
    public ThroughputInterceptor() {
    }

    private static final Log log = LogFactory.getLog(ThroughputInterceptor.class);

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(ThroughputInterceptor.class);

    // Statistics are best effort: the increments below may lose updates when several
    // threads update them at once. Volatile is used to keep individual reads and writes
    // atomic and visible without adding any locking to the message path, which is
    // shared by every interceptor in the channel.
    volatile double mbTx = 0;
    volatile double mbAppTx = 0;
    volatile double mbRx = 0;
    volatile double timeTx = 0;
    volatile double lastCnt = 0;
    final AtomicLong msgTxCnt = new AtomicLong(1);
    final AtomicLong msgRxCnt = new AtomicLong(0);
    final AtomicLong msgTxErr = new AtomicLong(0);
    int interval = 10000;
    final AtomicInteger access = new AtomicInteger(0);
    volatile long txStart = 0;
    volatile long rxStart = 0;


    @Override
    public void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload)
            throws ChannelException {
        if (access.addAndGet(1) == 1) {
            txStart = System.currentTimeMillis();
        }
        long bytes = XByteBuffer.getDataPackageLength(((ChannelData) msg).getDataPackageLength());
        try {
            super.sendMessage(destination, msg, payload);
        } catch (ChannelException x) {
            msgTxErr.addAndGet(1);
            // Release the in-flight slot exactly once. The previous check-then-act on
            // access could skip the decrement when another send was still in flight,
            // permanently keeping the counter above zero and stopping the reports, or
            // let two failing sends decrement the same slot.
            access.addAndGet(-1);
            throw x;
        }
        mbTx += (bytes * destination.length) / (1024d * 1024d);
        mbAppTx += bytes / (1024d * 1024d);
        if (access.addAndGet(-1) == 0) {
            long stop = System.currentTimeMillis();
            timeTx += (stop - txStart) / 1000d;
            if ((msgTxCnt.get() / (double) interval) >= lastCnt) {
                lastCnt++;
                report(timeTx);
            }
        }
        msgTxCnt.addAndGet(1);
    }

    @Override
    public void messageReceived(ChannelMessage msg) {
        if (rxStart == 0) {
            rxStart = System.currentTimeMillis();
        }
        long bytes = XByteBuffer.getDataPackageLength(((ChannelData) msg).getDataPackageLength());
        mbRx += bytes / (1024d * 1024d);
        msgRxCnt.addAndGet(1);
        if (msgRxCnt.get() % interval == 0) {
            report(timeTx);
        }
        super.messageReceived(msg);

    }

    @Override
    public void report(double timeTx) {
        if (log.isInfoEnabled()) {
            // Local formatter: DecimalFormat is not thread safe and this method can be
            // entered concurrently by send and receive threads.
            DecimalFormat df = new DecimalFormat("#0.00");
            double txRate = timeTx > 0 ? mbTx / timeTx : 0;
            double appTxRate = timeTx > 0 ? mbAppTx / timeTx : 0;
            double rxElapsed = (System.currentTimeMillis() - rxStart) / 1000d;
            double rxRate = rxElapsed > 0 ? mbRx / rxElapsed : 0;
            log.info(sm.getString("throughputInterceptor.report", msgTxCnt, df.format(mbTx), df.format(mbAppTx),
                    df.format(timeTx), df.format(txRate), df.format(appTxRate), msgTxErr, msgRxCnt,
                    df.format(rxRate), df.format(mbRx)));
        }
    }

    @Override
    public void setInterval(int interval) {
        this.interval = interval;
    }

    @Override
    public int getInterval() {
        return interval;
    }

    @Override
    public double getLastCnt() {
        return lastCnt;
    }

    @Override
    public double getMbAppTx() {
        return mbAppTx;
    }

    @Override
    public double getMbRx() {
        return mbRx;
    }

    @Override
    public double getMbTx() {
        return mbTx;
    }

    @Override
    public AtomicLong getMsgRxCnt() {
        return msgRxCnt;
    }

    @Override
    public AtomicLong getMsgTxCnt() {
        return msgTxCnt;
    }

    @Override
    public AtomicLong getMsgTxErr() {
        return msgTxErr;
    }

    @Override
    public long getRxStart() {
        return rxStart;
    }

    @Override
    public double getTimeTx() {
        return timeTx;
    }

    @Override
    public long getTxStart() {
        return txStart;
    }

}

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

import java.util.concurrent.atomic.AtomicLong;

/**
 * MBean interface for managing the ThroughputInterceptor.
 */
public interface ThroughputInterceptorMBean {

    /**
     * Returns the option flag used to determine if this interceptor should process a message.
     * @return the option flag
     */
    int getOptionFlag();

    /**
     * Returns the reporting interval in milliseconds.
     * @return the interval
     */
    int getInterval();

    /**
     * Sets the reporting interval in milliseconds.
     * @param interval the interval
     */
    void setInterval(int interval);

    /**
     * Returns the last message count.
     * @return the last count
     */
    double getLastCnt();

    /**
     * Returns the cumulative amount of data transmitted at the application layer, in MB.
     * @return the amount in MB
     */
    double getMbAppTx();

    /**
     * Returns the cumulative amount of data received, in MB.
     * @return the amount in MB
     */
    double getMbRx();

    /**
     * Returns the cumulative amount of data transmitted, in MB.
     * @return the amount in MB
     */
    double getMbTx();

    /**
     * Returns the received message count.
     * @return the count
     */
    AtomicLong getMsgRxCnt();

    /**
     * Returns the transmitted message count.
     * @return the count
     */
    AtomicLong getMsgTxCnt();

    /**
     * Returns the transmit error count.
     * @return the count
     */
    AtomicLong getMsgTxErr();

    /**
     * Returns the receive start timestamp.
     * @return the timestamp
     */
    long getRxStart();

    /**
     * Returns the accumulated total transmit time in seconds.
     * @return the time in seconds
     */
    double getTimeTx();

    /**
     * Returns the transmit start timestamp.
     * @return the timestamp
     */
    long getTxStart();

    /**
     * Reports throughput statistics.
     * @param timeTx the transmit time
     */
    void report(double timeTx);

}

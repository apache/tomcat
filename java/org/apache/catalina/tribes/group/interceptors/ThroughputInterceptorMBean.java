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

public interface ThroughputInterceptorMBean {

    public int getOptionFlag();

    // Attributes
    public int getInterval();

    public void setInterval(int interval);

    // stats
    public double getLastCnt();

    public double getMbAppTx();

    public double getMbRx();

    public double getMbTx();

    public AtomicLong getMsgRxCnt();

    public AtomicLong getMsgTxCnt();

    public AtomicLong getMsgTxErr();

    public long getRxStart();

    public double getTimeTx();

    public long getTxStart();

    // Operations
    public void report(double timeTx);

}

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
package org.apache.catalina.tribes.transport.nio;


public interface NioReceiverMBean {

    // Receiver Attributes
    String getAddress();

    boolean getDirect();

    int getPort();

    int getAutoBind();

    int getSecurePort();

    int getUdpPort();

    long getSelectorTimeout();

    int getMaxThreads();

    int getMinThreads();

    long getMaxIdleTime();

    boolean getOoBInline();

    int getRxBufSize();

    int getTxBufSize();

    int getUdpRxBufSize();

    int getUdpTxBufSize();

    boolean getSoKeepAlive();

    boolean getSoLingerOn();

    int getSoLingerTime();

    boolean getSoReuseAddress();

    boolean getTcpNoDelay();

    int getTimeout();

    boolean getUseBufferPool();

    boolean isListening();

    // pool stats
    int getPoolSize();

    int getActiveCount();

    long getTaskCount();

    long getCompletedTaskCount();

}

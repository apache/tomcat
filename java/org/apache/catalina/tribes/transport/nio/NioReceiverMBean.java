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
    public String getAddress();

    public boolean getDirect();

    public int getPort();

    public int getAutoBind();

    public int getSecurePort();

    public int getUdpPort();

    public long getSelectorTimeout();

    public int getMaxThreads();

    public int getMinThreads();

    public long getMaxIdleTime();

    public boolean getOoBInline();

    public int getRxBufSize();

    public int getTxBufSize();

    public int getUdpRxBufSize();

    public int getUdpTxBufSize();

    public boolean getSoKeepAlive();

    public boolean getSoLingerOn();

    public int getSoLingerTime();

    public boolean getSoReuseAddress();

    public boolean getTcpNoDelay();

    public int getTimeout();

    public boolean getUseBufferPool();

    public boolean isListening();

    // pool stats
    public int getPoolSize();

    public int getActiveCount();

    public long getTaskCount();

    public long getCompletedTaskCount();

}

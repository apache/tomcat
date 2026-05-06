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
package org.apache.catalina.tribes.transport;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.catalina.tribes.Member;

/** Abstract base implementation of a data sender. */
public abstract class AbstractSender implements DataSender {

    /** Indicates whether the sender is currently connected. */
    private volatile boolean connected = false;
    /** Receive buffer size for TCP connections. */
    private int rxBufSize = Constants.DEFAULT_CLUSTER_ACK_BUFFER_SIZE;
    /** Transmit buffer size for TCP connections. */
    private int txBufSize = Constants.DEFAULT_CLUSTER_MSG_BUFFER_SIZE;
    /** Receive buffer size for UDP connections. */
    private int udpRxBufSize = Constants.DEFAULT_CLUSTER_ACK_BUFFER_SIZE;
    /** Transmit buffer size for UDP connections. */
    private int udpTxBufSize = Constants.DEFAULT_CLUSTER_MSG_BUFFER_SIZE;
    /** Whether to use direct (ByteBuffer) buffers. */
    private boolean directBuffer = false;
    /** Maximum number of requests before disconnecting for keepalive. -1 means disabled. */
    private int keepAliveCount = -1;
    /** Current request count. */
    private int requestCount = 0;
    /** Timestamp when the connection was established. */
    private long connectTime;
    /** Maximum time in milliseconds before disconnecting for keepalive. -1 means disabled. */
    private long keepAliveTime = -1;
    /** Connection timeout in milliseconds. */
    private long timeout = 3000;
    /** The destination member for this sender. */
    private Member destination;
    /** The InetAddress of the destination. */
    private InetAddress address;
    /** The port of the destination. */
    private int port;
    /** Maximum number of retry attempts before giving up. */
    private int maxRetryAttempts = 1;// 1 resends
    /** Current retry attempt count. */
    private int attempt;
    /** Whether TCP_NODELAY is enabled (disables Nagle's algorithm). */
    private boolean tcpNoDelay = true;
    /** Whether SO_KEEPALIVE is enabled. */
    private boolean soKeepAlive = false;
    /** Whether out-of-band data is delivered inline. */
    private boolean ooBInline = true;
    /** Whether SO_REUSEADDR is enabled. */
    private boolean soReuseAddress = true;
    /** Whether SO_LINGER is enabled. */
    private boolean soLingerOn = false;
    /** SO_LINGER timeout value in seconds. */
    private int soLingerTime = 3;
    /** TCP traffic class value for socket options. */
    private int soTrafficClass = 0x04 | 0x08 | 0x010;
    /** Whether to throw an exception when an ACK failure occurs. */
    private boolean throwOnFailedAck = true;
    /** Whether this sender uses UDP-based communication. */
    private boolean udpBased = false;
    /** The UDP port of the destination. */
    private int udpPort = -1;

    /**
     * transfers sender properties from one sender to another
     *
     * @param from AbstractSender
     * @param to   AbstractSender
     */
    public static void transferProperties(AbstractSender from, AbstractSender to) {
        to.rxBufSize = from.rxBufSize;
        to.txBufSize = from.txBufSize;
        to.directBuffer = from.directBuffer;
        to.keepAliveCount = from.keepAliveCount;
        to.keepAliveTime = from.keepAliveTime;
        to.timeout = from.timeout;
        to.destination = from.destination;
        to.address = from.address;
        to.port = from.port;
        to.maxRetryAttempts = from.maxRetryAttempts;
        to.tcpNoDelay = from.tcpNoDelay;
        to.soKeepAlive = from.soKeepAlive;
        to.ooBInline = from.ooBInline;
        to.soReuseAddress = from.soReuseAddress;
        to.soLingerOn = from.soLingerOn;
        to.soLingerTime = from.soLingerTime;
        to.soTrafficClass = from.soTrafficClass;
        to.throwOnFailedAck = from.throwOnFailedAck;
        to.udpBased = from.udpBased;
        to.udpPort = from.udpPort;
    }


    /**
     * Constructs a new AbstractSender with default property values.
     */
    public AbstractSender() {

    }

    @Override
    public boolean keepalive() {
        boolean disconnect = false;
        if (isUdpBased()) {
            disconnect = true; // always disconnect UDP, TODO optimize the keepalive handling
        } else if (keepAliveCount >= 0 && requestCount > keepAliveCount) {
            disconnect = true;
        } else if (keepAliveTime >= 0 && (System.currentTimeMillis() - connectTime) > keepAliveTime) {
            disconnect = true;
        }
        if (disconnect) {
            disconnect();
        }
        return disconnect;
    }

    /**
     * Sets the connected state of this sender.
     * @param connected the new connected state
     */
    protected void setConnected(boolean connected) {
        this.connected = connected;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public long getConnectTime() {
        return connectTime;
    }

    /**
     * Returns the destination member for this sender.
     * @return the destination member
     */
    public Member getDestination() {
        return destination;
    }


    /**
     * Returns the maximum number of requests before disconnecting for keepalive.
     * @return the keep alive count, -1 means disabled
     */
    public int getKeepAliveCount() {
        return keepAliveCount;
    }

    /**
     * Returns the maximum time in milliseconds before disconnecting for keepalive.
     * @return the keep alive time in milliseconds, -1 means disabled
     */
    public long getKeepAliveTime() {
        return keepAliveTime;
    }

    @Override
    public int getRequestCount() {
        return requestCount;
    }

    /**
     * Returns the receive buffer size for TCP connections.
     * @return the receive buffer size
     */
    public int getRxBufSize() {
        return rxBufSize;
    }

    /**
     * Returns the connection timeout in milliseconds.
     * @return the timeout value
     */
    public long getTimeout() {
        return timeout;
    }

    /**
     * Returns the transmit buffer size for TCP connections.
     * @return the transmit buffer size
     */
    public int getTxBufSize() {
        return txBufSize;
    }

    /**
     * Returns the InetAddress of the destination.
     * @return the destination address
     */
    public InetAddress getAddress() {
        return address;
    }

    /**
     * Returns the port of the destination.
     * @return the destination port
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the maximum number of retry attempts.
     * @return the maximum retry attempts
     */
    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    /**
     * Sets whether to use direct (ByteBuffer) buffers.
     * @param directBuffer true to use direct buffers
     */
    public void setDirectBuffer(boolean directBuffer) {
        this.directBuffer = directBuffer;
    }

    /**
     * Returns whether direct (ByteBuffer) buffers are used.
     * @return true if direct buffers are used
     */
    public boolean getDirectBuffer() {
        return this.directBuffer;
    }

    /**
     * Returns the current retry attempt count.
     * @return the attempt count
     */
    public int getAttempt() {
        return attempt;
    }

    /**
     * Returns whether TCP_NODELAY is enabled.
     * @return true if TCP_NODELAY is enabled
     */
    public boolean getTcpNoDelay() {
        return tcpNoDelay;
    }

    /**
     * Returns whether SO_KEEPALIVE is enabled.
     * @return true if SO_KEEPALIVE is enabled
     */
    public boolean getSoKeepAlive() {
        return soKeepAlive;
    }

    /**
     * Returns whether out-of-band data is delivered inline.
     * @return true if OO_BINLINE is enabled
     */
    public boolean getOoBInline() {
        return ooBInline;
    }

    /**
     * Returns whether SO_REUSEADDR is enabled.
     * @return true if SO_REUSEADDR is enabled
     */
    public boolean getSoReuseAddress() {
        return soReuseAddress;
    }

    /**
     * Returns whether SO_LINGER is enabled.
     * @return true if SO_LINGER is enabled
     */
    public boolean getSoLingerOn() {
        return soLingerOn;
    }

    /**
     * Returns the SO_LINGER timeout value in seconds.
     * @return the linger time
     */
    public int getSoLingerTime() {
        return soLingerTime;
    }

    /**
     * Returns the TCP traffic class value.
     * @return the traffic class value
     */
    public int getSoTrafficClass() {
        return soTrafficClass;
    }

    /**
     * Returns whether an exception is thrown on ACK failure.
     * @return true if exceptions are thrown on failed ACKs
     */
    public boolean getThrowOnFailedAck() {
        return throwOnFailedAck;
    }

    @Override
    public void setKeepAliveCount(int keepAliveCount) {
        this.keepAliveCount = keepAliveCount;
    }

    @Override
    public void setKeepAliveTime(long keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }

    /**
     * Sets the current request count.
     * @param requestCount the new request count
     */
    public void setRequestCount(int requestCount) {
        this.requestCount = requestCount;
    }

    @Override
    public void setRxBufSize(int rxBufSize) {
        this.rxBufSize = rxBufSize;
    }

    @Override
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    @Override
    public void setTxBufSize(int txBufSize) {
        this.txBufSize = txBufSize;
    }

    /**
     * Sets the connection timestamp.
     * @param connectTime the connect time timestamp
     */
    public void setConnectTime(long connectTime) {
        this.connectTime = connectTime;
    }

    /**
     * Sets the maximum number of retry attempts.
     * @param maxRetryAttempts the maximum retry attempts
     */
    public void setMaxRetryAttempts(int maxRetryAttempts) {
        this.maxRetryAttempts = maxRetryAttempts;
    }

    /**
     * Sets the current retry attempt count.
     * @param attempt the attempt count
     */
    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }

    /**
     * Sets whether TCP_NODELAY is enabled.
     * @param tcpNoDelay true to enable TCP_NODELAY
     */
    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    /**
     * Sets whether SO_KEEPALIVE is enabled.
     * @param soKeepAlive true to enable SO_KEEPALIVE
     */
    public void setSoKeepAlive(boolean soKeepAlive) {
        this.soKeepAlive = soKeepAlive;
    }

    /**
     * Sets whether out-of-band data is delivered inline.
     * @param ooBInline true to enable OO_BINLINE
     */
    public void setOoBInline(boolean ooBInline) {
        this.ooBInline = ooBInline;
    }

    /**
     * Sets whether SO_REUSEADDR is enabled.
     * @param soReuseAddress true to enable SO_REUSEADDR
     */
    public void setSoReuseAddress(boolean soReuseAddress) {
        this.soReuseAddress = soReuseAddress;
    }

    /**
     * Sets whether SO_LINGER is enabled.
     * @param soLingerOn true to enable SO_LINGER
     */
    public void setSoLingerOn(boolean soLingerOn) {
        this.soLingerOn = soLingerOn;
    }

    /**
     * Sets the SO_LINGER timeout value in seconds.
     * @param soLingerTime the linger time in seconds
     */
    public void setSoLingerTime(int soLingerTime) {
        this.soLingerTime = soLingerTime;
    }

    /**
     * Sets the TCP traffic class value.
     * @param soTrafficClass the traffic class value
     */
    public void setSoTrafficClass(int soTrafficClass) {
        this.soTrafficClass = soTrafficClass;
    }

    /**
     * Sets whether to throw an exception on ACK failure.
     * @param throwOnFailedAck true to throw on failed ACKs
     */
    public void setThrowOnFailedAck(boolean throwOnFailedAck) {
        this.throwOnFailedAck = throwOnFailedAck;
    }

    /**
     * Sets the destination member and derives address, port, and UDP port from it.
     * @param destination the destination member
     * @throws UnknownHostException if the address cannot be determined
     */
    public void setDestination(Member destination) throws UnknownHostException {
        this.destination = destination;
        this.address = InetAddress.getByAddress(destination.getHost());
        this.port = destination.getPort();
        this.udpPort = destination.getUdpPort();

    }

    /**
     * Sets the port of the destination.
     * @param port the destination port
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Sets the InetAddress of the destination.
     * @param address the destination address
     */
    public void setAddress(InetAddress address) {
        this.address = address;
    }


    /**
     * Returns whether this sender uses UDP-based communication.
     * @return true if UDP-based
     */
    public boolean isUdpBased() {
        return udpBased;
    }


    /**
     * Sets whether this sender uses UDP-based communication.
     * @param udpBased true if UDP-based
     */
    public void setUdpBased(boolean udpBased) {
        this.udpBased = udpBased;
    }


    /**
     * Returns the UDP port of the destination.
     * @return the UDP port
     */
    public int getUdpPort() {
        return udpPort;
    }


    /**
     * Sets the UDP port of the destination.
     * @param udpPort the UDP port
     */
    public void setUdpPort(int udpPort) {
        this.udpPort = udpPort;
    }


    /**
     * Returns the receive buffer size for UDP connections.
     * @return the UDP receive buffer size
     */
    public int getUdpRxBufSize() {
        return udpRxBufSize;
    }


    /**
     * Sets the receive buffer size for UDP connections.
     * @param udpRxBufSize the UDP receive buffer size
     */
    public void setUdpRxBufSize(int udpRxBufSize) {
        this.udpRxBufSize = udpRxBufSize;
    }


    /**
     * Returns the transmit buffer size for UDP connections.
     * @return the UDP transmit buffer size
     */
    public int getUdpTxBufSize() {
        return udpTxBufSize;
    }


    /**
     * Sets the transmit buffer size for UDP connections.
     * @param udpTxBufSize the UDP transmit buffer size
     */
    public void setUdpTxBufSize(int udpTxBufSize) {
        this.udpTxBufSize = udpTxBufSize;
    }

}

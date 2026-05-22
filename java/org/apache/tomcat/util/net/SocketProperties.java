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
package org.apache.tomcat.util.net;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;

import javax.management.ObjectName;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * Properties that can be set in the &lt;Connector&gt; element in server.xml. All properties are prefixed with
 * &quot;socket.&quot; and are currently only working for the Nio connector
 */
public class SocketProperties {

    private static final Log log = LogFactory.getLog(SocketProperties.class);
    private static final StringManager sm = StringManager.getManager(SocketProperties.class);

    /**
     * Enable/disable socket processor cache, this bounded cache stores SocketProcessor objects to reduce GC.
     * <p>
     * Default is 0<br>
     * -1 is unlimited<br>
     * 0 is disabled
     */
    protected int processorCache = 0;

    /**
     * Enable/disable poller event cache, this bounded cache stores PollerEvent objects to reduce GC for the poller
     * <p>
     * Default is 0<br>
     * -1 is unlimited<br>
     * 0 is disabled<br>
     * &gt;0 the max number of objects to keep in cache.
     */
    protected int eventCache = 0;

    /**
     * Enable/disable direct buffers for the network buffers. Default value is disabled.
     */
    protected boolean directBuffer = false;

    /**
     * Enable/disable direct buffers for the network buffers for SSL. Default value is disabled.
     */
    protected boolean directSslBuffer = false;

    /**
     * Socket receive buffer size in bytes (SO_RCVBUF). JVM default used if not set.
     */
    protected Integer rxBufSize = null;

    /**
     * Socket send buffer size in bytes (SO_SNDBUF). JVM default used if not set.
     */
    protected Integer txBufSize = null;

    /**
     * The application read buffer size in bytes. Default value is 8192.
     */
    protected int appReadBufSize = 8192;

    /**
     * The application write buffer size in bytes. Default value is 8192.
     */
    protected int appWriteBufSize = 8192;

    /**
     * NioChannel pool size for the endpoint, this value is how many channels.
     * <p>
     * 0 means no cache<br>
     * -1 means unlimited cached<br>
     * -2 means bufferPoolSize will be used<br>
     * Default value is -2
     */
    protected int bufferPool = -2;

    /**
     * Buffer pool size in bytes to be cached.
     * <p>
     * -1 means unlimited<br>
     * 0 means no cache<br>
     * Default value is based on the max memory reported by the JVM, if less than 1GB, then 0, else the value divided by
     * 32. This value will then be used to compute bufferPool if its value is -2
     */
    protected int bufferPoolSize = -2;

    /**
     * TCP_NO_DELAY option. JVM default used if not set.
     */
    protected Boolean tcpNoDelay = Boolean.TRUE;

    /**
     * SO_KEEPALIVE option. JVM default used if not set.
     */
    protected Boolean soKeepAlive = null;

    /**
     * OOBINLINE option. JVM default used if not set.
     */
    protected Boolean ooBInline = null;

    /**
     * SO_REUSEADDR option. JVM default used if not set.
     */
    protected Boolean soReuseAddress = null;

    /**
     * SO_LINGER option, paired with the <code>soLingerTime</code> value. JVM defaults used unless both attributes are
     * set.
     */
    protected Boolean soLingerOn = null;

    /**
     * SO_LINGER option, paired with the <code>soLingerOn</code> value. JVM defaults used unless both attributes are
     * set.
     */
    protected Integer soLingerTime = null;

    /**
     * SO_TIMEOUT option. default is 20000.
     */
    protected Integer soTimeout = Integer.valueOf(20000);

    /**
     * Performance preferences according to <a href=
     * "https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/Socket.html#setPerformancePreferences(int,int,int)">setPerformancePreferences</a>
     * All three performance attributes must be set or the JVM defaults will be used.
     */
    protected Integer performanceConnectionTime = null;

    /**
     * Performance preferences according to <a href=
     * "https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/Socket.html#setPerformancePreferences(int,int,int)">setPerformancePreferences</a>
     * All three performance attributes must be set or the JVM defaults will be used.
     */
    protected Integer performanceLatency = null;

    /**
     * Performance preferences according to <a href=
     * "https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/Socket.html#setPerformancePreferences(int,int,int)">setPerformancePreferences</a>
     * All three performance attributes must be set or the JVM defaults will be used.
     */
    protected Integer performanceBandwidth = null;

    /**
     * The minimum frequency of the timeout interval to avoid excess load from the poller during high traffic
     */
    protected long timeoutInterval = 1000;

    /**
     * Timeout in milliseconds for an unlock to take place.
     */
    protected int unlockTimeout = 250;

    private ObjectName oname = null;


    /**
     * Creates a new instance of SocketProperties with default values.
     */
    public SocketProperties() {
    }

    /**
     * Applies socket properties to the given {@link Socket}.
     * @param socket the socket to configure
     * @throws SocketException if a socket error occurs
     */
    public void setProperties(Socket socket) throws SocketException {
        if (rxBufSize != null) {
            socket.setReceiveBufferSize(rxBufSize.intValue());
        }
        if (txBufSize != null) {
            socket.setSendBufferSize(txBufSize.intValue());
        }
        if (ooBInline != null) {
            socket.setOOBInline(ooBInline.booleanValue());
        }
        if (soKeepAlive != null) {
            socket.setKeepAlive(soKeepAlive.booleanValue());
        }
        if (performanceConnectionTime != null && performanceLatency != null && performanceBandwidth != null) {
            socket.setPerformancePreferences(performanceConnectionTime.intValue(), performanceLatency.intValue(),
                    performanceBandwidth.intValue());
        }
        if (soReuseAddress != null) {
            socket.setReuseAddress(soReuseAddress.booleanValue());
        }
        if (soLingerOn != null && soLingerTime != null) {
            socket.setSoLinger(soLingerOn.booleanValue(), soLingerTime.intValue());
        }
        if (soTimeout != null && soTimeout.intValue() >= 0) {
            socket.setSoTimeout(soTimeout.intValue());
        }
        if (tcpNoDelay != null) {
            try {
                socket.setTcpNoDelay(tcpNoDelay.booleanValue());
            } catch (SocketException e) {
                // Some socket types may not support this option which is set by default
            }
        }
    }

    /**
     * Applies socket properties to the given {@link ServerSocket}.
     * @param socket the server socket to configure
     * @throws SocketException if a socket error occurs
     */
    public void setProperties(ServerSocket socket) throws SocketException {
        if (rxBufSize != null) {
            socket.setReceiveBufferSize(rxBufSize.intValue());
        }
        if (performanceConnectionTime != null && performanceLatency != null && performanceBandwidth != null) {
            socket.setPerformancePreferences(performanceConnectionTime.intValue(), performanceLatency.intValue(),
                    performanceBandwidth.intValue());
        }
        if (soReuseAddress != null) {
            socket.setReuseAddress(soReuseAddress.booleanValue());
        }
        if (soTimeout != null && soTimeout.intValue() >= 0) {
            socket.setSoTimeout(soTimeout.intValue());
        }
    }

    /**
     * Applies socket properties to the given {@link AsynchronousSocketChannel}.
     * @param socket the asynchronous socket channel to configure
     * @throws IOException if an I/O error occurs
     */
    public void setProperties(AsynchronousSocketChannel socket) throws IOException {
        if (rxBufSize != null) {
            socket.setOption(StandardSocketOptions.SO_RCVBUF, rxBufSize);
        }
        if (txBufSize != null) {
            socket.setOption(StandardSocketOptions.SO_SNDBUF, txBufSize);
        }
        if (soKeepAlive != null) {
            socket.setOption(StandardSocketOptions.SO_KEEPALIVE, soKeepAlive);
        }
        if (soReuseAddress != null) {
            socket.setOption(StandardSocketOptions.SO_REUSEADDR, soReuseAddress);
        }
        if (soLingerOn != null && soLingerOn.booleanValue() && soLingerTime != null) {
            socket.setOption(StandardSocketOptions.SO_LINGER, soLingerTime);
        }
        if (tcpNoDelay != null) {
            socket.setOption(StandardSocketOptions.TCP_NODELAY, tcpNoDelay);
        }
    }

    /**
     * Applies socket properties to the given {@link AsynchronousServerSocketChannel}.
     * @param socket the asynchronous server socket channel to configure
     * @throws IOException if an I/O error occurs
     */
    public void setProperties(AsynchronousServerSocketChannel socket) throws IOException {
        if (rxBufSize != null) {
            socket.setOption(StandardSocketOptions.SO_RCVBUF, rxBufSize);
        }
        if (soReuseAddress != null) {
            socket.setOption(StandardSocketOptions.SO_REUSEADDR, soReuseAddress);
        }
    }

    /**
     * Returns whether direct buffers are used for network buffers.
     * @return {@code true} if direct buffers are enabled
     */
    public boolean getDirectBuffer() {
        return directBuffer;
    }

    /**
     * Returns whether direct buffers are used for SSL network buffers.
     * @return {@code true} if direct SSL buffers are enabled
     */
    public boolean getDirectSslBuffer() {
        return directSslBuffer;
    }

    /**
     * Returns the OOBINLINE socket option value.
     * @return the OOBINLINE value
     */
    public boolean getOoBInline() {
        return ooBInline == null ? false : ooBInline.booleanValue();
    }

    /**
     * Returns the performance preference for bandwidth.
     * @return the bandwidth preference value
     */
    public int getPerformanceBandwidth() {
        return performanceBandwidth == null ? 0 : performanceBandwidth.intValue();
    }

    /**
     * Returns the performance preference for connection time.
     * @return the connection time preference value
     */
    public int getPerformanceConnectionTime() {
        return performanceConnectionTime == null ? 0 : performanceConnectionTime.intValue();
    }

    /**
     * Returns the performance preference for latency.
     * @return the latency preference value
     */
    public int getPerformanceLatency() {
        return performanceLatency == null ? 0 : performanceLatency.intValue();
    }

    /**
     * Returns the socket receive buffer size in bytes.
     * @return the receive buffer size
     */
    public int getRxBufSize() {
        return rxBufSize == null ? 0 : rxBufSize.intValue();
    }

    /**
     * Returns the SO_KEEPALIVE socket option value.
     * @return the keep-alive value
     */
    public boolean getSoKeepAlive() {
        return soKeepAlive == null ? false : soKeepAlive.booleanValue();
    }

    /**
     * Returns whether SO_LINGER is enabled.
     * @return {@code true} if SO_LINGER is enabled
     */
    public boolean getSoLingerOn() {
        return soLingerOn == null ? false : soLingerOn.booleanValue();
    }

    /**
     * Returns the SO_LINGER timeout value in seconds.
     * @return the linger timeout value
     */
    public int getSoLingerTime() {
        return soLingerTime == null ? 0 : soLingerTime.intValue();
    }

    /**
     * Returns the SO_REUSEADDR socket option value.
     * @return the reuse address value
     */
    public boolean getSoReuseAddress() {
        return soReuseAddress == null ? false : soReuseAddress.booleanValue();
    }

    /**
     * Returns the SO_TIMEOUT value in milliseconds.
     * @return the socket timeout value
     */
    public int getSoTimeout() {
        return soTimeout == null ? 0 : soTimeout.intValue();
    }

    /**
     * Returns the TCP_NODELAY socket option value.
     * @return the TCP no delay value
     */
    public boolean getTcpNoDelay() {
        return tcpNoDelay == null ? false : tcpNoDelay.booleanValue();
    }

    /**
     * Returns the socket send buffer size in bytes.
     * @return the send buffer size
     */
    public int getTxBufSize() {
        return txBufSize == null ? 0 : txBufSize.intValue();
    }

    /**
     * Returns the NioChannel pool size.
     * @return the buffer pool size
     */
    public int getBufferPool() {
        return bufferPool;
    }

    /**
     * Returns the buffer pool size in bytes to be cached.
     * @return the buffer pool byte size
     */
    public int getBufferPoolSize() {
        return bufferPoolSize;
    }

    /**
     * Returns the poller event cache size.
     * @return the event cache size
     */
    public int getEventCache() {
        return eventCache;
    }

    /**
     * Returns the application read buffer size in bytes.
     * @return the application read buffer size
     */
    public int getAppReadBufSize() {
        return appReadBufSize;
    }

    /**
     * Returns the application write buffer size in bytes.
     * @return the application write buffer size
     */
    public int getAppWriteBufSize() {
        return appWriteBufSize;
    }

    /**
     * Returns the socket processor cache size.
     * @return the processor cache size
     */
    public int getProcessorCache() {
        return processorCache;
    }

    /**
     * Returns the minimum frequency of the timeout interval in milliseconds.
     * @return the timeout interval
     */
    public long getTimeoutInterval() {
        return timeoutInterval;
    }

    /**
     * Returns the direct buffer pool size.
     * @return the direct buffer pool size
     */
    public int getDirectBufferPool() {
        return bufferPool;
    }

    /**
     * Sets the performance preference for connection time.
     * @param performanceConnectionTime the connection time preference value
     */
    public void setPerformanceConnectionTime(int performanceConnectionTime) {
        this.performanceConnectionTime = Integer.valueOf(performanceConnectionTime);
    }

    /**
     * Sets the socket send buffer size in bytes.
     * @param txBufSize the send buffer size
     */
    public void setTxBufSize(int txBufSize) {
        this.txBufSize = Integer.valueOf(txBufSize);
    }

    /**
     * Sets the TCP_NODELAY socket option.
     * @param tcpNoDelay the TCP no delay value
     */
    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = Boolean.valueOf(tcpNoDelay);
    }

    /**
     * Sets the SO_TIMEOUT value in milliseconds.
     * @param soTimeout the socket timeout value
     */
    public void setSoTimeout(int soTimeout) {
        this.soTimeout = Integer.valueOf(soTimeout);
    }

    /**
     * Sets the SO_REUSEADDR socket option.
     * @param soReuseAddress the reuse address value
     */
    public void setSoReuseAddress(boolean soReuseAddress) {
        this.soReuseAddress = Boolean.valueOf(soReuseAddress);
    }

    /**
     * Sets the SO_LINGER timeout value in seconds.
     * @param soLingerTime the linger timeout value
     */
    public void setSoLingerTime(int soLingerTime) {
        this.soLingerTime = Integer.valueOf(soLingerTime);
    }

    /**
     * Sets the SO_KEEPALIVE socket option.
     * @param soKeepAlive the keep-alive value
     */
    public void setSoKeepAlive(boolean soKeepAlive) {
        this.soKeepAlive = Boolean.valueOf(soKeepAlive);
    }

    /**
     * Sets the socket receive buffer size in bytes.
     * @param rxBufSize the receive buffer size
     */
    public void setRxBufSize(int rxBufSize) {
        this.rxBufSize = Integer.valueOf(rxBufSize);
    }

    /**
     * Sets the performance preference for latency.
     * @param performanceLatency the latency preference value
     */
    public void setPerformanceLatency(int performanceLatency) {
        this.performanceLatency = Integer.valueOf(performanceLatency);
    }

    /**
     * Sets the performance preference for bandwidth.
     * @param performanceBandwidth the bandwidth preference value
     */
    public void setPerformanceBandwidth(int performanceBandwidth) {
        this.performanceBandwidth = Integer.valueOf(performanceBandwidth);
    }

    /**
     * Sets the OOBINLINE socket option.
     * @param ooBInline the OOB inline value
     */
    public void setOoBInline(boolean ooBInline) {
        this.ooBInline = Boolean.valueOf(ooBInline);
    }

    /**
     * Sets whether direct buffers are used for network buffers.
     * @param directBuffer {@code true} to enable direct buffers
     */
    public void setDirectBuffer(boolean directBuffer) {
        this.directBuffer = directBuffer;
    }

    /**
     * Sets whether direct buffers are used for SSL network buffers.
     * @param directSslBuffer {@code true} to enable direct SSL buffers
     */
    public void setDirectSslBuffer(boolean directSslBuffer) {
        this.directSslBuffer = directSslBuffer;
    }

    /**
     * Sets whether SO_LINGER is enabled.
     * @param soLingerOn {@code true} to enable SO_LINGER
     */
    public void setSoLingerOn(boolean soLingerOn) {
        this.soLingerOn = Boolean.valueOf(soLingerOn);
    }

    /**
     * Sets the NioChannel pool size.
     * @param bufferPool the buffer pool size
     */
    public void setBufferPool(int bufferPool) {
        this.bufferPool = bufferPool;
    }

    /**
     * Sets the buffer pool size in bytes to be cached.
     * @param bufferPoolSize the buffer pool byte size
     */
    public void setBufferPoolSize(int bufferPoolSize) {
        this.bufferPoolSize = bufferPoolSize;
    }

    /**
     * Sets the poller event cache size.
     * @param eventCache the event cache size
     */
    public void setEventCache(int eventCache) {
        this.eventCache = eventCache;
    }

    /**
     * Sets the application read buffer size in bytes.
     * @param appReadBufSize the application read buffer size
     */
    public void setAppReadBufSize(int appReadBufSize) {
        this.appReadBufSize = appReadBufSize;
    }

    /**
     * Sets the application write buffer size in bytes.
     * @param appWriteBufSize the application write buffer size
     */
    public void setAppWriteBufSize(int appWriteBufSize) {
        this.appWriteBufSize = appWriteBufSize;
    }

    /**
     * Sets the socket processor cache size.
     * @param processorCache the processor cache size
     */
    public void setProcessorCache(int processorCache) {
        this.processorCache = processorCache;
    }

    /**
     * Sets the minimum frequency of the timeout interval in milliseconds.
     * @param timeoutInterval the timeout interval
     */
    public void setTimeoutInterval(long timeoutInterval) {
        this.timeoutInterval = timeoutInterval;
    }

    /**
     * Sets the direct buffer pool size.
     * @param directBufferPool the direct buffer pool size
     */
    public void setDirectBufferPool(int directBufferPool) {
        this.bufferPool = directBufferPool;
    }

    /**
     * Returns the unlock timeout in milliseconds.
     * @return the unlock timeout
     */
    public int getUnlockTimeout() {
        return unlockTimeout;
    }

    /**
     * Sets the unlock timeout in milliseconds.
     * @param unlockTimeout the unlock timeout value (must be positive)
     */
    public void setUnlockTimeout(int unlockTimeout) {
        if (unlockTimeout > 0) {
            this.unlockTimeout = unlockTimeout;
        } else {
            log.warn(sm.getString("socketProperties.negativeUnlockTimeout"));
        }
    }

    /**
     * Get the actual buffer pool size to use.
     *
     * @param bufferOverhead When TLS is enabled, additional network buffers are needed and will be added to the
     *                           application buffer size
     *
     * @return the actual buffer pool size that will be used
     */
    public int getActualBufferPool(int bufferOverhead) {
        if (bufferPool != -2) {
            return bufferPool;
        } else {
            if (bufferPoolSize == -1) {
                return -1;
            } else if (bufferPoolSize == 0) {
                return 0;
            } else {
                long actualBufferPoolSize = bufferPoolSize;
                long poolSize;
                if (actualBufferPoolSize == -2) {
                    long maxMemory = Runtime.getRuntime().maxMemory();
                    if (maxMemory > Integer.MAX_VALUE) {
                        actualBufferPoolSize = maxMemory / 32;
                    } else {
                        return 0;
                    }
                }
                int bufSize = appReadBufSize + appWriteBufSize + bufferOverhead;
                if (bufSize == 0) {
                    return 0;
                }
                poolSize = actualBufferPoolSize / (bufSize);
                if (poolSize > Integer.MAX_VALUE) {
                    return Integer.MAX_VALUE;
                } else {
                    return (int) poolSize;
                }
            }
        }
    }

    void setObjectName(ObjectName oname) {
        this.oname = oname;
    }

    ObjectName getObjectName() {
        return oname;
    }
}

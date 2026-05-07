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

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.ObjectName;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.ChannelReceiver;
import org.apache.catalina.tribes.MessageListener;
import org.apache.catalina.tribes.io.ListenCallback;
import org.apache.catalina.tribes.jmx.JmxRegistry;
import org.apache.catalina.tribes.util.ExecutorFactory;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Base implementation of a channel receiver that handles TCP connections and provides
 * configuration for socket options, thread pools, and message handling.
 */
public abstract class ReceiverBase implements ChannelReceiver, ListenCallback, RxTaskPool.TaskCreator {

    /**
     * Option flag to use direct (off-heap) buffers for data transfer.
     */
    public static final int OPTION_DIRECT_BUFFER = 0x0004;

    private static final Log log = LogFactory.getLog(ReceiverBase.class);

    private static final Object bindLock = new Object();

    /**
     * String manager for localized messages in this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    private MessageListener listener;
    private String host = "auto";
    private InetAddress bind;
    private int port = 4000;
    private int udpPort = -1;
    private int securePort = -1;
    private int rxBufSize = Constants.DEFAULT_CLUSTER_MSG_BUFFER_SIZE;
    private int txBufSize = Constants.DEFAULT_CLUSTER_ACK_BUFFER_SIZE;
    private int udpRxBufSize = Constants.DEFAULT_CLUSTER_MSG_BUFFER_SIZE;
    private int udpTxBufSize = Constants.DEFAULT_CLUSTER_ACK_BUFFER_SIZE;

    private volatile boolean listen = false;
    private RxTaskPool pool;
    private boolean direct = true;
    private long tcpSelectorTimeout = 5000;
    // how many times to search for an available socket
    private int autoBind = 100;
    private int maxThreads = 15;
    private int minThreads = 6;
    private int maxTasks = 100;
    private int minTasks = 10;
    private boolean tcpNoDelay = true;
    private boolean soKeepAlive = false;
    private boolean ooBInline = true;
    private boolean soReuseAddress = true;
    private boolean soLingerOn = true;
    private int soLingerTime = 3;
    private int soTrafficClass = 0x04 | 0x08 | 0x010;
    private int timeout = 3000; // 3 seconds
    private boolean useBufferPool = true;
    private boolean daemon = true;
    private long maxIdleTime = 60000;

    private ExecutorService executor;
    private Channel channel;

    /**
     * the ObjectName of this Receiver.
     */
    private ObjectName oname = null;

    /**
     * Default constructor for ReceiverBase.
     */
    public ReceiverBase() {
    }

    /**
     * Starts the receiver by creating the executor service and registering with JMX.
     *
     * @throws IOException if an I/O error occurs during startup
     */
    @Override
    public void start() throws IOException {
        if (executor == null) {
            String channelName = "";
            if (channel.getName() != null) {
                channelName = "[" + channel.getName() + "]";
            }
            TaskThreadFactory tf = new TaskThreadFactory("Tribes-Task-Receiver" + channelName + "-");
            executor = ExecutorFactory.newThreadPool(minThreads, maxThreads, maxIdleTime, TimeUnit.MILLISECONDS, tf);
        }
        // register jmx
        JmxRegistry jmxRegistry = JmxRegistry.getRegistry(channel);
        if (jmxRegistry != null) {
            this.oname = jmxRegistry.registerJmx(",component=Receiver", this);
        }
    }

    /**
     * Stops the receiver by shutting down the executor and unregistering from JMX.
     */
    @Override
    public void stop() {
        if (executor != null) {
            executor.shutdownNow();// ignore left overs
        }
        executor = null;
        if (oname != null) {
            JmxRegistry jmxRegistry = JmxRegistry.getRegistry(channel);
            if (jmxRegistry != null) {
                jmxRegistry.unregisterJmx(oname);
            }
            oname = null;
        }
        channel = null;
    }

    /**
     * Returns the message listener for this receiver.
     *
     * @return the message listener
     */
    @Override
    public MessageListener getMessageListener() {
        return listener;
    }

    /**
     * Returns the port on which this receiver is listening.
     *
     * @return the port number
     */
    @Override
    public int getPort() {
        return port;
    }

    /**
     * Returns the size of the receive buffer for TCP connections.
     *
     * @return the receive buffer size
     */
    public int getRxBufSize() {
        return rxBufSize;
    }

    /**
     * Returns the size of the transmit buffer for TCP connections.
     *
     * @return the transmit buffer size
     */
    public int getTxBufSize() {
        return txBufSize;
    }

    /**
     * Sets the message listener for this receiver.
     *
     * @param listener the message listener to set
     */
    @Override
    public void setMessageListener(MessageListener listener) {
        this.listener = listener;
    }

    /**
     * Sets the size of the receive buffer for TCP connections.
     *
     * @param rxBufSize the receive buffer size to set
     */
    public void setRxBufSize(int rxBufSize) {
        this.rxBufSize = rxBufSize;
    }

    /**
     * Sets the size of the transmit buffer for TCP connections.
     *
     * @param txBufSize the transmit buffer size to set
     */
    public void setTxBufSize(int txBufSize) {
        this.txBufSize = txBufSize;
    }

    /**
     * Returns the bind address.
     *
     * @return the bind address
     */
    public InetAddress getBind() {
        if (bind == null) {
            try {
                if ("auto".equals(host)) {
                    host = InetAddress.getLocalHost().getHostAddress();
                }
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("receiverBase.start", host));
                }
                bind = InetAddress.getByName(host);
            } catch (IOException ioe) {
                log.error(sm.getString("receiverBase.bind.failed", host), ioe);
            }
        }
        return bind;
    }

    /**
     * Attempts to bind using the provided port and if that fails attempts to bind to each of the ports from portstart
     * to (portstart + retries -1) until either there are no more ports or the bind is successful. The address to bind
     * to is obtained via a call to {@link #getBind()}.
     *
     * @param socket    The socket to bind
     * @param portstart Starting port for bind attempts
     * @param retries   Number of times to attempt to bind (port incremented between attempts)
     *
     * @throws IOException Socket bind error
     */
    protected void bind(ServerSocket socket, int portstart, int retries) throws IOException {
        synchronized (bindLock) {
            InetSocketAddress addr = null;
            int port = portstart;
            while (retries > 0) {
                try {
                    addr = new InetSocketAddress(getBind(), port);
                    socket.bind(addr);
                    setPort(port);
                    log.info(sm.getString("receiverBase.socket.bind", addr));
                    retries = 0;
                } catch (IOException ioe) {
                    retries--;
                    if (retries <= 0) {
                        log.info(sm.getString("receiverBase.unable.bind", addr));
                        throw ioe;
                    }
                    port++;
                }
            }
        }
    }

    /**
     * Same as bind() except it does it for the UDP port
     *
     * @param socket    The socket to bind
     * @param portstart Starting port for bind attempts
     * @param retries   Number of times to attempt to bind (port incremented between attempts)
     *
     * @return int The retry count
     *
     * @throws IOException Socket bind error
     */
    protected int bindUdp(DatagramSocket socket, int portstart, int retries) throws IOException {
        InetSocketAddress addr = null;
        while (retries > 0) {
            try {
                addr = new InetSocketAddress(getBind(), portstart);
                socket.bind(addr);
                setUdpPort(portstart);
                log.info(sm.getString("receiverBase.udp.bind", addr));
                return 0;
            } catch (IOException ioe) {
                retries--;
                if (retries <= 0) {
                    log.info(sm.getString("receiverBase.unable.bind.udp", addr));
                    throw ioe;
                }
                portstart++;
                try {
                    Thread.sleep(25);
                } catch (InterruptedException ti) {
                    Thread.currentThread().interrupt();
                }
                retries = bindUdp(socket, portstart, retries);
            }
        }
        return retries;
    }


    /**
     * Receives a channel message and forwards it to the listener if the listener accepts it.
     *
     * @param data the channel message received
     */
    @Override
    public void messageDataReceived(ChannelMessage data) {
        if (this.listener != null) {
            if (listener.accept(data)) {
                listener.messageReceived(data);
            }
        }
    }

    /**
     * Returns the options flags for worker threads.
     *
     * @return the worker thread options
     */
    public int getWorkerThreadOptions() {
        int options = 0;
        if (getDirect()) {
            options = options | OPTION_DIRECT_BUFFER;
        }
        return options;
    }


    /**
     * Sets the bind address.
     *
     * @param bind the bind address to set
     */
    public void setBind(InetAddress bind) {
        this.bind = bind;
    }

    /**
     * Returns whether direct (off-heap) buffers are used.
     *
     * @return true if direct buffers are used
     */
    public boolean getDirect() {
        return direct;
    }


    /**
     * Sets whether direct (off-heap) buffers should be used.
     *
     * @param direct true to use direct buffers
     */
    public void setDirect(boolean direct) {
        this.direct = direct;
    }


    /**
     * Returns the bind address as a string.
     *
     * @return the bind address
     */
    public String getAddress() {
        getBind();
        return this.host;
    }

    /**
     * Returns the host address for this receiver.
     *
     * @return the host address
     */
    @Override
    public String getHost() {
        return getAddress();
    }

    /**
     * Returns the TCP selector timeout in milliseconds.
     *
     * @return the selector timeout
     */
    public long getSelectorTimeout() {
        return tcpSelectorTimeout;
    }

    /**
     * Returns whether the receiver is in listen mode.
     *
     * @return true if the receiver is listening
     */
    public boolean doListen() {
        return listen;
    }

    /**
     * Returns the message listener for this receiver.
     *
     * @return the message listener
     */
    public MessageListener getListener() {
        return listener;
    }

    /**
     * Returns the task pool used by this receiver.
     *
     * @return the task pool
     */
    public RxTaskPool getTaskPool() {
        return pool;
    }

    /**
     * Returns the number of attempts to find an available port for auto-binding.
     *
     * @return the auto-bind retry count
     */
    public int getAutoBind() {
        return autoBind;
    }

    /**
     * Returns the maximum number of threads in the executor pool.
     *
     * @return the maximum thread count
     */
    public int getMaxThreads() {
        return maxThreads;
    }

    /**
     * Returns the minimum number of threads in the executor pool.
     *
     * @return the minimum thread count
     */
    public int getMinThreads() {
        return minThreads;
    }

    /**
     * Returns whether TCP_NODELAY is enabled.
     *
     * @return true if TCP_NODELAY is enabled
     */
    public boolean getTcpNoDelay() {
        return tcpNoDelay;
    }

    /**
     * Returns whether SO_KEEPALIVE is enabled.
     *
     * @return true if SO_KEEPALIVE is enabled
     */
    public boolean getSoKeepAlive() {
        return soKeepAlive;
    }

    /**
     * Returns whether OOBINLINE (out-of-band data) is enabled.
     *
     * @return true if OOBINLINE is enabled
     */
    public boolean getOoBInline() {
        return ooBInline;
    }


    /**
     * Returns whether SO_LINGER is enabled.
     *
     * @return true if SO_LINGER is enabled
     */
    public boolean getSoLingerOn() {
        return soLingerOn;
    }

    /**
     * Returns the SO_LINGER timeout value in seconds.
     *
     * @return the SO_LINGER timeout
     */
    public int getSoLingerTime() {
        return soLingerTime;
    }

    /**
     * Returns whether SO_REUSEADDR is enabled.
     *
     * @return true if SO_REUSEADDR is enabled
     */
    public boolean getSoReuseAddress() {
        return soReuseAddress;
    }

    /**
     * Returns the IP traffic class (TOS) value for sockets.
     *
     * @return the traffic class value
     */
    public int getSoTrafficClass() {
        return soTrafficClass;
    }

    /**
     * Returns the socket timeout in milliseconds.
     *
     * @return the socket timeout
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * Returns whether a buffer pool is used for message handling.
     *
     * @return true if a buffer pool is used
     */
    public boolean getUseBufferPool() {
        return useBufferPool;
    }

    /**
     * Returns the secure port number.
     *
     * @return the secure port number, or -1 if not configured
     */
    @Override
    public int getSecurePort() {
        return securePort;
    }

    /**
     * Returns the minimum number of tasks in the pool.
     *
     * @return the minimum task count
     */
    public int getMinTasks() {
        return minTasks;
    }

    /**
     * Returns the maximum number of tasks in the pool.
     *
     * @return the maximum task count
     */
    public int getMaxTasks() {
        return maxTasks;
    }

    /**
     * Returns the executor service used by this receiver.
     *
     * @return the executor service
     */
    public ExecutorService getExecutor() {
        return executor;
    }

    /**
     * Returns whether the receiver is currently listening for connections.
     *
     * @return true if the receiver is listening
     */
    public boolean isListening() {
        return listen;
    }

    /**
     * Sets the TCP selector timeout in milliseconds.
     *
     * @param selTimeout the selector timeout in milliseconds
     */
    public void setSelectorTimeout(long selTimeout) {
        tcpSelectorTimeout = selTimeout;
    }

    /**
     * Sets whether the receiver should listen for incoming connections.
     *
     * @param doListen true to enable listening
     */
    public void setListen(boolean doListen) {
        this.listen = doListen;
    }


    /**
     * Sets the bind address for this receiver.
     *
     * @param host the host address to bind to
     */
    public void setAddress(String host) {
        this.host = host;
    }

    /**
     * Sets the host address for this receiver.
     *
     * @param host the host address to set
     */
    public void setHost(String host) {
        setAddress(host);
    }

    /**
     * Sets the message listener for this receiver.
     *
     * @param listener the message listener to set
     */
    public void setListener(MessageListener listener) {
        this.listener = listener;
    }

    /**
     * Sets the task pool for this receiver.
     *
     * @param pool the task pool to set
     */
    public void setPool(RxTaskPool pool) {
        this.pool = pool;
    }

    /**
     * Sets the port on which this receiver listens.
     *
     * @param port the port number to set
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Sets the number of attempts to find an available port for auto-binding.
     *
     * @param autoBind the number of retry attempts
     */
    public void setAutoBind(int autoBind) {
        this.autoBind = autoBind;
        if (this.autoBind <= 0) {
            this.autoBind = 1;
        }
    }

    /**
     * Sets the maximum number of threads in the executor pool.
     *
     * @param maxThreads the maximum thread count
     */
    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
    }

    /**
     * Sets the minimum number of threads in the executor pool.
     *
     * @param minThreads the minimum thread count
     */
    public void setMinThreads(int minThreads) {
        this.minThreads = minThreads;
    }

    /**
     * Sets whether TCP_NODELAY should be enabled.
     *
     * @param tcpNoDelay true to enable TCP_NODELAY
     */
    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    /**
     * Sets whether SO_KEEPALIVE should be enabled.
     *
     * @param soKeepAlive true to enable SO_KEEPALIVE
     */
    public void setSoKeepAlive(boolean soKeepAlive) {
        this.soKeepAlive = soKeepAlive;
    }

    /**
     * Sets whether OOBINLINE (out-of-band data) should be enabled.
     *
     * @param ooBInline true to enable OOBINLINE
     */
    public void setOoBInline(boolean ooBInline) {
        this.ooBInline = ooBInline;
    }


    /**
     * Sets whether SO_LINGER should be enabled.
     *
     * @param soLingerOn true to enable SO_LINGER
     */
    public void setSoLingerOn(boolean soLingerOn) {
        this.soLingerOn = soLingerOn;
    }

    /**
     * Sets the SO_LINGER timeout value in seconds.
     *
     * @param soLingerTime the SO_LINGER timeout in seconds
     */
    public void setSoLingerTime(int soLingerTime) {
        this.soLingerTime = soLingerTime;
    }

    /**
     * Sets whether SO_REUSEADDR should be enabled.
     *
     * @param soReuseAddress true to enable SO_REUSEADDR
     */
    public void setSoReuseAddress(boolean soReuseAddress) {
        this.soReuseAddress = soReuseAddress;
    }

    /**
     * Sets the IP traffic class (TOS) value for sockets.
     *
     * @param soTrafficClass the traffic class value
     */
    public void setSoTrafficClass(int soTrafficClass) {
        this.soTrafficClass = soTrafficClass;
    }

    /**
     * Sets the socket timeout in milliseconds.
     *
     * @param timeout the socket timeout in milliseconds
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    /**
     * Sets whether a buffer pool should be used for message handling.
     *
     * @param useBufferPool true to use a buffer pool
     */
    public void setUseBufferPool(boolean useBufferPool) {
        this.useBufferPool = useBufferPool;
    }

    /**
     * Sets the secure port number.
     *
     * @param securePort the secure port number
     */
    public void setSecurePort(int securePort) {
        this.securePort = securePort;
    }

    /**
     * Sets the minimum number of tasks in the pool.
     *
     * @param minTasks the minimum task count
     */
    public void setMinTasks(int minTasks) {
        this.minTasks = minTasks;
    }

    /**
     * Sets the maximum number of tasks in the pool.
     *
     * @param maxTasks the maximum task count
     */
    public void setMaxTasks(int maxTasks) {
        this.maxTasks = maxTasks;
    }

    /**
     * Sets the executor service for this receiver.
     *
     * @param executor the executor service to set
     */
    public void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    /**
     * Performs a heartbeat operation. No-op in this base implementation.
     */
    @Override
    public void heartbeat() {
        // empty operation
    }

    /**
     * Returns the UDP port number.
     *
     * @return the UDP port number, or -1 if not configured
     */
    @Override
    public int getUdpPort() {
        return udpPort;
    }

    /**
     * Sets the UDP port number.
     *
     * @param udpPort the UDP port number
     */
    public void setUdpPort(int udpPort) {
        this.udpPort = udpPort;
    }

    /**
     * Returns the size of the receive buffer for UDP connections.
     *
     * @return the UDP receive buffer size
     */
    public int getUdpRxBufSize() {
        return udpRxBufSize;
    }

    /**
     * Sets the size of the receive buffer for UDP connections.
     *
     * @param udpRxBufSize the UDP receive buffer size
     */
    public void setUdpRxBufSize(int udpRxBufSize) {
        this.udpRxBufSize = udpRxBufSize;
    }

    /**
     * Returns the size of the transmit buffer for UDP connections.
     *
     * @return the UDP transmit buffer size
     */
    public int getUdpTxBufSize() {
        return udpTxBufSize;
    }

    /**
     * Sets the size of the transmit buffer for UDP connections.
     *
     * @param udpTxBufSize the UDP transmit buffer size
     */
    public void setUdpTxBufSize(int udpTxBufSize) {
        this.udpTxBufSize = udpTxBufSize;
    }

    @Override
    public Channel getChannel() {
        return channel;
    }

    @Override
    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    // ---------------------------------------------- stats of the thread pool
    /**
     * Return the current number of threads that are managed by the pool.
     *
     * @return the current number of threads that are managed by the pool
     */
    public int getPoolSize() {
        if (executor instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) executor).getPoolSize();
        } else {
            return -1;
        }
    }

    /**
     * Return the current number of threads that are in use.
     *
     * @return the current number of threads that are in use
     */
    public int getActiveCount() {
        if (executor instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) executor).getActiveCount();
        } else {
            return -1;
        }
    }

    /**
     * Return the total number of tasks that have ever been scheduled for execution by the pool.
     *
     * @return the total number of tasks that have ever been scheduled for execution by the pool
     */
    public long getTaskCount() {
        if (executor instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) executor).getTaskCount();
        } else {
            return -1;
        }
    }

    /**
     * Return the total number of tasks that have completed execution by the pool.
     *
     * @return the total number of tasks that have completed execution by the pool
     */
    public long getCompletedTaskCount() {
        if (executor instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) executor).getCompletedTaskCount();
        } else {
            return -1;
        }
    }

    // ---------------------------------------------- ThreadFactory Inner Class
    class TaskThreadFactory implements ThreadFactory {
        final ThreadGroup group;
        final AtomicInteger threadNumber = new AtomicInteger(1);
        final String namePrefix;

        TaskThreadFactory(String namePrefix) {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(daemon);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }

    /**
     * Returns whether the worker threads are daemon threads.
     *
     * @return true if worker threads are daemon threads
     */
    public boolean isDaemon() {
        return daemon;
    }

    /**
     * Returns the maximum idle time for threads in the executor pool.
     *
     * @return the maximum idle time in milliseconds
     */
    public long getMaxIdleTime() {
        return maxIdleTime;
    }

    /**
     * Sets whether the worker threads should be daemon threads.
     *
     * @param daemon true to use daemon threads
     */
    public void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    /**
     * Sets the maximum idle time for threads in the executor pool.
     *
     * @param maxIdleTime the maximum idle time in milliseconds
     */
    public void setMaxIdleTime(long maxIdleTime) {
        this.maxIdleTime = maxIdleTime;
    }

}

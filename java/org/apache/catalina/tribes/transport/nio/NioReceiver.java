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

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Deque;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.catalina.tribes.io.ObjectReader;
import org.apache.catalina.tribes.transport.AbstractRxTask;
import org.apache.catalina.tribes.transport.ReceiverBase;
import org.apache.catalina.tribes.transport.RxTaskPool;
import org.apache.catalina.tribes.util.ExceptionUtils;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class NioReceiver extends ReceiverBase implements Runnable, NioReceiverMBean {

    private static final Log log = LogFactory.getLog(NioReceiver.class);

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(NioReceiver.class);

    private volatile boolean running = false;

    private AtomicReference<Selector> selector = new AtomicReference<>();
    private ServerSocketChannel serverChannel = null;
    private DatagramChannel datagramChannel = null;

    protected final Deque<Runnable> events = new ConcurrentLinkedDeque<>();

    public NioReceiver() {
    }

    @Override
    public void stop() {
        this.stopListening();
        super.stop();
    }

    @Override
    public void start() throws IOException {
        super.start();
        try {
            setPool(new RxTaskPool(getMaxThreads(), getMinThreads(), this));
        } catch (Exception x) {
            log.fatal(sm.getString("nioReceiver.threadpool.fail"), x);
            if (x instanceof IOException) {
                throw (IOException) x;
            } else {
                throw new IOException(x.getMessage());
            }
        }
        try {
            getBind();
            bind();
            String channelName = "";
            if (getChannel().getName() != null) {
                channelName = "[" + getChannel().getName() + "]";
            }
            Thread t = new Thread(this, "NioReceiver" + channelName);
            t.setDaemon(true);
            t.start();
        } catch (Exception x) {
            log.fatal(sm.getString("nioReceiver.start.fail"), x);
            if (x instanceof IOException) {
                throw (IOException) x;
            } else {
                throw new IOException(x.getMessage());
            }
        }
    }

    @Override
    public AbstractRxTask createRxTask() {
        NioReplicationTask thread = new NioReplicationTask(this, this);
        thread.setUseBufferPool(this.getUseBufferPool());
        thread.setRxBufSize(getRxBufSize());
        thread.setOptions(getWorkerThreadOptions());
        return thread;
    }


    protected void bind() throws IOException {
        // allocate an unbound server socket channel
        serverChannel = ServerSocketChannel.open();
        // Get the associated ServerSocket to bind it with
        ServerSocket serverSocket = serverChannel.socket();
        // create a new Selector for use below
        this.selector.set(Selector.open());
        // set the port the server channel will listen to
        // serverSocket.bind(new InetSocketAddress(getBind(), getTcpListenPort()));
        bind(serverSocket, getPort(), getAutoBind());
        // set non-blocking mode for the listening socket
        serverChannel.configureBlocking(false);
        // register the ServerSocketChannel with the Selector
        serverChannel.register(this.selector.get(), SelectionKey.OP_ACCEPT);

        // set up the datagram channel
        if (this.getUdpPort() > 0) {
            datagramChannel = DatagramChannel.open();
            configureDatagraChannel();
            // bind to the address to avoid security checks
            bindUdp(datagramChannel.socket(), getUdpPort(), getAutoBind());
        }
    }

    private void configureDatagraChannel() throws IOException {
        datagramChannel.configureBlocking(false);
        datagramChannel.socket().setSendBufferSize(getUdpTxBufSize());
        datagramChannel.socket().setReceiveBufferSize(getUdpRxBufSize());
        datagramChannel.socket().setReuseAddress(getSoReuseAddress());
        datagramChannel.socket().setSoTimeout(getTimeout());
        datagramChannel.socket().setTrafficClass(getSoTrafficClass());
    }

    public void addEvent(Runnable event) {
        Selector selector = this.selector.get();
        if (selector != null) {
            events.add(event);
            if (log.isTraceEnabled()) {
                log.trace("Adding event to selector:" + event);
            }
            if (isListening()) {
                selector.wakeup();
            }
        }
    }

    public void events() {
        if (events.isEmpty()) {
            return;
        }
        Runnable r = null;
        while ((r = events.pollFirst()) != null) {
            try {
                if (log.isTraceEnabled()) {
                    log.trace("Processing event in selector:" + r);
                }
                r.run();
            } catch (Exception x) {
                log.error(sm.getString("nioReceiver.eventsError"), x);
            }
        }
    }

    public static void cancelledKey(SelectionKey key) {
        ObjectReader reader = (ObjectReader) key.attachment();
        if (reader != null) {
            reader.setCancelled(true);
            reader.finish();
        }
        key.cancel();
        key.attach(null);
        if (key.channel() instanceof SocketChannel) {
            try {
                ((SocketChannel) key.channel()).socket().close();
            } catch (IOException e) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("nioReceiver.closeError"), e);
                }
            }
        }
        if (key.channel() instanceof DatagramChannel) {
            try {
                ((DatagramChannel) key.channel()).socket().close();
            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("nioReceiver.closeError"), e);
                }
            }
        }
        try {
            key.channel().close();
        } catch (IOException e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("nioReceiver.closeError"), e);
            }
        }

    }

    protected long lastCheck = System.currentTimeMillis();

    protected void socketTimeouts() {
        long now = System.currentTimeMillis();
        if ((now - lastCheck) < getSelectorTimeout()) {
            return;
        }
        // timeout
        Selector tmpsel = this.selector.get();
        Set<SelectionKey> keys = (isListening() && tmpsel != null) ? tmpsel.keys() : null;
        if (keys == null) {
            return;
        }
        for (SelectionKey key : keys) {
            try {
                // if (key.interestOps() == SelectionKey.OP_READ) {
                // //only timeout sockets that we are waiting for a read from
                // ObjectReader ka = (ObjectReader) key.attachment();
                // long delta = now - ka.getLastAccess();
                // if (delta > (long) getTimeout()) {
                // cancelledKey(key);
                // }
                // }
                // else
                if (key.interestOps() == 0) {
                    // check for keys that didn't make it in.
                    ObjectReader ka = (ObjectReader) key.attachment();
                    if (ka != null) {
                        long delta = now - ka.getLastAccess();
                        if (delta > getTimeout() && (!ka.isAccessed())) {
                            if (log.isWarnEnabled()) {
                                log.warn(sm.getString("nioReceiver.threadsExhausted", Integer.valueOf(getTimeout()),
                                        Boolean.valueOf(ka.isCancelled()), key,
                                        new java.sql.Timestamp(ka.getLastAccess())));
                            }
                            ka.setLastAccess(now);
                            // key.interestOps(SelectionKey.OP_READ);
                        } // end if
                    } else {
                        cancelledKey(key);
                    } // end if
                } // end if
            } catch (CancelledKeyException ckx) {
                cancelledKey(key);
            }
        }
        lastCheck = System.currentTimeMillis();
    }


    /**
     * Get data from channel and store in byte array send it to cluster
     *
     * @throws IOException IO error
     */
    protected void listen() throws Exception {
        if (doListen()) {
            log.warn(sm.getString("nioReceiver.alreadyStarted"));
            return;
        }

        setListen(true);

        // Avoid NPEs if selector is set to null on stop.
        Selector selector = this.selector.get();

        if (selector != null && datagramChannel != null) {
            ObjectReader oreader = new ObjectReader(MAX_UDP_SIZE); // max size for a datagram packet
            registerChannel(selector, datagramChannel, SelectionKey.OP_READ, oreader);
        }

        while (doListen() && selector != null) {
            // this may block for a long time, upon return the
            // selected set contains keys of the ready channels
            try {
                events();
                socketTimeouts();
                int n = selector.select(getSelectorTimeout());
                if (n == 0) {
                    // there is a good chance that we got here
                    // because the TcpReplicationThread called
                    // selector wakeup().
                    // if that happens, we must ensure that that
                    // thread has enough time to call interestOps
                    // synchronized (interestOpsMutex) {
                    // if we got the lock, means there are no
                    // keys trying to register for the
                    // interestOps method
                    // }
                    continue; // nothing to do
                }
                // get an iterator over the set of selected keys
                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                // look at each key in the selected set
                while (it != null && it.hasNext()) {
                    SelectionKey key = it.next();
                    // Is a new connection coming in?
                    if (key.isAcceptable()) {
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        SocketChannel channel = server.accept();
                        channel.socket().setReceiveBufferSize(getRxBufSize());
                        channel.socket().setSendBufferSize(getTxBufSize());
                        channel.socket().setTcpNoDelay(getTcpNoDelay());
                        channel.socket().setKeepAlive(getSoKeepAlive());
                        channel.socket().setOOBInline(getOoBInline());
                        channel.socket().setReuseAddress(getSoReuseAddress());
                        channel.socket().setSoLinger(getSoLingerOn(), getSoLingerTime());
                        channel.socket().setSoTimeout(getTimeout());
                        Object attach = new ObjectReader(channel);
                        registerChannel(selector, channel, SelectionKey.OP_READ, attach);
                    }
                    // is there data to read on this channel?
                    if (key.isReadable()) {
                        readDataFromSocket(key);
                    } else {
                        key.interestOps(key.interestOps() & (~SelectionKey.OP_WRITE));
                    }

                    // remove key from selected set, it's been handled
                    it.remove();
                }
            } catch (ClosedSelectorException cse) {
                // ignore is normal at shutdown or stop listen socket
            } catch (CancelledKeyException nx) {
                log.warn(sm.getString("nioReceiver.clientDisconnect"));
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.error(sm.getString("nioReceiver.requestError"), t);
            }

        }
        serverChannel.close();
        if (datagramChannel != null) {
            try {
                datagramChannel.close();
            } catch (Exception iox) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("nioReceiver.closeError"), iox);
                }
            }
            datagramChannel = null;
        }
        closeSelector();
    }


    /**
     * Close Selector.
     *
     * @see org.apache.catalina.tribes.transport.ReceiverBase#stop()
     */
    protected void stopListening() {
        setListen(false);
        Selector selector = this.selector.get();
        if (selector != null) {
            try {
                // Unlock the thread if it is blocked waiting for input
                selector.wakeup();
                // Wait for the receiver thread to finish
                int count = 0;
                while (running && count < 50) {
                    Thread.sleep(100);
                    count++;
                }
                if (running) {
                    log.warn(sm.getString("nioReceiver.stop.threadRunning"));
                }
                closeSelector();
            } catch (Exception x) {
                log.error(sm.getString("nioReceiver.stop.fail"), x);
            } finally {
                this.selector.set(null);
            }
        }
    }

    private void closeSelector() throws IOException {
        Selector selector = this.selector.getAndSet(null);
        if (selector == null) {
            return;
        }
        try {
            // look at each key in the selected set
            for (SelectionKey key : selector.keys()) {
                key.channel().close();
                key.attach(null);
                key.cancel();
            }
        } catch (IOException ignore) {
            if (log.isWarnEnabled()) {
                log.warn(sm.getString("nioReceiver.cleanup.fail"), ignore);
            }
        } catch (ClosedSelectorException ignore) {
            // Ignore
        }
        try {
            selector.selectNow();
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            // Ignore everything else
        }
        selector.close();
    }

    // ----------------------------------------------------------

    /**
     * Register the given channel with the given selector for the given operations of interest
     *
     * @param selector The selector to use
     * @param channel  The channel
     * @param ops      The operations to register
     * @param attach   Attachment object
     *
     * @throws Exception IO error with channel
     */
    protected void registerChannel(Selector selector, SelectableChannel channel, int ops, Object attach)
            throws Exception {
        if (channel == null) {
            return; // could happen
        }
        // set the new channel non-blocking
        channel.configureBlocking(false);
        // register it with the selector
        channel.register(selector, ops, attach);
    }

    /**
     * Start thread and listen
     */
    @Override
    public void run() {
        running = true;
        try {
            listen();
        } catch (Exception x) {
            log.error(sm.getString("nioReceiver.run.fail"), x);
        } finally {
            running = false;
        }
    }

    // ----------------------------------------------------------

    /**
     * Sample data handler method for a channel with data ready to read.
     *
     * @param key A SelectionKey object associated with a channel determined by the selector to be ready for reading. If
     *                the channel returns an EOF condition, it is closed here, which automatically invalidates the
     *                associated key. The selector will then de-register the channel on the next select call.
     *
     * @throws Exception IO error with channel
     */
    protected void readDataFromSocket(SelectionKey key) throws Exception {
        NioReplicationTask task = (NioReplicationTask) getTaskPool().getRxTask();
        if (task == null) {
            // No threads/tasks available, do nothing, the selection
            // loop will keep calling this method until a
            // thread becomes available, the thread pool itself has a waiting mechanism
            // so we will not wait here.
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("nioReceiver.noThread"));
            }
        } else {
            // invoking this wakes up the worker thread then returns
            // add task to thread pool
            task.serviceChannel(key);
            getExecutor().execute(task);
        }
    }


}

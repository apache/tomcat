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
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import org.apache.catalina.tribes.ChannelReceiver;
import org.apache.catalina.tribes.io.ListenCallback;
import org.apache.catalina.tribes.io.ObjectReader;
import org.apache.catalina.tribes.transport.Constants;
import org.apache.catalina.tribes.transport.ReceiverBase;
import org.apache.catalina.tribes.transport.RxTaskPool;
import org.apache.catalina.tribes.transport.AbstractRxTask;
import org.apache.catalina.tribes.util.StringManager;
import java.util.LinkedList;
import java.util.Set;
import java.nio.channels.CancelledKeyException;

/**
 * @author Filip Hanik
 * @version $Revision$ $Date$
 */
public class NioReceiver extends ReceiverBase implements Runnable, ChannelReceiver, ListenCallback {

    protected static org.apache.juli.logging.Log log = org.apache.juli.logging.LogFactory.getLog(NioReceiver.class);

    /**
     * The string manager for this package.
     */
    protected StringManager sm = StringManager.getManager(Constants.Package);

    /**
     * The descriptive information about this implementation.
     */
    private static final String info = "NioReceiver/1.0";

    private Selector selector = null;
    private ServerSocketChannel serverChannel = null;

    protected LinkedList events = new LinkedList();
//    private Object interestOpsMutex = new Object();

    public NioReceiver() {
    }

    /**
     * Return descriptive information about this implementation and the
     * corresponding version number, in the format
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {
        return (info);
    }

//    public Object getInterestOpsMutex() {
//        return interestOpsMutex;
//    }

    public void stop() {
        this.stopListening();
        super.stop();
    }

    /**
     * start cluster receiver
     * @throws Exception
     * @see org.apache.catalina.tribes.ClusterReceiver#start()
     */
    public void start() throws IOException {
        super.start();
        try {
            setPool(new RxTaskPool(getMaxThreads(),getMinThreads(),this));
        } catch (Exception x) {
            log.fatal("ThreadPool can initilzed. Listener not started", x);
            if ( x instanceof IOException ) throw (IOException)x;
            else throw new IOException(x.getMessage());
        }
        try {
            getBind();
            bind();
            Thread t = new Thread(this, "NioReceiver");
            t.setDaemon(true);
            t.start();
        } catch (Exception x) {
            log.fatal("Unable to start cluster receiver", x);
            if ( x instanceof IOException ) throw (IOException)x;
            else throw new IOException(x.getMessage());
        }
    }
    
    public AbstractRxTask createRxTask() {
        NioReplicationTask thread = new NioReplicationTask(this,this);
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
        selector = Selector.open();
        // set the port the server channel will listen to
        //serverSocket.bind(new InetSocketAddress(getBind(), getTcpListenPort()));
        bind(serverSocket,getTcpListenPort(),getAutoBind());
        // set non-blocking mode for the listening socket
        serverChannel.configureBlocking(false);
        // register the ServerSocketChannel with the Selector
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
    }
    
    public void addEvent(Runnable event) {
        if ( selector != null ) {
            synchronized (events) {
                events.add(event);
            }
            if ( log.isTraceEnabled() ) log.trace("Adding event to selector:"+event);
            if ( isListening() && selector!=null ) selector.wakeup();
        }
    }

    public void events() {
        if ( events.size() == 0 ) return;
        synchronized (events) {
            Runnable r = null;
            while ( (events.size() > 0) && (r = (Runnable)events.removeFirst()) != null ) {
                try {
                    if ( log.isTraceEnabled() ) log.trace("Processing event in selector:"+r);
                    r.run();
                } catch ( Exception x ) {
                    log.error("",x);
                }
            }
            events.clear();
        }
    }
    
    public static void cancelledKey(SelectionKey key) {
        ObjectReader reader = (ObjectReader)key.attachment();
        if ( reader != null ) {
            reader.setCancelled(true);
            reader.finish();
        }
        key.cancel(); 
        key.attach(null);
        try { ((SocketChannel)key.channel()).socket().close(); } catch (IOException e) { if (log.isDebugEnabled()) log.debug("", e); }
        try { key.channel().close(); } catch (IOException e) { if (log.isDebugEnabled()) log.debug("", e); }
        
    }
    
    protected void socketTimeouts() {
        //timeout
        Selector tmpsel = selector;
        Set keys =  (isListening()&&tmpsel!=null)?tmpsel.keys():null;
        if ( keys == null ) return;
        long now = System.currentTimeMillis();
        for (Iterator iter = keys.iterator(); iter.hasNext(); ) {
            SelectionKey key = (SelectionKey) iter.next();
            try {
//                if (key.interestOps() == SelectionKey.OP_READ) {
//                    //only timeout sockets that we are waiting for a read from
//                    ObjectReader ka = (ObjectReader) key.attachment();
//                    long delta = now - ka.getLastAccess();
//                    if (delta > (long) getTimeout()) {
//                        cancelledKey(key);
//                    }
//                }
//                else
                if ( key.interestOps() == 0 ) {
                    //check for keys that didn't make it in.
                    ObjectReader ka = (ObjectReader) key.attachment();
                    if ( ka != null ) {
                        long delta = now - ka.getLastAccess();
                        if (delta > (long) getTimeout() && (!ka.isAccessed())) {
                            log.warn("Channel key is registered, but has had no interest ops for the last "+getTimeout()+" ms. (cancelled:"+ka.isCancelled()+"):"+key+" last access:"+new java.sql.Timestamp(ka.getLastAccess()));
//                            System.out.println("Interest:"+key.interestOps());
//                            System.out.println("Ready Ops:"+key.readyOps());
//                            System.out.println("Valid:"+key.isValid());
                            ka.setLastAccess(now);
                            //key.interestOps(SelectionKey.OP_READ);
                        }//end if
                    } else {
                        cancelledKey(key);
                    }//end if
                }//end if
            }catch ( CancelledKeyException ckx ) {
                cancelledKey(key);
            }
        }
    }


    /**
     * get data from channel and store in byte array
     * send it to cluster
     * @throws IOException
     * @throws java.nio.channels.ClosedChannelException
     */
    protected void listen() throws Exception {
        if (doListen()) {
            log.warn("ServerSocketChannel already started");
            return;
        }
        
        setListen(true);

        while (doListen() && selector != null) {
            // this may block for a long time, upon return the
            // selected set contains keys of the ready channels
            try {
                events();
                socketTimeouts();
                int n = selector.select(getTcpSelectorTimeout());
                if (n == 0) {
                    //there is a good chance that we got here
                    //because the TcpReplicationThread called
                    //selector wakeup().
                    //if that happens, we must ensure that that
                    //thread has enough time to call interestOps
//                    synchronized (interestOpsMutex) {
                        //if we got the lock, means there are no
                        //keys trying to register for the
                        //interestOps method
//                    }
                    continue; // nothing to do
                }
                // get an iterator over the set of selected keys
                Iterator it = selector.selectedKeys().iterator();
                // look at each key in the selected set
                while (it.hasNext()) {
                    SelectionKey key = (SelectionKey) it.next();
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
                        channel.socket().setSoLinger(getSoLingerOn(),getSoLingerTime());
                        channel.socket().setTrafficClass(getSoTrafficClass());
                        channel.socket().setSoTimeout(getTimeout());
                        Object attach = new ObjectReader(channel);
                        registerChannel(selector,
                                        channel,
                                        SelectionKey.OP_READ,
                                        attach);
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
            } catch (java.nio.channels.ClosedSelectorException cse) {
                // ignore is normal at shutdown or stop listen socket
            } catch (java.nio.channels.CancelledKeyException nx) {
                log.warn("Replication client disconnected, error when polling key. Ignoring client.");
            } catch (Throwable x) {
                try {
                    log.error("Unable to process request in NioReceiver", x);
                }catch ( Throwable tx ) {
                    //in case an out of memory error, will affect the logging framework as well
                    tx.printStackTrace();
                }
            }

        }
        serverChannel.close();
        if (selector != null)
            selector.close();
    }

    

    /**
     * Close Selector.
     *
     * @see org.apache.catalina.tribes.transport.ClusterReceiverBase#stopListening()
     */
    protected void stopListening() {
        setListen(false);
        if (selector != null) {
            try {
                selector.wakeup();
                selector.close();
            } catch (Exception x) {
                log.error("Unable to close cluster receiver selector.", x);
            } finally {
                selector = null;
            }
        }
    }

    // ----------------------------------------------------------

    /**
     * Register the given channel with the given selector for
     * the given operations of interest
     */
    protected void registerChannel(Selector selector,
                                   SelectableChannel channel,
                                   int ops,
                                   Object attach) throws Exception {
        if (channel == null)return; // could happen
        // set the new channel non-blocking
        channel.configureBlocking(false);
        // register it with the selector
        channel.register(selector, ops, attach);
    }

    /**
     * Start thread and listen
     */
    public void run() {
        try {
            listen();
        } catch (Exception x) {
            log.error("Unable to run replication listener.", x);
        }
    }

    // ----------------------------------------------------------

    /**
     * Sample data handler method for a channel with data ready to read.
     * @param key A SelectionKey object associated with a channel
     *  determined by the selector to be ready for reading.  If the
     *  channel returns an EOF condition, it is closed here, which
     *  automatically invalidates the associated key.  The selector
     *  will then de-register the channel on the next select call.
     */
    protected void readDataFromSocket(SelectionKey key) throws Exception {
        NioReplicationTask task = (NioReplicationTask) getTaskPool().getRxTask();
        if (task == null) {
            // No threads/tasks available, do nothing, the selection
            // loop will keep calling this method until a
            // thread becomes available, the thread pool itself has a waiting mechanism
            // so we will not wait here.
            if (log.isDebugEnabled()) log.debug("No TcpReplicationThread available");
        } else {
            // invoking this wakes up the worker thread then returns
            //add task to thread pool
            task.serviceChannel(key);
            getExecutor().execute(task);
        }
    }


}

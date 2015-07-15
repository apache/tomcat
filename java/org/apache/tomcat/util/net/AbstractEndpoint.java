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

import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.apache.juli.logging.Log;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.net.AbstractEndpoint.Acceptor.AcceptorState;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.threads.LimitLatch;
import org.apache.tomcat.util.threads.ResizableExecutor;
import org.apache.tomcat.util.threads.TaskQueue;
import org.apache.tomcat.util.threads.TaskThreadFactory;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;

/**
 * @param <S> The type for the sockets managed by this endpoint.
 *
 * @author Mladen Turk
 * @author Remy Maucherat
 */
public abstract class AbstractEndpoint<S> {

    // -------------------------------------------------------------- Constants

    protected static final StringManager sm = StringManager.getManager(
            AbstractEndpoint.class.getPackage().getName());

    public static interface Handler<S> {

        /**
         * Different types of socket states to react upon.
         */
        public enum SocketState {
            // TODO Add a new state to the AsyncStateMachine and remove
            //      ASYNC_END (if possible)
            OPEN, CLOSED, LONG, ASYNC_END, SENDFILE, UPGRADING, UPGRADED
        }


        /**
         * Process the provided socket with the given current status.
         *
         * @param socket The socket to process
         * @param status The current socket status
         *
         * @return The state of the socket after processing
         */
        public SocketState process(SocketWrapperBase<S> socket,
                SocketStatus status);


        /**
         * Obtain the GlobalRequestProcessor associated with the handler.
         *
         * @return the GlobalRequestProcessor
         */
        public Object getGlobal();


        /**
         * Release any resources associated with the given SocketWrapper.
         *
         * @param socketWrapper The socketWrapper to release resources for
         */
        public void release(SocketWrapperBase<S> socketWrapper);


        /**
         * Inform the handler that the endpoint has stopped accepting any new
         * connections. Typically, the endpoint will be stopped shortly
         * afterwards but it is possible that the endpoint will be resumed so
         * the handler should not assume that a stop will follow.
         */
        public void pause();


        /**
         * Recycle resources associated with the handler.
         */
        public void recycle();
    }

    protected enum BindState {
        UNBOUND, BOUND_ON_INIT, BOUND_ON_START
    }

    public abstract static class Acceptor implements Runnable {
        public enum AcceptorState {
            NEW, RUNNING, PAUSED, ENDED
        }

        protected volatile AcceptorState state = AcceptorState.NEW;
        public final AcceptorState getState() {
            return state;
        }

        private String threadName;
        protected final void setThreadName(final String threadName) {
            this.threadName = threadName;
        }
        protected final String getThreadName() {
            return threadName;
        }
    }


    private static final int INITIAL_ERROR_DELAY = 50;
    private static final int MAX_ERROR_DELAY = 1600;


    /**
     * Async timeout thread
     */
    protected class AsyncTimeout implements Runnable {

        private volatile boolean asyncTimeoutRunning = true;

        /**
         * The background thread that checks async requests and fires the
         * timeout if there has been no activity.
         */
        @Override
        public void run() {

            // Loop until we receive a shutdown command
            while (asyncTimeoutRunning) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Ignore
                }
                long now = System.currentTimeMillis();
                for (SocketWrapperBase<S> socket : waitingRequests) {
                    long asyncTimeout = socket.getAsyncTimeout();
                    if (asyncTimeout > 0) {
                        long asyncStart = socket.getLastAsyncStart();
                        if ((now - asyncStart) > asyncTimeout) {
                            // Avoid multiple timeouts
                            socket.setAsyncTimeout(-1);
                            processSocket(socket, SocketStatus.TIMEOUT, true);
                        }
                    }
                }

                // Loop if endpoint is paused
                while (paused && asyncTimeoutRunning) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }

            }
        }


        protected void stop() {
            asyncTimeoutRunning = false;
        }
    }


    // ----------------------------------------------------------------- Fields


    /**
     * Running state of the endpoint.
     */
    protected volatile boolean running = false;


    /**
     * Will be set to true whenever the endpoint is paused.
     */
    protected volatile boolean paused = false;

    /**
     * Are we using an internal executor
     */
    protected volatile boolean internalExecutor = false;


    /**
     * counter for nr of connections handled by an endpoint
     */
    private volatile LimitLatch connectionLimitLatch = null;

    /**
     * Socket properties
     */
    protected SocketProperties socketProperties = new SocketProperties();
    public SocketProperties getSocketProperties() {
        return socketProperties;
    }

    /**
     * Threads used to accept new connections and pass them to worker threads.
     */
    protected Acceptor[] acceptors;


    // ----------------------------------------------------------------- Properties

    private String defaultSSLHostConfigName = SSLHostConfig.DEFAULT_SSL_HOST_NAME;
    public String getDefaultSSLHostConfigName() {
        return defaultSSLHostConfigName;
    }
    public void setDefaultSSLHostConfigName(String defaultSSLHostConfigName) {
        this.defaultSSLHostConfigName = defaultSSLHostConfigName;
    }


    protected Map<String,SSLHostConfig> sslHostConfigs = new ConcurrentHashMap<>();
    public void addSslHostConfig(SSLHostConfig sslHostConfig) {
        String key = sslHostConfig.getHostName();
        if (key == null || key.length() == 0) {
            throw new IllegalArgumentException(sm.getString("endpoint.noSslHostName"));
        }
        SSLHostConfig duplicate = sslHostConfigs.put(key, sslHostConfig);
        if (duplicate != null) {
            throw new IllegalArgumentException(sm.getString("endpoint.duplicateSslHostName", key));
        }
        sslHostConfig.setConfigType(getSslConfigType());
    }
    public SSLHostConfig[] findSslHostConfigs() {
        return sslHostConfigs.values().toArray(new SSLHostConfig[0]);
    }
    protected abstract SSLHostConfig.Type getSslConfigType();

    protected SSLHostConfig getSSLHostConfig(String sniHostName) {
        SSLHostConfig result = null;

        if (sniHostName != null) {
            // First choice - direct match
            result = sslHostConfigs.get(sniHostName);
            if (result != null) {
                return result;
            }
            // Second choice, wildcard match
            int indexOfDot = sniHostName.indexOf('.');
            if (indexOfDot > -1) {
                result = sslHostConfigs.get("*" + sniHostName.substring(indexOfDot));
            }
        }

        // Fall-back. Use the default
        if (result == null) {
            result = sslHostConfigs.get(getDefaultSSLHostConfigName());
        }
        if (result == null) {
            // Should never happen.
            throw new IllegalStateException();
        }
        return result;
    }


    /**
     * Has the user requested that send file be used where possible?
     */
    private boolean useSendfile = true;
    public boolean getUseSendfile() {
        return useSendfile;
    }
    public void setUseSendfile(boolean useSendfile) {
        this.useSendfile = useSendfile;
    }


    /**
     * Time to wait for the internal executor (if used) to terminate when the
     * endpoint is stopped in milliseconds. Defaults to 5000 (5 seconds).
     */
    private long executorTerminationTimeoutMillis = 5000;

    public long getExecutorTerminationTimeoutMillis() {
        return executorTerminationTimeoutMillis;
    }

    public void setExecutorTerminationTimeoutMillis(
            long executorTerminationTimeoutMillis) {
        this.executorTerminationTimeoutMillis = executorTerminationTimeoutMillis;
    }


    /**
     * Acceptor thread count.
     */
    protected int acceptorThreadCount = 1;

    public void setAcceptorThreadCount(int acceptorThreadCount) {
        this.acceptorThreadCount = acceptorThreadCount;
    }
    public int getAcceptorThreadCount() { return acceptorThreadCount; }


    /**
     * Priority of the acceptor threads.
     */
    protected int acceptorThreadPriority = Thread.NORM_PRIORITY;
    public void setAcceptorThreadPriority(int acceptorThreadPriority) {
        this.acceptorThreadPriority = acceptorThreadPriority;
    }
    public int getAcceptorThreadPriority() { return acceptorThreadPriority; }


    private int maxConnections = 10000;
    public void setMaxConnections(int maxCon) {
        this.maxConnections = maxCon;
        LimitLatch latch = this.connectionLimitLatch;
        if (latch != null) {
            // Update the latch that enforces this
            if (maxCon == -1) {
                releaseConnectionLatch();
            } else {
                latch.setLimit(maxCon);
            }
        } else if (maxCon > 0) {
            initializeConnectionLatch();
        }
    }

    public int  getMaxConnections() { return this.maxConnections; }

    /**
     * Return the current count of connections handled by this endpoint, if the
     * connections are counted (which happens when the maximum count of
     * connections is limited), or <code>-1</code> if they are not. This
     * property is added here so that this value can be inspected through JMX.
     * It is visible on "ThreadPool" MBean.
     *
     * <p>The count is incremented by the Acceptor before it tries to accept a
     * new connection. Until the limit is reached and thus the count cannot be
     * incremented,  this value is more by 1 (the count of acceptors) than the
     * actual count of connections that are being served.
     *
     * @return The count
     */
    public long getConnectionCount() {
        LimitLatch latch = connectionLimitLatch;
        if (latch != null) {
            return latch.getCount();
        }
        return -1;
    }

    /**
     * External Executor based thread pool.
     */
    private Executor executor = null;
    public void setExecutor(Executor executor) {
        this.executor = executor;
        this.internalExecutor = (executor==null);
    }
    public Executor getExecutor() { return executor; }


    /**
     * Server socket port.
     */
    private int port;
    public int getPort() { return port; }
    public void setPort(int port ) { this.port=port; }

    public abstract int getLocalPort();

    /**
     * Address for the server socket.
     */
    private InetAddress address;
    public InetAddress getAddress() { return address; }
    public void setAddress(InetAddress address) { this.address = address; }

    /**
     * Allows the server developer to specify the backlog that
     * should be used for server sockets. By default, this value
     * is 100.
     */
    private int backlog = 100;
    public void setBacklog(int backlog) { if (backlog > 0) this.backlog = backlog; }
    public int getBacklog() { return backlog; }

    /**
     * Controls when the Endpoint binds the port. <code>true</code>, the default
     * binds the port on {@link #init()} and unbinds it on {@link #destroy()}.
     * If set to <code>false</code> the port is bound on {@link #start()} and
     * unbound on {@link #stop()}.
     */
    private boolean bindOnInit = true;
    public boolean getBindOnInit() { return bindOnInit; }
    public void setBindOnInit(boolean b) { this.bindOnInit = b; }
    private BindState bindState = BindState.UNBOUND;

    /**
     * Keepalive timeout, if not set the soTimeout is used.
     */
    private Integer keepAliveTimeout = null;
    public int getKeepAliveTimeout() {
        if (keepAliveTimeout == null) {
            return getSoTimeout();
        } else {
            return keepAliveTimeout.intValue();
        }
    }
    public void setKeepAliveTimeout(int keepAliveTimeout) {
        this.keepAliveTimeout = Integer.valueOf(keepAliveTimeout);
    }


    /**
     * Socket TCP no delay.
     *
     * @return The current TCP no delay setting for sockets created by this
     *         endpoint
     */
    public boolean getTcpNoDelay() { return socketProperties.getTcpNoDelay();}
    public void setTcpNoDelay(boolean tcpNoDelay) { socketProperties.setTcpNoDelay(tcpNoDelay); }


    /**
     * Socket linger.
     *
     * @return The current socket linger time for sockets created by this
     *         endpoint
     */
    public int getSoLinger() { return socketProperties.getSoLingerTime(); }
    public void setSoLinger(int soLinger) {
        socketProperties.setSoLingerTime(soLinger);
        socketProperties.setSoLingerOn(soLinger>=0);
    }


    /**
     * Socket timeout.
     *
     * @return The current socket timeout for sockets created by this endpoint
     */
    public int getSoTimeout() { return socketProperties.getSoTimeout(); }
    public void setSoTimeout(int soTimeout) { socketProperties.setSoTimeout(soTimeout); }

    /**
     * SSL engine.
     */
    private boolean SSLEnabled = false;
    public boolean isSSLEnabled() { return SSLEnabled; }
    public void setSSLEnabled(boolean SSLEnabled) { this.SSLEnabled = SSLEnabled; }


    private int minSpareThreads = 10;
    public int getMinSpareThreads() {
        return Math.min(minSpareThreads,getMaxThreads());
    }
    public void setMinSpareThreads(int minSpareThreads) {
        this.minSpareThreads = minSpareThreads;
        if (running && executor!=null) {
            if (executor instanceof java.util.concurrent.ThreadPoolExecutor) {
                ((java.util.concurrent.ThreadPoolExecutor)executor).setCorePoolSize(minSpareThreads);
            } else if (executor instanceof ResizableExecutor) {
                ((ResizableExecutor)executor).resizePool(minSpareThreads, maxThreads);
            }
        }
    }

    /**
     * Maximum amount of worker threads.
     */
    private int maxThreads = 200;
    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
        if (running && executor!=null) {
            if (executor instanceof java.util.concurrent.ThreadPoolExecutor) {
                ((java.util.concurrent.ThreadPoolExecutor)executor).setMaximumPoolSize(maxThreads);
            } else if (executor instanceof ResizableExecutor) {
                ((ResizableExecutor)executor).resizePool(minSpareThreads, maxThreads);
            }
        }
    }
    public int getMaxThreads() {
        return getMaxThreadsExecutor(running);
    }
    protected int getMaxThreadsExecutor(boolean useExecutor) {
        if (useExecutor && executor != null) {
            if (executor instanceof java.util.concurrent.ThreadPoolExecutor) {
                return ((java.util.concurrent.ThreadPoolExecutor)executor).getMaximumPoolSize();
            } else if (executor instanceof ResizableExecutor) {
                return ((ResizableExecutor)executor).getMaxThreads();
            } else {
                return -1;
            }
        } else {
            return maxThreads;
        }
    }

    /**
     * Max keep alive requests
     */
    private int maxKeepAliveRequests=100; // as in Apache HTTPD server
    public int getMaxKeepAliveRequests() {
        return maxKeepAliveRequests;
    }
    public void setMaxKeepAliveRequests(int maxKeepAliveRequests) {
        this.maxKeepAliveRequests = maxKeepAliveRequests;
    }

    /**
     * The maximum number of headers in a request that are allowed.
     * 100 by default. A value of less than 0 means no limit.
     */
    private int maxHeaderCount = 100; // as in Apache HTTPD server
    public int getMaxHeaderCount() {
        return maxHeaderCount;
    }
    public void setMaxHeaderCount(int maxHeaderCount) {
        this.maxHeaderCount = maxHeaderCount;
    }

    /**
     * Name of the thread pool, which will be used for naming child threads.
     */
    private String name = "TP";
    public void setName(String name) { this.name = name; }
    public String getName() { return name; }

    /**
     * The default is true - the created threads will be
     *  in daemon mode. If set to false, the control thread
     *  will not be daemon - and will keep the process alive.
     */
    private boolean daemon = true;
    public void setDaemon(boolean b) { daemon = b; }
    public boolean getDaemon() { return daemon; }


    /**
     * Priority of the worker threads.
     */
    protected int threadPriority = Thread.NORM_PRIORITY;
    public void setThreadPriority(int threadPriority) { this.threadPriority = threadPriority; }
    public int getThreadPriority() { return threadPriority; }

    protected abstract boolean getDeferAccept();


    protected final List<String> negotiableProtocols = new ArrayList<>();
    public void addNegotiatedProtocol(String negotiableProtocol) {
        negotiableProtocols.add(negotiableProtocol);
    }
    public boolean hasNegotiableProtocols() {
        return (negotiableProtocols.size() > 0);
    }

    /**
     * Attributes provide a way for configuration to be passed to sub-components
     * without the {@link org.apache.coyote.ProtocolHandler} being aware of the
     * properties available on those sub-components.
     */
    protected HashMap<String, Object> attributes = new HashMap<>();

    /**
     * Generic property setter called when a property for which a specific
     * setter already exists within the
     * {@link org.apache.coyote.ProtocolHandler} needs to be made available to
     * sub-components. The specific setter will call this method to populate the
     * attributes.
     *
     * @param name  Name of property to set
     * @param value The value to set the property to
     */
    public void setAttribute(String name, Object value) {
        if (getLog().isTraceEnabled()) {
            getLog().trace(sm.getString("endpoint.setAttribute", name, value));
        }
        attributes.put(name, value);
    }
    /**
     * Used by sub-components to retrieve configuration information.
     *
     * @param key The name of the property for which the value should be
     *            retrieved
     *
     * @return The value of the specified property
     */
    public Object getAttribute(String key) {
        Object value = attributes.get(key);
        if (getLog().isTraceEnabled()) {
            getLog().trace(sm.getString("endpoint.getAttribute", key, value));
        }
        return value;
    }



    public boolean setProperty(String name, String value) {
        setAttribute(name, value);
        final String socketName = "socket.";
        try {
            if (name.startsWith(socketName)) {
                return IntrospectionUtils.setProperty(socketProperties, name.substring(socketName.length()), value);
            } else {
                return IntrospectionUtils.setProperty(this,name,value,false);
            }
        }catch ( Exception x ) {
            getLog().error("Unable to set attribute \""+name+"\" to \""+value+"\"",x);
            return false;
        }
    }
    public String getProperty(String name) {
        return (String) getAttribute(name);
    }

    /**
     * Return the amount of threads that are managed by the pool.
     *
     * @return the amount of threads that are managed by the pool
     */
    public int getCurrentThreadCount() {
        if (executor!=null) {
            if (executor instanceof ThreadPoolExecutor) {
                return ((ThreadPoolExecutor)executor).getPoolSize();
            } else if (executor instanceof ResizableExecutor) {
                return ((ResizableExecutor)executor).getPoolSize();
            } else {
                return -1;
            }
        } else {
            return -2;
        }
    }

    /**
     * Return the amount of threads that are in use
     *
     * @return the amount of threads that are in use
     */
    public int getCurrentThreadsBusy() {
        if (executor!=null) {
            if (executor instanceof ThreadPoolExecutor) {
                return ((ThreadPoolExecutor)executor).getActiveCount();
            } else if (executor instanceof ResizableExecutor) {
                return ((ResizableExecutor)executor).getActiveCount();
            } else {
                return -1;
            }
        } else {
            return -2;
        }
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isPaused() {
        return paused;
    }


    public void createExecutor() {
        internalExecutor = true;
        TaskQueue taskqueue = new TaskQueue();
        TaskThreadFactory tf = new TaskThreadFactory(getName() + "-exec-", daemon, getThreadPriority());
        executor = new ThreadPoolExecutor(getMinSpareThreads(), getMaxThreads(), 60, TimeUnit.SECONDS,taskqueue, tf);
        taskqueue.setParent( (ThreadPoolExecutor) executor);
    }

    public void shutdownExecutor() {
        if ( executor!=null && internalExecutor ) {
            if ( executor instanceof ThreadPoolExecutor ) {
                //this is our internal one, so we need to shut it down
                ThreadPoolExecutor tpe = (ThreadPoolExecutor) executor;
                tpe.shutdownNow();
                long timeout = getExecutorTerminationTimeoutMillis();
                if (timeout > 0) {
                    try {
                        tpe.awaitTermination(timeout, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                    if (tpe.isTerminating()) {
                        getLog().warn(sm.getString("endpoint.warn.executorShutdown", getName()));
                    }
                }
                TaskQueue queue = (TaskQueue) tpe.getQueue();
                queue.setParent(null);
            }
            executor = null;
        }
    }

    /**
     * Unlock the server socket accept using a bogus connection.
     */
    protected void unlockAccept() {
        // Only try to unlock the acceptor if it is necessary
        boolean unlockRequired = false;
        for (Acceptor acceptor : acceptors) {
            if (acceptor.getState() == AcceptorState.RUNNING) {
                unlockRequired = true;
                break;
            }
        }
        if (!unlockRequired) {
            return;
        }

        InetSocketAddress saddr = null;
        try {
            // Need to create a connection to unlock the accept();
            if (address == null) {
                saddr = new InetSocketAddress("localhost", getLocalPort());
            } else {
                saddr = new InetSocketAddress(address, getLocalPort());
            }
            try (java.net.Socket s = new java.net.Socket()) {
                int stmo = 2 * 1000;
                int utmo = 2 * 1000;
                if (getSocketProperties().getSoTimeout() > stmo)
                    stmo = getSocketProperties().getSoTimeout();
                if (getSocketProperties().getUnlockTimeout() > utmo)
                    utmo = getSocketProperties().getUnlockTimeout();
                s.setSoTimeout(stmo);
                s.setSoLinger(getSocketProperties().getSoLingerOn(),getSocketProperties().getSoLingerTime());
                if (getLog().isDebugEnabled()) {
                    getLog().debug("About to unlock socket for:"+saddr);
                }
                s.connect(saddr,utmo);
                if (getDeferAccept()) {
                    /*
                     * In the case of a deferred accept / accept filters we need to
                     * send data to wake up the accept. Send OPTIONS * to bypass
                     * even BSD accept filters. The Acceptor will discard it.
                     */
                    OutputStreamWriter sw;

                    sw = new OutputStreamWriter(s.getOutputStream(), "ISO-8859-1");
                    sw.write("OPTIONS * HTTP/1.0\r\n" +
                             "User-Agent: Tomcat wakeup connection\r\n\r\n");
                    sw.flush();
                }
                if (getLog().isDebugEnabled()) {
                    getLog().debug("Socket unlock completed for:"+saddr);
                }

                // Wait for upto 1000ms acceptor threads to unlock
                long waitLeft = 1000;
                for (Acceptor acceptor : acceptors) {
                    while (waitLeft > 0 &&
                            acceptor.getState() == AcceptorState.RUNNING) {
                        Thread.sleep(50);
                        waitLeft -= 50;
                    }
                }
            }
        } catch(Exception e) {
            if (getLog().isDebugEnabled()) {
                getLog().debug(sm.getString("endpoint.debug.unlock", "" + getPort()), e);
            }
        }
    }


    // ---------------------------------------------- Request processing methods

    /**
     * Process the given SocketWrapper with the given status. Used to trigger
     * processing as if the Poller (for those endpoints that have one)
     * selected the socket.
     *
     * @param socketWrapper The socket wrapper to process
     * @param socketStatus  The input status to the processing
     * @param dispatch      Should the processing be performed on a new
     *                          container thread
     */
    public abstract void processSocket(SocketWrapperBase<S> socketWrapper,
            SocketStatus socketStatus, boolean dispatch);


    public void executeNonBlockingDispatches(SocketWrapperBase<S> socketWrapper) {
        /*
         * This method is called when non-blocking IO is initiated by defining
         * a read and/or write listener in a non-container thread. It is called
         * once the non-container thread completes so that the first calls to
         * onWritePossible() and/or onDataAvailable() as appropriate are made by
         * the container.
         *
         * Processing the dispatches requires (for APR/native at least)
         * that the socket has been added to the waitingRequests queue. This may
         * not have occurred by the time that the non-container thread completes
         * triggering the call to this method. Therefore, the coded syncs on the
         * SocketWrapper as the container thread that initiated this
         * non-container thread holds a lock on the SocketWrapper. The container
         * thread will add the socket to the waitingRequests queue before
         * releasing the lock on the socketWrapper. Therefore, by obtaining the
         * lock on socketWrapper before processing the dispatches, we can be
         * sure that the socket has been added to the waitingRequests queue.
         */
        synchronized (socketWrapper) {
            Iterator<DispatchType> dispatches = socketWrapper.getIteratorAndClearDispatches();

            while (dispatches != null && dispatches.hasNext()) {
                DispatchType dispatchType = dispatches.next();
                processSocket(socketWrapper, dispatchType.getSocketStatus(), false);
            }
        }
    }

    // ------------------------------------------------------- Lifecycle methods

    /*
     * NOTE: There is no maintenance of state or checking for valid transitions
     * within this class other than ensuring that bind/unbind are called in the
     * right place. It is expected that the calling code will maintain state and
     * prevent invalid state transitions.
     */

    public abstract void bind() throws Exception;
    public abstract void unbind() throws Exception;
    public abstract void startInternal() throws Exception;
    public abstract void stopInternal() throws Exception;

    public final void init() throws Exception {
        if (bindOnInit) {
            bind();
            bindState = BindState.BOUND_ON_INIT;
        }
    }

    public final void start() throws Exception {
        if (bindState == BindState.UNBOUND) {
            bind();
            bindState = BindState.BOUND_ON_START;
        }
        startInternal();
    }

    protected final void startAcceptorThreads() {
        int count = getAcceptorThreadCount();
        acceptors = new Acceptor[count];

        for (int i = 0; i < count; i++) {
            acceptors[i] = createAcceptor();
            String threadName = getName() + "-Acceptor-" + i;
            acceptors[i].setThreadName(threadName);
            Thread t = new Thread(acceptors[i], threadName);
            t.setPriority(getAcceptorThreadPriority());
            t.setDaemon(getDaemon());
            t.start();
        }
    }


    /**
     * Hook to allow Endpoints to provide a specific Acceptor implementation.
     */
    protected abstract Acceptor createAcceptor();


    /**
     * Pause the endpoint, which will stop it accepting new connections.
     */
    public void pause() {
        if (running && !paused) {
            paused = true;
            unlockAccept();
            getHandler().pause();
        }
    }

    /**
     * Resume the endpoint, which will make it start accepting new connections
     * again.
     */
    public void resume() {
        if (running) {
            paused = false;
        }
    }

    public final void stop() throws Exception {
        stopInternal();
        if (bindState == BindState.BOUND_ON_START) {
            unbind();
            bindState = BindState.UNBOUND;
        }
    }

    public final void destroy() throws Exception {
        if (bindState == BindState.BOUND_ON_INIT) {
            unbind();
            bindState = BindState.UNBOUND;
        }
    }


    protected abstract Handler<S> getHandler();

    protected abstract Log getLog();

    protected LimitLatch initializeConnectionLatch() {
        if (maxConnections==-1) return null;
        if (connectionLimitLatch==null) {
            connectionLimitLatch = new LimitLatch(getMaxConnections());
        }
        return connectionLimitLatch;
    }

    protected void releaseConnectionLatch() {
        LimitLatch latch = connectionLimitLatch;
        if (latch!=null) latch.releaseAll();
        connectionLimitLatch = null;
    }

    protected void countUpOrAwaitConnection() throws InterruptedException {
        if (maxConnections==-1) return;
        LimitLatch latch = connectionLimitLatch;
        if (latch!=null) latch.countUpOrAwait();
    }

    protected long countDownConnection() {
        if (maxConnections==-1) return -1;
        LimitLatch latch = connectionLimitLatch;
        if (latch!=null) {
            long result = latch.countDown();
            if (result<0) {
                getLog().warn("Incorrect connection count, multiple socket.close called on the same socket." );
            }
            return result;
        } else return -1;
    }

    /**
     * Provides a common approach for sub-classes to handle exceptions where a
     * delay is required to prevent a Thread from entering a tight loop which
     * will consume CPU and may also trigger large amounts of logging. For
     * example, this can happen with the Acceptor thread if the ulimit for open
     * files is reached.
     *
     * @param currentErrorDelay The current delay being applied on failure
     * @return  The delay to apply on the next failure
     */
    protected int handleExceptionWithDelay(int currentErrorDelay) {
        // Don't delay on first exception
        if (currentErrorDelay > 0) {
            try {
                Thread.sleep(currentErrorDelay);
            } catch (InterruptedException e) {
                // Ignore
            }
        }

        // On subsequent exceptions, start the delay at 50ms, doubling the delay
        // on every subsequent exception until the delay reaches 1.6 seconds.
        if (currentErrorDelay == 0) {
            return INITIAL_ERROR_DELAY;
        } else if (currentErrorDelay < MAX_ERROR_DELAY) {
            return currentErrorDelay * 2;
        } else {
            return MAX_ERROR_DELAY;
        }

    }


    protected final Set<SocketWrapperBase<S>> waitingRequests = Collections
            .newSetFromMap(new ConcurrentHashMap<SocketWrapperBase<S>, Boolean>());

    /**
     * The async timeout thread.
     */
    private AsyncTimeout asyncTimeout = null;
    public AsyncTimeout getAsyncTimeout() {
        return asyncTimeout;
    }
    public void setAsyncTimeout(AsyncTimeout asyncTimeout) {
        this.asyncTimeout = asyncTimeout;
    }
}


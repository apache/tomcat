/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.coyote;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistration;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.WebConnection;

import org.apache.coyote.http11.upgrade.InternalHttpUpgradeHandler;
import org.apache.juli.logging.Log;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.collections.SynchronizedStack;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

public abstract class AbstractProtocol<S> implements ProtocolHandler,
        MBeanRegistration {

    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager(AbstractProtocol.class);


    /**
     * Counter used to generate unique JMX names for connectors using automatic
     * port binding.
     */
    private static final AtomicInteger nameCounter = new AtomicInteger(0);


    /**
     * Unique ID for this connector. Only used if the connector is configured
     * to use a random port as the port will change if stop(), start() is
     * called.
     */
    private int nameIndex = 0;


    /**
     * Endpoint that provides low-level network I/O - must be matched to the
     * ProtocolHandler implementation (ProtocolHandler using NIO, requires NIO
     * Endpoint etc.).
     */
    private final AbstractEndpoint<S,?> endpoint;


    private Handler<S> handler;


    private final Set<Processor> waitingProcessors = ConcurrentHashMap.newKeySet();

    /**
     * Controller for the timeout scheduling.
     */
    private ScheduledFuture<?> timeoutFuture = null;
    private ScheduledFuture<?> monitorFuture;

    public AbstractProtocol(AbstractEndpoint<S,?> endpoint) {
        this.endpoint = endpoint;
        ConnectionHandler<S> cHandler = new ConnectionHandler<>(this);
        getEndpoint().setHandler(cHandler);
        setHandler(cHandler);
        setConnectionLinger(Constants.DEFAULT_CONNECTION_LINGER);
        setTcpNoDelay(Constants.DEFAULT_TCP_NO_DELAY);
    }


    // ----------------------------------------------- Generic property handling

    /**
     * Generic property setter used by the digester. Other code should not need
     * to use this. The digester will only use this method if it can't find a
     * more specific setter. That means the property belongs to the Endpoint,
     * the ServerSocketFactory or some other lower level component. This method
     * ensures that it is visible to both.
     *
     * @param name  The name of the property to set
     * @param value The value, in string form, to set for the property
     *
     * @return <code>true</code> if the property was set successfully, otherwise
     *         <code>false</code>
     */
    public boolean setProperty(String name, String value) {
        return endpoint.setProperty(name, value);
    }


    /**
     * Generic property getter used by the digester. Other code should not need
     * to use this.
     *
     * @param name The name of the property to get
     *
     * @return The value of the property converted to a string
     */
    public String getProperty(String name) {
        return endpoint.getProperty(name);
    }


    // ------------------------------- Properties managed by the ProtocolHandler

    /**
     * Name of MBean for the Global Request Processor.
     */
    protected ObjectName rgOname = null;
    public ObjectName getGlobalRequestProcessorMBeanName() {
        return rgOname;
    }

    /**
     * The adapter provides the link between the ProtocolHandler and the
     * connector.
     */
    protected Adapter adapter;
    @Override
    public void setAdapter(Adapter adapter) { this.adapter = adapter; }
    @Override
    public Adapter getAdapter() { return adapter; }


    /**
     * The maximum number of idle processors that will be retained in the cache
     * and re-used with a subsequent request. The default is 200. A value of -1
     * means unlimited. In the unlimited case, the theoretical maximum number of
     * cached Processor objects is {@link #getMaxConnections()} although it will
     * usually be closer to {@link #getMaxThreads()}.
     */
    protected int processorCache = 200;
    public int getProcessorCache() { return this.processorCache; }
    public void setProcessorCache(int processorCache) {
        this.processorCache = processorCache;
    }


    private String clientCertProvider = null;
    /**
     * When client certificate information is presented in a form other than
     * instances of {@link java.security.cert.X509Certificate} it needs to be
     * converted before it can be used and this property controls which JSSE
     * provider is used to perform the conversion. For example it is used with
     * the AJP connectors and with the
     * {@link org.apache.catalina.valves.SSLValve}. If not specified, the
     * default provider will be used.
     *
     * @return The name of the JSSE provider to use
     */
    public String getClientCertProvider() { return clientCertProvider; }
    public void setClientCertProvider(String s) { this.clientCertProvider = s; }


    private int maxHeaderCount = 100;
    public int getMaxHeaderCount() {
        return maxHeaderCount;
    }
    public void setMaxHeaderCount(int maxHeaderCount) {
        this.maxHeaderCount = maxHeaderCount;
    }


    @Override
    public boolean isSendfileSupported() {
        return endpoint.getUseSendfile();
    }


    @Override
    public String getId() {
        return endpoint.getId();
    }


    // ---------------------- Properties that are passed through to the EndPoint

    @Override
    public Executor getExecutor() { return endpoint.getExecutor(); }
    @Override
    public void setExecutor(Executor executor) {
        endpoint.setExecutor(executor);
    }


    @Override
    public ScheduledExecutorService getUtilityExecutor() { return endpoint.getUtilityExecutor(); }
    @Override
    public void setUtilityExecutor(ScheduledExecutorService utilityExecutor) {
        endpoint.setUtilityExecutor(utilityExecutor);
    }


    public int getMaxThreads() { return endpoint.getMaxThreads(); }
    public void setMaxThreads(int maxThreads) {
        endpoint.setMaxThreads(maxThreads);
    }

    public int getMaxConnections() { return endpoint.getMaxConnections(); }
    public void setMaxConnections(int maxConnections) {
        endpoint.setMaxConnections(maxConnections);
    }


    public int getMinSpareThreads() { return endpoint.getMinSpareThreads(); }
    public void setMinSpareThreads(int minSpareThreads) {
        endpoint.setMinSpareThreads(minSpareThreads);
    }


    public int getThreadPriority() { return endpoint.getThreadPriority(); }
    public void setThreadPriority(int threadPriority) {
        endpoint.setThreadPriority(threadPriority);
    }


    public int getAcceptCount() { return endpoint.getAcceptCount(); }
    public void setAcceptCount(int acceptCount) { endpoint.setAcceptCount(acceptCount); }


    public boolean getTcpNoDelay() { return endpoint.getTcpNoDelay(); }
    public void setTcpNoDelay(boolean tcpNoDelay) {
        endpoint.setTcpNoDelay(tcpNoDelay);
    }


    public int getConnectionLinger() { return endpoint.getConnectionLinger(); }
    public void setConnectionLinger(int connectionLinger) {
        endpoint.setConnectionLinger(connectionLinger);
    }


    /**
     * The time Tomcat will wait for a subsequent request before closing the
     * connection. The default is {@link #getConnectionTimeout()}.
     *
     * @return The timeout in milliseconds
     */
    public int getKeepAliveTimeout() { return endpoint.getKeepAliveTimeout(); }
    public void setKeepAliveTimeout(int keepAliveTimeout) {
        endpoint.setKeepAliveTimeout(keepAliveTimeout);
    }

    public InetAddress getAddress() { return endpoint.getAddress(); }
    public void setAddress(InetAddress ia) {
        endpoint.setAddress(ia);
    }


    public int getPort() { return endpoint.getPort(); }
    public void setPort(int port) {
        endpoint.setPort(port);
    }


    public int getPortOffset() { return endpoint.getPortOffset(); }
    public void setPortOffset(int portOffset) {
        endpoint.setPortOffset(portOffset);
    }


    public int getPortWithOffset() { return endpoint.getPortWithOffset(); }


    public int getLocalPort() { return endpoint.getLocalPort(); }

    /*
     * When Tomcat expects data from the client, this is the time Tomcat will
     * wait for that data to arrive before closing the connection.
     */
    public int getConnectionTimeout() {
        return endpoint.getConnectionTimeout();
    }
    public void setConnectionTimeout(int timeout) {
        endpoint.setConnectionTimeout(timeout);
    }

    public long getConnectionCount() {
        return endpoint.getConnectionCount();
    }

    public void setAcceptorThreadPriority(int threadPriority) {
        endpoint.setAcceptorThreadPriority(threadPriority);
    }
    public int getAcceptorThreadPriority() {
      return endpoint.getAcceptorThreadPriority();
    }


    // ---------------------------------------------------------- Public methods

    public synchronized int getNameIndex() {
        if (nameIndex == 0) {
            nameIndex = nameCounter.incrementAndGet();
        }

        return nameIndex;
    }


    /**
     * The name will be prefix-address-port if address is non-null and
     * prefix-port if the address is null.
     *
     * @return A name for this protocol instance that is appropriately quoted
     *         for use in an ObjectName.
     */
    public String getName() {
        return ObjectName.quote(getNameInternal());
    }


    private String getNameInternal() {
        StringBuilder name = new StringBuilder(getNamePrefix());
        name.append('-');
        String id = getId();
        if (id != null) {
            name.append(id);
        } else {
            if (getAddress() != null) {
                name.append(getAddress().getHostAddress());
                name.append('-');
            }
            int port = getPortWithOffset();
            if (port == 0) {
                // Auto binding is in use. Check if port is known
                name.append("auto-");
                name.append(getNameIndex());
                port = getLocalPort();
                if (port != -1) {
                    name.append('-');
                    name.append(port);
                }
            } else {
                name.append(port);
            }
        }
        return name.toString();
    }


    public void addWaitingProcessor(Processor processor) {
        if (getLog().isDebugEnabled()) {
            getLog().debug(sm.getString("abstractProtocol.waitingProcessor.add", processor));
        }
        waitingProcessors.add(processor);
    }


    public void removeWaitingProcessor(Processor processor) {
        if (getLog().isDebugEnabled()) {
            getLog().debug(sm.getString("abstractProtocol.waitingProcessor.remove", processor));
        }
        waitingProcessors.remove(processor);
    }


    /*
     * Primarily for debugging and testing. Could be exposed via JMX if
     * considered useful.
     */
    public int getWaitingProcessorCount() {
        return waitingProcessors.size();
    }


    // ----------------------------------------------- Accessors for sub-classes

    protected AbstractEndpoint<S,?> getEndpoint() {
        return endpoint;
    }


    protected Handler<S> getHandler() {
        return handler;
    }

    protected void setHandler(Handler<S> handler) {
        this.handler = handler;
    }


    // -------------------------------------------------------- Abstract methods

    /**
     * Concrete implementations need to provide access to their logger to be
     * used by the abstract classes.
     * @return the logger
     */
    protected abstract Log getLog();


    /**
     * Obtain the prefix to be used when construction a name for this protocol
     * handler. The name will be prefix-address-port.
     * @return the prefix
     */
    protected abstract String getNamePrefix();


    /**
     * Obtain the name of the protocol, (Http, Ajp, etc.). Used with JMX.
     * @return the protocol name
     */
    protected abstract String getProtocolName();


    /**
     * Find a suitable handler for the protocol negotiated
     * at the network layer.
     * @param name The name of the requested negotiated protocol.
     * @return The instance where {@link UpgradeProtocol#getAlpnName()} matches
     *         the requested protocol
     */
    protected abstract UpgradeProtocol getNegotiatedProtocol(String name);


    /**
     * Find a suitable handler for the protocol upgraded name specified. This
     * is used for direct connection protocol selection.
     * @param name The name of the requested negotiated protocol.
     * @return The instance where {@link UpgradeProtocol#getAlpnName()} matches
     *         the requested protocol
     */
    protected abstract UpgradeProtocol getUpgradeProtocol(String name);


    /**
     * Create and configure a new Processor instance for the current protocol
     * implementation.
     *
     * @return A fully configured Processor instance that is ready to use
     */
    protected abstract Processor createProcessor();


    protected abstract Processor createUpgradeProcessor(
            SocketWrapperBase<?> socket,
            UpgradeToken upgradeToken);


    // ----------------------------------------------------- JMX related methods

    protected String domain;
    protected ObjectName oname;
    protected MBeanServer mserver;

    public ObjectName getObjectName() {
        return oname;
    }

    public String getDomain() {
        return domain;
    }

    @Override
    public ObjectName preRegister(MBeanServer server, ObjectName name)
            throws Exception {
        oname = name;
        mserver = server;
        domain = name.getDomain();
        return name;
    }

    @Override
    public void postRegister(Boolean registrationDone) {
        // NOOP
    }

    @Override
    public void preDeregister() throws Exception {
        // NOOP
    }

    @Override
    public void postDeregister() {
        // NOOP
    }

    private ObjectName createObjectName() throws MalformedObjectNameException {
        // Use the same domain as the connector
        domain = getAdapter().getDomain();

        if (domain == null) {
            return null;
        }

        StringBuilder name = new StringBuilder(getDomain());
        name.append(":type=ProtocolHandler,port=");
        int port = getPortWithOffset();
        if (port > 0) {
            name.append(port);
        } else {
            name.append("auto-");
            name.append(getNameIndex());
        }
        InetAddress address = getAddress();
        if (address != null) {
            name.append(",address=");
            name.append(ObjectName.quote(address.getHostAddress()));
        }
        return new ObjectName(name.toString());
    }


    // ------------------------------------------------------- Lifecycle methods

    /*
     * NOTE: There is no maintenance of state or checking for valid transitions
     * within this class. It is expected that the connector will maintain state
     * and prevent invalid state transitions.
     */

    @Override
    public void init() throws Exception {
        if (getLog().isInfoEnabled()) {
            getLog().info(sm.getString("abstractProtocolHandler.init", getName()));
            logPortOffset();
        }

        if (oname == null) {
            // Component not pre-registered so register it
            oname = createObjectName();
            if (oname != null) {
                Registry.getRegistry(null, null).registerComponent(this, oname, null);
            }
        }

        if (this.domain != null) {
            ObjectName rgOname = new ObjectName(domain + ":type=GlobalRequestProcessor,name=" + getName());
            this.rgOname = rgOname;
            Registry.getRegistry(null, null).registerComponent(
                    getHandler().getGlobal(), rgOname, null);
        }

        String endpointName = getName();
        endpoint.setName(endpointName.substring(1, endpointName.length()-1));
        endpoint.setDomain(domain);

        endpoint.init();
    }


    @Override
    public void start() throws Exception {
        if (getLog().isInfoEnabled()) {
            getLog().info(sm.getString("abstractProtocolHandler.start", getName()));
            logPortOffset();
        }

        endpoint.start();
        monitorFuture = getUtilityExecutor().scheduleWithFixedDelay(
                () -> {
                    startAsyncTimeout();
                }, 0, 60, TimeUnit.SECONDS);
    }


    /**
     * Note: The name of this method originated with the Servlet 3.0
     * asynchronous processing but evolved over time to represent a timeout that
     * is triggered independently of the socket read/write timeouts.
     */
    protected void startAsyncTimeout() {
        if (timeoutFuture == null || timeoutFuture.isDone()) {
            if (timeoutFuture != null && timeoutFuture.isDone()) {
                // There was an error executing the scheduled task, get it and log it
                try {
                    timeoutFuture.get();
                } catch (InterruptedException | ExecutionException e) {
                    getLog().error(sm.getString("abstractProtocolHandler.asyncTimeoutError"), e);
                }
            }
            timeoutFuture = getUtilityExecutor().scheduleAtFixedRate(
                    () -> {
                        long now = System.currentTimeMillis();
                        for (Processor processor : waitingProcessors) {
                            processor.timeoutAsync(now);
                        }
                    }, 1, 1, TimeUnit.SECONDS);
        }
    }

    protected void stopAsyncTimeout() {
        if (timeoutFuture != null) {
            timeoutFuture.cancel(false);
            timeoutFuture = null;
        }
    }

    @Override
    public void pause() throws Exception {
        if (getLog().isInfoEnabled()) {
            getLog().info(sm.getString("abstractProtocolHandler.pause", getName()));
        }

        endpoint.pause();
    }


    public boolean isPaused() {
        return endpoint.isPaused();
    }


    @Override
    public void resume() throws Exception {
        if(getLog().isInfoEnabled()) {
            getLog().info(sm.getString("abstractProtocolHandler.resume", getName()));
        }

        endpoint.resume();
    }


    @Override
    public void stop() throws Exception {
        if(getLog().isInfoEnabled()) {
            getLog().info(sm.getString("abstractProtocolHandler.stop", getName()));
            logPortOffset();
        }

        if (monitorFuture != null) {
            monitorFuture.cancel(true);
            monitorFuture = null;
        }
        stopAsyncTimeout();
        // Timeout any waiting processor
        for (Processor processor : waitingProcessors) {
            processor.timeoutAsync(-1);
        }

        endpoint.stop();
    }


    @Override
    public void destroy() throws Exception {
        if(getLog().isInfoEnabled()) {
            getLog().info(sm.getString("abstractProtocolHandler.destroy", getName()));
            logPortOffset();
        }

        try {
            endpoint.destroy();
        } finally {
            if (oname != null) {
                if (mserver == null) {
                    Registry.getRegistry(null, null).unregisterComponent(oname);
                } else {
                    // Possibly registered with a different MBeanServer
                    try {
                        mserver.unregisterMBean(oname);
                    } catch (MBeanRegistrationException | InstanceNotFoundException e) {
                        getLog().info(sm.getString("abstractProtocol.mbeanDeregistrationFailed",
                                oname, mserver));
                    }
                }
            }

            ObjectName rgOname = getGlobalRequestProcessorMBeanName();
            if (rgOname != null) {
                Registry.getRegistry(null, null).unregisterComponent(rgOname);
            }
        }
    }


    @Override
    public void closeServerSocketGraceful() {
        endpoint.closeServerSocketGraceful();
    }


    @Override
    public long awaitConnectionsClose(long waitMillis) {
        getLog().info(sm.getString("abstractProtocol.closeConnectionsAwait",
                Long.valueOf(waitMillis), getName()));
        return endpoint.awaitConnectionsClose(waitMillis);
    }


    private void logPortOffset() {
        if (getPort() != getPortWithOffset()) {
            getLog().info(sm.getString("abstractProtocolHandler.portOffset", getName(),
                    String.valueOf(getPort()), String.valueOf(getPortOffset())));
        }
    }


    // ------------------------------------------- Connection handler base class

    protected static class ConnectionHandler<S> implements AbstractEndpoint.Handler<S> {

        private final AbstractProtocol<S> proto;
        private final RequestGroupInfo global = new RequestGroupInfo();
        private final AtomicLong registerCount = new AtomicLong(0);
        private final RecycledProcessors recycledProcessors = new RecycledProcessors(this);

        public ConnectionHandler(AbstractProtocol<S> proto) {
            this.proto = proto;
        }

        protected AbstractProtocol<S> getProtocol() {
            return proto;
        }

        protected Log getLog() {
            return getProtocol().getLog();
        }

        @Override
        public Object getGlobal() {
            return global;
        }

        @Override
        public void recycle() {
            recycledProcessors.clear();
        }


        @Override
        public SocketState process(SocketWrapperBase<S> wrapper, SocketEvent status) {
            if (getLog().isDebugEnabled()) {
                getLog().debug(sm.getString("abstractConnectionHandler.process",
                        wrapper.getSocket(), status));
            }
            if (wrapper == null) {
                // Nothing to do. Socket has been closed.
                return SocketState.CLOSED;
            }

            S socket = wrapper.getSocket();

            // We take complete ownership of the Processor inside of this method to ensure
            // no other thread can release it while we're using it. Whatever processor is
            // held by this variable will be associated with the SocketWrapper before this
            // method returns.
            Processor processor = (Processor) wrapper.takeCurrentProcessor();
            if (getLog().isDebugEnabled()) {
                getLog().debug(sm.getString("abstractConnectionHandler.connectionsGet",
                        processor, socket));
            }

            // Timeouts are calculated on a dedicated thread and then
            // dispatched. Because of delays in the dispatch process, the
            // timeout may no longer be required. Check here and avoid
            // unnecessary processing.
            if (SocketEvent.TIMEOUT == status &&
                    (processor == null ||
                    !processor.isAsync() && !processor.isUpgrade() ||
                    processor.isAsync() && !processor.checkAsyncTimeoutGeneration())) {
                // This is effectively a NO-OP
                return SocketState.OPEN;
            }

            if (processor != null) {
                // Make sure an async timeout doesn't fire
                getProtocol().removeWaitingProcessor(processor);
            } else if (status == SocketEvent.DISCONNECT || status == SocketEvent.ERROR) {
                // Nothing to do. Endpoint requested a close and there is no
                // longer a processor associated with this socket.
                return SocketState.CLOSED;
            }

            try {
                if (processor == null) {
                    String negotiatedProtocol = wrapper.getNegotiatedProtocol();
                    // OpenSSL typically returns null whereas JSSE typically
                    // returns "" when no protocol is negotiated
                    if (negotiatedProtocol != null && negotiatedProtocol.length() > 0) {
                        UpgradeProtocol upgradeProtocol = getProtocol().getNegotiatedProtocol(negotiatedProtocol);
                        if (upgradeProtocol != null) {
                            processor = upgradeProtocol.getProcessor(wrapper, getProtocol().getAdapter());
                            if (getLog().isDebugEnabled()) {
                                getLog().debug(sm.getString("abstractConnectionHandler.processorCreate", processor));
                            }
                        } else if (negotiatedProtocol.equals("http/1.1")) {
                            // Explicitly negotiated the default protocol.
                            // Obtain a processor below.
                        } else {
                            // TODO:
                            // OpenSSL 1.0.2's ALPN callback doesn't support
                            // failing the handshake with an error if no
                            // protocol can be negotiated. Therefore, we need to
                            // fail the connection here. Once this is fixed,
                            // replace the code below with the commented out
                            // block.
                            if (getLog().isDebugEnabled()) {
                                getLog().debug(sm.getString("abstractConnectionHandler.negotiatedProcessor.fail",
                                        negotiatedProtocol));
                            }
                            return SocketState.CLOSED;
                            /*
                             * To replace the code above once OpenSSL 1.1.0 is
                             * used.
                            // Failed to create processor. This is a bug.
                            throw new IllegalStateException(sm.getString(
                                    "abstractConnectionHandler.negotiatedProcessor.fail",
                                    negotiatedProtocol));
                            */
                        }
                    }
                }
                if (processor == null) {
                    processor = recycledProcessors.pop();
                    if (getLog().isDebugEnabled()) {
                        getLog().debug(sm.getString("abstractConnectionHandler.processorPop", processor));
                    }
                }
                if (processor == null) {
                    processor = getProtocol().createProcessor();
                    register(processor);
                    if (getLog().isDebugEnabled()) {
                        getLog().debug(sm.getString("abstractConnectionHandler.processorCreate", processor));
                    }
                }

                processor.setSslSupport(wrapper.getSslSupport());

                SocketState state = SocketState.CLOSED;
                do {
                    state = processor.process(wrapper, status);

                    if (state == SocketState.UPGRADING) {
                        // Get the HTTP upgrade handler
                        UpgradeToken upgradeToken = processor.getUpgradeToken();
                        // Restore leftover input to the wrapper so the upgrade
                        // processor can process it.
                        ByteBuffer leftOverInput = processor.getLeftoverInput();
                        wrapper.unRead(leftOverInput);
                        if (upgradeToken == null) {
                            // Assume direct HTTP/2 connection
                            UpgradeProtocol upgradeProtocol = getProtocol().getUpgradeProtocol("h2c");
                            if (upgradeProtocol != null) {
                                // Release the Http11 processor to be re-used
                                release(processor);
                                // Create the upgrade processor
                                processor = upgradeProtocol.getProcessor(wrapper, getProtocol().getAdapter());
                            } else {
                                if (getLog().isDebugEnabled()) {
                                    getLog().debug(sm.getString(
                                        "abstractConnectionHandler.negotiatedProcessor.fail",
                                        "h2c"));
                                }
                                // Exit loop and trigger appropriate clean-up
                                state = SocketState.CLOSED;
                            }
                        } else {
                            HttpUpgradeHandler httpUpgradeHandler = upgradeToken.getHttpUpgradeHandler();
                            // Release the Http11 processor to be re-used
                            release(processor);
                            // Create the upgrade processor
                            processor = getProtocol().createUpgradeProcessor(wrapper, upgradeToken);
                            if (getLog().isDebugEnabled()) {
                                getLog().debug(sm.getString("abstractConnectionHandler.upgradeCreate",
                                        processor, wrapper));
                            }
                            // Initialise the upgrade handler (which may trigger
                            // some IO using the new protocol which is why the lines
                            // above are necessary)
                            // This cast should be safe. If it fails the error
                            // handling for the surrounding try/catch will deal with
                            // it.
                            if (upgradeToken.getInstanceManager() == null) {
                                httpUpgradeHandler.init((WebConnection) processor);
                            } else {
                                ClassLoader oldCL = upgradeToken.getContextBind().bind(false, null);
                                try {
                                    httpUpgradeHandler.init((WebConnection) processor);
                                } finally {
                                    upgradeToken.getContextBind().unbind(false, oldCL);
                                }
                            }
                            if (httpUpgradeHandler instanceof InternalHttpUpgradeHandler) {
                                if (((InternalHttpUpgradeHandler) httpUpgradeHandler).hasAsyncIO()) {
                                    // The handler will initiate all further I/O
                                    state = SocketState.ASYNC_IO;
                                }
                            }
                        }
                    }
                } while ( state == SocketState.UPGRADING);

                if (state == SocketState.LONG) {
                    // In the middle of processing a request/response. Keep the
                    // socket associated with the processor. Exact requirements
                    // depend on type of long poll
                    longPoll(wrapper, processor);
                    if (processor.isAsync()) {
                        getProtocol().addWaitingProcessor(processor);
                    }
                } else if (state == SocketState.OPEN) {
                    // In keep-alive but between requests. OK to recycle
                    // processor. Continue to poll for the next request.
                    release(processor);
                    processor = null;
                    wrapper.registerReadInterest();
                } else if (state == SocketState.SENDFILE) {
                    // Sendfile in progress. If it fails, the socket will be
                    // closed. If it works, the socket either be added to the
                    // poller (or equivalent) to await more data or processed
                    // if there are any pipe-lined requests remaining.
                } else if (state == SocketState.UPGRADED) {
                    // Don't add sockets back to the poller if this was a
                    // non-blocking write otherwise the poller may trigger
                    // multiple read events which may lead to thread starvation
                    // in the connector. The write() method will add this socket
                    // to the poller if necessary.
                    if (status != SocketEvent.OPEN_WRITE) {
                        longPoll(wrapper, processor);
                        getProtocol().addWaitingProcessor(processor);
                    }
                } else if (state == SocketState.ASYNC_IO) {
                    // Don't add sockets back to the poller.
                    // The handler will initiate all further I/O
                } else if (state == SocketState.SUSPENDED) {
                    // Don't add sockets back to the poller.
                    // The resumeProcessing() method will add this socket
                    // to the poller.
                } else {
                    // Connection closed. OK to recycle the processor.
                    // Processors handling upgrades require additional clean-up
                    // before release.
                    if (processor != null && processor.isUpgrade()) {
                        UpgradeToken upgradeToken = processor.getUpgradeToken();
                        HttpUpgradeHandler httpUpgradeHandler = upgradeToken.getHttpUpgradeHandler();
                        InstanceManager instanceManager = upgradeToken.getInstanceManager();
                        if (instanceManager == null) {
                            httpUpgradeHandler.destroy();
                        } else {
                            ClassLoader oldCL = upgradeToken.getContextBind().bind(false, null);
                            try {
                                httpUpgradeHandler.destroy();
                            } finally {
                                try {
                                    instanceManager.destroyInstance(httpUpgradeHandler);
                                } catch (Throwable e) {
                                    ExceptionUtils.handleThrowable(e);
                                    getLog().error(sm.getString("abstractConnectionHandler.error"), e);
                                }
                                upgradeToken.getContextBind().unbind(false, oldCL);
                            }
                        }
                    }

                    release(processor);
                    processor = null;
                }

                if (processor != null) {
                    wrapper.setCurrentProcessor(processor);
                }
                return state;
            } catch(java.net.SocketException e) {
                // SocketExceptions are normal
                getLog().debug(sm.getString(
                        "abstractConnectionHandler.socketexception.debug"), e);
            } catch (java.io.IOException e) {
                // IOExceptions are normal
                getLog().debug(sm.getString(
                        "abstractConnectionHandler.ioexception.debug"), e);
            } catch (ProtocolException e) {
                // Protocol exceptions normally mean the client sent invalid or
                // incomplete data.
                getLog().debug(sm.getString(
                        "abstractConnectionHandler.protocolexception.debug"), e);
            }
            // Future developers: if you discover any other
            // rare-but-nonfatal exceptions, catch them here, and log as
            // above.
            catch (OutOfMemoryError oome) {
                // Try and handle this here to give Tomcat a chance to close the
                // connection and prevent clients waiting until they time out.
                // Worst case, it isn't recoverable and the attempt at logging
                // will trigger another OOME.
                getLog().error(sm.getString("abstractConnectionHandler.oome"), oome);
            } catch (Throwable e) {
                ExceptionUtils.handleThrowable(e);
                // any other exception or error is odd. Here we log it
                // with "ERROR" level, so it will show up even on
                // less-than-verbose logs.
                getLog().error(sm.getString("abstractConnectionHandler.error"), e);
            }

            // Make sure socket/processor is removed from the list of current
            // connections
            release(processor);
            return SocketState.CLOSED;
        }


        protected void longPoll(SocketWrapperBase<?> socket, Processor processor) {
            if (!processor.isAsync()) {
                // This is currently only used with HTTP
                // Either:
                //  - this is an upgraded connection
                //  - the request line/headers have not been completely
                //    read
                socket.registerReadInterest();
            }
        }


        /**
         * Expected to be used by the handler once the processor is no longer
         * required. Care must be taken to ensure that this method is only
         * called once per processor, after the request processing has
         * completed.
         *
         * @param processor Processor being released (that was associated with
         *                  the socket)
         */
        private void release(Processor processor) {
            if (processor != null) {
                processor.recycle();
                if (processor.isUpgrade()) {
                    // While UpgradeProcessor instances should not normally be
                    // present in waitingProcessors there are various scenarios
                    // where this can happen. E.g.:
                    // - when AsyncIO is used
                    // - WebSocket I/O error on non-container thread
                    // Err on the side of caution and always try and remove any
                    // UpgradeProcessor instances from waitingProcessors
                    getProtocol().removeWaitingProcessor(processor);
                } else {
                    // After recycling, only instances of UpgradeProcessorBase
                    // will return true for isUpgrade().
                    // Instances of UpgradeProcessorBase should not be added to
                    // recycledProcessors since that pool is only for AJP or
                    // HTTP processors
                    recycledProcessors.push(processor);
                    if (getLog().isDebugEnabled()) {
                        getLog().debug("Pushed Processor [" + processor + "]");
                    }
                }
            }
        }


        /**
         * Expected to be used by the Endpoint to release resources on socket
         * close, errors etc.
         */
        @Override
        public void release(SocketWrapperBase<S> socketWrapper) {
            Processor processor = (Processor) socketWrapper.takeCurrentProcessor();
            release(processor);
        }


        protected void register(Processor processor) {
            if (getProtocol().getDomain() != null) {
                synchronized (this) {
                    try {
                        long count = registerCount.incrementAndGet();
                        RequestInfo rp =
                            processor.getRequest().getRequestProcessor();
                        rp.setGlobalProcessor(global);
                        ObjectName rpName = new ObjectName(
                                getProtocol().getDomain() +
                                ":type=RequestProcessor,worker="
                                + getProtocol().getName() +
                                ",name=" + getProtocol().getProtocolName() +
                                "Request" + count);
                        if (getLog().isDebugEnabled()) {
                            getLog().debug("Register [" + processor + "] as [" + rpName + "]");
                        }
                        Registry.getRegistry(null, null).registerComponent(rp,
                                rpName, null);
                        rp.setRpName(rpName);
                    } catch (Exception e) {
                        getLog().warn(sm.getString("abstractProtocol.processorRegisterError"), e);
                    }
                }
            }
        }

        protected void unregister(Processor processor) {
            if (getProtocol().getDomain() != null) {
                synchronized (this) {
                    try {
                        Request r = processor.getRequest();
                        if (r == null) {
                            // Probably an UpgradeProcessor
                            return;
                        }
                        RequestInfo rp = r.getRequestProcessor();
                        rp.setGlobalProcessor(null);
                        ObjectName rpName = rp.getRpName();
                        if (getLog().isDebugEnabled()) {
                            getLog().debug("Unregister [" + rpName + "]");
                        }
                        Registry.getRegistry(null, null).unregisterComponent(
                                rpName);
                        rp.setRpName(null);
                    } catch (Exception e) {
                        getLog().warn(sm.getString("abstractProtocol.processorUnregisterError"), e);
                    }
                }
            }
        }

        @Override
        public final void pause() {
            /*
             * Inform all the processors associated with current connections
             * that the endpoint is being paused. Most won't care. Those
             * processing multiplexed streams may wish to take action. For
             * example, HTTP/2 may wish to stop accepting new streams.
             *
             * Note that even if the endpoint is resumed, there is (currently)
             * no API to inform the Processors of this.
             */
            for (SocketWrapperBase<S> wrapper : proto.getEndpoint().getConnections()) {
                Processor processor = (Processor) wrapper.getCurrentProcessor();
                if (processor != null) {
                    processor.pause();
                }
            }
        }
    }

    protected static class RecycledProcessors extends SynchronizedStack<Processor> {

        private final transient ConnectionHandler<?> handler;
        protected final AtomicInteger size = new AtomicInteger(0);

        public RecycledProcessors(ConnectionHandler<?> handler) {
            this.handler = handler;
        }

        @SuppressWarnings("sync-override") // Size may exceed cache size a bit
        @Override
        public boolean push(Processor processor) {
            int cacheSize = handler.getProtocol().getProcessorCache();
            boolean offer = cacheSize == -1 ? true : size.get() < cacheSize;
            //avoid over growing our cache or add after we have stopped
            boolean result = false;
            if (offer) {
                result = super.push(processor);
                if (result) {
                    size.incrementAndGet();
                }
            }
            if (!result) {
                handler.unregister(processor);
            }
            return result;
        }

        @SuppressWarnings("sync-override") // OK if size is too big briefly
        @Override
        public Processor pop() {
            Processor result = super.pop();
            if (result != null) {
                size.decrementAndGet();
            }
            return result;
        }

        @Override
        public synchronized void clear() {
            Processor next = pop();
            while (next != null) {
                handler.unregister(next);
                next = pop();
            }
            super.clear();
            size.set(0);
        }
    }

}

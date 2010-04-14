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

package org.apache.coyote.ajp;

import java.net.InetAddress;
import java.net.Socket;
import java.net.URLEncoder;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.coyote.ActionCode;
import org.apache.coyote.Adapter;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.RequestGroupInfo;
import org.apache.coyote.RequestInfo;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.net.JIoEndpoint;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.JIoEndpoint.Handler;
import org.apache.tomcat.util.res.StringManager;


/**
 * Abstract the protocol implementation, including threading, etc.
 * Processor is single threaded and specific to stream-based protocols,
 * will not fit Jk protocols like JNI.
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 */
public class AjpProtocol 
    implements ProtocolHandler, MBeanRegistration {
    
    
    private static final Log log = LogFactory.getLog(AjpProtocol.class);

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


    // ------------------------------------------------------------ Constructor


    public AjpProtocol() {
        cHandler = new AjpConnectionHandler(this);
        setSoLinger(Constants.DEFAULT_CONNECTION_LINGER);
        setSoTimeout(Constants.DEFAULT_CONNECTION_TIMEOUT);
        //setServerSoTimeout(Constants.DEFAULT_SERVER_SOCKET_TIMEOUT);
        setTcpNoDelay(Constants.DEFAULT_TCP_NO_DELAY);
    }

    
    // ----------------------------------------------------- Instance Variables


    protected ObjectName tpOname;
    
    
    protected ObjectName rgOname;


    /**
     * Associated java.io endpoint.
     */
    protected JIoEndpoint endpoint = new JIoEndpoint();


    /**
     * Configuration attributes.
     */
    protected Hashtable<String,Object> attributes =
        new Hashtable<String,Object>();


    /**
     * Adapter which will process the requests received by this endpoint.
     */
    private Adapter adapter;
    
    
    /**
     * Connection handler for AJP.
     */
    private AjpConnectionHandler cHandler;


    // --------------------------------------------------------- Public Methods


    /** 
     * Pass config info
     */
    public void setAttribute(String name, Object value) {
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("ajpprotocol.setattribute", name, value));
        }
        attributes.put(name, value);
    }

    public Object getAttribute(String key) {
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("ajpprotocol.getattribute", key));
        }
        return attributes.get(key);
    }


    public Iterator<String> getAttributeNames() {
        return attributes.keySet().iterator();
    }


    /**
     * The adapter, used to call the connector
     */
    public void setAdapter(Adapter adapter) {
        this.adapter = adapter;
    }


    public Adapter getAdapter() {
        return adapter;
    }


    /** Start the protocol
     */
    public void init() throws Exception {
        endpoint.setName(getName());
        endpoint.setHandler(cHandler);

        try {
            endpoint.init();
        } catch (Exception ex) {
            log.error(sm.getString("ajpprotocol.endpoint.initerror"), ex);
            throw ex;
        }
        if (log.isInfoEnabled()) {
            log.info(sm.getString("ajpprotocol.init", getName()));
        }
    }


    public void start() throws Exception {
        if (this.domain != null ) {
            try {
                tpOname = new ObjectName
                    (domain + ":" + "type=ThreadPool,name=" + getName());
                Registry.getRegistry(null, null)
                    .registerComponent(endpoint, tpOname, null );
            } catch (Exception e) {
                log.error("Can't register threadpool" );
            }
            rgOname = new ObjectName
                (domain + ":type=GlobalRequestProcessor,name=" + getName());
            Registry.getRegistry(null, null).registerComponent
                (cHandler.global, rgOname, null);
        }

        try {
            endpoint.start();
        } catch (Exception ex) {
            log.error(sm.getString("ajpprotocol.endpoint.starterror"), ex);
            throw ex;
        }
        if (log.isInfoEnabled())
            log.info(sm.getString("ajpprotocol.start", getName()));
    }

    public void pause() throws Exception {
        try {
            endpoint.pause();
        } catch (Exception ex) {
            log.error(sm.getString("ajpprotocol.endpoint.pauseerror"), ex);
            throw ex;
        }
        if (log.isInfoEnabled())
            log.info(sm.getString("ajpprotocol.pause", getName()));
    }

    public void resume() throws Exception {
        try {
            endpoint.resume();
        } catch (Exception ex) {
            log.error(sm.getString("ajpprotocol.endpoint.resumeerror"), ex);
            throw ex;
        }
        if (log.isInfoEnabled())
            log.info(sm.getString("ajpprotocol.resume", getName()));
    }

    public void destroy() throws Exception {
        if (log.isInfoEnabled())
            log.info(sm.getString("ajpprotocol.stop", getName()));
        endpoint.destroy();
        if (tpOname!=null)
            Registry.getRegistry(null, null).unregisterComponent(tpOname);
        if (rgOname != null)
            Registry.getRegistry(null, null).unregisterComponent(rgOname);
    }

    // *
    public String getName() {
        String encodedAddr = "";
        if (getAddress() != null) {
            encodedAddr = "" + getAddress();
            if (encodedAddr.startsWith("/"))
                encodedAddr = encodedAddr.substring(1);
            encodedAddr = URLEncoder.encode(encodedAddr) + "-";
        }
        return ("ajp-" + encodedAddr + endpoint.getPort());
    }

    /**
     * Processor cache.
     */
    protected int processorCache = -1;
    public int getProcessorCache() { return this.processorCache; }
    public void setProcessorCache(int processorCache) { this.processorCache = processorCache; }

    public Executor getExecutor() { return endpoint.getExecutor(); }
    public void setExecutor(Executor executor) { endpoint.setExecutor(executor); }
    
    public int getMaxThreads() { return endpoint.getMaxThreads(); }
    public void setMaxThreads(int maxThreads) { endpoint.setMaxThreads(maxThreads); }

    public int getThreadPriority() { return endpoint.getThreadPriority(); }
    public void setThreadPriority(int threadPriority) { endpoint.setThreadPriority(threadPriority); }

    public int getBacklog() { return endpoint.getBacklog(); }
    public void setBacklog(int backlog) { endpoint.setBacklog(backlog); }

    public int getPort() { return endpoint.getPort(); }
    public void setPort(int port) { endpoint.setPort(port); }

    public InetAddress getAddress() { return endpoint.getAddress(); }
    public void setAddress(InetAddress ia) { endpoint.setAddress(ia); }

    public boolean getTcpNoDelay() { return endpoint.getTcpNoDelay(); }
    public void setTcpNoDelay(boolean tcpNoDelay) { endpoint.setTcpNoDelay(tcpNoDelay); }

    public int getSoLinger() { return endpoint.getSoLinger(); }
    public void setSoLinger(int soLinger) { endpoint.setSoLinger(soLinger); }

    public int getSoTimeout() { return endpoint.getSoTimeout(); }
    public void setSoTimeout(int soTimeout) { endpoint.setSoTimeout(soTimeout); }

    /**
     * Should authentication be done in the native webserver layer, 
     * or in the Servlet container ?
     */
    protected boolean tomcatAuthentication = true;
    public boolean getTomcatAuthentication() { return tomcatAuthentication; }
    public void setTomcatAuthentication(boolean tomcatAuthentication) { this.tomcatAuthentication = tomcatAuthentication; }

    /**
     * Required secret.
     */
    protected String requiredSecret = null;
    public void setRequiredSecret(String requiredSecret) { this.requiredSecret = requiredSecret; }
    
    /**
     * AJP packet size.
     */
    protected int packetSize = Constants.MAX_PACKET_SIZE;
    public int getPacketSize() { return packetSize; }
    public void setPacketSize(int packetSize) {
        if(packetSize < Constants.MAX_PACKET_SIZE) {
            this.packetSize = Constants.MAX_PACKET_SIZE;
        } else {
            this.packetSize = packetSize;
        }
    }

    
    /**
     * The number of seconds Tomcat will wait for a subsequent request
     * before closing the connection.
     */
    protected int keepAliveTimeout = -1;
    public int getKeepAliveTimeout() { return keepAliveTimeout; }
    public void setKeepAliveTimeout(int timeout) { keepAliveTimeout = timeout; }


    // --------------------------------------  AjpConnectionHandler Inner Class


    protected static class AjpConnectionHandler implements Handler {

        protected AjpProtocol proto;
        protected AtomicLong registerCount = new AtomicLong(0);
        protected RequestGroupInfo global = new RequestGroupInfo();
        protected ConcurrentHashMap<SocketWrapper<Socket>, AjpProcessor> connections =
            new ConcurrentHashMap<SocketWrapper<Socket>, AjpProcessor>();

        protected ConcurrentLinkedQueue<AjpProcessor> recycledProcessors = 
            new ConcurrentLinkedQueue<AjpProcessor>() {
            protected AtomicInteger size = new AtomicInteger(0);
            @Override
            public boolean offer(AjpProcessor processor) {
                boolean offer = (proto.processorCache == -1) ? true : (size.get() < proto.processorCache);
                //avoid over growing our cache or add after we have stopped
                boolean result = false;
                if ( offer ) {
                    result = super.offer(processor);
                    if ( result ) {
                        size.incrementAndGet();
                    }
                }
                if (!result) unregister(processor);
                return result;
            }
            
            @Override
            public AjpProcessor poll() {
                AjpProcessor result = super.poll();
                if ( result != null ) {
                    size.decrementAndGet();
                }
                return result;
            }
            
            @Override
            public void clear() {
                AjpProcessor next = poll();
                while ( next != null ) {
                    unregister(next);
                    next = poll();
                }
                super.clear();
                size.set(0);
            }
        };

        public AjpConnectionHandler(AjpProtocol proto) {
            this.proto = proto;
        }
        
        public SocketState process(SocketWrapper<Socket> socket) {
            return process(socket,SocketStatus.OPEN);
        }

        public SocketState process(SocketWrapper<Socket> socket, SocketStatus status) {
            AjpProcessor processor = connections.remove(socket);
            try {
                if (processor == null) {
                    processor = recycledProcessors.poll();
                }
                if (processor == null) {
                    processor = createProcessor();
                }
                processor.action(ActionCode.ACTION_START, null);

                SocketState state = socket.isAsync()?processor.asyncDispatch(status):processor.process(socket);
                if (state == SocketState.LONG) {
                    connections.put(socket, processor);
                    socket.setAsync(true);
                } else {
                    connections.remove(socket);
                    socket.setAsync(false);
                }
                return state;
            } catch(java.net.SocketException e) {
                // SocketExceptions are normal
                AjpProtocol.log.debug
                    (sm.getString
                     ("ajpprotocol.proto.socketexception.debug"), e);
            } catch (java.io.IOException e) {
                // IOExceptions are normal
                AjpProtocol.log.debug
                    (sm.getString
                     ("ajpprotocol.proto.ioexception.debug"), e);
            }
            // Future developers: if you discover any other
            // rare-but-nonfatal exceptions, catch them here, and log as
            // above.
            catch (Throwable e) {
                // any other exception or error is odd. Here we log it
                // with "ERROR" level, so it will show up even on
                // less-than-verbose logs.
                AjpProtocol.log.error
                    (sm.getString("ajpprotocol.proto.error"), e);
            } finally {
                processor.action(ActionCode.ACTION_STOP, null);
                recycledProcessors.offer(processor);
            }
            return SocketState.CLOSED;
        }

        protected AjpProcessor createProcessor() {
            AjpProcessor processor = new AjpProcessor(proto.packetSize, proto.endpoint);
            processor.setAdapter(proto.adapter);
            processor.setTomcatAuthentication(proto.tomcatAuthentication);
            processor.setRequiredSecret(proto.requiredSecret);
            processor.setKeepAliveTimeout(proto.keepAliveTimeout);
            register(processor);
            return processor;
        }
        
        protected void register(AjpProcessor processor) {
            if (proto.getDomain() != null) {
                synchronized (this) {
                    try {
                        long count = registerCount.incrementAndGet();
                        RequestInfo rp = processor.getRequest().getRequestProcessor();
                        rp.setGlobalProcessor(global);
                        ObjectName rpName = new ObjectName
                            (proto.getDomain() + ":type=RequestProcessor,worker="
                                + proto.getName() + ",name=AjpRequest" + count);
                        if (log.isDebugEnabled()) {
                            log.debug("Register " + rpName);
                        }
                        Registry.getRegistry(null, null).registerComponent(rp, rpName, null);
                        rp.setRpName(rpName);
                    } catch (Exception e) {
                        log.warn("Error registering request");
                    }
                }
            }
        }

        protected void unregister(AjpProcessor processor) {
            if (proto.getDomain() != null) {
                synchronized (this) {
                    try {
                        RequestInfo rp = processor.getRequest().getRequestProcessor();
                        rp.setGlobalProcessor(null);
                        ObjectName rpName = rp.getRpName();
                        if (log.isDebugEnabled()) {
                            log.debug("Unregister " + rpName);
                        }
                        Registry.getRegistry(null, null).unregisterComponent(rpName);
                        rp.setRpName(null);
                    } catch (Exception e) {
                        log.warn("Error unregistering request", e);
                    }
                }
            }
        }

    }


    // -------------------- Various implementation classes --------------------


    protected String domain;
    protected ObjectName oname;
    protected MBeanServer mserver;

    public ObjectName getObjectName() {
        return oname;
    }

    public String getDomain() {
        return domain;
    }

    public ObjectName preRegister(MBeanServer server,
                                  ObjectName name) throws Exception {
        oname=name;
        mserver=server;
        domain=name.getDomain();
        return name;
    }

    public void postRegister(Boolean registrationDone) {
    }

    public void preDeregister() throws Exception {
    }

    public void postDeregister() {
    }
    
 
}

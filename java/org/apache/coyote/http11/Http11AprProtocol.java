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

package org.apache.coyote.http11;

import java.net.InetAddress;
import java.net.URLEncoder;
import java.util.HashMap;
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
import org.apache.tomcat.util.net.AprEndpoint;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.AprEndpoint.Handler;
import org.apache.tomcat.util.res.StringManager;


/**
 * Abstract the protocol implementation, including threading, etc.
 * Processor is single threaded and specific to stream-based protocols,
 * will not fit Jk protocols like JNI.
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 */
public class Http11AprProtocol implements ProtocolHandler, MBeanRegistration {

    private static final Log log = LogFactory.getLog(Http11AprProtocol.class);

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);

    public Http11AprProtocol() {
        setSoLinger(Constants.DEFAULT_CONNECTION_LINGER);
        setSoTimeout(Constants.DEFAULT_CONNECTION_TIMEOUT);
        //setServerSoTimeout(Constants.DEFAULT_SERVER_SOCKET_TIMEOUT);
        setTcpNoDelay(Constants.DEFAULT_TCP_NO_DELAY);
    }

    /** Pass config info
     */
    public void setAttribute( String name, Object value ) {
        if( log.isTraceEnabled())
            log.trace(sm.getString("http11protocol.setattribute", name, value));

        attributes.put(name, value);
    }

    public Object getAttribute( String key ) {
        if( log.isTraceEnabled())
            log.trace(sm.getString("http11protocol.getattribute", key));
        return attributes.get(key);
    }

    public Iterator<String> getAttributeNames() {
        return attributes.keySet().iterator();
    }

    /**
     * The adapter, used to call the connector.
     */
    protected Adapter adapter;
    public void setAdapter(Adapter adapter) { this.adapter = adapter; }
    public Adapter getAdapter() { return adapter; }


    /** Start the protocol
     */
    public void init() throws Exception {
        endpoint.setName(getName());
        endpoint.setHandler(cHandler);

        try {
            endpoint.init();
        } catch (Exception ex) {
            log.error(sm.getString("http11protocol.endpoint.initerror"), ex);
            throw ex;
        }
        if(log.isInfoEnabled())
            log.info(sm.getString("http11protocol.init", getName()));

    }

    ObjectName tpOname;
    ObjectName rgOname;

    public void start() throws Exception {
        if( this.domain != null ) {
            try {
                tpOname=new ObjectName
                    (domain + ":" + "type=ThreadPool,name=" + getName());
                Registry.getRegistry(null, null)
                .registerComponent(endpoint, tpOname, null );
            } catch (Exception e) {
                log.error("Can't register threadpool" );
            }
            rgOname=new ObjectName
                (domain + ":type=GlobalRequestProcessor,name=" + getName());
            Registry.getRegistry(null, null).registerComponent
                ( cHandler.global, rgOname, null );
        }

        try {
            endpoint.start();
        } catch (Exception ex) {
            log.error(sm.getString("http11protocol.endpoint.starterror"), ex);
            throw ex;
        }
        if(log.isInfoEnabled())
            log.info(sm.getString("http11protocol.start", getName()));
    }

    public void pause() throws Exception {
        try {
            endpoint.pause();
        } catch (Exception ex) {
            log.error(sm.getString("http11protocol.endpoint.pauseerror"), ex);
            throw ex;
        }
        if(log.isInfoEnabled())
            log.info(sm.getString("http11protocol.pause", getName()));
    }

    public void resume() throws Exception {
        try {
            endpoint.resume();
        } catch (Exception ex) {
            log.error(sm.getString("http11protocol.endpoint.resumeerror"), ex);
            throw ex;
        }
        if(log.isInfoEnabled())
            log.info(sm.getString("http11protocol.resume", getName()));
    }

    public void destroy() throws Exception {
        if(log.isInfoEnabled())
            log.info(sm.getString("http11protocol.stop", getName()));
        endpoint.destroy();
        if( tpOname!=null )
            Registry.getRegistry(null, null).unregisterComponent(tpOname);
        if( rgOname != null )
            Registry.getRegistry(null, null).unregisterComponent(rgOname);
    }

    public String getName() {
        String encodedAddr = "";
        if (getAddress() != null) {
            encodedAddr = "" + getAddress();
            if (encodedAddr.startsWith("/"))
                encodedAddr = encodedAddr.substring(1);
            encodedAddr = URLEncoder.encode(encodedAddr) + "-";
        }
        return ("http-" + encodedAddr + endpoint.getPort());
    }

    protected AprEndpoint endpoint=new AprEndpoint();

    protected HashMap<String, Object> attributes = new HashMap<String, Object>();

    private Http11ConnectionHandler cHandler = new Http11ConnectionHandler(this);

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
     * The number of seconds Tomcat will wait for a subsequent request
     * before closing the connection.
     */
    public int getKeepAliveTimeout() { return endpoint.getKeepAliveTimeout(); }
    public void setKeepAliveTimeout(int timeout) { endpoint.setKeepAliveTimeout(timeout); }

    public boolean getUseSendfile() { return endpoint.getUseSendfile(); }
    public void setUseSendfile(boolean useSendfile) { endpoint.setUseSendfile(useSendfile); }

    public int getPollTime() { return endpoint.getPollTime(); }
    public void setPollTime(int pollTime) { endpoint.setPollTime(pollTime); }

    public void setPollerSize(int pollerSize) { endpoint.setPollerSize(pollerSize); }
    public int getPollerSize() { return endpoint.getPollerSize(); }

    public void setPollerThreadCount(int pollerThreadCount) { endpoint.setPollerThreadCount(pollerThreadCount); }
    public int getPollerThreadCount() { return endpoint.getPollerThreadCount(); }
    
    public int getSendfileSize() { return endpoint.getSendfileSize(); }
    public void setSendfileSize(int sendfileSize) { endpoint.setSendfileSize(sendfileSize); }
    
    public void setSendfileThreadCount(int sendfileThreadCount) { endpoint.setSendfileThreadCount(sendfileThreadCount); }
    public int getSendfileThreadCount() { return endpoint.getSendfileThreadCount(); }

    public boolean getDeferAccept() { return endpoint.getDeferAccept(); }
    public void setDeferAccept(boolean deferAccept) { endpoint.setDeferAccept(deferAccept); }

    protected int socketBuffer = 9000;
    public int getSocketBuffer() { return socketBuffer; }
    public void setSocketBuffer(int socketBuffer) { this.socketBuffer = socketBuffer; }

    /**
     * Maximum size of the post which will be saved when processing certain
     * requests, such as a POST.
     */
    protected int maxSavePostSize = 4 * 1024;
    public int getMaxSavePostSize() { return maxSavePostSize; }
    public void setMaxSavePostSize(int valueI) { maxSavePostSize = valueI; }

    // HTTP
    /**
     * Maximum size of the HTTP message header.
     */
    protected int maxHttpHeaderSize = 8 * 1024;
    public int getMaxHttpHeaderSize() { return maxHttpHeaderSize; }
    public void setMaxHttpHeaderSize(int valueI) { maxHttpHeaderSize = valueI; }


    // HTTP
    /**
     * If true, the regular socket timeout will be used for the full duration
     * of the connection.
     */
    protected boolean disableUploadTimeout = true;
    public boolean getDisableUploadTimeout() { return disableUploadTimeout; }
    public void setDisableUploadTimeout(boolean isDisabled) { disableUploadTimeout = isDisabled; }

    // HTTP
    /**
     * Integrated compression support.
     */
    protected String compression = "off";
    public String getCompression() { return compression; }
    public void setCompression(String valueS) { compression = valueS; }
    
    
    // HTTP
    protected String noCompressionUserAgents = null;
    public String getNoCompressionUserAgents() { return noCompressionUserAgents; }
    public void setNoCompressionUserAgents(String valueS) { noCompressionUserAgents = valueS; }

    
    // HTTP
    protected String compressableMimeTypes = "text/html,text/xml,text/plain";
    public String getCompressableMimeType() { return compressableMimeTypes; }
    public void setCompressableMimeType(String valueS) { compressableMimeTypes = valueS; }
    
    
    // HTTP
    protected int compressionMinSize = 2048;
    public int getCompressionMinSize() { return compressionMinSize; }
    public void setCompressionMinSize(int valueI) { compressionMinSize = valueI; }


    // HTTP
    /**
     * User agents regular expressions which should be restricted to HTTP/1.0 support.
     */
    protected String restrictedUserAgents = null;
    public String getRestrictedUserAgents() { return restrictedUserAgents; }
    public void setRestrictedUserAgents(String valueS) { restrictedUserAgents = valueS; }
    
    
    protected String protocol = null;
    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { setSecure(true); this.protocol = protocol; }

    /**
     * Maximum number of requests which can be performed over a keepalive 
     * connection. The default is the same as for Apache HTTP Server.
     */
    protected int maxKeepAliveRequests = 100;
    public int getMaxKeepAliveRequests() { return maxKeepAliveRequests; }
    public void setMaxKeepAliveRequests(int mkar) { maxKeepAliveRequests = mkar; }

    /**
     * Return the Keep-Alive policy for the connection.
     */
    public boolean getKeepAlive() {
        return ((maxKeepAliveRequests != 0) && (maxKeepAliveRequests != 1));
    }

    /**
     * Set the keep-alive policy for this connection.
     */
    public void setKeepAlive(boolean keepAlive) {
        if (!keepAlive) {
            setMaxKeepAliveRequests(1);
        }
    }

    /**
     * Server header.
     */
    protected String server;
    public void setServer( String server ) { this.server = server; }
    public String getServer() { return server; }

    /**
     * This timeout represents the socket timeout which will be used while
     * the adapter execution is in progress, unless disableUploadTimeout
     * is set to true. The default is the same as for Apache HTTP Server
     * (300 000 milliseconds).
     */
    protected int timeout = 300000;
    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = timeout; }

    /**
     * This field indicates if the protocol is secure from the perspective of
     * the client (= https is used).
     */
    protected boolean secure;
    public boolean getSecure() { return secure; }
    public void setSecure(boolean b) { secure = b; }

    // --------------------  SSL related properties --------------------

    /**
     * SSL engine.
     */
    public boolean isSSLEnabled() { return endpoint.isSSLEnabled(); }
    public void setSSLEnabled(boolean SSLEnabled) { endpoint.setSSLEnabled(SSLEnabled); }


    /**
     * SSL protocol.
     */
    public String getSSLProtocol() { return endpoint.getSSLProtocol(); }
    public void setSSLProtocol(String SSLProtocol) { endpoint.setSSLProtocol(SSLProtocol); }


    /**
     * SSL password (if a cert is encrypted, and no password has been provided, a callback
     * will ask for a password).
     */
    public String getSSLPassword() { return endpoint.getSSLPassword(); }
    public void setSSLPassword(String SSLPassword) { endpoint.setSSLPassword(SSLPassword); }


    /**
     * SSL cipher suite.
     */
    public String getSSLCipherSuite() { return endpoint.getSSLCipherSuite(); }
    public void setSSLCipherSuite(String SSLCipherSuite) { endpoint.setSSLCipherSuite(SSLCipherSuite); }


    /**
     * SSL certificate file.
     */
    public String getSSLCertificateFile() { return endpoint.getSSLCertificateFile(); }
    public void setSSLCertificateFile(String SSLCertificateFile) { endpoint.setSSLCertificateFile(SSLCertificateFile); }


    /**
     * SSL certificate key file.
     */
    public String getSSLCertificateKeyFile() { return endpoint.getSSLCertificateKeyFile(); }
    public void setSSLCertificateKeyFile(String SSLCertificateKeyFile) { endpoint.setSSLCertificateKeyFile(SSLCertificateKeyFile); }


    /**
     * SSL certificate chain file.
     */
    public String getSSLCertificateChainFile() { return endpoint.getSSLCertificateChainFile(); }
    public void setSSLCertificateChainFile(String SSLCertificateChainFile) { endpoint.setSSLCertificateChainFile(SSLCertificateChainFile); }


    /**
     * SSL CA certificate path.
     */
    public String getSSLCACertificatePath() { return endpoint.getSSLCACertificatePath(); }
    public void setSSLCACertificatePath(String SSLCACertificatePath) { endpoint.setSSLCACertificatePath(SSLCACertificatePath); }


    /**
     * SSL CA certificate file.
     */
    public String getSSLCACertificateFile() { return endpoint.getSSLCACertificateFile(); }
    public void setSSLCACertificateFile(String SSLCACertificateFile) { endpoint.setSSLCACertificateFile(SSLCACertificateFile); }


    /**
     * SSL CA revocation path.
     */
    public String getSSLCARevocationPath() { return endpoint.getSSLCARevocationPath(); }
    public void setSSLCARevocationPath(String SSLCARevocationPath) { endpoint.setSSLCARevocationPath(SSLCARevocationPath); }


    /**
     * SSL CA revocation file.
     */
    public String getSSLCARevocationFile() { return endpoint.getSSLCARevocationFile(); }
    public void setSSLCARevocationFile(String SSLCARevocationFile) { endpoint.setSSLCARevocationFile(SSLCARevocationFile); }


    /**
     * SSL verify client.
     */
    public String getSSLVerifyClient() { return endpoint.getSSLVerifyClient(); }
    public void setSSLVerifyClient(String SSLVerifyClient) { endpoint.setSSLVerifyClient(SSLVerifyClient); }


    /**
     * SSL verify depth.
     */
    public int getSSLVerifyDepth() { return endpoint.getSSLVerifyDepth(); }
    public void setSSLVerifyDepth(int SSLVerifyDepth) { endpoint.setSSLVerifyDepth(SSLVerifyDepth); }
    
    // --------------------  Connection handler --------------------

    static class Http11ConnectionHandler implements Handler {
        
        protected Http11AprProtocol proto;
        protected AtomicLong registerCount = new AtomicLong(0);
        protected RequestGroupInfo global = new RequestGroupInfo();
        
        protected ConcurrentHashMap<Long, Http11AprProcessor> connections =
            new ConcurrentHashMap<Long, Http11AprProcessor>();
        protected ConcurrentLinkedQueue<Http11AprProcessor> recycledProcessors = 
            new ConcurrentLinkedQueue<Http11AprProcessor>() {
            protected AtomicInteger size = new AtomicInteger(0);
            @Override
            public boolean offer(Http11AprProcessor processor) {
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
            public Http11AprProcessor poll() {
                Http11AprProcessor result = super.poll();
                if ( result != null ) {
                    size.decrementAndGet();
                }
                return result;
            }
            
            @Override
            public void clear() {
                Http11AprProcessor next = poll();
                while ( next != null ) {
                    unregister(next);
                    next = poll();
                }
                super.clear();
                size.set(0);
            }
        };


        Http11ConnectionHandler(Http11AprProtocol proto) {
            this.proto = proto;
        }

        public SocketState event(long socket, SocketStatus status) {
            Http11AprProcessor result = connections.get(Long.valueOf(socket));
            
            SocketState state = SocketState.CLOSED; 
            if (result != null) {
                // Call the appropriate event
                try {
                    state = result.event(status);
                } catch (java.net.SocketException e) {
                    // SocketExceptions are normal
                    Http11AprProtocol.log.debug
                        (sm.getString
                            ("http11protocol.proto.socketexception.debug"), e);
                } catch (java.io.IOException e) {
                    // IOExceptions are normal
                    Http11AprProtocol.log.debug
                        (sm.getString
                            ("http11protocol.proto.ioexception.debug"), e);
                }
                // Future developers: if you discover any other
                // rare-but-nonfatal exceptions, catch them here, and log as
                // above.
                catch (Throwable e) {
                    // any other exception or error is odd. Here we log it
                    // with "ERROR" level, so it will show up even on
                    // less-than-verbose logs.
                    Http11AprProtocol.log.error
                        (sm.getString("http11protocol.proto.error"), e);
                } finally {
                    if (state != SocketState.LONG) {
                        connections.remove(Long.valueOf(socket));
                        recycledProcessors.offer(result);
                        if (state == SocketState.OPEN) {
                            proto.endpoint.getPoller().add(socket);
                        }
                    } else {
                        proto.endpoint.getCometPoller().add(socket);
                    }
                }
            }
            return state;
        }
        
        public SocketState process(long socket) {
            Http11AprProcessor processor = recycledProcessors.poll();
            try {
                if (processor == null) {
                    processor = createProcessor();
                }

                processor.action(ActionCode.ACTION_START, null);

                SocketState state = processor.process(socket);
                if (state == SocketState.LONG) {
                    // Associate the connection with the processor. The next request 
                    // processed by this thread will use either a new or a recycled
                    // processor.
                    connections.put(Long.valueOf(socket), processor);
                    proto.endpoint.getCometPoller().add(socket);
                } else {
                    recycledProcessors.offer(processor);
                }
                return state;

            } catch (java.net.SocketException e) {
                // SocketExceptions are normal
                Http11AprProtocol.log.debug
                    (sm.getString
                     ("http11protocol.proto.socketexception.debug"), e);
            } catch (java.io.IOException e) {
                // IOExceptions are normal
                Http11AprProtocol.log.debug
                    (sm.getString
                     ("http11protocol.proto.ioexception.debug"), e);
            }
            // Future developers: if you discover any other
            // rare-but-nonfatal exceptions, catch them here, and log as
            // above.
            catch (Throwable e) {
                // any other exception or error is odd. Here we log it
                // with "ERROR" level, so it will show up even on
                // less-than-verbose logs.
                Http11AprProtocol.log.error
                    (sm.getString("http11protocol.proto.error"), e);
            }
            recycledProcessors.offer(processor);
            return SocketState.CLOSED;
        }

        public SocketState asyncDispatch(long socket, SocketStatus status) {
            Http11AprProcessor result = connections.get(Long.valueOf(socket));
            
            SocketState state = SocketState.CLOSED; 
            if (result != null) {
                // Call the appropriate event
                try {
                    state = result.asyncDispatch(socket, status);
                } catch (java.net.SocketException e) {
                    // SocketExceptions are normal
                    Http11AprProtocol.log.debug
                        (sm.getString
                            ("http11protocol.proto.socketexception.debug"), e);
                } catch (java.io.IOException e) {
                    // IOExceptions are normal
                    Http11AprProtocol.log.debug
                        (sm.getString
                            ("http11protocol.proto.ioexception.debug"), e);
                }
                // Future developers: if you discover any other
                // rare-but-nonfatal exceptions, catch them here, and log as
                // above.
                catch (Throwable e) {
                    // any other exception or error is odd. Here we log it
                    // with "ERROR" level, so it will show up even on
                    // less-than-verbose logs.
                    Http11AprProtocol.log.error
                        (sm.getString("http11protocol.proto.error"), e);
                } finally {
                    if (state != SocketState.LONG) {
                        connections.remove(Long.valueOf(socket));
                        recycledProcessors.offer(result);
                        if (state == SocketState.OPEN) {
                            proto.endpoint.getPoller().add(socket);
                        }
                    }
                }
            }
            return state;
        }

        protected Http11AprProcessor createProcessor() {
            Http11AprProcessor processor =
                new Http11AprProcessor(proto.maxHttpHeaderSize, proto.endpoint);
            processor.setAdapter(proto.adapter);
            processor.setMaxKeepAliveRequests(proto.maxKeepAliveRequests);
            processor.setTimeout(proto.timeout);
            processor.setDisableUploadTimeout(proto.disableUploadTimeout);
            processor.setCompressionMinSize(proto.compressionMinSize);
            processor.setCompression(proto.compression);
            processor.setNoCompressionUserAgents(proto.noCompressionUserAgents);
            processor.setCompressableMimeTypes(proto.compressableMimeTypes);
            processor.setRestrictedUserAgents(proto.restrictedUserAgents);
            processor.setSocketBuffer(proto.socketBuffer);
            processor.setMaxSavePostSize(proto.maxSavePostSize);
            processor.setServer(proto.server);
            register(processor);
            return processor;
        }
        
        protected void register(Http11AprProcessor processor) {
            if (proto.getDomain() != null) {
                synchronized (this) {
                    try {
                        long count = registerCount.incrementAndGet();
                        RequestInfo rp = processor.getRequest().getRequestProcessor();
                        rp.setGlobalProcessor(global);
                        ObjectName rpName = new ObjectName
                            (proto.getDomain() + ":type=RequestProcessor,worker="
                                + proto.getName() + ",name=HttpRequest" + count);
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

        protected void unregister(Http11AprProcessor processor) {
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

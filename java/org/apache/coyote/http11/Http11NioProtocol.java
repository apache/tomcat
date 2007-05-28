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
import java.util.Hashtable;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.coyote.ActionCode;
import org.apache.coyote.ActionHook;
import org.apache.coyote.Adapter;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.RequestGroupInfo;
import org.apache.coyote.RequestInfo;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint;
import org.apache.tomcat.util.net.NioEndpoint.Handler;
import org.apache.tomcat.util.net.SSLImplementation;
import org.apache.tomcat.util.net.SecureNioChannel;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.res.StringManager;


/**
 * Abstract the protocol implementation, including threading, etc.
 * Processor is single threaded and specific to stream-based protocols,
 * will not fit Jk protocols like JNI.
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 * @author Filip Hanik
 */
public class Http11NioProtocol implements ProtocolHandler, MBeanRegistration
{
    protected SSLImplementation sslImplementation = null;
    
    public Http11NioProtocol() {
        cHandler = new Http11ConnectionHandler( this );
        setSoLinger(Constants.DEFAULT_CONNECTION_LINGER);
        setSoTimeout(Constants.DEFAULT_CONNECTION_TIMEOUT);
        //setServerSoTimeout(Constants.DEFAULT_SERVER_SOCKET_TIMEOUT);
        setTcpNoDelay(Constants.DEFAULT_TCP_NO_DELAY);
    }

    /**
     * The string manager for this package.
     */
    protected static StringManager sm =
        StringManager.getManager(Constants.Package);

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

    public Iterator getAttributeNames() {
        return attributes.keySet().iterator();
    }

    /**
     * Set a property.
     */
    public void setProperty(String name, String value) {
        if ( name!=null && (name.startsWith("socket.") ||name.startsWith("selectorPool.")) ){
            ep.setProperty(name, value);
        } else {
            ep.setProperty(name,value); //make sure we at least try to set all properties
        }
        setAttribute(name, value);
    }

    /**
     * Get a property
     */
    public String getProperty(String name) {
        return (String)getAttribute(name);
    }

    /** The adapter, used to call the connector
     */
    public void setAdapter(Adapter adapter) {
        this.adapter=adapter;
    }

    public Adapter getAdapter() {
        return adapter;
    }


    /** Start the protocol
     */
    public void init() throws Exception {
        ep.setName(getName());
        ep.setHandler(cHandler);
        
        //todo, determine if we even need these
        ep.getSocketProperties().setRxBufSize(Math.max(ep.getSocketProperties().getRxBufSize(),getMaxHttpHeaderSize()));
        ep.getSocketProperties().setTxBufSize(Math.max(ep.getSocketProperties().getTxBufSize(),getMaxHttpHeaderSize()));
        
        try {
            ep.init();
            sslImplementation = SSLImplementation.getInstance("org.apache.tomcat.util.net.jsse.JSSEImplementation");
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
                .registerComponent(ep, tpOname, null );
            } catch (Exception e) {
                log.error("Can't register threadpool" );
            }
            rgOname=new ObjectName
                (domain + ":type=GlobalRequestProcessor,name=" + getName());
            Registry.getRegistry(null, null).registerComponent
                ( cHandler.global, rgOname, null );
        }

        try {
            ep.start();
        } catch (Exception ex) {
            log.error(sm.getString("http11protocol.endpoint.starterror"), ex);
            throw ex;
        }
        if(log.isInfoEnabled())
            log.info(sm.getString("http11protocol.start", getName()));
    }

    public void pause() throws Exception {
        try {
            ep.pause();
        } catch (Exception ex) {
            log.error(sm.getString("http11protocol.endpoint.pauseerror"), ex);
            throw ex;
        }
        if(log.isInfoEnabled())
            log.info(sm.getString("http11protocol.pause", getName()));
    }

    public void resume() throws Exception {
        try {
            ep.resume();
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
        ep.destroy();
        if( tpOname!=null )
            Registry.getRegistry(null, null).unregisterComponent(tpOname);
        if( rgOname != null )
            Registry.getRegistry(null, null).unregisterComponent(rgOname);
    }

    // -------------------- Properties--------------------
    protected NioEndpoint ep=new NioEndpoint();
    protected boolean secure = false;

    protected Hashtable attributes = new Hashtable();

    private int maxKeepAliveRequests=100; // as in Apache HTTPD server
    private int timeout = 300000;   // 5 minutes as in Apache HTTPD server
    private int maxSavePostSize = 4 * 1024;
    private int maxHttpHeaderSize = 8 * 1024;
    protected int processorCache = 200; //max number of Http11NioProcessor objects cached
    private int socketCloseDelay=-1;
    private boolean disableUploadTimeout = true;
    private int socketBuffer = 9000;
    
    private Adapter adapter;
    private Http11ConnectionHandler cHandler;

    /**
     * Compression value.
     */
    private String compression = "off";
    private String noCompressionUserAgents = null;
    private String restrictedUserAgents = null;
    private String compressableMimeTypes = "text/html,text/xml,text/plain";
    private int compressionMinSize    = 2048;

    private String server;

    // -------------------- Pool setup --------------------

    public void setPollerThreadCount(int count) {
        ep.setPollerThreadCount(count);
    }
    
    public int getPollerThreadCount() {
        return ep.getPollerThreadCount();
    }
    
    public void setSelectorTimeout(long timeout) {
        ep.setSelectorTimeout(timeout);
    }
    
    public long getSelectorTimeout() {
        return ep.getSelectorTimeout();
    }
    // *
    public Executor getExecutor() {
        return ep.getExecutor();
    }

    // *
    public void setExecutor(Executor executor) {
        ep.setExecutor(executor);
    }
    
    public void setUseExecutor(boolean useexec) {
        ep.setUseExecutor(useexec);
    }

    public int getMaxThreads() {
        return ep.getMaxThreads();
    }

    public void setMaxThreads( int maxThreads ) {
        ep.setMaxThreads(maxThreads);
        setAttribute("maxThreads", "" + maxThreads);
    }

    public void setThreadPriority(int threadPriority) {
      ep.setThreadPriority(threadPriority);
      setAttribute("threadPriority", "" + threadPriority);
    }
    
    public void setAcceptorThreadPriority(int threadPriority) {
      ep.setAcceptorThreadPriority(threadPriority);
      setAttribute("acceptorThreadPriority", "" + threadPriority);
    }

    public void setPollerThreadPriority(int threadPriority) {
      ep.setPollerThreadPriority(threadPriority);
      setAttribute("pollerThreadPriority", "" + threadPriority);
    }

    public int getThreadPriority() {
      return ep.getThreadPriority();
    }

    public int getAcceptorThreadPriority() {
      return ep.getAcceptorThreadPriority();
    }
    
    public int getPollerThreadPriority() {
      return ep.getThreadPriority();
    }
    
    
    public boolean getUseSendfile() {
        return ep.getUseSendfile();
    }

    public void setUseSendfile(boolean useSendfile) {
        ep.setUseSendfile(useSendfile);
    }

    
    // -------------------- Tcp setup --------------------

    public int getBacklog() {
        return ep.getBacklog();
    }

    public void setBacklog( int i ) {
        ep.setBacklog(i);
        setAttribute("backlog", "" + i);
    }

    public int getPort() {
        return ep.getPort();
    }

    public void setPort( int port ) {
        ep.setPort(port);
        setAttribute("port", "" + port);
    }

    public InetAddress getAddress() {
        return ep.getAddress();
    }

    public void setAddress(InetAddress ia) {
        ep.setAddress( ia );
        setAttribute("address", "" + ia);
    }

    public String getName() {
        String encodedAddr = "";
        if (getAddress() != null) {
            encodedAddr = "" + getAddress();
            if (encodedAddr.startsWith("/"))
                encodedAddr = encodedAddr.substring(1);
            encodedAddr = URLEncoder.encode(encodedAddr) + "-";
        }
        return ("http-" + encodedAddr + ep.getPort());
    }

    public boolean getTcpNoDelay() {
        return ep.getTcpNoDelay();
    }

    public void setTcpNoDelay( boolean b ) {
        ep.setTcpNoDelay( b );
        setAttribute("tcpNoDelay", "" + b);
    }

    public boolean getDisableUploadTimeout() {
        return disableUploadTimeout;
    }

    public void setDisableUploadTimeout(boolean isDisabled) {
        disableUploadTimeout = isDisabled;
    }

    public int getSocketBuffer() {
        return socketBuffer;
    }

    public void setSocketBuffer(int valueI) {
        socketBuffer = valueI;
    }

    public String getCompression() {
        return compression;
    }

    public void setCompression(String valueS) {
        compression = valueS;
        setAttribute("compression", valueS);
    }

    public int getMaxSavePostSize() {
        return maxSavePostSize;
    }

    public void setMaxSavePostSize(int valueI) {
        maxSavePostSize = valueI;
        setAttribute("maxSavePostSize", "" + valueI);
    }

    public int getMaxHttpHeaderSize() {
        return maxHttpHeaderSize;
    }

    public void setMaxHttpHeaderSize(int valueI) {
        maxHttpHeaderSize = valueI;
        setAttribute("maxHttpHeaderSize", "" + valueI);
    }

    public String getRestrictedUserAgents() {
        return restrictedUserAgents;
    }

    public void setRestrictedUserAgents(String valueS) {
        restrictedUserAgents = valueS;
        setAttribute("restrictedUserAgents", valueS);
    }

    public String getNoCompressionUserAgents() {
        return noCompressionUserAgents;
    }

    public void setNoCompressionUserAgents(String valueS) {
        noCompressionUserAgents = valueS;
        setAttribute("noCompressionUserAgents", valueS);
    }

    public String getCompressableMimeType() {
        return compressableMimeTypes;
    }

    public void setCompressableMimeType(String valueS) {
        compressableMimeTypes = valueS;
        setAttribute("compressableMimeTypes", valueS);
    }

    public int getCompressionMinSize() {
        return compressionMinSize;
    }

    public void setCompressionMinSize(int valueI) {
        compressionMinSize = valueI;
        setAttribute("compressionMinSize", "" + valueI);
    }

    public int getSoLinger() {
        return ep.getSoLinger();
    }

    public void setSoLinger( int i ) {
        ep.setSoLinger( i );
        setAttribute("soLinger", "" + i);
    }

    public int getSoTimeout() {
        return ep.getSoTimeout();
    }

    public void setSoTimeout( int i ) {
        ep.setSoTimeout(i);
        setAttribute("soTimeout", "" + i);
    }

    public String getProtocol() {
        return getProperty("protocol");
    }

    public void setProtocol( String k ) {
        setSecure(true);
        setAttribute("protocol", k);
    }

    public boolean getSecure() {
        return secure;
    }

    public void setSecure( boolean b ) {
        ep.setSecure(b);
        secure=b;
        setAttribute("secure", "" + b);
    }

    public int getMaxKeepAliveRequests() {
        return maxKeepAliveRequests;
    }

    /** Set the maximum number of Keep-Alive requests that we will honor.
     */
    public void setMaxKeepAliveRequests(int mkar) {
        maxKeepAliveRequests = mkar;
        setAttribute("maxKeepAliveRequests", "" + mkar);
    }

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

    public int getSocketCloseDelay() {
        return socketCloseDelay;
    }

    public void setSocketCloseDelay( int d ) {
        socketCloseDelay=d;
        setAttribute("socketCloseDelay", "" + d);
    }

    public void setServer( String server ) {
        this.server = server;
    }

    public String getServer() {
        return server;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout( int timeouts ) {
        timeout = timeouts;
        setAttribute("timeout", "" + timeouts);
    }

    public void setProcessorCache(int processorCache) {
        this.processorCache = processorCache;
    }

    public void setOomParachute(int oomParachute) {
        ep.setOomParachute(oomParachute);
        setAttribute("oomParachute",oomParachute);
    }

    // --------------------  SSL related properties --------------------

    public String getKeystoreFile() { return ep.getKeystoreFile();}
    public void setKeystoreFile(String s ) { ep.setKeystoreFile(s);}
    public void setKeystore(String s) { setKeystoreFile(s);}
    public String getKeystore(){ return getKeystoreFile();}
    
    public String getAlgorithm() { return ep.getAlgorithm();}
    public void setAlgorithm(String s ) { ep.setAlgorithm(s);}
    
    public boolean getClientAuth() { return ep.getClientAuth();}
    public void setClientAuth(boolean b ) { ep.setClientAuth(b);}
    
    public String getKeystorePass() { return ep.getKeystorePass();}
    public void setKeystorePass(String s ) { ep.setKeystorePass(s);}
    public void setKeypass(String s) { setKeystorePass(s);}
    public String getKeypass() { return getKeystorePass();}
    
    
    public String getKeystoreType() { return ep.getKeystoreType();}
    public void setKeystoreType(String s ) { ep.setKeystoreType(s);}
    
    public String getSslProtocol() { return ep.getSslProtocol();}
    public void setSslProtocol(String s) { ep.setSslProtocol(s);}
    
    public String getCiphers() { return ep.getCiphers();}
    public void setCiphers(String s) { ep.setCiphers(s);}
    
    public boolean getSSLEnabled() { return ep.isSSLEnabled(); }
    public void setSSLEnabled(boolean SSLEnabled) { ep.setSSLEnabled(SSLEnabled); }
    
    

    // --------------------  Connection handler --------------------

    static class Http11ConnectionHandler implements Handler {

        protected Http11NioProtocol proto;
        protected static int count = 0;
        protected RequestGroupInfo global = new RequestGroupInfo();

        protected ConcurrentHashMap<NioChannel, Http11NioProcessor> connections =
            new ConcurrentHashMap<NioChannel, Http11NioProcessor>();
        protected ConcurrentLinkedQueue<Http11NioProcessor> recycledProcessors = new ConcurrentLinkedQueue<Http11NioProcessor>() {
            protected AtomicInteger size = new AtomicInteger(0);
            public boolean offer(Http11NioProcessor processor) {
                boolean offer = proto.processorCache==-1?true:size.get() < proto.processorCache;
                //avoid over growing our cache or add after we have stopped
                boolean result = false;
                if ( offer ) {
                    result = super.offer(processor);
                    if ( result ) {
                        size.incrementAndGet();
                    }
                }
                if (!result) deregister(processor);
                return result;
            }
            
            public Http11NioProcessor poll() {
                Http11NioProcessor result = super.poll();
                if ( result != null ) {
                    size.decrementAndGet();
                }
                return result;
            }
            
            public void clear() {
                Http11NioProcessor next = poll();
                while ( next != null ) {
                    deregister(next);
                    next = poll();
                }
                super.clear();
                size.set(0);
            }
        };

        Http11ConnectionHandler(Http11NioProtocol proto) {
            this.proto = proto;
        }
        
        public void releaseCaches() {
            recycledProcessors.clear();
        }

        public SocketState event(NioChannel socket, SocketStatus status) {
            Http11NioProcessor result = connections.get(socket);

            SocketState state = SocketState.CLOSED; 
            if (result != null) {
                if (log.isDebugEnabled()) log.debug("Http11NioProcessor.error="+result.error);
                // Call the appropriate event
                try {
                    state = result.event(status);
                } catch (java.net.SocketException e) {
                    // SocketExceptions are normal
                    Http11NioProtocol.log.debug
                        (sm.getString
                            ("http11protocol.proto.socketexception.debug"), e);
                } catch (java.io.IOException e) {
                    // IOExceptions are normal
                    Http11NioProtocol.log.debug
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
                    Http11NioProtocol.log.error
                        (sm.getString("http11protocol.proto.error"), e);
                } finally {
                    if (state != SocketState.LONG) {
                        connections.remove(socket);
                        recycledProcessors.offer(result);
                        if (state == SocketState.OPEN) {
                            socket.getPoller().add(socket);
                        }
                    } else {
                        if (log.isDebugEnabled()) log.debug("Keeping processor["+result);
                        //add correct poller events here based on Comet stuff
                        socket.getPoller().add(socket);
                    }
                }
            }
            return state;
        }

        public SocketState process(NioChannel socket) {
            Http11NioProcessor processor = null;
            try {
                if (processor == null) {
                    processor = recycledProcessors.poll();
                }
                if (processor == null) {
                    processor = createProcessor();
                }

                if (processor instanceof ActionHook) {
                    ((ActionHook) processor).action(ActionCode.ACTION_START, null);
                }
                
                
                if (proto.ep.getSecure() && (proto.sslImplementation != null)) {
                    if (socket instanceof SecureNioChannel) {
                        SecureNioChannel ch = (SecureNioChannel)socket;
                        processor.setSslSupport(proto.sslImplementation.getSSLSupport(ch.getSslEngine().getSession()));
                    }else processor.setSslSupport(null);
                } else {
                    processor.setSslSupport(null);
                }


                SocketState state = processor.process(socket);
                if (state == SocketState.LONG) {
                    // Associate the connection with the processor. The next request 
                    // processed by this thread will use either a new or a recycled
                    // processor.
                    if (log.isDebugEnabled()) log.debug("Not recycling ["+processor+"] Comet="+((NioEndpoint.KeyAttachment)socket.getAttachment(false)).getComet());
                    connections.put(socket, processor);
                    socket.getPoller().add(socket);
                } else {
                    recycledProcessors.offer(processor);
                }
                return state;

            } catch (java.net.SocketException e) {
                // SocketExceptions are normal
                Http11NioProtocol.log.debug
                    (sm.getString
                     ("http11protocol.proto.socketexception.debug"), e);
            } catch (java.io.IOException e) {
                // IOExceptions are normal
                Http11NioProtocol.log.debug
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
                Http11NioProtocol.log.error
                    (sm.getString("http11protocol.proto.error"), e);
            }
            recycledProcessors.offer(processor);
            return SocketState.CLOSED;
        }

        public Http11NioProcessor createProcessor() {
            Http11NioProcessor processor = new Http11NioProcessor(
              proto.ep.getSocketProperties().getRxBufSize(),
              proto.ep.getSocketProperties().getTxBufSize(), 
              proto.maxHttpHeaderSize,
              proto.ep);
            processor.setAdapter(proto.adapter);
            processor.setMaxKeepAliveRequests(proto.maxKeepAliveRequests);
            processor.setTimeout(proto.timeout);
            processor.setDisableUploadTimeout(proto.disableUploadTimeout);
            processor.setCompression(proto.compression);
            processor.setCompressionMinSize(proto.compressionMinSize);
            processor.setNoCompressionUserAgents(proto.noCompressionUserAgents);
            processor.setCompressableMimeTypes(proto.compressableMimeTypes);
            processor.setRestrictedUserAgents(proto.restrictedUserAgents);
            processor.setSocketBuffer(proto.socketBuffer);
            processor.setMaxSavePostSize(proto.maxSavePostSize);
            processor.setServer(proto.server);
            register(processor);
            return processor;
        }
        AtomicInteger registerCount = new AtomicInteger(0);
        public void register(Http11NioProcessor processor) {
            if (proto.getDomain() != null) {
                synchronized (this) {
                    try {
                        registerCount.addAndGet(1);
                        if (log.isDebugEnabled()) log.debug("Register ["+processor+"] count="+registerCount.get());
                        RequestInfo rp = processor.getRequest().getRequestProcessor();
                        rp.setGlobalProcessor(global);
                        ObjectName rpName = new ObjectName
                            (proto.getDomain() + ":type=RequestProcessor,worker="
                             + proto.getName() + ",name=HttpRequest" + count++);
                        Registry.getRegistry(null, null).registerComponent(rp, rpName, null);
                        rp.setRpName(rpName);
                    } catch (Exception e) {
                        log.warn("Error registering request");
                    }
                }
            }
        }
    
        public void deregister(Http11NioProcessor processor) {
            if (proto.getDomain() != null) {
                synchronized (this) {
                    try {
                        registerCount.addAndGet(-1);
                        if (log.isDebugEnabled()) log.debug("Deregister ["+processor+"] count="+registerCount.get());
                        RequestInfo rp = processor.getRequest().getRequestProcessor();
                        rp.setGlobalProcessor(null);
                        ObjectName rpName = rp.getRpName();
                        Registry.getRegistry(null, null).unregisterComponent(rpName);
                        rp.setRpName(null);
                    } catch (Exception e) {
                        log.warn("Error unregistering request", e);
                    }
                }
            }
        }

    }
    


    protected static org.apache.juli.logging.Log log
        = org.apache.juli.logging.LogFactory.getLog(Http11NioProtocol.class);

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

    public int getProcessorCache() {
        return processorCache;
    }

    public int getOomParachute() {
        return ep.getOomParachute();
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

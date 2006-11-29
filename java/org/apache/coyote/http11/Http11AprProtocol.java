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
import java.util.concurrent.Executor;

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
public class Http11AprProtocol implements ProtocolHandler, MBeanRegistration
{
    public Http11AprProtocol() {
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

        try {
            ep.init();
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
    protected AprEndpoint ep=new AprEndpoint();
    protected boolean secure;

    protected Hashtable attributes = new Hashtable();

    private int maxKeepAliveRequests=100; // as in Apache HTTPD server
    private int timeout = 300000;   // 5 minutes as in Apache HTTPD server
    private int maxSavePostSize = 4 * 1024;
    private int maxHttpHeaderSize = 8 * 1024;
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

    // *
    public Executor getExecutor() {
        return ep.getExecutor();
    }
    
    // *
    public void setExecutor(Executor executor) {
        ep.setExecutor(executor);
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

    public int getThreadPriority() {
      return ep.getThreadPriority();
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

    public int getFirstReadTimeout() {
        return ep.getFirstReadTimeout();
    }

    public void setFirstReadTimeout( int i ) {
        ep.setFirstReadTimeout(i);
        setAttribute("firstReadTimeout", "" + i);
    }

    public int getPollTime() {
        return ep.getPollTime();
    }

    public void setPollTime( int i ) {
        ep.setPollTime(i);
        setAttribute("pollTime", "" + i);
    }

    public void setPollerSize(int i) {
        ep.setPollerSize(i); 
        setAttribute("pollerSize", "" + i);
    }
    
    public int getPollerSize() {
        return ep.getPollerSize();
    }
    
    public void setSendfileSize(int i) {
        ep.setSendfileSize(i); 
        setAttribute("sendfileSize", "" + i);
    }
    
    public int getSendfileSize() {
        return ep.getSendfileSize();
    }
    
    public boolean getUseSendfile() {
        return ep.getUseSendfile();
    }

    public void setUseSendfile(boolean useSendfile) {
        ep.setUseSendfile(useSendfile);
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
     * The number of seconds Tomcat will wait for a subsequent request
     * before closing the connection.
     */
    public int getKeepAliveTimeout() {
        return ep.getKeepAliveTimeout(); 
    }

    public void setKeepAliveTimeout(int timeout) {
        ep.setKeepAliveTimeout(timeout);
    }

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

    // --------------------  SSL related properties --------------------

    /**
     * SSL engine.
     */
    public boolean isSSLEnabled() { return ep.isSSLEnabled(); }
    public void setSSLEnabled(boolean SSLEnabled) { ep.setSSLEnabled(SSLEnabled); }


    /**
     * SSL protocol.
     */
    public String getSSLProtocol() { return ep.getSSLProtocol(); }
    public void setSSLProtocol(String SSLProtocol) { ep.setSSLProtocol(SSLProtocol); }


    /**
     * SSL password (if a cert is encrypted, and no password has been provided, a callback
     * will ask for a password).
     */
    public String getSSLPassword() { return ep.getSSLPassword(); }
    public void setSSLPassword(String SSLPassword) { ep.setSSLPassword(SSLPassword); }


    /**
     * SSL cipher suite.
     */
    public String getSSLCipherSuite() { return ep.getSSLCipherSuite(); }
    public void setSSLCipherSuite(String SSLCipherSuite) { ep.setSSLCipherSuite(SSLCipherSuite); }


    /**
     * SSL certificate file.
     */
    public String getSSLCertificateFile() { return ep.getSSLCertificateFile(); }
    public void setSSLCertificateFile(String SSLCertificateFile) { ep.setSSLCertificateFile(SSLCertificateFile); }


    /**
     * SSL certificate key file.
     */
    public String getSSLCertificateKeyFile() { return ep.getSSLCertificateKeyFile(); }
    public void setSSLCertificateKeyFile(String SSLCertificateKeyFile) { ep.setSSLCertificateKeyFile(SSLCertificateKeyFile); }


    /**
     * SSL certificate chain file.
     */
    public String getSSLCertificateChainFile() { return ep.getSSLCertificateChainFile(); }
    public void setSSLCertificateChainFile(String SSLCertificateChainFile) { ep.setSSLCertificateChainFile(SSLCertificateChainFile); }


    /**
     * SSL CA certificate path.
     */
    public String getSSLCACertificatePath() { return ep.getSSLCACertificatePath(); }
    public void setSSLCACertificatePath(String SSLCACertificatePath) { ep.setSSLCACertificatePath(SSLCACertificatePath); }


    /**
     * SSL CA certificate file.
     */
    public String getSSLCACertificateFile() { return ep.getSSLCACertificateFile(); }
    public void setSSLCACertificateFile(String SSLCACertificateFile) { ep.setSSLCACertificateFile(SSLCACertificateFile); }


    /**
     * SSL CA revocation path.
     */
    public String getSSLCARevocationPath() { return ep.getSSLCARevocationPath(); }
    public void setSSLCARevocationPath(String SSLCARevocationPath) { ep.setSSLCARevocationPath(SSLCARevocationPath); }


    /**
     * SSL CA revocation file.
     */
    public String getSSLCARevocationFile() { return ep.getSSLCARevocationFile(); }
    public void setSSLCARevocationFile(String SSLCARevocationFile) { ep.setSSLCARevocationFile(SSLCARevocationFile); }


    /**
     * SSL verify client.
     */
    public String getSSLVerifyClient() { return ep.getSSLVerifyClient(); }
    public void setSSLVerifyClient(String SSLVerifyClient) { ep.setSSLVerifyClient(SSLVerifyClient); }


    /**
     * SSL verify depth.
     */
    public int getSSLVerifyDepth() { return ep.getSSLVerifyDepth(); }
    public void setSSLVerifyDepth(int SSLVerifyDepth) { ep.setSSLVerifyDepth(SSLVerifyDepth); }

    // --------------------  Connection handler --------------------

    static class Http11ConnectionHandler implements Handler {
        
        protected Http11AprProtocol proto;
        protected static int count = 0;
        protected RequestGroupInfo global = new RequestGroupInfo();
        
        protected ThreadLocal<Http11AprProcessor> localProcessor = 
            new ThreadLocal<Http11AprProcessor>();
        protected ConcurrentHashMap<Long, Http11AprProcessor> connections =
            new ConcurrentHashMap<Long, Http11AprProcessor>();
        protected java.util.Stack<Http11AprProcessor> recycledProcessors = 
            new java.util.Stack<Http11AprProcessor>();

        Http11ConnectionHandler(Http11AprProtocol proto) {
            this.proto = proto;
        }

        public SocketState event(long socket, SocketStatus status) {
            Http11AprProcessor result = connections.get(socket);
            
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
                        connections.remove(socket);
                        recycledProcessors.push(result);
                    }
                }
            }
            return state;
        }
        
        public SocketState process(long socket) {
            Http11AprProcessor processor = null;
            try {
                processor = (Http11AprProcessor) localProcessor.get();
                if (processor == null) {
                    synchronized (recycledProcessors) {
                        if (!recycledProcessors.isEmpty()) {
                            processor = recycledProcessors.pop();
                            localProcessor.set(processor);
                        }
                    }
                }
                if (processor == null) {
                    processor =
                        new Http11AprProcessor(proto.maxHttpHeaderSize, proto.ep);
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
                    localProcessor.set(processor);
                    if (proto.getDomain() != null) {
                        synchronized (this) {
                            try {
                                RequestInfo rp = processor.getRequest().getRequestProcessor();
                                rp.setGlobalProcessor(global);
                                ObjectName rpName = new ObjectName
                                (proto.getDomain() + ":type=RequestProcessor,worker="
                                        + proto.getName() + ",name=HttpRequest" + count++);
                                Registry.getRegistry(null, null).registerComponent(rp, rpName, null);
                            } catch (Exception e) {
                                log.warn("Error registering request");
                            }
                        }
                    }
                }

                if (processor instanceof ActionHook) {
                    ((ActionHook) processor).action(ActionCode.ACTION_START, null);
                }

                SocketState state = processor.process(socket);
                if (state == SocketState.LONG) {
                    // Associate the connection with the processor. The next request 
                    // processed by this thread will use either a new or a recycled
                    // processor.
                    connections.put(socket, processor);
                    localProcessor.set(null);
                    proto.ep.getCometPoller().add(socket);
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
            return SocketState.CLOSED;
        }
    }

    protected static org.apache.juli.logging.Log log
        = org.apache.juli.logging.LogFactory.getLog(Http11AprProtocol.class);

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

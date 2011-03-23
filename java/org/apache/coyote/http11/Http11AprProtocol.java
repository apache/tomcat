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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.ObjectName;

import org.apache.coyote.RequestGroupInfo;
import org.apache.coyote.RequestInfo;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AprEndpoint;
import org.apache.tomcat.util.net.AprEndpoint.Handler;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;
import org.apache.tomcat.util.res.StringManager;


/**
 * Abstract the protocol implementation, including threading, etc.
 * Processor is single threaded and specific to stream-based protocols,
 * will not fit Jk protocols like JNI.
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 */
public class Http11AprProtocol extends AbstractHttp11Protocol {

    private static final Log log = LogFactory.getLog(Http11AprProtocol.class);


    @Override
    protected Log getLog() { return log; }


    @Override
    protected AbstractEndpoint.Handler getHandler() {
        return cHandler;
    }


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);

    public Http11AprProtocol() {
        endpoint = new AprEndpoint();
        cHandler = new Http11ConnectionHandler(this);
        ((AprEndpoint) endpoint).setHandler(cHandler);
        setSoLinger(Constants.DEFAULT_CONNECTION_LINGER);
        setSoTimeout(Constants.DEFAULT_CONNECTION_TIMEOUT);
        setTcpNoDelay(Constants.DEFAULT_TCP_NO_DELAY);
        setProcessorCache(-1);
    }

    private final Http11ConnectionHandler cHandler;

    public boolean getUseSendfile() { return ((AprEndpoint)endpoint).getUseSendfile(); }
    public void setUseSendfile(boolean useSendfile) { ((AprEndpoint)endpoint).setUseSendfile(useSendfile); }

    public int getPollTime() { return ((AprEndpoint)endpoint).getPollTime(); }
    public void setPollTime(int pollTime) { ((AprEndpoint)endpoint).setPollTime(pollTime); }

    public void setPollerSize(int pollerSize) { ((AprEndpoint)endpoint).setPollerSize(pollerSize); }
    public int getPollerSize() { return ((AprEndpoint)endpoint).getPollerSize(); }

    public void setPollerThreadCount(int pollerThreadCount) { ((AprEndpoint)endpoint).setPollerThreadCount(pollerThreadCount); }
    public int getPollerThreadCount() { return ((AprEndpoint)endpoint).getPollerThreadCount(); }
    
    public int getSendfileSize() { return ((AprEndpoint)endpoint).getSendfileSize(); }
    public void setSendfileSize(int sendfileSize) { ((AprEndpoint)endpoint).setSendfileSize(sendfileSize); }
    
    public void setSendfileThreadCount(int sendfileThreadCount) { ((AprEndpoint)endpoint).setSendfileThreadCount(sendfileThreadCount); }
    public int getSendfileThreadCount() { return ((AprEndpoint)endpoint).getSendfileThreadCount(); }

    public boolean getDeferAccept() { return ((AprEndpoint)endpoint).getDeferAccept(); }
    public void setDeferAccept(boolean deferAccept) { ((AprEndpoint)endpoint).setDeferAccept(deferAccept); }

    // --------------------  SSL related properties --------------------

    /**
     * SSL protocol.
     */
    public String getSSLProtocol() { return ((AprEndpoint)endpoint).getSSLProtocol(); }
    public void setSSLProtocol(String SSLProtocol) { ((AprEndpoint)endpoint).setSSLProtocol(SSLProtocol); }


    /**
     * SSL password (if a cert is encrypted, and no password has been provided, a callback
     * will ask for a password).
     */
    public String getSSLPassword() { return ((AprEndpoint)endpoint).getSSLPassword(); }
    public void setSSLPassword(String SSLPassword) { ((AprEndpoint)endpoint).setSSLPassword(SSLPassword); }


    /**
     * SSL cipher suite.
     */
    public String getSSLCipherSuite() { return ((AprEndpoint)endpoint).getSSLCipherSuite(); }
    public void setSSLCipherSuite(String SSLCipherSuite) { ((AprEndpoint)endpoint).setSSLCipherSuite(SSLCipherSuite); }


    /**
     * SSL certificate file.
     */
    public String getSSLCertificateFile() { return ((AprEndpoint)endpoint).getSSLCertificateFile(); }
    public void setSSLCertificateFile(String SSLCertificateFile) { ((AprEndpoint)endpoint).setSSLCertificateFile(SSLCertificateFile); }


    /**
     * SSL certificate key file.
     */
    public String getSSLCertificateKeyFile() { return ((AprEndpoint)endpoint).getSSLCertificateKeyFile(); }
    public void setSSLCertificateKeyFile(String SSLCertificateKeyFile) { ((AprEndpoint)endpoint).setSSLCertificateKeyFile(SSLCertificateKeyFile); }


    /**
     * SSL certificate chain file.
     */
    public String getSSLCertificateChainFile() { return ((AprEndpoint)endpoint).getSSLCertificateChainFile(); }
    public void setSSLCertificateChainFile(String SSLCertificateChainFile) { ((AprEndpoint)endpoint).setSSLCertificateChainFile(SSLCertificateChainFile); }


    /**
     * SSL CA certificate path.
     */
    public String getSSLCACertificatePath() { return ((AprEndpoint)endpoint).getSSLCACertificatePath(); }
    public void setSSLCACertificatePath(String SSLCACertificatePath) { ((AprEndpoint)endpoint).setSSLCACertificatePath(SSLCACertificatePath); }


    /**
     * SSL CA certificate file.
     */
    public String getSSLCACertificateFile() { return ((AprEndpoint)endpoint).getSSLCACertificateFile(); }
    public void setSSLCACertificateFile(String SSLCACertificateFile) { ((AprEndpoint)endpoint).setSSLCACertificateFile(SSLCACertificateFile); }


    /**
     * SSL CA revocation path.
     */
    public String getSSLCARevocationPath() { return ((AprEndpoint)endpoint).getSSLCARevocationPath(); }
    public void setSSLCARevocationPath(String SSLCARevocationPath) { ((AprEndpoint)endpoint).setSSLCARevocationPath(SSLCARevocationPath); }


    /**
     * SSL CA revocation file.
     */
    public String getSSLCARevocationFile() { return ((AprEndpoint)endpoint).getSSLCARevocationFile(); }
    public void setSSLCARevocationFile(String SSLCARevocationFile) { ((AprEndpoint)endpoint).setSSLCARevocationFile(SSLCARevocationFile); }


    /**
     * SSL verify client.
     */
    public String getSSLVerifyClient() { return ((AprEndpoint)endpoint).getSSLVerifyClient(); }
    public void setSSLVerifyClient(String SSLVerifyClient) { ((AprEndpoint)endpoint).setSSLVerifyClient(SSLVerifyClient); }


    /**
     * SSL verify depth.
     */
    public int getSSLVerifyDepth() { return ((AprEndpoint)endpoint).getSSLVerifyDepth(); }
    public void setSSLVerifyDepth(int SSLVerifyDepth) { ((AprEndpoint)endpoint).setSSLVerifyDepth(SSLVerifyDepth); }
    
    // ----------------------------------------------------- JMX related methods

    @Override
    protected String getNamePrefix() {
        return ("http-apr");
    }


    // --------------------  Connection handler --------------------

    static class Http11ConnectionHandler implements Handler {
        
        protected Http11AprProtocol proto;
        protected AtomicLong registerCount = new AtomicLong(0);
        protected RequestGroupInfo global = new RequestGroupInfo();
        
        protected ConcurrentHashMap<Long, Http11AprProcessor> connections =
            new ConcurrentHashMap<Long, Http11AprProcessor>();

        protected ConcurrentLinkedQueue<Http11AprProcessor> recycledProcessors = 
            new ConcurrentLinkedQueue<Http11AprProcessor>() {
            private static final long serialVersionUID = 1L;
            protected AtomicInteger size = new AtomicInteger(0);
            @Override
            public boolean offer(Http11AprProcessor processor) {
                boolean offer = (proto.getProcessorCache() == -1) ? true : (size.get() < proto.getProcessorCache());
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

        @Override
        public Object getGlobal() {
            return global;
        }
        
        @Override
        public void recycle() {
            recycledProcessors.clear();
        }
        
        @Override
        public SocketState event(SocketWrapper<Long> socket, SocketStatus status) {
            Http11AprProcessor processor = connections.get(socket.getSocket());
            
            SocketState state = SocketState.CLOSED; 
            if (processor != null) {
                if (processor.comet) {
                    // Call the appropriate event
                    try {
                        state = processor.event(status);
                    } catch (java.net.SocketException e) {
                        // SocketExceptions are normal
                        Http11AprProtocol.log.debug(sm.getString(
                                "http11protocol.proto.socketexception.debug"),
                                e);
                    } catch (java.io.IOException e) {
                        // IOExceptions are normal
                        Http11AprProtocol.log.debug(sm.getString(
                                "http11protocol.proto.ioexception.debug"), e);
                    }
                    // Future developers: if you discover any other
                    // rare-but-nonfatal exceptions, catch them here, and log as
                    // above.
                    catch (Throwable e) {
                        ExceptionUtils.handleThrowable(e);
                        // any other exception or error is odd. Here we log it
                        // with "ERROR" level, so it will show up even on
                        // less-than-verbose logs.
                        Http11AprProtocol.log.error(sm.getString(
                                "http11protocol.proto.error"), e);
                    } finally {
                        if (state != SocketState.LONG) {
                            connections.remove(socket.getSocket());
                            socket.setAsync(false);
                            recycledProcessors.offer(processor);
                            if (state == SocketState.OPEN) {
                                ((AprEndpoint)proto.endpoint).getPoller().add(socket.getSocket().longValue());
                            }
                        } else {
                            ((AprEndpoint)proto.endpoint).getCometPoller().add(socket.getSocket().longValue());
                        }
                    }
                } else if (processor.isAsync()) {
                    state = asyncDispatch(socket, status);
                }
            }
            return state;
        }
        
        @Override
        public SocketState process(SocketWrapper<Long> socket) {
            Http11AprProcessor processor = recycledProcessors.poll();
            try {
                if (processor == null) {
                    processor = createProcessor();
                }

                SocketState state = processor.process(socket);
                if (state == SocketState.LONG) {
                    if (processor.isAsync()) {
                        // Check if the post processing is going to change the state
                        state = processor.asyncPostProcess();
                    }
                }
                if (state == SocketState.LONG || state == SocketState.ASYNC_END) {
                    // Need to make socket available for next processing cycle
                    // but no need for the poller
                    connections.put(socket.getSocket(), processor);
                    if (processor.isAsync()) {
                        socket.setAsync(true);
                    } else if (processor.comet) {
                        ((AprEndpoint) proto.endpoint).getCometPoller().add(
                                socket.getSocket().longValue());
                    }
                } else {
                    recycledProcessors.offer(processor);
                }
                return state;

            } catch (java.net.SocketException e) {
                // SocketExceptions are normal
                log.debug(sm.getString(
                        "http11protocol.proto.socketexception.debug"), e);
            } catch (java.io.IOException e) {
                // IOExceptions are normal
                log.debug(sm.getString(
                        "http11protocol.proto.ioexception.debug"), e);
            }
            // Future developers: if you discover any other
            // rare-but-nonfatal exceptions, catch them here, and log as
            // above.
            catch (Throwable e) {
                ExceptionUtils.handleThrowable(e);
                // any other exception or error is odd. Here we log it
                // with "ERROR" level, so it will show up even on
                // less-than-verbose logs.
                Http11AprProtocol.log.error(
                        sm.getString("http11protocol.proto.error"), e);
            }
            recycledProcessors.offer(processor);
            return SocketState.CLOSED;
        }

        @Override
        public SocketState asyncDispatch(SocketWrapper<Long> socket, SocketStatus status) {
            Http11AprProcessor processor = connections.get(socket.getSocket());
            
            SocketState state = SocketState.CLOSED; 
            if (processor != null) {
                // Call the appropriate event
                try {
                    state = processor.asyncDispatch(socket, status);
                // Future developers: if you discover any rare-but-nonfatal
                // exceptions, catch them here, and log as per {@link #event()}
                // above.
                } catch (Throwable e) {
                    ExceptionUtils.handleThrowable(e);
                    // any other exception or error is odd. Here we log it
                    // with "ERROR" level, so it will show up even on
                    // less-than-verbose logs.
                    Http11AprProtocol.log.error
                        (sm.getString("http11protocol.proto.error"), e);
                } finally {
                    if (state == SocketState.LONG && processor.isAsync()) {
                        state = processor.asyncPostProcess();
                    }
                    if (state != SocketState.LONG && state != SocketState.ASYNC_END) {
                        connections.remove(socket.getSocket());
                        socket.setAsync(false);
                        recycledProcessors.offer(processor);
                        if (state == SocketState.OPEN) {
                            ((AprEndpoint)proto.endpoint).getPoller().add(socket.getSocket().longValue());
                        }
                    }
                }
            }
            return state;
        }

        protected Http11AprProcessor createProcessor() {
            Http11AprProcessor processor = new Http11AprProcessor(
                    proto.getMaxHttpHeaderSize(), (AprEndpoint)proto.endpoint,
                    proto.getMaxTrailerSize());
            processor.setAdapter(proto.adapter);
            processor.setMaxKeepAliveRequests(proto.getMaxKeepAliveRequests());
            processor.setConnectionUploadTimeout(
                    proto.getConnectionUploadTimeout());
            processor.setDisableUploadTimeout(proto.getDisableUploadTimeout());
            processor.setCompressionMinSize(proto.getCompressionMinSize());
            processor.setCompression(proto.getCompression());
            processor.setNoCompressionUserAgents(proto.getNoCompressionUserAgents());
            processor.setCompressableMimeTypes(proto.getCompressableMimeTypes());
            processor.setRestrictedUserAgents(proto.getRestrictedUserAgents());
            processor.setSocketBuffer(proto.getSocketBuffer());
            processor.setMaxSavePostSize(proto.getMaxSavePostSize());
            processor.setServer(proto.getServer());
            processor.setClientCertProvider(proto.getClientCertProvider());
            register(processor);
            return processor;
        }
        
        protected void register(Http11AprProcessor processor) {
            if (proto.getDomain() != null) {
                synchronized (this) {
                    try {
                        long count = registerCount.incrementAndGet();
                        final RequestInfo rp = processor.getRequest().getRequestProcessor();
                        rp.setGlobalProcessor(global);
                        final ObjectName rpName = new ObjectName
                            (proto.getDomain() + ":type=RequestProcessor,worker="
                                + proto.getName() + ",name=HttpRequest" + count);
                        if (log.isDebugEnabled()) {
                            log.debug("Register " + rpName);
                        }
                        if (Constants.IS_SECURITY_ENABLED) {
                            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                                @Override
                                public Void run() {
                                    try {
                                        Registry.getRegistry(null, null).registerComponent(rp, rpName, null);
                                    } catch (Exception e) {
                                        log.warn("Error registering request");
                                    }
                                    return null;
                                }
                            });
                        } else {
                            Registry.getRegistry(null, null).registerComponent(rp, rpName, null);
                        }
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
}

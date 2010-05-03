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

import java.net.Socket;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.ObjectName;

import org.apache.coyote.ActionCode;
import org.apache.coyote.RequestGroupInfo;
import org.apache.coyote.RequestInfo;
import org.apache.juli.logging.Log;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.net.JIoEndpoint;
import org.apache.tomcat.util.net.SSLImplementation;
import org.apache.tomcat.util.net.ServerSocketFactory;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.JIoEndpoint.Handler;


/**
 * Abstract the protocol implementation, including threading, etc.
 * Processor is single threaded and specific to stream-based protocols,
 * will not fit Jk protocols like JNI.
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 */
public class Http11Protocol extends AbstractHttp11Protocol {


    private static final org.apache.juli.logging.Log log
        = org.apache.juli.logging.LogFactory.getLog(Http11Protocol.class);
    
    @Override
    public Log getLog() { return log; }


    // ------------------------------------------------------------ Constructor


    public Http11Protocol() {
        endpoint = new JIoEndpoint();
        setSoLinger(Constants.DEFAULT_CONNECTION_LINGER);
        setSoTimeout(Constants.DEFAULT_CONNECTION_TIMEOUT);
        //setServerSoTimeout(Constants.DEFAULT_SERVER_SOCKET_TIMEOUT);
        setTcpNoDelay(Constants.DEFAULT_TCP_NO_DELAY);
        
    }

    
    // ----------------------------------------------------------------- Fields


    protected Http11ConnectionHandler cHandler = new Http11ConnectionHandler(this);


    protected ServerSocketFactory socketFactory = null;
   


    // ----------------------------------------- ProtocolHandler Implementation
    // *







    @Override
    public void init() throws Exception {
        ((JIoEndpoint)endpoint).setName(getName());
        ((JIoEndpoint)endpoint).setHandler(cHandler);

        // Verify the validity of the configured socket factory
        try {
            if (isSSLEnabled()) {
                sslImplementation =
                    SSLImplementation.getInstance(sslImplementationName);
                socketFactory = sslImplementation.getServerSocketFactory();
                ((JIoEndpoint)endpoint).setServerSocketFactory(socketFactory);
            } else if (socketFactoryName != null) {
                socketFactory = (ServerSocketFactory) Class.forName(socketFactoryName).newInstance();
                ((JIoEndpoint)endpoint).setServerSocketFactory(socketFactory);
            }
        } catch (Exception ex) {
            log.error(sm.getString("http11protocol.socketfactory.initerror"),
                      ex);
            throw ex;
        }

        if (socketFactory!=null) {
            Iterator<String> attE = attributes.keySet().iterator();
            while( attE.hasNext() ) {
                String key = attE.next();
                Object v=attributes.get(key);
                socketFactory.setAttribute(key, v);
            }
        }
        
        try {
            endpoint.init();
        } catch (Exception ex) {
            log.error(sm.getString("http11protocol.endpoint.initerror"), ex);
            throw ex;
        }
        if (log.isInfoEnabled())
            log.info(sm.getString("http11protocol.init", getName()));

    }

    @Override
    public void start() throws Exception {
        if (this.domain != null) {
            try {
                tpOname = new ObjectName
                    (domain + ":" + "type=ThreadPool,name=" + getName());
                Registry.getRegistry(null, null)
                    .registerComponent(endpoint, tpOname, null );
            } catch (Exception e) {
                log.error("Can't register endpoint");
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
        if (log.isInfoEnabled())
            log.info(sm.getString("http11protocol.start", getName()));
    }


    @Override
    public void destroy() throws Exception {
        cHandler.recycledProcessors.clear();
        super.destroy();
    }
    // ------------------------------------------------------------- Properties

    

 
    /**
     * Name of the socket factory.
     */
    protected String socketFactoryName = null;
    public String getSocketFactory() { return socketFactoryName; }
    public void setSocketFactory(String valueS) { socketFactoryName = valueS; }
    
    /**
     * Name of the SSL implementation.
     */
    protected String sslImplementationName=null;
    public String getSSLImplementation() { return sslImplementationName; }
    public void setSSLImplementation( String valueS) {
        sslImplementationName = valueS;
        setSecure(true);
    }
    
    // -----------------------------------  Http11ConnectionHandler Inner Class

    protected static class Http11ConnectionHandler implements Handler {

        protected Http11Protocol proto;
        protected AtomicLong registerCount = new AtomicLong(0);
        protected RequestGroupInfo global = new RequestGroupInfo();
        protected ConcurrentHashMap<SocketWrapper<Socket>, Http11Processor> connections =
            new ConcurrentHashMap<SocketWrapper<Socket>, Http11Processor>();

        protected ConcurrentLinkedQueue<Http11Processor> recycledProcessors = 
            new ConcurrentLinkedQueue<Http11Processor>() {
            private static final long serialVersionUID = 1L;
            protected AtomicInteger size = new AtomicInteger(0);
            @Override
            public boolean offer(Http11Processor processor) {
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
            public Http11Processor poll() {
                Http11Processor result = super.poll();
                if ( result != null ) {
                    size.decrementAndGet();
                }
                return result;
            }
            
            @Override
            public void clear() {
                Http11Processor next = poll();
                while ( next != null ) {
                    unregister(next);
                    next = poll();
                }
                super.clear();
                size.set(0);
            }
        };

        Http11ConnectionHandler(Http11Protocol proto) {
            this.proto = proto;
        }

        public SocketState process(SocketWrapper<Socket> socket) {
            return process(socket,SocketStatus.OPEN);
        }

        public SocketState process(SocketWrapper<Socket> socket, SocketStatus status) {
            Http11Processor processor = connections.remove(socket);
            boolean recycle = true;
            try {
                if (processor == null) {
                    processor = recycledProcessors.poll();
                }
                if (processor == null) {
                    processor = createProcessor();
                }
                processor.action(ActionCode.ACTION_START, null);

                if (proto.isSSLEnabled() && (proto.sslImplementation != null)) {
                    processor.setSSLSupport
                        (proto.sslImplementation.getSSLSupport(socket.getSocket()));
                } else {
                    processor.setSSLSupport(null);
                }
                
                SocketState state = socket.isAsync()?processor.asyncDispatch(status):processor.process(socket);
                if (state == SocketState.LONG) {
                    connections.put(socket, processor);
                    socket.setAsync(true);
                    recycle = false;
                } else {
                    connections.remove(socket);
                    socket.setAsync(false);
                }
                return state;
            } catch(java.net.SocketException e) {
                // SocketExceptions are normal
                Http11Protocol.log.debug
                    (sm.getString
                     ("http11protocol.proto.socketexception.debug"), e);
            } catch (java.io.IOException e) {
                // IOExceptions are normal
                Http11Protocol.log.debug
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
                Http11Protocol.log.error
                    (sm.getString("http11protocol.proto.error"), e);
            } finally {
                //       if(proto.adapter != null) proto.adapter.recycle();
                //                processor.recycle();

                if (recycle) {
                    processor.action(ActionCode.ACTION_STOP, null);
                    recycledProcessors.offer(processor);
                }
            }
            return SocketState.CLOSED;
        }
        
        protected Http11Processor createProcessor() {
            Http11Processor processor =
                new Http11Processor(proto.getMaxHttpHeaderSize(), (JIoEndpoint)proto.endpoint);
            processor.setAdapter(proto.adapter);
            processor.setMaxKeepAliveRequests(proto.getMaxKeepAliveRequests());
            processor.setKeepAliveTimeout(proto.getKeepAliveTimeout());
            processor.setTimeout(proto.getTimeout());
            processor.setDisableUploadTimeout(proto.getDisableUploadTimeout());
            processor.setCompressionMinSize(proto.getCompressionMinSize());
            processor.setCompression(proto.getCompression());
            processor.setNoCompressionUserAgents(proto.getNoCompressionUserAgents());
            processor.setCompressableMimeTypes(proto.getCompressableMimeTypes());
            processor.setRestrictedUserAgents(proto.getRestrictedUserAgents());
            processor.setSocketBuffer(proto.getSocketBuffer());
            processor.setMaxSavePostSize(proto.getMaxSavePostSize());
            processor.setServer(proto.getServer());
            register(processor);
            return processor;
        }
        
        protected void register(Http11Processor processor) {
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

        protected void unregister(Http11Processor processor) {
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

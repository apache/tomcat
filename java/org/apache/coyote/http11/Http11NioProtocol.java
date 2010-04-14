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

import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.ObjectName;

import org.apache.coyote.ActionCode;
import org.apache.coyote.RequestGroupInfo;
import org.apache.coyote.RequestInfo;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint;
import org.apache.tomcat.util.net.SecureNioChannel;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.NioEndpoint.Handler;
import org.apache.tomcat.util.net.jsse.JSSEImplementation;


/**
 * Abstract the protocol implementation, including threading, etc.
 * Processor is single threaded and specific to stream-based protocols,
 * will not fit Jk protocols like JNI.
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 * @author Filip Hanik
 */
public class Http11NioProtocol extends AbstractHttp11Protocol {
    
    private static final Log log = LogFactory.getLog(Http11NioProtocol.class);
    
    @Override
    protected Log getLog() { return log; }
    
    public Http11NioProtocol() {
        endpoint=new NioEndpoint();
        cHandler = new Http11ConnectionHandler( this );
        setSoLinger(Constants.DEFAULT_CONNECTION_LINGER);
        setSoTimeout(Constants.DEFAULT_CONNECTION_TIMEOUT);
        //setServerSoTimeout(Constants.DEFAULT_SERVER_SOCKET_TIMEOUT);
        setTcpNoDelay(Constants.DEFAULT_TCP_NO_DELAY);
        
    }



    public NioEndpoint getEndpoint() {
        return ((NioEndpoint)endpoint);
    }
    



    /** Start the protocol
     */
    @Override
    public void init() throws Exception {
        endpoint.setName(getName());
        ((NioEndpoint)endpoint).setHandler(cHandler);
        
        try {
            endpoint.init();
            sslImplementation = new JSSEImplementation();
        } catch (Exception ex) {
            log.error(sm.getString("http11protocol.endpoint.initerror"), ex);
            throw ex;
        }
        if(log.isInfoEnabled())
            log.info(sm.getString("http11protocol.init", getName()));

    }

    @Override
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

 

    // -------------------- Properties--------------------
    

    private int socketCloseDelay=-1;
    
    private Http11ConnectionHandler cHandler;

    // -------------------- Pool setup --------------------

    public void setPollerThreadCount(int count) {
        ((NioEndpoint)endpoint).setPollerThreadCount(count);
    }
    
    public int getPollerThreadCount() {
        return ((NioEndpoint)endpoint).getPollerThreadCount();
    }
    
    public void setSelectorTimeout(long timeout) {
        ((NioEndpoint)endpoint).setSelectorTimeout(timeout);
    }
    
    public long getSelectorTimeout() {
        return ((NioEndpoint)endpoint).getSelectorTimeout();
    }
    // *

    
    public void setAcceptorThreadPriority(int threadPriority) {
        ((NioEndpoint)endpoint).setAcceptorThreadPriority(threadPriority);
      setAttribute("acceptorThreadPriority", "" + threadPriority);
    }

    public void setPollerThreadPriority(int threadPriority) {
        ((NioEndpoint)endpoint).setPollerThreadPriority(threadPriority);
      setAttribute("pollerThreadPriority", "" + threadPriority);
    }

    public int getAcceptorThreadPriority() {
      return ((NioEndpoint)endpoint).getAcceptorThreadPriority();
    }
    
    public int getPollerThreadPriority() {
      return ((NioEndpoint)endpoint).getThreadPriority();
    }
    
    
    public boolean getUseSendfile() {
        return ((NioEndpoint)endpoint).getUseSendfile();
    }

    public void setUseSendfile(boolean useSendfile) {
        ((NioEndpoint)endpoint).setUseSendfile(useSendfile);
    }
    
    // -------------------- Tcp setup --------------------
    public String getProtocol() {
        return getProperty("protocol");
    }

    public void setProtocol( String k ) {
        setSecure(true);
        setAttribute("protocol", k);
    }


    public int getSocketCloseDelay() {
        return socketCloseDelay;
    }

    public void setSocketCloseDelay( int d ) {
        socketCloseDelay=d;
        setAttribute("socketCloseDelay", "" + d);
    }

    public void setOomParachute(int oomParachute) {
        ((NioEndpoint)endpoint).setOomParachute(oomParachute);
        setAttribute("oomParachute", Integer.valueOf(oomParachute));
    }

    // --------------------  SSL related properties --------------------

    
    // --------------------  Connection handler --------------------

    static class Http11ConnectionHandler implements Handler {

        protected Http11NioProtocol proto;
        protected static int count = 0;
        protected RequestGroupInfo global = new RequestGroupInfo();

        protected ConcurrentHashMap<NioChannel, Http11NioProcessor> connections =
            new ConcurrentHashMap<NioChannel, Http11NioProcessor>();
        protected ConcurrentLinkedQueue<Http11NioProcessor> recycledProcessors = new ConcurrentLinkedQueue<Http11NioProcessor>() {
            protected AtomicInteger size = new AtomicInteger(0);
            @Override
            public boolean offer(Http11NioProcessor processor) {
                boolean offer = proto.getProcessorCache()==-1?true:size.get() < proto.getProcessorCache();
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
            
            @Override
            public Http11NioProcessor poll() {
                Http11NioProcessor result = super.poll();
                if ( result != null ) {
                    size.decrementAndGet();
                }
                return result;
            }
            
            @Override
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
        
        public void release(SocketChannel socket) {
            if (log.isDebugEnabled()) 
                log.debug("Iterating through our connections to release a socket channel:"+socket);
            boolean released = false;
            Iterator<java.util.Map.Entry<NioChannel, Http11NioProcessor>> it = connections.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<NioChannel, Http11NioProcessor> entry = it.next();
                if (entry.getKey().getIOChannel()==socket) {
                    it.remove();
                    Http11NioProcessor result = entry.getValue();
                    result.recycle();
                    deregister(result);
                    released = true;
                    break;
                }
            }
            if (log.isDebugEnabled()) 
                log.debug("Done iterating through our connections to release a socket channel:"+socket +" released:"+released);
        }
        
        public void release(NioChannel socket) {
            Http11NioProcessor result = connections.remove(socket);
            if ( result != null ) {
                result.recycle();
                recycledProcessors.offer(result);
            }
        }

        public SocketState event(NioChannel socket, SocketStatus status) {
            Http11NioProcessor result = connections.get(socket);
            NioEndpoint.KeyAttachment att = (NioEndpoint.KeyAttachment)socket.getAttachment(false);
            att.setAsync(false); //no longer check for timeout
            SocketState state = SocketState.CLOSED; 
            if (result != null) {
                if (log.isDebugEnabled()) log.debug("Http11NioProcessor.error="+result.error);
                // Call the appropriate event
                try {
                    if (result.async) {
                        state = result.asyncDispatch(status);
                    } else {
                        state = result.event(status);
                    }
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
                        socket.getPoller().add(socket,att.getCometOps());
                    }
                }
            }
            return state;
        }

        public SocketState process(NioChannel socket) {
            Http11NioProcessor processor = null;
            try {
                processor = connections.remove(socket);
                
                if (processor == null) {
                    processor = recycledProcessors.poll();
                }
                if (processor == null) {
                    processor = createProcessor();
                }

                processor.action(ActionCode.ACTION_START, null);
                
                if (proto.endpoint.isSSLEnabled() && (proto.sslImplementation != null)) {
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
                    //if (log.isDebugEnabled()) log.debug("Not recycling ["+processor+"] Comet="+((NioEndpoint.KeyAttachment)socket.getAttachment(false)).getComet());
                    connections.put(socket, processor);
                    
                    if (processor.comet) {
                        NioEndpoint.KeyAttachment att = (NioEndpoint.KeyAttachment)socket.getAttachment(false);
                        socket.getPoller().add(socket,att.getCometOps());
                    } else if (processor.async) {
                        NioEndpoint.KeyAttachment att = (NioEndpoint.KeyAttachment)socket.getAttachment(false);
                        att.setAsync(true);
                    } else {
                        //we should not hold on to the processor objects
                        release(socket);
                        socket.getPoller().add(socket);
                    }
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
              proto.getMaxHttpHeaderSize(),
              (NioEndpoint)proto.endpoint);
            processor.setAdapter(proto.adapter);
            processor.setMaxKeepAliveRequests(proto.getMaxKeepAliveRequests());
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
    



}

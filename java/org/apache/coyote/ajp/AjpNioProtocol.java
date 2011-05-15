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

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
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
import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint;
import org.apache.tomcat.util.net.NioEndpoint.Handler;
import org.apache.tomcat.util.net.SSLImplementation;
import org.apache.tomcat.util.net.SocketStatus;


/**
 * Abstract the protocol implementation, including threading, etc.
 * Processor is single threaded and specific to stream-based protocols,
 * will not fit Jk protocols like JNI.
 */
public class AjpNioProtocol extends AbstractAjpProtocol {
    
    
    private static final Log log = LogFactory.getLog(AjpNioProtocol.class);

    @Override
    protected Log getLog() { return log; }


    @Override
    protected AbstractEndpoint.Handler getHandler() {
        return cHandler;
    }


    // ------------------------------------------------------------ Constructor


    public AjpNioProtocol() {
        endpoint = new NioEndpoint();
        cHandler = new AjpConnectionHandler(this);
        ((NioEndpoint) endpoint).setHandler(cHandler);
        setSoLinger(Constants.DEFAULT_CONNECTION_LINGER);
        setSoTimeout(Constants.DEFAULT_CONNECTION_TIMEOUT);
        setTcpNoDelay(Constants.DEFAULT_TCP_NO_DELAY);
        // AJP does not use Send File
        ((NioEndpoint) endpoint).setUseSendfile(false);
    }

    
    // ----------------------------------------------------- Instance Variables


    /**
     * Connection handler for AJP.
     */
    private AjpConnectionHandler cHandler;


    // --------------------------------------------------------- Public Methods


    // AJP does not use Send File.
    public boolean getUseSendfile() { return false; }


    // ----------------------------------------------------- JMX related methods

    @Override
    protected String getNamePrefix() {
        return ("ajp-nio");
    }


    // --------------------------------------  AjpConnectionHandler Inner Class


    protected static class AjpConnectionHandler implements Handler {

        protected AjpNioProtocol proto;
        protected AtomicLong registerCount = new AtomicLong(0);
        protected RequestGroupInfo global = new RequestGroupInfo();

        protected ConcurrentHashMap<NioChannel, AjpNioProcessor> connections =
            new ConcurrentHashMap<NioChannel, AjpNioProcessor>();

        protected ConcurrentLinkedQueue<AjpNioProcessor> recycledProcessors = 
            new ConcurrentLinkedQueue<AjpNioProcessor>() {
            private static final long serialVersionUID = 1L;
            protected AtomicInteger size = new AtomicInteger(0);
            @Override
            public boolean offer(AjpNioProcessor processor) {
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
            public AjpNioProcessor poll() {
                AjpNioProcessor result = super.poll();
                if ( result != null ) {
                    size.decrementAndGet();
                }
                return result;
            }
            
            @Override
            public void clear() {
                AjpNioProcessor next = poll();
                while ( next != null ) {
                    unregister(next);
                    next = poll();
                }
                super.clear();
                size.set(0);
            }
        };

        public AjpConnectionHandler(AjpNioProtocol proto) {
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
        public SSLImplementation getSslImplementation() {
            // AJP does not support SSL
            return null;
        }

        @Override
        public void release(SocketChannel socket) {
            if (log.isDebugEnabled()) 
                log.debug("Iterating through our connections to release a socket channel:"+socket);
            boolean released = false;
            Iterator<java.util.Map.Entry<NioChannel, AjpNioProcessor>> it = connections.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<NioChannel, AjpNioProcessor> entry = it.next();
                if (entry.getKey().getIOChannel()==socket) {
                    it.remove();
                    AjpNioProcessor result = entry.getValue();
                    result.recycle();
                    unregister(result);
                    released = true;
                    break;
                }
            }
            if (log.isDebugEnabled()) 
                log.debug("Done iterating through our connections to release a socket channel:"+socket +" released:"+released);
        }
        
        /**
         * Use this only if the processor is not available, otherwise use
         * {@link #release(NioChannel, Http11NioProcessor).
         */
        @Override
        public void release(NioChannel socket) {
            AjpNioProcessor processor = connections.remove(socket);
            if (processor != null) {
                processor.recycle();
                recycledProcessors.offer(processor);
            }
        }


        public void release(NioChannel socket, AjpNioProcessor processor) {
            connections.remove(socket);
            processor.recycle();
            recycledProcessors.offer(processor);
        }

        // FIXME: Support for this could be added in AJP as well
        @Override
        public SocketState event(NioChannel socket, SocketStatus status) {
            AjpNioProcessor processor = connections.get(socket);
            NioEndpoint.KeyAttachment att = (NioEndpoint.KeyAttachment)socket.getAttachment(false);
            att.setAsync(false); //no longer check for timeout
            SocketState state = SocketState.CLOSED; 
            if (processor != null) {
                try {
                    state = processor.asyncDispatch(status);
                }
                // Future developers: if you discover any other
                // rare-but-nonfatal exceptions, catch them here, and log as
                // above.
                catch (Throwable e) {
                    ExceptionUtils.handleThrowable(e);
                    // any other exception or error is odd. Here we log it
                    // with "ERROR" level, so it will show up even on
                    // less-than-verbose logs.
                    AjpNioProtocol.log.error
                        (sm.getString("http11protocol.proto.error"), e);
                } finally {
                    if (processor.isAsync()) {
                        state = processor.asyncPostProcess();
                    }
                    if (state == SocketState.OPEN || state == SocketState.CLOSED) {
                        release(socket, processor);
                        if (state == SocketState.OPEN) {
                            socket.getPoller().add(socket);
                        }
                    } else if (state == SocketState.LONG) {
                        if (processor.isAsync()) {
                            att.setAsync(true); // Re-enable timeouts
                        } else {
                            // Comet
                            if (log.isDebugEnabled()) log.debug("Keeping processor["+processor);
                            // May receive more data from client
                            SelectionKey key = socket.getIOChannel().keyFor(socket.getPoller().getSelector());
                            key.interestOps(SelectionKey.OP_READ);
                            att.interestOps(SelectionKey.OP_READ);
                        }
                    } else {
                        // state == SocketState.ASYNC_END
                        // No further work required
                    }
                }
            }
            return state;
        }
        
        @Override
        public SocketState process(NioChannel socket) {
            AjpNioProcessor processor = recycledProcessors.poll();
            try {
                if (processor == null) {
                    processor = createProcessor();
                }

                SocketState state = processor.process(socket);
                if (state == SocketState.LONG) {
                    // Check if the post processing is going to change the state
                    state = processor.asyncPostProcess();
                }
                if (state == SocketState.LONG || state == SocketState.ASYNC_END) {
                    // Need to make socket available for next processing cycle
                    // but no need for the poller
                    connections.put(socket, processor);
                    NioEndpoint.KeyAttachment att =
                            (NioEndpoint.KeyAttachment)socket.getAttachment(false);
                    att.setAsync(true);
                } else {
                    processor.recycle();
                    recycledProcessors.offer(processor);
                }
                return state;

            } catch(java.net.SocketException e) {
                // SocketExceptions are normal
                log.debug(sm.getString(
                        "ajpprotocol.proto.socketexception.debug"), e);
            } catch (java.io.IOException e) {
                // IOExceptions are normal
                log.debug(sm.getString(
                        "ajpprotocol.proto.ioexception.debug"), e);
            }
            // Future developers: if you discover any other
            // rare-but-nonfatal exceptions, catch them here, and log as
            // above.
            catch (Throwable e) {
                ExceptionUtils.handleThrowable(e);
                // any other exception or error is odd. Here we log it
                // with "ERROR" level, so it will show up even on
                // less-than-verbose logs.
                log.error(sm.getString("ajpprotocol.proto.error"), e);
            }
            processor.recycle();
            recycledProcessors.offer(processor);
            return SocketState.CLOSED;
        }

        protected AjpNioProcessor createProcessor() {
            AjpNioProcessor processor = new AjpNioProcessor(proto.packetSize, (NioEndpoint)proto.endpoint);
            processor.setAdapter(proto.adapter);
            processor.setTomcatAuthentication(proto.tomcatAuthentication);
            processor.setRequiredSecret(proto.requiredSecret);
            processor.setClientCertProvider(proto.getClientCertProvider());
            register(processor);
            return processor;
        }
        
        protected void register(AjpNioProcessor processor) {
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

        protected void unregister(AjpNioProcessor processor) {
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

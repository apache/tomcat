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

import java.net.Socket;
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
import org.apache.tomcat.util.net.JIoEndpoint;
import org.apache.tomcat.util.net.JIoEndpoint.Handler;
import org.apache.tomcat.util.net.SSLImplementation;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;


/**
 * Abstract the protocol implementation, including threading, etc.
 * Processor is single threaded and specific to stream-based protocols,
 * will not fit Jk protocols like JNI.
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 */
public class AjpProtocol extends AbstractAjpProtocol {
    
    
    private static final Log log = LogFactory.getLog(AjpProtocol.class);

    @Override
    protected Log getLog() { return log; }


    @Override
    protected AbstractEndpoint.Handler getHandler() {
        return cHandler;
    }


    // ------------------------------------------------------------ Constructor


    public AjpProtocol() {
        endpoint = new JIoEndpoint();
        cHandler = new AjpConnectionHandler(this);
        ((JIoEndpoint) endpoint).setHandler(cHandler);
        setSoLinger(Constants.DEFAULT_CONNECTION_LINGER);
        setSoTimeout(Constants.DEFAULT_CONNECTION_TIMEOUT);
        setTcpNoDelay(Constants.DEFAULT_TCP_NO_DELAY);
    }

    
    // ----------------------------------------------------- Instance Variables

    
    /**
     * Connection handler for AJP.
     */
    private AjpConnectionHandler cHandler;


    // ----------------------------------------------------- JMX related methods

    @Override
    protected String getNamePrefix() {
        return ("ajp-bio");
    }


    // --------------------------------------  AjpConnectionHandler Inner Class


    protected static class AjpConnectionHandler implements Handler {

        protected AjpProtocol proto;
        protected AtomicLong registerCount = new AtomicLong(0);
        protected RequestGroupInfo global = new RequestGroupInfo();

        protected ConcurrentHashMap<SocketWrapper<Socket>, AjpProcessor> connections =
            new ConcurrentHashMap<SocketWrapper<Socket>, AjpProcessor>();

        protected ConcurrentLinkedQueue<AjpProcessor> recycledProcessors = 
            new ConcurrentLinkedQueue<AjpProcessor>() {
            private static final long serialVersionUID = 1L;
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
        
        @Override
        public Object getGlobal() {
            return global;
        }

        @Override
        public SSLImplementation getSslImplementation() {
            // AJP does not support SSL
            return null;
        }

        @Override
        public void recycle() {
            recycledProcessors.clear();
        }
        
        @Override
        public SocketState process(SocketWrapper<Socket> socket) {
            return process(socket,SocketStatus.OPEN);
        }

        @Override
        public SocketState process(SocketWrapper<Socket> socket, SocketStatus status) {
            AjpProcessor processor = connections.remove(socket);
            try {
                if (processor == null) {
                    processor = recycledProcessors.poll();
                }
                if (processor == null) {
                    processor = createProcessor();
                }

                SocketState state = socket.isAsync()?processor.asyncDispatch(status):processor.process(socket);
                if (state == SocketState.LONG) {
                    connections.put(socket, processor);
                    socket.setAsync(true);
                    // longPoll may change socket state (e.g. to trigger a
                    // complete or dispatch)
                    return processor.asyncPostProcess();
                } else {
                    socket.setAsync(false);
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
            recycledProcessors.offer(processor);
            return SocketState.CLOSED;
        }

        protected AjpProcessor createProcessor() {
            AjpProcessor processor = new AjpProcessor(proto.packetSize, (JIoEndpoint)proto.endpoint);
            processor.setAdapter(proto.adapter);
            processor.setTomcatAuthentication(proto.tomcatAuthentication);
            processor.setRequiredSecret(proto.requiredSecret);
            processor.setKeepAliveTimeout(proto.getKeepAliveTimeout());
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

}

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

import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.coyote.AbstractProtocol;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint;
import org.apache.tomcat.util.net.NioEndpoint.Handler;
import org.apache.tomcat.util.net.SSLImplementation;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;


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


    // ----------------------------------------------------- JMX related methods

    @Override
    protected String getNamePrefix() {
        return ("ajp-nio");
    }


    // --------------------------------------  AjpConnectionHandler Inner Class


    protected static class AjpConnectionHandler
            extends AbstractConnectionHandler implements Handler {

        protected AjpNioProtocol proto;

        protected ConcurrentHashMap<NioChannel, AjpNioProcessor> connections =
            new ConcurrentHashMap<NioChannel, AjpNioProcessor>();

        protected RecycledProcessors<AjpNioProcessor> recycledProcessors =
            new RecycledProcessors<AjpNioProcessor>(this);

        public AjpConnectionHandler(AjpNioProtocol proto) {
            this.proto = proto;
        }

        @Override
        protected AbstractProtocol getProtocol() {
            return proto;
        }

        @Override
        protected Log getLog() {
            return log;
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
         * {@link #release(NioChannel, AjpNioProcessor)}.
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

        @Override
        public SocketState process(SocketWrapper<NioChannel> socketWrapper,
                SocketStatus status) {
            NioChannel socket = socketWrapper.getSocket();
            AjpNioProcessor processor = connections.remove(socket);

            socketWrapper.setAsync(false); //no longer check for timeout

            try {
                if (processor == null) {
                    processor = recycledProcessors.poll();
                }
                if (processor == null) {
                    processor = createProcessor();
                }

                SocketState state = SocketState.CLOSED;
                do {
                    if (processor.isAsync() || state == SocketState.ASYNC_END) {
                        state = processor.asyncDispatch(status);
                    } else {
                        state = processor.process(socket);
                    }

                    if (processor.isAsync()) {
                        state = processor.asyncPostProcess();
                    }
                } while (state == SocketState.ASYNC_END);

                if (state == SocketState.LONG) {
                    // In the middle of processing a request/response. Keep the
                    // socket associated with the processor.
                    connections.put(socket, processor);
                    
                    socketWrapper.setAsync(true);
                } else if (state == SocketState.OPEN){
                    // In keep-alive but between requests. OK to recycle
                    // processor. Continue to poll for the next request.
                    release(socket, processor);
                    socket.getPoller().add(socket);
                } else {
                    // Connection closed. OK to recycle the processor.
                    release(socket, processor);
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
            release(socket, processor);
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
    }
}

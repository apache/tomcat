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

import java.util.concurrent.ConcurrentHashMap;

import org.apache.coyote.AbstractProtocol;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AprEndpoint;
import org.apache.tomcat.util.net.AprEndpoint.Handler;
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
public class AjpAprProtocol extends AbstractAjpProtocol {
    
    
    private static final Log log = LogFactory.getLog(AjpAprProtocol.class);

    @Override
    protected Log getLog() { return log; }


    @Override
    protected AbstractEndpoint.Handler getHandler() {
        return cHandler;
    }


    // ------------------------------------------------------------ Constructor


    public AjpAprProtocol() {
        endpoint = new AprEndpoint();
        cHandler = new AjpConnectionHandler(this);
        ((AprEndpoint) endpoint).setHandler(cHandler);
        setSoLinger(Constants.DEFAULT_CONNECTION_LINGER);
        setSoTimeout(Constants.DEFAULT_CONNECTION_TIMEOUT);
        setTcpNoDelay(Constants.DEFAULT_TCP_NO_DELAY);
        // AJP does not use Send File
        ((AprEndpoint) endpoint).setUseSendfile(false);
    }

    
    // ----------------------------------------------------- Instance Variables


    /**
     * Connection handler for AJP.
     */
    private AjpConnectionHandler cHandler;


    // --------------------------------------------------------- Public Methods


    public int getPollTime() { return ((AprEndpoint)endpoint).getPollTime(); }
    public void setPollTime(int pollTime) { ((AprEndpoint)endpoint).setPollTime(pollTime); }

    // pollerSize is now a synonym for maxConnections
    public void setPollerSize(int pollerSize) { endpoint.setMaxConnections(pollerSize); }
    public int getPollerSize() { return endpoint.getMaxConnections(); }


    // ----------------------------------------------------- JMX related methods

    @Override
    protected String getNamePrefix() {
        return ("ajp-apr");
    }


    // --------------------------------------  AjpConnectionHandler Inner Class


    protected static class AjpConnectionHandler
            extends AbstractConnectionHandler implements Handler {

        protected AjpAprProtocol proto;

        protected ConcurrentHashMap<SocketWrapper<Long>, AjpAprProcessor> connections =
            new ConcurrentHashMap<SocketWrapper<Long>, AjpAprProcessor>();

        protected RecycledProcessors<AjpAprProcessor> recycledProcessors =
            new RecycledProcessors<AjpAprProcessor>(this);

        public AjpConnectionHandler(AjpAprProtocol proto) {
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
        
        // FIXME: Support for this could be added in AJP as well
        @Override
        public SocketState event(SocketWrapper<Long> socket, SocketStatus status) {
            return SocketState.CLOSED;
        }
        
        @Override
        public SocketState process(SocketWrapper<Long> socket) {
            AjpAprProcessor processor = recycledProcessors.poll();
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
                    socket.setAsync(true);
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

        @Override
        public SocketState asyncDispatch(SocketWrapper<Long> socket, SocketStatus status) {

            AjpAprProcessor processor = connections.get(socket);
            
            SocketState state = SocketState.CLOSED; 
            if (processor != null) {
                // Call the appropriate event
                try {
                    state = processor.asyncDispatch(status);
                }
                // Future developers: if you discover any other
                // rare-but-nonfatal exceptions, catch them here, and log as
                // debug.
                catch (Throwable e) {
                    ExceptionUtils.handleThrowable(e);
                    // any other exception or error is odd. Here we log it
                    // with "ERROR" level, so it will show up even on
                    // less-than-verbose logs.
                    AjpAprProtocol.log.error
                        (sm.getString("ajpprotocol.proto.error"), e);
                } finally {
                    if (state == SocketState.LONG && processor.isAsync()) {
                        state = processor.asyncPostProcess();
                    }
                    if (state != SocketState.LONG && state != SocketState.ASYNC_END) {
                        connections.remove(socket);
                        processor.recycle();
                        recycledProcessors.offer(processor);
                        if (state == SocketState.OPEN) {
                            ((AprEndpoint)proto.endpoint).getPoller().add(socket.getSocket().longValue());
                        }
                    }
                }
            }
            return state;
        }
        
        protected AjpAprProcessor createProcessor() {
            AjpAprProcessor processor = new AjpAprProcessor(proto.packetSize, (AprEndpoint)proto.endpoint);
            processor.setAdapter(proto.adapter);
            processor.setTomcatAuthentication(proto.tomcatAuthentication);
            processor.setRequiredSecret(proto.requiredSecret);
            processor.setClientCertProvider(proto.getClientCertProvider());
            register(processor);
            return processor;
        }
    }
}

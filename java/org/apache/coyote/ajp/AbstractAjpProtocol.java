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
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;
import org.apache.tomcat.util.res.StringManager;

public abstract class AbstractAjpProtocol extends AbstractProtocol {
    
    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


    @Override
    protected String getProtocolName() {
        return "Ajp";
    }



    // ------------------------------------------------- AJP specific properties
    // ------------------------------------------ managed in the ProtocolHandler
    
    /**
     * Should authentication be done in the native webserver layer, 
     * or in the Servlet container ?
     */
    protected boolean tomcatAuthentication = true;
    public boolean getTomcatAuthentication() { return tomcatAuthentication; }
    public void setTomcatAuthentication(boolean tomcatAuthentication) {
        this.tomcatAuthentication = tomcatAuthentication;
    }


    /**
     * Required secret.
     */
    protected String requiredSecret = null;
    public void setRequiredSecret(String requiredSecret) {
        this.requiredSecret = requiredSecret;
    }


    /**
     * AJP packet size.
     */
    protected int packetSize = Constants.MAX_PACKET_SIZE;
    public int getPacketSize() { return packetSize; }
    public void setPacketSize(int packetSize) {
        if(packetSize < Constants.MAX_PACKET_SIZE) {
            this.packetSize = Constants.MAX_PACKET_SIZE;
        } else {
            this.packetSize = packetSize;
        }
    }
    
    protected abstract static class AbstractAjpConnectionHandler<S,P extends AbstractAjpProcessor<S>>
            extends AbstractConnectionHandler {

        protected ConcurrentHashMap<SocketWrapper<S>,P> connections =
            new ConcurrentHashMap<SocketWrapper<S>,P>();

        protected RecycledProcessors<P> recycledProcessors =
            new RecycledProcessors<P>(this);
        
        @Override
        public void recycle() {
            recycledProcessors.clear();
        }
        
        public SocketState process(SocketWrapper<S> socket,
                SocketStatus status) {
            P processor = connections.remove(socket);

            socket.setAsync(false);

            try {
                if (processor == null) {
                    processor = recycledProcessors.poll();
                }
                if (processor == null) {
                    processor = createProcessor();
                }

                initSsl(socket, processor);

                SocketState state = SocketState.CLOSED;
                do {
                    if (processor.isAsync() || state == SocketState.ASYNC_END) {
                        state = processor.asyncDispatch(status);
                    } else if (processor.comet) {
                        state = processor.event(status);
                    } else {
                        state = processor.process(socket);
                    }
    
                    if (state != SocketState.CLOSED && processor.isAsync()) {
                        state = processor.asyncPostProcess();
                    }
                } while (state == SocketState.ASYNC_END);

                if (state == SocketState.LONG) {
                    // In the middle of processing a request/response. Keep the
                    // socket associated with the processor. Exact requirements
                    // depend on type of long poll
                    longPoll(socket, processor);
                } else if (state == SocketState.OPEN){
                    // In keep-alive but between requests. OK to recycle
                    // processor. Continue to poll for the next request.
                    release(socket, processor, false, true);
                } else {
                    // Connection closed. OK to recycle the processor.
                    release(socket, processor, true, false);
                }
                return state;
            } catch(java.net.SocketException e) {
                // SocketExceptions are normal
                getLog().debug(sm.getString(
                        "ajpprotocol.proto.socketexception.debug"), e);
            } catch (java.io.IOException e) {
                // IOExceptions are normal
                getLog().debug(sm.getString(
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
                getLog().error(sm.getString("ajpprotocol.proto.error"), e);
            }
            release(socket, processor, true, false);
            return SocketState.CLOSED;
        }
        
        protected abstract P createProcessor();
        @SuppressWarnings("unused")
        protected void initSsl(SocketWrapper<S> socket, P processor) {
            // NOOP for AJP
        }
        protected void longPoll(SocketWrapper<S> socket, P processor) {
            // Same requirements for all AJP connectors
            connections.put(socket, processor);
            socket.setAsync(true);
            
        }
        protected abstract void release(SocketWrapper<S> socket, P processor,
                boolean socketClosing, boolean addToPoller);
    }
}

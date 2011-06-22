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

import java.nio.channels.SelectionKey;
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
import org.apache.tomcat.util.net.NioEndpoint.KeyAttachment;
import org.apache.tomcat.util.net.SSLImplementation;
import org.apache.tomcat.util.net.SecureNioChannel;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;


/**
 * Abstract the protocol implementation, including threading, etc.
 * Processor is single threaded and specific to stream-based protocols,
 * will not fit Jk protocols like JNI.
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 * @author Filip Hanik
 */
public class Http11NioProtocol extends AbstractHttp11JsseProtocol {
    
    private static final Log log = LogFactory.getLog(Http11NioProtocol.class);


    @Override
    protected Log getLog() { return log; }
    

    @Override
    protected AbstractEndpoint.Handler getHandler() {
        return cHandler;
    }


    public Http11NioProtocol() {
        endpoint=new NioEndpoint();
        cHandler = new Http11ConnectionHandler(this);
        ((NioEndpoint) endpoint).setHandler(cHandler);
        setSoLinger(Constants.DEFAULT_CONNECTION_LINGER);
        setSoTimeout(Constants.DEFAULT_CONNECTION_TIMEOUT);
        setTcpNoDelay(Constants.DEFAULT_TCP_NO_DELAY);
    }


    public NioEndpoint getEndpoint() {
        return ((NioEndpoint)endpoint);
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
    }

    public void setPollerThreadPriority(int threadPriority) {
        ((NioEndpoint)endpoint).setPollerThreadPriority(threadPriority);
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
    public int getSocketCloseDelay() {
        return socketCloseDelay;
    }

    public void setSocketCloseDelay( int d ) {
        socketCloseDelay=d;
    }

    public void setOomParachute(int oomParachute) {
        ((NioEndpoint)endpoint).setOomParachute(oomParachute);
    }

    // ----------------------------------------------------- JMX related methods

    @Override
    protected String getNamePrefix() {
        return ("http-nio");
    }


    // --------------------  Connection handler --------------------

    protected static class Http11ConnectionHandler
            extends AbstractConnectionHandler implements Handler {

        protected Http11NioProtocol proto;

        protected ConcurrentHashMap<SocketWrapper<NioChannel>, Http11NioProcessor> connections =
            new ConcurrentHashMap<SocketWrapper<NioChannel>, Http11NioProcessor>();

        protected RecycledProcessors<Http11NioProcessor> recycledProcessors =
            new RecycledProcessors<Http11NioProcessor>(this);

        Http11ConnectionHandler(Http11NioProtocol proto) {
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
        public SSLImplementation getSslImplementation() {
            return proto.sslImplementation;
        }

        @Override
        public void recycle() {
            recycledProcessors.clear();
        }
        
        @Override
        public void release(SocketChannel socket) {
            if (log.isDebugEnabled()) 
                log.debug("Iterating through our connections to release a socket channel:"+socket);
            boolean released = false;
            Iterator<java.util.Map.Entry<SocketWrapper<NioChannel>, Http11NioProcessor>> it = connections.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<SocketWrapper<NioChannel>, Http11NioProcessor> entry = it.next();
                if (entry.getKey().getSocket().getIOChannel()==socket) {
                    it.remove();
                    Http11NioProcessor result = entry.getValue();
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
         * {@link #release(NioChannel, Http11NioProcessor)}.
         */
        @Override
        public void release(SocketWrapper<NioChannel> socket) {
            Http11NioProcessor processor = connections.remove(socket);
            if (processor != null) {
                processor.recycle();
                recycledProcessors.offer(processor);
            }
        }


        public void release(SocketWrapper<NioChannel> socket,
                Http11NioProcessor processor) {
            connections.remove(socket);
            processor.recycle();
            recycledProcessors.offer(processor);
        }


        @Override
        public SocketState process(SocketWrapper<NioChannel> socket,
                SocketStatus status) {
            Http11NioProcessor processor = connections.remove(socket);

            socket.setAsync(false); //no longer check for timeout

            try {
                if (processor == null) {
                    processor = recycledProcessors.poll();
                }
                if (processor == null) {
                    processor = createProcessor();
                }

                if (proto.isSSLEnabled() &&
                        (proto.sslImplementation != null)
                        && (socket.getSocket() instanceof SecureNioChannel)) {
                    SecureNioChannel ch = (SecureNioChannel)socket.getSocket();
                    processor.setSslSupport(
                            proto.sslImplementation.getSSLSupport(
                                    ch.getSslEngine().getSession()));
                } else {
                    processor.setSslSupport(null);
                }

                SocketState state = SocketState.CLOSED;
                do {
                    if (processor.isAsync() || state == SocketState.ASYNC_END) {
                        state = processor.asyncDispatch(status);
                    } else if (processor.comet) {
                        state = processor.event(status);
                    } else {
                        state = processor.process(socket.getSocket());
                    }

                    if (processor.isAsync()) {
                        state = processor.asyncPostProcess();
                    }
                } while (state == SocketState.ASYNC_END);

                if (state == SocketState.LONG) {
                    // In the middle of processing a request/response. Keep the
                    // socket associated with the processor.
                    connections.put(socket, processor);
                    
                    if (processor.isAsync()) {
                        socket.setAsync(true);
                    } else {
                        // Either:
                        //  - this is comet request
                        //  - the request line/headers have not been completely
                        //    read
                        SelectionKey key = socket.getSocket().getIOChannel().keyFor(
                                socket.getSocket().getPoller().getSelector());
                        key.interestOps(SelectionKey.OP_READ);
                        ((KeyAttachment) socket).interestOps(
                                SelectionKey.OP_READ);
                    }
                } else if (state == SocketState.OPEN){
                    // In keep-alive but between requests. OK to recycle
                    // processor. Continue to poll for the next request.
                    release(socket, processor);
                    socket.getSocket().getPoller().add(socket.getSocket());
                } else {
                    // Connection closed. OK to recycle the processor.
                    release(socket, processor);
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
                log.error(sm.getString("http11protocol.proto.error"), e);
            }
            release(socket, processor);
            return SocketState.CLOSED;
        }

        public Http11NioProcessor createProcessor() {
            Http11NioProcessor processor = new Http11NioProcessor(
                    proto.getMaxHttpHeaderSize(), (NioEndpoint)proto.endpoint,
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
            register(processor);
            return processor;
        }
    }
}

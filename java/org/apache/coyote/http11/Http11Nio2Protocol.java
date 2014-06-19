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

import java.io.IOException;
import java.nio.channels.ReadPendingException;

import javax.net.ssl.SSLEngine;
import javax.servlet.http.HttpUpgradeHandler;

import org.apache.coyote.AbstractProtocol;
import org.apache.coyote.Processor;
import org.apache.coyote.http11.upgrade.Nio2Processor;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.Nio2Channel;
import org.apache.tomcat.util.net.Nio2Endpoint;
import org.apache.tomcat.util.net.Nio2Endpoint.Handler;
import org.apache.tomcat.util.net.Nio2Endpoint.Nio2SocketWrapper;
import org.apache.tomcat.util.net.SSLImplementation;
import org.apache.tomcat.util.net.SecureNio2Channel;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;


/**
 * HTTP/1.1 protocol implementation using NIO2.
 */
public class Http11Nio2Protocol extends AbstractHttp11JsseProtocol<Nio2Channel> {

    private static final Log log = LogFactory.getLog(Http11Nio2Protocol.class);


    @Override
    protected Log getLog() { return log; }


    @Override
    protected AbstractEndpoint.Handler getHandler() {
        return cHandler;
    }


    public Http11Nio2Protocol() {
        endpoint=new Nio2Endpoint();
        cHandler = new Http11ConnectionHandler(this);
        ((Nio2Endpoint) endpoint).setHandler(cHandler);
        setSoLinger(Constants.DEFAULT_CONNECTION_LINGER);
        setSoTimeout(Constants.DEFAULT_CONNECTION_TIMEOUT);
        setTcpNoDelay(Constants.DEFAULT_TCP_NO_DELAY);
    }


    public Nio2Endpoint getEndpoint() {
        return ((Nio2Endpoint)endpoint);
    }

    @Override
    public void start() throws Exception {
        super.start();
        if (npnHandler != null) {
            npnHandler.init(getEndpoint(), 0, getAdapter());
        }
    }

    // -------------------- Properties--------------------

    private final Http11ConnectionHandler cHandler;

    // -------------------- Pool setup --------------------

    public void setAcceptorThreadPriority(int threadPriority) {
        ((Nio2Endpoint)endpoint).setAcceptorThreadPriority(threadPriority);
    }

    public void setPollerThreadPriority(int threadPriority) {
        ((Nio2Endpoint)endpoint).setPollerThreadPriority(threadPriority);
    }

    public int getAcceptorThreadPriority() {
      return ((Nio2Endpoint)endpoint).getAcceptorThreadPriority();
    }

    public int getPollerThreadPriority() {
      return ((Nio2Endpoint)endpoint).getThreadPriority();
    }

    public boolean getUseSendfile() {
        return endpoint.getUseSendfile();
    }

    public void setUseSendfile(boolean useSendfile) {
        ((Nio2Endpoint)endpoint).setUseSendfile(useSendfile);
    }

    // -------------------- Tcp setup --------------------

    public void setOomParachute(int oomParachute) {
        ((Nio2Endpoint)endpoint).setOomParachute(oomParachute);
    }

    // ----------------------------------------------------- JMX related methods

    @Override
    protected String getNamePrefix() {
        return ("http-nio2");
    }


    // --------------------  Connection handler --------------------

    protected static class Http11ConnectionHandler
            extends AbstractConnectionHandler<Nio2Channel,Http11Nio2Processor>
            implements Handler {

        protected Http11Nio2Protocol proto;

        Http11ConnectionHandler(Http11Nio2Protocol proto) {
            this.proto = proto;
        }

        @Override
        protected AbstractProtocol<Nio2Channel> getProtocol() {
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

        /**
         * Expected to be used by the Poller to release resources on socket
         * close, errors etc.
         */
        @Override
        public void release(SocketWrapper<Nio2Channel> socket) {
            Processor<Nio2Channel> processor =
                connections.remove(socket.getSocket());
            if (processor != null) {
                processor.recycle(true);
                recycledProcessors.push(processor);
            }
        }

        @Override
        public SocketState process(SocketWrapper<Nio2Channel> socket,
                SocketStatus status) {
            if (proto.npnHandler != null) {
                SocketState ss = proto.npnHandler.process(socket, status);
                if (ss != SocketState.OPEN) {
                    return ss;
                }
            }
            return super.process(socket, status);
        }


        /**
         * Expected to be used by the handler once the processor is no longer
         * required.
         *
         * @param socket
         * @param processor
         * @param isSocketClosing   Not used in HTTP
         * @param addToPoller
         */
        @Override
        public void release(SocketWrapper<Nio2Channel> socket,
                Processor<Nio2Channel> processor, boolean isSocketClosing,
                boolean addToPoller) {
            processor.recycle(isSocketClosing);
            recycledProcessors.push(processor);
            if (socket.isAsync()) {
                ((Nio2Endpoint) proto.endpoint).removeTimeout(socket);
            }
            if (addToPoller) {
                ((Nio2Endpoint) proto.endpoint).awaitBytes(socket);
            }
        }


        @Override
        protected void initSsl(SocketWrapper<Nio2Channel> socket,
                Processor<Nio2Channel> processor) {
            if (proto.isSSLEnabled() &&
                    (proto.sslImplementation != null)
                    && (socket.getSocket() instanceof SecureNio2Channel)) {
                SecureNio2Channel ch = (SecureNio2Channel)socket.getSocket();
                processor.setSslSupport(
                        proto.sslImplementation.getSSLSupport(
                                ch.getSslEngine().getSession()));
            } else {
                processor.setSslSupport(null);
            }

        }

        @Override
        protected void longPoll(SocketWrapper<Nio2Channel> socket,
                Processor<Nio2Channel> processor) {
            if (processor.isAsync()) {
                socket.setAsync(true);
                ((Nio2Endpoint) proto.endpoint).addTimeout(socket);
            } else if (processor.isUpgrade()) {
                if (((Nio2SocketWrapper) socket).isUpgradeInit()) {
                    try {
                        ((Nio2Endpoint) proto.endpoint).awaitBytes(socket);
                    } catch (ReadPendingException e) {
                        // Ignore, the initial state after upgrade is
                        // impossible to predict, and a read must be pending
                        // to get a first notification
                    }
                }
            } else {
                // Either:
                //  - this is comet request
                //  - this is an upgraded connection
                //  - the request line/headers have not been completely
                //    read
                // The completion handlers should be in place,
                // so nothing to do here
            }
        }

        @Override
        public Http11Nio2Processor createProcessor() {
            Http11Nio2Processor processor = new Http11Nio2Processor(
                    proto.getMaxHttpHeaderSize(), (Nio2Endpoint) proto.endpoint,
                    proto.getMaxTrailerSize(), proto.getMaxExtensionSize(),
                    proto.getMaxSwallowSize());
            processor.setAdapter(proto.getAdapter());
            processor.setMaxKeepAliveRequests(proto.getMaxKeepAliveRequests());
            processor.setKeepAliveTimeout(proto.getKeepAliveTimeout());
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

        @Override
        protected Processor<Nio2Channel> createUpgradeProcessor(
                SocketWrapper<Nio2Channel> socket,
                HttpUpgradeHandler httpUpgradeProcessor)
                throws IOException {
            return new Nio2Processor(proto.endpoint, socket, httpUpgradeProcessor,
                    proto.getUpgradeAsyncWriteBufferSize());
        }

        @Override
        public void onCreateSSLEngine(SSLEngine engine) {
            if (proto.npnHandler != null) {
                proto.npnHandler.onCreateEngine(engine);
            }
        }

        @Override
        public void closeAll() {
            for (Nio2Channel channel : connections.keySet()) {
                ((Nio2Endpoint) proto.endpoint).closeSocket(channel.getSocket(), SocketStatus.STOP);
            }
        }
    }
}

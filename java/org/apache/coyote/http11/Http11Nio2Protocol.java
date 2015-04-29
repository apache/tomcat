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

import org.apache.coyote.Processor;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.Nio2Channel;
import org.apache.tomcat.util.net.Nio2Endpoint;
import org.apache.tomcat.util.net.Nio2Endpoint.Handler;
import org.apache.tomcat.util.net.Nio2Endpoint.Nio2SocketWrapper;
import org.apache.tomcat.util.net.SocketWrapperBase;


/**
 * HTTP/1.1 protocol implementation using NIO2.
 */
public class Http11Nio2Protocol extends AbstractHttp11JsseProtocol<Nio2Channel> {

    private static final Log log = LogFactory.getLog(Http11Nio2Protocol.class);


    public Http11Nio2Protocol() {
        super(new Nio2Endpoint());
        Http11ConnectionHandler cHandler = new Http11ConnectionHandler(this);
        setHandler(cHandler);
        ((Nio2Endpoint) getEndpoint()).setHandler(cHandler);
    }


    @Override
    protected Log getLog() { return log; }


    // -------------------- Pool setup --------------------

    public void setAcceptorThreadPriority(int threadPriority) {
        ((Nio2Endpoint)getEndpoint()).setAcceptorThreadPriority(threadPriority);
    }

    public int getAcceptorThreadPriority() {
      return ((Nio2Endpoint)getEndpoint()).getAcceptorThreadPriority();
    }


    // -------------------- Tcp setup --------------------

    public void setOomParachute(int oomParachute) {
        ((Nio2Endpoint)getEndpoint()).setOomParachute(oomParachute);
    }


    // ----------------------------------------------------- JMX related methods

    @Override
    protected String getNamePrefix() {
        if (isSSLEnabled()) {
            return ("https-nio2");
        } else {
            return ("http-nio2");
        }
    }


    // --------------------  Connection handler --------------------

    protected static class Http11ConnectionHandler
            extends AbstractHttp11ConnectionHandler<Nio2Channel>
            implements Handler {

        Http11ConnectionHandler(Http11Nio2Protocol proto) {
            super(proto);
        }

        @Override
        protected Log getLog() {
            return log;
        }

        /**
         * Expected to be used by the Poller to release resources on socket
         * close, errors etc.
         */
        @Override
        public void release(SocketWrapperBase<Nio2Channel> socket) {
            Nio2Channel channel = socket.getSocket();
            if (channel != null) {
                Processor processor = connections.remove(channel);
                if (processor != null) {
                    processor.recycle();
                    recycledProcessors.push(processor);
                }
            }
        }


        @Override
        public void release(SocketWrapperBase<Nio2Channel> socket,
                Processor processor, boolean addToPoller) {
            processor.recycle();
            recycledProcessors.push(processor);
            if (socket.isAsync()) {
                ((Nio2Endpoint) getProtocol().getEndpoint()).removeTimeout(socket);
            }
            if (addToPoller) {
                ((Nio2SocketWrapper) socket).awaitBytes();
            }
        }


        @Override
        protected void longPoll(SocketWrapperBase<Nio2Channel> socket, Processor processor) {
            if (processor.isAsync()) {
                socket.setAsync(true);
                ((Nio2Endpoint) getProtocol().getEndpoint()).addTimeout(socket);
            } else {
                // Either:
                //  - this is an upgraded connection
                //  - the request line/headers have not been completely
                //    read
                // The completion handlers should be in place,
                // so nothing to do here
            }
        }

        @Override
        public void closeAll() {
            for (Nio2Channel channel : connections.keySet()) {
                ((Nio2Endpoint) getProtocol().getEndpoint()).closeSocket(channel.getSocket());
            }
        }
    }
}

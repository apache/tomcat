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

import javax.net.ssl.SSLEngine;

import org.apache.coyote.AbstractProtocol;
import org.apache.coyote.Processor;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.Nio2Channel;
import org.apache.tomcat.util.net.Nio2Endpoint;
import org.apache.tomcat.util.net.Nio2Endpoint.Handler;
import org.apache.tomcat.util.net.SSLImplementation;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;


/**
 * Abstract the protocol implementation, including threading, etc.
 * Processor is single threaded and specific to stream-based protocols,
 * will not fit Jk protocols like JNI.
 */
public class AjpNio2Protocol extends AbstractAjpProtocol<Nio2Channel> {


    private static final Log log = LogFactory.getLog(AjpNio2Protocol.class);

    @Override
    protected Log getLog() { return log; }


    @Override
    protected AbstractEndpoint.Handler getHandler() {
        return cHandler;
    }


    // ------------------------------------------------------------ Constructor


    public AjpNio2Protocol() {
        endpoint = new Nio2Endpoint();
        cHandler = new AjpConnectionHandler(this);
        ((Nio2Endpoint) endpoint).setHandler(cHandler);
        setSoLinger(Constants.DEFAULT_CONNECTION_LINGER);
        setSoTimeout(Constants.DEFAULT_CONNECTION_TIMEOUT);
        setTcpNoDelay(Constants.DEFAULT_TCP_NO_DELAY);
        // AJP does not use Send File
        ((Nio2Endpoint) endpoint).setUseSendfile(false);
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Connection handler for AJP.
     */
    private final AjpConnectionHandler cHandler;


    // ----------------------------------------------------- JMX related methods

    @Override
    protected String getNamePrefix() {
        return ("ajp-nio2");
    }


    // --------------------------------------  AjpConnectionHandler Inner Class


    protected static class AjpConnectionHandler
            extends AbstractAjpConnectionHandler<Nio2Channel, AjpNio2Processor>
            implements Handler {

        protected final AjpNio2Protocol proto;

        public AjpConnectionHandler(AjpNio2Protocol proto) {
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
            // AJP does not support SSL
            return null;
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

        /**
         * Expected to be used by the handler once the processor is no longer
         * required.
         */
        @Override
        public void release(SocketWrapper<Nio2Channel> socket,
                Processor<Nio2Channel> processor, boolean isSocketClosing,
                boolean addToPoller) {
            processor.recycle(isSocketClosing);
            recycledProcessors.push(processor);
            if (addToPoller) {
                ((Nio2Endpoint) proto.endpoint).awaitBytes(socket);
            }
        }

        @Override
        protected AjpNio2Processor createProcessor() {
            AjpNio2Processor processor = new AjpNio2Processor(proto.packetSize, (Nio2Endpoint) proto.endpoint);
            processor.setAdapter(proto.getAdapter());
            processor.setTomcatAuthentication(proto.tomcatAuthentication);
            processor.setRequiredSecret(proto.requiredSecret);
            processor.setClientCertProvider(proto.getClientCertProvider());
            register(processor);
            return processor;
        }

        @Override
        public void onCreateSSLEngine(SSLEngine engine) {
        }

        @Override
        public void closeAll() {
            for (Nio2Channel channel : connections.keySet()) {
                ((Nio2Endpoint) proto.endpoint).closeSocket(channel.getSocket(), SocketStatus.STOP);
            }
        }
    }
}

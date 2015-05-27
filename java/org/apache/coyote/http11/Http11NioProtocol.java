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

import org.apache.coyote.Processor;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint;
import org.apache.tomcat.util.net.NioEndpoint.Handler;
import org.apache.tomcat.util.net.SocketWrapperBase;


/**
 * Abstract the protocol implementation, including threading, etc.
 * Processor is single threaded and specific to stream-based protocols,
 * will not fit Jk protocols like JNI.
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 */
public class Http11NioProtocol extends AbstractHttp11JsseProtocol<NioChannel> {

    private static final Log log = LogFactory.getLog(Http11NioProtocol.class);


    public Http11NioProtocol() {
        super(new NioEndpoint());
        Http11ConnectionHandler cHandler = new Http11ConnectionHandler(this);
        setHandler(cHandler);
        ((NioEndpoint) getEndpoint()).setHandler(cHandler);
    }


    @Override
    protected Log getLog() { return log; }


    // -------------------- Pool setup --------------------

    public void setPollerThreadCount(int count) {
        ((NioEndpoint)getEndpoint()).setPollerThreadCount(count);
    }

    public int getPollerThreadCount() {
        return ((NioEndpoint)getEndpoint()).getPollerThreadCount();
    }

    public void setSelectorTimeout(long timeout) {
        ((NioEndpoint)getEndpoint()).setSelectorTimeout(timeout);
    }

    public long getSelectorTimeout() {
        return ((NioEndpoint)getEndpoint()).getSelectorTimeout();
    }

    public void setPollerThreadPriority(int threadPriority) {
        ((NioEndpoint)getEndpoint()).setPollerThreadPriority(threadPriority);
    }

    public int getPollerThreadPriority() {
      return ((NioEndpoint)getEndpoint()).getThreadPriority();
    }


    // ----------------------------------------------------- JMX related methods

    @Override
    protected String getNamePrefix() {
        if (isSSLEnabled()) {
            return ("https-nio");
        } else {
            return ("http-nio");
        }
    }


    // --------------------  Connection handler --------------------

    protected static class Http11ConnectionHandler
            extends AbstractHttp11ConnectionHandler<NioChannel>
            implements Handler {

        Http11ConnectionHandler(Http11NioProtocol proto) {
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
        public void release(SocketChannel socket) {
            if (log.isDebugEnabled())
                log.debug("Iterating through our connections to release a socket channel:"+socket);
            boolean released = false;
            Iterator<java.util.Map.Entry<NioChannel, Processor>> it = connections.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<NioChannel, Processor> entry = it.next();
                if (entry.getKey().getIOChannel()==socket) {
                    it.remove();
                    Processor result = entry.getValue();
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
         * Expected to be used by the Poller to release resources on socket
         * close, errors etc.
         */
        @Override
        public void release(SocketWrapperBase<NioChannel> socket) {
            Processor processor = connections.remove(socket.getSocket());
            if (processor != null) {
                processor.recycle();
                recycledProcessors.push(processor);
            }
        }


        @Override
        public void release(SocketWrapperBase<NioChannel> socket,
                Processor processor, boolean addToPoller) {
            processor.recycle();
            recycledProcessors.push(processor);
            if (addToPoller) {
                socket.registerReadInterest();
            }
        }

        @Override
        protected void longPoll(SocketWrapperBase<NioChannel> socket, Processor processor) {

            if (processor.isAsync()) {
                socket.setAsync(true);
            } else {
                // Either:
                //  - this is an upgraded connection
                //  - the request line/headers have not been completely
                //    read
                socket.registerReadInterest();
            }
        }
    }
}

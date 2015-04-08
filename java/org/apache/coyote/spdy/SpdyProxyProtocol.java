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
package org.apache.coyote.spdy;

import java.io.IOException;
import java.nio.channels.SocketChannel;

import org.apache.coyote.AbstractProtocol;
import org.apache.coyote.UpgradeProtocol;
import org.apache.coyote.ajp.Constants;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.spdy.NetSupportSocket;
import org.apache.tomcat.spdy.SpdyConnection;
import org.apache.tomcat.spdy.SpdyContext;
import org.apache.tomcat.spdy.SpdyContext.SpdyHandler;
import org.apache.tomcat.spdy.SpdyStream;
import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapperBase;

/**
 * SPDY in 'proxy' mode - no SSL and no header compression.
 * This doesn't require JNI libraries, SSL/compression are off-loaded to
 * a reverse proxy ( apache, etc ).
 *
 * To configure:
 * &lt;Connector port="8011" protocol="org.apache.coyote.spdy.SpdyProxyNioProtocol"/&gt;
 *
 * To test, use
 *   chrome  --use-spdy=no-compress,no-ssl [--enable-websocket-over-spdy]
 *
 * TODO: Remote information (client ip, certs, etc ) will be sent in X- headers.
 * TODO: if spdy-&gt;spdy proxy, info about original spdy stream for pushes.
 *
 * TODO: This proxy implementation was refactored to use NIO instead of BIO. It
 *       is untested as SPDY/2 is now obsolete and is not supported by current
 *       browsers. This code should be reviewed when work starts on the HTTP/2
 *       implementation.
 *
 */
public class SpdyProxyProtocol extends AbstractProtocol<NioChannel> {
    private static final Log log = LogFactory.getLog(SpdyProxyProtocol.class);

    private SpdyContext spdyContext;

    private boolean compress = false;

    public SpdyProxyProtocol() {
        super(new NioEndpoint());
        NioEndpoint.Handler cHandler = new TomcatNioHandler();
        ((NioEndpoint) getEndpoint()).setHandler(cHandler);
        setSoTimeout(Constants.DEFAULT_CONNECTION_TIMEOUT);
    }

    @Override
    protected Log getLog() {
        return log;
    }

    @Override
    protected String getNamePrefix() {
        return "spdy2-nio";
    }

    @Override
    protected String getProtocolName() {
        return "spdy2";
    }

    @Override
    public void start() throws Exception {
        super.start();
        spdyContext = new SpdyContext();
        spdyContext.setTlsCompression(false, compress);
        spdyContext.setHandler(new SpdyHandler() {
            @Override
            public void onStream(SpdyConnection con, SpdyStream ch) throws IOException {
                SpdyProcessor sp = new SpdyProcessor(con, getEndpoint());
                sp.setAdapter(getAdapter());
                sp.onSynStream(ch);
            }
        });
        spdyContext.setNetSupport(new NetSupportSocket());
        spdyContext.setExecutor(getEndpoint().getExecutor());
    }

    public boolean isCompress() {
        return compress;
    }

    public void setCompress(boolean compress) {
        this.compress = compress;
    }

    public class TomcatNioHandler implements NioEndpoint.Handler {

        @Override
        public Object getGlobal() {
            return null;
        }

        @Override
        public void recycle() {
        }

        @Override
        public SocketState process(SocketWrapperBase<NioChannel> socket,
                SocketStatus status) {

            spdyContext.getNetSupport().onAccept(socket.getSocket());
            return SocketState.CLOSED;
        }

        @Override
        public void release(SocketWrapperBase<NioChannel> socket) {
            // TODO Auto-generated method stub
        }

        @Override
        public void release(SocketChannel socket) {
            // TODO Auto-generated method stub
        }
    }

    @Override
    protected UpgradeProtocol getNegotiatedProtocol(String name) {
        // TODO Auto-generated method stub
        return null;
    }
}

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

import org.apache.coyote.Adapter;
import org.apache.coyote.http11.NpnHandler;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jni.SSLExt;
import org.apache.tomcat.spdy.NetSupportOpenSSL;
import org.apache.tomcat.spdy.SpdyConnection;
import org.apache.tomcat.spdy.SpdyContext;
import org.apache.tomcat.spdy.SpdyContext.SpdyHandler;
import org.apache.tomcat.spdy.SpdyStream;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.AprEndpoint;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;

/**
 * Plugin for APR connector providing SPDY support via NPN negotiation.
 *
 * Example:
 * <Connector port="9443"
 *            npnHandler="org.apache.coyote.spdy.SpdyAprNpnHandler"
 *            protocol="HTTP/1.1"
 *            SSLEnabled="true"
 *            maxThreads="150"
 *            scheme="https"
 *            secure="true"
 *            sslProtocol="TLS"
 *            SSLCertificateFile="conf/localhost-cert.pem"
 *            SSLCertificateKeyFile="conf/localhost-key.pem"/>
 *
 * This requires APR library ( libtcnative-1 ) to be present and compiled
 * with a recent openssl or a openssl patched with npn support.
 *
 * Because we need to auto-detect SPDY and fallback to HTTP ( based on SSL next
 * proto ) this is implemented in tomcat a special way:
 * Http11AprProtocol will delegate to Spdy.process if spdy is
 * negotiated by TLS.
 *
 */
public class SpdyAprNpnHandler implements NpnHandler<Long> {

    private static final Log log = LogFactory.getLog(AprEndpoint.class);

    private SpdyContext spdyContext;

    @Override
    public void init(final AbstractEndpoint<Long> ep, long sslContext,
            final Adapter adapter) {
        spdyContext = new SpdyContext();
        if (sslContext == 0) {
            // Apr endpoint without SSL - proxy mode.
            spdyContext.setTlsCompression(false, false);
            return;
        }
        if (0 != SSLExt.setNPN(sslContext, SpdyContext.SPDY_NPN_OUT)) {
            log.warn("SPDY/NPN not supported");
        }
        spdyContext.setNetSupport(new NetSupportOpenSSL());
        spdyContext.setExecutor(ep.getExecutor());
        spdyContext.setHandler(new SpdyHandler() {
            @Override
            public void onStream(SpdyConnection con, SpdyStream ch)
                    throws IOException {
                SpdyProcessor<Long> sp = new SpdyProcessor<>(con, ep);
                sp.setAdapter(adapter);
                sp.onSynStream(ch);
            }
        });
    }

    @Override
    public SocketState process(SocketWrapper<Long> socketWrapper,
            SocketStatus status) {

        long socket = socketWrapper.getSocket().longValue();

        if (! spdyContext.getNetSupport().isSpdy(socketWrapper.getSocket())) {
            return SocketState.OPEN;
        }

        ((NetSupportOpenSSL) spdyContext.getNetSupport()).onAcceptLong(socket);

        // No need to keep tomcat thread busy - but socket will be handled by apr socket context.
        return SocketState.LONG;
    }


    @Override
    public void onCreateEngine(Object socket) {
    }
}

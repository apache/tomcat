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
import org.apache.coyote.http11.Http11AprProtocol;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jni.SSLExt;
import org.apache.tomcat.spdy.SpdyConnection;
import org.apache.tomcat.spdy.SpdyContext;
import org.apache.tomcat.spdy.SpdyContextJni;
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
public class SpdyAprNpnHandler implements Http11AprProtocol.NpnHandler {

    private static final Log log = LogFactory.getLog(AprEndpoint.class);

    private SpdyContextApr spdyContext;

    boolean ssl = true;

    @Override
    public void init(final AbstractEndpoint ep, long sslContext,
            final Adapter adapter) {
        if (sslContext == 0) {
            // Apr endpoint without SSL.
            ssl = false;
            spdyContext = new SpdyContextApr(ep, adapter);
            spdyContext.setExecutor(ep.getExecutor());
            return;
        }
        if (0 == SSLExt.setNPN(sslContext, SpdyContext.SPDY_NPN_OUT)) {
            spdyContext = new SpdyContextApr(ep, adapter);
            spdyContext.setExecutor(ep.getExecutor());
        } else {
            log.warn("SPDY/NPN not supported");
        }
    }


    private final class SpdyContextApr extends SpdyContextJni {
        private final AbstractEndpoint ep;

        private final Adapter adapter;

        private SpdyContextApr(AbstractEndpoint ep, Adapter adapter) {
            this.ep = ep;
            this.adapter = adapter;
        }

        @Override
        protected void onSynStream(SpdyConnection con, SpdyStream ch) throws IOException {
            SpdyProcessor sp = new SpdyProcessor(con, ep);
            sp.setAdapter(adapter);
            sp.onSynStream(ch);
        }
    }

    @Override
    public SocketState process(SocketWrapper<Long> socketO, SocketStatus status,
            Http11AprProtocol proto, AbstractEndpoint endpoint) {

        SocketWrapper<Long> socketW = socketO;
        long socket = socketW.getSocket().longValue();

        try {
            spdyContext.onAccept(socket);
        } catch (IOException e) {
        }
        // No need to keep tomcat thread busy - but socket will be handled by apr socket context.
        return SocketState.LONG;
    }

    public void onClose(SocketWrapper<Long> socketWrapper) {
    }
}

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
import java.util.HashMap;
import java.util.Map;

import org.apache.coyote.Adapter;
import org.apache.coyote.http11.Http11AprProtocol;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jni.Error;
import org.apache.tomcat.jni.SSLExt;
import org.apache.tomcat.jni.Status;
import org.apache.tomcat.spdy.CompressDeflater6;
import org.apache.tomcat.spdy.SpdyConnection;
import org.apache.tomcat.spdy.SpdyContext;
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

    private SpdyContext spdyContext;

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


    private final class SpdyContextApr extends SpdyContext {
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

    public static class SpdyConnectionApr extends SpdyConnection {
        long socket;

        public SpdyConnectionApr(SocketWrapper<Long> socketW,
                SpdyContext spdyContext, boolean ssl) {
            super(spdyContext);
            this.socket = socketW.getSocket().longValue();
            if (ssl) {
                setCompressSupport(new CompressDeflater6());
            }
        }

        // TODO: write/read should go to SocketWrapper.
        @Override
        public int write(byte[] data, int off, int len) {
            if (socket == 0 || inClosed) {
                return -1;
            }
            int rem = len;
            while (rem > 0) {
                int sent = org.apache.tomcat.jni.Socket.send(socket, data, off,
                        rem);
                if (sent < 0) {
                    inClosed = true;
                    return -1;
                }
                if (sent == 0) {
                    return len - rem;
                }
                rem -= sent;
                off += sent;
            }
            return len;
        }

        /**
         */
        @Override
        public int read(byte[] data, int off, int len) throws IOException {
            if (socket == 0 || inClosed) {
                return 0;
            }
            int rd = org.apache.tomcat.jni.Socket.recv(socket, data, off, len);
            if (rd == -Status.APR_EOF) {
                inClosed = true;
                return -1;
            }
            if (rd == -Status.TIMEUP) {
                rd = 0;
            }
            if (rd == -Status.EAGAIN) {
                rd = 0;
            }
            if (rd < 0) {
                // all other errors
                inClosed = true;
                throw new IOException("Error: " + rd + " "
                        + Error.strerror((int) -rd));
            }
            off += rd;
            len -= rd;
            return rd;
        }
    }

    // apr normally creates a new object on each poll.
    // For 'upgraded' protocols we need to remember it's handled differently.
    Map<Long, SpdyConnectionApr> lightProcessors =
            new HashMap<Long, SpdyConnectionApr>();

    @Override
    public SocketState process(SocketWrapper<Long> socketO, SocketStatus status,
            Http11AprProtocol proto, AbstractEndpoint endpoint) {

        SocketWrapper<Long> socketW = socketO;
        long socket = ((Long) socketW.getSocket()).longValue();

        SpdyConnectionApr lh = lightProcessors.get(socket);
        // Are we getting an HTTP request ?
        if (lh == null && status != SocketStatus.OPEN) {
            return null;
        }

        log.info("Status: " + status);

        SocketState ss = null;
        if (lh != null) {
            // STOP, ERROR, DISCONNECT, TIMEOUT -> onClose
            if (status == SocketStatus.TIMEOUT) {
                // Called from maintain - we're removed from the poll
                ((AprEndpoint) endpoint).getCometPoller().add(
                        socketO.getSocket().longValue(), false);
                return SocketState.LONG;
            }
            if (status == SocketStatus.STOP || status == SocketStatus.DISCONNECT ||
                    status == SocketStatus.ERROR) {
                SpdyConnectionApr wrapper = lightProcessors.remove(socket);
                if (wrapper != null) {
                    wrapper.onClose();
                }
                return SocketState.CLOSED;
            }
            int rc = lh.onBlockingSocket();
            ss = (rc == SpdyConnection.LONG) ? SocketState.LONG
                    : SocketState.CLOSED;
        } else {
            // OPEN, no existing socket
            if (!ssl || SSLExt.checkNPN(socket, SpdyContext.SPDY_NPN)) {
                // NPN negotiated or not ssl
                lh = new SpdyConnectionApr(socketW, spdyContext, ssl);

                int rc = lh.onBlockingSocket();
                ss = (rc == SpdyConnection.LONG) ? SocketState.LONG
                        : SocketState.CLOSED;
                if (ss == SocketState.LONG) {
                    lightProcessors.put(socketO.getSocket().longValue(), lh);
                }
            } else {
                return null;
            }
        }

        // OPEN is used for both 'first time' and 'new connection'
        // In theory we shouldn't get another open while this is in
        // progress ( only after we add back to the poller )

        if (ss == SocketState.LONG) {
            log.info("Long poll: " + status);
            ((AprEndpoint) endpoint).getCometPoller().add(
                    socketO.getSocket().longValue(), false);
        }
        return ss;
    }

    public void onClose(SocketWrapper<Long> socketWrapper) {
    }


}

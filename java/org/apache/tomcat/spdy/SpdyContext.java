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
package org.apache.tomcat.spdy;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Will implement polling/reuse of heavy objects, allow additional
 * configuration.
 *
 * The abstract methods allow integration with different libraries (
 * compression, request handling )
 *
 * In 'external' mode it must be used with APR library and compression.
 *
 * In 'intranet' mode - it is supposed to be used behind a load balancer that
 * handles SSL and compression. Test with: --user-data-dir=/tmp/test
 * --use-spdy=no-compress,no-ssl
 */
public final class SpdyContext {

    public static final byte[] SPDY_NPN;

    public static final byte[] SPDY_NPN_OUT;
    static {
        SPDY_NPN = "spdy/2".getBytes();
        SPDY_NPN_OUT = new byte[SPDY_NPN.length + 2];
        System.arraycopy(SPDY_NPN, 0, SPDY_NPN_OUT, 1, SPDY_NPN.length);
        SPDY_NPN_OUT[0] = (byte) SPDY_NPN.length;
    }

    private Executor executor;

    private int defaultFrameSize = 8192;

    public static final boolean debug = false;

    protected boolean tls = true;
    protected boolean compression = true;

    private NetSupport netSupport;


    public abstract static class NetSupport {
        protected SpdyContext ctx;

        public void setSpdyContext(SpdyContext ctx) {
            this.ctx = ctx;
        }

        public abstract SpdyConnection getConnection(String host, int port)
                throws IOException;

        public abstract boolean isSpdy(Object socketW);

        public abstract void onAccept(Object socket);

        public abstract void listen(int port, String cert, String key)
                throws IOException;

        public abstract void stop() throws IOException;
    }

    public SpdyContext() {
    }

    public void setTlsCompression(boolean tls, boolean compress) {
        this.tls = tls;
        this.compression = compress;
    }

    /**
     * Get a frame - frames are heavy buffers, may be reused.
     */
    public SpdyFrame getFrame() {
        return new SpdyFrame(defaultFrameSize);
    }

    /**
     * Set the max frame size.
     *
     * Larger data packets will be split in multiple frames.
     *
     * ( the code is currently accepting larger control frames - it's not
     * clear if we should just reject them, many servers limit header size -
     * the http connector also has a 8k limit - getMaxHttpHeaderSize )
     */
    public void setFrameSize(int frameSize) {
        defaultFrameSize = frameSize;
    }

    /**
     * Override for server side to return a custom stream.
     */
    public SpdyStream getStream(SpdyConnection framer) {
        SpdyStream spdyStream = new SpdyStream(framer);
        return spdyStream;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    public void setNetSupport(NetSupport netSupport) {
        this.netSupport = netSupport;
        netSupport.setSpdyContext(this);
    }

    public NetSupport getNetSupport() {
        if (netSupport == null) {
            try {
                Class<?> c0 = Class.forName("org.apache.tomcat.spdy.NetSupportOpenSSL");
                netSupport = (NetSupport) c0.newInstance();
                netSupport.setSpdyContext(this);
                return netSupport;
            } catch (Throwable t) {
                // ignore, openssl not supported
            }
            try {
                Class<?> c1 = Class.forName("org.apache.tomcat.spdy.NetSupportJava7");
                netSupport = (NetSupport) c1.newInstance();
                netSupport.setSpdyContext(this);
                return netSupport;
            } catch (Throwable t) {
                // ignore, npn not supported
            }
            // non-ssl mode must be set explicitly
            throw new RuntimeException("SSL NextProtoclNegotiation no supported.");
        }

        return netSupport;
    }


    /**
     * SPDY is a multiplexed protocol - the SpdyProcessors will be executed on
     * this executor.
     *
     * If the context returns null - we'll assume the SpdyProcessors are fully
     * non blocking, and will execute them in the spdy thread.
     */
    public Executor getExecutor() {
        if (executor == null) {
            executor = Executors.newCachedThreadPool();
        }
        return executor;
    }

    SpdyHandler handler;

    public SpdyHandler getHandler() {
        return handler;
    }

    public void setHandler(SpdyHandler handler) {
        this.handler = handler;
    }

    public static interface SpdyHandler {
        public void onStream(SpdyConnection spdyCon, SpdyStream ch) throws IOException;

    }

    /**
     * A handler implementing this interface will be called in the 'io' thread - the
     * thread reading the multiplexed stream, and in the case of non-blocking
     * transports also handling polling the socket.
     *
     */
    public static interface NonBlockingSpdyHandler extends SpdyHandler {
    }


    /**
     * Client mode: return a connection for host/port.
     * @throws IOException
     */
    public SpdyConnection getConnection(String host, int port) throws IOException {
        return netSupport.getConnection(host, port);
    }

    public final void listen(final int port, String cert, String key) throws IOException {
        netSupport.listen(port, cert, key);
    }

    /**
     * Close all pending connections and free resources.
     */
    public final void stop() throws IOException {
        netSupport.stop();
    }

    public void onStream(SpdyConnection spdyConnection, SpdyStream ch) throws IOException {
        if (handler instanceof NonBlockingSpdyHandler) {
            handler.onStream(spdyConnection, ch);
        } else {
            getExecutor().execute(ch);
        }
    }
}

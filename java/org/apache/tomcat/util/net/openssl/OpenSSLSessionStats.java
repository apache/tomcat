/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.net.openssl;

import org.apache.tomcat.jni.SSLContext;

/**
 * Stats exposed by an OpenSSL session context.
 *
 * @see <a href="https://www.openssl.org/docs/ssl/SSL_CTX_sess_number.html"><code>SSL_CTX_sess_number</code></a>
 */
public final class OpenSSLSessionStats {

    private final long context;

    OpenSSLSessionStats(long context) {
        this.context = context;
    }

    /**
     * @return The current number of sessions in the internal session cache.
     */
    public long number() {
        return SSLContext.sessionNumber(context);
    }

    /**
     * @return The number of started SSL/TLS handshakes in client mode.
     */
    public long connect() {
        return SSLContext.sessionConnect(context);
    }

    /**
     * @return The number of successfully established SSL/TLS sessions in client mode.
     */
    public long connectGood() {
        return SSLContext.sessionConnectGood(context);
    }

    /**
     * @return The number of start renegotiations in client mode.
     */
    public long connectRenegotiate() {
        return SSLContext.sessionConnectRenegotiate(context);
    }

    /**
     * @return The number of started SSL/TLS handshakes in server mode.
     */
    public long accept() {
        return SSLContext.sessionAccept(context);
    }

    /**
     * @return The number of successfully established SSL/TLS sessions in server mode.
     */
    public long acceptGood() {
        return SSLContext.sessionAcceptGood(context);
    }

    /**
     * @return The number of start renegotiations in server mode.
     */
    public long acceptRenegotiate() {
        return SSLContext.sessionAcceptRenegotiate(context);
    }

    /**
     * @return The number of successfully reused sessions. In client mode, a session set with {@code SSL_set_session}
     *             successfully reused is counted as a hit. In server mode, a session successfully retrieved from
     *             internal or external cache is counted as a hit.
     */
    public long hits() {
        return SSLContext.sessionHits(context);
    }

    /**
     * @return The number of successfully retrieved sessions from the external session cache in server mode.
     */
    public long cbHits() {
        return SSLContext.sessionCbHits(context);
    }

    /**
     * @return The number of sessions proposed by clients that were not found in the internal session cache in server
     *             mode.
     */
    public long misses() {
        return SSLContext.sessionMisses(context);
    }

    /**
     * @return The number of sessions proposed by clients and either found in the internal or external session cache in
     *             server mode, but that were invalid due to timeout. These sessions are not included in the
     *             {@link #hits()} count.
     */
    public long timeouts() {
        return SSLContext.sessionTimeouts(context);
    }

    /**
     * @return The number of sessions that were removed because the maximum session cache size was exceeded.
     */
    public long cacheFull() {
        return SSLContext.sessionCacheFull(context);
    }
}

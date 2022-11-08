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
package org.apache.tomcat.util.net.openssl.panama;

import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.util.Enumeration;
import java.util.NoSuchElementException;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;

import static org.apache.tomcat.util.openssl.openssl_h.*;

import org.apache.tomcat.util.res.StringManager;

/**
 * OpenSSL specific {@link SSLSessionContext} implementation.
 */
public class OpenSSLSessionContext implements SSLSessionContext {
    private static final StringManager sm = StringManager.getManager(OpenSSLSessionContext.class);
    private static final Enumeration<byte[]> EMPTY = new EmptyEnumeration();

    private static final int TICKET_KEYS_SIZE = 48;

    private final OpenSSLSessionStats stats;
    private final OpenSSLContext context;

    OpenSSLSessionContext(OpenSSLContext context) {
        this.context = context;
        stats = new OpenSSLSessionStats(context.getSSLContext());
    }

    @Override
    public SSLSession getSession(byte[] bytes) {
        return null;
    }

    @Override
    public Enumeration<byte[]> getIds() {
        return EMPTY;
    }

    /**
     * Sets the SSL session ticket keys of this context.
     *
     * @param keys The session ticket keys
     */
    public void setTicketKeys(byte[] keys) {
        if (keys == null) {
            throw new IllegalArgumentException(sm.getString("sessionContext.nullTicketKeys"));
        }
        if (keys.length != TICKET_KEYS_SIZE) {
            throw new IllegalArgumentException(sm.getString("sessionContext.invalidTicketKeysLength", keys.length));
        }
        try (var memorySession = Arena.openConfined()) {
            var array = memorySession.allocateArray(ValueLayout.JAVA_BYTE, keys);
            // #define SSL_CTX_set_tlsext_ticket_keys(ctx, keys, keylen)
            //     SSL_CTX_ctrl((ctx),SSL_CTRL_SET_TLSEXT_TICKET_KEYS, (keylen), (keys))
            SSL_CTX_ctrl(context.getSSLContext(), SSL_CTRL_SET_TLSEXT_TICKET_KEYS(), TICKET_KEYS_SIZE, array);
        }
    }

    /**
     * Enable or disable caching of SSL sessions.
     *
     * @param enabled {@code true} to enable caching, {@code false} to disable
     */
    public void setSessionCacheEnabled(boolean enabled) {
        long mode = enabled ? SSL_SESS_CACHE_SERVER() : SSL_SESS_CACHE_OFF();
        // # define SSL_CTX_set_session_cache_mode(ctx,m) \
        //     SSL_CTX_ctrl(ctx,SSL_CTRL_SET_SESS_CACHE_MODE,m,NULL)
        SSL_CTX_ctrl(context.getSSLContext(), SSL_CTRL_SET_SESS_CACHE_MODE(), mode, null);
    }

    /**
     * @return {@code true} if caching of SSL sessions is enabled, {@code false}
     *         otherwise.
     */
    public boolean isSessionCacheEnabled() {
        // # define SSL_CTX_get_session_cache_mode(ctx) \
        //    SSL_CTX_ctrl(ctx,SSL_CTRL_GET_SESS_CACHE_MODE,0,NULL)
        return SSL_CTX_ctrl(context.getSSLContext(), SSL_CTRL_GET_SESS_CACHE_MODE(), 0, null) == SSL_SESS_CACHE_SERVER();
    }

    /**
     * @return The statistics for this context.
     */
    public OpenSSLSessionStats stats() {
        return stats;
    }

    @Override
    public void setSessionTimeout(int seconds) {
        if (seconds < 0) {
            throw new IllegalArgumentException();
        }
        SSL_CTX_set_timeout(context.getSSLContext(), seconds);
    }

    @Override
    public int getSessionTimeout() {
        return (int) SSL_CTX_get_timeout(context.getSSLContext());
    }

    @Override
    public void setSessionCacheSize(int size) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        // # define SSL_CTX_sess_set_cache_size(ctx,t) \
        //     SSL_CTX_ctrl(ctx,SSL_CTRL_SET_SESS_CACHE_SIZE,t,NULL)
        SSL_CTX_ctrl(context.getSSLContext(), SSL_CTRL_SET_SESS_CACHE_SIZE(), size, null);
    }

    @Override
    public int getSessionCacheSize() {
        // # define SSL_CTX_sess_get_cache_size(ctx) \
        //     SSL_CTX_ctrl(ctx,SSL_CTRL_GET_SESS_CACHE_SIZE,0,NULL)
        return (int) SSL_CTX_ctrl(context.getSSLContext(), SSL_CTRL_GET_SESS_CACHE_SIZE(), 0, null);
    }

    /**
     * Set the context within which session be reused (server side only)
     * See <a href="http://www.openssl.org/docs/ssl/SSL_CTX_set_session_id_context.html">
     *     man SSL_CTX_set_session_id_context</a>
     *
     * @param sidCtx can be any kind of binary data, it is therefore possible to use e.g. the name
     *               of the application and/or the hostname and/or service name
     * @return {@code true} if success, {@code false} otherwise.
     */
    public boolean setSessionIdContext(byte[] sidCtx) {
        try (var memorySession = Arena.openConfined()) {
            var array = memorySession.allocateArray(ValueLayout.JAVA_BYTE, sidCtx);
            return (SSL_CTX_set_session_id_context(context.getSSLContext(), array, sidCtx.length) == 1);
        }
    }

    private static final class EmptyEnumeration implements Enumeration<byte[]> {
        @Override
        public boolean hasMoreElements() {
            return false;
        }

        @Override
        public byte[] nextElement() {
            throw new NoSuchElementException();
        }
    }
}

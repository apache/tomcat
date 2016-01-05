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

import java.util.Enumeration;
import java.util.NoSuchElementException;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;

import org.apache.tomcat.jni.SSLContext;
import org.apache.tomcat.util.res.StringManager;

/**
 * OpenSSL specific {@link SSLSessionContext} implementation.
 */
public abstract class OpenSSLSessionContext implements SSLSessionContext {
    private static final StringManager sm = StringManager.getManager(OpenSSLSessionContext.class);
    private static final Enumeration<byte[]> EMPTY = new EmptyEnumeration();

    private final OpenSSLSessionStats stats;
    final long context;

    OpenSSLSessionContext(long context) {
        this.context = context;
        stats = new OpenSSLSessionStats(context);
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
        SSLContext.setSessionTicketKeys(context, keys);
    }

    /**
     * Enable or disable caching of SSL sessions.
     *
     * @param enabled {@code true} to enable caching, {@code false} to disable
     */
    public abstract void setSessionCacheEnabled(boolean enabled);

    /**
     * @return {@code true} if caching of SSL sessions is enabled, {@code false}
     *         otherwise.
     */
    public abstract boolean isSessionCacheEnabled();

    /**
     * @return The statistics for this context.
     */
    public OpenSSLSessionStats stats() {
        return stats;
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

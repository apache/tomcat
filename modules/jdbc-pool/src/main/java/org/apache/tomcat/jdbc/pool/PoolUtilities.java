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
package org.apache.tomcat.jdbc.pool;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Utility methods for managing JDBC pool properties: the copy the connect
 * path hands to the JDBC driver, and the password-masked copies the JMX and
 * bean property getters expose, are all built here.
 */
public class PoolUtilities {

    /**
     * Property key for the database user name.
     */
    public static final String PROP_USER = "user";

    /**
     * Property key for the database password.
     */
    public static final String PROP_PASSWORD = "password";

    /**
     * Default constructor.
     */
    public PoolUtilities() {
    }

    /**
     * Creates a shallow copy of the given properties. Entries that the source
     * resolves to a String through a defaults table are materialized into the
     * copy as plain values, so the copy resolves every String the source
     * resolves and needs no defaults table of its own; the defaults table
     * itself is not carried over. A String directly present in the source
     * under the same key shadows the defaults there and takes precedence in
     * the copy. A non-String directly present value shadows nothing, because
     * {@link Properties#getProperty(String)} falls through to the defaults
     * whenever the direct value is not a String, so where the source resolves
     * a String under such a key the resolved String takes its place in the
     * copy; entries the source resolves to no String are copied as they are,
     * and a non-String held only in a defaults table - which neither the
     * source nor the copy resolves - does not appear in the copy at all.
     * <p>
     * The copy is per-key best effort against a source modified concurrently:
     * an entry the modification removes may still be copied, and an entry it
     * adds is copied only if it was already there when the copy sampled the
     * source. A caller that needs the copy to show one state the source was
     * actually in must serialize it against the modifications itself.
     *
     * @param p the properties to clone, not {@code null}
     *
     * @return a new Properties instance, without a defaults table, resolving
     *         every String the source resolves and carrying over its other
     *         direct entries
     *
     * @throws NullPointerException if the source is {@code null}
     */
    public static Properties clone(Properties p) {
        Properties c = new Properties();
        c.putAll(p);
        // putAll(...) copies only the entries directly present in the source,
        // so materialize the String values the source resolves through
        // defaults; the precedence rule deciding which of them are skipped is
        // the javadoc's. Re-read each value from the source:
        // stringPropertyNames() is only a snapshot and the source may be
        // modified concurrently, so a snapshotted key may resolve to nothing
        // by the time it is read
        for (String key : p.stringPropertyNames()) {
            String value = p.getProperty(key);
            if (value != null && !(c.get(key) instanceof String)) {
                c.setProperty(key, value);
            }
        }
        return c;
    }

    /**
     * Whether {@code key} names a credential, ignoring case and surrounding whitespace. The drivers accept
     * {@code pass} and {@code pwd} as aliases of {@code password}, so a masked copy has to remove the same spellings
     * the string maskers treat as credentials.
     *
     * @param key the property key to test, possibly {@code null}
     *
     * @return {@code true} when the key is one of the recognised credential keys
     */
    static boolean isCredentialKey(String key) {
        if (key == null) {
            return false;
        }
        String candidate = key.trim();
        return PROP_PASSWORD.equalsIgnoreCase(candidate) || "pass".equalsIgnoreCase(candidate)
                || "pwd".equalsIgnoreCase(candidate);
    }

    /**
     * Creates a copy of the given properties as {@link #clone(Properties)}
     * does, with every credential entry removed. A credential entry is one
     * whose key is recognised by {@link #isCredentialKey(String)}:
     * {@code password}, {@code pass} and {@code pwd}, ignoring case and
     * surrounding whitespace. The copy carries no defaults table, so the
     * removal also reaches a credential the source only resolved through its
     * defaults - a reference copy would let the remove walk past the defaults
     * table and leave the credential resolvable.
     *
     * @param p the properties to clone, not {@code null}
     *
     * @return a new Properties instance with all entries except the credentials
     *
     * @throws NullPointerException if the source is {@code null}
     */
    public static Properties cloneWithoutPassword(Properties p) {
        Properties result = clone(p);
        for (Object key : result.keySet().toArray()) {
            if (key instanceof String && isCredentialKey((String) key)) {
                result.remove(key);
            }
        }
        return result;
    }

    /**
     * Returns a copy of a throwable whose message - and the messages of its suppressed throwables and causes - carry
     * no configured credentials. The stack frames are preserved. A diagnostic that has to include a driver or wrapper
     * failure without trusting the text that failure carries uses this copy for its log record.
     *
     * @param value the throwable to copy, possibly {@code null}
     *
     * @return the masked copy, or {@code null} when the input is {@code null}
     */
    public static Throwable maskCredentials(Throwable value) {
        return value == null ? null : mask(value, new IdentityHashMap<>());
    }

    private static Throwable mask(Throwable value, Map<Throwable, Throwable> masked) {
        Throwable previous = masked.get(value);
        if (previous != null) {
            return previous;
        }
        String message = PoolProperties.maskCredentials(value.getMessage());
        MaskedThrowable copy = new MaskedThrowable(value.getClass().getName(), message);
        masked.put(value, copy);
        copy.setStackTrace(value.getStackTrace());
        for (Throwable suppressed : value.getSuppressed()) {
            Throwable maskedSuppressed = mask(suppressed, masked);
            if (maskedSuppressed != copy) {
                copy.addSuppressed(maskedSuppressed);
            }
        }
        Throwable cause = value.getCause();
        if (cause != null) {
            Throwable maskedCause = mask(cause, masked);
            if (maskedCause != copy) {
                copy.initCause(maskedCause);
            }
        }
        return copy;
    }

    /**
     * The throwable {@link #maskCredentials(Throwable)} logs in place of the original: it keeps the original class name
     * in its message and its stack frames, but not the original message text.
     */
    private static final class MaskedThrowable extends Throwable {

        private static final long serialVersionUID = 1L;

        private final String originalClass;

        MaskedThrowable(String originalClass, String message) {
            super(message);
            this.originalClass = originalClass;
        }

        @Override
        public String toString() {
            String message = getMessage();
            return originalClass + (message == null || message.isEmpty() ? "" : ": " + message);
        }
    }
}

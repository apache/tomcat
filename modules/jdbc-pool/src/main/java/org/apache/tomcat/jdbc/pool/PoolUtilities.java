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
     * Creates a copy of the given properties as {@link #clone(Properties)}
     * does, with the password entry removed. The copy carries no defaults
     * table, so the removal also reaches a password the source only resolved
     * through its defaults - a reference copy would let the remove walk past
     * the defaults table and leave the password resolvable.
     *
     * @param p the properties to clone, not {@code null}
     *
     * @return a new Properties instance with all entries except the password
     *
     * @throws NullPointerException if the source is {@code null}
     */
    public static Properties cloneWithoutPassword(Properties p) {
        Properties result = clone(p);
        result.remove(PROP_PASSWORD);
        return result;
    }
}

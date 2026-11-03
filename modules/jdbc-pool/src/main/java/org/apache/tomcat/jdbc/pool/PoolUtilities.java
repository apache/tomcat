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
 * Utility methods for managing JDBC pool properties.
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
     * resolves and needs no defaults table of its own. A String directly
     * present in the source under the same key shadows the defaults there and
     * takes precedence in the copy. A non-String directly present value
     * shadows nothing, because {@link Properties#getProperty(String)} falls
     * through to the defaults whenever the direct value is not a String, so
     * where the source resolves a String under such a key the resolved String
     * takes its place in the copy; entries the source resolves to no String
     * are copied as they are. The defaults table itself is not carried over.
     *
     * @param p the properties to clone
     *
     * @return a new Properties instance containing all entries from the source
     */
    public static Properties clone(Properties p) {
        Properties c = new Properties();
        c.putAll(p);
        // putAll(...) copies only the entries directly present in the source,
        // so materialize the String values the source resolves through
        // defaults. Re-read each value from the source: stringPropertyNames()
        // is only a snapshot and the source may be modified concurrently, so
        // a snapshotted key may resolve to nothing by the time it is read.
        // Skip only a directly copied String, which is what the source
        // resolves too; a non-String directly copied value resolves to
        // nothing on its own - getProperty(...) only falls through to the
        // defaults when the direct value is not a String - so the value the
        // source resolves replaces it, keeping the copy able to resolve every
        // String the source resolves
        for (String key : p.stringPropertyNames()) {
            String value = p.getProperty(key);
            if (value != null && !(c.get(key) instanceof String)) {
                c.setProperty(key, value);
            }
        }
        return c;
    }

    /**
     * Creates a copy of the given properties with the password entry removed.
     *
     * @param p the properties to clone
     *
     * @return a new Properties instance with all entries except the password
     */
    public static Properties cloneWithoutPassword(Properties p) {
        Properties result = clone(p);
        result.remove(PROP_PASSWORD);
        return result;
    }
}

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
     * Creates a shallow copy of the given properties.
     *
     * @param p the properties to clone
     * @return a new Properties instance containing all entries from the source
     */
    public static Properties clone(Properties p) {
        Properties c = new Properties();
        c.putAll(p);
        return c;
    }

    /**
     * Creates a copy of the given properties with the password entry removed.
     *
     * @param p the properties to clone
     * @return a new Properties instance with all entries except the password
     */
    public static Properties cloneWithoutPassword(Properties p) {
        Properties result = clone(p);
        result.remove(PROP_PASSWORD);
        return result;
    }
}

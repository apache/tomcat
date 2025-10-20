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
package org.apache.tomcat.dbcp.dbcp2;

/**
 * Constants.
 *
 * @since 2.0
 */
public class Constants {

    /**
     * Constant used to build JMX strings.
     */
    public static final String JMX_CONNECTION_POOL_BASE_EXT = ",connectionpool=";

    /**
     * Constant used to build JMX strings.
     */
    public static final String JMX_CONNECTION_POOL_PREFIX = "connections";

    /**
     * Constant used to build JMX strings.
     */
    public static final String JMX_CONNECTION_BASE_EXT = JMX_CONNECTION_POOL_BASE_EXT + JMX_CONNECTION_POOL_PREFIX + ",connection=";

    /**
     * Constant used to build JMX strings.
     */
    public static final String JMX_STATEMENT_POOL_BASE_EXT = JMX_CONNECTION_BASE_EXT;

    /**
     * Constant used to build JMX strings.
     */
    public static final String JMX_STATEMENT_POOL_PREFIX = ",statementpool=statements";

    /**
     * JDBC properties and URL key for passwords.
     *
     * @since 2.9.0
     */
    public static final String KEY_PASSWORD = "password";

    /**
     * JDBC properties and URL key for users.
     *
     * @since 2.9.0
     */
    public static final String KEY_USER = "user";
}

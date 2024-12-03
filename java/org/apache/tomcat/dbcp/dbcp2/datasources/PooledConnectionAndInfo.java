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
package org.apache.tomcat.dbcp.dbcp2.datasources;

import javax.sql.PooledConnection;

/**
 * Immutable poolable object holding a {@link PooledConnection} along with the user name and password used to create the
 * connection.
 *
 * @since 2.0
 */
final class PooledConnectionAndInfo {

    private final PooledConnection pooledConnection;

    private final UserPassKey userPassKey;

    PooledConnectionAndInfo(final PooledConnection pooledConnection, final UserPassKey userPassKey) {
        this.pooledConnection = pooledConnection;
        this.userPassKey = userPassKey;
    }

    /**
     * Gets the value of password.
     *
     * @return value of password.
     */
    String getPassword() {
        return userPassKey.getPassword();
    }

    /**
     * Gets the pooled connection.
     *
     * @return the pooled connection.
     */
    PooledConnection getPooledConnection() {
        return pooledConnection;
    }

    /**
     * Gets the value of userName.
     *
     * @return value of userName.
     */
    String getUserName() {
        return userPassKey.getUserName();
    }

    UserPassKey getUserPassKey() {
        return userPassKey;
    }
}

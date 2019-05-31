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

package org.apache.tomcat.dbcp.dbcp.datasources;

import javax.sql.PooledConnection;

/**
 * Immutable poolable object holding a PooledConnection along with the username and password
 * used to create the connection.
 *
 */
final class PooledConnectionAndInfo {
    private final PooledConnection pooledConnection;
    private final String password;
    private final String username;
    private final UserPassKey upkey;

    PooledConnectionAndInfo(PooledConnection pc, String username, String password) {
        this.pooledConnection = pc;
        this.username = username;
        this.password = password;
        upkey = new UserPassKey(username, password);
    }

    final PooledConnection getPooledConnection() {
        return pooledConnection;
    }

    final UserPassKey getUserPassKey() {
        return upkey;
    }

    /**
     * Get the value of password.
     * @return value of password.
     */
    final String getPassword() {
        return password;
    }

    /**
     * Get the value of username.
     * @return value of username.
     */
    final String getUsername() {
        return username;
    }

}

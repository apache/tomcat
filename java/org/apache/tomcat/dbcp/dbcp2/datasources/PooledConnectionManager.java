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

import java.sql.SQLException;

import javax.sql.PooledConnection;

/**
 * Methods to manage PoolableConnections and the connection pools that source them.
 *
 * @since 2.0
 */
interface PooledConnectionManager {
    /**
     * Close the PooledConnection and remove it from the connection pool
     * to which it belongs, adjusting pool counters.
     *
     * @param pc PooledConnection to be invalidated
     * @throws SQLException if an SQL error occurs closing the connection
     */
    void invalidate(PooledConnection pc) throws SQLException;

    /**
     * Sets the databsase password used when creating connections.
     *
     * @param password password used when authenticating to the database
     */
    void setPassword(String password);


    /**
     * Closes the connection pool associated with the given user.
     *
     * @param username user name
     * @throws SQLException if an error occurs closing idle connections in the pool
     */
    void closePool(String username) throws SQLException;

}

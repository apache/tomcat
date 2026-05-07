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

import java.sql.SQLException;

/**
 * JMX MBean interface for monitoring pooled connections.
 */
public interface PooledConnectionMBean {
    // PooledConnection
    /**
     * Returns the connection version.
     * @return the connection version
     */
    long getConnectionVersion();
    /**
     * Returns true if the connection has been initialized.
     * @return true if initialized
     */
    boolean isInitialized();
    /**
     * Returns true if the connection has exceeded its maximum age.
     * @return true if max age expired
     */
    boolean isMaxAgeExpired();
    /**
     * Returns true if the connection is suspected to be invalid.
     * @return true if suspect
     */
    boolean isSuspect();
    /**
     * Returns the timestamp of the last pool action.
     * @return the timestamp
     */
    long getTimestamp();
    /**
     * Returns true if the connection has been discarded.
     * @return true if discarded
     */
    boolean isDiscarded();
    /**
     * Returns the timestamp of the last successful validation.
     * @return the last validated timestamp
     */
    long getLastValidated();
    /**
     * Returns the timestamp of the last successful connection.
     * @return the last connected timestamp
     */
    long getLastConnected();
    /**
     * Returns true if the connection has been released.
     * @return true if released
     */
    boolean isReleased();

    // java.sql.Connection
    /**
     * Clears any warnings reported on this connection.
     */
    void clearWarnings();
    /**
     * Returns true if the connection has been closed.
     * @return true if closed
     * @throws SQLException if a database access error occurs
     */
    boolean isClosed() throws SQLException;
    /**
     * Returns the current auto-commit mode.
     * @return the auto-commit mode
     * @throws SQLException if a database access error occurs
     */
    boolean getAutoCommit() throws SQLException;
    /**
     * Returns the current catalog name.
     * @return the catalog name
     * @throws SQLException if a database access error occurs
     */
    String getCatalog() throws SQLException;
    /**
     * Returns the current holdability for ResultSet objects.
     * @return the holdability
     * @throws SQLException if a database access error occurs
     */
    int getHoldability() throws SQLException;
    /**
     * Returns true if the connection is in read-only mode.
     * @return true if read-only
     * @throws SQLException if a database access error occurs
     */
    boolean isReadOnly() throws SQLException;
    /**
     * Returns the current schema name.
     * @return the schema name
     * @throws SQLException if a database access error occurs
     */
    String getSchema() throws SQLException;
    /**
     * Returns the current transaction isolation level.
     * @return the transaction isolation level
     * @throws SQLException if a database access error occurs
     */
    int getTransactionIsolation() throws SQLException;
}

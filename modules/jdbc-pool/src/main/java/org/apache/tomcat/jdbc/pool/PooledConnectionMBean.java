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
 * JMX MBean interface for monitoring and managing pooled connections.
 */
public interface PooledConnectionMBean {
    // PooledConnection
    /**
     * Returns the connection version: the pool purge generation in force
     * when this pooled connection was created. The pool counter is seeded
     * from {@link Long#MIN_VALUE}, so a connection created against a freshly
     * started pool reports a large negative number; it is not a connection
     * age and not a version of the driver. Connections older than the pool's
     * current generation are retired by the pool as they are used or swept.
     *
     * @return the pool purge generation at connection creation
     */
    long getConnectionVersion();

    /**
     * Returns true if the connection has been initialized.
     *
     * @return true if initialized
     */
    boolean isInitialized();

    /**
     * Returns true if the connection has exceeded its maximum age. When a
     * maximum age is configured, a connection that currently has no physical
     * session - see {@link #getLastConnected()} - also returns true, as a
     * session that is not connected is never young enough to keep; without a
     * configured maximum age this attribute always reports false.
     *
     * @return true if max age expired
     */
    boolean isMaxAgeExpired();

    /**
     * Returns true if the connection is suspected to be invalid.
     *
     * @return true if suspect
     */
    boolean isSuspect();

    /**
     * Returns the timestamp of the last pool action on this connection.
     * Timestamps are set when the pool hands the connection out or takes it
     * back, and the value drives the abandoned-connection check - it is also
     * reset by the ResetAbandonedTimer interceptor on application calls,
     * which additionally clears the suspect flag, so a reset need not be a
     * pool action. The value starts at the time this pooled connection was
     * created.
     *
     * @return the timestamp of the last pool action as defined by
     *             {@link System#currentTimeMillis()}
     */
    long getTimestamp();

    /**
     * Returns true if the connection has been discarded.
     *
     * @return true if discarded
     */
    boolean isDiscarded();

    /**
     * Returns the timestamp at which the last successful validation completed
     * on the current physical session. While that session has not been
     * validated it reports 0, so a validation inherited from the session a
     * reconnect replaced cannot be mistaken for one of this session. Before
     * any session has been established, it reports the time this pooled
     * connection was created.
     *
     * @return the last validated timestamp as defined by
     *             {@link System#currentTimeMillis()}, or 0 while the current
     *             session has not been validated
     */
    long getLastValidated();

    /**
     * Returns the timestamp of the last successful connection, or -1 when
     * this pooled connection currently has no physical session - never
     * connected, disconnected, or mid-reconnect.
     *
     * @return the last connected timestamp as defined by
     *             {@link System#currentTimeMillis()}, or -1
     */
    long getLastConnected();

    /**
     * Returns true if the connection has been released.
     *
     * @return true if released
     */
    boolean isReleased();

    // java.sql.Connection
    /**
     * Clears any warnings reported on this connection. The operation reaches
     * a live session whether or not it is currently borrowed, so warnings an
     * application has not yet read are cleared as well. It is a no-op while
     * the pooled connection has no physical session.
     *
     * @throws SQLException if the driver refuses to clear the warnings, so a
     *             JMX client can tell a refused clear from a completed one
     */
    void clearWarnings() throws SQLException;

    /**
     * Returns true if the connection has been closed, including the case
     * where the pooled connection currently has no physical session.
     *
     * @return true if closed
     *
     * @throws SQLException if the physical connection reports a failure
     */
    boolean isClosed() throws SQLException;

    /**
     * Returns the current auto-commit mode.
     *
     * @return the auto-commit mode
     *
     * @throws SQLException if a database access error occurs
     */
    boolean getAutoCommit() throws SQLException;

    /**
     * Returns the current catalog name.
     *
     * @return the catalog name
     *
     * @throws SQLException if a database access error occurs
     */
    String getCatalog() throws SQLException;

    /**
     * Returns the current holdability for ResultSet objects.
     *
     * @return the holdability
     *
     * @throws SQLException if a database access error occurs
     */
    int getHoldability() throws SQLException;

    /**
     * Returns true if the connection is in read-only mode.
     *
     * @return true if read-only
     *
     * @throws SQLException if a database access error occurs
     */
    boolean isReadOnly() throws SQLException;

    /**
     * Returns the current schema name.
     *
     * @return the schema name
     *
     * @throws SQLException if a database access error occurs
     */
    String getSchema() throws SQLException;

    /**
     * Returns the current transaction isolation level.
     *
     * @return the transaction isolation level
     *
     * @throws SQLException if a database access error occurs
     */
    int getTransactionIsolation() throws SQLException;
}

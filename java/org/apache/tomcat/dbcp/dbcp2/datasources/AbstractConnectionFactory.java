/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tomcat.dbcp.dbcp2.datasources;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;

import org.apache.tomcat.dbcp.dbcp2.Utils;
import org.apache.tomcat.dbcp.pool2.PooledObject;

/**
 * Abstracts services for connection factories in this package.
 */
class AbstractConnectionFactory {

    protected final ConnectionPoolDataSource cpds;
    protected Duration maxConnDuration = Duration.ofMillis(-1);
    protected final boolean rollbackAfterValidation;

    /**
     * Map of PooledConnectionAndInfo instances
     */
    protected final Map<PooledConnection, PooledConnectionAndInfo> pcMap = new ConcurrentHashMap<>();

    /**
     * Map of PooledConnections for which close events are ignored. Connections are muted when they are being validated.
     */
    protected final Set<PooledConnection> validatingSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
    protected final String validationQuery;
    protected final Duration validationQueryTimeoutDuration;

    AbstractConnectionFactory(final ConnectionPoolDataSource cpds, final String validationQuery,
            final Duration validationQueryTimeoutDuration, final boolean rollbackAfterValidation) {
        this.cpds = cpds;
        this.validationQuery = validationQuery;
        this.validationQueryTimeoutDuration = validationQueryTimeoutDuration;
        this.rollbackAfterValidation = rollbackAfterValidation;
    }

    /**
     * Sets the maximum lifetime of a connection after which the connection will always fail activation,
     * passivation and validation.
     *
     * @param duration
     *            A value of zero or less indicates an infinite lifetime. The default value is -1 milliseconds.
     * @since 2.10.0
     */
    void setMaxConn(final Duration duration) {
        this.maxConnDuration = duration;
    }

    /**
     * Converts a duration to seconds where a duration less than one second becomes 1 second.
     *
     * @param duration the duration to convert.
     * @return a duration to seconds where a duration less than one second becomes 1 second.
     * @throws ArithmeticException if the query validation timeout does not fit as seconds in an int.
     */
    private int toSeconds(final Duration duration) {
        if (duration.isNegative() || duration.isZero()) {
            return 0;
        }
        final long seconds = validationQueryTimeoutDuration.getSeconds();
        return seconds != 0 ? Math.toIntExact(seconds) : 1;
    }

    protected void validateLifetime(final PooledObject<PooledConnectionAndInfo> pooledObject) throws SQLException {
        Utils.validateLifetime(pooledObject, maxConnDuration);
    }

    public boolean validateObject(final PooledObject<PooledConnectionAndInfo> pooledObject) {
        try {
            validateLifetime(pooledObject);
        } catch (final Exception e) {
            return false;
        }
        boolean valid = false;
        final PooledConnection pooledConn = pooledObject.getObject().getPooledConnection();
        Connection conn = null;
        // logical Connection from the PooledConnection must be closed
        // before another one can be requested and closing it will
        // generate an event. Keep track so we know not to return
        // the PooledConnection
        validatingSet.add(pooledConn);
        try {
            final int timeoutSeconds = toSeconds(validationQueryTimeoutDuration);
            if (validationQuery == null) {
                try {
                    conn = pooledConn.getConnection();
                    valid = conn.isValid(timeoutSeconds);
                } catch (final SQLException e) {
                    valid = false;
                }
            } else {
                Statement stmt = null;
                ResultSet rset = null;
                try {
                    conn = pooledConn.getConnection();
                    stmt = conn.createStatement();
                    if (timeoutSeconds > 0) {
                        stmt.setQueryTimeout(timeoutSeconds);
                    }
                    rset = stmt.executeQuery(validationQuery);
                    valid = rset.next();
                    if (rollbackAfterValidation) {
                        conn.rollback();
                    }
                } catch (final Exception e) {
                    valid = false;
                } finally {
                    Utils.closeQuietly((AutoCloseable) rset);
                    Utils.closeQuietly((AutoCloseable) stmt);
                }
            }
        } finally {
            Utils.closeQuietly((AutoCloseable) conn);
            validatingSet.remove(pooledConn);
        }
        return valid;
    }

}

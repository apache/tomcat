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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;

import org.apache.tomcat.dbcp.dbcp2.Utils;
import org.apache.tomcat.dbcp.pool2.KeyedObjectPool;
import org.apache.tomcat.dbcp.pool2.KeyedPooledObjectFactory;
import org.apache.tomcat.dbcp.pool2.PooledObject;
import org.apache.tomcat.dbcp.pool2.impl.DefaultPooledObject;

/**
 * A {@link KeyedPooledObjectFactory} that creates {@link org.apache.tomcat.dbcp.dbcp2.PoolableConnection
 * PoolableConnection}s.
 *
 * @since 2.0
 */
class KeyedCPDSConnectionFactory implements KeyedPooledObjectFactory<UserPassKey, PooledConnectionAndInfo>,
        ConnectionEventListener, PooledConnectionManager {

    private static final String NO_KEY_MESSAGE = "close() was called on a Connection, but "
            + "I have no record of the underlying PooledConnection.";

    private final ConnectionPoolDataSource cpds;
    private final String validationQuery;
    private final Duration validationQueryTimeoutDuration;
    private final boolean rollbackAfterValidation;
    private KeyedObjectPool<UserPassKey, PooledConnectionAndInfo> pool;
    private Duration maxConnLifetime = Duration.ofMillis(-1);

    /**
     * Map of PooledConnections for which close events are ignored. Connections are muted when they are being validated.
     */
    private final Set<PooledConnection> validatingSet = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Map of PooledConnectionAndInfo instances
     */
    private final Map<PooledConnection, PooledConnectionAndInfo> pcMap = new ConcurrentHashMap<>();

    /**
     * Creates a new {@code KeyedPoolableConnectionFactory}.
     *
     * @param cpds
     *            the ConnectionPoolDataSource from which to obtain PooledConnections
     * @param validationQuery
     *            a query to use to {@link #validateObject validate} {@link Connection}s. Should return at least one
     *            row. May be {@code null} in which case3 {@link Connection#isValid(int)} will be used to validate
     *            connections.
     * @param validationQueryTimeoutSeconds
     *            The Duration to allow for the validation query to complete
     * @param rollbackAfterValidation
     *            whether a rollback should be issued after {@link #validateObject validating} {@link Connection}s.
     * @since 2.10.0
     */
    public KeyedCPDSConnectionFactory(final ConnectionPoolDataSource cpds, final String validationQuery,
            final Duration validationQueryTimeoutSeconds, final boolean rollbackAfterValidation) {
        this.cpds = cpds;
        this.validationQuery = validationQuery;
        this.validationQueryTimeoutDuration = validationQueryTimeoutSeconds;
        this.rollbackAfterValidation = rollbackAfterValidation;
    }

    /**
     * Creates a new {@code KeyedPoolableConnectionFactory}.
     *
     * @param cpds
     *            the ConnectionPoolDataSource from which to obtain PooledConnections
     * @param validationQuery
     *            a query to use to {@link #validateObject validate} {@link Connection}s. Should return at least one
     *            row. May be {@code null} in which case3 {@link Connection#isValid(int)} will be used to validate
     *            connections.
     * @param validationQueryTimeoutSeconds
     *            The time, in seconds, to allow for the validation query to complete
     * @param rollbackAfterValidation
     *            whether a rollback should be issued after {@link #validateObject validating} {@link Connection}s.
     * @deprecated Use {@link #KeyedCPDSConnectionFactory(ConnectionPoolDataSource, String, Duration, boolean)}.
     */
    @Deprecated
    public KeyedCPDSConnectionFactory(final ConnectionPoolDataSource cpds, final String validationQuery,
            final int validationQueryTimeoutSeconds, final boolean rollbackAfterValidation) {
        this(cpds, validationQuery, Duration.ofSeconds(validationQueryTimeoutSeconds), rollbackAfterValidation);
    }

    @Override
    public void activateObject(final UserPassKey key, final PooledObject<PooledConnectionAndInfo> p) throws Exception {
        validateLifetime(p);
    }

    /**
     * This implementation does not fully close the KeyedObjectPool, as this would affect all users. Instead, it clears
     * the pool associated with the given user. This method is not currently used.
     */
    @Override
    public void closePool(final String userName) throws SQLException {
        try {
            pool.clear(new UserPassKey(userName));
        } catch (final Exception ex) {
            throw new SQLException("Error closing connection pool", ex);
        }
    }

    /**
     * This will be called if the Connection returned by the getConnection method came from a PooledConnection, and the
     * user calls the close() method of this connection object. What we need to do here is to release this
     * PooledConnection from our pool...
     */
    @Override
    public void connectionClosed(final ConnectionEvent event) {
        final PooledConnection pc = (PooledConnection) event.getSource();
        // if this event occurred because we were validating, or if this
        // connection has been marked for removal, ignore it
        // otherwise return the connection to the pool.
        if (!validatingSet.contains(pc)) {
            final PooledConnectionAndInfo pci = pcMap.get(pc);
            if (pci == null) {
                throw new IllegalStateException(NO_KEY_MESSAGE);
            }
            try {
                pool.returnObject(pci.getUserPassKey(), pci);
            } catch (final Exception e) {
                System.err.println("CLOSING DOWN CONNECTION AS IT COULD " + "NOT BE RETURNED TO THE POOL");
                pc.removeConnectionEventListener(this);
                try {
                    pool.invalidateObject(pci.getUserPassKey(), pci);
                } catch (final Exception e3) {
                    System.err.println("EXCEPTION WHILE DESTROYING OBJECT " + pci);
                    e3.printStackTrace();
                }
            }
        }
    }

    /**
     * If a fatal error occurs, close the underlying physical connection so as not to be returned in the future
     */
    @Override
    public void connectionErrorOccurred(final ConnectionEvent event) {
        final PooledConnection pc = (PooledConnection) event.getSource();
        if (null != event.getSQLException()) {
            System.err.println("CLOSING DOWN CONNECTION DUE TO INTERNAL ERROR (" + event.getSQLException() + ")");
        }
        pc.removeConnectionEventListener(this);

        final PooledConnectionAndInfo info = pcMap.get(pc);
        if (info == null) {
            throw new IllegalStateException(NO_KEY_MESSAGE);
        }
        try {
            pool.invalidateObject(info.getUserPassKey(), info);
        } catch (final Exception e) {
            System.err.println("EXCEPTION WHILE DESTROYING OBJECT " + info);
            e.printStackTrace();
        }
    }

    /**
     * Closes the PooledConnection and stops listening for events from it.
     */
    @Override
    public void destroyObject(final UserPassKey key, final PooledObject<PooledConnectionAndInfo> p) throws Exception {
        final PooledConnection pooledConnection = p.getObject().getPooledConnection();
        pooledConnection.removeConnectionEventListener(this);
        pcMap.remove(pooledConnection);
        pooledConnection.close();
    }

    /**
     * Returns the keyed object pool used to pool connections created by this factory.
     *
     * @return KeyedObjectPool managing pooled connections
     */
    public KeyedObjectPool<UserPassKey, PooledConnectionAndInfo> getPool() {
        return pool;
    }

    /**
     * Invalidates the PooledConnection in the pool. The KeyedCPDSConnectionFactory closes the connection and pool
     * counters are updated appropriately. Also clears any idle instances associated with the user name that was used to
     * create the PooledConnection. Connections associated with this user are not affected and they will not be
     * automatically closed on return to the pool.
     */
    @Override
    public void invalidate(final PooledConnection pc) throws SQLException {
        final PooledConnectionAndInfo info = pcMap.get(pc);
        if (info == null) {
            throw new IllegalStateException(NO_KEY_MESSAGE);
        }
        final UserPassKey key = info.getUserPassKey();
        try {
            pool.invalidateObject(key, info); // Destroy and update pool counters
            pool.clear(key); // Remove any idle instances with this key
        } catch (final Exception ex) {
            throw new SQLException("Error invalidating connection", ex);
        }
    }

    // ***********************************************************************
    // java.sql.ConnectionEventListener implementation
    // ***********************************************************************

    /**
     * Creates a new {@code PooledConnectionAndInfo} from the given {@code UserPassKey}.
     *
     * @param userPassKey
     *            {@code UserPassKey} containing user credentials
     * @throws SQLException
     *             if the connection could not be created.
     * @see org.apache.tomcat.dbcp.pool2.KeyedPooledObjectFactory#makeObject(java.lang.Object)
     */
    @Override
    public synchronized PooledObject<PooledConnectionAndInfo> makeObject(final UserPassKey userPassKey) throws Exception {
        PooledConnection pooledConnection = null;
        final String userName = userPassKey.getUserName();
        final String password = userPassKey.getPassword();
        if (userName == null) {
            pooledConnection = cpds.getPooledConnection();
        } else {
            pooledConnection = cpds.getPooledConnection(userName, password);
        }

        if (pooledConnection == null) {
            throw new IllegalStateException("Connection pool data source returned null from getPooledConnection");
        }

        // should we add this object as a listener or the pool.
        // consider the validateObject method in decision
        pooledConnection.addConnectionEventListener(this);
        final PooledConnectionAndInfo pci = new PooledConnectionAndInfo(pooledConnection, userPassKey);
        pcMap.put(pooledConnection, pci);

        return new DefaultPooledObject<>(pci);
    }

    @Override
    public void passivateObject(final UserPassKey key, final PooledObject<PooledConnectionAndInfo> p) throws Exception {
        validateLifetime(p);
    }

    /**
     * Sets the maximum lifetime of a connection after which the connection will always fail activation,
     * passivation and validation.
     *
     * @param maxConnLifetimeMillis
     *            A value of zero or less indicates an infinite lifetime. The default value is -1 milliseconds.
     * @since 2.10.0
     */
    public void setMaxConn(final Duration maxConnLifetimeMillis) {
        this.maxConnLifetime = maxConnLifetimeMillis;
    }

    /**
     * Sets the maximum lifetime of a connection after which the connection will always fail activation,
     * passivation and validation.
     *
     * @param maxConnLifetimeMillis
     *            A value of zero or less indicates an infinite lifetime. The default value is -1 milliseconds.
     * @since 2.9.0
     * @deprecated Use {@link #setMaxConn(Duration)}.
     */
    @Deprecated
    public void setMaxConnLifetime(final Duration maxConnLifetimeMillis) {
        this.maxConnLifetime = maxConnLifetimeMillis;
    }

    /**
     * Sets the maximum lifetime in milliseconds of a connection after which the connection will always fail activation,
     * passivation and validation.
     *
     * @param maxConnLifetimeMillis
     *            A value of zero or less indicates an infinite lifetime. The default value is -1.
     * @deprecated Use {@link #setMaxConnLifetime(Duration)}.
     */
    @Deprecated
    public void setMaxConnLifetimeMillis(final long maxConnLifetimeMillis) {
        setMaxConn(Duration.ofMillis(maxConnLifetimeMillis));
    }

    /**
     * Does nothing. This factory does not cache user credentials.
     */
    @Override
    public void setPassword(final String password) {
        // Does nothing. This factory does not cache user credentials.
    }

    public void setPool(final KeyedObjectPool<UserPassKey, PooledConnectionAndInfo> pool) {
        this.pool = pool;
    }

    private void validateLifetime(final PooledObject<PooledConnectionAndInfo> p) throws Exception {
        if (maxConnLifetime.compareTo(Duration.ZERO) > 0) {
            final Duration lifetimeDuration = Duration.between(p.getCreateInstant(), Instant.now());
            if (lifetimeDuration.compareTo(maxConnLifetime) > 0) {
                throw new Exception(Utils.getMessage("connectionFactory.lifetimeExceeded", lifetimeDuration, maxConnLifetime));
            }
        }
    }

    /**
     * Validates a pooled connection.
     *
     * @param key
     *            ignored
     * @param pooledObject
     *            wrapped {@code PooledConnectionAndInfo} containing the connection to validate
     * @return true if validation succeeds
     */
    @Override
    public boolean validateObject(final UserPassKey key, final PooledObject<PooledConnectionAndInfo> pooledObject) {
        try {
            validateLifetime(pooledObject);
        } catch (final Exception e) {
            return false;
        }
        boolean valid = false;
        final PooledConnection pooledConn = pooledObject.getObject().getPooledConnection();
        Connection conn = null;
        validatingSet.add(pooledConn);
        if (null == validationQuery) {
            Duration timeoutDuration = validationQueryTimeoutDuration;
            if (timeoutDuration.isNegative()) {
                timeoutDuration = Duration.ZERO;
            }
            try {
                conn = pooledConn.getConnection();
                valid = conn.isValid((int) timeoutDuration.getSeconds());
            } catch (final SQLException e) {
                valid = false;
            } finally {
                Utils.closeQuietly((AutoCloseable) conn);
                validatingSet.remove(pooledConn);
            }
        } else {
            Statement stmt = null;
            ResultSet rset = null;
            // logical Connection from the PooledConnection must be closed
            // before another one can be requested and closing it will
            // generate an event. Keep track so we know not to return
            // the PooledConnection
            validatingSet.add(pooledConn);
            try {
                conn = pooledConn.getConnection();
                stmt = conn.createStatement();
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
                Utils.closeQuietly((AutoCloseable) conn);
                validatingSet.remove(pooledConn);
            }
        }
        return valid;
    }
}

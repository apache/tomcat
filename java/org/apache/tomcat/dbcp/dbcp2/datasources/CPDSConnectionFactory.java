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
import org.apache.tomcat.dbcp.pool2.ObjectPool;
import org.apache.tomcat.dbcp.pool2.PooledObject;
import org.apache.tomcat.dbcp.pool2.PooledObjectFactory;
import org.apache.tomcat.dbcp.pool2.impl.DefaultPooledObject;

/**
 * A {@link PooledObjectFactory} that creates {@link org.apache.tomcat.dbcp.dbcp2.PoolableConnection PoolableConnection}s.
 *
 * @since 2.0
 */
class CPDSConnectionFactory
        implements PooledObjectFactory<PooledConnectionAndInfo>, ConnectionEventListener, PooledConnectionManager {

    private static final String NO_KEY_MESSAGE = "close() was called on a Connection, but I have no record of the underlying PooledConnection.";

    private final ConnectionPoolDataSource cpds;
    private final String validationQuery;
    private final Duration validationQueryTimeoutDuration;
    private final boolean rollbackAfterValidation;
    private ObjectPool<PooledConnectionAndInfo> pool;
    private UserPassKey userPassKey;
    private Duration maxConnDuration = Duration.ofMillis(-1);

    /**
     * Map of PooledConnections for which close events are ignored. Connections are muted when they are being validated.
     */
    private final Set<PooledConnection> validatingSet = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Map of PooledConnectionAndInfo instances
     */
    private final Map<PooledConnection, PooledConnectionAndInfo> pcMap = new ConcurrentHashMap<>();

    /**
     * Creates a new {@code PoolableConnectionFactory}.
     *
     * @param cpds
     *            the ConnectionPoolDataSource from which to obtain PooledConnection's
     * @param validationQuery
     *            a query to use to {@link #validateObject validate} {@link Connection}s. Should return at least one
     *            row. May be {@code null} in which case {@link Connection#isValid(int)} will be used to validate
     *            connections.
     * @param validationQueryTimeoutDuration
     *            Timeout Duration before validation fails
     * @param rollbackAfterValidation
     *            whether a rollback should be issued after {@link #validateObject validating} {@link Connection}s.
     * @param userName
     *            The user name to use to create connections
     * @param userPassword
     *            The password to use to create connections
     * @since 2.10.0
     */
    public CPDSConnectionFactory(final ConnectionPoolDataSource cpds, final String validationQuery,
            final Duration validationQueryTimeoutDuration, final boolean rollbackAfterValidation, final String userName,
        final char[] userPassword) {
        this.cpds = cpds;
        this.validationQuery = validationQuery;
        this.validationQueryTimeoutDuration = validationQueryTimeoutDuration;
        this.userPassKey = new UserPassKey(userName, userPassword);
        this.rollbackAfterValidation = rollbackAfterValidation;
    }

    /**
     * Creates a new {@code PoolableConnectionFactory}.
     *
     * @param cpds
     *            the ConnectionPoolDataSource from which to obtain PooledConnection's
     * @param validationQuery
     *            a query to use to {@link #validateObject validate} {@link Connection}s. Should return at least one
     *            row. May be {@code null} in which case {@link Connection#isValid(int)} will be used to validate
     *            connections.
     * @param validationQueryTimeoutDuration
     *            Timeout in seconds before validation fails
     * @param rollbackAfterValidation
     *            whether a rollback should be issued after {@link #validateObject validating} {@link Connection}s.
     * @param userName
     *            The user name to use to create connections
     * @param userPassword
     *            The password to use to create connections
     * @since 2.10.0
     */
    public CPDSConnectionFactory(final ConnectionPoolDataSource cpds, final String validationQuery, final Duration validationQueryTimeoutDuration,
        final boolean rollbackAfterValidation, final String userName, final String userPassword) {
        this(cpds, validationQuery, validationQueryTimeoutDuration, rollbackAfterValidation, userName, Utils.toCharArray(userPassword));
    }

    /**
     * Creates a new {@code PoolableConnectionFactory}.
     *
     * @param cpds
     *            the ConnectionPoolDataSource from which to obtain PooledConnection's
     * @param validationQuery
     *            a query to use to {@link #validateObject validate} {@link Connection}s. Should return at least one
     *            row. May be {@code null} in which case {@link Connection#isValid(int)} will be used to validate
     *            connections.
     * @param validationQueryTimeoutSeconds
     *            Timeout in seconds before validation fails
     * @param rollbackAfterValidation
     *            whether a rollback should be issued after {@link #validateObject validating} {@link Connection}s.
     * @param userName
     *            The user name to use to create connections
     * @param userPassword
     *            The password to use to create connections
     * @since 2.4.0
     * @deprecated Use {@link #CPDSConnectionFactory(ConnectionPoolDataSource, String, Duration, boolean, String, char[])}.
     */
    @Deprecated
    public CPDSConnectionFactory(final ConnectionPoolDataSource cpds, final String validationQuery,
            final int validationQueryTimeoutSeconds, final boolean rollbackAfterValidation, final String userName,
        final char[] userPassword) {
        this.cpds = cpds;
        this.validationQuery = validationQuery;
        this.validationQueryTimeoutDuration = Duration.ofSeconds(validationQueryTimeoutSeconds);
        this.userPassKey = new UserPassKey(userName, userPassword);
        this.rollbackAfterValidation = rollbackAfterValidation;
    }

    /**
     * Creates a new {@code PoolableConnectionFactory}.
     *
     * @param cpds
     *            the ConnectionPoolDataSource from which to obtain PooledConnection's
     * @param validationQuery
     *            a query to use to {@link #validateObject validate} {@link Connection}s. Should return at least one
     *            row. May be {@code null} in which case {@link Connection#isValid(int)} will be used to validate
     *            connections.
     * @param validationQueryTimeoutSeconds
     *            Timeout in seconds before validation fails
     * @param rollbackAfterValidation
     *            whether a rollback should be issued after {@link #validateObject validating} {@link Connection}s.
     * @param userName
     *            The user name to use to create connections
     * @param userPassword
     *            The password to use to create connections
     * @deprecated Use {@link #CPDSConnectionFactory(ConnectionPoolDataSource, String, Duration, boolean, String, String)}.
     */
    @Deprecated
    public CPDSConnectionFactory(final ConnectionPoolDataSource cpds, final String validationQuery, final int validationQueryTimeoutSeconds,
            final boolean rollbackAfterValidation, final String userName, final String userPassword) {
        this(cpds, validationQuery, validationQueryTimeoutSeconds, rollbackAfterValidation, userName, Utils.toCharArray(userPassword));
    }

    @Override
    public void activateObject(final PooledObject<PooledConnectionAndInfo> p) throws Exception {
        validateLifetime(p);
    }

    /**
     * Verifies that the user name matches the user whose connections are being managed by this factory and closes the
     * pool if this is the case; otherwise does nothing.
     */
    @Override
    public void closePool(final String userName) throws SQLException {
        synchronized (this) {
            if (userName == null || !userName.equals(this.userPassKey.getUserName())) {
                return;
            }
        }
        try {
            pool.close();
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
        // if this event occurred because we were validating, ignore it
        // otherwise return the connection to the pool.
        if (!validatingSet.contains(pc)) {
            final PooledConnectionAndInfo pci = pcMap.get(pc);
            if (pci == null) {
                throw new IllegalStateException(NO_KEY_MESSAGE);
            }

            try {
                pool.returnObject(pci);
            } catch (final Exception e) {
                System.err.println("CLOSING DOWN CONNECTION AS IT COULD " + "NOT BE RETURNED TO THE POOL");
                pc.removeConnectionEventListener(this);
                try {
                    doDestroyObject(pci);
                } catch (final Exception e2) {
                    System.err.println("EXCEPTION WHILE DESTROYING OBJECT " + pci);
                    e2.printStackTrace();
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

        final PooledConnectionAndInfo pci = pcMap.get(pc);
        if (pci == null) {
            throw new IllegalStateException(NO_KEY_MESSAGE);
        }
        try {
            pool.invalidateObject(pci);
        } catch (final Exception e) {
            System.err.println("EXCEPTION WHILE DESTROYING OBJECT " + pci);
            e.printStackTrace();
        }
    }

    /**
     * Closes the PooledConnection and stops listening for events from it.
     */
    @Override
    public void destroyObject(final PooledObject<PooledConnectionAndInfo> p) throws Exception {
        doDestroyObject(p.getObject());
    }

    private void doDestroyObject(final PooledConnectionAndInfo pci) throws Exception {
        final PooledConnection pc = pci.getPooledConnection();
        pc.removeConnectionEventListener(this);
        pcMap.remove(pc);
        pc.close();
    }

    /**
     * (Testing API) Gets the value of password for the default user.
     *
     * @return value of password.
     */
    char[] getPasswordCharArray() {
        return userPassKey.getPasswordCharArray();
    }

    /**
     * Returns the object pool used to pool connections created by this factory.
     *
     * @return ObjectPool managing pooled connections
     */
    public ObjectPool<PooledConnectionAndInfo> getPool() {
        return pool;
    }

    /**
     * Invalidates the PooledConnection in the pool. The CPDSConnectionFactory closes the connection and pool counters
     * are updated appropriately. Also closes the pool. This ensures that all idle connections are closed and
     * connections that are checked out are closed on return.
     */
    @Override
    public void invalidate(final PooledConnection pc) throws SQLException {
        final PooledConnectionAndInfo pci = pcMap.get(pc);
        if (pci == null) {
            throw new IllegalStateException(NO_KEY_MESSAGE);
        }
        try {
            pool.invalidateObject(pci); // Destroy instance and update pool counters
            pool.close(); // Clear any other instances in this pool and kill others as they come back
        } catch (final Exception ex) {
            throw new SQLException("Error invalidating connection", ex);
        }
    }

    // ***********************************************************************
    // java.sql.ConnectionEventListener implementation
    // ***********************************************************************

    @Override
    public synchronized PooledObject<PooledConnectionAndInfo> makeObject() {
        final PooledConnectionAndInfo pci;
        try {
            PooledConnection pc = null;
            if (userPassKey.getUserName() == null) {
                pc = cpds.getPooledConnection();
            } else {
                pc = cpds.getPooledConnection(userPassKey.getUserName(), userPassKey.getPassword());
            }

            if (pc == null) {
                throw new IllegalStateException("Connection pool data source returned null from getPooledConnection");
            }

            // should we add this object as a listener or the pool.
            // consider the validateObject method in decision
            pc.addConnectionEventListener(this);
            pci = new PooledConnectionAndInfo(pc, userPassKey);
            pcMap.put(pc, pci);
        } catch (final SQLException e) {
            throw new RuntimeException(e.getMessage());
        }
        return new DefaultPooledObject<>(pci);
    }

    @Override
    public void passivateObject(final PooledObject<PooledConnectionAndInfo> p) throws Exception {
        validateLifetime(p);
    }

    /**
     * Sets the maximum Duration of a connection after which the connection will always fail activation,
     * passivation and validation.
     *
     * @param maxConnDuration
     *            A value of zero or less indicates an infinite lifetime. The default value is -1 milliseconds.
     * @since 2.10.0
     */
    public void setMaxConn(final Duration maxConnDuration) {
        this.maxConnDuration = maxConnDuration;
    }

    /**
     * Sets the maximum lifetime in milliseconds of a connection after which the connection will always fail activation,
     * passivation and validation.
     *
     * @param maxConnDuration
     *            A value of zero or less indicates an infinite lifetime. The default value is -1 milliseconds.
     * @since 2.9.0
     * @deprecated Use {@link #setMaxConn(Duration)}.
     */
    @Deprecated
    public void setMaxConnLifetime(final Duration maxConnDuration) {
        this.maxConnDuration = maxConnDuration;
    }

    /**
     * Sets the maximum lifetime in milliseconds of a connection after which the connection will always fail activation,
     * passivation and validation.
     *
     * @param maxConnLifetimeMillis
     *            A value of zero or less indicates an infinite lifetime. The default value is -1.
     * @deprecated Use {@link #setMaxConn(Duration)}.
     */
    @Deprecated
    public void setMaxConnLifetimeMillis(final long maxConnLifetimeMillis) {
        setMaxConnLifetime(Duration.ofMillis(maxConnLifetimeMillis));
    }

    /**
     * Sets the database password used when creating new connections.
     *
     * @param userPassword
     *            new password
     */
    public synchronized void setPassword(final char[] userPassword) {
        this.userPassKey = new UserPassKey(userPassKey.getUserName(), userPassword);
    }

    /**
     * Sets the database password used when creating new connections.
     *
     * @param userPassword
     *            new password
     */
    @Override
    public synchronized void setPassword(final String userPassword) {
        this.userPassKey = new UserPassKey(userPassKey.getUserName(), userPassword);
    }

    /**
     *
     * @param pool
     *            the {@link ObjectPool} in which to pool those {@link Connection}s
     */
    public void setPool(final ObjectPool<PooledConnectionAndInfo> pool) {
        this.pool = pool;
    }

    /**
     * @since 2.6.0
     */
    @Override
    public synchronized String toString() {
        final StringBuilder builder = new StringBuilder(super.toString());
        builder.append("[cpds=");
        builder.append(cpds);
        builder.append(", validationQuery=");
        builder.append(validationQuery);
        builder.append(", validationQueryTimeoutDuration=");
        builder.append(validationQueryTimeoutDuration);
        builder.append(", rollbackAfterValidation=");
        builder.append(rollbackAfterValidation);
        builder.append(", pool=");
        builder.append(pool);
        builder.append(", maxConnDuration=");
        builder.append(maxConnDuration);
        builder.append(", validatingSet=");
        builder.append(validatingSet);
        builder.append(", pcMap=");
        builder.append(pcMap);
        builder.append("]");
        return builder.toString();
    }

    private void validateLifetime(final PooledObject<PooledConnectionAndInfo> pooledObject) throws Exception {
        if (maxConnDuration.compareTo(Duration.ZERO) > 0) {
            final Duration lifetimeDuration = Duration.between(pooledObject.getCreateInstant(), Instant.now());
            if (lifetimeDuration.compareTo(maxConnDuration) > 0) {
                throw new Exception(Utils.getMessage("connectionFactory.lifetimeExceeded", lifetimeDuration, maxConnDuration));
            }
        }
    }

    @Override
    public boolean validateObject(final PooledObject<PooledConnectionAndInfo> p) {
        try {
            validateLifetime(p);
        } catch (final Exception e) {
            return false;
        }
        boolean valid = false;
        final PooledConnection pconn = p.getObject().getPooledConnection();
        Connection conn = null;
        validatingSet.add(pconn);
        if (null == validationQuery) {
            Duration timeoutDuration = validationQueryTimeoutDuration;
            if (timeoutDuration.isNegative()) {
                timeoutDuration = Duration.ZERO;
            }
            try {
                conn = pconn.getConnection();
                valid = conn.isValid((int) timeoutDuration.getSeconds());
            } catch (final SQLException e) {
                valid = false;
            } finally {
                Utils.closeQuietly((AutoCloseable) conn);
                validatingSet.remove(pconn);
            }
        } else {
            Statement stmt = null;
            ResultSet rset = null;
            // logical Connection from the PooledConnection must be closed
            // before another one can be requested and closing it will
            // generate an event. Keep track so we know not to return
            // the PooledConnection
            validatingSet.add(pconn);
            try {
                conn = pconn.getConnection();
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
                validatingSet.remove(pconn);
            }
        }
        return valid;
    }
}

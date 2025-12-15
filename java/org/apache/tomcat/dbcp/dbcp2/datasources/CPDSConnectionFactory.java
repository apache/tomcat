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
import java.sql.SQLException;
import java.time.Duration;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;

import org.apache.tomcat.dbcp.pool2.ObjectPool;
import org.apache.tomcat.dbcp.pool2.PooledObject;
import org.apache.tomcat.dbcp.pool2.PooledObjectFactory;
import org.apache.tomcat.dbcp.pool2.impl.DefaultPooledObject;

/**
 * A {@link PooledObjectFactory} that creates {@link org.apache.tomcat.dbcp.dbcp2.PoolableConnection PoolableConnection}s.
 *
 * @since 2.0
 */
final class CPDSConnectionFactory extends AbstractConnectionFactory
        implements PooledObjectFactory<PooledConnectionAndInfo>, ConnectionEventListener, PooledConnectionManager {

    private static final String NO_KEY_MESSAGE = "close() was called on a Connection, but I have no record of the underlying PooledConnection.";

    private ObjectPool<PooledConnectionAndInfo> pool;
    private UserPassKey userPassKey;

    /**
     * Creates a new {@link PoolableConnectionFactory}.
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
    CPDSConnectionFactory(final ConnectionPoolDataSource cpds, final String validationQuery,
            final Duration validationQueryTimeoutDuration, final boolean rollbackAfterValidation, final String userName,
            final char[] userPassword) {
        super(cpds, validationQuery, validationQueryTimeoutDuration, rollbackAfterValidation);
        this.userPassKey = new UserPassKey(userName, userPassword);
    }

    @Override
    public void activateObject(final PooledObject<PooledConnectionAndInfo> pooledObject) throws SQLException {
        validateLifetime(pooledObject);
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
                System.err.println("CLOSING DOWN CONNECTION AS IT COULD NOT BE RETURNED TO THE POOL");
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
    public void destroyObject(final PooledObject<PooledConnectionAndInfo> p) throws SQLException {
        doDestroyObject(p.getObject());
    }

    private void doDestroyObject(final PooledConnectionAndInfo pci) throws SQLException {
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
            pool.close(); // Clear any other instances in this pool and kill others as they come back
            // Calling close before invalidate ensures that invalidate will not trigger a create attempt
            pool.invalidateObject(pci); // Destroy instance and update pool counters
        } catch (final Exception ex) {
            throw new SQLException("Error invalidating connection", ex);
        }
    }

    @Override
    public synchronized PooledObject<PooledConnectionAndInfo> makeObject() throws SQLException {
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
        final PooledConnectionAndInfo pci = new PooledConnectionAndInfo(pc, userPassKey);
        pcMap.put(pc, pci);
        return new DefaultPooledObject<>(pci);
    }

    @Override
    public void passivateObject(final PooledObject<PooledConnectionAndInfo> p) throws SQLException {
        validateLifetime(p);
    }

    /**
     * Sets the database password used when creating new connections.
     *
     * @param userPassword
     *            new password
     */
    @Override
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
}

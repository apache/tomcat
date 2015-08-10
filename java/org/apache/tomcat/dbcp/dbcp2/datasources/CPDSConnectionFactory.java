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
 * A {@link PooledObjectFactory} that creates
 * {@link org.apache.tomcat.dbcp.dbcp2.PoolableConnection PoolableConnection}s.
 *
 * @author John D. McNally
 * @since 2.0
 */
class CPDSConnectionFactory
        implements PooledObjectFactory<PooledConnectionAndInfo>,
        ConnectionEventListener, PooledConnectionManager {

    private static final String NO_KEY_MESSAGE
            = "close() was called on a Connection, but "
            + "I have no record of the underlying PooledConnection.";

    private final ConnectionPoolDataSource _cpds;
    private final String _validationQuery;
    private final int _validationQueryTimeout;
    private final boolean _rollbackAfterValidation;
    private ObjectPool<PooledConnectionAndInfo> _pool;
    private final String _username;
    private String _password = null;
    private long maxConnLifetimeMillis = -1;


    /**
     * Map of PooledConnections for which close events are ignored.
     * Connections are muted when they are being validated.
     */
    private final Set<PooledConnection> validatingSet =
            Collections.newSetFromMap(new ConcurrentHashMap<PooledConnection,Boolean>());

    /**
     * Map of PooledConnectionAndInfo instances
     */
    private final Map<PooledConnection, PooledConnectionAndInfo> pcMap =
        new ConcurrentHashMap<>();

    /**
     * Create a new {@code PoolableConnectionFactory}.
     *
     * @param cpds the ConnectionPoolDataSource from which to obtain
     * PooledConnection's
     * @param validationQuery a query to use to {@link #validateObject
     * validate} {@link Connection}s. Should return at least one row.
     * May be {@code null} in which case {@link Connection#isValid(int)} will
     * be used to validate connections.
     * @param validationQueryTimeout Timeout in seconds before validation fails
     * @param rollbackAfterValidation whether a rollback should be issued
     * after {@link #validateObject validating} {@link Connection}s.
     * @param username
     * @param password
     */
    public CPDSConnectionFactory(ConnectionPoolDataSource cpds,
                                 String validationQuery,
                                 int validationQueryTimeout,
                                 boolean rollbackAfterValidation,
                                 String username,
                                 String password) {
        _cpds = cpds;
        _validationQuery = validationQuery;
        _validationQueryTimeout = validationQueryTimeout;
        _username = username;
        _password = password;
        _rollbackAfterValidation = rollbackAfterValidation;
    }

    /**
     * Returns the object pool used to pool connections created by this factory.
     *
     * @return ObjectPool managing pooled connections
     */
    public ObjectPool<PooledConnectionAndInfo> getPool() {
        return _pool;
    }

    /**
     *
     * @param pool the {@link ObjectPool} in which to pool those {@link
     * Connection}s
     */
    public void setPool(ObjectPool<PooledConnectionAndInfo> pool) {
        this._pool = pool;
    }

    @Override
    public synchronized PooledObject<PooledConnectionAndInfo> makeObject() {
        PooledConnectionAndInfo pci;
        try {
            PooledConnection pc = null;
            if (_username == null) {
                pc = _cpds.getPooledConnection();
            } else {
                pc = _cpds.getPooledConnection(_username, _password);
            }

            if (pc == null) {
                throw new IllegalStateException("Connection pool data source returned null from getPooledConnection");
            }

            // should we add this object as a listener or the pool.
            // consider the validateObject method in decision
            pc.addConnectionEventListener(this);
            pci = new PooledConnectionAndInfo(pc, _username, _password);
            pcMap.put(pc, pci);
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage());
        }
        return new DefaultPooledObject<>(pci);
    }

    /**
     * Closes the PooledConnection and stops listening for events from it.
     */
    @Override
    public void destroyObject(PooledObject<PooledConnectionAndInfo> p) throws Exception {
        doDestroyObject(p.getObject());
    }

    private void doDestroyObject(PooledConnectionAndInfo pci) throws Exception{
        PooledConnection pc = pci.getPooledConnection();
        pc.removeConnectionEventListener(this);
        pcMap.remove(pc);
        pc.close();
    }

    @Override
    public boolean validateObject(PooledObject<PooledConnectionAndInfo> p) {
        try {
            validateLifetime(p);
        } catch (Exception e) {
            return false;
        }
        boolean valid = false;
        PooledConnection pconn = p.getObject().getPooledConnection();
        Connection conn = null;
        validatingSet.add(pconn);
        if (null == _validationQuery) {
            int timeout = _validationQueryTimeout;
            if (timeout < 0) {
                timeout = 0;
            }
            try {
                conn = pconn.getConnection();
                valid = conn.isValid(timeout);
            } catch (SQLException e) {
                valid = false;
            } finally {
                Utils.closeQuietly(conn);
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
                rset = stmt.executeQuery(_validationQuery);
                if (rset.next()) {
                    valid = true;
                } else {
                    valid = false;
                }
                if (_rollbackAfterValidation) {
                    conn.rollback();
                }
            } catch (Exception e) {
                valid = false;
            } finally {
                Utils.closeQuietly(rset);
                Utils.closeQuietly(stmt);
                Utils.closeQuietly(conn);
                validatingSet.remove(pconn);
            }
        }
        return valid;
    }

    @Override
    public void passivateObject(PooledObject<PooledConnectionAndInfo> p)
            throws Exception {
        validateLifetime(p);
    }

    @Override
    public void activateObject(PooledObject<PooledConnectionAndInfo> p)
            throws Exception {
        validateLifetime(p);
    }

    // ***********************************************************************
    // java.sql.ConnectionEventListener implementation
    // ***********************************************************************

    /**
     * This will be called if the Connection returned by the getConnection
     * method came from a PooledConnection, and the user calls the close()
     * method of this connection object. What we need to do here is to
     * release this PooledConnection from our pool...
     */
    @Override
    public void connectionClosed(ConnectionEvent event) {
        PooledConnection pc = (PooledConnection) event.getSource();
        // if this event occurred because we were validating, ignore it
        // otherwise return the connection to the pool.
        if (!validatingSet.contains(pc)) {
            PooledConnectionAndInfo pci = pcMap.get(pc);
            if (pci == null) {
                throw new IllegalStateException(NO_KEY_MESSAGE);
            }

            try {
                _pool.returnObject(pci);
            } catch (Exception e) {
                System.err.println("CLOSING DOWN CONNECTION AS IT COULD "
                        + "NOT BE RETURNED TO THE POOL");
                pc.removeConnectionEventListener(this);
                try {
                    doDestroyObject(pci);
                } catch (Exception e2) {
                    System.err.println("EXCEPTION WHILE DESTROYING OBJECT "
                            + pci);
                    e2.printStackTrace();
                }
            }
        }
    }

    /**
     * If a fatal error occurs, close the underlying physical connection so as
     * not to be returned in the future
     */
    @Override
    public void connectionErrorOccurred(ConnectionEvent event) {
        PooledConnection pc = (PooledConnection)event.getSource();
        if (null != event.getSQLException()) {
            System.err.println(
                    "CLOSING DOWN CONNECTION DUE TO INTERNAL ERROR ("
                    + event.getSQLException() + ")");
        }
        pc.removeConnectionEventListener(this);

        PooledConnectionAndInfo pci = pcMap.get(pc);
        if (pci == null) {
            throw new IllegalStateException(NO_KEY_MESSAGE);
        }
        try {
            _pool.invalidateObject(pci);
        } catch (Exception e) {
            System.err.println("EXCEPTION WHILE DESTROYING OBJECT " + pci);
            e.printStackTrace();
        }
    }

    // ***********************************************************************
    // PooledConnectionManager implementation
    // ***********************************************************************

    /**
     * Invalidates the PooledConnection in the pool.  The CPDSConnectionFactory
     * closes the connection and pool counters are updated appropriately.
     * Also closes the pool.  This ensures that all idle connections are closed
     * and connections that are checked out are closed on return.
     */
    @Override
    public void invalidate(PooledConnection pc) throws SQLException {
        PooledConnectionAndInfo pci = pcMap.get(pc);
        if (pci == null) {
            throw new IllegalStateException(NO_KEY_MESSAGE);
        }
        try {
            _pool.invalidateObject(pci);  // Destroy instance and update pool counters
            _pool.close();  // Clear any other instances in this pool and kill others as they come back
        } catch (Exception ex) {
            throw new SQLException("Error invalidating connection", ex);
        }
    }

    /**
     * Sets the database password used when creating new connections.
     *
     * @param password new password
     */
    @Override
    public synchronized void setPassword(String password) {
        _password = password;
    }

    /**
     * Sets the maximum lifetime in milliseconds of a connection after which the
     * connection will always fail activation, passivation and validation. A
     * value of zero or less indicates an infinite lifetime. The default value
     * is -1.
     */
    public void setMaxConnLifetimeMillis(long maxConnLifetimeMillis) {
        this.maxConnLifetimeMillis = maxConnLifetimeMillis;
    }

    /**
     * Verifies that the username matches the user whose connections are being managed by this
     * factory and closes the pool if this is the case; otherwise does nothing.
     */
    @Override
    public void closePool(String username) throws SQLException {
        synchronized (this) {
            if (username == null || !username.equals(_username)) {
                return;
            }
        }
        try {
            _pool.close();
        } catch (Exception ex) {
            throw new SQLException("Error closing connection pool", ex);
        }
    }

    private void validateLifetime(PooledObject<PooledConnectionAndInfo> p)
            throws Exception {
        if (maxConnLifetimeMillis > 0) {
            long lifetime = System.currentTimeMillis() - p.getCreateTime();
            if (lifetime > maxConnLifetimeMillis) {
                throw new Exception(Utils.getMessage(
                        "connectionFactory.lifetimeExceeded",
                        Long.valueOf(lifetime),
                        Long.valueOf(maxConnLifetimeMillis)));
            }
        }
    }
}

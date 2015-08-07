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
import org.apache.tomcat.dbcp.pool2.KeyedObjectPool;
import org.apache.tomcat.dbcp.pool2.KeyedPooledObjectFactory;
import org.apache.tomcat.dbcp.pool2.PooledObject;
import org.apache.tomcat.dbcp.pool2.impl.DefaultPooledObject;

/**
 * A {@link KeyedPooledObjectFactory} that creates
 * {@link org.apache.tomcat.dbcp.dbcp2.PoolableConnection PoolableConnection}s.
 *
 * @author John D. McNally
 * @since 2.0
 */
class KeyedCPDSConnectionFactory
    implements KeyedPooledObjectFactory<UserPassKey,PooledConnectionAndInfo>,
    ConnectionEventListener, PooledConnectionManager {

    private static final String NO_KEY_MESSAGE
            = "close() was called on a Connection, but "
            + "I have no record of the underlying PooledConnection.";

    private final ConnectionPoolDataSource _cpds;
    private final String _validationQuery;
    private final int _validationQueryTimeout;
    private final boolean _rollbackAfterValidation;
    private KeyedObjectPool<UserPassKey,PooledConnectionAndInfo> _pool;
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
     * Create a new {@code KeyedPoolableConnectionFactory}.
     * @param cpds the ConnectionPoolDataSource from which to obtain
     * PooledConnections
     * @param validationQuery a query to use to {@link #validateObject validate}
     * {@link Connection}s.  Should return at least one row. May be
     * {@code null} in which case3 {@link Connection#isValid(int)} will be used
     * to validate connections.
     * @param rollbackAfterValidation whether a rollback should be issued after
     * {@link #validateObject validating} {@link Connection}s.
     */
    public KeyedCPDSConnectionFactory(ConnectionPoolDataSource cpds,
                                      String validationQuery,
                                      int validationQueryTimeout,
                                      boolean rollbackAfterValidation) {
        _cpds = cpds;
        _validationQuery = validationQuery;
        _validationQueryTimeout = validationQueryTimeout;
        _rollbackAfterValidation = rollbackAfterValidation;
    }

    public void setPool(KeyedObjectPool<UserPassKey,PooledConnectionAndInfo> pool) {
        this._pool = pool;
    }

    /**
     * Returns the keyed object pool used to pool connections created by this factory.
     *
     * @return KeyedObjectPool managing pooled connections
     */
    public KeyedObjectPool<UserPassKey,PooledConnectionAndInfo> getPool() {
        return _pool;
    }

    /**
     * Creates a new {@link PooledConnectionAndInfo} from the given {@link UserPassKey}.
     *
     * @param upkey {@link UserPassKey} containing user credentials
     * @throws SQLException if the connection could not be created.
     * @see org.apache.tomcat.dbcp.pool2.KeyedPooledObjectFactory#makeObject(java.lang.Object)
     */
    @Override
    public synchronized PooledObject<PooledConnectionAndInfo> makeObject(UserPassKey upkey)
            throws Exception {
        PooledConnectionAndInfo pci = null;

        PooledConnection pc = null;
        String username = upkey.getUsername();
        String password = upkey.getPassword();
        if (username == null) {
            pc = _cpds.getPooledConnection();
        } else {
            pc = _cpds.getPooledConnection(username, password);
        }

        if (pc == null) {
            throw new IllegalStateException("Connection pool data source returned null from getPooledConnection");
        }

        // should we add this object as a listener or the pool.
        // consider the validateObject method in decision
        pc.addConnectionEventListener(this);
        pci = new PooledConnectionAndInfo(pc, username, password);
        pcMap.put(pc, pci);

        return new DefaultPooledObject<>(pci);
    }

    /**
     * Closes the PooledConnection and stops listening for events from it.
     */
    @Override
    public void destroyObject(UserPassKey key, PooledObject<PooledConnectionAndInfo> p)
            throws Exception {
        PooledConnection pc = p.getObject().getPooledConnection();
        pc.removeConnectionEventListener(this);
        pcMap.remove(pc);
        pc.close();
    }

    /**
     * Validates a pooled connection.
     *
     * @param key ignored
     * @param p wrapped {@link PooledConnectionAndInfo} containing the
     *          connection to validate
     * @return true if validation succeeds
     */
    @Override
    public boolean validateObject(UserPassKey key,
            PooledObject<PooledConnectionAndInfo> p) {
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
            } catch(Exception e) {
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
    public void passivateObject(UserPassKey key,
            PooledObject<PooledConnectionAndInfo> p) throws Exception {
        validateLifetime(p);
    }

    @Override
    public void activateObject(UserPassKey key,
            PooledObject<PooledConnectionAndInfo> p) throws Exception {
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
        PooledConnection pc = (PooledConnection)event.getSource();
        // if this event occurred because we were validating, or if this
        // connection has been marked for removal, ignore it
        // otherwise return the connection to the pool.
        if (!validatingSet.contains(pc)) {
            PooledConnectionAndInfo pci = pcMap.get(pc);
            if (pci == null) {
                throw new IllegalStateException(NO_KEY_MESSAGE);
            }
            try {
                _pool.returnObject(pci.getUserPassKey(), pci);
            } catch (Exception e) {
                System.err.println("CLOSING DOWN CONNECTION AS IT COULD " +
                "NOT BE RETURNED TO THE POOL");
                pc.removeConnectionEventListener(this);
                try {
                    _pool.invalidateObject(pci.getUserPassKey(), pci);
                } catch (Exception e3) {
                    System.err.println("EXCEPTION WHILE DESTROYING OBJECT " +
                            pci);
                    e3.printStackTrace();
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
            System.err
                .println("CLOSING DOWN CONNECTION DUE TO INTERNAL ERROR (" +
                         event.getSQLException() + ")");
        }
        pc.removeConnectionEventListener(this);

        PooledConnectionAndInfo info = pcMap.get(pc);
        if (info == null) {
            throw new IllegalStateException(NO_KEY_MESSAGE);
        }
        try {
            _pool.invalidateObject(info.getUserPassKey(), info);
        } catch (Exception e) {
            System.err.println("EXCEPTION WHILE DESTROYING OBJECT " + info);
            e.printStackTrace();
        }
    }

    // ***********************************************************************
    // PooledConnectionManager implementation
    // ***********************************************************************

    /**
     * Invalidates the PooledConnection in the pool.  The KeyedCPDSConnectionFactory
     * closes the connection and pool counters are updated appropriately.
     * Also clears any idle instances associated with the username that was used
     * to create the PooledConnection.  Connections associated with this user
     * are not affected and they will not be automatically closed on return to the pool.
     */
    @Override
    public void invalidate(PooledConnection pc) throws SQLException {
        PooledConnectionAndInfo info = pcMap.get(pc);
        if (info == null) {
            throw new IllegalStateException(NO_KEY_MESSAGE);
        }
        UserPassKey key = info.getUserPassKey();
        try {
            _pool.invalidateObject(key, info);  // Destroy and update pool counters
            _pool.clear(key); // Remove any idle instances with this key
        } catch (Exception ex) {
            throw new SQLException("Error invalidating connection", ex);
        }
    }

    /**
     * Does nothing.  This factory does not cache user credentials.
     */
    @Override
    public void setPassword(String password) {
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
     * This implementation does not fully close the KeyedObjectPool, as
     * this would affect all users.  Instead, it clears the pool associated
     * with the given user.  This method is not currently used.
     */
    @Override
    public void closePool(String username) throws SQLException {
        try {
            _pool.clear(new UserPassKey(username, null));
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

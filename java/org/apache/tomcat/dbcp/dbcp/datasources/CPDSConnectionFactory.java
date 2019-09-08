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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;

import org.apache.tomcat.dbcp.pool.ObjectPool;
import org.apache.tomcat.dbcp.pool.PoolableObjectFactory;

/**
 * A {@link PoolableObjectFactory} that creates
 * {@link org.apache.tomcat.dbcp.dbcp.PoolableConnection}s.
 *
 * @author John D. McNally
 */
class CPDSConnectionFactory
        implements PoolableObjectFactory<PooledConnectionAndInfo>, ConnectionEventListener, PooledConnectionManager {

    private static final String NO_KEY_MESSAGE
            = "close() was called on a Connection, but "
            + "I have no record of the underlying PooledConnection.";

    private final ConnectionPoolDataSource _cpds;
    private final String _validationQuery;
    private final boolean _rollbackAfterValidation;
    private final ObjectPool<PooledConnectionAndInfo> _pool;
    private String _username = null;
    private String _password = null;

    /**
     * Map of PooledConnections for which close events are ignored.
     * Connections are muted when they are being validated.
     */
    private final Map<PooledConnection, Void>  validatingMap = new HashMap<PooledConnection, Void>();

    /**
     * Map of PooledConnectionAndInfo instances
     */
    private final WeakHashMap <PooledConnection, PooledConnectionAndInfo> pcMap =
            new WeakHashMap<PooledConnection, PooledConnectionAndInfo>();

    /**
     * Create a new <code>PoolableConnectionFactory</code>.
     *
     * @param cpds the ConnectionPoolDataSource from which to obtain
     * PooledConnection's
     * @param pool the {@link ObjectPool} in which to pool those
     * {@link Connection}s
     * @param validationQuery a query to use to {@link #validateObject validate}
     * {@link Connection}s. Should return at least one row. May be
     * <code>null</code>
     * @param username
     * @param password
     */
    public CPDSConnectionFactory(ConnectionPoolDataSource cpds,
                                 ObjectPool<PooledConnectionAndInfo> pool,
                                 String validationQuery,
                                 String username,
                                 String password) {
        this(cpds, pool, validationQuery, false, username, password);
    }

    /**
     * Create a new <code>PoolableConnectionFactory</code>.
     *
     * @param cpds the ConnectionPoolDataSource from which to obtain
     * PooledConnection's
     * @param pool the {@link ObjectPool} in which to pool those {@link
     * Connection}s
     * @param validationQuery a query to use to {@link #validateObject
     * validate} {@link Connection}s. Should return at least one row.
     * May be <code>null</code>
     * @param rollbackAfterValidation whether a rollback should be issued
     * after {@link #validateObject validating} {@link Connection}s.
     * @param username
     * @param password
     */
     public CPDSConnectionFactory(ConnectionPoolDataSource cpds,
                                  ObjectPool<PooledConnectionAndInfo> pool,
                                  String validationQuery,
                                  boolean rollbackAfterValidation,
                                  String username,
                                  String password) {
         _cpds = cpds;
         _pool = pool;
         pool.setFactory(this);
         _validationQuery = validationQuery;
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

    @Override
    public synchronized PooledConnectionAndInfo makeObject() {
        PooledConnectionAndInfo obj;
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
            obj = new PooledConnectionAndInfo(pc, _username, _password);
            pcMap.put(pc, obj);
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage());
        }
        return obj;
    }

    /**
     * Closes the PooledConnection and stops listening for events from it.
     */
    @Override
    public void destroyObject(PooledConnectionAndInfo obj) throws Exception {
        PooledConnection pc = obj.getPooledConnection();
        pc.removeConnectionEventListener(this);
        pcMap.remove(pc);
        pc.close();
    }

    @Override
    public boolean validateObject(PooledConnectionAndInfo obj) {
        boolean valid = false;
        PooledConnection pconn = obj.getPooledConnection();
        String query = _validationQuery;
        if (null != query) {
            Connection conn = null;
            Statement stmt = null;
            ResultSet rset = null;
            // logical Connection from the PooledConnection must be closed
            // before another one can be requested and closing it will
            // generate an event. Keep track so we know not to return
            // the PooledConnection
            validatingMap.put(pconn, null);
            try {
                conn = pconn.getConnection();
                stmt = conn.createStatement();
                rset = stmt.executeQuery(query);
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
                if (rset != null) {
                    try {
                        rset.close();
                    } catch (Throwable t) {
                        // ignore
                    }
                }
                if (stmt != null) {
                    try {
                        stmt.close();
                    } catch (Throwable t) {
                        // ignore
                    }
                }
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (Throwable t) {
                        // ignore
                    }
                }
                validatingMap.remove(pconn);
            }
        } else {
            valid = true;
        }
        return valid;
    }

    @Override
    public void passivateObject(PooledConnectionAndInfo obj) {
    }

    @Override
    public void activateObject(PooledConnectionAndInfo obj) {
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
        // if this event occured becase we were validating, ignore it
        // otherwise return the connection to the pool.
        if (!validatingMap.containsKey(pc)) {
            PooledConnectionAndInfo info = pcMap.get(pc);
            if (info == null) {
                throw new IllegalStateException(NO_KEY_MESSAGE);
            }

            try {
                _pool.returnObject(info);
            } catch (Exception e) {
                System.err.println("CLOSING DOWN CONNECTION AS IT COULD "
                        + "NOT BE RETURNED TO THE POOL");
                pc.removeConnectionEventListener(this);
                try {
                    destroyObject(info);
                } catch (Exception e2) {
                    System.err.println("EXCEPTION WHILE DESTROYING OBJECT "
                            + info);
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

        PooledConnectionAndInfo info = pcMap.get(pc);
        if (info == null) {
            throw new IllegalStateException(NO_KEY_MESSAGE);
        }
        try {
            _pool.invalidateObject(info);
        } catch (Exception e) {
            System.err.println("EXCEPTION WHILE DESTROYING OBJECT " + info);
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
        PooledConnectionAndInfo info = pcMap.get(pc);
        if (info == null) {
            throw new IllegalStateException(NO_KEY_MESSAGE);
        }
        try {
            _pool.invalidateObject(info);  // Destroy instance and update pool counters
            _pool.close();  // Clear any other instances in this pool and kill others as they come back
        } catch (Exception ex) {
            throw (SQLException) new SQLException("Error invalidating connection").initCause(ex);
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
            throw (SQLException) new SQLException("Error closing connection pool").initCause(ex);
        }
    }

}

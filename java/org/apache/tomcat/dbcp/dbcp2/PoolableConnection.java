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
package org.apache.tomcat.dbcp.dbcp2;

import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.apache.tomcat.dbcp.pool2.ObjectPool;

/**
 * A delegating connection that, rather than closing the underlying
 * connection, returns itself to an {@link ObjectPool} when
 * closed.
 *
 * @author Rodney Waldhoff
 * @author Glenn L. Nielsen
 * @author James House
 * @since 2.0
 */
public class PoolableConnection extends DelegatingConnection<Connection>
        implements PoolableConnectionMXBean {

    private static MBeanServer MBEAN_SERVER = null;

    static {
        try {
            MBEAN_SERVER = ManagementFactory.getPlatformMBeanServer();
        } catch (Exception ex) {
            // ignore - JMX not available
        }
    }

    /** The pool to which I should return. */
    private ObjectPool<PoolableConnection> _pool = null;

    private final ObjectName _jmxName;

    // Use a prepared statement for validation, retaining the last used SQL to
    // check if the validation query has changed.
    private PreparedStatement validationPreparedStatement = null;
    private String lastValidationSql = null;

    /**
     *
     * @param conn my underlying connection
     * @param pool the pool to which I should return when closed
     */
    public PoolableConnection(Connection conn,
            ObjectPool<PoolableConnection> pool, ObjectName jmxName) {
        super(conn);
        _pool = pool;
        _jmxName = jmxName;

        if (jmxName != null) {
            try {
                MBEAN_SERVER.registerMBean(this, jmxName);
            } catch (InstanceAlreadyExistsException |
                    MBeanRegistrationException | NotCompliantMBeanException e) {
                // For now, simply skip registration
            }
        }
    }


    @Override
    protected void passivate() throws SQLException {
        super.passivate();
        setClosedInternal(true);
    }


    /**
     * {@inheritDoc}
     * <p>
     * This method should not be used by a client to determine whether or not a
     * connection should be return to the connection pool (by calling
     * {@link #close()}). Clients should always attempt to return a connection
     * to the pool once it is no longer required.
     */
    @Override
    public boolean isClosed() throws SQLException {
        if (isClosedInternal()) {
            return true;
        }

        if (getDelegateInternal().isClosed()) {
            // Something has gone wrong. The underlying connection has been
            // closed without the connection being returned to the pool. Return
            // it now.
            close();
            return true;
        }

        return false;
    }


    /**
     * Returns me to my pool.
     */
     @Override
    public synchronized void close() throws SQLException {
        if (isClosedInternal()) {
            // already closed
            return;
        }

        boolean isUnderlyingConectionClosed;
        try {
            isUnderlyingConectionClosed = getDelegateInternal().isClosed();
        } catch (SQLException e) {
            try {
                _pool.invalidateObject(this);
            } catch(IllegalStateException ise) {
                // pool is closed, so close the connection
                passivate();
                getInnermostDelegate().close();
            } catch (Exception ie) {
                // DO NOTHING the original exception will be rethrown
            }
            throw new SQLException("Cannot close connection (isClosed check failed)", e);
        }

        /* Can't set close before this code block since the connection needs to
         * be open when validation runs. Can't set close after this code block
         * since by then the connection will have been returned to the pool and
         * may have been borrowed by another thread. Therefore, the close flag
         * is set in passivate().
         */
        if (isUnderlyingConectionClosed) {
            // Abnormal close: underlying connection closed unexpectedly, so we
            // must destroy this proxy
            try {
                _pool.invalidateObject(this);
            } catch(IllegalStateException e) {
                // pool is closed, so close the connection
                passivate();
                getInnermostDelegate().close();
            } catch (Exception e) {
                throw new SQLException("Cannot close connection (invalidating pooled object failed)", e);
            }
        } else {
            // Normal close: underlying connection is still open, so we
            // simply need to return this proxy to the pool
            try {
                _pool.returnObject(this);
            } catch(IllegalStateException e) {
                // pool is closed, so close the connection
                passivate();
                getInnermostDelegate().close();
            } catch(SQLException e) {
                throw e;
            } catch(RuntimeException e) {
                throw e;
            } catch(Exception e) {
                throw new SQLException("Cannot close connection (return to pool failed)", e);
            }
        }
    }

    /**
     * Actually close my underlying {@link Connection}.
     */
    @Override
    public void reallyClose() throws SQLException {
        if (_jmxName != null) {
            try {
                MBEAN_SERVER.unregisterMBean(_jmxName);
            } catch (MBeanRegistrationException | InstanceNotFoundException e) {
                // Ignore
            }
        }


        if (validationPreparedStatement != null) {
            try {
                validationPreparedStatement.close();
            } catch (SQLException sqle) {
                // Ignore
            }
        }

        super.closeInternal();
    }


    /**
     * Expose the {@link #toString()} method via a bean getter so it can be read
     * as a property via JMX.
     */
    @Override
    public String getToString() {
        return toString();
    }


    public void validate(String sql, int timeout) throws SQLException {
        if (sql == null || sql.length() == 0) {
            if (timeout < 0) {
                timeout = 0;
            }
            if (!isValid(timeout)) {
                throw new SQLException("isValid() returned false");
            }
            return;
        }

        if (!sql.equals(lastValidationSql)) {
            lastValidationSql = sql;
            // Has to be the innermost delegate else the prepared statement will
            // be closed when the pooled connection is passivated.
            validationPreparedStatement =
                    getInnermostDelegateInternal().prepareStatement(sql);
        }

        if (timeout > 0) {
            validationPreparedStatement.setQueryTimeout(timeout);
        }

        try (ResultSet rs = validationPreparedStatement.executeQuery()) {
            if(!rs.next()) {
                throw new SQLException("validationQuery didn't return a row");
            }
        } catch (SQLException sqle) {
            throw sqle;
        }
    }
}


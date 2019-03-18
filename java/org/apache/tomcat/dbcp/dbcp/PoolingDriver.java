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

package org.apache.tomcat.dbcp.dbcp;

import java.io.IOException;
import java.io.InputStream;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.tomcat.dbcp.jocl.JOCLContentHandler;
import org.apache.tomcat.dbcp.pool.ObjectPool;
import org.xml.sax.SAXException;


/**
 * A {@link Driver} implementation that obtains
 * {@link Connection}s from a registered
 * {@link ObjectPool}.
 *
 * @author Rodney Waldhoff
 * @author Dirk Verbeeck
 */
public class PoolingDriver implements Driver {
    /** Register myself with the {@link DriverManager}. */
    static {
        try {
            DriverManager.registerDriver(new PoolingDriver());
        } catch(Exception e) {
        }
    }

    /** The map of registered pools. */
    protected static final HashMap<String, ObjectPool<? extends Connection>> _pools =
            new HashMap<String, ObjectPool<? extends Connection>>();

    /** Controls access to the underlying connection */
    private static boolean accessToUnderlyingConnectionAllowed = false;

    public PoolingDriver() {
    }

    /**
     * Returns the value of the accessToUnderlyingConnectionAllowed property.
     *
     * @return true if access to the underlying is allowed, false otherwise.
     */
    public static synchronized boolean isAccessToUnderlyingConnectionAllowed() {
        return accessToUnderlyingConnectionAllowed;
    }

    /**
     * Sets the value of the accessToUnderlyingConnectionAllowed property.
     * It controls if the PoolGuard allows access to the underlying connection.
     * (Default: false)
     *
     * @param allow Access to the underlying connection is granted when true.
     */
    public static synchronized void setAccessToUnderlyingConnectionAllowed(boolean allow) {
        accessToUnderlyingConnectionAllowed = allow;
    }

    /**
     * WARNING: This method throws DbcpExceptions (RuntimeExceptions)
     * and will be replaced by the protected getConnectionPool method.
     *
     * @deprecated This will be removed in a future version of DBCP.
     */
    @Deprecated
    public synchronized ObjectPool<? extends Connection> getPool(String name) {
        try {
            return getConnectionPool(name);
        }
        catch (Exception e) {
            throw new DbcpException(e);
        }
    }

    public synchronized ObjectPool<? extends Connection> getConnectionPool(String name) throws SQLException {
        ObjectPool<? extends Connection> pool = _pools.get(name);
        if(null == pool) {
            InputStream in = this.getClass().getResourceAsStream(String.valueOf(name) + ".jocl");
            if (in == null) {
                in = Thread.currentThread().getContextClassLoader(
                        ).getResourceAsStream(String.valueOf(name) + ".jocl");
            }
            if(null != in) {
                JOCLContentHandler jocl = null;
                try {
                    jocl = JOCLContentHandler.parse(in);
                }
                catch (SAXException e) {
                    throw (SQLException) new SQLException("Could not parse configuration file").initCause(e);
                }
                catch (IOException e) {
                    throw (SQLException) new SQLException("Could not load configuration file").initCause(e);
                }
                if(jocl.getType(0).equals(String.class)) {
                    pool = getPool((String)(jocl.getValue(0)));
                    if(null != pool) {
                        registerPool(name,pool);
                    }
                } else {
                    pool = ((PoolableConnectionFactory)(jocl.getValue(0))).getPool();
                    if(null != pool) {
                        registerPool(name,pool);
                    }
                }
            }
            else {
                throw new SQLException("Configuration file not found");
            }
        }
        return pool;
    }

    public synchronized void registerPool(String name, ObjectPool<? extends Connection> pool) {
        _pools.put(name,pool);
    }

    public synchronized void closePool(String name) throws SQLException {
        ObjectPool<? extends Connection> pool = _pools.get(name);
        if (pool != null) {
            _pools.remove(name);
            try {
                pool.close();
            }
            catch (Exception e) {
                throw (SQLException) new SQLException("Error closing pool " + name).initCause(e);
            }
        }
    }

    public synchronized String[] getPoolNames(){
        Set<String> names = _pools.keySet();
        return names.toArray(new String[names.size()]);
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        try {
            return url.startsWith(URL_PREFIX);
        } catch(NullPointerException e) {
            return false;
        }
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if(acceptsURL(url)) {
            ObjectPool<? extends Connection> pool = getConnectionPool(url.substring(URL_PREFIX_LEN));
            if(null == pool) {
                throw new SQLException("No pool found for " + url + ".");
            } else {
                try {
                    Connection conn = pool.borrowObject();
                    if (conn != null) {
                        conn = new PoolGuardConnectionWrapper(pool, conn);
                    }
                    return conn;
                } catch(SQLException e) {
                    throw e;
                } catch(NoSuchElementException e) {
                    throw (SQLException) new SQLException("Cannot get a connection, pool error: " + e.getMessage()).initCause(e);
                } catch(RuntimeException e) {
                    throw e;
                } catch(Exception e) {
                    throw (SQLException) new SQLException("Cannot get a connection, general error: " + e.getMessage()).initCause(e);
                }
            }
        } else {
            return null;
        }
    }

    /**
     * Invalidates the given connection.
     *
     * @param conn connection to invalidate
     * @throws SQLException if the connection is not a
     * <code>PoolGuardConnectionWrapper</code> or an error occurs invalidating
     * the connection
     * @since 1.2.2
     */
    public void invalidateConnection(Connection conn) throws SQLException {
        if (conn instanceof PoolGuardConnectionWrapper) { // normal case
            PoolGuardConnectionWrapper pgconn = (PoolGuardConnectionWrapper) conn;
            @SuppressWarnings("unchecked")
            ObjectPool<Connection> pool = (ObjectPool<Connection>) pgconn.pool;
            Connection delegate = pgconn.delegate;
            try {
                pool.invalidateObject(delegate);
            }
            catch (Exception e) {
            }
            pgconn.delegate = null;
        }
        else {
            throw new SQLException("Invalid connection class");
        }
    }

    @Override
    public int getMajorVersion() {
        return MAJOR_VERSION;
    }

    @Override
    public int getMinorVersion() {
        return MINOR_VERSION;
    }

    @Override
    public boolean jdbcCompliant() {
        return true;
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        return new DriverPropertyInfo[0];
    }

    /* JDBC_4_1_ANT_KEY_BEGIN */
    // No @Override else it won't compile with Java 6
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }
    /* JDBC_4_1_ANT_KEY_END */

    /** My URL prefix */
    protected static final String URL_PREFIX = "jdbc:apache:commons:dbcp:";
    protected static final int URL_PREFIX_LEN = URL_PREFIX.length();

    // version numbers
    protected static final int MAJOR_VERSION = 1;
    protected static final int MINOR_VERSION = 0;

    /**
     * PoolGuardConnectionWrapper is a Connection wrapper that makes sure a
     * closed connection cannot be used anymore.
     */
    static private class PoolGuardConnectionWrapper extends DelegatingConnection {

        private final ObjectPool<? extends Connection> pool;
        private Connection delegate;

        PoolGuardConnectionWrapper(ObjectPool<? extends Connection> pool, Connection delegate) {
            super(delegate);
            this.pool = pool;
            this.delegate = delegate;
        }

        @Override
        protected void checkOpen() throws SQLException {
            if(delegate == null) {
                throw new SQLException("Connection is closed.");
            }
        }

        @Override
        public void close() throws SQLException {
            if (delegate != null) {
                this.delegate.close();
                this.delegate = null;
                super.setDelegate(null);
            }
        }

        @Override
        public boolean isClosed() throws SQLException {
            if (delegate == null) {
                return true;
            }
            return delegate.isClosed();
        }

        @Override
        public void clearWarnings() throws SQLException {
            checkOpen();
            delegate.clearWarnings();
        }

        @Override
        public void commit() throws SQLException {
            checkOpen();
            delegate.commit();
        }

        @Override
        public Statement createStatement() throws SQLException {
            checkOpen();
            return new DelegatingStatement(this, delegate.createStatement());
        }

        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
            checkOpen();
            return new DelegatingStatement(this, delegate.createStatement(resultSetType, resultSetConcurrency));
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (delegate == null){
                return false;
            }
            return delegate.equals(obj);
        }

        @Override
        public boolean getAutoCommit() throws SQLException {
            checkOpen();
            return delegate.getAutoCommit();
        }

        @Override
        public String getCatalog() throws SQLException {
            checkOpen();
            return delegate.getCatalog();
        }

        @Override
        public DatabaseMetaData getMetaData() throws SQLException {
            checkOpen();
            return delegate.getMetaData();
        }

        @Override
        public int getTransactionIsolation() throws SQLException {
            checkOpen();
            return delegate.getTransactionIsolation();
        }

        @Override
        public Map<String, Class<?>> getTypeMap() throws SQLException {
            checkOpen();
            return delegate.getTypeMap();
        }

        @Override
        public SQLWarning getWarnings() throws SQLException {
            checkOpen();
            return delegate.getWarnings();
        }

        @Override
        public int hashCode() {
            if (delegate == null){
                return 0;
            }
            return delegate.hashCode();
        }

        @Override
        public boolean isReadOnly() throws SQLException {
            checkOpen();
            return delegate.isReadOnly();
        }

        @Override
        public String nativeSQL(String sql) throws SQLException {
            checkOpen();
            return delegate.nativeSQL(sql);
        }

        @Override
        public CallableStatement prepareCall(String sql) throws SQLException {
            checkOpen();
            return new DelegatingCallableStatement(this, delegate.prepareCall(sql));
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            checkOpen();
            return new DelegatingCallableStatement(this, delegate.prepareCall(sql, resultSetType, resultSetConcurrency));
        }

        @Override
        public PreparedStatement prepareStatement(String sql) throws SQLException {
            checkOpen();
            return new DelegatingPreparedStatement(this, delegate.prepareStatement(sql));
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            checkOpen();
            return new DelegatingPreparedStatement(this, delegate.prepareStatement(sql, resultSetType, resultSetConcurrency));
        }

        @Override
        public void rollback() throws SQLException {
            checkOpen();
            delegate.rollback();
        }

        @Override
        public void setAutoCommit(boolean autoCommit) throws SQLException {
            checkOpen();
            delegate.setAutoCommit(autoCommit);
        }

        @Override
        public void setCatalog(String catalog) throws SQLException {
            checkOpen();
            delegate.setCatalog(catalog);
        }

        @Override
        public void setReadOnly(boolean readOnly) throws SQLException {
            checkOpen();
            delegate.setReadOnly(readOnly);
        }

        @Override
        public void setTransactionIsolation(int level) throws SQLException {
            checkOpen();
            delegate.setTransactionIsolation(level);
        }

        @Override
        public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
            checkOpen();
            delegate.setTypeMap(map);
        }

        @Override
        public String toString() {
            if (delegate == null){
                return "NULL";
            }
            return delegate.toString();
        }

        @Override
        public int getHoldability() throws SQLException {
            checkOpen();
            return delegate.getHoldability();
        }

        @Override
        public void setHoldability(int holdability) throws SQLException {
            checkOpen();
            delegate.setHoldability(holdability);
        }

        @Override
        public java.sql.Savepoint setSavepoint() throws SQLException {
            checkOpen();
            return delegate.setSavepoint();
        }

        @Override
        public java.sql.Savepoint setSavepoint(String name) throws SQLException {
            checkOpen();
            return delegate.setSavepoint(name);
        }

        @Override
        public void releaseSavepoint(java.sql.Savepoint savepoint) throws SQLException {
            checkOpen();
            delegate.releaseSavepoint(savepoint);
        }

        @Override
        public void rollback(java.sql.Savepoint savepoint) throws SQLException {
            checkOpen();
            delegate.rollback(savepoint);
        }

        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            checkOpen();
            return new DelegatingStatement(this, delegate.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability));
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            checkOpen();
            return new DelegatingCallableStatement(this, delegate.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability));
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
            checkOpen();
            return new DelegatingPreparedStatement(this, delegate.prepareStatement(sql, autoGeneratedKeys));
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            checkOpen();
            return new DelegatingPreparedStatement(this, delegate.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability));
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
            checkOpen();
            return new DelegatingPreparedStatement(this, delegate.prepareStatement(sql, columnIndexes));
        }

        @Override
        public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
            checkOpen();
            return new DelegatingPreparedStatement(this, delegate.prepareStatement(sql, columnNames));
        }

        /**
         * @see org.apache.tomcat.dbcp.dbcp.DelegatingConnection#getDelegate()
         */
        @Override
        public Connection getDelegate() {
            if (isAccessToUnderlyingConnectionAllowed()) {
                return super.getDelegate();
            } else {
                return null;
            }
        }

        /**
         * @see org.apache.tomcat.dbcp.dbcp.DelegatingConnection#getInnermostDelegate()
         */
        @Override
        public Connection getInnermostDelegate() {
            if (isAccessToUnderlyingConnectionAllowed()) {
                return super.getInnermostDelegate();
            } else {
                return null;
            }
        }
    }
}

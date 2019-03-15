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

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import java.util.NoSuchElementException;

import org.apache.tomcat.dbcp.pool.KeyedObjectPool;
import org.apache.tomcat.dbcp.pool.KeyedPoolableObjectFactory;

/**
 * A {@link DelegatingConnection} that pools {@link PreparedStatement}s.
 * <p>
 * The {@link #prepareStatement} and {@link #prepareCall} methods, rather than creating a new PreparedStatement
 * each time, may actually pull the statement from a pool of unused statements.
 * The {@link PreparedStatement#close} method of the returned statement doesn't
 * actually close the statement, but rather returns it to the pool. 
 * (See {@link PoolablePreparedStatement}, {@link PoolableCallableStatement}.)
 * 
 *
 * @see PoolablePreparedStatement
 * @author Rodney Waldhoff
 * @author Dirk Verbeeck
 * @version $Revision: 885261 $ $Date: 2009-11-29 15:07:02 -0500 (Sun, 29 Nov 2009) $
 */
public class PoolingConnection extends DelegatingConnection implements Connection, KeyedPoolableObjectFactory {
    /** Pool of {@link PreparedStatement}s. and {@link CallableStatement}s */
    protected KeyedObjectPool _pstmtPool = null;

    /** Prepared Statement type */
    private static final byte STATEMENT_PREPAREDSTMT = 0;
    
    /** Callable Statement type */
    private static final byte STATEMENT_CALLABLESTMT = 1;
     
    
    /**
     * Constructor.
     * @param c the underlying {@link Connection}.
     */
    public PoolingConnection(Connection c) {
        super(c);
    }

    /**
     * Constructor.
     * @param c the underlying {@link Connection}.
     * @param pool {@link KeyedObjectPool} of {@link PreparedStatement}s and {@link CallableStatement}s.
     */
    public PoolingConnection(Connection c, KeyedObjectPool pool) {
        super(c);
        _pstmtPool = pool;
    }


    /**
     * Close and free all {@link PreparedStatement}s or {@link CallableStatement} from the pool, and
     * close the underlying connection.
     */
    public synchronized void close() throws SQLException {
        if(null != _pstmtPool) {
            KeyedObjectPool oldpool = _pstmtPool;            
            _pstmtPool = null;
            try {
                oldpool.close();
            } catch(RuntimeException e) {
                throw e;
            } catch(SQLException e) {
                throw e;
            } catch(Exception e) {
                throw (SQLException) new SQLException("Cannot close connection").initCause(e);
            }
        }
        getInnermostDelegate().close();
    }

    /**
     * Create or obtain a {@link PreparedStatement} from the pool.
     * @param sql the sql string used to define the PreparedStatement
     * @return a {@link PoolablePreparedStatement}
     */
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        if (null == _pstmtPool) {
            throw new SQLException(
                    "Statement pool is null - closed or invalid PoolingConnection.");
        }
        try {
            return(PreparedStatement)(_pstmtPool.borrowObject(createKey(sql)));
        } catch(NoSuchElementException e) {
            throw (SQLException) new SQLException("MaxOpenPreparedStatements limit reached").initCause(e); 
        } catch(RuntimeException e) {
            throw e;
        } catch(Exception e) {
            throw new SQLNestedException("Borrow prepareStatement from pool failed", e);
        }
    }

    /**
     * Create or obtain a {@link PreparedStatement} from the pool.
     * @param sql the sql string used to define the PreparedStatement
     * @param resultSetType result set type
     * @param resultSetConcurrency result set concurrency
     * @return a {@link PoolablePreparedStatement}
     */
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        if (null == _pstmtPool) {
            throw new SQLException(
                    "Statement pool is null - closed or invalid PoolingConnection.");
        }
        try {
            return(PreparedStatement)(_pstmtPool.borrowObject(createKey(sql,resultSetType,resultSetConcurrency)));
        } catch(NoSuchElementException e) {
            throw (SQLException) new SQLException("MaxOpenPreparedStatements limit reached").initCause(e); 
        } catch(RuntimeException e) {
            throw e;
        } catch(Exception e) {
            throw (SQLException) new SQLException("Borrow prepareStatement from pool failed").initCause(e);
        }
    }
    
    /**
     * Create or obtain a {@link CallableStatement} from the pool.
     * @param sql the sql string used to define the CallableStatement
     * @return a {@link PoolableCallableStatement}
     * @throws SQLException
     * @since 1.3
     */
    public CallableStatement prepareCall(String sql) throws SQLException {
        try {
            return (CallableStatement) (_pstmtPool.borrowObject(createKey(sql, STATEMENT_CALLABLESTMT)));
        } catch (NoSuchElementException e) {
            throw new SQLNestedException("MaxOpenCallableStatements limit reached", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLNestedException("Borrow callableStatement from pool failed", e);
        }
    }
    
    /**
     * Create or obtain a {@link CallableStatement} from the pool.
     * @param sql the sql string used to define the CallableStatement
     * @param resultSetType result set type
     * @param resultSetConcurrency result set concurrency
     * @return a {@link PoolableCallableStatement}
     * @throws SQLException
     * @since 1.3
     */
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        try {
            return (CallableStatement) (_pstmtPool.borrowObject(createKey(sql, resultSetType,
                            resultSetConcurrency, STATEMENT_CALLABLESTMT)));
        } catch (NoSuchElementException e) {
            throw new SQLNestedException("MaxOpenCallableStatements limit reached", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLNestedException("Borrow callableStatement from pool failed", e);
        }
    }
    

//    TODO: possible enhancement, cache these preparedStatements as well

//    public PreparedStatement prepareStatement(String sql, int resultSetType,
//                                              int resultSetConcurrency,
//                                              int resultSetHoldability)
//        throws SQLException {
//        return super.prepareStatement(
//            sql, resultSetType, resultSetConcurrency, resultSetHoldability);
//    }
//
//    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys)
//        throws SQLException {
//        return super.prepareStatement(sql, autoGeneratedKeys);
//    }
//
//    public PreparedStatement prepareStatement(String sql, int columnIndexes[])
//        throws SQLException {
//        return super.prepareStatement(sql, columnIndexes);
//    }
//
//    public PreparedStatement prepareStatement(String sql, String columnNames[])
//        throws SQLException {
//        return super.prepareStatement(sql, columnNames);
//    }

    /**
     * Create a PStmtKey for the given arguments.
     * @param sql the sql string used to define the statement
     * @param resultSetType result set type
     * @param resultSetConcurrency result set concurrency
     */
    protected Object createKey(String sql, int resultSetType, int resultSetConcurrency) {
        String catalog = null;
        try {
            catalog = getCatalog();
        } catch (SQLException e) {}
        return new PStmtKey(normalizeSQL(sql), catalog, resultSetType, resultSetConcurrency);
    }
    
    /**
     * Create a PStmtKey for the given arguments.
     * @param sql the sql string used to define the statement
     * @param resultSetType result set type
     * @param resultSetConcurrency result set concurrency
     * @param stmtType statement type - either {@link #STATEMENT_CALLABLESTMT} or {@link #STATEMENT_PREPAREDSTMT}
     */
    protected Object createKey(String sql, int resultSetType, int resultSetConcurrency, byte stmtType) {
        String catalog = null;
        try {
            catalog = getCatalog();
        } catch (SQLException e) {}
        return new PStmtKey(normalizeSQL(sql), catalog, resultSetType, resultSetConcurrency, stmtType);
    }

    /**
     * Create a PStmtKey for the given arguments.
     * @param sql the sql string used to define the statement
     */
    protected Object createKey(String sql) {
        String catalog = null;
        try {
            catalog = getCatalog();
        } catch (SQLException e) {}
        return new PStmtKey(normalizeSQL(sql), catalog);
    }
    
    /**
     * Create a PStmtKey for the given arguments.
     * @param sql the sql string used to define the statement
     * @param stmtType statement type - either {@link #STATEMENT_CALLABLESTMT} or {@link #STATEMENT_PREPAREDSTMT}
     */
    protected Object createKey(String sql, byte stmtType) {
        String catalog = null;
        try {
            catalog = getCatalog();
        } catch (SQLException e) {}
        return new PStmtKey(normalizeSQL(sql), catalog, stmtType);
    }

    /**
     * Normalize the given SQL statement, producing a
     * cannonical form that is semantically equivalent to the original.
     */
    protected String normalizeSQL(String sql) {
        return sql.trim();
    }

    /**
     * {@link KeyedPoolableObjectFactory} method for creating
     * {@link PoolablePreparedStatement}s or {@link PoolableCallableStatement}s.
     * The <code>stmtType</code> field in the key determines whether 
     * a PoolablePreparedStatement or PoolableCallableStatement is created.
     * 
     * @param obj the key for the {@link PreparedStatement} to be created
     * @see #createKey(String, int, int, byte)
     */
    public Object makeObject(Object obj) throws Exception {
        if(null == obj || !(obj instanceof PStmtKey)) {
            throw new IllegalArgumentException("Prepared statement key is null or invalid.");
        } else {
            PStmtKey key = (PStmtKey)obj;
            if( null == key._resultSetType && null == key._resultSetConcurrency ) {
                if (key._stmtType == STATEMENT_PREPAREDSTMT ) {
                    return new PoolablePreparedStatement(getDelegate().prepareStatement( key._sql), key, _pstmtPool, this); 
                } else {
                    return new PoolableCallableStatement(getDelegate().prepareCall( key._sql), key, _pstmtPool, this);
                }
            } else { // Both _resultSetType and _resultSetConcurrency are non-null here (both or neither are set by constructors)
                if(key._stmtType == STATEMENT_PREPAREDSTMT) {
                    return new PoolablePreparedStatement(getDelegate().prepareStatement(
                        key._sql, key._resultSetType.intValue(),key._resultSetConcurrency.intValue()), key, _pstmtPool, this);
                } else {
                    return new PoolableCallableStatement( getDelegate().prepareCall(
                        key._sql,key._resultSetType.intValue(), key._resultSetConcurrency.intValue()), key, _pstmtPool, this);
                }
            }
        }
    }

    /**
     * {@link KeyedPoolableObjectFactory} method for destroying
     * PoolablePreparedStatements and PoolableCallableStatements.
     * Closes the underlying statement.
     * 
     * @param key ignored
     * @param obj the pooled statement to be destroyed.
     */
    public void destroyObject(Object key, Object obj) throws Exception {
        if(obj instanceof DelegatingPreparedStatement) {
            ((DelegatingPreparedStatement)obj).getInnermostDelegate().close();
        } else {
            ((PreparedStatement)obj).close();
        }
    }

    /**
     * {@link KeyedPoolableObjectFactory} method for validating
     * pooled statements. Currently always returns true.
     * 
     * @param key ignored
     * @param obj ignored
     * @return <tt>true</tt>
     */
    public boolean validateObject(Object key, Object obj) {
        return true;
    }

    /**
     * {@link KeyedPoolableObjectFactory} method for activating
     * pooled statements.
     * 
     * @param key ignored
     * @param obj pooled statement to be activated
     */
    public void activateObject(Object key, Object obj) throws Exception {
        ((DelegatingPreparedStatement)obj).activate();
    }

    /**
     * {@link KeyedPoolableObjectFactory} method for passivating
     * {@link PreparedStatement}s or {@link CallableStatement}s.
     * Invokes {@link PreparedStatement#clearParameters}.
     * 
     * @param key ignored
     * @param obj a {@link PreparedStatement}
     */
    public void passivateObject(Object key, Object obj) throws Exception {
        ((PreparedStatement)obj).clearParameters();
        ((DelegatingPreparedStatement)obj).passivate();
    }

    public String toString() {
        if (_pstmtPool != null ) {
            return "PoolingConnection: " + _pstmtPool.toString();
        } else {
            return "PoolingConnection: null";
        }
    }

    /**
     * A key uniquely identifiying {@link PreparedStatement}s.
     */
    static class PStmtKey {
        
        /** SQL defining Prepared or Callable Statement */
        protected String _sql = null;
        
        /** Result set type */
        protected Integer _resultSetType = null;
        
        /** Result set concurrency */
        protected Integer _resultSetConcurrency = null;
        
        /** Database catalog */
        protected String _catalog = null;
        
        /** 
         *  Statement type. Either STATEMENT_PREPAREDSTMT (PreparedStatement)
         *  or STATEMENT_CALLABLESTMT (CallableStatement) 
         */
        protected byte _stmtType = STATEMENT_PREPAREDSTMT;
        
        PStmtKey(String sql) {
            _sql = sql;
        }

        PStmtKey(String sql, String catalog) {
            _sql = sql;
            _catalog = catalog;
        }
        
        PStmtKey(String sql, String catalog, byte stmtType) {
            _sql = sql;
            _catalog = catalog;
            _stmtType = stmtType;
        }

        PStmtKey(String sql, int resultSetType, int resultSetConcurrency) {
            _sql = sql;
            _resultSetType = new Integer(resultSetType);
            _resultSetConcurrency = new Integer(resultSetConcurrency);
        }

        PStmtKey(String sql, String catalog, int resultSetType, int resultSetConcurrency) {
            _sql = sql;
            _catalog = catalog;
            _resultSetType = new Integer(resultSetType);
            _resultSetConcurrency = new Integer(resultSetConcurrency);
        }
        
        PStmtKey(String sql, String catalog, int resultSetType, int resultSetConcurrency, byte stmtType) {
            _sql = sql;
            _catalog = catalog;
            _resultSetType = new Integer(resultSetType);
            _resultSetConcurrency = new Integer(resultSetConcurrency);
            _stmtType = stmtType;
        }

        public boolean equals(Object that) {
            try {
                PStmtKey key = (PStmtKey)that;
                return( ((null == _sql && null == key._sql) || _sql.equals(key._sql)) &&  
                        ((null == _catalog && null == key._catalog) || _catalog.equals(key._catalog)) &&
                        ((null == _resultSetType && null == key._resultSetType) || _resultSetType.equals(key._resultSetType)) &&
                        ((null == _resultSetConcurrency && null == key._resultSetConcurrency) || _resultSetConcurrency.equals(key._resultSetConcurrency)) &&
                        (_stmtType == key._stmtType)
                      );
            } catch(ClassCastException e) {
                return false;
            } catch(NullPointerException e) {
                return false;
            }
        }

        public int hashCode() {
            if (_catalog==null)
                return(null == _sql ? 0 : _sql.hashCode());
            else
                return(null == _sql ? _catalog.hashCode() : (_catalog + _sql).hashCode());
        }

        public String toString() {
            StringBuffer buf = new StringBuffer();
            buf.append("PStmtKey: sql=");
            buf.append(_sql);
            buf.append(", catalog=");
            buf.append(_catalog);
            buf.append(", resultSetType=");
            buf.append(_resultSetType);
            buf.append(", resultSetConcurrency=");
            buf.append(_resultSetConcurrency);
            buf.append(", statmentType=");
            buf.append(_stmtType);
            return buf.toString();
        }
    }
}

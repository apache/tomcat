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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.apache.tomcat.dbcp.pool.KeyedObjectPool;

/**
 * A {@link DelegatingCallableStatement} that cooperates with
 * {@link PoolingConnection} to implement a pool of {@link CallableStatement}s.
 * <p>
 * The {@link #close} method returns this statement to its containing pool. (See {@link PoolingConnection}.)
 *
 * @see PoolingConnection
 * @since 1.3
 */
public class PoolableCallableStatement extends DelegatingCallableStatement {

    /**
     * The {@link KeyedObjectPool} from which this CallableStatement was obtained.
     */
    private final KeyedObjectPool<PStmtKey, DelegatingPreparedStatement> _pool;

    /**
     * Key for this statement in the containing {@link KeyedObjectPool}.
     */
    private final PStmtKey _key;

    /**
     * Constructor.
     *
     * @param stmt the underlying {@link CallableStatement}
     * @param key the key for this statement in the {@link KeyedObjectPool}
     * @param pool the {@link KeyedObjectPool} from which this CallableStatement was obtained
     * @param conn the {@link Connection} that created this CallableStatement
     */
    public PoolableCallableStatement(CallableStatement stmt, PStmtKey key,
            KeyedObjectPool<PStmtKey, DelegatingPreparedStatement> pool, Connection conn) {
        super((DelegatingConnection)conn, stmt);
        _pool = pool;
        _key = key;

        // Remove from trace now because this statement will be
        // added by the activate method.
        if(_conn != null) {
            _conn.removeTrace(this);
        }
    }

    /**
     * Returns the CallableStatement to the pool.  If {{@link #isClosed()}, this is a No-op.
     */
    @Override
    public void close() throws SQLException {
        // calling close twice should have no effect
        if (!isClosed()) {
            try {
                _pool.returnObject(_key,this);
            } catch(SQLException e) {
                throw e;
            } catch(RuntimeException e) {
                throw e;
            } catch(Exception e) {
                throw new SQLException("Cannot close CallableStatement (return to pool failed)", e);
            }
        }
    }

    /**
     * Activates after retrieval from the pool. Adds a trace for this CallableStatement to the Connection
     * that created it.
     */
    @Override
    public void activate() throws SQLException {
        _closed = false;
        if( _conn != null ) {
            _conn.addTrace( this );
        }
        super.activate();
    }

    /**
     * Passivates to prepare for return to the pool.  Removes the trace associated with this CallableStatement
     * from the Connection that created it.  Also closes any associated ResultSets.
     */
    @Override
    public void passivate() throws SQLException {
        _closed = true;
        if( _conn != null ) {
            _conn.removeTrace(this);
        }

        // The JDBC spec requires that a statment close any open
        // ResultSet's when it is closed.
        // FIXME The PreparedStatement we're wrapping should handle this for us.
        // See DBCP-10 for what could happen when ResultSets are closed twice.
        List<AbandonedTrace> resultSets = getTrace();
        if(resultSets != null) {
            ResultSet[] set = resultSets.toArray(new ResultSet[resultSets.size()]);
            for(int i = 0; i < set.length; i++) {
                set[i].close();
            }
            clearTrace();
        }

        super.passivate();
    }

}

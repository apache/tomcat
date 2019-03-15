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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.apache.tomcat.dbcp.pool.KeyedObjectPool;

/**
 * A {@link DelegatingPreparedStatement} that cooperates with
 * {@link PoolingConnection} to implement a pool of {@link PreparedStatement}s.
 * <p>
 * My {@link #close} method returns me to my containing pool. (See {@link PoolingConnection}.)
 *
 * @see PoolingConnection
 * @author Rodney Waldhoff
 * @author Glenn L. Nielsen
 * @author James House
 * @author Dirk Verbeeck
 * @version $Revision: 745860 $ $Date: 2009-02-19 08:45:07 -0500 (Thu, 19 Feb 2009) $
 */
public class PoolablePreparedStatement extends DelegatingPreparedStatement implements PreparedStatement {
    /**
     * The {@link KeyedObjectPool} from which I was obtained.
     */
    protected KeyedObjectPool _pool = null;

    /**
     * My "key" as used by {@link KeyedObjectPool}.
     */
    protected Object _key = null;

    private volatile boolean batchAdded = false;

    /**
     * Constructor
     * @param stmt my underlying {@link PreparedStatement}
     * @param key my key" as used by {@link KeyedObjectPool}
     * @param pool the {@link KeyedObjectPool} from which I was obtained.
     * @param conn the {@link Connection} from which I was created
     */
    public PoolablePreparedStatement(PreparedStatement stmt, Object key, KeyedObjectPool pool, Connection conn) {
        super((DelegatingConnection) conn, stmt);
        _pool = pool;
        _key = key;

        // Remove from trace now because this statement will be 
        // added by the activate method.
        if(_conn != null) {
            _conn.removeTrace(this);
        }
    }

    /**
     * Add batch.
     */
    public void addBatch() throws SQLException {
        super.addBatch();
        batchAdded = true;
    }

    /**
     * Clear Batch.
     */
    public void clearBatch() throws SQLException {
        batchAdded = false;
        super.clearBatch();
    }

    /**
     * Return me to my pool.
     */
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
                throw new SQLNestedException("Cannot close preparedstatement (return to pool failed)", e);
            }
        }
    }
    
    protected void activate() throws SQLException{
        _closed = false;
        if(_conn != null) {
            _conn.addTrace(this);
        }
        super.activate();
    }
  
    protected void passivate() throws SQLException {
        _closed = true;
        if(_conn != null) {
            _conn.removeTrace(this);
        }

        // The JDBC spec requires that a statment close any open
        // ResultSet's when it is closed.
        // FIXME The PreparedStatement we're wrapping should handle this for us.
        // See bug 17301 for what could happen when ResultSets are closed twice.
        List resultSets = getTrace();
        if( resultSets != null) {
            ResultSet[] set = (ResultSet[]) resultSets.toArray(new ResultSet[resultSets.size()]);
            for (int i = 0; i < set.length; i++) {
                set[i].close();
            }
            clearTrace();
        }
        if (batchAdded) {
            clearBatch();
        }
        
        super.passivate();
    }

}

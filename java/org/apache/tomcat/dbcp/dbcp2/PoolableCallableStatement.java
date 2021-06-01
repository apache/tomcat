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

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.tomcat.dbcp.pool2.KeyedObjectPool;

/**
 * A {@link DelegatingCallableStatement} that cooperates with {@link PoolingConnection} to implement a pool of
 * {@link CallableStatement}s.
 * <p>
 * The {@link #close} method returns this statement to its containing pool. (See {@link PoolingConnection}.)
 *
 * @see PoolingConnection
 * @since 2.0
 */
public class PoolableCallableStatement extends DelegatingCallableStatement {

    /**
     * The {@link KeyedObjectPool} from which this CallableStatement was obtained.
     */
    private final KeyedObjectPool<PStmtKey, DelegatingPreparedStatement> pool;

    /**
     * Key for this statement in the containing {@link KeyedObjectPool}.
     */
    private final PStmtKey key;

    /**
     * Constructor.
     *
     * @param callableStatement
     *            the underlying {@link CallableStatement}
     * @param key
     *            the key for this statement in the {@link KeyedObjectPool}
     * @param pool
     *            the {@link KeyedObjectPool} from which this CallableStatement was obtained
     * @param connection
     *            the {@link DelegatingConnection} that created this CallableStatement
     */
    public PoolableCallableStatement(final CallableStatement callableStatement, final PStmtKey key,
            final KeyedObjectPool<PStmtKey, DelegatingPreparedStatement> pool,
            final DelegatingConnection<Connection> connection) {
        super(connection, callableStatement);
        this.pool = pool;
        this.key = key;

        // Remove from trace now because this statement will be
        // added by the activate method.
        removeThisTrace(getConnectionInternal());
    }

    /**
     * Returns the CallableStatement to the pool. If {{@link #isClosed()}, this is a No-op.
     */
    @Override
    public void close() throws SQLException {
        // calling close twice should have no effect
        if (!isClosed()) {
            try {
                pool.returnObject(key, this);
            } catch (final SQLException | RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new SQLException("Cannot close CallableStatement (return to pool failed)", e);
            }
        }
    }

    /**
     * Activates after retrieval from the pool. Adds a trace for this CallableStatement to the Connection that created
     * it.
     *
     * @since 2.4.0 made public, was protected in 2.3.0.
     */
    @Override
    public void activate() throws SQLException {
        setClosedInternal(false);
        if (getConnectionInternal() != null) {
            getConnectionInternal().addTrace(this);
        }
        super.activate();
    }

    /**
     * Passivates to prepare for return to the pool. Removes the trace associated with this CallableStatement from the
     * Connection that created it. Also closes any associated ResultSets.
     *
     * @since 2.4.0 made public, was protected in 2.3.0.
     */
    @Override
    public void passivate() throws SQLException {
        setClosedInternal(true);
        removeThisTrace(getConnectionInternal());

        // The JDBC spec requires that a statement close any open
        // ResultSet's when it is closed.
        // FIXME The PreparedStatement we're wrapping should handle this for us.
        // See DBCP-10 for what could happen when ResultSets are closed twice.
        final List<AbandonedTrace> resultSetList = getTrace();
        if (resultSetList != null) {
            final List<Exception> thrownList = new ArrayList<>();
            final ResultSet[] resultSets = resultSetList.toArray(Utils.EMPTY_RESULT_SET_ARRAY);
            for (final ResultSet resultSet : resultSets) {
                if (resultSet != null) {
                    try {
                        resultSet.close();
                    } catch (final Exception e) {
                        thrownList.add(e);
                    }
                }
            }
            clearTrace();
            if (!thrownList.isEmpty()) {
                throw new SQLExceptionList(thrownList);
            }
        }

        super.passivate();
    }

}

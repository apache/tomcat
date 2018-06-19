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

package org.apache.tomcat.dbcp.dbcp2.managed;

import java.sql.Connection;

import javax.management.ObjectName;

import org.apache.tomcat.dbcp.dbcp2.Constants;
import org.apache.tomcat.dbcp.dbcp2.DelegatingPreparedStatement;
import org.apache.tomcat.dbcp.dbcp2.PStmtKey;
import org.apache.tomcat.dbcp.dbcp2.PoolableConnection;
import org.apache.tomcat.dbcp.dbcp2.PoolableConnectionFactory;
import org.apache.tomcat.dbcp.dbcp2.PoolingConnection;
import org.apache.tomcat.dbcp.pool2.KeyedObjectPool;
import org.apache.tomcat.dbcp.pool2.PooledObject;
import org.apache.tomcat.dbcp.pool2.impl.DefaultPooledObject;
import org.apache.tomcat.dbcp.pool2.impl.GenericKeyedObjectPool;
import org.apache.tomcat.dbcp.pool2.impl.GenericKeyedObjectPoolConfig;

/**
 * A {@link PoolableConnectionFactory} that creates {@link PoolableManagedConnection}s.
 *
 * @since 2.0
 */
public class PoolableManagedConnectionFactory extends PoolableConnectionFactory {

    /** Transaction registry associated with connections created by this factory */
    private final TransactionRegistry transactionRegistry;

    /**
     * Creates a PoolableManagedConnectionFactory and attach it to a connection pool.
     *
     * @param connFactory
     *            XAConnectionFactory
     * @param dataSourceJmxName
     *            The data source name.
     */
    public PoolableManagedConnectionFactory(final XAConnectionFactory connFactory, final ObjectName dataSourceJmxName) {
        super(connFactory, dataSourceJmxName);
        this.transactionRegistry = connFactory.getTransactionRegistry();
    }

    /**
     * Uses the configured XAConnectionFactory to create a {@link PoolableManagedConnection}. Throws
     * <code>IllegalStateException</code> if the connection factory returns null. Also initializes the connection using
     * configured initialization SQL (if provided) and sets up a prepared statement pool associated with the
     * PoolableManagedConnection if statement pooling is enabled.
     */
    @Override
    public synchronized PooledObject<PoolableConnection> makeObject() throws Exception {
        Connection conn = getConnectionFactory().createConnection();
        if (conn == null) {
            throw new IllegalStateException("Connection factory returned null from createConnection");
        }
        initializeConnection(conn);
        if (getPoolStatements()) {
            conn = new PoolingConnection(conn);
            final GenericKeyedObjectPoolConfig<DelegatingPreparedStatement> config = new GenericKeyedObjectPoolConfig<>();
            config.setMaxTotalPerKey(-1);
            config.setBlockWhenExhausted(false);
            config.setMaxWaitMillis(0);
            config.setMaxIdlePerKey(1);
            config.setMaxTotal(getMaxOpenPreparedStatements());
            final ObjectName dataSourceJmxName = getDataSourceJmxName();
            final long connIndex = getConnectionIndex().getAndIncrement();
            if (dataSourceJmxName != null) {
                final StringBuilder base = new StringBuilder(dataSourceJmxName.toString());
                base.append(Constants.JMX_CONNECTION_BASE_EXT);
                base.append(Long.toString(connIndex));
                config.setJmxNameBase(base.toString());
                config.setJmxNamePrefix(Constants.JMX_STATEMENT_POOL_PREFIX);
            } else {
                config.setJmxEnabled(false);
            }
            final KeyedObjectPool<PStmtKey, DelegatingPreparedStatement> stmtPool = new GenericKeyedObjectPool<>(
                    (PoolingConnection) conn, config);
            ((PoolingConnection) conn).setStatementPool(stmtPool);
            ((PoolingConnection) conn).setCacheState(getCacheState());
        }
        final PoolableManagedConnection pmc = new PoolableManagedConnection(transactionRegistry, conn, getPool(),
                getDisconnectionSqlCodes(), isFastFailValidation());
        pmc.setCacheState(getCacheState());
        return new DefaultPooledObject<>(pmc);
    }
}

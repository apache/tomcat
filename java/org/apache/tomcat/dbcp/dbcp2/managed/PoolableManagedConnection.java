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
import java.sql.SQLException;
import java.util.Collection;

import org.apache.tomcat.dbcp.dbcp2.PoolableConnection;
import org.apache.tomcat.dbcp.pool2.ObjectPool;

/**
 * PoolableConnection that unregisters from TransactionRegistry on Connection real destroy.
 *
 * @see PoolableConnection
 * @since 2.0
 */
public class PoolableManagedConnection extends PoolableConnection {
    private final TransactionRegistry transactionRegistry;

    /**
     * Create a PoolableManagedConnection.
     *
     * @param transactionRegistry
     *            transaction registry
     * @param conn
     *            underlying connection
     * @param pool
     *            connection pool
     */
    public PoolableManagedConnection(final TransactionRegistry transactionRegistry, final Connection conn,
            final ObjectPool<PoolableConnection> pool) {
        this(transactionRegistry, conn, pool, null, false);
    }

    /**
     * Create a PoolableManagedConnection.
     *
     * @param transactionRegistry
     *            transaction registry
     * @param conn
     *            underlying connection
     * @param pool
     *            connection pool
     * @param disconnectSqlCodes
     *            SQL_STATE codes considered fatal disconnection errors
     * @param fastFailValidation
     *            true means fatal disconnection errors cause subsequent validations to fail immediately (no attempt to
     *            run query or isValid)
     */
    public PoolableManagedConnection(final TransactionRegistry transactionRegistry, final Connection conn,
            final ObjectPool<PoolableConnection> pool, final Collection<String> disconnectSqlCodes,
            final boolean fastFailValidation) {
        super(conn, pool, null, disconnectSqlCodes, fastFailValidation);
        this.transactionRegistry = transactionRegistry;
    }

    /**
     * Actually close the underlying connection.
     */
    @Override
    public void reallyClose() throws SQLException {
        try {
            super.reallyClose();
        } finally {
            transactionRegistry.unregisterConnection(this);
        }
    }
}

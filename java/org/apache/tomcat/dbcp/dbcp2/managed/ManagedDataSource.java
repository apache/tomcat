/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.dbcp.dbcp2.managed;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

import org.apache.tomcat.dbcp.dbcp2.PoolingDataSource;
import org.apache.tomcat.dbcp.pool2.ObjectPool;

/**
 * The ManagedDataSource is a PoolingDataSource that creates ManagedConnections.
 *
 * @param <C>
 *            The kind of {@link Connection} to manage.
 * @since 2.0
 */
public class ManagedDataSource<C extends Connection> extends PoolingDataSource<C> {
    private TransactionRegistry transactionRegistry;

    /**
     * Creates a ManagedDataSource which obtains connections from the specified pool and manages them using the
     * specified transaction registry. The TransactionRegistry must be the transaction registry obtained from the
     * XAConnectionFactory used to create the connection pool. If not, an error will occur when attempting to use the
     * connection in a global transaction because the XAResource object associated with the connection will be
     * unavailable.
     *
     * @param pool
     *            the connection pool
     * @param transactionRegistry
     *            the transaction registry obtained from the XAConnectionFactory used to create the connection pool
     *            object factory
     */
    public ManagedDataSource(final ObjectPool<C> pool, final TransactionRegistry transactionRegistry) {
        super(pool);
        this.transactionRegistry = transactionRegistry;
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (getPool() == null) {
            throw new IllegalStateException("Pool has not been set");
        }
        if (transactionRegistry == null) {
            throw new IllegalStateException("TransactionRegistry has not been set");
        }

        return new ManagedConnection<>(getPool(), transactionRegistry, isAccessToUnderlyingConnectionAllowed());
    }

    /**
     * Gets the transaction registry.
     *
     * @return The transaction registry.
     * @see #setTransactionRegistry(TransactionRegistry)
     * @since 2.6.0
     */
    public TransactionRegistry getTransactionRegistry() {
        return transactionRegistry;
    }

    /**
     * Sets the transaction registry from the XAConnectionFactory used to create the pool. The transaction registry can
     * only be set once using either a connector or this setter method.
     *
     * @param transactionRegistry
     *            the transaction registry acquired from the XAConnectionFactory used to create the pool
     */
    public void setTransactionRegistry(final TransactionRegistry transactionRegistry) {
        if (this.transactionRegistry != null) {
            throw new IllegalStateException("TransactionRegistry already set");
        }
        Objects.requireNonNull(transactionRegistry, "transactionRegistry is null");

        this.transactionRegistry = transactionRegistry;
    }
}

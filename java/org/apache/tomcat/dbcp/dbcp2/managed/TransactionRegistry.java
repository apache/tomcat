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
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import javax.transaction.xa.XAResource;

import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;

import org.apache.tomcat.dbcp.dbcp2.DelegatingConnection;

/**
 * TransactionRegistry tracks Connections and XAResources in a transacted environment for a single XAConnectionFactory.
 * <p>
 * The TransactionRegistry hides the details of transaction processing from the existing DBCP pooling code, and gives
 * the ManagedConnection a way to enlist connections in a transaction, allowing for the maximal rescue of DBCP.
 * </p>
 *
 * @since 2.0
 */
public class TransactionRegistry {
    private final TransactionManager transactionManager;
    private final Map<Transaction, TransactionContext> caches = new WeakHashMap<>();
    private final Map<Connection, XAResource> xaResources = new WeakHashMap<>();
    private final TransactionSynchronizationRegistry transactionSynchronizationRegistry;

    /**
     * Provided for backwards compatibility
     * @param transactionManager the transaction manager used to enlist connections
     */
    public TransactionRegistry(final TransactionManager transactionManager) {
        this (transactionManager, null);
    }

    /**
     * Creates a TransactionRegistry for the specified transaction manager.
     *
     * @param transactionManager
     *            the transaction manager used to enlist connections.
     * @param transactionSynchronizationRegistry
     *              The optional TSR to register synchronizations with
     * @since 2.6.0
     */
    public TransactionRegistry(final TransactionManager transactionManager, final TransactionSynchronizationRegistry transactionSynchronizationRegistry) {
        this.transactionManager = transactionManager;
        this.transactionSynchronizationRegistry = transactionSynchronizationRegistry;
    }

    /**
     * Gets the active TransactionContext or null if not Transaction is active.
     *
     * @return The active TransactionContext or null if no Transaction is active.
     * @throws SQLException
     *             Thrown when an error occurs while fetching the transaction.
     */
    public TransactionContext getActiveTransactionContext() throws SQLException {
        Transaction transaction = null;
        try {
            transaction = transactionManager.getTransaction();

            // was there a transaction?
            if (transaction == null) {
                return null;
            }

            // This is the transaction on the thread so no need to check its status - we should try to use it and
            // fail later based on the subsequent status
        } catch (final SystemException e) {
            throw new SQLException("Unable to determine current transaction ", e);
        }

        // register the context (or create a new one)
        synchronized (this) {
            return caches.computeIfAbsent(transaction, k -> new TransactionContext(this, k, transactionSynchronizationRegistry));
        }
    }

    private Connection getConnectionKey(final Connection connection) {
        final Connection result;
        if (connection instanceof DelegatingConnection) {
            result = ((DelegatingConnection<?>) connection).getInnermostDelegateInternal();
        } else {
            result = connection;
        }
        return result;
    }

    /**
     * Gets the XAResource registered for the connection.
     *
     * @param connection
     *            the connection
     * @return The XAResource registered for the connection; never null.
     * @throws SQLException
     *             Thrown when the connection does not have a registered XAResource.
     */
    public synchronized XAResource getXAResource(final Connection connection) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        final Connection key = getConnectionKey(connection);
        final XAResource xaResource = xaResources.get(key);
        if (xaResource == null) {
            throw new SQLException("Connection does not have a registered XAResource " + connection);
        }
        return xaResource;
    }

    /**
     * Registers the association between a Connection and a XAResource. When a connection is enlisted in a transaction,
     * it is actually the XAResource that is given to the transaction manager.
     *
     * @param connection
     *            The JDBC connection.
     * @param xaResource
     *            The XAResource which managed the connection within a transaction.
     */
    public synchronized void registerConnection(final Connection connection, final XAResource xaResource) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(xaResource, "xaResource");
        xaResources.put(connection, xaResource);
    }

    /**
     * Unregisters a destroyed connection from {@link TransactionRegistry}.
     *
     * @param connection
     *            A destroyed connection from {@link TransactionRegistry}.
     */
    public synchronized void unregisterConnection(final Connection connection) {
        xaResources.remove(getConnectionKey(connection));
    }
}

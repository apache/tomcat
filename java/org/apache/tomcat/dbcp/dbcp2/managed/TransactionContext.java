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

import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.xa.XAResource;

/**
 * TransactionContext represents the association between a single XAConnectionFactory and a Transaction. This context
 * contains a single shared connection which should be used by all ManagedConnections for the XAConnectionFactory, the
 * ability to listen for the transaction completion event, and a method to check the status of the transaction.
 *
 * @since 2.0
 */
public class TransactionContext {
    private final TransactionRegistry transactionRegistry;
    private final WeakReference<Transaction> transactionRef;
    private Connection sharedConnection;
    private boolean transactionComplete;

    /**
     * Creates a TransactionContext for the specified Transaction and TransactionRegistry. The TransactionRegistry is
     * used to obtain the XAResource for the shared connection when it is enlisted in the transaction.
     *
     * @param transactionRegistry
     *            the TransactionRegistry used to obtain the XAResource for the shared connection
     * @param transaction
     *            the transaction
     */
    public TransactionContext(final TransactionRegistry transactionRegistry, final Transaction transaction) {
        Objects.requireNonNull(transactionRegistry, "transactionRegistry is null");
        Objects.requireNonNull(transaction, "transaction is null");
        this.transactionRegistry = transactionRegistry;
        this.transactionRef = new WeakReference<>(transaction);
        this.transactionComplete = false;
    }

    /**
     * Gets the connection shared by all ManagedConnections in the transaction. Specifically, connection using the same
     * XAConnectionFactory from which the TransactionRegistry was obtained.
     *
     * @return the shared connection for this transaction
     */
    public Connection getSharedConnection() {
        return sharedConnection;
    }

    /**
     * Sets the shared connection for this transaction. The shared connection is enlisted in the transaction.
     *
     * @param sharedConnection
     *            the shared connection
     * @throws SQLException
     *             if a shared connection is already set, if XAResource for the connection could not be found in the
     *             transaction registry, or if there was a problem enlisting the connection in the transaction
     */
    public void setSharedConnection(final Connection sharedConnection) throws SQLException {
        if (this.sharedConnection != null) {
            throw new IllegalStateException("A shared connection is already set");
        }

        // This is the first use of the connection in this transaction, so we must
        // enlist it in the transaction
        final Transaction transaction = getTransaction();
        try {
            final XAResource xaResource = transactionRegistry.getXAResource(sharedConnection);
            if (!transaction.enlistResource(xaResource)) {
                throw new SQLException("Unable to enlist connection in transaction: enlistResource returns 'false'.");
            }
        } catch (final IllegalStateException e) {
            // This can happen if the transaction is already timed out
            throw new SQLException("Unable to enlist connection in the transaction", e);
        } catch (final RollbackException e) {
            // transaction was rolled back... proceed as if there never was a transaction
        } catch (final SystemException e) {
            throw new SQLException("Unable to enlist connection the transaction", e);
        }

        this.sharedConnection = sharedConnection;
    }

    /**
     * Adds a listener for transaction completion events.
     *
     * @param listener
     *            the listener to add
     * @throws SQLException
     *             if a problem occurs adding the listener to the transaction
     */
    public void addTransactionContextListener(final TransactionContextListener listener) throws SQLException {
        try {
            getTransaction().registerSynchronization(new Synchronization() {
                @Override
                public void beforeCompletion() {
                    // empty
                }

                @Override
                public void afterCompletion(final int status) {
                    listener.afterCompletion(TransactionContext.this, status == Status.STATUS_COMMITTED);
                }
            });
        } catch (final RollbackException e) {
            // JTA spec doesn't let us register with a transaction marked rollback only
            // just ignore this and the tx state will be cleared another way.
        } catch (final Exception e) {
            throw new SQLException("Unable to register transaction context listener", e);
        }
    }

    /**
     * True if the transaction is active or marked for rollback only.
     *
     * @return true if the transaction is active or marked for rollback only; false otherwise
     * @throws SQLException
     *             if a problem occurs obtaining the transaction status
     */
    public boolean isActive() throws SQLException {
        try {
            final Transaction transaction = this.transactionRef.get();
            if (transaction == null) {
                return false;
            }
            final int status = transaction.getStatus();
            return status == Status.STATUS_ACTIVE || status == Status.STATUS_MARKED_ROLLBACK;
        } catch (final SystemException e) {
            throw new SQLException("Unable to get transaction status", e);
        }
    }

    private Transaction getTransaction() throws SQLException {
        final Transaction transaction = this.transactionRef.get();
        if (transaction == null) {
            throw new SQLException("Unable to enlist connection because the transaction has been garbage collected");
        }
        return transaction;
    }

    /**
     * Sets the transaction complete flag to true.
     *
     * @since 2.4.0
     */
    public void completeTransaction() {
        this.transactionComplete = true;
    }

    /**
     * Gets the transaction complete flag to true.
     *
     * @return The transaction complete flag.
     *
     * @since 2.4.0
     */
    public boolean isTransactionComplete() {
        return this.transactionComplete;
    }
}

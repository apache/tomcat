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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.tomcat.dbcp.dbcp2.DelegatingConnection;
import org.apache.tomcat.dbcp.pool2.ObjectPool;

/**
 * ManagedConnection is responsible for managing a database connection in a transactional environment (typically called
 * "Container Managed"). A managed connection operates like any other connection when no global transaction (a.k.a. XA
 * transaction or JTA Transaction) is in progress. When a global transaction is active a single physical connection to
 * the database is used by all ManagedConnections accessed in the scope of the transaction. Connection sharing means
 * that all data access during a transaction has a consistent view of the database. When the global transaction is
 * committed or rolled back the enlisted connections are committed or rolled back. Typically upon transaction
 * completion, a connection returns to the auto commit setting in effect before being enlisted in the transaction, but
 * some vendors do not properly implement this.
 * <p>
 * When enlisted in a transaction the setAutoCommit(), commit(), rollback(), and setReadOnly() methods throw a
 * SQLException. This is necessary to assure that the transaction completes as a single unit.
 * </p>
 *
 * @param <C>
 *            the Connection type
 *
 * @since 2.0
 */
public class ManagedConnection<C extends Connection> extends DelegatingConnection<C> {

    private final ObjectPool<C> pool;
    private final TransactionRegistry transactionRegistry;
    private final boolean accessToUnderlyingConnectionAllowed;
    private TransactionContext transactionContext;
    private boolean isSharedConnection;
    private final Lock lock;

    /**
     * Constructs a new instance responsible for managing a database connection in a transactional environment.
     *
     * @param pool
     *            The connection pool.
     * @param transactionRegistry
     *            The transaction registry.
     * @param accessToUnderlyingConnectionAllowed
     *            Whether or not to allow access to the underlying Connection.
     * @throws SQLException
     *             Thrown when there is problem managing transactions.
     */
    public ManagedConnection(final ObjectPool<C> pool, final TransactionRegistry transactionRegistry,
            final boolean accessToUnderlyingConnectionAllowed) throws SQLException {
        super(null);
        this.pool = pool;
        this.transactionRegistry = transactionRegistry;
        this.accessToUnderlyingConnectionAllowed = accessToUnderlyingConnectionAllowed;
        this.lock = new ReentrantLock();
        updateTransactionStatus();
    }

    @Override
    protected void checkOpen() throws SQLException {
        super.checkOpen();
        updateTransactionStatus();
    }

    private void updateTransactionStatus() throws SQLException {
        // if there is a is an active transaction context, assure the transaction context hasn't changed
        if (transactionContext != null && !transactionContext.isTransactionComplete()) {
            if (transactionContext.isActive()) {
                if (transactionContext != transactionRegistry.getActiveTransactionContext()) {
                    throw new SQLException("Connection can not be used while enlisted in another transaction");
                }
                return;
            }
            // transaction should have been cleared up by TransactionContextListener, but in
            // rare cases another lister could have registered which uses the connection before
            // our listener is called. In that rare case, trigger the transaction complete call now
            transactionComplete();
        }

        // the existing transaction context ended (or we didn't have one), get the active transaction context
        transactionContext = transactionRegistry.getActiveTransactionContext();

        // if there is an active transaction context and it already has a shared connection, use it
        if (transactionContext != null && transactionContext.getSharedConnection() != null) {
            // A connection for the connection factory has already been enrolled
            // in the transaction, replace our delegate with the enrolled connection

            // return current connection to the pool
            final C connection = getDelegateInternal();
            setDelegate(null);
            if (connection != null) {
                try {
                    pool.returnObject(connection);
                } catch (final Exception ignored) {
                    // whatever... try to invalidate the connection
                    try {
                        pool.invalidateObject(connection);
                    } catch (final Exception ignore) {
                        // no big deal
                    }
                }
            }

            // add a listener to the transaction context
            transactionContext.addTransactionContextListener(new CompletionListener());

            // Set our delegate to the shared connection. Note that this will
            // always be of type C since it has been shared by another
            // connection from the same pool.
            @SuppressWarnings("unchecked")
            final C shared = (C) transactionContext.getSharedConnection();
            setDelegate(shared);

            // remember that we are using a shared connection so it can be cleared after the
            // transaction completes
            isSharedConnection = true;
        } else {
            C connection = getDelegateInternal();
            // if our delegate is null, create one
            if (connection == null) {
                try {
                    // borrow a new connection from the pool
                    connection = pool.borrowObject();
                    setDelegate(connection);
                } catch (final Exception e) {
                    throw new SQLException("Unable to acquire a new connection from the pool", e);
                }
            }

            // if we have a transaction, out delegate becomes the shared delegate
            if (transactionContext != null) {
                // add a listener to the transaction context
                transactionContext.addTransactionContextListener(new CompletionListener());

                // register our connection as the shared connection
                try {
                    transactionContext.setSharedConnection(connection);
                } catch (final SQLException e) {
                    // transaction is hosed
                    transactionContext = null;
                    try {
                        pool.invalidateObject(connection);
                    } catch (final Exception e1) {
                        // we are try but no luck
                    }
                    throw e;
                }
            }
        }
        // autoCommit may have been changed directly on the underlying
        // connection
        clearCachedState();
    }

    @Override
    public void close() throws SQLException {
        if (!isClosedInternal()) {
            // Don't actually close the connection if in a transaction. The
            // connection will be closed by the transactionComplete method.
            //
            // DBCP-484 we need to make sure setClosedInternal(true) being
            // invoked if transactionContext is not null as this value will
            // be modified by the transactionComplete method which could run
            // in the different thread with the transaction calling back.
            lock.lock();
            try {
                if (transactionContext == null || transactionContext.isTransactionComplete()) {
                    super.close();
                }
            } finally {
                try {
                    setClosedInternal(true);
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    /**
     * Delegates to {@link ManagedConnection#transactionComplete()} for transaction completion events.
     *
     * @since 2.0
     */
    protected class CompletionListener implements TransactionContextListener {
        @Override
        public void afterCompletion(final TransactionContext completedContext, final boolean commited) {
            if (completedContext == transactionContext) {
                transactionComplete();
            }
        }
    }

    protected void transactionComplete() {
        lock.lock();
        try {
            transactionContext.completeTransaction();
        } finally {
            lock.unlock();
        }

        // If we were using a shared connection, clear the reference now that
        // the transaction has completed
        if (isSharedConnection) {
            setDelegate(null);
            isSharedConnection = false;
        }

        // If this connection was closed during the transaction and there is
        // still a delegate present close it
        final Connection delegate = getDelegateInternal();
        if (isClosedInternal() && delegate != null) {
            try {
                setDelegate(null);

                if (!delegate.isClosed()) {
                    delegate.close();
                }
            } catch (final SQLException ignored) {
                // Not a whole lot we can do here as connection is closed
                // and this is a transaction callback so there is no
                // way to report the error.
            }
        }
    }

    //
    // The following methods can't be used while enlisted in a transaction
    //

    @Override
    public void setAutoCommit(final boolean autoCommit) throws SQLException {
        if (transactionContext != null) {
            throw new SQLException("Auto-commit can not be set while enrolled in a transaction");
        }
        super.setAutoCommit(autoCommit);
    }

    @Override
    public void commit() throws SQLException {
        if (transactionContext != null) {
            throw new SQLException("Commit can not be set while enrolled in a transaction");
        }
        super.commit();
    }

    @Override
    public void rollback() throws SQLException {
        if (transactionContext != null) {
            throw new SQLException("Commit can not be set while enrolled in a transaction");
        }
        super.rollback();
    }

    @Override
    public void setReadOnly(final boolean readOnly) throws SQLException {
        if (transactionContext != null) {
            throw new SQLException("Read-only can not be set while enrolled in a transaction");
        }
        super.setReadOnly(readOnly);
    }

    //
    // Methods for accessing the delegate connection
    //

    /**
     * If false, getDelegate() and getInnermostDelegate() will return null.
     *
     * @return if false, getDelegate() and getInnermostDelegate() will return null
     */
    public boolean isAccessToUnderlyingConnectionAllowed() {
        return accessToUnderlyingConnectionAllowed;
    }

    @Override
    public C getDelegate() {
        if (isAccessToUnderlyingConnectionAllowed()) {
            return getDelegateInternal();
        }
        return null;
    }

    @Override
    public Connection getInnermostDelegate() {
        if (isAccessToUnderlyingConnectionAllowed()) {
            return super.getInnermostDelegateInternal();
        }
        return null;
    }
}

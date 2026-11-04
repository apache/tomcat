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
package org.apache.tomcat.jdbc.pool.interceptor;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.DataSourceFactory;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * Interceptor that keep track of connection state to avoid roundtrips to the database. The
 * {@link org.apache.tomcat.jdbc.pool.ConnectionPool} is optimized to do as little work as possible. The pool itself
 * doesn't remember settings like {@link java.sql.Connection#setAutoCommit(boolean)},
 * {@link java.sql.Connection#setReadOnly(boolean)}, {@link java.sql.Connection#setCatalog(String)} or
 * {@link java.sql.Connection#setTransactionIsolation(int)}. It relies on the application to remember how and when these
 * settings have been applied. In the cases where the application code doesn't know or want to keep track of the state,
 * this interceptor helps cache the state, and it also avoids roundtrips to the database asking for it.
 */

public class ConnectionState extends JdbcInterceptor {
    private static final Log log = LogFactory.getLog(ConnectionState.class);

    /**
     * Creates a new ConnectionState interceptor instance.
     */
    public ConnectionState() {
        super();
    }

    /**
     * Method names for reading connection state.
     */
    protected final String[] readState = { "getAutoCommit", "getTransactionIsolation", "isReadOnly", "getCatalog" };

    /**
     * Method names for writing connection state.
     */
    protected final String[] writeState = { "setAutoCommit", "setTransactionIsolation", "setReadOnly", "setCatalog" };

    /**
     * Cached auto-commit state of the connection.
     */
    protected Boolean autoCommit = null;

    /**
     * Cached transaction isolation level of the connection.
     */
    protected Integer transactionIsolation = null;

    /**
     * The transaction isolation level this physical session refused, or
     * {@link DataSourceFactory#UNKNOWN_TRANSACTIONISOLATION} when the session has
     * not refused one. A refused level does not count as needing a restore on later
     * borrows, which keeps the pool out of a loop of doomed restore attempts, error
     * logs and transaction terminations on every borrow; see {@link #reset}. The
     * record covers the refused level only: the pool reads the configured level on
     * every borrow, so a level this session has not refused - for instance one
     * configured after the refusal - is restored as usual. Cleared when the session
     * is replaced, so every physical session gets its own attempt. The interceptor
     * instance belongs to one pooled connection, so a plain field needs no
     * synchronisation.
     */
    private int refusedIsolationLevel = DataSourceFactory.UNKNOWN_TRANSACTIONISOLATION;

    /**
     * Cached read-only state of the connection.
     */
    protected Boolean readOnly = null;

    /**
     * Cached catalog (database) name of the connection.
     */
    protected String catalog = null;


    /**
     * Restores the connection state the pool was configured to enforce. The pool invokes this on every borrow, before
     * the connection is handed to its next borrower. If the previous borrower returned the connection while still in a
     * transaction, that transaction is terminated first: the isolation level and the read only state are transaction
     * scoped on many drivers, and an open transaction would otherwise absorb the restores below (or, when auto-commit
     * is restored, silently commit the previous borrower's uncommitted work). If terminating that transaction fails,
     * the borrow fails and the pool retires the connection. So does a failed restore of the read-only, auto-commit or
     * catalog state: a connection known to sit in a state other than the configured default must not reach the next
     * borrower. A transaction isolation level the driver refuses is the exception: the connection stays in service,
     * so the pool never strands borrowers over that setting. The refusal is logged once, at refusal time, and that
     * level then stops counting as a pending restore on that session - later borrows neither retry it nor log it
     * again nor terminate a pending transaction on its behalf. The record covers the refused level only: the pool
     * reads the configured level on every borrow, so a level the session has not refused yet - one configured after
     * the refusal, for instance - is attempted and applied as usual, and a replaced session retries even the same
     * level. If the refused level was the only pending restore, the next borrower then inherits an open transaction
     * left by the previous one, exactly as it does for a pool that configures no defaults at all. Nothing is
     * terminated when there is nothing to restore - no default is configured, the
     * cache already shows every configured default in place, or the only default that would need restoring is one
     * this session refused - so a pool that uses this interceptor only to cache the state does not pay for the
     * termination.
     *
     * @param parent the pool the connection belongs to, or {@code null} when the interceptor chain is being torn down
     * @param con    the pooled connection being set up for its next borrower
     */
    @SuppressWarnings("null") // Suppress false positive warnings
    @Override
    public void reset(ConnectionPool parent, PooledConnection con) {
        if (parent == null || con == null) {
            // We are resetting, reset our defaults
            autoCommit = null;
            transactionIsolation = null;
            readOnly = null;
            catalog = null;
            refusedIsolationLevel = DataSourceFactory.UNKNOWN_TRANSACTIONISOLATION;
            return;
        }
        PoolConfiguration poolProperties = parent.getPoolProperties();
        final int defaultTransactionIsolation = poolProperties.getDefaultTransactionIsolation();
        final Boolean defaultReadOnly = poolProperties.getDefaultReadOnly();
        final Boolean defaultAutoCommit = poolProperties.getDefaultAutoCommit();
        final String defaultCatalog = poolProperties.getDefaultCatalog();
        boolean resetTransactionIsolation = defaultTransactionIsolation != DataSourceFactory.UNKNOWN_TRANSACTIONISOLATION &&
                refusedIsolationLevel != defaultTransactionIsolation &&
                (transactionIsolation == null || transactionIsolation.intValue() != defaultTransactionIsolation);
        boolean resetReadOnly = defaultReadOnly != null &&
                (readOnly == null || readOnly.booleanValue() != defaultReadOnly.booleanValue());
        boolean resetAutoCommit = defaultAutoCommit != null &&
                (autoCommit == null || autoCommit.booleanValue() != defaultAutoCommit.booleanValue());
        boolean resetCatalog = defaultCatalog != null && (catalog == null || !catalog.equals(defaultCatalog));
        if (resetTransactionIsolation || resetReadOnly || resetAutoCommit || resetCatalog) {
            // The previous borrower may have returned this connection while
            // its transaction was still open, unless the pool was configured
            // to terminate transactions on return. Transaction isolation and
            // read only state are transaction scoped on many drivers (the
            // change is refused or deferred until the next transaction) and
            // restoring auto-commit to true would commit whatever the
            // previous borrower left pending. Terminate that transaction
            // first so the restores below are actually applied.
            try {
                Connection raw = con.getConnection();
                if (!raw.getAutoCommit()) {
                    raw.rollback();
                }
            } catch (SQLException x) {
                // Whether the previous borrower's work survived is unknown,
                // so the connection must not go to the next borrower.
                // ConnectionPool.setupConnection() catches this, discards the
                // connection and fails the borrow.
                throw new IllegalStateException(
                        MessageFactory.get("connectionState.unableToTerminatePendingTransaction"), x);
            }
        }
        if (resetTransactionIsolation) {
            try {
                if (transactionIsolation == null || transactionIsolation.intValue() != defaultTransactionIsolation) {
                    con.getConnection().setTransactionIsolation(defaultTransactionIsolation);
                    transactionIsolation = Integer.valueOf(defaultTransactionIsolation);
                }
            } catch (SQLException x) {
                transactionIsolation = null;
                // A driver that refuses a level refuses it for this session,
                // and the configured level is pool configuration that changes
                // rarely, so retrying the refused level per borrow would keep
                // the pool paying a doomed round trip, a termination of
                // whatever the previous borrower left pending, and this error
                // log - on every borrow, forever. Record the refused level so
                // it stops counting as a pending restore; the next borrower
                // inherits an open transaction the way it does on a pool that
                // configures no defaults at all. A later configuration of a
                // different level, or a replaced session, clears the way for a
                // fresh attempt: this record names only the refused level.
                // Report it here, at refusal time, as no later borrow will log
                // it again.
                refusedIsolationLevel = defaultTransactionIsolation;
                log.error(MessageFactory.get("connectionState.unableToResetTransactionIsolation",
                        defaultTransactionIsolation), x);
            }
        }
        if (resetReadOnly) {
            try {
                if (readOnly == null || readOnly.booleanValue() != defaultReadOnly.booleanValue()) {
                    con.getConnection().setReadOnly(defaultReadOnly.booleanValue());
                    readOnly = defaultReadOnly;
                }
            } catch (SQLException x) {
                readOnly = null;
                // The connection is known to sit in a state the pool was
                // configured not to hand out, and the restore that would fix
                // that failed, so it must not go to the next borrower.
                // ConnectionPool.setupConnection() catches this, discards the
                // connection and fails the borrow.
                throw new IllegalStateException(
                        MessageFactory.get("connectionState.unableToRestoreReadOnly", defaultReadOnly), x);
            }
        }
        if (resetAutoCommit) {
            try {
                if (autoCommit == null || autoCommit.booleanValue() != defaultAutoCommit.booleanValue()) {
                    con.getConnection().setAutoCommit(defaultAutoCommit.booleanValue());
                    autoCommit = defaultAutoCommit;
                }
            } catch (SQLException x) {
                autoCommit = null;
                // As for the read-only state above: the driver refused the
                // configured auto-commit state, so the borrow fails and the
                // connection is discarded.
                throw new IllegalStateException(
                        MessageFactory.get("connectionState.unableToRestoreAutoCommit", defaultAutoCommit), x);
            }
        }
        if (resetCatalog) {
            try {
                if (catalog == null || (!catalog.equals(defaultCatalog))) {
                    con.getConnection().setCatalog(defaultCatalog);
                    catalog = defaultCatalog;
                }
            } catch (SQLException x) {
                catalog = null;
                // As for the read-only state above: the driver refused the
                // configured catalog, so the borrower learns of the
                // misconfiguration instead of getting a connection that was
                // never pointed at the database the pool asks it to work in.
                throw new IllegalStateException(
                        MessageFactory.get("connectionState.unableToRestoreCatalog", defaultCatalog), x);
            }
        }

    }


    @Override
    public void disconnected(ConnectionPool parent, PooledConnection con, boolean finalizing) {
        // We are resetting, reset our defaults
        autoCommit = null;
        transactionIsolation = null;
        readOnly = null;
        catalog = null;
        refusedIsolationLevel = DataSourceFactory.UNKNOWN_TRANSACTIONISOLATION;
        super.disconnected(parent, con, finalizing);
    }


    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        boolean read = false;
        int index = -1;
        for (int i = 0; (!read) && i < readState.length; i++) {
            read = compare(name, readState[i]);
            if (read) {
                index = i;
            }
        }
        boolean write = false;
        for (int i = 0; (!write) && (!read) && i < writeState.length; i++) {
            write = compare(name, writeState[i]);
            if (write) {
                index = i;
            }
        }
        Object result = null;
        if (read) {
            switch (index) {
                case 0: {
                    result = autoCommit;
                    break;
                }
                case 1: {
                    result = transactionIsolation;
                    break;
                }
                case 2: {
                    result = readOnly;
                    break;
                }
                case 3: {
                    result = catalog;
                    break;
                }
                default: // NOOP
            }
            // Return cached result, if we have it
            if (result != null) {
                return result;
            }
        }

        try {
            result = super.invoke(proxy, method, args);
            if (read || write) {
                switch (index) {
                    case 0: {
                        autoCommit = (Boolean) (read ? result : args[0]);
                        break;
                    }
                    case 1: {
                        transactionIsolation = (Integer) (read ? result : args[0]);
                        break;
                    }
                    case 2: {
                        readOnly = (Boolean) (read ? result : args[0]);
                        break;
                    }
                    case 3: {
                        catalog = (String) (read ? result : args[0]);
                        break;
                    }
                }
            }
        } catch (Throwable e) {
            if (write) {
                log.warn(MessageFactory.get("connectionState.resetStateToNull", name), e);
                switch (index) {
                    case 0: {
                        autoCommit = null;
                        break;
                    }
                    case 1: {
                        transactionIsolation = null;
                        break;
                    }
                    case 2: {
                        readOnly = null;
                        break;
                    }
                    case 3: {
                        catalog = null;
                        break;
                    }
                }
            }
            throw e;
        }
        return result;
    }

}

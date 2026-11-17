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
import org.apache.tomcat.jdbc.pool.PoolUtilities;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.apache.tomcat.jdbc.pool.ProxyConnection;
import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * Interceptor that keeps track of connection state to avoid roundtrips to the database. The
 * {@link org.apache.tomcat.jdbc.pool.ConnectionPool} is optimized to do as little work as possible. The pool itself
 * doesn't remember settings like {@link java.sql.Connection#setAutoCommit(boolean)},
 * {@link java.sql.Connection#setReadOnly(boolean)}, {@link java.sql.Connection#setCatalog(String)} or
 * {@link java.sql.Connection#setTransactionIsolation(int)}. It relies on the application to remember how and when these
 * settings have been applied. In the cases where the application code doesn't know or want to keep track of the state,
 * this interceptor helps cache the state, and it also avoids roundtrips to the database asking for it.
 * <p>
 * Caching is only half the role. Once this interceptor is configured, the pool hands it the enforcement of the four
 * configured defaults - {@code defaultAutoCommit}, {@code defaultReadOnly}, {@code defaultTransactionIsolation} and
 * {@code defaultCatalog}: {@link org.apache.tomcat.jdbc.pool.PooledConnection#connect()} no longer applies them at
 * connection creation, and {@link #reset(ConnectionPool, PooledConnection)} applies them to every borrow and re-applies
 * whatever a previous borrower changed, on the terms that method states.
 * <p>
 * A subclass that overrides {@link #reset(ConnectionPool, PooledConnection)} must call the superclass, or apply those
 * defaults itself. The pool recognises this class by type and, having found it, leaves the defaults to the interceptor
 * alone; an override that skips the superclass therefore silently drops both the connect-time application and the
 * borrow-time restore, warning no one.
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
     * Method names for reading connection state. The index into this array and into {@link #writeState} is the
     * identity of a tracked state across the class: 0 is the auto-commit state, 1 the transaction isolation level,
     * 2 the read only state and 3 the catalog. The entry at an index of {@link #writeState} is the setter whose
     * value the cache field of the same index holds, and {@link #invoke} matches both arrays by index against the
     * cache fields. Reordering one array without the other would map a setter's value into the wrong field; adding
     * a tracked state means all three - both arrays, the cache field, and the matching in {@link #invoke} - at once.
     */
    protected final String[] readState = { "getAutoCommit", "getTransactionIsolation", "isReadOnly", "getCatalog" };

    /**
     * Method names for writing connection state, indexed as {@link #readState}.
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
     * {@link DataSourceFactory#UNKNOWN_TRANSACTIONISOLATION} when the session has not refused one. Only confirmed
     * refusals are recorded ({@link #confirmAgainstFreedSession}). The record covers the refused level only, so a
     * level this session has not refused - one configured after the refusal, for instance - is still attempted, and
     * a replaced session gets a fresh attempt at every level. The interceptor instance belongs to one pooled
     * connection, so a plain field needs no synchronisation.
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
     * The pooled connection whose current borrow this instance serves,
     * recorded by every {@link #reset} that arms the chain for a borrower
     * and cleared when the session is taken out of service. Cached state
     * reads are answered above the borrow fence at the tail of the chain -
     * a hit never reaches it - so a hit may only stand while this record
     * still describes a session in service: once the pool has discarded the
     * session or taken it out of service, the read falls through to the
     * fence and fails like every other call on the handle. Which borrower
     * an in-service session belongs to is not something this record can
     * see - a clean recycle hands it the same object - and that question is
     * the tail's handle identity answers, so both are asked
     * ({@link #chainFence}, {@link #stillServed}).
     */
    private PooledConnection served = null;

    /**
     * The {@link ProxyConnection} at the tail of the chain this instance serves in, resolved once on first use.
     * Only the tail's identity of the handle the current borrow runs under can tell a cached read of that borrow
     * from one left parked above this cache across a clean recycle ({@link #stillServed}).
     */
    private ProxyConnection chainFence = null;


    /**
     * Restores the connection state the pool was configured to enforce. The pool invokes this on every borrow, before
     * the connection is handed to its next borrower.
     * <p>
     * When any restore is pending, a transaction the previous borrower left open is terminated first: isolation and
     * read only are transaction scoped on many drivers, an open transaction would absorb the restores, and restoring
     * auto-commit would silently commit the previous borrower's work. Failing to terminate that transaction, and a
     * confirmed refusal to restore the read-only, auto-commit or catalog state, fail the borrow and retire the
     * session - a connection known to sit in a state other than the configured one must not reach a borrower. A
     * refusal the driver blames on the session's transaction state is confirmed first, against a session freed of it
     * ({@link #confirmAgainstFreedSession}).
     * <p>
     * A transaction isolation level the driver refuses on a freed session is the one confirmed failure that does not
     * retire the connection: the level is recorded ({@link #refusedIsolationLevel}) and stops counting as a pending
     * restore, so later borrows neither retry it nor pay a transaction termination on its behalf. When nothing needs
     * restoring, nothing is terminated - a pool that uses this interceptor only to cache the state pays nothing.
     *
     * @param parent the pool the connection belongs to, or {@code null} when the interceptor chain is being torn down
     * @param con    the pooled connection being set up for its next borrower
     */
    @SuppressWarnings("null") // The resetX booleans guard each defaultX for null, but do not read to ECJ as doing so
    @Override
    public void reset(ConnectionPool parent, PooledConnection con) {
        if (parent == null || con == null) {
            // Teardown walk: the chain is being taken out of service, and
            // nothing may be remembered about a session that is gone.
            autoCommit = null;
            transactionIsolation = null;
            readOnly = null;
            catalog = null;
            refusedIsolationLevel = DataSourceFactory.UNKNOWN_TRANSACTIONISOLATION;
            served = null;
            return;
        }
        // The record of the served connection travels with the arming of
        // the chain and the caches, as served's javadoc states.
        served = con;
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
            // The javadoc's terminate-first step. The transaction may have
            // survived the return, unless the pool was configured to
            // terminate transactions there; the probe is the same one the
            // pool's own termination paths use - auto-commit off means a
            // transaction is open.
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
                // and the configured level changes rarely, so retrying per
                // borrow would keep the pool paying a doomed round trip and
                // its error log forever - the refusal is recorded
                // (refusedIsolationLevel) and reported here, at refusal
                // time. A refusal reported as a transaction-state error may
                // instead blame state the auto-commit probe above never saw,
                // so it first gets its confirmation against a freed session.
                SQLException confirmed = confirmAgainstFreedSession(con, x,
                        raw -> raw.setTransactionIsolation(defaultTransactionIsolation));
                if (confirmed == null) {
                    transactionIsolation = Integer.valueOf(defaultTransactionIsolation);
                } else {
                    refusedIsolationLevel = defaultTransactionIsolation;
                    log.error(MessageFactory.get("connectionState.unableToResetTransactionIsolation",
                            Integer.valueOf(defaultTransactionIsolation)),
                            PoolUtilities.maskCredentials(confirmed));
                }
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
                // A refusal the driver blames on the session's transaction
                // state gets its confirmation against a session freed of it
                // first: this very reset exists to clear exactly that state,
                // and a driver that accepts the restore there was never
                // unable to apply it.
                SQLException confirmed = confirmAgainstFreedSession(con, x,
                        raw -> raw.setReadOnly(defaultReadOnly.booleanValue()));
                if (confirmed == null) {
                    readOnly = defaultReadOnly;
                } else {
                    // The connection is known to sit in a state the pool was
                    // configured not to hand out, and the restore that would
                    // fix that failed even on a session freed of its
                    // transaction, so it must not go to the next borrower.
                    // ConnectionPool.setupConnection() catches this, discards
                    // the connection and fails the borrow.
                    throw new IllegalStateException(
                            MessageFactory.get("connectionState.unableToRestoreReadOnly", defaultReadOnly),
                            confirmed);
                }
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
                // configured auto-commit state even on a session freed of its
                // transaction, so the borrow fails and the connection is
                // discarded.
                SQLException confirmed = confirmAgainstFreedSession(con, x,
                        raw -> raw.setAutoCommit(defaultAutoCommit.booleanValue()));
                if (confirmed == null) {
                    autoCommit = defaultAutoCommit;
                } else {
                    throw new IllegalStateException(
                            MessageFactory.get("connectionState.unableToRestoreAutoCommit", defaultAutoCommit),
                            confirmed);
                }
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
                // configured catalog even on a session freed of its
                // transaction, so the borrower learns of the misconfiguration
                // instead of getting a connection that was never pointed at
                // the database the pool asks it to work in.
                SQLException confirmed = confirmAgainstFreedSession(con, x,
                        raw -> raw.setCatalog(defaultCatalog));
                if (confirmed == null) {
                    catalog = defaultCatalog;
                } else {
                    throw new IllegalStateException(
                            MessageFactory.get("connectionState.unableToRestoreCatalog", defaultCatalog),
                            confirmed);
                }
            }
        }

    }


    /**
     * One attempt at restoring a configured connection state onto a session.
     */
    private interface RestoreAttempt {
        void apply(Connection raw) throws SQLException;
    }


    /**
     * Confirms a restore refusal that the driver blamed on the session's
     * transaction state - SQLSTATE class 25, the "not while a transaction is
     * open" answer MySQL (error 1568) and PostgreSQL give a mid-transaction
     * read-only, auto-commit, isolation-level or catalog change - against a
     * session freed of that state, and answers with the refusal that stands.
     * The probe that terminates a pending transaction ahead of the restores
     * cannot see a transaction the previous borrower started with raw SQL
     * while auto-commit was on, so the refusal may be about that hidden
     * state rather than about the value being restored - and clearing that
     * state is what the restores around this refusal exist to do. The
     * session's transaction is therefore cleared with a rollback - a no-op
     * the major drivers tolerate when none is open - and the restore is
     * attempted once more on the freed session.
     *
     * @param con     the pooled connection whose session produced the refusal
     * @param refusal the refusal to confirm, reported while the session still
     *                held the state it blames
     * @param retry   the same restore, attempted again on the freed session
     * @return {@code null} if the freed session accepted the restore; the
     *         exception with which the freed session refuses the restore
     *         for some other reason than its transaction state, confirming
     *         the refusal; or the original refusal, which blamed no
     *         transaction state and stands as reported
     * @throws IllegalStateException if the confirming rollback fails, or the
     *         freed session answers the retry with the transaction-state
     *         error again - either way the transaction the refusal blamed is
     *         not cleared, and whether the previous borrower's work survived
     *         is unknown, so the connection must not go to the next borrower
     *         - and the refusal must not be taken as confirmed either, as it
     *         was never answered by a session freed of the state it blamed.
     *         ConnectionPool.setupConnection() catches this, discards the
     *         connection and fails the borrow.
     */
    private SQLException confirmAgainstFreedSession(PooledConnection con, SQLException refusal,
            RestoreAttempt retry) {
        final String sqlState = refusal.getSQLState();
        if (sqlState == null || !sqlState.startsWith("25")) {
            // The driver did not blame the session's transaction state:
            // there is no state to clear and the refusal stands as reported.
            return refusal;
        }
        Connection raw = con.getConnection();
        try {
            raw.rollback();
        } catch (SQLException z) {
            throw new IllegalStateException(
                    MessageFactory.get("connectionState.unableToTerminatePendingTransaction"), z);
        }
        try {
            retry.apply(raw);
            return null;
        } catch (SQLException y) {
            final String retryState = y.getSQLState();
            if (retryState != null && retryState.startsWith("25")) {
                // The freed session still answers with the transaction-state
                // error: the state the refusal blamed is not cleared, or the
                // driver keeps reporting it. A transaction that will not
                // clear is the one state no borrower may be handed, so the
                // session must not stay in service - as for a confirming
                // rollback that itself fails, whether the previous
                // borrower's work survived is unknown. And the refusal is
                // not confirmed either: what repeats is still the state's
                // refusal, not the value's own, so it must not be recorded.
                throw new IllegalStateException(
                        MessageFactory.get(
                                "connectionState.unableToTerminatePendingTransaction"), y);
            }
            // Refused a second time, now for a reason that is not the
            // session's transaction state: the refusal is the value's own.
            return y;
        }
    }


    @Override
    public void disconnected(ConnectionPool parent, PooledConnection con, boolean finalizing) {
        // The session goes out of service: everything the caches describe
        // is about to stop existing.
        autoCommit = null;
        transactionIsolation = null;
        readOnly = null;
        catalog = null;
        refusedIsolationLevel = DataSourceFactory.UNKNOWN_TRANSACTIONISOLATION;
        served = null;
        super.disconnected(parent, con, finalizing);
    }


    /**
     * Answers whether a cached state read may stand: the session it
     * describes must still be in service - not discarded under the
     * borrower, its physical session not taken out of service - and the
     * handle the read arrived on must still be the handle the chain's tail
     * is armed for, which is the only way to tell a read of the current
     * borrow from one left parked across a clean recycle that re-armed the
     * very same session for the next borrower. A false answer lets the
     * read fall through to the chain's tail, where the borrow fence answers
     * about the handle the way it answers every other call.
     *
     * @param proxy the connection handle the read arrived on
     * @return true if a cached state read may be answered
     */
    private boolean stillServed(Object proxy) {
        PooledConnection pc = served;
        if (pc == null || pc.isDiscarded() || pc.getConnection() == null) {
            return false;
        }
        ProxyConnection fence = resolveChainFence();
        return proxy != null && fence != null && proxy == fence.getBorrowHandle();
    }


    /**
     * Resolves, once, the proxy connection at the tail of the chain this
     * instance serves in. The chain's wiring is fixed when it is built and
     * an interceptor instance belongs to exactly one chain, so the walk
     * below runs until the tail is found and then never again. A chain with
     * no tail - which the pool's own setup already rules out - resolves to
     * {@code null}, and the caller lets the read fall through: a
     * conservative fall-through is never a wrong answer, while an answer
     * the fence would have refused is.
     *
     * @return the borrow fence at the chain's tail, or {@code null} if
     *         there is none
     */
    private ProxyConnection resolveChainFence() {
        ProxyConnection tail = chainFence;
        if (tail == null) {
            JdbcInterceptor link = this;
            while (link != null && !(link instanceof ProxyConnection)) {
                link = link.getNext();
            }
            if (link instanceof ProxyConnection) {
                tail = (ProxyConnection) link;
                chainFence = tail;
            }
        }
        return tail;
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
            // A cached read answers only while stillServed says it may
            // stand; otherwise it falls through to the chain's tail, which
            // answers the handle the way it answers every other call.
            if (result != null && stillServed(proxy)) {
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
                log.warn(MessageFactory.get("connectionState.resetStateToNull", name),
                        PoolUtilities.maskCredentials(e));
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

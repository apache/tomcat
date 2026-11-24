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
 * whatever a previous borrower changed, on the terms that method states. A state the pool does not configure is
 * restored to the value the physical session was established with, captured when the session is first seen, so a
 * caching-only configuration does not carry one borrower's setting into the next borrow either.
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
     * The auto-commit state the physical session was established with. It is the state a borrower must find when
     * the pool configures no {@code defaultAutoCommit}, so that one borrower's change does not outlive its borrow.
     * {@code null} when the driver could not answer or when no session has been captured: nothing is restored then,
     * and the cached read falls through instead of serving a stale value.
     */
    private Boolean initialAutoCommit = null;

    /**
     * The transaction isolation level the physical session was established with, or {@code null} when it could not
     * be read. Used the same way as {@link #initialAutoCommit}.
     */
    private Integer initialTransactionIsolation = null;

    /**
     * The read-only state the physical session was established with, or {@code null} when it could not be read.
     * Used the same way as {@link #initialAutoCommit}.
     */
    private Boolean initialReadOnly = null;

    /**
     * The catalog the physical session was established with. A {@code null} value is ambiguous - the driver may
     * have no catalog or may have failed to answer it - so it is only recorded when read successfully; see
     * {@link #initialCatalogKnown}.
     */
    private String initialCatalog = null;

    /**
     * Whether {@link #initialCatalog} holds the physical session's initial catalog. Kept apart from the value
     * because a driver may legitimately establish a session with no catalog.
     */
    private boolean initialCatalogKnown = false;


    /**
     * Restores the connection state the pool was configured to enforce. The pool invokes this on every borrow, before
     * the connection is handed to its next borrower.
     * <p>
     * Each of the four states is restored to the configured default when one is configured. A state the pool does not
     * configure is restored to the state the physical session was established with, captured once when the session is
     * first seen: keeping one borrower's auto-commit, isolation, read-only or catalog setting across a return would
     * hand that state to a borrower that never asked for it. A state whose initial value the driver would not report
     * cannot be restored; its cached value is dropped on every borrow instead, so a read reaches the driver and
     * reflects the session rather than answering from one borrower's cache.
     * <p>
     * A transaction the previous borrower left open is terminated before the restores, whether or not one of them is
     * pending: isolation and read only are transaction scoped on many drivers, an open transaction would absorb the
     * restores, restoring auto-commit would silently commit the previous borrower's work, and a borrower whose
     * configured auto-commit is already off triggers no restore at all - yet must not inherit the previous borrower's
     * uncommitted work. The transaction is rolled back, never committed. The session is only asked to roll back when
     * the pooled connection reports that work was done since its transaction was last ended
     * ({@link PooledConnection#isTransactionDirty()}), so a clean session does not pay a rollback round trip on every
     * borrow. Failing to terminate the transaction, and a confirmed refusal to restore the read-only, auto-commit or
     * catalog state, fail the borrow and retire the session - a connection known to sit in a state other than the one
     * the pool hands out must not reach a borrower. A refusal the driver blames on the session's transaction state is
     * confirmed first, against a session freed of it ({@link #confirmAgainstFreedSession}).
     * <p>
     * A transaction isolation level the driver refuses on a freed session is the one confirmed failure that does not
     * retire the connection: the level is recorded ({@link #refusedIsolationLevel}) and stops counting as a pending
     * restore, so later borrows do not retry it.
     *
     * @param parent the pool the connection belongs to, or {@code null} when the interceptor chain is being torn down
     * @param con    the pooled connection being set up for its next borrower
     */
    @SuppressWarnings("null") // The resetX booleans guard each target for null, but do not read to ECJ as doing so
    @Override
    public void reset(ConnectionPool parent, PooledConnection con) {
        if (parent == null || con == null) {
            // Teardown walk: the chain is being taken out of service, and
            // nothing may be remembered about a session that is gone.
            clearState();
            refusedIsolationLevel = DataSourceFactory.UNKNOWN_TRANSACTIONISOLATION;
            served = null;
            return;
        }
        // The record of the served connection travels with the arming of
        // the chain and the caches, as served's javadoc states. A null
        // record means no session has served a borrow yet on this chain: the
        // connection was just established, or a reconnect has replaced the
        // session and the disconnect walked the chain first, so the state
        // read below is still the one the driver set up.
        final boolean newSession = served == null;
        served = con;
        PoolConfiguration poolProperties = parent.getPoolProperties();
        final int defaultTransactionIsolation = poolProperties.getDefaultTransactionIsolation();
        final Boolean defaultReadOnly = poolProperties.getDefaultReadOnly();
        final Boolean defaultAutoCommit = poolProperties.getDefaultAutoCommit();
        final String defaultCatalog = poolProperties.getDefaultCatalog();
        Connection raw = con.getConnection();
        if (raw != null && newSession) {
            // Capture the session's initial state once, for the fields the
            // pool does not configure, before a borrower can change it.
            initialAutoCommit = defaultAutoCommit == null ? readAutoCommit(raw) : null;
            initialTransactionIsolation = defaultTransactionIsolation == DataSourceFactory.UNKNOWN_TRANSACTIONISOLATION
                    ? readTransactionIsolation(raw) : null;
            initialReadOnly = defaultReadOnly == null ? readReadOnly(raw) : null;
            if (defaultCatalog == null) {
                initialCatalogKnown = captureCatalog(raw);
            } else {
                initialCatalog = null;
                initialCatalogKnown = false;
            }
            // The captured values are also the session's current state, so
            // seed the cache with them: a borrower that makes no change has
            // nothing to restore, and a cached read answers correctly until
            // a restore replaces the value.
            autoCommit = initialAutoCommit;
            transactionIsolation = initialTransactionIsolation;
            readOnly = initialReadOnly;
            catalog = initialCatalogKnown ? initialCatalog : null;
        }
        final Integer targetTransactionIsolation = defaultTransactionIsolation !=
                DataSourceFactory.UNKNOWN_TRANSACTIONISOLATION
                        ? Integer.valueOf(defaultTransactionIsolation) : initialTransactionIsolation;
        final Boolean targetReadOnly = defaultReadOnly != null ? defaultReadOnly : initialReadOnly;
        final Boolean targetAutoCommit = defaultAutoCommit != null ? defaultAutoCommit : initialAutoCommit;
        final String targetCatalog = defaultCatalog != null ? defaultCatalog : initialCatalog;
        final boolean targetCatalogKnown = defaultCatalog != null || initialCatalogKnown;
        if (targetAutoCommit == null) {
            // No configured value and no captured initial value (the driver
            // would not report one): a cached read must not answer from the
            // previous borrower's setting.
            autoCommit = null;
        }
        if (targetTransactionIsolation == null) {
            transactionIsolation = null;
        }
        if (targetReadOnly == null) {
            readOnly = null;
        }
        if (!targetCatalogKnown) {
            catalog = null;
        }
        boolean resetTransactionIsolation = targetTransactionIsolation != null &&
                refusedIsolationLevel != targetTransactionIsolation.intValue() &&
                (transactionIsolation == null ||
                        transactionIsolation.intValue() != targetTransactionIsolation.intValue());
        boolean resetReadOnly = targetReadOnly != null &&
                (readOnly == null || readOnly.booleanValue() != targetReadOnly.booleanValue());
        boolean resetAutoCommit = targetAutoCommit != null &&
                (autoCommit == null || autoCommit.booleanValue() != targetAutoCommit.booleanValue());
        boolean resetCatalog = targetCatalogKnown && (catalog == null || !catalog.equals(targetCatalog));
        // The javadoc's terminate-first step, run on every borrow: with the
        // previous borrower's auto-commit already matching the target (the
        // configured default or the initial value) none of the booleans above
        // is set, yet an open transaction would still be handed on. The
        // transaction may also have survived the return, unless the pool was
        // configured to terminate transactions there. The dirty marker says
        // whether any statement executed since the transaction was last ended:
        // a clean session cannot hold a transaction, so it is spared the
        // rollback round trip; an unclean one is probed with the same check
        // the pool's own termination paths use - auto-commit off means a
        // transaction is open.
        try {
            if (raw != null && con.isTransactionDirty() && !raw.getAutoCommit()) {
                raw.rollback();
            }
            con.clearTransactionDirty();
        } catch (SQLException x) {
            // Whether the previous borrower's work survived is unknown,
            // so the connection must not go to the next borrower.
            // ConnectionPool.setupConnection() catches this, discards the
            // connection and fails the borrow.
            throw new IllegalStateException(
                    MessageFactory.get("connectionState.unableToTerminatePendingTransaction"), x);
        }
        if (resetTransactionIsolation) {
            try {
                if (transactionIsolation == null ||
                        transactionIsolation.intValue() != targetTransactionIsolation.intValue()) {
                    con.getConnection().setTransactionIsolation(targetTransactionIsolation.intValue());
                    transactionIsolation = targetTransactionIsolation;
                }
            } catch (SQLException x) {
                transactionIsolation = null;
                // A driver that refuses a level refuses it for this session,
                // and the level changes rarely, so retrying per borrow would
                // keep the pool paying a doomed round trip and its error log
                // forever - the refusal is recorded (refusedIsolationLevel)
                // and reported here, at refusal time. A refusal reported as a
                // transaction-state error may instead blame state the
                // auto-commit probe above never saw, so it first gets its
                // confirmation against a freed session.
                SQLException confirmed = confirmAgainstFreedSession(con, x,
                        session -> session.setTransactionIsolation(targetTransactionIsolation.intValue()));
                if (confirmed == null) {
                    transactionIsolation = targetTransactionIsolation;
                } else {
                    refusedIsolationLevel = targetTransactionIsolation.intValue();
                    log.error(MessageFactory.get("connectionState.unableToResetTransactionIsolation",
                            targetTransactionIsolation),
                            PoolUtilities.maskCredentials(confirmed));
                }
            }
        }
        if (resetReadOnly) {
            try {
                if (readOnly == null || readOnly.booleanValue() != targetReadOnly.booleanValue()) {
                    con.getConnection().setReadOnly(targetReadOnly.booleanValue());
                    readOnly = targetReadOnly;
                }
            } catch (SQLException x) {
                readOnly = null;
                // A refusal the driver blames on the session's transaction
                // state gets its confirmation against a session freed of it
                // first: this very reset exists to clear exactly that state,
                // and a driver that accepts the restore there was never
                // unable to apply it.
                SQLException confirmed = confirmAgainstFreedSession(con, x,
                        session -> session.setReadOnly(targetReadOnly.booleanValue()));
                if (confirmed == null) {
                    readOnly = targetReadOnly;
                } else {
                    // The connection is known to sit in a state the pool must
                    // not hand out, and the restore that would fix that failed
                    // even on a session freed of its transaction, so it must
                    // not go to the next borrower.
                    // ConnectionPool.setupConnection() catches this, discards
                    // the connection and fails the borrow.
                    throw new IllegalStateException(
                            MessageFactory.get("connectionState.unableToRestoreReadOnly", targetReadOnly),
                            confirmed);
                }
            }
        }
        if (resetAutoCommit) {
            try {
                if (autoCommit == null || autoCommit.booleanValue() != targetAutoCommit.booleanValue()) {
                    con.getConnection().setAutoCommit(targetAutoCommit.booleanValue());
                    autoCommit = targetAutoCommit;
                }
            } catch (SQLException x) {
                autoCommit = null;
                // As for the read-only state above: the driver refused the
                // target auto-commit state even on a session freed of its
                // transaction, so the borrow fails and the connection is
                // discarded.
                SQLException confirmed = confirmAgainstFreedSession(con, x,
                        session -> session.setAutoCommit(targetAutoCommit.booleanValue()));
                if (confirmed == null) {
                    autoCommit = targetAutoCommit;
                } else {
                    throw new IllegalStateException(
                            MessageFactory.get("connectionState.unableToRestoreAutoCommit", targetAutoCommit),
                            confirmed);
                }
            }
        }
        if (resetCatalog) {
            try {
                if (catalog == null || (!catalog.equals(targetCatalog))) {
                    con.getConnection().setCatalog(targetCatalog);
                    catalog = targetCatalog;
                }
            } catch (SQLException x) {
                catalog = null;
                // As for the read-only state above: the driver refused the
                // target catalog even on a session freed of its transaction,
                // so the borrower learns of the failure instead of getting a
                // connection that was never pointed at the right database.
                SQLException confirmed = confirmAgainstFreedSession(con, x,
                        session -> session.setCatalog(targetCatalog));
                if (confirmed == null) {
                    catalog = targetCatalog;
                } else {
                    throw new IllegalStateException(
                            MessageFactory.get("connectionState.unableToRestoreCatalog", targetCatalog),
                            confirmed);
                }
            }
        }

    }


    /**
     * Clears every cached and captured state value. Called when the session
     * goes out of service.
     */
    private void clearState() {
        autoCommit = null;
        transactionIsolation = null;
        readOnly = null;
        catalog = null;
        initialAutoCommit = null;
        initialTransactionIsolation = null;
        initialReadOnly = null;
        initialCatalog = null;
        initialCatalogKnown = false;
    }


    /**
     * Reads the auto-commit state a physical session was established with.
     *
     * @param raw the physical session
     * @return its auto-commit state, or {@code null} if the driver would not
     *         report it
     */
    private Boolean readAutoCommit(Connection raw) {
        try {
            return Boolean.valueOf(raw.getAutoCommit());
        } catch (SQLException x) {
            log.debug(MessageFactory.get("connectionState.unableToReadInitialState", "auto-commit"),
                    PoolUtilities.maskCredentials(x));
            return null;
        }
    }


    /**
     * Reads the transaction isolation level a physical session was established
     * with.
     *
     * @param raw the physical session
     * @return its isolation level, or {@code null} if the driver would not
     *         report it
     */
    private Integer readTransactionIsolation(Connection raw) {
        try {
            return Integer.valueOf(raw.getTransactionIsolation());
        } catch (SQLException x) {
            log.debug(MessageFactory.get("connectionState.unableToReadInitialState", "transaction isolation"),
                    PoolUtilities.maskCredentials(x));
            return null;
        }
    }


    /**
     * Reads the read-only state a physical session was established with.
     *
     * @param raw the physical session
     * @return its read-only state, or {@code null} if the driver would not
     *         report it
     */
    private Boolean readReadOnly(Connection raw) {
        try {
            return Boolean.valueOf(raw.isReadOnly());
        } catch (SQLException x) {
            log.debug(MessageFactory.get("connectionState.unableToReadInitialState", "read-only"),
                    PoolUtilities.maskCredentials(x));
            return null;
        }
    }


    /**
     * Reads the catalog a physical session was established with. The answer is
     * recorded in {@link #initialCatalog} together with
     * {@link #initialCatalogKnown}, so a driver's null catalog is distinguished
     * from a driver that would not report one.
     *
     * @param raw the physical session
     * @return {@code true} if the driver reported its catalog
     */
    private boolean captureCatalog(Connection raw) {
        try {
            initialCatalog = raw.getCatalog();
            return true;
        } catch (SQLException x) {
            log.debug(MessageFactory.get("connectionState.unableToReadInitialState", "catalog"),
                    PoolUtilities.maskCredentials(x));
            initialCatalog = null;
            return false;
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
        clearState();
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

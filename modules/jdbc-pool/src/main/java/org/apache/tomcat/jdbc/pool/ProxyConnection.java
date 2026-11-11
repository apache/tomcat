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
package org.apache.tomcat.jdbc.pool;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.XAConnection;

import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * The bottom-most interceptor, wrapping a {@link PooledConnection}. It intercepts {@link java.sql.Connection#close()},
 * {@link java.sql.Connection#abort(java.util.concurrent.Executor)}, {@link Object#toString()},
 * {@link javax.sql.PooledConnection#getConnection()}, {@link Object#equals(Object)} and {@link Object#hashCode()}.
 * Method names are compared with {@link String#equals(Object)} unless {@link PoolConfiguration#setUseEquals(boolean)}
 * selects reference comparison.
 * <p>
 * The connection handle implements {@link javax.sql.PooledConnection} (and {@link javax.sql.XAConnection} for an XA
 * pool). The pool manages session reuse itself, so the four listener methods are delegated to the driver's XA
 * connection where one exists, and otherwise fail with an {@link SQLException} naming the method rather than accepting
 * a notification the pool could never deliver faithfully.
 */
public class ProxyConnection extends JdbcInterceptor {

    /**
     * The underlying pooled connection. Volatile because writes that end or re-arm a borrow have no synchronization on
     * the reader's side.
     */
    protected volatile PooledConnection connection = null;

    /**
     * The connection handle the current borrow was served under, or {@code null} once the borrow has ended. It is the
     * per-borrow identity that distinguishes a call of the current borrow from a late call of an earlier one.
     */
    protected volatile Object borrowHandle = null;

    /**
     * The parent connection pool. Volatile because the close and abort branches read it without synchronization; reset
     * writes the session before the pool so a reader never sees a null pool with a live session behind it.
     */
    protected volatile ConnectionPool pool = null;

    /**
     * Returns the underlying pooled connection.
     *
     * @return the pooled connection
     */
    public PooledConnection getConnection() {
        return connection;
    }

    /**
     * Sets the underlying pooled connection.
     *
     * @param connection the pooled connection
     */
    public void setConnection(PooledConnection connection) {
        this.connection = connection;
    }

    /**
     * Returns the handle of the borrow this interceptor is armed for.
     *
     * @return the current borrow's handle, or <code>null</code> once that borrow has ended - its own close
     *             or abort, or a teardown reset - until the recording of the next borrow's handle
     */
    public Object getBorrowHandle() {
        return borrowHandle;
    }

    /**
     * Records the handle of the borrow this interceptor serves. Only {@link ConnectionPool#setupConnection(PooledConnection)}
     * calls this, with the handle it is about to hand to the borrower.
     *
     * @param borrowHandle the new borrow's handle
     */
    public void setBorrowHandle(Object borrowHandle) {
        this.borrowHandle = borrowHandle;
    }

    /**
     * Returns the parent connection pool.
     *
     * @return the connection pool
     */
    public ConnectionPool getPool() {
        return pool;
    }

    /**
     * Sets the parent connection pool.
     *
     * @param pool the connection pool
     */
    public void setPool(ConnectionPool pool) {
        this.pool = pool;
    }

    /**
     * Creates a new ProxyConnection wrapping the given pooled connection.
     *
     * @param parent    the parent connection pool
     * @param con       the pooled connection
     * @param useEquals whether to compare method names with {@link String#equals(Object)} rather than by
     *                  reference
     */
    protected ProxyConnection(ConnectionPool parent, PooledConnection con, boolean useEquals) {
        pool = parent;
        connection = con;
        setUseEquals(useEquals);
    }

    @Override
    public void reset(ConnectionPool parent, PooledConnection con) {
        // Re-arm this interceptor for the next borrow on the session side only: the handle that borrow is served
        // under does not exist yet - setupConnection creates it after this walk and records it here when it does.
        // Until it is recorded, no call can pass servesHandle, so a call that predates this re-arming - one left
        // parked in the chain above this interceptor by a borrow that has since ended - meets a handle it does not
        // serve and is answered as the ended borrow it belongs to.
        //
        // The three writes are also ordered, because the guards in invoke() read these fields with no lock
        // spanning the check and the use, and both callers of this method - the teardown reset(null, null) that
        // takes a borrowed session out of service (the abandoned-connection check, a forced pool close, the
        // release on the return path) and the re-arm reset(this, con) of setupConnection - walk the cached chain
        // without any lock the readers wait on:
        //   - clearing the borrow handle first means that from here until setupConnection records the next
        //     borrow's handle, no receiver can pass servesHandle at all, so no late call of an ended borrow can
        //     seal or return the session this walk is arming - on the re-arm in both directions, since a clean
        //     recycle re-arms this interceptor with the very same pooled connection the ended borrow held;
        //   - writing the session before the pool keeps the pair agreed for the close and abort branches, which
        //     read the pool first and the session second: a reader that observes the pool already nulled cannot
        //     observe a still-live session behind that read, and a close that reads a live session cannot have
        //     read a nulled pool.
        // A close that reads these fields before this walk reaches this interceptor completes on the fully live
        // borrow it captured; one that reads them after finds the guard closed.
        this.borrowHandle = null;
        this.connection = con;
        this.pool = parent;
    }

    /**
     * Checks whether this interceptor wraps the given interface. This is the view from the interceptor itself; the
     * contract of the connection handle the pool hands out - which also implements {@link java.sql.Connection} and
     * {@link javax.sql.PooledConnection} (and {@link javax.sql.XAConnection} on XA pools) - is answered by the dispatch
     * in {@link #invoke(Object, Method, Object[])}.
     *
     * @param iface the interface to check
     *
     * @return true if this interceptor wraps the given interface
     */
    public boolean isWrapperFor(Class<?> iface) {
        // Answer about one snapshot of the handle, with no borrow receiver.
        return isWrapperFor(iface, connection, null);
    }

    /*
     * The isWrapperFor answer for one snapshot of the session field. The fence re-checks that the snapshot still
     * describes this borrow before answering, because a discard marking can move the state without touching the
     * field. A closed handle answers false, matching the closed failure unwrap() raises.
     */
    private boolean isWrapperFor(Class<?> iface, PooledConnection pc, Object proxy) {
        if (pc != null && (connection != pc || pc.isDiscarded()
                || (proxy != null && !servesHandle(proxy)))) {
            return false;
        }
        if (proxy != null && iface.isInstance(proxy)) {
            // The Wrapper contract: an object is a wrapper of the interfaces
            // it implements itself. This answer runs below the fence, not
            // above it, so the true comes only from a state the fence
            // vouched for, and it then predicts the proxy the unwrap of the
            // same interface hands back from that same state.
            return true;
        }
        if (pc == null) {
            // The borrow has ended and nothing is held, so no interface is
            // provided - the same false, and matching not-a-wrapper failure
            // of unwrap(), that the pair gives wherever nothing is provided
            // any more.
            return false;
        }
        if (iface == XAConnection.class && pc.getXAConnection() != null) {
            return true;
        }
        Connection physical = pc.getConnection();
        if (physical == null) {
            // Released handle: there is nothing left below to be a wrapper
            return false;
        }
        if (iface.isInstance(physical)) {
            return true;
        }
        if (iface == XAConnection.class) {
            // javax.sql.XAConnection is only ever handed out from the pooled
            // connection's own XA handle - unwrap() says so - so the driver
            // below is not asked about it
            return false;
        }
        // The driver's own connection may itself wrap a further connection -
        // a driver built around another driver's, like the AWS wrapper over
        // the PostgreSQL one - and it, not the class of the object it
        // presents, is what knows which interfaces it can unwrap to.
        try {
            return physical.isWrapperFor(iface);
        } catch (SQLException x) {
            // A driver that cannot answer the question is not a wrapper
            // for the interface
            return false;
        }
    }


    /**
     * Unwraps the connection to the given interface.
     *
     * @param iface the interface to unwrap to
     *
     * @return the unwrapped connection. The pool-internal {@link PooledConnection} type is not exposed through the
     *             public Wrapper contract.
     *
     * @throws SQLException if the connection does not wrap the given interface
     */
    public Object unwrap(Class<?> iface) throws SQLException {
        // Answer about one snapshot of the handle, with no borrow receiver.
        return unwrap(iface, connection, null);
    }

    /*
     * The unwrap answer for one snapshot of the session field. The fence re-checks the snapshot before anything is
     * read off it; a sealed handle gets the closed failure rather than not-a-wrapper, since the borrow is the problem.
     */
    private Object unwrap(Class<?> iface, PooledConnection pc, Object proxy) throws SQLException {
        if (pc != null && (connection != pc || pc.isDiscarded()
                || (proxy != null && !servesHandle(proxy)))) {
            throw new SQLException(MessageFactory.get("proxyConnection.connectionClosed"));
        }
        if (pc == null) {
            throw new SQLException(MessageFactory.get("proxyConnection.notAWrapper", iface.getName()));
        }
        if (proxy != null && iface != XAConnection.class && iface.isInstance(proxy)
                && !iface.isInstance(pc.getConnection())) {
            // The handle is a wrapper of every interface it implements itself. Interfaces the physical connection
            // also provides keep resolving below; XAConnection is excluded because unwrapping to it yields the
            // driver's XA connection. The returned proxy is the caller's own handle, inert once the borrow is over.
            return proxy;
        }
        if (iface == XAConnection.class) {
            javax.sql.XAConnection xa = pc.getXAConnection();
            if (xa == null) {
                throw new SQLException(MessageFactory.get("proxyConnection.notAWrapper", iface.getName()));
            }
            return xa;
        }
        Connection physical = pc.getConnection();
        if (iface.isInstance(physical)) {
            return physical;
        }
        if (physical != null && isWrapperFor(iface, pc, null)) {
            return physical.unwrap(iface);
        }
        throw new SQLException(MessageFactory.get("proxyConnection.notAWrapper", iface.getName()));
    }

    /**
     * Whether the given caller is the handle of the borrow this interceptor is armed for. The read is volatile,
     * mirroring the reads of the session field: the recording by {@link ConnectionPool#setupConnection(PooledConnection)}
     * and the clearing by the close and abort branches of {@link #invoke(Object, Method, Object[])} and by
     * {@link #reset(ConnectionPool, PooledConnection)} have no synchronization on this side.
     *
     * @param proxy the receiver the call arrived on
     *
     * @return true if the call carries the identity of the borrow this interceptor currently serves
     */
    private boolean servesHandle(Object proxy) {
        return proxy == borrowHandle;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (compare(ISCLOSED_VAL, method)) {
            // A call arriving on a handle other than the borrow this interceptor is armed for is answered as
            // closed even while the session behind it is live for the next borrower: this handle's own borrow is
            // over, and answering it with the next borrow's liveness would be answering about a session this
            // handle no longer serves.
            return Boolean.valueOf(isClosed() || !servesHandle(proxy));
        }
        if (compare(ISVALID_VAL, method) && (isClosed() || !servesHandle(proxy))) {
            // isValid() is the probe for liveness, and the JDBC contract
            // answers a closed connection with false rather than with an
            // exception. A session the pool discarded while its borrower
            // still held the handle - an abandon, a forced pool close, an
            // interceptor marking it discarded - does not seal the facade,
            // so without this branch the probe would reach the closed guard
            // below and throw; a handle of a borrow that has ended answers
            // false for the same reason. The guard answers false, as the
            // sealed facade does; a live handle falls through.
            return Boolean.FALSE;
        }
        if (compare(EQUALS_VAL, method)) {
            // The pool always wraps the chain in a DisposableConnectionFacade,
            // which answers equals and hashCode at its own level and never
            // delegates them, so these branches are reached only when this
            // interceptor is invoked directly - by tests or other interceptors
            // navigating the handler chain. Kept so the Object contract holds
            // for those callers: without them the calls would fall through to
            // the generic dispatch at the end of this method and be answered
            // about the driver's physical connection, or refused as closed on
            // a released interceptor, instead of answered. Object.equals must
            // answer false rather than throw for a null or foreign argument,
            // and it must stay answerable on a released handle, so the
            // identity of the handle itself is what compares - mirroring the
            // semantics the facade gives on the handle path.
            Object other = (args == null || args.length == 0) ? null : args[0];
            return Boolean.valueOf(proxy == other);
        }
        if (compare(HASHCODE_VAL, method)) {
            return Integer.valueOf(System.identityHashCode(proxy));
        }
        if (compare(CLOSE_VAL, method)) {
            // The pool and session fields are read once, into locals, for the
            // whole branch, as in the abort branch below: the pool takes
            // chains down under live borrowers - the abandoned-connection
            // check, a forced pool close and the release of a borrowed session
            // each walk the cached chain with reset(null, null), from the
            // housekeeping thread or the finalizer - and neither that walk nor
            // this branch holds a lock the other waits on. A branch that
            // re-read the fields after its guard could pass on the state still
            // in place before the walk reached this interceptor and then
            // dereference a pool the walk had already nulled, throwing a raw
            // NullPointerException out of close() - a call the specification
            // makes idempotent and prescribes no failure for, and one nothing
            // converts on these paths: the facade delegates close outside its
            // catch of the NullPointerException, and never saw this close.
            ConnectionPool poolRef = this.pool;
            PooledConnection poolc = this.connection;
            if (poolc == null || poolRef == null || !servesHandle(proxy)) {
                // No-op for already closed. A call arriving on a handle other
                // than this interceptor's current borrow is the same case seen
                // from the other side of a recycle: the borrow that handle was
                // handed to has ended, so its close - however late it reaches
                // this interceptor, parked in the chain above it - is the
                // specification's no-op, and must not seal the session field
                // or run the return of the session the next borrower is being
                // served. A missing pool or session is the same no-op for a
                // borrow the pool has already taken the chain down under.
                return null;
            }
            // The guard passed against a borrow still fully armed when these
            // reads ran - the close outran the teardown walk, the outcome
            // indistinguishable from having simply closed first. The return
            // runs on the captured objects, never on the fields again; a
            // concurrent pool-side release counts the two paths' one terminal
            // step exactly once through its busy claim and released guard.
            // The per-borrow identity is sealed beside the session field, not
            // left to the next borrow's reset walk: a clean return runs no
            // walk over this cached interceptor at all, so an unsealed handle
            // would go on matching the armed identity - while the session sits
            // idle, and again while the next borrow's walk is between the
            // interceptors that consult it and this one's own reset - long
            // enough for a call parked above the guards to be answered as the
            // current borrow's when its own borrow ended here.
            this.borrowHandle = null;
            this.connection = null;
            poolRef.returnConnection(poolc);
            return null;
        } else if (compare(ABORT_VAL, method)) {
            // java.sql.Connection.abort(Executor): the specification has the
            // call mark the connection closed, close the physical session and
            // release its resources, and makes it a no-op on a closed
            // connection. A handle its borrower has already released is
            // answered by the facade, which disposes of abort as it does of
            // close; the guard here covers the same state reached without a
            // facade. The pool and session fields are read once, into locals:
            // a concurrent disposal of the session - which a destroy or a
            // factory close can trigger at any moment, from the finalizer
            // thread - nulls them, and re-reading them after the guard would
            // either fail the branch with a null pointer or silently skip the
            // return that frees the slot the abort is there to free.
            ConnectionPool poolRef = this.pool;
            PooledConnection poolc = this.connection;
            if (poolc == null || poolRef == null || !servesHandle(proxy)) {
                // Ended or never-armed handle, and a handle other than this
                // interceptor's current borrow: for both, this handle's borrow
                // is over, abort on a closed connection is the specification's
                // no-op, and the session behind the interceptor - the next
                // borrower's, if the chain has been re-armed - is not this
                // call's to disconnect or end.
                return null;
            }
            // The borrow ends here, ahead of the driver call, which the
            // specification allows to run long: from now on every use of this
            // handle fails the way it fails on a closed connection, and a
            // concurrent close of the same handle finds it already ended. The
            // identity is sealed with the session for the same reason as in
            // the close branch: the guards above read it, and the promise
            // this comment makes is only true for all of them once a late
            // call of this handle can no longer pass as the current borrow.
            this.borrowHandle = null;
            this.connection = null;
            try {
                if (!poolc.isDiscarded()) {
                    // A session the pool marked to be taken out of service on
                    // its return makes the handle report itself closed, and
                    // the no-op the specification prescribes for a closed
                    // connection asks nothing of the driver.
                    Connection physical = poolc.getConnection();
                    if (physical != null) {
                        // The driver's own answer - including the failure the
                        // specification prescribes for a null executor, and a
                        // refusal - reaches the caller.
                        method.invoke(physical, args);
                    }
                }
            } catch (Throwable t) {
                if (t instanceof InvocationTargetException) {
                    throw t.getCause() != null ? t.getCause() : t;
                } else {
                    throw t;
                }
            } finally {
                // Whatever the driver made of the call, the session cannot go
                // back to the pool: the application holding this handle now
                // believes the specification, which says the connection is
                // closed after abort, and will not return it - leaving it in
                // busy would strand one maxActive slot per abort (bug 65929) -
                // and where the driver ran the closing work asynchronously the
                // session may die at any moment, so no other borrower may
                // receive it either. Marking it discarded first makes the
                // return take it out of service whatever the driver's
                // isClosed() says, the same terminal step the pool takes on a
                // connection whose state it cannot trust: it frees the slot
                // and wakes the threads waiting for one. For a session already
                // marked discarded it is that step run early - this handle is
                // now disposed, so the close that was to complete the discard
                // will not come.
                poolc.setDiscarded(true);
                poolRef.returnConnection(poolc);
            }
            return null;
        } else if (compare(TOSTRING_VAL, method)) {
            return this.toString();
        } else if (compare(GETCONNECTION_VAL, method)) {
            // javax.sql.PooledConnection#getConnection(). The pooled
            // connection is read once, into a snapshot, the way the dispatch
            // at the bottom of this method reads it, and the physical session
            // is handed out only while that snapshot still shows this
            // handle's borrow. The accessor promises the session behind a
            // live connection: a handle whose session the pool discarded
            // while borrowed must not reveal that very session - the caller
            // would keep working on a connection the pool has committed to
            // retiring - and a session the pool disconnected behind the
            // handle (an abandon, a forced pool close) holds null by then,
            // which as this call's answer would only surface as an unrelated
            // NullPointerException at the caller's first use. A handle whose
            // borrow has ended is owed the same answer: the session this
            // field now names may be the one a later borrower is being
            // served, and the borrow identity is what tells the two apart.
            // As in the dispatch below, a seal or a re-arm landing after the
            // snapshot fails the re-check, and a session found null after it
            // is refused on the same ground - failing closed is the honest
            // answer. A snapshot that already reads closed is not served
            // here; the closed guard below gives it the failure every other
            // use of a closed handle gets.
            PooledConnection poolc = connection;
            if (poolc != null && !poolc.isDiscarded() && servesHandle(proxy)) {
                if (connection != poolc || poolc.isDiscarded() || !servesHandle(proxy)) {
                    throw new SQLException(MessageFactory.get("proxyConnection.connectionClosed"));
                }
                Connection physical = poolc.getConnection();
                if (physical == null) {
                    throw new SQLException(MessageFactory.get("proxyConnection.connectionClosed"));
                }
                return physical;
            }
        } else if (method.getDeclaringClass() != Object.class &&
                method.getDeclaringClass().isAssignableFrom(XAConnection.class) && connection != null) {
            // The Object.class exclusion is defensive: the only Object
            // methods java.lang.reflect.Proxy routes to an invocation
            // handler are equals, hashCode and toString, and all three are
            // answered above. Any other method this condition matches can
            // only be answered by the XA connection wrapper - which exists
            // only when the pool is backed by an XA data source, and only
            // while the physical connection is still connected. The
            // condition also matches methods declared by the interfaces
            // XAConnection extends, javax.sql.PooledConnection among them,
            // so on a plain pool calls such as addConnectionEventListener
            // reach this branch without a target; dispatching them at null
            // would surface a raw NullPointerException to the application.
            //
            // The handle's own state is answered first, the way every other
            // branch of this method answers it, on a single snapshot of the
            // field: a session the pool marked discarded while borrowed
            // (an interceptor's setDiscarded, and the abandon or forced close
            // on their way to the disconnect) makes the handle report itself
            // closed, yet until the return its XA connection is still open -
            // dispatching at it then would quietly accept a registration, or
            // hand out the XAResource, for a session the pool has committed
            // to retire. A handle other than this interceptor's current
            // borrow fails on the same ground from the later side: its borrow
            // ended, and the XA connection this field now reaches belongs to
            // whoever the pool re-armed the session for. The re-check answers
            // a seal or a re-arm landed since the condition, and a second
            // read of the flag, a marking that landed since - exactly as in
            // the dispatch at the bottom of this method.
            PooledConnection poolc = connection;
            if (poolc == null || poolc.isDiscarded() || !servesHandle(proxy)) {
                throw new SQLException(MessageFactory.get("proxyConnection.connectionClosed"));
            }
            if (connection != poolc || poolc.isDiscarded() || !servesHandle(proxy)) {
                throw new SQLException(MessageFactory.get("proxyConnection.connectionClosed"));
            }
            javax.sql.XAConnection xaConnection = poolc.getXAConnection();
            if (xaConnection == null) {
                if (isClosed()) {
                    // The handle was disconnected while borrowed; report it
                    // the way every other method on this class does, rather
                    // than blaming the XA configuration.
                    throw new SQLException(MessageFactory.get("proxyConnection.connectionClosed"));
                }
                throw new SQLException(MessageFactory.get("proxyConnection.notXAConnection", method.getName()));
            }
            try {
                return method.invoke(xaConnection, args);
            } catch (Throwable t) {
                if (t instanceof InvocationTargetException) {
                    throw t.getCause() != null ? t.getCause() : t;
                } else {
                    throw t;
                }
            }
        }
        // The closed guard reads the pooled connection once, into a
        // snapshot, and the Wrapper branches below - the only ones between
        // this guard and the dispatch that use the session - act on that
        // snapshot instead of reading the field a second time: the seal a
        // concurrent close or abort makes, and the reset(null, null) of the
        // pool's teardown walk, write the field from other threads with no
        // lock this guard could wait on (the reason it is volatile,
        // documented on the field), so a guard whose branch re-read the
        // field would answer about two states of the handle and meet what
        // the seal had left where the guard had seen a live borrow. The
        // Wrapper branches answer through the private overloads of unwrap()
        // and isWrapperFor(), which take this snapshot and this call's
        // receiver and re-run this guard's closed test - handle leg
        // included - as a fence of their own before they answer anything,
        // including the interfaces the handle implements itself, instead of
        // reading the field again. The dispatch at the bottom takes its own
        // snapshot further down, with its own re-checks.
        PooledConnection snapshot = connection;
        if (snapshot == null || snapshot.isDiscarded() || !servesHandle(proxy)) {
            // Closed by the handle's own close or abort, by the pool taking
            // the session out of service under the borrower, or because this
            // call arrived on a handle whose borrow has ended - all three are
            // answered the way every use of a closed handle is.
            throw new SQLException(MessageFactory.get("proxyConnection.connectionClosed"));
        }
        boolean unwrapping = compare(UNWRAP_VAL, method);
        if (unwrapping || compare(ISWRAPPERFOR_VAL, method)) {
            // The Wrapper pair is one question - what does this handle
            // provide - asked two ways, so both branches answer from the
            // snapshot the guard just checked and share the re-check that
            // answers a seal, a discard marking or a re-arm landed since.
            // The borrow handle joins the field read because a clean recycle
            // re-arms this interceptor with the very same pooled connection
            // object, and only the handle tells the two borrows apart. The
            // unwrap gets the closed failure: the handle *was* a wrapper when
            // the check passed, and what stops the answer is not the
            // interface asked for but the borrow - a not-a-wrapper message
            // would only invite the caller to try the next interface of a
            // session that is gone. The isWrapperFor carries no failure in a
            // boolean and answers false, the answer that keeps the pair
            // agreed with that refusal.
            Class<?> iface = (Class<?>) args[0];
            if (connection != snapshot || snapshot.isDiscarded() || !servesHandle(proxy)) {
                if (unwrapping) {
                    throw new SQLException(MessageFactory.get("proxyConnection.connectionClosed"));
                }
                return Boolean.FALSE;
            }
            // Both answers descend into the private overloads - the
            // interfaces the handle implements itself among them - and are
            // read off one final fenced read, with this call's receiver in
            // that fence. The self-interface answers used to be given up
            // here, above the fence: a marking or a seal landing with this
            // re-check's own read left the pair disagreeing - isWrapperFor
            // true and the unwrap handing the caller its own handle back on
            // a handle answering isClosed true, next to the closed failure
            // the fenced helpers gave the session-valued interfaces at the
            // same landing. Below the fence the pair agrees on every landing:
            // false and the closed failure exactly where the fence finds the
            // borrow ended, and - past the fence's own read, where a write
            // has outrun the last check the way every other handout point of
            // this class tolerates - the handle's own answer, true and its
            // proxy or its session, together.
            if (unwrapping) {
                return unwrap(iface, snapshot, proxy);
            }
            return Boolean.valueOf(isWrapperFor(iface, snapshot, proxy));
        }
        // The call is dispatched on a snapshot of the pooled connection - the
        // fence the statement facade already runs against this same race (see
        // StatementProxy#invoke): a concurrent close or abort of this handle,
        // or the pool taking the session out of service while it was still
        // borrowed, is a write to this field or to the session's discarded
        // flag from another thread, and a call that read them as live must
        // not find them sealed at the dispatch. The snapshot is refused if it
        // already reads closed, and the re-check immediately before the invoke
        // answers a write that landed since - such a call fails the way every
        // use of a closed handle fails, rather than being executed against a
        // session that is being, or has been, handed to the next borrower.
        // What neither read can see is an ended borrow followed by a clean
        // recycle: the recycle re-arms this interceptor through reset() with
        // the very same pooled connection object the ended borrow held, so a
        // call that entered the chain before the close and descends only after
        // the re-arm would find the next borrower's live state and no trace of
        // the borrow it belongs to - that is what the borrow-handle leg of the
        // re-check fences. The physical connection is read from that same
        // snapshot and checked because a session the pool disconnected while
        // borrowed (an abandon, a forced pool close) owes the caller the
        // closed-connection failure, not the raw NullPointerException a
        // reflective call on a null receiver would surface.
        //
        // The forwarded call is deliberately not run under the pool's
        // per-connection lifecycle lock (PooledConnection#lock), which would
        // make the check and the dispatch indivisible against the return: that
        // lock is a no-op unless the pool sweeper or the useLock property is
        // switched on, so it fences nothing in the configurations where the
        // queue claims do the protecting; and where it is live, the other
        // holders of the very same lock are the pool's maintenance paths - the
        // abandoned-connection check takes it on every busy connection, from
        // the one housekeeping timer thread that serves every pool in the JVM
        // - so a forwarded application call, whose duration nothing bounds,
        // could hold the maintenance of every pool behind it and defeat the
        // very check whose job is to reclaim a connection stuck inside such a
        // call. The window that remains is the one the statement facade
        // documents as its own: a call that took its snapshot before the seal
        // became visible completes against the session it was given, an
        // outcome indistinguishable from having simply outrun the close.
        PooledConnection poolc = connection;
        if (poolc == null || poolc.isDiscarded() || !servesHandle(proxy)) {
            throw new SQLException(MessageFactory.get("proxyConnection.connectionClosed"));
        }
        if (connection != poolc || poolc.isDiscarded() || !servesHandle(proxy)) {
            // The guard above passed while this handle's borrow was still live,
            // and a close, an abort or a discard has landed since, or the chain
            // has been re-armed for a new borrow since: it is too late for the
            // dispatch either way, and fail closed is the honest answer.
            throw new SQLException(MessageFactory.get("proxyConnection.connectionClosed"));
        }
        Connection physical = poolc.getConnection();
        if (physical == null) {
            throw new SQLException(MessageFactory.get("proxyConnection.connectionClosed"));
        }
        try {
            return method.invoke(physical, args);
        } catch (Throwable t) {
            if (t instanceof InvocationTargetException) {
                throw t.getCause() != null ? t.getCause() : t;
            } else {
                throw t;
            }
        }
    }

    /**
     * Returns true if the underlying connection has been closed or discarded.
     *
     * @return true if closed or discarded
     */
    public boolean isClosed() {
        // Read the field once: this is the state the closed guard of invoke()
        // consults with the same single read, and two reads would let a seal
        // from a concurrent close of the same handle slip between the null
        // test and the discarded read, turning the guard itself into the
        // NullPointerException the closed contract promises to never raise.
        PooledConnection poolc = connection;
        return poolc == null || poolc.isDiscarded();
    }

    /**
     * Returns the delegate pooled connection.
     *
     * @return the delegate pooled connection
     */
    public PooledConnection getDelegateConnection() {
        return connection;
    }

    /**
     * Returns the parent connection pool.
     *
     * @return the parent connection pool
     */
    public ConnectionPool getParentPool() {
        return pool;
    }

    @Override
    public String toString() {
        // Read the pooled connection once: the expression used to read the
        // field twice, and the seal of a concurrent close or abort, or the
        // reset(null, null) of a teardown walk, could land between the reads,
        // turning the string of a handle whose borrow ended in that instant
        // into a raw NullPointerException where the Object contract asks for a
        // stable answer.
        PooledConnection poolc = connection;
        return "ProxyConnection[" + (poolc != null ? poolc.toString() : "null") + "]";
    }

}

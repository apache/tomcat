/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.jdbc.pool.interceptor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Wrapper;

import javax.management.ObjectName;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.apache.tomcat.jdbc.pool.jmx.JmxUtil;
import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * Class that resets the abandoned timer on any activity on the Connection, on any query execution and on any read of a
 * {@link java.sql.ResultSet} returned by a statement obtained from the pooled connection, whether the operation
 * completed successfully or failed. Iterating a large result set therefore keeps the connection alive through the fetch
 * phase, not only until the query returns. Each reset happens when an individual call completes, so a single call that
 * itself blocks on the database for longer than
 * {@link org.apache.tomcat.jdbc.pool.PoolConfiguration#setRemoveAbandonedTimeout(int)} still gets the connection
 * abandoned. The statement facade is always used, so a result set handle that outlives its borrow is sealed off before
 * it can go on refreshing the abandoned timer of whoever holds that physical connection next, and because every stamp
 * from a call counts only for the borrow that call was started under, a call that was still in flight when its borrow
 * ended cannot refresh that timer either. The handles this interceptor hands out release what they wrap - the
 * statement layer, the physical result set - once a close has been forwarded to them, and answer any later call from
 * the closed contract, as the statement facade's own handles do; a handle the application keeps beyond its close
 * therefore stops keeping the physical objects it wrapped reachable, though it continues to keep the interceptor
 * beneath it, and through it the pooled connection, reachable - as the facade's own inner-class handles do. This
 * interceptor is useful for when you have a
 * {@link org.apache.tomcat.jdbc.pool.PoolConfiguration#setRemoveAbandonedTimeout(int)} that is fairly low, and you
 * want to reset the abandoned time each time any operation on the connection is performed. This is useful for batch
 * processing programs that use connections for extensive amount of times.
 */
public class ResetAbandonedTimer extends AbstractQueryReport implements ResetAbandonedTimerMBean {

    private static final Log log = LogFactory.getLog(ResetAbandonedTimer.class);

    /**
     * The pooled connection this instance is bound to. It is written by the
     * borrow that binds the chain and by the disposal that releases it, which
     * are the borrower's thread and the pool's own, and it is read by the
     * activity hooks and by whichever thread invokes the JMX operation, so, as
     * in the proxies below, volatile in order to reach those readers at all.
     */
    private volatile PooledConnection pcon;

    /**
     * Whether the pooled connection this instance is bound to is currently
     * checked out. It is written by the paths that settle that binding - the
     * borrow that arms the chain, the close that ends the borrow and the
     * disposal of the chain - which are the borrower's thread and the pool's
     * own, and it is read by the activity hooks and by whichever thread invokes
     * the JMX operation, so, as in the proxies below, volatile in order to reach
     * those readers at all.
     */
    private volatile boolean checkedOut;

    /**
     * Identifies the borrow that armed this instance. It is bumped by the
     * walk that arms the chain, so a value taken from this field names one
     * particular borrow for as long as that borrow, and no other, is the
     * armed one. It exists because the flag beside it identifies only the
     * pooled connection, and a connection is borrowed again after it is
     * handed back: the call of a past borrow that was still in flight when
     * that borrow ended finds the flag up again, raised for the newer
     * borrow it has nothing to do with. A frame that stamps from a call
     * therefore carries the value captured when the call entered the chain,
     * and stamps only while it is still the current one; the value is
     * bumped, never reused, so a past borrow's value can never answer for a
     * later one. As in the fields beside it, volatile in order to reach the
     * stamping threads at all.
     */
    private volatile long borrowEpoch;

    /**
     * The name this instance is registered under as an MBean, or {@code null}
     * while it is not registered. The borrow that registers it and the disposal
     * that unregisters it are different threads, and a disposal reading a
     * stale null would skip the unregistration and leave the MBean published,
     * pinning this instance and everything it reaches, so volatile like the
     * fields beside it.
     */
    private volatile ObjectName oname = null;

    /**
     * Default constructor.
     */
    public ResetAbandonedTimer() {
    }

    @Override
    public void reset(ConnectionPool parent, PooledConnection con) {
        super.reset(parent, con);
        if (con == null) {
            // The chain is going out of service. The monitor is the one the
            // borrow takes below, so the unbinding and the unregistration
            // cannot interleave with a rebind. Within it the flag comes down
            // before the binding does, so that a reader which catches the
            // binding while it is still live - the JMX caller reads these
            // fields without the monitor - reads this borrow as over rather
            // than as running, and stamps nothing.
            synchronized (this) {
                this.checkedOut = false;
                this.pcon = null;
                if (oname != null) {
                    JmxUtil.unregisterJmx(oname);
                    oname = null;
                }
            }
        } else {
            // This walk is the borrow, under the same monitor the disposal
            // takes above: the connection it binds is the one being handed
            // out, so from here on the timer this class keeps is worth
            // resetting again, and the name registered here can never be
            // missed by an unbinding running beside this walk.
            synchronized (this) {
                // The new borrow's identity is taken before the binding is
                // published: a call that enters the chain from here on
                // captures this value and stands for this borrow, and every
                // frame that captured a value before it now names a borrow
                // that is over, whatever else it finds settled.
                this.borrowEpoch++;
                this.pcon = con;
                this.checkedOut = true;
                if (oname == null) {
                    // The publish is best effort: what this interceptor does on
                    // the borrow path does not depend on its management
                    // surface, and a connection the application reaches without
                    // an MBean works exactly like one it reaches with. The name
                    // is keyed by the class rather than the instance, so a
                    // chain that configures this interceptor twice asks twice
                    // for one name and the second instance is refused - a
                    // configuration mistake an operator wants told about, and
                    // told about on every attempt, which is why the report of
                    // the refusal is left where JmxUtil puts it: at error
                    // level, with the exception behind it. A refused name is
                    // not claimed here, so it stays the property of whoever
                    // holds it and the next borrow tries again.
                    String keyprop = ",JdbcInterceptor=" + getClass().getSimpleName();
                    oname = JmxUtil.registerJmx(pcon.getObjectName(), keyprop, this);
                }
            }
        }
    }

    /**
     * Resets the abandoned timer of the pooled connection this interceptor is bound to, and only while that connection
     * is checked out. This is the implementation of the MBean operation; the activity hooks below stamp through
     * {@link #stampFor(long)}, which adds a borrow gate on top of this one. The flag alone is what keeps the management
     * operation off a connection that is idle in the pool, where a stamp had nothing to avert - the abandoned check
     * looks only at connections that are held - and did undo the age
     * {@link org.apache.tomcat.jdbc.pool.ConnectionPool#checkIdle()} measures it by. That flag is also what keeps the
     * stamp from the close's own finally: by the time that finally runs, the borrow it would answer for is over. What
     * the flag cannot tell apart is the late finally of a call that was in flight when its borrow ended and whose
     * connection has since been borrowed again - the flag is up again by then, for a borrow the late call is not part
     * of - which is why the hooks are gated on the borrow, not on this flag alone.
     *
     * @return {@code true} if the timestamp of a checked-out connection was reset, {@code false} if this interceptor is
     *             bound to no connection or to one that is not checked out
     */
    @Override
    public boolean resetTimer() {
        // Each field is read once: both are settled by other threads, and a gate
        // that read one of them twice could act on a state that no longer holds.
        // The window this leaves is the race itself - a stamp taken on a
        // connection that was being discarded as the read happened, at most one
        // - which is the posture the proxies below take toward a concurrent
        // close, and borne for the same reason: no decision is taken on a
        // timestamp of a connection that is on its way out.
        final PooledConnection con = pcon;
        final boolean held = checkedOut;
        if (con == null || !held) {
            return false;
        }
        con.setTimestamp(System.currentTimeMillis());
        return true;
    }

    /**
     * Takes the stamp of an activity hook on behalf of the frame that entered the chain under the given borrow.
     * <p>
     * A call that entered the chain during one borrow can finish after that borrow has ended and after the same pooled
     * connection has been handed to the next borrower - the application closed the handle from another thread while
     * the call was still with the driver, which is the very situation this interceptor is configured against. The flag
     * {@link #resetTimer()} reads is up again by then, raised for that newer borrow, so a hook stamping through it
     * directly would attribute this call's activity to whoever holds the connection now: it would push that borrower's
     * abandonment deadline back by a full timeout, and {@link PooledConnection#setTimestamp(long)} would clear the
     * suspect state recorded about that borrow. The borrow the frame entered under tells the two apart: an armed chain
     * past this frame's borrow has bumped the epoch this frame captured, and its stamp is refused.
     * <p>
     * What the gate costs a legitimate call whose close races it is its own stamp, which has nothing left to avert:
     * the borrow it would protect is ending as the stamp is decided.
     *
     * @param epoch the borrow this frame captured when it entered the chain
     */
    private void stampFor(long epoch) {
        // The epoch is read once, as in resetTimer(): it is settled by the
        // thread that arms the chain, and a second read could match an arm
        // this frame has nothing to do with.
        if (epoch != borrowEpoch) {
            return;
        }
        resetTimer();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        final long epoch = borrowEpoch;
        try {
            return super.invoke(proxy, method, args);
        } finally {
            // The operation was attempted on the connection, which counts
            // as activity even when it failed - for the borrow the call
            // entered the chain under, and for that one only: if that
            // borrow is over, whoever holds the connection now is not to
            // be kept alive by this call's completing
            stampFor(epoch);
        }
    }

    /**
     * Wraps the query reporting statement proxy so that the result sets handed to the application reset the abandoned
     * timer while they are read. Statements that this interceptor does not proxy are passed through unchanged.
     */
    @Override
    public Object createStatement(Object proxy, Method method, Object[] args, Object statement, long time) {
        Object result = super.createStatement(proxy, method, args, statement, time);
        if (result == statement) {
            // Not one of the statement types we proxy, or the proxy
            // creation above failed - nothing to wrap
            return result;
        }
        try {
            String name = method.getName();
            Class<?> type = Statement.class;
            int index = CREATE_STATEMENT_IDX;
            if (compare(PREPARE_STATEMENT, name)) {
                type = PreparedStatement.class;
                index = PREPARE_STATEMENT_IDX;
            } else if (compare(PREPARE_CALL, name)) {
                type = CallableStatement.class;
                index = PREPARE_CALL_IDX;
            }
            return getConstructor(index, type).newInstance(new Object[] { new TimerStatementProxy(result) });
        } catch (Exception x) {
            // The application keeps the query-report handle beneath: its
            // result sets come un-wrapped, and since the execution stamp is
            // taken on this handle, its queries keep no timer either. The
            // degraded keep-alive this leaves is the price of the one stamp
            // that knows its own borrow.
            log.warn(MessageFactory.get("resetAbandonedTimer.unableToCreateTimerStatementProxy",
                    getClass().getName()), x);
        }
        return result;
    }

    @Override
    protected void prepareCall(String query, long time) {
        // Nothing is stamped here. A prepare is a call on the connection, and
        // the invoke() above stamps it once the call completes, under the
        // borrow that call entered the chain under; a hook cannot see that
        // borrow, so a stamp taken here would be taken without the gate, in
        // the middle of a call that may outlive its borrow.
    }

    @Override
    protected void prepareStatement(String sql, long time) {
        // As above
    }

    /**
     * Takes the borrow down. The base class fires this hook on the connection-level close, on the way down to the pool
     * and once per borrow, so it is where this instance stops belonging to a checked-out connection - and it comes down
     * here, on the way to the pool, rather than at the unbind below, because that unbind does not happen on a return at
     * all: the chain stays bound to its pooled connection for the whole idle period, so a flag left up until then would
     * leave every hook of this interceptor, and the MBean operation, acting on a connection the application let go of.
     * <p>
     * What this hook still does not do is stamp. It runs at the end of a borrow rather than during it, which is
     * precisely where a stamp has nothing left to avert. An abandonment or suspect decision is a decision about a borrow
     * that is still held, and the sweep that makes it has looked at this connection before the close started. What a
     * stamp here does change is the state the return is reported from, and that read happens below this hook, in the
     * pool: {@link PooledConnection#setTimestamp(long)} clears the suspect flag and moves the timestamp the elapsed time
     * of that report is computed from, so resetting here hands {@code ConnectionPool.returnConnection} a connection that
     * reads as never suspected, and neither half of that report - neither the log line nor the notification - ever
     * speaks for the connections it exists for. The stamp a returned connection does need, the timestamp it goes idle
     * under, is the return path's own to write and it writes it, below this hook. The close is therefore the one call on
     * a borrowed connection this interceptor leaves unstamped, and the flag lowered here is what keeps it that way:
     * {@link #invoke(Object, Method, Object[])} stamps once the call it wraps has run, so without the flag its finally
     * would take that stamp after the connection is back in the pool and its state has been settled - which is the same
     * interference one layer down. The override stays because the hook is abstract in the base class.
     */
    @Override
    public void closeInvoked() {
        // The borrow is ending: see above
        checkedOut = false;
    }

    /**
     * Proxy for a statement that turns the result sets the statement hands out into {@link TimerResultSetProxy}
     * instances, whatever the method that returned one was called. Every other call is forwarded to the wrapped
     * statement, apart from the identity of this handle and from every call after a close has been forwarded, which
     * this proxy and the closed contract answer; what the {@link java.sql.Wrapper} methods hand back is forwarded
     * unwrapped - a driver object reached through {@code unwrap()} stays the driver's own object, even one that
     * implements {@link java.sql.ResultSet}.
     */
    private final class TimerStatementProxy implements InvocationHandler {
        /**
         * The statement proxy created by {@code AbstractQueryReport}, nulled
         * once a close has been forwarded so that a handle the application
         * keeps beyond its close stops pinning the statement layer beneath it.
         * A close can come from a thread other than the one invoking on the
         * handle - the pool's statement finalizer closes the statements it
         * tracks from whichever thread closes the connection - so the field is
         * read by more threads than the one that nulls it and, as in the
         * facade's and the query report proxy, must be volatile to reach them
         * at all.
         */
        private volatile Object delegate;

        TimerStatementProxy(Object delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // Handle identity on this proxy, like the statement facade
            // does, rather than on the object below it: the application
            // must be able to rely on its own handles. These answers come
            // from this object alone, so they are not an operation on the
            // connection and the timer stays as it is - as for the result
            // set proxy's own identity calls below.
            if (compare(TOSTRING_VAL, method)) {
                return toString();
            }
            if (compare(EQUALS_VAL, method)) {
                if (args[0] == null || !Proxy.isProxyClass(args[0].getClass())) {
                    return Boolean.FALSE;
                }
                return Boolean.valueOf(this.equals(Proxy.getInvocationHandler(args[0])));
            }
            if (compare(HASHCODE_VAL, method)) {
                return Integer.valueOf(this.hashCode());
            }
            final long epoch = borrowEpoch;
            try {
                // Read the delegate once for this call: the close that nulls it
                // can come from another thread, and the guards and the forward
                // must agree on the one value this call read. A call that finds
                // the delegate gone is answered from the closed contract below -
                // the same answers the query report proxy beneath gives for its
                // own closed state - rather than by invoking on a null receiver:
                // that is a NullPointerException, where the closed contract owes
                // the application a SQLException. The window this cannot close is
                // the race itself: a call that read the delegate while it was
                // still live goes to it, an outcome indistinguishable from having
                // simply outrun the close. Under the always-installed facade an
                // application never reaches these answers - the facade's own
                // handle seals a retained handle first; they stand for a caller
                // that holds this handle directly.
                final Object target = delegate;
                final boolean close = compare(CLOSE_VAL, method);
                // Allow close to be called multiple times, from either thread
                if (close && target == null) {
                    return null;
                }
                if (compare(ISCLOSED_VAL, method) && target == null) {
                    return Boolean.TRUE;
                }
                if (target == null) {
                    throw new SQLException(MessageFactory.get("resetAbandonedTimer.statementClosed"));
                }
                Object result;
                try {
                    // Execute on the delegate this call read as live
                    result = method.invoke(target, args);
                } catch (InvocationTargetException x) {
                    Throwable cause = x.getCause();
                    if (cause == null) {
                        throw x;
                    }
                    throw cause;
                }
                // Perform close cleanup: the handle kept beyond this close now
                // holds nothing that can reach the statement beneath it
                if (close) {
                    delegate = null;
                }
                // Wrap every result set the call hands back, whatever the method
                // that returned it was called, on the same rule the statement
                // facade uses: a decision made on the method name only sees the
                // three calls the standard API names, and misses the result sets
                // declared to come back as an Object - a cursor OUT-parameter
                // reaches the application through CallableStatement.getObject(...),
                // and iterating it keeps a cursor open on this connection just as
                // an executeQuery one does. The Wrapper methods are kept out of
                // this for the facade's reason: unwrap() is the documented way to
                // reach the driver's own object, and a driver's cursor type is
                // such an object, so what it hands back stays the driver's object
                // even where that object implements ResultSet.
                if (result instanceof ResultSet && method.getDeclaringClass() != Wrapper.class) {
                    try {
                        return getConstructor(RESULTSET_IDX, ResultSet.class)
                                .newInstance(new Object[] { new TimerResultSetProxy(result, proxy) });
                    } catch (Exception x) {
                        // The application just does not get the timer reset
                        // while reading this result set
                        log.warn(MessageFactory.get("resetAbandonedTimer.unableToCreateTimerResultSetProxy",
                                ResetAbandonedTimer.this.getClass().getName()), x);
                    }
                }
                return result;
            } finally {
                // The operation was attempted on the statement, which counts
                // as activity on the connection even when it failed - the
                // execution of a query, which this class exists to keep
                // alive, passes through this handle like every other call on
                // it. The stamp is the borrow's, as in the connection hook.
                stampFor(epoch);
            }
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public String toString() {
            return ResetAbandonedTimer.this.getClass().getName() + "[Proxy=" + hashCode() + "; Delegate=" + delegate +
                    ']';
        }
    }

    /**
     * Proxy for a result set that resets the abandoned timer on every read, so that an application iterating a result
     * set - or doing its per-row work between the reads - counts as active and is not abandoned mid-iteration. Every
     * result set a read hands out is turned into a handle of this proxy too, on the statement proxy's return-type rule,
     * so that iterating a nested cursor reaches this timer as well; every other call is forwarded to the wrapped result
     * set, apart from the identity of this handle, from the {@link java.sql.ResultSet#getStatement()} answer this proxy
     * keeps, and from every call after a close has been forwarded, which this proxy and the closed contract answer;
     * what the {@link java.sql.Wrapper} methods hand back is forwarded unwrapped - a driver object reached through
     * {@code unwrap()} stays the driver's own object, even one that implements {@link java.sql.ResultSet}.
     */
    private final class TimerResultSetProxy implements InvocationHandler {
        /**
         * The result set being read, nulled once a close has been forwarded so
         * that a handle the application keeps beyond its close stops pinning
         * the physical result set. Read by other threads than the one that
         * nulls it - a close can come from a foreign thread, as in the
         * statement proxy - so, as in the facade and the query report proxy,
         * volatile.
         */
        private volatile Object delegate;
        /**
         * The statement handle in use by this interceptor, returned by {@link java.sql.ResultSet#getStatement()}. It
         * keeps the timer reset like the handle the application holds, but it is not necessarily that same outermost
         * handle: the statement facade substitutes its own handle from outside this interceptor, and any interceptor
         * configured around this one may too. Nulled along with {@link #delegate} once a close has been forwarded, so
         * that the handle of a closed result set stops pinning the statement layer either; read in the same snapshot as
         * the delegate, so it is volatile for the same reason.
         */
        private volatile Object statement;

        TimerResultSetProxy(Object delegate, Object statement) {
            this.delegate = delegate;
            this.statement = statement;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // Handle identity on this proxy, like the statement facade
            // does, rather than on the object below it: the application
            // must be able to rely on its own handles
            if (compare(TOSTRING_VAL, method)) {
                return toString();
            }
            if (compare(EQUALS_VAL, method)) {
                if (args[0] == null || !Proxy.isProxyClass(args[0].getClass())) {
                    return Boolean.FALSE;
                }
                return Boolean.valueOf(this.equals(Proxy.getInvocationHandler(args[0])));
            }
            if (compare(HASHCODE_VAL, method)) {
                return Integer.valueOf(this.hashCode());
            }
            final long epoch = borrowEpoch;
            try {
                // Read both fields once for this call: the close that nulls
                // them can come from another thread, and the guards and the
                // forward must agree on the one value this call read. The
                // close nulls the delegate before the statement, so reading
                // the statement first keeps the snapshot coherent: a call that
                // read the delegate as live cannot have missed the statement.
                final Object stmt = statement;
                final Object target = delegate;
                final boolean close = compare(CLOSE_VAL, method);
                // Allow close to be called multiple times, from either thread
                if (close && target == null) {
                    return null;
                }
                if (compare(ISCLOSED_VAL, method) && target == null) {
                    return Boolean.TRUE;
                }
                if (target == null) {
                    // Answered from the closed contract, as the facade's own
                    // result set handle does, rather than by invoking on a
                    // null receiver: that is a NullPointerException, where the
                    // closed contract owes the application a SQLException
                    throw new SQLException(MessageFactory.get("resetAbandonedTimer.resultSetClosed"));
                }
                if (compare(GET_STATEMENT, method)) {
                    return stmt;
                }
                Object result;
                try {
                    // Invoke on the delegate this call read as live
                    result = method.invoke(target, args);
                } catch (InvocationTargetException x) {
                    Throwable cause = x.getCause();
                    if (cause == null) {
                        throw x;
                    }
                    throw cause;
                }
                // Perform close cleanup: the handle kept beyond this close now
                // holds nothing that can reach the cursor or the statement
                if (close) {
                    delegate = null;
                    statement = null;
                }
                // The statement proxy's rule, mirrored one layer down: a
                // cursor-valued column - a nested cursor, or a REF cursor
                // selected as a column - reaches the application through a
                // getObject(...) overload that comes back as an Object, and
                // the facade above this handle wraps whatever a read hands
                // back, so a cursor let through raw here reaches the
                // application with the facade's handle directly over the
                // driver's cursor: iterating it would keep the session busy
                // while this interceptor's timer watched what it is told is an
                // idle connection. The new handle is parented to the same
                // statement as this one reports, so getStatement() keeps
                // answering a Statement, and the Wrapper methods are kept out
                // of this for the statement proxy's reason: unwrap() is the
                // documented way to reach the driver's own object, and a
                // driver's cursor type is such an object.
                if (result instanceof ResultSet && method.getDeclaringClass() != Wrapper.class) {
                    try {
                        return getConstructor(RESULTSET_IDX, ResultSet.class)
                                .newInstance(new Object[] { new TimerResultSetProxy(result, stmt) });
                    } catch (Exception x) {
                        // The application just does not get the timer reset
                        // while reading this nested cursor
                        log.warn(MessageFactory.get("resetAbandonedTimer.unableToCreateTimerResultSetProxy",
                                ResetAbandonedTimer.this.getClass().getName()), x);
                    }
                }
                return result;
            } finally {
                // Reading the result set is activity on the connection,
                // even when the read failed. The guards above sit inside
                // this block, so a read refused on a handle whose delegate
                // has been released counts as activity too, like every
                // other attempted operation on this connection - for the
                // borrow this call entered the handle under, and for that
                // one only.
                stampFor(epoch);
            }
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public String toString() {
            return ResetAbandonedTimer.this.getClass().getName() + "[Proxy=" + hashCode() + "; Delegate=" + delegate +
                    ']';
        }
    }
}

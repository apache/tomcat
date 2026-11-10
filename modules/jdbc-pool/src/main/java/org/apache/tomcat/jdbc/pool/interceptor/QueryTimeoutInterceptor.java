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

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.PoolProperties.InterceptorProperty;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.apache.tomcat.jdbc.pool.util.ExceptionUtils;
import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * Interceptor that sets a query timeout on every created {@link Statement}.
 * <p>
 * The timeout is applied by the {@code createStatement} hook, which only runs for statement-creation calls that pass
 * through this interceptor. When combined with {@link StatementCache}, this interceptor must therefore be listed
 * <em>before</em> {@code StatementCache} in the
 * {@link org.apache.tomcat.jdbc.pool.PoolConfiguration#setJdbcInterceptors(String) jdbcInterceptors} configuration. A
 * statement cache hit is served by the cache without invoking the rest of the chain, and a statement recycled into the
 * cache has its query timeout reset to zero, so an interceptor listed after the cache never sees the reuse and the
 * cached statement executes with no timeout. Listed before the cache, the hook runs for every creation request,
 * including the ones the cache serves, and the timeout is re-applied to the reused statement.
 * <p>
 * The same applies to statements whose connection never enters the chain: one reached through
 * {@link javax.sql.PooledConnection#getConnection()} or {@code unwrap}, and, on a pool backed by an
 * {@link javax.sql.XADataSource}, the only connection an application uses - such an application gets its connection
 * from {@link javax.sql.XAConnection#getConnection()}, which the pool answers with the session behind the chain.
 * Those statements keep whatever timeout the driver gives them; for the XA case the pool warns at startup, see
 * {@link #poolStarted(ConnectionPool)}.
 * <p>
 * Whether a configured timeout actually interrupts a query is up to the driver. A driver that refuses
 * {@link Statement#setQueryTimeout(int)} does not stop the statement from being handed out - the pool cannot enforce a
 * timeout itself, so such a statement runs unbounded - and the refusal is reported once per physical connection rather
 * than once per statement, so that the log says what is not in effect without repeating itself for every statement
 * that connection creates.
 */
public class QueryTimeoutInterceptor extends AbstractCreateStatementInterceptor {
    private static final Log log = LogFactory.getLog(QueryTimeoutInterceptor.class);

    /**
     * Query timeout value in seconds.
     */
    int timeout = 1;

    /**
     * Whether the driver of this connection has already been reported as refusing
     * {@link Statement#setQueryTimeout(int)}. The pool builds one interceptor chain per pooled connection and reuses
     * that chain for every borrow of it, so this record makes a refusal an event of the physical connection rather than
     * of the statement creation that happened to notice it: a driver that refuses the call refuses it for every
     * statement it is asked about, and one line per creation would bury the fact that the configured protection is not
     * in effect. It is cleared when the physical session is replaced, so the session that takes over reports itself.
     * <p>
     * It is {@code volatile} because that clear is not always on a borrower thread: the pool drives
     * {@code disconnected()} from its own threads when it takes a connection out of service under a borrower - the
     * abandoned-connection cleaner or a forced close - and either can overlap a borrower still creating statements.
     * The hook itself runs on one borrower at a time, so its check-and-set is not contended, and the only cost of
     * the clear racing the set is one duplicate or one missing warning line.
     */
    private volatile boolean timeoutRefusalReported;

    /**
     * Whether an unexpected failure of {@link Statement#setQueryTimeout(int)} -
     * an exception that is not a refusal to apply the timeout - has already
     * been reported on this connection. Same per-physical-connection record,
     * written and read under the same reasoning and with the same visibility
     * as {@link #timeoutRefusalReported}, and deliberately kept a separate
     * record: the two reports say different things about the driver, and
     * neither may spend the other's single line.
     */
    private volatile boolean timeoutFailureReported;

    /**
     * Default constructor.
     */
    public QueryTimeoutInterceptor() {
        super();
    }

    /**
     * Initializes the {@code queryTimeout} property from the provided configuration map. A name that is not one of
     * the {@link #isKnownProperty(String) names this interceptor reads} is not applied and is warned about: names
     * are matched exactly, so a mis-cased {@code queryTimeout} is not a spelling of the property but a different,
     * unread name - and the timeout it leaves unconfigured keeps the aggressive default, which is the wrong
     * direction for a typo to fail in. A value that is not an integer is likewise not applied and is warned about:
     * the timeout keeps the value it has - for a fresh instance the one-second default - so a misconfigured pool
     * behaves as the default and the warning says so as the bad value is configured. The pool configures an
     * interceptor instance once at pool start, once per physical connection chain, and once when the pool stops, so
     * these warnings are no more frequent than the per-connection driver refusal report below; that report is the
     * one that can re-fire, when the pool replaces the physical session behind a connection.
     *
     * @param properties map of interceptor properties
     */
    @Override
    public void setProperties(Map<String, InterceptorProperty> properties) {
        super.setProperties(properties);
        for (String name : properties.keySet()) {
            if (!isKnownProperty(name)) {
                log.warn(MessageFactory.get("queryTimeoutInterceptor.unknownProperty", name));
            }
        }
        InterceptorProperty p = properties.get("queryTimeout");
        if (p == null || p.getValue() == null) {
            // No property, or one with no value to read: as ever, the timeout
            // stays at the default it carries, without a word about it.
            return;
        }
        try {
            timeout = Integer.parseInt(p.getValue());
        } catch (NumberFormatException x) {
            log.warn(MessageFactory.get("queryTimeoutInterceptor.invalidQueryTimeout",
                    p.getValue(), Integer.valueOf(timeout)));
        }
    }

    /**
     * Ends the silence about the one configuration in which this interceptor is
     * inert for an application's normal use: a pool backed by a
     * {@link javax.sql.XADataSource}, where the application's only
     * {@link java.sql.Connection} comes from
     * {@link javax.sql.XAConnection#getConnection()} and so stays outside this
     * chain (see the class javadoc and the {@code getConnection()} branch of
     * {@link org.apache.tomcat.jdbc.pool.ProxyConnection}). The interceptor
     * cannot reach the statements created there - that would mean re-wrapping the
     * session the pool hands out, which is a pool-level change, not an
     * interceptor's - and refusing to start the pool would deny an application a
     * timeout its driver may still apply at its own creation call, so what is
     * done instead is one warning at pool start, while the configuration is
     * still the operator's to change. Statements taken through the pool's own
     * chained handle keep the configured timeout even on an XA-backed pool, so
     * the warning names the one route the escape runs by, not the pool as a
     * whole.
     *
     * @param pool the pool that is starting
     */
    @Override
    public void poolStarted(ConnectionPool pool) {
        super.poolStarted(pool);
        // The configured timeout is already applied: the pool configures the
        // properties of a definition instance before it lets that instance know
        // about the pool start. A timeout of zero or less switches the feature
        // off, and there is then no configured protection whose absence the
        // warning could report.
        if (pool != null && timeout > 0
                && pool.getPoolProperties().getDataSource() instanceof javax.sql.XADataSource) {
            log.warn(MessageFactory.get("queryTimeoutInterceptor.xaBypass", Integer.valueOf(timeout)));
        }
    }

    /**
     * Whether this interceptor configuration understands the named property. {@code queryTimeout} is this class's
     * own; {@code useEquals} is read by the {@link org.apache.tomcat.jdbc.pool.JdbcInterceptor} base. A subclass that
     * adds properties should recognize them here too, so the unrecognized-name report stays a report about a typo
     * rather than becoming a report about the subclass's own design.
     *
     * @param name the property name as configured
     *
     * @return {@code true} if the name is understood and its value acted upon
     */
    protected boolean isKnownProperty(String name) {
        return "queryTimeout".equals(name) || "useEquals".equals(name);
    }

    /**
     * Sets the query timeout on the created statement if it is a {@link Statement} and the timeout value is positive.
     *
     * @param proxy     the actual proxy object
     * @param method    the method that was called
     * @param args      the arguments to the method
     * @param statement the statement that the underlying connection created
     * @param time      elapsed time in milliseconds
     *
     * @return the (possibly wrapped) statement object
     */
    @Override
    public Object createStatement(Object proxy, Method method, Object[] args, Object statement, long time) {
        if (statement instanceof Statement && timeout > 0) {
            Statement s = (Statement) statement;
            try {
                s.setQueryTimeout(timeout);
            } catch (SQLException | UnsupportedOperationException x) {
                // These are the two shapes of a refusal. The spec has the
                // driver refuse a feature by throwing SQL, and a wrapper
                // driver that has not implemented the call refuses it by
                // throwing its Unsupported-OperationException where the
                // wrapped driver would have thrown the SQL: both answer the
                // call with "this driver will not set your timeout", which
                // is the news the refusal report tells.
                reportTimeoutRefusal(x);
            } catch (RuntimeException x) {
                // Any other unchecked exception is not an answer to the
                // call, it is the driver failing to answer it - a driver bug
                // rather than a refusal. The statement is still handed out
                // (the application must not lose it to the pool's inability
                // to set an optional protection) and the failure is still
                // reported, but not as a refusal, and not on the refusal's
                // single-per-connection line: one odd failure must not spend
                // the report a genuine refusal later on the same connection
                // still deserves.
                reportUnexpectedTimeoutFailure(x);
            } catch (Error x) {
                // The module treats a non-fatal Error as an ordinary failed
                // wrapper call; a virtual-machine error is re-thrown first.
                // The optional timeout is then reported as an unexpected
                // failure and the statement is still handed out, as above.
                ExceptionUtils.handleThrowable(x);
                reportUnexpectedTimeoutFailure(x);
            }
        }
        return statement;
    }

    /*
     * Reports that the driver will not apply the configured timeout, once per
     * physical connection. The statement is still handed out, unchanged: only
     * the driver can enforce a timeout, so refusing to create a statement the
     * application can use would trade it for a protection the pool cannot
     * deliver. What the pool can do is say so, and say it once: the warning
     * names the timeout that is not in effect and says this connection will not
     * mention it again, so one line carries both the state and the volume of
     * what is missing from the log.
     */
    private void reportTimeoutRefusal(Throwable x) {
        if (timeoutRefusalReported) {
            return;
        }
        timeoutRefusalReported = true;
        log.warn(MessageFactory.get("queryTimeoutInterceptor.queryTimeoutRefused", Integer.valueOf(timeout),
                reasonOf(x)), x);
    }

    /*
     * Reports an unexpected failure once per physical connection and on its own
     * line, so the failure is heard without being called a refusal and without
     * taking the refusal's report out of this connection's hands. The statement
     * is still handed out, for the same reason a refusal hands it out.
     */
    private void reportUnexpectedTimeoutFailure(Throwable x) {
        if (timeoutFailureReported) {
            return;
        }
        timeoutFailureReported = true;
        log.warn(MessageFactory.get("queryTimeoutInterceptor.queryTimeoutSetupFailed", Integer.valueOf(timeout),
                reasonOf(x)), x);
    }

    /*
     * The reason a report quotes for an exception. Each report is the one line
     * of its kind this connection will ever get, so the reason slot must read
     * even when the exception states no message - which an unchecked failure
     * commonly does: the exception's own name says what the missing message
     * cannot, where a raw null would render the literal "null".
     */
    private static String reasonOf(Throwable x) {
        String reason = x.getMessage();
        return reason != null && !reason.isEmpty() ? reason : x.toString();
    }

    /**
     * Forgets the reported refusal and the reported unexpected failure, as the physical connection this interceptor is
     * attached to is gone: the session that replaces it gets its own reports, the way it gets its own driver to answer
     * the call.
     */
    @Override
    public void disconnected(ConnectionPool parent, PooledConnection con, boolean finalizing) {
        timeoutRefusalReported = false;
        timeoutFailureReported = false;
        super.disconnected(parent, con, finalizing);
    }

    /**
     * No-op; the refusal record belongs to the physical connection, not to the borrow, so ending a borrow has nothing
     * here to clean up.
     */
    @Override
    public void closeInvoked() {
    }

}

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
     * The hook runs on the thread that borrowed the connection, and a pooled connection is only borrowed by one thread
     * at a time - the next borrower reaches this instance through the pool's idle queue, which publishes what the
     * previous one stored here - so a plain field is enough, as it is for the cached state of {@link ConnectionState}.
     */
    private boolean timeoutRefusalReported;

    /**
     * Default constructor.
     */
    public QueryTimeoutInterceptor() {
        super();
    }

    /**
     * Initializes the {@code queryTimeout} property from the provided configuration map. A value that is not an
     * integer is not applied and is warned about: the timeout keeps the value it has - for a fresh instance the
     * one-second default - so a misconfigured pool behaves as the default and the warning says so as the bad value
     * is configured. The pool configures an interceptor instance once at pool start, once per physical connection
     * chain, and once when the pool stops, so the report shares the per-connection reporting volume of the driver
     * refusal below.
     *
     * @param properties map of interceptor properties
     */
    @Override
    public void setProperties(Map<String, InterceptorProperty> properties) {
        super.setProperties(properties);
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
                    p.getValue(), timeout));
        }
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
            } catch (SQLException x) {
                reportTimeoutRefusal(x);
            }
        }
        return statement;
    }

    /*
     * Reports that the driver will not apply the configured timeout, once per physical connection.
     *
     * The statement is still handed out, unchanged: enforcing a timeout is something only the driver can do, so
     * refusing to create a statement an application can use would trade a protection the pool cannot deliver for a
     * driver that simply does not implement the call. What the pool can do is say so - and say it in a way that is
     * readable, which per-statement warnings are not. The warning therefore names the timeout that is not in effect and
     * says that this connection will not mention it again, so one line tells the operator both the state and the
     * volume of what is missing from the log.
     */
    private void reportTimeoutRefusal(SQLException x) {
        if (timeoutRefusalReported) {
            return;
        }
        timeoutRefusalReported = true;
        log.warn(MessageFactory.get("queryTimeoutInterceptor.queryTimeoutRefused", timeout, x.getMessage()), x);
    }

    /**
     * Forgets the reported refusal, as the physical connection this interceptor is attached to is gone: the session
     * that replaces it gets its own refusal recorded and its own warning, the way it gets its own driver to answer the
     * call.
     */
    @Override
    public void disconnected(ConnectionPool parent, PooledConnection con, boolean finalizing) {
        timeoutRefusalReported = false;
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

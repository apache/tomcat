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

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.SQLException;

import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * A DisposableConnectionFacade object is the top most interceptor of the connection handle the pool hands out:
 * the pool creates one per borrow, wrapping the per-borrow {@link StatementFacade} above the interceptor chain
 * cached on the {@link PooledConnection}, which ends in the {@link ProxyConnection} over the physical session.
 * Disposing the facade - breaking the link to that chain - is what makes a handle inert once its borrow has
 * ended, so a handle kept past its borrow can never drive the session the pool has re-served to the next
 * borrower.
 * <p>
 * The DisposableConnectionFacade intercepts seven method names. While the facade is live, the answers marked
 * live are given; once it is disposed, the names marked disposed are answered by the facade itself and the call
 * never reaches the chain:
 * <ul>
 * <li>{@link java.sql.Connection#close()} - live: breaks the link between this facade and the next interceptor,
 * then delegates the close to the chain, which returns the connection to the pool; may be called multiple
 * times. Disposed: the no-op the JDBC specification prescribes for a closed connection.</li>
 * <li>{@link java.sql.Connection#abort(java.util.concurrent.Executor)} - live: disposes of the facade in the
 * same way, then delegates the abort to the chain, which ends the borrow. Disposed: the no-op the
 * specification prescribes for a closed connection.</li>
 * <li>{@link java.lang.Object#equals(Object)} and {@link java.lang.Object#hashCode()} - answered in both
 * states, with the identity of the handle: each borrow is one handle even over a physical session the pool has
 * re-served.</li>
 * <li>{@link java.sql.Connection#isClosed()} - live: delegated. Disposed: <code>true</code>.</li>
 * <li>{@link java.sql.Connection#isValid(int)} - live: delegated. Disposed: <code>false</code>, the answer the
 * JDBC contract gives a closed connection.</li>
 * <li>{@link java.lang.Object#toString()} - live: delegated. Disposed: a custom string, so a released handle
 * stays printable for logging.</li>
 * </ul>
 * Every other method on a disposed handle fails with an {@link SQLException} reporting the closed connection,
 * the way a use of a closed connection fails; a live facade delegates it to the chain.
 * <p>
 * By default method names are compared with {@link String#equals(Object)}; {@link #setUseEquals(boolean)}
 * called with a <code>false</code> argument switches an instance to reference comparison. This facade takes its
 * value from the interceptor it wraps - in a configured pool, the top configured interceptor's own value,
 * decided by that interceptor's <code>useEquals</code> property; the pool-level
 * {@link PoolConfiguration#setUseEquals(boolean)} reaches the {@link ProxyConnection} at the bottom of the
 * chain, not this class.
 */
public class DisposableConnectionFacade extends JdbcInterceptor {
    /**
     * Construct a DisposableConnectionFacade that wraps the given interceptor.
     *
     * @param interceptor The next interceptor in the chain
     */
    protected DisposableConnectionFacade(JdbcInterceptor interceptor) {
        setUseEquals(interceptor.isUseEquals());
        setNext(interceptor);
    }

    @Override
    public void reset(ConnectionPool parent, PooledConnection con) {
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
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Read the link once, into a snapshot: the disposed answers, the
        // dispose and the dispatch below are all decided about one state of
        // the facade, and a call can only ever enter the chain this guard
        // looked at - so a failure surfacing from the dispatch can only have
        // come from below it.
        JdbcInterceptor next = getNext();
        if (compare(EQUALS_VAL, method)) {
            // Object.equals must answer false - never throw - for a null
            // or foreign argument, so the handler extraction has to be
            // guarded: Proxy.getInvocationHandler throws NPE on null and
            // IllegalArgumentException on a non-proxy.
            Object other = args[0];
            return Boolean.valueOf(other != null && Proxy.isProxyClass(other.getClass()) &&
                    this.equals(Proxy.getInvocationHandler(other)));
        } else if (compare(HASHCODE_VAL, method)) {
            return Integer.valueOf(this.hashCode());
        } else if (next == null) {
            if (compare(ISCLOSED_VAL, method)) {
                return Boolean.TRUE;
            } else if (compare(CLOSE_VAL, method)) {
                return null;
            } else if (compare(ABORT_VAL, method)) {
                // The JDBC specification makes abort on an already closed
                // connection a no-op, so the disposed facade answers it
                // quietly instead of letting the dead chain turn it into the
                // already-closed failure that every other call gets.
                return null;
            } else if (compare(ISVALID_VAL, method)) {
                return Boolean.FALSE;
            }
        }

        if (compare(CLOSE_VAL, method) || compare(ABORT_VAL, method)) {
            // Dispose the facade before delegating close or abort: the chain
            // hands the physical connection back to the pool (close to the
            // idle queue, abort straight out of service), where its slot can
            // be taken by a waiting thread and a new setup run before this
            // call returns. Sealing only afterwards would leave a window in
            // which new calls pass the disposed guard above and execute
            // against the next borrower's connection, and a concurrent
            // second dispose call - a close racing an abort is the
            // specification's own use case for abort - could drive the
            // return a second time. Capture and seal are one atomic step
            // under this facade's monitor so that of the dispose calls
            // exactly one observes a live link and delegates; the delegation
            // itself runs outside the lock, which matters most for abort: the
            // specification allows the driver to take an extended period in
            // it, and a concurrent close must not wait on that.
            JdbcInterceptor captured;
            synchronized (this) {
                captured = getNext();
                setNext(null);
            }
            if (captured == null) {
                // Already disposed by a concurrent close.
                return null;
            }
            return captured.invoke(proxy, method, args);
        }

        if (next == null) {
            // The disposed answer is decided on this call's own chain read,
            // not by catching the NullPointerException the dead chain throws
            // around the delegation: that catch could not tell the sentinel
            // from a genuine NullPointerException raised below the facade by
            // a call it had already dispatched, so a driver or interceptor
            // failure that landed while a concurrent dispose was sealing the
            // facade was rewritten into this misleading closed failure with
            // its trace discarded. A failure surfacing from the dispatch
            // below is such a failure and stays what it is; a call that
            // entered the chain before a concurrent dispose sealed it is
            // refused by the borrow fence the layers below the facade run.
            if (compare(TOSTRING_VAL, method)) {
                return "DisposableConnectionFacade[null]";
            }
            throw new SQLException(MessageFactory.get("disposableConnectionFacade.pooledConnectionClosed"));
        }
        return next.invoke(proxy, method, args);
    }
}

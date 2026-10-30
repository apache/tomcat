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

/**
 * A DisposableConnectionFacade object is the top most interceptor that wraps an object of type
 * {@link PooledConnection}. The DisposableConnectionFacade intercepts three methods:
 * <ul>
 * <li>{@link java.sql.Connection#close()} - breaks the link between cutoff and the next interceptor, then returns the
 * connection to the pool. May be called multiple times.</li>
 * <li>{@link java.sql.Connection#abort(java.util.concurrent.Executor)} - disposes of the facade in the same way, then
 * delegates the abort to the chain, which ends the borrow. A facade already disposed answers the call with the
 * no-op the JDBC specification prescribes for a closed connection.</li>
 * <li>{@link java.lang.Object#toString()} - returns a custom string for this object</li>
 * </ul>
 * By default method comparisons is done on a String reference level, unless the
 * {@link PoolConfiguration#setUseEquals(boolean)} has been called with a <code>true</code> argument.
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
        } else if (getNext() == null) {
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
            JdbcInterceptor next;
            synchronized (this) {
                next = getNext();
                setNext(null);
            }
            if (next == null) {
                // Already disposed by a concurrent close.
                return null;
            }
            return next.invoke(proxy, method, args);
        }

        try {
            return super.invoke(proxy, method, args);
        } catch (NullPointerException e) {
            if (getNext() == null) {
                if (compare(TOSTRING_VAL, method)) {
                    return "DisposableConnectionFacade[null]";
                }
                throw new SQLException("PooledConnection has already been closed.");
            }

            throw e;
        }
    }
}

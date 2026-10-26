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
 * A DisposableConnectionFacade object is the top most interceptor that wraps an
 * object of type {@link PooledConnection}. The DisposableConnectionFacade intercepts
 * two methods:
 * <ul>
 *   <li>{@link java.sql.Connection#close()} - breaks the link between cutoff
 *       and the next interceptor, then returns the connection to the pool.
 *       May be called multiple times.</li>
 *   <li>{@link java.lang.Object#toString()} - returns a custom string for this
 *       object</li>
 * </ul>
 * By default method comparisons is done on a String reference level, unless the
 * {@link PoolConfiguration#setUseEquals(boolean)} has been called with a
 * <code>true</code> argument.
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
        return this==obj;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args)
            throws Throwable {
        if (compare(EQUALS_VAL, method)) {
            // Object.equals must answer false - never throw - for a null
            // or foreign argument, so the handler extraction has to be
            // guarded: Proxy.getInvocationHandler throws NPE on null and
            // IllegalArgumentException on a non-proxy.
            Object other = args[0];
            return Boolean.valueOf(other != null
                    && Proxy.isProxyClass(other.getClass())
                    && this.equals(Proxy.getInvocationHandler(other)));
        } else if (compare(HASHCODE_VAL, method)) {
            return Integer.valueOf(this.hashCode());
        } else if (getNext()==null) {
            if (compare(ISCLOSED_VAL, method)) {
                return Boolean.TRUE;
            }
            else if (compare(CLOSE_VAL, method)) {
                return null;
            }
            else if (compare(ISVALID_VAL, method)) {
                return Boolean.FALSE;
            }
        }

        if (compare(CLOSE_VAL, method)) {
            // Dispose the facade before delegating close: the chain hands
            // the physical connection back to the pool, where it can be
            // re-borrowed and the shared interceptor chain re-armed before
            // this call returns. Sealing only afterwards would leave a
            // window in which new calls pass the disposed guard above and
            // execute against the next borrower's connection, and a
            // concurrent second close could drive the return a second time.
            // Capture and seal are one atomic step under this facade's
            // monitor so that of two concurrent closes exactly one observes
            // a live link and delegates; the delegation itself runs outside
            // the lock.
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
                throw new SQLException(
                        "PooledConnection has already been closed.");
            }

            throw e;
        }
    }
}

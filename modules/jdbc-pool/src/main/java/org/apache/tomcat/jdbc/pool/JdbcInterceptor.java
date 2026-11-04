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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

import org.apache.tomcat.jdbc.pool.PoolProperties.InterceptorProperty;

/**
 * Abstract class that is to be extended for implementations of interceptors. When an operation is called on the
 * {@link java.sql.Connection} handle the pool hands out, the call travels down the interceptor chain; an interceptor
 * that does not answer a call itself continues it by returning {@code super.invoke(proxy, method, args)}. A few calls
 * are answered before the chain is entered - the {@link DisposableConnectionFacade} on top of every handle answers
 * {@link Object#equals(Object)} and {@link Object#hashCode()} itself, and a handle whose borrow has ended is refused
 * by that facade - but everything else reaches each link of the chain. Interceptors are useful to change or improve
 * behavior of the connection pool.<br>
 * Interceptors can receive a set of properties. Each sub class is responsible for parsing the properties during runtime
 * when they are needed or simply override the {@link #setProperties(Map)} method. Properties arrive in a key-value pair
 * of Strings as they were received through the configuration. One set of interceptor instances is built per pooled
 * connection (see {@link #setProperties(Map)}), plus a throwaway instance per configured class for each of the
 * {@link #poolStarted(ConnectionPool)} and {@link #poolClosed(ConnectionPool)} notifications. Hooks and
 * {@link #invoke(Object, Method, Object[])} can arrive from any thread that touches the pool: borrower threads run the
 * chain, while pool management threads run {@link #reset(ConnectionPool, PooledConnection)} and
 * {@link #disconnected(ConnectionPool, PooledConnection, boolean)} when a connection is shut down.
 */
public abstract class JdbcInterceptor implements InvocationHandler {
    /**
     * {@link java.sql.Connection#close()} method name
     */
    public static final String CLOSE_VAL = "close";
    /**
     * {@link java.sql.Connection#abort(java.util.concurrent.Executor)} method name
     */
    public static final String ABORT_VAL = "abort";
    /**
     * {@link Object#toString()} method name
     */
    public static final String TOSTRING_VAL = "toString";
    /**
     * {@link java.sql.Connection#isClosed()} method name
     */
    public static final String ISCLOSED_VAL = "isClosed";
    /**
     * {@link javax.sql.PooledConnection#getConnection()} method name
     */
    public static final String GETCONNECTION_VAL = "getConnection";
    /**
     * {@link java.sql.Wrapper#unwrap(Class)} method name
     */
    public static final String UNWRAP_VAL = "unwrap";
    /**
     * {@link java.sql.Wrapper#isWrapperFor(Class)} method name
     */
    public static final String ISWRAPPERFOR_VAL = "isWrapperFor";

    /**
     * {@link java.sql.Connection#isValid(int)} method name
     */
    public static final String ISVALID_VAL = "isValid";

    /**
     * {@link java.lang.Object#equals(Object)}
     */
    public static final String EQUALS_VAL = "equals";

    /**
     * {@link java.lang.Object#hashCode()}
     */
    public static final String HASHCODE_VAL = "hashCode";

    /**
     * Properties for this interceptor.
     */
    protected Map<String, InterceptorProperty> properties = null;

    /**
     * The next interceptor in the chain
     */
    private volatile JdbcInterceptor next = null;
    /**
     * Property that decides how we do string comparison, default is to use {@link String#equals(Object)}. If set to
     * <code>false</code> then the equality operator (==) is used.
     */
    private boolean useEquals = true;

    /**
     * Public constructor for instantiation through reflection
     */
    public JdbcInterceptor() {
        // NOOP
    }

    /**
     * Gets invoked each time an operation on {@link java.sql.Connection} is invoked. {@inheritDoc}
     * <p>
     * The base implementation passes the call to the {@link #getNext() next} interceptor in the chain. When there is no
     * next interceptor, the call fails fast with a {@link NullPointerException}: a chain built by the pool always ends
     * in the {@link ProxyConnection} terminal, so reaching this case means a chain was hand-built without a terminal
     * link. Interceptors that do not answer a call themselves continue it with
     * {@code super.invoke(proxy, method, args)}.
     */

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (getNext() != null) {
            return getNext().invoke(proxy, method, args);
        } else {
            throw new NullPointerException();
        }
    }

    /**
     * Returns the next interceptor in the chain
     *
     * @return the next interceptor in the chain
     */
    public JdbcInterceptor getNext() {
        return next;
    }

    /**
     * configures the next interceptor in the chain
     *
     * @param next The next chain item
     */
    public void setNext(JdbcInterceptor next) {
        this.next = next;
    }

    /**
     * Performs a string comparison, using references unless the useEquals property is set to true.
     *
     * @param name1 The first name
     * @param name2 The second name
     *
     * @return true if name1 is equal to name2 based on {@link #useEquals}
     */
    public boolean compare(String name1, String name2) {
        if (isUseEquals()) {
            return name1.equals(name2);
        } else {
            return name1 == name2;
        }
    }

    /**
     * Compares a method name (String) to a method (Method) {@link #compare(String,String)} Uses reference comparison
     * unless the useEquals property is set to true
     *
     * @param methodName The method name
     * @param method     The method
     *
     * @return <code>true</code> if the name matches
     */
    public boolean compare(String methodName, Method method) {
        return compare(methodName, method.getName());
    }

    /**
     * Gets called each time the connection is borrowed from the pool This means that if an interceptor holds a
     * reference to the connection the interceptor can be reused for another connection. <br>
     * This method may be called with null as both arguments when we are closing down the connection. When called for a
     * shutdown reset, an interceptor that throws is isolated: the exception is logged and ignored, and the remaining
     * interceptors in the chain are reset as well. <br>
     * Chain topology is owned by the pool: a hook must not call {@link #setNext(JdbcInterceptor)} on itself or on any
     * other link while the chain is wired, and in particular not from this callback. The walks that run this callback do
     * not agree on when they read the next link - the walk at borrow time re-reads it after the callback, the shutdown
     * walks read it before - so a hook that rewired its own link would be visited differently depending on which walk
     * runs, and could be skipped or revisited without limit.
     *
     * @param parent - the connection pool owning the connection
     * @param con    - the pooled connection
     */
    public abstract void reset(ConnectionPool parent, PooledConnection con);

    /**
     * Called when {@link java.sql.Connection#close()} is called on the underlying connection. This is to notify the
     * interceptors, that the physical connection has been released. Implementation of this method should be thought
     * through with care, as no actions should trigger an exception. An interceptor that nevertheless throws is
     * isolated: the exception is logged and ignored, the remaining interceptors in the chain are still notified, and
     * the physical connection is closed as it would have been. As for {@link #reset(ConnectionPool, PooledConnection)},
     * a hook must not rewire the chain from this callback: chain topology is owned by the pool.
     *
     * @param parent     - the connection pool that this connection belongs to
     * @param con        - the pooled connection that holds this connection
     * @param finalizing - if this connection is finalizing. True means that the pooled connection will not reconnect
     *                       the underlying connection
     */
    public void disconnected(ConnectionPool parent, PooledConnection con, boolean finalizing) {
    }


    /**
     * Returns the properties configured for this interceptor. The map is shared with every other interceptor instance
     * created from the same interceptor definition and with every later chain build, so it must be treated as
     * read-only: mutating it changes what all those instances and all later chains see.
     *
     * @return the configured properties for this interceptor, to be treated as read-only
     */
    public Map<String, InterceptorProperty> getProperties() {
        return properties;
    }

    /**
     * Called during the creation of an interceptor, once per instance: once for each instance created to build a pooled
     * connection's interceptor chain, and once on the throwaway instance of each configured class that receives
     * {@link #poolStarted(ConnectionPool)} at pool start and {@link #poolClosed(ConnectionPool)} at pool close. The pool
     * passes the live map of the interceptor definition, shared across all these instances, so it must be treated as
     * read-only (see {@link #getProperties()}). Override this method to perform type casts between string values and
     * object properties.
     *
     * @param properties The properties
     */
    public void setProperties(Map<String, InterceptorProperty> properties) {
        this.properties = properties;
        final String useEquals = "useEquals";
        InterceptorProperty p = properties.get(useEquals);
        if (p != null) {
            // Same parsing as every other interceptor property: a value that
            // is not exactly "true" or "false" keeps the current value rather
            // than silently switching to reference comparison, the way
            // Boolean.parseBoolean would.
            setUseEquals(p.getValueAsBoolean(isUseEquals()));
        }
    }

    /**
     * True if the compare method uses the Object.equals(Object) method, false if comparison is done on a reference
     * level.
     *
     * @return true if equals is used for comparisons
     */
    public boolean isUseEquals() {
        return useEquals;
    }

    /**
     * Set to true if string comparisons (for the {@link #compare(String, Method)} and {@link #compare(String, String)}
     * methods) should use the Object.equals(Object) method The default is true
     *
     * @param useEquals <code>true</code> if equals will be used for comparisons
     */
    public void setUseEquals(boolean useEquals) {
        this.useEquals = useEquals;
    }

    /**
     * This method is invoked by a connection pool when the pool is closed. Interceptor classes can override this method
     * if they keep static variables or other tracking means around. <b>This method is only invoked on a single instance
     * of the interceptor, and not on every instance created.</b> That instance is a throwaway created solely for this
     * notification: it shares no state with the instances in any connection's chain, so any state this hook needs must
     * be static or keyed on the pool. The callback is delivered for every definition the pool told about its
     * {@link #poolStarted(ConnectionPool) start}, whatever the interceptor configuration lists by the time the pool
     * closes, and additionally for any definition the configuration has gained since the start. An override that
     * registers state at the start must therefore find its release delivered even after its definition has been
     * removed from a live pool's configuration, and must tolerate a delivery it did not pair with its own start. An
     * exception thrown here is logged at debug level and otherwise ignored; the pool close continues.
     *
     * @param pool - the pool that is being closed.
     */
    public void poolClosed(ConnectionPool pool) {
        // NOOP
    }

    /**
     * This method is invoked by a connection pool when the pool is first started up, usually when the first connection
     * is requested. Interceptor classes can override this method if they keep static variables or other tracking means
     * around. <b>This method is only invoked on a single instance of the interceptor, and not on every instance
     * created.</b> That instance is a throwaway created solely for this notification: it shares no state with the
     * instances in any connection's chain or with the instance that later receives {@link #poolClosed(ConnectionPool)},
     * so any state this hook needs must be static or keyed on the pool. An exception thrown here aborts pool
     * initialization: the pool is closed and the failure is reported to the caller that triggered the start-up.
     *
     * @param pool - the pool that is being started up.
     */
    public void poolStarted(ConnectionPool pool) {
        // NOOP
    }

}

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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.apache.tomcat.jdbc.pool.util.ExceptionUtils;
import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * Abstraction interceptor. This component intercepts all calls to create some type of SQL statement. By extending this
 * class, one can intercept queries and update statements by overriding the
 * {@link #createStatement(Object, Method, Object[], Object, long)} method.
 * <p>
 * Subclass contract. The base intercepts three things on the connection proxy, and a subclass participates in each of
 * them:</p>
 * <ul>
 * <li>the statement creation path, through {@link #createStatement}, which receives the created statement and the
 * monotonic elapsed time and returns what the application will hold;</li>
 * <li>the close path, through {@link #closeInvoked()}, fired on every {@link java.sql.Connection#close()} before the
 * close reaches the pool, while the connection is still alive;</li>
 * <li>the borrow-start path: this base holds no per-borrow state and its {@link #reset} is a no-op, so any per-borrow
 * state a subclass keeps must be cleared by the subclass's own reset override - the pool fans out reset, not
 * anything in this class.</li>
 * </ul>
 * <p>
 * Proxy classes come from the constructor cache described at {@link #getConstructor(int, Class)}; each index there is
 * bound to one interface and a subclass must keep it that way.</p>
 */
public abstract class AbstractCreateStatementInterceptor extends JdbcInterceptor {
    private static final Log log = LogFactory.getLog(AbstractCreateStatementInterceptor.class);

    /**
     * {@link java.sql.Connection#createStatement()}
     */
    protected static final String CREATE_STATEMENT = "createStatement";
    /**
     * Index for {@link #CREATE_STATEMENT} in {@link #STATEMENT_TYPES}
     */
    protected static final int CREATE_STATEMENT_IDX = 0;
    /**
     * {@link java.sql.Connection#prepareStatement(String)}
     */
    protected static final String PREPARE_STATEMENT = "prepareStatement";
    /**
     * Index for {@link #PREPARE_STATEMENT} in {@link #STATEMENT_TYPES}
     */
    protected static final int PREPARE_STATEMENT_IDX = 1;
    /**
     * {@link java.sql.Connection#prepareCall(String)}
     */
    protected static final String PREPARE_CALL = "prepareCall";
    /**
     * Index for {@link #PREPARE_CALL} in {@link #STATEMENT_TYPES}
     */
    protected static final int PREPARE_CALL_IDX = 2;

    /**
     * {@link Statement#getResultSet()}
     */
    protected static final String GET_RESULTSET = "getResultSet";

    /**
     * {@link Statement#getGeneratedKeys()}
     */
    protected static final String GET_GENERATED_KEYS = "getGeneratedKeys";

    /**
     * {@link ResultSet#getStatement()}
     */
    protected static final String GET_STATEMENT = "getStatement";

    /**
     * Index in {@link #constructors} of the {@link ResultSet} proxy
     * constructor. This is not an index into {@link #STATEMENT_TYPES}: the
     * cache carries one slot beyond the statement types, for the result set
     * handles of subclasses that wrap statement-level calls such as
     * {@link Statement#getResultSet()} and {@link ResultSet#getStatement()}.
     */
    protected static final int RESULTSET_IDX = 3;

    /**
     * Array of statement type method names
     */
    protected static final String[] STATEMENT_TYPES = { CREATE_STATEMENT, PREPARE_STATEMENT, PREPARE_CALL };
    /**
     * Count of statement types in {@link #STATEMENT_TYPES}
     */
    protected static final int STATEMENT_TYPE_COUNT = STATEMENT_TYPES.length;

    /**
     * {@link java.sql.Statement#execute}
     */
    protected static final String EXECUTE = "execute";
    /**
     * {@link java.sql.Statement#executeQuery}
     */
    protected static final String EXECUTE_QUERY = "executeQuery";
    /**
     * {@link java.sql.Statement#executeUpdate}
     */
    protected static final String EXECUTE_UPDATE = "executeUpdate";
    /**
     * {@link java.sql.Statement#executeBatch()}
     */
    protected static final String EXECUTE_BATCH = "executeBatch";
    /**
     * {@link java.sql.Statement#executeLargeUpdate} and its overloads, and
     * {@link java.sql.PreparedStatement#executeLargeUpdate()}
     */
    protected static final String EXECUTE_LARGE_UPDATE = "executeLargeUpdate";
    /**
     * {@link java.sql.Statement#executeLargeBatch()}
     */
    protected static final String EXECUTE_LARGE_BATCH = "executeLargeBatch";

    /**
     * Array of execute type method names
     */
    protected static final String[] EXECUTE_TYPES = { EXECUTE, EXECUTE_QUERY, EXECUTE_UPDATE, EXECUTE_BATCH,
            EXECUTE_LARGE_UPDATE, EXECUTE_LARGE_BATCH };

    /**
     * The constructors used to create statement proxies, one slot per index:
     * {@link #CREATE_STATEMENT_IDX}, {@link #PREPARE_STATEMENT_IDX},
     * {@link #PREPARE_CALL_IDX} and {@link #RESULTSET_IDX}. The cache is
     * shared by all subclasses loaded through the same class loader. Its only
     * writer is this class's initializer, which the JVM runs once and whose
     * writes it publishes to every thread that uses the class, so reading a
     * slot needs no locking. Subclasses reach the entries only through
     * {@link #getConstructor(int, Class)}, which enforces that every use of an
     * index passes an interface the cached proxy implements.
     */
    private static final Constructor<?>[] constructors = new Constructor[STATEMENT_TYPE_COUNT + 1];

    /**
     * Reads one slot of the constructor cache. Package-private rather than
     * protected: getConstructor is the accessor for subclasses, in-tree or
     * third party, and this exists only so the tests in this package can
     * observe what the initializer published.
     *
     * @param idx the index of the slot
     *
     * @return the constructor cached for the index, or null if the initializer
     *             failed to populate it
     */
    static Constructor<?> cachedConstructor(int idx) {
        return constructors[idx];
    }

    /**
     * Replaces one slot of the constructor cache, returning what was there.
     * The write counterpart of {@link #cachedConstructor(int)}: package-private
     * and used only by the tests in this package, to simulate a slot the class
     * initializer failed to populate.
     *
     * @param idx         the index of the slot
     * @param replacement what to store, possibly null
     *
     * @return the constructor that was in the slot
     */
    static Constructor<?> replaceCachedConstructor(int idx, Constructor<?> replacement) {
        Constructor<?> previous = constructors[idx];
        constructors[idx] = replacement;
        return previous;
    }

    static {
        // Class initialization (JLS 12.4.2) runs once per class loader and its
        // writes are visible to every thread once it completes, so populating
        // the canonical slots here makes them safely published without any
        // monitor on the hot statement path.
        populateConstructor(CREATE_STATEMENT_IDX, Statement.class);
        populateConstructor(PREPARE_STATEMENT_IDX, PreparedStatement.class);
        populateConstructor(PREPARE_CALL_IDX, CallableStatement.class);
        populateConstructor(RESULTSET_IDX, ResultSet.class);
    }

    /**
     * Computes the proxy constructor for one index of {@link #constructors}
     * and stores it in the cache. On failure the slot is left empty and only
     * logged; {@link #getConstructor(int, Class)} then computes a constructor
     * on demand without caching it, so a failure here degrades to the
     * pre-existing behavior rather than failing class initialization.
     *
     * @param idx   - the index of the constructor
     * @param clazz - the interface that the proxy will implement
     */
    @SuppressWarnings("deprecation")
    private static void populateConstructor(int idx, Class<?> clazz) {
        try {
            Class<?> proxyClass = Proxy.getProxyClass(AbstractCreateStatementInterceptor.class.getClassLoader(),
                    new Class[] { clazz });
            constructors[idx] = proxyClass.getConstructor(new Class[] { InvocationHandler.class });
        } catch (ReflectiveOperationException | IllegalArgumentException | LinkageError e) {
            // An exception escaping the class initializer would surface as an
            // ExceptionInInitializerError at every use of every subclass.
            log.warn(MessageFactory.get("abstractCreateStatementInterceptor.unableToCreateConstructorCacheEntry",
                    idx, clazz.getName()), e);
        }
    }

    /**
     * Default constructor
     */
    public AbstractCreateStatementInterceptor() {
        super();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (compare(CLOSE_VAL, method)) {
            try {
                closeInvoked();
            } catch (Throwable t) {
                // Propagate the fatal errors the module never swallows, then
                // isolate the rest: the borrow must end whatever a hook makes
                // of it, because the application handle is already disposed at
                // this point, so a throw here would strand the session as busy
                // with no way to retry the close.
                ExceptionUtils.handleThrowable(t);
                log.warn(MessageFactory.get("abstractCreateStatementInterceptor.closeInvokedFailed",
                        getClass().getName()), t);
            }
            return super.invoke(proxy, method, args);
        } else {
            if (isStatement(method, false)) {
                long start = System.nanoTime();
                Object statement = super.invoke(proxy, method, args);
                long delta = elapsedMillis(start);
                try {
                    return createStatement(proxy, method, args, statement, delta);
                } catch (Throwable t) {
                    // The application never receives the statement when the
                    // hook fails, so do not orphan it on the pooled
                    // connection. See the createStatement() contract below.
                    if (statement instanceof Statement) {
                        try {
                            ((Statement) statement).close();
                        } catch (Throwable closeFailure) {
                            // Best effort: never mask the hook's failure, but
                            // propagate the fatal errors the module never
                            // swallows.
                            ExceptionUtils.handleThrowable(closeFailure);
                            if (closeFailure != t) {
                                t.addSuppressed(closeFailure);
                            }
                        }
                    }
                    throw t;
                }
            } else {
                return super.invoke(proxy, method, args);
            }
        }
    }

    /**
     * Measures the milliseconds elapsed since the {@link System#nanoTime()}
     * reading taken at {@code startNanos}, rounded to the nearest millisecond.
     * <p>
     * An interval is measured with the monotonic clock. Had it been measured
     * with the wall clock instead, an NTP correction, a manual set, or
     * resuming a suspended VM between the two readings could have made the
     * difference negative, and a negative duration is never over the slow
     * query threshold while the statistics book it as if it were real.
     * </p>
     *
     * @param startNanos the {@link System#nanoTime()} reading the measurement began with
     *
     * @return the elapsed time in milliseconds
     */
    protected static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos + 500000) / 1000000;
    }

    /**
     * Hands out the proxy constructor cached for this index. The method is
     * safe for concurrent use: the only writer of the cache is this class's
     * initializer, whose writes the JVM publishes to every thread that uses
     * the class, so reading a slot needs no locking; {@code newInstance} on
     * the returned constructor runs as it always has, outside this method. If
     * the initializer failed to populate the index, a constructor for the
     * requested interface is computed on demand and returned without being
     * cached, so such calls return equivalent, but not necessarily identical,
     * objects.
     *
     * @param idx   - the index of the constructor
     * @param clazz - the interface that the proxy will implement
     *
     * @return - returns a constructor used to create new instances
     *
     * @throws NoSuchMethodException Constructor not found
     * @throws IllegalStateException The index is already cached for a proxy class that does not implement
     *                                   {@code clazz}. Every use of an index in this shared cache must pass
     *                                   an interface that the cached proxy implements.
     */
    /*
     * The pool's comments argue for caching the proxy class, so continue to use Proxy.getProxyClass(): the drop-in
     * alternative, Proxy.newProxyInstance, would rebuild the proxy class lookup on every statement creation instead
     * of reading a cache slot. getProxyClass has been deprecated since Java 9 without being marked for removal, so
     * the deprecation suppression is sufficient; should that ever flip, the removal lint and this comment will say
     * so and the caching argument must be re-weighed against the replacement.
     */
    @SuppressWarnings("deprecation")
    protected Constructor<?> getConstructor(int idx, Class<?> clazz) throws NoSuchMethodException {
        Constructor<?> cached = constructors[idx];
        if (cached == null) {
            // Only reachable if the class initializer failed to populate this
            // slot. Compute the constructor but deliberately do not store it:
            // a write from an arbitrary thread would be the only write outside
            // the class initializer and would defeat the safe publication of
            // the other entries, while the JDK's own proxy class cache keeps
            // the repeated computation to a lookup.
            Class<?> proxyClass = Proxy.getProxyClass(AbstractCreateStatementInterceptor.class.getClassLoader(),
                    new Class[] { clazz });
            return proxyClass.getConstructor(new Class[] { InvocationHandler.class });
        }
        if (!clazz.isAssignableFrom(cached.getDeclaringClass())) {
            throw new IllegalStateException(MessageFactory.get(
                    "abstractCreateStatementInterceptor.constructorCacheIndexPopulated",
                    idx, Arrays.toString(cached.getDeclaringClass().getInterfaces()), clazz.getName()));
        }
        return cached;
    }

    /**
     * This method will be invoked after a successful statement creation. This method can choose to return a wrapper
     * around the statement or return the statement itself. If this method returns a wrapper then it should return a
     * wrapper object that implements one of the following interfaces. {@link java.sql.Statement},
     * {@link java.sql.PreparedStatement} or {@link java.sql.CallableStatement}
     * <p>
     * An implementation must not make the statement reachable from any other code before it returns, because if this
     * method throws then the statement is closed on a best-effort basis by the interceptor base class before the
     * throwable is propagated to the application.
     *
     * @param proxy     the actual proxy object
     * @param method    the method that was called. It will be one of the methods defined in {@link #STATEMENT_TYPES}
     * @param args      the arguments to the method
     * @param statement the statement that the underlying connection created
     * @param time      elapsed time in milliseconds, measured with the monotonic clock
     *
     * @return a {@link java.sql.Statement} object
     */
    public abstract Object createStatement(Object proxy, Method method, Object[] args, Object statement, long time);

    /**
     * Method invoked when the operation {@link java.sql.Connection#close()} is invoked. It runs before the close is
     * passed down the chain, so that a hook can still reach the connection while it is alive - statements must be
     * closed before the session is handed to the next borrower. Implementation of this method should be thought
     * through with care, as no actions should trigger an exception. An interceptor that nevertheless throws is
     * isolated: the exception is logged and ignored, the remaining interceptors in the chain still receive the close,
     * and the connection is returned to the pool as it would have been. The fatal virtual machine errors the pool
     * never swallows are the exception: those propagate.
     */
    public abstract void closeInvoked();

    /**
     * Returns true if the method that is being invoked matches one of the statement types.
     *
     * @param method  the method being invoked on the proxy
     * @param process initial accumulator value, pass <code>false</code>
     *
     * @return returns true if the method name matched
     */
    protected boolean isStatement(Method method, boolean process) {
        return process(STATEMENT_TYPES, method, process);
    }

    /**
     * Returns true if the method that is being invoked matches one of the execute types.
     *
     * @param method  the method being invoked on the proxy
     * @param process initial accumulator value, pass <code>false</code>
     *
     * @return returns true if the method name matched
     */
    protected boolean isExecute(Method method, boolean process) {
        return process(EXECUTE_TYPES, method, process);
    }

    /**
     * Returns true if the method that is being invoked matches one of the method names passed in
     *
     * @param names   list of method names that we want to intercept
     * @param method  the method being invoked on the proxy
     * @param process initial accumulator value, pass <code>false</code>
     *
     * @return returns true if the method name matched
     */
    protected boolean process(String[] names, Method method, boolean process) {
        final String name = method.getName();
        for (int i = 0; (!process) && i < names.length; i++) {
            process = compare(names[i], name);
        }
        return process;
    }

    /**
     * no-op for this interceptor. no state is stored.
     */
    @Override
    public void reset(ConnectionPool parent, PooledConnection con) {
        // NOOP
    }
}

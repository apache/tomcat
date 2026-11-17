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
import java.sql.SQLException;

import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * Interceptor that translates an exception type the invoked method does not declare into a type it does, so that a
 * call through the pool's connection proxy respects the {@link java.lang.reflect.InvocationHandler} contract: the
 * exception is re-thrown unchanged when the method declares it (a subclass of a declared type passes as well),
 * replaced by an {@link SQLException} carrying it as the cause when the method declares {@code SQLException}, and
 * replaced by a {@link RuntimeException} carrying it as the cause otherwise. An {@link Error} is always re-thrown as
 * it is, including one carried by an {@link InvocationTargetException}.
 * <p>
 * The pool prepends this interceptor to the definitions parsed from {@code jdbcInterceptors}, so it heads the chain
 * of every connection that has an interceptor chain at all; a pool configured without an interceptor list has no
 * chain and therefore no trap. An undeclared checked exception reaching such an untrapped connection still respects
 * the proxy contract - the JDK proxy itself turns it into an
 * {@link java.lang.reflect.UndeclaredThrowableException} - what the trap adds on top of that is the declared,
 * JDBC-shaped exception with the original preserved as its cause.
 */
public class TrapException extends JdbcInterceptor {


    /**
     * Constructs a new TrapException interceptor.
     */
    public TrapException() {
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            return super.invoke(proxy, method, args);
        } catch (Exception t) {
            Throwable exception = t;
            if (t instanceof InvocationTargetException && t.getCause() != null) {
                exception = t.getCause();
                if (exception instanceof Error) {
                    throw exception;
                }
            }
            Class<?> exceptionClass = exception.getClass();
            if (!isDeclaredException(method, exceptionClass)) {
                if (isDeclaredException(method, SQLException.class)) {
                    SQLException sqlx =
                            new SQLException(MessageFactory.get("trapException.uncaughtUnderlyingException"));
                    sqlx.initCause(exception);
                    exception = sqlx;
                } else {
                    RuntimeException rx =
                            new RuntimeException(MessageFactory.get("trapException.uncaughtUnderlyingException"));
                    rx.initCause(exception);
                    exception = rx;
                }
            }
            throw exception;
        }

    }

    /**
     * Checks if the given exception class is declared in the throws clause of the specified method.
     *
     * @param m     The method to check
     * @param clazz The exception class to look for
     *
     * @return {@code true} if the exception is declared, {@code false} otherwise
     */
    public boolean isDeclaredException(Method m, Class<?> clazz) {
        for (Class<?> cl : m.getExceptionTypes()) {
            if (cl.equals(clazz) || cl.isAssignableFrom(clazz)) {
                return true;
            }
        }
        return false;
    }

    /**
     * no-op for this interceptor. no state is stored.
     */
    @Override
    public void reset(ConnectionPool parent, PooledConnection con) {
        // NOOP
    }

}

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
package org.apache.tomcat.jdbc.pool;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * Pins the exception translation the trap performs on each of its paths: a
 * declared exception is re-thrown as the same instance - nothing of its
 * message, SQLState, vendor code or cause is reshaped - an undeclared one
 * becomes a {@link SQLException} when the method declares that and a
 * {@link RuntimeException} when it declares nothing, always carrying the
 * original as the cause, and an {@link Error} propagates as it is whether it
 * is thrown directly or hidden inside an {@link InvocationTargetException}.
 * The end-to-end case drives a real {@link Proxy} to hold the whole purpose
 * of the class to its contract: an undeclared checked exception must leave
 * the proxy as a declared, JDBC-shaped exception rather than the JDK's own
 * {@link java.lang.reflect.UndeclaredThrowableException}.
 */
class TestTrapException {

    /** Chain link that answers every call by throwing the object it was given. */
    private static final class Thrower extends JdbcInterceptor {
        private final Throwable thrown;

        Thrower(Throwable thrown) {
            this.thrown = thrown;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            throw thrown;
        }

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            // NOOP - the link holds nothing but the object to throw
        }
    }

    /** A trap wired above a link that throws the given object on every call. */
    private static TrapException trapOver(Throwable thrown) {
        TrapException trap = new TrapException();
        trap.setNext(new Thrower(thrown));
        return trap;
    }

    /** The outcome of driving one call - whatever the trap let out. */
    private static Throwable outcomeOf(TrapException trap, Method method, Object proxy) {
        try {
            trap.invoke(proxy, method, null);
            return Assertions.fail("the trap let a successful return through: " + method);
        } catch (Throwable outcome) {
            return outcome;
        }
    }

    @Test
    void aDeclaredExceptionIsRethrownAsTheSameInstance() throws Exception {
        // A subclass of the declared type: the widening of isDeclaredException
        // must let it through untouched, SQLState and vendor code and all.
        SQLTimeoutException thrown = new SQLTimeoutException("deadline gone", "HY000", 42);

        Throwable outcome = outcomeOf(trapOver(thrown), Statement.class.getMethod("close"), new Object());

        Assertions.assertSame(thrown, outcome, "a declared exception is re-thrown as itself");
        Assertions.assertEquals("HY000", ((SQLException) outcome).getSQLState());
        Assertions.assertEquals(42, ((SQLException) outcome).getErrorCode());
    }

    @Test
    void anUndeclaredExceptionOnASqlThrowingMethodBecomesAnSqlExceptionCarryingIt() throws Exception {
        IllegalStateException thrown = new IllegalStateException("state gone wrong");

        Throwable outcome = outcomeOf(trapOver(thrown), Statement.class.getMethod("execute", String.class),
                new Object());

        Assertions.assertInstanceOf(SQLException.class, outcome);
        Assertions.assertSame(thrown, outcome.getCause(), "the original rides along as the cause");
        Assertions.assertEquals(MessageFactory.get("trapException.uncaughtUnderlyingException"),
                outcome.getMessage());
    }

    @Test
    void anUndeclaredExceptionOnAMethodWithoutDeclaredExceptionsBecomesARuntimeExceptionCarryingIt()
            throws Exception {
        IllegalArgumentException thrown = new IllegalArgumentException("no argument fits");

        Throwable outcome = outcomeOf(trapOver(thrown), Object.class.getMethod("toString"), new Object());

        Assertions.assertInstanceOf(RuntimeException.class, outcome,
                "a method declaring nothing can only ever be given an unchecked exception");
        Assertions.assertSame(thrown, outcome.getCause());
    }

    @Test
    void anErrorHiddenInsideAnInvocationTargetExceptionIsRethrownAsIs() throws Exception {
        AssertionError error = new AssertionError("an assertion broke");
        InvocationTargetException ite = new InvocationTargetException(error);

        Throwable outcome = outcomeOf(trapOver(ite), Statement.class.getMethod("execute", String.class),
                new Object());

        Assertions.assertSame(error, outcome, "an Error is never retyped, however it arrives");
    }

    @Test
    void anErrorThrownDirectlyPropagatesUntouched() throws Exception {
        AssertionError error = new AssertionError("thrown straight out");

        Throwable outcome = outcomeOf(trapOver(error), Statement.class.getMethod("execute", String.class),
                new Object());

        Assertions.assertSame(error, outcome, "Errors pass the trap unchanged, as everywhere in the pool");
    }

    @Test
    void anInvocationTargetExceptionWithoutACauseIsWrappedCarryingItself() throws Exception {
        InvocationTargetException ite = new InvocationTargetException(null);

        Throwable outcome = outcomeOf(trapOver(ite), Statement.class.getMethod("close"), new Object());

        // The ITE carries nothing to preserve, so nothing is lost: it is the
        // cause of the wrapper the trap produces for the undeclared throw.
        Assertions.assertInstanceOf(SQLException.class, outcome);
        Assertions.assertSame(ite, outcome.getCause());
    }

    @Test
    void throughARealProxyAnUndeclaredCheckedExceptionLeavesAsADeclaredSqlException() throws Throwable {
        // End to end: handler, proxy and interface contract together. A
        // throw the interface method never declares must not be able to
        // reach the caller as the JDK's own UndeclaredThrowableException.
        IOException undeclared = new IOException("driver spoke off-contract");
        Connection proxy = (Connection) Proxy.newProxyInstance(TestTrapException.class.getClassLoader(),
                new Class<?>[] { Connection.class }, trapOver(undeclared));

        SQLException surfaced = Assertions.assertThrows(SQLException.class, () -> proxy.createStatement(),
                "the trap converts before the JDK proxy gets to wrap the call in an"
                        + " UndeclaredThrowableException");
        Assertions.assertSame(undeclared, surfaced.getCause(),
                "the driver's own exception stays reachable beneath the declared one");
    }

    @Test
    void isDeclaredExceptionacceptsTheDeclaredTypeItsSubtypesAndNothingElse() throws Exception {
        Method close = Statement.class.getMethod("close");
        Method toStringMethod = Object.class.getMethod("toString");
        TrapException trap = new TrapException();

        Assertions.assertTrue(trap.isDeclaredException(close, SQLException.class), "the declared type itself");
        Assertions.assertTrue(trap.isDeclaredException(close, SQLTimeoutException.class),
                "a subtype of the declared type passes under the same widening");
        Assertions.assertFalse(trap.isDeclaredException(close, IllegalStateException.class),
                "nothing else is declared, whatever else is thrown");
        Assertions.assertFalse(trap.isDeclaredException(toStringMethod, RuntimeException.class),
                "a method declaring nothing declares it for no type at all");
    }
}

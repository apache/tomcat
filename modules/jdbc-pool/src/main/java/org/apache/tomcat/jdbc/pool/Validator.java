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

import java.sql.Connection;

/**
 * Interface to be implemented by custom validator classes. An implementation configured through
 * {@link PoolConfiguration#setValidatorClassName(String)} is instantiated reflectively and therefore must have a
 * public no-arg constructor.
 */
public interface Validator {
    /**
     * Validate a connection and return a boolean to indicate if it's valid.
     * <p>
     * The pool calls this method with the underlying JDBC connection, outside any interceptor chain, and treats any
     * runtime exception or non-fatal {@link Error} thrown by an implementation as a validation failure; only a
     * {@link VirtualMachineError} is re-thrown. Implementations that invoke JDBC APIs must catch
     * {@link java.sql.SQLException} and return {@code false} themselves, since this method does not declare a checked
     * exception. A {@code false} result (or a thrown runtime exception or error) causes the pool to discard the
     * connection; on a borrow that discard happens within the same {@code getConnection()} call, as the pool
     * immediately re-establishes the session and validates the replacement, reporting it as
     * {@link PooledConnection#VALIDATE_INIT} when new connections are initialized.
     * <p>
     * The pool performs no transaction management as part of validating. The built-in, query-based check ends any
     * transaction open on the session when it completes - its own, or one a previous borrower left behind - but a
     * validator replaces that check and owns the session state it observes: an implementation that executes
     * statements on a session whose auto-commit mode is off must commit or roll back the transaction those
     * statements open, itself.
     * <p>
     * The pool may skip validation entirely for a given action - it validates when the corresponding
     * {@code testOnBorrow}, {@code testOnReturn}, {@code testWhileIdle} or {@code testOnConnect} flag is set (the
     * idle action additionally requires a running pool sweeper, see
     * {@link PoolConfiguration#setTimeBetweenEvictionRunsMillis(int)}), and for
     * {@link PooledConnection#VALIDATE_INIT} also when a non-blank {@link PoolConfiguration#getInitSQL() initSQL} is
     * configured - and it may short-circuit repeated validations, see
     * {@link PoolConfiguration#setValidationInterval(long)}. The validator replaces the query-based validation
     * entirely: while it is set, neither the {@link PoolConfiguration#getValidationQuery() validation query} nor a
     * configured init SQL is executed. This method is therefore not guaranteed to run for every borrow, and it must
     * not be relied on for per-borrow session state. Implementations are invoked concurrently from any application,
     * management or pool-internal thread - including a sweeper timer thread shared by every pool in the JVM - and
     * must be thread-safe.
     *
     * @param connection     the underlying {@link Connection} to test - not the pool's proxied handle, though the
     *                           object itself may be a driver or third-party wrapper
     * @param validateAction the action used. One of {@link PooledConnection#VALIDATE_BORROW},
     *                           {@link PooledConnection#VALIDATE_IDLE}, {@link PooledConnection#VALIDATE_INIT} or
     *                           {@link PooledConnection#VALIDATE_RETURN}
     *
     * @return true if the connection is valid
     */
    boolean validate(Connection connection, int validateAction);
}

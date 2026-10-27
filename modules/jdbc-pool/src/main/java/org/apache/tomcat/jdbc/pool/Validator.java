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
 * Interface to be implemented by custom validator classes.
 */
public interface Validator {
    /**
     * Validate a connection and return a boolean to indicate if it's valid.
     * <p>
     * The pool calls this method with the underlying JDBC connection, outside
     * any interceptor chain, and treats any runtime exception thrown by an
     * implementation as a validation failure. Implementations that invoke JDBC
     * APIs must catch {@link java.sql.SQLException} and return {@code false}
     * themselves, since this method does not declare a checked exception.
     * A {@code false} result (or a thrown runtime exception) causes the pool
     * to discard the connection.
     * <p>
     * The pool may skip validation entirely for a given action - it only
     * validates when the corresponding {@code testOnBorrow},
     * {@code testOnReturn}, {@code testWhileIdle} or {@code testOnConnect}
     * flag is set - and it may short-circuit repeated validations, see
     * {@link PoolConfiguration#setValidationInterval(long)}. This method is
     * therefore not guaranteed to run for every borrow, and it must not be
     * relied on for per-borrow session state. Implementations are invoked
     * concurrently from borrowing and returning application threads and from
     * the pool cleaner thread and must be thread-safe.
     *
     * @param connection the underlying (non-proxied) {@link Connection} object to test
     * @param validateAction the action used. One of {@link PooledConnection#VALIDATE_BORROW},
     *   {@link PooledConnection#VALIDATE_IDLE}, {@link PooledConnection#VALIDATE_INIT} or
     *   {@link PooledConnection#VALIDATE_RETURN}
     * @return true if the connection is valid
     */
    boolean validate(Connection connection, int validateAction);
}

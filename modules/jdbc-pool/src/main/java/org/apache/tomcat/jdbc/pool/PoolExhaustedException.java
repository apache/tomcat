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

import java.sql.SQLException;

/**
 * Exception thrown when the connection pool is exhausted and no connections are available.
 */
public class PoolExhaustedException extends SQLException {

    private static final long serialVersionUID = 3501536931777262475L;

    /**
     * SQL state reported with pool-exhaustion failures: {@code "HY000"}, the general
     * error class. Pool exhaustion is a pool-side condition rather than a database
     * error, so none of the connection-class states (the {@code 08xxx} family, which
     * describe the session itself failing) says exactly this; still, consumers that
     * multiplex many {@link SQLException} sources and classify them by state need a
     * non-null label to recognize pool exhaustion without referencing pool classes.
     */
    public static final String SQL_STATE = "HY000";

    /**
     * Constructs a PoolExhaustedException with no detail message.
     */
    public PoolExhaustedException() {
    }

    /**
     * Constructs a PoolExhaustedException with the specified detail message. The
     * exception carries {@link #SQL_STATE} as its SQL state.
     *
     * @param reason the detail message
     */
    public PoolExhaustedException(String reason) {
        super(reason, SQL_STATE);
    }

    /**
     * Constructs a PoolExhaustedException with the specified cause.
     *
     * @param cause the cause of this exception
     */
    public PoolExhaustedException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a PoolExhaustedException with the specified detail message and SQL state.
     *
     * @param reason   the detail message
     * @param SQLState the SQL state code
     */
    public PoolExhaustedException(String reason, String SQLState) {
        super(reason, SQLState);
    }

    /**
     * Constructs a PoolExhaustedException with the specified detail message and cause.
     *
     * @param reason the detail message
     * @param cause  the cause of this exception
     */
    public PoolExhaustedException(String reason, Throwable cause) {
        super(reason, cause);
    }

    /**
     * Constructs a PoolExhaustedException with the specified detail message, SQL state, and vendor code.
     *
     * @param reason     the detail message
     * @param SQLState   the SQL state code
     * @param vendorCode the vendor-specific error code
     */
    public PoolExhaustedException(String reason, String SQLState, int vendorCode) {
        super(reason, SQLState, vendorCode);
    }

    /**
     * Constructs a PoolExhaustedException with the specified detail message, SQL state, and cause.
     *
     * @param reason   the detail message
     * @param sqlState the SQL state code
     * @param cause    the cause of this exception
     */
    public PoolExhaustedException(String reason, String sqlState, Throwable cause) {
        super(reason, sqlState, cause);
    }

    /**
     * Constructs a PoolExhaustedException with the specified detail message, SQL state, vendor code, and cause.
     *
     * @param reason     the detail message
     * @param sqlState   the SQL state code
     * @param vendorCode the vendor-specific error code
     * @param cause      the cause of this exception
     */
    public PoolExhaustedException(String reason, String sqlState, int vendorCode, Throwable cause) {
        super(reason, sqlState, vendorCode, cause);
    }

}

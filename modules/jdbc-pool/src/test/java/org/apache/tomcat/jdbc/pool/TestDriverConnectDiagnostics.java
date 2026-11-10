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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * The diagnostics {@code connectUsingDriver()} emits about the configured JDBC
 * URL - the debug line it writes when instantiating the driver, and the
 * {@link java.sql.SQLException} it throws when a driver declines the URL by
 * returning null - print that URL, which legitimately carries an inline
 * credential (the {@code ;PASSWORD=...} pair an H2 or PostgreSQL style URL
 * accepts, or a {@code ?password=...} query pair). Enabling DEBUG is a standard
 * support step and the returned-null failure is logged at the default level on
 * every attempt, so a credential in the URL must not reach either surface: the
 * same masking that keeps the configuration dump clean
 * ({@code TestPoolProperties}) has to cover them, while the driver still
 * receives the real URL.
 */
class TestDriverConnectDiagnostics extends JdbcPoolBaseTest {

    /**
     * Captures what the pooled connection class logs. The handler goes in
     * before anything runs, and a canary record - written through the very
     * JULI logger the class logs through, not through JUL directly - proves
     * the wiring observes those records before the line under test gets its
     * chance to log.
     */
    private static final class ConnectionLogCapture implements AutoCloseable {
        private static final String CANARY = "pooledconnection-handler-wiring-canary";

        private final Logger logger = Logger.getLogger(PooledConnection.class.getName());
        private final Logger poolLogger = Logger.getLogger(ConnectionPool.class.getName());
        private final Level originalLevel = logger.getLevel();
        private final Level originalPoolLevel = poolLogger.getLevel();
        private final List<LogRecord> records = Collections.synchronizedList(new ArrayList<>());
        private final Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
                // no buffering
            }

            @Override
            public void close() {
                // nothing to release
            }
        };

        ConnectionLogCapture() {
            logger.addHandler(handler);
            logger.setLevel(Level.ALL);
            poolLogger.addHandler(handler);
            poolLogger.setLevel(Level.ALL);
            LogFactory.getLog(PooledConnection.class).error(CANARY);
            if (records.stream().noneMatch(r -> CANARY.equals(r.getMessage()))) {
                close();
                throw new AssertionError("the test handler must observe what this class logs");
            }
            records.clear();
        }

        /**
         * The driver-instantiation line, which must have been logged exactly
         * once - that is what makes an assertion about its content a real
         * assertion rather than a pass-by on a line that never printed.
         */
        String onlyDriverInstantiationLine() {
            List<String> lines = new ArrayList<>();
            for (LogRecord record : records.toArray(new LogRecord[0])) {
                String message = record.getMessage();
                if (message != null && message.startsWith("Instantiating driver using class:")) {
                    lines.add(message);
                }
            }
            Assertions.assertEquals(1, lines.size(),
                    "the first connect must log exactly one driver instantiation line, saw: " + lines);
            return lines.get(0);
        }

        /** Nothing this class logged - message or thrown - may carry the secret. */
        void assertNothingLoggedCarries(String secret) {
            for (LogRecord record : records.toArray(new LogRecord[0])) {
                String message = String.valueOf(record.getMessage());
                String thrown = String.valueOf(record.getThrown());
                Assertions.assertFalse(message.contains(secret) || thrown.contains(secret),
                        "a credential must not reach the log, but this record carries it: "
                                + message + " " + thrown);
            }
        }

        @Override
        public void close() {
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
            poolLogger.removeHandler(handler);
            poolLogger.setLevel(originalPoolLevel);
        }
    }

    @Test
    void aUrlCarryingItsCredentialReachesTheDebugLineMasked() throws Exception {
        String secret = "S3cret-In-The-Url";
        // A fresh database on the test server, created by this very connect
        // (-ifNotExists) under the credential the URL embeds, so the pool's
        // borrow genuinely succeeds and the line under test is the only thing
        // the failure paths could also have printed.
        String credentialUrl = getUrl() + "_driver_log;USER=sa;PASSWORD=" + secret;

        try (ConnectionLogCapture log = new ConnectionLogCapture()) {
            DataSource ds = newDataSource();
            ds.getPoolProperties().setUrl(credentialUrl);
            ds.getPoolProperties().setPassword(secret);
            ds.getPoolProperties().setInitialSize(0);
            ds.createPool();
            try (Connection c = ds.getConnection()) {
                Assertions.assertTrue(c.isValid(1), "the pool must genuinely connect through this URL");
            }

            String line = log.onlyDriverInstantiationLine();
            Assertions.assertTrue(line.contains("[url=" + getUrl() + "_driver_log;USER=sa;PASSWORD=********]"),
                    "the URL must stay readable with only its credential masked, was: " + line);
            log.assertNothingLoggedCarries(secret);
        }
    }

    @Test
    void aUrlCarryingNoCredentialStillReachesTheDebugLineWhole() throws Exception {
        try (ConnectionLogCapture log = new ConnectionLogCapture()) {
            DataSource ds = newDataSource();
            ds.getPoolProperties().setInitialSize(0);
            ds.createPool();
            try (Connection c = ds.getConnection()) {
                Assertions.assertTrue(c.isValid(1), "the pool must connect");
            }

            String line = log.onlyDriverInstantiationLine();
            Assertions.assertTrue(line.contains("[url=" + getUrl() + "]"),
                    "a URL with nothing to mask must print exactly as configured, was: " + line);
        }
    }

    /**
     * A driver that answers every URL the way the JDBC contract lets a driver
     * answer one it does not recognise: {@code connect} returns null. This is
     * the persistent-misconfiguration case that makes the returned-null failure
     * recur on every attempt, so it is where that failure's message reaches the
     * log.
     */
    public static class NullReturningDriver implements java.sql.Driver {
        @Override
        public Connection connect(String url, java.util.Properties info) {
            return null;
        }

        @Override
        public boolean acceptsURL(String url) {
            return true;
        }

        @Override
        public java.sql.DriverPropertyInfo[] getPropertyInfo(String url, java.util.Properties info) {
            return new java.sql.DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 0;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
            throw new java.sql.SQLFeatureNotSupportedException();
        }
    }

    public static class FailingDriver implements java.sql.Driver {
        @Override
        public Connection connect(String url, java.util.Properties info) throws SQLException {
            throw new SQLException("unable to connect to " + url);
        }

        @Override
        public boolean acceptsURL(String url) {
            return true;
        }

        @Override
        public java.sql.DriverPropertyInfo[] getPropertyInfo(String url, java.util.Properties info) {
            return new java.sql.DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 0;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
            throw new java.sql.SQLFeatureNotSupportedException();
        }
    }

    @Test
    void aDriverFailureMessageIsNotLoggedWithItsCredential() throws Exception {
        String secret = "S3cret-In-The-Driver-Failure";
        String url = "jdbc:failing:thin:@dbhost:1521:sid?user=appuser;PASSWORD=" + secret;
        PoolProperties properties = new PoolProperties();
        properties.setUrl(url);
        properties.setDriverClassName(FailingDriver.class.getName());
        properties.setJmxEnabled(false);
        properties.setInitialSize(0);
        properties.setTimeBetweenEvictionRunsMillis(-1);
        DataSource ds = manage(new DataSource(properties));

        try (ConnectionLogCapture log = new ConnectionLogCapture()) {
            Assertions.assertThrows(SQLException.class, ds::getConnection,
                    "the driver failure must be reported");
            log.assertNothingLoggedCarries(secret);
        }
    }

    @Test
    void aDriverThatDeclinesTheUrlIsReportedWithTheUrlMasked() throws Exception {
        // The failure is the borrow's own SQLException and the pool logs it at
        // the default level, so a driver declining a credential-bearing URL
        // must not have that credential written into the message.
        String secret = "S3cret-In-The-Declined-Url";
        PoolProperties properties = new PoolProperties();
        properties.setUrl("jdbc:declined:thin:@dbhost:1521:sid?user=appuser&password=" + secret);
        properties.setDriverClassName(NullReturningDriver.class.getName());
        properties.setJmxEnabled(false);
        properties.setInitialSize(0);
        properties.setTimeBetweenEvictionRunsMillis(-1);
        PooledConnection con = new PooledConnection(properties, new ConnectionPool(properties));

        SQLException thrown = Assertions.assertThrows(SQLException.class, con::connect);
        String message = thrown.getMessage();
        Assertions.assertTrue(message.contains("password=********"),
                "the pool's own failure message must mask the URL, was: " + message);
        Assertions.assertTrue(message.contains("jdbc:declined:thin:@dbhost:1521:sid?user=appuser"),
                "the URL must stay diagnosable with only its credential masked, was: " + message);
        Assertions.assertFalse(message.contains(secret),
                "the declined-URL failure must not carry the credential, was: " + message);
    }
}

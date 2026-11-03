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
 * The debug line {@code connectUsingDriver()} writes when it instantiates the
 * configured driver prints the JDBC URL of the configuration, and enabling
 * DEBUG on this package is a standard support step. A pool whose configuration
 * carries its credential inside the URL - the {@code ;PASSWORD=...} pair an H2
 * or PostgreSQL style URL accepts - must therefore not have that credential
 * written to the log: the same masking that keeps the configuration dump
 * clean ({@code TestPoolProperties}) has to cover this line too, while the
 * driver itself still receives the real URL.
 */
class TestDriverInstantiationLog extends JdbcPoolBaseTest {

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
        private final Level originalLevel = logger.getLevel();
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
}

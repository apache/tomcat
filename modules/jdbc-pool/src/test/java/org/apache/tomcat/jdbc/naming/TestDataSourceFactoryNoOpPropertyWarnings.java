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
package org.apache.tomcat.jdbc.naming;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.Reference;
import javax.naming.StringRefAddr;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.DataSourceFactory;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;

/**
 * The factory tells the operator when a property it accepts has no effect.
 * {@code poolPreparedStatements} and {@code maxOpenPreparedStatements} get a
 * "is not a valid setting, it will have no effect." warning; the two other
 * accepted no-ops were silently swallowed: {@code
 * accessToUnderlyingConnectionAllowed}, whose setter in
 * {@link org.apache.tomcat.jdbc.pool.PoolProperties} is a literal NOOP so a
 * configured {@code false} is discarded while the getter keeps reporting
 * {@code true}, and {@code numTestsPerEvictionRun}, documented in
 * {@link org.apache.tomcat.jdbc.pool.PoolConfiguration} as "Property not
 * used" and read by no eviction code. Both must produce the same warning as
 * the established pair, through every route that reaches the parser.
 *
 * <p>Configuration only - no database and no pool is needed to pin what the
 * factory logs.</p>
 */
class TestDataSourceFactoryNoOpPropertyWarnings {

    /** The established wording, as used for poolPreparedStatements. */
    private static String noEffectWarning(String property) {
        return property + " is not a valid setting, it will have no effect.";
    }

    /**
     * Captures what the factory class logs. The handler goes in before
     * anything runs, and a canary record - written through the very JULI
     * logger the factory logs through, not through JUL directly - proves the
     * wiring observes those records before anything that must be reported or
     * stay quiet gets its chance to log.
     */
    private static final class FactoryLogCapture implements AutoCloseable {
        private static final String CANARY = "datasourcefactory-handler-wiring-canary";

        private final Logger logger = Logger.getLogger(DataSourceFactory.class.getName());
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

        FactoryLogCapture() {
            logger.addHandler(handler);
            logger.setLevel(Level.ALL);
            LogFactory.getLog(DataSourceFactory.class).warn(CANARY);
            if (records.stream().noneMatch(r -> CANARY.equals(r.getMessage()))) {
                close();
                throw new AssertionError("the test handler must observe what the factory logs");
            }
        }

        void assertWarnedNoEffect(String property) {
            String expected = noEffectWarning(property);
            boolean seen = records.stream().anyMatch(
                    r -> r.getLevel().intValue() == Level.WARNING.intValue() && expected.equals(r.getMessage()));
            Assertions.assertTrue(seen, "expected the warning \"" + expected + "\", captured: " + records);
        }

        void assertNothingReportedAsHavingNoEffect() {
            LogRecord[] snapshot;
            synchronized (records) {
                snapshot = records.toArray(new LogRecord[0]);
            }
            for (LogRecord record : snapshot) {
                Assertions.assertFalse(record.getLevel().intValue() >= Level.WARNING.intValue()
                                && record.getMessage() != null && record.getMessage()
                                .endsWith(" is not a valid setting, it will have no effect."),
                        "no no-op property was configured, so nothing may be reported as having no effect: "
                                + record.getMessage());
            }
        }

        @Override
        public void close() {
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
        }
    }

    /**
     * A factory that stops where the real one parses the properties, so the
     * full JNDI route can be exercised without creating a pool behind it. The
     * {@code dataSourceObject} parameter type is deliberately the fully
     * qualified {@code javax.sql.DataSource} of the overridden signature: an
     * override that narrows it to the pool's own class would be an overload
     * the factory never calls.
     */
    private static final class ParsingFactory extends DataSourceFactory {
        private PoolConfiguration parsed;

        @Override
        protected javax.sql.DataSource createDataSource(Properties properties, Context context, boolean xa,
                javax.sql.DataSource dataSourceObject) {
            // exactly what the real implementation does first
            parsed = DataSourceFactory.parsePoolProperties(properties);
            return null;
        }
    }

    @Test
    void accessToUnderlyingConnectionAllowedIsWarnedThatItHasNoEffect() {
        Properties properties = new Properties();
        properties.setProperty("accessToUnderlyingConnectionAllowed", "false");

        PoolConfiguration parsed;
        try (FactoryLogCapture logs = new FactoryLogCapture()) {
            parsed = DataSourceFactory.parsePoolProperties(properties);
            logs.assertWarnedNoEffect("accessToUnderlyingConnectionAllowed");
        }

        // the value is discarded by the NOOP setter exactly as it was before
        // the warning was added: the configuration keeps reporting that access
        // is allowed - which is precisely why the parse must warn about it
        Assertions.assertTrue(parsed.isAccessToUnderlyingConnectionAllowed(),
                "the hard-wired answer must not change, only get reported");
    }

    @Test
    void numTestsPerEvictionRunIsWarnedThatItHasNoEffect() {
        Properties properties = new Properties();
        properties.setProperty("numTestsPerEvictionRun", "3");

        PoolConfiguration parsed;
        try (FactoryLogCapture logs = new FactoryLogCapture()) {
            parsed = DataSourceFactory.parsePoolProperties(properties);
            logs.assertWarnedNoEffect("numTestsPerEvictionRun");
        }

        // the value is still stored, as it was before the warning, even though
        // no reader of it takes part in any eviction decision
        Assertions.assertEquals(3, parsed.getNumTestsPerEvictionRun());
    }

    @Test
    void bothNoOpPropertiesAreWarnedWhenTheyArriveThroughTheJndiReference() throws Exception {
        ParsingFactory factory = new ParsingFactory();
        Reference ref = new Reference("javax.sql.DataSource", null, null);
        ref.add(new StringRefAddr("accessToUnderlyingConnectionAllowed", "false"));
        ref.add(new StringRefAddr("numTestsPerEvictionRun", "5"));

        try (FactoryLogCapture logs = new FactoryLogCapture()) {
            factory.getObjectInstance(ref, new CompositeName("jdbc/TestDS"), null, null);
            logs.assertWarnedNoEffect("accessToUnderlyingConnectionAllowed");
            logs.assertWarnedNoEffect("numTestsPerEvictionRun");
        }

        Assertions.assertNotNull(factory.parsed, "the reference attributes must reach the parser");
        Assertions.assertEquals(5, factory.parsed.getNumTestsPerEvictionRun(),
                "the configured value must still be applied, as before");
        Assertions.assertTrue(factory.parsed.isAccessToUnderlyingConnectionAllowed(),
                "the discarded value must not start having an effect either");
    }

    @Test
    void anUnparseableNumTestsPerEvictionRunIsWarnedBeforeTheParseFails() {
        Properties properties = new Properties();
        properties.setProperty("numTestsPerEvictionRun", "not-a-number");

        try (FactoryLogCapture logs = new FactoryLogCapture()) {
            Assertions.assertThrows(NumberFormatException.class,
                    () -> DataSourceFactory.parsePoolProperties(properties),
                    "a value that cannot be parsed must still fail the parse, as it did before");
            // the warning reports the property being configured, not the
            // success of parsing it, so it must precede the parse failure
            logs.assertWarnedNoEffect("numTestsPerEvictionRun");
        }
    }

    @Test
    void theEstablishedNoOpWarningsAreUnchanged() {
        // control pinning the message style the two new warnings mirror
        Properties properties = new Properties();
        properties.setProperty("poolPreparedStatements", "true");
        properties.setProperty("maxOpenPreparedStatements", "10");

        try (FactoryLogCapture logs = new FactoryLogCapture()) {
            DataSourceFactory.parsePoolProperties(properties);
            logs.assertWarnedNoEffect("poolPreparedStatements");
            logs.assertWarnedNoEffect("maxOpenPreparedStatements");
        }
    }

    @Test
    void propertiesThatDoSomethingAreNotReportedAsNoOps() {
        Properties properties = new Properties();
        properties.setProperty("maxActive", "7");
        properties.setProperty("url", "jdbc:h2:mem:no-op-warning-check");

        try (FactoryLogCapture logs = new FactoryLogCapture()) {
            PoolConfiguration parsed = DataSourceFactory.parsePoolProperties(properties);
            logs.assertNothingReportedAsHavingNoEffect();
            Assertions.assertEquals(7, parsed.getMaxActive());
            Assertions.assertEquals("jdbc:h2:mem:no-op-warning-check", parsed.getUrl());
        }
    }
}

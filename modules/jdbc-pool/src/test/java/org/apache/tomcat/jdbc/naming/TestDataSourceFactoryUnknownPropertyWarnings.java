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

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.Reference;
import javax.naming.StringRefAddr;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.DataSourceFactory;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.apache.tomcat.jdbc.pool.PoolProperties;

/**
 * The lookup in {@code getObjectInstance} reads the attributes by name, so a
 * name it does not know - a typo like {@code maxactive} next to the real
 * {@code maxActive} - is dropped without a trace and the pool silently starts
 * with defaults. Every such name must be reported as ignored, while the names
 * the JNDI container itself puts into the resource reference
 * ({@code org.apache.naming.ResourceRef}: description, scope, auth, singleton;
 * the delegated factory attribute read by
 * {@code org.apache.naming.factory.FactoryBase}) are container metadata, not
 * pool properties, and must not be reported.
 *
 * <p>The warning tests run through the real {@code getObjectInstance} loop; a
 * parsing-only factory keeps the pool-free ones from creating a pool, and one
 * end-to-end test builds a working pool against the test database.</p>
 */
class TestDataSourceFactoryUnknownPropertyWarnings extends JdbcPoolBaseTest {

    /** The wording the factory reports an unrecognized attribute with. */
    private static final String IGNORED_SUFFIX = " is not a valid property, it will be ignored.";

    private static String ignoredWarning(String name) {
        return name + IGNORED_SUFFIX;
    }

    /**
     * Captures what the factory class logs. The handler goes in before
     * anything runs, and a canary record - written through the very JULI
     * logger the factory logs through, not through JUL directly - proves the
     * wiring observes those records before anything that must be reported or
     * stay quiet gets its chance to log.
     */
    private static final class FactoryLogCapture implements AutoCloseable {
        private static final String CANARY = "datasourcefactory-unknown-property-handler-wiring-canary";

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

        private Set<String> reportedAsIgnored() {
            LogRecord[] snapshot;
            synchronized (records) {
                snapshot = records.toArray(new LogRecord[0]);
            }
            return Arrays.stream(snapshot)
                    .filter(r -> r.getLevel().intValue() == Level.WARNING.intValue() && r.getMessage() != null
                            && r.getMessage().endsWith(IGNORED_SUFFIX))
                    .map(LogRecord::getMessage)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        void assertWarnedIgnoredExactlyOnce(String name) {
            long occurrences;
            synchronized (records) {
                occurrences = records.stream()
                        .filter(r -> r.getLevel().intValue() == Level.WARNING.intValue()
                                && ignoredWarning(name).equals(r.getMessage()))
                        .count();
            }
            Assertions.assertEquals(1, occurrences, "expected exactly one warning about \"" + name +
                    "\", saw " + occurrences + ", captured: " + records);
        }

        void assertNotWarnedIgnored(String name) {
            Assertions.assertFalse(reportedAsIgnored().contains(ignoredWarning(name)),
                    "\"" + name + "\" is a recognized property name and may not be reported as ignored, "
                            + "captured: " + reportedAsIgnored());
        }

        void assertOnlyReportedAsIgnored(String... names) {
            Set<String> expected = new LinkedHashSet<>();
            for (String name : names) {
                expected.add(ignoredWarning(name));
            }
            Assertions.assertEquals(expected, reportedAsIgnored(),
                    "exactly the unrecognized names may be reported as ignored");
        }

        @Override
        public void close() {
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
        }
    }

    /**
     * A factory that stops where the real one parses the properties, so the
     * attribute sweep can be exercised without creating a pool behind it.
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

    private static Reference dataSourceReference() {
        return new Reference("javax.sql.DataSource", null, null);
    }

    @Test
    void typoedAttributeNameIsReportedWhileTheRecognizedNameIsNot() throws Exception {
        ParsingFactory factory = new ParsingFactory();
        Reference ref = dataSourceReference();
        ref.add(new StringRefAddr("maxActive", "20"));
        ref.add(new StringRefAddr("maxactive", "500"));

        try (FactoryLogCapture logs = new FactoryLogCapture()) {
            factory.getObjectInstance(ref, new CompositeName("jdbc/TestDS"), null, null);
            logs.assertWarnedIgnoredExactlyOnce("maxactive");
            logs.assertNotWarnedIgnored("maxActive");
        }

        Assertions.assertNotNull(factory.parsed, "the reference attributes must reach the parser");
        Assertions.assertEquals(20, factory.parsed.getMaxActive(),
                "the recognized name is applied and the typo silently matched nothing");
    }

    @Test
    void everyUnrecognizedNameIsReportedExactlyOnce() throws Exception {
        ParsingFactory factory = new ParsingFactory();
        Reference ref = dataSourceReference();
        ref.add(new StringRefAddr("maxActive", "5"));
        ref.add(new StringRefAddr("maxactive", "500"));
        ref.add(new StringRefAddr("maxTotal", "8"));
        ref.add(new StringRefAddr("validation_query", "SELECT 1"));
        ref.add(new StringRefAddr("initialsize", "3"));

        try (FactoryLogCapture logs = new FactoryLogCapture()) {
            factory.getObjectInstance(ref, new CompositeName("jdbc/TestDS"), null, null);
            logs.assertWarnedIgnoredExactlyOnce("maxactive");
            logs.assertWarnedIgnoredExactlyOnce("maxTotal");
            logs.assertWarnedIgnoredExactlyOnce("validation_query");
            logs.assertWarnedIgnoredExactlyOnce("initialsize");
            logs.assertOnlyReportedAsIgnored("maxactive", "maxTotal", "validation_query", "initialsize");
        }

        Assertions.assertEquals(5, factory.parsed.getMaxActive(),
                "the one recognized name among the noise must still be applied");
    }

    @Test
    void containerManagedAddressesAreNotReportedAsUnknown() throws Exception {
        // The exact addresses a Tomcat Resource reference carries on top of
        // the configured attributes: ResourceRef adds description, scope,
        // auth and singleton, FactoryBase reads factory as an address, and
        // BeanFactory still recognizes the removed forceString.
        ParsingFactory factory = new ParsingFactory();
        Reference ref = dataSourceReference();
        ref.add(new StringRefAddr("factory", "org.apache.tomcat.jdbc.pool.DataSourceFactory"));
        ref.add(new StringRefAddr("singleton", "true"));
        ref.add(new StringRefAddr("scope", "Shareable"));
        ref.add(new StringRefAddr("auth", "Container"));
        ref.add(new StringRefAddr("description", "Test Datasource"));
        ref.add(new StringRefAddr("forceString", "no"));
        // recognized pool properties, including the odd one the pool accepts
        // under a non-bean name
        ref.add(new StringRefAddr("maxActive", "6"));
        ref.add(new StringRefAddr("object_name", "TestPool"));

        try (FactoryLogCapture logs = new FactoryLogCapture()) {
            factory.getObjectInstance(ref, new CompositeName("jdbc/TestDS"), null, null);
            logs.assertOnlyReportedAsIgnored();
        }

        Assertions.assertEquals(6, factory.parsed.getMaxActive(),
                "the pool properties must still be applied next to the container addresses");
    }

    @Test
    void theRealFactoryOnAWorkingReferenceReportsOnlyTheTypo() throws Exception {
        // End to end: a real container-shaped reference that does build a
        // working pool must warn about the typo'd attribute and nothing else,
        // and the typo'd value must not have reached the pool.
        Reference ref = dataSourceReference();
        ref.add(new StringRefAddr("factory", "org.apache.tomcat.jdbc.pool.DataSourceFactory"));
        ref.add(new StringRefAddr("singleton", "true"));
        ref.add(new StringRefAddr("scope", "Shareable"));
        ref.add(new StringRefAddr("auth", "Container"));
        ref.add(new StringRefAddr("description", "Test Datasource"));
        ref.add(new StringRefAddr("jmxEnabled", "false"));
        ref.add(new StringRefAddr("initialSize", "1"));
        ref.add(new StringRefAddr("minIdle", "0"));
        ref.add(new StringRefAddr("driverClassName", "org.h2.Driver"));
        ref.add(new StringRefAddr("url", getUrl()));
        ref.add(new StringRefAddr("username", "sa"));
        ref.add(new StringRefAddr("password", ""));
        ref.add(new StringRefAddr("maxactive", "500"));

        Object result;
        try (FactoryLogCapture logs = new FactoryLogCapture()) {
            result = new DataSourceFactory().getObjectInstance(ref, new CompositeName("jdbc/TestDS"), null, null);
            logs.assertWarnedIgnoredExactlyOnce("maxactive");
            logs.assertOnlyReportedAsIgnored("maxactive");
        }

        Assertions.assertInstanceOf(DataSource.class, result, "the factory must still return the pool");
        DataSource ds = manage((DataSource) result);
        try (Connection c = ds.getConnection();
                ResultSet rs = c.createStatement().executeQuery("SELECT 1")) {
            Assertions.assertTrue(rs.next(), "the pool must really work");
        }
        Assertions.assertEquals(PoolProperties.DEFAULT_MAX_ACTIVE, ds.getPoolProperties().getMaxActive(),
                "the typo'd attribute must stay ignored, not reach the pool by any other route");
    }
}

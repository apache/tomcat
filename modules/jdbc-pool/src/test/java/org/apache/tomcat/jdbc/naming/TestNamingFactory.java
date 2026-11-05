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

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.naming.CompositeName;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.StringRefAddr;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.DataSourceFactory;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.apache.tomcat.jdbc.pool.Validator;
import org.apache.tomcat.jdbc.pool.XADataSource;
import org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport;
import org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx;

/**
 * Expected JNDI/naming factory functionality: a container-style
 * {@code Resource} description (a {@link Reference}) produces a
 * correctly configured, fully working pool; and the generic naming
 * factory applies documented property types to the configured object.
 */
/* Public because the public nested targets instantiated through reflection
   must declare public constructors, which Checkstyle's RedundantModifier
   check only accepts within a public enclosing class. */
public class TestNamingFactory extends JdbcPoolBaseTest {

    private Reference dataSourceReference(String className) {
        Reference ref = new Reference(className, null, null);
        ref.add(new StringRefAddr("driverClassName", "org.h2.Driver"));
        ref.add(new StringRefAddr("url", getUrl()));
        ref.add(new StringRefAddr("username", "sa"));
        ref.add(new StringRefAddr("password", ""));
        ref.add(new StringRefAddr("maxActive", "4"));
        ref.add(new StringRefAddr("initialSize", "1"));
        ref.add(new StringRefAddr("minIdle", "0"));
        return ref;
    }

    /*
     * A property that cannot be applied fails the lookup: the partially
     * configured object must never be returned, for a singleton resource
     * the container caches whatever object the factory returns. Targets
     * that a test must inspect despite the failure self-register in the
     * constructor, which runs before any property is applied.
     */
    private void assertLookupFails(Reference ref, String boundName) {
        Assertions.assertThrows(NamingException.class,
                () -> new GenericNamingResourcesFactory().getObjectInstance(
                        ref, new CompositeName(boundName), null, null));
    }

    @Test
    void referenceProducesWorkingConfiguredPool() throws Exception {
        Name name = new CompositeName("jdbc/FunctionalDS");
        Object result = new DataSourceFactory().getObjectInstance(
                dataSourceReference("javax.sql.DataSource"), name, null, null);

        Assertions.assertInstanceOf(DataSource.class, result,
                "factory must produce a pool DataSource");
        DataSource ds = manage((DataSource) result);
        Assertions.assertEquals(4, ds.getPoolProperties().getMaxActive(),
                "maxActive from the reference must be applied");
        Assertions.assertEquals("sa", ds.getPoolProperties().getUsername());

        try (java.sql.Connection c = ds.getConnection();
                java.sql.ResultSet rs = c.createStatement().executeQuery("SELECT 1")) {
            Assertions.assertTrue(rs.next());
        }
        Assertions.assertEquals(1, poolSessions(),
                "the JNDI-configured pool must reach the real database");
    }

    @Test
    void xaReferenceProducesXaDataSourceType() throws Exception {
        Name name = new CompositeName("jdbc/FunctionalXaDS");
        Object result = new DataSourceFactory().getObjectInstance(
                dataSourceReference("javax.sql.XADataSource"), name, null, null);
        Assertions.assertInstanceOf(XADataSource.class, result,
                "a javax.sql.XADataSource reference must yield the pool's XA type");
        if (result instanceof DataSource) {
            manage((DataSource) result);
        }
    }

    @Test
    void concreteXaPoolClassNameReferenceProducesXaDataSourceType() throws Exception {
        // A resource whose type names the factory's own XA class - the class
        // the factory news for every XA reference - must build that XA pool,
        // where previously only the sibling non-XA concrete class was
        // accepted and this one came back as an unresolvable resource.
        Name name = new CompositeName("jdbc/ConcreteXaDS");
        Object result = new DataSourceFactory().getObjectInstance(
                dataSourceReference("org.apache.tomcat.jdbc.pool.XADataSource"),
                name, null, null);
        Assertions.assertInstanceOf(XADataSource.class, result,
                "the concrete XA class name must yield the pool's XA type");
        if (result instanceof DataSource) {
            manage((DataSource) result);
        }
    }

    /*
     * Boolean attributes are applied strictly: only "true" and "false",
     * ignoring case and surrounding whitespace, configure the property;
     * an empty attribute means "unset" - the PoolProperties default, and
     * for the tri-state defaultAutoCommit/defaultReadOnly the "leave the
     * driver default alone" state - and anything else fails the factory
     * instead of being silently applied as false, the way the numeric
     * attributes already fail on a bad value.
     */

    @Test
    void misspelledBooleanAttributeFailsTheFactory()
            throws Exception {
        Reference ref = dataSourceReference("javax.sql.DataSource");
        // pre-fix this was silently applied as testOnBorrow=false
        ref.add(new StringRefAddr("testOnBorrow", "ture"));

        IllegalArgumentException e = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new DataSourceFactory().getObjectInstance(
                        ref, new CompositeName("jdbc/BadBooleanDS"),
                        null, null),
                "a misspelled boolean value must fail the factory call");
        Assertions.assertTrue(e.getMessage().contains("testOnBorrow"),
                "the failure must name the misconfigured attribute, was: "
                        + e.getMessage());
        Assertions.assertTrue(e.getMessage().contains("ture"),
                "the failure must show the offending value, was: "
                        + e.getMessage());
    }

    @Test
    void misspelledTriStateAttributeFailsTheFactory()
            throws Exception {
        Reference ref = dataSourceReference("javax.sql.DataSource");
        // pre-fix this was silently applied as defaultAutoCommit=FALSE,
        // forcing auto-commit off on every connection of the pool
        ref.add(new StringRefAddr("defaultAutoCommit", "Yes"));

        IllegalArgumentException e = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new DataSourceFactory().getObjectInstance(
                        ref, new CompositeName("jdbc/BadTriStateDS"),
                        null, null),
                "a misspelled tri-state boolean must fail the factory call "
                        + "instead of forcing an explicit value");
        Assertions.assertTrue(e.getMessage().contains("defaultAutoCommit"),
                "the failure must name the misconfigured attribute, was: "
                        + e.getMessage());
        Assertions.assertTrue(e.getMessage().contains("Yes"),
                "the failure must show the offending value, was: "
                        + e.getMessage());
    }

    @Test
    void emptyBooleanAttributeLeavesThePropertyUnset() {
        java.util.Properties attrs = new java.util.Properties();
        attrs.setProperty("defaultAutoCommit", "");
        attrs.setProperty("defaultReadOnly", "");
        // fairQueue defaults to true: pre-fix, the empty attribute forced
        // false and silently changed the pool's behavior
        attrs.setProperty("fairQueue", "");

        PoolConfiguration configured =
                DataSourceFactory.parsePoolProperties(attrs);

        Assertions.assertNull(configured.getDefaultAutoCommit(),
                "an empty tri-state attribute must keep the "
                        + "\"use the driver default\" state");
        Assertions.assertNull(configured.getDefaultReadOnly(),
                "an empty tri-state attribute must keep the "
                        + "\"use the driver default\" state");
        Assertions.assertTrue(configured.isFairQueue(),
                "an empty plain boolean attribute must leave the "
                        + "PoolProperties default in place");
    }

    @Test
    void emptyCredentialUrlAndValidatorAttributesLeaveThePropertiesUnset() {
        // Pre-fix these four attributes stored the blank verbatim even
        // though the factory contract promises a blank leaves the property
        // at its PoolProperties default: a cleared password attribute left
        // the pool authenticating with an empty password entry, a cleared
        // url made every physical connection attempt fail against the
        // driver, and a cleared validatorClassName looked for a class named
        // "" on every pool start and warned of the ClassNotFoundException
        // before falling back to standard validation.
        java.util.Properties attrs = new java.util.Properties();
        attrs.setProperty("username", " ");
        attrs.setProperty("password", "");
        attrs.setProperty("url", "  ");
        attrs.setProperty("validatorClassName", "");

        PoolConfiguration configured =
                DataSourceFactory.parsePoolProperties(attrs);

        Assertions.assertNull(configured.getUsername(),
                "a blank username attribute must leave the driver "
                        + "properties without a user entry");
        Assertions.assertNull(configured.getPassword(),
                "a blank password attribute must leave the driver "
                        + "properties without a password entry");
        Assertions.assertNull(configured.getUrl(),
                "a blank url attribute must leave the url unset");
        Assertions.assertNull(configured.getValidatorClassName(),
                "a blank validatorClassName attribute must keep the "
                        + "standard validation default");
    }

    @Test
    void credentialAttributesApplyVerbatimAndValidatorClassNameAppliesTrimmed() {
        // A credential can legitimately carry surrounding whitespace, so it
        // is stored verbatim; a Java class name never can, so the factory
        // trims it the same way it trims driverClassName.
        java.util.Properties attrs = new java.util.Properties();
        attrs.setProperty("username", " scott ");
        attrs.setProperty("password", " secret ");
        attrs.setProperty("validatorClassName",
                " " + ParsingValidator.class.getName() + " ");

        PoolConfiguration configured =
                DataSourceFactory.parsePoolProperties(attrs);

        Assertions.assertEquals(" scott ", configured.getUsername(),
                "a non-blank username must be stored verbatim");
        Assertions.assertEquals(" secret ", configured.getPassword(),
                "a non-blank password must be stored verbatim");
        Assertions.assertEquals(ParsingValidator.class.getName(),
                configured.getValidatorClassName(),
                "a non-blank validatorClassName must be stored trimmed");
        Assertions.assertInstanceOf(ParsingValidator.class,
                configured.getValidator(),
                "the trimmed class name must be the one loaded");
    }

    @Test
    void emptyDefaultAutoCommitLeavesConnectionsAtTheDriverDefault()
            throws Exception {
        DataSource pool = factoryPoolWithAutoCommit("");
        try (java.sql.Connection c = pool.getConnection()) {
            Assertions.assertTrue(c.getAutoCommit(),
                    "an empty defaultAutoCommit must leave the connections "
                            + "at the driver's own auto-commit default");
        }
    }

    @Test
    void configuredDefaultAutoCommitStillForcesTheStateOnConnections()
            throws Exception {
        DataSource pool = factoryPoolWithAutoCommit("false");
        try (java.sql.Connection c = pool.getConnection()) {
            Assertions.assertFalse(c.getAutoCommit(),
                    "an explicitly configured defaultAutoCommit must still "
                            + "be applied to the connections");
        }
    }

    @Test
    void booleanValuesApplyIgnoringCaseAndSurroundingWhitespace() {
        java.util.Properties attrs = new java.util.Properties();
        attrs.setProperty("testOnBorrow", "TRUE");
        // pre-fix, the trailing space made this false
        attrs.setProperty("testOnReturn", "True ");
        attrs.setProperty("testWhileIdle", " False ");
        attrs.setProperty("removeAbandoned", "false");

        PoolConfiguration configured =
                DataSourceFactory.parsePoolProperties(attrs);

        Assertions.assertTrue(configured.isTestOnBorrow(),
                "\"TRUE\" must be accepted as true");
        Assertions.assertTrue(configured.isTestOnReturn(),
                "\"True \" must be accepted as true, whitespace included");
        Assertions.assertFalse(configured.isTestWhileIdle(),
                "\" False \" must be accepted as false, whitespace included");
        Assertions.assertFalse(configured.isRemoveAbandoned(),
                "the exact lowercase spelling must keep working");
    }

    /*
     * The defaultTransactionIsolation attribute follows the same strict
     * policy: names and the numeric java.sql.Connection constants are
     * accepted ignoring surrounding whitespace, an empty attribute means
     * "unset" - the "leave the driver default alone" state - and anything
     * else fails the parse. Pre-fix an unusable value was silently applied
     * as that unset sentinel, so connections ran at the driver's isolation
     * level while the only trace was a System.err line claiming the
     * attribute had not been set at all.
     */

    @Test
    void isolationLevelNamesApplyIgnoringCaseAndSurroundingWhitespace() {
        java.util.Properties attrs = new java.util.Properties();
        attrs.setProperty("defaultTransactionIsolation", " read_committed ");
        Assertions.assertEquals(java.sql.Connection.TRANSACTION_READ_COMMITTED,
                DataSourceFactory.parsePoolProperties(attrs)
                        .getDefaultTransactionIsolation(),
                "\" read_committed \" must be accepted, whitespace included");

        attrs.setProperty("defaultTransactionIsolation", "NONE");
        Assertions.assertEquals(java.sql.Connection.TRANSACTION_NONE,
                DataSourceFactory.parsePoolProperties(attrs)
                        .getDefaultTransactionIsolation(),
                "the documented name must still apply");
    }

    @Test
    void numericIsolationLevelAppliesIgnoringSurroundingWhitespace() {
        java.util.Properties attrs = new java.util.Properties();
        attrs.setProperty("defaultTransactionIsolation", " 2 ");
        Assertions.assertEquals(2,
                DataSourceFactory.parsePoolProperties(attrs)
                        .getDefaultTransactionIsolation(),
                "the numeric form must be accepted, whitespace included");
    }

    @Test
    void unusableIsolationLevelFailsTheParseNamingTheProperty() {
        java.util.Properties attrs = new java.util.Properties();
        attrs.setProperty("defaultTransactionIsolation", "READ_COMMIT");

        IllegalArgumentException e = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> DataSourceFactory.parsePoolProperties(attrs),
                "an unusable isolation level must fail the parse instead of "
                        + "silently leaving the level at the driver default");
        Assertions.assertTrue(e.getMessage().contains("defaultTransactionIsolation"),
                "the failure must name the misconfigured attribute, was: "
                        + e.getMessage());
        Assertions.assertTrue(e.getMessage().contains("READ_COMMIT"),
                "the failure must show the offending value, was: "
                        + e.getMessage());
        Assertions.assertNotNull(e.getCause(),
                "the parse failure must be kept as the cause");
    }

    @Test
    void emptyIsolationLevelAttributeLeavesThePropertyUnset() {
        java.util.Properties attrs = new java.util.Properties();
        attrs.setProperty("defaultTransactionIsolation", "");

        PoolConfiguration configured =
                DataSourceFactory.parsePoolProperties(attrs);

        Assertions.assertEquals(DataSourceFactory.UNKNOWN_TRANSACTIONISOLATION,
                configured.getDefaultTransactionIsolation(),
                "an empty attribute must keep the \"leave the driver default "
                        + "alone\" state");
    }

    /*
     * The numeric attributes follow the same strict policy as the booleans:
     * surrounding whitespace is ignored, an empty attribute means "unset"
     * (the PoolProperties default) rather than a NumberFormatException for
     * the empty string, and a value that cannot be parsed - or does not fit
     * the property - fails the configuration naming the property, instead of
     * aborting the whole JNDI resource with an exception that only quotes
     * the offending text.
     */

    @Test
    void numericAttributesApplyIgnoringSurroundingWhitespace() {
        java.util.Properties attrs = new java.util.Properties();
        attrs.setProperty("maxActive", " 12 ");
        attrs.setProperty("maxAge", " 86400000000 ");

        PoolConfiguration configured =
                DataSourceFactory.parsePoolProperties(attrs);

        Assertions.assertEquals(12, configured.getMaxActive(),
                "\" 12 \" must be accepted, whitespace included");
        Assertions.assertEquals(86400000000L, configured.getMaxAge(),
                "a long attribute must be accepted, whitespace included");
    }

    @Test
    void emptyNumericAttributesLeaveThePropertiesUnset() {
        java.util.Properties attrs = new java.util.Properties();
        attrs.setProperty("maxActive", "");
        attrs.setProperty("maxWait", " ");

        PoolConfiguration configured =
                DataSourceFactory.parsePoolProperties(attrs);

        Assertions.assertEquals(PoolProperties.DEFAULT_MAX_ACTIVE,
                configured.getMaxActive(),
                "an empty numeric attribute must leave the default in place");
        Assertions.assertEquals(new PoolProperties().getMaxWait(),
                configured.getMaxWait(),
                "an all-whitespace numeric attribute must leave the default in place");
    }

    @Test
    void unparseableNumericValueFailsTheParseNamingTheProperty() {
        java.util.Properties attrs = new java.util.Properties();
        attrs.setProperty("maxWait", "12 seconds");

        IllegalArgumentException e = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> DataSourceFactory.parsePoolProperties(attrs));
        Assertions.assertTrue(e.getMessage().contains("maxWait"),
                "the failure must name the misconfigured attribute, was: "
                        + e.getMessage());
        Assertions.assertTrue(e.getMessage().contains("12 seconds"),
                "the failure must show the offending value, was: "
                        + e.getMessage());
        Assertions.assertInstanceOf(NumberFormatException.class, e.getCause(),
                "the parse failure must be kept as the cause");
    }

    @Test
    void numericValueBeyondThePropertyRangeFailsTheParseNamingTheProperty() {
        java.util.Properties attrs = new java.util.Properties();
        // a millisecond value no int setter can hold (~2.7 years)
        attrs.setProperty("minEvictableIdleTimeMillis", "86400000000");

        IllegalArgumentException e = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> DataSourceFactory.parsePoolProperties(attrs));
        Assertions.assertTrue(e.getMessage().contains("minEvictableIdleTimeMillis"),
                "the failure must name the misconfigured attribute, was: "
                        + e.getMessage());
        Assertions.assertTrue(e.getMessage().contains("86400000000"),
                "the failure must show the offending value, was: "
                        + e.getMessage());
    }

    @Test
    void blankStringAttributesLeaveThePropertiesUnset() {
        // The same cleared-template policy the booleans, numerics, the
        // isolation level and dataSourceJNDI follow. Pre-fix a stored empty
        // defaultCatalog reached connection.setCatalog("") on every physical
        // connection - driver-dependent from inert to a per-connect failure -
        // and a blank dataSource aborted the resource with guidance about
        // carrying the object, irrelevant to a value cleared to mean "off".
        java.util.Properties attrs = new java.util.Properties();
        attrs.setProperty("defaultCatalog", "");
        attrs.setProperty("dataSource", " ");
        attrs.setProperty("driverClassName", "");
        attrs.setProperty("jdbcInterceptors", " ");
        attrs.setProperty("validationQuery", "");

        PoolConfiguration configured =
                DataSourceFactory.parsePoolProperties(attrs);

        Assertions.assertNull(configured.getDefaultCatalog(),
                "an empty defaultCatalog must leave the property unset");
        Assertions.assertNull(configured.getDataSource(),
                "a blank dataSource must leave the property unset");
        Assertions.assertNull(configured.getDriverClassName(),
                "a blank driverClassName must leave the property unset");
        Assertions.assertNull(configured.getJdbcInterceptors(),
                "a blank jdbcInterceptors must leave the property unset");
        Assertions.assertNull(configured.getValidationQuery(),
                "a blank validationQuery must leave the property unset,"
                        + " routing validation to isValid()");
    }

    @Test
    void blankDriverClassNameStillBuildsTheDriverManagerFallbackPool()
            throws Exception {
        // Pre-fix, the empty attribute defeated the connect path's documented
        // DriverManager fallback (keyed on the property being null) and
        // aborted the whole resource on loading the empty class.
        Reference ref = new Reference("javax.sql.DataSource", null, null);
        ref.add(new StringRefAddr("jmxEnabled", "false"));
        ref.add(new StringRefAddr("initialSize", "1"));
        ref.add(new StringRefAddr("minIdle", "0"));
        ref.add(new StringRefAddr("url", getUrl()));
        ref.add(new StringRefAddr("username", "sa"));
        ref.add(new StringRefAddr("password", ""));
        ref.add(new StringRefAddr("driverClassName", ""));
        ref.add(new StringRefAddr("jdbcInterceptors", " "));

        DataSource ds = manage((DataSource) new DataSourceFactory()
                .getObjectInstance(ref, new CompositeName("jdbc/BlankDS"),
                        null, null));

        try (java.sql.Connection c = ds.getConnection();
                java.sql.ResultSet rs = c.createStatement().executeQuery("SELECT 1")) {
            Assertions.assertTrue(rs.next(),
                    "the pool must build and connect through the fallback");
        }
    }

    @Test
    void driverClassNameAppliesIgnoringSurroundingWhitespace() {
        // A Java class name carries no meaningful surrounding whitespace, so
        // the padded shape a line-wrapped XML attribute yields must load the
        // class it names, like the padded numeric and boolean spellings
        // already do. Pre-fix only the blank test trimmed and the padded
        // value went to the class loader verbatim.
        java.util.Properties attrs = new java.util.Properties();
        attrs.setProperty("driverClassName", " org.h2.Driver ");

        Assertions.assertEquals("org.h2.Driver",
                DataSourceFactory.parsePoolProperties(attrs).getDriverClassName(),
                "a padded class name must be applied trimmed");
    }

    @Test
    void interceptorListWithRedundantSeparatorsStillBuildsThePool()
            throws Exception {
        // Pre-fix, one stray doubled separator - or one whitespace-only
        // segment - made createPool() load the generated class "...
        // pool.interceptor." and abort the whole resource with a
        // SQLException whose message was null; the identical separator
        // dropped at the end of the list was tolerated.
        Reference ref = new Reference("javax.sql.DataSource", null, null);
        ref.add(new StringRefAddr("jmxEnabled", "false"));
        ref.add(new StringRefAddr("initialSize", "1"));
        ref.add(new StringRefAddr("minIdle", "0"));
        ref.add(new StringRefAddr("url", getUrl()));
        ref.add(new StringRefAddr("username", "sa"));
        ref.add(new StringRefAddr("password", ""));
        ref.add(new StringRefAddr("jdbcInterceptors",
                " ; StatementFinalizer;;SlowQueryReport ; "));

        DataSource ds = manage((DataSource) new DataSourceFactory()
                .getObjectInstance(ref, new CompositeName("jdbc/SemiDS"),
                        null, null));

        try (java.sql.Connection c = ds.getConnection();
                java.sql.ResultSet rs = c.createStatement().executeQuery("SELECT 1")) {
            Assertions.assertTrue(rs.next(),
                    "the pool must build and borrow with its named chain");
        }
    }

    @Test
    void aConfiguredCatalogIsStoredUntrimmed() {
        // The counterpart: a catalog name is a database identifier, so only
        // the blank test trims - a configured name keeps its exact spelling.
        java.util.Properties attrs = new java.util.Properties();
        attrs.setProperty("defaultCatalog", " My Catalog ");

        Assertions.assertEquals(" My Catalog ",
                DataSourceFactory.parsePoolProperties(attrs).getDefaultCatalog(),
                "a configured catalog name must be stored verbatim");
    }

    @Test
    void blankObjectNameLeavesThePoolAtItsGeneratedName() {
        // A cleared object_name attribute must not reach the pool as an
        // empty quoted name - the JMX object name would carry an empty name
        // component where the property being unset lets the pool name
        // itself.
        java.util.Properties attrs = new java.util.Properties();
        attrs.setProperty("object_name", " ");

        String name = DataSourceFactory.parsePoolProperties(attrs).getName();

        Assertions.assertNotEquals("\"\"", name,
                "a blank object_name must not become an empty quoted name");
        Assertions.assertTrue(name.startsWith("Tomcat Connection Pool["),
                "an unset object_name must leave the generated pool name,"
                        + " was: " + name);
    }

    @Test
    void aConfiguredObjectNameIsQuotedButStoredVerbatim() {
        // The counterpart, like the catalog: the name is quoted precisely so
        // that "My Pool" survives the JMX object name, and only the blank
        // test trims - a configured name keeps its exact spelling.
        java.util.Properties attrs = new java.util.Properties();
        attrs.setProperty("object_name", " My Pool ");

        Assertions.assertEquals("\" My Pool \"",
                DataSourceFactory.parsePoolProperties(attrs).getName(),
                "a configured pool name must be quoted but stored verbatim");
    }

    @Test
    void aContainerShapedObjectNameIsUsedVerbatim() throws Exception {
        // The container passes this name to a pool that does not name
        // itself. The factory stores it through ObjectName.quote(), the way
        // it stores every configured name, and nothing else may touch it:
        // the pool name, the registration name and the statistics key must
        // all carry that one stored form.
        String containerName = "Catalina/localhost/app1/jdbc/DS";
        String storedName = ObjectName.quote(containerName);

        Reference ref = dataSourceReference("javax.sql.DataSource");
        ref.add(new StringRefAddr("jmxEnabled", "false"));
        ref.add(new StringRefAddr("object_name", containerName));
        ref.add(new StringRefAddr("jdbcInterceptors", "SlowQueryReport"));

        DataSource ds = manage((DataSource) new DataSourceFactory().getObjectInstance(
                ref, new CompositeName("jdbc/DS"), null, null));

        Assertions.assertEquals(storedName, ds.getPoolProperties().getName(),
                "the container-passed name must be the pool name, only the storage quote applied");

        try (java.sql.Connection c = ds.getConnection();
                java.sql.ResultSet rs = c.createStatement().executeQuery("SELECT 1")) {
            Assertions.assertTrue(rs.next());
        }

        Assertions.assertEquals(storedName, ds.getPool().getRegistrationName(),
                "the pool must register under the container-passed name");
        ConcurrentHashMap<String, SlowQueryReport.QueryStats> stats =
                SlowQueryReport.getPoolStats(storedName);
        Assertions.assertNotNull(stats,
                "the statistics registry must key the pool by its container name");
        Assertions.assertTrue(stats.containsKey("SELECT 1"),
                "the executed query must be recorded under the container name");
    }

    /* A factory-built pool carrying a container-shaped object_name. */
    private DataSource containerNamedPool(String containerName) throws Exception {
        Reference ref = dataSourceReference("javax.sql.DataSource");
        ref.add(new StringRefAddr("jmxEnabled", "false"));
        ref.add(new StringRefAddr("object_name", containerName));
        ref.add(new StringRefAddr("jdbcInterceptors",
                "SlowQueryReportJmx(notifyPool=false,threshold=-1)"));
        return manage((DataSource) new DataSourceFactory().getObjectInstance(
                ref, new CompositeName("jdbc/DS"), null, null));
    }

    @Test
    void twoContainerNamedPoolsKeepSeparateRegistries() throws Exception {
        // The two-webapp case at pool level: one JNDI name, two contexts, so
        // two generated names of the container shape. Nothing may merge the
        // pools: each keeps its own statistics registry and its own JMX
        // registration, where the counter names of unnamed pools could once
        // collide.
        String name1 = "Catalina/localhost/app1/jdbc/DS";
        String name2 = "Catalina/localhost/app2/jdbc/DS";
        String stored1 = ObjectName.quote(name1);
        String stored2 = ObjectName.quote(name2);

        DataSource ds1 = containerNamedPool(name1);
        DataSource ds2 = containerNamedPool(name2);

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname1 = new ObjectName("tomcat.jdbc:type="
                + SlowQueryReportJmx.class.getName() + ",name=" + stored1);
        ObjectName oname2 = new ObjectName("tomcat.jdbc:type="
                + SlowQueryReportJmx.class.getName() + ",name=" + stored2);

        try (java.sql.Connection c1 = ds1.getConnection();
                java.sql.Connection c2 = ds2.getConnection()) {
            Assertions.assertTrue(server.isRegistered(oname1),
                    "the first pool must publish its interceptor MBean under its own name");
            Assertions.assertTrue(server.isRegistered(oname2),
                    "the second pool must publish its interceptor MBean under its own name");
            Assertions.assertEquals(stored1, ds1.getPool().getRegistrationName());
            Assertions.assertEquals(stored2, ds2.getPool().getRegistrationName());
        }

        ConcurrentHashMap<String, SlowQueryReport.QueryStats> stats1 =
                SlowQueryReport.getPoolStats(stored1);
        ConcurrentHashMap<String, SlowQueryReport.QueryStats> stats2 =
                SlowQueryReport.getPoolStats(stored2);
        Assertions.assertNotNull(stats1);
        Assertions.assertNotNull(stats2);
        Assertions.assertSame(stats1, SlowQueryReport.getPoolStats(stored1),
                "the statistics of one name must stay one registry");
        Assertions.assertNotSame(stats1, stats2,
                "the two container names must not share one statistics registry");
    }

    /* A factory-built pool, H2-backed, with one extra attribute. */
    private DataSource factoryPoolWithAutoCommit(String value)
            throws Exception {
        Reference ref = dataSourceReference("javax.sql.DataSource");
        ref.add(new StringRefAddr("jmxEnabled", "false"));
        ref.add(new StringRefAddr("defaultAutoCommit", value));
        return manage((DataSource) new DataSourceFactory().getObjectInstance(
                ref, new CompositeName("jdbc/AutoCommitDS"), null, null));
    }

    @Test
    void namingFactoryAppliesDocumentedPropertyTypes() throws Exception {
        Reference ref = new Reference(NamingTarget.class.getName(), null, null);
        ref.add(new StringRefAddr("name", "configured"));
        ref.add(new StringRefAddr("count", "7"));
        ref.add(new StringRefAddr("ratio", "1234567890"));
        ref.add(new StringRefAddr("enabled", "true"));

        Object result = new org.apache.tomcat.jdbc.naming.GenericNamingResourcesFactory()
                .getObjectInstance(ref, new CompositeName("test/target"), null, null);
        Assertions.assertInstanceOf(NamingTarget.class, result);
        NamingTarget target = (NamingTarget) result;
        Assertions.assertEquals("configured", target.getName());
        Assertions.assertEquals(7, target.getCount());
        Assertions.assertEquals(1234567890L, target.getRatio());
        Assertions.assertTrue(target.isEnabled());
    }

    @Test
    void namingFactoryAppliesTheReferenceImplementationTypes()
            throws Exception {
        // BeanFactory converts all of these; the generic factory must not
        // drop properties that the reference implementation configures
        Reference ref = new Reference(WideTarget.class.getName(), null, null);
        ref.add(new StringRefAddr("small", "7"));
        ref.add(new StringRefAddr("tiny", "-3"));
        ref.add(new StringRefAddr("ratio", "1.5"));
        ref.add(new StringRefAddr("precise", "0.25"));
        ref.add(new StringRefAddr("initial", "configured"));
        ref.add(new StringRefAddr("boxed", "x"));

        Object result = new GenericNamingResourcesFactory().getObjectInstance(
                ref, new CompositeName("test/wide"), null, null);
        Assertions.assertInstanceOf(WideTarget.class, result);
        WideTarget target = (WideTarget) result;
        Assertions.assertEquals((short) 7, target.getSmall());
        Assertions.assertEquals((byte) -3, target.getTiny());
        Assertions.assertEquals(1.5f, target.getRatio(), 0.0f);
        Assertions.assertEquals(0.25d, target.getPrecise(), 0.0d);
        Assertions.assertEquals('c', target.getInitial());
        Assertions.assertEquals(Character.valueOf('x'), target.getBoxed());
    }

    @Test
    void outOfRangeNumberFailsTheLookupNamingTheProperty() throws Exception {
        Reference ref = new Reference(WideTarget.class.getName(), null, null);
        // no short can hold this: the conversion must fail and, like every
        // other unapplied property, fail the lookup
        ref.add(new StringRefAddr("small", "99999"));

        NamingException e = Assertions.assertThrows(NamingException.class,
                () -> new GenericNamingResourcesFactory().getObjectInstance(
                        ref, new CompositeName("test/widebad"), null, null));
        Assertions.assertTrue(e.getMessage().contains("small"),
                "the failure must name the property, was: " + e.getMessage());
    }

    @Test
    void missingContentIsNotAppliedToAStringSetter() throws Exception {
        Reference ref = new Reference(NamingTarget.class.getName(), null, null);
        // pre-fix the String setter received a fabricated null, wiping the
        // configured default and reporting success; missing content is not
        // a value to apply, to a String setter no less than to a typed one
        NamingTarget.last = null;
        ref.add(new StringRefAddr("name", null));

        assertLookupFails(ref, "test/nullstring");
        NamingTarget target = NamingTarget.last;
        Assertions.assertNotNull(target);
        Assertions.assertEquals("untitled", target.getName(),
                "the default must survive an address with no content");
    }

    @Test
    void traceLoggingDoesNotIncludeThePropertyValue() throws Exception {
        Logger logger = Logger.getLogger(
                GenericNamingResourcesFactory.class.getName());
        Level originalLevel = logger.getLevel();
        final List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
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
        logger.addHandler(handler);
        logger.setLevel(Level.FINEST);
        try {
            Reference ref = new Reference(NamingTarget.class.getName(),
                    null, null);
            ref.add(new StringRefAddr("name", "s3cr3t-value"));

            Object result = new GenericNamingResourcesFactory().getObjectInstance(
                    ref, new CompositeName("test/trace"), null, null);
            Assertions.assertEquals("s3cr3t-value",
                    ((NamingTarget) result).getName(),
                    "the property must still be applied");
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
        }
        for (LogRecord record : records) {
            Assertions.assertFalse(record.getMessage() != null &&
                    record.getMessage().contains("s3cr3t-value"),
                    "the trace line must not log the configured value: "
                            + record.getMessage());
        }
    }

    /** Target with the numeric and character setters BeanFactory supports. */
    public static class WideTarget {
        private short small;
        private byte tiny;
        private float ratio;
        private double precise;
        private char initial;
        private Character boxed;

        public short getSmall() {
            return small;
        }
        public void setSmall(short small) {
            this.small = small;
        }
        public byte getTiny() {
            return tiny;
        }
        public void setTiny(byte tiny) {
            this.tiny = tiny;
        }
        public float getRatio() {
            return ratio;
        }
        public void setRatio(float ratio) {
            this.ratio = ratio;
        }
        public double getPrecise() {
            return precise;
        }
        public void setPrecise(double precise) {
            this.precise = precise;
        }
        public char getInitial() {
            return initial;
        }
        public void setInitial(char initial) {
            this.initial = initial;
        }
        public Character getBoxed() {
            return boxed;
        }
        public void setBoxed(Character boxed) {
            this.boxed = boxed;
        }
    }

    /*
     * An address that carries no content is a value the factory cannot
     * convert, not a value to fabricate: Boolean.valueOf(null or "") is
     * false and InetAddress.getByName(null or "") is the loopback address,
     * so both conversions silently produce something the configuration
     * never asked for. Like the numeric conversions, which throw on null
     * and empty content, these must leave the setter uninvoked and report
     * the property as not applied.
     */

    @Test
    void missingContentIsNotAppliedAsBooleanOrLoopback() throws Exception {
        Logger logger = Logger.getLogger(
                GenericNamingResourcesFactory.class.getName());
        Level originalLevel = logger.getLevel();
        final List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
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
        logger.addHandler(handler);
        logger.setLevel(Level.FINEST);
        try {
            Reference ref = new Reference(ConvertedTarget.class.getName(),
                    null, null);
            // pre-fix these set enabled, checked, flag and marker to false
            // and addr and host to the loopback address, reported as a
            // successful set
            ref.add(new StringRefAddr("enabled", null));
            ref.add(new StringRefAddr("checked", ""));
            // the boxed java.lang.Boolean conversion takes the same branch
            ref.add(new StringRefAddr("flag", null));
            ref.add(new StringRefAddr("marker", ""));
            ref.add(new StringRefAddr("addr", null));
            ref.add(new StringRefAddr("host", ""));

            ConvertedTarget.last = null;
            assertLookupFails(ref, "test/converted");
            ConvertedTarget target = ConvertedTarget.last;
            Assertions.assertNotNull(target,
                    "the object is created before properties are applied");
            for (String property : new String[] { "enabled", "checked",
                    "flag", "marker", "addr", "host" }) {
                Assertions.assertFalse(target.isInvoked(property),
                        "the setter for [" + property + "] must not be "
                                + "invoked with a value fabricated from "
                                + "missing content");
            }
            Assertions.assertFalse(target.isEnabled());
            Assertions.assertFalse(target.isChecked());
            Assertions.assertNull(target.getFlag(),
                    "the default must survive an address with no content");
            Assertions.assertNull(target.getMarker(),
                    "the default must survive an address with no content");
            Assertions.assertNull(target.getAddr(),
                    "the default must survive an address with no content");
            Assertions.assertNull(target.getHost(),
                    "the default must survive an address with no content");
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
        }
        for (String property : new String[] { "enabled", "checked",
                "flag", "marker", "addr", "host" }) {
            boolean warned = false;
            for (LogRecord record : records) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()
                        && record.getMessage() != null
                        && record.getMessage().contains("[" + property + "]")) {
                    warned = true;
                    break;
                }
            }
            Assertions.assertTrue(warned, "failure to apply property ["
                    + property
                    + "] must be reported at warn level or above");
        }
    }

    @Test
    void booleanAndInetAddressContentStillApplies() throws Exception {
        Reference ref = new Reference(ConvertedTarget.class.getName(),
                null, null);
        ref.add(new StringRefAddr("enabled", "true"));
        // an explicit "false" is content and must still be applied
        ref.add(new StringRefAddr("checked", "false"));
        // the boxed java.lang.Boolean conversion takes the same branch
        ref.add(new StringRefAddr("flag", "true"));
        ref.add(new StringRefAddr("marker", "false"));
        ref.add(new StringRefAddr("addr", "127.0.0.1"));
        ref.add(new StringRefAddr("host", "192.0.2.1"));

        Object result = new GenericNamingResourcesFactory().getObjectInstance(
                ref, new CompositeName("test/convertedok"), null, null);
        Assertions.assertInstanceOf(ConvertedTarget.class, result);
        ConvertedTarget target = (ConvertedTarget) result;
        Assertions.assertTrue(target.isEnabled());
        Assertions.assertTrue(target.isInvoked("checked"),
                "\"false\" is configured content, not missing content");
        Assertions.assertFalse(target.isChecked());
        Assertions.assertEquals(Boolean.TRUE, target.getFlag(),
                "explicit content must be applied through the boxed setter");
        Assertions.assertEquals(Boolean.FALSE, target.getMarker(),
                "explicit content must be applied through the boxed setter");
        Assertions.assertEquals(java.net.InetAddress.getByName("127.0.0.1"),
                target.getAddr());
        Assertions.assertEquals(java.net.InetAddress.getByName("192.0.2.1"),
                target.getHost());
    }

    /*
     * Boolean.valueOf() maps everything that is not exactly "true" (ignore
     * case) to false: whitespace-padded and misspelled configuration values
     * were silently applied as false and reported as a successful set. The
     * boolean conversion must accept only content that spells a boolean,
     * like the pool's own DataSourceFactory.parseBoolean() does; anything
     * else must leave the setter uninvoked and be reported as not applied.
     */

    @Test
    void unrecognizedBooleanContentIsNotAppliedAsFalse() throws Exception {
        Logger logger = Logger.getLogger(
                GenericNamingResourcesFactory.class.getName());
        Level originalLevel = logger.getLevel();
        final List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
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
        logger.addHandler(handler);
        logger.setLevel(Level.FINEST);
        try {
            Reference ref = new Reference(ConvertedTarget.class.getName(),
                    null, null);
            // the realistic configuration mistakes: a value padded by an XML
            // line wrap, a synonym and a typo. Pre-fix every one of these
            // was applied as false with no diagnostic.
            ref.add(new StringRefAddr("checked", "Yes"));
            ref.add(new StringRefAddr("flag", "1"));
            ref.add(new StringRefAddr("marker", "ture"));
            // surrounding whitespace around genuine content is still
            // accepted, like DataSourceFactory.parseBoolean() does
            ref.add(new StringRefAddr("enabled", " True "));

            ConvertedTarget.last = null;
            // checked/flag/marker are not applicable, so the lookup fails;
            // enabled=" True " is applied first, verified via the target's
            // self-registration before the failure
            assertLookupFails(ref, "test/badboolean");
            ConvertedTarget target = ConvertedTarget.last;
            Assertions.assertNotNull(target);
            Assertions.assertTrue(target.isEnabled(),
                    "\" True \" is a padded true and must be applied");
            for (String property : new String[] { "checked", "flag",
                    "marker" }) {
                Assertions.assertFalse(target.isInvoked(property),
                        "the setter for [" + property
                                + "] must not be invoked with content that"
                                + " does not spell a boolean");
            }
            Assertions.assertFalse(target.isChecked());
            Assertions.assertNull(target.getFlag(),
                    "the default must survive unrecognized content");
            Assertions.assertNull(target.getMarker(),
                    "the default must survive unrecognized content");
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
        }
        for (String property : new String[] { "checked", "flag",
                "marker" }) {
            boolean warned = false;
            for (LogRecord record : records) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()
                        && record.getMessage() != null
                        && record.getMessage().contains("[" + property + "]")) {
                    warned = true;
                    break;
                }
            }
            Assertions.assertTrue(warned, "failure to apply property ["
                    + property
                    + "] must be reported at warn level or above");
        }
    }

    /** Target with primitive and boxed boolean and InetAddress setters. */
    public static class ConvertedTarget {
        // set by the constructor so a test can inspect the created object
        // even when the factory fails the lookup before returning it
        static ConvertedTarget last;
        private final java.util.Set<String> invoked =
                new java.util.HashSet<>();
        private boolean enabled;
        private boolean checked;
        private Boolean flag;
        private Boolean marker;
        private java.net.InetAddress addr;
        private java.net.InetAddress host;

        public ConvertedTarget() {
            last = this;
        }

        public boolean isInvoked(String name) {
            return invoked.contains(name);
        }
        public boolean isEnabled() {
            return enabled;
        }
        public void setEnabled(boolean enabled) {
            invoked.add("enabled");
            this.enabled = enabled;
        }
        public boolean isChecked() {
            return checked;
        }
        public void setChecked(boolean checked) {
            invoked.add("checked");
            this.checked = checked;
        }
        public Boolean getFlag() {
            return flag;
        }
        public void setFlag(Boolean flag) {
            invoked.add("flag");
            this.flag = flag;
        }
        public Boolean getMarker() {
            return marker;
        }
        public void setMarker(Boolean marker) {
            invoked.add("marker");
            this.marker = marker;
        }
        public java.net.InetAddress getAddr() {
            return addr;
        }
        public void setAddr(java.net.InetAddress addr) {
            invoked.add("addr");
            this.addr = addr;
        }
        public java.net.InetAddress getHost() {
            return host;
        }
        public void setHost(java.net.InetAddress host) {
            invoked.add("host");
            this.host = host;
        }
    }

    @Test
    void unsupportedSetterTypeIsNotInvokedWithNull() throws Exception {
        Reference ref = new Reference(UnsupportedTypeTarget.class.getName(),
                null, null);
        // "props" only matches setProps(Properties) whose parameter type the
        // factory cannot convert a string to. It must not be invoked with a
        // null argument, wiping the configured default.
        ref.add(new StringRefAddr("props", "anything"));

        UnsupportedTypeTarget.last = null;
        assertLookupFails(ref, "test/unsupported");
        UnsupportedTypeTarget target = UnsupportedTypeTarget.last;
        Assertions.assertNotNull(target);
        Assertions.assertFalse(target.isPropsInvoked(),
                "setter with an unsupported parameter type must not be "
                        + "invoked with a null value");
        Assertions.assertEquals(UnsupportedTypeTarget.DEFAULT_PROPS, target.getProps(),
                "the default value must survive an inapplicable property");
    }

    @Test
    void overloadedSetterWithBadValueDoesNotNullOtherField()
            throws Exception {
        Reference ref = new Reference(OverloadedTarget.class.getName(),
                null, null);
        // "port" matches both setPort(int) and setPort(Properties). The
        // numeric conversion of the value fails; the unsupported overload
        // must not be invoked with null instead.
        ref.add(new StringRefAddr("port", "12o"));

        OverloadedTarget.last = null;
        assertLookupFails(ref, "test/overloaded");
        OverloadedTarget target = OverloadedTarget.last;
        Assertions.assertNotNull(target);
        Assertions.assertFalse(target.isPropsInvoked(),
                "the unsupported overload must not be invoked with null when "
                        + "the numeric conversion fails");
        Assertions.assertEquals(-1, target.getPort(),
                "the numeric property must keep its default on a bad value");
    }

    /** Target with a setter of a type the factory cannot convert to. */
    public static class UnsupportedTypeTarget {
        static UnsupportedTypeTarget last;
        static final java.util.Properties DEFAULT_PROPS =
                new java.util.Properties();
        private java.util.Properties props = DEFAULT_PROPS;
        private boolean propsInvoked;

        public UnsupportedTypeTarget() {
            last = this;
        }

        public java.util.Properties getProps() {
            return props;
        }
        public void setProps(java.util.Properties props) {
            this.propsInvoked = true;
            this.props = props;
        }
        public boolean isPropsInvoked() {
            return propsInvoked;
        }
    }

    /** Target with an overloaded setter, one supported and one not. */
    public static class OverloadedTarget {
        static OverloadedTarget last;
        private int port = -1;
        private java.util.Properties props;
        private boolean propsInvoked;

        public OverloadedTarget() {
            last = this;
        }

        public int getPort() {
            return port;
        }
        public void setPort(int port) {
            this.port = port;
        }
        public java.util.Properties getProps() {
            return props;
        }
        public void setPort(java.util.Properties props) {
            this.propsInvoked = true;
            this.props = props;
        }
        public boolean isPropsInvoked() {
            return propsInvoked;
        }
    }

    @Test
    void convertibleSetterOverloadsAreChosenDeterministically() throws Exception {
        Reference ref = new Reference(AmbiguousOverloadTarget.class.getName(),
                null, null);
        ref.add(new StringRefAddr("port", "1527"));
        ref.add(new StringRefAddr("ratio", "1.5"));

        AmbiguousOverloadTarget.last = null;
        Object result = new GenericNamingResourcesFactory().getObjectInstance(
                ref, new CompositeName("test/ambiguous"), null, null);

        Assertions.assertInstanceOf(AmbiguousOverloadTarget.class, result);
        AmbiguousOverloadTarget target = (AmbiguousOverloadTarget) result;
        Assertions.assertEquals("int", target.getPortSetter(),
                "the int overload must win over the long one for an integral value");
        Assertions.assertEquals("double", target.getRatioSetter(),
                "the double overload must win over the float one for a decimal value");
    }

    /** Target with several convertible setters for the same property. */
    public static class AmbiguousOverloadTarget {
        static AmbiguousOverloadTarget last;
        private String portSetter;
        private String ratioSetter;

        public AmbiguousOverloadTarget() {
            last = this;
        }

        public void setPort(int port) {
            portSetter = "int";
        }
        public void setPort(long port) {
            portSetter = "long";
        }
        public String getPortSetter() {
            return portSetter;
        }
        public void setRatio(float ratio) {
            ratioSetter = "float";
        }
        public void setRatio(double ratio) {
            ratioSetter = "double";
        }
        public String getRatioSetter() {
            return ratioSetter;
        }
    }

    @Test
    void reservedReferenceAddressesAreNotAppliedAsProperties()
            throws Exception {
        Reference ref = new Reference(BagTarget.class.getName(), null, null);
        // addresses a ResourceRef carries for a <Resource> element
        ref.add(new StringRefAddr("singleton", "true"));
        ref.add(new StringRefAddr("auth", "Container"));
        ref.add(new StringRefAddr("scope", "Shareable"));
        ref.add(new StringRefAddr("description", "my pool"));
        ref.add(new StringRefAddr("forceString", "ignored"));
        // the factory attribute is routed through the resource property bag
        // by the Digester and arrives as a plain address
        ref.add(new StringRefAddr("factory",
                GenericNamingResourcesFactory.class.getName()));
        // genuine configuration property
        ref.add(new StringRefAddr("databaseName", "sample"));

        Object result = new GenericNamingResourcesFactory().getObjectInstance(
                ref, new CompositeName("test/bag"), null, null);
        Assertions.assertInstanceOf(BagTarget.class, result);
        BagTarget target = (BagTarget) result;
        Assertions.assertEquals(java.util.Collections.singletonMap("databaseName", "sample"),
                target.getBag(),
                "reference metadata must not be applied as bean properties");
    }

    /** Target with a generic setProperty(name, value) fallback. */
    public static class BagTarget {
        private final java.util.Map<String, String> bag =
                new java.util.LinkedHashMap<>();

        public void setProperty(String name, String value) {
            bag.put(name, value);
        }
        public java.util.Map<String, String> getBag() {
            return bag;
        }
    }

    @Test
    void incompatibleSetPropertyOverloadIsNotInvokedAsFallback()
            throws Exception {
        Logger logger = Logger.getLogger(
                GenericNamingResourcesFactory.class.getName());
        Level originalLevel = logger.getLevel();
        final List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
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
        logger.addHandler(handler);
        logger.setLevel(Level.FINEST);
        try {
            Reference ref = new Reference(
                    DecoySetPropertyTarget.class.getName(), null, null);
            // "whatever" matches no setter; only the setProperty overloads
            // could be consulted as the (name, value) fallback, and neither
            // one can accept those arguments
            ref.add(new StringRefAddr("whatever", "v"));

            DecoySetPropertyTarget.last = null;
            assertLookupFails(ref, "test/decoy");
            Assertions.assertFalse(
                    DecoySetPropertyTarget.last.isInvoked(),
                    "a setProperty overload the factory cannot call with the "
                            + "configured name and value must not run");
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
        }
        boolean warned = false;
        for (LogRecord record : records) {
            Assertions.assertFalse(
                    record.getThrown() instanceof IllegalArgumentException,
                    "the fallback must not be recorded by method name only: "
                            + "invoking an unreachable setProperty overload "
                            + "reflectively produced " + record.getMessage()
                            + " with " + record.getThrown());
            if (record.getLevel().intValue() >= Level.WARNING.intValue()
                    && record.getMessage() != null
                    && record.getMessage().contains("[whatever]")) {
                warned = true;
            }
        }
        Assertions.assertTrue(warned,
                "the unapplied property must be reported once, at warn level");
    }

    /**
     * Target whose setProperty overloads cannot accept the (name, value)
     * arguments of the generic fallback. The two overloads cover both
     * recording buckets, boolean and default return type; either one,
     * invoked with two arguments, fails reflectively.
     */
    public static class DecoySetPropertyTarget {
        static DecoySetPropertyTarget last;
        private boolean invoked;

        public DecoySetPropertyTarget() {
            last = this;
        }

        public boolean isInvoked() {
            return invoked;
        }
        public boolean setProperty(String name) {
            invoked = true;
            return true;
        }
        public void setProperty(int index) {
            invoked = true;
        }
    }

    @Test
    void propertyBagFallbackSurvivesIncompatibleOverload()
            throws Exception {
        Reference ref = new Reference(MixedBagTarget.class.getName(),
                null, null);
        ref.add(new StringRefAddr("databaseName", "sample"));

        Object result = new GenericNamingResourcesFactory().getObjectInstance(
                ref, new CompositeName("test/mixedbag"), null, null);
        Assertions.assertInstanceOf(MixedBagTarget.class, result);
        MixedBagTarget target = (MixedBagTarget) result;
        Assertions.assertEquals(java.util.Collections.singletonMap(
                        "databaseName", "sample"),
                target.getBag(),
                "the (name, value) overload must be used as the fallback "
                        + "even when an unreachable overload exists");
        Assertions.assertFalse(target.isIndexInvoked(),
                "the unreachable overload must not be invoked");
    }

    /**
     * Target carrying a usable setProperty(name, value) property bag next
     * to an unrelated setProperty overload. Recording used to match on the
     * method name only, so which of the two overloads the fallback ended
     * up calling depended on the reflection method order.
     */
    public static class MixedBagTarget {
        private final java.util.Map<String, String> bag =
                new java.util.LinkedHashMap<>();
        private boolean indexInvoked;

        public void setProperty(String name, String value) {
            bag.put(name, value);
        }
        public java.util.Map<String, String> getBag() {
            return bag;
        }
        public void setProperty(int index) {
            indexInvoked = true;
        }
        public boolean isIndexInvoked() {
            return indexInvoked;
        }
    }

    @Test
    void hashtableStyleSetPropertyBagIsApplied() throws Exception {
        Reference ref = new Reference(ObjectBagTarget.class.getName(),
                null, null);
        ref.add(new StringRefAddr("databaseName", "sample"));

        Object result = new GenericNamingResourcesFactory().getObjectInstance(
                ref, new CompositeName("test/objectbag"), null, null);
        Assertions.assertInstanceOf(ObjectBagTarget.class, result);
        Assertions.assertEquals(java.util.Collections.singletonMap(
                        "databaseName", "sample"),
                ((ObjectBagTarget) result).getBag(),
                "a setProperty(Object, Object) bag can accept the string "
                        + "name and value and must stay usable");
    }

    /** Target with a Hashtable-style setProperty(Object, Object) bag. */
    public static class ObjectBagTarget {
        private final java.util.Map<Object, Object> bag =
                new java.util.LinkedHashMap<>();

        public void setProperty(Object key, Object value) {
            bag.put(key, value);
        }
        public java.util.Map<Object, Object> getBag() {
            return bag;
        }
    }

    @Test
    void boxedBooleanBagRefusalFailsTheLookup() throws Exception {
        Reference ref = new Reference(RefusingBoxedBagTarget.class.getName(),
                null, null);
        // pre-fix the boxed java.lang.Boolean return landed in the
        // fire-and-forget bucket: the bag's refusal was counted as applied
        // and the unconfigured object was returned
        ref.add(new StringRefAddr("anything", "v"));

        NamingException e = Assertions.assertThrows(NamingException.class,
                () -> new GenericNamingResourcesFactory().getObjectInstance(
                        ref, new CompositeName("test/boxedrefuse"),
                        null, null));
        Assertions.assertTrue(e.getMessage().contains("anything"),
                "the failure must name the refused property, was: "
                        + e.getMessage());
    }

    @Test
    void boxedBooleanBagAcceptanceApplies() throws Exception {
        Reference ref = new Reference(AcceptingBoxedBagTarget.class.getName(),
                null, null);
        ref.add(new StringRefAddr("databaseName", "sample"));

        Object result = new GenericNamingResourcesFactory().getObjectInstance(
                ref, new CompositeName("test/boxedaccept"), null, null);
        Assertions.assertInstanceOf(AcceptingBoxedBagTarget.class, result);
        Assertions.assertEquals(java.util.Collections.singletonMap(
                        "databaseName", "sample"),
                ((AcceptingBoxedBagTarget) result).getBag(),
                "a boxed Boolean bag that returns TRUE must apply like "
                        + "the primitive boolean one");
    }

    /** Property bag whose boxed Boolean return refuses every property. */
    public static class RefusingBoxedBagTarget {
        public Boolean setProperty(@SuppressWarnings("unused") String name,
                @SuppressWarnings("unused") String value) {
            return Boolean.FALSE;
        }
    }

    /** Property bag whose boxed Boolean return accepts every property. */
    public static class AcceptingBoxedBagTarget {
        private final java.util.Map<String, String> bag =
                new java.util.LinkedHashMap<>();

        public Boolean setProperty(String name, String value) {
            bag.put(name, value);
            return Boolean.TRUE;
        }
        public java.util.Map<String, String> getBag() {
            return bag;
        }
    }

    @Test
    void conversionFailureIsNotAbsorbedByThePropertyBag() throws Exception {
        Reference ref = new Reference(
                TypedSetterAndBagTarget.class.getName(), null, null);
        // setCount(int) matches the property but the value does not build an
        // int; pre-fix the permissive bag was still consulted, so the typo
        // landed in the bag, the bean property kept its default and the
        // lookup reported success
        TypedSetterAndBagTarget.last = null;
        ref.add(new StringRefAddr("count", "12o"));

        NamingException e = Assertions.assertThrows(NamingException.class,
                () -> new GenericNamingResourcesFactory().getObjectInstance(
                        ref, new CompositeName("test/mixedbag"),
                        null, null));
        Assertions.assertTrue(e.getMessage().contains("count"),
                "the failure must name the property, was: " + e.getMessage());
        TypedSetterAndBagTarget target = TypedSetterAndBagTarget.last;
        Assertions.assertNotNull(target);
        Assertions.assertEquals(-1, target.getCount());
        Assertions.assertTrue(target.getBag().isEmpty(),
                "the bag must not absorb a value the typed setter rejected, "
                        + "bag was: " + target.getBag());
    }

    /** Target with a typed setter and a permissive property bag. */
    public static class TypedSetterAndBagTarget {
        static TypedSetterAndBagTarget last;
        private int count = -1;
        private final java.util.Map<String, String> bag =
                new java.util.LinkedHashMap<>();

        public TypedSetterAndBagTarget() {
            last = this;
        }

        public void setProperty(String name, String value) {
            bag.put(name, value);
        }
        public void setCount(int count) {
            this.count = count;
        }
        public int getCount() {
            return count;
        }
        public java.util.Map<String, String> getBag() {
            return bag;
        }
    }

    @Test
    void unappliedPropertiesFailTheLookupAndAreReportedAsWarnings() throws Exception {
        Logger logger = Logger.getLogger(
                GenericNamingResourcesFactory.class.getName());
        Level originalLevel = logger.getLevel();
        final List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
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
        logger.addHandler(handler);
        logger.setLevel(Level.FINEST);
        try {
            Reference ref = new Reference(WarnTarget.class.getName(),
                    null, null);
            // "count" matches setCount(int) but the value does not parse
            ref.add(new StringRefAddr("count", "12o"));
            // no setter exists at all
            ref.add(new StringRefAddr("noSuchProperty", "x"));
            // the matched setter throws
            ref.add(new StringRefAddr("boom", "go"));

            // a property that cannot be applied fails the lookup: the
            // half-configured object must not be returned, for a singleton
            // resource the container would cache it for every later lookup
            NamingException e = Assertions.assertThrows(
                    NamingException.class,
                    () -> new GenericNamingResourcesFactory()
                            .getObjectInstance(
                                    ref, new CompositeName("test/warn"),
                                    null, null));
            for (String property : new String[] { "count", "noSuchProperty",
                    "boom" }) {
                Assertions.assertTrue(e.getMessage().contains(property),
                        "the failure must name the unapplied property ["
                                + property + "], was: " + e.getMessage());
            }
            Assertions.assertTrue(
                    e.getMessage().contains(WarnTarget.class.getName()),
                    "the failure must name the resource class, was: "
                            + e.getMessage());
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
        }
        for (String property : new String[] { "count", "noSuchProperty",
                "boom" }) {
            boolean warned = false;
            for (LogRecord record : records) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()
                        && record.getMessage() != null
                        && record.getMessage().contains("[" + property + "]")) {
                    warned = true;
                    break;
                }
            }
            Assertions.assertTrue(warned, "failure to apply property [" + property
                    + "] must be reported at warn level or above");
        }
    }

    /*
     * A failing setter must not write the configured value to the log: the
     * property may well be a password, and the warn level is on by default.
     * The real cause is reported once, by setProperty; the caller must not
     * add a second, misleading "no usable setter found" warning on top.
     */

    @Test
    void failureLogsDoNotEchoConfiguredValues() throws Exception {
        Logger logger = Logger.getLogger(
                GenericNamingResourcesFactory.class.getName());
        Level originalLevel = logger.getLevel();
        final List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
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
        logger.addHandler(handler);
        logger.setLevel(Level.WARNING);
        final String secret = "S3cr3t-Pa55word";
        NamingException thrown;
        try {
            Reference ref = new Reference(WarnTarget.class.getName(),
                    null, null);
            // the matched setter throws with the secret in hand: pre-fix the
            // WARN line echoed the value, and the caller added a second WARN
            // claiming no usable setter had been found
            ref.add(new StringRefAddr("password", secret));

            thrown = Assertions.assertThrows(NamingException.class,
                    () -> new GenericNamingResourcesFactory()
                            .getObjectInstance(
                                    ref, new CompositeName("test/secret"),
                                    null, null));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
        }
        Assertions.assertFalse(thrown.getMessage().contains(secret),
                "the failure must not echo the configured value, was: "
                        + thrown.getMessage());
        int reported = 0;
        for (LogRecord record : records) {
            Assertions.assertFalse(
                    record.getMessage() != null
                            && record.getMessage().contains(secret),
                    "a failing setter must not write the configured value to"
                            + " the log: " + record.getMessage());
            if (record.getLevel().intValue() >= Level.WARNING.intValue()
                    && record.getMessage() != null
                    && record.getMessage().contains("[password]")) {
                reported++;
            }
        }
        Assertions.assertEquals(1, reported,
                "the failing setter must be reported once, at warn level, "
                        + "without a duplicate claim of a missing setter");
    }

    /** Target exercising the property-set failure paths. */
    public static class WarnTarget {
        private int count = -1;

        public int getCount() {
            return count;
        }
        public void setCount(int count) {
            this.count = count;
        }
        public void setBoom(@SuppressWarnings("unused") String value) {
            throw new IllegalStateException("setter always fails");
        }
        public void setPassword(@SuppressWarnings("unused") String password) {
            throw new IllegalStateException("password setter always fails");
        }
    }

    @Test
    void unusableClassNamesFailWithNamingExceptions() throws Exception {
        GenericNamingResourcesFactory factory =
                new GenericNamingResourcesFactory();
        Name refName = new CompositeName("jdbc/test");

        // missing type attribute: a ResourceRef may carry a null class name
        NamingException ne = Assertions.assertThrows(NamingException.class,
                () -> factory.getObjectInstance(
                        new Reference(null, null, null), refName, null, null));
        Assertions.assertTrue(ne.getMessage().contains("type"),
                "message must name the missing type attribute");

        // unknown class: a NamingException naming the class, not a bare
        // ClassNotFoundException with an empty name
        ne = Assertions.assertThrows(NamingException.class,
                () -> factory.getObjectInstance(new Reference(
                        "com.example.NoSuchClass", null, null),
                        refName, null, null));
        Assertions.assertTrue(ne.getMessage().contains("com.example.NoSuchClass"),
                "message must name the missing class");
        Assertions.assertInstanceOf(ClassNotFoundException.class, ne.getRootCause());

        // interface: a NamingException naming the class, not a bare
        // NoSuchMethodException
        ne = Assertions.assertThrows(NamingException.class,
                () -> factory.getObjectInstance(new Reference(
                        "javax.sql.DataSource", null, null),
                        refName, null, null));
        Assertions.assertTrue(ne.getMessage().contains("javax.sql.DataSource"),
                "message must name the unusable class");
        Assertions.assertInstanceOf(ReflectiveOperationException.class,
                ne.getRootCause());
        // a class whose no-argument constructor exists but throws
        ne = Assertions.assertThrows(NamingException.class,
                () -> factory.getObjectInstance(new Reference(
                        ThrowingCtorTarget.class.getName(), null, null),
                        refName, null, null));
        Assertions.assertTrue(ne.getMessage().contains("constructor"),
                "message must report the failing constructor");
        Assertions.assertInstanceOf(java.lang.reflect.InvocationTargetException.class,
                ne.getRootCause());
    }

    /**
     * Validator the trimmed {@code validatorClassName} attribute applied by
     * the parsing tests above must resolve to. Instantiated reflectively by
     * PoolProperties, hence public with a public constructor, the same
     * shape as the naming targets below.
     */
    public static class ParsingValidator implements Validator {

        public ParsingValidator() {
        }

        @Override
        public boolean validate(java.sql.Connection connection, int validateAction) {
            return true;
        }
    }

    /**
     * Target whose no-argument constructor throws. The constructor must
     * keep its explicit public modifier: the factory locates it with
     * reflection (Class.getConstructor() only finds declared public
     * constructors), and the enclosing class is public so Checkstyle's
     * RedundantModifier check does not treat the modifier as redundant.
     */
    public static class ThrowingCtorTarget {
        public ThrowingCtorTarget() {
            throw new IllegalStateException("boom");
        }
    }

    /** Plain configuration target for the generic naming factory. */
    public static class NamingTarget {
        static NamingTarget last;
        private String name = "untitled";
        private int count;
        private long ratio;
        private boolean enabled;

        public NamingTarget() {
            last = this;
        }

        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public int getCount() {
            return count;
        }
        public void setCount(int count) {
            this.count = count;
        }
        public long getRatio() {
            return ratio;
        }
        public void setRatio(long ratio) {
            this.ratio = ratio;
        }
        public boolean isEnabled() {
            return enabled;
        }
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}

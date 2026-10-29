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
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

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
import org.apache.tomcat.jdbc.pool.XADataSource;

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

            Object result = new GenericNamingResourcesFactory()
                    .getObjectInstance(
                            ref, new CompositeName("test/converted"),
                            null, null);
            Assertions.assertInstanceOf(ConvertedTarget.class, result);
            ConvertedTarget target = (ConvertedTarget) result;
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

    /** Target with primitive and boxed boolean and InetAddress setters. */
    public static class ConvertedTarget {
        private final java.util.Set<String> invoked =
                new java.util.HashSet<>();
        private boolean enabled;
        private boolean checked;
        private Boolean flag;
        private Boolean marker;
        private java.net.InetAddress addr;
        private java.net.InetAddress host;

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

        Object result = new GenericNamingResourcesFactory().getObjectInstance(
                ref, new CompositeName("test/unsupported"), null, null);
        Assertions.assertInstanceOf(UnsupportedTypeTarget.class, result);
        UnsupportedTypeTarget target = (UnsupportedTypeTarget) result;
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

        Object result = new GenericNamingResourcesFactory().getObjectInstance(
                ref, new CompositeName("test/overloaded"), null, null);
        Assertions.assertInstanceOf(OverloadedTarget.class, result);
        OverloadedTarget target = (OverloadedTarget) result;
        Assertions.assertFalse(target.isPropsInvoked(),
                "the unsupported overload must not be invoked with null when "
                        + "the numeric conversion fails");
        Assertions.assertEquals(-1, target.getPort(),
                "the numeric property must keep its default on a bad value");
    }

    /** Target with a setter of a type the factory cannot convert to. */
    public static class UnsupportedTypeTarget {
        static final java.util.Properties DEFAULT_PROPS =
                new java.util.Properties();
        private java.util.Properties props = DEFAULT_PROPS;
        private boolean propsInvoked;

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
        private int port = -1;
        private java.util.Properties props;
        private boolean propsInvoked;

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

            Object result = new GenericNamingResourcesFactory()
                    .getObjectInstance(
                            ref, new CompositeName("test/decoy"), null, null);
            Assertions.assertInstanceOf(DecoySetPropertyTarget.class, result);
            Assertions.assertFalse(
                    ((DecoySetPropertyTarget) result).isInvoked(),
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
        private boolean invoked;

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
    void propertySetFailuresAreReportedAsWarnings() throws Exception {
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

            Object result = new GenericNamingResourcesFactory()
                    .getObjectInstance(
                            ref, new CompositeName("test/warn"), null, null);
            Assertions.assertInstanceOf(WarnTarget.class, result);
            // the half-configured object is still returned, as documented
            Assertions.assertEquals(-1, ((WarnTarget) result).getCount());
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
        private String name;
        private int count;
        private long ratio;
        private boolean enabled;

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

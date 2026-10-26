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
import org.apache.tomcat.jdbc.pool.XADataSource;

/**
 * Expected JNDI/naming factory functionality: a container-style
 * {@code Resource} description (a {@link Reference}) produces a
 * correctly configured, fully working pool; and the generic naming
 * factory applies documented property types to the configured object.
 */
class TestNamingFactory extends JdbcPoolBaseTest {

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
        public void setBoom(String value) {
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

    /** Target whose no-argument constructor throws. */
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

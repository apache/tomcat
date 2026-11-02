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
package org.apache.tomcat.jdbc.pool.jmx;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.apache.tomcat.jdbc.pool.PoolUtilities;

/**
 * The pool's own MBean is a second live-configuration surface: an operator writes a credential there without ever
 * touching the data source's bean. A credential lives in two places - the configuration property, and the entry of the
 * same name among the database properties that {@link org.apache.tomcat.jdbc.pool.PooledConnection} hands the driver -
 * and the connect path lets a non-null credential override the database properties while a removed one is left with
 * nothing to override. So a write through the MBean had to reach both places, as a write through the data source does.
 * It reached only one: an operator who cleared the password there found that connections the pool established
 * afterwards still went out with it, because the entry among the database properties was what authenticated them.
 * <p>
 * The pool here is configured the way the complaint is configured in practice - the credential sits among the
 * database properties, with no top-level property overriding it. The driver records the properties it is handed and
 * then connects with the test database's own credentials, so that the assertions are about what the pool offers the
 * driver rather than about what the database would have accepted.
 */
class TestJmxCredentialWrites extends JdbcPoolBaseTest {

    /* A published-MBean pool that connects with a credential held in the database properties. */
    private DataSource poolWithMBean() throws Exception {
        RecordingDriver.lastConnectInfo.set(null);
        DataSource ds = newDataSource();
        ds.setDriverClassName(RecordingDriver.class.getName());
        PoolConfiguration properties = ds.getPoolProperties();
        properties.setJmxEnabled(true);
        properties.setInitialSize(0);
        Properties dbProperties = new Properties();
        dbProperties.setProperty(PoolUtilities.PROP_USER, "alice");
        dbProperties.setProperty(PoolUtilities.PROP_PASSWORD, "first-secret");
        dbProperties.setProperty("applicationName", "pool-jmx-test");
        // The connect path prefers the top level property whenever it is there
        // at all, so take the fixture's own away - and do it first, because a
        // credential setter is what owns the entry of the same name and a
        // cleared credential takes its entry along with it.
        properties.setUsername(null);
        properties.setPassword(null);
        properties.setDbProperties(dbProperties);
        ds.createPool();
        Assertions.assertNotNull(ds.getPool().getJmxPool(),
                "precondition: the fixture must have published the pool's own MBean");
        return ds;
    }

    /*
     * What the next established connection is handed. A pooled connection keeps
     * the credential it first connected with, so the connection that answered
     * with the old credential has to leave the pool before the new state can be
     * observed - which is also the remedy the credential setters document.
     */
    private static Properties establishFreshConnection(DataSource ds) {
        ds.purge();
        try (Connection con = ds.getConnection()) {
            Assertions.assertNotNull(con);
        } catch (SQLException x) {
            throw new AssertionError(x);
        }
        Properties handed = RecordingDriver.lastConnectInfo.get();
        Assertions.assertNotNull(handed, "precondition: the driver must have been called");
        return handed;
    }

    @Test
    void aPasswordClearedThroughThePoolMBeanReachesTheDriverAsNoPassword() throws Exception {
        DataSource ds = poolWithMBean();
        Assertions.assertEquals("first-secret", establishFreshConnection(ds).getProperty(PoolUtilities.PROP_PASSWORD),
                "precondition: the credential among the database properties is what connects");

        ds.getPool().getJmxPool().setPassword(null);

        Assertions.assertNull(ds.getPoolProperties().getPassword(),
                "precondition: the top level property was already empty");
        Properties handed = establishFreshConnection(ds);
        Assertions.assertFalse(handed.containsKey(PoolUtilities.PROP_PASSWORD),
                "the password an operator cleared through the pool's own MBean must not go on "
                        + "authenticating connections the pool establishes afterwards");
        Assertions.assertEquals("alice", handed.getProperty(PoolUtilities.PROP_USER),
                "clearing the password must leave the user name alone");
        Assertions.assertEquals("pool-jmx-test", handed.getProperty("applicationName"),
                "a credential write must leave the other database properties as they were");
    }

    @Test
    void aUserNameClearedThroughThePoolMBeanReachesTheDriverAsNoUserName() throws Exception {
        DataSource ds = poolWithMBean();
        Assertions.assertEquals("alice", establishFreshConnection(ds).getProperty(PoolUtilities.PROP_USER),
                "precondition: the user name among the database properties is what connects");

        ds.getPool().getJmxPool().setUsername(null);

        Properties handed = establishFreshConnection(ds);
        Assertions.assertFalse(handed.containsKey(PoolUtilities.PROP_USER),
                "the user name an operator cleared through the pool's own MBean must not go on "
                        + "identifying connections the pool establishes afterwards");
        Assertions.assertEquals("first-secret", handed.getProperty(PoolUtilities.PROP_PASSWORD),
                "clearing the user name must leave the password alone");
    }

    @Test
    void aPasswordRotatedThroughThePoolMBeanReplacesTheEntryTheDriverIsHanded() throws Exception {
        DataSource ds = poolWithMBean();
        Assertions.assertEquals("first-secret", establishFreshConnection(ds).getProperty(PoolUtilities.PROP_PASSWORD),
                "precondition: the credential among the database properties is what connects");

        ds.getPool().getJmxPool().setPassword("rotated-secret");

        Properties handed = establishFreshConnection(ds);
        Assertions.assertEquals("rotated-secret", handed.getProperty(PoolUtilities.PROP_PASSWORD),
                "a password written through the pool's MBean must be the one new connections are established with");
        Assertions.assertEquals("rotated-secret",
                ds.getPoolProperties().getDbProperties().getProperty(PoolUtilities.PROP_PASSWORD),
                "and it must replace the entry rather than sit beside it: a superseded password left "
                        + "among the database properties is a credential that can still be handed out "
                        + "the moment the top level property stops overriding it");
    }

    /**
     * Records the properties each connect was offered and then delegates to the real driver with the test database's
     * own credentials, so that a test can look at what the pool decided to hand over without the database voting on
     * whether it approves of it.
     */
    public static class RecordingDriver implements Driver {

        static final AtomicReference<Properties> lastConnectInfo = new AtomicReference<>();

        private final Driver delegate = new org.h2.Driver();

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            Properties handed = new Properties();
            handed.putAll(info);
            lastConnectInfo.set(handed);
            Properties forDelegate = new Properties();
            forDelegate.putAll(info);
            forDelegate.setProperty(PoolUtilities.PROP_USER, "sa");
            forDelegate.setProperty(PoolUtilities.PROP_PASSWORD, "");
            return delegate.connect(url, forDelegate);
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return delegate.acceptsURL(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
            return delegate.getPropertyInfo(url, info);
        }

        @Override
        public int getMajorVersion() {
            return delegate.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return delegate.getMinorVersion();
        }

        @Override
        public boolean jdbcCompliant() {
            return delegate.jdbcCompliant();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }
    }
}

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

import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;

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
 * driver rather than about what the database would have accepted. The rotation test leaves that fixture for one where
 * the credential is a top level property and the database refuses a superseded password, because what it pins is how
 * a write reaches the connections that were already in service when it was written.
 * <p>
 * The last two tests pin the database properties attribute itself, the one configuration surface whose read and
 * write are the same attribute: the read is masked and the write replaces the whole set, so the ordinary idiom of
 * reading the attribute, changing one entry and writing it back is a write of the set without the password. That
 * consequence is deliberate and documented on {@link ConnectionPool#getDbProperties()} and
 * {@link ConnectionPool#setDbProperties(Properties)}, and what these tests pin is that it costs exactly what the
 * documentation says: over a pool whose credential lives only among the database properties the round trip takes it
 * away and new connections go out with nothing to authenticate them, while over a pool whose credential the password
 * property also holds, the mirrored entry still drops but the property is what signs the connections afterwards.
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
     * What the next established connection is handed. The purge takes out the connections that signed on before the
     * write under test, so that what these assertions look at is a connection established from the configuration as
     * it now stands. What happens to a connection that was already in service when the credential was written is the
     * subject of the test below, and that one does not purge.
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
     * The rotation reaches the connections that were already in service when it was written, and who takes such a
     * connection up again decides how it gets there. A borrowed connection takes the new password up as it is handed
     * over: the borrow compares the identity the session carries with the identity configured now, finds them
     * different, and re-establishes that same connection under the password written over the MBean, so the borrower is
     * served with the rotated credential and nobody has to purge. An idle connection the pool renews on its own
     * account - one that max age has expired while it sat idle - signs on again with the credential it cached at its
     * first sign-on, which a database that has moved on refuses; the pool retires that connection, and the borrow that
     * follows is served by a connection established under the new password. The superseded credential therefore stops
     * authenticating either way, and the refusals along the way are the pool's own renewals rather than borrowed
     * sessions failing.
     */
    @Test
    void aPasswordRotatedThroughThePoolMBeanReachesTheConnectionsAlreadyInService() throws Exception {
        RotatingDriver.acceptedPassword.set("first-secret");
        RotatingDriver.presented.clear();
        DataSource ds = poolWithRotatingDriver();

        borrowAndReturn(ds);
        Assertions.assertEquals(Collections.singletonList("first-secret"), RotatingDriver.snapshot(),
                "precondition: the connection now in service signed on with the first password");
        Assertions.assertEquals(1, poolSessions(), "precondition: one physical session is in service");

        // The database moves first: that is the situation an operator is
        // reacting to when they write the credential through the MBean.
        RotatingDriver.acceptedPassword.set("rotated-secret");
        ds.getPool().getJmxPool().setPassword("rotated-secret");

        // The renewal the pool runs by itself, over a connection nobody has
        // borrowed since the write. It presents the credential that connection
        // cached, the database refuses it, and the session leaves the pool.
        ds.getPoolProperties().setMaxAge(1);
        sleep(5);
        ds.getPool().getJmxPool().testIdle();
        Assertions.assertEquals("first-secret", RotatingDriver.lastPresented(),
                "a connection the pool renews on its own account signs on again with the credential it cached");
        Assertions.assertEquals(0, poolSessions(),
                "the refused session leaves the pool rather than staying in service on the old credential");

        // The next borrow is served by a connection established with the
        // password written here.
        borrowAndReturn(ds);
        Assertions.assertEquals("rotated-secret", RotatingDriver.lastPresented(),
                "the connection the pool establishes after the rotation carries the password written through it");
        Assertions.assertEquals(1, poolSessions());
        ds.getPoolProperties().setMaxAge(0);

        // A borrowed connection takes the rotation up in place, without the
        // purge the setters offer as the immediate remedy.
        long reconnected = ds.getPool().getReconnectedCount();
        RotatingDriver.acceptedPassword.set("second-secret");
        ds.getPool().getJmxPool().setPassword("second-secret");
        borrowAndReturn(ds);
        Assertions.assertEquals("second-secret", RotatingDriver.lastPresented(),
                "the borrowed session that carried the superseded credential is re-established under the new one");
        Assertions.assertEquals(reconnected + 1, ds.getPool().getReconnectedCount(),
                "the connection already in service was re-established rather than replaced by another one");
        Assertions.assertEquals(1, poolSessions(),
                "the re-established connection holds one session, not the two the old and the new sign-on would leave");

        // And it keeps the identity it was re-established with: the following
        // borrow is the same session and signs on nobody.
        int signedOn = RotatingDriver.snapshot().size();
        borrowAndReturn(ds);
        Assertions.assertEquals(reconnected + 1, ds.getPool().getReconnectedCount(),
                "a connection that already carries the configured identity is not re-established again");
        Assertions.assertEquals(signedOn, RotatingDriver.snapshot().size(),
                "the converged connection is reused as it is, so the rotation does not churn the pool");
    }

    /* A published-MBean pool whose credential is a top level property, so that each connection caches it. */
    private DataSource poolWithRotatingDriver() throws Exception {
        DataSource ds = newDataSource();
        ds.setDriverClassName(RotatingDriver.class.getName());
        PoolConfiguration properties = ds.getPoolProperties();
        properties.setJmxEnabled(true);
        properties.setInitialSize(0);
        properties.setUsername("alice");
        properties.setPassword("first-secret");
        ds.createPool();
        Assertions.assertNotNull(ds.getPool().getJmxPool(),
                "precondition: the fixture must have published the pool's own MBean");
        return ds;
    }

    /* Takes a connection out and hands it back, so that it sits in the idle queue afterwards. */
    private static void borrowAndReturn(DataSource ds) {
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("SELECT 1");
        } catch (SQLException x) {
            throw new AssertionError(x);
        }
    }

    /* A published-MBean pool whose credential is a top level property, so the setters mirror it into the entries. */
    private DataSource poolWithTopLevelCredential() throws Exception {
        RecordingDriver.lastConnectInfo.set(null);
        DataSource ds = newDataSource();
        ds.setDriverClassName(RecordingDriver.class.getName());
        PoolConfiguration properties = ds.getPoolProperties();
        properties.setJmxEnabled(true);
        properties.setInitialSize(0);
        properties.setUsername("alice");
        properties.setPassword("first-secret");
        properties.getDbProperties().setProperty("applicationName", "pool-jmx-test");
        ds.createPool();
        Assertions.assertNotNull(ds.getPool().getJmxPool(),
                "precondition: the fixture must have published the pool's own MBean");
        return ds;
    }

    /*
     * The platform server the pool's own MBean is published to, and the registration a container would have made:
     * the data source itself is not a JMX compliant MBean, so preRegister() is what publishes the pool MBean. The
     * round-trip tests read and write the database properties attribute over the server rather than calling the
     * wrapper, because it is the attribute - as any console sees it - whose masked value makes the write destructive.
     */
    private static MBeanServer server() {
        return ManagementFactory.getPlatformMBeanServer();
    }

    private static ObjectName publish(DataSource ds) throws Exception {
        ObjectName dsName = new ObjectName("tomcat:type=JmxCredentialWritesTest,uid=" + System.nanoTime());
        ds.preRegister(server(), dsName);
        ObjectName poolOname = ds.createObjectName(dsName);
        Assertions.assertTrue(server().isRegistered(poolOname),
                "precondition: the container's registration must have published the pool's own MBean");
        return poolOname;
    }

    /* Takes a registration down the way a closing container does, and leaves nothing of it published. */
    private static void unpublish(DataSource ds, ObjectName poolOname) throws Exception {
        MBeanServer server = server();
        ds.close();
        ds.postDeregister();
        String uid = poolOname.getKeyProperty("uid");
        for (ObjectName stale : server.queryNames(new ObjectName("tomcat.jdbc:*"), null)) {
            if (uid.equals(stale.getKeyProperty("uid"))) {
                server.unregisterMBean(stale);
            }
        }
    }

    /**
     * The database properties attribute is published read-write, and unlike the masked strings beside it the masked
     * value it reads is a whole configuration: an operator who reads it, changes one entry and writes it back hands
     * the pool a set with the password taken out, and because the write replaces the live collection, that is the
     * credential's last appearance. Nothing on the read, the write or the MBeanInfo says so - every attribute of a
     * standard registration carries the same boilerplate description - so the hazard is documented on the attribute
     * instead, and what is pinned here is that it costs exactly what that documentation says: the live configuration
     * loses the entry, a connection the pool establishes afterwards is handed no password, and the Password
     * attribute - the credential's owning control, which mirrors into the database properties - is what puts it back.
     */
    @Test
    void aDatabasePropertiesRoundTripThroughThePoolMBeanTakesTheCredentialAway() throws Exception {
        DataSource ds = poolWithMBean();
        Assertions.assertEquals("first-secret", establishFreshConnection(ds).getProperty(PoolUtilities.PROP_PASSWORD),
                "precondition: the credential among the database properties is what connects");

        ObjectName poolOname = publish(ds);
        try {
            Properties read = (Properties) server().getAttribute(poolOname, "DbProperties");
            Assertions.assertEquals("alice", read.getProperty(PoolUtilities.PROP_USER),
                    "precondition: the published read still carries the user name - it looks complete");
            Assertions.assertEquals("pool-jmx-test", read.getProperty("applicationName"),
                    "precondition: and the ordinary properties unchanged");
            Assertions.assertFalse(read.containsKey(PoolUtilities.PROP_PASSWORD),
                    "precondition: the read is the masked one - and nothing in the set it hands the caller says "
                            + "which pair it left out");

            server().setAttribute(poolOname, new Attribute("DbProperties", read));

            Assertions.assertFalse(ds.getPoolProperties().getDbProperties().containsKey(PoolUtilities.PROP_PASSWORD),
                    "the write is the whole set the read handed it, so the live configuration now holds no password "
                            + "entry: the write is a replacement, not a change applied over the set the pool holds");
            Properties handed = establishFreshConnection(ds);
            Assertions.assertFalse(handed.containsKey(PoolUtilities.PROP_PASSWORD),
                    "and the pool that lost the entry establishes its connections with nothing to authenticate them - "
                            + "the documented, deliberate consequence of a masked read beside a whole-set write, "
                            + "pinned so that the documentation cannot silently drift from the behaviour");
            Assertions.assertEquals("alice", handed.getProperty(PoolUtilities.PROP_USER),
                    "the round trip changed the credential and nothing else");
            Assertions.assertEquals("pool-jmx-test", handed.getProperty("applicationName"),
                    "the ordinary properties came back exactly as written");

            ds.getPool().getJmxPool().setPassword("first-secret");
            Assertions.assertEquals("first-secret",
                    establishFreshConnection(ds).getProperty(PoolUtilities.PROP_PASSWORD),
                    "the Password attribute is the credential's owning control: writing it mirrors the entry back "
                            + "among the database properties and the pool authenticates again");
        } finally {
            unpublish(ds, poolOname);
        }
    }

    /**
     * The same round trip over a pool whose password the property also holds, which is where the two configurations
     * part: the replaced set carries no password entry whatever mirrored it, because the write re-mirrors nothing -
     * but the connect path falls back on the property, so this configuration loses the mirrored entry, not its
     * credential.
     */
    @Test
    void aDatabasePropertiesRoundTripThroughThePoolMBeanLeavesATopLevelPasswordAuthenticating() throws Exception {
        DataSource ds = poolWithTopLevelCredential();
        Assertions.assertEquals("first-secret", establishFreshConnection(ds).getProperty(PoolUtilities.PROP_PASSWORD),
                "precondition: the mirrored entry is present and what the driver is handed");
        Assertions.assertEquals("first-secret", ds.getPoolProperties().getDbProperties()
                .getProperty(PoolUtilities.PROP_PASSWORD), "precondition: and it mirrors the property");

        ObjectName poolOname = publish(ds);
        try {
            Properties read = (Properties) server().getAttribute(poolOname, "DbProperties");
            server().setAttribute(poolOname, new Attribute("DbProperties", read));

            Assertions.assertFalse(ds.getPoolProperties().getDbProperties().containsKey(PoolUtilities.PROP_PASSWORD),
                    "even here the replaced set holds no password entry: the write mirrors nothing back from the "
                            + "properties beside it");
            Assertions.assertEquals("first-secret", ds.getPoolProperties().getPassword(),
                    "while the property the credential also lives in is untouched by the write");
            Assertions.assertEquals("first-secret",
                    establishFreshConnection(ds).getProperty(PoolUtilities.PROP_PASSWORD),
                    "and what authenticates afterwards is the property: this configuration loses an entry it had "
                            + "been mirroring, not the credential that signs its connections");
        } finally {
            unpublish(ds, poolOname);
        }
    }

    /**
     * A driver that, like a database that has just rotated a password, accepts only the credential it is currently
     * configured to accept and refuses every other, and that records the password each connect presented. The
     * delegate is still reached with the test database's own credentials, so a connection the driver accepts is a real
     * session the pool can use.
     */
    public static class RotatingDriver extends RecordingDriver {

        static final AtomicReference<String> acceptedPassword = new AtomicReference<>();
        static final List<String> presented = Collections.synchronizedList(new ArrayList<>());

        static List<String> snapshot() {
            synchronized (presented) {
                return new ArrayList<>(presented);
            }
        }

        static String lastPresented() {
            List<String> seen = snapshot();
            Assertions.assertFalse(seen.isEmpty(), "precondition: the driver must have been called");
            return seen.get(seen.size() - 1);
        }

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            String password = info.getProperty(PoolUtilities.PROP_PASSWORD);
            presented.add(password);
            if (!acceptedPassword.get().equals(password)) {
                throw new SQLException("the database does not accept password [" + password + "] any more");
            }
            return super.connect(url, info);
        }
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

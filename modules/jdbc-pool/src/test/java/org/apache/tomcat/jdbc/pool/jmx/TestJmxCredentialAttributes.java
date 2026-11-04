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
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolProperties.InterceptorDefinition;

/**
 * The three raw configuration strings of the pool - the JDBC URL, the
 * connection properties and the interceptor definitions - are published as
 * attributes of the pool's own MBean, and any of them is a place an operator
 * may type a credential: a URL of the form
 * {@code jdbc:mysql://host/db?password=xxx} carries its own, so does a
 * {@code connectionProperties} line, and so does the parameter list of an
 * interceptor definition, whose pairs reach a third-party interceptor through
 * {@code setProperties()} exactly as the pairs of the other two reach the
 * driver.
 * <p>
 * The MBean already refuses to say the word for the credential held in the
 * dedicated property: {@link ConnectionPool#getPassword()} answers with a fixed
 * sentence rather than the password, and {@link ConnectionPool#getDbProperties()}
 * answers with a copy that has the password entry taken out. Both exist for
 * exactly one reason - a monitor that only reads must be able to look at a live
 * pool's configuration - and that reason does not stop at one attribute: while
 * the neighbouring pair kept the secret, these two handed it back verbatim, so
 * the shortest read path to a credential was the one attribute an operator reads
 * when the password attribute tells them nothing. The masking the configuration
 * applies to its own dump and to the pool's log lines never reached here.
 * </p>
 * <p>
 * What is pinned below is therefore a read/write asymmetry, the same one the
 * password attribute has: a write through the MBean takes the raw string, since
 * a configuration written scrubbed is a pool that cannot connect, while the read
 * gives back the pool's own best-effort masking, so the JMX read, the
 * configuration dump and the log all say the same thing about a configuration
 * that carries its credential inline.
 * </p>
 */
class TestJmxCredentialAttributes extends JdbcPoolBaseTest {

    /*
     * The mask the configuration dump and the pool's log lines put over a
     * recognised credential, so that a monitor cannot tell from the mask which
     * surface it read.
     */
    private static final String MASK = "********";

    private static final String URL_SECRET = "url-inline-secret";
    private static final String PROPERTIES_SECRET = "properties-inline-secret";
    private static final String INTERCEPTOR_SECRET = "interceptor-inline-secret";

    private static MBeanServer server() {
        return ManagementFactory.getPlatformMBeanServer();
    }

    /* A name no other test of the suite uses, so nothing collides by accident. */
    private static ObjectName uniqueName(String role) throws MalformedObjectNameException {
        return new ObjectName("tomcat:type=JmxCredentialAttributesTest,role=" + role
                + ",uid=" + System.nanoTime());
    }

    /*
     * One container registration: the name the MBean server was asked to publish
     * the data source under, and the name the pool MBean - what the attributes
     * below are read from - actually answers for.
     */
    private static final class Registration {
        private final DataSource ds;
        private final ObjectName dsName;
        private final ObjectName poolOname;

        private Registration(DataSource ds, ObjectName dsName, ObjectName poolOname) {
            this.ds = ds;
            this.dsName = dsName;
            this.poolOname = poolOname;
        }
    }

    /*
     * Everything a registration published: the pool's own MBean and anything
     * published below it. The uid is carried down from the data source's name
     * into every name derived from it, so it identifies this registration's
     * publications and nothing else.
     */
    private static Set<ObjectName> publishedFor(MBeanServer server, ObjectName dsName) throws Exception {
        String uid = dsName.getKeyProperty("uid");
        Set<ObjectName> found = new HashSet<>();
        for (ObjectName name : server.queryNames(new ObjectName("tomcat.jdbc:*"), null)) {
            if (uid.equals(name.getKeyProperty("uid"))) {
                found.add(name);
            }
        }
        return found;
    }

    /*
     * Publishes a pool the way a container does - the data source is not itself
     * a JMX compliant MBean, so the pool's own MBean is what gets published,
     * from the data source's preRegister() - and checks that the read path this
     * class is about really is the attribute read of a registered MBean.
     */
    private Registration publish(DataSource ds, String role) throws Exception {
        ObjectName dsName = uniqueName(role);
        ds.preRegister(server(), dsName);
        ObjectName poolOname = ds.createObjectName(dsName);
        Assertions.assertTrue(server().isRegistered(poolOname),
                "precondition: the container's registration must have published the pool's own MBean");
        return new Registration(ds, dsName, poolOname);
    }

    /*
     * Takes a registration down the way a closing container does, and leaves
     * nothing of it published.
     */
    private static void closeAndUnpublish(Registration r) throws Exception {
        MBeanServer server = server();
        r.ds.close();
        r.ds.postDeregister();
        for (ObjectName stale : publishedFor(server, r.dsName)) {
            server.unregisterMBean(stale);
        }
    }

    /*
     * A pool whose credential is carried inline in both raw strings, the way a
     * URL typed into a JNDI resource or a connectionProperties line of a
     * container descriptor carries it. No session is established: these tests
     * read the published configuration, they do not use it.
     */
    private DataSource poolWithInlineCredentials() throws Exception {
        DataSource ds = newDataSource();
        ds.setUrl(getUrl() + ";password=" + URL_SECRET);
        ds.getPoolProperties().setConnectionProperties("password=" + PROPERTIES_SECRET + ";a=b");
        ds.getPoolProperties().setJmxEnabled(true);
        ds.getPoolProperties().setInitialSize(0);
        ds.createPool();
        return ds;
    }

    @Test
    void theUrlAttributeOfThePoolMBeanNeverCarriesTheCredentialTypedIntoTheUrl() throws Exception {
        Registration r = publish(poolWithInlineCredentials(), "url-attribute");
        try {
            String published = (String) server().getAttribute(r.poolOname, "Url");
            Assertions.assertFalse(published.contains(URL_SECRET),
                    "a password typed into the JDBC URL must not come back out of the pool's MBean in"
                            + " cleartext: the password attribute of that very MBean refuses to say the"
                            + " word at all, and does so precisely so that a monitor which only reads may"
                            + " look at a live pool's configuration - an attribute that repeats the URL as"
                            + " it stands takes that protection away from every configuration that carries"
                            + " its credential in the URL rather than in the dedicated property");
            Assertions.assertEquals(getUrl() + ";password=" + MASK, published,
                    "what the attribute does publish must be the pool's own masking of the configured URL"
                            + " - the credential pair under the mask the configuration dump uses, and the"
                            + " rest of the URL still readable, since the point of the attribute is to tell"
                            + " an operator which database this pool is pointed at");

            Assertions.assertEquals(getUrl() + ";password=" + URL_SECRET, r.ds.getPoolProperties().getUrl(),
                    "masking the read must leave the configuration itself as it was written: the URL is what"
                            + " the connect path hands the driver, so scrubbing it there turns a display"
                            + " change into a pool that cannot connect");
        } finally {
            closeAndUnpublish(r);
        }
    }

    @Test
    void theConnectionPropertiesAttributeOfThePoolMBeanNeverCarriesTheCredentialTypedIntoThem()
            throws Exception {
        Registration r = publish(poolWithInlineCredentials(), "properties-attribute");
        try {
            String published = (String) server().getAttribute(r.poolOname, "ConnectionProperties");
            Assertions.assertFalse(published.contains(PROPERTIES_SECRET),
                    "a password carried by the connection properties must not come back out of the pool's"
                            + " MBean in cleartext: those properties go to the driver as they are typed, so"
                            + " they are a first place to look for a credential, and the MBean's own password"
                            + " attribute is masked for the sake of the read-only monitor that would read it"
                            + " here instead");
            Assertions.assertEquals("password=" + MASK + ";a=b", published,
                    "the masking must hide the credential and say nothing else about the configuration: the"
                            + " other pairs are what an operator needs to see to diagnose a pool, and a mask"
                            + " that swallowed them would be a monitor turned away from the configuration");

            Assertions.assertEquals("password=" + PROPERTIES_SECRET + ";a=b",
                    r.ds.getPoolProperties().getConnectionProperties(),
                    "the configuration the connect path reads must keep the string as it was written: the"
                            + " pairs behind the mask are the pairs the driver is handed");
        } finally {
            closeAndUnpublish(r);
        }
    }

    @Test
    void theJdbcInterceptorsAttributeOfThePoolMBeanNeverCarriesTheCredentialTypedIntoADefinition()
            throws Exception {
        DataSource ds = newDataSource();
        // An interceptor definition whose parameter list carries a credential,
        // the way an interceptor that reports to a monitored endpoint is
        // configured in this very string. ResetAbandonedTimer stands in for
        // any interceptor that takes its configuration through
        // setProperties(): the base JdbcInterceptor stores every pair the
        // definition carries rather than only the ones it knows, so the
        // credential below really is delivered to the interceptor as typed -
        // and the pool start instantiates the configured class and hands it
        // these properties, so the configuration is live, not decoration.
        ds.setJdbcInterceptors("ResetAbandonedTimer(password=" + INTERCEPTOR_SECRET + ")");
        ds.getPoolProperties().setJmxEnabled(true);
        ds.getPoolProperties().setInitialSize(0);
        ds.createPool();
        InterceptorDefinition configured = null;
        for (InterceptorDefinition definition : ds.getPoolProperties().getJdbcInterceptorsAsArray()) {
            if ("ResetAbandonedTimer".equals(definition.getClassName())) {
                configured = definition;
            }
        }
        Assertions.assertNotNull(configured,
                "precondition: the definition below must really parse into an interceptor definition, not just "
                        + "read as one");
        Assertions.assertEquals(INTERCEPTOR_SECRET, configured.getProperties().get("password").getValue(),
                "precondition: the pair typed into the definition must reach the parsed properties the "
                        + "interceptor is configured with - the attribute under test has a credential to hide "
                        + "precisely because the parse keeps it");

        Registration r = publish(ds, "interceptors-attribute");
        try {
            String published = (String) server().getAttribute(r.poolOname, "JdbcInterceptors");
            Assertions.assertFalse(published.contains(INTERCEPTOR_SECRET),
                    "a password typed into an interceptor definition must not come back out of the pool's MBean"
                            + " in cleartext: interceptor properties reach a third-party interceptor as typed,"
                            + " so the definition string is as much a place to look for a credential as the URL"
                            + " and the connection properties are, and the configuration dump over"
                            + " PoolProperties already masks these pairs for the sake of exactly the read-only"
                            + " monitor that reads this attribute");
            Assertions.assertEquals("ResetAbandonedTimer(password=" + MASK + ")", published,
                    "the masking must hide the credential and keep everything else the attribute is read for:"
                            + " which interceptors are configured and which properties they carry, each pair"
                            + " named as configured, so a monitor still sees the shape of the chain behind the"
                            + " mask");

            Assertions.assertEquals("ResetAbandonedTimer(password=" + INTERCEPTOR_SECRET + ")",
                    r.ds.getPoolProperties().getJdbcInterceptors(),
                    "the configuration the interceptor chain is built from must keep the string as it was"
                            + " written: the pairs behind the mask are the pairs the interceptor is"
                            + " configured with");
        } finally {
            closeAndUnpublish(r);
        }
    }

    @Test
    void aCredentialWrittenThroughThePoolMBeanIsStoredRawAndReadBackMasked() throws Exception {
        Registration r = publish(poolWithInlineCredentials(), "write-path");
        try {
            String rotatedUrl = getUrl() + ";password=rotated-" + URL_SECRET;
            String rotatedProperties = "password=rotated-" + PROPERTIES_SECRET + ";a=b";
            AttributeList writes = new AttributeList();
            writes.add(new Attribute("Url", rotatedUrl));
            writes.add(new Attribute("ConnectionProperties", rotatedProperties));
            server().setAttributes(r.poolOname, writes);

            Assertions.assertEquals(rotatedUrl, r.ds.getPoolProperties().getUrl(),
                    "an operator who writes the URL through the pool's MBean must have written the URL, the"
                            + " whole URL: a configuration surface that scrubs on the way in is a surface an"
                            + " operator cannot configure a pool with, which is why the masked read of the"
                            + " password attribute has always had an unmasked write beside it");
            Assertions.assertEquals(rotatedProperties, r.ds.getPoolProperties().getConnectionProperties(),
                    "and the same for the connection properties, which reach the driver pair for pair");

            Assertions.assertFalse(((String) server().getAttribute(r.poolOname, "Url"))
                    .contains("rotated-" + URL_SECRET),
                    "a credential written through the MBean is masked on the way out like one written"
                            + " anywhere else: an operator who rotates a secret over JMX must not have made"
                            + " the secret readable over JMX by the rotation");
            Assertions.assertFalse(((String) server().getAttribute(r.poolOname, "ConnectionProperties"))
                    .contains("rotated-" + PROPERTIES_SECRET),
                    "and the same for the connection properties");
        } finally {
            closeAndUnpublish(r);
        }
    }

    @Test
    void aPoolWhoseUrlCarriesItsCredentialStillBorrowsAWorkingConnection() throws Exception {
        DataSource ds = newDataSource();
        // A database of its own, created by the very URL that names its
        // credential. H2 authenticates a session against the database it
        // connects to, so the credential in the URL below is not decoration but
        // what the session is really established with. The password the
        // configuration carries is overlaid on the properties handed to the
        // driver, and a driver that also finds the password in the URL counts
        // that as one property twice, so the URL is the only thing carrying it.
        String rawUrl = urlForDatabase("jmxattr_" + System.nanoTime()) + ";password=" + URL_SECRET;
        ds.setUrl(rawUrl);
        ds.setPassword(null);
        ds.getPoolProperties().setJmxEnabled(true);
        ds.createPool();
        Registration r = publish(ds, "connect");
        try {
            Assertions.assertEquals(1, ds.getPool().getIdle(),
                    "precondition: the pool established its initial session out of the URL as configured,"
                            + " credential and all, before anything was ever read off the MBean");
            Assertions.assertFalse(((String) server().getAttribute(r.poolOname, "Url")).contains(URL_SECRET),
                    "the pool that connects with that credential must publish it masked: the masking is a"
                            + " decision taken where the configuration is read for display, and not a change to"
                            + " what this pool hands its driver");

            try (Connection con = ds.getConnection();
                    Statement st = con.createStatement();
                    ResultSet rs = st.executeQuery("SELECT 1")) {
                Assertions.assertTrue(rs.next(), "the borrowed session must answer");
                Assertions.assertEquals(1, rs.getInt(1), "on a statement that ran");
            }
            Assertions.assertEquals(rawUrl, ds.getPoolProperties().getUrl(),
                    "and connecting with the credential must have left the configured URL intact, since a"
                            + " pool that rewrites its own URL once it is monitored is a pool that cannot"
                            + " reconnect");
        } finally {
            closeAndUnpublish(r);
        }
    }

    /* The database URL of this test class' server, with a database name of the caller's own. */
    private static String urlForDatabase(String database) {
        return getUrl().substring(0, getUrl().indexOf("/mem:")) + "/mem:" + database;
    }
}

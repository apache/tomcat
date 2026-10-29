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
import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.HashSet;
import java.util.Set;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.interceptor.StatementCache;

/**
 * What {@link JmxUtil#registerJmx(ObjectName, String, Object)} hands back to
 * the components that publish themselves through it.
 * <p>
 * Swallowing the MBean server's refusals is the helper's job: the pool starts
 * with JMX enabled by default, and a JVM whose platform server is unusable
 * must not keep an application away from its database. What the helper must
 * not do is report a name it did not publish. The pooled connection and the
 * {@code StatementCache} and {@code ResetAbandonedTimer} interceptors keep the
 * returned name as their proof of registration - that name is the gate they
 * check before trying again, and the handle they release when the component
 * goes away - so a name reported for a refused registration does two things at
 * once: the component believes it is monitored and never tries again, and on
 * release it unregisters a name it never published. Where the name is held by
 * a live MBean of some other component - which is what a refusal means - that
 * release silently takes the management surface of a component that is running
 * perfectly well.</p>
 * <p>
 * The contract the first tests pin is that the returned name is this call's own
 * publish, or nothing at all. The last five drive that through a real pool: a
 * {@code StatementCache} whose name is taken must leave the MBean that owns the
 * name registered when its connection is discarded, and it must publish itself
 * at the next borrow once the obstacle is gone; the {@code DataSource} one
 * level up must keep the same books for the pool MBean it publishes, whose
 * handle it releases at {@code close()}, at the container's
 * {@code postDeregister()}, and at a registration the MBean server failed -
 * which, running no {@code postDeregister()} at all, leaves
 * {@code postRegister(false)} as the only hook that could ever release the
 * pool's publication.</p>
 */
class TestJmxRegistration extends JdbcPoolBaseTest {

    private static final String POOL_DOMAIN = "tomcat.jdbc";

    /*
     * A minimal MBean to occupy a name with: one readable attribute, so a test
     * can also tell which instance a registered name currently answers for.
     */
    public interface ProbeMBean {
        String getMarker();
    }

    /* The MBean behind {@link ProbeMBean}; the marker names its own instance. */
    public static class Probe implements ProbeMBean {
        private final String marker;

        Probe(String marker) {
            this.marker = marker;
        }

        @Override
        public String getMarker() {
            return marker;
        }
    }

    /*
     * A modeler-style wrapper of the kind a container registers in place of a
     * DataSource. The DataSource is not itself a JMX compliant MBean, so the
     * MBean server only ever sees a wrapper, and the wrapper forwards all four
     * registration callbacks to the resource it manages - which is what the
     * modeler's BaseModelMBean does. This is what lets the server, rather than
     * a test, drive a DataSource through a registration it is about to fail.
     */
    public interface ContainerMBean {
        String getMarker();
    }

    public static class Container implements ContainerMBean, MBeanRegistration {
        private final DataSource ds;

        Container(DataSource ds) {
            this.ds = ds;
        }

        @Override
        public String getMarker() {
            return "container";
        }

        @Override
        public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
            ds.preRegister(server, name);
            return name;
        }

        @Override
        public void postRegister(Boolean registrationDone) {
            ds.postRegister(registrationDone);
        }

        @Override
        public void preDeregister() throws Exception {
            ds.preDeregister();
        }

        @Override
        public void postDeregister() {
            ds.postDeregister();
        }
    }

    /*
     * A caching pool that has published itself and handed out one connection,
     * left with that connection back in the pool and its cache holding no
     * registration: the state a discarded connection leaves behind, and the one
     * the cache is in while it waits to publish itself again.
     */
    private static final class ReusableCache {
        private final DataSource ds;
        private final StatementCache cache;
        private final ObjectName cacheName;

        private ReusableCache(DataSource ds, StatementCache cache, ObjectName cacheName) {
            this.ds = ds;
            this.cache = cache;
            this.cacheName = cacheName;
        }
    }

    private static MBeanServer server() {
        return ManagementFactory.getPlatformMBeanServer();
    }

    /* A name no other test of the suite uses, so nothing collides by accident. */
    private static ObjectName uniqueName(String role) throws MalformedObjectNameException {
        return new ObjectName("tomcat:type=JmxRegistrationTest,role=" + role
                + ",uid=" + System.nanoTime());
    }

    /* The pool MBeans: the pool's own, and everything published below it. */
    private static Set<ObjectName> poolMBeans(MBeanServer server) throws Exception {
        return server.queryNames(new ObjectName(POOL_DOMAIN + ":*"), null);
    }

    /*
     * The registration handle a StatementCache keeps for itself. It is a private
     * field, and it is exactly the state the fix is about - what the cache
     * believes it published - so it is read directly, the way TestJmxMonitor
     * reads the DataSource's handle.
     */
    private static ObjectName handleOf(StatementCache cache) throws Exception {
        Field handle = StatementCache.class.getDeclaredField("oname");
        handle.setAccessible(true);
        return (ObjectName) handle.get(cache);
    }

    /*
     * The pool-MBean handle a DataSource keeps for itself: the same state,
     * read the same way, one level up.
     */
    private static ObjectName handleOf(DataSource ds) throws Exception {
        Field handle = DataSource.class.getDeclaredField("oname");
        handle.setAccessible(true);
        return (ObjectName) handle.get(ds);
    }

    /*
     * The MBean of the one pooled connection that has been handed out, below the
     * given pool MBean. An interceptor's MBean sits below that and repeats the
     * connection's key properties, so it is skipped.
     */
    private static ObjectName connectionMBeanUnder(MBeanServer server, ObjectName poolOname)
            throws Exception {
        String uid = poolOname.getKeyProperty("uid");
        ObjectName found = null;
        for (ObjectName name : poolMBeans(server)) {
            if (name.getKeyProperty("connections") != null
                    && name.getKeyProperty("JdbcInterceptor") == null
                    && uid.equals(name.getKeyProperty("uid"))) {
                Assertions.assertNull(found, "this test drives exactly one pooled connection,"
                        + " but a second one is registered as " + name);
                found = name;
            }
        }
        Assertions.assertNotNull(found,
                "a borrowed connection of an enabled pool publishes itself below the pool"
                        + " MBean, and nothing was found below " + poolOname);
        return found;
    }

    /*
     * Publishes a caching pool the way a container does, lets it hand out its
     * only connection, and then unbinds that cache from the connection the way
     * the pool does when it discards one - {@code reset(null,null)}, which is
     * where an interceptor releases the name it published. What is left is a
     * pooled connection that will be handed out again, with its cache's own name
     * free to be taken by something else.
     */
    private ReusableCache publishACachingPoolAndFreeItsCacheName(String role) throws Exception {
        MBeanServer server = server();
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJmxEnabled(true);
        ds.setJdbcInterceptors(StatementCache.class.getName() + "(prepared=true,max=5)");
        ds.getPoolProperties().setInitialSize(0);
        ds.createPool();

        Set<ObjectName> before = poolMBeans(server);
        ds.preRegister(server, uniqueName(role));
        Set<ObjectName> added = new HashSet<>(poolMBeans(server));
        added.removeAll(before);
        Assertions.assertEquals(1, added.size(),
                "publishing an enabled pool must add its one MBean");
        ObjectName poolOname = added.iterator().next();
        Assertions.assertEquals(poolOname, ds.getPool().getJmxPool().getObjectName(),
                "the pool MBean must know the name it was published under");

        Connection first = ds.getConnection();
        StatementCache cache = interceptorOf(first, StatementCache.class);
        Assertions.assertNotNull(cache, "this pool must be running a statement cache");
        ObjectName cacheName = new ObjectName(
                connectionMBeanUnder(server, poolOname) + ",JdbcInterceptor=StatementCache");
        Assertions.assertTrue(server.isRegistered(cacheName),
                "the cache of a borrowed connection publishes itself below that connection");
        Assertions.assertEquals(cacheName, handleOf(cache),
                "a cache whose publish was accepted holds the name it was accepted under");
        first.close();

        cache.reset(null, null);
        Assertions.assertFalse(server.isRegistered(cacheName),
                "unbinding a cache from its connection must release the name it holds");
        return new ReusableCache(ds, cache, cacheName);
    }

    @Test
    void aPublishedMBeanComesBackAsItsOwnNameAndStaysReleasable() throws Exception {
        MBeanServer server = server();
        ObjectName base = uniqueName("published");
        ObjectName full = new ObjectName(base.toString() + ",connections=Probe[1]");
        try {
            ObjectName registered = JmxUtil.registerJmx(base, ",connections=Probe[1]",
                    new Probe("published"));
            Assertions.assertEquals(full, registered,
                    "a registration the server accepted must come back as the name it was"
                            + " published under, since that name is the only handle the caller"
                            + " ever gets in order to release it");
            Assertions.assertTrue(server.isRegistered(registered),
                    "a name the caller is given must be a live registration");
            Assertions.assertEquals("published", server.getAttribute(registered, "Marker"),
                    "the name must answer for the object that was handed to the helper");

            // the other half of the deal: the name the helper hands back is one
            // the caller may unregister, and unregistering it works
            JmxUtil.unregisterJmx(registered);
            Assertions.assertFalse(server.isRegistered(registered),
                    "a name the helper handed back must be releasable by the caller");
        } finally {
            if (server.isRegistered(full)) {
                server.unregisterMBean(full);
            }
        }
    }

    @Test
    void aRegistrationTheServerRefusesComesBackAsNull() throws Exception {
        MBeanServer server = server();
        ObjectName name = uniqueName("refused");
        server.registerMBean(new Probe("owner"), name);
        try {
            // the exact same name, so this is the InstanceAlreadyExists case a
            // pooled connection or an interceptor meets whenever a name has
            // outlived the component that held it
            ObjectName registered = JmxUtil.registerJmx(name, null, new Probe("intruder"));
            Assertions.assertNull(registered,
                    "a registration the server refused must not report the name it was refused:"
                            + " a caller keeps whatever it is given as proof that it is"
                            + " monitored, and releases that name when it goes away - which"
                            + " takes the surface of the MBean that really owns it");
            Assertions.assertEquals("owner", server.getAttribute(name, "Marker"),
                    "a refused publish must leave the owner of the name alone");
        } finally {
            server.unregisterMBean(name);
        }
    }

    @Test
    void anAbsentBaseNameIsNoRegistrationAtAll() throws Exception {
        MBeanServer server = server();
        Set<ObjectName> before = new HashSet<>(poolMBeans(server));

        // a component with no pool MBean to sit below - a pool that was never
        // published - has nowhere to be published at all, and has to hear so
        ObjectName registered = JmxUtil.registerJmx(null, ",connections=Probe[1]",
                new Probe("orphan"));
        Assertions.assertNull(registered,
                "with no base name there is nothing to register under, and the caller must be"
                        + " told that instead of being left to hold a name");
        Assertions.assertEquals(before, new HashSet<>(poolMBeans(server)),
                "skipping the registration must publish nothing");
    }

    @Test
    void aMalformedNameIsNoRegistrationAtAll() throws Exception {
        MBeanServer server = server();
        ObjectName base = uniqueName("malformed");
        Set<ObjectName> before = new HashSet<>(poolMBeans(server));

        // an unquoted key property value may not contain '=', so the name being
        // asked for cannot be built at all
        ObjectName registered = JmxUtil.registerJmx(base, ",connections=Probe[1]=x",
                new Probe("malformed"));
        Assertions.assertNull(registered,
                "a name that cannot be built is a failed registration, and the caller must not"
                        + " be handed the base name instead as if it had been published");
        Assertions.assertFalse(server.isRegistered(base),
                "a name that cannot be built must not fall back to registering under the base");
        Assertions.assertEquals(before, new HashSet<>(poolMBeans(server)),
                "a malformed name must publish nothing");
    }

    @Test
    void aCacheThatWasRefusedItsNameDoesNotUnregisterTheMBeanThatOwnsIt() throws Exception {
        MBeanServer server = server();
        ReusableCache setup = publishACachingPoolAndFreeItsCacheName("cache");
        DataSource ds = setup.ds;
        // something else takes the name the cache used to hold: the leftover of a
        // pool that died without deregistering, or another component that picked
        // the same name. It is live and reporting.
        ObjectName foreign = setup.cacheName;
        server.registerMBean(new Probe("foreign"), foreign);
        try {
            // the pooled connection goes out again, so the cache asks for the very
            // same name and the server refuses it
            Connection second = ds.getConnection();
            try {
                Assertions.assertSame(setup.cache, interceptorOf(second, StatementCache.class),
                        "the pooled connection was reused, so the cache being bound again is"
                                + " the very instance that was refused");
                Assertions.assertEquals("foreign", server.getAttribute(foreign, "Marker"),
                        "a refused publish must not have touched the MBean holding the name");
            } finally {
                second.close();
            }

            // the harm, on the pool's own lifecycle: closing the pool discards the
            // pooled connection, and discarding unbinds the cache from it, which is
            // where a cache releases whatever name it believes it holds
            ds.close();
            Assertions.assertTrue(server.isRegistered(foreign),
                    "discarding a connection whose cache was refused a name must leave the MBean"
                            + " that owns that name registered: unregistering it takes the"
                            + " management surface of a component that has done nothing wrong,"
                            + " and does so silently");
        } finally {
            if (server.isRegistered(foreign)) {
                server.unregisterMBean(foreign);
            }
            ds.close();
            ds.postDeregister();
        }
    }

    @Test
    void aCacheThatWasRefusedItsNameTriesAgainOnceTheNameIsFree() throws Exception {
        MBeanServer server = server();
        ReusableCache setup = publishACachingPoolAndFreeItsCacheName("retry");
        DataSource ds = setup.ds;
        ObjectName name = setup.cacheName;
        try {
            server.registerMBean(new Probe("foreign"), name);
            try (Connection second = ds.getConnection()) {
                Assertions.assertNotNull(second,
                        "the pool must keep handing out connections while one of its"
                                + " interceptors cannot publish itself");
            }
            Assertions.assertEquals("foreign", server.getAttribute(name, "Marker"),
                    "while the name is taken the MBean that owns it keeps it, and the cache"
                            + " publishes nothing of its own");

            // the obstacle goes away, exactly as it does in a running container
            // once the stale registration that held the name is cleared
            server.unregisterMBean(name);
            Connection third = ds.getConnection();
            try {
                Assertions.assertTrue(server.isRegistered(name),
                        "a cache that was refused must not count that as a registration: the"
                                + " next borrow has to try again, or the pool keeps a live"
                                + " connection whose cache stays invisible to management for"
                                + " the whole life of that connection");
                Assertions.assertEquals(name, handleOf(setup.cache),
                        "the name the cache holds now must be the one that is registered:"
                                + " the retry must have published the pool's own cache, which"
                                + " is what the borrow that was refused was supposed to do");
            } finally {
                third.close();
            }
        } finally {
            if (server.isRegistered(name)) {
                server.unregisterMBean(name);
            }
            ds.close();
            ds.postDeregister();
        }
    }

    @Test
    void aDataSourceThatWasRefusedItsPoolNameDoesNotUnregisterTheMBeanThatOwnsIt()
            throws Exception {
        MBeanServer server = server();
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJmxEnabled(true);
        ds.createPool();
        ObjectName dsName = uniqueName("datasource");
        // the exact name the DataSource will ask the server for: createObjectName
        // is a deterministic mapping of the DataSource's own name, so two data
        // sources - or a data source and the leftover of one that died without
        // deregistering - derive the same name from the same keys
        ObjectName foreign = ds.createObjectName(dsName);
        server.registerMBean(new Probe("foreign"), foreign);
        try {
            // the container registers the DataSource, and the pool MBean the
            // DataSource publishes from preRegister() is refused its name
            ds.preRegister(server, dsName);
            Assertions.assertEquals("foreign", server.getAttribute(foreign, "Marker"),
                    "a refused publish must not have touched the MBean holding the name");
            // read the handle before close(): close() clears whatever handle is
            // held, so after it the read no longer says what the refusal left
            ObjectName kept = handleOf(ds);

            // the harm, on the DataSource's own lifecycle: close() and the
            // container's postDeregister() each unregister the handle they hold
            ds.close();
            ds.postDeregister();
            Assertions.assertTrue(server.isRegistered(foreign),
                    "closing a DataSource whose pool publish was refused must leave the MBean"
                            + " that owns that name registered: unregistering it takes the"
                            + " management surface of a component that has done nothing wrong,"
                            + " and does so silently");
            Assertions.assertNull(kept,
                    "a DataSource whose pool publish was refused must not keep the refused"
                            + " name as its registration handle: that handle is exactly what"
                            + " close() and postDeregister() unregister, and this data source"
                            + " never published under it");
        } finally {
            if (server.isRegistered(foreign)) {
                server.unregisterMBean(foreign);
            }
            ds.close();
            ds.postDeregister();
        }
    }

    /*
     * Everything the pool publishes for one DataSource registration: the pool's
     * own MBean and anything published below it. The uid is carried down from
     * the DataSource's name into every name derived from it, so it identifies
     * the publications of this registration and nothing else.
     */
    private static Set<ObjectName> publishedFor(MBeanServer server, ObjectName dsName)
            throws Exception {
        String uid = dsName.getKeyProperty("uid");
        Set<ObjectName> found = new HashSet<>();
        for (ObjectName name : poolMBeans(server)) {
            if (uid.equals(name.getKeyProperty("uid"))) {
                found.add(name);
            }
        }
        return found;
    }

    @Test
    void aFailedDataSourceRegistrationUnpublishesThePoolMBeanPreRegisterPublished()
            throws Exception {
        MBeanServer server = server();
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJmxEnabled(true);
        ds.getPoolProperties().setInitialSize(0);
        ds.createPool();

        // the container is about to fail registering this DataSource: its name
        // is held by another MBean, while the pool name derived from it is free
        // and so preRegister() does publish the pool MBean before the server
        // notices the conflict
        ObjectName blocked = uniqueName("failed");
        ObjectName poolOname = ds.createObjectName(blocked);
        server.registerMBean(new Probe("owner"), blocked);
        try {
            Assertions.assertFalse(server.isRegistered(poolOname));
            Assertions.assertThrows(InstanceAlreadyExistsException.class,
                    () -> server.registerMBean(new Container(ds), blocked));

            // what the failed registration leaves behind. The server calls
            // postRegister(FALSE) and no postDeregister() at all, and a
            // container that could not register the DataSource will not close
            // it either, so a pool MBean still published here is published for
            // good: statistics and notifications of a pool nobody manages,
            // pinning the pool, its credentials and its driver class loader
            Assertions.assertFalse(server.isRegistered(poolOname),
                    "a DataSource whose registration failed must unpublish the pool MBean its"
                            + " preRegister() published: postRegister(FALSE) is the only hook"
                            + " that ever runs for a registration that failed, so anything left"
                            + " registered here outlives every chance to unregister it");
            Assertions.assertTrue(publishedFor(server, blocked).isEmpty(),
                    "nothing of a registration the server refused may stay published");
            Assertions.assertNull(handleOf(ds),
                    "a failed registration must leave no handle behind: it is what close()"
                            + " and postDeregister() unregister, and there is nothing left to"
                            + " unregister");
            Assertions.assertEquals("owner", server.getAttribute(blocked, "Marker"),
                    "the cleanup must have released only the pool's own publication");

            // the container gives up on the DataSource and closes it: the
            // close's own release must ride over the cleanup that already ran
            ds.close();
            ds.postDeregister();
            Assertions.assertTrue(publishedFor(server, blocked).isEmpty(),
                    "closing a DataSource whose registration failed must publish nothing"
                            + " retroactively");
            Assertions.assertEquals("owner", server.getAttribute(blocked, "Marker"),
                    "a DataSource whose registration was cleaned up must not take the name"
                            + " the registration failed against when it is later closed");
        } finally {
            if (server.isRegistered(blocked)) {
                server.unregisterMBean(blocked);
            }
            ds.close();
            ds.postDeregister();
            // test hygiene: take away anything a broken build left published
            for (ObjectName stale : publishedFor(server, blocked)) {
                server.unregisterMBean(stale);
            }
        }
    }

    @Test
    void aSurvivingDataSourcePublishesItsPoolAtTheNextSuccessfulRegistration()
            throws Exception {
        MBeanServer server = server();
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJmxEnabled(true);
        ds.getPoolProperties().setInitialSize(0);
        ds.createPool();

        ObjectName blocked = uniqueName("rebound");
        ObjectName fresh = uniqueName("rebound-ok");
        server.registerMBean(new Probe("owner"), blocked);
        try {
            Assertions.assertThrows(InstanceAlreadyExistsException.class,
                    () -> server.registerMBean(new Container(ds), blocked));

            // the failed attempt must not have made the DataSource unmanageable
            // for good: the same DataSource registered under a free name
            // publishes its pool, and a cleanup that also ran on a registration
            // the server accepted would take that publication away again
            server.registerMBean(new Container(ds), fresh);
            ObjectName poolOname = ds.createObjectName(fresh);
            Assertions.assertTrue(server.isRegistered(poolOname),
                    "a DataSource that survived a failed registration must publish its pool"
                            + " MBean at the next successful registration");
            Assertions.assertEquals(poolOname, handleOf(ds),
                    "the handle kept after a successful registration must be the name the pool"
                            + " MBean is published under");
            Assertions.assertEquals(0,
                    ((Number) server.getAttribute(poolOname, "NumIdle")).longValue(),
                    "the published pool must answer for the live pool");

            // the container stops the DataSource: the same lifecycle releases
            // the publication as always, and nothing survives it
            server.unregisterMBean(fresh);
            Assertions.assertFalse(server.isRegistered(poolOname),
                    "a DataSource unregistered by its container must take its pool MBean with"
                            + " it, after an earlier registration of the same DataSource"
                            + " failed");
            Assertions.assertNull(handleOf(ds),
                    "the deregistration must drop the handle as it always does");
            Assertions.assertTrue(publishedFor(server, fresh).isEmpty(),
                    "the pool must publish nothing once its registration has been released");
        } finally {
            if (server.isRegistered(fresh)) {
                server.unregisterMBean(fresh);
            }
            if (server.isRegistered(blocked)) {
                server.unregisterMBean(blocked);
            }
            ds.close();
            ds.postDeregister();
            // test hygiene: the first, failed registration's publication loses
            // its handle under the second one, so a broken build would leave it
            // with no owner at all - take away anything that is still published
            for (ObjectName stale : publishedFor(server, blocked)) {
                server.unregisterMBean(stale);
            }
        }
    }
}

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
import java.util.Set;

import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * The registration handle of {@code DataSource} ({@code oname}) is the one
 * slot through which {@code close()}, {@code postRegister(false)} and
 * {@code postDeregister()} take the pool MBean back; the lifecycle promises
 * pin that it is released exactly once and that a name this data source never
 * published is never released. Both promises need the slot to name a publish
 * that is in effect - so the slot may be written only by a completed publish,
 * and no failure or skip of a (re-)registration may clear or replace a handle
 * it does not own.
 * <p>
 * Before this fix the slot was pre-written with the derived name before the
 * publish was even attempted and reassigned unconditionally from the helper's
 * answer, so a rejected re-publish of a name already held (a second call into
 * {@code registerJmx()}) cleared the live handle - orphaning that MBean
 * permanently, since neither a later close nor a deregistration could find it
 * any more - and a second registration under another name replaced the handle
 * and leaked the first publish the same way. (The third interleaving the
 * defect report listed - the connection sweep throwing after a completed
 * publish, whose exception path used to null the handle - is unreachable
 * today: the sweep's helpers swallow every checked failure and neither the
 * locks nor the queue iterators it uses throw. Its closure by construction,
 * the slot being written before the sweep and the exception path no longer
 * clearing it, is carried by review rather than by a test of its own.)
 */
class TestDataSourceReregistration extends JdbcPoolBaseTest {

    private static MBeanServer server() {
        return ManagementFactory.getPlatformMBeanServer();
    }

    /* A name no other test of the suite uses, so nothing collides by accident. */
    private static ObjectName uniqueName(String role) throws Exception {
        return new ObjectName("tomcat:type=ReregistrationTest,role=" + role
                + ",uid=" + System.nanoTime());
    }

    /* Test hygiene: take away anything a broken build left published for this
       registration - the uid is carried down into every name derived from it. */
    private static void sweep(MBeanServer server, ObjectName uidBearer) throws Exception {
        String uid = uidBearer.getKeyProperty("uid");
        for (ObjectName name : server.queryNames(new ObjectName("tomcat.jdbc:*"), null)) {
            if (uid.equals(name.getKeyProperty("uid"))) {
                server.unregisterMBean(name);
            }
        }
    }

    /* A data source exposing the two protected members the tests observe. */
    private static final class ProbeDataSource extends DataSource {
        ObjectName heldHandle() {
            return oname;
        }

        void registerAgain() {
            registerJmx();
        }
    }

    private ProbeDataSource newProbeDataSource() {
        ProbeDataSource ds = new ProbeDataSource();
        ds.setUrl(getUrl());
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.getPoolProperties().setJmxEnabled(true);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        return manage(ds);
    }

    @Test
    void aRefusedRePublishKeepsTheHeldHandle() throws Exception {
        MBeanServer server = server();
        ProbeDataSource ds = newProbeDataSource();
        ds.createPool();
        ObjectName dsName = uniqueName("duplicate-call");
        ObjectName poolOname = ds.createObjectName(dsName);
        try {
            server.registerMBean(new TestJmxRegistration.Container(ds), dsName);
            Assertions.assertTrue(server.isRegistered(poolOname),
                    "the registration must have published the pool MBean");
            Assertions.assertEquals(poolOname, ds.heldHandle(),
                    "the handle must name the publish in effect");

            // A direct second call aims at the name the held publish took;
            // the server refuses the duplicate. The refusal is not a
            // registration of this call - the handle of the publish that IS
            // in effect must survive it, or nothing could ever take that
            // MBean back.
            ds.registerAgain();
            Assertions.assertEquals(poolOname, ds.heldHandle(),
                    "a publish the server refused must leave the held handle "
                            + "in place: clearing it here orphaned the live "
                            + "MBean permanently");
            Assertions.assertTrue(server.isRegistered(poolOname),
                    "the held publish must still be registered");

            // The handle still works: the close takes the MBean back.
            ds.close(true);
            Assertions.assertFalse(server.isRegistered(poolOname),
                    "the close of a data source that kept its handle must "
                            + "take the pool MBean back");
            Assertions.assertNull(ds.heldHandle(), "the close must leave no handle behind");
        } finally {
            if (server.isRegistered(dsName)) {
                server.unregisterMBean(dsName);
            }
            ds.close();
            sweep(server, dsName);
        }
    }

    @Test
    void aSecondRegistrationNeitherOrphansTheFirstPublishNorTakesItsName() throws Exception {
        MBeanServer server = server();
        ProbeDataSource ds = newProbeDataSource();
        ds.createPool();
        ObjectName first = uniqueName("first");
        ObjectName second = uniqueName("second");
        ObjectName firstPoolOname = ds.createObjectName(first);
        ObjectName secondPoolOname = ds.createObjectName(second);
        try {
            server.registerMBean(new TestJmxRegistration.Container(ds), first);
            Assertions.assertTrue(server.isRegistered(firstPoolOname));
            Assertions.assertEquals(firstPoolOname, ds.heldHandle());

            // A second registration of the same data source has no handle of
            // its own, and postDeregister() cannot tell which registration it
            // is ending. It is refused so that its deregistration cannot
            // release the publish of the still-live first registration.
            Assertions.assertThrows(MBeanRegistrationException.class,
                    () -> server.registerMBean(new TestJmxRegistration.Container(ds), second),
                    "a second registration must be refused while the first publish is held");
            Assertions.assertFalse(server.isRegistered(secondPoolOname),
                    "a refused second registration must publish nothing of the pool");
            Assertions.assertEquals(firstPoolOname, ds.heldHandle(),
                    "the held handle must survive a refused second registration");

            // The surviving handle still works: deregistering the first
            // registration takes the MBean back exactly once.
            server.unregisterMBean(first);
            Assertions.assertFalse(server.isRegistered(firstPoolOname),
                    "the deregistration of the surviving registration must take "
                            + "the pool MBean back");
            Assertions.assertNull(ds.heldHandle(), "the release must clear the handle");
        } finally {
            if (server.isRegistered(second)) {
                server.unregisterMBean(second);
            }
            if (server.isRegistered(first)) {
                server.unregisterMBean(first);
            }
            ds.close();
            sweep(server, first);
            sweep(server, second);
            Set<ObjectName> leaked = server.queryNames(
                    new ObjectName("tomcat.jdbc:*,uid=" + first.getKeyProperty("uid")), null);
            Assertions.assertTrue(leaked.isEmpty(), "no MBean of this test may remain: " + leaked);
        }
    }

    @Test
    void aFailedSecondRegistrationDoesNotReleaseTheFirstRegistrationsPublish() throws Exception {
        MBeanServer server = server();
        ProbeDataSource ds = newProbeDataSource();
        ds.createPool();
        ObjectName first = uniqueName("alive");
        ObjectName second = uniqueName("failed-second");
        ObjectName firstPoolOname = ds.createObjectName(first);
        try {
            server.registerMBean(new TestJmxRegistration.Container(ds), first);
            Assertions.assertTrue(server.isRegistered(firstPoolOname),
                    "the first registration must have published the pool MBean");

            // The second registration of the same data source is refused
            // before the server gets a chance to fail it against another
            // name. A failed registration runs no postDeregister(), but it
            // must not take back the publish of the still-live first one.
            Assertions.assertThrows(MBeanRegistrationException.class,
                    () -> server.registerMBean(new TestJmxRegistration.Container(ds), second),
                    "the second registration must be refused");
            Assertions.assertTrue(server.isRegistered(firstPoolOname),
                    "a failed second registration must leave the pool MBean of "
                            + "the live first registration published");
            Assertions.assertEquals(firstPoolOname, ds.heldHandle(),
                    "a failed second registration must leave the handle of the "
                            + "live first registration in place");

            // The surviving handle still works: deregistration takes the MBean
            // back exactly once.
            server.unregisterMBean(first);
            Assertions.assertFalse(server.isRegistered(firstPoolOname),
                    "the deregistration of the surviving registration must still "
                            + "take the pool MBean back");
            Assertions.assertNull(ds.heldHandle(), "the release must clear the handle");
        } finally {
            if (server.isRegistered(first)) {
                server.unregisterMBean(first);
            }
            if (server.isRegistered(second)) {
                server.unregisterMBean(second);
            }
            ds.close();
            sweep(server, first);
            sweep(server, second);
        }
    }
}

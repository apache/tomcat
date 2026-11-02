/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.jdbc.pool;

import java.util.Hashtable;

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.jmx.JmxUtil;
import org.apache.tomcat.jdbc.pool.util.MessageFactory;


/**
 * A DataSource that can be instantiated through IoC and implements the DataSource interface since the DataSourceProxy
 * is used as a generic proxy. The DataSource simply wraps a {@link ConnectionPool} in order to provide a standard
 * interface to the user.
 */
public class DataSource extends DataSourceProxy implements javax.sql.DataSource, MBeanRegistration,
        org.apache.tomcat.jdbc.pool.jmx.ConnectionPoolMBean, javax.sql.ConnectionPoolDataSource {
    private static final Log log = LogFactory.getLog(DataSource.class);

    /**
     * Constructor for reflection only. A default set of pool properties will be created.
     */
    public DataSource() {
        super();
    }

    /**
     * Constructs a DataSource object wrapping a connection
     *
     * @param poolProperties The pool properties
     */
    public DataSource(PoolConfiguration poolProperties) {
        super(poolProperties);
    }

    /**
     * Unregisters the connection pool MBean before closing the pool, so a pool under teardown does not keep reporting
     * statistics under a live object name (and does not pin the closing pool, its credentials and its JDBC driver class
     * loader in the MBean server). Monitoring stops with this call, whichever way the close goes: with
     * <code>all=false</code> a connection that is currently borrowed keeps its database session and stays usable
     * until its holder returns it, but it is no longer reported by the pool MBean.<br>
     * {@inheritDoc}
     */
    @Override
    public void close(boolean all) {
        // This monitor serializes the registration lifecycle against the
        // closure: preRegister(), postRegister() and postDeregister() take it
        // too, and the flag-and-take step of DataSourceProxy.close(boolean)
        // and the publication step of pCreatePool() run under the same lock -
        // why a close therefore cannot slip in beside an in-flight publication
        // is told at preRegister().
        // The monitor is held for these steps only: the pool teardown below
        // disconnects every pooled session, an unbounded and uninterruptible
        // wait against a hung database, and a parked teardown must not park
        // the later borrows' publication step or the container's callbacks -
        // the same reason pool construction already runs outside it. A close
        // concurrent with this one finds the pool already taken and returns
        // instead of waiting for the in-flight teardown.
        final ConnectionPool p;
        synchronized (this) {
            releasePoolMBean();
            p = takePoolForClose();
        }
        closePool(p, all);
    }

    // The JMX operations below register the pool itself under the
    // tomcat.jdbc domain.
    /**
     * The JMX ObjectName for the connection pool MBean. The handle is checked,
     * cleared and released only under this data source's monitor. It is written
     * only by a publish that completed - it always names an MBean this data
     * source registered and still holds - and cleared only when that MBean is
     * unregistered, so no failure or skip of a (re-)registration can drop or
     * replace the handle the close and the deregistration callbacks release.
     */
    protected volatile ObjectName oname = null;

    /**
     * Whether the publish that the handle names was made by the registration
     * currently in flight - the {@link #preRegister(MBeanServer, ObjectName)}
     * whose {@link #postRegister(Boolean)} completion is still pending. Set by
     * {@link #registerJmx()} when a registration-driven publish is accepted,
     * and cleared with the handle and when a registration completes. The
     * server reports a failed registration to {@link #postRegister(Boolean)}
     * without saying what that registration published; a registration that
     * published nothing - because an earlier registration's handle was held -
     * has nothing of its own to undo, and this is how that is known.
     */
    private volatile boolean publishedByRegistration = false;

    /**
     * The name derived from the registration currently in flight, set and
     * cleared by {@link #preRegister(MBeanServer, ObjectName)} around its call
     * into {@link #registerJmx()}, under this data source's monitor. A second
     * registration must read its publish target from here rather than from the
     * handle slot, so it can never overwrite the handle of a publish that is
     * already held.
     */
    private volatile ObjectName pendingName = null;

    /**
     * Unregisters the underlying connection pool mbean.<br>
     * {@inheritDoc}
     */
    @Override
    public synchronized void postDeregister() {
        // Under the monitor that close() and the registration callbacks take,
        // so the container's deregistration and a concurrent close release the
        // pool MBean exactly once: whichever of them runs second finds the
        // handle already gone and unregisters nothing - not even a name some
        // third component has published in the meantime.
        releasePoolMBean();
    }

    /**
     * Releases the pool MBean that {@link #preRegister(MBeanServer, ObjectName)} published when the MBean server did
     * not complete this DataSource's own registration. Per the {@link MBeanRegistration} contract a {@code false}
     * {@code registrationDone} means that the registration phase failed, and after a failed registration the server
     * does not call {@link #postDeregister()}: this callback is the only chance to undo that side effect. Leaving the
     * pool MBean registered would keep a pool nobody manages reporting statistics and notifications, and would pin the
     * pool - with its properties, its credentials and its JDBC driver class loader - in the MBean server. Only a
     * publish this registration made is undone: a registration that published nothing - because an earlier
     * registration still held the handle, or because it derived no name at all - releases nothing, since the handle it
     * finds in place belongs to that earlier registration and is that registration's to give back.<br>
     * {@inheritDoc}
     */
    @Override
    public synchronized void postRegister(Boolean registrationDone) {
        if (Boolean.TRUE.equals(registrationDone)) {
            publishedByRegistration = false;
            return;
        }
        if (publishedByRegistration) {
            releasePoolMBean();
        }
        publishedByRegistration = false;
    }

    /**
     * no-op<br>
     * {@inheritDoc}
     */
    @Override
    public void preDeregister() throws Exception {
        // NOOP
    }

    /**
     * If the connection pool MBean exists, it will be registered during this operation. A pool MBean this data source
     * already published under an earlier registration is kept, and this registration then publishes nothing (see
     * {@link #registerJmx()}).<br>
     * {@inheritDoc}
     */
    @Override
    public synchronized ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
        // Under the monitor close() takes, so the publication below cannot
        // overlap a closure: a close that ran completely between registerJmx()
        // reading the registration name and the server actually accepting the
        // publish would unregister a name the pool never took and close the
        // pool, and the publish would then register the dead pool's MBean
        // under a live name that no later close() or postDeregister() - which
        // only release a held handle - could ever take back.
        try {
            if (isJmxEnabled() && oname == null) {
                final ObjectName derived = createObjectName(name);
                if (derived != null) {
                    try {
                        pendingName = derived;
                        registerJmx();
                    } finally {
                        pendingName = null;
                    }
                }
            } else if (isJmxEnabled() && oname != null) {
                log.warn(MessageFactory.get("dataSource.mbeanAlreadyRegistered", oname, name));
            }
        } catch (MalformedObjectNameException x) {
            // Nothing has been published and the handle slot was not touched:
            // there is nothing here to take back.
            log.error(MessageFactory.get("dataSource.unableToCreateObjectName"), x);
        } catch (RuntimeException x) {
            // Never break the caller's MBean registration because of
            // pool-side state (for instance a pool that has not been
            // created yet); preRegister throwing aborts the whole
            // registration in the MBean server. The handle slot is left as
            // registerJmx() last wrote it: a RuntimeException after a
            // completed publish - from a later step than the publish itself -
            // must not lose the handle of a live MBean, whose release then
            // belongs to the close and the deregistration callbacks as ever.
            log.error(MessageFactory.get("dataSource.unableToRegisterMBean"), x);
        }
        return name;
    }

    /**
     * Creates the ObjectName for the ConnectionPoolMBean object to be registered
     *
     * @param original the ObjectName for the DataSource
     *
     * @return the ObjectName for the ConnectionPoolMBean
     *
     * @throws MalformedObjectNameException Invalid object name
     */
    public ObjectName createObjectName(ObjectName original) throws MalformedObjectNameException {
        String domain = ConnectionPool.POOL_JMX_DOMAIN;
        Hashtable<String, String> properties = original.getKeyPropertyList();
        String origDomain = original.getDomain();
        properties.put("type", "ConnectionPool");
        properties.put("class", this.getClass().getName());
        if (original.getKeyProperty("path") != null || properties.get("context") != null) {
            // This ensures that if the registration came from tomcat, we're not losing
            // the unique domain, but putting that into as an engine attribute
            properties.put("engine", origDomain);
        }
        ObjectName name = new ObjectName(domain, properties);
        return name;
    }

    /**
     * Registers the ConnectionPoolMBean under a unique name based on the ObjectName for the DataSource. A pool that has
     * not been created yet (or was already closed) has no MBean to publish; the registration is then skipped rather
     * than failing, since the caller of {@link #preRegister(MBeanServer, ObjectName)} must not see an exception over
     * pool-side lifecycle state. A publish the MBean server refused - because the name is already in use by another
     * MBean, for instance - is likewise not this pool's registration. In neither case is a handle this data source
     * already holding disturbed: it names a publish that is still in effect, and dropping it here would leave that
     * MBean held by nothing - neither a later close() nor a deregistration could ever take it back.
     */
    protected synchronized void registerJmx() {
        // Runs under this data source's monitor - from preRegister() or a
        // direct call - the monitor close(boolean) also takes, so no closure
        // can interleave with the publish below.
        final ConnectionPool p = pool;
        // Read the pool's JMX object only once: a pool that was closed before
        // this registration has given it up, and one read that saw it must not
        // be followed by another that observes the null - a registration
        // attempt against that null would only fail inside the helper, with a
        // spurious error log
        final org.apache.tomcat.jdbc.pool.jmx.ConnectionPool jmx = p == null ? null : p.getJmxPool();
        // The name to publish: the one the registration in flight derived, or,
        // for a direct call outside a registration, the handle this data
        // source holds. A direct call that still holds a live publish then
        // aims at the name that publish took, which the server refuses as a
        // duplicate - and the handle survives the refusal, as it must.
        final ObjectName name = pendingName == null ? oname : pendingName;
        if (jmx == null) {
            // Nothing to publish; the handle slot, which this call did not
            // write, is left as it stands.
            log.warn(MessageFactory.get("dataSource.mbeanNotRegistered", name));
            return;
        }
        final ObjectName published = JmxUtil.registerJmx(name, null, jmx);
        if (published != null) {
            // Recorded before the connection sweep below: a publish the server
            // accepted is held from the moment it happened, whatever a later
            // step makes of it.
            this.oname = published;
            // Only a registration-driven publish - one made from
            // preRegister(), which sets pendingName around its call - has a
            // registration that can fail and undo it in postRegister(false);
            // a publish from a direct call belongs to no registration, and
            // releasing it on a later registration failure would tear down a
            // publication that registration never made.
            publishedByRegistration = pendingName != null;
            // A connection publishes itself below this name when its physical
            // connection is established; the connections the pool was already
            // holding could not do that while this name did not exist yet, so
            // they are published now. A publish the server refused registers
            // nothing of its own and has no connections to publish either.
            p.registerConnectionMBeans();
        }
    }

    /**
     * Unregisters the ConnectionPoolMBean published under the given name. The
     * name is the registration handle captured by the caller, which holds this
     * data source's monitor: the handle is checked, cleared and released as
     * one step there, so it never reaches this method twice and a name this
     * data source never published is never released.
     *
     * @param name the ObjectName the pool MBean was published under, or
     *            {@code null} to release nothing
     */
    protected void unregisterJmx(ObjectName name) {
        JmxUtil.unregisterJmx(name);
    }

    /**
     * Releases the registration handle and unregisters the pool MBean
     * published under the name it points to, as one step under this data
     * source's monitor. Every path that gives the handle up runs through
     * here - {@link #close(boolean)}, the {@link #postRegister(Boolean)} of a
     * failed registration and the container's {@link #postDeregister()} - so
     * of all of them the release is performed at most once: whichever runs
     * second finds the handle gone and unregisters nothing. The unregistration
     * is then best-effort and cannot break the caller: the handle is already
     * taken, so a failure of the unregistration - a subclass that throws from
     * {@link #unregisterJmx(ObjectName)}, or an {@code Error} surfacing
     * through it - may cost a registered MBean, but the close that asked for
     * this release still closes, and no later path re-attempts a name this
     * data source has given up.
     */
    private void releasePoolMBean() {
        final ObjectName name = oname;
        oname = null;
        publishedByRegistration = false;
        if (name != null) {
            try {
                unregisterJmx(name);
            } catch (Throwable t) {
                // Logged, never propagated: an escaping exception here would
                // abort the caller between taking the handle and setting the
                // terminal flag, leaving a pool still running under a caller
                // that believes it closed - with the handle already gone, so
                // no later path could ever take the MBean back either.
                log.error(MessageFactory.get("dataSource.unableToUnregisterMBean", name), t);
            }
        }
    }
}

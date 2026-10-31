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
     * Unregisters the connection pool MBean before closing the pool, so a dead pool does not keep reporting stale
     * statistics under a live object name (and does not pin the closed pool, its credentials and its JDBC driver class
     * loader in the MBean server).<br>
     * {@inheritDoc}
     */
    @Override
    public synchronized void close(boolean all) {
        // This monitor serializes the whole registration lifecycle against the
        // closure: preRegister(), postRegister() and postDeregister() take it
        // too, and DataSourceProxy.close(boolean) and the publication step of
        // pCreatePool() below run under the same lock. A close therefore
        // cannot slip in beside an in-flight publication - between its read of
        // the registration name and the publish itself - where its own
        // unregister would find nothing to take back and the pool MBean would
        // then be published onto the dead pool under a live name that no later
        // close() or postDeregister() could ever take back.
        releasePoolMBean();
        super.close(all);
    }


    // The JMX operations below register the pool itself under the
    // tomcat.jdbc domain.
    /**
     * The JMX ObjectName for the connection pool MBean. The handle is checked,
     * cleared and released only under this data source's monitor.
     */
    protected volatile ObjectName oname = null;

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
     * pool - with its properties, its credentials and its JDBC driver class loader - in the MBean server.<br>
     * {@inheritDoc}
     */
    @Override
    public synchronized void postRegister(Boolean registrationDone) {
        // The same check-and-clear-unregister of the registration handle as
        // postDeregister() and close(), under the same monitor: a handle this
        // bean still holds at a failed registration is exactly the publish
        // preRegister() made, since registerJmx() keeps a name only for an
        // accepted publish. Clearing it here also makes a later close() or
        // postDeregister() skip the unregister, so the cleanup is never
        // performed twice.
        if (!Boolean.TRUE.equals(registrationDone)) {
            releasePoolMBean();
        }
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
     * If the connection pool MBean exists, it will be registered during this operation.<br>
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
            if (isJmxEnabled()) {
                this.oname = createObjectName(name);
                if (oname != null) {
                    registerJmx();
                }
            }
        } catch (MalformedObjectNameException x) {
            this.oname = null;
            log.error(MessageFactory.get("dataSource.unableToCreateObjectName"), x);
        } catch (RuntimeException x) {
            // Never break the caller's MBean registration because of
            // pool-side state (for instance a pool that has not been
            // created yet); preRegister throwing aborts the whole
            // registration in the MBean server.
            this.oname = null;
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
     * MBean, for instance - is likewise not this pool's registration.
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
        if (jmx == null) {
            log.warn(MessageFactory.get("dataSource.mbeanNotRegistered", oname));
            // oname is the handle postDeregister() unregisters; do not keep
            // one that was never registered
            this.oname = null;
            return;
        }
        // oname is only kept for an actually published MBean: the helper
        // hands back the name of its own publish, or null when the server
        // refused it, so a name a foreign MBean owns is never released as
        // if it were this pool's registration
        this.oname = JmxUtil.registerJmx(oname, null, jmx);
        if (this.oname != null) {
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
     * second finds the handle gone and unregisters nothing.
     */
    private void releasePoolMBean() {
        final ObjectName name = oname;
        oname = null;
        if (name != null) {
            unregisterJmx(name);
        }
    }
}

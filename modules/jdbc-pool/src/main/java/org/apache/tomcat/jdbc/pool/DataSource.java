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
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.jmx.JmxUtil;
import org.apache.tomcat.jdbc.pool.util.ExceptionUtils;
import org.apache.tomcat.jdbc.pool.util.MessageFactory;


/**
 * A DataSource that can be instantiated through IoC and implements the DataSource interface since the DataSourceProxy
 * is used as a generic proxy. The DataSource simply wraps a {@link ConnectionPool} in order to provide a standard
 * interface to the user.
 * <p>
 * A JMX registration made before the pool exists publishes nothing, because there is no pool MBean to publish. Create
 * the pool before registering the data source, or register it again after the pool has been created, to have the pool
 * monitored.
 * <p>
 * Deregistering a published data source releases the pool MBean and the connection and interceptor MBeans published
 * below it, but it does not close the pool. A JMX-enabled pool that is no longer wanted must therefore be closed
 * before the data source is dropped: while the pool MBean is registered it holds the pool, and the connection MBeans
 * hold the pooled connections. Abandoning a registered pool without {@link #close(boolean)} keeps the pool, its
 * pooled sessions and its JDBC driver class loader reachable for the life of the JVM.
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
        // The monitor serializes the registration lifecycle against the
        // closure. It is not held across the pool teardown, which can block on
        // a driver call.
        final ConnectionPool p;
        synchronized (this) {
            releasePoolMBean();
            p = takePoolForClose();
        }
        closePool(p, all);
    }

    /**
     * The JMX ObjectName for the connection pool MBean. Written only by a
     * completed publish, cleared only when that MBean is released, and
     * accessed under this data source's monitor.
     */
    protected volatile ObjectName oname = null;

    /**
     * Whether the publish named by {@link #oname} was made by the registration
     * currently in flight. Set by {@link #registerJmx()} when a
     * registration-driven publish is accepted and cleared when that
     * registration completes.
     */
    private volatile boolean publishedByRegistration = false;

    /**
     * The name derived by the registration currently in flight, used by
     * {@link #preRegister(MBeanServer, ObjectName)} so a publish cannot
     * overwrite the handle of a registration that is already held.
     */
    private volatile ObjectName pendingName = null;

    /**
     * Unregisters the underlying connection pool mbean.<br>
     * {@inheritDoc}
     */
    @Override
    public synchronized void postDeregister() {
        releasePoolMBean();
    }

    /**
     * Releases the pool MBean published by this registration when the MBean server did not complete it. A failed
     * registration does not run {@link #postDeregister()}, so this is the only chance to undo a publish this
     * registration made. A registration that published nothing releases nothing.<br>
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
    }

    /**
     * If the connection pool MBean exists, it is registered during this operation. A second registration is refused
     * while this data source already holds a publication.<br>
     * {@inheritDoc}
     */
    @Override
    public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
        // The publish runs under the monitor close() takes. The connection
        // sweep runs outside it, since it can block on a connection lock.
        ConnectionPool sweepPool = null;
        try {
            synchronized (this) {
                if (oname != null) {
                    // A second registration has no handle of its own, and
                    // postDeregister() cannot tell which registration it is
                    // ending. Refuse it rather than let its deregistration
                    // release the live registration's MBean.
                    String message = MessageFactory.get("dataSource.mbeanAlreadyRegistered", oname, name);
                    throw new MBeanRegistrationException(new IllegalStateException(message), message);
                }
                if (isJmxEnabled()) {
                    final ObjectName derived = createObjectName(name);
                    if (derived != null) {
                        pendingName = derived;
                        try {
                            sweepPool = publishJmx();
                        } finally {
                            pendingName = null;
                        }
                    }
                }
            }
        } catch (MalformedObjectNameException x) {
            // Nothing has been published and the handle slot was not touched:
            // there is nothing here to take back.
            log.error(MessageFactory.get("dataSource.unableToCreateObjectName"), x);
        } catch (MBeanRegistrationException x) {
            // The second registration is refused; the first registration's
            // publish is left exactly as it stands.
            throw x;
        } catch (RuntimeException x) {
            // Never break the caller's MBean registration because of
            // pool-side state (for instance a pool that has not been
            // created yet); preRegister throwing aborts the whole
            // registration in the MBean server. The handle slot is left as
            // publishJmx() last wrote it: a RuntimeException after a
            // completed publish - from a later step than the publish itself -
            // must not lose the handle of a live MBean, whose release then
            // belongs to the close and the deregistration callbacks as ever.
            log.error(MessageFactory.get("dataSource.unableToRegisterMBean"), x);
        }
        if (sweepPool != null) {
            try {
                sweepPool.registerConnectionMBeans();
            } catch (RuntimeException x) {
                log.error(MessageFactory.get("dataSource.unableToRegisterMBean"), x);
            }
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
     * Registers the ConnectionPoolMBean under a unique name derived from the DataSource's ObjectName. A pool that has
     * not been created, or a publish the server refused, is skipped without disturbing a handle this data source
     * already holds.
     */
    protected void registerJmx() {
        final ConnectionPool p;
        synchronized (this) {
            p = publishJmx();
        }
        if (p != null) {
            p.registerConnectionMBeans();
        }
    }

    /**
     * Publishes the pool MBean under this data source's monitor and returns
     * the pool whose connections must then be published, or {@code null} when
     * nothing was published. The caller must hold this monitor; the
     * connection sweep is the caller's to run outside it.
     *
     * @return the pool whose connections need publishing, or {@code null}
     */
    protected ConnectionPool publishJmx() {
        final ConnectionPool p = pool;
        // Read the pool's JMX object once: a pool closed in the meantime has
        // given it up.
        final org.apache.tomcat.jdbc.pool.jmx.ConnectionPool jmx = p == null ? null : p.getJmxPool();
        // A registration in flight publishes the name it derived. A direct
        // call publishes the handle it already holds, so a duplicate attempt
        // is refused and the handle survives.
        final ObjectName name = pendingName == null ? oname : pendingName;
        if (jmx == null) {
            log.warn(MessageFactory.get("dataSource.mbeanNotRegistered", name));
            return null;
        }
        final ObjectName published = JmxUtil.registerJmx(name, null, jmx);
        if (published != null) {
            this.oname = published;
            // Only a publish made from preRegister() can be undone by a
            // failed registration in postRegister(false).
            publishedByRegistration = pendingName != null;
            return p;
        }
        return null;
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
     * Releases the MBeans published below the pool MBean - the connections the pool holds and the interceptors of their
     * chains - so that deregistering this data source does not leave them pinning a pool that stays live. The pool and
     * its sessions are untouched, and a later registration publishes the children again. The release is best effort:
     * JMX is a management channel, so a failure here is logged and does not stop the pool MBean from being released.
     */
    private void releaseConnectionMBeans() {
        ConnectionPool p = pool;
        if (p == null) {
            return;
        }
        try {
            p.unregisterConnectionMBeans();
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(MessageFactory.get("dataSource.unableToUnregisterConnectionMBeans"), t);
        }
    }

    /**
     * Releases the registration handle and unregisters the pool MBean it names, under this data source's monitor. Every
     * release path goes through here, so the handle is given up at most once. The unregistration itself is best effort
     * and never breaks the caller.
     */
    private void releasePoolMBean() {
        final ObjectName name = oname;
        oname = null;
        publishedByRegistration = false;
        if (name != null) {
            releaseConnectionMBeans();
            try {
                unregisterJmx(name);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.error(MessageFactory.get("dataSource.unableToUnregisterMBean", name), t);
            }
        }
    }
}

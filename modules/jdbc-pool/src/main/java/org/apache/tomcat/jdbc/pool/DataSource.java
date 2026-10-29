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
        // Same monitor as DataSourceProxy.close(boolean), so the
        // check-and-clear of the registration handle is atomic against a
        // concurrent close.
        if (oname != null) {
            unregisterJmx();
            oname = null;
        }
        super.close(all);
    }


    // The JMX operations below register the pool itself under the
    // tomcat.jdbc domain.
    /**
     * The JMX ObjectName for the connection pool MBean.
     */
    protected volatile ObjectName oname = null;

    /**
     * Unregisters the underlying connection pool mbean.<br>
     * {@inheritDoc}
     */
    @Override
    public void postDeregister() {
        if (oname != null) {
            unregisterJmx();
            oname = null;
        }
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
    public void postRegister(Boolean registrationDone) {
        // The same check-and-clear of the registration handle as
        // postDeregister() and close(): a handle this bean still holds at a
        // failed registration is exactly the publish preRegister() made, since
        // registerJmx() keeps a name only for an accepted publish. Clearing it
        // here also makes a later close() or postDeregister() skip the
        // unregister, so the cleanup is never performed twice.
        if (!Boolean.TRUE.equals(registrationDone) && oname != null) {
            unregisterJmx();
            oname = null;
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
    public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
        try {
            if (isJmxEnabled()) {
                this.oname = createObjectName(name);
                if (oname != null) {
                    registerJmx();
                }
            }
        } catch (MalformedObjectNameException x) {
            this.oname = null;
            log.error("Unable to create object name for JDBC pool.", x);
        } catch (RuntimeException x) {
            // Never break the caller's MBean registration because of
            // pool-side state (for instance a pool that has not been
            // created yet); preRegister throwing aborts the whole
            // registration in the MBean server.
            this.oname = null;
            log.error("Unable to register JDBC pool MBean.", x);
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
    protected void registerJmx() {
        final ConnectionPool p = pool;
        // Read the pool's JMX object only once: close() gives it up
        // concurrently, and a second read can observe the null the first one
        // did not - a registration attempt against that null would only fail
        // inside the helper, with a spurious error log
        final org.apache.tomcat.jdbc.pool.jmx.ConnectionPool jmx = p == null ? null : p.getJmxPool();
        if (jmx == null) {
            log.warn("JDBC pool MBean [" + oname + "] not registered: the pool " +
                    "has not been created yet or has already been closed.");
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
     * Unregisters the ConnectionPoolMBean from JMX.
     */
    protected void unregisterJmx() {
        JmxUtil.unregisterJmx(oname);
    }
}

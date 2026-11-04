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

import java.sql.SQLException;

import javax.sql.XAConnection;

import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * XA-enabled data source for distributed transaction support.
 * <p>
 * Note that XA support is not provided by this class itself: XA capability comes from the data source configured
 * through the {@code dataSource} or {@code dataSourceJNDI} attribute, which must implement
 * {@link javax.sql.XADataSource} (see {@link PoolConfiguration#setDataSource(Object)}). A pool configured with only
 * {@code driverClassName} and {@code url} pools plain JDBC connections; a request for an XA connection from such a pool
 * fails with an explicit message naming the missing configuration rather than later, inside the transaction manager.
 * <p>
 * What the {@code getXAConnection} methods return is nevertheless not that configured data source's own handle: it is
 * a session borrowed from the pool, whose {@code close()} returns the session to the pool instead of closing the
 * physical connection. The deviations from the {@code javax.sql} contract that follow from this are stated on those
 * methods.
 */
public class XADataSource extends DataSource implements javax.sql.XADataSource {

    /**
     * Constructor for reflection only. A default set of pool properties will be created.
     */
    public XADataSource() {
        super();
    }

    /**
     * Constructs a DataSource object wrapping a connection
     *
     * @param poolProperties The pool configuration
     */
    public XADataSource(PoolConfiguration poolProperties) {
        super(poolProperties);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation first checks that the pool can provide XA connections at all, and fails with an explicit
     * configuration hint when it cannot.
     * <p>
     * Deviation from the {@code javax.sql} contract: the returned {@link XAConnection} is a handle borrowed from the
     * pool, not a freshly opened physical connection, so under load this call blocks waiting for a free session or
     * fails with pool exhaustion rather than with a database error. Its {@link java.sql.Connection#close() close()}
     * returns the session to the pool instead of closing the physical connection; every XA branch started through the
     * returned {@link javax.transaction.xa.XAResource} must therefore be completed - committed or rolled back - before
     * {@code close()}, and neither the handle nor its {@code XAResource} may be used after it. A handle closed while a
     * branch stands prepared parks that in-doubt branch, and the database locks held for it, on a session the pool
     * hands to another borrower: the pool's return-time rollback unwinds only local work and can never resolve a
     * prepared branch.
     * <p>
     * The connection obtained from the returned handle's
     * {@link javax.sql.PooledConnection#getConnection()} is the driver's raw logical session, not a pool-managed
     * connection: the pool's interceptors and features do not apply to it, and closing it invalidates the session
     * under the pool's own notion of a live connection. Release the borrowed session by closing the handle, not that
     * connection.
     */
    @Override
    public XAConnection getXAConnection() throws SQLException {
        requireXABacking();
        return super.getXAConnection();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation first checks that the pool can provide XA connections at all, and fails with an explicit
     * configuration hint when it cannot. The returned handle deviates from the {@code javax.sql} contract as described
     * on {@link #getXAConnection()}, and carries one deviation of its own: the given credentials are honored only when {@link PoolConfiguration#setAlternateUsernameAllowed(boolean)
     * alternateUsernameAllowed} is enabled, which it is not by default; otherwise the borrow proceeds under the pool's
     * configured user name, exactly as {@link #getConnection(String, String)} does, and every XA branch started
     * through this handle's {@code XAResource} runs under that identity, not the one passed in here.
     *
     * @param username The user name
     * @param password The password
     */
    @Override
    public XAConnection getXAConnection(String username, String password) throws SQLException {
        requireXABacking();
        return super.getXAConnection(username, password);
    }

    /**
     * Verifies that the pool is backed by an XA data source, matching the condition under which
     * {@link PooledConnection} creates XA connections.
     *
     * @throws SQLException if the pool cannot hold XA connections, with a message naming the configuration that is
     *                          missing
     */
    private void requireXABacking() throws SQLException {
        Object ds = getPoolProperties().getDataSource();
        if (ds instanceof javax.sql.XADataSource) {
            return;
        }
        if (ds == null) {
            // A configured JNDI name only reaches the pool as a data source
            // through the DataSourceFactory, which fails pool creation when
            // the name cannot be resolved; a name set on a programmatically
            // configured pool therefore never becomes a data source at all.
            String jndiName = getPoolProperties().getDataSourceJNDI();
            if (jndiName != null) {
                throw new SQLException(MessageFactory.get("xaDataSource.jndiNotResolved", getName(), jndiName));
            }
            throw new SQLException(MessageFactory.get("xaDataSource.noDataSource", getName()));
        }
        throw new SQLException(MessageFactory.get("xaDataSource.dataSourceNotXA", getName(),
                ds.getClass().getName()));
    }
}

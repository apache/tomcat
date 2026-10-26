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

/**
 * XA-enabled data source for distributed transaction support.
 * <p>
 * Note that XA support is not provided by this class itself: XA
 * connections are obtained from the data source configured through the
 * {@code dataSource} or {@code dataSourceJNDI} attribute, which must
 * implement {@link javax.sql.XADataSource} (see
 * {@link PoolConfiguration#setDataSource(Object)}). A pool configured
 * with only {@code driverClassName} and {@code url} pools plain JDBC
 * connections; a request for an XA connection from such a pool fails
 * with an explicit message naming the missing configuration rather
 * than later, inside the transaction manager.
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
     * @param poolProperties The pool configuration
     */
    public XADataSource(PoolConfiguration poolProperties) {
        super(poolProperties);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation first checks that the pool can provide XA
     * connections at all, and fails with an explicit configuration hint
     * when it cannot.
     */
    @Override
    public XAConnection getXAConnection() throws SQLException {
        requireXABacking();
        return super.getXAConnection();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation first checks that the pool can provide XA
     * connections at all, and fails with an explicit configuration hint
     * when it cannot.
     * @param username The user name
     * @param password The password
     */
    @Override
    public XAConnection getXAConnection(String username, String password) throws SQLException {
        requireXABacking();
        return super.getXAConnection(username, password);
    }

    /**
     * Verifies that the pool is backed by an XA data source, matching
     * the condition under which {@link PooledConnection} creates XA
     * connections.
     * @throws SQLException if the pool cannot hold XA connections, with
     *         a message naming the configuration that is missing
     */
    private void requireXABacking() throws SQLException {
        Object ds = getPoolProperties().getDataSource();
        if (ds instanceof javax.sql.XADataSource) {
            return;
        }
        if (ds == null) {
            throw new SQLException("The connection pool [" + getName() + "] cannot provide XA connections: it is"
                    + " configured with a JDBC driver and URL (or the 'dataSourceJNDI' lookup did not resolve to a"
                    + " data source), so the pool holds plain JDBC connections. Set the 'dataSource' or"
                    + " 'dataSourceJNDI' attribute to a javax.sql.XADataSource provided by the JDBC driver.");
        }
        throw new SQLException("The connection pool [" + getName() + "] cannot provide XA connections: the data"
                + " source configured through the 'dataSource' or 'dataSourceJNDI' attribute ("
                + ds.getClass().getName() + ") does not implement javax.sql.XADataSource.");
    }
}

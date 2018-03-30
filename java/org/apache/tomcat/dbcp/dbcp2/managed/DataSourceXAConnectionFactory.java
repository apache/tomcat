/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.dbcp.dbcp2.managed;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.PooledConnection;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.TransactionManager;
import javax.transaction.xa.XAResource;

/**
 * An implementation of XAConnectionFactory which uses a real XADataSource to obtain connections and XAResources.
 *
 * @author Dain Sundstrom
 * @since 2.0
 */
public class DataSourceXAConnectionFactory implements XAConnectionFactory {
    private final TransactionRegistry transactionRegistry;
    private final XADataSource xaDataSource;
    private String username;
    private String password;

    /**
     * Creates an DataSourceXAConnectionFactory which uses the specified XADataSource to create database
     * connections.  The connections are enlisted into transactions using the specified transaction manager.
     *
     * @param transactionManager the transaction manager in which connections will be enlisted
     * @param xaDataSource the data source from which connections will be retrieved
     */
    public DataSourceXAConnectionFactory(final TransactionManager transactionManager, final XADataSource xaDataSource) {
        this(transactionManager, xaDataSource, null, null);
    }

    /**
     * Creates an DataSourceXAConnectionFactory which uses the specified XADataSource to create database
     * connections.  The connections are enlisted into transactions using the specified transaction manager.
     *
     * @param transactionManager the transaction manager in which connections will be enlisted
     * @param xaDataSource the data source from which connections will be retrieved
     * @param username the username used for authenticating new connections or null for unauthenticated
     * @param password the password used for authenticating new connections
     */
    public DataSourceXAConnectionFactory(final TransactionManager transactionManager, final XADataSource xaDataSource, final String username, final String password) {
        if (transactionManager == null) {
            throw new NullPointerException("transactionManager is null");
        }
        if (xaDataSource == null) {
            throw new NullPointerException("xaDataSource is null");
        }

        this.transactionRegistry = new TransactionRegistry(transactionManager);
        this.xaDataSource = xaDataSource;
        this.username = username;
        this.password = password;
    }

    /**
     * Gets the username used to authenticate new connections.
     * @return the user name or null if unauthenticated connections are used
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the username used to authenticate new connections.
     * @param username the username used for authenticating the connection or null for unauthenticated
     */
    public void setUsername(final String username) {
        this.username = username;
    }

    /**
     * Sets the password used to authenticate new connections.
     * @param password the password used for authenticating the connection or null for unauthenticated
     */
    public void setPassword(final String password) {
        this.password = password;
    }

    @Override
    public TransactionRegistry getTransactionRegistry() {
        return transactionRegistry;
    }

    @Override
    public Connection createConnection() throws SQLException {
        // create a new XAConnection
        XAConnection xaConnection;
        if (username == null) {
            xaConnection = xaDataSource.getXAConnection();
        } else {
            xaConnection = xaDataSource.getXAConnection(username, password);
        }

        // get the real connection and XAResource from the connection
        final Connection connection = xaConnection.getConnection();
        final XAResource xaResource = xaConnection.getXAResource();

        // register the xa resource for the connection
        transactionRegistry.registerConnection(connection, xaResource);

        // The Connection we're returning is a handle on the XAConnection.
        // When the pool calling us closes the Connection, we need to
        // also close the XAConnection that holds the physical connection.
        xaConnection.addConnectionEventListener(new ConnectionEventListener() {

            @Override
            public void connectionClosed(final ConnectionEvent event) {
                final PooledConnection pc = (PooledConnection) event.getSource();
                pc.removeConnectionEventListener(this);
                try {
                    pc.close();
                } catch (final SQLException e) {
                    System.err.println("Failed to close XAConnection");
                    e.printStackTrace();
                }
            }

            @Override
            public void connectionErrorOccurred(final ConnectionEvent event) {
                connectionClosed(event);
            }
        });


        return connection;
    }
}

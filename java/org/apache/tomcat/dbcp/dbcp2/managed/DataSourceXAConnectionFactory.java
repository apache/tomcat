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
import java.util.Objects;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.PooledConnection;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.TransactionManager;
import javax.transaction.xa.XAResource;

import org.apache.tomcat.dbcp.dbcp2.Utils;

/**
 * An implementation of XAConnectionFactory which uses a real XADataSource to obtain connections and XAResources.
 *
 * @since 2.0
 */
public class DataSourceXAConnectionFactory implements XAConnectionFactory {
    private final TransactionRegistry transactionRegistry;
    private final XADataSource xaDataSource;
    private String userName;
    private char[] userPassword;

    /**
     * Creates an DataSourceXAConnectionFactory which uses the specified XADataSource to create database connections.
     * The connections are enlisted into transactions using the specified transaction manager.
     *
     * @param transactionManager
     *            the transaction manager in which connections will be enlisted
     * @param xaDataSource
     *            the data source from which connections will be retrieved
     */
    public DataSourceXAConnectionFactory(final TransactionManager transactionManager, final XADataSource xaDataSource) {
        this(transactionManager, xaDataSource, null, (char[]) null);
    }

    /**
     * Creates an DataSourceXAConnectionFactory which uses the specified XADataSource to create database connections.
     * The connections are enlisted into transactions using the specified transaction manager.
     *
     * @param transactionManager
     *            the transaction manager in which connections will be enlisted
     * @param xaDataSource
     *            the data source from which connections will be retrieved
     * @param userName
     *            the user name used for authenticating new connections or null for unauthenticated
     * @param userPassword
     *            the password used for authenticating new connections
     */
    public DataSourceXAConnectionFactory(final TransactionManager transactionManager, final XADataSource xaDataSource,
            final String userName, final char[] userPassword) {
        Objects.requireNonNull(transactionManager, "transactionManager is null");
        Objects.requireNonNull(xaDataSource, "xaDataSource is null");
        this.transactionRegistry = new TransactionRegistry(transactionManager);
        this.xaDataSource = xaDataSource;
        this.userName = userName;
        this.userPassword = userPassword;
    }

    /**
     * Creates an DataSourceXAConnectionFactory which uses the specified XADataSource to create database connections.
     * The connections are enlisted into transactions using the specified transaction manager.
     *
     * @param transactionManager
     *            the transaction manager in which connections will be enlisted
     * @param xaDataSource
     *            the data source from which connections will be retrieved
     * @param userName
     *            the user name used for authenticating new connections or null for unauthenticated
     * @param userPassword
     *            the password used for authenticating new connections
     */
    public DataSourceXAConnectionFactory(final TransactionManager transactionManager, final XADataSource xaDataSource,
            final String userName, final String userPassword) {
        this(transactionManager, xaDataSource, userName, Utils.toCharArray(userPassword));
    }

    /**
     * Gets the user name used to authenticate new connections.
     *
     * @return the user name or null if unauthenticated connections are used
     */
    public String getUsername() {
        return userName;
    }

    /**
     * Sets the user name used to authenticate new connections.
     *
     * @param userName
     *            the user name used for authenticating the connection or null for unauthenticated
     */
    public void setUsername(final String userName) {
        this.userName = userName;
    }

    /**
     * Sets the password used to authenticate new connections.
     *
     * @param userPassword
     *            the password used for authenticating the connection or null for unauthenticated.
     * @since 2.4.0
     */
    public void setPassword(final char[] userPassword) {
        this.userPassword = userPassword;
    }

    /**
     * Sets the password used to authenticate new connections.
     *
     * @param userPassword
     *            the password used for authenticating the connection or null for unauthenticated
     */
    public void setPassword(final String userPassword) {
        this.userPassword = Utils.toCharArray(userPassword);
    }

    @Override
    public TransactionRegistry getTransactionRegistry() {
        return transactionRegistry;
    }

    @Override
    public Connection createConnection() throws SQLException {
        // create a new XAConnection
        XAConnection xaConnection;
        if (userName == null) {
            xaConnection = xaDataSource.getXAConnection();
        } else {
            xaConnection = xaDataSource.getXAConnection(userName, Utils.toString(userPassword));
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

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
package org.apache.tomcat.dbcp.dbcp2.managed;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.PooledConnection;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.xa.XAResource;

import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;

import org.apache.tomcat.dbcp.dbcp2.Utils;

/**
 * An implementation of XAConnectionFactory which uses a real XADataSource to obtain connections and XAResources.
 *
 * @since 2.0
 */
public class DataSourceXAConnectionFactory implements XAConnectionFactory {

    private static final class XAConnectionEventListener implements ConnectionEventListener {
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
    }

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
     * @since 2.6.0
     */
    public DataSourceXAConnectionFactory(final TransactionManager transactionManager, final XADataSource xaDataSource) {
        this(transactionManager, xaDataSource, null, (char[]) null, null);
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
        this(transactionManager, xaDataSource, userName, userPassword, null);
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
     * @param transactionSynchronizationRegistry
     *            register with this TransactionSynchronizationRegistry
     * @since 2.6.0
     */
    public DataSourceXAConnectionFactory(final TransactionManager transactionManager, final XADataSource xaDataSource,
            final String userName, final char[] userPassword, final TransactionSynchronizationRegistry transactionSynchronizationRegistry) {
        Objects.requireNonNull(transactionManager, "transactionManager");
        Objects.requireNonNull(xaDataSource, "xaDataSource");
        // We do allow the transactionSynchronizationRegistry to be null for non-app server environments
        this.transactionRegistry = new TransactionRegistry(transactionManager, transactionSynchronizationRegistry);
        this.xaDataSource = xaDataSource;
        this.userName = userName;
        this.userPassword = Utils.clone(userPassword);
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
        this(transactionManager, xaDataSource, userName, Utils.toCharArray(userPassword), null);
    }

    /**
     * Creates an DataSourceXAConnectionFactory which uses the specified XADataSource to create database connections.
     * The connections are enlisted into transactions using the specified transaction manager.
     *
     * @param transactionManager
     *            the transaction manager in which connections will be enlisted
     * @param xaDataSource
     *            the data source from which connections will be retrieved
     * @param transactionSynchronizationRegistry
     *            register with this TransactionSynchronizationRegistry
     */
    public DataSourceXAConnectionFactory(final TransactionManager transactionManager, final XADataSource xaDataSource, final TransactionSynchronizationRegistry transactionSynchronizationRegistry) {
        this(transactionManager, xaDataSource, null, (char[]) null, transactionSynchronizationRegistry);
    }

    @Override
    public Connection createConnection() throws SQLException {
        // create a new XAConnection
        final XAConnection xaConnection;
        if (userName == null) {
            xaConnection = xaDataSource.getXAConnection();
        } else {
            xaConnection = xaDataSource.getXAConnection(userName, Utils.toString(userPassword));
        }
        // get the real connection and XAResource from the connection
        final Connection connection = xaConnection.getConnection();
        final XAResource xaResource = xaConnection.getXAResource();
        // register the XA resource for the connection
        transactionRegistry.registerConnection(connection, xaResource);
        // The Connection we're returning is a handle on the XAConnection.
        // When the pool calling us closes the Connection, we need to
        // also close the XAConnection that holds the physical connection.
        xaConnection.addConnectionEventListener(new XAConnectionEventListener());

        return connection;
    }

    @Override
    public TransactionRegistry getTransactionRegistry() {
        return transactionRegistry;
    }

    /**
     * Gets the user name used to authenticate new connections.
     *
     * @return the user name or null if unauthenticated connections are used
     * @deprecated Use {@link #getUserName()}.
     */
    @Deprecated
    public String getUsername() {
        return userName;
    }

    /**
     * Gets the user name used to authenticate new connections.
     *
     * @return the user name or null if unauthenticated connections are used
     * @since 2.6.0
     */
    public String getUserName() {
        return userName;
    }

    /**
     * Gets the user password.
     *
     * @return the user password.
     */
    public char[] getUserPassword() {
        return Utils.clone(userPassword);
    }

    /**
     * Gets the XA data source.
     *
     * @return the XA data source.
     */
    public XADataSource getXaDataSource() {
        return xaDataSource;
    }

    /**
     * Sets the password used to authenticate new connections.
     *
     * @param userPassword
     *            the password used for authenticating the connection or null for unauthenticated.
     * @since 2.4.0
     */
    public void setPassword(final char[] userPassword) {
        this.userPassword = Utils.clone(userPassword);
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

    /**
     * Sets the user name used to authenticate new connections.
     *
     * @param userName
     *            the user name used for authenticating the connection or null for unauthenticated
     */
    public void setUsername(final String userName) {
        this.userName = userName;
    }
}

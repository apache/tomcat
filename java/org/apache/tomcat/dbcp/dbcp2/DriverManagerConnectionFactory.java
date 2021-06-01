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
package org.apache.tomcat.dbcp.dbcp2;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * A {@link DriverManager}-based implementation of {@link ConnectionFactory}.
 *
 * @since 2.0
 */
public class DriverManagerConnectionFactory implements ConnectionFactory {

    static {
        // Related to DBCP-212
        // Driver manager does not sync loading of drivers that use the service
        // provider interface. This will cause issues is multi-threaded
        // environments. This hack makes sure the drivers are loaded before
        // DBCP tries to use them.
        DriverManager.getDrivers();
    }

    private final String connectionUri;

    private final String userName;

    private final char[] userPassword;

    private final Properties properties;

    /**
     * Constructor for DriverManagerConnectionFactory.
     *
     * @param connectionUri
     *            a database url of the form <code> jdbc:<em>subprotocol</em>:<em>subname</em></code>
     * @since 2.2
     */
    public DriverManagerConnectionFactory(final String connectionUri) {
        this.connectionUri = connectionUri;
        this.properties = new Properties();
        this.userName = null;
        this.userPassword = null;
    }

    /**
     * Constructor for DriverManagerConnectionFactory.
     *
     * @param connectionUri
     *            a database url of the form <code> jdbc:<em>subprotocol</em>:<em>subname</em></code>
     * @param properties
     *            a list of arbitrary string tag/value pairs as connection arguments; normally at least a "user" and
     *            "password" property should be included.
     */
    public DriverManagerConnectionFactory(final String connectionUri, final Properties properties) {
        this.connectionUri = connectionUri;
        this.properties = properties;
        this.userName = null;
        this.userPassword = null;
    }

    /**
     * Constructor for DriverManagerConnectionFactory.
     *
     * @param connectionUri
     *            a database url of the form <code>jdbc:<em>subprotocol</em>:<em>subname</em></code>
     * @param userName
     *            the database user
     * @param userPassword
     *            the user's password
     */
    public DriverManagerConnectionFactory(final String connectionUri, final String userName,
            final char[] userPassword) {
        this.connectionUri = connectionUri;
        this.userName = userName;
        this.userPassword = Utils.clone(userPassword);
        this.properties = null;
    }

    /**
     * Constructor for DriverManagerConnectionFactory.
     *
     * @param connectionUri
     *            a database url of the form <code>jdbc:<em>subprotocol</em>:<em>subname</em></code>
     * @param userName
     *            the database user
     * @param userPassword
     *            the user's password
     */
    public DriverManagerConnectionFactory(final String connectionUri, final String userName,
            final String userPassword) {
        this.connectionUri = connectionUri;
        this.userName = userName;
        this.userPassword =  Utils.toCharArray(userPassword);
        this.properties = null;
    }

    @Override
    public Connection createConnection() throws SQLException {
        if (null == properties) {
            if (userName == null && userPassword == null) {
                return DriverManager.getConnection(connectionUri);
            }
            return DriverManager.getConnection(connectionUri, userName, Utils.toString(userPassword));
        }
        return DriverManager.getConnection(connectionUri, properties);
    }

    /**
     * @return The connection URI.
     * @since 2.6.0
     */
    public String getConnectionUri() {
        return connectionUri;
    }

    /**
     * @return The Properties.
     * @since 2.6.0
     */
    public Properties getProperties() {
        return properties;
    }

    /**
     * @return The user name.
     * @since 2.6.0
     */
    public String getUserName() {
        return userName;
    }
}

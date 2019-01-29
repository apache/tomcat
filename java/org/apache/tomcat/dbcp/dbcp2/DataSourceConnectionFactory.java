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
import java.sql.SQLException;

import javax.sql.DataSource;

/**
 * A {@link DataSource}-based implementation of {@link ConnectionFactory}.
 *
 * @since 2.0
 */
public class DataSourceConnectionFactory implements ConnectionFactory {

    private final DataSource dataSource;

    private final String userName;

    private final char[] userPassword;

    /**
     * Constructs an instance for the given DataSource.
     *
     * @param dataSource
     *            The DataSource for this factory.
     */
    public DataSourceConnectionFactory(final DataSource dataSource) {
        this(dataSource, null, (char[]) null);
    }

    /**
     * Constructs an instance for the given DataSource.
     *
     * @param dataSource
     *            The DataSource for this factory.
     * @param userName
     *            The user name.
     * @param userPassword
     *            The user password.
     * @since 2.4.0
     */
    public DataSourceConnectionFactory(final DataSource dataSource, final String userName, final char[] userPassword) {
        this.dataSource = dataSource;
        this.userName = userName;
        this.userPassword = Utils.clone(userPassword);
    }

    /**
     * Constructs an instance for the given DataSource.
     *
     * @param dataSource
     *            The DataSource for this factory.
     * @param userName
     *            The user name.
     * @param password
     *            The user password.
     */
    public DataSourceConnectionFactory(final DataSource dataSource, final String userName, final String password) {
        this.dataSource = dataSource;
        this.userName = userName;
        this.userPassword = Utils.toCharArray(password);
    }

    @Override
    public Connection createConnection() throws SQLException {
        if (null == userName && null == userPassword) {
            return dataSource.getConnection();
        }
        return dataSource.getConnection(userName, Utils.toString(userPassword));
    }

    /**
     * @return The data source.
     * @since 2.6.0
     */
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * @return The user name.
     * @since 2.6.0
     */
    public String getUserName() {
        return userName;
    }

    /**
     * @return The user password.
     * @since 2.6.0
     */
    public char[] getUserPassword() {
        return userPassword;
    }
}

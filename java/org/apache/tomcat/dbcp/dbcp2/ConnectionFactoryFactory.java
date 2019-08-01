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

import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;

/*
 * Creates {@link ConnectionFactory} instances.
 *
 * @since 2.7.0
 */
class ConnectionFactoryFactory {

    /**
     * Creates a new {@link DriverConnectionFactory} allowing for an override through
     * {@link BasicDataSource#getDriverClassName()}.
     *
     * @param basicDataSource Configures creation.
     * @param driver          The JDBC driver.
     * @return a new {@link DriverConnectionFactory} allowing for a {@link BasicDataSource#getDriverClassName()}
     *         override.
     * @throws SQLException Thrown when instantiation fails.
     */
    static ConnectionFactory createConnectionFactory(final BasicDataSource basicDataSource, final Driver driver)
            throws SQLException {
        final Properties connectionProperties = basicDataSource.getConnectionProperties();
        final String url = basicDataSource.getUrl();
        // Set up the driver connection factory we will use
        final String user = basicDataSource.getUsername();
        if (user != null) {
            connectionProperties.put("user", user);
        } else {
            basicDataSource.log("DBCP DataSource configured without a 'username'");
        }

        final String pwd = basicDataSource.getPassword();
        if (pwd != null) {
            connectionProperties.put("password", pwd);
        } else {
            basicDataSource.log("DBCP DataSource configured without a 'password'");
        }
        final String connectionFactoryClassName = basicDataSource.getConnectionFactoryClassName();
        if (connectionFactoryClassName != null) {
            try {
                final Class<?> connectionFactoryFromCCL = Class.forName(connectionFactoryClassName);
                return (ConnectionFactory) connectionFactoryFromCCL
                        .getConstructor(Driver.class, String.class, Properties.class)
                        .newInstance(driver, url, connectionProperties);
            } catch (final Exception t) {
                final String message = "Cannot load ConnectionFactory implementation '" + connectionFactoryClassName
                        + "'";
                basicDataSource.log(message, t);
                throw new SQLException(message, t);
            }
        }
        // Defaults to DriverConnectionFactory
        return new DriverConnectionFactory(driver, url, connectionProperties);
    }

}

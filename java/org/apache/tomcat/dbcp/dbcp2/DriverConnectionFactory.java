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
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;

/**
 * A {@link Driver}-based implementation of {@link ConnectionFactory}.
 *
 * @since 2.0
 */
public class DriverConnectionFactory implements ConnectionFactory {

    private final String connectionString;

    private final Driver driver;

    private final Properties properties;

    /**
     * Constructs a connection factory for a given Driver.
     *
     * @param driver
     *            The Driver.
     * @param connectString
     *            The connection string.
     * @param properties
     *            The connection properties.
     */
    public DriverConnectionFactory(final Driver driver, final String connectString, final Properties properties) {
        this.driver = driver;
        this.connectionString = connectString;
        this.properties = properties;
    }

    @Override
    public Connection createConnection() throws SQLException {
        return driver.connect(connectionString, properties);
    }

    /**
     * @return The connection String.
     * @since 2.6.0
     */
    public String getConnectionString() {
        return connectionString;
    }

    /**
     * @return The Driver.
     * @since 2.6.0
     */
    public Driver getDriver() {
        return driver;
    }

    /**
     * @return The Properties.
     * @since 2.6.0
     */
    public Properties getProperties() {
        return properties;
    }

    @Override
    public String toString() {
        return this.getClass().getName() + " [" + String.valueOf(driver) + ";" + String.valueOf(connectionString) + ";"
                + String.valueOf(properties) + "]";
    }
}

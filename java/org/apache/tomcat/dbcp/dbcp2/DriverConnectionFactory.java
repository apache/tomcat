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
 * @author Rodney Waldhoff
 * @since 2.0
 */
public class DriverConnectionFactory implements ConnectionFactory {
    public DriverConnectionFactory(final Driver driver, final String connectUri, final Properties props) {
        _driver = driver;
        _connectUri = connectUri;
        _props = props;
    }

    @Override
    public Connection createConnection() throws SQLException {
        return _driver.connect(_connectUri,_props);
    }

    private final Driver _driver;
    private final String _connectUri;
    private final Properties _props;

    @Override
    public String toString() {
        return this.getClass().getName() + " [" + String.valueOf(_driver) + ";" +
                String.valueOf(_connectUri) + ";"  + String.valueOf(_props) + "]";
    }
}

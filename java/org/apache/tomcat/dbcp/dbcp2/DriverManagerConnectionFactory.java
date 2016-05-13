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
 * @author Rodney Waldhoff
 * @author Ignacio J. Ortega
 * @author Dirk Verbeeck
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


    /**
     * Constructor for DriverManagerConnectionFactory.
     * @param connectUri a database url of the form
     * <code> jdbc:<em>subprotocol</em>:<em>subname</em></code>
     * @since 2.2
     */
    public DriverManagerConnectionFactory(final String connectUri) {
        _connectUri = connectUri;
        _props = new Properties();
    }

    /**
     * Constructor for DriverManagerConnectionFactory.
     * @param connectUri a database url of the form
     * <code> jdbc:<em>subprotocol</em>:<em>subname</em></code>
     * @param props a list of arbitrary string tag/value pairs as
     * connection arguments; normally at least a "user" and "password"
     * property should be included.
     */
    public DriverManagerConnectionFactory(final String connectUri, final Properties props) {
        _connectUri = connectUri;
        _props = props;
    }

    /**
     * Constructor for DriverManagerConnectionFactory.
     * @param connectUri a database url of the form
     * <code>jdbc:<em>subprotocol</em>:<em>subname</em></code>
     * @param uname the database user
     * @param passwd the user's password
     */
    public DriverManagerConnectionFactory(final String connectUri, final String uname, final String passwd) {
        _connectUri = connectUri;
        _uname = uname;
        _passwd = passwd;
    }

    @Override
    public Connection createConnection() throws SQLException {
        if(null == _props) {
            if(_uname == null && _passwd == null) {
                return DriverManager.getConnection(_connectUri);
            }
            return DriverManager.getConnection(_connectUri,_uname,_passwd);
        }
        return DriverManager.getConnection(_connectUri,_props);
    }

    private String _connectUri = null;
    private String _uname = null;
    private String _passwd = null;
    private Properties _props = null;
}

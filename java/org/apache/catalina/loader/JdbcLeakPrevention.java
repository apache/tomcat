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


package org.apache.catalina.loader;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;

import org.apache.catalina.util.StringManager;

/**
 * This class is loaded by the  {@link WebappClassLoader} to enable it to
 * deregister JDBC drivers forgotten by the web application. There are some
 * classloading hacks involved - see {@link WebappClassLoader#clearReferences()}
 * for details - but the short version is do not just create a new instance of
 * this class with the new keyword.
 */
public class JdbcLeakPrevention {

    /**
     * The logger for this class.
     */
    protected static org.apache.juli.logging.Log log=
        org.apache.juli.logging.LogFactory.getLog( JdbcLeakPrevention.class );

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);

    public void clearJdbcDriverRegistrations() {
        // Unregister any JDBC drivers loaded by the class loader that loaded
        // this class - ie the webapp class loader
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            try {
                DriverManager.deregisterDriver(driver);
            } catch (SQLException sqle) {
                log.warn(sm.getString("jdbcLeakPrevention.jdbcRemoveFailed",
                        driver.toString()), sqle);
            }
        }
        
    }
}

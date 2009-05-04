/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.jdbc.test.driver;

import java.sql.Connection;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;

public class Driver implements java.sql.Driver {

    public boolean acceptsURL(String url) throws SQLException {
        // TODO Auto-generated method stub
        return false;
    }

    public Connection connect(String url, Properties info) throws SQLException {
        // TODO Auto-generated method stub
        return new org.apache.tomcat.jdbc.test.driver.Connection();
    }

    public int getMajorVersion() {
        // TODO Auto-generated method stub
        return 0;
    }

    public int getMinorVersion() {
        // TODO Auto-generated method stub
        return 0;
    }

    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        // TODO Auto-generated method stub
        return null;
    }

    public boolean jdbcCompliant() {
        // TODO Auto-generated method stub
        return false;
    }

}

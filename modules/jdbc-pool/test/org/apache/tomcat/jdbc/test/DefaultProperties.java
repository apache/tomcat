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
package org.apache.tomcat.jdbc.test;

import java.util.Properties;

import org.apache.tomcat.jdbc.pool.DataSourceFactory;
import org.apache.tomcat.jdbc.pool.PoolProperties;

/**
 * @author Filip Hanik
 * @version 1.0
 */
public class DefaultProperties extends PoolProperties {
    public DefaultProperties() {
        dbProperties = new Properties();
        
        url = System.getProperty("url","jdbc:mysql://localhost:3306/mysql?autoReconnect=true");
        driverClassName = System.getProperty("driverClassName","com.mysql.jdbc.Driver");
        password = System.getProperty("password","password");
        username = System.getProperty("username","root");
        
        validationQuery = System.getProperty("validationQuery","SELECT 1");
        defaultAutoCommit = true;
        defaultReadOnly = false;
        defaultTransactionIsolation = DataSourceFactory.UNKNOWN_TRANSACTIONISOLATION;
        connectionProperties = null;
        defaultCatalog = null;
        initialSize = 10;
        maxActive = 100;
        maxIdle = initialSize;
        minIdle = initialSize;
        maxWait = 10000;
        
        testOnBorrow = true;
        testOnReturn = false;
        testWhileIdle = true;
        timeBetweenEvictionRunsMillis = 5000;
        numTestsPerEvictionRun = 0;
        minEvictableIdleTimeMillis = 1000;
        accessToUnderlyingConnectionAllowed = false;
        removeAbandoned = true;
        removeAbandonedTimeout = 5000;
        logAbandoned = true;
        loginTimeout = 0;
        validationInterval = 0; //always validate
        initSQL = null;
        testOnConnect = false;;
        dbProperties.setProperty("user",username);
        dbProperties.setProperty("password",password);
    }
}

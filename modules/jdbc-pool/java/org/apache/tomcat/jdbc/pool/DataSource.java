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
package org.apache.tomcat.jdbc.pool;

import java.sql.SQLException;
import java.util.Properties;


/**
 * A DataSource that can be instantiated through IoC and implements the DataSource interface
 * since the DataSourceProxy is used as a generic proxy
 * @author Filip Hanik
 * @version 1.0
 */
public class DataSource extends DataSourceProxy implements javax.sql.DataSource, org.apache.tomcat.jdbc.pool.jmx.ConnectionPoolMBean {

    public DataSource() {
        super();
    }

    public DataSource(PoolProperties poolProperties) {
        super(poolProperties);
    }

//===============================================================================
//  Expose JMX attributes through Tomcat's dynamic reflection
//===============================================================================
    public void checkAbandoned() {
        try {
            createPool().checkAbandoned();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public void checkIdle() {
        try {
            createPool().checkIdle();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public int getActive() {
        try {
            return createPool().getActive();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public String getConnectionProperties() {
        try {
            return createPool().getPoolProperties().getConnectionProperties();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public Properties getDbProperties() {
        try {
            return createPool().getPoolProperties().getDbProperties();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public String getDefaultCatalog() {
        try {
            return createPool().getPoolProperties().getDefaultCatalog();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public int getDefaultTransactionIsolation() {
        try {
            return createPool().getPoolProperties().getDefaultTransactionIsolation();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public String getDriverClassName() {
        try {
            return createPool().getPoolProperties().getDriverClassName();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public int getIdle() {
        try {
            return createPool().getIdle();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public int getInitialSize() {
        try {
            return createPool().getPoolProperties().getInitialSize();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public String getInitSQL() {
        try {
            return createPool().getPoolProperties().getInitSQL();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public String getJdbcInterceptors() {
        try {
            return createPool().getPoolProperties().getJdbcInterceptors();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public int getMaxActive() {
        try {
            return createPool().getPoolProperties().getMaxActive();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public int getMaxIdle() {
        try {
            return createPool().getPoolProperties().getMaxIdle();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public int getMaxWait() {
        try {
            return createPool().getPoolProperties().getMaxWait();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public int getMinEvictableIdleTimeMillis() {
        try {
            return createPool().getPoolProperties().getMinEvictableIdleTimeMillis();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public int getMinIdle() {
        try {
            return createPool().getPoolProperties().getMinIdle();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public String getName() {
        try {
            return createPool().getName();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public int getNumTestsPerEvictionRun() {
        try {
            return createPool().getPoolProperties().getNumTestsPerEvictionRun();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public String getPassword() {
        return "Password not available as DataSource/JMX operation.";
    }

    public int getRemoveAbandonedTimeout() {
        try {
            return createPool().getPoolProperties().getRemoveAbandonedTimeout();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public int getSize() {
        try {
            return createPool().getSize();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public int getTimeBetweenEvictionRunsMillis() {
        try {
            return createPool().getPoolProperties().getTimeBetweenEvictionRunsMillis();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public String getUrl() {
        try {
            return createPool().getPoolProperties().getUrl();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public String getUsername() {
        try {
            return createPool().getPoolProperties().getUsername();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public long getValidationInterval() {
        try {
            return createPool().getPoolProperties().getValidationInterval();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public String getValidationQuery() {
        try {
            return createPool().getPoolProperties().getValidationQuery();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public boolean isAccessToUnderlyingConnectionAllowed() {
        try {
            return createPool().getPoolProperties().isAccessToUnderlyingConnectionAllowed();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public boolean isDefaultAutoCommit() {
        try {
            return createPool().getPoolProperties().isDefaultAutoCommit();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public boolean isDefaultReadOnly() {
        try {
            return createPool().getPoolProperties().isDefaultReadOnly();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public boolean isLogAbandoned() {
        try {
            return createPool().getPoolProperties().isLogAbandoned();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public boolean isPoolSweeperEnabled() {
        try {
            return createPool().getPoolProperties().isPoolSweeperEnabled();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public boolean isRemoveAbandoned() {
        try {
            return createPool().getPoolProperties().isRemoveAbandoned();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public boolean isTestOnBorrow() {
        try {
            return createPool().getPoolProperties().isTestOnBorrow();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public boolean isTestOnConnect() {
        try {
            return createPool().getPoolProperties().isTestOnConnect();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public boolean isTestOnReturn() {
        try {
            return createPool().getPoolProperties().isTestOnReturn();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public boolean isTestWhileIdle() {
        try {
            return createPool().getPoolProperties().isTestWhileIdle();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    public void testIdle() {
        try {
            createPool().testAllIdle();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }
    

}

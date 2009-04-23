/* Licensed to the Apache Software Foundation (ASF) under one or more
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
package org.apache.tomcat.jdbc.pool.jmx;

import java.util.Properties;

public interface ConnectionPoolMBean  {

    //=================================================================
    //       POOL STATS
    //=================================================================

    public int getSize();

    public int getIdle();

    public int getActive();
    
    public boolean isPoolSweeperEnabled();
    
    public int getNumIdle();
    
    public int getNumActive();
    
    //=================================================================
    //       POOL OPERATIONS
    //=================================================================
    public void checkIdle();

    public void checkAbandoned();

    public void testIdle();

    //=================================================================
    //       POOL NOTIFICATIONS
    //=================================================================

    
    //=================================================================
    //       POOL PROPERTIES
    //=================================================================
    public Properties getDbProperties();

    public String getUrl();

    public String getDriverClassName();

    public boolean isDefaultAutoCommit();

    public boolean isDefaultReadOnly();

    public int getDefaultTransactionIsolation();

    public String getConnectionProperties();

    public String getDefaultCatalog();

    public int getInitialSize();

    public int getMaxActive();

    public int getMaxIdle();

    public int getMinIdle();

    public int getMaxWait();

    public String getValidationQuery();

    public boolean isTestOnBorrow();

    public boolean isTestOnReturn();

    public boolean isTestWhileIdle();

    public int getTimeBetweenEvictionRunsMillis();

    public int getNumTestsPerEvictionRun();

    public int getMinEvictableIdleTimeMillis();

    public boolean isAccessToUnderlyingConnectionAllowed();

    public boolean isRemoveAbandoned();

    public int getRemoveAbandonedTimeout();

    public boolean isLogAbandoned();

    public int getLoginTimeout();

    public String getName();

    public String getPassword();

    public String getUsername();

    public long getValidationInterval();

    public String getInitSQL();

    public boolean isTestOnConnect();

    public String getJdbcInterceptors();

    public int getAbandonWhenPercentageFull();
    
}

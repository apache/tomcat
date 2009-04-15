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

import java.lang.management.ManagementFactory;
import java.sql.SQLException;
import java.util.Hashtable;
import java.util.Properties;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.tomcat.jdbc.pool.jmx.ConnectionPoolMBean;


/**
 * A DataSource that can be instantiated through IoC and implements the DataSource interface
 * since the DataSourceProxy is used as a generic proxy
 * @author Filip Hanik
 * @version 1.0
 */
public class DataSource extends DataSourceProxy implements MBeanRegistration,javax.sql.DataSource, org.apache.tomcat.jdbc.pool.jmx.ConnectionPoolMBean {

    public DataSource() {
        super();
    }

    public DataSource(PoolProperties poolProperties) {
        super(poolProperties);
    }

//===============================================================================
//  Register the actual pool itself under the tomcat.jdbc domain
//===============================================================================
    protected volatile ObjectName oname = null;
    public void postDeregister() {
        if (oname!=null) unregisterJmx();
    }

    public void postRegister(Boolean registrationDone) {
    }


    public void preDeregister() throws Exception {
    }

    public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
        try {
            this.oname = createObjectName(name);
            if (oname!=null) registerJmx();
        }catch (MalformedObjectNameException x) {
            log.error("Unable to create object name for JDBC pool.",x);
        }
        return name;   
    }
    
    public ObjectName createObjectName(ObjectName original) throws MalformedObjectNameException {
        String domain = "tomcat.jdbc";
        Hashtable<String,String> properties = original.getKeyPropertyList();
        String origDomain = original.getDomain();
        properties.put("type", "ConnectionPool");
        properties.put("class", this.getClass().getName());
        if (original.getKeyProperty("path")!=null) {
            properties.put("engine", origDomain);
        }
        ObjectName name = new ObjectName(domain,properties);
        return name;
    }
    
    protected void registerJmx() {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            mbs.registerMBean(pool.getJmxPool(), oname);
        } catch (Exception e) {
            log.error("Unable to register JDBC pool with JMX",e);
        }
    }
    
    protected void unregisterJmx() {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            mbs.unregisterMBean(oname);
        } catch (InstanceNotFoundException ignore) {
        } catch (Exception e) {
            log.error("Unable to unregister JDBC pool with JMX",e);
        }
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
    
    public int getNumActive() {
        return getActive();
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
    
    public int getNumIdle() {
        return getIdle();
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

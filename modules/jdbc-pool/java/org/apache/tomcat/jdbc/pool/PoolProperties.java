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


import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Filip Hanik
 *
 */
public class PoolProperties {
    protected static AtomicInteger poolCounter = new AtomicInteger(0);
    protected Properties dbProperties = new Properties();
    protected String url = null;
    protected String driverClassName = null;
    protected Boolean defaultAutoCommit = null;
    protected Boolean defaultReadOnly = null;
    protected int defaultTransactionIsolation = DataSourceFactory.UNKNOWN_TRANSACTIONISOLATION;
    protected String defaultCatalog = null;
    protected String connectionProperties;
    protected int initialSize = 10;
    protected int maxActive = 100;
    protected int maxIdle = maxActive;
    protected int minIdle = initialSize;
    protected int maxWait = 30000;
    protected String validationQuery;
    protected boolean testOnBorrow = false;
    protected boolean testOnReturn = false;
    protected boolean testWhileIdle = false;
    protected int timeBetweenEvictionRunsMillis = 5000;
    protected int numTestsPerEvictionRun;
    protected int minEvictableIdleTimeMillis = 60000;
    protected boolean accessToUnderlyingConnectionAllowed;
    protected boolean removeAbandoned = false;
    protected int removeAbandonedTimeout = 60;
    protected boolean logAbandoned = false;
    protected int loginTimeout = 10000;
    protected String name = "Tomcat Connection Pool["+(poolCounter.addAndGet(1))+","+System.identityHashCode(PoolProperties.class)+"]";
    protected String password;
    protected String username;
    protected long validationInterval = 30000;
    protected boolean jmxEnabled = true;
    protected String initSQL;
    protected boolean testOnConnect =false;
    private String jdbcInterceptors=null;
    private boolean fairQueue = false;
    private boolean useEquals = false;

    private InterceptorDefinition[] interceptors = null;
    
    public boolean isFairQueue() {
        return fairQueue;
    }

    public void setFairQueue(boolean fairQueue) {
        this.fairQueue = fairQueue;
    }

    public boolean isAccessToUnderlyingConnectionAllowed() {
        return accessToUnderlyingConnectionAllowed;
    }

    public String getConnectionProperties() {
        return connectionProperties;
    }

    public Properties getDbProperties() {
        return dbProperties;
    }

    public boolean isDefaultAutoCommit() {
        return defaultAutoCommit;
    }

    public String getDefaultCatalog() {
        return defaultCatalog;
    }

    public boolean isDefaultReadOnly() {
        return defaultReadOnly;
    }

    public int getDefaultTransactionIsolation() {
        return defaultTransactionIsolation;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public int getInitialSize() {
        return initialSize;
    }

    public boolean isLogAbandoned() {
        return logAbandoned;
    }

    public int getLoginTimeout() {
        return loginTimeout;
    }

    public int getMaxActive() {
        return maxActive;
    }

    public int getMaxIdle() {
        return maxIdle;
    }

    public int getMaxWait() {
        return maxWait;
    }

    public int getMinEvictableIdleTimeMillis() {
        return minEvictableIdleTimeMillis;
    }

    public int getMinIdle() {
        return minIdle;
    }

    public String getName() {
        return name;
    }

    public int getNumTestsPerEvictionRun() {
        return numTestsPerEvictionRun;
    }

    public String getPassword() {
        return password;
    }

    public String getPoolName() {
        return getName();
    }

    public boolean isRemoveAbandoned() {
        return removeAbandoned;
    }

    public int getRemoveAbandonedTimeout() {
        return removeAbandonedTimeout;
    }

    public boolean isTestOnBorrow() {
        return testOnBorrow;
    }

    public boolean isTestOnReturn() {
        return testOnReturn;
    }

    public boolean isTestWhileIdle() {
        return testWhileIdle;
    }

    public int getTimeBetweenEvictionRunsMillis() {
        return timeBetweenEvictionRunsMillis;
    }

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public String getValidationQuery() {
        return validationQuery;
    }

    public long getValidationInterval() {
        return validationInterval;
    }

    public String getInitSQL() {
        return initSQL;
    }

    public boolean isTestOnConnect() {
        return testOnConnect;
    }

    public String getJdbcInterceptors() {
        return jdbcInterceptors;
    }

    public InterceptorDefinition[] getJdbcInterceptorsAsArray() {
        if (interceptors == null) {
            if (jdbcInterceptors==null) {
                interceptors = new InterceptorDefinition[0];
            } else {
                String[] interceptorValues = jdbcInterceptors.split(";");
                InterceptorDefinition[] definitions = new InterceptorDefinition[interceptorValues.length];
                for (int i=0; i<interceptorValues.length; i++) {
                    int propIndex = interceptorValues[i].indexOf("(");
                    if (propIndex<0) {
                        definitions[i] = new InterceptorDefinition(interceptorValues[i]);
                    } else {
                        String name = interceptorValues[i].substring(0,propIndex);
                        definitions[i] = new InterceptorDefinition(name);
                        String propsAsString = interceptorValues[i].substring(propIndex+1, interceptorValues[i].length());
                        String[] props = propsAsString.split(",");
                        for (int j=0; j<props.length; j++) {
                            int pidx = props[j].indexOf("=");
                            String propName = props[j].substring(0,pidx);
                            String propValue = props[j].substring(pidx+1);
                            definitions[i].addProperty(new InterceptorProperty(propName,propValue));
                        }
                    }
                }
                interceptors = definitions;
            }
        }
        return interceptors;
    }

    public void setAccessToUnderlyingConnectionAllowed(boolean
        accessToUnderlyingConnectionAllowed) {
        this.accessToUnderlyingConnectionAllowed =
            accessToUnderlyingConnectionAllowed;
    }

    public void setConnectionProperties(String connectionProperties) {
        this.connectionProperties = connectionProperties;
    }

    public void setDbProperties(Properties dbProperties) {
        this.dbProperties = dbProperties;
    }

    public void setDefaultAutoCommit(Boolean defaultAutoCommit) {
        this.defaultAutoCommit = defaultAutoCommit;
    }

    public void setDefaultCatalog(String defaultCatalog) {
        this.defaultCatalog = defaultCatalog;
    }

    public void setDefaultReadOnly(Boolean defaultReadOnly) {
        this.defaultReadOnly = defaultReadOnly;
    }

    public void setDefaultTransactionIsolation(int defaultTransactionIsolation) {
        this.defaultTransactionIsolation = defaultTransactionIsolation;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public void setInitialSize(int initialSize) {
        this.initialSize = initialSize;
    }

    public void setLogAbandoned(boolean logAbandoned) {
        this.logAbandoned = logAbandoned;
    }

    public void setLoginTimeout(int loginTimeout) {
        this.loginTimeout = loginTimeout;
    }

    public void setMaxActive(int maxActive) {
        this.maxActive = maxActive;
    }

    public void setMaxIdle(int maxIdle) {
        this.maxIdle = maxIdle;
    }

    public void setMaxWait(int maxWait) {
        this.maxWait = maxWait;
    }

    public void setMinEvictableIdleTimeMillis(int minEvictableIdleTimeMillis) {
        this.minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
    }

    public void setMinIdle(int minIdle) {
        this.minIdle = minIdle;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setNumTestsPerEvictionRun(int numTestsPerEvictionRun) {
        this.numTestsPerEvictionRun = numTestsPerEvictionRun;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setRemoveAbandoned(boolean removeAbandoned) {
        this.removeAbandoned = removeAbandoned;
    }

    public void setRemoveAbandonedTimeout(int removeAbandonedTimeout) {
        this.removeAbandonedTimeout = removeAbandonedTimeout;
    }

    public void setTestOnBorrow(boolean testOnBorrow) {
        this.testOnBorrow = testOnBorrow;
    }

    public void setTestWhileIdle(boolean testWhileIdle) {
        this.testWhileIdle = testWhileIdle;
    }

    public void setTestOnReturn(boolean testOnReturn) {
        this.testOnReturn = testOnReturn;
    }

    public void setTimeBetweenEvictionRunsMillis(int
                                                 timeBetweenEvictionRunsMillis) {
        this.timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setValidationInterval(long validationInterval) {
        this.validationInterval = validationInterval;
    }

    public void setValidationQuery(String validationQuery) {
        this.validationQuery = validationQuery;
    }

    public void setInitSQL(String initSQL) {
        this.initSQL = initSQL;
    }

    public void setTestOnConnect(boolean testOnConnect) {
        this.testOnConnect = testOnConnect;
    }

    public void setJdbcInterceptors(String jdbcInterceptors) {
        this.jdbcInterceptors = jdbcInterceptors;
        this.interceptors = null;
    }

    public String toString() {
        StringBuffer buf = new StringBuffer("ConnectionPool[");
        try {
            String[] fields = DataSourceFactory.ALL_PROPERTIES;
            for (int i=0; i<fields.length; i++) {
                final String[] prefix = new String[] {"get","is"};
                for (int j=0; j<prefix.length; j++) {

                    String name = prefix[j] + fields[i].substring(0, 1).toUpperCase() +
                                  fields[i].substring(1);
                    Method m = null;
                    try {
                        m = getClass().getMethod(name);
                    }catch (NoSuchMethodException nm) {
                        continue;
                    }
                    buf.append(fields[i]);
                    buf.append("=");
                    buf.append(m.invoke(this, new Object[0]));
                    buf.append("; ");
                    break;
                }
            }
        }catch (Exception x) {
            //shouldn;t happen
            x.printStackTrace();
        }
        return buf.toString();
    }

    public static int getPoolCounter() {
        return poolCounter.get();
    }

    public boolean isJmxEnabled() {
        return jmxEnabled;
    }

    public void setJmxEnabled(boolean jmxEnabled) {
        this.jmxEnabled = jmxEnabled;
    }

    public Boolean getDefaultAutoCommit() {
        return defaultAutoCommit;
    }

    public Boolean getDefaultReadOnly() {
        return defaultReadOnly;
    }
    
    public boolean isPoolSweeperEnabled() {
        boolean result = getTimeBetweenEvictionRunsMillis()>0;
        result = result && (isRemoveAbandoned() && getRemoveAbandonedTimeout()>0);
        result = result || (isTestWhileIdle() && getValidationQuery()!=null);
        return result;
    }
    
    
    
    public static class InterceptorDefinition {
        protected String className;
        protected List<InterceptorProperty> properties = new ArrayList<InterceptorProperty>();

        public InterceptorDefinition(String className) {
            this.className = className;
        }

        public String getClassName() {
            return className;
        }
        public void addProperty(String name, String value) {
            InterceptorProperty p = new InterceptorProperty(name,value);
            addProperty(p);
        }
        
        public void addProperty(InterceptorProperty p) {
            properties.add(p);
        }
        
        public List<InterceptorProperty> getProperties() {
            return properties;
        }
    } 
    
    public static class InterceptorProperty {
        String name;
        String value;
        public InterceptorProperty(String name, String value) {
            this.name = name;
            this.value = value;
        }
        public String getName() {
            return name;
        }
        public String getValue() {
            return value;
        }
    }

    public boolean isUseEquals() {
        return useEquals;
    }

    public void setUseEquals(boolean useEquals) {
        this.useEquals = useEquals;
    }

}

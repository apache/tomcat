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


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Properties;

import javax.management.ObjectName;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;
import javax.sql.DataSource;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * <p>JNDI object factory that creates an instance of
 * <code>BasicDataSource</code> that has been configured based on the
 * <code>RefAddr</code> values of the specified <code>Reference</code>,
 * which must match the names and data types of the
 * <code>BasicDataSource</code> bean properties.</p>
 * <br/>
 * Properties available for configuration:<br/>
 * <a href="http://commons.apache.org/dbcp/configuration.html">Commons DBCP properties</a><br/>
 *<ol>
 *  <li>initSQL - A query that gets executed once, right after the connection is established.</li>
 *  <li>testOnConnect - run validationQuery after connection has been established.</li>
 *  <li>validationInterval - avoid excess validation, only run validation at most at this frequency - time in milliseconds.</li>
 *  <li>jdbcInterceptors - a semicolon separated list of classnames extending {@link JdbcInterceptor} class.</li>
 *  <li>jmxEnabled - true of false, whether to register the pool with JMX.</li>
 *  <li>fairQueue - true of false, whether the pool should sacrifice a little bit of performance for true fairness.</li>
 *</ol>
 * @author Craig R. McClanahan
 * @author Dirk Verbeeck
 * @author Filip Hanik
 */
public class DataSourceFactory implements ObjectFactory {
    protected static Log log = LogFactory.getLog(DataSourceFactory.class);

    protected final static String PROP_DEFAULTAUTOCOMMIT = "defaultAutoCommit";
    protected final static String PROP_DEFAULTREADONLY = "defaultReadOnly";
    protected final static String PROP_DEFAULTTRANSACTIONISOLATION = "defaultTransactionIsolation";
    protected final static String PROP_DEFAULTCATALOG = "defaultCatalog";
    
    protected final static String PROP_DRIVERCLASSNAME = "driverClassName";
    protected final static String PROP_PASSWORD = "password";
    protected final static String PROP_URL = "url";
    protected final static String PROP_USERNAME = "username";

    protected final static String PROP_MAXACTIVE = "maxActive";
    protected final static String PROP_MAXIDLE = "maxIdle";
    protected final static String PROP_MINIDLE = "minIdle";
    protected final static String PROP_INITIALSIZE = "initialSize";
    protected final static String PROP_MAXWAIT = "maxWait";
    
    protected final static String PROP_TESTONBORROW = "testOnBorrow";
    protected final static String PROP_TESTONRETURN = "testOnReturn";
    protected final static String PROP_TESTWHILEIDLE = "testWhileIdle";
    protected final static String PROP_TESTONCONNECT = "testOnConnect";
    protected final static String PROP_VALIDATIONQUERY = "validationQuery";
    
    protected final static String PROP_TIMEBETWEENEVICTIONRUNSMILLIS = "timeBetweenEvictionRunsMillis";
    protected final static String PROP_NUMTESTSPEREVICTIONRUN = "numTestsPerEvictionRun";
    protected final static String PROP_MINEVICTABLEIDLETIMEMILLIS = "minEvictableIdleTimeMillis";
    
    protected final static String PROP_ACCESSTOUNDERLYINGCONNECTIONALLOWED = "accessToUnderlyingConnectionAllowed";
    
    protected final static String PROP_REMOVEABANDONED = "removeAbandoned";
    protected final static String PROP_REMOVEABANDONEDTIMEOUT = "removeAbandonedTimeout";
    protected final static String PROP_LOGABANDONED = "logAbandoned";
    protected final static String PROP_ABANDONWHENPERCENTAGEFULL = "abandonWhenPercentageFull";
    
    protected final static String PROP_POOLPREPAREDSTATEMENTS = "poolPreparedStatements";
    protected final static String PROP_MAXOPENPREPAREDSTATEMENTS = "maxOpenPreparedStatements";
    protected final static String PROP_CONNECTIONPROPERTIES = "connectionProperties";
    
    protected final static String PROP_INITSQL = "initSQL";
    protected final static String PROP_INTERCEPTORS = "jdbcInterceptors";
    protected final static String PROP_VALIDATIONINTERVAL = "validationInterval";
    protected final static String PROP_JMX_ENABLED = "jmxEnabled";
    protected final static String PROP_FAIR_QUEUE = "fairQueue";
    
    protected static final String PROP_USE_EQUALS = "useEquals";
    
    public static final int UNKNOWN_TRANSACTIONISOLATION = -1;
    
    public static final String OBJECT_NAME = "object_name";


    protected final static String[] ALL_PROPERTIES = {
        PROP_DEFAULTAUTOCOMMIT,
        PROP_DEFAULTREADONLY,
        PROP_DEFAULTTRANSACTIONISOLATION,
        PROP_DEFAULTCATALOG,
        PROP_DRIVERCLASSNAME,
        PROP_MAXACTIVE,
        PROP_MAXIDLE,
        PROP_MINIDLE,
        PROP_INITIALSIZE,
        PROP_MAXWAIT,
        PROP_TESTONBORROW,
        PROP_TESTONRETURN,
        PROP_TIMEBETWEENEVICTIONRUNSMILLIS,
        PROP_NUMTESTSPEREVICTIONRUN,
        PROP_MINEVICTABLEIDLETIMEMILLIS,
        PROP_TESTWHILEIDLE,
        PROP_TESTONCONNECT,
        PROP_PASSWORD,
        PROP_URL,
        PROP_USERNAME,
        PROP_VALIDATIONQUERY,
        PROP_VALIDATIONINTERVAL,
        PROP_ACCESSTOUNDERLYINGCONNECTIONALLOWED,
        PROP_REMOVEABANDONED,
        PROP_REMOVEABANDONEDTIMEOUT,
        PROP_LOGABANDONED,
        PROP_POOLPREPAREDSTATEMENTS,
        PROP_MAXOPENPREPAREDSTATEMENTS,
        PROP_CONNECTIONPROPERTIES,
        PROP_INITSQL,
        PROP_INTERCEPTORS,
        PROP_JMX_ENABLED,
        PROP_FAIR_QUEUE,
        PROP_USE_EQUALS,
        OBJECT_NAME,
        PROP_ABANDONWHENPERCENTAGEFULL
    };

    // -------------------------------------------------- ObjectFactory Methods

    /**
     * <p>Create and return a new <code>BasicDataSource</code> instance.  If no
     * instance can be created, return <code>null</code> instead.</p>
     *
     * @param obj The possibly null object containing location or
     *  reference information that can be used in creating an object
     * @param name The name of this object relative to <code>nameCtx</code>
     * @param nameCtx The context relative to which the <code>name</code>
     *  parameter is specified, or <code>null</code> if <code>name</code>
     *  is relative to the default initial context
     * @param environment The possibly null environment that is used in
     *  creating this object
     *
     * @exception Exception if an exception occurs creating the instance
     */
    public Object getObjectInstance(Object obj, Name name, Context nameCtx,
                                    Hashtable environment) throws Exception {

        // We only know how to deal with <code>javax.naming.Reference</code>s
        // that specify a class name of "javax.sql.DataSource"
        if ((obj == null) || !(obj instanceof Reference)) {
            return null;
        }
        Reference ref = (Reference) obj;
        
        boolean ok = false;
        if ("javax.sql.DataSource".equals(ref.getClassName())) {
            ok = true;
        }
        if (org.apache.tomcat.jdbc.pool.DataSource.class.getName().equals(ref.getClassName())) {
            ok = true;
        }
        
        if (!ok) {
            log.warn(ref.getClassName()+" is not a valid class name/type for this JNDI factory.");
            return null;
        }
        

        Properties properties = new Properties();
        for (int i = 0; i < ALL_PROPERTIES.length; i++) {
            String propertyName = ALL_PROPERTIES[i];
            RefAddr ra = ref.get(propertyName);
            if (ra != null) {
                String propertyValue = ra.getContent().toString();
                properties.setProperty(propertyName, propertyValue);
            }
        }

        return createDataSource(properties);
    }
    
    public static PoolProperties parsePoolProperties(Properties properties) throws IOException{
        PoolProperties poolProperties = new PoolProperties();
        String value = null;

        value = properties.getProperty(PROP_DEFAULTAUTOCOMMIT);
        if (value != null) {
            poolProperties.setDefaultAutoCommit(Boolean.valueOf(value));
        }

        value = properties.getProperty(PROP_DEFAULTREADONLY);
        if (value != null) {
            poolProperties.setDefaultReadOnly(Boolean.valueOf(value));
        }

        value = properties.getProperty(PROP_DEFAULTTRANSACTIONISOLATION);
        if (value != null) {
            int level = UNKNOWN_TRANSACTIONISOLATION;
            if ("NONE".equalsIgnoreCase(value)) {
                level = Connection.TRANSACTION_NONE;
            } else if ("READ_COMMITTED".equalsIgnoreCase(value)) {
                level = Connection.TRANSACTION_READ_COMMITTED;
            } else if ("READ_UNCOMMITTED".equalsIgnoreCase(value)) {
                level = Connection.TRANSACTION_READ_UNCOMMITTED;
            } else if ("REPEATABLE_READ".equalsIgnoreCase(value)) {
                level = Connection.TRANSACTION_REPEATABLE_READ;
            } else if ("SERIALIZABLE".equalsIgnoreCase(value)) {
                level = Connection.TRANSACTION_SERIALIZABLE;
            } else {
                try {
                    level = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    System.err.println("Could not parse defaultTransactionIsolation: " + value);
                    System.err.println("WARNING: defaultTransactionIsolation not set");
                    System.err.println("using default value of database driver");
                    level = UNKNOWN_TRANSACTIONISOLATION;
                }
            }
            poolProperties.setDefaultTransactionIsolation(level);
        }

        value = properties.getProperty(PROP_DEFAULTCATALOG);
        if (value != null) {
            poolProperties.setDefaultCatalog(value);
        }

        value = properties.getProperty(PROP_DRIVERCLASSNAME);
        if (value != null) {
            poolProperties.setDriverClassName(value);
        }

        value = properties.getProperty(PROP_MAXACTIVE);
        if (value != null) {
            poolProperties.setMaxActive(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_MAXIDLE);
        if (value != null) {
            poolProperties.setMaxIdle(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_MINIDLE);
        if (value != null) {
            poolProperties.setMinIdle(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_INITIALSIZE);
        if (value != null) {
            poolProperties.setInitialSize(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_MAXWAIT);
        if (value != null) {
            poolProperties.setMaxWait(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_TESTONBORROW);
        if (value != null) {
            poolProperties.setTestOnBorrow(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_TESTONRETURN);
        if (value != null) {
            poolProperties.setTestOnReturn(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_TESTONCONNECT);
        if (value != null) {
            poolProperties.setTestOnConnect(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_TIMEBETWEENEVICTIONRUNSMILLIS);
        if (value != null) {
            poolProperties.setTimeBetweenEvictionRunsMillis(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_NUMTESTSPEREVICTIONRUN);
        if (value != null) {
            poolProperties.setNumTestsPerEvictionRun(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_MINEVICTABLEIDLETIMEMILLIS);
        if (value != null) {
            poolProperties.setMinEvictableIdleTimeMillis(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_TESTWHILEIDLE);
        if (value != null) {
            poolProperties.setTestWhileIdle(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_PASSWORD);
        if (value != null) {
            poolProperties.setPassword(value);
        }

        value = properties.getProperty(PROP_URL);
        if (value != null) {
            poolProperties.setUrl(value);
        }

        value = properties.getProperty(PROP_USERNAME);
        if (value != null) {
            poolProperties.setUsername(value);
        }

        value = properties.getProperty(PROP_VALIDATIONQUERY);
        if (value != null) {
            poolProperties.setValidationQuery(value);
        }

        value = properties.getProperty(PROP_VALIDATIONINTERVAL);
        if (value != null) {
            poolProperties.setValidationInterval(Long.parseLong(value));
        }

        value = properties.getProperty(PROP_ACCESSTOUNDERLYINGCONNECTIONALLOWED);
        if (value != null) {
            poolProperties.setAccessToUnderlyingConnectionAllowed(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_REMOVEABANDONED);
        if (value != null) {
            poolProperties.setRemoveAbandoned(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_REMOVEABANDONEDTIMEOUT);
        if (value != null) {
            poolProperties.setRemoveAbandonedTimeout(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_LOGABANDONED);
        if (value != null) {
            poolProperties.setLogAbandoned(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_POOLPREPAREDSTATEMENTS);
        if (value != null) {
            log.warn(PROP_POOLPREPAREDSTATEMENTS + " is not a valid setting, it will have no effect.");
        }

        value = properties.getProperty(PROP_MAXOPENPREPAREDSTATEMENTS);
        if (value != null) {
            log.warn(PROP_MAXOPENPREPAREDSTATEMENTS + " is not a valid setting, it will have no effect.");
        }

        value = properties.getProperty(PROP_CONNECTIONPROPERTIES);
        if (value != null) {
            Properties p = getProperties(value);
            poolProperties.setDbProperties(p);
        } else {
            poolProperties.setDbProperties(new Properties());
        }

        poolProperties.getDbProperties().setProperty("user",poolProperties.getUsername());
        poolProperties.getDbProperties().setProperty("password",poolProperties.getPassword());

        value = properties.getProperty(PROP_INITSQL);
        if (value != null) {
            poolProperties.setInitSQL(value);
        }

        value = properties.getProperty(PROP_INTERCEPTORS);
        if (value != null) {
            poolProperties.setJdbcInterceptors(value);
        }

        value = properties.getProperty(PROP_JMX_ENABLED);
        if (value != null) {
            poolProperties.setJmxEnabled(Boolean.parseBoolean(value));
        }
        
        value = properties.getProperty(PROP_FAIR_QUEUE);
        if (value != null) {
            poolProperties.setFairQueue(Boolean.parseBoolean(value));
        }
        
        value = properties.getProperty(PROP_USE_EQUALS);
        if (value != null) {
            poolProperties.setUseEquals(Boolean.parseBoolean(value));
        }

        value = properties.getProperty(OBJECT_NAME);
        if (value != null) {
            poolProperties.setName(ObjectName.quote(value));
        }
        
        value = properties.getProperty(PROP_ABANDONWHENPERCENTAGEFULL);
        if (value != null) {
            poolProperties.setAbandonWhenPercentageFull(Integer.parseInt(value));
        }
        
        return poolProperties;
    }

    /**
     * Creates and configures a {@link BasicDataSource} instance based on the
     * given properties.
     *
     * @param properties the datasource configuration properties
     * @throws Exception if an error occurs creating the data source
     */
    public static DataSource createDataSource(Properties properties) throws Exception {
        PoolProperties poolProperties = DataSourceFactory.parsePoolProperties(properties);
        org.apache.tomcat.jdbc.pool.DataSource dataSource = new org.apache.tomcat.jdbc.pool.DataSource(poolProperties);
        
        //initialize the pool itself
        dataSource.createPool();
        // Return the configured DataSource instance
        return dataSource;
    }

    public static DataSource getDataSource(org.apache.tomcat.jdbc.pool.DataSourceProxy dataSource) {
        if (dataSource instanceof DataSource) return (DataSource)dataSource;
        //only return a proxy if we didn't implement the DataSource interface
        DataSourceHandler handler = new DataSourceHandler(dataSource);
        DataSource ds = (DataSource)Proxy.newProxyInstance(DataSourceFactory.class.getClassLoader(), new Class[] {javax.sql.DataSource.class}, handler);
        return ds;
    }

    /**
     * <p>Parse properties from the string. Format of the string must be [propertyName=property;]*<p>
     * @param propText
     * @return Properties
     * @throws Exception
     */
    static protected Properties getProperties(String propText) throws IOException {
        Properties p = new Properties();
        if (propText != null) {
            p.load(new ByteArrayInputStream(propText.replace(';', '\n').getBytes()));
        }
        return p;
    }

    protected static class DataSourceHandler implements InvocationHandler {
        protected org.apache.tomcat.jdbc.pool.DataSourceProxy datasource = null;
        protected static HashMap<Method,Method> methods = new HashMap<Method,Method>();
        public DataSourceHandler(org.apache.tomcat.jdbc.pool.DataSourceProxy ds) {
            this.datasource = ds;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Method m = methods.get(method);
            if (m==null) {
                m = datasource.getClass().getMethod(method.getName(), method.getParameterTypes());
                methods.put(method, m);
            }
            try {
                return m.invoke(datasource, args);
            }catch (InvocationTargetException t) {
                throw t.getTargetException();
            }
        }

    }
}

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
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;


import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Implementation of {@link PoolConfiguration} that holds the configuration
 * properties for a connection pool.
 */
public class PoolProperties implements PoolConfiguration, Cloneable, Serializable {

    private static final long serialVersionUID = -8519283440854213745L;
    private static final Log log = LogFactory.getLog(PoolProperties.class);

    /**
     * Constructs a PoolProperties with default values.
     */
    public PoolProperties() {
    }

    /**
     * Default maximum number of active connections.
     */
    public static final int DEFAULT_MAX_ACTIVE = 100;

    /**
     * Counter for tracking the number of pools created.
     */
    protected static final AtomicInteger poolCounter = new AtomicInteger(0);
    /** Database-specific properties. */
    private volatile Properties dbProperties = new Properties();
    /** The JDBC connection URL. */
    private volatile String url = null;
    /** The JDBC driver class name. */
    private volatile String driverClassName = null;
    /** The default auto-commit setting for connections. */
    private volatile Boolean defaultAutoCommit = null;
    /** The default read-only setting for connections. */
    private volatile Boolean defaultReadOnly = null;
    /** The default transaction isolation level. */
    private volatile int defaultTransactionIsolation = DataSourceFactory.UNKNOWN_TRANSACTIONISOLATION;
    /** The default catalog for connections. */
    private volatile String defaultCatalog = null;
    /** Additional connection properties. */
    private volatile String connectionProperties;
    /** The initial number of connections in the pool. */
    private volatile int initialSize = 10;
    /** The maximum number of active connections. */
    private volatile int maxActive = DEFAULT_MAX_ACTIVE;
    /** The maximum number of idle connections. */
    private volatile int maxIdle = maxActive;
    /** The minimum number of idle connections. */
    private volatile int minIdle = initialSize;
    /** The maximum wait time in milliseconds for a connection. */
    private volatile int maxWait = 30000;
    /** The SQL query used for validation. */
    private volatile String validationQuery;
    /** The validation query timeout in seconds. */
    private volatile int validationQueryTimeout = -1;
    /** The fully qualified class name of the validator. */
    private volatile String validatorClassName;
    /** The validator instance. */
    private transient volatile Validator validator;
    /** Whether to validate connections on borrow. */
    private volatile boolean testOnBorrow = false;
    /** Whether to validate connections on return. */
    private volatile boolean testOnReturn = false;
    /** Whether to validate idle connections. */
    private volatile boolean testWhileIdle = false;
    /** The time between eviction runs in milliseconds. */
    private volatile int timeBetweenEvictionRunsMillis = 5000;
    /** The number of connections to test per eviction run. */
    private volatile int numTestsPerEvictionRun;
    /** The minimum idle time before a connection is eligible for eviction. */
    private volatile int minEvictableIdleTimeMillis = 60000;
    /** Whether to allow access to the underlying connection. */
    private volatile boolean accessToUnderlyingConnectionAllowed = true;
    /** Whether to remove abandoned connections. */
    private volatile boolean removeAbandoned = false;
    /** The timeout in seconds for abandoned connection removal. */
    private volatile int removeAbandonedTimeout = 60;
    /** Whether to log abandoned connections. */
    private volatile boolean logAbandoned = false;
    /** The pool name. */
    private volatile String name = "Tomcat Connection Pool["+(poolCounter.addAndGet(1))+"-"+System.identityHashCode(PoolProperties.class)+"]";
    /** The database password. */
    private volatile String password;
    /** The database username. */
    private volatile String username;
    /** The validation interval in milliseconds. */
    private volatile long validationInterval = 3000;
    /** Whether JMX registration is enabled. */
    private volatile boolean jmxEnabled = true;
    /** The SQL to execute on connection creation. */
    private volatile String initSQL;
    /** Whether to validate connections on creation. */
    private volatile boolean testOnConnect =false;
    /** The JDBC interceptors configuration. */
    private volatile String jdbcInterceptors=null;
    /** Whether to use a fair queue for connection requests. */
    private volatile boolean fairQueue = true;
    /** Whether to use equals for object comparison. */
    private volatile boolean useEquals = true;
    /** The percentage threshold for abandoning connections. */
    private volatile int abandonWhenPercentageFull = 0;
    /** The maximum age of a connection in milliseconds. */
    private volatile long maxAge = 0;
    /** Whether to use locking for thread safety. */
    private volatile boolean useLock = false;
    /** The interceptor definitions. */
    private volatile InterceptorDefinition[] interceptors = null;
    /** The suspect timeout in seconds. */
    private volatile int suspectTimeout = 0;
    /** The underlying data source. */
    private volatile Object dataSource = null;
    /** The JNDI name of the data source. */
    private volatile String dataSourceJNDI = null;
    /** Whether alternate usernames are allowed. */
    private volatile boolean alternateUsernameAllowed = false;
    /** Whether to commit on connection return. */
    private volatile boolean commitOnReturn = false;
    /** Whether to rollback on connection return. */
    private volatile boolean rollbackOnReturn = false;
    /** Whether to use a disposable connection facade. */
    private volatile boolean useDisposableConnectionFacade = true;
    /** Whether to log validation errors. */
    private volatile boolean logValidationErrors = false;
    /** Whether to propagate interrupt state. */
    private volatile boolean propagateInterruptState = false;
    /** Whether to ignore exceptions during pre-load. */
    private volatile boolean ignoreExceptionOnPreLoad = false;
    /** Whether to use a statement facade. */
    private volatile boolean useStatementFacade = true;

    @Override
    public void setAbandonWhenPercentageFull(int percentage) {
        if (percentage<0) {
            abandonWhenPercentageFull = 0;
        } else if (percentage>100) {
            abandonWhenPercentageFull = 100;
        } else {
            abandonWhenPercentageFull = percentage;
        }
    }

    @Override
    public int getAbandonWhenPercentageFull() {
        return abandonWhenPercentageFull;
    }

    @Override
    public boolean isFairQueue() {
        return fairQueue;
    }

    @Override
    public void setFairQueue(boolean fairQueue) {
        this.fairQueue = fairQueue;
    }

    @Override
    public boolean isAccessToUnderlyingConnectionAllowed() {
        return accessToUnderlyingConnectionAllowed;
    }


    @Override
    public String getConnectionProperties() {
        return connectionProperties;
    }


    @Override
    public Properties getDbProperties() {
        return dbProperties;
    }


    @Override
    public Boolean isDefaultAutoCommit() {
        return defaultAutoCommit;
    }


    @Override
    public String getDefaultCatalog() {
        return defaultCatalog;
    }


    @Override
    public Boolean isDefaultReadOnly() {
        return defaultReadOnly;
    }


    @Override
    public int getDefaultTransactionIsolation() {
        return defaultTransactionIsolation;
    }


    @Override
    public String getDriverClassName() {
        return driverClassName;
    }


    @Override
    public int getInitialSize() {
        return initialSize;
    }


    @Override
    public boolean isLogAbandoned() {
        return logAbandoned;
    }


    @Override
    public int getMaxActive() {
        return maxActive;
    }


    @Override
    public int getMaxIdle() {
        return maxIdle;
    }


    @Override
    public int getMaxWait() {
        return maxWait;
    }


    @Override
    public int getMinEvictableIdleTimeMillis() {
        return minEvictableIdleTimeMillis;
    }


    @Override
    public int getMinIdle() {
        return minIdle;
    }


    @Override
    public String getName() {
        return name;
    }


    @Override
    public int getNumTestsPerEvictionRun() {
        return numTestsPerEvictionRun;
    }


    @Override
    public String getPassword() {
        return password;
    }


    @Override
    public String getPoolName() {
        return getName();
    }


    @Override
    public boolean isRemoveAbandoned() {
        return removeAbandoned;
    }


    @Override
    public int getRemoveAbandonedTimeout() {
        return removeAbandonedTimeout;
    }


    @Override
    public boolean isTestOnBorrow() {
        return testOnBorrow;
    }


    @Override
    public boolean isTestOnReturn() {
        return testOnReturn;
    }


    @Override
    public boolean isTestWhileIdle() {
        return testWhileIdle;
    }


    @Override
    public int getTimeBetweenEvictionRunsMillis() {
        return timeBetweenEvictionRunsMillis;
    }


    @Override
    public String getUrl() {
        return url;
    }


    @Override
    public String getUsername() {
        return username;
    }


    @Override
    public String getValidationQuery() {
        return validationQuery;
    }

    @Override
    public int getValidationQueryTimeout() {
        return validationQueryTimeout;
    }

    @Override
    public void setValidationQueryTimeout(int validationQueryTimeout) {
        this.validationQueryTimeout = validationQueryTimeout;
    }


    @Override
    public String getValidatorClassName() {
        return validatorClassName;
    }


    @Override
    public Validator getValidator() {
        return validator;
    }

    @Override
    public void setValidator(Validator validator) {
        this.validator = validator;
        if (validator!=null) {
            this.validatorClassName = validator.getClass().getName();
        } else {
            this.validatorClassName = null;
        }
    }



    @Override
    public long getValidationInterval() {
        return validationInterval;
    }


    @Override
    public String getInitSQL() {
        return initSQL;
    }


    @Override
    public boolean isTestOnConnect() {
        return testOnConnect;
    }


    @Override
    public String getJdbcInterceptors() {
        return jdbcInterceptors;
    }


    @Override
    public InterceptorDefinition[] getJdbcInterceptorsAsArray() {
        if (interceptors == null) {
            if (jdbcInterceptors==null) {
                interceptors = new InterceptorDefinition[0];
            } else {
                String[] interceptorValues = jdbcInterceptors.split(";");
                InterceptorDefinition[] definitions = new InterceptorDefinition[interceptorValues.length+1];
                //always add the trap interceptor to the mix
                definitions[0] = new InterceptorDefinition(TrapException.class);
                for (int i=0; i<interceptorValues.length; i++) {
                    int propIndex = interceptorValues[i].indexOf('(');
                    int endIndex = interceptorValues[i].indexOf(')');
                    if (propIndex<0 || endIndex<0 || endIndex <= propIndex) {
                        definitions[i+1] = new InterceptorDefinition(interceptorValues[i].trim());
                    } else {
                        String name = interceptorValues[i].substring(0,propIndex).trim();
                        definitions[i+1] = new InterceptorDefinition(name);
                        String propsAsString = interceptorValues[i].substring(propIndex+1, endIndex);
                        String[] props = propsAsString.split(",");
                        for (int j=0; j<props.length; j++) {
                            int pidx = props[j].indexOf('=');
                            String propName = props[j].substring(0,pidx).trim();
                            String propValue = props[j].substring(pidx+1).trim();
                            definitions[i+1].addProperty(new InterceptorProperty(propName,propValue));
                        }
                    }
                }
                interceptors = definitions;
            }
        }
        return interceptors;
    }


    @Override
    public void setAccessToUnderlyingConnectionAllowed(boolean accessToUnderlyingConnectionAllowed) {
        // NOOP
    }


    @Override
    public void setConnectionProperties(String connectionProperties) {
        this.connectionProperties = connectionProperties;
        getProperties(connectionProperties, getDbProperties());
    }


    @Override
    public void setDbProperties(Properties dbProperties) {
        this.dbProperties = dbProperties;
    }


    @Override
    public void setDefaultAutoCommit(Boolean defaultAutoCommit) {
        this.defaultAutoCommit = defaultAutoCommit;
    }


    @Override
    public void setDefaultCatalog(String defaultCatalog) {
        this.defaultCatalog = defaultCatalog;
    }


    @Override
    public void setDefaultReadOnly(Boolean defaultReadOnly) {
        this.defaultReadOnly = defaultReadOnly;
    }


    @Override
    public void setDefaultTransactionIsolation(int defaultTransactionIsolation) {
        this.defaultTransactionIsolation = defaultTransactionIsolation;
    }


    @Override
    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }


    @Override
    public void setInitialSize(int initialSize) {
        this.initialSize = initialSize;
    }


    @Override
    public void setLogAbandoned(boolean logAbandoned) {
        this.logAbandoned = logAbandoned;
    }


    @Override
    public void setMaxActive(int maxActive) {
        this.maxActive = maxActive;
    }


    @Override
    public void setMaxIdle(int maxIdle) {
        this.maxIdle = maxIdle;
    }


    @Override
    public void setMaxWait(int maxWait) {
        this.maxWait = maxWait;
    }


    @Override
    public void setMinEvictableIdleTimeMillis(int minEvictableIdleTimeMillis) {
        this.minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
    }


    @Override
    public void setMinIdle(int minIdle) {
        this.minIdle = minIdle;
    }


    @Override
    public void setName(String name) {
        this.name = name;
    }


    @Override
    public void setNumTestsPerEvictionRun(int numTestsPerEvictionRun) {
        this.numTestsPerEvictionRun = numTestsPerEvictionRun;
    }


    @Override
    public void setPassword(String password) {
        this.password = password;
    }


    @Override
    public void setRemoveAbandoned(boolean removeAbandoned) {
        this.removeAbandoned = removeAbandoned;
    }


    @Override
    public void setRemoveAbandonedTimeout(int removeAbandonedTimeout) {
        this.removeAbandonedTimeout = removeAbandonedTimeout;
    }


    @Override
    public void setTestOnBorrow(boolean testOnBorrow) {
        this.testOnBorrow = testOnBorrow;
    }


    @Override
    public void setTestWhileIdle(boolean testWhileIdle) {
        this.testWhileIdle = testWhileIdle;
    }


    @Override
    public void setTestOnReturn(boolean testOnReturn) {
        this.testOnReturn = testOnReturn;
    }


    @Override
    public void setTimeBetweenEvictionRunsMillis(int
                                                 timeBetweenEvictionRunsMillis) {
        this.timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
    }


    @Override
    public void setUrl(String url) {
        this.url = url;
    }


    @Override
    public void setUsername(String username) {
        this.username = username;
    }


    @Override
    public void setValidationInterval(long validationInterval) {
        this.validationInterval = validationInterval;
    }


    @Override
    public void setValidationQuery(String validationQuery) {
        this.validationQuery = validationQuery;
    }


    @Override
    public void setValidatorClassName(String className) {
        this.validatorClassName = className;

        validator = null;

        if (className == null) {
            return;
        }

        try {
            @SuppressWarnings("unchecked")
            Class<Validator> validatorClass = (Class<Validator>)ClassLoaderUtil.loadClass(
                    className,
                    PoolProperties.class.getClassLoader(),
                    Thread.currentThread().getContextClassLoader()
            );
            validator = validatorClass.getConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            log.warn("The class "+className+" cannot be found.", e);
        } catch (ClassCastException e) {
            log.warn("The class "+className+" does not implement the Validator interface.", e);
        } catch (IllegalAccessException e) {
            log.warn("The class "+className+" or its no-arg constructor are inaccessible.", e);
        } catch (ReflectiveOperationException | IllegalArgumentException | SecurityException e) {
            log.warn("An object of class "+className+" cannot be instantiated. Make sure that "+
                     "it includes an implicit or explicit no-arg constructor.", e);
        }
    }


    @Override
    public void setInitSQL(String initSQL) {
        this.initSQL = initSQL!=null && initSQL.trim().length()>0 ? initSQL : null;
    }


    @Override
    public void setTestOnConnect(boolean testOnConnect) {
        this.testOnConnect = testOnConnect;
    }


    @Override
    public void setJdbcInterceptors(String jdbcInterceptors) {
        this.jdbcInterceptors = jdbcInterceptors;
        this.interceptors = null;
    }


    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("ConnectionPool[");
        try {
            String[] fields = DataSourceFactory.ALL_PROPERTIES;
            for (String field: fields) {
                final String[] prefix = new String[] {"get","is"};
                for (int j=0; j<prefix.length; j++) {

                    String name = prefix[j]
                            + field.substring(0, 1).toUpperCase(Locale.ENGLISH)
                            + field.substring(1);
                    Method m = null;
                    try {
                        m = getClass().getMethod(name);
                    }catch (NoSuchMethodException nm) {
                        continue;
                    }
                    buf.append(field);
                    buf.append('=');
                    if (DataSourceFactory.PROP_PASSWORD.equals(field)) {
                        buf.append("********");
                    } else {
                        buf.append(m.invoke(this, new Object[0]));
                    }
                    buf.append("; ");
                    break;
                }
            }
            buf.append(']');
        } catch (Exception x) {
            //shouldn't happen
            if (log.isDebugEnabled()) {
                log.debug("toString() call failed", x);
            }
        }
        return buf.toString();
    }

    /**
     * Returns the current pool counter value.
     * @return the pool counter value
     */
    public static int getPoolCounter() {
        return poolCounter.get();
    }


    @Override
    public boolean isJmxEnabled() {
        return jmxEnabled;
    }


    @Override
    public void setJmxEnabled(boolean jmxEnabled) {
        this.jmxEnabled = jmxEnabled;
    }


    @Override
    public Boolean getDefaultAutoCommit() {
        return defaultAutoCommit;
    }


    @Override
    public Boolean getDefaultReadOnly() {
        return defaultReadOnly;
    }



    @Override
    public int getSuspectTimeout() {
        return this.suspectTimeout;
    }


    @Override
    public void setSuspectTimeout(int seconds) {
        this.suspectTimeout = seconds;
    }


    @Override
    public boolean isPoolSweeperEnabled() {
        boolean timer = getTimeBetweenEvictionRunsMillis()>0;
        boolean result = timer && (isRemoveAbandoned() && getRemoveAbandonedTimeout()>0);
        result = result || (timer && getSuspectTimeout()>0);
        result = result || (timer && isTestWhileIdle());
        result = result || (timer && getMinEvictableIdleTimeMillis()>0);
        result = result || (timer && getMaxAge()>0);
        return result;
    }


    /**
     * Definition of a JDBC interceptor with its configuration properties.
     */
    public static class InterceptorDefinition implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * Interceptor class name.
         */
        protected String className;
        /**
         * Map of interceptor properties.
         */
        protected Map<String,InterceptorProperty> properties = new HashMap<>();
        /**
         * Cached interceptor class.
         */
        protected volatile Class<?> clazz = null;
        /**
         * Constructs an InterceptorDefinition with the given class name.
         * @param className the interceptor class name
         */
        public InterceptorDefinition(String className) {
            this.className = className;
        }

        /**
         * Constructs an InterceptorDefinition with the given class.
         * @param cl the interceptor class
         */
        public InterceptorDefinition(Class<?> cl) {
            this(cl.getName());
            clazz = cl;
        }

        /**
         * Returns the interceptor class name.
         * @return the class name
         */
        public String getClassName() {
            return className;
        }
        /**
         * Adds a property with the given name and value.
         * @param name the property name
         * @param value the property value
         */
        public void addProperty(String name, String value) {
            InterceptorProperty p = new InterceptorProperty(name,value);
            addProperty(p);
        }

        /**
         * Adds the given interceptor property.
         * @param p the property to add
         */
        public void addProperty(InterceptorProperty p) {
            properties.put(p.getName(), p);
        }

        /**
         * Returns the map of interceptor properties.
         * @return the properties map
         */
        public Map<String,InterceptorProperty> getProperties() {
            return properties;
        }

        /**
         * Returns the interceptor class, loading it if necessary.
         * @return the interceptor class
         * @throws ClassNotFoundException if the class cannot be found
         */
        @SuppressWarnings("unchecked")
        public Class<? extends JdbcInterceptor> getInterceptorClass() throws ClassNotFoundException {
            if (clazz==null) {
                if (getClassName().indexOf('.')<0) {
                    if (log.isDebugEnabled()) {
                        log.debug("Loading interceptor class:" +  PKG_PREFIX + getClassName());
                    }
                    clazz = ClassLoaderUtil.loadClass(
                        PKG_PREFIX + getClassName(),
                        PoolProperties.class.getClassLoader(),
                        Thread.currentThread().getContextClassLoader()
                    );
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("Loading interceptor class:"+getClassName());
                    }
                    clazz = ClassLoaderUtil.loadClass(
                        getClassName(),
                        PoolProperties.class.getClassLoader(),
                        Thread.currentThread().getContextClassLoader()
                    );
                }
            }
            return (Class<? extends JdbcInterceptor>)clazz;
        }
    }

    /**
     * Represents a property for a JDBC interceptor.
     */
    public static class InterceptorProperty implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * Property name.
         */
        String name;
        /**
         * Property value.
         */
        String value;
        /**
         * Constructs an InterceptorProperty with the given name and value.
         * @param name the property name
         * @param value the property value
         */
        public InterceptorProperty(String name, String value) {
            assert(name!=null);
            this.name = name;
            this.value = value;
        }
        /**
         * Returns the property name.
         * @return the property name
         */
        public String getName() {
            return name;
        }
        /**
         * Returns the property value.
         * @return the property value
         */
        public String getValue() {
            return value;
        }

        /**
         * Returns the property value as a boolean.
         * @param def the default value if the property is null or invalid
         * @return the boolean value
         */
        public boolean getValueAsBoolean(boolean def) {
            if (value==null) {
                return def;
            }
            if ("true".equals(value)) {
                return true;
            }
            if ("false".equals(value)) {
                return false;
            }
            return def;
        }

        /**
         * Returns the property value as an int.
         * @param def the default value if the property is null or invalid
         * @return the int value
         */
        public int getValueAsInt(int def) {
            if (value==null) {
                return def;
            }
            try {
                int v = Integer.parseInt(value);
                return v;
            }catch (NumberFormatException nfe) {
                return def;
            }
        }

        /**
         * Returns the property value as a long.
         * @param def the default value if the property is null or invalid
         * @return the long value
         */
        public long getValueAsLong(long def) {
            if (value==null) {
                return def;
            }
            try {
                return Long.parseLong(value);
            }catch (NumberFormatException nfe) {
                return def;
            }
        }

        /**
         * Returns the property value as a byte.
         * @param def the default value if the property is null or invalid
         * @return the byte value
         */
        public byte getValueAsByte(byte def) {
            if (value==null) {
                return def;
            }
            try {
                return Byte.parseByte(value);
            }catch (NumberFormatException nfe) {
                return def;
            }
        }

        /**
         * Returns the property value as a short.
         * @param def the default value if the property is null or invalid
         * @return the short value
         */
        public short getValueAsShort(short def) {
            if (value==null) {
                return def;
            }
            try {
                return Short.parseShort(value);
            }catch (NumberFormatException nfe) {
                return def;
            }
        }

        /**
         * Returns the property value as a float.
         * @param def the default value if the property is null or invalid
         * @return the float value
         */
        public float getValueAsFloat(float def) {
            if (value==null) {
                return def;
            }
            try {
                return Float.parseFloat(value);
            }catch (NumberFormatException nfe) {
                return def;
            }
        }

        /**
         * Returns the property value as a double.
         * @param def the default value if the property is null or invalid
         * @return the double value
         */
        public double getValueAsDouble(double def) {
            if (value==null) {
                return def;
            }
            try {
                return Double.parseDouble(value);
            }catch (NumberFormatException nfe) {
                return def;
            }
        }

        /**
         * Returns the property value as a char.
         * @param def the default value if the property is null or invalid
         * @return the char value
         */
        public char getValueAschar(char def) {
            if (value==null) {
                return def;
            }
            try {
                return value.charAt(0);
            }catch (StringIndexOutOfBoundsException nfe) {
                return def;
            }
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o==this) {
                return true;
            }
            if (o instanceof InterceptorProperty) {
                InterceptorProperty other = (InterceptorProperty)o;
                return other.name.equals(this.name);
            }
            return false;
        }
    }


    @Override
    public boolean isUseEquals() {
        return useEquals;
    }


    @Override
    public void setUseEquals(boolean useEquals) {
        this.useEquals = useEquals;
    }


    @Override
    public long getMaxAge() {
        return maxAge;
    }


    @Override
    public void setMaxAge(long maxAge) {
        this.maxAge = maxAge;
    }


    @Override
    public boolean getUseLock() {
        return useLock;
    }


    @Override
    public void setUseLock(boolean useLock) {
        this.useLock = useLock;
    }


    @Override
    public void setDataSource(Object ds) {
        if (ds instanceof DataSourceProxy) {
            throw new IllegalArgumentException("Layered pools are not allowed.");
        }
        this.dataSource = ds;
    }

    @Override
    public Object getDataSource() {
        return dataSource;
    }


    @Override
    public void setDataSourceJNDI(String jndiDS) {
        this.dataSourceJNDI = jndiDS;
    }

    @Override
    public String getDataSourceJNDI() {
        return this.dataSourceJNDI;
    }


    /**
     * Parses a property string into a Properties object.
     * @param propText the property string with semicolon-separated key=value pairs
     * @param props the Properties object to populate, or null to create a new one
     * @return the populated Properties object
     */
    public static Properties getProperties(String propText, Properties props) {
        if (props==null) {
            props = new Properties();
        }
        if (propText != null) {
            try {
                props.load(new ByteArrayInputStream(propText.replace(';', '\n').getBytes()));
            }catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
        return props;
    }

    @Override
    public boolean isAlternateUsernameAllowed() {
        return alternateUsernameAllowed;
    }

    @Override
    public void setAlternateUsernameAllowed(boolean alternateUsernameAllowed) {
        this.alternateUsernameAllowed = alternateUsernameAllowed;
    }


    @Override
    public void setCommitOnReturn(boolean commitOnReturn) {
        this.commitOnReturn = commitOnReturn;
    }

    @Override
    public boolean getCommitOnReturn() {
        return this.commitOnReturn;
    }

    @Override
    public void setRollbackOnReturn(boolean rollbackOnReturn) {
        this.rollbackOnReturn = rollbackOnReturn;
    }

    @Override
    public boolean getRollbackOnReturn() {
        return this.rollbackOnReturn;
    }

    @Override
    public void setUseDisposableConnectionFacade(boolean useDisposableConnectionFacade) {
        this.useDisposableConnectionFacade = useDisposableConnectionFacade;
    }

    @Override
    public boolean getUseDisposableConnectionFacade() {
        return useDisposableConnectionFacade;
    }

    @Override
    public void setLogValidationErrors(boolean logValidationErrors) {
        this.logValidationErrors = logValidationErrors;
    }

    @Override
    public boolean getLogValidationErrors() {
        return this.logValidationErrors;
    }

    @Override
    public boolean getPropagateInterruptState() {
        return propagateInterruptState;
    }

    @Override
    public void setPropagateInterruptState(boolean propagateInterruptState) {
        this.propagateInterruptState = propagateInterruptState;
    }

    @Override
    public boolean isIgnoreExceptionOnPreLoad() {
        return ignoreExceptionOnPreLoad;
    }

    @Override
    public void setIgnoreExceptionOnPreLoad(boolean ignoreExceptionOnPreLoad) {
        this.ignoreExceptionOnPreLoad = ignoreExceptionOnPreLoad;
    }

    @Override
    public boolean getUseStatementFacade() {
        return useStatementFacade;
    }

    @Override
    public void setUseStatementFacade(boolean useStatementFacade) {
        this.useStatementFacade = useStatementFacade;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        // TODO Auto-generated method stub
        return super.clone();
    }



}

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

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;


import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.util.ExceptionUtils;
import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * Implementation of {@link PoolConfiguration} that holds the configuration properties for a connection pool.
 */
public class PoolProperties implements PoolConfiguration, Cloneable, Serializable {

    private static final long serialVersionUID = -8519283440854213745L;
    private static final Log log = LogFactory.getLog(PoolProperties.class);

    /** The mask the {@link #toString()} dump prints in place of a password. */
    private static final String PASSWORD_MASK = "********";

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
    /** Keys the last parse of {@link #connectionProperties} contributed to {@link #dbProperties}. */
    private volatile Set<String> connectionPropertyKeys = Collections.emptySet();
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
    /**
     * The pool name. The identity hash of the class object is the same for
     * every pool in a JVM - it identifies the class loader that loaded this
     * class, not the pool - so the per-pool uniqueness of the default name
     * comes from the counter alone. The hash term is upstream's default-name
     * shape and stays for that reason.
     */
    private volatile String name = "Tomcat Connection Pool[" + (poolCounter.addAndGet(1)) + "-" +
            System.identityHashCode(PoolProperties.class) + "]";
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
    private volatile boolean testOnConnect = false;
    /** The JDBC interceptors configuration. */
    private volatile String jdbcInterceptors = null;
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
    /**
     * Whether to use a disposable connection facade.
     *
     * @deprecated The disposable connection facade is always used, so this property no longer has any effect.
     */
    @Deprecated
    private volatile boolean useDisposableConnectionFacade = true;
    /** Whether to log validation errors. */
    private volatile boolean logValidationErrors = false;
    /** Whether to propagate interrupt state. */
    private volatile boolean propagateInterruptState = false;
    /** Whether to ignore exceptions during pre-load. */
    private volatile boolean ignoreExceptionOnPreLoad = false;
    /**
     * Whether to use a statement facade.
     *
     * @deprecated The statement facade is always used, so this property no longer has any effect.
     */
    @Deprecated
    private volatile boolean useStatementFacade = true;

    @Override
    public void setAbandonWhenPercentageFull(int percentage) {
        if (percentage < 0) {
            abandonWhenPercentageFull = 0;
        } else if (percentage > 100) {
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
        if (validator != null) {
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


    /**
     * {@inheritDoc}
     *
     * <p>The string is parsed on first use and the result cached. The check,
     * the parse and the publish run inside this one synchronized method, under
     * the same lock {@link #setJdbcInterceptors(String)} takes to clear the
     * cache: a parse that read the previous value of the string can therefore
     * never store its result back after a reconfiguration cleared it, which is
     * what would leave every later caller on the discarded interceptor chain
     * for good. The string is read once into a local so the parse works on a
     * single snapshot. A malformed value throws before the cache is written, so
     * a later call parses the then-current value again. The lock is never held
     * over interceptor class loading: an {@link InterceptorDefinition} parsed
     * from the string only keeps the class name, and those classes are loaded
     * later by the pool, outside this lock.</p>
     */
    @Override
    public synchronized InterceptorDefinition[] getJdbcInterceptorsAsArray() {
        if (interceptors == null) {
            // Read once: the parse below must work on a single snapshot.
            String interceptorList = jdbcInterceptors;
            if (interceptorList == null) {
                interceptors = new InterceptorDefinition[0];
            } else {
                String[] interceptorValues = interceptorList.split(";");
                List<InterceptorDefinition> definitions =
                        new ArrayList<>(interceptorValues.length + 1);
                // Always add the trap interceptor to the mix
                definitions.add(new InterceptorDefinition(TrapException.class));
                for (int i = 0; i < interceptorValues.length; i++) {
                    int propIndex = interceptorValues[i].indexOf('(');
                    int endIndex = interceptorValues[i].indexOf(')');
                    String name;
                    String propsAsString = null;
                    if (propIndex < 0 || endIndex < 0 || endIndex <= propIndex) {
                        name = interceptorValues[i].trim();
                    } else {
                        name = interceptorValues[i].substring(0, propIndex).trim();
                        propsAsString = interceptorValues[i].substring(propIndex + 1, endIndex);
                    }
                    if (name.isEmpty()) {
                        // A leading or doubled separator, or a segment that
                        // opens straight on "(", leaves no interceptor name:
                        // there is nothing to load, and a definition without
                        // a class name fails the whole pool start later, on
                        // the generated "...pool.interceptor." class, with a
                        // message naming nothing. split() already drops a
                        // redundant trailing separator and the property parse
                        // tolerates one inside the parentheses, so skipping
                        // the empty name makes a redundant separator cosmetic
                        // throughout the syntax rather than fatal wherever
                        // split() kept it.
                        continue;
                    }
                    InterceptorDefinition definition = new InterceptorDefinition(name);
                    if (propsAsString != null) {
                        String[] props = propsAsString.split(",");
                        for (int j = 0; j < props.length; j++) {
                            String prop = props[j];
                            if (prop.trim().isEmpty()) {
                                // An empty segment comes from Name(), Name( )
                                // or a redundant separator: there is no property
                                // to add, so tolerate it rather than fail the
                                // whole pool start on a cosmetic spelling
                                continue;
                            }
                            int pidx = prop.indexOf('=');
                            if (pidx < 0) {
                                throw new IllegalArgumentException(MessageFactory.get(
                                        "poolProperties.invalidInterceptorProperty", prop.trim(),
                                        interceptorValues[i]));
                            }
                            String propName = prop.substring(0, pidx).trim();
                            String propValue = prop.substring(pidx + 1).trim();
                            definition.addProperty(new InterceptorProperty(propName, propValue));
                        }
                    }
                    definitions.add(definition);
                }
                interceptors = definitions.toArray(new InterceptorDefinition[0]);
            }
        }
        return interceptors;
    }


    @Override
    public void setAccessToUnderlyingConnectionAllowed(boolean accessToUnderlyingConnectionAllowed) {
        // NOOP
    }


    /**
     * Stores the connection properties string and parses the pairs into the
     * database properties the pool connects with. The pairs contributed by the
     * previous value of the string are removed first, so reconfiguring a running
     * pool with a reduced or cleared string really stops sending the dropped
     * properties to the driver instead of leaving them merged in. Entries the
     * string never contributed, such as the {@code user} and {@code password}
     * injected by {@link DataSourceFactory#parsePoolProperties(Properties)} or
     * mirrored in by {@link DataSourceProxy}, are kept, and while a
     * {@code username}/{@code password} field is set its entry holds the
     * field's value even when this string names that key, the rule the
     * credential setters establish. A configuration whose
     * database properties are {@code null} only stores the raw string, as there
     * is no property collection to parse into. The string is parsed before any
     * state is touched, so a value the parser rejects - a malformed Unicode
     * escape - leaves the previous configuration fully in place
     * along with the exception. The remove and re-add run inside
     * this one synchronized method; an unsynchronised reader of the database
     * properties, which {@link #getDbProperties()} has always handed out live,
     * can therefore observe the contributed keys briefly missing.
     *
     * @param connectionProperties the property string with semicolon-separated
     *                             key=value pairs, or {@code null} to clear it
     */
    @Override
    public synchronized void setConnectionProperties(String connectionProperties) {
        Properties dbProps = getDbProperties();
        // Parse into a fresh collection before anything is mutated: that is
        // what tells apart the keys this string contributes from the ones
        // dbProperties holds from elsewhere, and a string the parse rejects
        // must not have cost the previous configuration its pairs yet.
        Properties parsed = dbProps == null ? null : getProperties(connectionProperties, new Properties());
        this.connectionProperties = connectionProperties;
        if (dbProps == null) {
            // The pairs have no collection to land in; nothing was contributed,
            // so there is nothing to track for a later removal either.
            connectionPropertyKeys = Collections.emptySet();
            return;
        }
        // A configuration serialized before this field existed deserializes
        // with it null, as field initializers do not run on deserialization.
        if (connectionPropertyKeys != null) {
            for (String key : connectionPropertyKeys) {
                dbProps.remove(key);
            }
        }
        Set<String> keys = new HashSet<>(parsed.size());
        for (String key : parsed.stringPropertyNames()) {
            dbProps.setProperty(key, parsed.getProperty(key));
            keys.add(key);
        }
        // The credential setters' rule outlives the string: while a credential
        // field is set, its dbProperties entry holds the field's value, even
        // when this string named the key - DataSourceFactory injects the
        // configured username/password over the string's pairs, so a
        // reconfigure that drops the keys from the string must not strip
        // credentials the configuration still carries. With the field unset
        // this parse re-asserts nothing and the entry is the string's own
        // again. The entry can also be missing here although the string names
        // the key, because clearing a credential takes the entry away whatever
        // contributed it; this parse hands the pair back - writing the string
        // is a fresh declaration of what it contributes.
        if (username != null) {
            dbProps.setProperty(PoolUtilities.PROP_USER, username);
        }
        if (password != null) {
            dbProps.setProperty(PoolUtilities.PROP_PASSWORD, password);
        }
        connectionPropertyKeys = keys;
    }


    /**
     * Replaces the database properties the pool connects with. The keys tracked
     * from the last connection properties parse contributed to the previous
     * collection, not to the replacement, so the tracking is reset: the caller
     * owns whatever the new collection holds.
     *
     * @param dbProperties the new database properties, may be {@code null}
     */
    @Override
    public synchronized void setDbProperties(Properties dbProperties) {
        this.dbProperties = dbProperties;
        connectionPropertyKeys = Collections.emptySet();
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


    /**
     * Stores the password and, as {@link PoolConfiguration#setPassword(String)} promises, keeps the
     * {@code password} entry of the database properties in step with it.
     * <p>
     * Both halves have to be written together. The connect path clones the database properties and then lets a
     * non-null password override the copy, so while a password is set the entry is only what the override replaces;
     * once the password is removed there is nothing left to override with and the entry alone decides what the
     * driver is handed. A configuration that kept the two apart would therefore keep authenticating connections
     * with a credential that had been set aside. Writing them as one step matters for the same reason: a reader
     * that saw the field cleared but the entry still standing would be looking at exactly that state.
     * <p>
     * A configuration whose database properties are {@code null} has no collection to hold the entry, so only the
     * field is stored.
     * <p>
     * The removal covers the entry whatever put it there. A {@code connectionProperties} string that names
     * {@code password} contributes the same entry, and clearing the password takes that entry away as well: clearing
     * a credential has to win over every surface that still names the key, because it is the operation an operator
     * uses to stop a credential authenticating connections. The string is not modified, so
     * {@link #getConnectionProperties()} keeps reporting it and the next {@link #setConnectionProperties(String)}
     * whose value names the key applies that pair again. Retiring a credential the string carries means taking it
     * out of the string.
     *
     * @param password The password
     */
    @Override
    public synchronized void setPassword(String password) {
        this.password = password;
        if (this.dbProperties != null) {
            if (password == null) {
                this.dbProperties.remove(PoolUtilities.PROP_PASSWORD);
                warnStringStillNamesCredential(PoolUtilities.PROP_PASSWORD);
            } else {
                this.dbProperties.setProperty(PoolUtilities.PROP_PASSWORD, password);
            }
        }
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
    public void setTimeBetweenEvictionRunsMillis(int timeBetweenEvictionRunsMillis) {
        this.timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
    }


    @Override
    public void setUrl(String url) {
        this.url = url;
    }


    /**
     * Stores the user name and keeps the {@code user} entry of the database properties in step with it, for the
     * reason given on {@link #setPassword(String)}. A {@code null} user name likewise removes the entry whatever
     * contributed it, including a pair the {@code connectionProperties} string names.
     *
     * @param username The user name
     */
    @Override
    public synchronized void setUsername(String username) {
        this.username = username;
        if (this.dbProperties != null) {
            if (username == null) {
                this.dbProperties.remove(PoolUtilities.PROP_USER);
                warnStringStillNamesCredential(PoolUtilities.PROP_USER);
            } else {
                this.dbProperties.setProperty(PoolUtilities.PROP_USER, username);
            }
        }
    }


    /**
     * Notes at warn level when clearing a credential has just removed an entry
     * that the configured {@code connectionProperties} string still names, so
     * the divergence between the reported configuration and the properties the
     * driver is handed is not silent. Names the key, never the value: the clear
     * is the operator's intent to retire the credential.
     *
     * @param key the credential key whose entry was removed
     */
    private void warnStringStillNamesCredential(String key) {
        // Called with the monitor held, which is also the one setConnectionProperties
        // publishes the tracking under.
        if (connectionPropertyKeys != null && connectionPropertyKeys.contains(key)) {
            log.warn(MessageFactory.get("poolProperties.credentialClearStripsStringEntry", key));
        }
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
            Class<Validator> validatorClass = (Class<Validator>) ClassLoaderUtil.loadClass(className,
                    PoolProperties.class.getClassLoader(), Thread.currentThread().getContextClassLoader());
            validator = validatorClass.getConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            log.warn(MessageFactory.get("poolProperties.validatorClassNotFound", className), e);
        } catch (ClassCastException e) {
            log.warn(MessageFactory.get("poolProperties.validatorNotValidator", className), e);
        } catch (IllegalAccessException e) {
            log.warn(MessageFactory.get("poolProperties.validatorInaccessible", className), e);
        } catch (ReflectiveOperationException | IllegalArgumentException | SecurityException e) {
            log.warn(MessageFactory.get("poolProperties.validatorCannotInstantiate", className), e);
        }
    }


    @Override
    public void setInitSQL(String initSQL) {
        this.initSQL = initSQL != null && initSQL.trim().length() > 0 ? initSQL : null;
    }


    @Override
    public void setTestOnConnect(boolean testOnConnect) {
        this.testOnConnect = testOnConnect;
    }


    /**
     * Stores the interceptor configuration string and drops the parsed cache
     * so the next {@link #getJdbcInterceptorsAsArray()} re-parses the new
     * value. The clear runs under the same lock the parse publishes under, so
     * a parse that started from the previous value of the string can never
     * store its result back after this clear.
     *
     * @param jdbcInterceptors the semicolon separated interceptor configuration,
     *                         may be {@code null} to use none
     */
    @Override
    public synchronized void setJdbcInterceptors(String jdbcInterceptors) {
        this.jdbcInterceptors = jdbcInterceptors;
        this.interceptors = null;
    }


    /**
     * Best-effort dump of the configuration. The {@code password} field is
     * masked whenever it carries a secret and prints as {@code null} when
     * none is configured, and the {@code url}, {@code connectionProperties}
     * and {@code jdbcInterceptors} strings pass through credential masking,
     * so the dump can be logged without carrying a cleartext credential. The
     * masking is best effort in the way {@link #maskCredentials(String)} and
     * {@link #maskInterceptorCredentials(String)} describe, so it removes the
     * credentials an operator normally configures rather than proving the
     * configuration holds no other secret. A property whose getter fails is
     * reported as an error in its own field and does not truncate the rest of
     * the dump.
     *
     * @return the dump, always bracket-balanced even on internal failure
     */
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("ConnectionPool[");
        try {
            String[] fields = DataSourceFactory.ALL_PROPERTIES;
            for (String field : fields) {
                final String[] prefix = new String[] { "get", "is" };
                for (int j = 0; j < prefix.length; j++) {

                    String name = prefix[j] + field.substring(0, 1).toUpperCase(Locale.ENGLISH) + field.substring(1);
                    Method m = null;
                    try {
                        m = getClass().getMethod(name);
                    } catch (NoSuchMethodException nm) {
                        continue;
                    }
                    buf.append(field);
                    buf.append('=');
                    // One bad property - a subclass getter that throws, or a
                    // dataSource object whose toString() throws - must cost
                    // only its own field, not the rest of the dump. The
                    // append itself is inside this try because buf.append
                    // calls the object's toString() directly: an Error from a
                    // hostile third-party toString() arrives unwrapped here,
                    // not wrapped like one from inside Method.invoke, and a
                    // dump that promises not to throw must hold that promise
                    // against the arbitrary objects it prints - except for
                    // the fatal errors, which the module convention is to
                    // propagate rather than report.
                    try {
                        Object value = m.invoke(this, new Object[0]);
                        if (DataSourceFactory.PROP_PASSWORD.equals(field)) {
                            buf.append(value == null ? "null" : PASSWORD_MASK);
                        } else if (DataSourceFactory.PROP_URL.equals(field)
                                || DataSourceFactory.PROP_CONNECTIONPROPERTIES.equals(field)) {
                            // The same dump prints these raw configuration
                            // strings, so a credential typed into one of them
                            // has to be masked too, not just the password field.
                            buf.append(value == null ? "null" : maskCredentials((String) value));
                        } else if (DataSourceFactory.PROP_INTERCEPTORS.equals(field)) {
                            // Interceptor properties reach a third-party
                            // interceptor through setProperties(), so this
                            // configuration string carries credentials the
                            // same way; its pairs live inside parentheses,
                            // which the segment-start walk above cannot see.
                            buf.append(value == null ? "null" : maskInterceptorCredentials((String) value));
                        } else {
                            buf.append(value);
                        }
                    } catch (ReflectiveOperationException | RuntimeException | Error x) {
                        Throwable cause = ExceptionUtils.unwrapInvocationTargetException(x);
                        ExceptionUtils.handleThrowable(cause);
                        buf.append("<error: ").append(cause).append('>');
                    }
                    buf.append("; ");
                    break;
                }
            }
        } catch (Exception x) {
            // Shouldn't happen
            if (log.isDebugEnabled()) {
                log.debug("toString() call failed", x);
            }
        } finally {
            // The javadoc promises a bracket-balanced dump even on internal
            // failure, and an unterminated "ConnectionPool[" line is what a
            // truncated diagnostic log entry looks like.
            buf.append(']');
        }
        return buf.toString();
    }


    /**
     * Masks the credentials embedded in a raw configuration string so that a
     * password typed into the JDBC URL or the connection properties is not
     * printed in cleartext the way the dedicated password field is masked in
     * {@link #toString()}. Two shapes are scrubbed.
     * <p>
     * The value of a pair whose key - at the start of a segment, ignoring
     * case - is one of {@code password}, {@code pass} and {@code pwd}. A
     * segment starts at the beginning of the string or at {@code ;}, a new
     * line, {@code &amp;} or {@code ?}. Only the first two are boundaries to
     * the properties parser that applies a configuration string, and the
     * pairs behind them are the pairs a driver receives: their value runs to
     * the next such boundary, because an {@code &amp;} or {@code ?} is an
     * ordinary character inside a delivered value and masking up to one
     * would print the tail of the password the driver really receives, while
     * a new line the reader deletes as a backslash continuation is no
     * boundary at all and the mask runs across it into the next line. A pair
     * that only appears to start behind {@code &amp;} or {@code ?} was
     * folded into another pair's value by the parser, and it is masked to the
     * end of its segment all the same - a secret typed there was typed as a
     * credential. The key runs to the first value separator the parser
     * accepts - {@code =}, {@code :} or white space - so a key such as
     * {@code compass}, a credential-shaped word inside a longer value, and
     * the colons of a JDBC URL scheme, host or port are all left alone.
     * Either way a masked value cannot hide a later pair. A pair with an
     * empty value is masked like any other, so a masked pair in the dump does
     * not by itself say that a non-empty secret was configured.
     * <p>
     * The password of a URL {@code user:password@host} userinfo section, and
     * of the equivalent {@code user/password@host} form used by the legacy
     * Oracle driver URLs, wherever they appear in the string. The user name is
     * kept visible for diagnostics.
     * <p>
     * The pass is best effort rather than a guarantee: colon-separated
     * dialects that put a pair behind the colon rather than at the segment
     * start, like DB2's {@code :user=x:password=y}, and driver-specific
     * secrets that use a key other than the three above are not recognised,
     * and recognising them would require a full parse of each driver's URL
     * dialect. What a masked result says is that no credential of the
     * recognised shapes remains in the string, not that the string carries no
     * secret at all.
     *
     * @param value the raw configuration string, may be {@code null}
     *
     * @return the string with every recognised credential replaced by the mask,
     *         or the string unchanged when it holds none
     */
    private static String maskCredentials(String value) {
        if (value == null) {
            return null;
        }
        // The authority pass runs first: a userinfo-only URL carries no pair
        // the segment walk below would recognise, and its colons must not
        // reach the walk as candidate value separators.
        String scrubbed = maskUrlAuthority(value);
        // Walk the parser's segments: one pair per segment, key first, exactly
        // as java.util.Properties reads them after the semicolons became new
        // lines. Anything that is not a recognised credential pair - the
        // JDBC URL itself, driver values carrying colons or spaces - is copied
        // verbatim.
        StringBuilder result = new StringBuilder(scrubbed.length());
        int i = 0;
        boolean afterPairBoundary = true;
        while (i < scrubbed.length()) {
            int segmentEnd = i;
            while (segmentEnd < scrubbed.length() && !isCredentialSegmentSeparator(scrubbed.charAt(segmentEnd))) {
                segmentEnd++;
            }
            // The reader skips leading white space before a key.
            int keyStart = i;
            while (keyStart < segmentEnd && isCredentialValueSeparator(scrubbed.charAt(keyStart))) {
                keyStart++;
            }
            int keyEnd = keyStart;
            while (keyEnd < segmentEnd && !isCredentialValueSeparator(scrubbed.charAt(keyEnd))) {
                keyEnd++;
            }
            if (keyEnd < segmentEnd && isCredentialKey(scrubbed.substring(keyStart, keyEnd))) {
                // Keep the pair's key and the separator, hide the value.
                int valueEnd = segmentEnd;
                if (afterPairBoundary) {
                    // A pair the properties parser applies, so the value -
                    // the password the driver really receives - runs to the
                    // next boundary that parser sees. An '&' or '?' inside it
                    // is an ordinary character there, and a boundary the
                    // reader deletes as part of a backslash continuation is
                    // not such a boundary either.
                    valueEnd = segmentEnd;
                    while (valueEnd < scrubbed.length()) {
                        while (valueEnd < scrubbed.length()
                                && !isPairSeparator(scrubbed.charAt(valueEnd))) {
                            valueEnd++;
                        }
                        if (valueEnd < scrubbed.length() && continuesValueLine(scrubbed, valueEnd)) {
                            valueEnd++;
                            if (valueEnd < scrubbed.length() && scrubbed.charAt(valueEnd) == '\n') {
                                // The new line of a \r\n pair, which the
                                // reader deletes as one new line with the \r.
                                valueEnd++;
                            }
                        } else {
                            break;
                        }
                    }
                }
                result.append(scrubbed, i, keyEnd + 1);
                result.append(PASSWORD_MASK);
                i = valueEnd;
            } else {
                result.append(scrubbed, i, segmentEnd);
                i = segmentEnd;
            }
            if (i < scrubbed.length()) {
                // Keep the segment separator itself.
                char boundary = scrubbed.charAt(i);
                result.append(boundary);
                i++;
                afterPairBoundary = isPairSeparator(boundary);
            }
        }
        return result.toString();
    }


    /**
     * Masks the credential values carried in an interceptor definition
     * string, as a best-effort reading of that syntax rather than a parse of
     * it. The pair reading mirrors what {@link
     * #getJdbcInterceptorsAsArray()} delivers to an interceptor: the
     * comma-separated pairs inside a definition's parentheses, with a pair's
     * name being the text before its first {@code =}, trimmed. Every
     * parenthesised list of a definition is read, not just the one the parse
     * consumes, and a list that never closes is read to the end of the
     * definition: a pair typed in either place was typed as a credential,
     * even though a malformed definition hides it from the interceptor. A
     * pair whose name is one of the keys {@link #isCredentialKey recognises}
     * has its value replaced by the mask; everything else - the class names,
     * the property names and the non-credential values - is copied verbatim,
     * so the dump still shows which interceptors and properties are
     * configured. Like {@link #maskCredentials(String)} this rules out the
     * names this class treats as credentials, not any secret an interceptor
     * may hold under another name.
     *
     * @param value the raw interceptor definition string, may be {@code null}
     *
     * @return the string with every recognised credential value replaced by
     *         the mask, or the string unchanged when it holds none
     */
    private static String maskInterceptorCredentials(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder result = new StringBuilder(value.length());
        int segmentStart = 0;
        while (segmentStart < value.length()) {
            int segmentEnd = value.indexOf(';', segmentStart);
            if (segmentEnd < 0) {
                segmentEnd = value.length();
            }
            int cursor = segmentStart;
            int open = value.indexOf('(', cursor);
            while (open >= 0 && open < segmentEnd) {
                result.append(value, cursor, open + 1);
                int close = value.indexOf(')', open + 1);
                if (close < 0 || close > segmentEnd) {
                    // Never closed inside this segment (the closing
                    // parenthesis of a later definition does not close this
                    // one either): mask whatever was typed as a property to
                    // the end of the segment.
                    close = segmentEnd;
                }
                int pairStart = open + 1;
                while (pairStart < close) {
                    int pairEnd = value.indexOf(',', pairStart);
                    if (pairEnd < 0 || pairEnd >= close) {
                        pairEnd = close;
                    }
                    int separator = value.indexOf('=', pairStart);
                    if (separator >= 0 && separator < pairEnd
                            && isCredentialKey(value.substring(pairStart, separator).trim())) {
                        result.append(value, pairStart, separator + 1);
                        result.append(PASSWORD_MASK);
                    } else {
                        result.append(value, pairStart, pairEnd);
                    }
                    if (pairEnd < close) {
                        result.append(',');
                    }
                    pairStart = pairEnd + 1;
                }
                cursor = close;
                open = value.indexOf('(', cursor);
            }
            // What is left of the segment - the class name before the first
            // parenthesis and the text after the last closed property list -
            // is outside every property list.
            result.append(value, cursor, segmentEnd);
            if (segmentEnd < value.length()) {
                // Keep the definition separator itself.
                result.append(';');
            }
            segmentStart = segmentEnd + 1;
        }
        return result.toString();
    }


    /**
     * Whether {@code c} ends a key and starts the pair's value, covering the
     * separators a {@link java.util.Properties} reader accepts; white space
     * doubles as the leading white space the reader skips before a key.
     *
     * @param c the character to test
     *
     * @return {@code true} when the character is a key/value separator
     */
    private static boolean isCredentialValueSeparator(char c) {
        return c == '=' || c == ':' || c == ' ' || c == '\t' || c == '\f';
    }


    /**
     * Masks the password of the authority section of every URL in a raw
     * configuration string, as a best-effort reading of the URL rather than a
     * parse of a driver's dialect, covering the {@code user:password@host}
     * userinfo form of a {@code scheme://} URL and the {@code user/password@host}
     * form of the legacy Oracle driver URLs. The scan of an authority is
     * bounded so that a {@code :} of a port or an {@code @} or {@code :}
     * appearing later in the path or query is not mistaken for a credential
     * separator; a name that is not followed by a credential separator inside
     * the authority is treated as a bare host and left alone rather than
     * guessed at.
     *
     * @param value the raw configuration string, not {@code null}
     *
     * @return the string with every recognised authority password replaced by
     *         the mask, or the string unchanged when it carries none
     */
    private static String maskUrlAuthority(String value) {
        // Mask ranges are collected first and emitted in one pass at the end,
        // so the two recognisers below cannot interfere through the order in
        // which they happen to find their matches.
        List<int[]> ranges = new ArrayList<>(2);
        // Every scheme:// authority in the string: a failover or logging URL
        // carried in a value is as credential-bearing as the URL in front of
        // it, and the authority never contains "://" itself (the scan of one
        // authority ends at the first '/'), so the search resumes after it.
        int from = 0;
        int schemeEnd;
        while ((schemeEnd = value.indexOf("://", from)) >= 0) {
            int authorityStart = schemeEnd + 3;
            int authorityEnd = authorityStart;
            while (authorityEnd < value.length() && !isAuthorityTerminator(value.charAt(authorityEnd))) {
                authorityEnd++;
            }
            // An unencoded '@' inside the password itself can only extend the
            // userinfo, so the credential runs up to the last '@' of the
            // authority, if any.
            int at = value.lastIndexOf('@', authorityEnd - 1);
            if (at >= authorityStart) {
                int colon = value.indexOf(':', authorityStart);
                if (colon >= 0 && colon < at) {
                    ranges.add(new int[] { colon + 1, at });
                }
            }
            from = authorityEnd;
        }
        // Legacy Oracle form, e.g. jdbc:oracle:thin:user/password@host:1521:sid,
        // wherever it appears in the string - as the URL itself or as the
        // value of a pair. Its scan is bounded by the segment the prefix
        // starts in, so an '@' of a later URL cannot drag the mask across it.
        String oraclePrefix = "jdbc:oracle:";
        for (int p = indexOfIgnoreCase(value, oraclePrefix, 0); p >= 0;
                p = indexOfIgnoreCase(value, oraclePrefix, p + 1)) {
            int prefixEnd = p + oraclePrefix.length();
            int segmentEnd = prefixEnd;
            while (segmentEnd < value.length() && !isCredentialSegmentSeparator(value.charAt(segmentEnd))) {
                segmentEnd++;
            }
            int firstAt = value.indexOf('@', prefixEnd);
            if (firstAt >= 0 && firstAt < segmentEnd) {
                int slash = value.indexOf('/', prefixEnd);
                if (slash >= 0 && slash < firstAt) {
                    // The section opens with the user/password pair. An
                    // unencoded '@' inside the password itself can only
                    // extend the credential, so as in the scheme:// pass
                    // above it runs up to the last '@' of the segment; the
                    // at-sign-only connect forms, which carry neither a user
                    // nor a password, are already excluded by the slash
                    // lying behind the first '@'.
                    int at = value.lastIndexOf('@', segmentEnd - 1);
                    ranges.add(new int[] { slash + 1, at });
                }
            }
        }
        if (ranges.isEmpty()) {
            return value;
        }
        ranges.sort(Comparator.comparingInt(range -> range[0]));
        StringBuilder result = new StringBuilder(value.length());
        int copied = 0;
        for (int[] range : ranges) {
            if (range[0] < copied) {
                // Overlaps a mask already emitted - the pathological case of
                // one recognised form nested in the other's credential. The
                // earlier, outer mask has already hidden this region's start,
                // and re-masked text is never re-scanned.
                continue;
            }
            result.append(value, copied, range[0]);
            result.append(PASSWORD_MASK);
            copied = range[1];
        }
        result.append(value, copied, value.length());
        return result.toString();
    }


    /**
     * Finds the first occurrence of {@code needle} in {@code value} at or
     * after {@code from}, ignoring case.
     *
     * @param value  the string to search
     * @param needle the literal to look for
     * @param from   the index to start at
     *
     * @return the index of the match, or {@code -1}
     */
    private static int indexOfIgnoreCase(String value, String needle, int from) {
        for (int i = Math.max(from, 0); i <= value.length() - needle.length(); i++) {
            if (value.regionMatches(true, i, needle, 0, needle.length())) {
                return i;
            }
        }
        return -1;
    }


    /**
     * Whether {@code c} ends the authority section of a URL, covering the path,
     * query and the semicolon used as a parameter separator by some drivers.
     *
     * @param c the character to test
     *
     * @return {@code true} when the character ends the authority section
     */
    private static boolean isAuthorityTerminator(char c) {
        return c == '/' || c == '?' || c == ';';
    }


    /**
     * Whether {@code c} ends one configuration segment and starts the next,
     * covering the URL query separators, the semicolon used by the connection
     * properties string and the new lines that separate the pairs for the
     * parser that applies the string.
     *
     * @param c the character to test
     *
     * @return {@code true} when the character is a segment boundary
     */
    private static boolean isCredentialSegmentSeparator(char c) {
        return c == ';' || c == '&' || c == '?' || c == '\n' || c == '\r';
    }


    /**
     * Whether {@code c} ends a pair for the properties parser that applies
     * the connection properties string, one of the boundaries along which a
     * delivered value cannot run - see {@link #continuesValueLine(String,
     * int)} for the one new line that is no boundary at all. The query
     * separators {@code &} and {@code ?} are ordinary characters inside a
     * delivered value and so are deliberately not pair boundaries here,
     * whatever they separate once a driver reads a URL-shaped value itself.
     *
     * @param c the character to test
     *
     * @return {@code true} when the character ends a pair for the parser
     */
    private static boolean isPairSeparator(char c) {
        return c == ';' || c == '\n' || c == '\r';
    }


    /**
     * Whether the new line at {@code lineBreak} is one the reader deletes as
     * a line continuation, joining the next line into the delivered value:
     * the line ends in an odd number of backslashes, of which the last one
     * escapes the new line while the others stand as literal pairs.
     *
     * @param value     the string being walked
     * @param lineBreak the index of the new line to test
     *
     * @return {@code true} when the value continues on the next line
     */
    private static boolean continuesValueLine(String value, int lineBreak) {
        int backslashes = 0;
        for (int i = lineBreak - 1; i >= 0 && value.charAt(i) == '\\'; i--) {
            backslashes++;
        }
        return (backslashes & 1) == 1;
    }


    /**
     * Whether {@code key} names a credential, ignoring case and surrounding
     * whitespace.
     *
     * @param key the extracted key
     *
     * @return {@code true} when the key is one of the recognised credential keys
     */
    private static boolean isCredentialKey(String key) {
        return DataSourceFactory.PROP_PASSWORD.equalsIgnoreCase(key)
                || "pass".equalsIgnoreCase(key)
                || "pwd".equalsIgnoreCase(key);
    }

    /**
     * Returns the current pool counter value.
     *
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
        boolean timer = getTimeBetweenEvictionRunsMillis() > 0;
        boolean result = timer && (isRemoveAbandoned() && getRemoveAbandonedTimeout() > 0);
        result = result || (timer && getSuspectTimeout() > 0);
        result = result || (timer && isTestWhileIdle());
        result = result || (timer && getMinEvictableIdleTimeMillis() > 0);
        result = result || (timer && getMaxAge() > 0);
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
        protected Map<String, InterceptorProperty> properties = new HashMap<>();
        /**
         * Cached interceptor class.
         */
        protected volatile Class<?> clazz = null;

        /**
         * Constructs an InterceptorDefinition with the given class name.
         *
         * @param className the interceptor class name
         */
        public InterceptorDefinition(String className) {
            this.className = className;
        }

        /**
         * Constructs an InterceptorDefinition with the given class.
         *
         * @param cl the interceptor class
         */
        public InterceptorDefinition(Class<?> cl) {
            this(cl.getName());
            clazz = cl;
        }

        /**
         * Returns the interceptor class name.
         *
         * @return the class name
         */
        public String getClassName() {
            return className;
        }

        /**
         * Adds a property with the given name and value.
         *
         * @param name  the property name
         * @param value the property value
         */
        public void addProperty(String name, String value) {
            InterceptorProperty p = new InterceptorProperty(name, value);
            addProperty(p);
        }

        /**
         * Adds the given interceptor property.
         *
         * @param p the property to add
         */
        public void addProperty(InterceptorProperty p) {
            properties.put(p.getName(), p);
        }

        /**
         * Returns the map of interceptor properties.
         *
         * @return the properties map
         */
        public Map<String, InterceptorProperty> getProperties() {
            return properties;
        }

        /**
         * Returns the interceptor class, loading it if necessary.
         *
         * @return the interceptor class
         *
         * @throws ClassNotFoundException if the class cannot be found
         */
        @SuppressWarnings("unchecked")
        public Class<? extends JdbcInterceptor> getInterceptorClass() throws ClassNotFoundException {
            if (clazz == null) {
                if (getClassName().indexOf('.') < 0) {
                    if (log.isDebugEnabled()) {
                        log.debug("Loading interceptor class:" + PKG_PREFIX + getClassName());
                    }
                    clazz = ClassLoaderUtil.loadClass(PKG_PREFIX + getClassName(),
                            PoolProperties.class.getClassLoader(), Thread.currentThread().getContextClassLoader());
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("Loading interceptor class:" + getClassName());
                    }
                    clazz = ClassLoaderUtil.loadClass(getClassName(), PoolProperties.class.getClassLoader(),
                            Thread.currentThread().getContextClassLoader());
                }
            }
            return (Class<? extends JdbcInterceptor>) clazz;
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
         *
         * @param name  the property name, not {@code null}
         * @param value the property value
         * @throws NullPointerException if the name is {@code null} - the name
         *                              keys the property in the definitions'
         *                              map, so a nameless property could only
         *                              fail later, from the map's hashing
         */
        public InterceptorProperty(String name, String value) {
            this.name = Objects.requireNonNull(name,
                    () -> MessageFactory.get("poolProperties.interceptorPropertyNameRequired"));
            this.value = value;
        }

        /**
         * Returns the property name.
         *
         * @return the property name
         */
        public String getName() {
            return name;
        }

        /**
         * Returns the property value.
         *
         * @return the property value
         */
        public String getValue() {
            return value;
        }

        /**
         * Returns the property value as a boolean.
         *
         * @param def the default value if the property is null or invalid
         *
         * @return the boolean value
         */
        public boolean getValueAsBoolean(boolean def) {
            if (value == null) {
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
         *
         * @param def the default value if the property is null or invalid
         *
         * @return the int value
         */
        public int getValueAsInt(int def) {
            if (value == null) {
                return def;
            }
            try {
                int v = Integer.parseInt(value);
                return v;
            } catch (NumberFormatException nfe) {
                return def;
            }
        }

        /**
         * Returns the property value as a long.
         *
         * @param def the default value if the property is null or invalid
         *
         * @return the long value
         */
        public long getValueAsLong(long def) {
            if (value == null) {
                return def;
            }
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException nfe) {
                return def;
            }
        }

        /**
         * Returns the property value as a byte.
         *
         * @param def the default value if the property is null or invalid
         *
         * @return the byte value
         */
        public byte getValueAsByte(byte def) {
            if (value == null) {
                return def;
            }
            try {
                return Byte.parseByte(value);
            } catch (NumberFormatException nfe) {
                return def;
            }
        }

        /**
         * Returns the property value as a short.
         *
         * @param def the default value if the property is null or invalid
         *
         * @return the short value
         */
        public short getValueAsShort(short def) {
            if (value == null) {
                return def;
            }
            try {
                return Short.parseShort(value);
            } catch (NumberFormatException nfe) {
                return def;
            }
        }

        /**
         * Returns the property value as a float.
         *
         * @param def the default value if the property is null or invalid
         *
         * @return the float value
         */
        public float getValueAsFloat(float def) {
            if (value == null) {
                return def;
            }
            try {
                return Float.parseFloat(value);
            } catch (NumberFormatException nfe) {
                return def;
            }
        }

        /**
         * Returns the property value as a double.
         *
         * @param def the default value if the property is null or invalid
         *
         * @return the double value
         */
        public double getValueAsDouble(double def) {
            if (value == null) {
                return def;
            }
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException nfe) {
                return def;
            }
        }

        /**
         * Returns the property value as a char.
         * <p>
         * The method name is misspaced (capital {@code A} in {@code AsChar})
         * in the upstream API. It is left as published so the existing public
         * signature is not broken.
         *
         * @param def the default value if the property is null or invalid
         *
         * @return the char value
         */
        public char getValueAschar(char def) {
            if (value == null) {
                return def;
            }
            try {
                return value.charAt(0);
            } catch (StringIndexOutOfBoundsException nfe) {
                return def;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (o instanceof InterceptorProperty) {
                InterceptorProperty other = (InterceptorProperty) o;
                return Objects.equals(other.name, this.name);
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
            throw new IllegalArgumentException(MessageFactory.get("poolProperties.layeredPoolsNotAllowed"));
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
     *
     * @param propText the property string with semicolon-separated key=value pairs
     * @param props    the Properties object to populate, or null to create a new one
     *
     * @return the populated Properties object
     */
    public static Properties getProperties(String propText, Properties props) {
        if (props == null) {
            props = new Properties();
        }
        if (propText != null) {
            try {
                // Load through a Reader: Properties.load(InputStream) decodes as
                // ISO-8859-1 while encoding the string to bytes uses the platform
                // charset, silently corrupting any non-ASCII value on a
                // UTF-8-default JVM. A Reader carries the exact characters, so no
                // charset conversion takes place at all.
                props.load(new StringReader(propText.replace(';', '\n')));
            } catch (IOException ioe) {
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

    /**
     * {@inheritDoc}
     *
     * @deprecated The disposable connection facade is always used, so this property no longer has any effect.
     */
    @Override
    @Deprecated
    public void setUseDisposableConnectionFacade(boolean useDisposableConnectionFacade) {
        this.useDisposableConnectionFacade = useDisposableConnectionFacade;
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated The disposable connection facade is always used, so this property no longer has any effect.
     */
    @Override
    @Deprecated
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

    /**
     * {@inheritDoc}
     *
     * @deprecated The statement facade is always used, so this property no longer has any effect.
     */
    @Override
    @Deprecated
    public boolean getUseStatementFacade() {
        return useStatementFacade;
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated The statement facade is always used, so this property no longer has any effect.
     */
    @Override
    @Deprecated
    public void setUseStatementFacade(boolean useStatementFacade) {
        this.useStatementFacade = useStatementFacade;
    }

    /**
     * Returns a copy of the configuration. Beyond the field-by-field copy,
     * the driver-properties collection is copied too, through
     * {@link PoolUtilities#clone(Properties)} - the same copy the connect
     * path makes - so a defaults-backed property that resolves on the
     * original also resolves on the copy.
     * {@link #setConnectionProperties(String)} removes the keys the previous
     * value of the string contributed, so a clone sharing the live collection
     * would have a reconfigure on either copy strip the other one's pairs. The
     * tracked key set is never mutated after publication, so both copies may
     * share it, and it stays consistent with the copied entries.
     * <p>
     * The copy is taken under the monitor the reconfiguring setters hold, so
     * a reconfiguration concurrent with the clone cannot appear in the copy
     * as a state no setter ever published: a stale driver-property entry the
     * copy's tracked key set would never remove again, or a fresh interceptor
     * string whose published parse cache still holds the previous chain -
     * which the cached read would then never re-parse.
     *
     * @return the copy
     * @throws CloneNotSupportedException if cloning of this class is ever
     *                                    refused
     */
    @Override
    protected synchronized Object clone() throws CloneNotSupportedException {
        PoolProperties clone = (PoolProperties) super.clone();
        Properties dbProperties = clone.dbProperties;
        if (dbProperties != null) {
            clone.dbProperties = PoolUtilities.clone(dbProperties);
        }
        return clone;
    }


    /**
     * Serializes the configuration under the monitor the reconfiguring
     * setters hold, for the same torn-snapshot reason {@link #clone()}
     * synchronizes for: the setters publish a reconfiguration over several
     * field writes, and a field-by-field serialization that lands between
     * them would hand the stream a state no setter ever published. The
     * monitor is a leaf, so holding it across the write cannot order against
     * any other lock.
     *
     * @param oos the stream to write the object graph to
     *
     * @throws IOException if the stream fails
     */
    private void writeObject(ObjectOutputStream oos) throws IOException {
        synchronized (this) {
            oos.defaultWriteObject();
        }
    }


}

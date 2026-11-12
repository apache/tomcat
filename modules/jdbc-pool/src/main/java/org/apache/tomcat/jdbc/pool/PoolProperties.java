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
import java.io.ObjectInputStream;
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
 * <p>
 * The setters that maintain the database properties, the credentials, the validator and the interceptor list publish
 * their changes under this instance's monitor, which is also the monitor {@link #clone()} and serialization take.
 * Those setters are the snapshot boundary for the state they own. Setters that assign a single plain field do not take
 * the monitor, so a copy taken under it is not a consistent snapshot of those fields against a concurrent write.
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
    /**
     * The minimum number of idle connections, initialized once from
     * {@link #initialSize} at construction; a later change to
     * {@code initialSize} does not move it.
     */
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
     * The pool name. This default is the fallback for a pool nobody named: a
     * container-managed pool is given a name of its own before it starts, so
     * what is left to name here is a pool built programmatically, once per
     * {@code PoolProperties} instance. The counter is what makes those names
     * distinct within this class loader; upstream's default also carried the
     * identity hash of this class object, which identifies only the class
     * loader that loaded it - the same value for every pool in a JVM - and so
     * is not in the name any more.
     */
    private volatile String name = "Tomcat Connection Pool[" + (poolCounter.addAndGet(1)) + "]";
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
    public synchronized void setValidator(Validator validator) {
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
     *
     * <p>The returned array is a copy, so a caller cannot change the cached
     * definition array or the chain later connections build. The definitions
     * it holds are shared and must be treated as read-only.</p>
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
                    String segment = interceptorValues[i];
                    int propIndex = segment.indexOf('(');
                    String name;
                    String propsAsString = null;
                    if (propIndex < 0) {
                        name = segment.trim();
                        if (name.indexOf(')') >= 0) {
                            throw invalidInterceptorDefinition(segment);
                        }
                    } else {
                        int endIndex = findClosingParenthesis(segment, propIndex);
                        if (endIndex < 0) {
                            throw invalidInterceptorDefinition(segment);
                        }
                        if (!segment.substring(endIndex + 1).trim().isEmpty()) {
                            // Silently ignoring text after the closing
                            // parenthesis would apply a configuration other
                            // than the one written.
                            throw invalidInterceptorDefinition(segment);
                        }
                        name = segment.substring(0, propIndex).trim();
                        propsAsString = segment.substring(propIndex + 1, endIndex);
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
                        for (String prop : splitInterceptorProperties(propsAsString)) {
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
                                        maskInterceptorCredentials(segment)));
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
        return interceptors.clone();
    }


    /**
     * Finds the parenthesis matching the one at {@code openIndex}, honoring
     * nested parentheses inside a property value.
     *
     * @param value     the segment containing the opening parenthesis
     * @param openIndex the index of the opening parenthesis
     *
     * @return the index of the matching closing parenthesis, or {@code -1}
     */
    private static int findClosingParenthesis(String value, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }


    /**
     * Splits an interceptor property list at commas that are not inside
     * parentheses, so a value may carry a parenthesized list containing
     * commas. Empty segments are kept for the caller to tolerate.
     *
     * @param props the property list text
     *
     * @return the property segments
     */
    private static List<String> splitInterceptorProperties(String props) {
        List<String> segments = new ArrayList<>();
        int start = 0;
        int depth = 0;
        for (int i = 0; i < props.length(); i++) {
            char c = props.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')' && depth > 0) {
                depth--;
            } else if (c == ',' && depth == 0) {
                segments.add(props.substring(start, i));
                start = i + 1;
            }
        }
        segments.add(props.substring(start));
        return segments;
    }


    /**
     * Builds the failure for an interceptor definition whose parentheses or
     * trailing text cannot be parsed, with credentials in the definition
     * masked.
     *
     * @param segment the malformed definition segment
     *
     * @return the named failure
     */
    private static IllegalArgumentException invalidInterceptorDefinition(String segment) {
        return new IllegalArgumentException(MessageFactory.get(
                "poolProperties.invalidInterceptorDefinition", maskInterceptorCredentials(segment)));
    }


    @Override
    public void setAccessToUnderlyingConnectionAllowed(boolean accessToUnderlyingConnectionAllowed) {
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
     * is no property collection to parse into; the connect path still parses
     * the string when it builds the driver properties, so the null collection
     * does not make the configured pairs inert. When there is a collection, the
     * string is parsed into it before any state is touched, so a value the
     * parser rejects - a malformed Unicode
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
        // Removing a key only reaches a directly present entry, so when the
        // collection is defaults-backed the removal can uncover the defaults
        // value instead of dropping the key. Detect that on each removal and,
        // if it happens, continue on a defaults-free copy that is published
        // instead of the live collection.
        Properties writable = dbProps;
        // A configuration serialized before this field existed deserializes
        // with it null, as field initializers do not run on deserialization.
        if (connectionPropertyKeys != null) {
            for (String key : connectionPropertyKeys) {
                writable.remove(key);
                if (writable.getProperty(key) != null) {
                    Properties copy = PoolUtilities.clone(writable);
                    copy.remove(key);
                    writable = copy;
                }
            }
        }
        @SuppressWarnings("null")
        Set<String> keys = new HashSet<>(parsed.size());
        for (String key : parsed.stringPropertyNames()) {
            writable.setProperty(key, parsed.getProperty(key));
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
            writable.setProperty(PoolUtilities.PROP_USER, username);
        }
        if (password != null) {
            writable.setProperty(PoolUtilities.PROP_PASSWORD, password);
        }
        dbProperties = writable;
        connectionPropertyKeys = keys;
    }


    /**
     * Replaces the database properties the pool connects with. The keys tracked
     * from the last connection properties parse contributed to the previous
     * collection, not to the replacement, so the tracking is reset: the caller
     * owns whatever the new collection holds. The current
     * {@link #connectionProperties} string is not parsed into the replacement;
     * call {@link #setConnectionProperties(String)} after this method to
     * reapply it, or put the string's pairs in the replacement collection.
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
            // A non-null password replaces every spelling of the credential, not only the canonical
            // lower-case key: an older pass, pwd or PASSWORD entry must not keep authenticating
            // connections with the superseded value.
            removeCredentialDbProperties(password == null);
            if (password != null) {
                this.dbProperties.setProperty(PoolUtilities.PROP_PASSWORD, password);
            }
        }
    }


    /**
     * Removes every database property whose key is recognized as a password alias, so clearing the password also
     * stops a value configured as {@code pass} or {@code pwd} from still authenticating connections. When a new
     * password replaces the old one, the canonical entry is written again after this removal; the warning for a
     * credential the string still names is only wanted when the credential is actually being cleared.
     *
     * @param warnStringEntries whether to warn for each removed key the configured connection-properties string still
     *                              names
     */
    private void removeCredentialDbProperties(boolean warnStringEntries) {
        Properties props = this.dbProperties;
        if (props == null) {
            return;
        }
        // The keys have to be collected from the direct entries as well as the
        // resolved String names: stringPropertyNames() omits a key whose direct
        // value is not a String, and such an entry must still be removed.
        for (String key : allPropertyKeys(props)) {
            if (PoolUtilities.isCredentialKey(key)) {
                removeDbProperty(key);
                if (warnStringEntries) {
                    warnStringStillNamesCredential(key);
                }
            }
        }
    }


    /**
     * Removes every database property whose key names the user, ignoring case
     * and surrounding whitespace, so clearing the username also stops a value
     * configured as {@code USER} from still authenticating connections. A new
     * user name is written again after this removal.
     *
     * @param warnStringEntries whether to warn for each removed key the configured connection-properties string still
     *                              names
     */
    private void removeUsernameDbProperties(boolean warnStringEntries) {
        Properties props = this.dbProperties;
        if (props == null) {
            return;
        }
        for (String key : allPropertyKeys(props)) {
            if (PoolUtilities.PROP_USER.equalsIgnoreCase(key.trim())) {
                removeDbProperty(key);
                if (warnStringEntries) {
                    warnStringStillNamesCredential(key);
                }
            }
        }
    }


    /**
     * Returns a snapshot of every String key a properties collection currently
     * resolves, including the keys whose only value is not a String. The
     * removal loops use the snapshot because removing an uncovered defaults
     * value can publish a replacement collection.
     *
     * @param props the properties to inspect
     *
     * @return the snapshot of String keys
     */
    private static Set<String> allPropertyKeys(Properties props) {
        Set<String> keys = new HashSet<>();
        for (Object key : props.keySet().toArray()) {
            if (key instanceof String) {
                keys.add((String) key);
            }
        }
        keys.addAll(props.stringPropertyNames());
        return keys;
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
     * contributed it, including a pair the {@code connectionProperties} string names; the key is matched ignoring
     * case and surrounding whitespace, so a {@code USER} spelling cannot keep authenticating connections either.
     *
     * @param username The user name
     */
    @Override
    public synchronized void setUsername(String username) {
        this.username = username;
        if (this.dbProperties != null) {
            // A non-null user name replaces every spelling of the key, so an older USER entry cannot keep
            // authenticating connections with the superseded identity.
            removeUsernameDbProperties(username == null);
            if (username != null) {
                this.dbProperties.setProperty(PoolUtilities.PROP_USER, username);
            }
        }
    }


    /**
     * Removes an entry from the database properties, including a value that
     * the collection only resolves through a defaults table. A plain
     * {@link Properties#remove(Object)} reaches only a directly present entry,
     * so it would leave such a value in place - and the connect path
     * materializes it into the copy handed to the driver. The direct entry is
     * removed in place, and only when that uncovers a defaults value is the
     * result materialized into a defaults-free copy and published instead.
     * Called with the monitor held.
     *
     * @param key the property key to remove
     */
    private void removeDbProperty(String key) {
        Properties props = this.dbProperties;
        if (props == null) {
            return;
        }
        props.remove(key);
        if (props.getProperty(key) != null) {
            Properties copy = PoolUtilities.clone(props);
            copy.remove(key);
            this.dbProperties = copy;
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


    /**
     * Stores the validation query through {@link #configuredStatement(String)}:
     * a blank query is the unset value it means, not a statement. A query that
     * carries anything is stored verbatim.
     *
     * @param validationQuery the query used for validation, or null or a blank
     *                            string if no validation query is set
     */
    @Override
    public void setValidationQuery(String validationQuery) {
        this.validationQuery = configuredStatement(validationQuery);
    }


    @Override
    public synchronized void setValidatorClassName(String className) {
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

        if (validator == null) {
            // The line above names the cause; this names the consequence,
            // at the level an operator sees without enabling anything: the
            // configuration keeps reporting the validator's class name,
            // while validation in fact runs on the query path. Left at the
            // warning alone, that disagreement is invisible in the default
            // log.
            log.error(MessageFactory.get("poolProperties.validatorNotInEffect", className));
        }
    }


    /**
     * Stores the init SQL through {@link #configuredStatement(String)}: a
     * blank init SQL is the unset value it means, not a statement a new
     * session should be given. A statement that carries anything is stored
     * verbatim.
     *
     * @param initSQL the statement a newly established session is
     *                    initialized with, or null or a blank string if there
     *                    is none
     */
    @Override
    public void setInitSQL(String initSQL) {
        this.initSQL = configuredStatement(initSQL);
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
     * <p>
     * Changing the list on a live pool takes effect only for connections whose interceptor chains are built after the
     * change. A pooled connection that already has a chain keeps it until that connection is destroyed; an ordinary
     * return and re-borrow does not rebuild it. The pool's lifecycle notifications follow the start, not the moment: the
     * close is delivered for the definitions configured when the pool started, whatever this property lists by then,
     * so removing a definition from a live pool does not strand what its registration at the pool's start put in
     * place.
     * </p>
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
     * and a configured live {@code dataSource} object is printed as its class
     * and identity rather than through its own {@code toString()}, and the
     * {@code initSQL} and {@code validationQuery} statements print as
     * {@code <sql>} rather than as text SQL syntax can shape around a
     * credential, so the dump
     * can be logged without carrying a cleartext credential. The
     * masking is best effort in the way {@link #maskCredentials(String)} and
     * {@link #maskInterceptorCredentials(String)} describe, so it removes the
     * credentials an operator normally configures rather than proving the
     * configuration holds no other secret. A property whose getter fails is
     * reported as an error in its own field and does not truncate the rest of
     * the dump. {@code object_name} has no getter of its own: the factory
     * applies it to the pool name, which the dump shows as {@code name}.
     *
     * @return the dump, always bracket-balanced even on internal failure
     */
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("ConnectionPool[");
        try {
            // object_name has no getter of its own: the factory applies it to
            // the pool name, which the dump therefore prints explicitly.
            buf.append("name=").append(sanitizeForLog(name)).append("; ");
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
                    // One bad property - a subclass getter that throws, or an
                    // object-valued property whose toString() throws - must cost
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
                            buf.append(value == null ? "null" : sanitizeForLog(maskCredentials((String) value)));
                        } else if (DataSourceFactory.PROP_INTERCEPTORS.equals(field)) {
                            // Interceptor properties reach a third-party
                            // interceptor through setProperties(), so this
                            // configuration string carries credentials the
                            // same way; its pairs live inside parentheses,
                            // which the segment-start walk above cannot see.
                            buf.append(value == null ? "null" : sanitizeForLog(maskInterceptorCredentials((String) value)));
                        } else if (DataSourceFactory.PROP_DATASOURCE.equals(field)) {
                            // A live data source object prints its identity
                            // only: its own toString() may carry the URL, the
                            // user or a password, and this dump promises not
                            // to hand those back.
                            buf.append(value == null ? "null"
                                    : value.getClass().getName() + "@" +
                                            Integer.toHexString(System.identityHashCode(value)));
                        } else if (DataSourceFactory.PROP_INITSQL.equals(field)
                                || DataSourceFactory.PROP_VALIDATIONQUERY.equals(field)) {
                            // SQL statement text is application-shaped and can
                            // embed a credential in syntax the credential
                            // masker does not know. The dump reports only the
                            // presence of the statement.
                            buf.append(value == null ? "null" : "<sql>");
                        } else {
                            buf.append(value == null ? "null" : sanitizeForLog(String.valueOf(value)));
                        }
                    } catch (ReflectiveOperationException | RuntimeException | Error x) {
                        Throwable cause = ExceptionUtils.unwrapInvocationTargetException(x);
                        ExceptionUtils.handleThrowable(cause);
                        try {
                            buf.append("<error: ").append(sanitizeForLog(String.valueOf(cause))).append('>');
                        } catch (Throwable hostile) {
                            // A Throwable whose own toString() throws must
                            // cost only its own field, not every field after
                            // it: fall back to its class name.
                            ExceptionUtils.handleThrowable(hostile);
                            buf.append("<error: ").append(cause.getClass().getName()).append('>');
                        }
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
     * Replaces carriage returns and line feeds with spaces in a value that is
     * interpolated into the one-line configuration dump, so application-shaped
     * text cannot forge additional log lines.
     *
     * @param value the text, possibly {@code null}
     *
     * @return the text with line breaks flattened, or {@code null}
     */
    private static String sanitizeForLog(String value) {
        if (value == null || (value.indexOf('\r') < 0 && value.indexOf('\n') < 0)) {
            return value;
        }
        return value.replace('\r', ' ').replace('\n', ' ');
    }


    /**
     * Returns the statement an SQL setting carries, or {@code null} when it carries none. A blank is not a statement:
     * a configuration surface that cannot express the unset value writes blank to mean exactly that. Both
     * {@link #setValidationQuery(String)} and {@link #setInitSQL(String)} store and read through this rule.
     *
     * @param sql the configured or passed statement, may be {@code null}
     *
     * @return the statement itself if it carries anything, {@code null} if it is null or blank
     */
    static String configuredStatement(String sql) {
        return sql != null && sql.trim().length() > 0 ? sql : null;
    }


    /**
     * Masks the credentials embedded in a raw configuration string so that a password typed into the JDBC URL or the
     * connection properties is not printed in cleartext. The pool applies this wherever it logs diagnostics about these
     * strings, so the log and the configuration dump agree on what a masked configuration looks like. Two shapes are
     * scrubbed: the value of a pair whose key is {@code password}, {@code pass} or {@code pwd}, and the password of a
     * URL {@code user:password@host} or {@code user/password@host} userinfo section. The pass is best effort: secrets
     * carried under another key or in a driver-specific dialect are not recognised.
     *
     * @param value the raw configuration string, may be {@code null}
     *
     * @return the string with every recognised credential replaced by the mask, or the string unchanged when it holds
     *             none
     */
    public static String maskCredentials(String value) {
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
     * Masks the credential values carried in an interceptor definition string, as a best-effort reading of that syntax
     * rather than a parse of it. Pairs at any parenthesis depth whose name is a key {@link #isCredentialKey recognises}
     * have their value replaced by the mask; class names, property names and non-credential values are copied verbatim.
     * Like {@link #maskCredentials(String)} this rules out the names this class treats as credentials, not any secret
     * an interceptor may hold under another name.
     *
     * @param value the raw interceptor definition string, may be {@code null}
     *
     * @return the string with every recognised credential value replaced by the mask, or the string unchanged when it
     *             holds none
     */
    public static String maskInterceptorCredentials(String value) {
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
            maskInterceptorSegment(value, segmentStart, segmentEnd, result);
            if (segmentEnd < value.length()) {
                // Keep the definition separator itself.
                result.append(';');
            }
            segmentStart = segmentEnd + 1;
        }
        return result.toString();
    }


    /**
     * Appends one definition segment, masking a recognised credential pair at
     * any parenthesis depth. A {@code (} or {@code )} changes the depth and a
     * comma at a property-list depth starts a new pair; a pair typed inside a
     * nested list is therefore read the same way as one in the list the
     * interceptor parser consumes. A list that never closes is read to the end
     * of the segment.
     *
     * @param value the string being masked
     * @param start the start of the segment
     * @param end the end of the segment, exclusive
     * @param result the masked output
     */
    private static void maskInterceptorSegment(String value, int start, int end, StringBuilder result) {
        int depth = 0;
        int i = start;
        boolean pairStart = false;
        while (i < end) {
            char c = value.charAt(i);
            if (c == '(') {
                result.append(c);
                depth++;
                i++;
                pairStart = depth > 0;
                continue;
            }
            if (c == ')') {
                result.append(c);
                if (depth > 0) {
                    depth--;
                }
                i++;
                pairStart = false;
                continue;
            }
            if (c == ',' && depth > 0) {
                result.append(c);
                i++;
                pairStart = true;
                continue;
            }
            if (pairStart) {
                int separator = -1;
                int j = i;
                while (j < end) {
                    char next = value.charAt(j);
                    if (next == '=') {
                        separator = j;
                        break;
                    }
                    if (next == ',' || next == ')') {
                        break;
                    }
                    j++;
                }
                if (separator >= 0 && isCredentialKey(value.substring(i, separator).trim())) {
                    result.append(value, i, separator + 1);
                    result.append(PASSWORD_MASK);
                    int valueEnd = separator + 1;
                    int valueDepth = 0;
                    while (valueEnd < end) {
                        char next = value.charAt(valueEnd);
                        if (next == '(') {
                            valueDepth++;
                        } else if (next == ')') {
                            if (valueDepth == 0) {
                                break;
                            }
                            valueDepth--;
                        } else if (next == ',' && valueDepth == 0) {
                            break;
                        }
                        valueEnd++;
                    }
                    i = valueEnd;
                    pairStart = false;
                    continue;
                }
                pairStart = false;
            }
            result.append(c);
            i++;
        }
    }


    /**
     * Masks the credential values carried by an array of parsed interceptor
     * definitions, the display counterpart of
     * {@link #maskInterceptorCredentials(String)} for what
     * {@link #getJdbcInterceptorsAsArray()} has already parsed out of that
     * string. Every definition of the array is replaced by a new definition of
     * the same class name, and every property whose name is one of the keys
     * {@link #isCredentialKey recognises} by a property holding the mask; the
     * other property names and values, and the order of the array, are copied as
     * they stand, so the copy still shows which interceptors are configured and
     * which properties they carry. The copy is also defensive: it shares no
     * definition, no property and no array slot with the array it was made from,
     * so a reader handed the copy cannot reach through it to the definitions the
     * pool builds its interceptor chains from. Like the masking of the string
     * this rules out the names this class treats as credentials, not any secret
     * an interceptor may hold under another name.
     * <p>
     * The copy carries the interceptor's class <em>name</em> rather than the
     * class object a definition may have cached: a display copy is never meant
     * to instantiate an interceptor, and loading a class is not something a
     * read of the configuration should do.
     * </p>
     *
     * @param definitions the parsed definitions, may be {@code null}
     *
     * @return a masked copy of the array, or {@code null} when the array is
     *         {@code null}
     */
    public static InterceptorDefinition[] maskInterceptorCredentials(InterceptorDefinition[] definitions) {
        if (definitions == null) {
            return null;
        }
        InterceptorDefinition[] result = new InterceptorDefinition[definitions.length];
        for (int i = 0; i < definitions.length; i++) {
            InterceptorDefinition definition = definitions[i];
            if (definition == null) {
                // An array this class did not build may hold a hole; a hole is
                // copied as a hole rather than turned into a failure on the
                // read of a configuration.
                continue;
            }
            InterceptorDefinition copy = new InterceptorDefinition(definition.getClassName());
            for (InterceptorProperty property : definition.getProperties().values()) {
                copy.addProperty(new InterceptorProperty(property.getName(),
                        isCredentialKey(property.getName()) ? PASSWORD_MASK : property.getValue()));
            }
            result[i] = copy;
        }
        return result;
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
     * whitespace. The predicate is shared with the masked property copies
     * {@link PoolUtilities#cloneWithoutPassword(Properties)} builds, so the
     * two policies cannot drift apart.
     *
     * @param key the extracted key
     *
     * @return {@code true} when the key is one of the recognised credential keys
     */
    private static boolean isCredentialKey(String key) {
        return PoolUtilities.isCredentialKey(key);
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
         * Returns an independent copy of this definition, including its property map and the cached class reference. A
         * configuration clone shares no mutable definition state with its original: adding or replacing a property on
         * one copy cannot change what the other copy configures.
         *
         * @return the copy
         */
        InterceptorDefinition copy() {
            InterceptorDefinition copy = new InterceptorDefinition(className);
            copy.clazz = clazz;
            for (Map.Entry<String, InterceptorProperty> entry : properties.entrySet()) {
                InterceptorProperty property = entry.getValue();
                copy.properties.put(entry.getKey(), new InterceptorProperty(property.getName(), property.getValue()));
            }
            return copy;
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
         * Returns the interceptor class, loading it if necessary. The configured class must extend
         * {@link JdbcInterceptor}: a class that loads but does not extend it fails the pool start with a
         * {@link ClassCastException} when this method returns.
         *
         * @return the interceptor class
         *
         * @throws ClassNotFoundException if the class cannot be found
         * @throws ClassCastException if the class does not extend {@link JdbcInterceptor}
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
     * Represents a property for a JDBC interceptor. Equality and hashing use the property name only, because the name
     * keys the property in the definitions' map; two properties with the same name but different values compare equal.
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
         * The method name has a lower-case {@code c} ({@code getValueAschar}
         * rather than {@code getValueAsChar}) in the upstream API. It is left
         * as published so the existing public signature is not broken.
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
     * Returns a copy of the configuration, including a copy of the driver-properties collection so a defaults-backed
     * property resolves on the copy too, and independent copies of the parsed interceptor definitions and their
     * property maps. A property added to or replaced on a definition in one copy therefore cannot reconfigure the
     * other. The validator instance is runtime state and is shared by the copy; replacing it through
     * {@link #setValidator(Validator)} or {@link #setValidatorClassName(String)} only changes the copy it is called
     * on. The copy is taken under the monitor the reconfiguring setters hold, so a concurrent reconfiguration cannot
     * appear in the copy as a state no setter ever published.
     *
     * @return the copy
     * @throws CloneNotSupportedException if cloning of this class is ever refused
     */
    @Override
    protected synchronized Object clone() throws CloneNotSupportedException {
        PoolProperties clone = (PoolProperties) super.clone();
        Properties dbProperties = clone.dbProperties;
        if (dbProperties != null) {
            clone.dbProperties = PoolUtilities.clone(dbProperties);
        }
        InterceptorDefinition[] definitions = clone.interceptors;
        if (definitions != null) {
            InterceptorDefinition[] copies = new InterceptorDefinition[definitions.length];
            for (int i = 0; i < definitions.length; i++) {
                copies[i] = definitions[i] == null ? null : definitions[i].copy();
            }
            clone.interceptors = copies;
        }
        Set<String> keys = clone.connectionPropertyKeys;
        if (keys != null && !keys.isEmpty()) {
            clone.connectionPropertyKeys = new HashSet<>(keys);
        }
        return clone;
    }


    /**
     * Serializes the configuration under the monitor the reconfiguring setters hold, so the stream cannot capture a
     * state between two writes of one reconfiguration.
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


    /**
     * Replays the validator configuration after deserialization. The validator
     * instance is transient - it is runtime state, an object loaded through
     * some class loader and carrying whatever that loader can reach - but the
     * class name it was built from is serialized, and a configuration that
     * read back the name without rebuilding the instance would validate on the
     * query path while reporting the validator as configured, which is the
     * disagreement {@link #setValidatorClassName(String)} was made to log.
     * Replaying the setter rebuilds the instance in the reading JVM, or
     * reports - as the original setter did - why it could not be built there.
     *
     * @param ois the stream to read the object graph from
     *
     * @throws IOException if the stream fails
     * @throws ClassNotFoundException if a serialized class cannot be found
     */
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        if (validatorClassName != null) {
            setValidatorClassName(validatorClassName);
        }
    }


}

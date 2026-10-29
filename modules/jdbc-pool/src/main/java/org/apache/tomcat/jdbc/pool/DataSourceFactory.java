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


import java.sql.Connection;
import java.util.Hashtable;
import java.util.Properties;

import javax.management.ObjectName;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;
import javax.sql.DataSource;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * <p>
 * JNDI object factory that creates an instance of <code>BasicDataSource</code> that has been configured based on the
 * <code>RefAddr</code> values of the specified <code>Reference</code>, which must match the names and data types of the
 * <code>BasicDataSource</code> bean properties.
 * </p>
 * <br>
 * Properties available for configuration:<br>
 * <a href="https://commons.apache.org/dbcp/configuration.html">Commons DBCP properties</a><br>
 * <ol>
 * <li>initSQL - A query that gets executed once, right after the connection is established.</li>
 * <li>testOnConnect - run validationQuery after connection has been established.</li>
 * <li>validationInterval - avoid excess validation, only run validation at most at this frequency - time in
 * milliseconds.</li>
 * <li>jdbcInterceptors - a semicolon separated list of classnames extending {@link JdbcInterceptor} class.</li>
 * <li>jmxEnabled - true of false, whether to register the pool with JMX.</li>
 * <li>fairQueue - true of false, whether the pool should sacrifice a little bit of performance for true fairness.</li>
 * </ol>
 */
public class DataSourceFactory implements ObjectFactory {
    /**
     * Default constructor.
     */
    public DataSourceFactory() {
    }

    private static final Log log = LogFactory.getLog(DataSourceFactory.class);

    /**
     * Property name for defaultAutoCommit.
     */
    protected static final String PROP_DEFAULTAUTOCOMMIT = "defaultAutoCommit";

    /**
     * Property name for defaultReadOnly.
     */
    protected static final String PROP_DEFAULTREADONLY = "defaultReadOnly";

    /**
     * Property name for defaultTransactionIsolation.
     */
    protected static final String PROP_DEFAULTTRANSACTIONISOLATION = "defaultTransactionIsolation";

    /**
     * Property name for defaultCatalog.
     */
    protected static final String PROP_DEFAULTCATALOG = "defaultCatalog";

    /**
     * Property name for driverClassName.
     */
    protected static final String PROP_DRIVERCLASSNAME = "driverClassName";

    /**
     * Property name for password.
     */
    protected static final String PROP_PASSWORD = "password";

    /**
     * Property name for url.
     */
    protected static final String PROP_URL = "url";

    /**
     * Property name for username.
     */
    protected static final String PROP_USERNAME = "username";

    /**
     * Property name for maxActive.
     */
    protected static final String PROP_MAXACTIVE = "maxActive";

    /**
     * Property name for maxIdle.
     */
    protected static final String PROP_MAXIDLE = "maxIdle";

    /**
     * Property name for minIdle.
     */
    protected static final String PROP_MINIDLE = "minIdle";

    /**
     * Property name for initialSize.
     */
    protected static final String PROP_INITIALSIZE = "initialSize";

    /**
     * Property name for maxWait.
     */
    protected static final String PROP_MAXWAIT = "maxWait";

    /**
     * Property name for maxAge.
     */
    protected static final String PROP_MAXAGE = "maxAge";

    /**
     * Property name for testOnBorrow.
     */
    protected static final String PROP_TESTONBORROW = "testOnBorrow";

    /**
     * Property name for testOnReturn.
     */
    protected static final String PROP_TESTONRETURN = "testOnReturn";

    /**
     * Property name for testWhileIdle.
     */
    protected static final String PROP_TESTWHILEIDLE = "testWhileIdle";

    /**
     * Property name for testOnConnect.
     */
    protected static final String PROP_TESTONCONNECT = "testOnConnect";

    /**
     * Property name for validationQuery.
     */
    protected static final String PROP_VALIDATIONQUERY = "validationQuery";

    /**
     * Property name for validationQueryTimeout.
     */
    protected static final String PROP_VALIDATIONQUERY_TIMEOUT = "validationQueryTimeout";

    /**
     * Property name for validatorClassName.
     */
    protected static final String PROP_VALIDATOR_CLASS_NAME = "validatorClassName";

    /**
     * Property name for numTestsPerEvictionRun.
     */
    protected static final String PROP_NUMTESTSPEREVICTIONRUN = "numTestsPerEvictionRun";

    /**
     * Property name for timeBetweenEvictionRunsMillis.
     */
    protected static final String PROP_TIMEBETWEENEVICTIONRUNSMILLIS = "timeBetweenEvictionRunsMillis";

    /**
     * Property name for minEvictableIdleTimeMillis.
     */
    protected static final String PROP_MINEVICTABLEIDLETIMEMILLIS = "minEvictableIdleTimeMillis";

    /**
     * Property name for accessToUnderlyingConnectionAllowed.
     */
    protected static final String PROP_ACCESSTOUNDERLYINGCONNECTIONALLOWED = "accessToUnderlyingConnectionAllowed";

    /**
     * Property name for removeAbandoned.
     */
    protected static final String PROP_REMOVEABANDONED = "removeAbandoned";

    /**
     * Property name for removeAbandonedTimeout.
     */
    protected static final String PROP_REMOVEABANDONEDTIMEOUT = "removeAbandonedTimeout";

    /**
     * Property name for logAbandoned.
     */
    protected static final String PROP_LOGABANDONED = "logAbandoned";

    /**
     * Property name for abandonWhenPercentageFull.
     */
    protected static final String PROP_ABANDONWHENPERCENTAGEFULL = "abandonWhenPercentageFull";

    /**
     * Property name for poolPreparedStatements.
     */
    protected static final String PROP_POOLPREPAREDSTATEMENTS = "poolPreparedStatements";

    /**
     * Property name for maxOpenPreparedStatements.
     */
    protected static final String PROP_MAXOPENPREPAREDSTATEMENTS = "maxOpenPreparedStatements";

    /**
     * Property name for connectionProperties.
     */
    protected static final String PROP_CONNECTIONPROPERTIES = "connectionProperties";

    /**
     * Property name for initSQL.
     */
    protected static final String PROP_INITSQL = "initSQL";

    /**
     * Property name for jdbcInterceptors.
     */
    protected static final String PROP_INTERCEPTORS = "jdbcInterceptors";

    /**
     * Property name for validationInterval.
     */
    protected static final String PROP_VALIDATIONINTERVAL = "validationInterval";

    /**
     * Property name for jmxEnabled.
     */
    protected static final String PROP_JMX_ENABLED = "jmxEnabled";

    /**
     * Property name for fairQueue.
     */
    protected static final String PROP_FAIR_QUEUE = "fairQueue";

    /**
     * Property name for useEquals.
     */
    protected static final String PROP_USE_EQUALS = "useEquals";

    /**
     * Property name for useLock.
     */
    protected static final String PROP_USE_CON_LOCK = "useLock";

    /**
     * Property name for dataSource.
     */
    protected static final String PROP_DATASOURCE = "dataSource";

    /**
     * Property name for dataSourceJNDI.
     */
    protected static final String PROP_DATASOURCE_JNDI = "dataSourceJNDI";

    /**
     * Property name for suspectTimeout.
     */
    protected static final String PROP_SUSPECT_TIMEOUT = "suspectTimeout";

    /**
     * Property name for alternateUsernameAllowed.
     */
    protected static final String PROP_ALTERNATE_USERNAME_ALLOWED = "alternateUsernameAllowed";

    /**
     * Property name for commitOnReturn.
     */
    protected static final String PROP_COMMITONRETURN = "commitOnReturn";

    /**
     * Property name for rollbackOnReturn.
     */
    protected static final String PROP_ROLLBACKONRETURN = "rollbackOnReturn";

    /**
     * Property name for useDisposableConnectionFacade. The property is deprecated and has no effect; the disposable
     * connection facade is always used.
     */
    protected static final String PROP_USEDISPOSABLECONNECTIONFACADE = "useDisposableConnectionFacade";

    /**
     * Property name for logValidationErrors.
     */
    protected static final String PROP_LOGVALIDATIONERRORS = "logValidationErrors";

    /**
     * Property name for propagateInterruptState.
     */
    protected static final String PROP_PROPAGATEINTERRUPTSTATE = "propagateInterruptState";

    /**
     * Property name for ignoreExceptionOnPreLoad.
     */
    protected static final String PROP_IGNOREEXCEPTIONONPRELOAD = "ignoreExceptionOnPreLoad";

    /**
     * Property name for useStatementFacade. The property is deprecated and has no effect; the statement facade is
     * always used.
     */
    protected static final String PROP_USESTATEMENTFACADE = "useStatementFacade";

    /**
     * Value indicating an unknown transaction isolation level.
     */
    public static final int UNKNOWN_TRANSACTIONISOLATION = -1;

    /**
     * Property name for the JMX object name.
     */
    public static final String OBJECT_NAME = "object_name";


    /**
     * Array of all supported property names.
     */
    protected static final String[] ALL_PROPERTIES = { PROP_DEFAULTAUTOCOMMIT, PROP_DEFAULTREADONLY,
            PROP_DEFAULTTRANSACTIONISOLATION, PROP_DEFAULTCATALOG, PROP_DRIVERCLASSNAME, PROP_MAXACTIVE, PROP_MAXIDLE,
            PROP_MINIDLE, PROP_INITIALSIZE, PROP_MAXWAIT, PROP_TESTONBORROW, PROP_TESTONRETURN,
            PROP_TIMEBETWEENEVICTIONRUNSMILLIS, PROP_NUMTESTSPEREVICTIONRUN, PROP_MINEVICTABLEIDLETIMEMILLIS,
            PROP_TESTWHILEIDLE, PROP_TESTONCONNECT, PROP_PASSWORD, PROP_URL, PROP_USERNAME, PROP_VALIDATIONQUERY,
            PROP_VALIDATIONQUERY_TIMEOUT, PROP_VALIDATOR_CLASS_NAME, PROP_VALIDATIONINTERVAL,
            PROP_ACCESSTOUNDERLYINGCONNECTIONALLOWED, PROP_REMOVEABANDONED, PROP_REMOVEABANDONEDTIMEOUT,
            PROP_LOGABANDONED, PROP_POOLPREPAREDSTATEMENTS, PROP_MAXOPENPREPAREDSTATEMENTS, PROP_CONNECTIONPROPERTIES,
            PROP_INITSQL, PROP_INTERCEPTORS, PROP_JMX_ENABLED, PROP_FAIR_QUEUE, PROP_USE_EQUALS, OBJECT_NAME,
            PROP_ABANDONWHENPERCENTAGEFULL, PROP_MAXAGE, PROP_USE_CON_LOCK, PROP_DATASOURCE, PROP_DATASOURCE_JNDI,
            PROP_SUSPECT_TIMEOUT, PROP_ALTERNATE_USERNAME_ALLOWED, PROP_COMMITONRETURN, PROP_ROLLBACKONRETURN,
            PROP_USEDISPOSABLECONNECTIONFACADE, PROP_LOGVALIDATIONERRORS, PROP_PROPAGATEINTERRUPTSTATE,
            PROP_IGNOREEXCEPTIONONPRELOAD, PROP_USESTATEMENTFACADE };

    // -------------------------------------------------- ObjectFactory Methods

    /**
     * <p>
     * Create and return a new <code>BasicDataSource</code> instance. If no instance can be created, return
     * <code>null</code> instead.
     * </p>
     *
     * @param obj         The possibly null object containing location or reference information that can be used in
     *                        creating an object
     * @param name        The name of this object relative to <code>nameCtx</code>
     * @param nameCtx     The context relative to which the <code>name</code> parameter is specified, or
     *                        <code>null</code> if <code>name</code> is relative to the default initial context
     * @param environment The possibly null environment that is used in creating this object
     *
     * @exception Exception if an exception occurs creating the instance
     */
    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?, ?> environment)
            throws Exception {

        // We only know how to deal with <code>javax.naming.Reference</code>s
        // that specify a class name of "javax.sql.DataSource"
        if ((obj == null) || !(obj instanceof Reference)) {
            return null;
        }
        Reference ref = (Reference) obj;
        boolean XA = false;
        boolean ok = false;
        if ("javax.sql.DataSource".equals(ref.getClassName())) {
            ok = true;
        }
        if ("javax.sql.XADataSource".equals(ref.getClassName())) {
            ok = true;
            XA = true;
        }
        if (org.apache.tomcat.jdbc.pool.DataSource.class.getName().equals(ref.getClassName())) {
            ok = true;
        }

        if (!ok) {
            log.warn(ref.getClassName() + " is not a valid class name/type for this JNDI factory.");
            return null;
        }


        Properties properties = new Properties();
        DataSource dataSourceObject = null;
        for (int i = 0; i < ALL_PROPERTIES.length; i++) {
            String propertyName = ALL_PROPERTIES[i];
            RefAddr ra = ref.get(propertyName);
            if (ra != null) {
                Object content = ra.getContent();
                if (content == null) {
                    // RefAddr.getContent() is nullable, and a property with no
                    // content simply has no value to apply
                    continue;
                }
                if (PROP_DATASOURCE.equals(propertyName) && content instanceof DataSource) {
                    // A live data source cannot travel through Properties as a
                    // string, so keep the object and hand it to the pool below
                    dataSourceObject = (DataSource) content;
                    continue;
                }
                properties.setProperty(propertyName, content.toString());
            }
        }

        if (dataSourceObject == null) {
            // no live data source involved - keep the legacy dispatch, so that
            // subclasses overriding the three argument createDataSource() are
            // still used for plain JNDI resources
            return createDataSource(properties, nameCtx, XA);
        }
        return createDataSource(properties, nameCtx, XA, dataSourceObject);
    }

    /**
     * Parse the given properties into a PoolConfiguration.
     *
     * @param properties The properties to parse
     *
     * @return The configured PoolConfiguration
     */
    public static PoolConfiguration parsePoolProperties(Properties properties) {
        PoolConfiguration poolProperties = new PoolProperties();
        String value = null;

        value = properties.getProperty(PROP_DEFAULTAUTOCOMMIT);
        if (value != null) {
            Boolean b = parseBoolean(PROP_DEFAULTAUTOCOMMIT, value);
            if (b != null) {
                poolProperties.setDefaultAutoCommit(b);
            }
        }

        value = properties.getProperty(PROP_DEFAULTREADONLY);
        if (value != null) {
            Boolean b = parseBoolean(PROP_DEFAULTREADONLY, value);
            if (b != null) {
                poolProperties.setDefaultReadOnly(b);
            }
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
            Boolean b = parseBoolean(PROP_TESTONBORROW, value);
            if (b != null) {
                poolProperties.setTestOnBorrow(b.booleanValue());
            }
        }

        value = properties.getProperty(PROP_TESTONRETURN);
        if (value != null) {
            Boolean b = parseBoolean(PROP_TESTONRETURN, value);
            if (b != null) {
                poolProperties.setTestOnReturn(b.booleanValue());
            }
        }

        value = properties.getProperty(PROP_TESTONCONNECT);
        if (value != null) {
            Boolean b = parseBoolean(PROP_TESTONCONNECT, value);
            if (b != null) {
                poolProperties.setTestOnConnect(b.booleanValue());
            }
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
            Boolean b = parseBoolean(PROP_TESTWHILEIDLE, value);
            if (b != null) {
                poolProperties.setTestWhileIdle(b.booleanValue());
            }
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

        value = properties.getProperty(PROP_VALIDATIONQUERY_TIMEOUT);
        if (value != null) {
            poolProperties.setValidationQueryTimeout(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_VALIDATOR_CLASS_NAME);
        if (value != null) {
            poolProperties.setValidatorClassName(value);
        }

        value = properties.getProperty(PROP_VALIDATIONINTERVAL);
        if (value != null) {
            poolProperties.setValidationInterval(Long.parseLong(value));
        }

        value = properties.getProperty(PROP_ACCESSTOUNDERLYINGCONNECTIONALLOWED);
        if (value != null) {
            Boolean b = parseBoolean(PROP_ACCESSTOUNDERLYINGCONNECTIONALLOWED, value);
            if (b != null) {
                poolProperties.setAccessToUnderlyingConnectionAllowed(b.booleanValue());
            }
        }

        value = properties.getProperty(PROP_REMOVEABANDONED);
        if (value != null) {
            Boolean b = parseBoolean(PROP_REMOVEABANDONED, value);
            if (b != null) {
                poolProperties.setRemoveAbandoned(b.booleanValue());
            }
        }

        value = properties.getProperty(PROP_REMOVEABANDONEDTIMEOUT);
        if (value != null) {
            poolProperties.setRemoveAbandonedTimeout(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_LOGABANDONED);
        if (value != null) {
            Boolean b = parseBoolean(PROP_LOGABANDONED, value);
            if (b != null) {
                poolProperties.setLogAbandoned(b.booleanValue());
            }
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
            // Use the canonical setter so that the configured string also
            // round-trips through getConnectionProperties(); it parses the
            // pairs into getDbProperties() with the very same parser that
            // getProperties(value) below used to apply.
            poolProperties.setConnectionProperties(value);
        } else {
            poolProperties.setDbProperties(new Properties());
        }

        if (poolProperties.getUsername() != null) {
            poolProperties.getDbProperties().setProperty("user", poolProperties.getUsername());
        }
        if (poolProperties.getPassword() != null) {
            poolProperties.getDbProperties().setProperty("password", poolProperties.getPassword());
        }

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
            Boolean b = parseBoolean(PROP_JMX_ENABLED, value);
            if (b != null) {
                poolProperties.setJmxEnabled(b.booleanValue());
            }
        }

        value = properties.getProperty(PROP_FAIR_QUEUE);
        if (value != null) {
            Boolean b = parseBoolean(PROP_FAIR_QUEUE, value);
            if (b != null) {
                poolProperties.setFairQueue(b.booleanValue());
            }
        }

        value = properties.getProperty(PROP_USE_EQUALS);
        if (value != null) {
            Boolean b = parseBoolean(PROP_USE_EQUALS, value);
            if (b != null) {
                poolProperties.setUseEquals(b.booleanValue());
            }
        }

        value = properties.getProperty(OBJECT_NAME);
        if (value != null) {
            poolProperties.setName(ObjectName.quote(value));
        }

        value = properties.getProperty(PROP_ABANDONWHENPERCENTAGEFULL);
        if (value != null) {
            poolProperties.setAbandonWhenPercentageFull(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_MAXAGE);
        if (value != null) {
            poolProperties.setMaxAge(Long.parseLong(value));
        }

        value = properties.getProperty(PROP_USE_CON_LOCK);
        if (value != null) {
            Boolean b = parseBoolean(PROP_USE_CON_LOCK, value);
            if (b != null) {
                poolProperties.setUseLock(b.booleanValue());
            }
        }

        value = properties.getProperty(PROP_DATASOURCE);
        if (value != null) {
            // Only genuinely string-valued dataSource attributes reach this
            // point: getObjectInstance hands an actual javax.sql.DataSource
            // RefAddr content straight to the pool instead of stringifying it.
            // A string cannot be turned into a data source here.
            throw new IllegalArgumentException("Can't set dataSource property as a string \"" + value +
                    "\". Either carry the javax.sql.DataSource object itself under the dataSource attribute" +
                    " of the Reference, or use the dataSourceJNDI attribute to name a data source bound in" +
                    " JNDI.");
        }

        value = properties.getProperty(PROP_DATASOURCE_JNDI);
        if (value != null) {
            poolProperties.setDataSourceJNDI(value);
        }

        value = properties.getProperty(PROP_SUSPECT_TIMEOUT);
        if (value != null) {
            poolProperties.setSuspectTimeout(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_ALTERNATE_USERNAME_ALLOWED);
        if (value != null) {
            Boolean b = parseBoolean(PROP_ALTERNATE_USERNAME_ALLOWED, value);
            if (b != null) {
                poolProperties.setAlternateUsernameAllowed(b.booleanValue());
            }
        }

        value = properties.getProperty(PROP_COMMITONRETURN);
        if (value != null) {
            Boolean b = parseBoolean(PROP_COMMITONRETURN, value);
            if (b != null) {
                poolProperties.setCommitOnReturn(b.booleanValue());
            }
        }

        value = properties.getProperty(PROP_ROLLBACKONRETURN);
        if (value != null) {
            Boolean b = parseBoolean(PROP_ROLLBACKONRETURN, value);
            if (b != null) {
                poolProperties.setRollbackOnReturn(b.booleanValue());
            }
        }

        value = properties.getProperty(PROP_USEDISPOSABLECONNECTIONFACADE);
        if (value != null) {
            Boolean b = parseBoolean(PROP_USEDISPOSABLECONNECTIONFACADE, value);
            if (b != null) {
                storeDeprecatedUseDisposableConnectionFacade(poolProperties, b.booleanValue());
            }
        }

        value = properties.getProperty(PROP_LOGVALIDATIONERRORS);
        if (value != null) {
            Boolean b = parseBoolean(PROP_LOGVALIDATIONERRORS, value);
            if (b != null) {
                poolProperties.setLogValidationErrors(b.booleanValue());
            }
        }

        value = properties.getProperty(PROP_PROPAGATEINTERRUPTSTATE);
        if (value != null) {
            Boolean b = parseBoolean(PROP_PROPAGATEINTERRUPTSTATE, value);
            if (b != null) {
                poolProperties.setPropagateInterruptState(b.booleanValue());
            }
        }

        value = properties.getProperty(PROP_IGNOREEXCEPTIONONPRELOAD);
        if (value != null) {
            Boolean b = parseBoolean(PROP_IGNOREEXCEPTIONONPRELOAD, value);
            if (b != null) {
                poolProperties.setIgnoreExceptionOnPreLoad(b.booleanValue());
            }
        }
        value = properties.getProperty(PROP_USESTATEMENTFACADE);
        if (value != null) {
            Boolean b = parseBoolean(PROP_USESTATEMENTFACADE, value);
            if (b != null) {
                storeDeprecatedUseStatementFacade(poolProperties, b.booleanValue());
            }
        }
        return poolProperties;
    }

    /**
     * Parses the value configured for a boolean property. Unlike {@link Boolean#parseBoolean(String)}, which silently
     * yields {@code false} for any input, an unrecognized value is reported instead of being applied as {@code false}.
     *
     * @param name  the property name, for the error message
     * @param value the configured value, never {@code null}
     *
     * @return {@link Boolean#TRUE} or {@link Boolean#FALSE}, or {@code null} when the value is empty and the property
     *             is therefore to be left unset: at its {@link PoolProperties} default for a plain boolean, at its "use
     *             the driver default" state for a tri-state property
     *
     * @throws IllegalArgumentException if the value is neither {@code true} nor {@code false}, ignoring case and
     *                                      surrounding whitespace
     */
    private static Boolean parseBoolean(String name, String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if ("true".equalsIgnoreCase(trimmed)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(trimmed)) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("Invalid boolean value '" + value + "' for property " + name);
    }

    /**
     * Creates and configures a {@link DataSource} instance based on the given properties.
     *
     * @param properties the datasource configuration properties
     *
     * @return the datasource
     *
     * @throws Exception if an error occurs creating the data source
     */
    public DataSource createDataSource(Properties properties) throws Exception {
        return createDataSource(properties, null, false);
    }

    /**
     * Creates and configures a {@link DataSource} instance based on the given properties.
     *
     * @param properties The datasource configuration properties
     * @param context    The JNDI context
     * @param XA         {@code true} to create an XA data source
     *
     * @return The configured datasource
     *
     * @throws Exception if an error occurs creating the data source
     */
    public DataSource createDataSource(Properties properties, Context context, boolean XA) throws Exception {
        return createDataSource(properties, context, XA, null);
    }

    /**
     * Creates and configures a {@link DataSource} instance based on the given properties, with an optional live data
     * source for the pool to wrap.
     * <p>
     * When {@code dataSourceObject} is not {@code null}, it is set on the pool configuration before the pool is
     * created, and the {@code dataSourceJNDI} lookup is skipped: the pool is built around the object that was handed
     * to the factory directly.
     * </p>
     *
     * @param properties        The datasource configuration properties
     * @param context           The JNDI context
     * @param XA                {@code true} to create an XA data source
     * @param dataSourceObject  A live {@link DataSource} the pool should wrap, or {@code null}
     *
     * @return The configured datasource
     *
     * @throws Exception if an error occurs creating the data source
     */
    protected DataSource createDataSource(Properties properties, Context context, boolean XA,
            DataSource dataSourceObject) throws Exception {
        PoolConfiguration poolProperties = parsePoolProperties(properties);
        if (dataSourceObject != null) {
            poolProperties.setDataSource(dataSourceObject);
        }
        if (poolProperties.getDataSourceJNDI() != null && poolProperties.getDataSource() == null) {
            performJNDILookup(context, poolProperties);
        }
        org.apache.tomcat.jdbc.pool.DataSource dataSource = XA ? new XADataSource(poolProperties)
                : new org.apache.tomcat.jdbc.pool.DataSource(poolProperties);
        // Initialise the pool itself
        dataSource.createPool();
        // Return the configured DataSource instance
        return dataSource;
    }

    /**
     * Perform a JNDI lookup for the data source.
     *
     * @param context        The JNDI context
     * @param poolProperties The pool configuration
     *
     * @throws NamingException if the name configured through the dataSourceJNDI property cannot be resolved. The name
     *                             was configured specifically to supply the connections for the pool, so a pool without
     *                             that data source is not a usable fallback and the failure is reported instead of
     *                             being deferred to the first connection request.
     */
    public void performJNDILookup(Context context, PoolConfiguration poolProperties) throws NamingException {
        Object jndiDS = null;
        try {
            if (context != null) {
                jndiDS = context.lookup(poolProperties.getDataSourceJNDI());
            } else {
                log.warn("dataSourceJNDI property is configured, but local JNDI context is null.");
            }
        } catch (NamingException e) {
            if (log.isDebugEnabled()) {
                log.debug(
                        "The name \"" + poolProperties.getDataSourceJNDI() + "\" cannot be found in the local context.",
                        e);
            }
        }
        NamingException initialContextFailure = null;
        if (jndiDS == null) {
            try {
                context = new InitialContext();
                jndiDS = context.lookup(poolProperties.getDataSourceJNDI());
            } catch (NamingException e) {
                initialContextFailure = e;
                log.warn("The name \"" + poolProperties.getDataSourceJNDI() +
                        "\" cannot be found in the InitialContext.", e);
            }
        }
        if (jndiDS != null) {
            poolProperties.setDataSource(jndiDS);
            return;
        }
        NamingException failure = new NamingException("Unable to create the connection pool: the name [" +
                poolProperties.getDataSourceJNDI() + "] configured through the 'dataSourceJNDI' attribute was" +
                " not found in the local JNDI context nor in the InitialContext. The pool is built around the" +
                " data source that name refers to; check that the name is correct and that the data source is" +
                " bound and visible to the application before the pool is created.");
        if (initialContextFailure != null) {
            failure.setRootCause(initialContextFailure);
        }
        throw failure;
    }

    /**
     * Parse properties from the string. Format of the string must be [propertyName=property;]*.
     *
     * @param propText The properties string
     *
     * @return the properties
     */
    protected static Properties getProperties(String propText) {
        return PoolProperties.getProperties(propText, null);
    }

    /**
     * Stores the configured value of the deprecated useDisposableConnectionFacade property. The property has no effect
     * any more, the disposable connection facade is always used; the value is stored as configured so that the property
     * keeps showing up the way it was set, the same as through any other configuration path.
     *
     * @param poolProperties the properties to store the value on
     * @param value          the configured value
     */
    @SuppressWarnings("deprecation")
    private static void storeDeprecatedUseDisposableConnectionFacade(PoolConfiguration poolProperties, boolean value) {
        poolProperties.setUseDisposableConnectionFacade(value);
    }

    /**
     * Stores the configured value of the deprecated useStatementFacade property. The property has no effect any more,
     * the statement facade is always used; the value is stored as configured so that the property keeps showing up the
     * way it was set, the same as through any other configuration path.
     *
     * @param poolProperties the properties to store the value on
     * @param value          the configured value
     */
    @SuppressWarnings("deprecation")
    private static void storeDeprecatedUseStatementFacade(PoolConfiguration poolProperties, boolean value) {
        poolProperties.setUseStatementFacade(value);
    }

}

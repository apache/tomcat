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
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Set;

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
import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * <p>
 * JNDI object factory that creates an instance of
 * {@link org.apache.tomcat.jdbc.pool.DataSource} - or {@link XADataSource}, when the
 * reference names a type to be built as XA - configured from the {@code RefAddr}
 * values of the specified {@code Reference}. Address names are matched against the
 * pool properties listed in {@link #ALL_PROPERTIES}; they are the attributes of the
 * pool documented with {@link PoolConfiguration}'s setters, not the property set of
 * any other pool implementation.
 * </p>
 * <p>
 * The factory applies a strict reading of the reference: an attribute whose value is
 * blank leaves its property at the {@link PoolProperties} default, and a value the
 * property cannot take fails the lookup with an exception naming the attribute
 * instead of being silently ignored.
 * </p>
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
    protected static final String PROP_USE_LOCK = "useLock";

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
            PROP_ABANDONWHENPERCENTAGEFULL, PROP_MAXAGE, PROP_USE_LOCK, PROP_DATASOURCE, PROP_DATASOURCE_JNDI,
            PROP_SUSPECT_TIMEOUT, PROP_ALTERNATE_USERNAME_ALLOWED, PROP_COMMITONRETURN, PROP_ROLLBACKONRETURN,
            PROP_USEDISPOSABLECONNECTIONFACADE, PROP_LOGVALIDATIONERRORS, PROP_PROPAGATEINTERRUPTSTATE,
            PROP_IGNOREEXCEPTIONONPRELOAD, PROP_USESTATEMENTFACADE };

    /**
     * Names of the reference addresses that the JNDI container itself places
     * into a resource reference alongside the pool properties. They configure
     * the naming layer, not the pool, and are therefore not reported as
     * unrecognized:
     * <ul>
     * <li>{@code factory}: the object factory to delegate to; the container
     * reads it as a reference address through
     * {@code ref.get(org.apache.naming.factory.Constants.FACTORY)} in
     * {@code org.apache.naming.factory.FactoryBase}.</li>
     * <li>{@code singleton}, {@code scope}, {@code auth} and
     * {@code description}: the resource metadata that
     * {@code org.apache.naming.ResourceRef} adds to every resource
     * reference.</li>
     * <li>{@code forceString}: the removed legacy attribute that the
     * container's {@code org.apache.naming.factory.BeanFactory} still
     * recognizes and reports on its own. With this factory it is inert,
     * but it is reserved to stay silent exactly as BeanFactory does, so a
     * resource switched between the two factories does not start to warn
     * about it.</li>
     * </ul>
     */
    private static final Set<String> RESERVED_JNDI_ADDRESS_NAMES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("factory", "singleton", "scope", "auth", "description", "forceString")));

    // -------------------------------------------------- ObjectFactory Methods

    /**
     * <p>
     * Creates and returns the pooled data source that the given {@code Reference}
     * describes: an {@link org.apache.tomcat.jdbc.pool.DataSource}, or an
     * {@link XADataSource} when the reference names {@code javax.sql.XADataSource}
     * or the pool's own XA class.
     * </p>
     *
     * @param obj         The possibly null object containing location or reference information that can be used in
     *                        creating an object
     * @param name        The name of this object relative to <code>nameCtx</code>
     * @param nameCtx     The context relative to which the <code>name</code> parameter is specified, or
     *                        <code>null</code> if <code>name</code> is relative to the default initial context
     * @param environment The possibly null environment that is used in creating this object
     *
     * @return the configured data source, or <code>null</code> if <code>obj</code> is not a
     *         <code>Reference</code> or names a class this factory does not build (the
     *         latter is logged)
     *
     * @throws IllegalArgumentException if an attribute carries a value its property cannot
     *         take: an unusable boolean, numeric or transaction-isolation value, or a
     *         {@code dataSource} attribute holding a String or another connection pool
     * @throws NamingException if the name given by the {@code dataSourceJNDI} attribute does
     *         not resolve to a data source that can back the pool
     *
     * @exception Exception if an exception occurs creating the instance
     */
    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?, ?> environment)
            throws Exception {

        // The reference must name one of the types this factory builds: the
        // two documented interface names and the pool's own concrete classes
        // (the non-XA one has always been accepted; the XA one is exactly
        // what the factory news for an XA reference).
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
        if (org.apache.tomcat.jdbc.pool.XADataSource.class.getName().equals(ref.getClassName())) {
            ok = true;
            XA = true;
        }

        if (!ok) {
            log.warn(MessageFactory.get("dataSourceFactory.invalidClassName", ref.getClassName()));
            return null;
        }

        Properties properties = new Properties();
        Object dataSourceObject = null;
        for (int i = 0; i < ALL_PROPERTIES.length; i++) {
            String propertyName = ALL_PROPERTIES[i];
            RefAddr ra = ref.get(propertyName);
            if (ra != null) {
                Object content = ra.getContent();
                if (content == null) {
                    // RefAddr.getContent() is nullable: a property carrying no
                    // content has no value to apply. The name is recognised,
                    // so note the skip where an operator wondering why a
                    // configured attribute did not take effect will look.
                    if (log.isDebugEnabled()) {
                        log.debug(MessageFactory.get("dataSourceFactory.nullContentProperty", propertyName));
                    }
                    continue;
                }
                if (PROP_DATASOURCE.equals(propertyName)) {
                    if (content instanceof DataSourceProxy) {
                        // A pool cannot wrap another pool; reported with the
                        // attribute and the object's class, the context the
                        // layered-pool rejection deeper down cannot supply -
                        // the lookup route already diagnoses this binding with
                        // the same details.
                        throw new IllegalArgumentException(MessageFactory.get(
                                "dataSourceFactory.cannotSetDataSourceLayered",
                                content.getClass().getName()));
                    }
                    if (isSupportedDataSourceObject(content)) {
                        // A live data source cannot travel through Properties
                        // as a string, so keep the object and hand it to the
                        // pool below
                        dataSourceObject = content;
                        continue;
                    }
                }
                properties.setProperty(propertyName, content.toString());
            }
        }

        // Whatever the lookup above did not recognize is never read, so the
        // operator hears about it here instead of watching the pool come up
        // with defaults. Names the JNDI container itself uses to build the
        // reference are not pool properties and stay unmentioned. The set is
        // read from ALL_PROPERTIES at lookup time rather than snapshotted at
        // class-initialisation: the array is protected so a subclass can
        // extend it, and a snapshot would leave the loop reading one list
        // while this sweep judged against another.
        Set<String> knownPropertyNames = new HashSet<>(Arrays.asList(ALL_PROPERTIES));
        Enumeration<RefAddr> addresses = ref.getAll();
        while (addresses.hasMoreElements()) {
            String addressName = addresses.nextElement().getType();
            if (!knownPropertyNames.contains(addressName)
                    && !RESERVED_JNDI_ADDRESS_NAMES.contains(addressName)) {
                log.warn(MessageFactory.get("dataSourceFactory.invalidProperty", addressName));
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
     * Whether content carried under the {@code dataSource} attribute is an
     * object the pool can build its connections from. The pool accepts the
     * three JDBC data source types its connect path understands
     * ({@code javax.sql.DataSource}, {@code javax.sql.XADataSource} and
     * {@code javax.sql.ConnectionPoolDataSource}, see
     * {@link PooledConnection#connectUsingDataSource()}); only the first is a
     * {@code javax.sql.DataSource}, so testing for that one type alone would
     * stringify an XA or pooled data source and then reject the string.
     *
     * @param content the {@code RefAddr} content of a {@code dataSource} attribute
     *
     * @return {@code true} if the object can back the pool
     */
    private static boolean isSupportedDataSourceObject(Object content) {
        return content instanceof DataSource || content instanceof javax.sql.XADataSource
                || content instanceof javax.sql.ConnectionPoolDataSource;
    }

    /**
     * Parse the given properties into a PoolConfiguration.
     *
     * <p>
     * A blank attribute value - empty or whitespace only, the shape a cleared
     * template attribute yields - leaves the property at its
     * {@link PoolProperties} default. A value the property cannot take fails the
     * parse naming the attribute, rather than being silently dropped the way
     * unparsed values used to be. Numeric, boolean, transaction-isolation and
     * class-name ({@code driverClassName} and {@code validatorClassName})
     * attributes apply their trimmed value, and so does the lookup of the name
     * given by {@code dataSourceJNDI}: whitespace around a number, a keyword or a
     * class or JNDI name is always noise. Other string values are stored verbatim
     * - a catalog, a validation query, a credential or a URL can legitimately
     * carry surrounding whitespace.
     * </p>
     *
     * <p>
     * Two attributes are not plain property assignments: {@code connectionProperties}
     * is resolved into the driver properties through
     * {@link PoolProperties#setConnectionProperties(String)}, and
     * {@code username}/{@code password} are merged into that same driver property
     * set afterwards, so credentials configured as their own attributes override a
     * {@code user} or {@code password} pair inside {@code connectionProperties}.
     * </p>
     *
     * @param properties The properties to parse
     *
     * @return The configured PoolConfiguration
     *
     * @throws IllegalArgumentException if an attribute carries a value its property
     *         cannot take
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
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                // Unset means the pool stays in the "use the driver default"
                // state it comes with.
                int level;
                if ("NONE".equalsIgnoreCase(trimmed)) {
                    level = Connection.TRANSACTION_NONE;
                } else if ("READ_COMMITTED".equalsIgnoreCase(trimmed)) {
                    level = Connection.TRANSACTION_READ_COMMITTED;
                } else if ("READ_UNCOMMITTED".equalsIgnoreCase(trimmed)) {
                    level = Connection.TRANSACTION_READ_UNCOMMITTED;
                } else if ("REPEATABLE_READ".equalsIgnoreCase(trimmed)) {
                    level = Connection.TRANSACTION_REPEATABLE_READ;
                } else if ("SERIALIZABLE".equalsIgnoreCase(trimmed)) {
                    level = Connection.TRANSACTION_SERIALIZABLE;
                } else {
                    try {
                        level = Integer.parseInt(trimmed);
                    } catch (NumberFormatException e) {
                        // An unusable value fails the resource naming the
                        // property, instead of silently dropping the
                        // configured isolation level.
                        throw new IllegalArgumentException(MessageFactory.get(
                                "dataSourceFactory.invalidTransactionIsolation", trimmed,
                                PROP_DEFAULTTRANSACTIONISOLATION), e);
                    }
                }
                poolProperties.setDefaultTransactionIsolation(level);
            }
        }

        value = properties.getProperty(PROP_DEFAULTCATALOG);
        if (value != null && !value.trim().isEmpty()) {
            // A stored empty value would be pushed into every physical
            // connection as setCatalog("") and whether that is inert or fails
            // is driver-dependent. The value itself is kept untrimmed - a
            // catalog name is a database identifier, which may carry
            // whitespace the way a JNDI name cannot.
            poolProperties.setDefaultCatalog(value);
        }

        value = properties.getProperty(PROP_DRIVERCLASSNAME);
        if (value != null && !value.trim().isEmpty()) {
            // The connect path's DriverManager fallback is keyed on the
            // property being null, so a stored empty class name would defeat
            // the fallback and abort the pool loading the empty class. The
            // trimmed value is applied: a Java class name can never carry
            // meaningful surrounding whitespace.
            poolProperties.setDriverClassName(value.trim());
        }

        value = properties.getProperty(PROP_MAXACTIVE);
        if (value != null) {
            Integer parsed = parseInteger(PROP_MAXACTIVE, value);
            if (parsed != null) {
                poolProperties.setMaxActive(parsed.intValue());
            }
        }

        value = properties.getProperty(PROP_MAXIDLE);
        if (value != null) {
            Integer parsed = parseInteger(PROP_MAXIDLE, value);
            if (parsed != null) {
                poolProperties.setMaxIdle(parsed.intValue());
            }
        }

        value = properties.getProperty(PROP_MINIDLE);
        if (value != null) {
            Integer parsed = parseInteger(PROP_MINIDLE, value);
            if (parsed != null) {
                poolProperties.setMinIdle(parsed.intValue());
            }
        }

        value = properties.getProperty(PROP_INITIALSIZE);
        if (value != null) {
            Integer parsed = parseInteger(PROP_INITIALSIZE, value);
            if (parsed != null) {
                poolProperties.setInitialSize(parsed.intValue());
            }
        }

        value = properties.getProperty(PROP_MAXWAIT);
        if (value != null) {
            Integer parsed = parseInteger(PROP_MAXWAIT, value);
            if (parsed != null) {
                poolProperties.setMaxWait(parsed.intValue());
            }
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
            Integer parsed = parseInteger(PROP_TIMEBETWEENEVICTIONRUNSMILLIS, value);
            if (parsed != null) {
                poolProperties.setTimeBetweenEvictionRunsMillis(parsed.intValue());
            }
        }

        value = properties.getProperty(PROP_NUMTESTSPEREVICTIONRUN);
        if (value != null && !value.trim().isEmpty()) {
            log.warn(MessageFactory.get("dataSourceFactory.invalidSetting", PROP_NUMTESTSPEREVICTIONRUN));
            Integer parsed = parseInteger(PROP_NUMTESTSPEREVICTIONRUN, value);
            if (parsed != null) {
                poolProperties.setNumTestsPerEvictionRun(parsed.intValue());
            }
        }

        value = properties.getProperty(PROP_MINEVICTABLEIDLETIMEMILLIS);
        if (value != null) {
            Integer parsed = parseInteger(PROP_MINEVICTABLEIDLETIMEMILLIS, value);
            if (parsed != null) {
                poolProperties.setMinEvictableIdleTimeMillis(parsed.intValue());
            }
        }

        value = properties.getProperty(PROP_TESTWHILEIDLE);
        if (value != null) {
            Boolean b = parseBoolean(PROP_TESTWHILEIDLE, value);
            if (b != null) {
                poolProperties.setTestWhileIdle(b.booleanValue());
            }
        }

        value = properties.getProperty(PROP_PASSWORD);
        if (value != null && !value.trim().isEmpty()) {
            // A stored empty value would go into the driver properties as an
            // empty password entry and authenticate with it, while the
            // documented meaning of a blank attribute is that the property
            // stays at its default - no password entry at all. The value
            // itself is kept untrimmed: a password may legitimately carry
            // surrounding whitespace.
            poolProperties.setPassword(value);
        }

        value = properties.getProperty(PROP_URL);
        if (value != null && !value.trim().isEmpty()) {
            // A stored empty value would fail every physical connection
            // attempt against the driver, while the documented meaning of a
            // blank attribute is that the property stays at its default. The
            // value itself is kept untrimmed, as the URL of a data source can
            // carry whitespace the way a class name cannot.
            poolProperties.setUrl(value);
        }

        value = properties.getProperty(PROP_USERNAME);
        if (value != null && !value.trim().isEmpty()) {
            // A stored empty value would go into the driver properties as an
            // empty user entry, while the documented meaning of a blank
            // attribute is that the property stays at its default - no user
            // entry at all. The value itself is kept untrimmed, as a user
            // name is treated like the password here.
            poolProperties.setUsername(value);
        }

        value = properties.getProperty(PROP_VALIDATIONQUERY);
        if (value != null && !value.trim().isEmpty()) {
            // The way to express "no validation query" is the property being
            // unset, which routes validation to Connection.isValid(); a
            // stored empty string instead runs the empty string as SQL on
            // every validation - a no-op on forgiving drivers and a failed
            // validation, session discard and reconnect on stricter ones. The
            // query itself is kept untrimmed; whitespace around real SQL is
            // the driver's to chew.
            poolProperties.setValidationQuery(value);
        }

        value = properties.getProperty(PROP_VALIDATIONQUERY_TIMEOUT);
        if (value != null) {
            Integer parsed = parseInteger(PROP_VALIDATIONQUERY_TIMEOUT, value);
            if (parsed != null) {
                poolProperties.setValidationQueryTimeout(parsed.intValue());
            }
        }

        value = properties.getProperty(PROP_VALIDATOR_CLASS_NAME);
        if (value != null && !value.trim().isEmpty()) {
            // A stored empty value would send the connect path looking for a
            // class named "" - a ClassNotFoundException warning on every pool
            // start before it fell back to standard validation - while the
            // documented meaning of a blank attribute is that the property
            // stays at its default standard validation. The trimmed value is
            // applied, as a Java class name can never carry meaningful
            // surrounding whitespace.
            poolProperties.setValidatorClassName(value.trim());
        }

        value = properties.getProperty(PROP_VALIDATIONINTERVAL);
        if (value != null) {
            Long parsed = parseNumber(PROP_VALIDATIONINTERVAL, value);
            if (parsed != null) {
                poolProperties.setValidationInterval(parsed.longValue());
            }
        }

        value = properties.getProperty(PROP_ACCESSTOUNDERLYINGCONNECTIONALLOWED);
        if (value != null && !value.trim().isEmpty()) {
            log.warn(MessageFactory.get("dataSourceFactory.invalidSetting", PROP_ACCESSTOUNDERLYINGCONNECTIONALLOWED));
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
            Integer parsed = parseInteger(PROP_REMOVEABANDONEDTIMEOUT, value);
            if (parsed != null) {
                poolProperties.setRemoveAbandonedTimeout(parsed.intValue());
            }
        }

        value = properties.getProperty(PROP_LOGABANDONED);
        if (value != null) {
            Boolean b = parseBoolean(PROP_LOGABANDONED, value);
            if (b != null) {
                poolProperties.setLogAbandoned(b.booleanValue());
            }
        }

        value = properties.getProperty(PROP_POOLPREPAREDSTATEMENTS);
        if (value != null && !value.trim().isEmpty()) {
            log.warn(MessageFactory.get("dataSourceFactory.invalidSetting", PROP_POOLPREPAREDSTATEMENTS));
        }

        value = properties.getProperty(PROP_MAXOPENPREPAREDSTATEMENTS);
        if (value != null && !value.trim().isEmpty()) {
            log.warn(MessageFactory.get("dataSourceFactory.invalidSetting", PROP_MAXOPENPREPAREDSTATEMENTS));
        }

        value = properties.getProperty(PROP_CONNECTIONPROPERTIES);
        if (value != null && !value.trim().isEmpty()) {
            // The canonical setter parses the pairs into the driver
            // properties and keeps the string round-tripping through
            // getConnectionProperties().
            poolProperties.setConnectionProperties(value);
        } else if (value != null) {
            // A blank attribute means the default, null: storing "" would
            // make the configuration dump and the getter report a value that
            // was never configured.
            poolProperties.setConnectionProperties(null);
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
        if (value != null && !value.trim().isEmpty()) {
            // Storing the empty string would leave the property non-null, and
            // the pool treats that as a configured chain whose only segment is
            // the empty one.
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
        if (value != null && !value.trim().isEmpty()) {
            // An empty attribute would be quoted into an empty pool name,
            // giving the JMX object name an empty name component where the
            // property being unset lets the pool name itself. The configured
            // name is stored verbatim - it is an identifier and can carry
            // meaningful whitespace, which is also why it is quoted.
            poolProperties.setName(ObjectName.quote(value));
        }

        value = properties.getProperty(PROP_ABANDONWHENPERCENTAGEFULL);
        if (value != null) {
            Integer parsed = parseInteger(PROP_ABANDONWHENPERCENTAGEFULL, value);
            if (parsed != null) {
                poolProperties.setAbandonWhenPercentageFull(parsed.intValue());
            }
        }

        value = properties.getProperty(PROP_MAXAGE);
        if (value != null) {
            Long parsed = parseNumber(PROP_MAXAGE, value);
            if (parsed != null) {
                poolProperties.setMaxAge(parsed.longValue());
            }
        }

        value = properties.getProperty(PROP_USE_LOCK);
        if (value != null) {
            Boolean b = parseBoolean(PROP_USE_LOCK, value);
            if (b != null) {
                poolProperties.setUseLock(b.booleanValue());
            }
        }

        value = properties.getProperty(PROP_DATASOURCE);
        if (value != null && !value.trim().isEmpty()) {
            // Only genuinely string-valued dataSource attributes reach this
            // point: getObjectInstance hands a live data source object
            // carried under the attribute (DataSource, XADataSource or
            // ConnectionPoolDataSource) straight to the pool instead of
            // stringifying it. A string cannot be turned into a data source
            // here.
            throw new IllegalArgumentException(MessageFactory.get("dataSourceFactory.cannotSetDataSourceString",
                    value));
        }

        value = properties.getProperty(PROP_DATASOURCE_JNDI);
        if (value != null) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                // A stored blank name would be one no context can resolve,
                // failing the whole resource on the lookup.
                poolProperties.setDataSourceJNDI(trimmed);
            }
        }

        value = properties.getProperty(PROP_SUSPECT_TIMEOUT);
        if (value != null) {
            Integer parsed = parseInteger(PROP_SUSPECT_TIMEOUT, value);
            if (parsed != null) {
                poolProperties.setSuspectTimeout(parsed.intValue());
            }
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
        throw new IllegalArgumentException(MessageFactory.get("dataSourceFactory.invalidBoolean", value, name));
    }

    /**
     * Parses the value configured for a numeric attribute. Like
     * {@link #parseBoolean(String, String)} the value tolerates surrounding
     * whitespace, an empty value means "leave the property unset" rather than
     * failing the parse, and a value that cannot be parsed fails the
     * configuration naming the property and quoting the value instead of
     * surfacing as a bare {@link NumberFormatException} about an anonymous
     * input string.
     *
     * @param name  the property name, for the error message
     * @param value the configured value, never {@code null}
     *
     * @return the parsed value, or {@code null} when the value is empty and
     *             the property is therefore to be left unset
     *
     * @throws IllegalArgumentException if the value is not a whole number
     */
    private static Long parseNumber(String name, String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(trimmed);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    MessageFactory.get("dataSourceFactory.invalidNumber", trimmed, name), e);
        }
    }

    /**
     * Parses the value configured for a numeric attribute whose setter takes
     * an {@code int}.
     *
     * @param name  the property name, for the error message
     * @param value the configured value, never {@code null}
     *
     * @return the parsed value, or {@code null} when the value is empty and
     *             the property is therefore to be left unset
     *
     * @throws IllegalArgumentException if the value is not a whole number, or
     *                                      is outside the range an {@code int}
     *                                      can hold
     */
    private static Integer parseInteger(String name, String value) {
        Long parsed = parseNumber(name, value);
        if (parsed == null) {
            return null;
        }
        if (parsed.longValue() < Integer.MIN_VALUE || parsed.longValue() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    MessageFactory.get("dataSourceFactory.numberOutOfRange", value, name));
        }
        return Integer.valueOf(parsed.intValue());
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
     * to the factory directly. The object is one of the data source types the pool's connect path accepts
     * ({@code javax.sql.DataSource}, {@code javax.sql.XADataSource} or
     * {@code javax.sql.ConnectionPoolDataSource}), not necessarily a {@code javax.sql.DataSource}.
     * </p>
     *
     * @param properties        The datasource configuration properties
     * @param context           The JNDI context
     * @param XA                {@code true} to create an XA data source
     * @param dataSourceObject  A live data source object the pool should wrap, or {@code null}
     *
     * @return The configured datasource
     *
     * @throws Exception if an error occurs creating the data source
     */
    protected DataSource createDataSource(Properties properties, Context context, boolean XA,
            Object dataSourceObject) throws Exception {
        PoolConfiguration poolProperties = parsePoolProperties(properties);
        if (dataSourceObject != null) {
            poolProperties.setDataSource(dataSourceObject);
        }
        if (poolProperties.getDataSourceJNDI() != null && poolProperties.getDataSource() == null) {
            performJNDILookup(context, poolProperties);
        }
        org.apache.tomcat.jdbc.pool.DataSource dataSource = XA ? new XADataSource(poolProperties)
                : new org.apache.tomcat.jdbc.pool.DataSource(poolProperties);
        dataSource.createPool();
        return dataSource;
    }

    /**
     * Perform a JNDI lookup for the data source.
     *
     * @param context        The JNDI context
     * @param poolProperties The pool configuration
     *
     * @throws NamingException if the name configured through the dataSourceJNDI property cannot be resolved, or
     *                             resolves to an object the pool cannot create connections from. The name was
     *                             configured specifically to supply the connections for the pool, so a pool without
     *                             that data source is not a usable fallback and the failure is reported instead of
     *                             being deferred to the first connection request.
     */
    public void performJNDILookup(Context context, PoolConfiguration poolProperties) throws NamingException {
        Object jndiDS = null;
        try {
            if (context != null) {
                jndiDS = context.lookup(poolProperties.getDataSourceJNDI());
            } else {
                // Not a fault: a bare Reference lookup outside a container
                // arrives with no local context, and the InitialContext
                // fallback below is written for exactly that.
                log.debug(MessageFactory.get("dataSourceFactory.jndiContextNull"));
            }
        } catch (NamingException e) {
            if (log.isDebugEnabled()) {
                log.debug(MessageFactory.get("dataSourceFactory.jndiLocalNameNotFound",
                        poolProperties.getDataSourceJNDI()), e);
            }
        }
        NamingException initialContextFailure = null;
        if (jndiDS == null) {
            try {
                context = new InitialContext();
                jndiDS = context.lookup(poolProperties.getDataSourceJNDI());
            } catch (NamingException e) {
                // Not warned here: this failure always ends in the thrown
                // jndiLookupFailed below, which names the same name and
                // carries this exception as its cause, and the caller logs
                // that - warning as well only made the one failed resource
                // read as two.
                initialContextFailure = e;
            }
        }
        if (jndiDS != null) {
            if (jndiDS instanceof DataSourceProxy) {
                // setDataSource's own rejection of layered pools, reported as
                // this method's contract promises and with the context the
                // rejection alone lacks: the attribute and the name that
                // resolved to the other pool.
                throw new NamingException(MessageFactory.get(
                        "dataSourceFactory.jndiLayeredPool",
                        poolProperties.getDataSourceJNDI(),
                        jndiDS.getClass().getName()));
            }
            if (!isSupportedDataSourceObject(jndiDS)) {
                // The same requirement the object-valued dataSource attribute
                // enforces: the name was configured to supply the pool's
                // connections, and a binding the connect path cannot use must
                // fail here naming the attribute and the name, rather than
                // surfacing at connect time as a generic error about an
                // unknown class.
                throw new NamingException(MessageFactory.get(
                        "dataSourceFactory.jndiNotDataSource",
                        poolProperties.getDataSourceJNDI(),
                        jndiDS.getClass().getName()));
            }
            poolProperties.setDataSource(jndiDS);
            return;
        }
        NamingException failure =
                new NamingException(MessageFactory.get("dataSourceFactory.jndiLookupFailed",
                        poolProperties.getDataSourceJNDI()));
        if (initialContextFailure != null) {
            failure.setRootCause(initialContextFailure);
        }
        throw failure;
    }

    /**
     * Parse properties from the string. Format of the string must be [propertyName=property;]*.
     *
     * <p>
     * Kept as a protected extension point for subclasses that parse property
     * strings of their own; this factory no longer calls it, as its
     * {@code connectionProperties} attribute goes through the canonical
     * {@link PoolProperties#setConnectionProperties(String)}, which parses with the
     * same delegate used here.
     * </p>
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

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

package org.apache.tomcat.dbcp.dbcp2;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.dbcp.pool2.impl.BaseObjectPoolConfig;
import org.apache.tomcat.dbcp.pool2.impl.GenericObjectPoolConfig;

/**
 * <p>JNDI object factory that creates an instance of
 * <code>BasicDataSource</code> that has been configured based on the
 * <code>RefAddr</code> values of the specified <code>Reference</code>, which
 * must match the names and data types of the <code>BasicDataSource</code> bean
 * properties with the following exceptions:</p>
 * <ul>
 * <li><code>connectionInitSqls</code> must be passed to this factory as a
 *     single String using semi-colon to delimit the statements whereas
 *     <code>BasicDataSource</code> requires a collection of Strings.</li>
 * </ul>
 *
 * @author Craig R. McClanahan
 * @author Dirk Verbeeck
 * @since 2.0
 */
public class BasicDataSourceFactory implements ObjectFactory {

    private static final Log log = LogFactory.getLog(BasicDataSourceFactory.class);

    private static final String PROP_DEFAULTAUTOCOMMIT = "defaultAutoCommit";
    private static final String PROP_DEFAULTREADONLY = "defaultReadOnly";
    private static final String PROP_DEFAULTTRANSACTIONISOLATION = "defaultTransactionIsolation";
    private static final String PROP_DEFAULTCATALOG = "defaultCatalog";
    private static final String PROP_CACHESTATE ="cacheState";
    private static final String PROP_DRIVERCLASSNAME = "driverClassName";
    private static final String PROP_LIFO = "lifo";
    private static final String PROP_MAXTOTAL = "maxTotal";
    private static final String PROP_MAXIDLE = "maxIdle";
    private static final String PROP_MINIDLE = "minIdle";
    private static final String PROP_INITIALSIZE = "initialSize";
    private static final String PROP_MAXWAITMILLIS = "maxWaitMillis";
    private static final String PROP_TESTONCREATE = "testOnCreate";
    private static final String PROP_TESTONBORROW = "testOnBorrow";
    private static final String PROP_TESTONRETURN = "testOnReturn";
    private static final String PROP_TIMEBETWEENEVICTIONRUNSMILLIS = "timeBetweenEvictionRunsMillis";
    private static final String PROP_NUMTESTSPEREVICTIONRUN = "numTestsPerEvictionRun";
    private static final String PROP_MINEVICTABLEIDLETIMEMILLIS = "minEvictableIdleTimeMillis";
    private static final String PROP_SOFTMINEVICTABLEIDLETIMEMILLIS = "softMinEvictableIdleTimeMillis";
    private static final String PROP_EVICTIONPOLICYCLASSNAME = "evictionPolicyClassName";
    private static final String PROP_TESTWHILEIDLE = "testWhileIdle";
    private static final String PROP_PASSWORD = "password";
    private static final String PROP_URL = "url";
    private static final String PROP_USERNAME = "username";
    private static final String PROP_VALIDATIONQUERY = "validationQuery";
    private static final String PROP_VALIDATIONQUERY_TIMEOUT = "validationQueryTimeout";
    private static final String PROP_JMX_NAME = "jmxName";

    /**
     * The property name for connectionInitSqls.
     * The associated value String must be of the form [query;]*
     */
    private static final String PROP_CONNECTIONINITSQLS = "connectionInitSqls";
    private static final String PROP_ACCESSTOUNDERLYINGCONNECTIONALLOWED = "accessToUnderlyingConnectionAllowed";
    private static final String PROP_REMOVEABANDONEDONBORROW = "removeAbandonedOnBorrow";
    private static final String PROP_REMOVEABANDONEDONMAINTENANCE = "removeAbandonedOnMaintenance";
    private static final String PROP_REMOVEABANDONEDTIMEOUT = "removeAbandonedTimeout";
    private static final String PROP_LOGABANDONED = "logAbandoned";
    private static final String PROP_ABANDONEDUSAGETRACKING = "abandonedUsageTracking";
    private static final String PROP_POOLPREPAREDSTATEMENTS = "poolPreparedStatements";
    private static final String PROP_MAXOPENPREPAREDSTATEMENTS = "maxOpenPreparedStatements";
    private static final String PROP_CONNECTIONPROPERTIES = "connectionProperties";
    private static final String PROP_MAXCONNLIFETIMEMILLIS = "maxConnLifetimeMillis";
    private static final String PROP_LOGEXPIREDCONNECTIONS = "logExpiredConnections";
    private static final String PROP_ROLLBACK_ON_RETURN = "rollbackOnReturn";
    private static final String PROP_ENABLE_AUTOCOMMIT_ON_RETURN = "enableAutoCommitOnReturn";
    private static final String PROP_DEFAULT_QUERYTIMEOUT = "defaultQueryTimeout";
    private static final String PROP_FASTFAIL_VALIDATION = "fastFailValidation";

    /**
     * Value string must be of the form [STATE_CODE,]*
     */
    private static final String PROP_DISCONNECTION_SQL_CODES = "disconnectionSqlCodes";

    /*
     * Block with obsolete properties from DBCP 1.x.
     * Warn users that these are ignored and they should use the 2.x properties.
     */
    private static final String NUPROP_MAXACTIVE = "maxActive";
    private static final String NUPROP_REMOVEABANDONED = "removeAbandoned";
    private static final String NUPROP_MAXWAIT = "maxWait";

    /*
     * Block with properties expected in a DataSource
     * This props will not be listed as ignored - we know that they may appear in Resource,
     * and not listing them as ignored.
     */
    private static final String SILENTPROP_FACTORY = "factory";
    private static final String SILENTPROP_SCOPE = "scope";
    private static final String SILENTPROP_SINGLETON = "singleton";
    private static final String SILENTPROP_AUTH = "auth";

    private static final String[] ALL_PROPERTIES = {
        PROP_DEFAULTAUTOCOMMIT,
        PROP_DEFAULTREADONLY,
        PROP_DEFAULTTRANSACTIONISOLATION,
        PROP_DEFAULTCATALOG,
        PROP_CACHESTATE,
        PROP_DRIVERCLASSNAME,
        PROP_LIFO,
        PROP_MAXTOTAL,
        PROP_MAXIDLE,
        PROP_MINIDLE,
        PROP_INITIALSIZE,
        PROP_MAXWAITMILLIS,
        PROP_TESTONCREATE,
        PROP_TESTONBORROW,
        PROP_TESTONRETURN,
        PROP_TIMEBETWEENEVICTIONRUNSMILLIS,
        PROP_NUMTESTSPEREVICTIONRUN,
        PROP_MINEVICTABLEIDLETIMEMILLIS,
        PROP_SOFTMINEVICTABLEIDLETIMEMILLIS,
        PROP_EVICTIONPOLICYCLASSNAME,
        PROP_TESTWHILEIDLE,
        PROP_PASSWORD,
        PROP_URL,
        PROP_USERNAME,
        PROP_VALIDATIONQUERY,
        PROP_VALIDATIONQUERY_TIMEOUT,
        PROP_CONNECTIONINITSQLS,
        PROP_ACCESSTOUNDERLYINGCONNECTIONALLOWED,
        PROP_REMOVEABANDONEDONBORROW,
        PROP_REMOVEABANDONEDONMAINTENANCE,
        PROP_REMOVEABANDONEDTIMEOUT,
        PROP_LOGABANDONED,
        PROP_ABANDONEDUSAGETRACKING,
        PROP_POOLPREPAREDSTATEMENTS,
        PROP_MAXOPENPREPAREDSTATEMENTS,
        PROP_CONNECTIONPROPERTIES,
        PROP_MAXCONNLIFETIMEMILLIS,
        PROP_LOGEXPIREDCONNECTIONS,
        PROP_ROLLBACK_ON_RETURN,
        PROP_ENABLE_AUTOCOMMIT_ON_RETURN,
        PROP_DEFAULT_QUERYTIMEOUT,
        PROP_FASTFAIL_VALIDATION,
        PROP_DISCONNECTION_SQL_CODES
    };

    /**
     * Obsolete properties from DBCP 1.x. with warning strings suggesting
     * new properties. LinkedHashMap will guarantee that properties will be listed
     * to output in order of insertion into map.
     */
    private static final Map<String, String> NUPROP_WARNTEXT = new LinkedHashMap<>();

    static {
        NUPROP_WARNTEXT.put(
                NUPROP_MAXACTIVE,
                "Property " + NUPROP_MAXACTIVE + " is not used in DBCP2, use " + PROP_MAXTOTAL + " instead. "
                        + PROP_MAXTOTAL + " default value is " + GenericObjectPoolConfig.DEFAULT_MAX_TOTAL+".");
        NUPROP_WARNTEXT.put(
                NUPROP_REMOVEABANDONED,
                "Property " + NUPROP_REMOVEABANDONED + " is not used in DBCP2,"
                        + " use one or both of "
                        + PROP_REMOVEABANDONEDONBORROW + " or " + PROP_REMOVEABANDONEDONMAINTENANCE + " instead. "
                        + "Both have default value set to false.");
        NUPROP_WARNTEXT.put(
                NUPROP_MAXWAIT,
                "Property " + NUPROP_MAXWAIT + " is not used in DBCP2"
                        + " , use " + PROP_MAXWAITMILLIS + " instead. "
                        + PROP_MAXWAITMILLIS + " default value is " + BaseObjectPoolConfig.DEFAULT_MAX_WAIT_MILLIS+".");
    }

    /**
     * Silent Properties.
     * These properties will not be listed as ignored - we know that they may appear in JDBC Resource references,
     * and we will not list them as ignored.
     */
    private static final List<String> SILENT_PROPERTIES = new ArrayList<>();

    static {
        SILENT_PROPERTIES.add(SILENTPROP_FACTORY);
        SILENT_PROPERTIES.add(SILENTPROP_SCOPE);
        SILENT_PROPERTIES.add(SILENTPROP_SINGLETON);
        SILENT_PROPERTIES.add(SILENTPROP_AUTH);

    }

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
    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?,?> environment)
        throws Exception {

        // We only know how to deal with <code>javax.naming.Reference</code>s
        // that specify a class name of "javax.sql.DataSource"
        if (obj == null || !(obj instanceof Reference)) {
            return null;
        }
        Reference ref = (Reference) obj;
        if (!"javax.sql.DataSource".equals(ref.getClassName())) {
            return null;
        }

        // Check property names and log warnings about obsolete and / or unknown properties
        final List<String> warnings = new ArrayList<>();
        final List<String> infoMessages = new ArrayList<>();
        validatePropertyNames(ref, name, warnings, infoMessages);
        for (String warning : warnings) {
            log.warn(warning);
        }
        for (String infoMessage : infoMessages) {
            log.info(infoMessage);
        }

        Properties properties = new Properties();
        for (String propertyName : ALL_PROPERTIES) {
            RefAddr ra = ref.get(propertyName);
            if (ra != null) {
                String propertyValue = ra.getContent().toString();
                properties.setProperty(propertyName, propertyValue);
            }
        }

        return createDataSource(properties);
    }

    /**
     * Collects warnings and info messages.  Warnings are generated when an obsolete
     * property is set.  Unknown properties generate info messages.
     *
     * @param ref Reference to check properties of
     * @param name Name provided to getObject
     * @param warnings container for warning messages
     * @param infoMessages container for info messages
     */
    private void validatePropertyNames(Reference ref, Name name, List<String> warnings,
                                      List<String> infoMessages) {
        final List<String> allPropsAsList = Arrays.asList(ALL_PROPERTIES);
        final String nameString = name != null ? "Name = " + name.toString() + " " : "";
        if (NUPROP_WARNTEXT!=null && !NUPROP_WARNTEXT.keySet().isEmpty()) {
            for (String propertyName : NUPROP_WARNTEXT.keySet()) {
                final RefAddr ra = ref.get(propertyName);
                if (ra != null && !allPropsAsList.contains(ra.getType())) {
                    final StringBuilder stringBuilder = new StringBuilder(nameString);
                    final String propertyValue = ra.getContent().toString();
                    stringBuilder.append(NUPROP_WARNTEXT.get(propertyName))
                            .append(" You have set value of \"")
                            .append(propertyValue)
                            .append("\" for \"")
                            .append(propertyName)
                            .append("\" property, which is being ignored.");
                    warnings.add(stringBuilder.toString());
                }
            }
        }

        final Enumeration<RefAddr> allRefAddrs = ref.getAll();
        while (allRefAddrs.hasMoreElements()) {
            final RefAddr ra = allRefAddrs.nextElement();
            final String propertyName = ra.getType();
            // If property name is not in the properties list, we haven't warned on it
            // and it is not in the "silent" list, tell user we are ignoring it.
            if (!(allPropsAsList.contains(propertyName)
                    || NUPROP_WARNTEXT.keySet().contains(propertyName)
                    || SILENT_PROPERTIES.contains(propertyName))) {
                final String propertyValue = ra.getContent().toString();
                final StringBuilder stringBuilder = new StringBuilder(nameString);
                stringBuilder.append("Ignoring unknown property: ")
                        .append("value of \"")
                        .append(propertyValue)
                        .append("\" for \"")
                        .append(propertyName)
                        .append("\" property");
                infoMessages.add(stringBuilder.toString());
            }
        }
    }

    /**
     * Creates and configures a {@link BasicDataSource} instance based on the
     * given properties.
     *
     * @param properties the datasource configuration properties
     * @throws Exception if an error occurs creating the data source
     */
    public static BasicDataSource createDataSource(Properties properties) throws Exception {
        BasicDataSource dataSource = new BasicDataSource();
        String value = null;

        value = properties.getProperty(PROP_DEFAULTAUTOCOMMIT);
        if (value != null) {
            dataSource.setDefaultAutoCommit(Boolean.valueOf(value));
        }

        value = properties.getProperty(PROP_DEFAULTREADONLY);
        if (value != null) {
            dataSource.setDefaultReadOnly(Boolean.valueOf(value));
        }

        value = properties.getProperty(PROP_DEFAULTTRANSACTIONISOLATION);
        if (value != null) {
            int level = PoolableConnectionFactory.UNKNOWN_TRANSACTIONISOLATION;
            if ("NONE".equalsIgnoreCase(value)) {
                level = Connection.TRANSACTION_NONE;
            }
            else if ("READ_COMMITTED".equalsIgnoreCase(value)) {
                level = Connection.TRANSACTION_READ_COMMITTED;
            }
            else if ("READ_UNCOMMITTED".equalsIgnoreCase(value)) {
                level = Connection.TRANSACTION_READ_UNCOMMITTED;
            }
            else if ("REPEATABLE_READ".equalsIgnoreCase(value)) {
                level = Connection.TRANSACTION_REPEATABLE_READ;
            }
            else if ("SERIALIZABLE".equalsIgnoreCase(value)) {
                level = Connection.TRANSACTION_SERIALIZABLE;
            }
            else {
                try {
                    level = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    System.err.println("Could not parse defaultTransactionIsolation: " + value);
                    System.err.println("WARNING: defaultTransactionIsolation not set");
                    System.err.println("using default value of database driver");
                    level = PoolableConnectionFactory.UNKNOWN_TRANSACTIONISOLATION;
                }
            }
            dataSource.setDefaultTransactionIsolation(level);
        }

        value = properties.getProperty(PROP_DEFAULTCATALOG);
        if (value != null) {
            dataSource.setDefaultCatalog(value);
        }

        value = properties.getProperty(PROP_CACHESTATE);
        if (value != null) {
            dataSource.setCacheState(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_DRIVERCLASSNAME);
        if (value != null) {
            dataSource.setDriverClassName(value);
        }

        value = properties.getProperty(PROP_LIFO);
        if (value != null) {
            dataSource.setLifo(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_MAXTOTAL);
        if (value != null) {
            dataSource.setMaxTotal(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_MAXIDLE);
        if (value != null) {
            dataSource.setMaxIdle(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_MINIDLE);
        if (value != null) {
            dataSource.setMinIdle(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_INITIALSIZE);
        if (value != null) {
            dataSource.setInitialSize(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_MAXWAITMILLIS);
        if (value != null) {
            dataSource.setMaxWaitMillis(Long.parseLong(value));
        }

        value = properties.getProperty(PROP_TESTONCREATE);
        if (value != null) {
            dataSource.setTestOnCreate(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_TESTONBORROW);
        if (value != null) {
            dataSource.setTestOnBorrow(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_TESTONRETURN);
        if (value != null) {
            dataSource.setTestOnReturn(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_TIMEBETWEENEVICTIONRUNSMILLIS);
        if (value != null) {
            dataSource.setTimeBetweenEvictionRunsMillis(Long.parseLong(value));
        }

        value = properties.getProperty(PROP_NUMTESTSPEREVICTIONRUN);
        if (value != null) {
            dataSource.setNumTestsPerEvictionRun(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_MINEVICTABLEIDLETIMEMILLIS);
        if (value != null) {
            dataSource.setMinEvictableIdleTimeMillis(Long.parseLong(value));
        }

        value = properties.getProperty(PROP_SOFTMINEVICTABLEIDLETIMEMILLIS);
        if (value != null) {
            dataSource.setSoftMinEvictableIdleTimeMillis(Long.parseLong(value));
        }

        value = properties.getProperty(PROP_EVICTIONPOLICYCLASSNAME);
        if (value != null) {
            dataSource.setEvictionPolicyClassName(value);
        }

        value = properties.getProperty(PROP_TESTWHILEIDLE);
        if (value != null) {
            dataSource.setTestWhileIdle(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_PASSWORD);
        if (value != null) {
            dataSource.setPassword(value);
        }

        value = properties.getProperty(PROP_URL);
        if (value != null) {
            dataSource.setUrl(value);
        }

        value = properties.getProperty(PROP_USERNAME);
        if (value != null) {
            dataSource.setUsername(value);
        }

        value = properties.getProperty(PROP_VALIDATIONQUERY);
        if (value != null) {
            dataSource.setValidationQuery(value);
        }

        value = properties.getProperty(PROP_VALIDATIONQUERY_TIMEOUT);
        if (value != null) {
            dataSource.setValidationQueryTimeout(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_ACCESSTOUNDERLYINGCONNECTIONALLOWED);
        if (value != null) {
            dataSource.setAccessToUnderlyingConnectionAllowed(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_REMOVEABANDONEDONBORROW);
        if (value != null) {
            dataSource.setRemoveAbandonedOnBorrow(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_REMOVEABANDONEDONMAINTENANCE);
        if (value != null) {
            dataSource.setRemoveAbandonedOnMaintenance(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_REMOVEABANDONEDTIMEOUT);
        if (value != null) {
            dataSource.setRemoveAbandonedTimeout(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_LOGABANDONED);
        if (value != null) {
            dataSource.setLogAbandoned(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_ABANDONEDUSAGETRACKING);
        if (value != null) {
            dataSource.setAbandonedUsageTracking(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_POOLPREPAREDSTATEMENTS);
        if (value != null) {
            dataSource.setPoolPreparedStatements(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_MAXOPENPREPAREDSTATEMENTS);
        if (value != null) {
            dataSource.setMaxOpenPreparedStatements(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_CONNECTIONINITSQLS);
        if (value != null) {
            dataSource.setConnectionInitSqls(parseList(value, ';'));
        }

        value = properties.getProperty(PROP_CONNECTIONPROPERTIES);
        if (value != null) {
          Properties p = getProperties(value);
          Enumeration<?> e = p.propertyNames();
          while (e.hasMoreElements()) {
            String propertyName = (String) e.nextElement();
            dataSource.addConnectionProperty(propertyName, p.getProperty(propertyName));
          }
        }

        value = properties.getProperty(PROP_MAXCONNLIFETIMEMILLIS);
        if (value != null) {
            dataSource.setMaxConnLifetimeMillis(Long.parseLong(value));
        }

        value = properties.getProperty(PROP_LOGEXPIREDCONNECTIONS);
        if (value != null) {
            dataSource.setLogExpiredConnections(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_JMX_NAME);
        if (value != null) {
            dataSource.setJmxName(value);
        }

        value = properties.getProperty(PROP_ENABLE_AUTOCOMMIT_ON_RETURN);
        if (value != null) {
            dataSource.setEnableAutoCommitOnReturn(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_ROLLBACK_ON_RETURN);
        if (value != null) {
            dataSource.setRollbackOnReturn(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_DEFAULT_QUERYTIMEOUT);
        if (value != null) {
            dataSource.setDefaultQueryTimeout(Integer.valueOf(value));
        }

        value = properties.getProperty(PROP_FASTFAIL_VALIDATION);
        if (value != null) {
            dataSource.setFastFailValidation(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_DISCONNECTION_SQL_CODES);
        if (value != null) {
            dataSource.setDisconnectionSqlCodes(parseList(value, ','));
        }

        // DBCP-215
        // Trick to make sure that initialSize connections are created
        if (dataSource.getInitialSize() > 0) {
            dataSource.getLogWriter();
        }

        // Return the configured DataSource instance
        return dataSource;
    }

    /**
     * <p>Parse properties from the string. Format of the string must be [propertyName=property;]*<p>
     * @param propText
     * @return Properties
     * @throws Exception
     */
    private static Properties getProperties(String propText) throws Exception {
      Properties p = new Properties();
      if (propText != null) {
        p.load(new ByteArrayInputStream(
                propText.replace(';', '\n').getBytes(StandardCharsets.ISO_8859_1)));
      }
      return p;
    }

    /**
     * Parse list of property values from a delimited string
     * @param value delimited list of values
     * @param delimiter character used to separate values in the list
     * @return String Collection of values
     */
    private static Collection<String> parseList(String value, char delimiter) {
        StringTokenizer tokenizer = new StringTokenizer(value, Character.toString(delimiter));
        Collection<String> tokens = new ArrayList<>(tokenizer.countTokens());
        while (tokenizer.hasMoreTokens()) {
            tokens.add(tokenizer.nextToken());
        }
        return tokens;
    }
}

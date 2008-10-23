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
package org.apache.tomcat.jdbc.test;

import java.lang.reflect.Method;
import java.util.Properties;

import org.apache.tomcat.dbcp.dbcp.BasicDataSource;
import org.apache.tomcat.dbcp.dbcp.BasicDataSourceFactory;

import junit.framework.TestCase;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.apache.tomcat.jdbc.pool.DataSourceProxy;

/**
 * @author Filip Hanik
 * @version 1.0
 */
public class DefaultTestCase extends TestCase {
    protected DataSourceProxy datasource;
    protected BasicDataSource tDatasource;
    protected int threadcount = 10;
    protected int iterations = 100000;
    public DefaultTestCase(String name) {
        super(name);
    }

    protected void init() throws Exception {
        PoolProperties p = new DefaultProperties();
        p.setJmxEnabled(false);
        p.setTestWhileIdle(false);
        p.setTestOnBorrow(false);
        p.setTestOnReturn(false);
        p.setValidationInterval(30000);
        p.setTimeBetweenEvictionRunsMillis(30000);
        p.setMaxActive(threadcount);
        p.setInitialSize(threadcount);
        p.setMaxWait(10000);
        p.setRemoveAbandonedTimeout(10000);
        p.setMinEvictableIdleTimeMillis(10000);
        p.setMinIdle(threadcount);
        p.setLogAbandoned(false);
        p.setRemoveAbandoned(false);
        datasource = new org.apache.tomcat.jdbc.pool.DataSourceProxy();
        datasource.setPoolProperties(p);
    }

    protected void transferProperties() {
        try {
            BasicDataSourceFactory factory = new BasicDataSourceFactory();
            Properties p = new Properties();

            for (int i=0; i<this.ALL_PROPERTIES.length; i++) {
                String name = "get" + Character.toUpperCase(ALL_PROPERTIES[i].charAt(0)) + ALL_PROPERTIES[i].substring(1);
                String bname = "is" + name.substring(3);
                Method get = null;
                try {
                    get = PoolProperties.class.getMethod(name, new Class[0]);
                }catch (NoSuchMethodException x) {
                    try {
                    get = PoolProperties.class.getMethod(bname, new Class[0]);
                    }catch (NoSuchMethodException x2) {
                        System.err.println(x2.getMessage());
                    }
                }
                   if (get!=null) {
                       Object value = get.invoke(datasource.getPoolProperties(), new Object[0]);
                       if (value!=null) {
                           p.setProperty(ALL_PROPERTIES[i], value.toString());
                       }
                }
            }
            tDatasource = (BasicDataSource)factory.createDataSource(p);
        }catch (Exception x) {
            x.printStackTrace();
        }
    }


    protected void tearDown() throws Exception {
        datasource = null;
        tDatasource = null;
        System.gc();
    }

    private final static String PROP_DEFAULTAUTOCOMMIT = "defaultAutoCommit";
    private final static String PROP_DEFAULTREADONLY = "defaultReadOnly";
    private final static String PROP_DEFAULTTRANSACTIONISOLATION = "defaultTransactionIsolation";
    private final static String PROP_DEFAULTCATALOG = "defaultCatalog";
    private final static String PROP_DRIVERCLASSNAME = "driverClassName";
    private final static String PROP_MAXACTIVE = "maxActive";
    private final static String PROP_MAXIDLE = "maxIdle";
    private final static String PROP_MINIDLE = "minIdle";
    private final static String PROP_INITIALSIZE = "initialSize";
    private final static String PROP_MAXWAIT = "maxWait";
    private final static String PROP_TESTONBORROW = "testOnBorrow";
    private final static String PROP_TESTONRETURN = "testOnReturn";
    private final static String PROP_TIMEBETWEENEVICTIONRUNSMILLIS = "timeBetweenEvictionRunsMillis";
    private final static String PROP_NUMTESTSPEREVICTIONRUN = "numTestsPerEvictionRun";
    private final static String PROP_MINEVICTABLEIDLETIMEMILLIS = "minEvictableIdleTimeMillis";
    private final static String PROP_TESTWHILEIDLE = "testWhileIdle";
    private final static String PROP_PASSWORD = "password";
    private final static String PROP_URL = "url";
    private final static String PROP_USERNAME = "username";
    private final static String PROP_VALIDATIONQUERY = "validationQuery";
    private final static String PROP_ACCESSTOUNDERLYINGCONNECTIONALLOWED = "accessToUnderlyingConnectionAllowed";
    private final static String PROP_REMOVEABANDONED = "removeAbandoned";
    private final static String PROP_REMOVEABANDONEDTIMEOUT = "removeAbandonedTimeout";
    private final static String PROP_LOGABANDONED = "logAbandoned";
    private final static String PROP_POOLPREPAREDSTATEMENTS = "poolPreparedStatements";
    private final static String PROP_MAXOPENPREPAREDSTATEMENTS = "maxOpenPreparedStatements";
    private final static String PROP_CONNECTIONPROPERTIES = "connectionProperties";

    private final static String[] ALL_PROPERTIES = {
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
        PROP_PASSWORD,
        PROP_URL,
        PROP_USERNAME,
        PROP_VALIDATIONQUERY,
        PROP_ACCESSTOUNDERLYINGCONNECTIONALLOWED,
        PROP_REMOVEABANDONED,
        PROP_REMOVEABANDONEDTIMEOUT,
        PROP_LOGABANDONED,
        PROP_CONNECTIONPROPERTIES
    };



}

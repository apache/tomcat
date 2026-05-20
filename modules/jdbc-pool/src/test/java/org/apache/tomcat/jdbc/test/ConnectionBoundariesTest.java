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

import org.apache.tomcat.jdbc.test.driver.Driver;

import java.sql.Connection;

import org.junit.Test;
import org.junit.Assert;

public class ConnectionBoundariesTest extends DefaultTestCase {


    @Override
    public org.apache.tomcat.jdbc.pool.DataSource createDefaultDataSource() {
        // TODO Auto-generated method stub
        org.apache.tomcat.jdbc.pool.DataSource ds = super.createDefaultDataSource();
        ds.getPoolProperties().setDriverClassName(Driver.class.getName());
        ds.getPoolProperties().setUrl(Driver.url);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMaxIdle(10);
        ds.getPoolProperties().setMinIdle(10);
        ds.getPoolProperties().setMaxActive(10);
        return ds;
    }
    @Test
    public void connectionWithRequestBoundariesTest() throws Exception {
        double javaVersion = Double.valueOf(System.getProperty("java.class.version"));
        org.junit.Assume.assumeTrue(javaVersion >= 53);
        Connection connection = datasource.getConnection();
        Assert.assertEquals("Connection.beginRequest() count", 1, Driver.beginRequestCount.get());
        Assert.assertEquals("Connection.endRequest() count", 0, Driver.endRequestCount.get());
        connection.close();
        Assert.assertEquals("Connection.beginRequest() count", 1, Driver.beginRequestCount.get());
        Assert.assertEquals("Connection.endRequest() count", 1, Driver.endRequestCount.get());
    }

    @Test
    public void connectionWithoutRequestBoundariesTest() throws Exception {
        double javaVersion = Double.valueOf(System.getProperty("java.class.version"));
        org.junit.Assume.assumeTrue(javaVersion < 53);
        Connection connection = datasource.getConnection();
        Assert.assertEquals("Connection.beginRequest() count", 0, Driver.beginRequestCount.get());
        Assert.assertEquals("Connection.endRequest() count", 0, Driver.endRequestCount.get());
        connection.close();
        Assert.assertEquals("Connection.beginRequest() count", 0, Driver.beginRequestCount.get());
        Assert.assertEquals("Connection.endRequest() count", 0, Driver.endRequestCount.get());
    }

}

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

import java.sql.Connection;

import org.apache.tomcat.jdbc.test.driver.Driver;

/**
 * @author Filip Hanik
 * @version 1.0
 */
public class PoolPurgeTest extends DefaultTestCase {
    public PoolPurgeTest(String name) {
        super(name);
    }

    static final int expectedSize = 2;


    @Override
    public org.apache.tomcat.jdbc.pool.DataSource createDefaultDataSource() {
        // TODO Auto-generated method stub
        org.apache.tomcat.jdbc.pool.DataSource ds = super.createDefaultDataSource();
        ds.getPoolProperties().setDriverClassName(Driver.class.getName());
        ds.getPoolProperties().setUrl(Driver.url);
        ds.getPoolProperties().setInitialSize(expectedSize);
        ds.getPoolProperties().setMaxIdle(expectedSize);
        ds.getPoolProperties().setMinIdle(expectedSize);
        ds.getPoolProperties().setMaxActive(expectedSize);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(30000);
        ds.getPoolProperties().setMaxAge(Long.MAX_VALUE);
        return ds;
    }


    @Override
    protected void tearDown() throws Exception {
        Driver.reset();
        super.tearDown();
    }



    public void testPoolPurge() throws Exception {
        init();
        this.datasource.getConnection().close();
        assertEquals("Nr of connections should be "+expectedSize, expectedSize , datasource.getSize());
        this.datasource.purge();
        assertEquals("Nr of connections should be 0", 0 , datasource.getSize());
        tearDown();
    }

    public void testPoolPurgeWithActive() throws Exception {
        init();
        Connection con = datasource.getConnection();
        assertEquals("Nr of connections should be "+expectedSize, expectedSize , datasource.getSize());
        this.datasource.purge();
        assertEquals("Nr of connections should be "+(expectedSize-1), (expectedSize-1) , datasource.getSize());
        con.close();
        assertEquals("Nr of connections should be 0", 0 , datasource.getSize());
        tearDown();
    }

    public void testPoolPurgeOnReturn() throws Exception {
        init();
        Connection con = datasource.getConnection();
        assertEquals("Nr of connections should be "+expectedSize, expectedSize , datasource.getSize());
        this.datasource.purgeOnReturn();
        assertEquals("Nr of connections should be "+expectedSize, expectedSize , datasource.getSize());
        con.close();
        assertEquals("Nr of connections should be "+(expectedSize-1), (expectedSize-1) , datasource.getSize());
        tearDown();
    }

}


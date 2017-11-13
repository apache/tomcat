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
package org.apache.tomcat.jdbc.bugs;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.PooledConnection;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.apache.tomcat.jdbc.test.DefaultProperties;

public class Bug54227 {


    public Bug54227() {
    }

    @Test
    public void testPool() throws SQLException, InterruptedException {
        PoolProperties poolProperties = new DefaultProperties();
        poolProperties.setMinIdle(0);
        poolProperties.setInitialSize(0);
        poolProperties.setMaxActive(1);
        poolProperties.setMaxWait(5000);
        poolProperties.setMaxAge(100);
        poolProperties.setRemoveAbandoned(false);

        final DataSource ds = new DataSource(poolProperties);
        Connection con;
        Connection actual1;
        Connection actual2;

        con = ds.getConnection();
        actual1 = ((PooledConnection)con).getConnection();
        con.close();
        con = ds.getConnection();
        actual2 = ((PooledConnection)con).getConnection();
        Assert.assertSame(actual1, actual2);
        con.close();
        Thread.sleep(150);
        con = ds.getConnection();
        actual2 = ((PooledConnection)con).getConnection();
        Assert.assertNotSame(actual1, actual2);
        con.close();
    }
}
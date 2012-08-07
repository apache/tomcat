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

import javax.sql.PooledConnection;

import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.test.driver.Driver;

public class EqualsHashCodeTest extends DefaultTestCase{
    public static final String password = "password";
    public static final String username = "username";

    public EqualsHashCodeTest(String s) {
        super(s);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        this.datasource.setDriverClassName(Driver.class.getName());
        this.datasource.setUrl("jdbc:tomcat:test");
        this.datasource.setPassword(password);
        this.datasource.setMaxActive(1);
        this.datasource.setMinIdle(datasource.getMaxActive());
        this.datasource.setMaxIdle(datasource.getMaxActive());
        this.datasource.setUsername(username);
        this.datasource.getConnection().close();
        ConnectionPool pool = datasource.createPool();
    }

    public void testEquals() throws Exception {
        Connection con1 = datasource.getConnection();
        Connection real1 = ((PooledConnection)con1).getConnection();
        assertEquals(con1, con1);
        con1.close();
        assertEquals(con1, con1);
        Connection con2 = datasource.getConnection();
        Connection real2 = ((PooledConnection)con2).getConnection();
        assertEquals(real1,real2);
        assertEquals(con2, con2);
        assertNotSame(con1, con2);
        con2.close();
        assertEquals(con2, con2);
    }

    public void testHashCode() throws Exception {
        Connection con1 = datasource.getConnection();
        assertEquals(con1.hashCode(), con1.hashCode());
        con1.close();
        assertEquals(con1.hashCode(), con1.hashCode());
        Connection con2 = datasource.getConnection();
        assertEquals(con2.hashCode(), con2.hashCode());
        assertNotSame(con1.hashCode(), con2.hashCode());
        con2.close();
        assertEquals(con2.hashCode(), con2.hashCode());
    }
}

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

import java.sql.SQLException;

import static org.junit.Assert.fail;

import org.junit.Test;

import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.apache.tomcat.jdbc.test.DefaultProperties;

public class Bug54978 {

    @Test
    public void testIllegalValidationQuery() {
        PoolProperties poolProperties = new DefaultProperties();
        poolProperties.setMinIdle(0);
        poolProperties.setInitialSize(1);
        poolProperties.setMaxActive(1);
        poolProperties.setMaxWait(5000);
        poolProperties.setMaxAge(100);
        poolProperties.setRemoveAbandoned(false);
        poolProperties.setTestOnBorrow(true);
        poolProperties.setTestOnConnect(false);
        poolProperties.setValidationQuery("sdadsada");
        final DataSource ds = new DataSource(poolProperties);
        try {
            ds.getConnection().close();
            fail("Validation should have failed.");
        }catch (SQLException x) {
        }
    }

    @Test
    public void testIllegalValidationQueryWithLegalInit() throws SQLException {
        PoolProperties poolProperties = new DefaultProperties();
        poolProperties.setMinIdle(0);
        poolProperties.setInitialSize(1);
        poolProperties.setMaxActive(1);
        poolProperties.setMaxWait(5000);
        poolProperties.setMaxAge(100);
        poolProperties.setRemoveAbandoned(false);
        poolProperties.setTestOnBorrow(true);
        poolProperties.setTestOnConnect(false);
        poolProperties.setValidationQuery("sdadsada");
        poolProperties.setInitSQL("SELECT 1");
        final DataSource ds = new DataSource(poolProperties);
        ds.getConnection().close();
    }
}
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
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.jdbc.pool;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * Replacing a data source's whole configuration is a start-time operation. A
 * live pool keeps the configuration object it was built from; replacing the
 * data source's field afterwards would make its setters and getters operate on
 * a second object that the running pool never reads.
 */
class TestDataSourceProxyPoolProperties extends JdbcPoolBaseTest {

    private static PoolProperties replacementProperties() {
        PoolProperties properties = new PoolProperties();
        properties.setUrl(getUrl());
        properties.setDriverClassName("org.h2.Driver");
        properties.setUsername("sa");
        properties.setPassword("");
        properties.setInitialSize(0);
        properties.setTimeBetweenEvictionRunsMillis(-1);
        properties.setJmxEnabled(false);
        return properties;
    }

    @Test
    void replacingTheConfigurationAfterPoolStartIsRejected() throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();
        PoolConfiguration original = ds.getPoolProperties();

        UnsupportedOperationException failure = Assertions.assertThrows(UnsupportedOperationException.class,
                () -> ds.setPoolProperties(replacementProperties()));

        Assertions.assertTrue(failure.getMessage().contains("poolProperties"),
                "the refusal must name the property, was: " + failure.getMessage());
        Assertions.assertSame(original, ds.getPoolProperties(),
                "a rejected replacement must leave the configuration the running pool uses untouched");
    }

    @Test
    void replacingTheConfigurationAfterCloseIsRejected() throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();
        ds.close();
        PoolConfiguration original = ds.getPoolProperties();

        UnsupportedOperationException failure = Assertions.assertThrows(UnsupportedOperationException.class,
                () -> ds.setPoolProperties(replacementProperties()));

        Assertions.assertTrue(failure.getMessage().contains("poolProperties"),
                "the refusal must name the property, was: " + failure.getMessage());
        Assertions.assertSame(original, ds.getPoolProperties(),
                "a closed data source must not accept a configuration replacement");
    }

    @Test
    void replacingTheConfigurationBeforePoolStartIsAccepted() {
        DataSource ds = new DataSource();
        PoolProperties properties = replacementProperties();

        Assertions.assertDoesNotThrow(() -> ds.setPoolProperties(properties));
        Assertions.assertSame(properties, ds.getPoolProperties());
    }
}

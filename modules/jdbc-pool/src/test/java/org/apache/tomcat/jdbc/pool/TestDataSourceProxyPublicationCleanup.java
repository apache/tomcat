/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.jdbc.pool;

import java.sql.SQLException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * A failure during the post-construction publication step must close the
 * freshly built pool, including the physical sessions its initial pre-load
 * established.
 */
class TestDataSourceProxyPublicationCleanup extends JdbcPoolBaseTest {

    /**
     * Throws when the configuration validation is called directly from the
     * publication step, after the pool constructor has already run.
     */
    public static class FailingPublicationProperties extends PoolProperties {

        @Override
        public int getMaxActive() {
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            for (int i = 0; i < stack.length - 1; i++) {
                if ("checkPoolConfiguration".equals(stack[i].getMethodName()) &&
                        "pCreatePool".equals(stack[i + 1].getMethodName())) {
                    throw new IllegalStateException("boom from publication");
                }
            }
            return super.getMaxActive();
        }
    }

    @Test
    void aFailureDuringPublicationClosesTheFreshPool() throws Exception {
        FailingPublicationProperties properties = new FailingPublicationProperties();
        properties.setUrl(getUrl());
        properties.setDriverClassName("org.h2.Driver");
        properties.setUsername("sa");
        properties.setPassword("");
        properties.setJmxEnabled(false);
        properties.setInitialSize(1);
        properties.setMinIdle(0);
        properties.setMaxActive(1);
        properties.setMaxIdle(1);
        properties.setTimeBetweenEvictionRunsMillis(-1);
        DataSource ds = manage(new DataSource(properties));

        SQLException thrown = Assertions.assertThrows(SQLException.class, ds::createPool,
                "a publication failure must fail pool creation");
        Assertions.assertInstanceOf(IllegalStateException.class, thrown.getCause(),
                "the publication failure must remain the cause");
        awaitSessions(0);
    }
}

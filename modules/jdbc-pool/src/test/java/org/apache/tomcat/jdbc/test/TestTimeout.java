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

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Filip Hanik
 * @version 1.0
 */
public class TestTimeout extends DefaultTestCase {
    public TestTimeout(String name) {
        super(name);
    }

    AtomicInteger counter = new AtomicInteger(0);

    public void testCheckoutTimeout() throws Exception {
        try {
            init();
            this.datasource.getPoolProperties().setTestWhileIdle(true);
            this.datasource.getPoolProperties().setTestOnBorrow(false);
            this.datasource.getPoolProperties().setTestOnReturn(false);
            this.datasource.getPoolProperties().setValidationInterval(30000);
            this.datasource.getPoolProperties().setTimeBetweenEvictionRunsMillis(1000);
            this.datasource.getPoolProperties().setMaxActive(20);
            this.datasource.getPoolProperties().setMaxWait(3000);
            this.datasource.getPoolProperties().setRemoveAbandonedTimeout(5);
            this.datasource.getPoolProperties().setMinEvictableIdleTimeMillis(5000);
            this.datasource.getPoolProperties().setMinIdle(5);
            this.datasource.getPoolProperties().setLogAbandoned(true);
            System.out.println("About to test connection pool:"+datasource);
            for (int i = 0; i < 21; i++) {
                long now = System.currentTimeMillis();
                this.datasource.getConnection();
                long delta = System.currentTimeMillis()-now;
                System.out.println("Got connection #"+i+" in "+delta+" ms.");
            }
            assertTrue(false);
        } catch ( Exception x ) {
            assertTrue(true);
        }finally {
            Thread.sleep(2000);
            tearDown();
        }
    }

    public void testCheckoutTimeoutFair() throws Exception {
        try {
            init();
            this.datasource.getPoolProperties().setFairQueue(true);
            this.datasource.getPoolProperties().setTestWhileIdle(true);
            this.datasource.getPoolProperties().setTestOnBorrow(false);
            this.datasource.getPoolProperties().setTestOnReturn(false);
            this.datasource.getPoolProperties().setValidationInterval(30000);
            this.datasource.getPoolProperties().setTimeBetweenEvictionRunsMillis(1000);
            this.datasource.getPoolProperties().setMaxActive(20);
            this.datasource.getPoolProperties().setMaxWait(3000);
            this.datasource.getPoolProperties().setRemoveAbandonedTimeout(5);
            this.datasource.getPoolProperties().setMinEvictableIdleTimeMillis(5000);
            this.datasource.getPoolProperties().setMinIdle(5);
            this.datasource.getPoolProperties().setLogAbandoned(true);
            System.out.println("About to test connection pool:"+datasource);
            for (int i = 0; i < 21; i++) {
                long now = System.currentTimeMillis();
                this.datasource.getConnection();
                long delta = System.currentTimeMillis()-now;
                System.out.println("Got connection #"+i+" in "+delta+" ms.");
            }
            assertTrue(false);
        } catch ( Exception x ) {
            assertTrue(true);
        }finally {
            Thread.sleep(2000);
            tearDown();
        }
    }

}

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
package org.apache.tomcat.jdbc.naming;

import javax.naming.CompositeName;
import javax.naming.Name;
import javax.naming.Reference;
import javax.naming.StringRefAddr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.DataSourceFactory;
import org.apache.tomcat.jdbc.pool.XADataSource;

/**
 * Expected JNDI/naming factory functionality: a container-style
 * {@code Resource} description (a {@link Reference}) produces a
 * correctly configured, fully working pool; and the generic naming
 * factory applies documented property types to the configured object.
 */
class TestNamingFactory extends JdbcPoolBaseTest {

    private Reference dataSourceReference(String className) {
        Reference ref = new Reference(className, null, null);
        ref.add(new StringRefAddr("driverClassName", "org.h2.Driver"));
        ref.add(new StringRefAddr("url", getUrl()));
        ref.add(new StringRefAddr("username", "sa"));
        ref.add(new StringRefAddr("password", ""));
        ref.add(new StringRefAddr("maxActive", "4"));
        ref.add(new StringRefAddr("initialSize", "1"));
        ref.add(new StringRefAddr("minIdle", "0"));
        return ref;
    }

    @Test
    void referenceProducesWorkingConfiguredPool() throws Exception {
        Name name = new CompositeName("jdbc/FunctionalDS");
        Object result = new DataSourceFactory().getObjectInstance(
                dataSourceReference("javax.sql.DataSource"), name, null, null);

        assertInstanceOf(DataSource.class, result,
                "factory must produce a pool DataSource");
        DataSource ds = manage((DataSource) result);
        assertEquals(4, ds.getPoolProperties().getMaxActive(),
                "maxActive from the reference must be applied");
        assertEquals("sa", ds.getPoolProperties().getUsername());

        try (java.sql.Connection c = ds.getConnection();
                java.sql.ResultSet rs = c.createStatement().executeQuery("SELECT 1")) {
            assertTrue(rs.next());
        }
        assertEquals(1, poolSessions(),
                "the JNDI-configured pool must reach the real database");
    }

    @Test
    void xaReferenceProducesXaDataSourceType() throws Exception {
        Name name = new CompositeName("jdbc/FunctionalXaDS");
        Object result = new DataSourceFactory().getObjectInstance(
                dataSourceReference("javax.sql.XADataSource"), name, null, null);
        assertInstanceOf(XADataSource.class, result,
                "a javax.sql.XADataSource reference must yield the pool's XA type");
        if (result instanceof DataSource) {
            manage((DataSource) result);
        }
    }

    @Test
    void namingFactoryAppliesDocumentedPropertyTypes() throws Exception {
        Reference ref = new Reference(NamingTarget.class.getName(), null, null);
        ref.add(new StringRefAddr("name", "configured"));
        ref.add(new StringRefAddr("count", "7"));
        ref.add(new StringRefAddr("ratio", "1234567890"));
        ref.add(new StringRefAddr("enabled", "true"));

        Object result = new org.apache.tomcat.jdbc.naming.GenericNamingResourcesFactory()
                .getObjectInstance(ref, new CompositeName("test/target"), null, null);
        assertInstanceOf(NamingTarget.class, result);
        NamingTarget target = (NamingTarget) result;
        assertEquals("configured", target.getName());
        assertEquals(7, target.getCount());
        assertEquals(1234567890L, target.getRatio());
        assertTrue(target.isEnabled());
    }

    /** Plain configuration target for the generic naming factory. */
    public static class NamingTarget {
        private String name;
        private int count;
        private long ratio;
        private boolean enabled;

        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public int getCount() {
            return count;
        }
        public void setCount(int count) {
            this.count = count;
        }
        public long getRatio() {
            return ratio;
        }
        public void setRatio(long ratio) {
            this.ratio = ratio;
        }
        public boolean isEnabled() {
            return enabled;
        }
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}

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

import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Configuration only, no database and no pool: the credential setters on
 * {@link DataSourceProxy} mirror the credential into the database properties
 * that {@link PooledConnection} clones when it hands them to the driver, and
 * passing {@code null} must clear the credential from both stores instead of
 * throwing halfway. The connection path only overrides the clone with the
 * stored password when that is non-null, so a mirror left behind keeps
 * authenticating freshly established connections with the cleared credential.
 */
class TestDataSourceProxyCredentials {

    @Test
    void aNullPasswordClearsTheMirroredDatabaseProperty() {
        DataSourceProxy proxy = new DataSourceProxy();
        proxy.setUsername("alice");
        proxy.setPassword("s3cret");
        Properties dbProperties = proxy.getPoolProperties().getDbProperties();
        Assertions.assertEquals("alice", dbProperties.getProperty(PoolUtilities.PROP_USER));
        Assertions.assertEquals("s3cret", dbProperties.getProperty(PoolUtilities.PROP_PASSWORD));

        // Before the fix this threw after clearing the field, leaving the
        // old password in the properties the driver receives.
        Assertions.assertDoesNotThrow(() -> proxy.setPassword(null));

        Assertions.assertNull(proxy.getPoolProperties().getPassword());
        Assertions.assertFalse(dbProperties.containsKey(PoolUtilities.PROP_PASSWORD),
                "the cleared password must not stay in the properties the driver is given");
        Assertions.assertEquals("alice", dbProperties.getProperty(PoolUtilities.PROP_USER),
                "clearing the password must leave the user name alone");
    }

    @Test
    void aNullUserNameClearsTheMirroredDatabaseProperty() {
        DataSourceProxy proxy = new DataSourceProxy();
        proxy.setUsername("alice");
        proxy.setPassword("s3cret");
        Properties dbProperties = proxy.getPoolProperties().getDbProperties();

        Assertions.assertDoesNotThrow(() -> proxy.setUsername(null));

        Assertions.assertNull(proxy.getPoolProperties().getUsername());
        Assertions.assertFalse(dbProperties.containsKey(PoolUtilities.PROP_USER),
                "the cleared user name must not stay in the properties the driver is given");
        Assertions.assertEquals("s3cret", dbProperties.getProperty(PoolUtilities.PROP_PASSWORD),
                "clearing the user name must leave the password alone");
    }

    @Test
    void credentialsCanBeSetAgainAfterTheyWereCleared() {
        DataSourceProxy proxy = new DataSourceProxy();
        proxy.setPassword("first");
        proxy.setUsername("alice");
        proxy.setPassword(null);
        proxy.setUsername(null);
        proxy.setPassword("second");
        proxy.setUsername("bob");
        Properties dbProperties = proxy.getPoolProperties().getDbProperties();

        Assertions.assertEquals("second", dbProperties.getProperty(PoolUtilities.PROP_PASSWORD));
        Assertions.assertEquals("bob", dbProperties.getProperty(PoolUtilities.PROP_USER));
    }

    @Test
    void settingCredentialsToleratesNullDatabaseProperties() {
        // PoolProperties.setDbProperties accepts null, and a credential
        // setter must not turn that into a NullPointerException that leaves
        // the value half-applied; the field is the whole of the state.
        DataSourceProxy proxy = new DataSourceProxy();
        proxy.getPoolProperties().setDbProperties(null);

        Assertions.assertDoesNotThrow(() -> proxy.setPassword("hunter2"));
        Assertions.assertDoesNotThrow(() -> proxy.setUsername(null));

        Assertions.assertEquals("hunter2", proxy.getPoolProperties().getPassword());
        Assertions.assertNull(proxy.getPoolProperties().getUsername());
    }
}

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

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * The credential setters replace every spelling of the credential they own,
 * and the documented rule outlives a {@code connectionProperties} parse: while
 * a username or password field is set, its entry holds the field's value, no
 * matter what the string names. A rotation through the string, through a JNDI
 * factory parse, or through a direct database-properties write must therefore
 * not leave a superseded {@code pass}, {@code pwd}, {@code PASSWORD} or
 * {@code USER} entry behind for the driver to authenticate with.
 */
abstract class ConnectionPropertiesCredentialAliasesBase extends JdbcPoolBaseTest {

    /** Records the properties each connect was offered and declines the session. */
    public static class AliasRecordingDriver implements Driver {

        static final AtomicReference<Properties> lastConnectInfo = new AtomicReference<>();

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            Properties handed = new Properties();
            handed.putAll(info);
            lastConnectInfo.set(handed);
            return null;
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return true;
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
    }

    private static void assertOnlyTheCanonicalCredentials(PoolProperties props) {
        Assertions.assertEquals("newuser", props.getDbProperties().getProperty("user"),
                "the canonical user entry must hold the configured user name");
        Assertions.assertEquals("newpass", props.getDbProperties().getProperty("password"),
                "the canonical password entry must hold the configured password");
        Assertions.assertNull(props.getDbProperties().getProperty("pass"),
                "an older pass alias must not keep authenticating connections");
        Assertions.assertNull(props.getDbProperties().getProperty("pwd"),
                "an older pwd alias must not keep authenticating connections");
        Assertions.assertNull(props.getDbProperties().getProperty("PASSWORD"),
                "a case-variant password alias must not keep authenticating connections");
        Assertions.assertNull(props.getDbProperties().getProperty("USER"),
                "an older USER spelling must not keep authenticating connections");
    }

    @Test
    void aStringWrittenAfterARotationCannotReintroduceCredentialAliases() {
        PoolProperties props = new PoolProperties();
        props.setUsername("newuser");
        props.setPassword("newpass");
        props.setConnectionProperties("keep=yes;pass=old;PWD=older;PASSWORD=oldest;USER=olduser;user=old");

        assertOnlyTheCanonicalCredentials(props);
        Assertions.assertEquals("yes", props.getDbProperties().getProperty("keep"),
                "non-credential entries from the string must still be applied");
    }

    @Test
    void aFactoryParseCannotReintroduceCredentialAliasesAfterTheCredentialAttributes() {
        Properties attributes = new Properties();
        attributes.setProperty("username", "newuser");
        attributes.setProperty("password", "newpass");
        attributes.setProperty("connectionProperties", "keep=yes;pass=old;PWD=older;PASSWORD=oldest;USER=olduser;user=old");

        PoolProperties props = (PoolProperties) DataSourceFactory.parsePoolProperties(attributes);

        assertOnlyTheCanonicalCredentials(props);
        Assertions.assertEquals("yes", props.getDbProperties().getProperty("keep"),
                "non-credential entries from the string must still be applied");
    }

    @Test
    void theDriverIsHandedNoSupersededCredentialAliases() throws Exception {
        PoolProperties props = new PoolProperties();
        props.setJmxEnabled(false);
        props.setInitialSize(0);
        props.setMinIdle(0);
        props.setUrl("jdbc:tomcat-jdbc-test:credential-aliases");
        props.setDriverClassName(AliasRecordingDriver.class.getName());
        props.setUsername("newuser");
        props.setPassword("newpass");
        props.setConnectionProperties("keep=yes;pass=old;PWD=older;PASSWORD=oldest;USER=olduser;user=old");
        AliasRecordingDriver.lastConnectInfo.set(null);

        ConnectionPool pool = new ConnectionPool(props);
        PooledConnection con = new PooledConnection(props, pool);
        try {
            Assertions.assertThrows(SQLException.class, con::connect,
                    "the recording driver declines the connect after recording the properties");
            Properties handed = AliasRecordingDriver.lastConnectInfo.get();
            Assertions.assertNotNull(handed, "the driver must have been offered the properties");
            Assertions.assertEquals("newuser", handed.getProperty("user"));
            Assertions.assertEquals("newpass", handed.getProperty("password"));
            Assertions.assertEquals("yes", handed.getProperty("keep"));
            Assertions.assertNull(handed.getProperty("pass"),
                    "the driver must not be handed a superseded pass alias");
            Assertions.assertNull(handed.getProperty("pwd"),
                    "the driver must not be handed a superseded pwd alias");
            Assertions.assertNull(handed.getProperty("PASSWORD"),
                    "the driver must not be handed a superseded password spelling");
            Assertions.assertNull(handed.getProperty("USER"),
                    "the driver must not be handed a superseded user spelling");
        } finally {
            con.release();
            pool.close(true);
        }
    }

    @Test
    void clearingCredentialsRemovesThemFromANullDatabasePropertiesString() throws Exception {
        PoolProperties props = new PoolProperties();
        props.setJmxEnabled(false);
        props.setInitialSize(0);
        props.setMinIdle(0);
        props.setUrl("jdbc:tomcat-jdbc-test:null-database-properties-credential-clear");
        props.setDriverClassName(AliasRecordingDriver.class.getName());
        props.setDbProperties(null);
        props.setConnectionProperties("keep=yes;user=alice;password=secret");
        props.setUsername(null);
        props.setPassword(null);
        AliasRecordingDriver.lastConnectInfo.set(null);

        ConnectionPool pool = new ConnectionPool(props);
        PooledConnection con = new PooledConnection(props, pool);
        try {
            Assertions.assertThrows(SQLException.class, con::connect,
                    "the recording driver declines the connect after recording the properties");
            Properties handed = AliasRecordingDriver.lastConnectInfo.get();
            Assertions.assertNotNull(handed, "the driver must have been offered the properties");
            Assertions.assertEquals("yes", handed.getProperty("keep"),
                    "non-credential entries from the string must still be applied");
            Assertions.assertNull(handed.getProperty("user"),
                    "clearing the user name must remove the string-provided user");
            Assertions.assertNull(handed.getProperty("password"),
                    "clearing the password must remove the string-provided password");
        } finally {
            con.release();
            pool.close(true);
        }
    }

    @Test
    void aStringWrittenAfterAClearDeclaresTheCredentialAgain() throws Exception {
        PoolProperties props = new PoolProperties();
        props.setJmxEnabled(false);
        props.setInitialSize(0);
        props.setMinIdle(0);
        props.setUrl("jdbc:tomcat-jdbc-test:null-database-properties-credential-redeclare");
        props.setDriverClassName(AliasRecordingDriver.class.getName());
        props.setDbProperties(null);
        props.setUsername(null);
        props.setPassword(null);
        props.setConnectionProperties("keep=yes;user=alice;password=secret");
        AliasRecordingDriver.lastConnectInfo.set(null);

        ConnectionPool pool = new ConnectionPool(props);
        PooledConnection con = new PooledConnection(props, pool);
        try {
            Assertions.assertThrows(SQLException.class, con::connect,
                    "the recording driver declines the connect after recording the properties");
            Properties handed = AliasRecordingDriver.lastConnectInfo.get();
            Assertions.assertNotNull(handed, "the driver must have been offered the properties");
            Assertions.assertEquals("alice", handed.getProperty("user"),
                    "a string written after a clear is a fresh declaration of the user name");
            Assertions.assertEquals("secret", handed.getProperty("password"),
                    "a string written after a clear is a fresh declaration of the password");
        } finally {
            con.release();
            pool.close(true);
        }
    }

    @Test
    void aDatabasePropertiesWriteCannotSneakAnAliasPastARotation() throws Exception {
        PoolProperties props = new PoolProperties();
        props.setJmxEnabled(false);
        props.setInitialSize(0);
        props.setMinIdle(0);
        props.setUrl("jdbc:tomcat-jdbc-test:credential-alias-write");
        props.setDriverClassName(AliasRecordingDriver.class.getName());
        props.setUsername("newuser");
        props.setPassword("newpass");
        props.getDbProperties().setProperty("pass", "old");
        props.getDbProperties().setProperty("PWD", "older");
        props.getDbProperties().setProperty("USER", "olduser");
        AliasRecordingDriver.lastConnectInfo.set(null);

        ConnectionPool pool = new ConnectionPool(props);
        PooledConnection con = new PooledConnection(props, pool);
        try {
            Assertions.assertThrows(SQLException.class, con::connect,
                    "the recording driver declines the connect after recording the properties");
            Properties handed = AliasRecordingDriver.lastConnectInfo.get();
            Assertions.assertNotNull(handed, "the driver must have been offered the properties");
            Assertions.assertEquals("newuser", handed.getProperty("user"));
            Assertions.assertEquals("newpass", handed.getProperty("password"));
            Assertions.assertNull(handed.getProperty("pass"),
                    "the driver must not be handed an alias written into the live collection");
            Assertions.assertNull(handed.getProperty("pwd"),
                    "the driver must not be handed an alias written into the live collection");
            Assertions.assertNull(handed.getProperty("USER"),
                    "the driver must not be handed a user spelling written into the live collection");
        } finally {
            con.release();
            pool.close(true);
        }
    }
}

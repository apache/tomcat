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
package org.apache.tomcat.jdbc.pool.util;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.pool.PoolUtilities;
import org.apache.tomcat.jdbc.pool.PooledConnection;

/**
 * Unit tests for {@link PoolUtilities}, the helpers that build the
 * {@link Properties} object {@link PooledConnection} hands to the JDBC driver
 * and the masked copies the JMX and bean property getters expose. A
 * {@code dbProperties} with a defaults table is a legal value for a plain
 * bean property, so the copy the driver receives must still resolve the
 * values the source resolves, and the password mask must not leave a
 * defaults-held password resolvable through the masked getter.
 */
abstract class PoolUtilitiesBase {

    @Test
    void theCopyResolvesValuesHeldOnlyInTheDefaultsTable() {
        Properties defaults = new Properties();
        defaults.setProperty("sslmode", "verify-full");
        Properties source = new Properties(defaults);
        source.setProperty(PoolUtilities.PROP_USER, "bob");

        Properties clone = PoolUtilities.clone(source);

        // Before the fix putAll(...) copied only the directly present entry,
        // so the driver never saw the defaults-held setting.
        Assertions.assertEquals("verify-full", clone.getProperty("sslmode"));
        Assertions.assertEquals("bob", clone.getProperty(PoolUtilities.PROP_USER));
        Assertions.assertEquals(source.stringPropertyNames(), clone.stringPropertyNames());
    }

    @Test
    void theCopyIsASnapshotOfTheSourceAndItsDefaults() {
        Properties defaults = new Properties();
        defaults.setProperty("sslmode", "verify-full");
        Properties source = new Properties(defaults);
        source.setProperty(PoolUtilities.PROP_USER, "bob");

        Properties clone = PoolUtilities.clone(source);
        defaults.setProperty("sslmode", "disable");
        source.setProperty(PoolUtilities.PROP_USER, "mallory");
        source.remove("sslmode");

        Assertions.assertEquals("verify-full", clone.getProperty("sslmode"));
        Assertions.assertEquals("bob", clone.getProperty(PoolUtilities.PROP_USER));
    }

    @Test
    void aDirectlyPresentValueTakesPrecedenceOverTheSameKeyInTheDefaultsTable() {
        Properties defaults = new Properties();
        defaults.setProperty("sslmode", "from-defaults");
        Properties source = new Properties(defaults);
        source.setProperty("sslmode", "direct");

        Properties clone = PoolUtilities.clone(source);

        // The materialization must not overwrite what putAll(...) already
        // copied: the directly present entry wins over the defaults entry.
        Assertions.assertEquals("direct", clone.getProperty("sslmode"));
        Assertions.assertEquals("direct", clone.get("sslmode"));
    }

    @Test
    void theCopyResolvesTheDefaultsStringBeneathANonStringDirectlyPresentValue() {
        // Properties.getProperty(...) falls through to the defaults whenever
        // the directly present value is not a String, so the source really
        // does resolve "verify-full" under this key. A containsKey(...) guard
        // in the materialization pass was blind to that: it saw the copied
        // non-String entry and skipped the key, leaving the defaults-backed
        // setting unresolvable in the tableless copy the driver receives.
        Properties defaults = new Properties();
        defaults.setProperty("sslmode", "verify-full");
        Properties source = new Properties(defaults);
        source.put("sslmode", new Object());

        Properties clone = PoolUtilities.clone(source);

        Assertions.assertEquals("verify-full", clone.getProperty("sslmode"),
                "the copy must resolve what the source resolves");
    }

    @Test
    void directlyPresentValuesAreCopiedAsTheyAre() {
        // Properties tolerates non-String values through the Hashtable API;
        // the copy keeps them verbatim rather than dropping the entry.
        Properties source = new Properties();
        Object value = new Object();
        source.put("object", value);
        source.setProperty(PoolUtilities.PROP_USER, "bob");

        Properties clone = PoolUtilities.clone(source);

        Assertions.assertSame(value, clone.get("object"));
        Assertions.assertEquals("bob", clone.getProperty(PoolUtilities.PROP_USER));
    }

    @Test
    void cloneWithoutPasswordMasksAPasswordHeldOnlyInTheDefaultsTable() {
        // A reference copy (new Properties(source)) would pass a remove(...)
        // over the defaults table and leave this password resolvable through
        // the masked getter.
        Properties defaults = new Properties();
        defaults.setProperty(PoolUtilities.PROP_PASSWORD, "s3cret");
        defaults.setProperty("sslmode", "verify-full");
        Properties source = new Properties(defaults);
        source.setProperty(PoolUtilities.PROP_USER, "bob");

        Properties masked = PoolUtilities.cloneWithoutPassword(source);

        Assertions.assertNull(masked.getProperty(PoolUtilities.PROP_PASSWORD));
        Assertions.assertFalse(masked.containsKey(PoolUtilities.PROP_PASSWORD));
        Assertions.assertEquals("verify-full", masked.getProperty("sslmode"));
        Assertions.assertEquals("bob", masked.getProperty(PoolUtilities.PROP_USER));
    }

    @Test
    void cloneWithoutPasswordMasksAResolvedPasswordBeneathANonStringDirectValue() {
        // The shape of the test above with the password also shadowed by a
        // non-String direct value: the source resolves the defaults-held
        // password through it, so the materialization must book it into the
        // copy - and the mask must then remove it with the key.
        Properties defaults = new Properties();
        defaults.setProperty(PoolUtilities.PROP_PASSWORD, "s3cret");
        Properties source = new Properties(defaults);
        source.put(PoolUtilities.PROP_PASSWORD, new Object());

        Properties masked = PoolUtilities.cloneWithoutPassword(source);

        Assertions.assertNull(masked.getProperty(PoolUtilities.PROP_PASSWORD));
        Assertions.assertFalse(masked.containsKey(PoolUtilities.PROP_PASSWORD));
    }

    @Test
    void cloneWithoutPasswordMasksEveryCredentialKeySpelling() {
        Properties source = new Properties();
        source.setProperty("sslmode", "verify-full");
        source.setProperty(PoolUtilities.PROP_PASSWORD, "secret1");
        source.setProperty("pass", "secret2");
        source.setProperty("pwd", "secret3");
        source.setProperty("PASSWORD", "secret4");
        source.setProperty(" Password ", "secret5");

        Properties masked = PoolUtilities.cloneWithoutPassword(source);

        Assertions.assertEquals(Collections.singleton("sslmode"),
                masked.stringPropertyNames(),
                "every credential-shaped key must be removed, whatever its spelling");
    }

    @Test
    void cloneWithoutPasswordMasksCredentialKeysResolvedFromDefaults() {
        Properties defaults = new Properties();
        defaults.setProperty("pass", "secret1");
        defaults.setProperty("pwd", "secret2");
        defaults.setProperty("PASSWORD", "secret3");
        defaults.setProperty("sslmode", "verify-full");
        Properties source = new Properties(defaults);

        Properties masked = PoolUtilities.cloneWithoutPassword(source);

        Assertions.assertEquals(Collections.singleton("sslmode"),
                masked.stringPropertyNames(),
                "a credential the source resolves through its defaults must not survive"
                        + " into the tableless copy");
    }

    @Test
    void maskingAThrowableFlattensEveryMessage() {
        SQLException failure = new SQLException(
                "driver failed\npassword=top-secret\rsecond line", "08001", 7);
        failure.initCause(new SQLException("cause\npassword=nested-secret"));

        Throwable masked = PoolUtilities.maskCredentials(failure);

        Assertions.assertInstanceOf(SQLException.class, masked);
        Assertions.assertEquals("08001", ((SQLException) masked).getSQLState());
        Assertions.assertEquals(7, ((SQLException) masked).getErrorCode());
        Assertions.assertFalse(masked.getMessage().contains("\n")
                || masked.getMessage().contains("\r"),
                "a throwable message must be flattened, was: " + masked.getMessage());
        Assertions.assertFalse(masked.getMessage().contains("top-secret"),
                "a credential in the throwable message must be masked, was: " + masked.getMessage());
        Assertions.assertTrue(masked.getMessage().contains("password=********"),
                "the credential must be masked in place, was: " + masked.getMessage());
        Assertions.assertNotNull(masked.getCause(), "the cause must survive the copy");
        Assertions.assertFalse(masked.getCause().getMessage().contains("\n")
                || masked.getCause().getMessage().contains("\r"),
                "the cause message must be flattened too, was: " + masked.getCause().getMessage());
        Assertions.assertFalse(masked.getCause().getMessage().contains("nested-secret"));
    }

    @Test
    void cloneWithoutPasswordMasksCredentialsEmbeddedInPropertyValues() {
        Properties source = new Properties();
        source.setProperty("url", "jdbc:mysql://user:url-secret@host/db");
        source.setProperty("logUrl", "http://admin:log-secret@host/path");
        source.setProperty("connectionString", "user=bob&password=value-secret");
        source.setProperty("applicationName", "plain-value");

        Properties masked = PoolUtilities.cloneWithoutPassword(source);

        Assertions.assertEquals("jdbc:mysql://user:********@host/db",
                masked.getProperty("url"),
                "a credential in a URL-shaped property value must be masked");
        Assertions.assertEquals("http://admin:********@host/path",
                masked.getProperty("logUrl"),
                "a credential in any URL-shaped value must be masked");
        Assertions.assertEquals("user=bob&password=********",
                masked.getProperty("connectionString"),
                "a credential pair inside a value must be masked");
        Assertions.assertEquals("plain-value", masked.getProperty("applicationName"),
                "an ordinary value must be unchanged");

        Assertions.assertEquals("jdbc:mysql://user:url-secret@host/db",
                source.getProperty("url"),
                "masking the copy must leave the source alone");
    }

    @Test
    void cloneWithoutPasswordMasksADirectlyPresentPassword() {
        Properties source = new Properties();
        source.setProperty(PoolUtilities.PROP_USER, "bob");
        source.setProperty(PoolUtilities.PROP_PASSWORD, "s3cret");

        Properties masked = PoolUtilities.cloneWithoutPassword(source);

        Assertions.assertFalse(masked.containsKey(PoolUtilities.PROP_PASSWORD));
        Assertions.assertEquals("bob", masked.getProperty(PoolUtilities.PROP_USER));
        Assertions.assertEquals("s3cret",
                source.getProperty(PoolUtilities.PROP_PASSWORD),
                "masking the copy must leave the source alone");
    }
}

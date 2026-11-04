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
 * Unit tests for {@link PoolUtilities}, the helpers that build the
 * {@link Properties} object {@link PooledConnection} hands to the JDBC driver
 * and the masked copies the JMX and bean property getters expose. A
 * {@code dbProperties} with a defaults table is a legal value for a plain
 * bean property, so the copy the driver receives must still resolve the
 * values the source resolves, and the password mask must not leave a
 * defaults-held password resolvable through the masked getter.
 */
class TestPoolUtilities {

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

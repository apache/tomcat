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

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Configuration only, no database and no pool: {@code PoolProperties}
 * renders itself for logging and diagnostics. The rendering must not leak
 * the password, and it must stay parseable - the legacy suite checked that
 * with a bracket balance, because a truncated or unbalanced dump is what
 * a broken {@code toString()} produces.
 *
 * <p>Ported from the retired legacy {@code PoolPropertiesTest}.</p>
 */
class TestPoolProperties {

    private static final String DEFAULT_USER = "username_def";
    private static final String DEFAULT_PASSWD = "password_def";

    @Test
    void toStringOutputShouldHaveBalancedBrackets() {
        PoolProperties properties = new PoolProperties();
        properties.setUsername(DEFAULT_USER);
        properties.setPassword(DEFAULT_PASSWD);
        properties.setAlternateUsernameAllowed(true);
        properties.setInitialSize(0);
        properties.setRemoveAbandoned(false);
        properties.setTimeBetweenEvictionRunsMillis(-1);

        String asString = properties.toString();

        List<Character> stack = new ArrayList<>();
        for (char c : asString.toCharArray()) {
            switch (c) {
                case '{':
                case '(':
                case '[':
                    stack.add(Character.valueOf(c));
                    break;
                case '}':
                    Assertions.assertEquals('{', stack.remove(stack.size() - 1).charValue());
                    break;
                case ')':
                    Assertions.assertEquals('(', stack.remove(stack.size() - 1).charValue());
                    break;
                case ']':
                    Assertions.assertEquals('[', stack.remove(stack.size() - 1).charValue());
                    break;
                default:
                    break;
            }
        }
        Assertions.assertEquals(0, stack.size(), "All brackets should have been closed");
    }

    @Test
    void poolNameIsAvailableBeforeThePoolIsCreatedAndAfterClose() {
        PoolProperties properties = new PoolProperties();
        properties.setName("test-pool");
        DataSourceProxy proxy = new DataSourceProxy(properties);

        Assertions.assertEquals("test-pool", proxy.getPoolName(),
                "getPoolName() must not require a running pool");
        Assertions.assertEquals(proxy.getName(), proxy.getPoolName(),
                "getPoolName() and getName() must agree");

        proxy.close();

        Assertions.assertEquals("test-pool", proxy.getPoolName(),
                "getPoolName() must still work after close()");
    }

    @Test
    void setConnectionPropertiesRoundTripsThroughTheProxy() {
        PoolProperties properties = new PoolProperties();
        DataSourceProxy proxy = new DataSourceProxy(properties);

        proxy.setConnectionProperties("first=one;second=two");

        Assertions.assertEquals("first=one;second=two", proxy.getConnectionProperties(),
                "the proxy setter must store the raw string like every other "
                        + "implementation of the interface does");
        // the same call must keep parsing the pairs into the driver
        // properties the pool connects with
        Assertions.assertEquals("one", properties.getDbProperties().getProperty("first"),
                "the parsed pairs must still reach the database properties");
        Assertions.assertEquals("two", properties.getDbProperties().getProperty("second"),
                "the parsed pairs must still reach the database properties");

        proxy.setConnectionProperties(null);
        Assertions.assertNull(proxy.getConnectionProperties(),
                "clearing the configuration must round-trip as well");
    }

    @Test
    @SuppressWarnings("deprecation")
    void theDeprecatedFacadePropertyIsStillStoredWhenConfigured() {
        Properties attrs = new Properties();
        attrs.setProperty("useDisposableConnectionFacade", "false");

        PoolConfiguration configured =
                DataSourceFactory.parsePoolProperties(attrs);

        // The property has no effect any more, but existing configuration
        // must keep loading and keep reading back as it was written.
        Assertions.assertFalse(configured.getUseDisposableConnectionFacade(),
                "the deprecated value must still round-trip through the "
                        + "factory path");
    }

    @Test
    void setConnectionPropertiesRoundTripsThroughTheFactory() {
        Properties attrs = new Properties();
        attrs.setProperty("connectionProperties", "first=one;second=two");

        PoolConfiguration configured =
                DataSourceFactory.parsePoolProperties(attrs);

        Assertions.assertEquals("first=one;second=two", configured.getConnectionProperties(),
                "the factory/JNDI path must store the raw string like every "
                        + "other implementation of the interface does");
        // the pairs must still reach the driver properties the pool connects with
        Assertions.assertEquals("one", configured.getDbProperties().getProperty("first"),
                "the parsed pairs must still reach the database properties");
        Assertions.assertEquals("two", configured.getDbProperties().getProperty("second"),
                "the parsed pairs must still reach the database properties");

        PoolConfiguration without =
                DataSourceFactory.parsePoolProperties(new Properties());
        Assertions.assertNull(without.getConnectionProperties(),
                "an unconfigured attribute must read back null");
        Assertions.assertTrue(without.getDbProperties().isEmpty(),
                "an unconfigured attribute must leave the driver properties empty");
    }

    @Test
    void nonAsciiValuesInConnectionPropertiesSurviveParsing() {
        // The escaped e-acute and a-ring below are multi-byte under the UTF-8
        // default charset every supported JVM ships with, while
        // Properties.load(InputStream) decodes as ISO-8859-1; parsing must
        // hand the driver exactly the characters the configuration named,
        // whatever the platform default charset is. Unicode escapes keep
        // this source file 7-bit for the ISO-8859-1 Ant build.
        PoolProperties properties = new PoolProperties();
        properties.setConnectionProperties("clientInfo=caf\u00e9;clientLocale=de_DE");

        Assertions.assertEquals("caf\u00e9", properties.getDbProperties().getProperty("clientInfo"),
                "a non-ASCII value must reach the driver properties uncorrupted");
        Assertions.assertEquals("de_DE", properties.getDbProperties().getProperty("clientLocale"),
                "the pairs around it must still parse");

        // the static helper the JNDI factory routes through
        Properties parsed = PoolProperties.getProperties("foo=b\u00e5r", null);
        Assertions.assertEquals("b\u00e5r", parsed.getProperty("foo"),
                "a non-ASCII value must survive the factory path as well");
    }

    @Test
    void toStringMasksThePasswordButStillShowsTheConfiguration() {
        PoolProperties properties = new PoolProperties();
        properties.setUsername(DEFAULT_USER);
        properties.setPassword(DEFAULT_PASSWD);
        properties.setUrl("jdbc:h2:mem:shown");

        String asString = properties.toString();

        Assertions.assertTrue(asString.contains("password=********"),
                "the password must be masked in the dump, was: " + asString);
        Assertions.assertFalse(asString.contains(DEFAULT_PASSWD),
                "the cleartext password must not appear anywhere in the dump");
        Assertions.assertTrue(asString.contains(DEFAULT_USER),
                "the non-secret configuration must still be readable, was: " + asString);
        Assertions.assertTrue(asString.contains("jdbc:h2:mem:shown"),
                "the JDBC URL must stay readable, was: " + asString);
    }
}

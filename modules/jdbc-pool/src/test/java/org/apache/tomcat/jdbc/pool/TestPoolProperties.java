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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.pool.util.MessageFactory;

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
    @SuppressWarnings("deprecation")
    void theDeprecatedStatementFacadePropertyIsStillStoredWhenConfigured() {
        Properties attrs = new Properties();
        attrs.setProperty("useStatementFacade", "false");

        PoolConfiguration configured =
                DataSourceFactory.parsePoolProperties(attrs);

        // The property has no effect any more, but existing configuration
        // must keep loading and keep reading back as it was written.
        Assertions.assertFalse(configured.getUseStatementFacade(),
                "the deprecated value must still round-trip through the "
                        + "factory path");
    }

    @Test
    void toStringDoesNotPrintTheConfiguredDataSourceObject() {
        PoolProperties properties = new PoolProperties();
        Object dataSource = new Object() {
            @Override
            public String toString() {
                return "jdbc:vendor://host/db?user=sa&password=datasource-secret";
            }
        };
        properties.setDataSource(dataSource);

        String dump = properties.toString();

        Assertions.assertFalse(dump.contains("datasource-secret"),
                "the dump must not call the configured data source object's toString()");
        Assertions.assertTrue(dump.contains("dataSource=" + dataSource.getClass().getName()),
                "the dump must still identify the configured data source by class");
    }

    @Test
    void getJdbcInterceptorsAsArrayReturnsACopy() {
        PoolProperties properties = new PoolProperties();
        properties.setJdbcInterceptors("ResetAbandonedTimer");

        PoolProperties.InterceptorDefinition[] first = properties.getJdbcInterceptorsAsArray();
        Assertions.assertTrue(first.length > 0, "precondition: the definition must parse");
        first[0] = null;

        PoolProperties.InterceptorDefinition[] second = properties.getJdbcInterceptorsAsArray();
        Assertions.assertNotSame(first, second,
                "each call must return its own array");
        Assertions.assertNotNull(second[0],
                "mutating the returned array must not change the cached definitions");
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

        Properties blankAttrs = new Properties();
        blankAttrs.setProperty("connectionProperties", "   ");
        PoolConfiguration blank =
                DataSourceFactory.parsePoolProperties(blankAttrs);
        Assertions.assertNull(blank.getConnectionProperties(),
                "a blank attribute must read back the default null, not an empty string");
        Assertions.assertTrue(blank.getDbProperties().isEmpty(),
                "a blank attribute must leave the driver properties empty");
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
    void reconfiguredConnectionPropertiesDropTheKeysTheStringDropped() {
        PoolProperties properties = new PoolProperties();
        // the credential injection DataSourceFactory.parsePoolProperties does
        // around the connectionProperties setter: these entries must survive
        properties.getDbProperties().setProperty("user", DEFAULT_USER);
        properties.getDbProperties().setProperty("password", DEFAULT_PASSWD);

        properties.setConnectionProperties("keep=yes;stale=leftover");
        properties.setConnectionProperties("keep=yes");

        Assertions.assertEquals("yes", properties.getDbProperties().getProperty("keep"),
                "a pair the new value still names must carry the new value");
        Assertions.assertNull(properties.getDbProperties().getProperty("stale"),
                "a pair dropped by the new value must not keep being sent to"
                        + " the driver for the life of the pool");
        Assertions.assertEquals(DEFAULT_USER, properties.getDbProperties().getProperty("user"),
                "a credential injected outside the string must survive reconfiguration");
        Assertions.assertEquals(DEFAULT_PASSWD, properties.getDbProperties().getProperty("password"),
                "a credential injected outside the string must survive reconfiguration");
    }

    @Test
    void credentialsOverridingTheStringSurviveAReconfigureThatDropsThem() {
        // The factory's own shape: the string names user/password, and the
        // username/password attributes are injected over the string's pairs.
        // Pre-fix the injection overwrote the values but the string stayed the
        // owner of the keys, so a JMX reconfigure whose string no longer named
        // the credentials removed them from the driver properties while the
        // credential fields still held them.
        Properties attrs = new Properties();
        attrs.setProperty("connectionProperties",
                "keep=yes;user=fromstring;password=fromstring");
        attrs.setProperty("username", DEFAULT_USER);
        attrs.setProperty("password", DEFAULT_PASSWD);

        PoolProperties configured =
                (PoolProperties) DataSourceFactory.parsePoolProperties(attrs);

        Assertions.assertEquals(DEFAULT_USER,
                configured.getDbProperties().getProperty("user"),
                "the configured username must override the string's pair");
        Assertions.assertEquals(DEFAULT_PASSWD,
                configured.getDbProperties().getProperty("password"),
                "the configured password must override the string's pair");

        // the JMX reconfigure path
        configured.setConnectionProperties("keep=again");

        Assertions.assertEquals("again",
                configured.getDbProperties().getProperty("keep"),
                "a pair the new string names must carry the new value");
        Assertions.assertEquals(DEFAULT_USER,
                configured.getDbProperties().getProperty("user"),
                "a credential the fields still carry must not be stripped by a"
                        + " reconfigure that dropped the key from the string");
        Assertions.assertEquals(DEFAULT_PASSWD,
                configured.getDbProperties().getProperty("password"),
                "a credential the fields still carry must not be stripped by a"
                        + " reconfigure that dropped the key from the string");
    }

    @Test
    void aCredentialEntryOnlyTheStringCarriesStaysTheStringsOwn() {
        // Counterpart pin: with the field unset the entry is the string's, so
        // the reconfigure really does drop it - the re-assert must not turn
        // setConnectionProperties into a credential keeper that outlives both
        // the field and the string.
        PoolProperties properties = new PoolProperties();
        properties.setConnectionProperties("user=fromstring");

        Assertions.assertEquals("fromstring",
                properties.getDbProperties().getProperty("user"),
                "the string's pair must be applied while no username is set");

        properties.setConnectionProperties("keep=yes");

        Assertions.assertNull(properties.getDbProperties().getProperty("user"),
                "the string must keep owning the entry while the field is unset");
    }

    @Test
    void clearingTheFieldAlsoStripsTheEntryTheStringContributes() {
        PoolProperties properties = new PoolProperties();
        properties.setConnectionProperties("password=fromstring");
        properties.setPassword("fromfield");

        properties.setPassword(null);

        Assertions.assertNull(properties.getDbProperties().getProperty("password"),
                "clearing the credential must stop it authenticating connections,"
                        + " whatever surface contributed the entry");
        Assertions.assertEquals("password=fromstring", properties.getConnectionProperties(),
                "the string is configuration and is not rewritten by a credential clear");
    }

    @Test
    void clearingCredentialsReachesValuesSuppliedByADefaultsTable() {
        Properties defaults = new Properties();
        defaults.setProperty(PoolUtilities.PROP_USER, DEFAULT_USER);
        defaults.setProperty(PoolUtilities.PROP_PASSWORD, DEFAULT_PASSWD);
        PoolProperties properties = new PoolProperties();
        properties.setDbProperties(new Properties(defaults));

        properties.setPassword(null);
        properties.setUsername(null);

        Assertions.assertNull(properties.getDbProperties().getProperty(PoolUtilities.PROP_PASSWORD),
                "clearing the password must remove a value a defaults table supplies");
        Assertions.assertNull(properties.getDbProperties().getProperty(PoolUtilities.PROP_USER),
                "clearing the user name must remove a value a defaults table supplies");
        Assertions.assertNull(
                PoolUtilities.clone(properties.getDbProperties()).getProperty(PoolUtilities.PROP_PASSWORD),
                "the copy handed to the driver must not materialize the cleared password");
        Assertions.assertNull(
                PoolUtilities.clone(properties.getDbProperties()).getProperty(PoolUtilities.PROP_USER),
                "the copy handed to the driver must not materialize the cleared user name");
    }

    @Test
    void droppingAConnectionPropertyReachesADefaultsTable() {
        Properties defaults = new Properties();
        defaults.setProperty("sslmode", "require");
        PoolProperties properties = new PoolProperties();
        properties.setDbProperties(new Properties(defaults));

        properties.setConnectionProperties("sslmode=disable");
        Assertions.assertEquals("disable",
                PoolUtilities.clone(properties.getDbProperties()).getProperty("sslmode"),
                "the string's pair must shadow the defaults value");

        properties.setConnectionProperties(null);

        Assertions.assertNull(properties.getDbProperties().getProperty("sslmode"),
                "clearing the string must remove the pair it contributed");
        Assertions.assertNull(PoolUtilities.clone(properties.getDbProperties()).getProperty("sslmode"),
                "the dropped pair must not fall back to the defaults value the driver copy would send");
    }

    @Test
    void rewritingTheStringAppliesItsCredentialPairAgain() {
        PoolProperties properties = new PoolProperties();
        properties.setConnectionProperties("password=fromstring");

        properties.setPassword(null);
        properties.setConnectionProperties("password=fromstring");

        Assertions.assertEquals("fromstring", properties.getDbProperties().getProperty("password"),
                "re-declaring the string re-declares its pair - retiring the credential"
                        + " means taking it out of the string");
    }

    @Test
    void aRejectedConnectionPropertiesStringLeavesTheConfigurationIntact() {
        PoolProperties properties = new PoolProperties();
        properties.setConnectionProperties("keep=yes;stale=x");

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> properties.setConnectionProperties("good=1;bad=\\uZZ"),
                "a malformed unicode escape must be rejected");

        Assertions.assertEquals("keep=yes;stale=x", properties.getConnectionProperties(),
                "the rejected string must not have become the configured one");
        Assertions.assertEquals("yes", properties.getDbProperties().getProperty("keep"),
                "the pairs the previous string contributed must still be there");
        Assertions.assertEquals("x", properties.getDbProperties().getProperty("stale"),
                "the pairs the previous string contributed must still be there");

        // The tracking must not have been lost either: a reconfigure after the
        // rejected write must still remove the pairs of the string that really
        // is configured.
        properties.setConnectionProperties("only=new");

        Assertions.assertEquals("new", properties.getDbProperties().getProperty("only"));
        Assertions.assertNull(properties.getDbProperties().getProperty("keep"),
                "a reconfigure after a rejected write must remove the old pairs");
        Assertions.assertNull(properties.getDbProperties().getProperty("stale"),
                "a reconfigure after a rejected write must remove the old pairs");
    }

    @Test
    void clearedConnectionPropertiesRemoveEveryParsedPair() {
        PoolProperties properties = new PoolProperties();
        properties.setConnectionProperties("stale=leftover");

        properties.setConnectionProperties(null);

        Assertions.assertNull(properties.getConnectionProperties(),
                "clearing the configuration must still round-trip as null");
        Assertions.assertTrue(properties.getDbProperties().isEmpty(),
                "clearing the string must remove the pairs it contributed");

        properties.setConnectionProperties("first=one;second=two");
        properties.setConnectionProperties("");
        Assertions.assertTrue(properties.getDbProperties().isEmpty(),
                "an empty string must clear the pairs just like a null one does");
    }

    @Test
    void aNamelessInterceptorPropertyIsRefusedAtOnce() {
        NullPointerException npe = Assertions.assertThrows(NullPointerException.class,
                () -> new PoolProperties.InterceptorProperty(null, "value"),
                "the name keys the property in the definitions' map, so the "
                        + "constructor must refuse null instead of letting the "
                        + "map's hashing fail later");
        Assertions.assertEquals(
                MessageFactory.get("poolProperties.interceptorPropertyNameRequired"),
                npe.getMessage(), "the refusal must carry the localized message");
    }

    @Test
    void aCloneCopiesTheDriverPropertiesCollection() throws Exception {
        PoolProperties original = new PoolProperties();
        original.setConnectionProperties("keep=yes;stale=x");

        PoolProperties clone = (PoolProperties) original.clone();

        clone.setConnectionProperties("only=clone");

        Assertions.assertEquals("yes", original.getDbProperties().getProperty("keep"),
                "a reconfigure of the copy must not strip the original's pairs");
        Assertions.assertEquals("x", original.getDbProperties().getProperty("stale"),
                "a reconfigure of the copy must not strip the original's pairs");
        Assertions.assertEquals("clone", clone.getDbProperties().getProperty("only"));

        original.setConnectionProperties(null);

        Assertions.assertEquals("clone", clone.getDbProperties().getProperty("only"),
                "and the other way around: the copy keeps what it configured");
    }

    @Test
    void aCloneResolvesPropertiesDefaultsLikeTheOriginal() throws Exception {
        Properties defaults = new Properties();
        defaults.setProperty("socketTimeout", "1000");
        Properties dbProperties = new Properties(defaults);
        dbProperties.setProperty("keep", "yes");

        PoolProperties original = new PoolProperties();
        original.setDbProperties(dbProperties);

        PoolProperties clone = (PoolProperties) original.clone();

        Assertions.assertEquals("1000", clone.getDbProperties().getProperty("socketTimeout"),
                "a property the original resolves through its defaults table "
                        + "must resolve on the copy too, as it does on the connect path");
        Assertions.assertEquals("yes", clone.getDbProperties().getProperty("keep"));
    }

    @Test
    void aThrowingPropertyFailsOnlyItsOwnFieldOfTheDump() {
        PoolProperties properties = new PoolProperties() {
            private static final long serialVersionUID = 1L;

            @Override
            public String getUrl() {
                throw new IllegalStateException("hostile getter");
            }
        };
        properties.setDataSourceJNDI("java:comp/env/jdbc/test");

        String asString = properties.toString();

        Assertions.assertTrue(asString.endsWith("]"),
                "one throwing property must not truncate the dump, was: " + asString);
        Assertions.assertTrue(asString.contains("url=<error:"),
                "the failing property must be reported in its own field, was: " + asString);
        // dataSourceJNDI is listed after url, so it is lost if the failure
        // still truncates the dump.
        Assertions.assertTrue(asString.contains("dataSourceJNDI"),
                "the properties must keep being listed after the failing one, was: " + asString);
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

    @Test
    void toStringPrintsAnUnsetPasswordAsNullNotAsTheMask() {
        // The mask hides a secret; it should not also pretend one is there.
        // An unset password prints the way every other unset property does.
        PoolProperties properties = new PoolProperties();
        properties.setUrl("jdbc:h2:mem:shown");

        String asString = properties.toString();

        Assertions.assertTrue(asString.contains("password=null"),
                "an unset password must say null, was: " + asString);
    }

    @Test
    void toStringMasksAPasswordEmbeddedInTheConnectionProperties() {
        PoolProperties properties = new PoolProperties();
        properties.setConnectionProperties("socketTimeout=1000;password=inline-secret;PWD=Shouted-Secret");

        String asString = properties.toString();

        Assertions.assertFalse(asString.contains("inline-secret"),
                "the password embedded in the string must not leak, was: " + asString);
        Assertions.assertFalse(asString.contains("Shouted-Secret"),
                "the key must be recognised ignoring case, was: " + asString);
        Assertions.assertTrue(asString.contains("connectionProperties=socketTimeout=1000;password=********;PWD=********"),
                "the other pair must keep printing with the pair masked, was: " + asString);
    }

    @Test
    void toStringMasksAPasswordParameterInTheUrl() {
        PoolProperties properties = new PoolProperties();
        properties.setUrl("jdbc:mysql://localhost:3306/db?user=me&password=url-secret&sessionVariables=foo=bar&ssl=true");

        String asString = properties.toString();

        Assertions.assertFalse(asString.contains("url-secret"),
                "the password in the URL query must not leak, was: " + asString);
        Assertions.assertTrue(asString.contains("url=jdbc:mysql://localhost:3306/db?user=me"
                + "&password=********&sessionVariables=foo=bar&ssl=true"),
                "the rest of the URL must stay readable, was: " + asString);
    }

    @Test
    void toStringMasksAPasswordInTheUrlUserinfo() {
        PoolProperties properties = new PoolProperties();
        properties.setUrl("jdbc:postgresql://user:inline-secret@host:5432/db");

        String asString = properties.toString();

        Assertions.assertFalse(asString.contains("inline-secret"),
                "the password in the URL userinfo must not leak, was: " + asString);
        Assertions.assertTrue(asString.contains("url=jdbc:postgresql://user:********@host:5432/db"),
                "the user, host and port must stay readable, was: " + asString);
    }

    @Test
    void toStringMasksAPasswordInTheLegacyOracleUserinfo() {
        PoolProperties properties = new PoolProperties();
        properties.setUrl("jdbc:oracle:thin:user/inline-secret@h:1521:ORCL");

        String asString = properties.toString();

        Assertions.assertFalse(asString.contains("inline-secret"),
                "the password in the legacy Oracle userinfo must not leak, was: " + asString);
        Assertions.assertTrue(asString.contains("url=jdbc:oracle:thin:user/********@h:1521:ORCL"),
                "the user and connect string must stay readable, was: " + asString);
    }

    @Test
    void toStringMasksALegacyOraclePasswordContainingAnAtSign() {
        // An unencoded '@' inside the password can only extend the credential,
        // exactly as in the scheme:// userinfo, so the credential runs to the
        // last '@' of the section - masking to the first one printed the tail
        // of the password in cleartext.
        PoolProperties properties = new PoolProperties();
        properties.setUrl("jdbc:oracle:thin:scott/se@cret1@dbhost:1521:ORCL");

        String asString = properties.toString();

        Assertions.assertFalse(asString.contains("cret1"),
                "the tail of the password past the '@' must not leak, was: " + asString);
        Assertions.assertTrue(asString.contains("url=jdbc:oracle:thin:scott/********@dbhost:1521:ORCL"),
                "the user and connect string must stay readable, was: " + asString);
    }

    @Test
    void toStringLeavesUrlAuthoritiesWithoutAUserinfoPasswordUntouched() {
        String[] urls = {
                "jdbc:mysql://host:3306/db",
                "jdbc:postgresql://user@host/db",
                "jdbc:sqlserver://host:1433;databaseName=x",
                "jdbc:oracle:thin:@h:1521:ORCL",
                // the at-sign-only forms carry no credential, whatever '@' a
                // later part of the connect string may hold
                "jdbc:oracle:thin:@//h:1521/svc@corp",
                "jdbc:postgresql://host/db?mail=admin@corp.com",
        };
        for (String url : urls) {
            PoolProperties properties = new PoolProperties();
            properties.setUrl(url);

            String asString = properties.toString();

            Assertions.assertTrue(asString.contains("url=" + url),
                    "an authority without a credential separator must come out unchanged: "
                            + url + ", was: " + asString);
        }
    }

    @Test
    void toStringMasksPairsSeparatedByNewLinesInTheConnectionProperties() {
        // The parser that applies the string splits pairs on new lines too - a
        // .properties source expands \n into a real one - so the masking has to
        // honour the same segmentation or a pair the driver really gets prints
        // inside the previous value.
        PoolProperties properties = new PoolProperties();
        properties.setConnectionProperties("socketTimeout=1000\nuser=app\npassword=multiline-secret");

        String asString = properties.toString();

        Assertions.assertFalse(asString.contains("multiline-secret"),
                "the pair behind a new line must not leak, was: " + asString);
        Assertions.assertTrue(asString.contains("password=********"),
                "the new-line separated pair must be masked, was: " + asString);
    }

    @Test
    void toStringMasksTheWholeDeliveredValueOfACredentialPairInTheConnectionProperties() {
        // The properties parser that applies the string delivers everything up
        // to the next semicolon or new line as the pair's value, so an '&' or
        // '?' inside it is part of the password the driver receives - masking
        // only up to it printed the tail of the real password in cleartext.
        PoolProperties properties = new PoolProperties();
        properties.setConnectionProperties("timeout=5;password=pas&sW0rd;x=1");

        String asString = properties.toString();

        Assertions.assertFalse(asString.contains("sW0rd"),
                "the tail of the delivered password past the '&' must not leak, was: " + asString);
        Assertions.assertTrue(asString.contains("connectionProperties=timeout=5;password=********;x=1"),
                "the pairs on either side of the credential must stay readable, was: " + asString);
    }

    @Test
    void toStringMasksAQueryPairInsideAUrlShapedValueOfTheConnectionProperties() {
        // The properties parser delivers the URL as one value, but a driver
        // reading that value as a URL splits its query at the '?', so a pair
        // behind the '?' keeps its fine segmentation and the value around it
        // stays readable.
        PoolProperties properties = new PoolProperties();
        properties.setConnectionProperties("logUrl=http://h/db?password=queryvalue;x=1");

        String asString = properties.toString();

        Assertions.assertFalse(asString.contains("queryvalue"),
                "the query pair of the URL-shaped value must not leak, was: " + asString);
        Assertions.assertTrue(asString.contains("connectionProperties=logUrl=http://h/db?password=********;x=1"),
                "the URL around the query pair must stay readable, was: " + asString);
    }

    @Test
    void toStringMasksEveryAuthorityInAStringNotJustTheFirst() {
        PoolProperties properties = new PoolProperties();
        // A failover URL in a query value and a logging URL in a pair are as
        // credential-bearing as the URL in front of them.
        properties.setUrl("jdbc:db://host:5432/db?fallback=jdbc:db://backup:fallback-secret@h2:5432/db");
        properties.setConnectionProperties("ping=http://a/b;logUrl=http://admin:logging-secret@h/x");

        String asString = properties.toString();

        Assertions.assertFalse(asString.contains("fallback-secret"),
                "the second authority of the URL must not leak, was: " + asString);
        Assertions.assertFalse(asString.contains("logging-secret"),
                "the second authority of the pairs must not leak, was: " + asString);
        Assertions.assertTrue(asString.contains("url=jdbc:db://host:5432/db?fallback=jdbc:db://backup:********@h2:5432/db"),
                "the rest of both URLs must stay readable, was: " + asString);
        Assertions.assertTrue(asString.contains("connectionProperties=ping=http://a/b;logUrl=http://admin:********@h/x"),
                "the rest of the pairs must stay readable, was: " + asString);
    }

    @Test
    void toStringMasksAQueryPairHidingBehindAnEarlierValue() {
        PoolProperties properties = new PoolProperties();
        properties.setUrl("jdbc:postgresql://host/db=seg?password=question-secret");

        String asString = properties.toString();

        Assertions.assertFalse(asString.contains("question-secret"),
                "a pair after a '?' must not hide inside the earlier value, was: " + asString);
        Assertions.assertTrue(asString.contains("url=jdbc:postgresql://host/db=seg?password=********"),
                "the rest of the URL must stay readable, was: " + asString);
    }

    @Test
    void toStringMasksCredentialPairsWrittenWithThePropertiesSeparators() {
        // The parser that applies the string reads pairs whose key is followed
        // by a colon or a space, so those forms really deliver a password and
        // must be masked like the equals form.
        PoolProperties properties = new PoolProperties();
        properties.setConnectionProperties("timeout:5;password:colon-secret;pass spaceSecret");

        String asString = properties.toString();

        Assertions.assertFalse(asString.contains("colon-secret"),
                "the colon pair must not leak, was: " + asString);
        Assertions.assertFalse(asString.contains("spaceSecret"),
                "the space pair must not leak, was: " + asString);
        Assertions.assertTrue(asString.contains("connectionProperties=timeout:5;password:********;pass ********"),
                "the keys stay visible and the non-credential pair untouched, was: " + asString);
    }

    @Test
    void toStringMasksCredentialPairsWrittenWithTheFormFeedSeparator() {
        // The reader the parser runs treats a form feed as white space too:
        // it terminates the key and it is skipped as leading white space
        // before one, so both spellings really deliver a password and must be
        // masked like the equals, colon, space and tab forms.
        String[][] forms = {
            { "password\u000CsecretFF", "secretFF" },
            { "\u000Cpassword=secretFF2", "secretFF2" },
        };
        for (String[] form : forms) {
            PoolProperties properties = new PoolProperties();
            properties.setConnectionProperties(form[0]);

            Assertions.assertEquals(form[1], properties.getDbProperties().getProperty("password"),
                    "the parser really delivers the value, so the dump has to mask it: " + form[0]);

            String asString = properties.toString();

            Assertions.assertFalse(asString.contains(form[1]),
                    "the form feed pair must not leak, was: " + asString);
        }
    }

    @Test
    void toStringLeavesUrlsAndValuesWithColonsAndSpacesAlone() {
        String[] connectionProperties = {
                // the scheme, host:port and database-name colons of URLs,
                "url=jdbc:h2:mem:pass;host=localhost:5432",
                // and a value that merely mentions a credential word.
                "note=the password policy applies",
        };
        for (String cp : connectionProperties) {
            PoolProperties properties = new PoolProperties();
            properties.setConnectionProperties(cp);

            String asString = properties.toString();

            Assertions.assertTrue(asString.contains("connectionProperties=" + cp),
                    "only a credential key at a segment start may be masked: "
                            + cp + ", was: " + asString);
        }
    }

    @Test
    void toStringMasksPairsWrittenWithThePropertiesSeparatorsInUrlQueries() {
        PoolProperties properties = new PoolProperties();
        properties.setUrl("jdbc:mysql://h:3306/db?user=me&password=colon-query-secret&x=1");

        String asString = properties.toString();

        Assertions.assertFalse(asString.contains("colon-query-secret"),
                "the query pair must not leak, was: " + asString);
    }

    @Test
    void toStringKeepsNonCredentialPairsInsideRawStringsUntouched() {
        PoolProperties properties = new PoolProperties();
        properties.setUrl("jdbc:h2:mem:shown");
        properties.setConnectionProperties("socketTimeout=1000;mypassword=not-the-key;passwordfile=/etc/pw");

        String asString = properties.toString();

        Assertions.assertTrue(asString.contains("connectionProperties=socketTimeout=1000;"
                        + "mypassword=not-the-key;passwordfile=/etc/pw"),
                "only the whole keys password, pass and pwd may be masked, was: " + asString);
        Assertions.assertTrue(asString.contains("url=jdbc:h2:mem:shown"),
                "a URL without an equals sign must come out unchanged, was: " + asString);
    }

    @Test
    void toStringMasksACredentialPropertyOfAnInterceptor() {
        // An interceptor takes its properties - among them any credential it
        // needs - out of this very string, so the dump must mask them as it
        // masks the pair forms of the other configuration strings.
        PoolProperties properties = new PoolProperties();
        properties.setJdbcInterceptors("ResetAbandonedTimer;custom.MyInterceptor("
                + "user=bob,password=interceptor-secret,PWD=Shouted-Secret)");

        String asString = properties.toString();

        Assertions.assertFalse(asString.contains("interceptor-secret"),
                "the interceptor password must not leak, was: " + asString);
        Assertions.assertFalse(asString.contains("Shouted-Secret"),
                "the key must be recognised ignoring case, was: " + asString);
        Assertions.assertTrue(asString.contains("jdbcInterceptors=ResetAbandonedTimer;"
                        + "custom.MyInterceptor(user=bob,password=********,PWD=********)"),
                "the class name and the other properties must stay readable, was: " + asString);
    }

    @Test
    void toStringMasksAnInterceptorCredentialInANestedPropertyList() {
        PoolProperties properties = new PoolProperties();
        properties.setJdbcInterceptors(
                "StatementCache(foo=(password=interceptor-secret),pwd=Shouted-Secret)");

        String asString = properties.toString();

        Assertions.assertFalse(asString.contains("interceptor-secret"),
                "a credential nested inside another property list must not leak, was: " + asString);
        Assertions.assertFalse(asString.contains("Shouted-Secret"),
                "the pair after the nested list must still be masked, was: " + asString);
        Assertions.assertTrue(asString.contains(
                        "StatementCache(foo=(password=********),pwd=********)"),
                "the surrounding structure must stay readable, was: " + asString);
    }

    @Test
    void toStringMasksAnInterceptorCredentialWrittenAroundSpacesAndLeavesOtherPairsAlone() {
        PoolProperties properties = new PoolProperties();
        properties.setJdbcInterceptors("SlowQueryReport(threshold=1000, Password = spaced-secret );"
                + "custom.X(name=passwordish,user=pass)");

        String asString = properties.toString();

        Assertions.assertFalse(asString.contains("spaced-secret"),
                "the name is the text before the first =, trimmed, was: " + asString);
        Assertions.assertTrue(asString.contains("custom.X(name=passwordish,user=pass)"),
                "only a whole property name may be masked, not a value that "
                        + "merely looks like a credential: " + asString);
    }

    @Test
    void cloneNeverCopiesATornInterceptorChain() throws Exception {
        // A setter publishes a reconfiguration over several field writes under
        // the configuration's monitor. A clone that skipped that monitor could
        // land between the writes and copy a fresh interceptor string together
        // with the previous string's parse cache - a chain the copy would then
        // never re-parse. Cloning under the monitor makes the mismatch
        // impossible, so this loop never observes one; it fails only if the
        // synchronization is ever taken back off.
        final String chainA = "org.apache.tomcat.jdbc.pool.interceptor.ResetAbandonedTimer";
        final String chainB = "org.apache.tomcat.jdbc.pool.interceptor.StatementFinalizer";
        for (int iter = 0; iter < 2000; iter++) {
            final PoolProperties properties = new PoolProperties();
            properties.setJdbcInterceptors(chainA);
            properties.getJdbcInterceptorsAsArray();
            final Thread reconfiguring = new Thread(() -> properties.setJdbcInterceptors(chainB));
            reconfiguring.start();
            PoolProperties copy = (PoolProperties) properties.clone();
            reconfiguring.join();
            String reported = copy.getJdbcInterceptors();
            String cached = copy.getJdbcInterceptorsAsArray()[1].getClassName();
            Assertions.assertEquals(reported, cached,
                    "the clone reported one interceptor string while its chain came from another");
        }
    }

    @Test
    void serializationNeverCarriesAnUntrackedDriverProperty() throws Exception {
        // The same multi-field publish, seen from the stream: a field-by-field
        // serialization that lands between the writes hands the deserialized
        // copy a driver-property entry that its own tracked key set never
        // removes again, so a retired credential would survive every later
        // reconfigure. After a reconfigure on the copy, every entry the string
        // contributed must be gone; a leftover is a torn snapshot.
        StringBuilder first = new StringBuilder(), second = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            if (i > 0) {
                first.append(';');
                second.append(';');
            }
            first.append("y").append(i).append("=1");
            second.append("z").append(i).append("=2");
        }
        final String keysFirst = first.toString(), keysSecond = second.toString();
        for (int iter = 0; iter < 500; iter++) {
            final PoolProperties properties = new PoolProperties();
            properties.setConnectionProperties(keysFirst);
            final Thread reconfiguring = new Thread(() -> properties.setConnectionProperties(keysSecond));
            reconfiguring.start();
            PoolProperties copy;
            try (java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                    java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(bytes)) {
                out.writeObject(properties);
                out.flush();
                try (java.io.ObjectInputStream in =
                        new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
                    copy = (PoolProperties) in.readObject();
                }
            }
            reconfiguring.join();
            copy.setConnectionProperties("q=1");
            Assertions.assertEquals("1", copy.getDbProperties().getProperty("q"),
                    "the fresh pair must be on the reconfigured copy");
            for (String key : copy.getDbProperties().stringPropertyNames()) {
                Assertions.assertFalse(key.startsWith("y") || key.startsWith("z"),
                        "the deserialized copy carried a pair no key set tracks: " + key);
            }
        }
    }

    @Test
    void toStringMasksAValueTheParserContinuesAcrossABacksslashLineBreak() {
        // The reader deletes a new line escaped by a trailing backslash and
        // joins the next line into the value, so the delivered password runs
        // past the line break the dump's segmentation sees; two backslashes
        // end the line and the next line really is its own pair.
        PoolProperties continued = new PoolProperties();
        continued.setConnectionProperties("password=abc\\\ncont16;x=1");

        Assertions.assertEquals("abccont16",
                continued.getDbProperties().getProperty("password"),
                "the parser really joins the lines");

        String asString = continued.toString();

        Assertions.assertFalse(asString.contains("cont16"),
                "the continuation tail of the delivered password must not leak, was: " + asString);
        Assertions.assertTrue(asString.contains("connectionProperties=password=********;x=1"),
                "the pair after the continuation must stay readable, was: " + asString);

        PoolProperties escaped = new PoolProperties();
        escaped.setConnectionProperties("password=abc\\\\\ncont16=y");

        Assertions.assertEquals("abc\\", escaped.getDbProperties().getProperty("password"),
                "two backslashes end the line instead of continuing it");

        asString = escaped.toString();

        Assertions.assertTrue(asString.contains("connectionProperties=password=********\ncont16=y"),
                "the next pair is really separate and must stay readable, was: " + asString);
    }

    @Test
    void toStringMasksAnInterceptorPropertyInAnUnclosedPropertyList() {
        // The parse delivers nothing from a definition whose parenthesis
        // never closes, but a pair typed inside it was typed as a credential;
        // and a null string has no properties at all, so the dump must print
        // it without reaching into the masker.
        PoolProperties unterminated = new PoolProperties();
        unterminated.setJdbcInterceptors("A(password=unterminated-secret;B(other=y)");

        String asString = unterminated.toString();

        Assertions.assertFalse(asString.contains("unterminated-secret"),
                "the credential typed into the open list must not leak, was: " + asString);

        PoolProperties none = new PoolProperties();
        none.setJdbcInterceptors(null);

        Assertions.assertTrue(none.toString().contains("jdbcInterceptors=null"),
                "a null string must print as null, was: " + none);
    }

    /** The implementation a configured validator must be rebuilt to. */
    public static class RebuiltValidator implements Validator {
        @Override
        public boolean validate(Connection connection, int validateAction) {
            return true;
        }
    }

    @Test
    void serializationRebuildsTheValidatorConfiguredByName() throws Exception {
        PoolProperties props = new PoolProperties();
        props.setValidatorClassName(RebuiltValidator.class.getName());
        Assertions.assertNotNull(props.getValidator(),
                "configuring by class name must build the instance");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(props);
        }
        PoolProperties read;
        try (ObjectInputStream in =
                new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            read = (PoolProperties) in.readObject();
        }

        // The instance is transient and the name alone does not validate:
        // without the read-side replay, the deserialized configuration
        // reports the validator by name while the query path does the work.
        Assertions.assertNotNull(read.getValidator(),
                "the deserialized configuration must not report a validator it does not run");
        Assertions.assertTrue(read.getValidator() instanceof RebuiltValidator,
                "the replay must rebuild the configured class");
    }
}

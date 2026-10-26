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

import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The credential half of the pool's decision "must this idle session be
 * re-established for the borrower that asks for it?", checked in
 * isolation. A pooled session remembers the identity it logged in as, a
 * {@code null} in a request means the pool's default identity, and the
 * operation both decides <em>and</em> records what was asked - so every
 * case below starts from an explicitly configured session.
 *
 * <p>Ported from the retired legacy {@code ShouldForceReconnectTest}. The
 * {@code maxAge} half of the same decision is covered against a real
 * database by {@code TestPoolEviction#maxAgeRetiresAnOldConnectionBetweenBorrowers}.</p>
 */
class TestShouldForceReconnect {

    private static final String DEFAULT_USER = "username_def";
    private static final String DEFAULT_PASSWD = "password_def";
    private static final String ALT_USER = "username_alt";
    private static final String ALT_PASSWD = "password_alt";

    private PoolProperties properties;

    @BeforeEach
    void setUp() {
        properties = new PoolProperties();
        properties.setUsername(DEFAULT_USER);
        properties.setPassword(DEFAULT_PASSWD);
        properties.setAlternateUsernameAllowed(true);
        properties.setInitialSize(0);
        properties.setRemoveAbandoned(false);
        properties.setTimeBetweenEvictionRunsMillis(-1);
    }

    private PooledConnection pooledConnection() throws Exception {
        return new PooledConnection(properties, new ConnectionPool(properties));
    }

    /** Records the identity a session was last established with. */
    private static void loggedInAs(PooledConnection con, String user, String password) {
        if (user == null) {
            con.getAttributes().remove(PooledConnection.PROP_USER);
        } else {
            con.getAttributes().put(PooledConnection.PROP_USER, user);
        }
        if (password == null) {
            con.getAttributes().remove(PooledConnection.PROP_PASSWORD);
        } else {
            con.getAttributes().put(PooledConnection.PROP_PASSWORD, password);
        }
    }

    @ParameterizedTest(name = "session {1}/{2} asked {3}/{4} forces reconnect: {5}")
    @MethodSource("credentialCombinations")
    void theSessionIsReEstablishedOnlyForAnotherIdentity(String sessionLabel,
            String loggedInUser, String loggedInPassword,
            String askedUser, String askedPassword, boolean expected) throws Exception {
        PooledConnection con = pooledConnection();
        loggedInAs(con, loggedInUser, loggedInPassword);
        Assertions.assertEquals(Boolean.valueOf(expected),
                Boolean.valueOf(con.shouldForceReconnect(askedUser, askedPassword)),
                sessionLabel + " session asked for " + askedUser);
    }

    static Stream<Arguments> credentialCombinations() {
        return Stream.of(
                // a session in the pool's default identity serves the default borrower
                same("default", DEFAULT_USER, DEFAULT_PASSWD, null, null),
                same("default", DEFAULT_USER, DEFAULT_PASSWD, DEFAULT_USER, DEFAULT_PASSWD),
                same("default", DEFAULT_USER, DEFAULT_PASSWD, null, DEFAULT_PASSWD),
                same("default", DEFAULT_USER, DEFAULT_PASSWD, DEFAULT_USER, null),
                // ... and nobody else
                forced("default", DEFAULT_USER, DEFAULT_PASSWD, ALT_USER, ALT_PASSWD),
                forced("default", DEFAULT_USER, DEFAULT_PASSWD, null, ALT_PASSWD),
                forced("default", DEFAULT_USER, DEFAULT_PASSWD, ALT_USER, null),
                // a session in an alternate identity serves exactly that user
                same("alternate", ALT_USER, ALT_PASSWD, ALT_USER, ALT_PASSWD),
                forced("alternate", ALT_USER, ALT_PASSWD, null, null),
                forced("alternate", ALT_USER, ALT_PASSWD, DEFAULT_USER, DEFAULT_PASSWD),
                forced("alternate", ALT_USER, ALT_PASSWD, null, DEFAULT_PASSWD),
                forced("alternate", ALT_USER, ALT_PASSWD, DEFAULT_USER, null),
                forced("alternate", ALT_USER, ALT_PASSWD, null, ALT_PASSWD),
                forced("alternate", ALT_USER, ALT_PASSWD, ALT_USER, null));
    }

    private static Arguments same(String label, String loggedInUser, String loggedInPassword,
            String askedUser, String askedPassword) {
        return Arguments.of(label, loggedInUser, loggedInPassword, askedUser, askedPassword,
                Boolean.FALSE);
    }

    private static Arguments forced(String label, String loggedInUser, String loggedInPassword,
            String askedUser, String askedPassword) {
        return Arguments.of(label, loggedInUser, loggedInPassword, askedUser, askedPassword,
                Boolean.TRUE);
    }

    @Test
    void aSessionThatNeverRecordedAnIdentityIsEstablishedForTheDefaultBorrower()
            throws Exception {
        PooledConnection con = pooledConnection();
        // no PROP_USER/PROP_PASSWORD at all: the pool cannot know who this
        // session belongs to, so it re-establishes it rather than trusting it
        Assertions.assertTrue(con.shouldForceReconnect(null, null));
    }

    @Test
    void theDecisionRecordsTheIdentityItJustVetoed() throws Exception {
        PooledConnection con = pooledConnection();
        loggedInAs(con, DEFAULT_USER, DEFAULT_PASSWD);

        Assertions.assertTrue(con.shouldForceReconnect(ALT_USER, ALT_PASSWD),
                "another identity must be refused the pooled session");
        Assertions.assertFalse(con.shouldForceReconnect(ALT_USER, ALT_PASSWD),
                "the decision records the new identity, so the next borrower asking "
                        + "for the same user gets the session once it is re-established");

        loggedInAs(con, DEFAULT_USER, DEFAULT_PASSWD);
        Assertions.assertFalse(con.shouldForceReconnect(null, null));
        Assertions.assertTrue(con.shouldForceReconnect(ALT_USER, ALT_PASSWD));
        Assertions.assertFalse(con.shouldForceReconnect(ALT_USER, ALT_PASSWD));
        Assertions.assertTrue(con.shouldForceReconnect(null, null),
                "and the same holds in the other direction");
    }

    @Test
    void withoutAlternateCredentialsSupportNoBorrowerEverForcesAReconnect()
            throws Exception {
        properties.setAlternateUsernameAllowed(false);
        PooledConnection con = pooledConnection();
        loggedInAs(con, ALT_USER, ALT_PASSWD);

        Assertions.assertFalse(con.shouldForceReconnect(DEFAULT_USER, DEFAULT_PASSWD),
                "with alternates disallowed the credentials of the borrower are ignored, "
                        + "so the session can never mismatch");
        Assertions.assertFalse(con.shouldForceReconnect(ALT_USER, ALT_PASSWD));
    }
}

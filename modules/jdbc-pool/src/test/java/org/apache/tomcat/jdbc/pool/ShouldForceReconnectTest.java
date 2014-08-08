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
package org.apache.tomcat.jdbc.pool;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ShouldForceReconnectTest {

    private ConnectionPool pool;
    private PoolProperties properties;

    private static final String DEFAULT_USER = "username_def";
    private static final String DEFAULT_PASSWD = "password_def";
    private static final String ALT_USER = "username_alt";
    private static final String ALT_PASSWD = "password_alt";

    @Before
    public void setUp() throws Exception {
        properties = new PoolProperties();
        properties.setUsername(DEFAULT_USER);
        properties.setPassword(DEFAULT_PASSWD);
        properties.setAlternateUsernameAllowed(true);
        properties.setInitialSize(0);
        properties.setRemoveAbandoned(false);
        properties.setTimeBetweenEvictionRunsMillis(-1);
        pool = new ConnectionPool(properties);
    }

    @After
    public void tearDown() throws Exception {




    }

    @Test
    public void testShouldForceReconnect() throws Exception {
        PooledConnection con = new PooledConnection(properties, pool);

        //connection previously connect with default
        configureDefault(con);
        assertFalse(con.shouldForceReconnect(null, null));

        configureDefault(con);
        assertFalse(con.shouldForceReconnect(DEFAULT_USER, DEFAULT_PASSWD));

        configureDefault(con);
        assertFalse(con.shouldForceReconnect(null,DEFAULT_PASSWD));

        configureDefault(con);
        assertFalse(con.shouldForceReconnect(DEFAULT_USER, null));

        configureDefault(con);
        assertTrue(con.shouldForceReconnect(ALT_USER,ALT_PASSWD));

        configureDefault(con);
        assertTrue(con.shouldForceReconnect(null,ALT_PASSWD));

        configureDefault(con);
        assertTrue(con.shouldForceReconnect(ALT_USER,null));

        //connection previously connect with alternate
        configureAlt(con);
        assertFalse(con.shouldForceReconnect(ALT_USER, ALT_PASSWD));

        configureAlt(con);
        assertTrue(con.shouldForceReconnect(null, null));

        configureAlt(con);
        assertTrue(con.shouldForceReconnect(DEFAULT_USER, DEFAULT_PASSWD));

        configureAlt(con);
        assertTrue(con.shouldForceReconnect(null, DEFAULT_PASSWD));

        configureAlt(con);
        assertTrue(con.shouldForceReconnect(DEFAULT_USER, null));

        configureAlt(con);
        assertTrue(con.shouldForceReconnect(null,ALT_PASSWD));

        configureAlt(con);
        assertTrue(con.shouldForceReconnect(ALT_USER,null));

        //test changes in username password
        configureDefault(con);
        assertFalse(con.shouldForceReconnect(null, null));
        assertTrue(con.shouldForceReconnect(ALT_USER, ALT_PASSWD));
        assertFalse(con.shouldForceReconnect(ALT_USER, ALT_PASSWD));
        assertTrue(con.shouldForceReconnect(null, null));

        configureDefault(con);
        assertFalse(con.shouldForceReconnect(DEFAULT_USER, DEFAULT_PASSWD));
        assertTrue(con.shouldForceReconnect(ALT_USER, ALT_PASSWD));
        assertFalse(con.shouldForceReconnect(ALT_USER, ALT_PASSWD));
        assertTrue(con.shouldForceReconnect(DEFAULT_USER, DEFAULT_PASSWD));

        configureAlt(con);
        assertFalse(con.shouldForceReconnect(ALT_USER, ALT_PASSWD));
        assertFalse(con.shouldForceReconnect(ALT_USER, ALT_PASSWD));
        assertTrue(con.shouldForceReconnect(DEFAULT_USER, DEFAULT_PASSWD));
        assertFalse(con.shouldForceReconnect(null, null));
        assertTrue(con.shouldForceReconnect(ALT_USER, ALT_PASSWD));


        configureAlt(con);
        assertTrue(con.shouldForceReconnect(DEFAULT_USER, DEFAULT_PASSWD));
        assertTrue(con.shouldForceReconnect(ALT_USER, ALT_PASSWD));
        assertFalse(con.shouldForceReconnect(ALT_USER, ALT_PASSWD));
        assertTrue(con.shouldForceReconnect(DEFAULT_USER, DEFAULT_PASSWD));

    }

    private void configureAlt(PooledConnection con) {
        con.getAttributes().put(PooledConnection.PROP_USER, ALT_USER);
        con.getAttributes().put(PooledConnection.PROP_PASSWORD, ALT_PASSWD);
    }

    private void configureDefault(PooledConnection con) {
        con.getAttributes().put(PooledConnection.PROP_USER, DEFAULT_USER);
        con.getAttributes().put(PooledConnection.PROP_PASSWORD, DEFAULT_PASSWD);
    }
}
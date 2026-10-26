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

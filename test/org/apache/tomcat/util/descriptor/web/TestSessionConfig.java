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
package org.apache.tomcat.util.descriptor.web;

import org.junit.Assert;
import org.junit.Test;

public class TestSessionConfig {

    @Test
    public void testCookieMaxAgeUnset() {
        SessionConfig sessionConfig = new SessionConfig();
        Assert.assertNull(sessionConfig.getCookieMaxAge());
    }

    @Test
    public void testCookieMaxAgeValid() {
        SessionConfig sessionConfig = new SessionConfig();

        sessionConfig.setCookieMaxAge("60");
        Assert.assertEquals(Integer.valueOf(60), sessionConfig.getCookieMaxAge());
        Assert.assertEquals("60", sessionConfig.getCookieAttribute(Constants.COOKIE_MAX_AGE_ATTR));

        sessionConfig.setCookieMaxAge("0");
        Assert.assertEquals(Integer.valueOf(0), sessionConfig.getCookieMaxAge());

        // Negative values disable cookie expiry and must be accepted
        sessionConfig.setCookieMaxAge("-1");
        Assert.assertEquals(Integer.valueOf(-1), sessionConfig.getCookieMaxAge());
    }

    @Test
    public void testCookieMaxAgeInvalid() {
        SessionConfig sessionConfig = new SessionConfig();

        // An invalid value must be rejected at deployment time rather than
        // causing a NumberFormatException whenever a session cookie is created
        try {
            sessionConfig.setCookieMaxAge("abc");
            Assert.fail("NumberFormatException expected");
        } catch (NumberFormatException e) {
            // Expected
        }
        Assert.assertNull(sessionConfig.getCookieAttribute(Constants.COOKIE_MAX_AGE_ATTR));
        Assert.assertNull(sessionConfig.getCookieMaxAge());

        try {
            sessionConfig.setCookieMaxAge("");
            Assert.fail("NumberFormatException expected");
        } catch (NumberFormatException e) {
            // Expected
        }
        Assert.assertNull(sessionConfig.getCookieAttribute(Constants.COOKIE_MAX_AGE_ATTR));
    }

    @Test
    public void testCookieMaxAgeOverflow() {
        SessionConfig sessionConfig = new SessionConfig();

        try {
            sessionConfig.setCookieMaxAge("2147483648");
            Assert.fail("NumberFormatException expected");
        } catch (NumberFormatException e) {
            // Expected
        }
        Assert.assertNull(sessionConfig.getCookieAttribute(Constants.COOKIE_MAX_AGE_ATTR));
    }
}

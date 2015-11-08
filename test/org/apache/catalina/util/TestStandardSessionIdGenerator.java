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
package org.apache.catalina.util;

import org.junit.Assert;
import org.junit.Test;

public class TestStandardSessionIdGenerator {

    // 100 character long valid session ID. This long to accomodate any future
    // changes in defaut session ID length
    private static final String VALID = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private StandardSessionIdGenerator generator = new StandardSessionIdGenerator();

    @Test
    public void testValidateNull() {
        Assert.assertFalse(generator.validateSessionId(null));
    }

    @Test
    public void testValidateEmpty() {
        Assert.assertFalse(generator.validateSessionId(""));
    }

    @Test
    public void testValidateOneChar() {
        Assert.assertFalse(generator.validateSessionId("A"));
    }

    @Test
    public void testValidateShort() {
        Assert.assertFalse(generator.validateSessionId(
                VALID.substring(0, (generator.getSessionIdLength() * 2) -1)));
    }

    @Test
    public void testValidateJustRight() {
        Assert.assertTrue(generator.validateSessionId(
                VALID.substring(0, (generator.getSessionIdLength() * 2))));
    }

    @Test
    public void testValidateLong() {
        Assert.assertTrue(generator.validateSessionId(VALID));
    }

    @Test
    public void testValidateInvalid() {
        Assert.assertFalse(generator.validateSessionId(VALID + "g"));
    }

    @Test
    public void testValidateWithJvmRoute() {
        Assert.assertTrue(generator.validateSessionId(VALID + ".g"));
    }

    @Test
    public void testValidateWithJvmRouteWithPerid() {
        Assert.assertTrue(generator.validateSessionId(VALID + ".g.h.i"));
    }

}

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
package org.apache.tomcat.util.http;

import org.junit.Assert;
import org.junit.Test;


public class TestSameSiteCookies {

    @Test
    public void testUnset() {
        SameSiteCookies attribute = SameSiteCookies.UNSET;

        Assert.assertEquals("Unset", attribute.getValue());
        Assert.assertEquals(SameSiteCookies.UNSET, attribute);

        Assert.assertNotEquals(SameSiteCookies.NONE, attribute);
        Assert.assertNotEquals(SameSiteCookies.LAX, attribute);
        Assert.assertNotEquals(SameSiteCookies.STRICT, attribute);
    }

    @Test
    public void testNone() {
        SameSiteCookies attribute = SameSiteCookies.NONE;

        Assert.assertEquals("None", attribute.getValue());
        Assert.assertEquals(SameSiteCookies.NONE, attribute);

        Assert.assertNotEquals(SameSiteCookies.UNSET, attribute);
        Assert.assertNotEquals(SameSiteCookies.LAX, attribute);
        Assert.assertNotEquals(SameSiteCookies.STRICT, attribute);
    }

    @Test
    public void testLax() {
        SameSiteCookies attribute = SameSiteCookies.LAX;

        Assert.assertEquals("Lax", attribute.getValue());
        Assert.assertEquals(SameSiteCookies.LAX, attribute);

        Assert.assertNotEquals(SameSiteCookies.UNSET, attribute);
        Assert.assertNotEquals(SameSiteCookies.NONE, attribute);
        Assert.assertNotEquals(SameSiteCookies.STRICT, attribute);
    }

    @Test
    public void testStrict() {
        SameSiteCookies attribute = SameSiteCookies.STRICT;

        Assert.assertEquals("Strict", attribute.getValue());
        Assert.assertEquals(SameSiteCookies.STRICT, attribute);

        Assert.assertNotEquals(SameSiteCookies.UNSET, attribute);
        Assert.assertNotEquals(SameSiteCookies.NONE, attribute);
        Assert.assertNotEquals(SameSiteCookies.LAX, attribute);
    }

    @Test
    public void testToValidAttribute() {
        Assert.assertEquals(SameSiteCookies.fromString("unset"), SameSiteCookies.UNSET);
        Assert.assertEquals(SameSiteCookies.fromString("Unset"), SameSiteCookies.UNSET);
        Assert.assertEquals(SameSiteCookies.fromString("UNSET"), SameSiteCookies.UNSET);

        Assert.assertEquals(SameSiteCookies.fromString("none"), SameSiteCookies.NONE);
        Assert.assertEquals(SameSiteCookies.fromString("None"), SameSiteCookies.NONE);
        Assert.assertEquals(SameSiteCookies.fromString("NONE"), SameSiteCookies.NONE);

        Assert.assertEquals(SameSiteCookies.fromString("lax"), SameSiteCookies.LAX);
        Assert.assertEquals(SameSiteCookies.fromString("Lax"), SameSiteCookies.LAX);
        Assert.assertEquals(SameSiteCookies.fromString("LAX"), SameSiteCookies.LAX);

        Assert.assertEquals(SameSiteCookies.fromString("strict"), SameSiteCookies.STRICT);
        Assert.assertEquals(SameSiteCookies.fromString("Strict"), SameSiteCookies.STRICT);
        Assert.assertEquals(SameSiteCookies.fromString("STRICT"), SameSiteCookies.STRICT);
    }

    @Test(expected = IllegalStateException.class)
    public void testToInvalidAttribute01() {
        SameSiteCookies.fromString("");
    }

    @Test(expected = IllegalStateException.class)
    public void testToInvalidAttribute02() {
        SameSiteCookies.fromString(" ");
    }

    @Test(expected = IllegalStateException.class)
    public void testToInvalidAttribute03() {
        SameSiteCookies.fromString("Strict1");
    }

    @Test(expected = IllegalStateException.class)
    public void testToInvalidAttribute04() {
        SameSiteCookies.fromString("foo");
    }

    @Test(expected = IllegalStateException.class)
    public void testToInvalidAttribute05() {
        SameSiteCookies.fromString("Lax ");
    }
}
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
package jakarta.servlet.http;

import org.junit.Assert;
import org.junit.Test;

/**
 * Basic tests for Cookie in STRICT_SERVLET_COMPLIANCE configuration.
 */
public class TestCookieStrict {
    static {
        System.setProperty("org.apache.tomcat.util.http.ServerCookie.STRICT_NAMING", "true");
    }

    @Test
    public void testDefaults() {
        Cookie cookie = new Cookie("strict", null);
        Assert.assertEquals("strict", cookie.getName());
        Assert.assertNull(cookie.getValue());
        Assert.assertEquals(0, cookie.getVersion());
        Assert.assertEquals(-1, cookie.getMaxAge());
    }

    @Test(expected = IllegalArgumentException.class)
    public void strictNamingImpliesRFC2109() {
        @SuppressWarnings("unused")
        Cookie cookie = new Cookie("@Foo", null);
    }
}

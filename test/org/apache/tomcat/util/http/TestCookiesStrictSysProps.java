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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.buf.ByteChunk;

/**
 * Test case for {@link LegacyCookieProcessor}. <b>Note</b> because of the use
 * of <code>final static</code> constants in {@link javax.servlet.http.Cookie},
 * each of these tests must be executed in a new JVM instance. The tests have
 * been placed in separate classes to facilitate this when running the unit
 * tests via Ant.
 */
public class TestCookiesStrictSysProps extends CookiesBaseTest {

    @Override
    @Test
    public void testCookiesInstance() throws Exception {

        System.setProperty("org.apache.catalina.STRICT_SERVLET_COMPLIANCE",
                "true");

        Tomcat tomcat = getTomcatInstance();

        addServlets(tomcat);

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/invalid");
        Assert.assertEquals("Cookie name fail", res.toString());
        res = getUrl("http://localhost:" + getPort() + "/null");
        Assert.assertEquals("Cookie name fail", res.toString());
        res = getUrl("http://localhost:" + getPort() + "/blank");
        Assert.assertEquals("Cookie name fail", res.toString());
        res = getUrl("http://localhost:" + getPort() + "/invalidFwd");
        Assert.assertEquals("Cookie name fail", res.toString());
        res = getUrl("http://localhost:" + getPort() + "/invalidStrict");
        Assert.assertEquals("Cookie name fail", res.toString());
        res = getUrl("http://localhost:" + getPort() + "/valid");
        Assert.assertEquals("Cookie name ok", res.toString());

        // Need to read response headers to test version switching
        Map<String,List<String>> headers = new HashMap<>();
        getUrl("http://localhost:" + getPort() + "/switch", res, headers);
        List<String> cookieHeaders = headers.get("Set-Cookie");
        for (String cookieHeader : cookieHeaders) {
            Assert.assertEquals("name=\"val?ue\"; Version=1", cookieHeader);
        }
    }
}

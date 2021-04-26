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
package org.apache.tomcat.util.http.parser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.tomcat.util.http.ServerCookies;

@RunWith(Parameterized.class)
public class TestCookie {

    @Parameterized.Parameters(name = "{index}: header[{0}]")
    public static Collection<Object[]> parameters() {

        List<Object[]> parameterSets = new ArrayList<>();

        String[] SEPS = new String[] { ",", ";" };
        String[] PATHS = new String[] { ";$Path=/foo", " ; $Path = /foo ", ""};
        String[] DOMAINS = new String[] { ";$Domain=bar.com",  " ; $Domain = bar.com ", ""};

        for (String sep1 : SEPS) {
            for (String path1 : PATHS) {
                for (String domain1 : DOMAINS) {
                    for (String sep2 : SEPS) {
                        for (String path2 : PATHS) {
                            for (String domain2 : DOMAINS) {
                                for (String sep3 : SEPS) {
                                    for (String path3 : PATHS) {
                                        for (String domain3 : DOMAINS) {
                                            StringBuilder sb = new StringBuilder("$Version=1");
                                            sb.append(sep1);
                                            sb.append("first=1");
                                            sb.append(path1);
                                            sb.append(domain1);
                                            sb.append(sep2);
                                            sb.append("second=2");
                                            sb.append(path2);
                                            sb.append(domain2);
                                            sb.append(sep3);
                                            sb.append("third=3");
                                            sb.append(path3);
                                            sb.append(domain3);

                                            parameterSets.add(new Object[] { sb.toString() });
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return parameterSets;
    }

    @Parameter(0)
    public String cookieHeader;

    @Test
    public void testParseThreeCookieHeader() {
        ServerCookies serverCookies = new ServerCookies(3);
        byte[] inputBytes = cookieHeader.getBytes(StandardCharsets.ISO_8859_1);
        Cookie.parseCookie(inputBytes, 0, inputBytes.length, serverCookies);
        Assert.assertEquals(3,  serverCookies.getCookieCount());
        Assert.assertEquals("first", serverCookies.getCookie(0).getName().toString());
        Assert.assertEquals("1", serverCookies.getCookie(0).getValue().toString());
        Assert.assertEquals("second", serverCookies.getCookie(1).getName().toString());
        Assert.assertEquals("2", serverCookies.getCookie(1).getValue().toString());
        Assert.assertEquals("third", serverCookies.getCookie(2).getName().toString());
        Assert.assertEquals("3", serverCookies.getCookie(2).getValue().toString());
    }
}

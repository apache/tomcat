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

import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.buf.MessageBytes;

public class TestLegacyCookieProcessor {

    /*
     * https://bz.apache.org/bugzilla/show_bug.cgi?id=59925
     */
    @Test
    public void testV0WithPath() {
        LegacyCookieProcessor cp = new LegacyCookieProcessor();
        cp.setAllowHttpSepsInV0(true);
        cp.setForwardSlashIsSeparator(true);

        MimeHeaders mimeHeaders = new MimeHeaders();
        ServerCookies serverCookies = new ServerCookies(4);

        MessageBytes cookieHeaderValue = mimeHeaders.addValue("Cookie");
        byte[] bytes = "$Version=0;cname=cvalue;$Path=/example".getBytes(StandardCharsets.UTF_8);
        cookieHeaderValue.setBytes(bytes, 0, bytes.length);
        cp.parseCookieHeader(mimeHeaders, serverCookies);
        Assert.assertEquals(1, serverCookies.getCookieCount());
        for (int i = 0; i < 1; i++) {
            ServerCookie actual = serverCookies.getCookie(i);
            Assert.assertEquals(0, actual.getVersion());
            Assert.assertEquals("cname", actual.getName().toString());
            actual.getValue().getByteChunk().setCharset(StandardCharsets.UTF_8);
            Assert.assertEquals("cvalue",
                    org.apache.tomcat.util.http.parser.Cookie.unescapeCookieValueRfc2109(
                            actual.getValue().toString()));
            Assert.assertEquals("/example", actual.getPath().toString());
        }
    }
}

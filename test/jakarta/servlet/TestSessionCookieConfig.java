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
package jakarta.servlet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestSessionCookieConfig extends TomcatBaseTest {

    /*
     * Not strictly testing the SessionCookieConfig class
     */
    @Test
    public void testCustomAttribute() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk responseBody = new ByteChunk();
        Map<String,List<String>> responseHeaders = new HashMap<>();

        int statusCode =
                getUrl("http://localhost:" + getPort() + "/test/bug49nnn/bug49196.jsp", responseBody, responseHeaders);

        Assert.assertEquals(HttpServletResponse.SC_OK, statusCode);
        Assert.assertTrue(responseBody.toString().contains("OK"));
        Assert.assertTrue(responseHeaders.containsKey("Set-Cookie"));

        List<String> setCookieHeaders = responseHeaders.get("Set-Cookie");
        Assert.assertEquals(1,  setCookieHeaders.size());

        String setCookieHeader = setCookieHeaders.get(0);
        Assert.assertTrue(setCookieHeader.contains("; aaa=bbb"));
    }
}

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

package org.apache.tomcat.lite.http;

import junit.framework.TestCase;

import org.apache.tomcat.lite.TestMain;
import org.apache.tomcat.lite.io.BBuffer;

public class HttpsTest extends TestCase {

    static int port = 8443;

    public void testSimpleClient() throws Exception {
        final HttpConnector httpClient = TestMain.shared().getClient();
        checkResponse(httpClient);
    }

    public void testSimpleServer() throws Exception {
        final HttpConnector httpClient = TestMain.shared().getClient();
        BBuffer res = TestMain.getUrl("https://localhost:8443/hello");
        assertTrue(res.toString().indexOf("Hello") >= 0);
    }


    private void checkResponse(HttpConnector httpCon) throws Exception {
        HttpRequest ch = httpCon.request("localhost", port).setSecure(true);

        ch.setRequestURI("/hello");
        ch.setProtocol("HTTP/1.0"); // to force close
        ch.send();
        BBuffer res = ch.readAll();

        assertTrue(res.toString().indexOf("Hello") >= 0);
    }

    public void testSimpleClient20() throws Exception {
        final HttpConnector httpClient = TestMain.shared().getClient();
        for (int i = 0; i < 10; i++) {
            checkResponse(httpClient);
        }
    }

    public void testSimpleRequestGoogle() throws Exception {
        for (int i = 0; i < 40; i++) {
        final HttpConnector httpClient = TestMain.shared().getClient();
        HttpRequest client = httpClient.request("www.google.com", 443).
            setSecure(true);
        client.getHttpChannel().setIOTimeout(2000000);
        client.setRequestURI("/accounts/ServiceLogin");
        client.send();

        BBuffer res = client.readAll();
        assertTrue(res.toString().indexOf("<title>Google Accounts</title>") > 0);
        }
    }


}

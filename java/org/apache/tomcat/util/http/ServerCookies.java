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

/**
 * This class is not thread-safe.
 */
public class ServerCookies {

    private ServerCookie[] serverCookies;

    private int cookieCount = 0;


    public ServerCookies(int initialSize) {
        serverCookies = new ServerCookie[initialSize];
    }


    /**
     * Register a new, initialized cookie. Cookies are recycled, and most of the
     * time an existing ServerCookie object is returned. The caller can set the
     * name/value and attributes for the cookie.
     */
    public ServerCookie addCookie() {
        if (cookieCount >= serverCookies.length) {
            ServerCookie scookiesTmp[] = new ServerCookie[2*cookieCount];
            System.arraycopy(serverCookies, 0, scookiesTmp, 0, cookieCount);
            serverCookies = scookiesTmp;
        }

        ServerCookie c = serverCookies[cookieCount];
        if (c == null) {
            c = new ServerCookie();
            serverCookies[cookieCount] = c;
        }
        cookieCount++;
        return c;
    }


    public ServerCookie getCookie(int idx) {
        return serverCookies[idx];
    }


    public int getCookieCount() {
        return cookieCount;
    }


    public void recycle() {
        for (int i = 0; i < cookieCount; i++) {
            serverCookies[i].recycle();
        }
        cookieCount = 0;
    }
}

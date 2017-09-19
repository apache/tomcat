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

import org.apache.tomcat.util.res.StringManager;

/**
 * This class is not thread-safe.
 */
public class ServerCookies {

    private static final StringManager sm = StringManager.getManager(ServerCookies.class);

    private ServerCookie[] serverCookies;

    private int cookieCount = 0;
    private int limit = 200;


    public ServerCookies(int initialSize) {
        serverCookies = new ServerCookie[initialSize];
    }


    /**
     * Register a new, initialized cookie. Cookies are recycled, and most of the
     * time an existing ServerCookie object is returned. The caller can set the
     * name/value and attributes for the cookie.
     * @return the new cookie
     */
    public ServerCookie addCookie() {
        if (limit > -1 && cookieCount >= limit) {
            throw new IllegalArgumentException(
                    sm.getString("cookies.maxCountFail", Integer.valueOf(limit)));
        }

        if (cookieCount >= serverCookies.length) {
            int newSize = limit > -1 ? Math.min(2*cookieCount, limit) : 2*cookieCount;
            ServerCookie scookiesTmp[] = new ServerCookie[newSize];
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


    public void setLimit(int limit) {
        this.limit = limit;
        if (limit > -1 && serverCookies.length > limit && cookieCount <= limit) {
            // shrink cookie list array
            ServerCookie scookiesTmp[] = new ServerCookie[limit];
            System.arraycopy(serverCookies, 0, scookiesTmp, 0, cookieCount);
            serverCookies = scookiesTmp;
        }
    }


    public void recycle() {
        for (int i = 0; i < cookieCount; i++) {
            serverCookies[i].recycle();
        }
        cookieCount = 0;
    }
}

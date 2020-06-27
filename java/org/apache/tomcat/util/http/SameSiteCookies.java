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

public enum SameSiteCookies {

    /**
     * Don't set the SameSite cookie attribute.
     */
    UNSET("Unset"),

    /**
     * Cookie is always sent in cross-site requests.
     */
    NONE("None"),

    /**
     * Cookie is only sent on same-site requests and cross-site top level navigation GET requests
     */
    LAX("Lax"),

    /**
     * Prevents the cookie from being sent by the browser in all cross-site requests
     */
    STRICT("Strict");

    private static final StringManager sm = StringManager.getManager(SameSiteCookies.class);

    private final String value;

    SameSiteCookies(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static SameSiteCookies fromString(String value) {
        for (SameSiteCookies sameSiteCookies : SameSiteCookies.values()) {
            if (sameSiteCookies.getValue().equalsIgnoreCase(value)) {
                return sameSiteCookies;
            }
        }

        throw new IllegalStateException(sm.getString("cookies.invalidSameSiteCookies", value));
    }
}

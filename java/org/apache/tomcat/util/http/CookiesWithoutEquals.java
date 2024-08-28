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

import java.util.Locale;

import org.apache.tomcat.util.res.StringManager;

public enum CookiesWithoutEquals {
    IGNORE("ignore"),
    NAME("name");
    /*
     * There is no VALUE option since the Servlet specification does not permit the creation of a Cookie with a name
     * that is either null or the zero length string.
     *
     * The historical intention (from the user agent perspective) of using a name-value-pair without an equals sign has
     * been to indicate a cookie with a name but no value. Tomcat has done the opposite. The current RFC6265bis text
     * treats a name-value-pair without an equals sign as a cookie with a value but no name. Supporting this will
     * require changes to the Servlet specifcation.
     */


    private static final StringManager sm = StringManager.getManager(CookiesWithoutEquals.class);

    private final String value;

    CookiesWithoutEquals(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static CookiesWithoutEquals fromString(String from) {
        String trimmedLower = from.trim().toLowerCase(Locale.ENGLISH);

        for (CookiesWithoutEquals value : values()) {
            if (value.getValue().equals(trimmedLower)) {
                return value;
            }
        }

        throw new IllegalStateException(sm.getString("cookiesWithoutEquals.invalid", from));
    }
}

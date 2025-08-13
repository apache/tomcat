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
     * In RFC 2019, cookie name and value were defined as follows:
     *    cookie          =       NAME "=" VALUE *(";" cookie-av)
     *    NAME            =       attr
     *    VALUE           =       value
     *    attr            =       token
     *    value           =       word
     *    And from RFC 2068
     *    token           =       1*<any CHAR except CTLs or tspecials>
     *    word            =       *TEXT
     * Set-Cookie and Cookie used the same definition.
     * Name had to be at least one character, equals sign was required, value could be the empty string.
     *
     * In RFC 2965, the definition of value changed to:
     *    value           =       token | quoted-string
     * Set-Cookie2 and Cookie use the same definition.
     * Name had to be at least one character, equals sign was required, value could not be the empty string (it could
     * be "").
     *
     * In RFC6265, which aimed to document actual usage, cookie name and value are defined as follows:
     *   cookie-pair      = cookie-name "=" cookie-value
     *   cookie-name      = token
     *   cookie-value     = *cookie-octet / ( DQUOTE *cookie-octet DQUOTE )
     * For the user agent, the equals sign was required and cookies with no name were ignored.
     *
     * In RFC6265bis, the definitions are unchanged.
     * For the user agent:
     *  - a name-value-pair without an equals sign is treated as the value of a cookie with an empty name.
     *  - both empty name and empty value are allowed but if both are empty the cookie will be ignored.
     *
     * To see how RFC6265 arrived at his behaviour, see:
     * https://github.com/httpwg/http-extensions/issues/159
     *
     * Historically, the users agents settled on using a name-value-pair without an equals sign to indicate a cookie
     * with a value but no name. Tomcat did the opposite. That arose from addressing this bug:
     * https://bz.apache.org/bugzilla/show_bug.cgi?id=49000 which was based on observed but not understood client
     * behaviour.
     *
     * The current RFC6265bis text explicitly treats a name-value-pair without an equals sign as a cookie with a value
     * but no name. There are currently no plans for the Servlet specification to support nameless cookies.
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

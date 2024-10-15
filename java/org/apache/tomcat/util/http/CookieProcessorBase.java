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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public abstract class CookieProcessorBase implements CookieProcessor {

    private static final String COOKIE_DATE_PATTERN = "EEE, dd MMM yyyy HH:mm:ss z";

    protected static final ThreadLocal<DateFormat> COOKIE_DATE_FORMAT = ThreadLocal.withInitial(() -> {
        DateFormat df = new SimpleDateFormat(COOKIE_DATE_PATTERN, Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        return df;
    });

    protected static final String ANCIENT_DATE;

    static {
        ANCIENT_DATE = COOKIE_DATE_FORMAT.get().format(new Date(10000));
    }

    private SameSiteCookies sameSiteCookies = SameSiteCookies.UNSET;

    private boolean partitioned = false;

    private CookiesWithoutEquals cookiesWithoutEquals = CookiesWithoutEquals.IGNORE;


    public String getCookiesWithoutEquals() {
        return cookiesWithoutEquals.getValue();
    }


    protected CookiesWithoutEquals getCookiesWithoutEqualsInternal() {
        return cookiesWithoutEquals;
    }


    public void setCookiesWithoutEquals(String cookiesWithoutEquals) {
        this.cookiesWithoutEquals = CookiesWithoutEquals.fromString(cookiesWithoutEquals);
    }


    public SameSiteCookies getSameSiteCookies() {
        return sameSiteCookies;
    }

    public void setSameSiteCookies(String sameSiteCookies) {
        this.sameSiteCookies = SameSiteCookies.fromString(sameSiteCookies);
    }


    /**
     * Should the {@code Partitioned} attribute be added by default to cookies created for this web application.
     * <p>
     * The name of the attribute used to indicate a partitioned cookie as part of
     * <a href="https://developers.google.com/privacy-sandbox/3pcd#partitioned">CHIPS</a> is not defined by an RFC and
     * may change in a non-backwards compatible way once equivalent functionality is included in an RFC.
     *
     * @return {@code true} if the {@code Partitioned} attribute should be added by default to cookies created for this
     *             web application, otherwise {@code false}
     */
    public boolean getPartitioned() {
        return partitioned;
    }


    /**
     * Configure whether the {@code Partitioned} attribute should be added by default to cookies created for this web
     * application.
     * <p>
     * The name of the attribute used to indicate a partitioned cookie as part of
     * <a href="https://developers.google.com/privacy-sandbox/3pcd#partitioned">CHIPS</a> is not defined by an RFC and
     * may change in a non-backwards compatible way once equivalent functionality is included in an RFC.
     *
     * @param partitioned {@code true} if the {@code Partitioned} attribute should be added by default to cookies
     *                        created for this web application, otherwise {@code false}
     */
    public void setPartitioned(boolean partitioned) {
        this.partitioned = partitioned;
    }
}

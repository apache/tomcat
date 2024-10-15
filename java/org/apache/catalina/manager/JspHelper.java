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
package org.apache.catalina.manager;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.apache.catalina.Session;
import org.apache.catalina.manager.util.SessionUtils;
import org.apache.tomcat.util.security.Escape;


/**
 * Helper JavaBean for JSPs, because JSTL 1.1/EL 2.0 is too dumb to to what I need (call methods with parameters), or I
 * am too dumb to use it correctly. :)
 *
 * @author C&eacute;drik LIME
 */
public class JspHelper {

    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /**
     * Public constructor, so that this class can be considered a JavaBean
     */
    private JspHelper() {
        super();
    }

    /**
     * Try to get user locale from the session, if possible.
     * <p>
     * IMPLEMENTATION NOTE: this method has explicit support for Tapestry 3 and Struts 1.x
     *
     * @param in_session Session from which the locale should be guessed
     *
     * @return String
     */
    public static String guessDisplayLocaleFromSession(Session in_session) {
        return localeToString(SessionUtils.guessLocaleFromSession(in_session));
    }

    private static String localeToString(Locale locale) {
        if (locale != null) {
            return escapeXml(locale.toString()); // locale.getDisplayName();
        } else {
            return "";
        }
    }

    /**
     * Try to get user name from the session, if possible.
     *
     * @param in_session The Servlet session
     *
     * @return the user name
     */
    public static String guessDisplayUserFromSession(Session in_session) {
        Object user = SessionUtils.guessUserFromSession(in_session);
        return escapeXml(user);
    }


    public static String getDisplayCreationTimeForSession(Session in_session) {
        try {
            if (in_session.getCreationTime() == 0) {
                return "";
            }
            DateFormat formatter = new SimpleDateFormat(DATE_TIME_FORMAT);
            return formatter.format(new Date(in_session.getCreationTime()));
        } catch (IllegalStateException ise) {
            // ignore: invalidated session
            return "";
        }
    }

    public static String getDisplayLastAccessedTimeForSession(Session in_session) {
        try {
            if (in_session.getLastAccessedTime() == 0) {
                return "";
            }
            DateFormat formatter = new SimpleDateFormat(DATE_TIME_FORMAT);
            return formatter.format(new Date(in_session.getLastAccessedTime()));
        } catch (IllegalStateException ise) {
            // ignore: invalidated session
            return "";
        }
    }

    public static String getDisplayUsedTimeForSession(Session in_session) {
        try {
            if (in_session.getCreationTime() == 0) {
                return "";
            }
        } catch (IllegalStateException ise) {
            // ignore: invalidated session
            return "";
        }
        return secondsToTimeString(SessionUtils.getUsedTimeForSession(in_session) / 1000);
    }

    public static String getDisplayTTLForSession(Session in_session) {
        try {
            if (in_session.getCreationTime() == 0) {
                return "";
            }
        } catch (IllegalStateException ise) {
            // ignore: invalidated session
            return "";
        }
        return secondsToTimeString(SessionUtils.getTTLForSession(in_session) / 1000);
    }

    public static String getDisplayInactiveTimeForSession(Session in_session) {
        try {
            if (in_session.getCreationTime() == 0) {
                return "";
            }
        } catch (IllegalStateException ise) {
            // ignore: invalidated session
            return "";
        }
        return secondsToTimeString(SessionUtils.getInactiveTimeForSession(in_session) / 1000);
    }

    public static String secondsToTimeString(long in_seconds) {
        StringBuilder buff = new StringBuilder(9);
        if (in_seconds < 0) {
            buff.append('-');
            in_seconds = -in_seconds;
        }
        long rest = in_seconds;
        long hour = rest / 3600;
        rest = rest % 3600;
        long minute = rest / 60;
        rest = rest % 60;
        long second = rest;
        if (hour < 10) {
            buff.append('0');
        }
        buff.append(hour);
        buff.append(':');
        if (minute < 10) {
            buff.append('0');
        }
        buff.append(minute);
        buff.append(':');
        if (second < 10) {
            buff.append('0');
        }
        buff.append(second);
        return buff.toString();
    }


    /*
     * Following copied from org.apache.taglibs.standard.tag.common.core.Util v1.1.2
     */

    private static final int HIGHEST_SPECIAL = '>';
    private static final char[][] specialCharactersRepresentation = new char[HIGHEST_SPECIAL + 1][];
    static {
        specialCharactersRepresentation['&'] = "&amp;".toCharArray();
        specialCharactersRepresentation['<'] = "&lt;".toCharArray();
        specialCharactersRepresentation['>'] = "&gt;".toCharArray();
        specialCharactersRepresentation['"'] = "&#034;".toCharArray();
        specialCharactersRepresentation['\''] = "&#039;".toCharArray();
    }

    public static String escapeXml(Object obj) {
        String value = null;
        try {
            value = obj == null ? null : obj.toString();
        } catch (Exception e) {
            // Ignore
        }
        return escapeXml(value);
    }

    /**
     * Performs the following substring replacements (to facilitate output to XML/HTML pages):
     * <ul>
     * <li>&amp; -&gt; &amp;amp;</li>
     * <li>&lt; -&gt; &amp;lt;</li>
     * <li>&gt; -&gt; &amp;gt;</li>
     * <li>" -&gt; &amp;#034;</li>
     * <li>' -&gt; &amp;#039;</li>
     * </ul>
     *
     * @param buffer The XML to escape
     *
     * @return the escaped XML
     */
    public static String escapeXml(String buffer) {

        if (buffer == null) {
            return "";
        }

        return Escape.xml(buffer);
    }

    public static String formatNumber(long number) {
        return NumberFormat.getNumberInstance().format(number);
    }
}

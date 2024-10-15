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

import java.text.ParseException;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class to generate HTTP dates.
 *
 * @author Remy Maucherat
 */
public final class FastHttpDateFormat {


    // -------------------------------------------------------------- Variables


    private static final int CACHE_SIZE =
            Integer.getInteger("org.apache.tomcat.util.http.FastHttpDateFormat.CACHE_SIZE", 1000).intValue();


    // HTTP date formats
    private static final String DATE_RFC5322 = "EEE, dd MMM yyyy HH:mm:ss z";
    private static final String DATE_OBSOLETE_RFC850 = "EEEEEE, dd-MMM-yy HH:mm:ss zzz";
    private static final String DATE_OBSOLETE_ASCTIME = "EEE MMMM d HH:mm:ss yyyy";

    private static final ConcurrentDateFormat FORMAT_RFC5322;
    private static final ConcurrentDateFormat FORMAT_OBSOLETE_RFC850;
    private static final ConcurrentDateFormat FORMAT_OBSOLETE_ASCTIME;

    private static final ConcurrentDateFormat[] httpParseFormats;

    static {
        // All the formats that use a timezone use GMT
        TimeZone tz = TimeZone.getTimeZone("GMT");

        FORMAT_RFC5322 = new ConcurrentDateFormat(DATE_RFC5322, Locale.US, tz);
        FORMAT_OBSOLETE_RFC850 = new ConcurrentDateFormat(DATE_OBSOLETE_RFC850, Locale.US, tz);
        FORMAT_OBSOLETE_ASCTIME = new ConcurrentDateFormat(DATE_OBSOLETE_ASCTIME, Locale.US, tz);

        httpParseFormats =
                new ConcurrentDateFormat[] { FORMAT_RFC5322, FORMAT_OBSOLETE_RFC850, FORMAT_OBSOLETE_ASCTIME };
    }

    /**
     * Instant on which the currentDate object was generated.
     */
    private static volatile long currentDateGeneratedInSeconds = 0L;


    /**
     * Current formatted date.
     */
    private static String currentDate = null;


    /**
     * Formatter cache.
     */
    private static final Map<Long,String> formatCache = new ConcurrentHashMap<>(CACHE_SIZE);


    /**
     * Parser cache.
     */
    private static final Map<String,Long> parseCache = new ConcurrentHashMap<>(CACHE_SIZE);


    // --------------------------------------------------------- Public Methods


    /**
     * Get the current date in HTTP format.
     *
     * @return the HTTP date
     */
    public static String getCurrentDate() {
        // according rfc5322, date/time data is accurate to the second.
        long nowInSeconds = System.currentTimeMillis() / 1000L;
        if (nowInSeconds != currentDateGeneratedInSeconds) {
            currentDate = FORMAT_RFC5322.format(new Date(nowInSeconds * 1000L));
            currentDateGeneratedInSeconds = nowInSeconds;
        }
        return currentDate;
    }


    /**
     * Get the HTTP format of the specified date.
     *
     * @param value The date
     *
     * @return the HTTP date
     */
    public static String formatDate(long value) {
        Long longValue = Long.valueOf(value);
        String cachedDate = formatCache.get(longValue);
        if (cachedDate != null) {
            return cachedDate;
        }

        String newDate = FORMAT_RFC5322.format(new Date(value));
        updateFormatCache(longValue, newDate);
        return newDate;
    }


    /**
     * Try to parse the given date as an HTTP date.
     *
     * @param value The HTTP date
     *
     * @return the date as a long or <code>-1</code> if the value cannot be parsed
     */
    public static long parseDate(String value) {

        Long cachedDate = parseCache.get(value);
        if (cachedDate != null) {
            return cachedDate.longValue();
        }

        long date = -1;
        for (int i = 0; (date == -1) && (i < httpParseFormats.length); i++) {
            try {
                date = httpParseFormats[i].parse(value).getTime();
                updateParseCache(value, Long.valueOf(date));
            } catch (ParseException e) {
                // Ignore
            }
        }

        return date;
    }


    /**
     * Update cache.
     */
    private static void updateFormatCache(Long key, String value) {
        if (value == null) {
            return;
        }
        if (formatCache.size() > CACHE_SIZE) {
            formatCache.clear();
        }
        formatCache.put(key, value);
    }


    /**
     * Update cache.
     */
    private static void updateParseCache(String key, Long value) {
        if (value == null) {
            return;
        }
        if (parseCache.size() > CACHE_SIZE) {
            parseCache.clear();
        }
        parseCache.put(key, value);
    }


}

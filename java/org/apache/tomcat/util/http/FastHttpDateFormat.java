/*
 *  Copyright 1999-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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

import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

/**
 * Utility class to generate HTTP dates.
 * 
 * @author Remy Maucherat
 */
public final class FastHttpDateFormat {


    // -------------------------------------------------------------- Variables


    /**
     * HTTP date format.
     */
    protected static final SimpleDateFormat format = 
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);


    /**
     * The set of SimpleDateFormat formats to use in getDateHeader().
     */
    protected static final SimpleDateFormat formats[] = {
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
        new SimpleDateFormat("EEEEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US),
        new SimpleDateFormat("EEE MMMM d HH:mm:ss yyyy", Locale.US)
    };


    protected final static TimeZone gmtZone = TimeZone.getTimeZone("GMT");


    /**
     * GMT timezone - all HTTP dates are on GMT
     */
    static {

        format.setTimeZone(gmtZone);

        formats[0].setTimeZone(gmtZone);
        formats[1].setTimeZone(gmtZone);
        formats[2].setTimeZone(gmtZone);

    }


    /**
     * Instant on which the currentDate object was generated.
     */
    protected static long currentDateGenerated = 0L;


    /**
     * Current formatted date.
     */
    protected static String currentDate = null;


    /**
     * Formatter cache.
     */
    protected static final HashMap formatCache = new HashMap();


    /**
     * Parser cache.
     */
    protected static final HashMap parseCache = new HashMap();


    // --------------------------------------------------------- Public Methods


    /**
     * Get the current date in HTTP format.
     */
    public static final String getCurrentDate() {

        long now = System.currentTimeMillis();
        if ((now - currentDateGenerated) > 1000) {
            synchronized (format) {
                if ((now - currentDateGenerated) > 1000) {
                    currentDateGenerated = now;
                    currentDate = format.format(new Date(now));
                }
            }
        }
        return currentDate;

    }


    /**
     * Get the HTTP format of the specified date.
     */
    public static final String formatDate
        (long value, DateFormat threadLocalformat) {

        String cachedDate = null;
        Long longValue = new Long(value);
        try {
            cachedDate = (String) formatCache.get(longValue);
        } catch (Exception e) {
        }
        if (cachedDate != null)
            return cachedDate;

        String newDate = null;
        Date dateValue = new Date(value);
        if (threadLocalformat != null) {
            newDate = threadLocalformat.format(dateValue);
            synchronized (formatCache) {
                updateCache(formatCache, longValue, newDate);
            }
        } else {
            synchronized (formatCache) {
                synchronized (format) {
                    newDate = format.format(dateValue);
                }
                updateCache(formatCache, longValue, newDate);
            }
        }
        return newDate;

    }


    /**
     * Try to parse the given date as a HTTP date.
     */
    public static final long parseDate(String value, 
                                       DateFormat[] threadLocalformats) {

        Long cachedDate = null;
        try {
            cachedDate = (Long) parseCache.get(value);
        } catch (Exception e) {
        }
        if (cachedDate != null)
            return cachedDate.longValue();

        Long date = null;
        if (threadLocalformats != null) {
            date = internalParseDate(value, threadLocalformats);
            synchronized (parseCache) {
                updateCache(parseCache, value, date);
            }
        } else {
            synchronized (parseCache) {
                date = internalParseDate(value, formats);
                updateCache(parseCache, value, date);
            }
        }
        if (date == null) {
            return (-1L);
        } else {
            return date.longValue();
        }

    }


    /**
     * Parse date with given formatters.
     */
    private static final Long internalParseDate
        (String value, DateFormat[] formats) {
        Date date = null;
        for (int i = 0; (date == null) && (i < formats.length); i++) {
            try {
                date = formats[i].parse(value);
            } catch (ParseException e) {
                ;
            }
        }
        if (date == null) {
            return null;
        }
        return new Long(date.getTime());
    }


    /**
     * Update cache.
     */
    private static final void updateCache(HashMap cache, Object key, 
                                          Object value) {
        if (value == null) {
            return;
        }
        if (cache.size() > 1000) {
            cache.clear();
        }
        cache.put(key, value);
    }


}

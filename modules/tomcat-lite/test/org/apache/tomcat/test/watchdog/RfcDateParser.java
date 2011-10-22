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
package org.apache.tomcat.test.watchdog;

import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A parser for date strings commonly found in http and email headers that
 * follow various RFC conventions.  Given a date-string, the parser will
 * attempt to parse it by trying matches with a set of patterns, returning
 * null on failure, a Date object on success.
 *
 * @author Ramesh.Mandava
 */
public class RfcDateParser {

    private boolean isGMT = false;

    static final String[] standardFormats = {
	"EEEE', 'dd-MMM-yy HH:mm:ss z",   // RFC 850 (obsoleted by 1036)
	"EEEE', 'dd-MMM-yy HH:mm:ss",     // ditto but no tz. Happens too often
	"EEE', 'dd-MMM-yyyy HH:mm:ss z",  // RFC 822/1123
	"EEE', 'dd MMM yyyy HH:mm:ss z",  // REMIND what rfc? Apache/1.1
	"EEEE', 'dd MMM yyyy HH:mm:ss z", // REMIND what rfc? Apache/1.1
	"EEE', 'dd MMM yyyy hh:mm:ss z",  // REMIND what rfc? Apache/1.1
	"EEEE', 'dd MMM yyyy hh:mm:ss z", // REMIND what rfc? Apache/1.1
	"EEE MMM dd HH:mm:ss z yyyy",      // Date's string output format
	"EEE MMM dd HH:mm:ss yyyy",	  // ANSI C asctime format()
	"EEE', 'dd-MMM-yy HH:mm:ss",      // No time zone 2 digit year RFC 1123
 	"EEE', 'dd-MMM-yyyy HH:mm:ss"     // No time zone RFC 822/1123
    };

    /* because there are problems with JDK1.1.6/SimpleDateFormat with
     * recognizing GMT, we have to create this workaround with the following
     * hardcoded strings */
    static final String[] gmtStandardFormats = {
	"EEEE',' dd-MMM-yy HH:mm:ss 'GMT'",   // RFC 850 (obsoleted by 1036)
	"EEE',' dd-MMM-yyyy HH:mm:ss 'GMT'",  // RFC 822/1123
	"EEE',' dd MMM yyyy HH:mm:ss 'GMT'",  // REMIND what rfc? Apache/1.1
	"EEEE',' dd MMM yyyy HH:mm:ss 'GMT'", // REMIND what rfc? Apache/1.1
	"EEE',' dd MMM yyyy hh:mm:ss 'GMT'",  // REMIND what rfc? Apache/1.1
	"EEEE',' dd MMM yyyy hh:mm:ss 'GMT'", // REMIND what rfc? Apache/1.1
	"EEE MMM dd HH:mm:ss 'GMT' yyyy"      // Date's string output format
    };

    String dateString;

    public RfcDateParser(String dateString) {
	this.dateString = dateString.trim();
	if (this.dateString.indexOf("GMT") != -1) {
	    isGMT = true;
	}
    }

    public Date getDate() {

        int arrayLen = isGMT ? gmtStandardFormats.length : standardFormats.length;
        for (int i = 0; i < arrayLen; i++) {
            Date d = null;

            if (isGMT) {
                d = tryParsing(gmtStandardFormats[i]);
            } else {
                d = tryParsing(standardFormats[i]);
            }
            if (d != null) {
                return d;
            }

        }

        return null;
    }

    private Date tryParsing(String format) {

	java.text.SimpleDateFormat df = new java.text.SimpleDateFormat(format, Locale.US);
	if (isGMT) {
	    df.setTimeZone(TimeZone.getTimeZone("GMT"));
	}
	try {
		return df.parse(dateString);
	} catch (Exception e) {
	    return null;
	}
    }
} /* class RfcDateParser */

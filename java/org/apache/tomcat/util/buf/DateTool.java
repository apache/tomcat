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

package org.apache.tomcat.util.buf;

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.tomcat.util.res.StringManager;

/**
 *  Common place for date utils.
 *
 * @deprecated Will be replaced with a more efficient impl, based on
 * FastDateFormat, with an API using less objects.
 * @author dac@eng.sun.com
 * @author Jason Hunter [jch@eng.sun.com]
 * @author James Todd [gonzo@eng.sun.com]
 * @author Costin Manolache
 */
public class DateTool {

    /** US locale - all HTTP dates are in english
     */
    private final static Locale LOCALE_US = Locale.US;

    /** GMT timezone - all HTTP dates are on GMT
     */
    public final static TimeZone GMT_ZONE = TimeZone.getTimeZone("GMT");

    /** format for RFC 1123 date string -- "Sun, 06 Nov 1994 08:49:37 GMT"
     */
    public final static String RFC1123_PATTERN =
        "EEE, dd MMM yyyy HH:mm:ss z";

    // format for RFC 1036 date string -- "Sunday, 06-Nov-94 08:49:37 GMT"
    public final static String rfc1036Pattern =
        "EEEEEEEEE, dd-MMM-yy HH:mm:ss z";

    // format for C asctime() date string -- "Sun Nov  6 08:49:37 1994"
    public final static String asctimePattern =
        "EEE MMM d HH:mm:ss yyyy";

    /** Pattern used for old cookies
     */
    private final static String OLD_COOKIE_PATTERN = "EEE, dd-MMM-yyyy HH:mm:ss z";

    /** DateFormat to be used to format dates. Called from MessageBytes
     */
    private final static DateFormat rfc1123Format =
	new SimpleDateFormat(RFC1123_PATTERN, LOCALE_US);
    
    /** DateFormat to be used to format old netscape cookies
	Called from ServerCookie
     */
    private final static DateFormat oldCookieFormat =
	new SimpleDateFormat(OLD_COOKIE_PATTERN, LOCALE_US);
    
    private final static DateFormat rfc1036Format =
	new SimpleDateFormat(rfc1036Pattern, LOCALE_US);
    
    private final static DateFormat asctimeFormat =
	new SimpleDateFormat(asctimePattern, LOCALE_US);
    
    static {
	rfc1123Format.setTimeZone(GMT_ZONE);
	oldCookieFormat.setTimeZone(GMT_ZONE);
	rfc1036Format.setTimeZone(GMT_ZONE);
	asctimeFormat.setTimeZone(GMT_ZONE);
    }
 
    private static String rfc1123DS;
    private static long   rfc1123Sec;

    private static StringManager sm =
        StringManager.getManager("org.apache.tomcat.util.buf.res");

    // Called from MessageBytes.getTime()
    static long parseDate( MessageBytes value ) {
     	return parseDate( value.toString());
    }

    // Called from MessageBytes.setTime
    /** 
     */
    public static String format1123( Date d ) {
	String dstr=null;
	synchronized(rfc1123Format) {
	    dstr = format1123(d, rfc1123Format);
	}
	return dstr;
    } 

    public static String format1123( Date d,DateFormat df ) {
        long dt = d.getTime() / 1000;
        if ((rfc1123DS != null) && (dt == rfc1123Sec))
            return rfc1123DS;
        rfc1123DS  = df.format( d );
        rfc1123Sec = dt;
        return rfc1123DS;
    } 


    // Called from ServerCookie
    /** 
     */
    public static void formatOldCookie( Date d, StringBuffer sb,
					  FieldPosition fp )
    {
	synchronized(oldCookieFormat) {
	    oldCookieFormat.format( d, sb, fp );
	}
    }

    // Called from ServerCookie
    public static String formatOldCookie( Date d )
    {
	String ocf=null;
	synchronized(oldCookieFormat) {
	    ocf= oldCookieFormat.format( d );
	}
	return ocf;
    }

    
    /** Called from HttpServletRequest.getDateHeader().
	Not efficient - but not very used.
     */
    public static long parseDate( String dateString ) {
	DateFormat [] format = {rfc1123Format,rfc1036Format,asctimeFormat};
	return parseDate(dateString,format);
    }
    public static long parseDate( String dateString, DateFormat []format ) {
	Date date=null;
	for(int i=0; i < format.length; i++) {
	    try {
		date = format[i].parse(dateString);
		return date.getTime();
	    } catch (ParseException e) { }
	    catch (StringIndexOutOfBoundsException e) { }
	}
	String msg = sm.getString("httpDate.pe", dateString);
	throw new IllegalArgumentException(msg);
    }

}

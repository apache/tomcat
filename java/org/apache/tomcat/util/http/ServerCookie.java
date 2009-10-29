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

import java.io.Serializable;
import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;


/**
 *  Server-side cookie representation.
 *  Allows recycling and uses MessageBytes as low-level
 *  representation ( and thus the byte-> char conversion can be delayed
 *  until we know the charset ).
 *
 *  Tomcat.core uses this recyclable object to represent cookies,
 *  and the facade will convert it to the external representation.
 */
public class ServerCookie implements Serializable {
    
    
    // Version 0 (Netscape) attributes
    private MessageBytes name=MessageBytes.newInstance();
    private MessageBytes value=MessageBytes.newInstance();
    // Expires - Not stored explicitly. Generated from Max-Age (see V1)
    private MessageBytes path=MessageBytes.newInstance();
    private MessageBytes domain=MessageBytes.newInstance();
    private boolean secure;
    
    // Version 1 (RFC2109) attributes
    private MessageBytes comment=MessageBytes.newInstance();
    private int maxAge = -1;
    private int version = 0;

    // Other fields
    private static final String OLD_COOKIE_PATTERN =
        "EEE, dd-MMM-yyyy HH:mm:ss z";
    private static final ThreadLocal<DateFormat> OLD_COOKIE_FORMAT =
        new ThreadLocal<DateFormat>() {
        protected DateFormat initialValue() {
            DateFormat df =
                new SimpleDateFormat(OLD_COOKIE_PATTERN, Locale.US);
            df.setTimeZone(TimeZone.getTimeZone("GMT"));
            return df;
        }
    };
    private static final String ancientDate;

    /**
     * If set to true, we parse cookies strictly according to the servlet,
     * cookie and HTTP specs by default.
     */
    public static final boolean STRICT_SERVLET_COMPLIANCE;

    /**
     * If set to false, we don't use the IE6/7 Max-Age/Expires work around.
     * Default is usually true. If STRICT_SERVLET_COMPLIANCE==true then default
     * is false. Explicitly setting always takes priority.
     */
    public static final boolean ALWAYS_ADD_EXPIRES;

    /**
     * If set to true, the <code>/</code> character will be treated as a
     * separator. Default is usually false. If STRICT_SERVLET_COMPLIANCE==true
     * then default is true. Explicitly setting always takes priority.
     */
    public static final boolean FWD_SLASH_IS_SEPARATOR;


    static {
        ancientDate = OLD_COOKIE_FORMAT.get().format(new Date(10000));
        
        STRICT_SERVLET_COMPLIANCE = Boolean.valueOf(System.getProperty(
                "org.apache.catalina.STRICT_SERVLET_COMPLIANCE",
                "false")).booleanValue();
        

        String alwaysAddExpires = System.getProperty(
                "org.apache.tomcat.util.http.ServerCookie.ALWAYS_ADD_EXPIRES");
        if (alwaysAddExpires == null) {
            ALWAYS_ADD_EXPIRES = !STRICT_SERVLET_COMPLIANCE;
        } else {
            ALWAYS_ADD_EXPIRES =
                Boolean.valueOf(alwaysAddExpires).booleanValue();
        }
        
        String  fwdSlashIsSeparator = System.getProperty(
                "org.apache.tomcat.util.http.ServerCookie.FWD_SLASH_IS_SEPARATOR");
        if (fwdSlashIsSeparator == null) {
            FWD_SLASH_IS_SEPARATOR = STRICT_SERVLET_COMPLIANCE;
        } else {
            FWD_SLASH_IS_SEPARATOR =
                Boolean.valueOf(fwdSlashIsSeparator).booleanValue();
        }
    }

    // Note: Servlet Spec =< 2.5 only refers to Netscape and RFC2109,
    // not RFC2965

    // Version 1 (RFC2965) attributes
    // TODO Add support for CommentURL
    // Discard - implied by maxAge <0
    // TODO Add support for Port

    public ServerCookie() {
    }

    public void recycle() {
        path.recycle();
        name.recycle();
        value.recycle();
        comment.recycle();
        maxAge=-1;
        path.recycle();
        domain.recycle();
        version=0;
        secure=false;
    }

    public MessageBytes getComment() {
        return comment;
    }

    public MessageBytes getDomain() {
        return domain;
    }

    public void setMaxAge(int expiry) {
        maxAge = expiry;
    }

    public int getMaxAge() {
        return maxAge;
    }

    public MessageBytes getPath() {
        return path;
    }

    public void setSecure(boolean flag) {
        secure = flag;
    }

    public boolean getSecure() {
        return secure;
    }

    public MessageBytes getName() {
        return name;
    }

    public MessageBytes getValue() {
        return value;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int v) {
        version = v;
    }


    // -------------------- utils --------------------

    public String toString() {
        return "Cookie " + getName() + "=" + getValue() + " ; "
            + getVersion() + " " + getPath() + " " + getDomain();
    }
    
    private static final String tspecials = ",; ";
    private static final String tspecials2 = "()<>@,;:\\\"/[]?={} \t";
    private static final String tspecials2NoSlash = "()<>@,;:\\\"[]?={} \t";

    /*
     * Tests a string and returns true if the string counts as a
     * reserved token in the Java language.
     *
     * @param value the <code>String</code> to be tested
     *
     * @return      <code>true</code> if the <code>String</code> is a reserved
     *              token; <code>false</code> if it is not
     */
    public static boolean isToken(String value) {
        return isToken(value,null);
    }
    
    public static boolean isToken(String value, String literals) {
        String tspecials = (literals==null?ServerCookie.tspecials:literals);
        if( value==null) return true;
        int len = value.length();

        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);

            if (tspecials.indexOf(c) != -1)
                return false;
        }
        return true;
    }

    public static boolean containsCTL(String value, int version) {
        if( value==null) return false;
        int len = value.length();
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c >= 0x7f) {
                if (c == 0x09)
                    continue; //allow horizontal tabs
                return true;
            }
        }
        return false;
    }

    public static boolean isToken2(String value) {
        return isToken2(value,null);
    }

    public static boolean isToken2(String value, String literals) {
        String tspecials2 = (literals==null?ServerCookie.tspecials2:literals);
        if( value==null) return true;
        int len = value.length();

        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if (tspecials2.indexOf(c) != -1)
                return false;
        }
        return true;
    }

    // -------------------- Cookie parsing tools

    
    /**
     * Return the header name to set the cookie, based on cookie version.
     */
    public String getCookieHeaderName() {
        return getCookieHeaderName(version);
    }

    /**
     * Return the header name to set the cookie, based on cookie version.
     */
    public static String getCookieHeaderName(int version) {
        // TODO Re-enable logging when RFC2965 is implemented
        // log( (version==1) ? "Set-Cookie2" : "Set-Cookie");
        if (version == 1) {
            // XXX RFC2965 not referenced in Servlet Spec
            // Set-Cookie2 is not supported by Netscape 4, 6, IE 3, 5
            // Set-Cookie2 is supported by Lynx and Opera
            // Need to check on later IE and FF releases but for now... 
            // RFC2109
            return "Set-Cookie";
            // return "Set-Cookie2";
        } else {
            // Old Netscape
            return "Set-Cookie";
        }
    }

    // TODO RFC2965 fields also need to be passed
    public static void appendCookieValue( StringBuffer headerBuf,
                                          int version,
                                          String name,
                                          String value,
                                          String path,
                                          String domain,
                                          String comment,
                                          int maxAge,
                                          boolean isSecure,
                                          boolean isHttpOnly)
    {
        StringBuffer buf = new StringBuffer();
        // Servlet implementation checks name
        buf.append( name );
        buf.append("=");
        // Servlet implementation does not check anything else
        
        version = maybeQuote2(version, buf, value,true);

        // Add version 1 specific information
        if (version == 1) {
            // Version=1 ... required
            buf.append ("; Version=1");

            // Comment=comment
            if ( comment!=null ) {
                buf.append ("; Comment=");
                maybeQuote2(version, buf, comment);
            }
        }
        
        // Add domain information, if present
        if (domain!=null) {
            buf.append("; Domain=");
            maybeQuote2(version, buf, domain);
        }

        // Max-Age=secs ... or use old "Expires" format
        // TODO RFC2965 Discard
        if (maxAge >= 0) {
            if (version > 0) {
                buf.append ("; Max-Age=");
                buf.append (maxAge);
            }
            // IE6, IE7 and possibly other browsers don't understand Max-Age.
            // They do understand Expires, even with V1 cookies!
            if (version == 0 || ALWAYS_ADD_EXPIRES) {
                // Wdy, DD-Mon-YY HH:MM:SS GMT ( Expires Netscape format )
                buf.append ("; Expires=");
                // To expire immediately we need to set the time in past
                if (maxAge == 0)
                    buf.append( ancientDate );
                else
                    OLD_COOKIE_FORMAT.get().format(
                            new Date(System.currentTimeMillis() +
                                    maxAge*1000L),
                            buf, new FieldPosition(0));
            }
        }

        // Path=path
        if (path!=null) {
            buf.append ("; Path=");
            if (version==0) {
                maybeQuote2(version, buf, path);
            } else {
                if (FWD_SLASH_IS_SEPARATOR) {
                    maybeQuote2(version, buf, path, ServerCookie.tspecials,
                            false);
                } else {
                    maybeQuote2(version, buf, path,
                            ServerCookie.tspecials2NoSlash, false);
                }
            }
        }

        // Secure
        if (isSecure) {
          buf.append ("; Secure");
        }
        
        // HttpOnly
        if (isHttpOnly) {
            buf.append("; HttpOnly");
        }
        headerBuf.append(buf);
    }

    public static boolean alreadyQuoted (String value) {
        if (value==null || value.length()==0) return false;
        return (value.charAt(0)=='\"' && value.charAt(value.length()-1)=='\"');
    }
    
    /**
     * Quotes values using rules that vary depending on Cookie version.
     * @param version
     * @param buf
     * @param value
     */
    public static int maybeQuote2 (int version, StringBuffer buf, String value) {
        return maybeQuote2(version,buf,value,false);
    }

    public static int maybeQuote2 (int version, StringBuffer buf, String value, boolean allowVersionSwitch) {
        return maybeQuote2(version,buf,value,null,allowVersionSwitch);
    }

    public static int maybeQuote2 (int version, StringBuffer buf, String value, String literals, boolean allowVersionSwitch) {
        if (value==null || value.length()==0) {
            buf.append("\"\"");
        }else if (containsCTL(value,version)) 
            throw new IllegalArgumentException("Control character in cookie value, consider BASE64 encoding your value");
        else if (alreadyQuoted(value)) {
            buf.append('"');
            buf.append(escapeDoubleQuotes(value,1,value.length()-1));
            buf.append('"');
        } else if (allowVersionSwitch && (!STRICT_SERVLET_COMPLIANCE) && version==0 && !isToken2(value, literals)) {
            buf.append('"');
            buf.append(escapeDoubleQuotes(value,0,value.length()));
            buf.append('"');
            version = 1;
        } else if (version==0 && !isToken(value,literals)) {
            buf.append('"');
            buf.append(escapeDoubleQuotes(value,0,value.length()));
            buf.append('"');
        } else if (version==1 && !isToken2(value,literals)) {
            buf.append('"');
            buf.append(escapeDoubleQuotes(value,0,value.length()));
            buf.append('"');
        }else {
            buf.append(value);
        }
        return version;
    }


    /**
     * Escapes any double quotes in the given string.
     *
     * @param s the input string
     * @param beginIndex start index inclusive
     * @param endIndex exclusive
     * @return The (possibly) escaped string
     */
    private static String escapeDoubleQuotes(String s, int beginIndex, int endIndex) {

        if (s == null || s.length() == 0 || s.indexOf('"') == -1) {
            return s;
        }

        StringBuffer b = new StringBuffer();
        for (int i = beginIndex; i < endIndex; i++) {
            char c = s.charAt(i);
            if (c == '\\' ) {
                b.append(c);
                //ignore the character after an escape, just append it
                if (++i>=endIndex) throw new IllegalArgumentException("Invalid escape character in cookie value.");
                b.append(s.charAt(i));
            } else if (c == '"')
                b.append('\\').append('"');
            else
                b.append(c);
        }

        return b.toString();
    }

    /**
     * Unescapes any double quotes in the given cookie value.
     *
     * @param bc The cookie value to modify
     */
    public static void unescapeDoubleQuotes(ByteChunk bc) {

        if (bc == null || bc.getLength() == 0 || bc.indexOf('"', 0) == -1) {
            return;
        }

        int src = bc.getStart();
        int end = bc.getEnd();
        int dest = src;
        byte[] buffer = bc.getBuffer();
        
        while (src < end) {
            if (buffer[src] == '\\' && src < end && buffer[src+1]  == '"') {
                src++;
            }
            buffer[dest] = buffer[src];
            dest ++;
            src ++;
        }
        bc.setEnd(dest);
    }
}


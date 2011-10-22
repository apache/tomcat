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

package org.apache.tomcat.lite.http;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.tomcat.lite.io.BBuffer;
import org.apache.tomcat.lite.io.CBuffer;


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
    private BBuffer name = BBuffer.allocate();
    private BBuffer value = BBuffer.allocate();

    private CBuffer nameC = CBuffer.newInstance();

    // Expires - Not stored explicitly. Generated from Max-Age (see V1)
    private BBuffer path = BBuffer.allocate();
    private BBuffer domain = BBuffer.allocate();
    private boolean secure;

    // Version 1 (RFC2109) attributes
    private BBuffer comment = BBuffer.allocate();
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


    static {
        ancientDate = OLD_COOKIE_FORMAT.get().format(new Date(10000));
    }

    /**
     * If set to true, we parse cookies according to the servlet spec,
     */
    public static final boolean STRICT_SERVLET_COMPLIANCE =
        Boolean.valueOf(System.getProperty("org.apache.catalina.STRICT_SERVLET_COMPLIANCE", "false")).booleanValue();

    /**
     * If set to false, we don't use the IE6/7 Max-Age/Expires work around
     */
    public static final boolean ALWAYS_ADD_EXPIRES =
        Boolean.valueOf(System.getProperty("org.apache.tomcat.util.http.ServerCookie.ALWAYS_ADD_EXPIRES", "true")).booleanValue();

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

    public BBuffer getComment() {
        return comment;
    }

    public BBuffer getDomain() {
        return domain;
    }

    public void setMaxAge(int expiry) {
        maxAge = expiry;
    }

    public int getMaxAge() {
        return maxAge;
    }

    public BBuffer getPath() {
        return path;
    }

    public void setSecure(boolean flag) {
        secure = flag;
    }

    public boolean getSecure() {
        return secure;
    }

    public BBuffer getName() {
        return name;
    }

    public BBuffer getValue() {
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
                maybeQuote2(version, buf, path, ServerCookie.tspecials2NoSlash, false);
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
    public static void unescapeDoubleQuotes(BBuffer bc) {

        if (bc == null || bc.getLength() == 0 || bc.indexOf('"', 0) == -1) {
            return;
        }

        int src = bc.getStart();
        int end = bc.getEnd();
        int dest = src;
        byte[] buffer = bc.array();

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

    /*
    List of Separator Characters (see isSeparator())
    Excluding the '/' char violates the RFC, but
    it looks like a lot of people put '/'
    in unquoted values: '/': ; //47
    '\t':9 ' ':32 '\"':34 '\'':39 '(':40 ')':41 ',':44 ':':58 ';':59 '<':60
    '=':61 '>':62 '?':63 '@':64 '[':91 '\\':92 ']':93 '{':123 '}':125
    */
    public static final char SEPARATORS[] = { '\t', ' ', '\"', '\'', '(', ')', ',',
        ':', ';', '<', '=', '>', '?', '@', '[', '\\', ']', '{', '}' };

    protected static final boolean separators[] = new boolean[128];
    static {
        for (int i = 0; i < 128; i++) {
            separators[i] = false;
        }
        for (int i = 0; i < SEPARATORS.length; i++) {
            separators[SEPARATORS[i]] = true;
        }
    }

    /** Add all Cookie found in the headers of a request.
     */
    public  static void processCookies(List<ServerCookie> cookies,
            List<ServerCookie> cookiesCache,
            HttpMessage.HttpMessageBytes msgBytes ) {

        // process each "cookie" header
        for (int i = 0; i < msgBytes.headerCount; i++) {
            if (msgBytes.getHeaderName(i).equalsIgnoreCase("Cookie")) {
                BBuffer bc = msgBytes.getHeaderValue(i);
                if (bc.remaining() == 0) {
                    continue;
                }
                processCookieHeader(cookies, cookiesCache,
                        bc.array(),
                        bc.getOffset(),
                        bc.getLength());

            }

        }
    }

    /**
     * Returns true if the byte is a separator character as
     * defined in RFC2619. Since this is called often, this
     * function should be organized with the most probable
     * outcomes first.
     * JVK
     */
    private static final boolean isSeparator(final byte c) {
         if (c > 0 && c < 126)
             return separators[c];
         else
             return false;
    }

    /**
     * Returns true if the byte is a whitespace character as
     * defined in RFC2619
     * JVK
     */
    private static final boolean isWhiteSpace(final byte c) {
        // This switch statement is slightly slower
        // for my vm than the if statement.
        // Java(TM) 2 Runtime Environment, Standard Edition (build 1.5.0_07-164)
        /*
        switch (c) {
        case ' ':;
        case '\t':;
        case '\n':;
        case '\r':;
        case '\f':;
            return true;
        default:;
            return false;
        }
        */
       if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f')
           return true;
       else
           return false;
    }

    /**
     * Parses a cookie header after the initial "Cookie:"
     * [WS][$]token[WS]=[WS](token|QV)[;|,]
     * RFC 2965
     * JVK
     */
    public static final void processCookieHeader(
            List<ServerCookie> cookies,
            List<ServerCookie> cookiesCache,
            byte bytes[], int off, int len){
        if( len<=0 || bytes==null ) return;
        int end=off+len;
        int pos=off;
        int nameStart=0;
        int nameEnd=0;
        int valueStart=0;
        int valueEnd=0;
        int version = 0;
        ServerCookie sc=null;
        boolean isSpecial;
        boolean isQuoted;

        while (pos < end) {
            isSpecial = false;
            isQuoted = false;

            // Skip whitespace and non-token characters (separators)
            while (pos < end &&
                   (isSeparator(bytes[pos]) || isWhiteSpace(bytes[pos])))
                {pos++; }

            if (pos >= end)
                return;

            // Detect Special cookies
            if (bytes[pos] == '$') {
                isSpecial = true;
                pos++;
            }

            // Get the cookie name. This must be a token
            valueEnd = valueStart = nameStart = pos;
            pos = nameEnd = getTokenEndPosition(bytes,pos,end);

            // Skip whitespace
            while (pos < end && isWhiteSpace(bytes[pos])) {pos++; }


            // Check for an '=' -- This could also be a name-only
            // cookie at the end of the cookie header, so if we
            // are past the end of the header, but we have a name
            // skip to the name-only part.
            if (pos < end && bytes[pos] == '=') {

                // Skip whitespace
                do {
                    pos++;
                } while (pos < end && isWhiteSpace(bytes[pos]));

                if (pos >= end)
                    return;

                // Determine what type of value this is, quoted value,
                // token, name-only with an '=', or other (bad)
                switch (bytes[pos]) {
                case '"': // Quoted Value
                    isQuoted = true;
                    valueStart=pos + 1; // strip "
                    // getQuotedValue returns the position before
                    // at the last qoute. This must be dealt with
                    // when the bytes are copied into the cookie
                    valueEnd=getQuotedValueEndPosition(bytes,
                                                       valueStart, end);
                    // We need pos to advance
                    pos = valueEnd;
                    // Handles cases where the quoted value is
                    // unterminated and at the end of the header,
                    // e.g. [myname="value]
                    if (pos >= end)
                        return;
                    break;
                case ';':
                case ',':
                    // Name-only cookie with an '=' after the name token
                    // This may not be RFC compliant
                    valueStart = valueEnd = -1;
                    // The position is OK (On a delimiter)
                    break;
                default:
                    if (!isSeparator(bytes[pos])) {
                        // Token
                        valueStart=pos;
                        // getToken returns the position at the delimeter
                        // or other non-token character
                        valueEnd=getTokenEndPosition(bytes, valueStart, end);
                        // We need pos to advance
                        pos = valueEnd;
                    } else  {
                        // INVALID COOKIE, advance to next delimiter
                        // The starting character of the cookie value was
                        // not valid.
                        //log("Invalid cookie. Value not a token or quoted value");
                        while (pos < end && bytes[pos] != ';' &&
                               bytes[pos] != ',')
                            {pos++; }
                        pos++;
                        // Make sure no special avpairs can be attributed to
                        // the previous cookie by setting the current cookie
                        // to null
                        sc = null;
                        continue;
                    }
                }
            } else {
                // Name only cookie
                valueStart = valueEnd = -1;
                pos = nameEnd;

            }

            // We should have an avpair or name-only cookie at this
            // point. Perform some basic checks to make sure we are
            // in a good state.

            // Skip whitespace
            while (pos < end && isWhiteSpace(bytes[pos])) {pos++; }


            // Make sure that after the cookie we have a separator. This
            // is only important if this is not the last cookie pair
            while (pos < end && bytes[pos] != ';' && bytes[pos] != ',') {
                pos++;
            }

            pos++;

            /*
            if (nameEnd <= nameStart || valueEnd < valueStart ) {
                // Something is wrong, but this may be a case
                // of having two ';' characters in a row.
                // log("Cookie name/value does not conform to RFC 2965");
                // Advance to next delimiter (ignoring everything else)
                while (pos < end && bytes[pos] != ';' && bytes[pos] != ',')
                    { pos++; };
                pos++;
                // Make sure no special cookies can be attributed to
                // the previous cookie by setting the current cookie
                // to null
                sc = null;
                continue;
            }
            */

            // All checks passed. Add the cookie, start with the
            // special avpairs first
            if (isSpecial) {
                isSpecial = false;
                // $Version must be the first avpair in the cookie header
                // (sc must be null)
                if (equals( "Version", bytes, nameStart, nameEnd) &&
                    sc == null) {
                    // Set version
                    if( bytes[valueStart] =='1' && valueEnd == (valueStart+1)) {
                        version=1;
                    } else {
                        // unknown version (Versioning is not very strict)
                    }
                    continue;
                }

                // We need an active cookie for Path/Port/etc.
                if (sc == null) {
                    continue;
                }

                // Domain is more common, so it goes first
                if (equals( "Domain", bytes, nameStart, nameEnd)) {
                    sc.getDomain().setBytes( bytes,
                                           valueStart,
                                           valueEnd-valueStart);
                    continue;
                }

                if (equals( "Path", bytes, nameStart, nameEnd)) {
                    sc.getPath().setBytes( bytes,
                                           valueStart,
                                           valueEnd-valueStart);
                    continue;
                }


                if (equals( "Port", bytes, nameStart, nameEnd)) {
                    // sc.getPort is not currently implemented.
                    // sc.getPort().setBytes( bytes,
                    //                        valueStart,
                    //                        valueEnd-valueStart );
                    continue;
                }

                // Unknown cookie, complain
                //log("Unknown Special Cookie");

            } else { // Normal Cookie
                // use a previous value from cache, if any (to avoid GC - tomcat
                // legacy )
                if (cookiesCache.size() > cookies.size()) {
                    sc = cookiesCache.get(cookies.size());
                    cookies.add(sc);
                } else {
                    sc = new ServerCookie();
                    cookiesCache.add(sc);
                    cookies.add(sc);
                }
                sc.setVersion( version );
                sc.getName().append( bytes, nameStart,
                                       nameEnd-nameStart);

                if (valueStart != -1) { // Normal AVPair
                    sc.getValue().append( bytes, valueStart,
                            valueEnd-valueStart);
                    if (isQuoted) {
                        // We know this is a byte value so this is safe
                        ServerCookie.unescapeDoubleQuotes(
                                sc.getValue());
                    }
                } else {
                    // Name Only
                    sc.getValue().recycle();
                }
                sc.nameC.recycle();
                sc.nameC.append(sc.getName());
                continue;
            }
        }
    }

    /**
     * Given the starting position of a token, this gets the end of the
     * token, with no separator characters in between.
     * JVK
     */
    private static final int getTokenEndPosition(byte bytes[], int off, int end){
        int pos = off;
        while (pos < end && !isSeparator(bytes[pos])) {pos++; }

        if (pos > end)
            return end;
        return pos;
    }

    /**
     * Given a starting position after an initial quote chracter, this gets
     * the position of the end quote. This escapes anything after a '\' char
     * JVK RFC 2616
     */
    private static final int getQuotedValueEndPosition(byte bytes[], int off, int end){
        int pos = off;
        while (pos < end) {
            if (bytes[pos] == '"') {
                return pos;
            } else if (bytes[pos] == '\\' && pos < (end - 1)) {
                pos+=2;
            } else {
                pos++;
            }
        }
        // Error, we have reached the end of the header w/o a end quote
        return end;
    }


    public static boolean equals( String s, byte b[], int start, int end) {
        int blen = end-start;
        if (b == null || blen != s.length()) {
            return false;
        }
        int boff = start;
        for (int i = 0; i < blen; i++) {
            if (b[boff++] != s.charAt(i)) {
                return false;
            }
        }
        return true;
    }

}


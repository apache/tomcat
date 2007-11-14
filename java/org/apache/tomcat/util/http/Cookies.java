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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.StringTokenizer;

import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;

/**
 * A collection of cookies - reusable and tuned for server side performance.
 * Based on RFC2965 ( and 2109 )
 *
 * This class is not synchronized.
 *
 * @author Costin Manolache
 * @author kevin seguin
 */
public final class Cookies { // extends MultiMap {

    private static org.apache.juli.logging.Log log=
        org.apache.juli.logging.LogFactory.getLog(Cookies.class );
    
    // expected average number of cookies per request
    public static final int INITIAL_SIZE=4; 
    ServerCookie scookies[]=new ServerCookie[INITIAL_SIZE];
    int cookieCount=0;
    boolean unprocessed=true;

    MimeHeaders headers;

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

    /**
     *  Construct a new cookie collection, that will extract
     *  the information from headers.
     *
     * @param headers Cookies are lazy-evaluated and will extract the
     *     information from the provided headers.
     */
    public Cookies(MimeHeaders headers) {
        this.headers=headers;
    }

    /**
     * Construct a new uninitialized cookie collection.
     * Use {@link #setHeaders} to initialize.
     */
    // [seguin] added so that an empty Cookies object could be
    // created, have headers set, then recycled.
    public Cookies() {
    }

    /**
     * Set the headers from which cookies will be pulled.
     * This has the side effect of recycling the object.
     *
     * @param headers Cookies are lazy-evaluated and will extract the
     *     information from the provided headers.
     */
    // [seguin] added so that an empty Cookies object could be
    // created, have headers set, then recycled.
    public void setHeaders(MimeHeaders headers) {
        recycle();
        this.headers=headers;
    }

    /**
     * Recycle.
     */
    public void recycle() {
            for( int i=0; i< cookieCount; i++ ) {
            if( scookies[i]!=null )
                scookies[i].recycle();
        }
        cookieCount=0;
        unprocessed=true;
    }

    /**
     * EXPENSIVE!!!  only for debugging.
     */
    public String toString() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("=== Cookies ===");
        int count = getCookieCount();
        for (int i = 0; i < count; ++i) {
            pw.println(getCookie(i).toString());
        }
        return sw.toString();
    }

    // -------------------- Indexed access --------------------
    
    public ServerCookie getCookie( int idx ) {
        if( unprocessed ) {
            getCookieCount(); // will also update the cookies
        }
        return scookies[idx];
    }

    public int getCookieCount() {
        if( unprocessed ) {
            unprocessed=false;
            processCookies(headers);
        }
        return cookieCount;
    }

    // -------------------- Adding cookies --------------------

    /** Register a new, unitialized cookie. Cookies are recycled, and
     *  most of the time an existing ServerCookie object is returned.
     *  The caller can set the name/value and attributes for the cookie
     */
    public ServerCookie addCookie() {
        if( cookieCount >= scookies.length  ) {
            ServerCookie scookiesTmp[]=new ServerCookie[2*cookieCount];
            System.arraycopy( scookies, 0, scookiesTmp, 0, cookieCount);
            scookies=scookiesTmp;
        }
        
        ServerCookie c = scookies[cookieCount];
        if( c==null ) {
            c= new ServerCookie();
            scookies[cookieCount]=c;
        }
        cookieCount++;
        return c;
    }


    // code from CookieTools 

    /** Add all Cookie found in the headers of a request.
     */
    public  void processCookies( MimeHeaders headers ) {
        if( headers==null )
            return;// nothing to process
        // process each "cookie" header
        int pos=0;
        while( pos>=0 ) {
            // Cookie2: version ? not needed
            pos=headers.findHeader( "Cookie", pos );
            // no more cookie headers headers
            if( pos<0 ) break;

            MessageBytes cookieValue=headers.getValue( pos );
            if( cookieValue==null || cookieValue.isNull() ) {
                pos++;
                continue;
            }

            // Uncomment to test the new parsing code
            if( cookieValue.getType() == MessageBytes.T_BYTES ) {
                if( dbg>0 ) log( "Parsing b[]: " + cookieValue.toString());
                ByteChunk bc=cookieValue.getByteChunk();
                processCookieHeader( bc.getBytes(),
                                     bc.getOffset(),
                                     bc.getLength());
            } else {
                if( dbg>0 ) log( "Parsing S: " + cookieValue.toString());
                processCookieHeader( cookieValue.toString() );
            }
            pos++;// search from the next position
        }
    }

    // XXX will be refactored soon!
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
    

    // ---------------------------------------------------------
    // -------------------- DEPRECATED, OLD --------------------
    
    private void processCookieHeader(  String cookieString )
    {
        if( dbg>0 ) log( "Parsing cookie header " + cookieString );
        // normal cookie, with a string value.
        // This is the original code, un-optimized - it shouldn't
        // happen in normal case

        StringTokenizer tok = new StringTokenizer(cookieString,
                                                  ";", false);
        while (tok.hasMoreTokens()) {
            String token = tok.nextToken();
            int i = token.indexOf("=");
            if (i > -1) {
                
                // XXX
                // the trims here are a *hack* -- this should
                // be more properly fixed to be spec compliant
                
                String name = token.substring(0, i).trim();
                String value = token.substring(i+1, token.length()).trim();
                // RFC 2109 and bug 
                value=stripQuote( value );
                ServerCookie cookie = addCookie();
                
                cookie.getName().setString(name);
                cookie.getValue().setString(value);
                if( dbg > 0 ) log( "Add cookie " + name + "=" + value);
            } else {
                // we have a bad cookie.... just let it go
            }
        }
    }

    /**
     *
     * Strips quotes from the start and end of the cookie string
     * This conforms to RFC 2965
     * 
     * @param value            a <code>String</code> specifying the cookie 
     *                         value (possibly quoted).
     *
     * @see #setValue
     *
     */
    private static String stripQuote( String value )
    {
        //        log("Strip quote from " + value );
        if (value.startsWith("\"") && value.endsWith("\"")) {
            try {
                return value.substring(1,value.length()-1);
            } catch (Exception ex) { 
            }
        }
        return value;
    }  


    // log
    static final int dbg=0;
    public void log(String s ) {
        if (log.isDebugEnabled())
            log.debug("Cookies: " + s);
    }


   /**
     * Returns true if the byte is a separator character as
     * defined in RFC2619. Since this is called often, this
     * function should be organized with the most probable
     * outcomes first.
     * JVK
     */
    public static final boolean isSeparator(final byte c) {
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
    public static final boolean isWhiteSpace(final byte c) {
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
    public final void processCookieHeader(byte bytes[], int off, int len){
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
            while (pos < end && isWhiteSpace(bytes[pos])) {pos++; }; 
         

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
                case '"':; // Quoted Value
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
                default:;
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
                        log("Invalid cookie. Value not a token or quoted value");
                        while (pos < end && bytes[pos] != ';' && 
                               bytes[pos] != ',') 
                            {pos++; };
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
            while (pos < end && isWhiteSpace(bytes[pos])) {pos++; }; 


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
                log("Unknown Special Cookie");

            } else { // Normal Cookie
                sc = addCookie();
                sc.setVersion( version );
                sc.getName().setBytes( bytes, nameStart,
                                       nameEnd-nameStart);
                
                if (valueStart != -1) { // Normal AVPair
                    sc.getValue().setBytes( bytes, valueStart,
                            valueEnd-valueStart);
                    if (isQuoted) {
                        // We know this is a byte value so this is safe
                        ServerCookie.unescapeDoubleQuotes(
                                sc.getValue().getByteChunk());
                    }
                } else {
                    // Name Only
                    sc.getValue().setString(""); 
                }
                continue;
            }
        }
    }

    /**
     * Given the starting position of a token, this gets the end of the
     * token, with no separator characters in between.
     * JVK
     */
    public static final int getTokenEndPosition(byte bytes[], int off, int end){
        int pos = off;
        while (pos < end && !isSeparator(bytes[pos])) {pos++; };
        
        if (pos > end)
            return end;
        return pos;
    }

    /** 
     * Given a starting position after an initial quote chracter, this gets
     * the position of the end quote. This escapes anything after a '\' char
     * JVK RFC 2616
     */
    public static final int getQuotedValueEndPosition(byte bytes[], int off, int end){
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

}

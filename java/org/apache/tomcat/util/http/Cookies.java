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

    /** Process a byte[] header - allowing fast processing of the
     *  raw data
     */
    void processCookieHeader(  byte bytes[], int off, int len )
    {
        if( len<=0 || bytes==null ) return;
        int end=off+len;
        int pos=off;
        
        int version=0; //sticky
        ServerCookie sc=null;
        

        while( pos<end ) {
            byte cc;
            // [ skip_spaces name skip_spaces "=" skip_spaces value EXTRA ; ] *
            if( dbg>0 ) log( "Start: " + pos + " " + end );
            
            pos=skipSpaces(bytes, pos, end);
            if( pos>=end )
                return; // only spaces
            int startName=pos;
            if( dbg>0 ) log( "SN: " + pos );
            
            // Version should be the first token
            boolean isSpecial=false;
            if(bytes[pos]=='$') { pos++; isSpecial=true; }

            pos= findDelim1( bytes, startName, end); // " =;,"
            int endName=pos;
            // current = "=" or " " or DELIM
            pos= skipSpaces( bytes, endName, end ); 
            if( dbg>0 ) log( "DELIM: " + endName + " " + (char)bytes[pos]);

            if(pos >= end ) {
                // it's a name-only cookie ( valid in RFC2109 )
                if( ! isSpecial ) {
                    sc=addCookie();
                    sc.getName().setBytes( bytes, startName,
                                           endName-startName );
                    sc.getValue().setString("");
                    sc.setVersion( version );
                    if( dbg>0 ) log( "Name only, end: " + startName + " " +
                                     endName);
                }
                return;
            }

            cc=bytes[pos];
            pos++;
            if( cc==';' || cc==',' || pos>=end ) {
                if( ! isSpecial && startName!= endName ) {
                    sc=addCookie();
                    sc.getName().setBytes( bytes, startName,
                                           endName-startName );
                    sc.getValue().setString("");
                    sc.setVersion( version );
                    if( dbg>0 ) log( "Name only: " + startName + " " + endName);
                }
                continue;
            }
            
            // we should have "=" ( tested all other alternatives )
            int startValue=skipSpaces( bytes, pos, end);
            int endValue=startValue;
            
            cc=bytes[pos];
            if( cc=='"' ) {
                endValue=findDelim3( bytes, startValue+1, end, cc );
                if (endValue == -1) {
                    endValue=findDelim2( bytes, startValue+1, end );
                } else startValue++;
                pos=endValue+1; // to skip to next cookie
             } else {
                endValue=findDelim2( bytes, startValue, end );
                pos=endValue+1;
            }
            
            // if not $Version, etc
            if( ! isSpecial ) {
                sc=addCookie();
                sc.getName().setBytes( bytes, startName, endName-startName );
                sc.getValue().setBytes( bytes, startValue, endValue-startValue);
                sc.setVersion( version );
                if( dbg>0 ) {
                    log( "New: " + sc.getName() + "X=X" + sc.getValue());
                }
                continue;
            }
            
            // special - Path, Version, Domain, Port
            if( dbg>0 ) log( "Special: " + startName + " " + endName);
            // XXX TODO
            if( equals( "$Version", bytes, startName, endName ) ) {
                if(dbg>0 ) log( "Found version " );
                if( bytes[startValue]=='1' && endValue==startValue+1 ) {
                    version=1;
                    if(dbg>0 ) log( "Found version=1" );
                }
                continue;
            }
            if( sc==null ) {
                // Path, etc without a previous cookie
                continue;
            }
            if( equals( "$Path", bytes, startName, endName ) ) {
                sc.getPath().setBytes( bytes,
                                       startValue,
                                       endValue-startValue );
            }
            if( equals( "$Domain", bytes, startName, endName ) ) {
                sc.getDomain().setBytes( bytes,
                                         startValue,
                                         endValue-startValue );
            }
            if( equals( "$Port", bytes, startName, endName ) ) {
                // sc.getPort().setBytes( bytes,
                //                        startValue,
                //                        endValue-startValue );
            }
        }
    }

    // -------------------- Utils --------------------
    public static int skipSpaces(  byte bytes[], int off, int end ) {
        while( off < end ) {
            byte b=bytes[off];
            if( b!= ' ' ) return off;
            off ++;
        }
        return off;
    }

    public static int findDelim1( byte bytes[], int off, int end )
    {
        while( off < end ) {
            byte b=bytes[off];
            if( b==' ' || b=='=' || b==';' || b==',' )
                return off;
            off++;
        }
        return off;
    }

    public static int findDelim2( byte bytes[], int off, int end )
    {
        while( off < end ) {
            byte b=bytes[off];
            if( b==';' || b==',' )
                return off;
            off++;
        }
        return off;
    }

    /*
     *  search for cc but skip \cc as required by rfc2616
     *   (according to rfc2616 cc should be ")
    */
    public static int findDelim3( byte bytes[], int off, int end, byte cc )
    {
        while( off < end ) {
            byte b=bytes[off];
            if ( b== '\\' ) {
              off++;
              off++;
              continue;
            }
            if( b==cc )
                return off;
            off++;
        }
        return -1;
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

    /*
    public static void main( String args[] ) {
        test("foo=bar; a=b");
        test("foo=bar;a=b");
        test("foo=bar;a=b;");
        test("foo=bar;a=b; ");
        test("foo=bar;a=b; ;");
        test("foo=;a=b; ;");
        test("foo;a=b; ;");
        // v1 
        test("$Version=1; foo=bar;a=b"); 
        test("$Version=\"1\"; foo='bar'; $Path=/path; $Domain=\"localhost\"");
        test("$Version=1;foo=bar;a=b; ; ");
        test("$Version=1;foo=;a=b; ; ");
        test("$Version=1;foo= ;a=b; ; ");
        test("$Version=1;foo;a=b; ; ");
        test("$Version=1;foo=\"bar\";a=b; ; ");
        test("$Version=1;foo=\"bar\";$Path=/examples;a=b; ; ");
        test("$Version=1;foo=\"bar\";$Domain=apache.org;a=b");
        test("$Version=1;foo=\"bar\";$Domain=apache.org;a=b;$Domain=yahoo.com");
        // rfc2965
        test("$Version=1;foo=\"bar\";$Domain=apache.org;$Port=8080;a=b");

        // wrong
        test("$Version=1;foo=\"bar\";$Domain=apache.org;$Port=8080;a=b");
    }

    public static void test( String s ) {
        System.out.println("Processing " + s );
        Cookies cs=new Cookies(null);
        cs.processCookieHeader( s.getBytes(), 0, s.length());
        for( int i=0; i< cs.getCookieCount() ; i++ ) {
            System.out.println("Cookie: " + cs.getCookie( i ));
        }
            
    }
    */

}

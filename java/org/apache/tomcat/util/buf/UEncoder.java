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

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.BitSet;

/** Efficient implementation for encoders.
 *  This class is not thread safe - you need one encoder per thread.
 *  The encoder will save and recycle the internal objects, avoiding
 *  garbage.
 * 
 *  You can add extra characters that you want preserved, for example
 *  while encoding a URL you can add "/".
 *
 *  @author Costin Manolache
 */
public final class UEncoder {

    private static org.apache.commons.logging.Log log=
        org.apache.commons.logging.LogFactory.getLog(UEncoder.class );
    
    // Not static - the set may differ ( it's better than adding
    // an extra check for "/", "+", etc
    private BitSet safeChars=null;
    private C2BConverter c2b=null;
    private ByteChunk bb=null;

    private String encoding="UTF8";
    private static final int debug=0;
    
    public UEncoder() {
	initSafeChars();
    }

    public void setEncoding( String s ) {
	encoding=s;
    }

    public void addSafeCharacter( char c ) {
	safeChars.set( c );
    }


    /** URL Encode string, using a specified encoding.
     *
     * @param buf The writer
     * @param s string to be encoded
     * @throws IOException If an I/O error occurs
     */
    public void urlEncode( Writer buf, String s )
	throws IOException
    {
	if( c2b==null ) {
	    bb=new ByteChunk(16); // small enough.
	    c2b=new C2BConverter( bb, encoding );
	}

	for (int i = 0; i < s.length(); i++) {
	    int c = (int) s.charAt(i);
	    if( safeChars.get( c ) ) {
		if( debug > 0 ) log("Safe: " + (char)c);
		buf.write((char)c);
	    } else {
		if( debug > 0 ) log("Unsafe:  " + (char)c);
		c2b.convert( (char)c );
		
		// "surrogate" - UTF is _not_ 16 bit, but 21 !!!!
		// ( while UCS is 31 ). Amazing...
		if (c >= 0xD800 && c <= 0xDBFF) {
		    if ( (i+1) < s.length()) {
			int d = (int) s.charAt(i+1);
			if (d >= 0xDC00 && d <= 0xDFFF) {
			    if( debug > 0 ) log("Unsafe:  " + c);
			    c2b.convert( (char)d);
			    i++;
			}
		    }
		}

		c2b.flushBuffer();
		
		urlEncode( buf, bb.getBuffer(), bb.getOffset(),
			   bb.getLength() );
		bb.recycle();
	    }
	}
    }

    /**
     */
    public void urlEncode( Writer buf, byte bytes[], int off, int len)
	throws IOException
    {
	for( int j=off; j< len; j++ ) {
	    buf.write( '%' );
	    char ch = Character.forDigit((bytes[j] >> 4) & 0xF, 16);
	    if( debug > 0 ) log("Encode:  " + ch);
	    buf.write(ch);
	    ch = Character.forDigit(bytes[j] & 0xF, 16);
	    if( debug > 0 ) log("Encode:  " + ch);
	    buf.write(ch);
	}
    }
    
    /**
     * Utility funtion to re-encode the URL.
     * Still has problems with charset, since UEncoder mostly
     * ignores it.
     */
    public String encodeURL(String uri) {
	String outUri=null;
	try {
	    // XXX optimize - recycle, etc
	    CharArrayWriter out = new CharArrayWriter();
	    urlEncode(out, uri);
	    outUri=out.toString();
	} catch (IOException iex) {
	}
	return outUri;
    }
    

    // -------------------- Internal implementation --------------------
    
    // 
    private void init() {
	
    }
    
    private void initSafeChars() {
	safeChars=new BitSet(128);
	int i;
	for (i = 'a'; i <= 'z'; i++) {
	    safeChars.set(i);
	}
	for (i = 'A'; i <= 'Z'; i++) {
	    safeChars.set(i);
	}
	for (i = '0'; i <= '9'; i++) {
	    safeChars.set(i);
	}
	//safe
	safeChars.set('$');
	safeChars.set('-');
	safeChars.set('_');
	safeChars.set('.');

	// Dangerous: someone may treat this as " "
	// RFC1738 does allow it, it's not reserved
	//    safeChars.set('+');
	//extra
	safeChars.set('!');
	safeChars.set('*');
	safeChars.set('\'');
	safeChars.set('(');
	safeChars.set(')');
	safeChars.set(',');	
    }

    private static void log( String s ) {
        if (log.isDebugEnabled())
            log.debug("Encoder: " + s );
    }
}

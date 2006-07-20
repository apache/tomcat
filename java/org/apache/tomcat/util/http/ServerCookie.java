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

import java.io.Serializable;
import java.text.FieldPosition;
import java.util.Date;

import org.apache.tomcat.util.buf.DateTool;
import org.apache.tomcat.util.buf.MessageBytes;


/**
 *  Server-side cookie representation.
 *   Allows recycling and uses MessageBytes as low-level
 *  representation ( and thus the byte-> char conversion can be delayed
 *  until we know the charset ).
 *
 *  Tomcat.core uses this recyclable object to represent cookies,
 *  and the facade will convert it to the external representation.
 */
public class ServerCookie implements Serializable {
    
    
    private static org.apache.commons.logging.Log log=
        org.apache.commons.logging.LogFactory.getLog(ServerCookie.class );
    
    private MessageBytes name=MessageBytes.newInstance();
    private MessageBytes value=MessageBytes.newInstance();

    private MessageBytes comment=MessageBytes.newInstance();    // ;Comment=VALUE
    private MessageBytes domain=MessageBytes.newInstance();    // ;Domain=VALUE ...

    private int maxAge = -1;	// ;Max-Age=VALUE
				// ;Discard ... implied by maxAge < 0
    // RFC2109: maxAge=0 will end a session
    private MessageBytes path=MessageBytes.newInstance();	// ;Path=VALUE .
    private boolean secure;	// ;Secure
    private int version = 0;	// ;Version=1

    //XXX CommentURL, Port -> use notes ?
    
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
    
    // Note -- disabled for now to allow full Netscape compatibility
    // from RFC 2068, token special case characters
    //
    // private static final String tspecials = "()<>@,;:\\\"/[]?={} \t";
    private static final String tspecials = ",; ";

    /*
     * Tests a string and returns true if the string counts as a
     * reserved token in the Java language.
     *
     * @param value		the <code>String</code> to be tested
     *
     * @return			<code>true</code> if the <code>String</code> is
     *				a reserved token; <code>false</code>
     *				if it is not
     */
    public static boolean isToken(String value) {
	if( value==null) return true;
	int len = value.length();

	for (int i = 0; i < len; i++) {
	    char c = value.charAt(i);

	    if (c < 0x20 || c >= 0x7f || tspecials.indexOf(c) != -1)
		return false;
	}
	return true;
    }

    public static boolean checkName( String name ) {
	if (!isToken(name)
		|| name.equalsIgnoreCase("Comment")	// rfc2019
		|| name.equalsIgnoreCase("Discard")	// 2019++
		|| name.equalsIgnoreCase("Domain")
		|| name.equalsIgnoreCase("Expires")	// (old cookies)
		|| name.equalsIgnoreCase("Max-Age")	// rfc2019
		|| name.equalsIgnoreCase("Path")
		|| name.equalsIgnoreCase("Secure")
		|| name.equalsIgnoreCase("Version")
	    ) {
	    return false;
	}
	return true;
    }

    // -------------------- Cookie parsing tools

    
    /** Return the header name to set the cookie, based on cookie
     *  version
     */
    public String getCookieHeaderName() {
	return getCookieHeaderName(version);
    }

    /** Return the header name to set the cookie, based on cookie
     *  version
     */
    public static String getCookieHeaderName(int version) {
	if( dbg>0 ) log( (version==1) ? "Set-Cookie2" : "Set-Cookie");
        if (version == 1) {
	    // RFC2109
	    return "Set-Cookie";
	    // XXX RFC2965 is not standard yet, and Set-Cookie2
	    // is not supported by Netscape 4, 6, IE 3, 5 .
	    // It is supported by Lynx, and there is hope 
	    //	    return "Set-Cookie2";
        } else {
	    // Old Netscape
	    return "Set-Cookie";
        }
    }

    private static final String ancientDate=DateTool.formatOldCookie(new Date(10000));

    public static void appendCookieValue( StringBuffer buf,
					  int version,
					  String name,
					  String value,
					  String path,
					  String domain,
					  String comment,
					  int maxAge,
					  boolean isSecure )
    {
        // this part is the same for all cookies
	buf.append( name );
        buf.append("=");
        maybeQuote(version, buf, value);

	// XXX Netscape cookie: "; "
 	// add version 1 specific information
	if (version == 1) {
	    // Version=1 ... required
	    buf.append ("; Version=1");

	    // Comment=comment
	    if ( comment!=null ) {
		buf.append ("; Comment=");
		maybeQuote (version, buf, comment);
	    }
	}
	
	// add domain information, if present

	if (domain!=null) {
	    buf.append("; Domain=");
	    maybeQuote (version, buf, domain);
	}

	// Max-Age=secs/Discard ... or use old "Expires" format
	if (maxAge >= 0) {
	    if (version == 0) {
		// XXX XXX XXX We need to send both, for
		// interoperatibility (long word )
		buf.append ("; Expires=");
		// Wdy, DD-Mon-YY HH:MM:SS GMT ( Expires netscape format )
		// To expire we need to set the time back in future
		// ( pfrieden@dChain.com )
                if (maxAge == 0)
		    buf.append( ancientDate );
		else
                    DateTool.formatOldCookie
                        (new Date( System.currentTimeMillis() +
                                   maxAge *1000L), buf,
                         new FieldPosition(0));

	    } else {
		buf.append ("; Max-Age=");
		buf.append (maxAge);
	    }
	}

	// Path=path
	if (path!=null) {
	    buf.append ("; Path=");
	    maybeQuote (version, buf, path);
	}

	// Secure
	if (isSecure) {
	  buf.append ("; Secure");
	}
	
	
    }

    public static void maybeQuote (int version, StringBuffer buf,
            String value) {
        // special case - a \n or \r  shouldn't happen in any case
        if (isToken(value)) {
            buf.append(value);
        } else {
            buf.append('"');
            buf.append(escapeDoubleQuotes(value));
            buf.append('"');
        }
    }

    // log
    static final int dbg=1;
    public static void log(String s ) {
        if (log.isDebugEnabled())
            log.debug("ServerCookie: " + s);
    }


    /**
     * Escapes any double quotes in the given string.
     *
     * @param s the input string
     *
     * @return The (possibly) escaped string
     */
    private static String escapeDoubleQuotes(String s) {

        if (s == null || s.length() == 0 || s.indexOf('"') == -1) {
            return s;
        }

        StringBuffer b = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"')
                b.append('\\').append('"');
            else
                b.append(c);
        }

        return b.toString();
    }

}


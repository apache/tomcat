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

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * An object which represents an HTTP cookie.  Can be constructed by
 * parsing a string from the set-cookie: header.
 *
 * Syntax: Set-Cookie: NAME=VALUE; expires=DATE;
 *             path=PATH; domain=DOMAIN_NAME; secure
 *
 * All but the first field are optional.
 *
 * @author	Ramesh.Mandava
 */

public class HttpCookie {
    private Date expirationDate = null;
    private String nameAndValue;
    private String path;
    private String domain;
    private boolean isSecure = false;
    private static boolean defaultSet = true;
    private static long defExprTime = 100;

    public HttpCookie(String cookieString) {
	/*
	System.out.println("Calling default expiration :");
	getDefaultExpiration();
	*/
	parseCookieString(cookieString);
    }

    //
    // Constructor for use by the bean
    //
    public HttpCookie(Date expirationDate,
		      String nameAndValue,
		      String path,
		      String domain,
		      boolean isSecure) {
	this.expirationDate = expirationDate;
	this.nameAndValue = nameAndValue;
	this.path = path;
	this.domain = domain;
	this.isSecure = isSecure;
    }

    public HttpCookie(URL url, String cookieString) {
	parseCookieString(cookieString);
	applyDefaults(url);
    }

    /**
     * Fills in default values for domain, path, etc. from the URL
     * after creation of the cookie.
     */
    private void applyDefaults(URL url) {
	if (domain == null) {
	    domain = url.getHost()+":"+((url.getPort() == -1) ? 80 : url.getPort());
	}

	if (path == null) {
	    path = url.getFile();

	    // larrylf: The documentation for cookies say that the path is
	    // by default, the path of the document, not the filename of the
	    // document.  This could be read as not including that document
	    // name itself, just its path (this is how NetScape intrprets it)
	    // so amputate the document name!
	    int last = path.lastIndexOf("/");
	    if( last > -1 ) {
		path = path.substring(0, last);
	    }
	}
    }


    /**
     * Parse the given string into its individual components, recording them
     * in the member variables of this object.
     */
    private void parseCookieString(String cookieString) {
	StringTokenizer tokens = new StringTokenizer(cookieString, ";");

	if (!tokens.hasMoreTokens()) {
	    // REMIND: make this robust against parse errors
	    nameAndValue="=";
	    return;
	}

	nameAndValue = tokens.nextToken().trim();
	
	while (tokens.hasMoreTokens()) {
	    String token = tokens.nextToken().trim();
	
	    if (token.equalsIgnoreCase("secure")) {
		isSecure = true;
	    } else {
		int equIndex = token.indexOf("=");
		
		if (equIndex < 0) {
		    continue;
		    // REMIND: malformed cookie
		}
		
		String attr = token.substring(0, equIndex);
		String val = token.substring(equIndex+1);
		
		if (attr.equalsIgnoreCase("path")) {
		    path = val;
		} else if (attr.equalsIgnoreCase("domain")) {
		    if( val.indexOf(".") == 0 ) {
			// spec seems to allow for setting the domain in
			// the form 'domain=.eng.sun.com'.  We want to
			// trim off the leading '.' so we can allow for
			// both leading dot and non leading dot forms
			// without duplicate storage.
			domain = val.substring(1);
		    } else {
			domain = val;
		    }
		} else if (attr.equalsIgnoreCase("expires")) {
		    expirationDate = parseExpireDate(val);
		} else {
		    // unknown attribute -- do nothing
		}
	    }
	}

	// commented the following out, b/c ok to have no expirationDate
	// that means that the cookie should last only for that particular
	// session.
	//	if (expirationDate == null) {
	//     	    expirationDate = getDefaultExpiration();
	//	}
    }

    /* Returns the default expiration, which is the current time + default
       expiration as specified in the properties file.
       This uses reflection to get at the properties file, since Globals is
       not in the utils/ directory
       */
    private Date getDefaultExpiration() {
	if (defaultSet == false) {
	    Properties props = new Properties();
	
	    try {
		FileInputStream fin = new FileInputStream("ServerAutoRun.properties");
		props.load( fin );
		
		System.out.println("Got properties from ServerAutoRun.properties");
		props.list(System.out);
		
	    } catch (IOException ex) {
		System.out.println("HttpCookie getDefaultExpiration : ServerAutoRun.properties not found!" + ex);
	    }
	
		 // defExprTime = props.getProperty("cookies.default.expiration");
		 defExprTime = Long.parseLong( props.getProperty("cookies.default.expiration") );

	    }
	    defaultSet = true;
	
	return (new Date(System.currentTimeMillis() + defExprTime));
	
    }

    //======================================================================
    //
    // Accessor functions
    //



     public String getNameValue() {
	return nameAndValue;
    }

    /**
     * Returns just the name part of the cookie
     */
     public String getName() {

	 // it probably can't have null value, but doesn't hurt much
	 // to check.
	 if (nameAndValue == null) {
	     return "=";
	 }
	 int index = nameAndValue.indexOf("=");
	 return (index < 0) ? "=" : nameAndValue.substring(0, index);
     }


    /**
     * Returns the domain of the cookie as it was presented
     */
     public String getDomain() {
	// REMIND: add port here if appropriate
	return domain;
    }

     public String getPath() {
	return path;
    }

     public Date getExpirationDate() {
	return expirationDate;
    }

    public boolean hasExpired() {
	if(expirationDate == null) {
	    return false;
	}
	return (expirationDate.getTime() <= System.currentTimeMillis());
    }

    /**
     * Returns true if the cookie has an expiration date (meaning it's
     * persistent), and if the date nas not expired;
     */
    public boolean isSaveable() {
	return (expirationDate != null)
	    && (expirationDate.getTime() > System.currentTimeMillis());
    }

    public boolean isSaveableInMemory() {
	return ((expirationDate == null) ||
		(expirationDate != null && expirationDate.getTime() > System.currentTimeMillis()));
    }
		
     public boolean isSecure() {
	return isSecure;
    }

    private Date parseExpireDate(String dateString) {
	// format is wdy, DD-Mon-yyyy HH:mm:ss GMT
	RfcDateParser parser = new RfcDateParser(dateString);
	Date theDate = parser.getDate();
	if (theDate == null) {
	    // Expire in some intelligent default time
	    theDate = getDefaultExpiration();
	}
	return theDate;
    }

    public String toString() {

	String result = (nameAndValue == null) ? "=" : nameAndValue;
	if (expirationDate != null) {
	    result += "; expires=" + expirationDate;
	}
	
	if (path != null) {
	    result += "; path=" + path;
	}

	if (domain != null) {
	    result += "; domain=" + domain;
	}

	if (isSecure) {
	    result += "; secure";
	}

	return result;
    }
}

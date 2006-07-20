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

import org.apache.tomcat.util.res.StringManager;

/**
 * Handle (internationalized) HTTP messages.
 * 
 * @author James Duncan Davidson [duncan@eng.sun.com]
 * @author James Todd [gonzo@eng.sun.com]
 * @author Jason Hunter [jch@eng.sun.com]
 * @author Harish Prabandham
 * @author costin@eng.sun.com
 */
public class HttpMessages {
    // XXX move message resources in this package
    protected static StringManager sm =
        StringManager.getManager("org.apache.tomcat.util.http.res");
	
    static String st_200=null;
    static String st_302=null;
    static String st_400=null;
    static String st_404=null;
    
    /** Get the status string associated with a status code.
     *  No I18N - return the messages defined in the HTTP spec.
     *  ( the user isn't supposed to see them, this is the last
     *  thing to translate)
     *
     *  Common messages are cached.
     *
     */
    public static String getMessage( int status ) {
	// method from Response.
	
	// Does HTTP requires/allow international messages or
	// are pre-defined? The user doesn't see them most of the time
	switch( status ) {
	case 200:
	    if( st_200==null ) st_200=sm.getString( "sc.200");
	    return st_200;
	case 302:
	    if( st_302==null ) st_302=sm.getString( "sc.302");
	    return st_302;
	case 400:
	    if( st_400==null ) st_400=sm.getString( "sc.400");
	    return st_400;
	case 404:
	    if( st_404==null ) st_404=sm.getString( "sc.404");
	    return st_404;
	}
	return sm.getString("sc."+ status);
    }

    /**
     * Filter the specified message string for characters that are sensitive
     * in HTML.  This avoids potential attacks caused by including JavaScript
     * codes in the request URL that is often reported in error messages.
     *
     * @param message The message string to be filtered
     */
    public static String filter(String message) {

	if (message == null)
	    return (null);

	char content[] = new char[message.length()];
	message.getChars(0, message.length(), content, 0);
	StringBuffer result = new StringBuffer(content.length + 50);
	for (int i = 0; i < content.length; i++) {
	    switch (content[i]) {
	    case '<':
		result.append("&lt;");
		break;
	    case '>':
		result.append("&gt;");
		break;
	    case '&':
		result.append("&amp;");
		break;
	    case '"':
		result.append("&quot;");
		break;
	    default:
		result.append(content[i]);
	    }
	}
	return (result.toString());
    }

}

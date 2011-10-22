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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Vector;


/**
 * Represents a collection of Cookie instances.
 * <p>
 * Fires events when the cookies have been changed internally. Deals
 * with management of cookies in terms of saving and loading them,
 * and disabling them.
 *
 * @author	Ramesh.Mandava
 */
public class CookieController {

//    private VetoableChangeSupport vceListeners;

    private static Hashtable cookieJar = new Hashtable();

    /* public no arg constructor for bean */
    public CookieController() {
    }

/////////////////////////////////////////////////////////////
 /**
     * Records any cookies which have been sent as part of an HTTP response.
     * The connection parameter must be already have been opened, so that
     * the response headers are available.  It's ok to pass a non-HTTP
     * URL connection, or one which does not have any set-cookie headers.
     */
    public void recordAnyCookies(Vector rcvVectorOfCookies , URL url ) {

	if ((rcvVectorOfCookies == null) || ( rcvVectorOfCookies.size()== 0) ) {
	    // no headers here
	    return;
	}	
	try {
	/*
        Properties properties = new Properties();
	FileInputStream fin = new FileInputStream("ServerAutoRun.properties");
	properties.load(fin);

	String cookiepolicy = properties.getProperty("cookie.acceptpolicy");
	if (cookiepolicy == null || cookiepolicy.equals("none")) {
	    return;
	}
	*/


	for (int hi = 0; hi<rcvVectorOfCookies.size(); hi++) {

		String cookieValue = (String)rcvVectorOfCookies.elementAt(hi) ;
		recordCookie(url, cookieValue); // What to do here
	    }

	}
	catch( Exception e )
	{
		System.out.println("exception : " + e );
	}
    }


    /**
     * Create a cookie from the cookie, and use the HttpURLConnection to
     * fill in unspecified values in the cookie with defaults.
     */
    public void recordCookie(URL url,
				     String cookieValue) {
	HttpCookie cookie = new HttpCookie(url, cookieValue);
	
	// First, check to make sure the cookie's domain matches the
	// server's, and has the required number of '.'s
	String twodot[]=
	    {"com", "edu", "net", "org", "gov", "mil", "int"};
	String domain = cookie.getDomain();
	if( domain == null )
	    return;
	int index = domain.indexOf(':');
	if (index != -1) {
	    int portCookie;
	    try {
		portCookie = (Integer.valueOf(domain.substring(index+1,domain.length()))).intValue();
	    } catch (Exception e) {
		return;
	    }
	    portCookie = ( portCookie == -1 ) ? 80 : portCookie;
	    domain=domain.substring(0,index);
	}
	domain.toLowerCase();
	
	String host = url.getHost();
	host.toLowerCase();

	boolean domainOK = host.equals(domain);
	if( !domainOK && host.endsWith( domain ) ) {
	    int dotsNeeded = 2;
	    for( int i = 0; i < twodot.length; i++ ) {
		if( domain.endsWith( twodot[i] ) ) {
		    dotsNeeded = 1;
		}
	    }

	    int lastChar = domain.length();
	    for( ; lastChar > 0 && dotsNeeded > 0; dotsNeeded-- ) {
		lastChar = domain.lastIndexOf( '.', lastChar-1 );
	    }

	    if( lastChar > 0 )
		domainOK = true;
	}

	if( domainOK ) {
	    recordCookie(cookie);

	}
    }


    /**
     * Record the cookie in the in-memory container of cookies.  If there
     * is already a cookie which is in the exact same domain with the
     * exact same
     */
    public void recordCookie(HttpCookie cookie) {
	if (!checkIfCookieOK(cookie)) {
	    return;
	}
	synchronized (cookieJar) {
	
	    String domain = cookie.getDomain().toLowerCase();

	    Vector cookieList = (Vector)cookieJar.get(domain);
	    if (cookieList == null) {
		cookieList = new Vector();
	    }

	    addOrReplaceCookie(cookieList, cookie);
	    cookieJar.put(domain, cookieList);
	
	}

    }

    public boolean checkIfCookieOK(HttpCookie cookie) {
	return true;
    }

    /**
     * Scans the vector of cookies looking for an exact match with the
     * given cookie.  Replaces it if there is one, otherwise adds
     * one at the end.  The vector is presumed to have cookies which all
     * have the same domain, so the domain of the cookie is not checked.
     * <p>
     * <p>
     * If this is called, it is assumed that the cookie jar is exclusively
     * held by the current thread.
     *
     */
    private void addOrReplaceCookie(Vector cookies,
				       HttpCookie cookie) {
	int numCookies = cookies.size();

	String path = cookie.getPath();
	String name = cookie.getName();
	HttpCookie replaced = null;
	int replacedIndex = -1;

	for (int i = 0; i < numCookies; i++) {
	    HttpCookie existingCookie = (HttpCookie)cookies.elementAt(i);
	
	    String existingPath = existingCookie.getPath();
	    if (path.equals(existingPath)) {
		String existingName = existingCookie.getName();
		if (name.equals(existingName)) {
		    // need to replace this one!
		    replaced = existingCookie;
		    replacedIndex = i;
		    break;
		}
	    }
	}
	
	
	// Do the replace - if cookie has already expired, remove
	// the replaced cookie.
	if (replaced != null) {
	    if (cookie.isSaveableInMemory()) {
		cookies.setElementAt(cookie, replacedIndex);
		//System.out.println("REPLACED existing cookie with " + cookie);
	    } else {
		cookies.removeElementAt(replacedIndex);
		//System.out.println("Removed cookie b/c or expr " + cookie);
	    }

	} else { // only save the cookie in memory if it is non persistent
          	 // or not expired.
	    if (cookie.isSaveableInMemory()) {
		cookies.addElement(cookie);
		//System.out.println("RECORDED new cookie " + cookie);
	    }

	}

    }

    public String applyRelevantCookies(URL url ) {

       try {	
		/*
		Properties properties = new Properties();
		FileInputStream fin = new FileInputStream("ServerAutoRun.properties");
		properties.load(fin);
		// check current accept policy instead enableCookies
		String cookiepolicy = properties.getProperty("cookie.acceptpolicy");
		if (cookiepolicy == null || cookiepolicy.equals("none")) {
		    return null;
		}

		*/

		return applyCookiesForHost(url);

	}
	catch ( Exception e )
	{
		System.out.println("Exception : " +e );
		return null;
	}


    }


   /**
     * Host may be a FQDN, or a partial domain name starting with a dot.
     * Adds any cookies which match the host and path to the
     * cookie set on the URL connection.
     */
    private String applyCookiesForHost(URL url ){
	String cookieString = null;
	Vector cookieVector = getAllRelevantCookies(url);
	
	if (cookieVector != null) {

	    for (Enumeration e = cookieVector.elements(); e.hasMoreElements();) {
		HttpCookie cookie = (HttpCookie)e.nextElement();
		if( cookieString == null ) {
		    cookieString = cookie.getNameValue();
		} else {
		    cookieString = cookieString + "; " + cookie.getNameValue();
		}
	    }
	
	 /*

	    if( cookieString != null ) {
		httpConn.setRequestProperty("Cookie", cookieString);
	
//		System.out.println("Returned cookie string: " + cookieString + " for HOST = " + host);
	     }

	  */


	}
//		System.out.println("Returned cookie string: " + cookieString + " for HOST = " + host);
	return cookieString;
	
    }

    private Vector getAllRelevantCookies(URL url) {
	String host = url.getHost();
	Vector cookieVector = getSubsetRelevantCookies(host, url);

	Vector tempVector;
	int index;

	while ((index = host.indexOf('.', 1)) >= 0) {
	    // trim off everything up to, and including the dot.
	    host = host.substring(index+1);
	
            // add onto cookieVector
	    tempVector = getSubsetRelevantCookies(host,url);
	    if (tempVector != null ) {
		for (Enumeration e = tempVector.elements(); e.hasMoreElements(); ) {
		    if (cookieVector == null) {
			cookieVector = new Vector(2);
		    }

		    cookieVector.addElement(e.nextElement());

		}
	    }
	}
	return cookieVector;
    }

    private Vector getSubsetRelevantCookies(String host, URL url) {

	Vector cookieList = (Vector)cookieJar.get(host);
	
//	System.out.println("getRelevantCookies() .. for host, url " + host +", "+url);
	Vector cookiePortList = (Vector)cookieJar.get(host+":"+((url.getPort() == -1) ? 80 : url.getPort()));
	if (cookiePortList != null) {
	    if (cookieList == null) {
		cookieList = new Vector(10);
	    }
	    Enumeration cookies = cookiePortList.elements();
	    while (cookies.hasMoreElements()) {
		cookieList.addElement(cookies.nextElement());
	    }
	}
	
	
	if (cookieList == null) {
	    return null;
	}

	String path = url.getFile();
//	System.out.println("        path is " + path + "; protocol = " + url.getProtocol());


	int queryInd = path.indexOf('?');
	if (queryInd > 0) {
	    // strip off the part following the ?
	    path = path.substring(0, queryInd);
	}

	Enumeration cookies = cookieList.elements();
	Vector cookiesToSend = new Vector(10);

	while (cookies.hasMoreElements()) {
	    HttpCookie cookie = (HttpCookie)cookies.nextElement();
	
	    String cookiePath = cookie.getPath();

	    if (path.startsWith(cookiePath)) {
		// larrylf: Actually, my documentation (from Netscape)
		// says that /foo should
		// match /foobar and /foo/bar.  Yuck!!!

		if (!cookie.hasExpired()) {
		    cookiesToSend.addElement(cookie);
		}

/*
   We're keeping this piece of commented out code around just in
   case we decide to put it back.  the spec does specify the above,
   but it is so disgusting!

		int cookiePathLen = cookiePath.length();

		// verify that /foo does not match /foobar by mistake
		if ((path.length() == cookiePathLen)
		    || (path.length() > cookiePathLen &&
			path.charAt(cookiePathLen) == '/')) {
		
		    // We have a matching cookie!

		    if (!cookie.hasExpired()) {
			cookiesToSend.addElement(cookie);
		    }
		}
*/
	    }
	}

	// Now, sort the cookies in most to least specific order
	// Yes, its the deaded bubblesort!! Wah Ha-ha-ha-ha....
	// (it should be a small vector, so perf is not an issue...)
	if( cookiesToSend.size() > 1 ) {
	    for( int i = 0; i < cookiesToSend.size()-1; i++ ) {
		HttpCookie headC = (HttpCookie)cookiesToSend.elementAt(i);
		String head = headC.getPath();
		// This little excercise is a cheap way to get
		// '/foo' to read more specfic then '/'
		if( !head.endsWith("/") ) {
		    head = head + "/";
		}
		for( int j = i+1; j < cookiesToSend.size(); j++ ) {
		    HttpCookie scanC = (HttpCookie)cookiesToSend.elementAt(j);
		    String scan = scanC.getPath();
		    if( !scan.endsWith("/") ) {
			scan = scan + "/";
		    }

		    int headCount = 0;
		    int index = -1;
		    while( (index=head.indexOf('/', index+1)) != -1 ) {
			headCount++;
		    }
		    index = -1;

		    int scanCount = 0;
		    while( (index=scan.indexOf('/', index+1)) != -1 ) {
			scanCount++;
		    }

		    if( scanCount > headCount ) {
			cookiesToSend.setElementAt(headC, j);
			cookiesToSend.setElementAt(scanC, i);
			headC = scanC;
			head = scan;
		    }
		}
	    }
	}


    return cookiesToSend;

    }

    /*
     * Writes cookies out to PrintWriter if they are persistent
     * (i.e. have a expr date)
     * and haven't expired. Will remove cookies that have expired as well
     */
    private void saveCookiesToStream(PrintWriter pw) {

	Enumeration cookieLists = cookieJar.elements();
		
	while (cookieLists.hasMoreElements()) {
	    Vector cookieList = (Vector)cookieLists.nextElement();

	    Enumeration cookies = cookieList.elements();

	    while (cookies.hasMoreElements()) {
		HttpCookie cookie = (HttpCookie)cookies.nextElement();
		
		if (cookie.getExpirationDate() != null) {
		    if (cookie.isSaveable()) {
			pw.println(cookie);
		    } else { // the cookie must have expired,
			//remove from Vector cookieList
			cookieList.removeElement(cookie);
		    }
		
		}
	    }
	}
        // Must print something to the printwriter in the case that
	// the cookieJar has been cleared - otherwise the old cookie
	// file will continue to exist.
	pw.print("");
    }
/////////////////////////////////////////////////////////////
    /* adds cookieList to the existing cookie jar*/
    public void addToCookieJar(HttpCookie[] cookieList) {

	if (cookieList != null) {
	    for (int i = 0; i < cookieList.length; i++) {
		
		recordCookie(cookieList[i]);
	    }
	}

    }

    /*adds one cookie to the Cookie Jar */
    public void addToCookieJar(String cookieString, URL docURL) {
	recordCookie(new HttpCookie(docURL, cookieString));
    }

    /* loads the cookies from the given filename */
    public void loadCookieJarFromFile(String cookieFileName) {
	try {
	    FileReader fr = new FileReader(cookieFileName);
	
	    BufferedReader in = new BufferedReader(fr);

	    try {
		String cookieString;
		while ((cookieString = in.readLine()) != null) {
		    HttpCookie cookie = new HttpCookie(cookieString);
		    // Record the cookie, without notification.  We don't
		    // do a notification for cookies that are read at
		    // program start-up.
		    recordCookie(cookie);
		}
	    } finally {
		in.close();
	    }

	
	} catch (IOException e) {
	    // do nothing; it's not an error not to have persistent cookies
	}

    }

    /* saves the cookies to the given file specified by fname */
    public void saveCookieJarToFile(String cookieFileName) {
	try {
	    FileWriter fw = new FileWriter(cookieFileName);
	    PrintWriter pw = new PrintWriter(fw, false);

	    try {
		saveCookiesToStream(pw);
	    } finally {
		pw.close();
	    }

	} catch (IOException e) {
	    // REMIND: I18N
	    System.err.println("Saving cookies failed " + e.getMessage());
	}
    }

    /**
     * Return an array with all of the cookies represented by this
     * jar.  This is useful when the bean is shutting down, and the client
     * wants to make the cookie jar persist.
     */
    public HttpCookie[] getAllCookies() {

	Vector result = new Vector();
	Hashtable jar;
	jar = (Hashtable) cookieJar.clone();
	
	synchronized (jar) {
	
	    for (Enumeration e = jar.elements(); e.hasMoreElements() ;) {
		Vector v = (Vector) e.nextElement();
		for (int i = 0; i < v.size(); i++) {
		    HttpCookie hc = (HttpCookie) v.elementAt(i);
		    result.addElement(hc);
		
		}
		
	    }
	}

	HttpCookie[] resultA = new HttpCookie[result.size()];
	for (int i = 0; i < result.size(); i++) {
	    resultA[i] = (HttpCookie) result.elementAt(i);
	}
	return resultA;
    }

    /* Gets all cookies that applies for the URL */
    public HttpCookie[] getCookiesForURL(URL url) {

	Vector cookieVector = getAllRelevantCookies(url);

	if (cookieVector == null) {
	    return null;
	}

	int i = 0;
	HttpCookie[] cookieArr = new HttpCookie[cookieVector.size()];

	for (Enumeration e = cookieVector.elements(); e.hasMoreElements(); ) {

	    cookieArr[i++] = (HttpCookie)e.nextElement();
//	    System.out.println("cookieArr["+(i-1)+"] = " +cookieArr[i-1].toString());
	}
	
	return cookieArr;
    }

    /* this will set the property of enableCookies to isDisabled */
    public void setCookieDisable(boolean isDisabled) {

	// Pending visit back this again
	try {
	Properties properties = new Properties();
	properties.load(new FileInputStream("ServerAutoRun.properties") );
	
	
	properties.put("enableCookies", isDisabled ? "false" : "true");
	properties.store(new FileOutputStream("ServerAutoRun.properties"),"comments");
	}
	catch ( Exception e )
	{
		System.out.println("Exception : " + e );
	}
    }

    public void discardAllCookies() {
	cookieJar.clear();
	
    }

    /*
     * purges any expired cookies in the Cookie hashtable.
     */
    public void purgeExpiredCookies() {
	Enumeration cookieLists = cookieJar.elements();
		
	while (cookieLists.hasMoreElements()) {
	    Vector cookieList = (Vector)cookieLists.nextElement();

	    Enumeration cookies = cookieList.elements();

	    while (cookies.hasMoreElements()) {
		HttpCookie cookie = (HttpCookie)cookies.nextElement();
		
		if (cookie.hasExpired()) {
		    cookieList.removeElement(cookie);
		}
	    }
	}

    }

}

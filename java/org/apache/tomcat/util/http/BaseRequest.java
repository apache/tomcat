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

/***************************************************************************
 * Description: Base http request object.                                  *
 * Author:      Keving Seguin [seguin@apache.org]                          *
 * Version:     $Revision$                                           *
 ***************************************************************************/

package org.apache.tomcat.util.http;

import java.io.PrintWriter;
import java.io.StringWriter;

import java.util.HashMap;
import java.util.Iterator;

import org.apache.tomcat.util.buf.MessageBytes;

/**
 * A general-purpose object for representing an HTTP
 * request.
 */
public class BaseRequest {

    // scheme constants
    public static final String SCHEME_HTTP = "http";
    public static final String SCHEME_HTTPS = "https";

    // request attributes
    MessageBytes method = MessageBytes.newInstance();
    MessageBytes protocol = MessageBytes.newInstance();
    MessageBytes requestURI = MessageBytes.newInstance();
    MessageBytes remoteAddr = MessageBytes.newInstance();
    MessageBytes remoteHost = MessageBytes.newInstance();
    MessageBytes serverName = MessageBytes.newInstance();
    int serverPort = 80;
    MessageBytes remoteUser = MessageBytes.newInstance();
    MessageBytes authType = MessageBytes.newInstance();
    MessageBytes queryString = MessageBytes.newInstance();
    MessageBytes authorization = MessageBytes.newInstance();
    String scheme = SCHEME_HTTP;
    boolean secure = false;
    int contentLength = 0;
    MessageBytes contentType = MessageBytes.newInstance();
    MimeHeaders headers = new MimeHeaders();
    Cookies cookies = new Cookies();
    HashMap attributes = new HashMap();

    MessageBytes tomcatInstanceId = MessageBytes.newInstance();
    
    /**
     * Recycles this object and readies it further use.
     */
    public void recycle() {
        method.recycle();
        protocol.recycle();
        requestURI.recycle();
        remoteAddr.recycle();
        remoteHost.recycle();
        serverName.recycle();
        serverPort = 80;
        remoteUser.recycle();
        authType.recycle();
        queryString.recycle();
        authorization.recycle();
        scheme = SCHEME_HTTP;
        secure = false;
        contentLength = 0;
        contentType.recycle();
        headers.recycle();
        cookies.recycle();
        attributes.clear();
        tomcatInstanceId.recycle();
    }

    /**
     * Get the method.
     * @return the method
     */
    public MessageBytes method() {
        return method;
    }

    /**
     * Get the protocol
     * @return the protocol
     */
    public MessageBytes protocol() {
        return protocol;
    }

    /**
     * Get the request uri
     * @return the request uri
     */
    public MessageBytes requestURI() {
        return requestURI;
    }

    /**
     * Get the remote address
     * @return the remote address
     */
    public MessageBytes remoteAddr() {
        return remoteAddr;
    }

    /**
     * Get the remote host
     * @return the remote host
     */
    public MessageBytes remoteHost() {
        return remoteHost;
    }

    /**
     * Get the server name
     * @return the server name
     */
    public MessageBytes serverName() {
        return serverName;
    }

    /**
     * Get the server port
     * @return the server port
     */
    public int getServerPort() {
        return serverPort;
    }

    /**
     * Set the server port
     * @param i the server port
     */
    public void setServerPort(int i) {
        serverPort = i;
    }

    /**
     * Get the remote user
     * @return the remote user
     */
    public MessageBytes remoteUser() {
        return remoteUser;
    }

    /**
     * Get the auth type
     * @return the auth type
     */
    public MessageBytes authType() {
        return authType;
    }

    /**
     * Get the query string
     * @return the query string
     */
    public MessageBytes queryString() {
        return queryString;
    }

    /**
     * Get the authorization credentials
     * @return the authorization credentials
     */
    public MessageBytes authorization() {
        return authorization;
    }

    /**
     * Get the scheme
     * @return the scheme
     */
    public String getScheme() {
        return scheme;
    }

    /**
     * Set the scheme.
     * @param s the scheme
     */
    public void setScheme(String s) {
        scheme = s;
    }

    /**
     * Get whether the request is secure or not.
     * @return <code>true</code> if the request is secure.
     */
    public boolean getSecure() {
        return secure;
    }

    /**
     * Set whether the request is secure or not.
     * @param b <code>true</code> if the request is secure.
     */
    public void setSecure(boolean b) {
        secure = b;
    }

    /**
     * Get the content length
     * @return the content length
     */
    public int getContentLength() {
        return contentLength;
    }

    /**
     * Set the content length
     * @param i the content length
     */
    public void setContentLength(int i) {
        contentLength = i;
    }

    /**
     * Get the content type
     * @return the content type
     */
    public MessageBytes contentType() {
        return contentType;
    }

    /**
     * Get this request's headers
     * @return request headers
     */
    public MimeHeaders headers() {
        return headers;
    }

    /**
     * Get cookies.
     * @return request cookies.
     */
    public Cookies cookies() {
        return cookies;
    }

    /**
     * Set an attribute on the request
     * @param name attribute name
     * @param value attribute value
     */
    public void setAttribute(String name, Object value) {
        if (name == null || value == null) {
            return;
        }
        attributes.put(name, value);
    }

    /**
     * Get an attribute on the request
     * @param name attribute name
     * @return attribute value
     */
    public Object getAttribute(String name) {
        if (name == null) {
            return null;
        }

        return attributes.get(name);
    }

    /**
     * Get iterator over attribute names
     * @return iterator over attribute names
     */
    public Iterator getAttributeNames() {
        return attributes.keySet().iterator();
    }

    /**
     * Get the host id ( or jvmRoute )
     * @return the jvm route
     */
    public MessageBytes instanceId() {
        return tomcatInstanceId;
    }

    // backward compat - jvmRoute is the id of this tomcat instance,
    // used by a load balancer on the server side to implement sticky
    // sessions, and on the tomcat side to format the session ids.
    public MessageBytes jvmRoute() {
        return tomcatInstanceId;
    }

    private Object notes[]=new Object[16];
    
    public final Object getNote(int id) {
        return notes[id];
    }

    public final void setNote(int id, Object cr) {
        notes[id]=cr;
    }
    
    /**
     * ** SLOW ** for debugging only!
     */
    public String toString() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        pw.println("=== BaseRequest ===");
        pw.println("method          = " + method.toString());
        pw.println("protocol        = " + protocol.toString());
        pw.println("requestURI      = " + requestURI.toString());
        pw.println("remoteAddr      = " + remoteAddr.toString());
        pw.println("remoteHost      = " + remoteHost.toString());
        pw.println("serverName      = " + serverName.toString());
        pw.println("serverPort      = " + serverPort);
        pw.println("remoteUser      = " + remoteUser.toString());
        pw.println("authType        = " + authType.toString());
        pw.println("queryString     = " + queryString.toString());
        pw.println("scheme          = " + scheme.toString());
        pw.println("secure          = " + secure);
        pw.println("contentLength   = " + contentLength);
        pw.println("contentType     = " + contentType);
        pw.println("attributes      = " + attributes.toString());
        pw.println("headers         = " + headers.toString());
        pw.println("cookies         = " + cookies.toString());
        pw.println("jvmRoute        = " + tomcatInstanceId.toString());
        return sw.toString();
    }
    
}

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


package org.apache.catalina.authenticator;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

import javax.servlet.http.Cookie;

import org.apache.tomcat.util.buf.ByteChunk;


/**
 * Object that saves the critical information from a request so that
 * form-based authentication can reproduce it once the user has been
 * authenticated.
 * <p>
 * <b>IMPLEMENTATION NOTE</b> - It is assumed that this object is accessed
 * only from the context of a single thread, so no synchronization around
 * internal collection classes is performed.
 *
 * @author Craig R. McClanahan
 * @version $Revision$ $Date$
 */

public final class SavedRequest {


    /**
     * The set of Cookies associated with this Request.
     */
    private ArrayList<Cookie> cookies = new ArrayList<Cookie>();

    public void addCookie(Cookie cookie) {
        cookies.add(cookie);
    }

    public Iterator getCookies() {
        return (cookies.iterator());
    }


    /**
     * The set of Headers associated with this Request.  Each key is a header
     * name, while the value is a ArrayList containing one or more actual
     * values for this header.  The values are returned as an Iterator when
     * you ask for them.
     */
    private HashMap<String,ArrayList<String>> headers =
        new HashMap<String,ArrayList<String>>();

    public void addHeader(String name, String value) {
        ArrayList<String> values = (ArrayList<String>) headers.get(name);
        if (values == null) {
            values = new ArrayList<String>();
            headers.put(name, values);
        }
        values.add(value);
    }

    public Iterator getHeaderNames() {
        return (headers.keySet().iterator());
    }

    public Iterator getHeaderValues(String name) {
        ArrayList values = (ArrayList) headers.get(name);
        if (values == null)
            return ((new ArrayList()).iterator());
        else
            return (values.iterator());
    }


    /**
     * The set of Locales associated with this Request.
     */
    private ArrayList<Locale> locales = new ArrayList<Locale>();

    public void addLocale(Locale locale) {
        locales.add(locale);
    }

    public Iterator getLocales() {
        return (locales.iterator());
    }


    /**
     * The request method used on this Request.
     */
    private String method = null;

    public String getMethod() {
        return (this.method);
    }

    public void setMethod(String method) {
        this.method = method;
    }



    /**
     * The set of request parameters associated with this Request.  Each
     * entry is keyed by the parameter name, pointing at a String array of
     * the corresponding values.
     */
    private HashMap<String,String[]> parameters =
        new HashMap<String,String[]>();

    public void addParameter(String name, String values[]) {
        parameters.put(name, values);
    }

    public Iterator getParameterNames() {
        return (parameters.keySet().iterator());
    }

    public String[] getParameterValues(String name) {
        return ((String[]) parameters.get(name));
    }


    /**
     * The query string associated with this Request.
     */
    private String queryString = null;

    public String getQueryString() {
        return (this.queryString);
    }

    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }


    /**
     * The request URI associated with this Request.
     */
    private String requestURI = null;

    public String getRequestURI() {
        return (this.requestURI);
    }

    public void setRequestURI(String requestURI) {
        this.requestURI = requestURI;
    }

    
    /**
     * The body of this request.
     */
    private ByteChunk body = null;
    
    public ByteChunk getBody() {
        return (this.body);
    }

    public void setBody(ByteChunk body) {
        this.body = body;
    }
    
    /**
     * The content type of the request, used if this is a POST.
     */
    private String contentType = null;
    
    public String getContentType() {
        return (this.contentType);
    }
    
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
}

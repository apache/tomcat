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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.Cookie;

import org.apache.tomcat.util.buf.ByteChunk;

/**
 * Object that saves the critical information from a request so that form-based authentication can reproduce it once the
 * user has been authenticated.
 * <p>
 * <b>IMPLEMENTATION NOTE</b> - It is assumed that this object is accessed only from the context of a single thread, so
 * no synchronization around internal collection classes is performed.
 */
public final class SavedRequest implements Serializable {

    /**
     * Default constructor.
     */
    public SavedRequest() {
    }

    private static final long serialVersionUID = 1L;

    /**
     * The set of Cookies associated with this Request.
     */
    private final List<Cookie> cookies = new ArrayList<>();

    /**
     * Adds a cookie to this saved request.
     * @param cookie the cookie to add
     */
    public void addCookie(Cookie cookie) {
        cookies.add(cookie);
    }

    /**
     * Returns an iterator over the cookies saved for this request.
     * @return iterator over the saved cookies
     */
    public Iterator<Cookie> getCookies() {
        return cookies.iterator();
    }


    /**
     * The set of Headers associated with this Request. Each key is a header name, while the value is a List containing
     * one or more actual values for this header. The values are returned as an Iterator when you ask for them.
     */
    private final Map<String,List<String>> headers = new HashMap<>();

    /**
     * Adds a header value to this saved request.
     * @param name the header name
     * @param value the header value
     */
    public void addHeader(String name, String value) {
        headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
    }

    /**
     * Returns an iterator over the names of all headers saved for this request.
     * @return iterator over the header names
     */
    public Iterator<String> getHeaderNames() {
        return headers.keySet().iterator();
    }

    /**
     * Returns an iterator over the values for the specified header.
     * @param name the header name
     * @return iterator over the header values, or an empty iterator if the header is not present
     */
    public Iterator<String> getHeaderValues(String name) {
        List<String> values = headers.get(name);
        if (values == null) {
            return Collections.emptyIterator();
        } else {
            return values.iterator();
        }
    }


    /**
     * The set of Locales associated with this Request.
     */
    private final List<Locale> locales = new ArrayList<>();

    /**
     * Adds a locale to this saved request.
     * @param locale the locale to add
     */
    public void addLocale(Locale locale) {
        locales.add(locale);
    }

    /**
     * Returns an iterator over the locales saved for this request.
     * @return iterator over the saved locales
     */
    public Iterator<Locale> getLocales() {
        return locales.iterator();
    }


    /**
     * The request method used on this Request.
     */
    private String method = null;

    /**
     * Returns the HTTP method of the saved request.
     * @return the request method, or {@code null} if not set
     */
    public String getMethod() {
        return this.method;
    }

    /**
     * Sets the HTTP method of the saved request.
     * @param method the request method
     */
    public void setMethod(String method) {
        this.method = method;
    }


    /**
     * The query string associated with this Request.
     */
    private String queryString = null;

    /**
     * Returns the query string of the saved request.
     * @return the query string, or {@code null} if not set
     */
    public String getQueryString() {
        return this.queryString;
    }

    /**
     * Sets the query string of the saved request.
     * @param queryString the query string
     */
    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }


    /**
     * The request URI associated with this Request.
     */
    private String requestURI = null;

    /**
     * Returns the request URI of the saved request.
     * @return the request URI, or {@code null} if not set
     */
    public String getRequestURI() {
        return this.requestURI;
    }

    /**
     * Sets the request URI of the saved request.
     * @param requestURI the request URI
     */
    public void setRequestURI(String requestURI) {
        this.requestURI = requestURI;
    }


    /**
     * The decode request URI associated with this Request. Path parameters are also excluded
     */
    private String decodedRequestURI = null;

    /**
     * Returns the decoded request URI of the saved request, with path parameters excluded.
     * @return the decoded request URI, or {@code null} if not set
     */
    public String getDecodedRequestURI() {
        return this.decodedRequestURI;
    }

    /**
     * Sets the decoded request URI of the saved request.
     * @param decodedRequestURI the decoded request URI
     */
    public void setDecodedRequestURI(String decodedRequestURI) {
        this.decodedRequestURI = decodedRequestURI;
    }


    /**
     * The body of this request.
     */
    private ByteChunk body = null;

    /**
     * Returns the body of the saved request.
     * @return the request body as a {@link ByteChunk}, or {@code null} if not set
     */
    public ByteChunk getBody() {
        return this.body;
    }

    /**
     * Sets the body of the saved request.
     * @param body the request body as a {@link ByteChunk}
     */
    public void setBody(ByteChunk body) {
        this.body = body;
    }


    /**
     * The content type of the request, used if this is a POST.
     */
    private String contentType = null;

    /**
     * Returns the content type of the saved request.
     * @return the content type, or {@code null} if not set
     */
    public String getContentType() {
        return this.contentType;
    }

    /**
     * Sets the content type of the saved request.
     * @param contentType the content type
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }


    /**
     * The original maxInactiveInterval for the session.
     */
    private Integer originalMaxInactiveInterval = null;

    /**
     * Returns the original max inactive interval for the session.
     * @return the original max inactive interval, or {@code null} if not set
     */
    public Integer getOriginalMaxInactiveIntervalOptional() {
        return originalMaxInactiveInterval;
    }

    /**
     * Obtain the original session maxInactiveInterval.
     *
     * @return the original session maxInactiveInterval
     *
     * @deprecated This method will be removed in Tomcat 12.0.x onwards. Use
     *                 {@link SavedRequest#getOriginalMaxInactiveIntervalOptional()}
     */
    @Deprecated
    public int getOriginalMaxInactiveInterval() {
        return (originalMaxInactiveInterval == null) ? -1 : originalMaxInactiveInterval.intValue();
    }

    /**
     * Sets the original max inactive interval for the session.
     * @param originalMaxInactiveInterval the max inactive interval in seconds
     */
    public void setOriginalMaxInactiveInterval(int originalMaxInactiveInterval) {
        this.originalMaxInactiveInterval = Integer.valueOf(originalMaxInactiveInterval);
    }
}

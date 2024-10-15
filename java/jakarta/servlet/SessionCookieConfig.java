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
package jakarta.servlet;

import java.util.Map;

/**
 * Configures the session cookies used by the web application associated with the ServletContext from which this
 * SessionCookieConfig was obtained.
 *
 * @since Servlet 3.0
 */
public interface SessionCookieConfig {

    /**
     * Sets the session cookie name.
     *
     * @param name The name of the session cookie
     *
     * @throws IllegalStateException if the associated ServletContext has already been initialised
     */
    void setName(String name);

    /**
     * Obtain the name to use for the session cookies.
     *
     * @return the name to use for session cookies.
     */
    String getName();

    /**
     * Sets the domain for the session cookie
     *
     * @param domain The session cookie domain
     *
     * @throws IllegalStateException if the associated ServletContext has already been initialised
     */
    void setDomain(String domain);

    /**
     * Obtain the domain to use for session cookies.
     *
     * @return the domain to use for session cookies.
     */
    String getDomain();

    /**
     * Sets the path of the session cookie.
     *
     * @param path The session cookie path
     *
     * @throws IllegalStateException if the associated ServletContext has already been initialised
     */
    void setPath(String path);

    /**
     * Obtain the path to use for session cookies. This is normally the context path.
     *
     * @return The path to use for session cookies.
     */
    String getPath();

    /**
     * If called, this method has no effect.
     *
     * @param comment Ignore
     *
     * @throws IllegalStateException if the associated ServletContext has already been initialised
     *
     * @deprecated This is no longer required with RFC 6265
     */
    @Deprecated(since = "Servlet 6.0", forRemoval = true)
    void setComment(String comment);

    /**
     * With the adoption of support for RFC 6265, this method should no longer be used.
     *
     * @return always {@code null}
     *
     * @deprecated This is no longer required with RFC 6265
     */
    @Deprecated(since = "Servlet 6.0", forRemoval = true)
    String getComment();

    /**
     * Sets the httpOnly flag for the session cookie.
     *
     * @param httpOnly The httpOnly setting to use for session cookies
     *
     * @throws IllegalStateException if the associated ServletContext has already been initialised
     */
    void setHttpOnly(boolean httpOnly);

    /**
     * Will session cookies be created with the httpOnly flag set?
     *
     * @return {@code true} if the flag should be set, otherwise {@code false}
     */
    boolean isHttpOnly();

    /**
     * Sets the secure flag for the session cookie.
     *
     * @param secure The secure setting to use for session cookies
     *
     * @throws IllegalStateException if the associated ServletContext has already been initialised
     */
    void setSecure(boolean secure);

    /**
     * Will session cookies be created with the secure flag set?
     *
     * @return {@code true} if the flag should be set, otherwise {@code false}
     */
    boolean isSecure();

    /**
     * Sets the maximum age.
     *
     * @param MaxAge the maximum age to set
     *
     * @throws IllegalStateException if the associated ServletContext has already been initialised
     */
    void setMaxAge(int MaxAge);

    /**
     * Obtain the maximum age to set for a session cookie.
     *
     * @return the maximum age in seconds
     */
    int getMaxAge();

    /**
     * Sets the value for the given session cookie attribute. When a value is set via this method, the value returned by
     * the attribute specific getter (if any) must be consistent with the value set via this method.
     *
     * @param name  Name of attribute to set
     * @param value Value of attribute
     *
     * @throws IllegalStateException    if the associated ServletContext has already been initialised
     * @throws IllegalArgumentException If the attribute name is null or contains any characters not permitted for use
     *                                      in Cookie names.
     * @throws NumberFormatException    If the attribute is known to be numerical but the provided value cannot be
     *                                      parsed to a number.
     *
     * @since Servlet 6.0
     */
    void setAttribute(String name, String value);

    /**
     * Obtain the value for a sesison cookie given attribute. Values returned from this method must be consistent with
     * the values set and returned by the attribute specific getters and setters in this class.
     *
     * @param name Name of attribute to return
     *
     * @return Value of specified attribute
     *
     * @since Servlet 6.0
     */
    String getAttribute(String name);

    /**
     * Obtain the Map of attributes and values (excluding version) for this session cookie.
     *
     * @return A read-only Map of attributes to values, excluding version.
     *
     * @since Servlet 6.0
     */
    Map<String,String> getAttributes();
}

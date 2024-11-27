/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jakarta.servlet.http;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.BitSet;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TreeMap;

/**
 * Creates a cookie, a small amount of information sent by a servlet to a Web browser, saved by the browser, and later
 * sent back to the server. A cookie's value can uniquely identify a client, so cookies are commonly used for session
 * management.
 * <p>
 * A cookie has a name, a single value, and optional attributes such as a comment, path and domain qualifiers, a maximum
 * age, and a version number. Some Web browsers have bugs in how they handle the optional attributes, so use them
 * sparingly to improve the interoperability of your servlets.
 * <p>
 * The servlet sends cookies to the browser by using the {@link HttpServletResponse#addCookie} method, which adds fields
 * to HTTP response headers to send cookies to the browser, one at a time. The browser is expected to support 50 cookies
 * for each domain, 3000 cookies total, and may limit cookie size to 4 KiB each.
 * <p>
 * The browser returns cookies to the servlet by adding fields to HTTP request headers. Cookies can be retrieved from a
 * request by using the {@link HttpServletRequest#getCookies} method. Several cookies might have the same name but
 * different path attributes.
 * <p>
 * Cookies affect the caching of the Web pages that use them. HTTP 1.0 does not cache pages that use cookies created
 * with this class. This class does not support the cache control defined with HTTP 1.1.
 * <p>
 * This class supports the RFC 6265 specification.
 */
public class Cookie implements Cloneable, Serializable {

    private static final String LSTRING_FILE = "jakarta.servlet.http.LocalStrings";
    private static final ResourceBundle LSTRINGS = ResourceBundle.getBundle(LSTRING_FILE);

    private static final CookieNameValidator validation = new RFC6265Validator();

    private static final String EMPTY_STRING = "";

    private static final long serialVersionUID = 2L;

    /**
     * Cookie name.
     */
    private final String name;

    /**
     * Cookie value.
     */
    private String value;

    /**
     * Attributes encoded in the header's cookie fields.
     */
    private volatile Map<String,String> attributes;

    private static final String DOMAIN = "Domain";
    private static final String MAX_AGE = "Max-Age";
    private static final String PATH = "Path";
    private static final String SECURE = "Secure";
    private static final String HTTP_ONLY = "HttpOnly";

    /**
     * Constructs a cookie with a specified name and value.
     * <p>
     * The cookie's name cannot be changed after creation.
     * <p>
     * The value can be anything the server chooses to send. Its value is probably of interest only to the server. The
     * cookie's value can be changed after creation with the <code>setValue</code> method.
     *
     * @param name  a <code>String</code> specifying the name of the cookie
     * @param value a <code>String</code> specifying the value of the cookie
     *
     * @throws IllegalArgumentException if the cookie name contains illegal characters
     *
     * @see #setValue
     */
    public Cookie(String name, String value) {
        validation.validate(name);
        this.name = name;
        this.value = value;
    }


    /**
     * If called, this method has no effect.
     *
     * @param purpose ignored
     *
     * @see #getComment
     *
     * @deprecated This is no longer required with RFC 6265
     */
    @Deprecated(since = "Servlet 6.0", forRemoval = true)
    public void setComment(String purpose) {
        // NO-OP
    }


    /**
     * With the adoption of support for RFC 6265, this method should no longer be used.
     *
     * @return always {@code null}
     *
     * @see #setComment
     *
     * @deprecated This is no longer required with RFC 6265
     */
    @Deprecated(since = "Servlet 6.0", forRemoval = true)
    public String getComment() {
        return null;
    }


    /**
     * Specifies the domain within which this cookie should be presented.
     * <p>
     * By default, cookies are only returned to the server that sent them.
     *
     * @param pattern a <code>String</code> containing the domain name within which this cookie is visible
     *
     * @see #getDomain
     */
    public void setDomain(String pattern) {
        if (pattern == null) {
            setAttributeInternal(DOMAIN, null);
        } else {
            // IE requires the domain to be lower case (unconfirmed)
            setAttributeInternal(DOMAIN, pattern.toLowerCase(Locale.ENGLISH));
        }
    }


    /**
     * Returns the domain name set for this cookie.
     *
     * @return a <code>String</code> containing the domain name
     *
     * @see #setDomain
     */
    public String getDomain() {
        return getAttribute(DOMAIN);
    }


    /**
     * Sets the maximum age of the cookie in seconds.
     * <p>
     * A positive value indicates that the cookie will expire after that many seconds have passed. Note that the value
     * is the <i>maximum</i> age when the cookie will expire, not the cookie's current age.
     * <p>
     * A negative value means that the cookie is not stored persistently and will be deleted when the Web browser exits.
     * A zero value causes the cookie to be deleted.
     *
     * @param expiry an integer specifying the maximum age of the cookie in seconds; if negative, means the cookie is
     *                   not stored; if zero, deletes the cookie
     *
     * @see #getMaxAge
     */
    public void setMaxAge(int expiry) {
        setAttributeInternal(MAX_AGE, Integer.toString(expiry));
    }


    /**
     * Returns the maximum age of the cookie, specified in seconds, By default, <code>-1</code> indicating the cookie
     * will persist until browser shutdown.
     *
     * @return an integer specifying the maximum age of the cookie in seconds; if negative, means the cookie persists
     *             until browser shutdown
     *
     * @see #setMaxAge
     */
    public int getMaxAge() {
        String maxAge = getAttribute(MAX_AGE);
        if (maxAge == null) {
            return -1;
        } else {
            return Integer.parseInt(maxAge);
        }
    }


    /**
     * Specifies a path for the cookie to which the client should return the cookie.
     * <p>
     * The cookie is visible to all the pages in the directory you specify, and all the pages in that directory's
     * subdirectories. A cookie's path must include the servlet that set the cookie, for example, <i>/catalog</i>, which
     * makes the cookie visible to all directories on the server under <i>/catalog</i>.
     *
     * @param uri a <code>String</code> specifying a path
     *
     * @see #getPath
     */
    public void setPath(String uri) {
        setAttributeInternal(PATH, uri);
    }


    /**
     * Returns the path on the server to which the browser returns this cookie. The cookie is visible to all subpaths on
     * the server.
     *
     * @return a <code>String</code> specifying a path that contains a servlet name, for example, <i>/catalog</i>
     *
     * @see #setPath
     */
    public String getPath() {
        return getAttribute(PATH);
    }


    /**
     * Indicates to the browser whether the cookie should only be sent using a secure protocol, such as HTTPS or SSL.
     * <p>
     * The default value is <code>false</code>.
     *
     * @param secure if <code>true</code>, sends the cookie from the browser to the server only when using a secure
     *                 protocol; if <code>false</code>, sent on any protocol
     *
     * @see #getSecure
     */
    public void setSecure(boolean secure) {
        if (secure) {
            setAttributeInternal(SECURE, EMPTY_STRING);
        } else {
            setAttributeInternal(SECURE, null);
        }
    }


    /**
     * Returns <code>true</code> if the browser is sending cookies only over a secure protocol, or <code>false</code> if
     * the browser can send cookies using any protocol.
     *
     * @return <code>true</code> if the browser uses a secure protocol; otherwise, <code>false</code>
     *
     * @see #setSecure
     */
    public boolean getSecure() {
        return EMPTY_STRING.equals(getAttribute(SECURE));
    }


    /**
     * Returns the name of the cookie. The name cannot be changed after creation.
     *
     * @return a <code>String</code> specifying the cookie's name
     */
    public String getName() {
        return name;
    }


    /**
     * Assigns a new value to a cookie after the cookie is created. If you use a binary value, you may want to use
     * BASE64 encoding.
     * <p>
     * With Version 0 cookies, values should not contain white space, brackets, parentheses, equals signs, commas,
     * double quotes, slashes, question marks, at signs, colons, and semicolons. Empty values may not behave the same
     * way on all browsers.
     *
     * @param newValue a <code>String</code> specifying the new value
     *
     * @see #getValue
     * @see Cookie
     */
    public void setValue(String newValue) {
        value = newValue;
    }


    /**
     * Returns the value of the cookie.
     *
     * @return a <code>String</code> containing the cookie's present value
     *
     * @see #setValue
     * @see Cookie
     */
    public String getValue() {
        return value;
    }


    /**
     * With the adoption of support for RFC 6265, this method should no longer be used.
     *
     * @return Always zero
     *
     * @see #setVersion
     *
     * @deprecated This is no longer required with RFC 6265
     */
    @Deprecated(since = "Servlet 6.0", forRemoval = true)
    public int getVersion() {
        return 0;
    }


    /**
     * If called, this method has no effect.
     *
     * @param v Ignored
     *
     * @see #getVersion
     *
     * @deprecated This is no longer required with RFC 6265
     */
    @Deprecated(since = "Servlet 6.0", forRemoval = true)
    public void setVersion(int v) {
        // NO-OP
    }


    /**
     * Overrides the standard <code>java.lang.Object.clone</code> method to return a copy of this cookie.
     */
    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Sets the flag that controls if this cookie will be hidden from scripts on the client side.
     *
     * @param httpOnly The new value of the flag
     *
     * @since Servlet 3.0
     */
    public void setHttpOnly(boolean httpOnly) {
        if (httpOnly) {
            setAttributeInternal(HTTP_ONLY, EMPTY_STRING);
        } else {
            setAttributeInternal(HTTP_ONLY, null);
        }
    }


    /**
     * Gets the flag that controls if this cookie will be hidden from scripts on the client side.
     *
     * @return <code>true</code> if the cookie is hidden from scripts, else <code>false</code>
     *
     * @since Servlet 3.0
     */
    public boolean isHttpOnly() {
        return EMPTY_STRING.equals(getAttribute(HTTP_ONLY));
    }


    /**
     * Sets the value for the given cookie attribute. When a value is set via this method, the value returned by the
     * attribute specific getter (if any) must be consistent with the value set via this method.
     *
     * @param name  Name of attribute to set
     * @param value Value of attribute
     *
     * @throws IllegalArgumentException If the attribute name is null or contains any characters not permitted for use
     *                                      in Cookie names.
     * @throws NumberFormatException    If the attribute is known to be numerical but the provided value cannot be
     *                                      parsed to a number.
     *
     * @since Servlet 6.0
     */
    public void setAttribute(String name, String value) {
        if (name == null) {
            throw new IllegalArgumentException(LSTRINGS.getString("cookie.attribute.invalidName.null"));
        }
        if (!validation.isToken(name)) {
            String msg = LSTRINGS.getString("cookie.attribute.invalidName.notToken");
            throw new IllegalArgumentException(MessageFormat.format(msg, name));
        }

        if (name.equalsIgnoreCase(MAX_AGE)) {
            if (value == null) {
                setAttributeInternal(MAX_AGE, null);
            } else {
                // Integer.parseInt throws NFE if required
                setMaxAge(Integer.parseInt(value));
            }
        } else {
            setAttributeInternal(name, value);
        }
    }


    private void setAttributeInternal(String name, String value) {
        if (attributes == null) {
            if (value == null) {
                return;
            } else {
                // Case insensitive keys but retain case used
                attributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            }
        }

        if (value == null) {
            attributes.remove(name);
        } else {
            attributes.put(name, value);
        }
    }


    /**
     * Obtain the value for a given attribute. Values returned from this method must be consistent with the values set
     * and returned by the attribute specific getters and setters in this class.
     *
     * @param name Name of attribute to return
     *
     * @return Value of specified attribute
     *
     * @since Servlet 6.0
     */
    public String getAttribute(String name) {
        if (attributes == null) {
            return null;
        } else {
            return attributes.get(name);
        }
    }


    /**
     * Obtain the Map of attributes and values (excluding version) for this cookie.
     *
     * @return A read-only Map of attributes to values, excluding version.
     *
     * @since Servlet 6.0
     */
    public Map<String,String> getAttributes() {
        if (attributes == null) {
            return Collections.emptyMap();
        } else {
            return Collections.unmodifiableMap(attributes);
        }
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((attributes == null) ? 0 : attributes.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((value == null) ? 0 : value.hashCode());
        return result;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Cookie other = (Cookie) obj;
        if (attributes == null) {
            if (other.attributes != null) {
                return false;
            }
        } else if (!attributes.equals(other.attributes)) {
            return false;
        }
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        if (value == null) {
            if (other.value != null) {
                return false;
            }
        } else if (!value.equals(other.value)) {
            return false;
        }
        return true;
    }
}


class CookieNameValidator {
    private static final String LSTRING_FILE = "jakarta.servlet.http.LocalStrings";
    protected static final ResourceBundle lStrings = ResourceBundle.getBundle(LSTRING_FILE);

    protected final BitSet allowed;

    protected CookieNameValidator(String separators) {
        allowed = new BitSet(128);
        allowed.set(0x20, 0x7f); // any CHAR except CTLs or separators
        for (int i = 0; i < separators.length(); i++) {
            char ch = separators.charAt(i);
            allowed.clear(ch);
        }
    }

    void validate(String name) {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException(lStrings.getString("err.cookie_name_blank"));
        }
        if (!isToken(name)) {
            String errMsg = lStrings.getString("err.cookie_name_is_token");
            throw new IllegalArgumentException(MessageFormat.format(errMsg, name));
        }
    }

    boolean isToken(String possibleToken) {
        int len = possibleToken.length();

        for (int i = 0; i < len; i++) {
            char c = possibleToken.charAt(i);
            if (!allowed.get(c)) {
                return false;
            }
        }
        return true;
    }
}

class RFC6265Validator extends CookieNameValidator {
    private static final String RFC2616_SEPARATORS = "()<>@,;:\\\"/[]?={} \t";

    RFC6265Validator() {
        super(RFC2616_SEPARATORS);
    }
}
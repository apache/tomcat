package org.apache.tomcat.util.http;

import org.apache.tomcat.util.res.StringManager;

public class SameSiteCookies {

    private static final StringManager sm = StringManager.getManager(SameSiteCookies.class);

    private final String value;

    public static final SameSiteCookies NONE = new SameSiteCookies("None");
    /** Don't set the SameSite cookie attribute. Cookie is always sent
     */
    public static final SameSiteCookies LAX = new SameSiteCookies("Lax");
    /** Cookie is only sent on same-site requests and cross-site top level navigation GET requests
     */
    public static final SameSiteCookies STRICT = new SameSiteCookies("Strict");
    /** Prevents the cookie from being sent by the browser in all cross-site requests
     */

    public static SameSiteCookies toAttribute(String value) {
        SameSiteCookies attribute;
        if (value.equalsIgnoreCase(NONE.value)) {
            attribute = NONE;
        } else if (value.equalsIgnoreCase(LAX.value)) {
            attribute = LAX;
        } else if (value.equalsIgnoreCase(STRICT.value)) {
            attribute = STRICT;
        } else {
            throw new IllegalStateException(
                    sm.getString("cookies.invalidSameSiteCookies", value));
        }
        return attribute;
    }

    private SameSiteCookies(String value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        boolean equals = false;
        if (o instanceof SameSiteCookies) {
            SameSiteCookies attribute = (SameSiteCookies) o;
            equals = value.equals(attribute.value);
        }
        return equals;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}

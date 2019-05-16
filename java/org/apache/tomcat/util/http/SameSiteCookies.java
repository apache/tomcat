package org.apache.tomcat.util.http;

import org.apache.tomcat.util.res.StringManager;

public enum SameSiteCookies {

    /**
     * Don't set the SameSite cookie attribute. Cookie is always sent
     */
    NONE("None"),

    /**
     * Cookie is only sent on same-site requests and cross-site top level navigation GET requests
     */
    LAX("Lax"),

    /**
     * Prevents the cookie from being sent by the browser in all cross-site requests
     */
    STRICT("Strict");

    private static final StringManager sm = StringManager.getManager(SameSiteCookies.class);

    private final String value;

    SameSiteCookies(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static SameSiteCookies fromString(String value) {
        for (SameSiteCookies sameSiteCookies : SameSiteCookies.values()) {
            if (sameSiteCookies.getValue().equalsIgnoreCase(value)) {
                return sameSiteCookies;
            }
        }

        throw new IllegalStateException(sm.getString("cookies.invalidSameSiteCookies", value));
    }
}

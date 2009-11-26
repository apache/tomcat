/*
 */
package org.apache.tomcat.lite.servlet;

import org.apache.tomcat.lite.http.HttpRequest;

public class ServletApi {

    public ServletContextImpl newContext() {
        return null;
    }

    public ServletRequestImpl newRequest(HttpRequest req) {
        return null;
    }


    public static ServletApi get() {
        Class<?> cls = null;
        try {
            Class.forName("javax.servlet.http.Part");
            cls = Class.forName("org.apache.tomcat.lite.servlet.ServletApi30");
        } catch (Throwable t) {
            try {
                cls = Class.forName("org.apache.tomcat.lite.servlet.ServletApi25");
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Can't load servlet api", e);
            }
        }
        try {
            return (ServletApi) cls.newInstance();
        } catch (Throwable e) {
            throw new RuntimeException("Can't load servlet api", e);
        }
    }
    
}
/*
 */
package org.apache.tomcat.lite.servlet;

import org.apache.tomcat.lite.http.HttpRequest;

public class ServletApi25  extends ServletApi {
    public ServletContextImpl newContext() {
        return new ServletContextImpl() {
            
        };
    }

    public ServletRequestImpl newRequest(HttpRequest req) {
        return new ServletRequestImpl(req) {
            
        };
    }
}

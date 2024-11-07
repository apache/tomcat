package org.apache.catalina.filters;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;

public class RateLimitFilterOfPerformance extends RateLimitFilter {
    public RateLimitFilterOfPerformance() {
        super();
    }
    public void init(FilterConfig filterConfig) throws ServletException {
        super.init(filterConfig);
    }
    @Override
    protected String getLimitByIdentifier(ServletRequest request) {
        return ((HttpServletRequest)request).getHeader(X_CUST_IP);
    }
    
    static final String X_CUST_IP = "x-req-ip";
}
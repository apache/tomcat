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
    protected String parseQuotaIdentifier(ServletRequest request) {
        return ((HttpServletRequest)request).getHeader(TesterRateLimitFilterPerformance.X_CUST_IP);
    }
}
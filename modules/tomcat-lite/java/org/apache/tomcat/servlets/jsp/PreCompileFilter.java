package org.apache.tomcat.servlets.jsp;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;

/** 
 * Filter for JSPs to support 'preCompile' support.
 * This is a silly feature, can and should be left out in prod.
 * It needs to be used in the default config to pass the tests.
 * 
 * @author Costin Manolache
 */
public class PreCompileFilter extends HttpServlet {
    // Copied from jasper.Constants to avoid compile dep
    /**
     * The query parameter that causes the JSP engine to just
     * pregenerated the servlet but not invoke it. 
     */
    public static final String PRECOMPILE = 
      System.getProperty("org.apache.jasper.Constants.PRECOMPILE", "jsp_precompile");
    
    /** 
     * If called from a <jsp-file> servlet, compile the servlet and init it.
     */
    public void init(ServletConfig arg0) throws ServletException {
        super.init(arg0);
    }
    
    boolean preCompile(HttpServletRequest request) throws ServletException {
        String queryString = request.getQueryString();
        if (queryString == null) {
            return (false);
        }
        int start = queryString.indexOf(PRECOMPILE);
        if (start < 0) {
            return (false);
        }
        queryString =
            queryString.substring(start + PRECOMPILE.length());
        if (queryString.length() == 0) {
            return (true);             // ?jsp_precompile
        }
        if (queryString.startsWith("&")) {
            return (true);             // ?jsp_precompile&foo=bar...
        }
        if (!queryString.startsWith("=")) {
            return (false);            // part of some other name or value
        }
        int limit = queryString.length();
        int ampersand = queryString.indexOf("&");
        if (ampersand > 0) {
            limit = ampersand;
        }
        String value = queryString.substring(1, limit);
        if (value.equals("true")) {
            return (true);             // ?jsp_precompile=true
        } else if (value.equals("false")) {
            // Spec says if jsp_precompile=false, the request should not
            // be delivered to the JSP page; the easiest way to implement
            // this is to set the flag to true, and precompile the page anyway.
            // This still conforms to the spec, since it says the
            // precompilation request can be ignored.
            return (true);             // ?jsp_precompile=false
        } else {
            throw new ServletException("Cannot have request parameter " +
                        PRECOMPILE + " set to " + value);
        }
    }
} 

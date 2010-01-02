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


package org.apache.tomcat.servlets.sec;


import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;


/**
 * Reimplementation of catalina IP valve.
 * 
 */
public class IPFilter implements Filter {
    /**
     * The set of <code>allow</code> regular expressions we will evaluate.
     */
    protected Pattern allows[] = new Pattern[0];


    /**
     * The set of <code>deny</code> regular expressions we will evaluate.
     */
    protected Pattern denies[] = new Pattern[0];

    // --------------------------------------------------------- Public Methods

    public void setAllows(String pattern) {
        allows = getPatterns(pattern);
    }
    
    public void setDenies(String pattern) {
        denies = getPatterns(pattern);
    }
    
    private Pattern[] getPatterns(String pattern) {
        String[] patSplit = pattern.split(",");
        ArrayList allowsAL = new ArrayList();
        for( int i=0; i<patSplit.length; i++) {
            patSplit[i] = patSplit[i].trim();
            if (!patSplit[i].equals("")) {
                allowsAL.add(Pattern.compile(patSplit[i]));
            }
        }
        // TODO
        Pattern[] result = new Pattern[allowsAL.size()];
        allowsAL.toArray(result);
        return result;
    }
    
    public void destroy() {
    }


    public void doFilter(ServletRequest request, 
                         ServletResponse servletResponse, 
                         FilterChain chain) 
            throws IOException, ServletException {
        
        HttpServletResponse response = (HttpServletResponse)servletResponse;
        String property = request.getRemoteAddr();
        
        // Check the deny patterns, if any
        for (int i = 0; i < denies.length; i++) {
            if (denies[i].matcher(property).matches()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        }

        // Check the allow patterns, if any
        for (int i = 0; i < allows.length; i++) {
            if (allows[i].matcher(property).matches()) {
                chain.doFilter(request, response);
                return;
            }
        }

        // Allow if denies specified but not allows
        if ((denies.length > 0) && (allows.length == 0)) {
            chain.doFilter(request, response);
            return;
        }

        // Deny this request
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }


    public void init(FilterConfig filterConfig) throws ServletException {
    }


}

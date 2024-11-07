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
package org.apache.catalina.filters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.ResponseFacade;

/**
 * A Filter that adds a series of Link and/or Content-Security-Policy
 * headers to a 103 response if a compatible protocol is in use.
 */
public class EarlyHintsFilter
    implements Filter
{
    private final ArrayList<String> csps = new ArrayList<String>(1);
    private final ArrayList<String> hints = new ArrayList<String>();

    @Override
    public void init(FilterConfig config) throws ServletException {
        Enumeration<String> paramNames = config.getInitParameterNames();
        while(paramNames.hasMoreElements()) {
            String name = paramNames.nextElement();

            if(name.startsWith("csp.")) {
                csps.add(config.getInitParameter(name));
            } else if(name.startsWith("link.")) {
                String hint = config.getInitParameter(name);
                int pos = hint.indexOf("${contextPath}");
                if(pos >= 0) {
                    hint = hint.replace("${contextPath}", config.getServletContext().getContextPath());
                }

                hints.add(hint);
            } else {
                config.getServletContext().log("WARNING: Unexpected init-param to EarlyHintsFilter: " + name);
            }
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse rsp = (HttpServletResponse)response;

        if(!csps.isEmpty()) {
            for(String csp : csps) {
                rsp.addHeader("Content-Security-Policy", csp);
            }
        }
        if(!hints.isEmpty()) {
            for(String hint : hints) {
                rsp.addHeader("Link", hint);
            }

            // NOTE: Tomcat will only return a 103 response here when
            // the request protocol is HTTP/1.1 or HTTP/2.0. For HTTP/1.0
            // requests, Tomcat will do nothing.

            ((ResponseFacade)rsp).sendEarlyHints();
        }

        chain.doFilter(request, response);
    }
}
